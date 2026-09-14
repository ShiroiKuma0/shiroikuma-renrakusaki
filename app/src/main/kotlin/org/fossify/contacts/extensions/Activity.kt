package org.fossify.contacts.extensions

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.view.children
import com.google.android.material.appbar.MaterialToolbar
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.getFileOutputStream
import org.fossify.commons.extensions.getPublicContactSourceSync
import org.fossify.commons.extensions.getTempFile
import org.fossify.commons.extensions.getVisibleContactSources
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.isPackageInstalled
import org.fossify.commons.extensions.launchActivityIntent
import org.fossify.commons.extensions.sharePathIntent
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toFileDirItem
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.tryInitiateCall
import org.fossify.commons.helpers.CONTACT_ID
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.IS_PRIVATE
import org.fossify.commons.helpers.ON_CLICK_CALL_CONTACT
import org.fossify.commons.helpers.ON_CLICK_EDIT_CONTACT
import org.fossify.commons.helpers.ON_CLICK_VIEW_CONTACT
import org.fossify.commons.helpers.PERMISSION_CALL_PHONE
import org.fossify.commons.helpers.SMT_PRIVATE
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.RadioItem
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.views.MyAppBarLayout
import org.fossify.contacts.BuildConfig
import org.fossify.contacts.R
import org.fossify.contacts.activities.EditContactActivity
import org.fossify.contacts.activities.SimpleActivity
import org.fossify.contacts.activities.ViewContactActivity
import org.fossify.contacts.dialogs.ImportContactsDialog
import org.fossify.contacts.helpers.DEFAULT_FILE_NAME
import org.fossify.contacts.helpers.DIALER_APP_MAIN_ACTIVITY
import org.fossify.contacts.helpers.OPEN_TAB_INTENT_EXTRA
import org.fossify.contacts.helpers.dialerAppPackages
import org.fossify.contacts.helpers.VcfExporter

fun SimpleActivity.startCallIntent(recipient: String) {
    handlePermission(PERMISSION_CALL_PHONE) {
        val action = if (it) Intent.ACTION_CALL else Intent.ACTION_DIAL
        Intent(action).apply {
            data = Uri.fromParts("tel", recipient, null)
            launchActivityIntent(this)
        }
    }
}

fun SimpleActivity.showContactSourcePicker(currentSource: String, callback: (newSource: String) -> Unit) {
    ContactsHelper(this).getSaveableContactSources { sources ->
        val items = ArrayList<RadioItem>()
        var sourceNames = sources.map { it.name }
        var currentSourceIndex = sourceNames.indexOfFirst { it == currentSource }
        sourceNames = sources.map { it.publicName }

        sourceNames.forEachIndexed { index, account ->
            items.add(RadioItem(index, account))
            if (currentSource == SMT_PRIVATE && account == getString(R.string.phone_storage_hidden)) {
                currentSourceIndex = index
            }
        }

        runOnUiThread {
            RadioGroupDialog(this, items, currentSourceIndex) {
                callback(sources[it as Int].name)
            }
        }
    }
}

fun BaseSimpleActivity.shareContacts(contacts: ArrayList<Contact>) {
    val filename = if (contacts.size == 1) {
        "${contacts.first().getNameToDisplay()}.vcf"
    } else {
        DEFAULT_FILE_NAME
    }

    val file = getTempFile(filename)
    if (file == null) {
        toast(org.fossify.commons.R.string.unknown_error_occurred)
        return
    }

    getFileOutputStream(file.toFileDirItem(this), true) {
        VcfExporter().exportContacts(
            context = this,
            outputStream = it,
            contacts = contacts,
            showExportingToast = false
        ) {
            if (it == VcfExporter.ExportResult.EXPORT_OK) {
                sharePathIntent(file.absolutePath, BuildConfig.APPLICATION_ID)
            } else {
                showErrorToast("$it")
            }
        }
    }
}

fun SimpleActivity.handleGenericContactClick(contact: Contact) {
    when (config.onContactClick) {
        ON_CLICK_CALL_CONTACT -> callContact(contact)
        ON_CLICK_VIEW_CONTACT -> viewContact(contact)
        ON_CLICK_EDIT_CONTACT -> editContact(contact, config.mergeDuplicateContacts)
    }
}

fun SimpleActivity.callContact(contact: Contact) {
    hideKeyboard()
    if (contact.phoneNumbers.isNotEmpty()) {
        tryInitiateCall(contact) { startCallIntent(it) }
    } else {
        toast(org.fossify.commons.R.string.no_phone_number_found)
    }
}

fun Activity.viewContact(contact: Contact) {
    hideKeyboard()
    Intent(applicationContext, ViewContactActivity::class.java).apply {
        putExtra(CONTACT_ID, contact.id)
        putExtra(IS_PRIVATE, contact.isPrivate())
        startActivity(this)
    }
}

