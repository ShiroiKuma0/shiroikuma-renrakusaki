package org.fossify.contacts.extensions

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.AlarmManagerCompat
import androidx.core.content.FileProvider
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.commons.models.contacts.Contact
import org.fossify.contacts.BuildConfig
import org.fossify.contacts.R
import org.fossify.contacts.helpers.AUTOMATIC_BACKUP_REQUEST_CODE
import org.fossify.contacts.helpers.Config
import org.fossify.contacts.helpers.VcfExporter
import org.fossify.contacts.helpers.getNextAutoBackupTime
import org.fossify.contacts.helpers.getPreviousAutoBackupTime
import org.fossify.contacts.receivers.AutomaticBackupReceiver
import org.joda.time.DateTime
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

val Context.config: Config get() = Config.newInstance(applicationContext)
fun Context.getCachePhotoUri(file: File = getCachePhoto()) = FileProvider.getUriForFile(this, "${BuildConfig.APPLICATION_ID}.provider", file)

@SuppressLint("UseCompatLoadingForDrawables")
fun Context.getPackageDrawable(packageName: String): Drawable {
    return resources.getDrawable(
        when (packageName) {
            TELEGRAM_PACKAGE -> R.drawable.ic_telegram_rect_vector
            SIGNAL_PACKAGE -> R.drawable.ic_signal_rect_vector
            WHATSAPP_PACKAGE -> R.drawable.ic_whatsapp_rect_vector
            VIBER_PACKAGE -> R.drawable.ic_viber_rect_vector
            else -> R.drawable.ic_threema_rect_vector
        }, theme
    )
}

