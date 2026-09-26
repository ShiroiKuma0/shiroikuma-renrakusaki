package org.fossify.contacts.helpers

import android.content.ContentValues
import android.content.Context
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import org.fossify.commons.extensions.getSharedPrefs
import org.fossify.commons.extensions.normalizePhoneNumber
import org.fossify.commons.models.contacts.Contact

// Provider data the commons Contact model doesn't carry, loaded with one supplemental query per
// contacts refresh and kept in volatile maps keyed by raw contact id:
// - the phonetic name ("reading" / フリガナ), which drives kana-row bucketing and sorting;
// - the provider lookup key, which the sort-field rekey below translates away from;
// - the number flagged IS_SUPER_PRIMARY, which is the number a tap on a favorite dials.
object ContactExtras {
    @Volatile
    var readings: Map<Int, String> = emptyMap()

    @Volatile
    var lookupKeys: Map<Int, String> = emptyMap()

    // Raw contact id → the contact's default number, i.e. the platform's own "primary" choice among
    // several. Absent for a contact that has never been given one. See helpers/CallNumbers.kt.
    @Volatile
    var superPrimaryNumbers: Map<Int, String> = emptyMap()
}

/**
 * A phonetic name as the provider stores it — three separate columns. Kept split rather than as the
 * single joined string the list sorts on, because a vCard carries the parts separately too: an
 * export/import round trip preserves family / middle / given exactly instead of re-splitting a
 * joined string and guessing where the boundaries were.
 */
data class PhoneticName(val family: String, val middle: String, val given: String) {
    val isEmpty: Boolean get() = family.isEmpty() && middle.isEmpty() && given.isEmpty()

    /** Family・middle・given joined by spaces — the form the grouped list buckets and sorts on. */
    fun joined(): String = listOf(family, middle, given).filter { it.isNotEmpty() }.joinToString(" ")

    companion object {
        /**
         * Split a whitespace-separated reading into the provider's three columns: first token family,
         * last token given, anything between them middle. A single token is a family name — for a
         * mononym that is what the provider's own name editor stores.
         */
        fun fromJoined(reading: String): PhoneticName {
            val tokens = reading.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            return when {
                tokens.isEmpty() -> PhoneticName("", "", "")
                tokens.size == 1 -> PhoneticName(tokens.first(), "", "")
                else -> PhoneticName(
                    family = tokens.first(),
                    middle = tokens.subList(1, tokens.size - 1).joinToString(" "),
                    given = tokens.last(),
                )
            }
        }
    }
}

// One pass over the provider's StructuredName rows: the phonetic-name columns and the lookup key,
// both keyed by raw contact id (what commons' Contact.id holds for provider contacts). Null when the
// query failed, so a caller can tell "nothing stored" from "could not read".
@Suppress("TooGenericExceptionCaught", "SwallowedException") // a failed query just leaves the maps stale
private fun Context.queryStructuredNames(): Pair<Map<Int, PhoneticName>, Map<Int, String>>? {
    val phonetics = HashMap<Int, PhoneticName>()
    val lookupKeys = HashMap<Int, String>()
    val projection = arrayOf(
        ContactsContract.Data.RAW_CONTACT_ID,
        ContactsContract.Data.LOOKUP_KEY,
        StructuredName.PHONETIC_FAMILY_NAME,
        StructuredName.PHONETIC_MIDDLE_NAME,
        StructuredName.PHONETIC_GIVEN_NAME,
    )
    val selection = "${ContactsContract.Data.MIMETYPE} = ?"
    val selectionArgs = arrayOf(StructuredName.CONTENT_ITEM_TYPE)
    try {
        contentResolver.query(ContactsContract.Data.CONTENT_URI, projection, selection, selectionArgs, null)
            ?.use { cursor ->
                while (cursor.moveToNext()) {
                    val rawId = cursor.getInt(0)
                    cursor.getString(1)?.let { lookupKeys[rawId] = it }
                    val phonetic = PhoneticName(
                        family = cursor.getString(2).orEmpty(),
                        middle = cursor.getString(3).orEmpty(),
                        given = cursor.getString(4).orEmpty(),
                    )
                    if (!phonetic.isEmpty) {
                        phonetics[rawId] = phonetic
                    }
                }
            }
    } catch (e: Exception) {
        return null
    }
    return phonetics to lookupKeys
}

