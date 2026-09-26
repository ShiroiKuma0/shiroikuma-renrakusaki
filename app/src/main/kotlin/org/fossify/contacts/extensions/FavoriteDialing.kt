package org.fossify.contacts.extensions

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.PERMISSION_CALL_PHONE
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.contacts.Contact
import org.fossify.contacts.R
import org.fossify.contacts.dialogs.ChooseCallNumberDialog
import org.fossify.contacts.helpers.DIALER_APP_DIALER_ACTIVITY
import org.fossify.contacts.helpers.DIALER_APP_DIALPAD_ACTIVITY
import org.fossify.contacts.helpers.TAP_TO_CALL_ALWAYS
import org.fossify.contacts.helpers.TAP_TO_CALL_IN_CAR
import org.fossify.contacts.helpers.TAP_TO_CALL_NEVER
import org.fossify.contacts.helpers.needsCallNumberChoice
import org.fossify.contacts.helpers.setDefaultCallNumber

/**
 * Tap-to-dial on the Favorites grid: whether it is on right now, how a call is actually placed, and
 * the "which number?" prompt that keeps a tap from ever needing a dialog while driving.
 */

/**
 * Whether a tap on a favorite should place a call at this moment. Read per gesture rather than kept
 * in a field: the car can connect (or be unplugged) while the grid is on screen, and the very next
 * tap should already behave the new way.
 */
fun Context.isFavoriteTapToDialActive(): Boolean = when (config.tapFavoriteToCall) {
    TAP_TO_CALL_ALWAYS -> true
    TAP_TO_CALL_IN_CAR -> isCarProjectionActive()
    else -> false
}

/**
 * Place a call through denwa's own dial screen.
 *
 * The intent names the component explicitly, and all three reasons matter: a bare ACTION_CALL raises
 * the app chooser; going through DialerActivity is what applies the per-contact SIM, so the badge on
 * the tile is the SIM actually used; and DialerActivity is where denwa raises its on-phone call
 * screen while Android Auto is projecting. Without denwa installed there is nothing to hand to, so
 * the stock path takes over.
 */
fun BaseSimpleActivity.callNumberViaDialerApp(number: String) {
    val dialerPackage = getInstalledDialerAppPackage()

    handlePermission(PERMISSION_CALL_PHONE) { granted ->
        // Without the permission ACTION_CALL is refused outright, so hand denwa the number to dial
        // rather than dropping the tap on the floor. That fallback goes to the dialpad, not to the
        // dial screen: DialerActivity answers ACTION_CALL alone and toasts at anything else.
        val action = if (granted) Intent.ACTION_CALL else Intent.ACTION_DIAL
        val component = if (granted) DIALER_APP_DIALER_ACTIVITY else DIALER_APP_DIALPAD_ACTIVITY
        Intent(action, Uri.fromParts("tel", number, null)).apply {
            // Without denwa installed there is no component to name and the intent goes out the way
            // it would from anywhere else — the feature is denwa's to complete.
            dialerPackage?.let { setClassName(it, component) }
            try {
                startActivity(this)
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
    }
}

/**
 * Ask which number to call, one contact after another, and store each answer as that contact's
 * default number. Contacts with nothing to choose are dropped first, so a caller may pass anything.
 * Skipping a contact (dismissing its dialog) moves on to the next rather than ending the walk.
 */
fun BaseSimpleActivity.promptForCallNumbers(contacts: List<Contact>, onDone: () -> Unit = {}) {
    val queue = ArrayDeque(contacts.filter { it.needsCallNumberChoice() })

    fun askNext() {
        val contact = queue.removeFirstOrNull()
        if (contact == null) {
            onDone()
            return
        }

        ChooseCallNumberDialog(this, contact, onSkip = { askNext() }) { number ->
            ensureBackgroundThread {
                setDefaultCallNumber(contact, number)
                runOnUiThread { askNext() }
            }
        }
    }

    askNext()
}

/**
 * The sweep, run from the Favorites overflow: every favorite that has several numbers and no default
 * yet gets asked about. Re-runnable — once every favorite has a number it just says so.
 */
fun BaseSimpleActivity.runCallNumberSweep(favorites: List<Contact>) {
    config.favoriteNumbersSweepOffered = true
    val pending = favorites.filter { it.needsCallNumberChoice() }
    if (pending.isEmpty()) {
        toast(R.string.favorites_all_have_numbers)
        return
    }

    promptForCallNumbers(pending)
}

/**
 * Offer the sweep once, the first time the favorites are shown with tap-to-dial switched on: that is
 * the moment the numbers start to matter. Declining is remembered — the overflow entry is how it is
 * run afterwards — and nothing here blocks the list from drawing.
 */
fun BaseSimpleActivity.offerCallNumberSweepOnce(favorites: List<Contact>) {
    if (config.favoriteNumbersSweepOffered || config.tapFavoriteToCall == TAP_TO_CALL_NEVER) {
        return
    }

    val pending = favorites.filter { it.needsCallNumberChoice() }
    if (pending.isEmpty()) {
        return
    }

    config.favoriteNumbersSweepOffered = true
    val message = getString(R.string.set_favorite_numbers_confirmation, pending.size)
    ConfirmationDialog(this, message, 0, org.fossify.commons.R.string.ok, org.fossify.commons.R.string.later) {
        promptForCallNumbers(pending)
    }
}
