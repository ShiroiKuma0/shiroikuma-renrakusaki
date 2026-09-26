package org.fossify.contacts.helpers

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.telephony.PhoneNumberUtils
import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.contacts.Contact

/**
 * Which of a contact's numbers a tap actually dials.
 *
 * The choice is stored as the platform's own `IS_SUPER_PRIMARY` flag — "the default number for this
 * contact" — rather than as a preference of ours. Every app honours it: denwa, the system dialer and
 * Android Auto's own dialer all call the same number we do, and nothing new has to be agreed between
 * the two forks. (The per-contact SIM had to be invented because Android has no concept of one; a
 * default number it does have.)
 */

/**
 * The number a tap on this contact places a call to: the platform's super-primary, else a number
 * flagged primary, else the first mobile, else the first number there is. Null only when the contact
 * holds no number at all — the caller opens the contact then, rather than doing nothing.
 */
fun Contact.numberToCall(): PhoneNumber? {
    val superPrimary = ContactExtras.superPrimaryNumbers[id]
    if (!superPrimary.isNullOrEmpty()) {
        phoneNumbers.firstOrNull { it.matches(superPrimary) }?.let { return it }
    }

    return phoneNumbers.firstOrNull { it.isPrimary }
        ?: phoneNumbers.firstOrNull { it.type == CommonDataKinds.Phone.TYPE_MOBILE }
        ?: phoneNumbers.firstOrNull()
}

/**
 * Whether it is worth asking 白い熊 which number this contact is called on: it has a choice to make
 * (more than one number) and has not made one yet. Private (device-only) contacts are excluded —
 * they have no provider row to carry the flag.
 */
fun Contact.needsCallNumberChoice(): Boolean {
    return !isPrivate() && phoneNumbers.size > 1 && ContactExtras.superPrimaryNumbers[id].isNullOrEmpty()
}

/**
 * Store [number] as the contact's default number, and mirror it into [ContactExtras] so the list
 * dials it without waiting for the next contacts refresh. Writing `IS_SUPER_PRIMARY` clears the flag
 * from the contact's other numbers — the provider sees to that itself. Call on a background thread.
 */
@Suppress("TooGenericExceptionCaught", "SwallowedException") // a failed write just leaves the old default
fun Context.setDefaultCallNumber(contact: Contact, number: PhoneNumber): Boolean {
    val rowId = findPhoneDataRowId(contact.id, number) ?: return false
    val values = ContentValues().apply {
        put(CommonDataKinds.Phone.IS_SUPER_PRIMARY, 1)
        put(CommonDataKinds.Phone.IS_PRIMARY, 1)
    }

    return try {
        val uri = ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, rowId)
        val updated = contentResolver.update(uri, values, null, null) > 0
        if (updated) {
            ContactExtras.superPrimaryNumbers = ContactExtras.superPrimaryNumbers + (contact.id to number.value)
        }
        updated
    } catch (e: Exception) {
        false
    }
}

// The Data row holding this number, among the contact's phone rows. Matched on the stored string
// first and on a phone-number comparison second, so formatting differences don't lose the row.
@Suppress("TooGenericExceptionCaught", "SwallowedException")
private fun Context.findPhoneDataRowId(rawContactId: Int, number: PhoneNumber): Long? {
    val projection = arrayOf(ContactsContract.Data._ID, CommonDataKinds.Phone.NUMBER)
    val selection = "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?"
    val args = arrayOf(rawContactId.toString(), CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
    var fallback: Long? = null

    try {
        contentResolver.query(ContactsContract.Data.CONTENT_URI, projection, selection, args, null)
            ?.use { cursor ->
                while (cursor.moveToNext()) {
                    val stored = cursor.getString(1).orEmpty()
                    if (stored == number.value) {
                        return cursor.getLong(0)
                    }
                    if (fallback == null && PhoneNumberUtils.compare(stored, number.value)) {
                        fallback = cursor.getLong(0)
                    }
                }
            }
    } catch (e: Exception) {
        return null
    }
    return fallback
}

// Same two-step match the row lookup uses, for comparing against the stored super-primary number.
private fun PhoneNumber.matches(other: String): Boolean {
    return value == other || normalizedNumber == other || PhoneNumberUtils.compare(value, other)
}
