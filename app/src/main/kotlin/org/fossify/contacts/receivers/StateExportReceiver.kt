package org.fossify.contacts.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.util.Log
import java.io.OutputStream
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isRPlus
import org.fossify.contacts.R
import org.fossify.contacts.extensions.config
import org.fossify.contacts.helpers.ACTION_CANCEL_EXPORT
import org.fossify.contacts.helpers.ACTION_EXPORT_STATE
import org.fossify.contacts.helpers.ACTION_LIST_CATEGORIES
import org.fossify.contacts.helpers.EXTRA_AUTOMATION_TOKEN
import org.fossify.contacts.helpers.EXTRA_BACKUP_PATH
import org.fossify.contacts.helpers.EXTRA_EXPORT_ITEMS
import org.fossify.contacts.helpers.EXTRA_PROGRESS_ACTION
import org.fossify.contacts.helpers.EXTRA_PROGRESS_APP
import org.fossify.contacts.helpers.EXTRA_PROGRESS_CURRENT
import org.fossify.contacts.helpers.EXTRA_PROGRESS_TEXT
import org.fossify.contacts.helpers.EXTRA_PROGRESS_TOTAL
import org.fossify.contacts.helpers.EXTRA_PROGRESS_UNIT
import org.fossify.contacts.helpers.EXTRA_REPLY_ACTION
import org.fossify.contacts.helpers.EXTRA_REPLY_ID
import org.fossify.contacts.helpers.EXTRA_REPLY_PACKAGE
import org.fossify.contacts.helpers.EXTRA_REPLY_RESULT
import org.fossify.contacts.helpers.ExportCancelledException
import org.fossify.contacts.helpers.PROGRESS_THROTTLE_MS
import org.fossify.contacts.helpers.ProgressReporter
import org.fossify.contacts.helpers.SettingsExport

/**
 * The 保存復元 state-export contract, for 白い熊 自由作業盤's one-run backup of every sister app.
 *
 * Three exported, token-gated actions:
 *  - [ACTION_LIST_CATEGORIES] — instant; replies "OK:" plus one `id<TAB>label<TAB>parent<TAB>on|off`
 *    line per selectable item. A sub-option names its parent in the third field ("settings.fonts" under
 *    "settings") and follows its line, so the caller can render it indented and make it follow the
 *    parent's toggle; the fourth field is this app's own answer to whether the item starts ticked.
 *  - [ACTION_EXPORT_STATE] — runs the same category ZIP export as the Export/Import page, headlessly
 *    (no Activity, no interaction), and replies with the written path and its real size. Extras:
 *    "token", optional "path" (an absolute directory that OVERRIDES the configured export folder),
 *    optional "items" (comma-separated category ids; absent = the default set), optional
 *    "progress_action", plus "reply_action"/"reply_package"/"reply_id".
 *  - [ACTION_CANCEL_EXPORT] — stops a running export from outside. Fire-and-forget: it answers nothing
 *    of its own, and the export it stops sends "ERROR:cancelled" for the original request.
 *
 * Directory precedence: the "path" extra → the app's configured export folder → ERROR:no-directory.
 *
 * The reply is a plain broadcast carrying "reply_id" + "result" — the only channel that works on this
 * EMUI (see BackupContactsReceiver for the evidence: the ordered-broadcast result is severed between
 * third-party apps and any Binder-bearing extra is dropped). Exactly one terminal reply per request,
 * guarded by an [AtomicBoolean] so an async success and a synchronous error can never both fire.
 *
 * Progress is reported as real counts, never a percentage — "連絡先 123/456", throttled to one
 * broadcast per [PROGRESS_THROTTLE_MS] with an unthrottled final one at completion.
 */
