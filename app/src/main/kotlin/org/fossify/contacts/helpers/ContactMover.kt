package org.fossify.contacts.helpers

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import org.fossify.commons.extensions.getCachePhoto
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.LocalContactsHelper
import org.fossify.commons.helpers.SMT_PRIVATE
import org.fossify.commons.models.contacts.Contact
import org.fossify.contacts.extensions.fetchAllContactsBlocking
import org.fossify.contacts.extensions.getCachePhotoUri
import java.io.FileOutputStream

/**
 * Moving one contact between the system store and the private store — the single implementation the
 * visibility menu (dialogs/ChangeVisibilityDialog) and the bulk "move everything to private" tool share.
 * Every field travels inside the contact (Contact.extra, commons sk11); group memberships by name; the
 * photo is the entry's OWN picture, handed over through a fresh cache file (a list-loaded system contact's
 * photoUri is the aggregate's, which may belong to another entry or point at nothing).
 */
object ContactMover {
    class Result(val ok: Boolean, val moved: Boolean)

    /** System → Private (+ visible). The private copy is undone when the system entry will not go. Blocking. */
    fun toPrivate(context: Context, member: Contact, visible: Boolean): Result {
        val full = ContactsHelper(context).getContactWithId(member.id, false) ?: return Result(false, false)
        val copy = full.copy(
            id = 0,
            source = SMT_PRIVATE,
            groups = context.privateGroupsNamed(full.groups),
            photo = null,
            photoUri = ownSystemPhoto(context, full.id)?.let { cacheUri(context, it) }.orEmpty(),
        )
        if (!LocalContactsHelper(context).insertOrUpdateContact(copy)) return Result(false, false)
        val localId = ContactsHelper.lastInsertedId
        if (!ContactsHelper(context).deleteContacts(arrayListOf(full)) || rawExists(context, full.id)) {
            LocalContactsHelper(context).deleteContactIds(mutableListOf(localId.toLong()))
            return Result(false, false)
        }
        if (visible) {
            SystemMirror.setVisible(context, listOf(localId), true)
        }
        return Result(true, moved = true)
    }

    /** Private (+ visible) → System, into the account the visible copies use. Blocking. */
    fun toSystem(context: Context, member: Contact): Result {
        val full = LocalContactsHelper(context).getContactWithId(member.id) ?: return Result(false, false)
        SystemMirror.setVisible(context, listOf(full.id), false)
        val (accountName, accountType) = SystemMirror.targetAccount(context)
        val copy = full.copy(
            id = 0,
            source = accountName.orEmpty(),
            groups = context.systemGroupsNamed(full.groups, accountName, accountType),
            photoUri = full.photo?.let { cacheUri(context, it) }.orEmpty(),
        )
        if (!ContactsHelper(context).insertContact(copy)) return Result(false, false)
        LocalContactsHelper(context).deleteContactIds(mutableListOf(full.id.toLong()))
        return Result(true, moved = true)
    }

    /**
     * Move every system contact — phone account and SIM cards alike — into private storage. A vCard of all
     * of them is written to the cleanup-backups directory first. [onProgress] gets (done, total).
     * Answers (moved, failed). Blocking.
     */
    fun moveAllToPrivate(context: Context, onProgress: (Int, Int) -> Unit): Pair<Int, Int> {
        val mirrorRawIds = SystemMirror.mirrors(context).values.map { it.toInt() }.toHashSet()
        val system = context.fetchAllContactsBlocking().filter { !it.isPrivate() && it.id !in mirrorRawIds }
        if (system.isEmpty()) return 0 to 0
        DuplicateCleaner.backup(context, system, "all-to-private")
        var moved = 0
        var failed = 0
        system.forEachIndexed { i, contact ->
            val result = try {
                toPrivate(context, contact, visible = false)
            } catch (e: Exception) {
                Result(false, false)
            }
            if (result.ok) moved++ else failed++
            onProgress(i + 1, system.size)
        }
        return moved to failed
    }

    private fun rawExists(context: Context, rawId: Int): Boolean = try {
        context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI, arrayOf(ContactsContract.RawContacts._ID),
            "${ContactsContract.RawContacts._ID} = ? AND ${ContactsContract.RawContacts.DELETED} = 0",
            arrayOf(rawId.toString()), null
        )?.use { it.count > 0 } ?: false
    } catch (e: Exception) {
        false
    }

    /** The raw contact's own picture: its full-size photo, else its thumbnail; null when it has none. */
    fun ownSystemPhoto(context: Context, rawId: Int): Bitmap? {
        try {
            val uri = Uri.withAppendedPath(
                ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI, rawId.toLong()),
                ContactsContract.RawContacts.DisplayPhoto.CONTENT_DIRECTORY
            )
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { fd ->
                fd.createInputStream().use { BitmapFactory.decodeStream(it) }?.let { return it }
            }
        } catch (ignored: Exception) {
        }
        try {
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI, arrayOf(ContactsContract.CommonDataKinds.Photo.PHOTO),
                "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf(rawId.toString(), ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE), null
            )?.use { cur ->
                if (cur.moveToFirst()) cur.getBlob(0)?.let { return BitmapFactory.decodeByteArray(it, 0, it.size) }
            }
        } catch (ignored: Exception) {
        }
        return null
    }

    private fun cacheUri(context: Context, bitmap: Bitmap): String? = try {
        val file = context.getCachePhoto()
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, PHOTO_JPEG_QUALITY, it) }
        context.getCachePhotoUri(file).toString()
    } catch (e: Exception) {
        null
    }

    private const val PHOTO_JPEG_QUALITY = 95
}
