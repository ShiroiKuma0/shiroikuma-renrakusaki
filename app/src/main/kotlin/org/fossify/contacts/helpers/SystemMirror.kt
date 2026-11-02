package org.fossify.contacts.helpers

import android.content.ContentProviderOperation
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import androidx.core.net.toUri
import org.fossify.commons.extensions.hasPermission
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.LocalContactsHelper
import org.fossify.commons.helpers.PERMISSION_READ_CONTACTS
import org.fossify.commons.helpers.PERMISSION_WRITE_CONTACTS
import org.fossify.commons.models.contacts.Contact
import org.fossify.contacts.extensions.config
import java.io.ByteArrayOutputStream

/**
 * "Visible to other apps" for a private contact: a COPY of it, written into a system account so apps
 * that only read ContactsContract — WhatsApp/Signal discovery, the car's Bluetooth phonebook, Android
 * Auto, the system call log — know the name. The private record stays the only one 白い熊 edits; the
 * copy is rewritten from it on every save and deleted when the toggle goes off.
 *
 * ## The copy carries as little as it can
 *
 * Name (with its reading) and phone numbers, plus the photo when Config.mirrorPhoto is on. No notes, addresses, e-mails,
 * events or groups: every app with READ_CONTACTS sees whatever is in the copy.
 *
 * ## How a copy is recognised — the one source of truth
 *
 * Its raw contact carries `SYNC1 = "shiroikuma.renrakusaki:<private id>"`. There is no separate flag
 * anywhere: a private contact IS visible exactly when such a raw contact exists. So the state cannot
 * drift from what other apps actually see, and it survives a wipe of our preferences.
 *
 * Because the copy is a real system contact, this app would list it next to the private original; every
 * list we load goes through [withoutMirrors], which drops the copies (or swaps one back for its private
 * original when duplicate-merging picked the copy).
 */
object SystemMirror {
    private const val TAG_PREFIX = "shiroikuma.renrakusaki:"
    private const val MAX_PHOTO_PX = 720
    private const val PHOTO_JPEG_QUALITY = 90

    /**
     * The private ids that had a copy at the last [mirrors] read — what the list badges draw from. Every
     * list load reads [mirrors] (getOwnContacts → withoutMirrors), and so does every toggle, so this is
     * fresh whenever a list is bound.
     */
    @Volatile
    var cachedMirroredIds: Set<Int> = emptySet()
        private set