class StateExportReceiver : BroadcastReceiver() {
    companion object {
        const val TAG = "RenrakusakiStateExport"
        private const val KILO = 1024.0

        // The export that may be in flight, so a CANCEL_EXPORT can reach it: a BroadcastReceiver is a
        // fresh instance per delivery, so the run's state cannot live on `this`. At most one export
        // exists at a time (the contract forbids two at once), so one slot is the whole registry.
        private val running = AtomicReference<Run?>(null)

        /** A headless export in flight: the request it answers, and its cooperative cancel flag. */
        private class Run(val replyId: String) {
            @Volatile
            var cancelled = false
        }
    }

    /** What a parsed request turned out to be: already answerable, or an export to run. */
    private sealed class Request {
        class Done(val result: String) : Request()
        class Export(val cats: Set<SettingsExport.Item>, val path: String) : Request()
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != ACTION_EXPORT_STATE && action != ACTION_LIST_CATEGORIES && action != ACTION_CANCEL_EXPORT) {
            return
        }

        // The cancel does no work and answers nothing — it only raises a flag the running export reads —
        // so it needs neither goAsync() nor a reply channel. Handled before either is set up.
        if (action == ACTION_CANCEL_EXPORT) {
            cancelExport(context.applicationContext, intent)
            return
        }

        // goAsync() holds the broadcast open until finish(); the guard makes finishWith idempotent so
        // the async success path and any synchronous error path can't double-finish (and a dropped
        // path can't leave the caller waiting forever).
        val pending = goAsync()
        val finished = AtomicBoolean(false)
        val appContext = context.applicationContext
        val replyAction = intent.getStringExtra(EXTRA_REPLY_ACTION)?.trim().orEmpty()
        val replyPackage = intent.getStringExtra(EXTRA_REPLY_PACKAGE)?.trim().orEmpty()
        val replyId = intent.getStringExtra(EXTRA_REPLY_ID)?.trim().orEmpty()
        val progressAction = intent.getStringExtra(EXTRA_PROGRESS_ACTION)?.trim().orEmpty()

        fun finishWith(result: String) {
            if (!finished.compareAndSet(false, true)) return
            Log.i(TAG, "result → $result")
            if (replyAction.isNotEmpty() && replyId.isNotEmpty()) {
                try {
                    appContext.sendBroadcast(
                        Intent(replyAction)
                            .setPackage(replyPackage.ifEmpty { null })
                            .putExtra(EXTRA_REPLY_ID, replyId)
                            .putExtra(EXTRA_REPLY_RESULT, result)
                            .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    )
                    Log.i(TAG, "reply broadcast sent → $replyAction ($replyPackage, id=$replyId)")
                } catch (e: Exception) {
                    Log.w(TAG, "reply broadcast failed: $e")
                }
            }
            pending.setResultData(result)
            pending.finish()
        }

        val request = try {
            parse(appContext, intent, action)
        } catch (e: Exception) {
            Request.Done("ERROR:${reason(e)}")
        }

