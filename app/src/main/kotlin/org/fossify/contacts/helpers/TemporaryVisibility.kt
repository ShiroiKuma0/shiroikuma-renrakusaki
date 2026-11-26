package org.fossify.contacts.helpers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.fossify.contacts.extensions.config
import org.fossify.contacts.receivers.TemporaryVisibilityReceiver

/**
 * "Private + visible for N minutes" (白い熊, 2026-11-26): the copy in system storage (helpers/SystemMirror)
 * with an end. It is there long enough for another app to find the contact — to start a chat in Molly —
 * and then removes itself, so no contact stays in system storage by oversight.
 *
 * The ends are kept per private contact id (Config.temporaryVisibility, device-local). The copy is removed
 * by an alarm ([TemporaryVisibilityReceiver]); EMUI may hold an alarm back, so [sweep] also runs at every
 * start of the app and with every list load. Making a contact private, or visible for good, forgets its end.
 */
object TemporaryVisibility {
    private const val REQUEST_CODE = 7301
    private const val MINUTE_MS = 60_000L

    /** Make these private contacts visible until [minutes] from now. Answers how many copies were written. Blocking. */
    fun start(context: Context, localIds: Collection<Int>, minutes: Int): Int {
        if (localIds.isEmpty()) return 0
        val mirrored = SystemMirror.mirrors(context).keys
        val changed = SystemMirror.setVisible(context, localIds.filter { it !in mirrored }, true)
        val until = System.currentTimeMillis() + minutes * MINUTE_MS
        val visibleNow = SystemMirror.mirrors(context).keys
        context.config.temporaryVisibility = context.config.temporaryVisibility + localIds.filter { it in visibleNow }.associateWith { until }
        schedule(context)
        return changed
    }

    /** These contacts' copies have no end any more (made private, or visible for good). */
    fun forget(context: Context, localIds: Collection<Int>) {
        val stored = context.config.temporaryVisibility
        if (stored.isEmpty() || localIds.none { it in stored }) return
        context.config.temporaryVisibility = stored - localIds.toSet()
        schedule(context)
    }

    /** When this contact's copy ends (epoch ms), or null when it has no end. */
    fun endOf(context: Context, localId: Int): Long? = context.config.temporaryVisibility[localId]

    /** Remove every copy whose time is up. Cheap when nothing is temporary. Blocking. */
    fun sweep(context: Context) {
        val stored = context.config.temporaryVisibility
        if (stored.isEmpty()) return
        val now = System.currentTimeMillis()
        val due = stored.filterValues { it <= now }.keys
        if (due.isNotEmpty()) {
            // setVisible(false) forgets the ends of the copies it removed; one that would not go is tried again
            SystemMirror.setVisible(context, due, false)
        }
        // an end without a copy (the contact was deleted, the copy removed some other way) is dropped
        val mirrored = SystemMirror.mirrors(context).keys
        val left = context.config.temporaryVisibility.filterKeys { it in mirrored }
        if (left.size != context.config.temporaryVisibility.size) context.config.temporaryVisibility = left
        schedule(context)
    }

    private fun schedule(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = PendingIntent.getBroadcast(
            context, REQUEST_CODE, Intent(context, TemporaryVisibilityReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val next = context.config.temporaryVisibility.values.minOrNull()
        try {
            if (next == null) {
                alarms.cancel(intent)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.coerceAtLeast(System.currentTimeMillis() + 1000), intent)
            }
        } catch (ignored: Exception) {
        }
    }
}
