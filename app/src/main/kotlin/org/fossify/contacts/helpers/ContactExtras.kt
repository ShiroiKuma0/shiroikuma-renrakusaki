package org.fossify.contacts.helpers

import android.content.Context
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import org.fossify.commons.models.contacts.Contact

// Provider data the commons Contact model doesn't carry, loaded with one supplemental query per
// contacts refresh and kept in volatile maps keyed by raw contact id:
// - the phonetic name ("reading" / フリガナ), which drives kana-row bucketing and sorting;
// - the provider lookup key, the stable identity the per-contact sort-field override is stored under.
object ContactExtras {
    @Volatile
    var readings: Map<Int, String> = emptyMap()

    @Volatile
    var lookupKeys: Map<Int, String> = emptyMap()
}

/** Refresh [ContactExtras] from the contacts provider. Call on a background thread. */
@Suppress("TooGenericExceptionCaught", "SwallowedException") // a failed query just leaves the maps stale
fun Context.loadContactExtras() {
    val readings = HashMap<Int, String>()
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
                    val reading = listOfNotNull(cursor.getString(2), cursor.getString(3), cursor.getString(4))
                        .filter { it.isNotEmpty() }
                        .joinToString(" ")
                    if (reading.isNotEmpty()) {
                        readings[rawId] = reading
                    }
                }
            }
    } catch (e: Exception) {
        return
    }
    ContactExtras.readings = readings
    ContactExtras.lookupKeys = lookupKeys
}

/** The contact's phonetic reading, or "" when none is stored (or not yet loaded). */
fun readingOf(contact: Contact): String = ContactExtras.readings[contact.id].orEmpty()

/** The preference key the contact's sort-field override is stored under. */
fun sortFieldKeyFor(contact: Contact): String =
    ContactExtras.lookupKeys[contact.id] ?: fallbackSortFieldKey(contact.contactId)

/** Fallback identity for contacts without a provider lookup key (e.g. private/local ones). */
fun fallbackSortFieldKey(contactId: Int): String = "contact:$contactId"
