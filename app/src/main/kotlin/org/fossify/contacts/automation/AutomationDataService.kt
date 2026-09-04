package org.fossify.contacts.automation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.contacts.R
import org.fossify.contacts.helpers.ExportCancelledException
import org.fossify.contacts.helpers.SettingsExport

/**
 * Where an automation data export or import actually runs.
 *
 * ## Why a foreground service and not the provider call
 *
 * The call returns in milliseconds; this can run for minutes. Two hard reasons it cannot be done anywhere
 * cheaper:
 *
 * - **A binder call holds the caller.** 応用管理 is drawing a list; a multi-minute synchronous call would
 *   freeze its UI, report no progress and refuse cancellation.
 * - **A backgrounded app writing for minutes is frozen mid-stream on this phone**, which yields a
 *   truncated archive underneath a success reply — the worst possible failure, because it is
 *   indistinguishable from a good backup until the day it is restored.
 *
 * ## The descriptor
 *
 * Already duplicated by [AutomationProvider] before it got here, because the original belongs to the
 * binder transaction and is closed the moment `call()` returns. This service owns the copy and closes it
 * in a `finally` — leaking one would hold the caller's file open indefinitely, and the caller cannot
 * checksum or encrypt a file that is still open.
 *
 * ## The reply
 *
 * The plain reply broadcast the family already proved on this EMUI — the ordered-broadcast result is
 * severed between third-party apps here, so it is not an option (see BackupContactsReceiver for the
 * measurement). Exactly one terminal answer per job, whatever path reaches it.
 */