        when (request) {
            is Request.Done -> finishWith(request.result)
            is Request.Export -> {
                val progress = throttledProgress(appContext, progressAction, replyPackage, replyId)
                val run = Run(replyId)
                running.set(run)
                ensureBackgroundThread {
                    try {
                        finishWith(export(appContext, request.cats, request.path, progress, run))
                    } finally {
                        // Only clear our own run: a later export must not be un-registered by this one.
                        running.compareAndSet(run, null)
                    }
                }
            }
        }
    }

    /**
     * CANCEL_EXPORT: token-gated like every other action, and safe to send at any time — with nothing
     * running, or after the export already finished, it is a silent no-op rather than an error. It sends
     * no reply of its own; it raises the run's flag, and the export unwinds at its next entry boundary,
     * discards its partial file and answers the original request with "ERROR:cancelled".
     *
     * The route matters: the export runs inside this exported receiver, so the stop signal arrives at the
     * same component the caller can already reach — nothing has to start a non-exported service.
     */
    private fun cancelExport(context: Context, intent: Intent) {
        val config = context.config
        val token = intent.getStringExtra(EXTRA_AUTOMATION_TOKEN)
        if (!config.automationEnabled || !config.isAutomationTokenValid(token)) {
            Log.i(TAG, "cancel refused: enabled=${config.automationEnabled}, tokenLen=${token?.length ?: 0}")
            return
        }

        // An explicit reply_id names one export; absent, it means "the one you are running".
        val replyId = intent.getStringExtra(EXTRA_REPLY_ID)?.trim().orEmpty()
        val run = running.get()
        val outcome = when {
            run == null -> "nothing running"
            replyId.isNotEmpty() && run.replyId.isNotEmpty() && replyId != run.replyId ->
                "names another export (running: ${run.replyId})"

            else -> {
                run.cancelled = true
                "signalled"
            }
        }
        Log.i(TAG, "cancel requested (reply_id=${replyId.ifEmpty { "-" }}) → $outcome")
    }

    /**
     * Decide the request without doing any work: the gate first (the switch and the token report
     * distinctly, since they debug differently), then the instant category list, then the export's own
     * validation — so a malformed request is answered before anything is written.
     */
    private fun parse(context: Context, intent: Intent, action: String?): Request {
        val config = context.config
        val token = intent.getStringExtra(EXTRA_AUTOMATION_TOKEN)
        val itemsRaw = intent.getStringExtra(EXTRA_EXPORT_ITEMS)?.trim().orEmpty()
        val path = intent.getStringExtra(EXTRA_BACKUP_PATH)?.trim().orEmpty()
        val cats = parseItems(itemsRaw)
        Log.i(
            TAG,
            "received $action: enabled=${config.automationEnabled}, tokenLen=${token?.length ?: 0}, " +
                "items=$itemsRaw, path=$path"
        )

        return when {
            !config.automationEnabled -> Request.Done("ERROR:automation disabled")
            !config.isAutomationTokenValid(token) -> Request.Done("ERROR:bad token")
            action == ACTION_LIST_CATEGORIES -> Request.Done(categoryList(context))
            cats == null -> Request.Done("ERROR:unknown category in items: $itemsRaw")
            path.isNotEmpty() && !path.startsWith("/") ->
                Request.Done("ERROR:$EXTRA_BACKUP_PATH must be an absolute directory")

            else -> Request.Export(cats, path)
        }
    }

    /**
     * "OK:" plus one `id<TAB>label<TAB>parent<TAB>on|off` line per selectable item — the ids are exactly
     * the ones "items" accepts. The fields are positional, so the third is always present and empty for a
     * top-level item; for a sub-option it is its parent's id, and the line follows the parent's, so the
     * caller can render it indented under it. The fourth is whether the item starts ticked: this app
     * stating its own default rather than the picker assuming one.
     */
    private fun categoryList(context: Context): String =
        SettingsExport.Item.listed.joinToString(separator = "\n", prefix = "OK:") {
            val startsTicked = if (it.defaultOn) "on" else "off"
            "${it.id}\t${context.getString(it.labelRes)}\t${it.parentId.orEmpty()}\t$startsTicked"
        }

    /**
     * The requested items, or null when [itemsRaw] names an id we do not export. Absent or empty means
     * this app's default set — exactly the items it reports as `on`. A parent id on its own selects that
     * category's own data only — its parts are separate ids, so they are included only when asked for.
     */
    private fun parseItems(itemsRaw: String): Set<SettingsExport.Item>? {
        val ids = itemsRaw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (ids.isEmpty()) return SettingsExport.Item.defaultSelection
        val items = ids.mapNotNull { SettingsExport.Item.byId(it) }.toSet()
        return items.takeIf { it.size == ids.distinct().size }
    }

    /** Runs on a background thread; returns the single result line and never throws. */
    private fun export(
        context: Context,
        cats: Set<SettingsExport.Item>,
        path: String,
        progress: ThrottledProgress,
        run: Run,
    ): String {
        val target = try {
            SettingsExport.headlessTarget(context, path) ?: return "ERROR:no-directory"
        } catch (e: Exception) {
            return storageError(path, e)
        }

        var finished = false
        return try {
            // The count is a fallback for a destination we cannot stat; it is final once exportBlocking
            // returns, which is after the ZIP's central directory has been flushed.
            val counting = CountingOutputStream(target.open())
            counting.use { SettingsExport.exportBlocking(context, cats, it, progress.reporter) { run.cancelled } }
            val bytes = target.size().takeIf { it > 0 } ?: counting.count
            progress.final(cats.size.toLong())
            finished = true
            "OK:${target.displayPath}|$bytes|${humanSize(bytes)}|${cats.size} categories"
        } catch (cancelled: ExportCancelledException) {
            Log.i(TAG, "export unwound: ${cancelled.message}")
            "ERROR:cancelled"
        } catch (e: Exception) {
            storageError(path, e)
        } finally {
            // A cancel and a failure end the same way: the destination is created before the first byte,
            // so take it back. What did not finish leaves the backup directory exactly as it was found.
            if (!finished) {
                target.discard()
            }
        }
    }

    // An absolute path we were told to write but cannot needs All-files access; name that specifically,
    // since it is the one failure 白い熊 fixes with a toggle rather than a code change.
    private fun storageError(path: String, e: Exception): String {
        val noAllFiles = isRPlus() && !Environment.isExternalStorageManager()
        return if (path.isNotEmpty() && noAllFiles) "ERROR:no-storage-access" else "ERROR:${reason(e)}"
    }

    private fun reason(e: Throwable): String =
        (e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName).replace('\n', ' ')

    /** Display size for the reply line — the caller cannot stat the file, so we compute both forms. */
    private fun humanSize(bytes: Long): String = when {
        bytes < KILO -> "$bytes B"
        bytes < KILO * KILO -> "%.1f KB".format(Locale.ROOT, bytes / KILO)
        bytes < KILO * KILO * KILO -> "%.1f MB".format(Locale.ROOT, bytes / (KILO * KILO))
        else -> "%.2f GB".format(Locale.ROOT, bytes / (KILO * KILO * KILO))
    }

    private fun throttledProgress(
        context: Context,
        progressAction: String,
        replyPackage: String,
        replyId: String,
    ): ThrottledProgress {
        val appLabel = context.getString(R.string.app_launcher_name)
        val unitCategory = context.getString(R.string.state_progress_unit_category)

        fun send(current: Long, total: Long, unit: String, text: String) {
            try {
                context.sendBroadcast(
                    Intent(progressAction)
                        .setPackage(replyPackage.ifEmpty { null })
                        .putExtra(EXTRA_REPLY_ID, replyId)
                        .putExtra(EXTRA_PROGRESS_APP, appLabel)
                        .putExtra(EXTRA_PROGRESS_TEXT, text)
                        .putExtra(EXTRA_PROGRESS_CURRENT, current)
                        .putExtra(EXTRA_PROGRESS_TOTAL, total)
                        .putExtra(EXTRA_PROGRESS_UNIT, unit)
                        .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                )
            } catch (e: Exception) {
                Log.w(TAG, "progress broadcast failed: $e")
            }
        }

        var lastSent = 0L
        return ThrottledProgress(
            reporter = { current, total, unit, text ->
                val now = System.currentTimeMillis()
                if (progressAction.isNotEmpty() && now - lastSent >= PROGRESS_THROTTLE_MS) {
                    lastSent = now
                    send(current, total, unit, text)
                }
            },
            final = { categories ->
                if (progressAction.isNotEmpty()) {
                    send(categories, categories, unitCategory, "$unitCategory $categories/$categories")
                }
            },
        )
    }

    /** The throttled progress channel plus the unthrottled completion broadcast. */
    private class ThrottledProgress(val reporter: ProgressReporter, val final: (Long) -> Unit)

    private class CountingOutputStream(private val out: OutputStream) : OutputStream() {
        var count = 0L
            private set

        override fun write(b: Int) {
            out.write(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            count += len
        }

        override fun flush() = out.flush()

        override fun close() = out.close()
    }
}
