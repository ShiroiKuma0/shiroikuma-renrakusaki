package org.fossify.contacts.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.provider.CallLog.Calls
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import org.fossify.commons.R as CommonsR
import org.fossify.commons.extensions.formatDateOrTime
import org.fossify.commons.extensions.hasPermission
import org.fossify.commons.extensions.normalizePhoneNumber
import org.fossify.commons.extensions.toImperialDateString
import org.fossify.commons.helpers.PERMISSION_READ_CALL_LOG
import org.fossify.commons.helpers.PERMISSION_READ_PHONE_STATE
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

// The calls with one contact, for the 発着信履歴 section of the contact screen — read straight from the
// system call log, the same rows denwa's Recents lists, and formatted the way denwa formats them
// (the call query and SIM lookup ported from denwa's RecentsHelper — keep them in step; the formats are
// shared through commons).

data class ContactCall(
    val number: String,
    val timestamp: Long,
    val duration: Int,
    val type: Int,
    /** 1-based SIM, or -1 when the call log does not say (or there is one SIM). */
    val simId: Int,
)

private const val NUMBER_SUFFIX_LENGTH = 9

private fun numberKey(number: String): String {
    val normalized = number.normalizePhoneNumber()
    return if (normalized.length > NUMBER_SUFFIX_LENGTH) normalized.takeLast(NUMBER_SUFFIX_LENGTH) else normalized
}

/** True when [a] and [b] are the same number, written either way (with or without the country code). */
fun sameNumber(a: String, b: String): Boolean {
    val keyA = numberKey(a)
    return keyA.isNotEmpty() && keyA == numberKey(b)
}

/**
 * The newest [limit] calls with any of [numbers], newest first. Empty without READ_CALL_LOG.
 * Call on a background thread.
 */
@Suppress("TooGenericExceptionCaught", "SwallowedException") // a failed query = no history shown
fun Context.loadContactCalls(numbers: Collection<String>, limit: Int): List<ContactCall> {
    if (!hasPermission(PERMISSION_READ_CALL_LOG) || numbers.isEmpty() || limit <= 0) {
        return emptyList()
    }
    val keys = numbers.map { numberKey(it) }.filter { it.isNotEmpty() }.toSet()
    if (keys.isEmpty()) return emptyList()

    val simLookup = simAccountLookup()
    val multipleSims = simLookup.values.toSet().size > 1
    val calls = ArrayList<ContactCall>()
    val projection = arrayOf(Calls.NUMBER, Calls.DATE, Calls.DURATION, Calls.TYPE, Calls.PHONE_ACCOUNT_ID)
    try {
        contentResolver.query(Calls.CONTENT_URI, projection, null, null, "${Calls.DATE} DESC")?.use { cursor ->
            while (cursor.moveToNext() && calls.size < limit) {
                val number = cursor.getString(0).orEmpty()
                if (numberKey(number) !in keys) continue
                val accountId = cursor.getString(4)
                calls.add(
                    ContactCall(
                        number = number,
                        timestamp = cursor.getLong(1),
                        duration = cursor.getInt(2),
                        type = cursor.getInt(3),
                        simId = if (multipleSims) simLookup[accountId] ?: -1 else -1,
                    )
                )
            }
        }
    } catch (e: Exception) {
        return emptyList()
    }
    return calls
}

/**
 * Call-log PHONE_ACCOUNT_ID → 1-based SIM, the way denwa's buildSIMAccountLookupMap builds it: the
 * telecom handle id, the subscription id, the ICC id and — because Huawei writes the slot there — the
 * 0-based slot index all lead to the same SIM.
 */
@SuppressLint("MissingPermission")
@Suppress("TooGenericExceptionCaught", "SwallowedException")
private fun Context.simAccountLookup(): Map<String, Int> {
    val map = HashMap<String, Int>()
    if (!hasPermission(PERMISSION_READ_PHONE_STATE)) return map
    val handles = try {
        getSystemService(TelecomManager::class.java)?.callCapablePhoneAccounts.orEmpty()
    } catch (e: Exception) {
        emptyList()
    }
    handles.forEachIndexed { index, handle -> map[handle.id] = index + 1 }
    try {
        val subscriptions = getSystemService(SubscriptionManager::class.java)?.activeSubscriptionInfoList.orEmpty()
        for (info in subscriptions) {
            val subId = info.subscriptionId.toString()
            val sim = map[subId] ?: (info.simSlotIndex + 1).takeIf { info.simSlotIndex in handles.indices } ?: continue
            map.putIfAbsent(subId, sim)
            info.iccId?.takeIf { it.isNotEmpty() }?.let { map.putIfAbsent(it, sim) }
            map.putIfAbsent(info.simSlotIndex.toString(), sim)
        }
    } catch (e: Exception) {
        // the handles alone are still a usable map
    }
    return map
}

// The time, duration and date formats themselves are commons' CallFormats.kt (sk13), shared with denwa.

/** The day heading above a group of calls: 今日 / 昨日, else the date as denwa's Recents writes it. */
fun Long.callDayHeading(context: Context, imperial: Boolean): String {
    val date = toCallLocalDate()
    val today = LocalDate.now()
    return when {
        date == today -> context.getString(CommonsR.string.today)
        date == today.minusDays(1) -> context.getString(CommonsR.string.yesterday)
        imperial -> toImperialDateString()
        else -> formatDateOrTime(context, hideTimeOnOtherDays = true, showCurrentYear = false)
    }
}

fun Long.callDayKey(): LocalDate = toCallLocalDate()

private fun Long.toCallLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

/**
 * The region a number stored without a country code is read in — the SIM's country, then the
 * network's, and only then the locale (denwa's getCallLogRegion: a Japanese locale over Czech SIMs).
 */
@Suppress("TooGenericExceptionCaught", "SwallowedException")
fun Context.callLogRegion(): String {
    val telephony = try {
        getSystemService(TelephonyManager::class.java)
    } catch (e: Exception) {
        null
    }
    val fromSim = try {
        telephony?.simCountryIso
    } catch (e: Exception) {
        null
    }
    val fromNetwork = try {
        telephony?.networkCountryIso
    } catch (e: Exception) {
        null
    }
    return listOfNotNull(fromSim, fromNetwork).firstOrNull { it.isNotEmpty() }?.uppercase(Locale.US)
        ?: Locale.getDefault().country
}