class AutomationDataService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val jobId = intent?.getStringExtra(EXTRA_JOB) ?: return stop(startId)
        // Taken out of the handover map, not the Intent: a ParcelFileDescriptor in an Intent extra is
        // duplicated by the system on delivery and the copy's lifetime stops being ours to reason about.
        val fd = HANDOVER.remove(jobId) ?: return stop(startId)
        val importing = intent.getBooleanExtra(EXTRA_IMPORTING, false)
        val items = intent.getStringExtra(AutomationProvider.KEY_ITEMS)
        val replyAction = intent.getStringExtra(AutomationProvider.KEY_REPLY_ACTION)
        val replyPackage = intent.getStringExtra(AutomationProvider.KEY_REPLY_PACKAGE)

        val replied = AtomicBoolean(false)
        fun reply(result: String) {
            // Exactly one terminal answer per job — a synchronous failure and an asynchronous success must
            // never both fire. The same guard the broadcast contract has carried since the first sister app.
            if (!replied.compareAndSet(false, true)) return
            Log.i(AutomationProvider.TAG, "job $jobId → $result")
            AutomationJobs.finish(jobId)
            if (replyAction.isNullOrEmpty() || replyPackage.isNullOrEmpty()) return
            try {
                sendBroadcast(
                    Intent(replyAction)
                        .setPackage(replyPackage)
                        // Without this a caller that has been backgrounded never hears the answer, and on
                        // a clean phone the caller may not have been launched at all.
                        .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                        .putExtra(AutomationProvider.KEY_JOB_ID, jobId)
                        .putExtra(AutomationProvider.KEY_RESULT, result)
                )
            } catch (e: Exception) {
                Log.w(AutomationProvider.TAG, "reply broadcast failed: $e")
            }
        }

        // AFTER `reply` exists, and guarded. `startForeground` throws when the declared
        // foregroundServiceType disagrees with the manifest, and on API 31+ it can be refused outright for
        // a service started from the background — which a provider `call()` always is. The descriptor has
        // already left HANDOVER by this point, so nothing else would ever close it, and a throw out of
        // `onStartCommand` would kill the service with the caller still waiting for an answer it will
        // never get.
        try {
            startForeground(NOTIFICATION_ID, notification(importing))
        } catch (e: Exception) {
            runCatching { fd.close() }
            Log.w(AutomationProvider.TAG, "cannot go foreground: $e")
            reply("ERROR:cannot go foreground: ${e.javaClass.simpleName}")
            return stop(startId)
        }

        ensureBackgroundThread {
            try {
                if (importing) runImport(fd, ::reply) else runExport(jobId, fd, items, ::reply)
            } catch (cancelled: ExportCancelledException) {
                Log.i(AutomationProvider.TAG, "job $jobId unwound: ${cancelled.message}")
                reply("ERROR:cancelled")
            } catch (t: Throwable) {
                reply("ERROR:${reason(t)}")
            } finally {
                runCatching { fd.close() }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    /**
     * Write the selected categories straight into the caller's descriptor.
     *
     * The byte count is accumulated as it goes rather than stat'ed afterwards: the caller owns the file
     * and we may not be able to see it at all — it can be an anonymous pipe, or a descriptor into a
     * directory this app cannot list.
     */
    private fun runExport(jobId: String, fd: ParcelFileDescriptor, items: String?, reply: (String) -> Unit) {
        val cats = resolve(items)
        if (cats == null) {
            reply("ERROR:unknown category in items: $items")
            return
        }

        var written = 0L
        ParcelFileDescriptor.AutoCloseOutputStream(fd).use { out ->
            val counting = object : OutputStream() {
                override fun write(b: Int) {
                    out.write(b)
                    written++
                }

                override fun write(b: ByteArray, off: Int, len: Int) {
                    out.write(b, off, len)
                    written += len
                }
            }
            SettingsExport.exportBlocking(
                context = this,
                items = cats,
                out = counting,
                isCancelled = { AutomationJobs.isCancelled(jobId) },
            )
        }
        reply("OK:$written|${cats.size} categories")
    }

    /**
     * Read the whole archive before touching anything.
     *
     * [SettingsExport.import] wants the bytes, and that is the right shape here for a reason beyond
     * convenience: a partial read that failed halfway would otherwise import half an archive, and a
     * half-restored app is worse than one that refused.
     *
     * The latch keeps this service — and its foreground notification — alive until the import finishes,
     * since [SettingsExport.import] answers on a thread of its own.
     */
    private fun runImport(fd: ParcelFileDescriptor, reply: (String) -> Unit) {
        val bytes = ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
        if (bytes.isEmpty()) {
            reply("ERROR:empty archive")
            return
        }
        // Every category the archive actually carries, not every category we know about: asking for one
        // the archive lacks is how a restore ends up reporting success over nothing.
        val present = SettingsExport.categoriesIn(bytes)
        if (present.isEmpty()) {
            reply("ERROR:archive carries no categories")
            return
        }

        val latch = CountDownLatch(1)
        SettingsExport.import(this, bytes, present) { result ->
            result
                .onSuccess { reply("OK:$it") }
                .onFailure { reply("ERROR:${reason(it)}") }
            latch.countDown()
        }
        latch.await()
        // The caller force-stops us straight after this, deliberately and on its side: a running process
        // writes its cached SharedPreferences back out at orderly shutdown and would silently undo the
        // import that just happened.
    }

    /** Absent or empty means this app's default set — exactly the items it reports as `on`. */
    private fun resolve(items: String?): Set<SettingsExport.Item>? {
        val ids = items.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (ids.isEmpty()) return SettingsExport.Item.defaultSelection
        val found = ids.mapNotNull { SettingsExport.Item.byId(it) }.toSet()
        return found.takeIf { it.size == ids.distinct().size }
    }

    private fun reason(e: Throwable): String =
        (e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName).replace('\n', ' ')

    private fun notification(importing: Boolean): Notification {
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.automation), NotificationManager.IMPORTANCE_LOW)
        )
        val title = getString(if (importing) R.string.automation_data_importing else R.string.automation_data_exporting)
        return Notification.Builder(this, CHANNEL)
            .setContentTitle(title)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()
    }

    private fun stop(startId: Int): Int {
        stopSelf(startId)
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "automation_data"
        private const val NOTIFICATION_ID = 9714
        private const val EXTRA_JOB = "job"
        private const val EXTRA_IMPORTING = "importing"

        /**
         * The descriptor's way across, because an Intent is the wrong vehicle for one.
         *
         * A [ParcelFileDescriptor] in an Intent extra is duplicated by the system on delivery and the
         * copy's lifetime stops being ours to reason about. Handing it through a map keyed by the job id
         * keeps exactly one open descriptor with exactly one owner — the service, which closes it in a
         * `finally`.
         */
        private val HANDOVER = ConcurrentHashMap<String, ParcelFileDescriptor>()

        /** Throws if the platform refuses the start; [AutomationProvider] answers the caller with that. */
        fun start(context: Context, jobId: String, fd: ParcelFileDescriptor, importing: Boolean, extras: Bundle?) {
            HANDOVER[jobId] = fd
            try {
                context.startForegroundService(
                    Intent(context, AutomationDataService::class.java)
                        .putExtra(EXTRA_JOB, jobId)
                        .putExtra(EXTRA_IMPORTING, importing)
                        .putExtra(AutomationProvider.KEY_ITEMS, extras?.getString(AutomationProvider.KEY_ITEMS))
                        .putExtra(
                            AutomationProvider.KEY_REPLY_ACTION,
                            extras?.getString(AutomationProvider.KEY_REPLY_ACTION)
                        )
                        .putExtra(
                            AutomationProvider.KEY_REPLY_PACKAGE,
                            extras?.getString(AutomationProvider.KEY_REPLY_PACKAGE)
                        )
                )
            } catch (e: Exception) {
                // Never strand the descriptor in the map when the service that would have closed it was
                // never started.
                HANDOVER.remove(jobId)
                throw e
            }
        }
    }
}
