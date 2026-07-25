package org.fossify.contacts.helpers

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.annotation.StringRes
import androidx.documentfile.provider.DocumentFile
import org.fossify.commons.extensions.getSharedPrefs
import org.fossify.commons.helpers.FontHelper
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.contacts.BuildConfig
import org.fossify.contacts.R
import org.fossify.contacts.activities.SimpleActivity
import org.fossify.contacts.extensions.config
import org.fossify.contacts.extensions.fetchAllContactsBlocking
import org.fossify.contacts.extensions.openBackupOutputStream
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * (current, total, unit, text) — the progress channel of a headless export. Real counts only: [text] is
 * the numbers-first line a caller displays ("連絡先 123/456"), never a percentage.
 */
typealias ProgressReporter = (current: Long, total: Long, unit: String, text: String) -> Unit

/**
 * Category-based settings + contacts export/import, modeled on the kojiki fork's KojikiExport.
 *
 * The export is a ZIP of plain files: `settings.json` (every SharedPreferences key, typed, minus
 * device-local ones), the imported fonts as real files under `fonts/`, `contacts.vcf` (every contact
 * from every source), and a `manifest.json` listing format, version and the categories present.
 *
 * Future-proof by construction: import applies only the selected categories, skips absent files, and
 * merges prefs per-key (a missing key keeps its current value; unknown keys are ignored), so exports
 * and app versions can drift apart without breaking a restore.
 */
object SettingsExport {

    const val FORMAT = "renrakusaki-export"
    const val VERSION = 1

    // The app's English dash-separated name, and the prefix every export of ours starts with — the whole
    // family names its backups "<app-name>_<yyyy-MM-dd_HH-mm-ss>.zip". Deliberately version-free: a
    // backup is identified by when it was taken, not by the build that wrote it (the build is recorded
    // inside, as manifest.json's appVersion). Older exports carried the version in the name and still
    // match this prefix, so the "last export" row keeps finding them.
    const val EXPORT_PREFIX = "shiroikuma-renrakusaki"

    /**
     * Everything independently selectable in an export or import: the top-level categories plus their
     * parts (sub-options). `id` is what the automation contract accepts in its "items" extra, and for a
     * top-level item it is also the stable name its data carries inside the ZIP. A part names its parent
     * through [parentId] and is dotted after it ("settings.fonts") — selecting a parent WITHOUT its parts
     * means that category's own data only. [labelRes] is the descriptive label shown in the picker
     * (in-app and in 自由作業盤), [shortLabelRes] the bare noun used in progress lines and summaries.
     */
    enum class Item(
        val id: String,
        val parentId: String?,
        @StringRes val labelRes: Int,
        @StringRes val shortLabelRes: Int,
    ) {
        SETTINGS("settings", null, R.string.eim_cat_settings, R.string.eim_cat_settings_short),
        SETTINGS_FONTS("settings.fonts", "settings", R.string.eim_cat_fonts, R.string.eim_cat_fonts_short),
        CONTACTS("contacts", null, R.string.eim_cat_contacts, R.string.eim_cat_contacts_short);

        val isTopLevel: Boolean get() = parentId == null

        /** The parts of this item, in declaration order — empty for a leaf. */
        val children: List<Item> get() = entries.filter { it.parentId == id }

        companion object {
            fun byId(id: String): Item? = entries.firstOrNull { it.id == id }

            /** Parents first, each followed by its own parts — the order both pickers render. */
            val listed: List<Item> get() = entries.filter { it.isTopLevel }.flatMap { listOf(it) + it.children }
        }
    }

    // The configured export folder: a persisted SAF tree grant kept in a device-local prefs file that is
    // itself never exported. Shared by the Export/Import page and the headless automation export, so both
    // resolve the same folder.
    private const val EXIM_PREFS = "renrakusaki_eximport"
    private const val EXIM_DIR_URI = "dir_uri"

    // Device-local keys never carried across an export: the automation shared secret AND its enable
    // switch (each device owns its own security state — a restore must never silently flip automation
    // on/off or overwrite the token), per-device timestamps, and one-time version markers.
    private val PREFS_EXCLUDE = setOf(AUTOMATION_TOKEN, AUTOMATION_ENABLED, "last_auto_backup_time", "last_version")

    /** "shiroikuma-renrakusaki_2026-07-25_18-58-23.zip" — app name, then when it was taken. */
    fun exportFileName(): String =
        EXPORT_PREFIX + "_" + SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(Date()) + ".zip"

