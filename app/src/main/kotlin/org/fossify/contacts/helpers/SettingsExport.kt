package org.fossify.contacts.helpers

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import org.fossify.commons.extensions.getSharedPrefs
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.FontHelper
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.contacts.Contact
import org.fossify.contacts.BuildConfig
import org.fossify.contacts.R
import org.fossify.contacts.activities.SimpleActivity
import org.fossify.contacts.extensions.config
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

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
    const val EXPORT_PREFIX = "shiroikuma-renrakusaki-"

    /** A selectable export/import category. `id` names the entry inside the ZIP. */
    enum class Cat(val id: String, @StringRes val labelRes: Int) {
        SETTINGS("settings", R.string.eim_cat_settings),
        CONTACTS("contacts", R.string.eim_cat_contacts);

        companion object {
            fun byId(id: String): Cat? = entries.firstOrNull { it.id == id }
        }
    }

    // Device-local keys never carried across an export: the automation shared secret AND its enable
    // switch (each device owns its own security state — a restore must never silently flip automation
    // on/off or overwrite the token), per-device timestamps, and one-time version markers.
    private val PREFS_EXCLUDE = setOf(AUTOMATION_TOKEN, AUTOMATION_ENABLED, "last_auto_backup_time", "last_version")

    fun exportFileName(): String =
        EXPORT_PREFIX + BuildConfig.VERSION_NAME + "-export_" +
            SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(Date()) + ".zip"

    // ---------------------------------------------------------------------------------------------
    // EXPORT
    // ---------------------------------------------------------------------------------------------

    /**
     * Write a ZIP of the selected categories. [openOut] runs on a background thread once the data is
     * gathered; [done] reports a short human summary or the failure, on that background thread.
     */
    fun export(activity: SimpleActivity, cats: Set<Cat>, openOut: () -> OutputStream?, done: (Result<String>) -> Unit) {
        if (Cat.CONTACTS in cats) {
            ContactsHelper(activity).getContacts(getAll = true, showOnlyContactsWithNumbers = false) { contacts ->
                writeZip(activity, cats, contacts, openOut, done)
            }
        } else {
            ensureBackgroundThread {
                writeZip(activity, cats, null, openOut, done)
            }
        }
    }

    private fun writeZip(
        activity: SimpleActivity,
        cats: Set<Cat>,
        contacts: ArrayList<Contact>?,
        openOut: () -> OutputStream?,
        done: (Result<String>) -> Unit,
    ) {
        val result = runCatching {
            val out = openOut() ?: error("no output stream")
            val parts = mutableListOf<String>()
            ZipOutputStream(out).use { zip ->
                val manifest = JSONObject()
                    .put("format", FORMAT)
                    .put("version", VERSION)
                    .put("app", activity.packageName)
                    .put("createdTs", System.currentTimeMillis())
                    .put("categories", JSONArray(cats.map { it.id }))
                writeEntry(zip, "manifest.json", manifest.toString(2).toByteArray())

                for (cat in cats) {
                    when (cat) {
                        Cat.SETTINGS -> {
                            val prefs = activity.getSharedPrefs()
                            writeEntry(zip, "settings.json", exportPrefs(prefs, PREFS_EXCLUDE).toByteArray())
                            val fonts = exportFonts(activity, zip)
                            parts += activity.getString(cat.labelRes) +
                                if (fonts > 0) " + $fonts fonts" else ""
                        }

                        Cat.CONTACTS -> {
                            if (contacts.isNullOrEmpty()) error("no contacts to export")
                            val buffer = ByteArrayOutputStream()
                            var exportResult: VcfExporter.ExportResult? = null
                            VcfExporter().exportContacts(
                                context = activity,
                                outputStream = buffer,
                                contacts = contacts,
                                showExportingToast = false
                            ) { exportResult = it }
                            if (exportResult == VcfExporter.ExportResult.EXPORT_FAIL) error("contacts export failed")
                            writeEntry(zip, "contacts.vcf", buffer.toByteArray())
                            parts += "${activity.getString(cat.labelRes)}: ${contacts.size}"
                        }
                    }
                }
            }
            parts.joinToString("・")
        }
        done(result)
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

    /** Categories present in a ZIP (from its manifest, falling back to the entries found). */
    fun categoriesIn(zip: ByteArray): Set<Cat> {
        val files = readZip(zip)
        files["manifest.json"]?.let { mf ->
            val cats = runCatching { JSONObject(mf.decodeToString()).optJSONArray("categories") }.getOrNull()
            if (cats != null) {
                val set = (0 until cats.length()).mapNotNull { Cat.byId(cats.optString(it)) }.toSet()
                if (set.isNotEmpty()) return set
            }
        }
        return Cat.entries.filter { files.containsKey(entryName(it)) }.toSet()
    }

    private fun entryName(cat: Cat) = when (cat) {
        Cat.SETTINGS -> "settings.json"
        Cat.CONTACTS -> "contacts.vcf"
    }

    /**
     * Apply the selected categories from a ZIP; absent ones are skipped. Runs on a background thread;
     * [done] reports a short per-category summary or the failure, on that background thread.
     */
    fun import(activity: SimpleActivity, zip: ByteArray, cats: Set<Cat>, done: (Result<String>) -> Unit) {
        ensureBackgroundThread {
            val result = runCatching {
                val files = readZip(zip)
                require(categoriesIn(zip).isNotEmpty()) { activity.getString(R.string.eim_import_none) }
                val parts = mutableListOf<String>()

                if (Cat.SETTINGS in cats) {
                    files["settings.json"]?.let { data ->
                        val n = importPrefs(activity.getSharedPrefs(), data.decodeToString(), PREFS_EXCLUDE)
                        val fonts = importFonts(activity, files)
                        parts += activity.getString(Cat.SETTINGS.labelRes) + ": $n" +
                            if (fonts > 0) " + $fonts fonts" else ""
                    }
                }

                if (Cat.CONTACTS in cats) {
                    files["contacts.vcf"]?.let { data ->
                        val temp = java.io.File(activity.cacheDir, "eximport.vcf")
                        temp.writeBytes(data)
                        val importResult = VcfImporter(activity).importContacts(
                            path = temp.absolutePath,
                            targetContactSource = activity.config.lastUsedContactSource
                        )
                        temp.delete()
                        if (importResult == VcfImporter.ImportResult.IMPORT_FAIL) error("contacts import failed")
                        parts += activity.getString(Cat.CONTACTS.labelRes) + ": " + importResult.name
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