fun Context.getAutomaticBackupIntent(): PendingIntent {
    val intent = Intent(this, AutomaticBackupReceiver::class.java)
    return PendingIntent.getBroadcast(this, AUTOMATIC_BACKUP_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}

fun Context.scheduleNextAutomaticBackup() {
    if (config.autoBackup) {
        val backupAtMillis = getNextAutoBackupTime().millis
        val pendingIntent = getAutomaticBackupIntent()
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            AlarmManagerCompat.setAndAllowWhileIdle(alarmManager, AlarmManager.RTC_WAKEUP, backupAtMillis, pendingIntent)
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }
}

fun Context.cancelScheduledAutomaticBackup() {
    val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
    alarmManager.cancel(getAutomaticBackupIntent())
}

fun Context.checkAndBackupContactsOnBoot() {
    if (config.autoBackup) {
        val previousRealBackupTime = config.lastAutoBackupTime
        val previousScheduledBackupTime = getPreviousAutoBackupTime().millis
        val missedPreviousBackup = previousRealBackupTime < previousScheduledBackupTime
        if (missedPreviousBackup) {
            // device was probably off at the scheduled time so backup now
            backupContacts()
        }
    }
}

fun Context.backupContacts() {
    require(isRPlus())
    ensureBackgroundThread {
        val config = config
        ContactsHelper(this).getContactsToExport(selectedContactSources = config.autoBackupContactSources) { contactsToBackup ->
            if (contactsToBackup.isEmpty()) {
                toast(org.fossify.commons.R.string.no_entries_for_exporting)
                config.lastAutoBackupTime = DateTime.now().millis
                scheduleNextAutomaticBackup()
                return@getContactsToExport
            }


            val now = DateTime.now()
            val year = now.year.toString()
            val month = now.monthOfYear.ensureTwoDigits()
            val day = now.dayOfMonth.ensureTwoDigits()
            val hours = now.hourOfDay.ensureTwoDigits()
            val minutes = now.minuteOfHour.ensureTwoDigits()
            val seconds = now.secondOfMinute.ensureTwoDigits()

            val filename = config.autoBackupFilename
                .replace("%Y", year, false)
                .replace("%M", month, false)
                .replace("%D", day, false)
                .replace("%h", hours, false)
                .replace("%m", minutes, false)
                .replace("%s", seconds, false)

            val outputFolder = File(config.autoBackupFolder).apply {
                mkdirs()
            }

            var exportFile = File(outputFolder, "$filename.vcf")
            var exportFilePath = exportFile.absolutePath
            val outputStream = try {
                if (hasProperStoredFirstParentUri(exportFilePath)) {
                    val exportFileUri = createDocumentUriUsingFirstParentTreeUri(exportFilePath)
                    if (!getDoesFilePathExist(exportFilePath)) {
                        createSAFFileSdk30(exportFilePath)
                    }
                    applicationContext.contentResolver.openOutputStream(exportFileUri, "wt") ?: FileOutputStream(exportFile)
                } else {
                    var num = 0
                    while (getDoesFilePathExist(exportFilePath) && !exportFile.canWrite()) {
                        num++
                        exportFile = File(outputFolder, "${filename}_${num}.vcf")
                        exportFilePath = exportFile.absolutePath
                    }
                    FileOutputStream(exportFile)
                }
            } catch (e: Exception) {
                showErrorToast(e)
                scheduleNextAutomaticBackup()
                return@getContactsToExport
            }

            VcfExporter().exportContacts(
                context = this,
                outputStream = outputStream,
                contacts = contactsToBackup.toMutableList() as ArrayList<Contact>,
                showExportingToast = false
            ) { exportResult ->
                if (exportResult == VcfExporter.ExportResult.EXPORT_FAIL) {
                    toast(org.fossify.commons.R.string.exporting_failed)
                }
            }

            config.lastAutoBackupTime = DateTime.now().millis
            scheduleNextAutomaticBackup()
        }
    }
}

private class BackupException(message: String) : Exception(message)

// On-demand backup for the automation broadcast (ACTION_BACKUP_CONTACTS): export every contact from
// every source to the caller-chosen location. [rawPath] is either a directory (a timestamped
// contacts_<stamp>.vcf is created inside) or a full .vcf file path. [onDone] fires EXACTLY ONCE, on a
// background thread, with the written file's absolute path on success or a human-readable reason on
// failure — so the ordered-broadcast ACK can never come back empty.
fun Context.backupContactsToPath(rawPath: String, onDone: (success: Boolean, result: String) -> Unit) {
    ensureBackgroundThread {
        runCatching { writeContactsBackup(rawPath) }.fold(
            onSuccess = { onDone(true, it) },
            onFailure = { onDone(false, it.message ?: it.javaClass.simpleName) },
        )
    }
}

// The backup body, written to throw on every failure so the single runCatching above turns it into a
// clear ERROR. Runs on the caller's background thread.
private fun Context.writeContactsBackup(rawPath: String): String {
    val contacts = fetchAllContactsBlocking()
    require(contacts.isNotEmpty()) { "no contacts to export" }

    // /sdcard is a symlink; normalize so the MediaStore path checks match.
    val path = rawPath.replaceFirst(Regex("^/sdcard"), Environment.getExternalStorageDirectory().absolutePath)
    val exportFile = if (path.endsWith(".vcf", ignoreCase = true)) {
        File(path)
    } else {
        val now = DateTime.now()
        val stamp = "%04d%02d%02d_%02d%02d%02d".format(
            now.year, now.monthOfYear, now.dayOfMonth, now.hourOfDay, now.minuteOfHour, now.secondOfMinute
        )
        File(path, "contacts_$stamp.vcf")
    }

    // Export to memory FIRST, then write the bytes ourselves. VcfExporter reports EXPORT_OK once the
    // vCards are built (before the stream write), so a failed destination write would otherwise be
    // reported as success; buffering separates "export produced data" from "the write landed".
    val buffer = ByteArrayOutputStream()
    var exportResult = VcfExporter.ExportResult.EXPORT_FAIL
    VcfExporter().exportContacts(this, buffer, contacts, showExportingToast = false) { exportResult = it }
    if (exportResult == VcfExporter.ExportResult.EXPORT_FAIL) error("contact export produced no data")
    val bytes = buffer.toByteArray()
    check(bytes.isNotEmpty()) { "contact export produced no data" }

    openBackupOutputStream(exportFile).use { it.write(bytes) }
    return exportFile.absolutePath
}

// ContactsHelper.getContacts is callback-based (its own background thread); bridge it to a blocking
// call so writeContactsBackup can be one linear try-path.
internal fun Context.fetchAllContactsBlocking(): ArrayList<Contact> {
    val latch = CountDownLatch(1)
    var result = ArrayList<Contact>()
    ContactsHelper(this).getContacts(getAll = true, showOnlyContactsWithNumbers = false) {
        result = it
        latch.countDown()
    }
    if (!latch.await(30, TimeUnit.SECONDS)) throw BackupException("timed out reading contacts")
    return result
}

// Writer strategy for backupContactsToPath, most-capable first: a persisted SAF grant (folders the
// user once picked in-app), then MediaStore (Download/ and Documents/ take non-media files from any
// app with no permission), then a direct FileOutputStream. The direct path is what lets an arbitrary
// absolute location work — but on API 30+ a shared-storage path needs All-files access, so throw a
// remedy-naming error instead of the silent FUSE/MediaProvider write rejection.
internal fun Context.openBackupOutputStream(exportFile: File, mimeType: String = "text/x-vcard"): OutputStream {
    val path = exportFile.absolutePath
    if (hasProperStoredFirstParentUri(path)) {
        val uri = createDocumentUriUsingFirstParentTreeUri(path)
        if (!getDoesFilePathExist(path)) {
            createSAFFileSdk30(path)
        }
        contentResolver.openOutputStream(uri, "wt")?.let { return it }
    }

    mediaStoreBackupOutputStream(exportFile, mimeType)?.let { return it }

    val primary = Environment.getExternalStorageDirectory().absolutePath
    if (isRPlus() && path.startsWith("$primary/") && !Environment.isExternalStorageManager()) {
        throw BackupException(
            "no permission to write $path — grant “All files access” to 白い熊 連絡先 " +
                "(Settings → 自動化), or back up under Download/ or Documents/"
        )
    }
    exportFile.parentFile?.mkdirs()
    return FileOutputStream(exportFile)
}

private fun Context.mediaStoreBackupOutputStream(exportFile: File, mimeType: String): OutputStream? {
    val primary = Environment.getExternalStorageDirectory().absolutePath
    val parent = exportFile.parentFile?.absolutePath ?: return null
    if (!parent.startsWith("$primary/")) {
        return null
    }

    val relativePath = parent.removePrefix("$primary/").trimEnd('/')
    val topDir = relativePath.substringBefore('/')
    if (topDir != Environment.DIRECTORY_DOWNLOADS && topDir != Environment.DIRECTORY_DOCUMENTS) {
        return null
    }

    val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    // Rewrite our own earlier file of the same name instead of piling up "name (1).vcf" copies.
    runCatching {
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf("$relativePath/", exportFile.name)
        contentResolver.query(collection, arrayOf(MediaStore.MediaColumns._ID), selection, selectionArgs, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val uri = ContentUris.withAppendedId(collection, cursor.getLong(0))
                return contentResolver.openOutputStream(uri, "wt")
            }
        }
    }

    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, exportFile.name)
        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        put(MediaStore.MediaColumns.RELATIVE_PATH, "$relativePath/")
    }
    val uri = contentResolver.insert(collection, values) ?: return null
    return contentResolver.openOutputStream(uri, "wt")
}

fun Context.copyUriToTempFile(uri: Uri, name: String): File? {
    val tempFile = getTempFile(name)
    contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(tempFile).use { output ->
            input.copyTo(output)
        }
    }

    return tempFile
}
