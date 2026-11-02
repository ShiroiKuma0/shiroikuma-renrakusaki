package org.fossify.contacts.helpers

import android.content.Context
import android.provider.ContactsContract
import org.fossify.commons.extensions.groupsDB
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.SMT_PRIVATE
import org.fossify.commons.models.contacts.Group

// Group memberships across a move between System and Private (dialogs/ChangeVisibilityDialog, and the
// edit screen's account change): the two stores have separate groups, so a membership is carried by the
// group's TITLE — joined to the same-named group on the other side, created there when missing.

// The private groups with these titles, created when missing.
fun Context.privateGroupsNamed(groups: List<Group>): ArrayList<Group> {
    val existing = groupsDB.getGroups()
    return groups.mapNotNull { group ->
        existing.firstOrNull { it.title == group.title }
            ?: ContactsHelper(this).createNewGroup(group.title, "", SMT_PRIVATE)
    }.distinctBy { it.id }.toCollection(ArrayList())
}

// The groups with these titles in the target system account, created there when missing. An
// account-less target cannot hold groups, so they stay behind.
fun Context.systemGroupsNamed(groups: List<Group>, accountName: String?, accountType: String?): ArrayList<Group> {
    if (groups.isEmpty() || accountName.isNullOrEmpty() || accountType.isNullOrEmpty()) return ArrayList()
    val existing = HashMap<String, Long>()
    try {
        contentResolver.query(
            ContactsContract.Groups.CONTENT_URI,
            arrayOf(ContactsContract.Groups._ID, ContactsContract.Groups.TITLE),
            "${ContactsContract.Groups.ACCOUNT_NAME} = ? AND ${ContactsContract.Groups.ACCOUNT_TYPE} = ? AND ${ContactsContract.Groups.DELETED} = 0",
            arrayOf(accountName, accountType),
            null
        )?.use { c -> while (c.moveToNext()) c.getString(1)?.let { existing.putIfAbsent(it, c.getLong(0)) } }
    } catch (ignored: Exception) {
    }
    return groups.mapNotNull { group ->
        existing[group.title]?.let { Group(it, group.title) }
            ?: ContactsHelper(this).createNewGroup(group.title, accountName, accountType)
    }.distinctBy { it.id }.toCollection(ArrayList())
}
