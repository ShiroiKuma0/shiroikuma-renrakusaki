package org.fossify.contacts.helpers

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.RawContacts
import org.fossify.commons.helpers.LocalContactsHelper
import org.fossify.commons.helpers.SMT_PRIVATE
import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.extensions.normalizePhoneNumber
import org.fossify.commons.extensions.getEmptyContact

/**
 * What is left in system storage — Android's contact store and the SIM cards themselves — and the
 * housekeeping that empties it (UI settings → Maintenance). Our own visible copies (SystemMirror) are
 * not counted and never touched: they are in system storage on purpose.
 *
 * The SIM cards are read and written through the telephony IccProvider (`content://icc/adn/subId/N`).
 * Huawei mirrors a card's entries into its sim accounts, but deleting such a mirrored entry only marks it
 * deleted in Android's store — the card keeps it. So clearing the cards is a step of its own.
 */
object SystemStorage {
    class Status(val byAccount: Map<String, Int>, val simCards: Map<Int, Int>, val pendingDeletes: Int) {
        val systemTotal: Int get() = byAccount.values.sum()
        val simTotal: Int get() = simCards.values.sum()
        val isClean: Boolean get() = systemTotal == 0 && simTotal == 0 && pendingDeletes == 0
    }

    private class SimEntry(val subId: Int, val name: String, val number: String)

    private const val MAX_SUB_ID = 8

    /** Count everything outside private storage. Blocking. */
    fun status(context: Context): Status {
        val mirrors = SystemMirror.mirrors(context).values.toHashSet()
        val byAccount = LinkedHashMap<String, Int>()
        var pending = 0
        try {
            context.contentResolver.query(
                RawContacts.CONTENT_URI, arrayOf(RawContacts._ID, RawContacts.ACCOUNT_NAME, RawContacts.DELETED), null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    if (c.getInt(2) != 0) {
                        pending++
                        continue
                    }
                    if (c.getLong(0) in mirrors) continue
                    val account = c.getString(1) ?: ""
                    byAccount[account] = (byAccount[account] ?: 0) + 1
                }
            }
        } catch (ignored: Exception) {
        }
        val sims = simEntries(context).groupingBy { it.subId }.eachCount()
        return Status(byAccount, sims, pending)
    }

    private fun simEntries(context: Context): List<SimEntry> {
        val entries = ArrayList<SimEntry>()
        for (sub in 1..MAX_SUB_ID) {
            try {
                context.contentResolver.query(Uri.parse("content://icc/adn/subId/$sub"), null, null, null, null)?.use { c ->
                    val nameCol = c.getColumnIndex("name")
                    val numberCol = c.getColumnIndex("number")
                    while (c.moveToNext()) {
                        val name = if (nameCol >= 0) c.getString(nameCol).orEmpty() else ""
                        val number = if (numberCol >= 0) c.getString(numberCol).orEmpty() else ""
                        if (name.isNotEmpty() || number.isNotEmpty()) entries.add(SimEntry(sub, name, number))
                    }
                }
            } catch (ignored: Exception) {
                // no such subscription, or no SIM phonebook behind it
            }
        }
        return entries
    }

    /**
     * Empty the SIM cards. An entry whose number no private contact has yet is first saved as a private
     * contact, so nothing on a card is lost. Answers how many card entries were removed. Blocking.
     */
    fun clearSimCards(context: Context): Int {
        val entries = simEntries(context)
        if (entries.isEmpty()) return 0
        val helper = LocalContactsHelper(context)
        val known = helper.getAllContacts().flatMap { it.phoneNumbers }
            .map { it.normalizedNumber.ifEmpty { it.value.normalizePhoneNumber() } }.toHashSet()
        var removed = 0
        entries.forEach { entry ->
            val normalized = entry.number.normalizePhoneNumber()
            if (entry.number.isNotEmpty() && normalized !in known && entry.number !in known) {
                val contact = context.getEmptyContact().apply {
                    source = SMT_PRIVATE
                    firstName = entry.name
                    phoneNumbers = arrayListOf(
                        PhoneNumber(entry.number, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE, "", normalized)
                    )
                }
                if (!helper.insertOrUpdateContact(contact)) return@forEach // never delete what could not be saved
                known.add(normalized)
            }
            try {
                val where = "tag='${entry.name.replace("'", "''")}' AND number='${entry.number.replace("'", "''")}'"
                if (context.contentResolver.delete(Uri.parse("content://icc/adn/subId/${entry.subId}"), where, null) > 0) removed++
            } catch (ignored: Exception) {
            }
        }
        return removed
    }

    /** Remove raw contacts left marked deleted (a SIM account's deletes wait forever on Huawei). Blocking. */
    fun purgeDeleted(context: Context): Int = try {
        val uri = RawContacts.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build()
        context.contentResolver.delete(uri, "${RawContacts.DELETED} = 1", null)
    } catch (e: Exception) {
        0
    }

    /**
     * Everything to private: system contacts (phone account and SIM accounts) moved one by one with a
     * backup first, then the cards emptied and the leftover delete marks purged. Answers (moved, failed).
     */
    fun moveEverythingToPrivate(context: Context, onProgress: (Int, Int) -> Unit): Pair<Int, Int> {
        val result = ContactMover.moveAllToPrivate(context, onProgress)
        clearSimCards(context)
        purgeDeleted(context)
        return result
    }
}
