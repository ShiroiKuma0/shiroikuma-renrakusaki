package org.fossify.contacts.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.graphics.drawable.LayerDrawable
import android.telephony.PhoneNumberUtils
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.signature.ObjectKey
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import org.fossify.commons.adapters.MyRecyclerViewAdapter
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.commons.interfaces.ItemMoveCallback
import org.fossify.commons.interfaces.ItemTouchHelperContract
import org.fossify.commons.interfaces.StartReorderDragListener
import org.fossify.commons.models.RadioItem
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.views.MyRecyclerView
import org.fossify.contacts.R
import org.fossify.contacts.activities.SimpleActivity
import org.fossify.contacts.activities.ViewContactActivity
import org.fossify.contacts.dialogs.CreateNewGroupDialog
import org.fossify.contacts.dialogs.SetDefaultSimDialog
import org.fossify.contacts.extensions.ThemeSlot
import org.fossify.contacts.extensions.applyThemeFont
import org.fossify.contacts.extensions.config
import org.fossify.contacts.extensions.editContact
import org.fossify.contacts.extensions.shareContacts
import org.fossify.contacts.extensions.themeColor
import org.fossify.contacts.helpers.*
import org.fossify.contacts.interfaces.RefreshContactsListener
import org.fossify.contacts.interfaces.RemoveFromGroupListener
import org.fossify.contacts.models.ContactSection
import java.text.Collator
import java.util.Collections
import java.util.Locale

