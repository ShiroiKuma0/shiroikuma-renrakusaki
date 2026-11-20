package org.fossify.contacts.activities

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
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.view.isVisible
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

// Route badges on the contact screen's numbers (dp / sp).
private const val ROUTE_BADGE_SIZE_DP = 30
private const val ROUTE_BADGE_PAD_DP = 7
private const val ROUTE_BADGE_GAP_DP = 6
private const val ROUTE_BADGE_RADIUS_DP = 7f
private const val ROUTE_BADGE_TEXT_SP = 16f

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
        private const val CALL_HISTORY_HEADING_SCALE = 1.1f
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
        setupViewContact()
        binding.contactSendSms.beVisibleIf(contact!!.phoneNumbers.isNotEmpty())
        binding.contactStartCall.beVisibleIf(contact!!.phoneNumbers.isNotEmpty())
        binding.contactSendEmail.beVisibleIf(contact!!.emails.isNotEmpty())

        if (contact!!.photoUri.isEmpty() && contact!!.photo == null) {
            showPhotoPlaceholder(binding.contactPhoto)
            binding.contactPhotoBottomShadow.beGone()
        } else {
            updateContactPhoto(contact!!.photoUri, binding.contactPhoto, binding.contactPhotoBottomShadow, contact!!.photo)
            val options = RequestOptions()
                .transform(FitCenter(), RoundedCorners(resources.getDimension(org.fossify.commons.R.dimen.normal_margin).toInt()))

            Glide.with(this)
                .load(contact!!.photo ?: currentContactPhotoPath)
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

        val textColor = getProperTextColor()
        arrayOf(
            binding.contactNameImage, binding.contactNumbersImage, binding.contactEmailsImage, binding.contactAddressesImage, binding.contactImsImage,
            binding.contactEventsImage, binding.contactSourceImage, binding.contactNotesImage, binding.contactRingtoneImage, binding.contactOrganizationImage,
            binding.contactWebsitesImage, binding.contactGroupsImage
        ).forEach {
            it.applyColorFilter(textColor)
        }

        binding.contactSendSms.setOnClickListener { trySendSMS() }
        binding.contactStartCall.setOnClickListener { tryInitiateCall(contact!!) { startDefaultRouteCall(contact!!, it) } }
        binding.contactSendEmail.setOnClickListener { trySendEmail() }

        binding.contactSendSms.setOnLongClickListener { toast(org.fossify.commons.R.string.send_sms); true; }
        binding.contactStartCall.setOnLongClickListener { toast(org.fossify.commons.R.string.call_contact); true; }
        binding.contactSendEmail.setOnLongClickListener { toast(org.fossify.commons.R.string.send_email); true; }

        updateTextColors(binding.contactScrollview)
        binding.contactToolbar.menu.findItem(R.id.open_with).isVisible = contact?.isPrivate() == false
        refreshVisibleToOtherAppsItem()
        // The overflow popup draws its titles in the platform's white otherwise — same slot as every
        // other menu in the app.
        binding.contactToolbar.menu.colorItemTitles(themeColor(ThemeSlot.SEARCH_MENU_TEXT))

        // Fork: everything drawn over the photo gets its own color plus a halo (the favorite star is
        // haloed in getStarDrawable, since it is re-set on every toggle).
        applyPhotoOverlayIcons(
            binding.contactToolbar, binding.contactSendSms, binding.contactStartCall, binding.contactSendEmail
        )
    }

    private fun setupViewContact() {
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        setupFavorite()
        setupNames()
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

    private fun setupContactDetails() {
        if (isFinishing || isDestroyed || contact == null) {
            return
        }

        setupPhoneNumbers()
        setupEmails()
        setupAddresses()
        setupIMs()
        setupEvents()
        setupWebsites()
        setupMoreFields()
        setupGroups()
        setupContactSources()
        setupNotes()
        setupRingtone()
        setupOrganization()
        updateTextColors(binding.contactScrollview)
        setupCallHistory()
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

    private fun setupFavorite() {
        binding.contactToggleFavorite.apply {
            beVisible()
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
    }

    private fun setupNames() {
        var displayName = contact!!.getNameToDisplay()
        if (contact!!.nickname.isNotEmpty()) {
            displayName += " (${contact!!.nickname})"
        }

        val showNameFields = showFields and SHOW_PREFIX_FIELD != 0 || showFields and SHOW_FIRST_NAME_FIELD != 0 || showFields and SHOW_MIDDLE_NAME_FIELD != 0 ||
            showFields and SHOW_SURNAME_FIELD != 0 || showFields and SHOW_SUFFIX_FIELD != 0

        binding.contactName.text = displayName
        binding.contactName.copyOnLongClick(displayName)
        binding.contactName.beVisibleIf(displayName.isNotEmpty() && !contact!!.isABusinessContact() && showNameFields)
        binding.contactNameImage.beInvisibleIf(binding.contactName.isGone())
    }

    // Fork: the name's reading (フリガナ), under the name it belongs to. Read straight from the provider
    // rather than from ContactExtras: this screen opens from other apps too, in a process where no
    // contacts refresh has ever run. A private contact carries its reading in Contact.extra.
    private fun setupReading() {
        binding.contactReading.beGone()
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

                binding.contactReading.apply {
                    text = reading
                    copyOnLongClick(reading)
                    beVisibleIf(!binding.contactName.isGone())
                    setTextColor(getProperTextColor())
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

    // Route badges on a number: red 1 and blue 2 dial through that SIM (by way of denwa), the yellow 臨N
    // through 白い熊 臨電話 — the contact's own 臨 account, else the default from UI settings. One tap dials;
    // a long-press on 臨 picks the contact's account. A route whose app is not installed has no badge.
    private fun setupRouteBadges(holder: android.widget.LinearLayout, number: String) {
        holder.removeAllViews()
        if (!config.routeBadges) return
        val shown = fullContact ?: contact ?: return
        val name = shown.getNameToDisplay()
        if (getInstalledDialerAppPackage() != null) {
            val simText = themeColor(ThemeSlot.ROUTE_SIM_TEXT)
            holder.addView(routeBadge("1", themeColor(ThemeSlot.ROUTE_SIM1), simText) { callNumberWithSim(number, name, true) })
            holder.addView(routeBadge("2", themeColor(ThemeSlot.ROUTE_SIM2), simText) { callNumberWithSim(number, name, false) })
        }
        if (Rindenwa.isInstalled(this)) {
            // each 臨 account wears the colours 白い熊 gave it in Rindenwa
            val account = effectiveRinAccount(shown)
            val (rinGround, rinText) = rinBadgeColors(account)
            val badge = routeBadge(rinBadgeFor(account), rinGround, rinText) {
                callNumberViaRindenwa(number, name, effectiveRinAccount(shown))
            }
            // an account that can do callback offers it first; any other keeps the plain account picker
            badge.setOnLongClickListener {
                if (rinCanCallback(effectiveRinAccount(shown))) showRinBadgeMenu(badge, shown, number, name) else pickOwnRinAccount(shown)
                true
            }
            holder.addView(badge)
        }
    }

    private fun pickOwnRinAccount(shown: Contact) {
        pickRinAccount(contactRinAccount(shown), getString(R.string.rin_account_use_default)) { account ->
            setContactRinAccount(shown, account)
            setupPhoneNumbers()
        }
    }

    // The long-press menu of a 臨N badge whose account can set up a callback (Odorik): Callback first,
    // then the ordinary call the tap places, then the account picker the long-press opened before.
    private fun showRinBadgeMenu(badge: View, shown: Contact, number: String, name: String) {
        androidx.appcompat.widget.PopupMenu(
            this, badge, android.view.Gravity.END, androidx.appcompat.R.attr.actionOverflowMenuStyle, 0
        ).apply {
            menu.add(0, R.id.rin_callback, 0, R.string.rin_callback)
            menu.add(0, R.id.rin_call, 1, org.fossify.commons.R.string.call)
            menu.add(0, R.id.rin_set_account, 2, R.string.set_rin_account)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.rin_callback -> orderCallbackViaRindenwa(number, name, effectiveRinAccount(shown))
                    R.id.rin_call -> callNumberViaRindenwa(number, name, effectiveRinAccount(shown))
                    R.id.rin_set_account -> pickOwnRinAccount(shown)
                }
                true
            }
            show()
        }
    }

    private fun routeBadge(label: String, ground: Int, text: Int, onClick: () -> Unit): TextView {
        val density = resources.displayMetrics.density
        return TextView(this).apply {
            this.text = label
            setTextColor(text)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, ROUTE_BADGE_TEXT_SP)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            includeFontPadding = false
            minWidth = (ROUTE_BADGE_SIZE_DP * density).toInt()
            val pad = (ROUTE_BADGE_PAD_DP * density).toInt()
            setPadding(pad, 0, pad, 0)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = ROUTE_BADGE_RADIUS_DP * density
                setColor(ground)
            }
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, (ROUTE_BADGE_SIZE_DP * density).toInt()
            ).apply { marginStart = (ROUTE_BADGE_GAP_DP * density).toInt() }
            setOnClickListener { onClick() }
        }
    }

    // Rindenwa's account list decides what a 臨 badge reads; ask for it once per visit and redraw on news.
    private var rinAccountsAsked = false

    private fun refreshRinAccountsOnce() {
        if (rinAccountsAsked || !config.routeBadges || !Rindenwa.isInstalled(this)) return
        rinAccountsAsked = true
        val appContext = applicationContext
        ensureBackgroundThread {
            val before = Rindenwa.cachedAccounts(appContext)
            val now = Rindenwa.refreshAccounts(appContext)
            if (now != null && now != before) {
                runOnUiThread { if (!isFinishing && !isDestroyed && contact != null) setupPhoneNumbers() }
            }
        }
    }

    private fun setupPhoneNumbers() {
        refreshRinAccountsOnce()
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
        binding.contactNumbersHolder.removeAllViews()

        if (phoneNumbers.isNotEmpty() && showFields and SHOW_PHONE_NUMBERS_FIELD != 0) {
            phoneNumbers.forEach { phoneNumber ->
                ItemViewPhoneNumberBinding.inflate(layoutInflater, binding.contactNumbersHolder, false).apply {
                    binding.contactNumbersHolder.addView(root)
                    if (config.formatPhoneNumbers) {
                        contactNumber.text = phoneNumber.value.formatPhoneNumber()
                    } else {
                        contactNumber.text = phoneNumber.value
                    }
                    contactNumberType.text = getPhoneNumberTypeText(phoneNumber.type, phoneNumber.label)
                    root.copyOnLongClick(phoneNumber.value)

                    root.setOnClickListener {
                        if (config.showCallConfirmation) {
                            CallConfirmationDialog(this@ViewContactActivity, phoneNumber.value) {
                                startDefaultRouteCall(contact!!, phoneNumber.value)
                            }
                        } else {
                            startDefaultRouteCall(contact!!, phoneNumber.value)
                        }
                    }

                    defaultToggleIcon.isVisible = phoneNumber.isPrimary
                    defaultToggleIcon.applyColorFilter(getProperTextColor())
                    setupRouteBadges(contactNumberRoutes, phoneNumber.value)
                }
            }
            binding.contactNumbersImage.beVisible()
            binding.contactNumbersHolder.beVisible()
        } else {
            binding.contactNumbersImage.beGone()
            binding.contactNumbersHolder.beGone()
        }

        // make sure the Call and SMS buttons are visible if any phone number is shown
        if (phoneNumbers.isNotEmpty()) {
            binding.contactSendSms.beVisible()
            binding.contactStartCall.beVisible()
        }
    }

    // a contact cannot have different emails per contact source. Such contacts are handled as separate ones, not duplicates of each other
    private fun setupEmails() {
        binding.contactEmailsHolder.removeAllViews()
        val emails = contact!!.emails
        if (emails.isNotEmpty() && showFields and SHOW_EMAILS_FIELD != 0) {
            emails.forEach {
                ItemViewEmailBinding.inflate(layoutInflater, binding.contactEmailsHolder, false).apply {
                    val email = it
                    binding.contactEmailsHolder.addView(root)
                    contactEmail.text = email.value
                    contactEmailType.text = getEmailTypeText(email.type, email.label)
                    root.copyOnLongClick(email.value)

                    root.setOnClickListener {
                        sendEmailIntent(email.value)
                    }
                }
            }
            binding.contactEmailsImage.beVisible()
            binding.contactEmailsHolder.beVisible()
        } else {
            binding.contactEmailsImage.beGone()
            binding.contactEmailsHolder.beGone()
        }
    }

    private fun setupAddresses() {
        var addresses = contact!!.addresses.toMutableSet() as LinkedHashSet<Address>

        if (mergeDuplicate) {
            duplicateContacts.forEach {
                addresses.addAll(it.addresses)
            }
        }

        addresses = addresses.sortedBy { it.type }.toMutableSet() as LinkedHashSet<Address>
        fullContact!!.addresses = addresses.toMutableList() as ArrayList<Address>
        binding.contactAddressesHolder.removeAllViews()

        if (addresses.isNotEmpty() && showFields and SHOW_ADDRESSES_FIELD != 0) {
            addresses.forEach {
                ItemViewAddressBinding.inflate(layoutInflater, binding.contactAddressesHolder, false).apply {
                    val address = it
                    binding.contactAddressesHolder.addView(root)
                    contactAddress.text = address.value
                    contactAddressType.text = getAddressTypeText(address.type, address.label)
                    root.copyOnLongClick(address.value)

                    root.setOnClickListener {
                        sendAddressIntent(address.value)
                    }
                }
            }
            binding.contactAddressesImage.beVisible()
            binding.contactAddressesHolder.beVisible()
        } else {
            binding.contactAddressesImage.beGone()
            binding.contactAddressesHolder.beGone()
        }
    }

    private fun setupIMs() {
        var IMs = contact!!.IMs.toMutableSet() as LinkedHashSet<IM>

        if (mergeDuplicate) {
            duplicateContacts.forEach {
                IMs.addAll(it.IMs)
            }
        }

        IMs = IMs.sortedBy { it.type }.toMutableSet() as LinkedHashSet<IM>
        fullContact!!.IMs = IMs.toMutableList() as ArrayList<IM>
        binding.contactImsHolder.removeAllViews()

        if (IMs.isNotEmpty() && showFields and SHOW_IMS_FIELD != 0) {
            IMs.forEach {
                ItemViewImBinding.inflate(layoutInflater, binding.contactImsHolder, false).apply {
                    val IM = it
                    binding.contactImsHolder.addView(root)
                    contactIm.text = IM.value
                    contactImType.text = getIMTypeText(IM.type, IM.label)
                    root.copyOnLongClick(IM.value)
                }
            }
            binding.contactImsImage.beVisible()
            binding.contactImsHolder.beVisible()
        } else {
            binding.contactImsImage.beGone()
            binding.contactImsHolder.beGone()
        }
    }

    private fun setupEvents() {
        var events = contact!!.events.toMutableSet() as LinkedHashSet<Event>

        if (mergeDuplicate) {
            duplicateContacts.forEach {
                events.addAll(it.events)
            }
        }

        events = events.sortedBy { it.type }.toMutableSet() as LinkedHashSet<Event>
        fullContact!!.events = events.toMutableList() as ArrayList<Event>
        binding.contactEventsHolder.removeAllViews()

        if (events.isNotEmpty() && showFields and SHOW_EVENTS_FIELD != 0) {
            events.forEach {
                ItemViewEventBinding.inflate(layoutInflater, binding.contactEventsHolder, false).apply {
                    binding.contactEventsHolder.addView(root)
                    it.value.getDateTimeFromDateString(true, contactEvent)
                    if (it.type == CommonDataKinds.Event.TYPE_CUSTOM && it.label.isNotEmpty()) contactEventType.text = it.label else contactEventType.setText(getEventTextId(it.type))
                    root.copyOnLongClick(it.value)
                }
            }
            binding.contactEventsImage.beVisible()
            binding.contactEventsHolder.beVisible()
        } else {
            binding.contactEventsImage.beGone()
            binding.contactEventsHolder.beGone()
        }
    }

    private fun setupWebsites() {
        var websites = contact!!.websites.toMutableSet() as LinkedHashSet<String>

        if (mergeDuplicate) {
            duplicateContacts.forEach {
                websites.addAll(it.websites)
            }
        }

        websites = websites.sorted().toMutableSet() as LinkedHashSet<String>
        fullContact!!.websites = websites.toMutableList() as ArrayList<String>
        binding.contactWebsitesHolder.removeAllViews()

        if (websites.isNotEmpty() && showFields and SHOW_WEBSITES_FIELD != 0) {
            websites.forEach {
                val url = it
                ItemWebsiteBinding.inflate(layoutInflater, binding.contactWebsitesHolder, false).apply {
                    binding.contactWebsitesHolder.addView(root)
                    contactWebsite.text = url
                    root.copyOnLongClick(url)

                    root.setOnClickListener {
                        openWebsiteIntent(url)
                    }
                }
            }
            binding.contactWebsitesImage.beVisible()
            binding.contactWebsitesHolder.beVisible()
        } else {
            binding.contactWebsitesImage.beGone()
            binding.contactWebsitesHolder.beGone()
        }
    }

    // Fork (sk11): every field beyond Fossify's own (Contact.extra) — organization details, relationships,
    // internet-call addresses, send to voicemail, and any other data the contact holds, read-only.
    private fun setupMoreFields() {
        val holder = binding.contactMoreHolder
        holder.removeAllViews()
        val extra = contact!!.extra
        val textColor = getProperTextColor()

        fun row(value: String, type: String, onClick: (() -> Unit)? = null) {
            if (value.isEmpty()) return
            ItemViewImBinding.inflate(layoutInflater, holder, false).apply {
                holder.addView(root)
                contactIm.text = value
                contactImType.text = type
                contactIm.setTextColor(textColor)
                contactImType.setTextColor(textColor)
                root.copyOnLongClick(value)
                if (onClick != null) root.setOnClickListener { onClick() } else root.background = null
            }
        }

        row(extra.department, getString(R.string.more_department))
        row(extra.jobDescription, getString(R.string.more_job_description))
        row(extra.officeLocation, getString(R.string.more_office_location))
        row(extra.companyPhonetic, getString(R.string.more_company_phonetic))
        row(extra.symbol, getString(R.string.more_symbol))
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

        holder.beVisibleIf(holder.childCount > 0)
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
        val primary = getProperPrimaryColor()

        // The section heading: bold, underlined accent, like every heading of ours.
        ItemContactCallDayBinding.inflate(layoutInflater, holder, false).apply {
            contactCallDayDivider.beGone()
            contactCallDayText.text = getString(R.string.contact_call_history)
            contactCallDayText.setTextColor(primary)
            contactCallDayText.setTextSize(TypedValue.COMPLEX_UNIT_PX, getTextSize() * CALL_HISTORY_HEADING_SCALE)
            contactCallDayUnderline.setBackgroundColor(primary)
            holder.addView(root)
        }

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

    private fun setupGroups() {
        var groups = contact!!.groups.toMutableSet() as LinkedHashSet<Group>

        if (mergeDuplicate) {
            duplicateContacts.forEach {
                groups.addAll(it.groups)
            }
        }

        groups = groups.sortedBy { it.title }.toMutableSet() as LinkedHashSet<Group>
        fullContact!!.groups = groups.toMutableList() as ArrayList<Group>
        binding.contactGroupsHolder.removeAllViews()

        if (groups.isNotEmpty() && showFields and SHOW_GROUPS_FIELD != 0) {
            groups.forEach {
                ItemViewGroupBinding.inflate(layoutInflater, binding.contactGroupsHolder, false).apply {
                    val group = it
                    binding.contactGroupsHolder.addView(root)
                    contactGroup.text = group.title
                    root.copyOnLongClick(group.title)
                }
            }
            binding.contactGroupsImage.beVisible()
            binding.contactGroupsHolder.beVisible()
        } else {
            binding.contactGroupsImage.beGone()
            binding.contactGroupsHolder.beGone()
        }
    }

    // Who can see this contact — System / Private / Private + visible — under its sources; a tap opens
    // the same three-way menu as a long-press on the photo in the lists (dialogs/ChangeVisibilityDialog).
    private fun addVisibilityRow() {
        if (!config.showVisibilityRow) return
        val shown = contact ?: return
        ItemViewContactSourceBinding.inflate(layoutInflater, binding.contactSourcesHolder, false).apply {
            binding.contactSourcesHolder.addView(root)
            contactSource.text = getString(R.string.visibility_title)
            contactSource.setOnClickListener {
                ChangeVisibilityDialog(this@ViewContactActivity, shown) { moved ->
                    if (moved) {
                        // It now lives under another id in the other store — there is nothing here to reload.
                        finish()
                    } else {
                        setupContactSources()
                        refreshVisibleToOtherAppsItem()
                    }
                }
            }
            ensureBackgroundThread {
                val members = ChangeVisibilityDialog.membersOf(this@ViewContactActivity, shown)
                val label = when (ChangeVisibilityDialog.stateOfGroup(this@ViewContactActivity, members)) {
                    ChangeVisibilityDialog.State.SYSTEM -> R.string.visibility_system
                    ChangeVisibilityDialog.State.PRIVATE -> R.string.visibility_private
                    ChangeVisibilityDialog.State.VISIBLE -> R.string.visibility_visible
                    null -> R.string.visibility_mixed
                }
                runOnUiThread { contactSource.text = getString(R.string.visibility_row, getString(label)) }
            }
        }
    }

    private fun setupContactSources() {
        binding.contactSourcesHolder.removeAllViews()
        if (showFields and SHOW_CONTACT_SOURCE_FIELD != 0) {
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

            for ((key, value) in sources) {
                ItemViewContactSourceBinding.inflate(layoutInflater, binding.contactSourcesHolder, false).apply {
                    contactSource.text = if (value == "") getString(R.string.phone_storage) else value
                    contactSource.copyOnLongClick(value)
                    binding.contactSourcesHolder.addView(root)

                    contactSource.setOnClickListener {
                        launchEditContact(key, true)
                    }

                    if (value.lowercase(Locale.getDefault()) == WHATSAPP) {
                        contactSourceImage.setImageDrawable(getPackageDrawable(WHATSAPP_PACKAGE))
                        contactSourceImage.beVisible()
                        contactSourceImage.setOnClickListener {
                            showSocialActions(key.id)
                        }
                    }

                    if (value.lowercase(Locale.getDefault()) == SIGNAL) {
                        contactSourceImage.setImageDrawable(getPackageDrawable(SIGNAL_PACKAGE))
                        contactSourceImage.beVisible()
                        contactSourceImage.setOnClickListener {
                            showSocialActions(key.id)
                        }
                    }

                    if (value.lowercase(Locale.getDefault()) == VIBER) {
                        contactSourceImage.setImageDrawable(getPackageDrawable(VIBER_PACKAGE))
                        contactSourceImage.beVisible()
                        contactSourceImage.setOnClickListener {
                            showSocialActions(key.id)
                        }
                    }

                    if (value.lowercase(Locale.getDefault()) == TELEGRAM) {
                        contactSourceImage.setImageDrawable(getPackageDrawable(TELEGRAM_PACKAGE))
                        contactSourceImage.beVisible()
                        contactSourceImage.setOnClickListener {
                            showSocialActions(key.id)
                        }
                    }

                    if (value.lowercase(Locale.getDefault()) == THREEMA) {
                        contactSourceImage.setImageDrawable(getPackageDrawable(THREEMA_PACKAGE))
                        contactSourceImage.beVisible()
                        contactSourceImage.setOnClickListener {
                            showSocialActions(key.id)
                        }
                    }
                }
            }

            addVisibilityRow()

            binding.contactSourceImage.beVisible()
            binding.contactSourcesHolder.beVisible()
        } else {
            binding.contactSourceImage.beGone()
            binding.contactSourcesHolder.beGone()
        }
    }

    private fun setupNotes() {
        val notes = contact!!.notes
        if (notes.isNotEmpty() && showFields and SHOW_NOTES_FIELD != 0) {
            binding.contactNotes.text = notes
            binding.contactNotesImage.beVisible()
            binding.contactNotes.beVisible()
            binding.contactNotes.copyOnLongClick(notes)
        } else {
            binding.contactNotesImage.beGone()
            binding.contactNotes.beGone()
        }
    }

    private fun setupRingtone() {
        if (showFields and SHOW_RINGTONE_FIELD != 0) {
            binding.contactRingtoneImage.beVisible()
            binding.contactRingtone.beVisible()

            val ringtone = contact!!.ringtone
            if (ringtone?.isEmpty() == true) {
                binding.contactRingtone.text = getString(org.fossify.commons.R.string.no_sound)
            } else if (ringtone?.isNotEmpty() == true && ringtone != getDefaultRingtoneUri().toString()) {
                if (ringtone == SILENT) {
                    binding.contactRingtone.text = getString(org.fossify.commons.R.string.no_sound)
                } else {
                    systemRingtoneSelected(Uri.parse(ringtone))
                }
            } else {
                binding.contactRingtoneImage.beGone()
                binding.contactRingtone.beGone()
                return
            }

            binding.contactRingtone.copyOnLongClick(binding.contactRingtone.text.toString())

            binding.contactRingtone.setOnClickListener {
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
                            binding.contactRingtone.text = it?.title
                            ringtoneUpdated(it?.uri)
                        },
                        onAlarmSoundDeleted = {}
                    )
                }
            }
        } else {
            binding.contactRingtoneImage.beGone()
            binding.contactRingtone.beGone()
        }
    }

    private fun setupOrganization() {
        val organization = contact!!.organization
        if (organization.isNotEmpty() && showFields and SHOW_ORGANIZATION_FIELD != 0) {
            binding.contactOrganizationCompany.text = organization.company
            binding.contactOrganizationJobPosition.text = organization.jobPosition
            binding.contactOrganizationImage.beGoneIf(organization.isEmpty())
            binding.contactOrganizationCompany.beGoneIf(organization.company.isEmpty())
            binding.contactOrganizationJobPosition.beGoneIf(organization.jobPosition.isEmpty())
            binding.contactOrganizationCompany.copyOnLongClick(binding.contactOrganizationCompany.value)
            binding.contactOrganizationJobPosition.copyOnLongClick(binding.contactOrganizationJobPosition.value)

            if (organization.company.isEmpty() && organization.jobPosition.isNotEmpty()) {
                (binding.contactOrganizationImage.layoutParams as RelativeLayout.LayoutParams).addRule(
                    RelativeLayout.ALIGN_TOP,
                    binding.contactOrganizationJobPosition.id
                )
            }
        } else {
            binding.contactOrganizationImage.beGone()
            binding.contactOrganizationCompany.beGone()
            binding.contactOrganizationJobPosition.beGone()
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
        binding.contactRingtone.text = ringtonePath.getFilenameFromPath()
        ringtoneUpdated(ringtonePath)
    }

    override fun systemRingtoneSelected(uri: Uri?) {
        val contactRingtone = RingtoneManager.getRingtone(this, uri)
        binding.contactRingtone.text = contactRingtone.getTitle(this)
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
        val addition = if (binding.contactSourcesHolder.childCount > 1 && mergeDuplicate) {
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

    // Fork: haloed like every other icon drawn over the photo (see applyPhotoOverlayIcons).
    private fun getStarDrawable(on: Boolean): Drawable? {
        val star = if (on) {
            org.fossify.commons.R.drawable.ic_star_vector
        } else {
            org.fossify.commons.R.drawable.ic_star_outline_vector
        }
        return photoOverlayIcon(resources.getDrawable(star), themeColor(ThemeSlot.PHOTO_ACTION_ICON))
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
