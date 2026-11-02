package org.fossify.contacts.dialogs

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.getCachePhoto
import org.fossify.commons.extensions.getVisibleContactSources
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.LocalContactsHelper
import org.fossify.commons.helpers.SMT_PRIVATE
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.RadioItem
import org.fossify.commons.models.contacts.Contact
import org.fossify.contacts.R
import org.fossify.contacts.extensions.config
import org.fossify.contacts.extensions.fetchAllContactsBlocking
import org.fossify.contacts.extensions.getCachePhotoUri
import org.fossify.contacts.helpers.SystemMirror
import org.fossify.contacts.helpers.privateGroupsNamed
import org.fossify.contacts.helpers.systemGroupsNamed
import java.io.FileOutputStream
import java.util.Locale

/**
 * Who can see a contact, and the one place to change it: System (every app), Private (this app and
 * the allowed sister apps), Private + visible (private, with a system copy — helpers/SystemMirror).
 *
 * Opened by a long-press on a contact's photo in the lists and by the visibility row on the contact
 * screen. Private ⇄ Private + visible only adds or removes the copy. Crossing between System and
 * Private is a real MOVE — a new record in the other store, the old one deleted — carrying every field,
 * so it happens at once too, without a confirmation.
 *
 * [onChanged] runs on the main thread after a change; `moved` is true when the contact now lives under a
 * different id (a screen showing it should close rather than reload).
 */
