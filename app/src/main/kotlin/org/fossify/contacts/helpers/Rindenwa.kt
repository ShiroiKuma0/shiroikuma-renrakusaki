package org.fossify.contacts.helpers

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.dialogs.CallConfirmationDialog
import org.fossify.commons.extensions.isPackageInstalled
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.contacts.Contact
import org.fossify.contacts.R
import org.fossify.contacts.extensions.ThemeSlot
import org.fossify.contacts.extensions.config
import org.fossify.contacts.extensions.themeColor
import org.json.JSONArray
import org.json.JSONObject

/**
 * One SIP account of 白い熊 臨電話. [number] is the 臨 number 白い熊 gave it there (0–9, unique), or -1 when
 * it has none; [isDefault] marks the account selected in Rindenwa's account strip. [badgeColor] and
 * [badgeTextColor] are the colours 白い熊 gave that account's 臨N badge in Rindenwa (ARGB, already resolved
 * there); null from a Rindenwa older than +019, which sends none.
 */
data class RinAccount(
    val number: Int,
    val identity: String,
    val name: String,
    val registered: Boolean,
    val isDefault: Boolean,
    val badgeColor: Int? = null,
    val badgeTextColor: Int? = null,
) {
    val badge: String get() = rinBadge(number)
}

/** The badge text of a 臨 account: 臨0 … 臨9, or a bare 臨 when the number is unknown. */
fun rinBadge(number: Int) = if (number >= 0) "$RIN_MARK$number" else RIN_MARK

const val RIN_MARK = "臨"
private const val RESULT_KEY = "result"
private const val RESULT_OK = "OK"
private const val RESULT_ERROR_PREFIX = "ERROR:"
private const val ACCOUNTS_KEY = "accounts"

/**
 * 白い熊 臨電話's call door (authority [RINDENWA_CALLS_AUTHORITY], ContentResolver.call()):
 *  - "accounts" → { result: "OK", accounts: JSON array of {number, identity, name, registered, default,
 *    badge_color, badge_text_color} } — the last two since Rindenwa +019
 *  - "dial", extras { to, account? (a 臨 number as a string, or an identity) } → { result: "OK" | "ERROR:<why>" }
 * Rindenwa admits only the packages on its own allow-list (its UI settings); we are on it by default.
 * Every call here crosses processes and may have to start Rindenwa, so none of it runs on the main thread.
 */
object Rindenwa {
    private val callsUri: Uri = Uri.parse("content://$RINDENWA_CALLS_AUTHORITY")

    @Volatile
    private var memory: List<RinAccount>? = null

    fun isInstalled(context: Context) = context.isPackageInstalled(RINDENWA_PACKAGE)

    /** The account list as last heard from Rindenwa — instantly, from memory or the stored copy. */
    fun cachedAccounts(context: Context): List<RinAccount> {
        memory?.let { return it }
        return parseAccounts(context.config.rinAccountsCache).also { memory = it }
    }

    /** Asks Rindenwa for its accounts. Blocking; null when it is missing, refuses us or does not answer. */
    fun refreshAccounts(context: Context): List<RinAccount>? {
        if (!isInstalled(context)) return null
        val bundle = call(context, "accounts", null) ?: return null
        if (bundle.getString(RESULT_KEY) != RESULT_OK) return null
        val json = bundle.getString(ACCOUNTS_KEY) ?: return null
        val accounts = parseAccounts(json)
        memory = accounts
        context.config.rinAccountsCache = json
        return accounts
    }

    /**
     * Places a call to [to] through the 臨 account numbered [account], or through the one selected in
     * Rindenwa when [account] is negative. Blocking. Returns null when the call was placed, else the reason.
     */
    fun dial(context: Context, to: String, account: Int): String? {
        val extras = Bundle().apply {
            putString("to", to)
            if (account >= 0) putString("account", account.toString())
        }
        val result = call(context, "dial", extras)?.getString(RESULT_KEY) ?: return ""
        return if (result == RESULT_OK) null else result.removePrefix(RESULT_ERROR_PREFIX)
    }

    private fun call(context: Context, method: String, extras: Bundle?): Bundle? = try {
        context.contentResolver.call(callsUri, method, null, extras)
    } catch (ignored: Exception) {
        null
    }

