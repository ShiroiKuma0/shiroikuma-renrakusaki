package org.fossify.contacts.helpers

import android.content.Context
import android.provider.CallLog
import android.provider.Telephony
import org.fossify.commons.extensions.hasPermission
import org.fossify.commons.extensions.normalizePhoneNumber
import org.fossify.commons.helpers.PERMISSION_READ_CALL_LOG
import org.fossify.commons.helpers.PERMISSION_READ_SMS
import org.fossify.commons.models.contacts.Contact

// The latest call and SMS per phone number, read straight from the system providers for the 詳 detail
// lines. Numbers are keyed by their trailing digits (denwa's comparable-suffix trick) so differing
// country-code formats still match. Loaded on each contacts refresh; empty without the permission.

data class LastEvent(val timestamp: Long, val incoming: Boolean, val missed: Boolean = false)

object ContactEvents {
    @Volatile
    var lastCalls: Map<String, LastEvent> = emptyMap()

    @Volatile
    var lastMessages: Map<String, LastEvent> = emptyMap()
}

private const val NUMBER_SUFFIX_LENGTH = 9

private fun numberKey(number: String): String {
    val normalized = number.normalizePhoneNumber()
    return if (normalized.length > NUMBER_SUFFIX_LENGTH) normalized.takeLast(NUMBER_SUFFIX_LENGTH) else normalized
}

/** Refresh [ContactEvents] from the call-log and SMS providers. Call on a background thread. */
fun Context.loadContactEvents() {
    if (hasPermission(PERMISSION_READ_CALL_LOG)) {
        ContactEvents.lastCalls = loadLatestPerNumber(
            uri = CallLog.Calls.CONTENT_URI,
            numberColumn = CallLog.Calls.NUMBER,
            dateColumn = CallLog.Calls.DATE,
            typeColumn = CallLog.Calls.TYPE,
        ) { type ->
            LastEventType(
                incoming = type != CallLog.Calls.OUTGOING_TYPE,
                missed = type == CallLog.Calls.MISSED_TYPE || type == CallLog.Calls.REJECTED_TYPE,
            )
        }
    }
    if (hasPermission(PERMISSION_READ_SMS)) {
        ContactEvents.lastMessages = loadLatestPerNumber(
            uri = Telephony.Sms.CONTENT_URI,
            numberColumn = Telephony.Sms.ADDRESS,
            dateColumn = Telephony.Sms.DATE,
            typeColumn = Telephony.Sms.TYPE,
        ) { type ->
            LastEventType(incoming = type == Telephony.Sms.MESSAGE_TYPE_INBOX)
        }
    }
}

private data class LastEventType(val incoming: Boolean, val missed: Boolean = false)

@Suppress("TooGenericExceptionCaught", "SwallowedException") // a failed query just leaves the map stale
private fun Context.loadLatestPerNumber(
    uri: android.net.Uri,
    numberColumn: String,
    dateColumn: String,
    typeColumn: String,
    interpret: (Int) -> LastEventType,
): Map<String, LastEvent> {
    val map = HashMap<String, LastEvent>()
    try {
        contentResolver.query(uri, arrayOf(numberColumn, dateColumn, typeColumn), null, null, "$dateColumn DESC")
            ?.use { cursor ->
                while (cursor.moveToNext()) {
                    val key = numberKey(cursor.getString(0).orEmpty())
                    if (key.isNotEmpty() && !map.containsKey(key)) {
                        val type = interpret(cursor.getInt(2))
                        map[key] = LastEvent(cursor.getLong(1), type.incoming, type.missed)
                    }
                }
            }
    } catch (e: Exception) {
        return map
    }
    return map
}

private fun latestFor(contact: Contact, events: Map<String, LastEvent>): LastEvent? =
    contact.phoneNumbers
        .mapNotNull { events[numberKey(it.normalizedNumber.ifEmpty { it.value })] }
        .maxByOrNull { it.timestamp }

/** The most recent call across all the contact's numbers, or null. */
fun lastCallFor(contact: Contact): LastEvent? = latestFor(contact, ContactEvents.lastCalls)

/** The most recent SMS across all the contact's numbers, or null. */
fun lastMessageFor(contact: Contact): LastEvent? = latestFor(contact, ContactEvents.lastMessages)