    // ---------------------------------------------------------------------------------------------
    // EXPORT
    // ---------------------------------------------------------------------------------------------

    /**
     * Write a ZIP of the selected categories. [openOut] runs on a background thread; [done] reports a
     * short human summary or the failure, on that background thread. A thin wrapper over
     * [exportBlocking] — the Export/Import page and the automation receiver share one export core.
     */
    fun export(context: Context, items: Set<Item>, openOut: () -> OutputStream?, done: (Result<String>) -> Unit) {
        ensureBackgroundThread {
            done(runCatching { (openOut() ?: error("no output stream")).use { exportBlocking(context, items, it) } })
        }
    }

    /**
     * The export core, callable headlessly — no Activity, no user interaction. Gathers the selected
     * categories, writes the ZIP into [out] and reports real counts through [onProgress] (unthrottled;
     * the caller decides how often to surface them). Blocking, so call it on a background thread, and it
     * throws on every failure so both callers get a single error path. Returns a short human summary.
     */
    fun exportBlocking(
        context: Context,
        items: Set<Item>,
        out: OutputStream,
        onProgress: ProgressReporter = { _, _, _, _ -> },
    ): String {
        // Declaration order, not the caller's, so a ZIP's contents don't depend on how the set was built.
        val ordered = Item.listed.filter { it in items }
        require(ordered.isNotEmpty()) { "nothing selected" }
        val total = ordered.size.toLong()
        val unit = context.getString(R.string.state_progress_unit_category)
        val parts = mutableListOf<String>()

        ZipOutputStream(out).use { zip ->
            val manifest = JSONObject()
                .put("format", FORMAT)
                .put("version", VERSION)
                .put("app", context.packageName)
                .put("appVersion", BuildConfig.VERSION_NAME)
                .put("createdTs", System.currentTimeMillis())
                .put("categories", JSONArray(ordered.map { it.id }))
            writeEntry(zip, "manifest.json", manifest.toString(2).toByteArray())

            ordered.forEachIndexed { index, item ->
                val done = index + 1L
                onProgress(done, total, unit, "$unit $done/$total — ${context.getString(item.shortLabelRes)}")
                parts += when (item) {
                    Item.SETTINGS -> writeSettings(context, zip)
                    Item.SETTINGS_FONTS -> writeFonts(context, zip)
                    Item.CONTACTS -> writeContacts(context, zip, onProgress)
                }
            }
        }
        return parts.joinToString("・")
    }

    private fun writeSettings(context: Context, zip: ZipOutputStream): String {
        writeEntry(zip, "settings.json", exportPrefs(context.getSharedPrefs(), PREFS_EXCLUDE).toByteArray())
        return context.getString(Item.SETTINGS.shortLabelRes)
    }

    private fun writeFonts(context: Context, zip: ZipOutputStream): String =
        "${context.getString(Item.SETTINGS_FONTS.shortLabelRes)}: ${exportFonts(context, zip)}"

    private fun writeContacts(context: Context, zip: ZipOutputStream, onProgress: ProgressReporter): String {
        val contacts = context.fetchAllContactsBlocking()
        if (contacts.isEmpty()) error("no contacts to export")
        val unit = context.getString(R.string.state_progress_unit_contacts)
        val total = contacts.size.toLong()

        // Export to memory first: VcfExporter reports OK once the vCards are built, so buffering keeps
        // "produced data" separate from "the ZIP entry was written".
        val buffer = ByteArrayOutputStream()
        var exportResult: VcfExporter.ExportResult? = null
        val exporter = VcfExporter { done, _ -> onProgress(done.toLong(), total, unit, "$unit $done/$total") }
        exporter.exportContacts(
            context = context,
            outputStream = buffer,
            contacts = contacts,
            showExportingToast = false,
        ) { exportResult = it }
        if (exportResult == VcfExporter.ExportResult.EXPORT_FAIL) error("contacts export failed")
        writeEntry(zip, "contacts.vcf", buffer.toByteArray())
        return "${context.getString(Item.CONTACTS.shortLabelRes)}: ${contacts.size}"
    }

    // ---------------------------------------------------------------------------------------------
    // HEADLESS DESTINATION (automation)
    // ---------------------------------------------------------------------------------------------

    /** A resolved headless export destination: where to write, what to call it, and how big it ended up. */
    class Target(val displayPath: String, val open: () -> OutputStream, val size: () -> Long)

