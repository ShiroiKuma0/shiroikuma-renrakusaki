package org.fossify.contacts.activities

import org.fossify.contacts.extensions.themeColor
import org.fossify.contacts.extensions.ThemeSlot
import android.graphics.drawable.GradientDrawable
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Intent
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.graphics.ColorUtils
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import org.fossify.commons.dialogs.CallConfirmationDialog
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.SelectAlarmSoundDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.contacts.*
import org.fossify.contacts.R
import org.fossify.contacts.databinding.*
import org.fossify.contacts.dialogs.ChangeVisibilityDialog
import org.fossify.contacts.dialogs.ChooseSocialDialog
import org.fossify.contacts.dialogs.ManageVisibleFieldsDialog
import org.fossify.contacts.dialogs.pickRinAccount
import org.fossify.contacts.extensions.*
import org.fossify.contacts.helpers.*
import java.util.Locale

// The route mark inside a call pill (dp / sp).
private const val ROUTE_BADGE_SIZE_DP = 26
private const val ROUTE_BADGE_PAD_DP = 6
private const val ROUTE_BADGE_RADIUS_DP = 7f
private const val ROUTE_BADGE_TEXT_SP = 15f

// A call pill: mark, gap, label.
private const val ACTION_PILL_PAD_START_DP = 11f
private const val ACTION_PILL_PAD_END_DP = 16f
private const val ACTION_PILL_PAD_V_DP = 8f
private const val ACTION_PILL_GAP_DP = 8f
private const val ACTION_PILL_TEXT_SP = 16f
private const val ACTION_PILL_LABEL_MAX_DP = 150f

// The tiles of the contact card (dp), and their text sizes as multiples of the app's text size.
private const val TILE_RADIUS_DP = 22f
private const val TILE_STROKE_DP = 1.5f
private const val TILE_GAP_DP = 10f
private const val TILE_EDGE_DP = 7f
private const val TILE_PAD_H_DP = 16f
private const val TILE_PAD_V_DP = 13f
private const val LINE_PAD_V_DP = 4f
private const val WATERMARK_SIZE_DP = 96f
private const val WATERMARK_OUT_END_DP = 14f
private const val WATERMARK_OUT_BOTTOM_DP = 18f
private const val WATERMARK_ALPHA = 0.13f
private const val TWO_COLUMN_MIN_WIDTH_DP = 520
private const val PHOTO_ASPECT_W = 3
private const val PHOTO_ASPECT_H = 4
private const val STAR_SIZE_DP = 40f
private const val STAR_PAD_DP = 6f
private const val CHIP_TOP_DP = 8f
private const val CHIP_ICON_DP = 15f
private const val CHIP_ICON_GAP_DP = 6f
private const val CHIP_PAD_H_DP = 12f
private const val CHIP_PAD_V_DP = 4f
private const val SOURCE_ICON_DP = 32f
private const val PERCENT = 100f
private const val CAPTION_SCALE = 0.78f
private const val CAPTION_LETTER_SPACING = 0.08f
private const val LABEL_SCALE = 0.85f
private const val VALUE_SCALE = 1.15f
private const val TITLE_SCALE = 1.35f
private const val NUMBER_SCALE = 1.6f
private const val NAME_SCALE = 1.7f
private const val CAPTION_SEPARATOR = " · "

class ViewContactActivity : ContactActivity() {
    private var isViewIntent = false
    private var wasEditLaunched = false
    private var duplicateContacts = ArrayList<Contact>()
    private var contactSources = ArrayList<ContactSource>()
    private var showFields = 0
    private var fullContact: Contact? = null    // contact with all fields filled from duplicates
    private var duplicateInitialized = false
    private val mergeDuplicate: Boolean get() = config.mergeDuplicateContacts
    private val binding by viewBinding(ActivityViewContactBinding::inflate)