fun Activity.editContact(contact: Contact, isMergedDuplicate: Boolean) {
    if (!isMergedDuplicate) {
        editContact(contact)
    } else {
        ContactsHelper(this).getContactSources { contactSources ->
            getDuplicateContacts(contact, true) { contacts ->
                if (contacts.size == 1) {
                    runOnUiThread {
                        editContact(contacts.first())
                    }
                } else {
                    val items = ArrayList(contacts.mapIndexed { index, contact ->
                        var source = getPublicContactSourceSync(contact.source, contactSources)
                        if (source == "") {
                            source = getString(R.string.phone_storage)
                        }
                        RadioItem(index, source, contact)
                    }.sortedBy { it.title })

                    runOnUiThread {
                        RadioGroupDialog(
                            activity = this,
                            items = items,
                            titleId = R.string.select_account,
                        ) {
                            editContact(it as Contact)
                        }
                    }
                }
            }
        }
    }
}

fun Activity.getDuplicateContacts(contact: Contact, includeCurrent: Boolean, callback: (duplicateContacts: ArrayList<Contact>) -> Unit) {
    val duplicateContacts = ArrayList<Contact>()
    if (includeCurrent) {
        duplicateContacts.add(contact)
    }
    ContactsHelper(this).getDuplicatesOfContact(contact, false) { contacts ->
        ensureBackgroundThread {
            val displayContactSources = getVisibleContactSources()
            contacts.filter { displayContactSources.contains(it.source) }.forEach {
                val duplicate = ContactsHelper(this).getContactWithId(it.id, it.isPrivate())
                if (duplicate != null) {
                    duplicateContacts.add(duplicate)
                }
            }
            callback(duplicateContacts)
        }
    }
}

fun Activity.editContact(contact: Contact) {
    hideKeyboard()
    Intent(applicationContext, EditContactActivity::class.java).apply {
        putExtra(CONTACT_ID, contact.id)
        putExtra(IS_PRIVATE, contact.isPrivate())
        startActivity(this)
    }
}

fun SimpleActivity.tryImportContactsFromFile(uri: Uri, callback: (Boolean) -> Unit) {
    when (uri.scheme) {
        "file" -> showImportContactsDialog(uri.path!!, callback)
        "content" -> {
            try {
                val tempFile = copyUriToTempFile(uri, "import-${System.currentTimeMillis()}-$DEFAULT_FILE_NAME")
                if (tempFile == null) {
                    toast(org.fossify.commons.R.string.unknown_error_occurred)
                    return
                }

                showImportContactsDialog(tempFile.absolutePath, callback)
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }

        else -> toast(org.fossify.commons.R.string.invalid_file_format)
    }
}

fun SimpleActivity.showImportContactsDialog(path: String, callback: (Boolean) -> Unit) {
    ImportContactsDialog(this, path, callback)
}

// Recolor a secondary screen's top bar title + back arrow from their theme slots, after setupTopAppBar.
fun BaseSimpleActivity.applyTopBarColors(appbar: MyAppBarLayout) {
    val toolbar = appbar.children.filterIsInstance<MaterialToolbar>().firstOrNull() ?: return
    toolbar.setTitleTextColor(themeColor(ThemeSlot.TOPBAR_TITLE))
    toolbar.navigationIcon?.applyColorFilter(themeColor(ThemeSlot.TOPBAR_NAV))
    toolbar.overflowIcon?.applyColorFilter(themeColor(ThemeSlot.TOPBAR_TITLE))
}

// Our Phone fork (denwa), release then debug — the mirror of denwa's getInstalledContactsAppPackage().
fun Context.getInstalledDialerAppPackage() = dialerAppPackages.firstOrNull { isPackageInstalled(it) }

// Hands back to denwa on the given tab (a commons TAB_* mask), for the Recents entry denwa's bottom bar
// lends us during a hand-off session. Targets its MainActivity directly — the launcher intent would not
// deliver the extra when the app is already running — and swaps with no animation, so a tap on the shared
// bar reads as a tab change rather than an app switch. We are deliberately left running: both activities
// stay alive, which is what makes alternating taps a warm task swap.
fun Activity.launchDialerApp(tab: Int) {
    val dialerPackage = getInstalledDialerAppPackage() ?: return
    Intent().apply {
        setClassName(dialerPackage, DIALER_APP_MAIN_ACTIVITY)
        putExtra(OPEN_TAB_INTENT_EXTRA, tab)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        try {
            val noAnimation = ActivityOptions.makeCustomAnimation(this@launchDialerApp, 0, 0).toBundle()
            startActivity(this, noAnimation)
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }
}