class ChangeVisibilityDialog(
    private val activity: Activity,
    private val contact: Contact,
    private val onChanged: (moved: Boolean) -> Unit,
) {
    enum class State { SYSTEM, PRIVATE, VISIBLE }

    init {
        ensureBackgroundThread {
            val members = membersOf(activity, contact)
            val current = stateOfGroup(activity, members)
            activity.runOnUiThread { show(current, members) }
        }
    }

    private fun show(current: State?, members: List<Contact>) {
        if (activity.isFinishing || activity.isDestroyed) return
        val items = arrayListOf(
            RadioItem(State.SYSTEM.ordinal, activity.getString(R.string.visibility_system), State.SYSTEM),
            RadioItem(State.PRIVATE.ordinal, activity.getString(R.string.visibility_private), State.PRIVATE),
            RadioItem(State.VISIBLE.ordinal, activity.getString(R.string.visibility_visible), State.VISIBLE),
        )
        // A mixed group (its entries in different states) has nothing ticked; any choice unifies it.
        RadioGroupDialog(activity, items, current?.ordinal ?: -1, R.string.visibility_title) { chosen ->
            val target = chosen as State
            if (target == current) return@RadioGroupDialog
            // No confirmation: a move carries every field (Contact.extra), so nothing is lost either way.
            perform { applyTo(members, target) }
        }
    }

    private class Result(val ok: Boolean, val moved: Boolean)

    private fun perform(action: () -> Result) {
        ensureBackgroundThread {
            val result = try {
                action()
            } catch (e: Exception) {
                Result(false, false)
            }
            activity.runOnUiThread {
                if (result.ok) {
                    activity.toast(R.string.visibility_changed)
                } else {
                    activity.toast(org.fossify.commons.R.string.unknown_error_occurred)
                }
                // Refresh even after a partial failure: the list must show what is really stored now.
                onChanged(result.moved)
            }
        }
    }

    /**
     * Bring EVERY entry of the row to [target]. The list merges same-name entries into one row (Settings →
     * merge duplicate contacts), and this phone holds many contacts twice; changing only the entry behind
     * the row left its twin behind, and the row then showed whichever one the merge happened to pick.
     */
    private fun applyTo(members: List<Contact>, target: State): Result {
        var ok = true
        var moved = false
        val mirrored = SystemMirror.mirrors(activity).keys
        members.forEach { member ->
            val step: Result = when {
                target == State.SYSTEM && member.isPrivate() -> moveToSystem(member)
                target == State.SYSTEM -> Result(true, false)
                !member.isPrivate() -> moveToPrivate(member, target == State.VISIBLE)
                (member.id in mirrored) == (target == State.VISIBLE) -> Result(true, false)
                else -> Result(SystemMirror.setVisible(activity, listOf(member.id), target == State.VISIBLE) > 0, false)
            }
            ok = ok && step.ok
            moved = moved || step.moved
        }
        return Result(ok, moved)
    }

    // System → Private (+ visible): every field travels inside the contact (Contact.extra, commons
    // sk11); groups are mapped by name. Insert into the private DB, then delete the system raw contact.
    private fun moveToPrivate(member: Contact, visible: Boolean): Result {
        val full = ContactsHelper(activity).getContactWithId(member.id, false) ?: return Result(false, false)
        val copy = full.copy(id = 0, source = SMT_PRIVATE, groups = activity.privateGroupsNamed(full.groups))
        if (!LocalContactsHelper(activity).insertOrUpdateContact(copy)) return Result(false, false)
        val localId = ContactsHelper.lastInsertedId
        if (!ContactsHelper(activity).deleteContacts(arrayListOf(full)) || rawExists(full.id)) {
            // The system entry would not go: undo the private copy rather than leave the contact twice.
            LocalContactsHelper(activity).deleteContactIds(mutableListOf(localId.toLong()))
            return Result(false, false)
        }
        if (visible) {
            SystemMirror.setVisible(activity, listOf(localId), true)
        }
        return Result(true, moved = true)
    }

    // Private (+ visible) → System: drop the copy, insert a full system contact into the account the
    // copies use (groups mapped by name, created there if missing), then delete the private record. The
    // photo is handed over through a fresh cache file, because a private contact's photoUri is often a
    // cache file that no longer exists.
    private fun moveToSystem(member: Contact): Result {
        val full = LocalContactsHelper(activity).getContactWithId(member.id) ?: return Result(false, false)
        SystemMirror.setVisible(activity, listOf(full.id), false)
        val (accountName, accountType) = SystemMirror.targetAccount(activity)
        val copy = full.copy(
            id = 0,
            source = accountName.orEmpty(),
            groups = activity.systemGroupsNamed(full.groups, accountName, accountType),
            photoUri = freshPhotoUri(full),
        )
        if (!ContactsHelper(activity).insertContact(copy)) return Result(false, false)
        LocalContactsHelper(activity).deleteContactIds(mutableListOf(full.id.toLong()))
        return Result(true, moved = true)
    }

    private fun rawExists(rawId: Int): Boolean = try {
        activity.contentResolver.query(
            android.provider.ContactsContract.RawContacts.CONTENT_URI, arrayOf(android.provider.ContactsContract.RawContacts._ID),
            "${android.provider.ContactsContract.RawContacts._ID} = ? AND ${android.provider.ContactsContract.RawContacts.DELETED} = 0",
            arrayOf(rawId.toString()), null
        )?.use { it.count > 0 } ?: false
    } catch (e: Exception) {
        false
    }

    private fun freshPhotoUri(contact: Contact): String {
        val bitmap = contact.photo ?: try {
            contact.photoUri.takeIf { it.isNotEmpty() }?.let { uri ->
                activity.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) }
            }
        } catch (e: Exception) {
            null
        } ?: return ""
        return try {
            val file = activity.getCachePhoto()
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, PHOTO_JPEG_QUALITY, it) }
            activity.getCachePhotoUri(file).toString()
        } catch (e: Exception) {
            ""
        }
    }

    companion object {
        private const val PHOTO_JPEG_QUALITY = 95

        /** The state of one entry. Blocking — reads the copies. */
        fun stateOf(activity: Activity, contact: Contact): State = when {
            !contact.isPrivate() -> State.SYSTEM
            SystemMirror.isMirrored(activity, contact.id) -> State.VISIBLE
            else -> State.PRIVATE
        }

        /**
         * Every entry the list shows as this one row: with duplicate merging on, all entries (system and
         * private, from the shown sources) with the same display name — our own visible copies excluded,
         * they are not entries. Without merging, just the contact. Blocking.
         */
        fun membersOf(activity: Activity, contact: Contact): List<Contact> {
            if (!activity.config.mergeDuplicateContacts) return listOf(contact)
            val name = contact.getNameToDisplay().lowercase(Locale.getDefault())
            val mirrorRawIds = SystemMirror.mirrors(activity).values.map { it.toInt() }.toHashSet()
            val sources = activity.getVisibleContactSources()
            val members = activity.fetchAllContactsBlocking().filter { other ->
                other.getNameToDisplay().lowercase(Locale.getDefault()) == name &&
                    other.source in sources &&
                    !(!other.isPrivate() && other.id in mirrorRawIds)
            }
            val self = members.any { it.id == contact.id && it.isPrivate() == contact.isPrivate() }
            return if (self) members else members + contact
        }

        /** One state for a whole row, or null when its entries disagree. Blocking. */
        fun stateOfGroup(activity: Activity, members: List<Contact>): State? {
            val mirrored = SystemMirror.mirrors(activity).keys
            val states = members.map { m ->
                when {
                    !m.isPrivate() -> State.SYSTEM
                    m.id in mirrored -> State.VISIBLE
                    else -> State.PRIVATE
                }
            }.toSet()
            return states.singleOrNull()
        }
    }
}