    companion object {
        private const val COMPARABLE_PHONE_NUMBER_LENGTH = 9
        private const val CALL_NUMBER_LABEL_SEPARATOR = " · "

        // Kinds with their own fields on this screen; a kept row of one of them only carries extra columns.
        private val MODELLED_KINDS = setOf(
            CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
            CommonDataKinds.Email.CONTENT_ITEM_TYPE, CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE,
            CommonDataKinds.Im.CONTENT_ITEM_TYPE, CommonDataKinds.Event.CONTENT_ITEM_TYPE,
            CommonDataKinds.Website.CONTENT_ITEM_TYPE, CommonDataKinds.Nickname.CONTENT_ITEM_TYPE,
            CommonDataKinds.Note.CONTENT_ITEM_TYPE, CommonDataKinds.Organization.CONTENT_ITEM_TYPE,
            CommonDataKinds.Relation.CONTENT_ITEM_TYPE, CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.contactAppbar.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        binding.contactPhotoBig.setBackgroundColor(themeColor(ThemeSlot.PHOTO_SCRIM))

        setupEdgeToEdge(padBottomSystem = listOf(binding.contactScrollview))

        showFields = getFieldsToShow()
        setupMenu()
    }

    override fun onResume() {
        super.onResume()
        isViewIntent = intent.action == ContactsContract.QuickContact.ACTION_QUICK_CONTACT || intent.action == Intent.ACTION_VIEW
        if (isViewIntent) {
            handlePermission(PERMISSION_READ_CONTACTS) {
                if (it) {
                    ensureBackgroundThread {
                        initContact()
                    }
                } else {
                    toast(org.fossify.commons.R.string.no_contacts_permission)
                    finish()
                }
            }
        } else {
            ensureBackgroundThread {
                initContact()
            }
        }
    }

    override fun onBackPressedCompat(): Boolean {
        return if (binding.contactPhotoBig.alpha == 1f) {
            hideBigContactPhoto()
            true
        } else {
            false
        }
    }

    // "Visible to other apps" — private contacts only; checked when a system copy exists (SystemMirror).
    private fun refreshVisibleToOtherAppsItem() {
        val item = binding.contactToolbar.menu.findItem(R.id.visible_to_other_apps) ?: return
        val localId = contact?.takeIf { it.isPrivate() }?.id
        item.isVisible = localId != null
        if (localId != null) {
            ensureBackgroundThread {
                val mirrored = SystemMirror.isMirrored(applicationContext, localId)
                runOnUiThread { item.isChecked = mirrored }
            }
        }
    }

    private fun setupMenu() {
        binding.contactToolbar.menu.apply {
            findItem(R.id.share).setOnMenuItemClickListener {
                if (fullContact != null) {
                    shareContact(fullContact!!)
                }
                true
            }

            findItem(R.id.edit).setOnMenuItemClickListener {
                if (contact != null) {
                    launchEditContact(contact!!, false)
                }
                true
            }

            findItem(R.id.open_with).setOnMenuItemClickListener {
                openWith()
                true
            }

            findItem(R.id.delete).setOnMenuItemClickListener {
                deleteContactWithMergeLogic()
                true
            }

            findItem(R.id.visible_to_other_apps).setOnMenuItemClickListener { item ->
                val localId = contact?.takeIf { it.isPrivate() }?.id ?: return@setOnMenuItemClickListener true
                val visible = !item.isChecked
                ensureBackgroundThread {
                    val changed = SystemMirror.setVisible(applicationContext, listOf(localId), visible) > 0
                    // switched on here, the copy stays: it has no end (helpers/TemporaryVisibility)
                    org.fossify.contacts.helpers.TemporaryVisibility.forget(applicationContext, listOf(localId))
                    runOnUiThread {
                        if (changed) {
                            item.isChecked = visible
                            toast(if (visible) R.string.now_visible_to_other_apps else R.string.now_private_only)
                        } else {
                            toast(org.fossify.commons.R.string.unknown_error_occurred)
                        }
                    }
                }
                true
            }

            findItem(R.id.manage_visible_fields).setOnMenuItemClickListener {
                ManageVisibleFieldsDialog(this@ViewContactActivity) {
                    showFields = getFieldsToShow()
                    ensureBackgroundThread {
                        initContact()
                    }
                }
                true
            }
        }

        binding.contactToolbar.setNavigationOnClickListener {
            finish()
        }
    }

    // When the setting is on, ignore the "Manage shown contact fields" mask and show every field a contact has.
    private fun getFieldsToShow() =
        if (config.showAllFieldsWhenViewing) ALL_CONTACT_FIELDS else config.showContactFields

    private fun initContact() {
        var wasLookupKeyUsed = false
        var contactId: Int
        try {
            contactId = intent.getIntExtra(CONTACT_ID, 0)
        } catch (e: Exception) {
            return
        }

        if (contactId == 0 && isViewIntent) {
            val data = intent.data
            if (data != null) {
                val rawId = if (data.path!!.contains("lookup")) {
                    val lookupKey = getLookupKeyFromUri(data)
                    if (lookupKey != null) {
                        contact = ContactsHelper(this).getContactWithLookupKey(lookupKey)
                        fullContact = contact
                        wasLookupKeyUsed = true
                    }

                    getLookupUriRawId(data)
                } else {
                    getContactUriRawId(data)
                }

                if (rawId != -1) {
                    contactId = rawId
                }
            }
        }

        if (contactId != 0 && !wasLookupKeyUsed) {
            contact = ContactsHelper(this).getContactWithId(contactId, intent.getBooleanExtra(IS_PRIVATE, false))
            fullContact = contact

            if (contact == null) {
                if (!wasEditLaunched) {
                    toast(org.fossify.commons.R.string.unknown_error_occurred)
                }
                finish()
            } else {
                runOnUiThread {
                    gotContact()
                }
            }
        } else {
            if (contact == null) {
                finish()
            } else {
                runOnUiThread {
                    gotContact()
                }
            }
        }
    }

    private fun gotContact() {
        if (isDestroyed || isFinishing) {
            return
        }

        binding.contactScrollview.beVisible()
        setupPhoto()
        setupViewContact()

        binding.contactToolbar.menu.findItem(R.id.open_with).isVisible = contact?.isPrivate() == false
        refreshVisibleToOtherAppsItem()
        // The overflow popup draws its titles in the platform's white otherwise — same slot as every
        // other menu in the app.
        binding.contactToolbar.menu.colorItemTitles(themeColor(ThemeSlot.SEARCH_MENU_TEXT))
        applyPhotoOverlayIcons(binding.contactToolbar)
    }

    private fun setupViewContact() {
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        setupReading()

        ContactsHelper(this).getContactSources {
            contactSources = it
            runOnUiThread {
                setupContactDetails()
            }
        }

        initializeDuplicateContacts {
            duplicateInitialized = true
            setupContactDetails()
        }
    }

    // ---- The card: tiles ------------------------------------------------------------------------------
    //
    // Every kind of information is a tile of its own — an outlined, faintly tinted box in the colour of its
    // kind (ThemeSlot.CARD_*), with a caption saying what it holds and a large faint icon in its corner.
    // The photo (whole, never cropped) and the name head the card, each number follows as a full-width tile
    // with its one-touch call pills, the other fields pack two to a row where the screen is wide enough,
    // and the call history closes it. The whole card is rebuilt whenever the duplicates or sources arrive.

    private class Tile(val root: FrameLayout, val body: LinearLayout, val color: Int)

    private var readingText = ""
    private var readingView: TextView? = null
    private var ringtoneView: TextView? = null
    private var sourceCount = 0

    private fun dp(value: Float) = (value * resources.displayMetrics.density).toInt()

    private val twoColumns: Boolean get() = resources.configuration.screenWidthDp >= TWO_COLUMN_MIN_WIDTH_DP

    private fun tileGround(color: Int) = GradientDrawable().apply {
        cornerRadius = dp(TILE_RADIUS_DP).toFloat()
        setColor(ColorUtils.blendARGB(getProperBackgroundColor(), color, config.cardTileFill / PERCENT))
        setStroke(dp(TILE_STROKE_DP).coerceAtLeast(1), color)
    }

    private fun tileMargins(params: ViewGroup.MarginLayoutParams) {
        val gap = dp(TILE_GAP_DP / 2)
        params.setMargins(gap, gap, gap, gap)
    }

    private fun tile(slot: ThemeSlot, caption: String, icon: Int?): Tile {
        val color = themeColor(slot)
        val root = FrameLayout(this).apply {
            background = tileGround(color)
            clipToOutline = true
        }
        if (icon != null && config.cardWatermarks) {
            val mark = ImageView(this).apply {
                setImageDrawable(resources.getColoredDrawableWithColor(icon, color))
                alpha = WATERMARK_ALPHA
            }
            val size = dp(WATERMARK_SIZE_DP)
            root.addView(mark, FrameLayout.LayoutParams(size, size, Gravity.BOTTOM or Gravity.END).apply {
                marginEnd = -dp(WATERMARK_OUT_END_DP)
                bottomMargin = -dp(WATERMARK_OUT_BOTTOM_DP)
            })
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(TILE_PAD_H_DP), dp(TILE_PAD_V_DP), dp(TILE_PAD_H_DP), dp(TILE_PAD_V_DP))
        }
        root.addView(body, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        if (caption.isNotEmpty()) body.addView(captionView(caption, color))
        return Tile(root, body, color)
    }

    private fun captionView(caption: String, color: Int) = TextView(this).apply {
        text = caption.uppercase(Locale.getDefault())
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, getTextSize() * CAPTION_SCALE)
        setTypeface(typeface, Typeface.BOLD)
        letterSpacing = CAPTION_LETTER_SPACING
    }

