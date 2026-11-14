package org.fossify.contacts.extensions

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.dialogs.CallConfirmationDialog
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.helpers.PERMISSION_CALL_PHONE
import org.fossify.commons.helpers.PERMISSION_READ_PHONE_STATE
import org.fossify.contacts.helpers.DIALER_APP_DIALER_ACTIVITY
import org.fossify.contacts.helpers.DIALER_APP_DIALPAD_ACTIVITY

// Attaching the swipe to a list (setupSwipeToCall / removeSwipeToCall) and SwipeToCallCallback itself
// live in shiroikuma-commons (sk13), shared with denwa; placing the call stays here.

/**
 * Call [number] with SIM 1 ([useSim1]) or SIM 2, through denwa like every other call this app places,
 * after the call confirmation when that is on. The SIM goes along as EXTRA_PHONE_ACCOUNT_HANDLE, which
 * denwa's DialerActivity takes over any saved per-contact or per-number SIM. If the SIM cannot be
 * resolved (one SIM only, READ_PHONE_STATE refused) the call goes out without it and denwa picks.
 */
fun BaseSimpleActivity.callNumberWithSim(number: String, name: String, useSim1: Boolean) {
    val call = {
        handlePermission(PERMISSION_READ_PHONE_STATE) { granted ->
            val handle = if (granted) simHandle(if (useSim1) 0 else 1) else null
            callNumberViaDialerAppWithHandle(number, handle)
        }
    }
    if (config.showCallConfirmation) CallConfirmationDialog(this, name) { call() } else call()
}

// The SIM at [index] among the call-capable accounts, in the order denwa numbers them (1, 2).
@SuppressLint("MissingPermission")
private fun BaseSimpleActivity.simHandle(index: Int): PhoneAccountHandle? = try {
    getSystemService(TelecomManager::class.java).callCapablePhoneAccounts.getOrNull(index)
} catch (e: SecurityException) {
    null
}

private fun BaseSimpleActivity.callNumberViaDialerAppWithHandle(number: String, handle: PhoneAccountHandle?) {
    val dialerPackage = getInstalledDialerAppPackage()
    handlePermission(PERMISSION_CALL_PHONE) { granted ->
        val action = if (granted) Intent.ACTION_CALL else Intent.ACTION_DIAL
        val component = if (granted) DIALER_APP_DIALER_ACTIVITY else DIALER_APP_DIALPAD_ACTIVITY
        Intent(action, Uri.fromParts("tel", number, null)).apply {
            dialerPackage?.let { setClassName(it, component) }
            handle?.let { putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, it) }
            try {
                startActivity(this)
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
    }
}
