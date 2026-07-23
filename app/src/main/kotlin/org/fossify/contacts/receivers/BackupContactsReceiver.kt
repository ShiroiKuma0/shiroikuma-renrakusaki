package org.fossify.contacts.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import org.fossify.contacts.extensions.backupContactsToPath
import org.fossify.contacts.extensions.config
import org.fossify.contacts.helpers.ACTION_BACKUP_CONTACTS
import org.fossify.contacts.helpers.EXTRA_AUTOMATION_TOKEN
import org.fossify.contacts.helpers.EXTRA_BACKUP_PATH
import org.fossify.contacts.helpers.EXTRA_REPLY_ACTION
import org.fossify.contacts.helpers.EXTRA_REPLY_ID
import org.fossify.contacts.helpers.EXTRA_REPLY_PACKAGE
import org.fossify.contacts.helpers.EXTRA_REPLY_RESULT

/**
 * Exported, token-gated on-demand backup trigger for the automation fork (白い熊 自由作業盤).
 *
 * Send a broadcast with [ACTION_BACKUP_CONTACTS] carrying "token" (the secret from
 * Settings → Automation) and "path" (a directory, or a full .vcf file path). Every terminal outcome
 * produces exactly one result line, "OK:<written file>" or "ERROR: <reason>", so the sending task
 * can verify the backup landed before doing anything destructive — the reason this exists: EMUI
 * recreates contacts2.db on a system-locale change, which wiped every contact on 2026-07-22.
 *
 * The result line is delivered over TWO channels:
 *  - a plain REPLY BROADCAST back to the caller, described by three string extras on the incoming
 *    intent: "reply_action" (the action to broadcast back), "reply_package" (setPackage target)
 *    and "reply_id" (a correlation id echoed verbatim). The reply carries "reply_id" + "result".
 *    Plain strings are the only reply channel that works on this EMUI — verified live 2026-07-23:
 *    the ordered-broadcast result is severed between third-party apps (the caller's resultTo is
 *    finished empty in ~10 ms while a severed copy runs on the "bgthirdapp" queue; see the
 *    dumpsys evidence), a live-Binder extra (ResultReceiver) gets the whole broadcast dropped
 *    before the receiver even runs, and a PendingIntent extra survives delivery but its send()
 *    never reaches the caller.
 *  - setResultData on the ordered-broadcast PendingResult — the standard channel, kept because it
 *    is correct AOSP behavior and costs nothing, but it never arrives cross-app on this device.
 *
 * Every outcome is also logged to Logcat (tag [TAG]) — though EMUI suppresses third-party app
 * logs by default; `dumpsys activity broadcasts history` is the shell-visible signal instead.
 */
class BackupContactsReceiver : BroadcastReceiver() {
    companion object {
        const val TAG = "RenrakusakiBackup"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_BACKUP_CONTACTS) {
            return
        }

        // goAsync() holds the broadcast open until finish(); the guard makes finishWith idempotent
        // so the async success path and any synchronous error path can't double-finish (and a
        // dropped path can't leave the caller waiting forever).
        val pending = goAsync()
        val finished = AtomicBoolean(false)
        val appContext = context.applicationContext
        val replyAction = intent.getStringExtra(EXTRA_REPLY_ACTION)?.trim().orEmpty()
        val replyPackage = intent.getStringExtra(EXTRA_REPLY_PACKAGE)?.trim().orEmpty()
        val replyId = intent.getStringExtra(EXTRA_REPLY_ID)?.trim().orEmpty()
        fun finishWith(resultData: String) {
            if (!finished.compareAndSet(false, true)) return
            Log.i(TAG, "result → $resultData")
            if (replyAction.isNotEmpty() && replyId.isNotEmpty()) {
                try {
                    appContext.sendBroadcast(
                        Intent(replyAction)
                            .setPackage(replyPackage.ifEmpty { null })
                            .putExtra(EXTRA_REPLY_ID, replyId)
                            .putExtra(EXTRA_REPLY_RESULT, resultData)
                            .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    )
                    Log.i(TAG, "reply broadcast sent → $replyAction ($replyPackage, id=$replyId)")
                } catch (e: Exception) {
                    Log.w(TAG, "reply broadcast failed: $e")
                }
            }
            pending.setResultData(resultData)
            pending.finish()
        }

        try {
            val config = context.config
            val token = intent.getStringExtra(EXTRA_AUTOMATION_TOKEN)
            val path = intent.getStringExtra(EXTRA_BACKUP_PATH)?.trim().orEmpty()
            Log.i(TAG, "received: enabled=${config.automationEnabled}, tokenLen=${token?.length ?: 0}, path=$path, reply=$replyAction/$replyId")

            when {
                !config.automationEnabled ->
                    finishWith("ERROR: automation is OFF — enable it in 白い熊 連絡先 → Settings → 自動化")
                !config.isAutomationTokenValid(token) ->
                    finishWith("ERROR: wrong token — copy the current one from 白い熊 連絡先 → Settings → 自動化")
                path.isEmpty() -> finishWith("ERROR: missing $EXTRA_BACKUP_PATH extra")
                !path.startsWith("/") -> finishWith("ERROR: $EXTRA_BACKUP_PATH must be an absolute path")
                else -> appContext.backupContactsToPath(path) { success, result ->
                    finishWith(if (success) "OK:$result" else "ERROR: $result")
                }
            }
        } catch (e: Exception) {
            finishWith("ERROR: ${e.message ?: e.javaClass.simpleName}")
        }
    }
}
