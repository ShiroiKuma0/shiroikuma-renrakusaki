package org.fossify.contacts.automation

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import org.fossify.contacts.extensions.config
import org.fossify.contacts.helpers.SettingsExport
import org.json.JSONArray
import org.json.JSONObject

/**
 * The data door: export this app's own state, and put it back, for a caller we can identify.
 *
 * ## Why a provider and not the exported receivers next to it
 *
 * Two reasons, and the first is the whole point of contract v2.
 *
 * **A broadcast cannot tell you who sent it.** v1's answer to that was a shared secret, which cannot
 * survive the wipe this feature exists to recover from. A provider gets the caller's identity from the
 * framework for free — see [AutomationCallers] for what is actually checked, and why a `shiroikuma.*`
 * prefix would have been strictly weaker than the token it replaced.
 *
 * **A list needs a synchronous answer.** 応用管理 draws a row per installed app before any export exists;
 * a broadcast round trip per app to fill a list is the wrong shape entirely.
 *
 * ## Why `import` lives ONLY here
 *
 * An import overwrites this app's data, and the §1 receivers are `exported="true"` with no permission —
 * an import action there would let any app on the phone wipe any sister app. The receivers stay the
 * unauthenticated half of the surface: they only ever write where they were told to and report what they
 * did. Anything that moves data through a caller-supplied descriptor comes through this door.
 *
 * ## What does NOT happen here
 *
 * The payload. [call] validates, starts a foreground service and returns — tens of megabytes over
 * minutes inside a binder call would block the caller, report no progress, refuse cancellation and die
 * silently if this process were killed.
 */
class AutomationProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    /**
     * Every method answers a [Bundle] with [KEY_RESULT] — `OK…` or `ERROR:…`, the same vocabulary the
     * broadcast contract already uses, so a caller has one grammar to parse rather than two.
     *
     * A refusal is returned, never thrown: an exception across a binder reaches the caller as a
     * `RuntimeException` carrying our stack trace, which tells 白い熊 nothing and tells a misbehaving
     * caller rather more than it should.
     */
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val ctx = context ?: return fail("ERROR:not ready")

        // WHO, before WHAT. A caller we cannot identify gets the same answer whatever it asked for.
        when (val verdict = AutomationCallers.verify(ctx, callingPackage)) {
            is AutomationCallers.Verdict.Refused -> {
                Log.i(TAG, "$method refused: ${verdict.why}")
                return fail(verdict.why)
            }

            AutomationCallers.Verdict.Allowed -> Unit
        }

        // Then this app's own switches — the same single gate the receivers use, so a token handed to an
        // app that does not require one is ignored here exactly as it is there.
        ctx.config.refuseAutomation(extras?.getString(KEY_TOKEN))?.let {
            Log.i(TAG, "$method refused: $it")
            return fail(it)
        }

        Log.i(TAG, "$method from $callingPackage")
        return when (method) {
            METHOD_DESCRIBE -> ok(describe(ctx))
            METHOD_EXPORT -> start(ctx, extras, importing = false)
            METHOD_IMPORT -> start(ctx, extras, importing = true)
            METHOD_CANCEL -> {
                AutomationJobs.cancel(extras?.getString(KEY_JOB_ID))
                ok("OK:cancelled")
            }

            else -> fail("ERROR:unknown method: $method")
        }
    }

    /**
     * What this app would export, answered without exporting anything.
     *
     * Returned from the call rather than written into the archive, deliberately: 応用管理 must draw a row
     * before an export exists, and at restore must judge compatibility BEFORE streaming tens of megabytes
     * into an app that would reject them — which it cannot do if the header is buried inside an encrypted
     * archive.
     *
     * Built with [JSONObject] rather than string concatenation because `contains` carries this app's own
     * localised category nouns, which are not ours to assume are JSON-safe.
     */
    private fun describe(ctx: Context): String {
        val pkg = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        val contains = SettingsExport.Item.defaultSelection
            .sortedBy { SettingsExport.Item.listed.indexOf(it) }
            .map { ctx.getString(it.shortLabelRes) }
        val header = JSONObject()
            .put("app_id", ctx.packageName)
            .put("version_code", @Suppress("DEPRECATION") pkg.versionCode)
            .put("version_name", pkg.versionName.orEmpty())
            .put("format", FORMAT)
            .put("min_format_readable", MIN_FORMAT_READABLE)
            // This app merges an import into whatever prefs exist and never clears them, so a
            // never-launched install is a perfectly good target.
            .put("requires_launch_first", false)
            .put("contains", JSONArray(contains))
        return "OK:$header"
    }

    /**
     * Hand the descriptor to a foreground service and get out of the way.
     *
     * The descriptor is **duplicated** before it leaves this method. The one in [extras] belongs to the
     * binder transaction and is closed the moment `call()` returns; a service reading it afterwards would
     * find it shut. That is a bug you only see under load, so it is not left to the service to remember.
     *
     * A service that will not start is answered, not thrown: on this platform a background
     * foreground-service start can be refused outright, and the caller needs to hear that as an `ERROR:`
     * line rather than as our stack trace. The duplicate is closed on that path — a leaked descriptor
     * holds the caller's file open, and a caller cannot checksum or encrypt a file that is still open.
     */
    private fun start(ctx: Context, extras: Bundle?, importing: Boolean): Bundle {
        @Suppress("DEPRECATION")
        val fd = extras?.getParcelable<ParcelFileDescriptor>(KEY_FD)
            ?: return fail("ERROR:no descriptor")
        val dup = runCatching { fd.dup() }.getOrNull() ?: return fail("ERROR:descriptor unusable")
        val jobId = AutomationJobs.begin()
        return try {
            AutomationDataService.start(ctx, jobId, dup, importing, extras)
            ok("OK:$jobId")
        } catch (e: Exception) {
            AutomationJobs.finish(jobId)
            runCatching { dup.close() }
            Log.w(TAG, "service start refused: $e")
            fail("ERROR:service start refused: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun ok(result: String) = Bundle().apply { putString(KEY_RESULT, result) }

    private fun fail(why: String) = Bundle().apply { putString(KEY_RESULT, why) }

    // A provider that is only ever call()ed still has to answer these. Refusing loudly beats returning an
    // empty cursor, which reads downstream as "there is no data" rather than "wrong door".
    override fun query(u: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? =
        throw UnsupportedOperationException("automation is call() only")

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("automation is call() only")

    override fun delete(uri: Uri, s: String?, a: Array<String>?): Int =
        throw UnsupportedOperationException("automation is call() only")

    override fun update(u: Uri, v: ContentValues?, s: String?, a: Array<String>?): Int =
        throw UnsupportedOperationException("automation is call() only")

    companion object {
        const val TAG = "RenrakusakiAutomation"

        const val METHOD_DESCRIBE = "describe"
        const val METHOD_EXPORT = "export"
        const val METHOD_IMPORT = "import"
        const val METHOD_CANCEL = "cancel"

        const val KEY_RESULT = "result"
        const val KEY_FD = "fd"
        const val KEY_TOKEN = "token"
        const val KEY_JOB_ID = "job_id"
        const val KEY_ITEMS = "items"
        const val KEY_REPLY_ACTION = "reply_action"
        const val KEY_REPLY_PACKAGE = "reply_package"

        /**
         * This app's archive format — the same number [SettingsExport] stamps into `manifest.json`, so the
         * header a caller reads here and the version inside the ZIP can never disagree. Bumped when an
         * older build could no longer read what we write.
         */
        const val FORMAT = SettingsExport.VERSION

        /**
         * The oldest archive this build can still read.
         *
         * Version skew has a direction: old data into a newer app is normally fine, because an app
         * migrates its own storage; newer data into an older app is not. This field is what lets a caller
         * refuse the second case at discovery time, before anything is streamed.
         */
        const val MIN_FORMAT_READABLE = 1
    }
}