// The number each contact has been given as its default, keyed by raw contact id. Only rows carrying
// the flag are read, so this is normally a handful of entries. Null when the query failed.
@Suppress("TooGenericExceptionCaught", "SwallowedException") // a failed query just leaves the map stale
private fun Context.querySuperPrimaryNumbers(): Map<Int, String>? {
    val numbers = HashMap<Int, String>()
    val projection = arrayOf(ContactsContract.Data.RAW_CONTACT_ID, CommonDataKinds.Phone.NUMBER)
    val selection =
        "${ContactsContract.Data.MIMETYPE} = ? AND ${CommonDataKinds.Phone.IS_SUPER_PRIMARY} != 0"
    val selectionArgs = arrayOf(CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
    try {
        contentResolver.query(ContactsContract.Data.CONTENT_URI, projection, selection, selectionArgs, null)
            ?.use { cursor ->
                while (cursor.moveToNext()) {
                    val number = cursor.getString(1).orEmpty()
                    if (number.isNotEmpty()) {
                        numbers[cursor.getInt(0)] = number
                    }
                }
            }
    } catch (e: Exception) {
        return null
    }
    return numbers
}

/** Refresh [ContactExtras] from the contacts provider. Call on a background thread. */
fun Context.loadContactExtras() {
    // Independent of the name query below: a failure in one must not leave the other stale.
    querySuperPrimaryNumbers()?.let { ContactExtras.superPrimaryNumbers = it }

    val (phonetics, lookupKeys) = queryStructuredNames() ?: return
    ContactExtras.readings = phonetics.mapValues { it.value.joined() }
    ContactExtras.lookupKeys = lookupKeys
}

/**
 * Every stored phonetic name, keyed by raw contact id — what the vCard export writes out. A fresh
 * query rather than a read of [ContactExtras]: that map holds readings already joined, and an export
 * can run headlessly (the automation data door) in a process where no contacts refresh ever ran.
 * Call on a background thread.
 */
fun Context.loadPhoneticNames(): Map<Int, PhoneticName> = queryStructuredNames()?.first.orEmpty()

/**
 * The phonetic name stored on one contact's provider row, keyed by raw contact id. Null when the row
 * could not be read at all; nothing stored reads back as an empty [PhoneticName]. One small query, so
 * a screen showing a single contact need not wait for (or depend on) a full contacts refresh having
 * filled [ContactExtras]. Call on a background thread.
 */
@Suppress("TooGenericExceptionCaught", "SwallowedException") // a failed query just leaves the reading blank
fun Context.loadPhoneticName(rawId: Int): PhoneticName? {
    val projection = arrayOf(
        StructuredName.PHONETIC_FAMILY_NAME,
        StructuredName.PHONETIC_MIDDLE_NAME,
        StructuredName.PHONETIC_GIVEN_NAME,
    )
    val selection = "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?"
    val args = arrayOf(rawId.toString(), StructuredName.CONTENT_ITEM_TYPE)
    try {
        contentResolver.query(ContactsContract.Data.CONTENT_URI, projection, selection, args, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return PhoneticName(
                        family = cursor.getString(0).orEmpty(),
                        middle = cursor.getString(1).orEmpty(),
                        given = cursor.getString(2).orEmpty(),
                    )
                }
            }
    } catch (e: Exception) {
        return null
    }
    return null
}

/**
 * Write a reading onto the contact commons has just inserted. Its insertContact() builds the
 * StructuredName row without the phonetic columns and hands back nothing but a Boolean, so the row
 * has to be found again: raw contact ids are handed out in ascending order, so the newest
 * StructuredName row is the one just written. The name on that row is checked against what was
 * inserted before anything is updated — if a sync adapter slipped an insert in between, the reading
 * is dropped rather than written onto somebody else's contact.
 *
 * Shared by the vCard import and the edit screen's first save of a new contact. A private contact
 * lives in the local DB, which has no phonetic columns at all — callers skip those. Call on a
 * background thread.
 */
@Suppress("TooGenericExceptionCaught", "SwallowedException") // a reading is not worth failing a save over
fun Context.writePhoneticNameOnLatestContact(contact: Contact, phonetic: PhoneticName) {
    val projection = arrayOf(
        ContactsContract.Data._ID,
        StructuredName.GIVEN_NAME,
        StructuredName.FAMILY_NAME,
    )
    val selection = "${ContactsContract.Data.MIMETYPE} = ?"
    val args = arrayOf(StructuredName.CONTENT_ITEM_TYPE)
    // Deliberately no LIMIT in the sort order — OEM providers have been known to choke on one, and
    // the first row of a descending cursor is the same answer.
    val order = "${ContactsContract.Data.RAW_CONTACT_ID} DESC"

    try {
        var dataId = -1L
        contentResolver.query(ContactsContract.Data.CONTENT_URI, projection, selection, args, order)
            ?.use { cursor ->
                if (
                    cursor.moveToFirst() &&
                    cursor.getString(1).orEmpty() == contact.firstName &&
                    cursor.getString(2).orEmpty() == contact.surname
                ) {
                    dataId = cursor.getLong(0)
                }
            }

        if (dataId < 0) {
            return
        }

        val values = ContentValues().apply {
            put(StructuredName.PHONETIC_FAMILY_NAME, phonetic.family)
            put(StructuredName.PHONETIC_MIDDLE_NAME, phonetic.middle)
            put(StructuredName.PHONETIC_GIVEN_NAME, phonetic.given)
        }
        contentResolver.update(
            ContactsContract.Data.CONTENT_URI,
            values,
            "${ContactsContract.Data._ID} = ?",
            arrayOf(dataId.toString()),
        )
    } catch (e: Exception) {
        return
    }
}

