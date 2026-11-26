package org.fossify.contacts.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.contacts.helpers.TemporaryVisibility

/** The alarm at the end of a temporary visible copy (helpers/TemporaryVisibility): removes what is due. */
class TemporaryVisibilityReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        ensureBackgroundThread {
            try {
                TemporaryVisibility.sweep(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }
}