class ContactsAdapter(
    activity: SimpleActivity,
    var contactItems: MutableList<Contact>,
    recyclerView: MyRecyclerView,
    highlightText: String = "",
    var viewType: Int = VIEW_TYPE_LIST,
    private val refreshListener: RefreshContactsListener?,
    private val location: Int,
    private val removeListener: RemoveFromGroupListener?,
    private val enableDrag: Boolean = false,
    itemClick: (Any) -> Unit,
    private val profileIconClick: ((Any) -> Unit)? = null,
    // Letter sections (grouped, foldable) — Contacts tab only; the pickers and Favorites stay flat.
    private val groupBySections: Boolean = false,
    // 詳 detail mode — single column, rows get last-call / last-SMS lines appended.
    private val detailMode: Boolean = false
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick), RecyclerViewFastScroller.OnPopupTextUpdate, ItemTouchHelperContract {

    private val NEW_GROUP_ID = -1

    private var config = activity.config
    private var textToHighlight = highlightText

    // What the RecyclerView actually shows: contacts, interleaved with ContactSection headers in grouped
    // mode. [cellInfos] mirrors it position-for-position with each contact row's grid cell (null = header).
    private var displayItems = ArrayList<Any>()
    private var cellInfos = ArrayList<RowCell?>()

    var startNameWithSurname = config.startNameWithSurname
    var showContactThumbnails = config.showContactThumbnails
    var showPhoneNumbers = config.showPhoneNumbers
    var fontSize = activity.getTextSize()
    var onDragEndListener: (() -> Unit)? = null

    // Configured thumbnail size (px) for the custom list rows; grid keeps its own cell size.
    private val thumbnailSizePx = (config.contactsListThumbnailSize * activity.resources.displayMetrics.density).toInt()

    // Configured list-row layout grouped into lines (each line a list of column fields). Static for the
    // adapter's lifetime; a config change recreates the adapter via forceListRedraw.
    private val contactRowLines: List<List<RowField>> by lazy { buildContactRowLines() }

    private var touchHelper: ItemTouchHelper? = null
    private var startReorderDragListener: StartReorderDragListener? = null

    init {
        rebuildDisplayList()
        setupDragListener(true)
        setupRowDecoration()

        if (enableDrag) {
            touchHelper = ItemTouchHelper(ItemMoveCallback(this, viewType == VIEW_TYPE_GRID))
            touchHelper!!.attachToRecyclerView(recyclerView)

            startReorderDragListener = object : StartReorderDragListener {
                override fun requestDrag(viewHolder: RecyclerView.ViewHolder) {
                    touchHelper?.startDrag(viewHolder)
                }
            }
        }
    }

    override fun getActionMenuId() = R.menu.cab

    override fun prepareActionMode(menu: Menu) {
        menu.apply {
            findItem(R.id.cab_edit).isVisible = isOneItemSelected()
            findItem(R.id.cab_remove).isVisible = location == LOCATION_FAVORITES_TAB || location == LOCATION_GROUP_CONTACTS
            findItem(R.id.cab_add_to_favorites).isVisible = location == LOCATION_CONTACTS_TAB || location == LOCATION_GROUP_CONTACTS
            findItem(R.id.cab_add_to_group).isVisible = location == LOCATION_CONTACTS_TAB || location == LOCATION_FAVORITES_TAB
            findItem(R.id.cab_send_sms_to_contacts).isVisible =
                location == LOCATION_CONTACTS_TAB || location == LOCATION_FAVORITES_TAB || location == LOCATION_GROUP_CONTACTS
            findItem(R.id.cab_send_email_to_contacts).isVisible =
                location == LOCATION_CONTACTS_TAB || location == LOCATION_FAVORITES_TAB || location == LOCATION_GROUP_CONTACTS
            findItem(R.id.cab_delete).isVisible = location == LOCATION_CONTACTS_TAB || location == LOCATION_GROUP_CONTACTS
            findItem(R.id.cab_create_shortcut).isVisible =
                isOreoPlus() && isOneItemSelected() && (location == LOCATION_FAVORITES_TAB || location == LOCATION_CONTACTS_TAB)
            // CAB only opens where long-press is enabled (never in INSERT_OR_EDIT), so one-selected suffices.
            findItem(R.id.cab_set_default_sim).isVisible = isOneItemSelected()

            if (location == LOCATION_GROUP_CONTACTS) {
                findItem(R.id.cab_remove).title = activity.getString(R.string.remove_from_group)
            }
        }
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_edit -> editContact()
            R.id.cab_select_all -> selectAll()
            R.id.cab_add_to_favorites -> addToFavorites()
            R.id.cab_add_to_group -> addToGroup()
            R.id.cab_share -> shareContacts()
            R.id.cab_send_sms_to_contacts -> sendSMSToContacts()
            R.id.cab_send_email_to_contacts -> sendEmailToContacts()
            R.id.cab_create_shortcut -> createShortcut()
            R.id.cab_set_default_sim -> setDefaultSim()
            R.id.cab_remove -> removeContacts()
            R.id.cab_delete -> askConfirmDelete()
        }
    }

    override fun getSelectableItemCount() = contactItems.size

    override fun getIsItemSelectable(position: Int) = displayItems.getOrNull(position) is Contact

    override fun getItemSelectionKey(position: Int) = (displayItems.getOrNull(position) as? Contact)?.id

    override fun getItemKeyPosition(key: Int) = displayItems.indexOfFirst { (it as? Contact)?.id == key }

    override fun onActionModeCreated() {
        notifyDataSetChanged()
    }

    override fun onActionModeDestroyed() {
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = when (viewType) {
            VIEW_TYPE_SECTION -> R.layout.item_contact_section

            VIEW_TYPE_GRID -> {
                if (showPhoneNumbers) org.fossify.commons.R.layout.item_contact_with_number_grid else org.fossify.commons.R.layout.item_contact_without_number_grid
            }

            // List rows are fully configurable (which fields, order, columns) — use our own layout.
            else -> R.layout.item_contact_custom
        }

        return createViewHolder(layout, parent)
    }

    override fun getItemViewType(position: Int): Int {
        return if (displayItems.getOrNull(position) is ContactSection) VIEW_TYPE_SECTION else viewType
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        when (val item = displayItems[position]) {
            is ContactSection -> {
                bindSectionView(holder.itemView, item)
                // MyRecyclerView's drag-select resolves positions via itemView.tag — every row needs it.
                bindViewHolder(holder)
            }

            is Contact -> {
                val allowLongClick = location != LOCATION_INSERT_OR_EDIT
                holder.bindView(item, true, allowLongClick) { itemView, layoutPosition ->
                    setupView(itemView, item, holder)
                }
                bindViewHolder(holder)
            }
        }
    }

    override fun getItemCount() = displayItems.size

    /** Whether the display position holds a section header (full-width in the grid). */
    fun isSectionAt(position: Int) = displayItems.getOrNull(position) is ContactSection

    private fun getItemWithKey(key: Int): Contact? = contactItems.firstOrNull { it.id == key }

    fun updateItems(newItems: List<Contact>, highlightText: String = "") {
        if (newItems.hashCode() != contactItems.hashCode()) {
            contactItems = newItems.toMutableList()
            textToHighlight = highlightText
            rebuildDisplayList()
            notifyDataSetChanged()
            finishActMode()
        } else if (textToHighlight != highlightText) {
            textToHighlight = highlightText
            rebuildDisplayList()
            notifyDataSetChanged()
        }
    }

    // Rebuild the flattened display list (and each row's grid cell) from the contacts. In grouped mode
    // contacts bucket under per-letter headers in their incoming (sorted) order; folded sections keep
    // only their header. While searching every section shows, so matches are never hidden by fold state.
    private fun rebuildDisplayList() {
        val items = ArrayList<Any>(contactItems.size)
        val cells = ArrayList<RowCell?>(contactItems.size)
        val columns = if (location == LOCATION_CONTACTS_TAB && !detailMode) config.contactsListColumns else 1

        if (!groupBySections) {
            contactItems.forEachIndexed { index, contact ->
                items.add(contact)
                cells.add(cellFor(index, contactItems.size, columns))
            }
        } else {
            // Order by section rank (kana rows あ〜わ, then A–Z, then ＃), then Japanese collation of
            // the kana-folded key — so readings and Latin names interleave correctly within a section.
            val collator = Collator.getInstance(Locale.JAPANESE)
            val keyed = contactItems.map { it to effectiveSortKey(it) }.sortedWith(
                compareBy<Pair<Contact, String>> { sectionRank(sectionTitleForSortKey(it.second)) }
                    .then(compareBy(collator) { foldKana(it.second) })
            )

            val sections = LinkedHashMap<String, MutableList<Contact>>()
            keyed.forEach { (contact, key) ->
                sections.getOrPut(sectionTitleForSortKey(key)) { mutableListOf() }.add(contact)
            }

            val expandedTitles = config.expandedContactSections
            val searching = textToHighlight.isNotEmpty()
            var previousExpanded = false
            sections.forEach { (title, members) ->
                val expanded = searching || expandedTitles.contains(title)
                items.add(ContactSection(title, members.size, expanded, showTopDivider = expanded || previousExpanded))
                cells.add(null)
                if (expanded) {
                    members.forEachIndexed { index, contact ->
                        items.add(contact)
                        cells.add(cellFor(index, members.size, columns, grouped = true))
                    }
                }
                previousExpanded = expanded
            }

            // An open section at the very bottom has no following header to close it — mark its last
            // row so the decoration draws the closing full-width line there.
            if (previousExpanded) {
                for (i in cells.indices.reversed()) {
                    val cell = cells[i] ?: break
                    if (!cell.lastRow) {
                        break
                    }
                    cells[i] = cell.copy(sectionClose = true)
                }
            }
        }

        displayItems = items
        cellInfos = cells
    }

    private fun cellFor(index: Int, sectionSize: Int, columns: Int, grouped: Boolean = false): RowCell {
        val lastRowStart = ((sectionSize + columns - 1) / columns - 1) * columns
        return RowCell(
            column = index % columns,
            lastRow = index >= lastRowStart,
            // Only grouped sections draw the row gap + divider above their first row (below the header);
            // the flat list keeps its edge-to-edge start.
            firstRow = grouped && index < columns,
        )
    }

    // The name the global sort setting points at — the same derivation the letter fast-scroller uses.
    private fun sortNameFor(contact: Contact): String {
        val sorting = config.sorting
        val name = when {
            contact.isABusinessContact() -> contact.getFullCompany()
            sorting and SORT_BY_SURNAME != 0 && contact.surname.isNotEmpty() -> contact.surname
            sorting and SORT_BY_MIDDLE_NAME != 0 && contact.middleName.isNotEmpty() -> contact.middleName
            sorting and SORT_BY_FIRST_NAME != 0 && contact.firstName.isNotEmpty() -> contact.firstName
            startNameWithSurname -> contact.surname
            else -> contact.firstName
        }
        return name.ifEmpty { contact.getNameToDisplay() }
    }

    // What the grouped list actually sorts and buckets by: the contact's sort-field override when set
    // (and non-empty), else its reading (フリガナ), else the name per the global sort setting.
    private fun effectiveSortKey(contact: Contact): String {
        val reading = readingOf(contact)
        val overrideValue = when (config.getSortField(sortFieldKeyFor(contact))) {
            SORT_FIELD_READING -> reading
            SORT_FIELD_NICKNAME -> contact.nickname
            SORT_FIELD_ORGANIZATION -> contact.getFullCompany()
            else -> ""
        }
        return overrideValue.ifEmpty { reading.ifEmpty { sortNameFor(contact) } }
    }

    private fun bindSectionView(view: View, section: ContactSection) {
        val headerColor = activity.themeColor(ThemeSlot.SECTION_HEADER)
        // Bold + double size by default; an explicit slot weight/size (settable on the UI page) wins.
        val baseStyle = if (config.getFontWeight(ThemeSlot.SECTION_HEADER.key) == 0) Typeface.BOLD else Typeface.NORMAL
        view.findViewById<TextView>(R.id.section_fold_indicator).apply {
            text = if (section.expanded) UNFOLDED_INDICATOR else FOLDED_INDICATOR
            setTextColor(headerColor)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * SECTION_HEADER_SCALE)
            applyThemeFont(ThemeSlot.SECTION_HEADER, baseStyle)
            // Slightly larger than the letter, whatever size the slot resolves to (textSize is px here).
            setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize * INDICATOR_SCALE)
        }
        view.findViewById<TextView>(R.id.section_title).apply {
            text = "${section.title} (${section.count})"
            setTextColor(headerColor)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * SECTION_HEADER_SCALE)
            applyThemeFont(ThemeSlot.SECTION_HEADER, baseStyle)
        }
        val paddingPx = (config.contactsSectionPadding * activity.resources.displayMetrics.density).toInt()
        view.findViewById<View>(R.id.section_content).updateLayoutParams<LinearLayout.LayoutParams> {
            topMargin = paddingPx
            bottomMargin = paddingPx
        }
        applyLine(
            view.findViewById(R.id.section_underline),
            activity.themeColor(ThemeSlot.SECTION_UNDERLINE),
            config.contactsSectionUnderlineThickness,
        )
        // Full-width separators only frame unfolded content (see ContactSection.showTopDivider).
        applyLine(
            view.findViewById(R.id.section_divider),
            activity.themeColor(ThemeSlot.SECTION_DIVIDER),
            if (section.showTopDivider) config.contactsSectionDividerThickness else 0,
        )
        view.setOnClickListener { toggleSection(section.title) }
    }

    // A plain View drawn as a line: colored, [thicknessDp] tall, hidden entirely at 0.
    private fun applyLine(line: View, color: Int, thicknessDp: Int) {
        if (thicknessDp <= 0) {
            line.beGone()
            return
        }
        line.beVisible()
        line.setBackgroundColor(color)
        line.updateLayoutParams { height = (thicknessDp * activity.resources.displayMetrics.density).toInt() }
    }

    // Flip one section's persisted fold state. Inert while searching — the search view forces every
    // section open, so a toggle would silently change what shows after the search closes.
    private fun toggleSection(title: String) {
        if (textToHighlight.isNotEmpty()) {
            return
        }
        val expanded = config.expandedContactSections.toMutableSet()
        if (!expanded.add(title)) {
            expanded.remove(title)
        }
        config.expandedContactSections = expanded
        rebuildDisplayList()
        notifyDataSetChanged()
    }

    private fun editContact() {
        val contact = getItemWithKey(selectedKeys.first()) ?: return
        activity.editContact(contact, config.mergeDuplicateContacts)
    }

    private fun askConfirmDelete() {
        val itemsCnt = selectedKeys.size
        val items = if (itemsCnt == 1) {
            "\"${getSelectedItems().first().getNameToDisplay()}\""
        } else {
            resources.getQuantityString(org.fossify.commons.R.plurals.delete_contacts, itemsCnt, itemsCnt)
        }

        val baseString = org.fossify.commons.R.string.deletion_confirmation
        val question = String.format(resources.getString(baseString), items)

        ConfirmationDialog(activity, question) {
            deleteContacts()
        }
    }

    private fun deleteContacts() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val contactsToRemove = getSelectedItems()
        val positions = getSelectedItemPositions()
        contactItems.removeAll(contactsToRemove)

        ContactsHelper(activity).getContacts(true) { allContacts ->
            ensureBackgroundThread {
                ContactsHelper(activity).deleteContacts(contactsToRemove
                    .flatMap { contactToRemove -> allContacts.filter {
                        (config.mergeDuplicateContacts || it.id == contactToRemove.id) && (it.getHashToCompare() == contactToRemove.getHashToCompare())
                    } }
                    .toMutableList() as ArrayList<Contact>)

                activity.runOnUiThread {
                    if (contactItems.isEmpty()) {
                        refreshListener?.refreshContacts(ALL_TABS_MASK)
                        finishActMode()
                    } else {
                        onContactsRemoved(positions)
                        refreshListener?.refreshContacts(TAB_CONTACTS or TAB_FAVORITES)
                    }
                }
            }
        }
    }

    // used for removing contacts from groups or favorites, not deleting actual contacts
    private fun removeContacts() {
        val contactsToRemove = getSelectedItems()
        val positions = getSelectedItemPositions()
        contactItems.removeAll(contactsToRemove)

        if (location == LOCATION_FAVORITES_TAB) {
            ContactsHelper(activity).removeFavorites(contactsToRemove)
            if (contactItems.isEmpty()) {
                refreshListener?.refreshContacts(TAB_FAVORITES)
                finishActMode()
            } else {
                onContactsRemoved(positions)
            }
        } else if (location == LOCATION_GROUP_CONTACTS) {
            removeListener?.removeFromGroup(contactsToRemove)
            onContactsRemoved(positions)
        }
    }

    // contactItems changed: refresh the display list, then notify. Grouped mode rebinds everything
    // (header counts and section runs shift); the flat list keeps the per-position removal animation.
    private fun onContactsRemoved(positions: ArrayList<Int>) {
        rebuildDisplayList()
        if (groupBySections) {
            notifyDataSetChanged()
            finishActMode()
        } else {
            removeSelectedItems(positions)
        }
    }

    private fun addToFavorites() {
        ContactsHelper(activity).addFavorites(getSelectedItems())
        refreshListener?.refreshContacts(TAB_FAVORITES)
        finishActMode()
    }

    private fun addToGroup() {
        val items = ArrayList<RadioItem>()
        ContactsHelper(activity).getStoredGroups {
            it.forEach {
                items.add(RadioItem(it.id!!.toInt(), it.title))
            }
            items.add(RadioItem(NEW_GROUP_ID, activity.getString(R.string.create_new_group)))
            showGroupsPicker(items)
        }
    }

    private fun showGroupsPicker(radioItems: ArrayList<RadioItem>) {
        val selectedContacts = getSelectedItems()
        RadioGroupDialog(activity, radioItems, 0) {
            if (it as Int == NEW_GROUP_ID) {
                CreateNewGroupDialog(activity) {
                    ensureBackgroundThread {
                        activity.addContactsToGroup(selectedContacts, it.id!!.toLong())
                        refreshListener?.refreshContacts(TAB_GROUPS)
                    }
                    finishActMode()
                }
            } else {
                ensureBackgroundThread {
                    activity.addContactsToGroup(selectedContacts, it.toLong())
                    refreshListener?.refreshContacts(TAB_GROUPS)
                }
                finishActMode()
            }
        }
    }

    private fun shareContacts() {
        activity.shareContacts(getSelectedItems())
    }

    private fun sendSMSToContacts() {
        activity.sendSMSToContacts(getSelectedItems())
    }

    private fun sendEmailToContacts() {
        activity.sendEmailToContacts(getSelectedItems())
    }

    @SuppressLint("NewApi")
    private fun createShortcut() {
        val manager = activity.getSystemService(ShortcutManager::class.java)
        if (manager.isRequestPinShortcutSupported) {
            val contact = getSelectedItems().first()
            val drawable = resources.getDrawable(R.drawable.shortcut_contact).mutate()
            getShortcutImage(contact, drawable) {
                val intent = Intent(activity, ViewContactActivity::class.java)
                intent.action = Intent.ACTION_VIEW
                intent.putExtra(CONTACT_ID, contact.id)
                intent.putExtra(IS_PRIVATE, contact.isPrivate())
                intent.flags = intent.flags or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY

                val shortcut = ShortcutInfo.Builder(activity, contact.hashCode().toString())
                    .setShortLabel(contact.getNameToDisplay())
                    .setIcon(Icon.createWithBitmap(drawable.convertToBitmap()))
                    .setIntent(intent)
                    .build()

                manager.requestPinShortcut(shortcut, null)
            }
        }
    }

    private fun getShortcutImage(contact: Contact, drawable: Drawable, callback: () -> Unit) {
        val appIconColor = baseConfig.appIconColor
        (drawable as LayerDrawable).findDrawableByLayerId(R.id.shortcut_contact_background).applyColorFilter(appIconColor)
        val placeholderImage = AppCompatResources.getDrawable(activity, R.drawable.ic_unknown_contact)
        if (contact.photoUri.isEmpty() && contact.photo == null) {
            drawable.setDrawableByLayerId(R.id.shortcut_contact_image, placeholderImage)
            callback()
        } else {
            ensureBackgroundThread {
                val options = RequestOptions()
                    .signature(ObjectKey(contact.getSignatureKey()))
                    .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                    .error(placeholderImage)

                val size = activity.resources.getDimension(org.fossify.commons.R.dimen.shortcut_size).toInt()
                val itemToLoad: Any? = contact.photoUri.ifEmpty { contact.photo }

                val builder = Glide.with(activity)
                    .asDrawable()
                    .load(itemToLoad)
                    .apply(options)
                    .apply(RequestOptions.circleCropTransform())
                    .into(size, size)

                try {
                    val bitmap = builder.get()
                    drawable.setDrawableByLayerId(R.id.shortcut_contact_image, bitmap)
                } catch (e: Exception) {
                }

                activity.runOnUiThread {
                    callback()
                }
            }
        }
    }

    private fun getSelectedItems() = contactItems.filter { selectedKeys.contains(it.id) } as ArrayList<Contact>

    // The contact's stored default SIM slot (1 or 2), found across any of its numbers; 0 = none.
    private fun contactSimSlot(contact: Contact): Int {
        contact.phoneNumbers.forEach { phoneNumber ->
            val key = phoneNumber.normalizedNumber.ifEmpty { phoneNumber.value }
            if (key.isNotEmpty()) {
                val slot = config.getSimSlot(key)
                if (slot == 1 || slot == 2) {
                    return slot
                }
            }
        }
        return 0
    }

    // Long-press ➜ CAB ➜ "Set default SIM for contact": pick SIM 1 / SIM 2 / None for every number the
    // contact has. The Phone fork reads this (via the content provider) to choose the SIM, incl. Android Auto.
    private fun setDefaultSim() {
        val contact = getSelectedItems().firstOrNull() ?: return
        SetDefaultSimDialog(activity, contactSimSlot(contact)) { slot ->
            contact.phoneNumbers.forEach { phoneNumber ->
                val key = phoneNumber.normalizedNumber.ifEmpty { phoneNumber.value }
                if (key.isNotEmpty()) {
                    config.setSimSlot(key, slot)
                }
            }
            finishActMode()
            notifyDataSetChanged()
        }
    }

    // Overlay a small SIM badge on the contact photo: SIM 1 red (bottom-start), SIM 2 blue (bottom-end),
    // number in yellow. Rebuilt each bind so recycled rows never show a stale badge.
    private fun setupSimBadge(view: View, contact: Contact) {
        val frame = view.findViewById<ConstraintLayout>(org.fossify.commons.R.id.item_contact_frame) ?: return
        frame.findViewById<View>(R.id.sim_badge)?.let { frame.removeView(it) }

        if (!showContactThumbnails) {
            return
        }
        val slot = contactSimSlot(contact)
        if (slot != 1 && slot != 2) {
            return
        }

        val badge = LayoutInflater.from(activity).inflate(R.layout.sim_badge, frame, false)
        badge.findViewById<ImageView>(R.id.sim_badge_icon)
            .applyColorFilter(if (slot == 1) SIM1_BADGE_COLOR else SIM2_BADGE_COLOR)
        badge.findViewById<TextView>(R.id.sim_badge_number).text = slot.toString()
        badge.layoutParams = ConstraintLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomToBottom = org.fossify.commons.R.id.item_contact_image
            if (slot == 1) {
                startToStart = org.fossify.commons.R.id.item_contact_image
            } else {
                endToEnd = org.fossify.commons.R.id.item_contact_image
            }
        }
        frame.addView(badge)
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing) {
            // Section-header rows have no contact image — guard, or recycling one NPEs in Glide.
            holder.itemView.findViewById<ImageView>(org.fossify.commons.R.id.item_contact_image)?.let {
                Glide.with(activity).clear(it)
            }
        }
    }

    private fun setupView(view: View, contact: Contact, holder: ViewHolder) {
        view.apply {
            setupViewBackground(activity)
            findViewById<ConstraintLayout>(org.fossify.commons.R.id.item_contact_frame)?.isSelected = selectedKeys.contains(contact.id)
            if (viewType == VIEW_TYPE_GRID) {
                setupGridText(this, contact)
            } else {
                setupCustomFields(this, contact)
            }

            findViewById<ImageView>(org.fossify.commons.R.id.item_contact_image).apply {
                beVisibleIf(showContactThumbnails)
                if (viewType == VIEW_TYPE_LIST) {
                    updateLayoutParams {
                        width = thumbnailSizePx
                        height = thumbnailSizePx
                    }
                }
                if (profileIconClick != null && viewType != VIEW_TYPE_GRID) {
                    setBackgroundResource(R.drawable.selector_clickable_circle)
                    setOnClickListener {
                        if (!actModeCallback.isSelectable) {
                            profileIconClick.invoke(contact)
                        } else {
                            holder.viewClicked(contact)
                        }
                    }
                    setOnLongClickListener {
                        holder.viewLongClicked()
                        true
                    }
                }
            }

            if (showContactThumbnails) {
                val placeholderImage = AppCompatResources.getDrawable(context, R.drawable.ic_unknown_contact)
                if (contact.photoUri.isEmpty() && contact.photo == null) {
                    findViewById<ImageView>(org.fossify.commons.R.id.item_contact_image).setImageDrawable(placeholderImage)
                } else {
                    val options = RequestOptions()
                        .signature(ObjectKey(contact.getSignatureKey()))
                        .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                        .error(placeholderImage)
                        .centerCrop()

                    val itemToLoad: Any? = contact.photoUri.ifEmpty { contact.photo }

                    Glide.with(activity)
                        .load(itemToLoad)
                        .apply(options)
                        .apply(RequestOptions.circleCropTransform())
                        .into(findViewById(org.fossify.commons.R.id.item_contact_image))
                }
            }

            setupSimBadge(this, contact)

            val dragIcon = findViewById<ImageView>(org.fossify.commons.R.id.drag_handle_icon)
            if (enableDrag && textToHighlight.isEmpty()) {
                dragIcon.apply {
                    beVisibleIf(selectedKeys.isNotEmpty())
                    applyColorFilter(textColor)
                    setOnTouchListener { _, event ->
                        if (event.action == MotionEvent.ACTION_DOWN) {
                            startReorderDragListener?.requestDrag(holder)
                        }
                        false
                    }
                }
            } else {
                dragIcon.apply {
                    beGone()
                    setOnTouchListener(null)
                }
            }
        }
    }

    // Grid view keeps the stock name (+ optional number) rendering and its CONTACT_/FAVORITE_ slots.
    private fun setupGridText(view: View, contact: Contact) {
        val nameSlot = if (location == LOCATION_FAVORITES_TAB) ThemeSlot.FAVORITE_NAME else ThemeSlot.CONTACT_NAME
        view.findViewById<TextView>(org.fossify.commons.R.id.item_contact_name).apply {
            text = highlightedName(contact)
            alpha = 1f
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
            applyThemeFont(nameSlot)
        }

        setupGridNumber(view, contact)
    }

    private fun highlightedName(contact: Contact): CharSequence {
        val fullName = contact.getNameToDisplay()
        if (textToHighlight.isEmpty()) {
            return fullName
        }
        return if (fullName.normalizeString().contains(textToHighlight.normalizeString(), true)) {
            fullName.highlightTextPart(textToHighlight, properPrimaryColor)
        } else {
            fullName.highlightTextFromNumbers(textToHighlight, properPrimaryColor)
        }
    }

    private fun setupGridNumber(view: View, contact: Contact) {
        val numberView = view.findViewById<TextView>(org.fossify.commons.R.id.item_contact_number) ?: return
        val phoneNumberToUse = if (textToHighlight.isEmpty()) {
            contact.phoneNumbers.firstOrNull()
        } else {
            contact.phoneNumbers.firstOrNull { it.value.contains(textToHighlight) } ?: contact.phoneNumbers.firstOrNull()
        }
        val rawNumber = phoneNumberToUse?.value ?: ""
        val numberText = if (config.formatPhoneNumbers) rawNumber.formatPhoneNumber() else rawNumber
        val numberSlot = if (location == LOCATION_FAVORITES_TAB) ThemeSlot.FAVORITE_NUMBER else ThemeSlot.CONTACT_NUMBER
        numberView.apply {
            text = if (textToHighlight.isEmpty()) {
                numberText
            } else {
                numberText.highlightTextPart(textToHighlight, properPrimaryColor, false, true)
            }
            alpha = 1f
            setTextColor(activity.themeColor(numberSlot))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
            applyThemeFont(numberSlot)
        }
    }

    // List view: render the configured fields into the custom row, grouped into lines/columns.
    // Empty values are skipped, and a line with no non-empty value is dropped entirely.
    private fun setupCustomFields(view: View, contact: Contact) {
        val fieldsHolder = view.findViewById<LinearLayout>(R.id.item_contact_fields_holder) ?: return
        fieldsHolder.removeAllViews()
        contactRowLines.forEach { lineFields ->
            val columns = lineFields.mapNotNull { field ->
                val text = field.extract(contact, activity)
                if (text.isEmpty()) null else field to text
            }
            if (columns.isEmpty()) {
                return@forEach
            }
            val lineView = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
                )
            }
            columns.forEachIndexed { index, (field, text) ->
                if (index > 0) {
                    lineView.addView(buildSpacerView())
                }
                lineView.addView(buildFieldView(field, text, isLast = index == columns.lastIndex))
            }
            fieldsHolder.addView(lineView)
        }

        if (detailMode) {
            lastCallFor(contact)?.let {
                fieldsHolder.addView(buildEventLineView(CALL_GLYPH, it, ThemeSlot.DETAIL_CALL))
            }
            lastMessageFor(contact)?.let {
                fieldsHolder.addView(buildEventLineView(SMS_GLYPH, it, ThemeSlot.DETAIL_SMS))
            }
        }
    }

    // A 詳 info line: leading glyph (☎ / ✉), a direction arrow colored by kind (incoming blue,
    // outgoing green, missed red), then the timestamp in the configured format.
    private fun buildEventLineView(glyph: String, event: LastEvent, slot: ThemeSlot): TextView {
        val arrow = if (event.incoming) INCOMING_ARROW else OUTGOING_ARROW
        val arrowColor = when {
            event.missed -> DETAIL_MISSED_COLOR
            event.incoming -> DETAIL_INCOMING_COLOR
            else -> DETAIL_OUTGOING_COLOR
        }
        val line = SpannableString("$glyph $arrow ${event.timestamp.formatDetailTime(activity)}")
        val arrowStart = glyph.length + 1
        line.setSpan(
            ForegroundColorSpan(arrowColor),
            arrowStart,
            arrowStart + arrow.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        return TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            text = line
            setTextColor(activity.themeColor(slot))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
            applyThemeFont(slot)
        }
    }

    // Columns flow left-to-right: every column is wrap_content except the last, which takes the remaining
    // width (so it ellipsizes rather than overflowing). Separation between columns is the spacer view.
    private fun buildFieldView(field: RowField, text: String, isLast: Boolean): TextView {
        return TextView(activity).apply {
            layoutParams = if (isLast) {
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            } else {
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
                )
            }
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            this.text = when {
                textToHighlight.isEmpty() -> text
                field == RowField.PHONE -> text.highlightTextFromNumbers(textToHighlight, properPrimaryColor)
                else -> text.highlightTextPart(textToHighlight, properPrimaryColor)
            }
            setTextColor(activity.themeColor(field.slot))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
            applyThemeFont(field.slot)
        }
    }

    // The configurable separator drawn between two columns on the same line (default: a comma).
    private fun buildSpacerView(): TextView {
        return TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                marginEnd = activity.resources.getDimensionPixelSize(org.fossify.commons.R.dimen.small_margin)
            }
            maxLines = 1
            text = config.contactsListColumnSpacer
            setTextColor(activity.themeColor(ThemeSlot.COLUMN_SPACER))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
            applyThemeFont(ThemeSlot.COLUMN_SPACER)
        }
    }

    // Apply the configurable row spacing + divider to the list (idempotent: drop any prior instance first).
    // List view only — grid keeps its own cell spacing.
    private fun setupRowDecoration() {
        for (i in recyclerView.itemDecorationCount - 1 downTo 0) {
            if (recyclerView.getItemDecorationAt(i) is ContactsRowDecoration) {
                recyclerView.removeItemDecorationAt(i)
            }
        }
        if (viewType != VIEW_TYPE_LIST) {
            return
        }
        val density = activity.resources.displayMetrics.density
        val columns = if (location == LOCATION_CONTACTS_TAB && !detailMode) config.contactsListColumns else 1
        val spacingPx = (config.contactsListSpacing * density).toInt()
        val hDividerPx = (config.contactsListDividerThickness * density).toInt()
        val vDividerPx = (config.contactsListVerticalDividerThickness * density).toInt()
        val sectionLinePx = if (groupBySections) (config.contactsSectionDividerThickness * density).toInt() else 0
        recyclerView.addItemDecoration(
            ContactsRowDecoration(
                columns,
                spacingPx,
                hDividerPx,
                activity.themeColor(ThemeSlot.CONTACT_DIVIDER),
                vDividerPx,
                activity.themeColor(ThemeSlot.CONTACT_VDIVIDER),
                sectionLinePx,
                activity.themeColor(ThemeSlot.SECTION_DIVIDER),
            ) { position -> cellInfos.getOrNull(position) }
        )
    }

    private fun buildContactRowLines(): List<List<RowField>> {
        val lines = ArrayList<MutableList<RowField>>()
        ContactsListConfig.parse(config.contactsListFields).filter { it.checked }.forEach { entry ->
            if (!entry.sameLine || lines.isEmpty()) {
                lines.add(mutableListOf(entry.field))
            } else {
                lines.last().add(entry.field)
            }
        }
        return lines
    }

    override fun onChange(position: Int) = (displayItems.getOrNull(position) as? Contact)?.getBubbleText() ?: ""

    override fun onRowMoved(fromPosition: Int, toPosition: Int) {
        activity.config.isCustomOrderSelected = true

        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(contactItems, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(contactItems, i, i - 1)
            }
        }

        // Drag-reorder only exists on the flat Favorites list, where the display list mirrors contactItems.
        rebuildDisplayList()
        notifyItemMoved(fromPosition, toPosition)
    }

    override fun onRowSelected(myViewHolder: ViewHolder?) {}

    override fun onRowClear(myViewHolder: ViewHolder?) {
        onDragEndListener?.invoke()
    }

    companion object {
        // Adapter-local view type for the letter-section header rows; the contact rows use the
        // commons VIEW_TYPE_LIST / VIEW_TYPE_GRID values, so keep a safe distance from those.
        private const val VIEW_TYPE_SECTION = 1000

        // Fold-state glyphs on the section headers, styled like the header letter itself.
        private const val FOLDED_INDICATOR = "▸"
        private const val UNFOLDED_INDICATOR = "▾"

        // 詳 info-line glyphs: the event kind, then its direction.
        private const val CALL_GLYPH = "☎"
        private const val SMS_GLYPH = "✉"
        private const val INCOMING_ARROW = "↙"
        private const val OUTGOING_ARROW = "↗"

        // Default header size relative to the list font (66% of the original 2x look); a per-slot
        // font size set on the UI page overrides it entirely.
        private const val SECTION_HEADER_SCALE = 1.32f

        // The fold/unfold glyph renders slightly larger than the header letter it follows.
        private const val INDICATOR_SCALE = 1.2f
    }
}