/**
 * The raw contact id of the contact commons has just inserted — the same problem
 * [writePhoneticNameOnLatestContact] solves, and the same answer: insertContact() hands back a
 * Boolean, ids are handed out in ascending order, so the newest StructuredName row is the one just
 * written. Its name is checked against what was inserted, so a sync adapter's insert slipping in
 * between yields null rather than somebody else's contact. Call on a background thread.
 */
@Suppress("TooGenericExceptionCaught", "SwallowedException") // no id just means the caller asks nothing
fun Context.latestInsertedRawContactId(contact: Contact): Int? {
    val projection = arrayOf(
        ContactsContract.Data.RAW_CONTACT_ID,
        StructuredName.GIVEN_NAME,
        StructuredName.FAMILY_NAME,
    )
    val selection = "${ContactsContract.Data.MIMETYPE} = ?"
    val args = arrayOf(StructuredName.CONTENT_ITEM_TYPE)
    val order = "${ContactsContract.Data.RAW_CONTACT_ID} DESC"

    try {
        contentResolver.query(ContactsContract.Data.CONTENT_URI, projection, selection, args, order)
            ?.use { cursor ->
                if (
                    cursor.moveToFirst() &&
                    cursor.getString(1).orEmpty() == contact.firstName &&
                    cursor.getString(2).orEmpty() == contact.surname
                ) {
                    return cursor.getInt(0)
                }
            }
    } catch (e: Exception) {
        return null
    }
    return null
}

/** The contact's phonetic reading, or "" when none is stored (or not yet loaded). */
fun readingOf(contact: Contact): String = ContactExtras.readings[contact.id].orEmpty()

/**
 * The preference key the contact's sort-field override is stored under.
 *
 * Device-independent by construction, so an override survives a backup/restore onto another phone.
 * This used to be the provider's lookup key, which is minted per device: after a restore every
 * override pointed at a contact that no longer existed and silently did nothing. The display name is
 * what an override is about and comes through a vCard round trip verbatim; the first phone number
 * backs it up for a contact with no name, and the contact id is the last resort for one with neither.
 */
fun sortFieldKeyFor(contact: Contact): String {
    val name = contact.getNameToDisplay().trim()
    if (name.isNotEmpty()) {
        return "name:$name"
    }

    val number = contact.phoneNumbers.firstOrNull()
        ?.let { it.normalizedNumber.ifEmpty { it.value.normalizePhoneNumber() } }
        .orEmpty()
    if (number.isNotEmpty()) {
        return "num:$number"
    }

    return fallbackSortFieldKey(contact.contactId)
}

/** Last-resort identity for a contact carrying neither a name nor a number. */
fun fallbackSortFieldKey(contactId: Int): String = "contact:$contactId"

/**
 * Move the per-contact sort-field overrides off the provider lookup keys older builds stored them
 * under and onto [sortFieldKeyFor]'s device-independent keys. Call on a background thread with the
 * freshly loaded contacts, after [loadContactExtras] has filled in the lookup keys.
 *
 * Idempotent and unflagged rather than a one-shot: once an entry has moved, its old key reads back as
 * DEFAULT and the pass does nothing, so running it on every refresh costs a handful of in-memory
 * prefs reads and picks up contacts that were not visible the first time round.
 */
fun Context.migrateSortFieldKeys(contacts: List<Contact>) {
    val prefs = getSharedPrefs()
    val editor = prefs.edit()
    var moved = 0
    contacts.forEach { contact ->
        val oldKey = ContactExtras.lookupKeys[contact.id] ?: fallbackSortFieldKey(contact.contactId)
        val newKey = sortFieldKeyFor(contact)
        if (oldKey == newKey) {
            return@forEach
        }

        val stored = prefs.getInt(SORT_FIELD_PREFIX + oldKey, SORT_FIELD_DEFAULT)
        if (stored != SORT_FIELD_DEFAULT) {
            editor.putInt(SORT_FIELD_PREFIX + newKey, stored)
            editor.remove(SORT_FIELD_PREFIX + oldKey)
            moved++
        }
    }

    if (moved > 0) {
        editor.apply()
    }
}