    /** The configured export folder (a persisted SAF tree), or null when none was ever picked. */
    fun configuredDir(context: Context): DocumentFile? =
        context.getSharedPreferences(EXIM_PREFS, Context.MODE_PRIVATE).getString(EXIM_DIR_URI, null)
            ?.let { runCatching { DocumentFile.fromTreeUri(context, Uri.parse(it)) }.getOrNull() }
            ?.takeIf { it.isDirectory }

    /** The raw persisted tree URI, for the Export/Import page's folder picker. */
    fun configuredDirUri(context: Context): Uri? =
        context.getSharedPreferences(EXIM_PREFS, Context.MODE_PRIVATE).getString(EXIM_DIR_URI, null)
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }

    fun setConfiguredDirUri(context: Context, uri: Uri) {
        context.getSharedPreferences(EXIM_PREFS, Context.MODE_PRIVATE).edit()
            .putString(EXIM_DIR_URI, uri.toString())
            .apply()
    }

    /**
     * Resolve where a headless export writes. Directory precedence, per the automation contract:
     * [pathOverride] (an absolute directory, created if missing) → the configured export folder →
     * null, which the caller reports as "no-directory".
     */
    fun headlessTarget(context: Context, pathOverride: String): Target? {
        val name = exportFileName()
        if (pathOverride.isNotEmpty()) {
            // /sdcard is a symlink; normalize so the MediaStore path checks inside the writer match.
            val primary = Environment.getExternalStorageDirectory().absolutePath
            val dir = pathOverride.replaceFirst(Regex("^/sdcard"), primary)
            val file = File(dir, name)
            file.parentFile?.mkdirs()
            return Target(
                displayPath = file.absolutePath,
                open = { context.openBackupOutputStream(file, "application/zip") },
                size = { file.length() },
            )
        }

        val dir = configuredDir(context) ?: return null
        val file = dir.createFile("application/zip", name) ?: error("cannot create a file in ${dir.name}")
        return Target(
            displayPath = displayPathOf(file.uri),
            open = { context.contentResolver.openOutputStream(file.uri) ?: error("cannot open ${file.uri}") },
            size = { file.length() },
        )
    }

    /**
     * Best-effort filesystem path for a SAF document ("primary:〇/x.zip" → "/storage/emulated/0/〇/x.zip"),
     * so the automation reply names a path 白い熊 can actually open. Falls back to the URI.
     */
    private fun displayPathOf(uri: Uri): String {
        val docId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return uri.toString()
        val volume = docId.substringBefore(':', "")
        val relative = docId.substringAfter(':', "")
        if (volume.isEmpty() || relative.isEmpty()) return uri.toString()
        val root = if (volume == "primary") {
            Environment.getExternalStorageDirectory().absolutePath
        } else {
            "/storage/$volume"
        }
        return "$root/$relative"
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, content: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content)
        zip.closeEntry()
    }

    /** Every pref key as a typed JSON entry, so import can round-trip exactly. */
    private fun exportPrefs(sp: SharedPreferences, exclude: Set<String>): String {
        val obj = JSONObject()
        for ((k, v) in sp.all) {
            if (k in exclude) continue
            val e = JSONObject()
            when (v) {
                is Boolean -> { e.put("t", "b"); e.put("v", v) }
                is Int -> { e.put("t", "i"); e.put("v", v) }
                is Long -> { e.put("t", "l"); e.put("v", v) }
                is Float -> { e.put("t", "f"); e.put("v", v.toDouble()) }
                is String -> { e.put("t", "s"); e.put("v", v) }
                is Set<*> -> { e.put("t", "ss"); e.put("v", JSONArray(v.map { it.toString() })) }
                else -> continue
            }
            obj.put(k, e)
        }
        return obj.toString(2)
    }

    private fun exportFonts(context: Context, zip: ZipOutputStream): Int {
        var count = 0
        FontHelper.getFontsDir(context).listFiles()?.forEach { f ->
            if (f.isFile) {
                writeEntry(zip, "fonts/${f.name}", f.readBytes())
                count++
            }
        }
        return count
    }

    // ---------------------------------------------------------------------------------------------
    // IMPORT
    // ---------------------------------------------------------------------------------------------

    /** Items present in a ZIP (from its manifest, falling back to the entries actually found). */
    fun categoriesIn(zip: ByteArray): Set<Item> {
        val files = readZip(zip)
        files["manifest.json"]?.let { mf ->
            val cats = runCatching { JSONObject(mf.decodeToString()).optJSONArray("categories") }.getOrNull()
            if (cats != null) {
                val set = (0 until cats.length()).mapNotNull { Item.byId(cats.optString(it)) }.toSet()
                if (set.isNotEmpty()) return set
            }
        }
        return Item.entries.filter { hasData(it, files) }.toSet()
    }

    // Older exports (and any ZIP with no usable manifest) are recognised by their entries: fonts were
    // always written under fonts/, so a pre-sub-option ZIP still reports both settings and its fonts.
    private fun hasData(item: Item, files: Map<String, ByteArray>) = when (item) {
        Item.SETTINGS -> files.containsKey("settings.json")
        Item.SETTINGS_FONTS -> files.keys.any { it.startsWith("fonts/") }
        Item.CONTACTS -> files.containsKey("contacts.vcf")
    }

    /**
     * Apply the selected categories from a ZIP; absent ones are skipped. Runs on a background thread;
     * [done] reports a short per-category summary or the failure, on that background thread.
     */
    fun import(activity: SimpleActivity, zip: ByteArray, cats: Set<Item>, done: (Result<String>) -> Unit) {
        ensureBackgroundThread {
            val result = runCatching {
                val files = readZip(zip)
                require(categoriesIn(zip).isNotEmpty()) { activity.getString(R.string.eim_import_none) }
                val parts = mutableListOf<String>()

                if (Item.SETTINGS in cats) {
                    files["settings.json"]?.let { data ->
                        val n = importPrefs(activity.getSharedPrefs(), data.decodeToString(), PREFS_EXCLUDE)
                        parts += activity.getString(Item.SETTINGS.shortLabelRes) + ": $n"
                    }
                }

                // Independently selectable: restoring the fonts without the prefs that reference them, or
                // the prefs without the font files, are both valid choices.
                if (Item.SETTINGS_FONTS in cats) {
                    val fonts = importFonts(activity, files)
                    if (fonts > 0) {
                        parts += activity.getString(Item.SETTINGS_FONTS.shortLabelRes) + ": $fonts"
                    }
                }

                if (Item.CONTACTS in cats) {
                    files["contacts.vcf"]?.let { data ->
                        val temp = java.io.File(activity.cacheDir, "eximport.vcf")
                        temp.writeBytes(data)
                        val importResult = VcfImporter(activity).importContacts(
                            path = temp.absolutePath,
                            targetContactSource = activity.config.lastUsedContactSource
                        )
                        temp.delete()
                        if (importResult == VcfImporter.ImportResult.IMPORT_FAIL) error("contacts import failed")
                        parts += activity.getString(Item.CONTACTS.shortLabelRes) + ": " + importResult.name
                    }
                }

                parts.joinToString("・")
            }
            done(result)
        }
    }

    /** Merge — never clear — so unrelated/device-local keys survive. Returns the applied-key count. */
    private fun importPrefs(sp: SharedPreferences, json: String, exclude: Set<String>): Int {
        val obj = JSONObject(json)
        val ed = sp.edit()
        var n = 0
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (k in exclude) continue
            val e = obj.optJSONObject(k) ?: continue
            when (e.optString("t")) {
                "b" -> ed.putBoolean(k, e.optBoolean("v"))
                "i" -> ed.putInt(k, e.optInt("v"))
                "l" -> ed.putLong(k, e.optLong("v"))
                "f" -> ed.putFloat(k, e.optDouble("v").toFloat())
                "s" -> ed.putString(k, e.optString("v"))
                "ss" -> {
                    val arr = e.optJSONArray("v") ?: JSONArray()
                    val set = HashSet<String>()
                    for (i in 0 until arr.length()) set.add(arr.optString(i))
                    ed.putStringSet(k, set)
                }

                else -> continue
            }
            n++
        }
        ed.commit()
        return n
    }

    private fun importFonts(context: Context, files: Map<String, ByteArray>): Int {
        val dir = FontHelper.getFontsDir(context).apply { mkdirs() }
        var count = 0
        for ((name, bytes) in files) {
            if (!name.startsWith("fonts/")) continue
            val fileName = name.removePrefix("fonts/").substringAfterLast('/')
            if (fileName.isEmpty()) continue
            java.io.File(dir, fileName).writeBytes(bytes)
            count++
        }
        return count
    }

    private fun readZip(bytes: ByteArray): Map<String, ByteArray> {
        val map = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    map[entry.name] = zip.readBytes()
                }
                entry = zip.nextEntry
            }
        }
        return map
    }
}