    private fun parseAccounts(json: String): List<RinAccount> = try {
        val array = JSONArray(json.ifEmpty { "[]" })
        (0 until array.length()).map { index ->
            val o: JSONObject = array.getJSONObject(index)
            RinAccount(
                number = o.optInt("number", -1),
                identity = o.optString("identity"),
                name = o.optString("name"),
                registered = o.optBoolean("registered"),
                isDefault = o.optBoolean("default"),
                // ARGB; read as a long so a value written unsigned still lands on the same int
                badgeColor = if (o.has("badge_color")) o.optLong("badge_color").toInt() else null,
                badgeTextColor = if (o.has("badge_text_color")) o.optLong("badge_text_color").toInt() else null,
            )
        }
    } catch (ignored: Exception) {
        emptyList()
    }
}

// ---- Which 臨 account a contact is called through ----

private fun Contact.routeKeys() = phoneNumbers.map { it.normalizedNumber.ifEmpty { it.value } }.filter { it.isNotEmpty() }

/** The contact's own 臨 account (set per contact, stored per number like its SIM), or a negative number. */
fun Context.contactRinAccount(contact: Contact): Int =
    contact.routeKeys().map { config.getRinAccount(it) }.firstOrNull { it >= 0 } ?: RIN_ACCOUNT_SELECTED

fun Context.setContactRinAccount(contact: Contact, account: Int) {
    contact.routeKeys().forEach { config.setRinAccount(it, account) }
}

/** What the 臨 default dials through for [contact]: its own account, else the UI setting (negative = Rindenwa's). */
fun Context.effectiveRinAccount(contact: Contact?): Int {
    val own = contact?.let { contactRinAccount(it) } ?: RIN_ACCOUNT_SELECTED
    return if (own >= 0) own else config.rinDefaultAccount
}

/** The badge for [account]; "as selected in 臨電話" shows the number of the account Rindenwa has selected. */
fun Context.rinBadgeFor(account: Int): String {
    if (account >= 0) return rinBadge(account)
    return rinBadge(Rindenwa.cachedAccounts(this).firstOrNull { it.isDefault }?.number ?: -1)
}

/**
 * Ground and text colour of the 臨 badge for [account] (negative = the account selected in Rindenwa):
 * the pair 白い熊 set for that account in Rindenwa's UI, else our own badge colours (UI settings → Call
 * routes; yellow with blue text unless changed) — for an older Rindenwa or an account it no longer lists.
 */
fun Context.rinBadgeColors(account: Int): Pair<Int, Int> {
    val known = Rindenwa.cachedAccounts(this).firstOrNull { if (account >= 0) it.number == account else it.isDefault }
    return (known?.badgeColor ?: themeColor(ThemeSlot.ROUTE_RIN)) to (known?.badgeTextColor ?: themeColor(ThemeSlot.ROUTE_RIN_TEXT))
}

/** A picker line for an account: 臨1 · name (identity). */
fun RinAccount.pickerLabel(): String {
    val who = name.ifEmpty { identity.removePrefix("sip:") }
    return "$badge · $who"
}

// ---- Placing the call, and opening Rindenwa ----

/** Calls [number] through 臨 account [account] (negative = the one selected in Rindenwa), after the confirmation when on. */
fun BaseSimpleActivity.callNumberViaRindenwa(number: String, name: String, account: Int) {
    val call = {
        val appContext = applicationContext
        ensureBackgroundThread {
            val error = Rindenwa.dial(appContext, number, account)
            if (error != null) runOnUiThread { toast(rinErrorText(error, account), android.widget.Toast.LENGTH_LONG) }
        }
    }
    if (config.showCallConfirmation) CallConfirmationDialog(this, name) { call() } else call()
}

private fun Context.rinErrorText(error: String, account: Int): String = when (error) {
    "" -> getString(R.string.rin_error_unavailable)
    "not allowed" -> getString(R.string.rin_error_not_allowed)
    "no such account" -> getString(R.string.rin_error_no_account, rinBadge(account))
    "not ready" -> getString(R.string.rin_error_not_ready)
    else -> getString(R.string.rin_error_other, error)
}

/**
 * The 臨電話 tab: brings Rindenwa forward on its call history. A warm task swap with no animation, like
 * the 電話 tab's hand-back to denwa — we are left running, so alternating taps read as tab changes.
 */
fun Activity.launchRindenwa() {
    Intent().apply {
        setClassName(RINDENWA_PACKAGE, RINDENWA_MAIN_ACTIVITY)
        putExtra(RINDENWA_OPEN_HISTORY_EXTRA, true)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        try {
            startActivity(this, ActivityOptions.makeCustomAnimation(this@launchRindenwa, 0, 0).toBundle())
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }
}