    /** private contact id → raw contact id of its copy. Empty without READ_CONTACTS. */
    fun mirrors(context: Context): Map<Int, Long> {
        if (!context.hasPermission(PERMISSION_READ_CONTACTS)) return emptyMap()
        val result = HashMap<Int, Long>()
        try {
            context.contentResolver.query(
                RawContacts.CONTENT_URI,
                arrayOf(RawContacts._ID, RawContacts.SYNC1),
                "${RawContacts.SYNC1} LIKE ? AND ${RawContacts.DELETED} = 0",
                arrayOf("$TAG_PREFIX%"),
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val localId = cursor.getString(1)?.removePrefix(TAG_PREFIX)?.toIntOrNull() ?: continue
                    result[localId] = cursor.getLong(0)
                }
            }
        } catch (ignored: Exception) {
        }
        cachedMirroredIds = result.keys.toSet()
        return result
    }

    fun isMirrored(context: Context, localId: Int) = mirrors(context).containsKey(localId)

    /**
     * Turn visibility on or off for these private contacts. Blocking — call off the main thread.
     * Answers how many contacts were changed.
     */
    fun setVisible(context: Context, localIds: Collection<Int>, visible: Boolean): Int {
        if (!context.hasPermission(PERMISSION_WRITE_CONTACTS)) return 0
        val existing = mirrors(context)
        var changed = 0
        localIds.forEach { id ->
            val ok = if (visible) sync(context, id, existing[id]) else remove(context, existing[id])
            if (ok) changed++
        }
        // Re-read, so cachedMirroredIds — what the list badges draw from — reflects the change at once.
        mirrors(context)
        return changed
    }

    /** After a private contact was saved: rewrite its copy, if it has one. Blocking. */
    fun refreshIfMirrored(context: Context, localId: Int) {
        val rawId = mirrors(context)[localId] ?: return
        sync(context, localId, rawId)
    }

    /** Rewrite every copy in place — after the photo setting changed. Blocking. */
    fun refreshAll(context: Context) {
        mirrors(context).forEach { (localId, rawId) -> sync(context, localId, rawId) }
    }

    /** Recreate every copy in the currently chosen account — after the account setting changed. Blocking. */
    fun moveAllToTargetAccount(context: Context) {
        val ids = mirrors(context).keys.toList()
        setVisible(context, ids, false)
        setVisible(context, ids, true)
    }

    /** Delete every copy whose private original no longer exists. Blocking; cheap when nothing is mirrored. */
    fun removeOrphans(context: Context) {
        val existing = mirrors(context)
        if (existing.isEmpty() || !context.hasPermission(PERMISSION_WRITE_CONTACTS)) return
        val helper = LocalContactsHelper(context)
        existing.filterKeys { helper.getContactWithId(it) == null }.values.forEach { remove(context, it) }
    }

    /**
     * Drop our copies from a contact list this app is about to show or export. When duplicate merging
     * kept the copy and discarded the private original (it merges by name and keeps the "longest"), the
     * original is put back in its place; a copy whose original is gone is simply dropped (removeOrphans
     * deletes it). Blocking — loads from the private DB.
     */
    fun withoutMirrors(context: Context, contacts: ArrayList<Contact>): ArrayList<Contact> {
        val byRawId = mirrors(context).entries.associate { (localId, rawId) -> rawId.toInt() to localId }
        if (byRawId.isEmpty()) return contacts
        val privateIds = contacts.filter { it.isPrivate() }.map { it.id }.toHashSet()
        val helper = LocalContactsHelper(context)
        val result = ArrayList<Contact>(contacts.size)
        contacts.forEach { contact ->
            val localId = if (contact.isPrivate()) null else byRawId[contact.id]
            when {
                localId == null -> result.add(contact)
                localId in privateIds -> Unit
                else -> helper.getContactWithId(localId)?.let {
                    privateIds.add(localId)
                    result.add(it)
                }
            }
        }
        return result
    }

    /**
     * The local id of a private contact just inserted from [wanted]: commons' insert answers only with a
     * Boolean, so it is found afterwards — the newest private contact with the same name and numbers,
     * skipping [taken]. Blocking.
     */
    fun findNewestPrivate(context: Context, wanted: Contact, taken: Set<Int> = emptySet()): Int? =
        findNewestPrivateIds(context, listOf(wanted), taken).first()

    /** [findNewestPrivate] for many at once — the private store is read a single time. Blocking. */
    fun findNewestPrivateIds(context: Context, wanted: List<Contact>, alreadyTaken: Set<Int> = emptySet()): List<Int?> {
        if (wanted.isEmpty()) return emptyList()
        val locals = LocalContactsHelper(context).getAllContacts().sortedByDescending { it.id }
        val taken = HashSet(alreadyTaken)
        return wanted.map { one ->
            val numbers = one.phoneNumbers.map { it.normalizedNumber }.toSet()
            locals.firstOrNull { local ->
                local.id !in taken && local.getNameToDisplay() == one.getNameToDisplay() &&
                    local.phoneNumbers.map { it.normalizedNumber }.toSet() == numbers
            }?.id?.also { taken.add(it) }
        }
    }

    /** True when this raw contact id is one of our copies. */
    fun isMirrorRawId(context: Context, rawId: Int) = mirrors(context).values.any { it.toInt() == rawId }

    // ---------------------------------------------------------------------------------------------

    private fun sync(context: Context, localId: Int, existingRawId: Long?): Boolean {
        val contact = LocalContactsHelper(context).getContactWithId(localId) ?: return false
        return try {
            val ops = ArrayList<ContentProviderOperation>()
            if (existingRawId == null) {
                val (accountName, accountType) = targetAccount(context)
                ops.add(
                    ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                        .withValue(RawContacts.ACCOUNT_NAME, accountName)
                        .withValue(RawContacts.ACCOUNT_TYPE, accountType)
                        .withValue(RawContacts.SYNC1, TAG_PREFIX + localId)
                        .build()
                )
            } else {
                ops.add(
                    ContentProviderOperation.newDelete(Data.CONTENT_URI)
                        .withSelection("${Data.RAW_CONTACT_ID} = ?", arrayOf(existingRawId.toString()))
                        .build()
                )
            }

            fun row(mimetype: String): ContentProviderOperation.Builder {
                val b = ContentProviderOperation.newInsert(Data.CONTENT_URI).withValue(Data.MIMETYPE, mimetype)
                return if (existingRawId == null) b.withValueBackReference(Data.RAW_CONTACT_ID, 0)
                else b.withValue(Data.RAW_CONTACT_ID, existingRawId)
            }

            val phonetic = PhoneticName(contact.extra.phoneticFamily, contact.extra.phoneticMiddle, contact.extra.phoneticGiven)
            ops.add(
                row(CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(CommonDataKinds.StructuredName.PREFIX, contact.prefix)
                    .withValue(CommonDataKinds.StructuredName.GIVEN_NAME, contact.firstName)
                    .withValue(CommonDataKinds.StructuredName.MIDDLE_NAME, contact.middleName)
                    .withValue(CommonDataKinds.StructuredName.FAMILY_NAME, contact.surname)
                    .withValue(CommonDataKinds.StructuredName.SUFFIX, contact.suffix)
                    // The reading travels too, so other apps and the car sort the copy where it belongs.
                    .withValue(CommonDataKinds.StructuredName.PHONETIC_FAMILY_NAME, phonetic.family)
                    .withValue(CommonDataKinds.StructuredName.PHONETIC_MIDDLE_NAME, phonetic.middle)
                    .withValue(CommonDataKinds.StructuredName.PHONETIC_GIVEN_NAME, phonetic.given)
                    .build()
            )

            contact.phoneNumbers.forEach { phone ->
                ops.add(
                    row(CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                        .withValue(CommonDataKinds.Phone.NUMBER, phone.value)
                        .withValue(CommonDataKinds.Phone.NORMALIZED_NUMBER, phone.normalizedNumber)
                        .withValue(CommonDataKinds.Phone.TYPE, phone.type)
                        .withValue(CommonDataKinds.Phone.LABEL, phone.label)
                        .withValue(CommonDataKinds.Phone.IS_PRIMARY, if (phone.isPrimary) 1 else 0)
                        .build()
                )
            }

            if (context.config.mirrorPhoto) {
                photoBytes(context, contact)?.let { bytes ->
                    ops.add(row(CommonDataKinds.Photo.CONTENT_ITEM_TYPE).withValue(CommonDataKinds.Photo.PHOTO, bytes).build())
                }
            }

            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun remove(context: Context, rawId: Long?): Boolean {
        rawId ?: return false
        return try {
            // As a sync adapter, so the row is really gone rather than left flagged DELETED for a sync
            // adapter that a local account does not have.
            val uri: Uri = RawContacts.CONTENT_URI.buildUpon()
                .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build()
            context.contentResolver.delete(uri, "${RawContacts._ID} = ?", arrayOf(rawId.toString())) > 0 ||
                context.contentResolver.delete(RawContacts.CONTENT_URI, "${RawContacts._ID} = ?", arrayOf(rawId.toString())) > 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Where copies go: the account chosen in UI settings → Private contacts, else the phone's own local
     * account (Huawei's com.android.huawei.phone, or any "phone"/"local" type), else the provider's
     * account-less local storage. Never a synced cloud account unless 白い熊 picks one.
     */
    fun targetAccount(context: Context): Pair<String?, String?> {
        val sources = ContactsHelper(context).getDeviceContactSources()
        val chosen = context.config.mirrorAccount
        if (chosen.isNotEmpty()) {
            sources.firstOrNull { it.name == chosen && it.type.isNotEmpty() }?.let { return it.name to it.type }
        }
        val local = sources.firstOrNull { it.type == HUAWEI_PHONE_ACCOUNT_TYPE }
            ?: sources.firstOrNull { it.type.contains("phone", true) || it.type.contains("local", true) }
        return if (local != null && local.name.isNotEmpty()) local.name to local.type else null to null
    }

    private fun photoBytes(context: Context, contact: Contact): ByteArray? {
        val bitmap = contact.photo ?: try {
            contact.photoUri.takeIf { it.isNotEmpty() }?.let { uri ->
                context.contentResolver.openInputStream(uri.toUri())?.use { BitmapFactory.decodeStream(it) }
            }
        } catch (e: Exception) {
            null
        } ?: return null

        val scale = MAX_PHOTO_PX.toFloat() / maxOf(bitmap.width, bitmap.height)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else {
            bitmap
        }
        return ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, PHOTO_JPEG_QUALITY, out)
            out.toByteArray()
        }
    }

    const val HUAWEI_PHONE_ACCOUNT_TYPE = "com.android.huawei.phone"
}
