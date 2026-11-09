package org.fossify.contacts.extensions

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.view.MotionEvent
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.dialogs.CallConfirmationDialog
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.helpers.PERMISSION_CALL_PHONE
import org.fossify.commons.helpers.PERMISSION_READ_PHONE_STATE
import org.fossify.contacts.R
import org.fossify.contacts.helpers.DIALER_APP_DIALER_ACTIVITY
import org.fossify.contacts.helpers.DIALER_APP_DIALPAD_ACTIVITY
import org.fossify.contacts.helpers.SwipeToCallCallback

// What setupSwipeToCall attaches to a list, kept on the list itself: the adapter is rebuilt on every
// column / layout / settings change, and each rebuild must replace these rather than pile up a second
// ItemTouchHelper (two helpers would both answer one swipe and place two calls).
private class SwipeToCallParts(
    val helper: ItemTouchHelper,
    val decoration: RecyclerView.ItemDecoration,
    val touchListener: RecyclerView.OnItemTouchListener,
)

/** Remove the swipe-to-call parts from this list, if any are attached. */
fun RecyclerView.removeSwipeToCall() {
    val parts = getTag(R.id.swipe_to_call_tag) as? SwipeToCallParts ?: return
    parts.helper.attachToRecyclerView(null)
    removeItemDecoration(parts.decoration)
    removeOnItemTouchListener(parts.touchListener)
    setTag(R.id.swipe_to_call_tag, null)
}

/** denwa's setupSwipeToCall (extensions/RecyclerView.kt), plus replacing whatever an earlier adapter attached. */
fun RecyclerView.setupSwipeToCall(callback: SwipeToCallCallback) {
    removeSwipeToCall()
    val itemTouchHelper = ItemTouchHelper(callback)
    itemTouchHelper.attachToRecyclerView(this)
    // Its own decoration, so resetting the helper never takes the "Dialing…" row away with it.
    addItemDecoration(callback.dialingDecoration)

    // Detaching forces ItemTouchHelper to clearView() every pending recover animation, dropping the
    // per-frame off-screen translation it would otherwise keep re-applying to a swiped row; the
    // callback triggers this right after a committed swipe, before the call screen pauses the list.
    callback.resetItemTouchHelper = {
        itemTouchHelper.attachToRecyclerView(null)
        itemTouchHelper.attachToRecyclerView(this)
    }

    // The list lives inside a horizontally-paging ViewPager. On touch-down over a swipeable row
    // we tell the parent not to intercept, so the row swipe wins instead of switching tabs.
    val touchListener = object : RecyclerView.SimpleOnItemTouchListener() {
        override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
            if (e.actionMasked == MotionEvent.ACTION_DOWN) {
                val child = rv.findChildViewUnder(e.x, e.y)
                val position = child?.let { rv.getChildAdapterPosition(it) } ?: RecyclerView.NO_POSITION
                if (position != RecyclerView.NO_POSITION && callback.canSwipe(position)) {
                    rv.parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            return false
        }
    }
    addOnItemTouchListener(touchListener)
    setTag(R.id.swipe_to_call_tag, SwipeToCallParts(itemTouchHelper, callback.dialingDecoration, touchListener))
}

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
