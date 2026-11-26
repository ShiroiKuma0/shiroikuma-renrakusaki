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
import org.fossify.contacts.helpers.ContactMover
import org.fossify.contacts.helpers.SystemMirror
import org.fossify.contacts.helpers.TemporaryVisibility
import org.fossify.contacts.helpers.privateGroupsNamed
import org.fossify.contacts.helpers.systemGroupsNamed
import java.io.FileOutputStream
import java.util.Locale

/**
 * Who can see a contact, and the one place to change it: System (every app), Private (this app and
 * the allowed sister apps), Private + visible (private, with a system copy — helpers/SystemMirror), and
 * the same for some minutes only (helpers/TemporaryVisibility). Another app's own entry beside a contact
 * (helpers/ForeignEntries) is no entry of it: every load drops those.
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
            val temporary = current == State.VISIBLE && temporaryEndOf(activity, members) != null
            activity.runOnUiThread { show(current, temporary, members) }
        }
    }

    private fun show(current: State?, temporary: Boolean, members: List<Contact>) {
        if (activity.isFinishing || activity.isDestroyed) return
        val minutes = activity.config.temporaryVisibilityMinutes
        val items = arrayListOf(
            RadioItem(State.SYSTEM.ordinal, activity.getString(R.string.visibility_system), State.SYSTEM),
            RadioItem(State.PRIVATE.ordinal, activity.getString(R.string.visibility_private), State.PRIVATE),
            RadioItem(State.VISIBLE.ordinal, activity.getString(R.string.visibility_visible), State.VISIBLE),
            // the visible state with an end (helpers/TemporaryVisibility); its value is the minutes
            RadioItem(TEMPORARY_ID, activity.getString(R.string.visibility_temporary, minutes), minutes),
        )
        // A mixed group (its entries in different states) has nothing ticked; any choice unifies it.
        val checked = if (temporary) TEMPORARY_ID else current?.ordinal ?: -1
        RadioGroupDialog(activity, items, checked, R.string.visibility_title) { chosen ->
            if (chosen is Int) {
                // chosen again while it runs, it starts the time anew
                perform { applyTo(members, State.VISIBLE, chosen) }
                return@RadioGroupDialog
            }
            val target = chosen as State
            if (target == current && !temporary) return@RadioGroupDialog
            // No confirmation: a move carries every field (Contact.extra), so nothing is lost either way.
            perform { applyTo(members, target, null) }
        }
    }

    private class Result(val ok: Boolean, val moved: Boolean, val localId: Int = 0)

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
    private fun applyTo(members: List<Contact>, target: State, temporaryMinutes: Int?): Result {
        var ok = true
        var moved = false
        val mirrored = SystemMirror.mirrors(activity).keys
        val nowPrivate = ArrayList<Int>() // the private ids the entries have after this
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
            if (step.ok && target != State.SYSTEM) nowPrivate.add(if (member.isPrivate()) member.id else step.localId)
        }
        // The copy's end: set for "visible for N minutes", forgotten for "visible" and "private".
        val ids = nowPrivate.filter { it != 0 }
        if (temporaryMinutes != null) TemporaryVisibility.start(activity, ids, temporaryMinutes) else TemporaryVisibility.forget(activity, ids)
        return Result(ok, moved)
    }

    // The moves themselves live in helpers/ContactMover, shared with the bulk "move everything to private".
    private fun moveToPrivate(member: Contact, visible: Boolean): Result =
        ContactMover.toPrivate(activity, member, visible).let { Result(it.ok, it.moved, it.localId) }

    private fun moveToSystem(member: Contact): Result =
        ContactMover.toSystem(activity, member).let { Result(it.ok, it.moved) }

    companion object {
        private const val TEMPORARY_ID = 3

        /** When a visible row's copies end (the earliest, epoch ms) — null unless every private entry has an end. */
        fun temporaryEndOf(activity: Activity, members: List<Contact>): Long? {
            val ends = members.filter { it.isPrivate() }.map { TemporaryVisibility.endOf(activity, it.id) }
            return if (ends.isEmpty() || ends.any { it == null }) null else ends.filterNotNull().minOrNull()
        }

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