    /**
     * One line of a tile: the value, what it is ([label]) beneath it in the quieter colour, and an optional
     * view at its end. A tap does [onClick]; a long-press copies [copyValue], or the value as it then reads.
     */
    @Suppress("LongParameterList")
    private fun Tile.line(
        value: CharSequence,
        label: String = "",
        scale: Float = VALUE_SCALE,
        bold: Boolean = false,
        end: View? = null,
        copyValue: String? = null,
        onClick: (() -> Unit)? = null,
    ): TextView {
        val activity = this@ViewContactActivity
        val valueView = TextView(activity).apply {
            text = value
            setTextColor(themeColor(ThemeSlot.CARD_VALUE))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, getTextSize() * scale)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(valueView)
        }
        if (label.isNotEmpty()) {
            column.addView(TextView(activity).apply {
                text = label
                setTextColor(themeColor(ThemeSlot.CARD_LABEL))
                setTextSize(TypedValue.COMPLEX_UNIT_PX, getTextSize() * LABEL_SCALE)
            })
        }
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(LINE_PAD_V_DP), 0, dp(LINE_PAD_V_DP))
            addView(column, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            end?.let { addView(it) }
            if (onClick != null) {
                background = rippleDrawable()
                setOnClickListener { onClick() }
            }
            setOnLongClickListener {
                copyToClipboard(copyValue ?: valueView.text.toString())
                true
            }
        }
        body.addView(row)
        return valueView
    }

    private fun setupContactDetails() {
        if (isFinishing || isDestroyed || contact == null) {
            return
        }

        refreshRinAccountsOnce()
        binding.contactHeadColumn.removeAllViews()
        binding.contactFields.removeAllViews()

        val numbers = mergedPhoneNumbers()
        addHeadTiles()
        if (showFields and SHOW_PHONE_NUMBERS_FIELD != 0) {
            numbers.forEach { number ->
                val phone = phoneTile(number)
                binding.contactFields.addView(
                    phone.root,
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also(::tileMargins),
                )
            }
        }
        addFieldTiles(
            listOfNotNull(
                emailsTile(), addressesTile(), imsTile(), eventsTile(), websitesTile(), moreTile(),
                groupsTile(), notesTile(), ringtoneTile(), sourcesTile(),
            )
        )
        setupCallHistory()
    }

    // Two tiles to a row on a wide screen, one on a narrow one; an odd last tile takes the whole row.
    private fun addFieldTiles(tiles: List<Tile>) {
        tiles.chunked(if (twoColumns) 2 else 1).forEach { chunk ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                isBaselineAligned = false
            }
            chunk.forEach {
                row.addView(it.root, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).also(::tileMargins))
            }
            binding.contactFields.addView(row)
        }
    }

    // The photo tile: the whole picture, fitted, never cropped to a strip. A tap enlarges it.
    private fun setupPhoto() {
        val edge = dp(TILE_EDGE_DP)
        val frame = dp(TILE_STROKE_DP).coerceAtLeast(1)
        binding.contactHolder.setPadding(edge, 0, edge, edge)
        binding.contactPhotoTile.apply {
            background = tileGround(themeColor(ThemeSlot.CARD_NAME))
            clipToOutline = true
            setPadding(frame, frame, frame, frame)
            val tileWidth = (resources.displayMetrics.widthPixels - 2 * edge) / 2 - dp(TILE_GAP_DP)
            layoutParams = (layoutParams as LinearLayout.LayoutParams).apply {
                height = minOf(resources.getDimensionPixelSize(R.dimen.top_contact_image_height), tileWidth * PHOTO_ASPECT_H / PHOTO_ASPECT_W)
                tileMargins(this)
            }
        }

        val shown = contact!!
        if (shown.photoUri.isEmpty() && shown.photo == null) {
            showPhotoPlaceholder(binding.contactPhoto)
            binding.contactPhoto.setOnClickListener(null)
            return
        }

        currentContactPhotoPath = shown.photoUri
        Glide.with(this)
            .load(shown.photo ?: shown.photoUri)
            .fitCenter()
            .error(R.drawable.ic_unknown_contact)
            .into(binding.contactPhoto)

        val options = RequestOptions()
            .transform(FitCenter(), RoundedCorners(resources.getDimension(org.fossify.commons.R.dimen.normal_margin).toInt()))
        Glide.with(this)
            .load(shown.photo ?: currentContactPhotoPath)
            .apply(options)
            .into(binding.contactPhotoBig)

        binding.contactPhoto.setOnClickListener {
            binding.contactPhotoBig.alpha = 0f
            binding.contactPhotoBig.beVisible()
            binding.contactPhotoBig.animate().alpha(1f).start()
        }
        binding.contactPhotoBig.setOnClickListener {
            hideBigContactPhoto()
        }
    }

    // Beside the photo: who it is (name, reading, favorite star, who can see it) and where they work.
    private fun addHeadTiles() {
        val shown = contact!!
        var displayName = shown.getNameToDisplay()
        if (shown.nickname.isNotEmpty()) {
            displayName += " (${shown.nickname})"
        }
        val showNameFields = showFields and SHOW_PREFIX_FIELD != 0 || showFields and SHOW_FIRST_NAME_FIELD != 0 ||
            showFields and SHOW_MIDDLE_NAME_FIELD != 0 || showFields and SHOW_SURNAME_FIELD != 0 || showFields and SHOW_SUFFIX_FIELD != 0
        val showName = displayName.isNotEmpty() && (showNameFields || shown.isABusinessContact())

        val name = tile(ThemeSlot.CARD_NAME, "", null)
        (name.body.layoutParams as FrameLayout.LayoutParams).gravity = Gravity.CENTER_VERTICAL
        val star = favoriteStar()
        val starSize = dp(STAR_SIZE_DP)
        if (showName) {
            star.layoutParams = LinearLayout.LayoutParams(starSize, starSize).apply { gravity = Gravity.TOP }
            name.line(displayName, scale = NAME_SCALE, bold = true, end = star)
        } else {
            name.body.addView(star, LinearLayout.LayoutParams(starSize, starSize))
        }
        readingView = TextView(this).apply {
            text = readingText
            setTextColor(themeColor(ThemeSlot.CARD_LABEL))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, getTextSize())
            beVisibleIf(showName && readingText.isNotEmpty())
            setOnLongClickListener {
                copyToClipboard(text.toString())
                true
            }
        }.also { name.body.addView(it) }
        if (config.showVisibilityRow) {
            name.body.addView(
                visibilityChip(shown),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(CHIP_TOP_DP)
                },
            )
        }

        binding.contactHeadColumn.addView(
            name.root, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).also(::tileMargins),
        )
        organizationTile()?.let {
            binding.contactHeadColumn.addView(
                it.root,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also(::tileMargins),
            )
        }
    }

    private fun favoriteStar() = ImageView(this).apply {
        val pad = dp(STAR_PAD_DP)
        setPadding(pad, pad, pad, pad)
        background = circleRippleDrawable()
        contentDescription = getString(R.string.toggle_favorite)
        tag = contact!!.starred
        setImageDrawable(getStarDrawable(tag == 1))

        setOnClickListener {
            val newIsStarred = if (tag == 1) 0 else 1
            ensureBackgroundThread {
                val contacts = arrayListOf(contact!!)
                if (newIsStarred == 1) {
                    ContactsHelper(context).addFavorites(contacts)
                } else {
                    ContactsHelper(context).removeFavorites(contacts)
                }
            }
            contact!!.starred = newIsStarred
            tag = contact!!.starred
            setImageDrawable(getStarDrawable(tag == 1))

            // Newly a favorite, and so one tap from being called: settle which of its numbers that
            // tap dials while the choice is being made, not from the car.
            if (newIsStarred == 1) {
                promptForCallNumbers(listOf(contact!!))
            }
        }

        setOnLongClickListener { toast(R.string.toggle_favorite); true; }
    }

    // Who can see this contact — System / Private / Private + visible — as a chip in the name tile; a tap
    // opens the same three-way menu as a long-press on the photo in the lists (dialogs/ChangeVisibilityDialog).
    private fun visibilityChip(shown: Contact): TextView {
        val color = themeColor(ThemeSlot.CARD_STORAGE)
        val chip = TextView(this).apply {
            text = getString(R.string.visibility_title)
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, getTextSize() * LABEL_SCALE)
            val lock = resources.getColoredDrawableWithColor(org.fossify.commons.R.drawable.ic_lock_vector, color)
            lock.setBounds(0, 0, dp(CHIP_ICON_DP), dp(CHIP_ICON_DP))
            setCompoundDrawablesRelative(lock, null, null, null)
            compoundDrawablePadding = dp(CHIP_ICON_GAP_DP)
            setPadding(dp(CHIP_PAD_H_DP), dp(CHIP_PAD_V_DP), dp(CHIP_PAD_H_DP), dp(CHIP_PAD_V_DP))
            background = pillDrawable(color, android.graphics.Color.TRANSPARENT, false)
            setOnClickListener {
                ChangeVisibilityDialog(this@ViewContactActivity, shown) { moved ->
                    if (moved) {
                        // It now lives under another id in the other store — there is nothing here to reload.
                        finish()
                    } else {
                        setupContactDetails()
                        refreshVisibleToOtherAppsItem()
                    }
                }
            }
        }
        ensureBackgroundThread {
            val members = ChangeVisibilityDialog.membersOf(this, shown)
            val state = ChangeVisibilityDialog.stateOfGroup(this, members)
            val end = if (state == ChangeVisibilityDialog.State.VISIBLE) ChangeVisibilityDialog.temporaryEndOf(this, members) else null
            val label = when (state) {
                ChangeVisibilityDialog.State.SYSTEM -> getString(R.string.card_visibility_system)
                ChangeVisibilityDialog.State.PRIVATE -> getString(R.string.card_visibility_private)
                ChangeVisibilityDialog.State.VISIBLE -> if (end == null) {
                    getString(R.string.card_visibility_visible)
                } else {
                    getString(R.string.card_visibility_temporary, android.text.format.DateFormat.getTimeFormat(this).format(java.util.Date(end)))
                }
                null -> getString(R.string.card_visibility_mixed)
            }
            runOnUiThread { chip.text = label }
        }
        return chip
    }

    // Fork: the name's reading (フリガナ), under the name it belongs to. Read straight from the provider
    // rather than from ContactExtras: this screen opens from other apps too, in a process where no
    // contacts refresh has ever run. A private contact carries its reading in Contact.extra.
    private fun setupReading() {
        readingText = ""
        val rawId = contact?.id ?: return
        if (rawId == 0 || !config.showReadingWhenViewing) {
            return
        }
        val isPrivate = contact!!.isPrivate()

        ensureBackgroundThread {
            val extra = contact?.extra
            val phonetic = if (isPrivate && extra != null) {
                PhoneticName(extra.phoneticFamily, extra.phoneticMiddle, extra.phoneticGiven)
            } else {
                loadPhoneticName(rawId)
            }
            val reading = phonetic?.takeIf { !it.isEmpty }?.let { orderedReading(it) }.orEmpty()
            if (reading.isEmpty()) {
                return@ensureBackgroundThread
            }

            runOnUiThread {
                if (isDestroyed || isFinishing) {
                    return@runOnUiThread
                }

                readingText = reading
                readingView?.apply {
                    text = reading
                    beVisible()
                }
            }
        }
    }

    // The parts in the order the name above is displayed in — surname first when that is how names are
    // shown here — so the reading lines up with the name rather than contradicting it.
    private fun orderedReading(phonetic: PhoneticName): String {
        val parts = if (config.startNameWithSurname) {
            listOf(phonetic.family, phonetic.given, phonetic.middle)
        } else {
            listOf(phonetic.given, phonetic.middle, phonetic.family)
        }

        return parts.filter { it.isNotEmpty() }.joinToString(" ")
    }

    private fun organizationTile(): Tile? {
        val shown = contact!!
        val organization = shown.organization
        val extra = shown.extra
        val company = tile(ThemeSlot.CARD_ORG, getString(R.string.company), org.fossify.commons.R.drawable.ic_business_vector)
        var lines = 0
        fun add(value: String, label: String = "", scale: Float = VALUE_SCALE) {
            if (value.isNotEmpty()) {
                company.line(value, label, scale)
                lines++
            }
        }
        if (showFields and SHOW_ORGANIZATION_FIELD != 0) {
            // a business contact's company is its name, in the tile above
            if (!shown.isABusinessContact()) add(organization.company, scale = TITLE_SCALE)
            add(organization.jobPosition)
        }
        add(extra.department, getString(R.string.more_department))
        add(extra.jobDescription, getString(R.string.more_job_description))
        add(extra.officeLocation, getString(R.string.more_office_location))
        add(extra.companyPhonetic, getString(R.string.more_company_phonetic))
        add(extra.symbol, getString(R.string.more_symbol))
        return company.takeIf { lines > 0 }
    }

    // ---- Phone numbers: one tile each, with its one-touch pills ----------------------------------------

    // The pills of a number, as set in UI settings → Contact card: each calls the number by one route
    // (SIM 1, SIM 2, the 臨 default, one 臨 account) or writes to it. A route whose app is not installed has
    // no pill. The pill of the contact's default route has the thick frame. On a 臨 pill a long-press opens
    // the callback menu when its account can set one up (Odorik), else — on the 臨 default pill — picks the
    // contact's own account.
    private fun phonePills(number: String): View? {
        val shown = fullContact ?: contact ?: return null
        val name = shown.getNameToDisplay()
        val default = contactDefaultRoute(shown)
        val hasDialer = getInstalledDialerAppPackage() != null
        val hasRin = Rindenwa.isInstalled(this)
        val simText = themeColor(ThemeSlot.ROUTE_SIM_TEXT)
        var mainGiven = false
        val pills = ArrayList<View>()
        config.phonePills.forEach { action ->
            val isRoute = action != PILL_ACTION_SMS
            if (isRoute && !config.routeBadges) return@forEach
            val main = isRoute && !mainGiven && isDefaultRoute(action, default, shown)
            val pill = when {
                action == PILL_ACTION_SMS -> {
                    val icon = ImageView(this).apply {
                        setImageDrawable(
                            resources.getColoredDrawableWithColor(org.fossify.commons.R.drawable.ic_sms_vector, themeColor(ThemeSlot.CARD_MESSAGE))
                        )
                        layoutParams = LinearLayout.LayoutParams(dp(ROUTE_BADGE_SIZE_DP.toFloat()), dp(ROUTE_BADGE_SIZE_DP.toFloat()))
                    }
                    actionPill(icon, getString(R.string.card_write), false) { launchSendSMSIntent(number) }
                }

                action == ROUTE_SIM1 && hasDialer ->
                    actionPill(routeBadge("1", themeColor(ThemeSlot.ROUTE_SIM1), simText), getString(R.string.sim_slot_1), main) {
                        callNumberWithSim(number, name, true)
                    }

                action == ROUTE_SIM2 && hasDialer ->
                    actionPill(routeBadge("2", themeColor(ThemeSlot.ROUTE_SIM2), simText), getString(R.string.sim_slot_2), main) {
                        callNumberWithSim(number, name, false)
                    }

                (action == ROUTE_RIN || action.startsWith(ROUTE_RIN_PREFIX)) && hasRin -> rinPill(action, shown, number, name, main)
                else -> null
            } ?: return@forEach
            if (main) mainGiven = true
            pills.add(pill)
        }
        return if (pills.isEmpty()) null else pillRow(*pills.toTypedArray())
    }

    private fun isDefaultRoute(action: String, default: String, shown: Contact): Boolean = when {
        default == ROUTE_OFF -> false
        action == default -> true
        action == ROUTE_RIN -> default.startsWith(ROUTE_RIN_PREFIX)
        action.startsWith(ROUTE_RIN_PREFIX) && default == ROUTE_RIN ->
            action.removePrefix(ROUTE_RIN_PREFIX).toIntOrNull() == effectiveRinAccount(shown)

        else -> false
    }

    // A 臨 pill: the account's own badge (in the colours 白い熊 gave it in Rindenwa) and its name there.
    private fun rinPill(action: String, shown: Contact, number: String, name: String, main: Boolean): View {
        val fixed = if (action == ROUTE_RIN) null else action.removePrefix(ROUTE_RIN_PREFIX).toIntOrNull()
        fun account() = fixed ?: effectiveRinAccount(shown)
        val now = account()
        val (ground, text) = rinBadgeColors(now)
        val known = Rindenwa.cachedAccounts(this).firstOrNull { if (now >= 0) it.number == now else it.isDefault }
        val label = known?.name?.takeIf { it.isNotBlank() } ?: getString(R.string.card_rindenwa)
        val pill = actionPill(routeBadge(rinBadgeFor(now), ground, text), label, main) {
            callNumberViaRindenwa(number, name, account())
        }
        pill.setOnLongClickListener {
            when {
                rinCanCallback(account()) -> showRinBadgeMenu(pill, shown, number, name, account(), ownAccount = fixed == null)
                fixed == null -> pickOwnRinAccount(shown)
                else -> copyToClipboard(number)
            }
            true
        }
        return pill
    }

    private fun pickOwnRinAccount(shown: Contact) {
        pickRinAccount(contactRinAccount(shown), getString(R.string.rin_account_use_default)) { account ->
            setContactRinAccount(shown, account)
            setupContactDetails()
        }
    }

    // The long-press menu of a 臨 pill whose account can set up a callback (Odorik): Callback first, then
    // the ordinary call the tap places, then — on the 臨 default pill — the contact's account picker.
    @Suppress("LongParameterList")
    private fun showRinBadgeMenu(anchor: View, shown: Contact, number: String, name: String, account: Int, ownAccount: Boolean) {
        androidx.appcompat.widget.PopupMenu(
            this, anchor, Gravity.END, androidx.appcompat.R.attr.actionOverflowMenuStyle, 0
        ).apply {
            menu.add(0, R.id.rin_callback, 0, R.string.rin_callback)
            menu.add(0, R.id.rin_call, 1, org.fossify.commons.R.string.call)
            if (ownAccount) menu.add(0, R.id.rin_set_account, 2, R.string.set_rin_account)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.rin_callback -> orderCallbackViaRindenwa(number, name, account)
                    R.id.rin_call -> callNumberViaRindenwa(number, name, account)
                    R.id.rin_set_account -> pickOwnRinAccount(shown)
                }
                true
            }
            show()
        }
    }

    // A call pill: outlined, never filled — thick frame and bold for the contact's default route.
    private fun actionPill(mark: View, label: String, main: Boolean, onClick: () -> Unit): LinearLayout {
        val accent = getProperPrimaryColor()
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ACTION_PILL_PAD_START_DP), dp(ACTION_PILL_PAD_V_DP), dp(ACTION_PILL_PAD_END_DP), dp(ACTION_PILL_PAD_V_DP))
            background = pillDrawable(accent, android.graphics.Color.TRANSPARENT, main)
            addView(mark)
            addView(TextView(context).apply {
                text = label
                setTextColor(accent)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, ACTION_PILL_TEXT_SP)
                if (main) setTypeface(typeface, Typeface.BOLD)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                maxWidth = dp(ACTION_PILL_LABEL_MAX_DP)
                setPadding(dp(ACTION_PILL_GAP_DP), 0, 0, 0)
            })
            setOnClickListener { onClick() }
        }
    }

    // The mark of a route inside its pill: 1 and 2 (SIM), 臨N (a Rindenwa account), on the route's own ground.
    private fun routeBadge(label: String, ground: Int, text: Int): TextView {
        val density = resources.displayMetrics.density
        return TextView(this).apply {
            this.text = label
            setTextColor(text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, ROUTE_BADGE_TEXT_SP)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            includeFontPadding = false
            minWidth = (ROUTE_BADGE_SIZE_DP * density).toInt()
            val pad = (ROUTE_BADGE_PAD_DP * density).toInt()
            setPadding(pad, 0, pad, 0)
            background = GradientDrawable().apply {
                cornerRadius = ROUTE_BADGE_RADIUS_DP * density
                setColor(ground)
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (ROUTE_BADGE_SIZE_DP * density).toInt())
        }
    }

    // Rindenwa's account list decides what a 臨 pill reads; ask for it once per visit and redraw on news.
    private var rinAccountsAsked = false

    private fun refreshRinAccountsOnce() {
        if (rinAccountsAsked || !config.routeBadges || !Rindenwa.isInstalled(this)) return
        rinAccountsAsked = true
        val appContext = applicationContext
        ensureBackgroundThread {
            val before = Rindenwa.cachedAccounts(appContext)
            val now = Rindenwa.refreshAccounts(appContext)
            if (now != null && now != before) {
                runOnUiThread { setupContactDetails() }
            }
        }
    }

    private fun mergedPhoneNumbers(): LinkedHashSet<PhoneNumber> {
        var phoneNumbers = contact!!.phoneNumbers.toMutableSet() as LinkedHashSet<PhoneNumber>

        if (mergeDuplicate) {
            duplicateContacts.forEach {
                phoneNumbers.addAll(it.phoneNumbers)
            }
        }

        if (duplicateInitialized) {
            val contactDefaultsNumbers = contact!!.phoneNumbers.filter { it.isPrimary }
            val duplicateContactsDefaultNumbers = duplicateContacts.flatMap { it.phoneNumbers }.filter { it.isPrimary }
            val defaultNumbers = (contactDefaultsNumbers + duplicateContactsDefaultNumbers).toSet()

            if (defaultNumbers.size > 1 && defaultNumbers.distinctBy { it.normalizedNumber }.size > 1) {
                phoneNumbers.forEach { it.isPrimary = false }
            } else if (defaultNumbers.size == 1) {
                if (mergeDuplicate) {
                    val defaultNumber = defaultNumbers.first()
                    val candidate = phoneNumbers.find { it.normalizedNumber == defaultNumber.normalizedNumber && !it.isPrimary }
                    candidate?.isPrimary = true
                } else {
                    duplicateContactsDefaultNumbers.forEach { defaultNumber ->
                        val candidate = phoneNumbers.find { it.normalizedNumber == defaultNumber.normalizedNumber && !it.isPrimary }
                        candidate?.isPrimary = true
                    }
                }
            }
        }

        phoneNumbers = phoneNumbers.distinctBy {
            if (it.normalizedNumber.length >= COMPARABLE_PHONE_NUMBER_LENGTH) {
                it.normalizedNumber.substring(it.normalizedNumber.length - COMPARABLE_PHONE_NUMBER_LENGTH)
            } else {
                it.normalizedNumber
            }
        }.toMutableSet() as LinkedHashSet<PhoneNumber>

        phoneNumbers = phoneNumbers.sortedBy { it.type }.toMutableSet() as LinkedHashSet<PhoneNumber>
        fullContact!!.phoneNumbers = phoneNumbers.toMutableList() as ArrayList<PhoneNumber>
        return phoneNumbers
    }

    // One number: what it is filed under (★ = the contact's default number), the number itself — a tap
    // calls it by the contact's default route, a long-press copies it — and its pills.
    private fun phoneTile(phoneNumber: PhoneNumber): Tile {
        val type = getPhoneNumberTypeText(phoneNumber.type, phoneNumber.label)
        var caption = getString(R.string.card_phone)
        if (type.isNotBlank()) caption += CAPTION_SEPARATOR + type
        if (phoneNumber.isPrimary) caption += " ★"
        val phone = tile(ThemeSlot.CARD_PHONE, caption, org.fossify.commons.R.drawable.ic_phone_vector)
        val shownNumber = if (config.formatPhoneNumbers) phoneNumber.value.formatPhoneNumber() else phoneNumber.value
        phone.line(shownNumber, scale = NUMBER_SCALE, bold = true, copyValue = phoneNumber.value) {
            if (config.showCallConfirmation) {
                CallConfirmationDialog(this, phoneNumber.value) {
                    startDefaultRouteCall(contact!!, phoneNumber.value)
                }
            } else {
                startDefaultRouteCall(contact!!, phoneNumber.value)
            }
        }
        phonePills(phoneNumber.value)?.let { phone.body.addView(it) }
        return phone
    }

    // ---- The other fields: one tile per kind -----------------------------------------------------------

    // a contact cannot have different emails per contact source. Such contacts are handled as separate ones, not duplicates of each other
    private fun emailsTile(): Tile? {
        val emails = contact!!.emails
        if (emails.isEmpty() || showFields and SHOW_EMAILS_FIELD == 0) return null
        val tile = tile(ThemeSlot.CARD_WEB, getString(R.string.emails), org.fossify.commons.R.drawable.ic_mail_vector)
        emails.forEach { email ->
            tile.line(email.value, getEmailTypeText(email.type, email.label)) { sendEmailIntent(email.value) }
        }
        return tile
    }

    private fun addressesTile(): Tile? {
        var addresses = contact!!.addresses.toMutableSet() as LinkedHashSet<Address>

        if (mergeDuplicate) {
            duplicateContacts.forEach {
                addresses.addAll(it.addresses)
            }
        }

        addresses = addresses.sortedBy { it.type }.toMutableSet() as LinkedHashSet<Address>
        fullContact!!.addresses = addresses.toMutableList() as ArrayList<Address>
        if (addresses.isEmpty() || showFields and SHOW_ADDRESSES_FIELD == 0) return null
        val tile = tile(ThemeSlot.CARD_WEB, getString(R.string.addresses), org.fossify.commons.R.drawable.ic_place_vector)
        addresses.forEach { address ->
            tile.line(address.value, getAddressTypeText(address.type, address.label)) { sendAddressIntent(address.value) }
        }
        return tile
    }

    private fun imsTile(): Tile? {
        var IMs = contact!!.IMs.toMutableSet() as LinkedHashSet<IM>

        if (mergeDuplicate) {
            duplicateContacts.forEach {
                IMs.addAll(it.IMs)
            }
        }

        IMs = IMs.sortedBy { it.type }.toMutableSet() as LinkedHashSet<IM>
        fullContact!!.IMs = IMs.toMutableList() as ArrayList<IM>
        if (IMs.isEmpty() || showFields and SHOW_IMS_FIELD == 0) return null
        val tile = tile(ThemeSlot.CARD_MESSAGE, getString(R.string.card_im), R.drawable.ic_im)
        IMs.forEach { tile.line(it.value, getIMTypeText(it.type, it.label)) }
        return tile
    }

    private fun eventsTile(): Tile? {
        var events = contact!!.events.toMutableSet() as LinkedHashSet<Event>

        if (mergeDuplicate) {
            duplicateContacts.forEach {
                events.addAll(it.events)
            }
        }

        events = events.sortedBy { it.type }.toMutableSet() as LinkedHashSet<Event>
        fullContact!!.events = events.toMutableList() as ArrayList<Event>
        if (events.isEmpty() || showFields and SHOW_EVENTS_FIELD == 0) return null
        val tile = tile(ThemeSlot.CARD_NOTE, getString(R.string.card_events), R.drawable.ic_cake_vector)
        events.forEach {
            val label = if (it.type == CommonDataKinds.Event.TYPE_CUSTOM && it.label.isNotEmpty()) it.label else getString(getEventTextId(it.type))
            val valueView = tile.line(it.value, label, copyValue = it.value)
            it.value.getDateTimeFromDateString(true, valueView)
        }
        return tile
    }

    private fun websitesTile(): Tile? {
        var websites = contact!!.websites.toMutableSet() as LinkedHashSet<String>

        if (mergeDuplicate) {
            duplicateContacts.forEach {
                websites.addAll(it.websites)
            }
        }

        websites = websites.sorted().toMutableSet() as LinkedHashSet<String>
        fullContact!!.websites = websites.toMutableList() as ArrayList<String>
        if (websites.isEmpty() || showFields and SHOW_WEBSITES_FIELD == 0) return null
        val tile = tile(ThemeSlot.CARD_WEB, getString(R.string.websites), org.fossify.commons.R.drawable.ic_link_vector)
        websites.forEach { url -> tile.line(url) { openWebsiteIntent(url) } }
        return tile
    }

    // Fork (sk11): the fields beyond Fossify's own (Contact.extra) that are not about the company —
    // relationships, internet-call addresses, send to voicemail, and any other data the contact holds,
    // read-only. (The organization details sit in the company tile.)
    private fun moreTile(): Tile? {
        val extra = contact!!.extra
        val tile = tile(ThemeSlot.CARD_NOTE, getString(R.string.more_other_data), org.fossify.commons.R.drawable.ic_label_vector)
        var lines = 0
        fun row(value: String, type: String, onClick: (() -> Unit)? = null) {
            if (value.isEmpty()) return
            tile.line(value, type, onClick = onClick)
            lines++
        }

        extra.relations.forEach {
            row(it.name, CommonDataKinds.Relation.getTypeLabel(resources, it.type, it.label).toString())
        }
        extra.sipAddresses.forEach { sip ->
            row(sip.value, CommonDataKinds.SipAddress.getTypeLabel(resources, sip.type, sip.label).toString()) {
                try {
                    startActivity(Intent(Intent.ACTION_CALL, Uri.fromParts("sip", sip.value, null)))
                } catch (e: Exception) {
                    showErrorToast(e)
                }
            }
        }
        if (extra.sendToVoicemail) row(getString(R.string.more_sent_to_voicemail), "")
        // Rows of kinds this app has no field for (another app's data, Identity…), shown as they are.
        extra.rows.filter { it.values.size > 1 && it.mimetype !in MODELLED_KINDS }.forEach { dataRow ->
            val value = dataRow.values[ContactsContract.Data.DATA1]?.takeIf { !it.startsWith("b64:") }.orEmpty()
            row(value, dataRow.mimetype.substringAfterLast('/').substringAfterLast('.'))
        }

        return tile.takeIf { lines > 0 }
    }

    private fun groupsTile(): Tile? {
        var groups = contact!!.groups.toMutableSet() as LinkedHashSet<Group>

        if (mergeDuplicate) {
            duplicateContacts.forEach {
                groups.addAll(it.groups)
            }
        }

        groups = groups.sortedBy { it.title }.toMutableSet() as LinkedHashSet<Group>
        fullContact!!.groups = groups.toMutableList() as ArrayList<Group>
        if (groups.isEmpty() || showFields and SHOW_GROUPS_FIELD == 0) return null
        val tile = tile(ThemeSlot.CARD_NOTE, getString(R.string.groups), org.fossify.commons.R.drawable.ic_people_vector)
        groups.forEach { tile.line(it.title) }
        return tile
    }

    private fun notesTile(): Tile? {
        val notes = contact!!.notes
        if (notes.isEmpty() || showFields and SHOW_NOTES_FIELD == 0) return null
        val tile = tile(ThemeSlot.CARD_NOTE, getString(org.fossify.commons.R.string.notes), org.fossify.commons.R.drawable.ic_label_vector)
        tile.line(notes)
        return tile
    }

    // Shown only for a contact with a ringtone of its own (or silenced); a tap picks another.
    private fun ringtoneTile(): Tile? {
        ringtoneView = null
        if (showFields and SHOW_RINGTONE_FIELD == 0) return null
        val ringtone = contact!!.ringtone
        val silent = ringtone?.isEmpty() == true || ringtone == SILENT
        val own = ringtone?.isNotEmpty() == true && ringtone != getDefaultRingtoneUri().toString()
        if (!silent && !own) return null

        val tile = tile(ThemeSlot.CARD_NOTE, getString(R.string.ringtone), org.fossify.commons.R.drawable.ic_bell_vector)
        ringtoneView = tile.line(getString(org.fossify.commons.R.string.no_sound)) {
            val ringtonePickerIntent = getRingtonePickerIntent()
            try {
                startActivityForResult(ringtonePickerIntent, INTENT_SELECT_RINGTONE)
            } catch (e: Exception) {
                val currentRingtone = contact!!.ringtone ?: getDefaultAlarmSound(RingtoneManager.TYPE_RINGTONE).uri
                SelectAlarmSoundDialog(this@ViewContactActivity,
                    currentRingtone,
                    AudioManager.STREAM_RING,
                    PICK_RINGTONE_INTENT_ID,
                    RingtoneManager.TYPE_RINGTONE,
                    true,
                    onAlarmPicked = {
                        ringtoneView?.text = it?.title
                        ringtoneUpdated(it?.uri)
                    },
                    onAlarmSoundDeleted = {}
                )
            }
        }
        if (!silent) systemRingtoneSelected(Uri.parse(ringtone))
        return tile
    }

    // Where the contact is stored; a tap edits that entry, and a messenger's icon opens its actions.
    private fun sourcesTile(): Tile? {
        sourceCount = 0
        if (showFields and SHOW_CONTACT_SOURCE_FIELD == 0) return null
        var sources = HashMap<Contact, String>()
        sources[contact!!] = getPublicContactSourceSync(contact!!.source, contactSources)

        if (mergeDuplicate) {
            duplicateContacts.forEach {
                sources[it] = getPublicContactSourceSync(it.source, contactSources)
            }
        }

        if (sources.size > 1) {
            sources = sources.toList().sortedBy { (key, value) -> value.lowercase(Locale.getDefault()) }.toMap() as LinkedHashMap<Contact, String>
        }
        sourceCount = sources.size

        val tile = tile(ThemeSlot.CARD_STORAGE, getString(R.string.contact_source), R.drawable.ic_account_box_vector)
        for ((key, value) in sources) {
            val messenger = when (value.lowercase(Locale.getDefault())) {
                WHATSAPP -> WHATSAPP_PACKAGE
                SIGNAL -> SIGNAL_PACKAGE
                VIBER -> VIBER_PACKAGE
                TELEGRAM -> TELEGRAM_PACKAGE
                THREEMA -> THREEMA_PACKAGE
                else -> null
            }
            val icon = messenger?.let {
                ImageView(this).apply {
                    setImageDrawable(getPackageDrawable(it))
                    layoutParams = LinearLayout.LayoutParams(dp(SOURCE_ICON_DP), dp(SOURCE_ICON_DP))
                    setOnClickListener { showSocialActions(key.id) }
                }
            }
            tile.line(if (value == "") getString(R.string.phone_storage) else value, end = icon, copyValue = value) {
                launchEditContact(key, true)
            }
        }
        return tile
    }

    private fun launchEditContact(contact: Contact, editExactContact: Boolean) {
        wasEditLaunched = true
        duplicateInitialized = false
        if (editExactContact) {
            editContact(contact)
        } else {
            editContact(contact, config.mergeDuplicateContacts)
        }
    }

    private fun openWith() {
        if (contact != null) {
            val uri = getContactPublicUri(contact!!)
            launchViewContactIntent(uri)
        }
    }

    // 発着信履歴 — the contact's latest calls under its fields, drawn exactly like denwa's Recents: its
    // day headings and its three-line row less the name (number · label with ✉ and ☎, then SIM badge,
    // arrow, time, duration and place), in denwa's own colours, fonts and formats
    // (served by denwa, helpers/DenwaCallLogStyle). setupContactDetails runs more than once while the
    // duplicates and sources arrive, so a newer load supersedes an older one.
    private var callHistoryGeneration = 0

    private fun setupCallHistory() {
        val holder = binding.contactCallsHolder
        val generation = ++callHistoryGeneration
        val numbers = fullContact?.phoneNumbers.orEmpty().toList()
        if (!config.contactCallHistory || numbers.isEmpty()) {
            holder.removeAllViews()
            holder.beGone()
            return
        }
        val limit = config.contactCallHistoryCount
        val text = getProperTextColor()
        val primary = getProperPrimaryColor()
        ensureBackgroundThread {
            val numberValues = numbers.map { it.value }
            val calls = withCallStamps(loadContactCalls(numberValues, limit), numberValues)
            val style = loadDenwaCallLogStyle(text, primary)
            val faces = CallHistoryFaces(
                date = denwaTypeface(style.dateFont),
                number = denwaTypeface(style.numberFont),
                duration = denwaTypeface(style.durationFont),
                country = denwaTypeface(style.countryFont),
                dayDate = denwaTypeface(style.dayDateFont, android.graphics.Typeface.BOLD),
            )
            runOnUiThread {
                if (generation == callHistoryGeneration && !isFinishing && !isDestroyed) {
                    showCallHistory(calls, numbers, style, faces)
                }
            }
        }
    }

    private class CallHistoryFaces(
        val date: android.graphics.Typeface,
        val number: android.graphics.Typeface,
        val duration: android.graphics.Typeface,
        val country: android.graphics.Typeface,
        val dayDate: android.graphics.Typeface,
    )

    private fun showCallHistory(calls: List<ContactCall>, numbers: List<PhoneNumber>, style: DenwaCallLogStyle, faces: CallHistoryFaces) {
        val holder = binding.contactCallsHolder
        holder.removeAllViews()
        if (calls.isEmpty()) {
            holder.beGone()
            return
        }

        val baseSizePx = if (style.textSizePx > 0f) style.textSizePx else getTextSize()
        // The call-history tile: the same frame and caption as every other tile of the card.
        val tileColor = themeColor(ThemeSlot.CARD_CALLS)
        holder.background = tileGround(tileColor)
        holder.clipToOutline = true
        holder.setPadding(0, dp(TILE_PAD_V_DP), 0, dp(TILE_PAD_V_DP))
        tileMargins(holder.layoutParams as ViewGroup.MarginLayoutParams)
        holder.addView(captionView(getString(R.string.contact_call_history), tileColor).apply {
            setPadding(dp(TILE_PAD_H_DP), 0, dp(TILE_PAD_H_DP), 0)
        })

        val region = callLogRegion()
        val days = calls.map { it.timestamp.callDayKey() }
        calls.forEachIndexed { index, call ->
            if (days[index] != days.getOrNull(index - 1)) {
                holder.addView(callDayRow(call, style, faces, baseSizePx))
            }
            // no line under a day's last call: the next day's own line, or the pill, follows
            val lineBelow = days.getOrNull(index + 1) == days[index]
            holder.addView(callRow(call, style, faces, baseSizePx, numbers, region, lineBelow))
        }

        holder.addView(
            pillRow(
                pill(getString(R.string.contact_call_history_all)) { openDenwaRecents(numbers.map { it.value }, fullContact?.getNameToDisplay()) },
                startPaddingPx = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.activity_margin),
            )
        )
        holder.beVisible()
    }

    private fun callDayRow(call: ContactCall, style: DenwaCallLogStyle, faces: CallHistoryFaces, baseSizePx: Float): View =
        ItemContactCallDayBinding.inflate(layoutInflater, binding.contactCallsHolder, false).apply {
            applyCallLine(contactCallDayDivider, style.dayDivider, style.dayDividerDp)
            applyCallLine(contactCallDayUnderline, style.dateUnderline, style.dateUnderlineDp)
            contactCallDayText.apply {
                text = call.timestamp.callDayHeading(this@ViewContactActivity, style.imperialDate)
                setTextColor(style.dayDate)
                typeface = faces.dayDate
                setTextSize(TypedValue.COMPLEX_UNIT_PX, baseSizePx * style.dayHeaderScale)
                if (style.dayDateFont.sizeSp > 0) setTextSize(TypedValue.COMPLEX_UNIT_SP, style.dayDateFont.sizeSp.toFloat())
            }
        }.root

    @Suppress("LongParameterList")
    private fun callRow(
        call: ContactCall,
        style: DenwaCallLogStyle,
        faces: CallHistoryFaces,
        baseSizePx: Float,
        numbers: List<PhoneNumber>,
        region: String,
        lineBelow: Boolean,
    ): View = ItemContactCallBinding.inflate(layoutInflater, binding.contactCallsHolder, false).apply {
        applyCallLine(contactCallDivider, style.divider, if (lineBelow) style.dividerDp else 0)
        val missed = call.type == CallLog.Calls.MISSED_TYPE
        val unanswered = call.type == CallLog.Calls.OUTGOING_TYPE && call.duration <= 0
        val showDuration = !missed && call.type != CallLog.Calls.REJECTED_TYPE && call.duration > 0

        fun TextView.styled(color: Int, face: android.graphics.Typeface, font: DenwaFont) {
            setTextColor(color)
            typeface = face
            setTextSize(TypedValue.COMPLEX_UNIT_PX, baseSizePx)
            if (font.sizeSp > 0) setTextSize(TypedValue.COMPLEX_UNIT_SP, font.sizeSp.toFloat())
        }

        // The number line: the number as the call log has it, and what the contact files it under.
        // The label after the “·” keeps its own colour; the number before it is the paler one.
        val shownNumber = if (style.formatNumbers) call.number.formatPhoneNumber() else call.number
        val label = numbers.firstOrNull { sameNumber(it.value, call.number) }?.let { getPhoneNumberTypeText(it.type, it.label) }.orEmpty()
        val numberLine = android.text.SpannableString(if (label.isBlank()) shownNumber else "$shownNumber$CALL_NUMBER_LABEL_SEPARATOR$label")
        if (label.isNotBlank()) {
            numberLine.setSpan(
                android.text.style.ForegroundColorSpan(style.numberLabel),
                shownNumber.length + CALL_NUMBER_LABEL_SEPARATOR.length,
                numberLine.length,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        contactCallNumber.apply {
            text = numberLine
            setTextColor(style.number)
            typeface = faces.number
            // a quieter line than the time below, unless denwa gives it a size of its own
            setTextSize(TypedValue.COMPLEX_UNIT_PX, baseSizePx * style.numberScale)
            if (style.numberFont.sizeSp > 0) setTextSize(TypedValue.COMPLEX_UNIT_SP, style.numberFont.sizeSp.toFloat())
        }

        // ✉ writes to this number; ☎ calls it back on the SIM this call used
        val name = fullContact?.getNameToDisplay().orEmpty()
        contactCallMessage.applyColorFilter(style.actionMessage)
        contactCallMessage.setOnClickListener { launchSendSMSIntent(call.number) }
        contactCallCall.applyColorFilter(style.actionCall)
        contactCallCall.setOnClickListener {
            if (call.simId == 1 || call.simId == 2) {
                callNumberWithSim(call.number, name, useSim1 = call.simId == 1)
            } else {
                startCallIntent(call.number)
            }
        }

        // the time takes its arrow's colour for the calls that have a say: one we missed, one we placed,
        // and one we placed in vain
        val timeColor = when {
            missed -> style.missed
            call.type != CallLog.Calls.OUTGOING_TYPE -> style.date
            unanswered -> style.unanswered
            else -> style.outgoing
        }
        contactCallTime.text = call.timestamp.formatCallTime(this@ViewContactActivity, style.timeFormat)
        contactCallTime.styled(timeColor, faces.date, style.dateFont)

        contactCallSeparator.setTextColor(style.duration)
        contactCallSeparator.setTextSize(TypedValue.COMPLEX_UNIT_PX, baseSizePx)
        contactCallSeparator.beVisibleIf(showDuration)
        contactCallDuration.text = call.duration.formatCallDuration(this@ViewContactActivity, style.durationFormat)
        contactCallDuration.styled(style.duration, faces.duration, style.durationFont)
        contactCallDuration.beVisibleIf(showDuration)

        val location = callLocation(call.number, region)
        contactCallLocation.text = location
        contactCallLocation.styled(style.country, faces.country, style.countryFont)
        contactCallLocation.beVisibleIf(location != null)

        // the badge as denwa stamped it when the call was made; without a stamp, the slot in today's colours
        val badge = call.badge
        val badgeDigit = badge?.digit ?: call.simId
        val showSim = badgeDigit > 0
        contactCallSimImage.beVisibleIf(showSim)
        contactCallSimId.beVisibleIf(showSim)
        if (showSim) {
            val bold = badge?.bold ?: style.simTextBold
            contactCallSimImage.applyColorFilter(badge?.color ?: style.simColor(badgeDigit))
            contactCallSimId.setTextColor(badge?.textColor ?: style.simText)
            contactCallSimId.setTypeface(null, if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            contactCallSimId.text = badgeDigit.toString()
        }

        val (icon, iconColor) = when (call.type) {
            CallLog.Calls.OUTGOING_TYPE -> R.drawable.ic_call_made_vector to (if (unanswered) style.unanswered else style.outgoing)
            CallLog.Calls.MISSED_TYPE -> R.drawable.ic_call_missed_vector to style.missed
            else -> R.drawable.ic_call_received_vector to style.incoming
        }
        contactCallType.setImageDrawable(resources.getColoredDrawableWithColor(icon, iconColor))

        // As in denwa, tapping a call narrows Recents to its number.
        root.setOnClickListener { openDenwaRecents(listOf(call.number), fullContact?.getNameToDisplay()) }
        root.setOnLongClickListener {
            copyToClipboard(call.number)
            true
        }
    }.root

    // The place, as denwa shows it: only for a number that carries its own country code, and not when it
    // is simply this phone's own country.
    @Suppress("SwallowedException")
    private fun callLocation(number: String, region: String): String? {
        val parsed = try {
            com.google.i18n.phonenumbers.PhoneNumberUtil.getInstance().parse(number, region)
        } catch (e: com.google.i18n.phonenumbers.NumberParseException) {
            return null
        }
        if (parsed.countryCodeSource == com.google.i18n.phonenumbers.Phonenumber.PhoneNumber.CountryCodeSource.FROM_DEFAULT_COUNTRY) {
            return null
        }
        val locale = Locale.getDefault()
        val location = com.google.i18n.phonenumbers.geocoding.PhoneNumberOfflineGeocoder.getInstance()
            .getDescriptionForNumber(parsed, locale, region)
        return location.takeIf { it.isNotEmpty() && it != locale.displayCountry }
    }

    private fun applyCallLine(view: View, color: Int, thicknessDp: Int) {
        if (thicknessDp <= 0) {
            view.beGone()
        } else {
            view.beVisible()
            view.setBackgroundColor(color)
            view.layoutParams = view.layoutParams.apply { height = (thicknessDp * resources.displayMetrics.density).toInt() }
        }
    }

    private fun showSocialActions(contactId: Int) {
        ensureBackgroundThread {
            val actions = getSocialActions(contactId)
            runOnUiThread {
                if (!isDestroyed && !isFinishing) {
                    ChooseSocialDialog(this@ViewContactActivity, actions) { action ->
                        Intent(Intent.ACTION_VIEW).apply {
                            val uri = ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, action.dataId)
                            setDataAndType(uri, action.mimetype)
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
                            try {
                                startActivity(this)
                            } catch (e: SecurityException) {
                                handlePermission(PERMISSION_CALL_PHONE) { success ->
                                    if (success) {
                                        startActivity(this)
                                    } else {
                                        toast(org.fossify.commons.R.string.no_phone_call_permission)
                                    }
                                }
                            } catch (e: ActivityNotFoundException) {
                                toast(org.fossify.commons.R.string.no_app_found)
                            } catch (e: Exception) {
                                showErrorToast(e)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun customRingtoneSelected(ringtonePath: String) {
        ringtoneView?.text = ringtonePath.getFilenameFromPath()
        ringtoneUpdated(ringtonePath)
    }

    override fun systemRingtoneSelected(uri: Uri?) {
        val contactRingtone = RingtoneManager.getRingtone(this, uri)
        ringtoneView?.text = contactRingtone.getTitle(this)
        ringtoneUpdated(uri?.toString() ?: "")
    }

    private fun ringtoneUpdated(path: String?) {
        contact!!.ringtone = path

        ensureBackgroundThread {
            if (contact!!.isPrivate()) {
                LocalContactsHelper(this).updateRingtone(contact!!.contactId, path ?: "")
            } else {
                ContactsHelper(this).updateRingtone(contact!!.contactId.toString(), path ?: "")
            }
        }
    }

    private fun initializeDuplicateContacts(callback: () -> Unit) {
        getDuplicateContacts(contact!!, false) {
            duplicateContacts = it
            runOnUiThread {
                callback()
            }
        }
    }

    private fun deleteContactWithMergeLogic() {
        val addition = if (sourceCount > 1 && mergeDuplicate) {
            "\n\n${getString(R.string.delete_from_all_sources)}"
        } else {
            ""
        }

        val message = "${getString(org.fossify.commons.R.string.proceed_with_deletion)}$addition"
        ConfirmationDialog(this, message, destructive = true) {
            if (contact != null) {
                ContactsHelper(this).deleteContact(contact!!, mergeDuplicate) {
                    finish()
                }
            }
        }
    }

    private fun getStarDrawable(on: Boolean): Drawable? {
        val star = if (on) {
            org.fossify.commons.R.drawable.ic_star_vector
        } else {
            org.fossify.commons.R.drawable.ic_star_outline_vector
        }
        return resources.getColoredDrawableWithColor(star, themeColor(ThemeSlot.CARD_NAME))
    }

    private fun hideBigContactPhoto() {
        binding.contactPhotoBig.animate().alpha(0f).withEndAction { binding.contactPhotoBig.beGone() }.start()
    }

    private fun View.copyOnLongClick(value: String) {
        setOnLongClickListener {
            copyToClipboard(value)
            true
        }
    }
}
