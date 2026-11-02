package org.fossify.contacts.helpers

import android.content.Context
import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import org.fossify.commons.extensions.getCachePhoto
import org.fossify.commons.extensions.normalizePhoneNumber
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.LocalContactsHelper
import org.fossify.commons.helpers.PHOTO_ADDED
import org.fossify.commons.helpers.PHOTO_CHANGED
import org.fossify.commons.helpers.PHOTO_UNCHANGED
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.models.contacts.ContactExtraData
import org.fossify.contacts.extensions.config
import org.fossify.contacts.extensions.fetchAllContactsBlocking
import org.fossify.contacts.extensions.getCachePhotoUri
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Housekeeping for contacts stored more than once (activities/DuplicatesActivity).
 *
 * Entries are grouped by display name — the same rule the list uses to merge rows — across both stores
 * (system and private; our own visible copies are not entries). Inside a group, entries whose content is
 * identical (same numbers, e-mails, addresses, notes, organization, every extra field, photo or not, and
 * the same store) form one **exact cluster**: all but one are pure copies and can go without review. A
 * group that still holds more than one distinct entry needs a person to decide: merge into one, keep
 * one, or leave them as different contacts.
 *
 * Nothing is deleted before the affected contacts are written to a vCard in
 * `Android/data/<app>/files/cleanup-backups/`.
 */
object DuplicateCleaner {
    class Cluster(val keeper: Contact, val copies: List<Contact>)
    class Group(val key: String, val name: String, val clusters: List<Cluster>) {
        val distinct: List<Contact> get() = clusters.map { it.keeper }
    }
    class Scan(val groups: List<Group>) {
        val exactCopies: Int get() = groups.sumOf { g -> g.clusters.sumOf { it.copies.size } }
        val contactsWithCopies: Int get() = groups.count { g -> g.clusters.any { it.copies.isNotEmpty() } }
        val toReview: List<Group> get() = groups.filter { it.clusters.size > 1 }
    }

    // System raw contacts that really carry a picture of their own. A list-loaded contact's photoUri is the
    // AGGREGATE's photo, so a SIM entry joined to a photographed contact "has" a photo it does not own.
    @Volatile
    private var systemPhotoIds: Set<Int> = emptySet()

    private fun loadSystemPhotoIds(context: Context): Set<Int> {
        val ids = HashSet<Int>()
        try {
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI, arrayOf(ContactsContract.Data.RAW_CONTACT_ID),
                "${ContactsContract.Data.MIMETYPE} = ? AND (${ContactsContract.CommonDataKinds.Photo.PHOTO} IS NOT NULL OR " +
                    "${ContactsContract.CommonDataKinds.Photo.PHOTO_FILE_ID} IS NOT NULL)",
                arrayOf(ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE), null
            )?.use { c -> while (c.moveToNext()) ids.add(c.getInt(0)) }
        } catch (ignored: Exception) {
        }
        return ids
    }

    /** The entry's own picture: full size when the provider has one, else its thumbnail. Blocking. */
    fun loadPhoto(context: Context, c: Contact): Bitmap? {
        if (c.isPrivate()) {
            return c.photo ?: LocalContactsHelper(context).getContactWithId(c.id)?.photo
        }
        if (c.id !in systemPhotoIds) return null
        try {
            val uri = Uri.withAppendedPath(
                ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI, c.id.toLong()),
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
                arrayOf(c.id.toString(), ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE), null
            )?.use { cur ->
                if (cur.moveToFirst()) cur.getBlob(0)?.let { return BitmapFactory.decodeByteArray(it, 0, it.size) }
            }
        } catch (ignored: Exception) {
        }
        return null
    }

    // A coarse fingerprint of each entry's own picture (12×12 grey levels): two entries "have a photo"
    // either way, but only the same fingerprint means the same picture. Filled during the scan, for the
    // entries of name groups only.
    @Volatile
    private var photoKeys: Map<String, String> = emptyMap()

    private fun idOf(c: Contact) = (if (c.isPrivate()) "p:" else "s:") + c.id

    /** The entry's photo fingerprint, "" when it has no picture of its own. */
    fun photoKey(c: Contact): String = photoKeys[idOf(c)].orEmpty()

    private fun fingerprint(bitmap: Bitmap): String {
        val small = Bitmap.createScaledBitmap(bitmap, PHOTO_KEY_SIZE, PHOTO_KEY_SIZE, true)
        val sb = StringBuilder()
        for (y in 0 until PHOTO_KEY_SIZE) for (x in 0 until PHOTO_KEY_SIZE) {
            val p = small.getPixel(x, y)
            val grey = ((p shr 16 and 0xFF) * 3 + (p shr 8 and 0xFF) * 6 + (p and 0xFF)) / 10
            sb.append('a' + grey / PHOTO_KEY_STEP)
        }
        return sb.toString()
    }

    private const val PHOTO_KEY_SIZE = 12
    private const val PHOTO_KEY_STEP = 32

    fun keyOf(contact: Contact) = contact.getNameToDisplay().trim().replace(Regex("\\s+"), " ").lowercase(Locale.getDefault())

    /** Read every entry and sort them into groups. Blocking. */
    fun scan(context: Context): Scan {
        val mirrorRawIds = SystemMirror.mirrors(context).values.map { it.toInt() }.toHashSet()
        val ignored = context.config.notDuplicateNames
        systemPhotoIds = loadSystemPhotoIds(context)
        val all = context.fetchAllContactsBlocking().filter { !(!it.isPrivate() && it.id in mirrorRawIds) }
        ContactsHelper(context).fillExtras(all)

        val named = all.groupBy { keyOf(it) }
            .filter { (key, entries) -> key.isNotEmpty() && entries.size > 1 && key !in ignored }
        val keys = HashMap<String, String>()
        named.values.flatten().filter { hasPhoto(it) }.forEach { c ->
            loadPhoto(context, c)?.let { keys[idOf(c)] = fingerprint(it) }
        }
        photoKeys = keys
        val groups = named
            .map { (key, entries) ->
                val clusters = entries.groupBy { signature(it) }.values.map { same ->
                    val ordered = same.sortedWith(compareByDescending<Contact> { it.starred }.thenByDescending { it.groups.size }.thenBy { it.id })
                    Cluster(ordered.first(), ordered.drop(1))
                }
                Group(key, entries.first().getNameToDisplay(), clusters)
            }
            .filter { it.clusters.size > 1 || it.clusters.first().copies.isNotEmpty() }
            .sortedBy { it.key }
        return Scan(groups)
    }

    // Everything that makes two entries the same contact — except how the name is split into parts, which
    // varies between identical imports ("Hasici CR" as given+family or as one name).
    // The store and account are part of it: an entry on the SIM card and its twin in the phone account are
    // not copies to throw away unreviewed.
    private fun signature(c: Contact): String = listOf(
        c.isPrivate(),
        c.source,
        c.phoneNumbers.map { it.normalizedNumber.ifEmpty { it.value.normalizePhoneNumber() } }.sorted(),
        c.emails.map { it.value.trim().lowercase(Locale.ROOT) }.sorted(),
        c.addresses.map { it.value.trim() }.sorted(),
        c.IMs.map { "${it.type}:${it.value.trim()}" }.sorted(),
        c.events.map { "${it.type}:${it.value}:${it.label}" }.sorted(),
        c.websites.map { it.trim() }.sorted(),
        c.notes.trim(), c.nickname.trim(), c.organization.company.trim(), c.organization.jobPosition.trim(),
        c.extra.copy(loaded = false, rows = arrayListOf()),
        hasPhoto(c),
        photoKey(c),
    ).toString()

    fun hasPhoto(c: Contact) = if (c.isPrivate()) c.photo != null || c.photoUri.isNotEmpty() else c.id in systemPhotoIds

    // ---- actions ------------------------------------------------------------------------------------

    /** Remove every exact copy, keeping one entry per cluster with the copies' star and groups. Blocking. */
    fun removeExactCopies(context: Context, scan: Scan): Int {
        val clusters = scan.groups.flatMap { it.clusters }.filter { it.copies.isNotEmpty() }
        if (clusters.isEmpty()) return 0
        backup(context, clusters.flatMap { listOf(it.keeper) + it.copies }, "exact-copies")
        var removed = 0
        clusters.forEach { cluster ->
            val keeper = cluster.keeper
            val helper = ContactsHelper(context)
            if (keeper.starred == 0 && cluster.copies.any { it.starred == 1 }) {
                if (keeper.isPrivate()) LocalContactsHelper(context).toggleFavorites(arrayOf(keeper.id), true)
                else helper.addFavorites(arrayListOf(keeper))
            }
            val keeperGroups = keeper.groups.map { it.id }.toSet()
            cluster.copies.flatMap { it.groups }.filter { it.id !in keeperGroups }.distinctBy { it.id }.forEach { group ->
                group.id?.let { helper.addContactsToGroup(arrayListOf(keeper), it) }
            }
            if (helper.deleteContacts(ArrayList(cluster.copies))) removed += cluster.copies.size
        }
        return removed
    }

    /** Keep [keep], delete the other entries of its group. Blocking. */
    fun keepOnly(context: Context, keep: Contact, group: Group): Boolean {
        val others = group.clusters.flatMap { listOf(it.keeper) + it.copies }.filter { !same(it, keep) }
        backup(context, others + keep, "keep-one")
        return ContactsHelper(context).deleteContacts(ArrayList(others))
    }

    /**
     * Fold every entry of [group] into [target]: all numbers, e-mails, addresses, IMs, events, websites,
     * groups, relations and SIP addresses; notes joined; single fields (nickname, organization, reading,
     * organization details) taken from the target, else from the first entry that has one; the photo, star
     * and ringtone kept if any entry has them. The other entries are then deleted. Blocking.
     */
    fun mergeInto(context: Context, target: Contact, group: Group, photoFrom: Contact? = null): Boolean {
        val entries = group.clusters.flatMap { listOf(it.keeper) + it.copies }
        val helper = ContactsHelper(context)
        val full = helper.getContactWithId(target.id, target.isPrivate()) ?: return false
        val others = entries.filter { !same(it, target) }.mapNotNull { helper.getContactWithId(it.id, it.isPrivate()) }
        backup(context, others + full, "merge")

        val merged = full.copy()
        val everyone = listOf(full) + others
        merged.phoneNumbers = everyone.flatMap { it.phoneNumbers }
            .distinctBy { it.normalizedNumber.ifEmpty { it.value.normalizePhoneNumber() } }.toCollection(ArrayList())
        merged.emails = everyone.flatMap { it.emails }.distinctBy { it.value.trim().lowercase(Locale.ROOT) }.toCollection(ArrayList())
        merged.addresses = everyone.flatMap { it.addresses }.distinctBy { it.value.trim() }.toCollection(ArrayList())
        merged.IMs = everyone.flatMap { it.IMs }.distinctBy { "${it.type}:${it.value.trim()}" }.toCollection(ArrayList())
        merged.events = everyone.flatMap { it.events }.distinctBy { "${it.type}:${it.value}" }.toCollection(ArrayList())
        merged.websites = everyone.flatMap { it.websites }.distinctBy { it.trim() }.toCollection(ArrayList())
        // Every memo, one under the other — but one entirely contained in another (an older, shorter
        // version) is left out rather than repeated.
        val notes = everyone.map { it.notes.trim() }.filter { it.isNotEmpty() }.distinct()
        merged.notes = notes.filter { n -> notes.none { other -> other != n && other.contains(n) } }.joinToString("\n")
        merged.nickname = everyone.map { it.nickname }.firstOrNull { it.isNotEmpty() }.orEmpty()
        merged.organization = everyone.map { it.organization }.firstOrNull { it.isNotEmpty() } ?: full.organization
        merged.starred = everyone.maxOf { it.starred }
        merged.ringtone = everyone.mapNotNull { it.ringtone }.firstOrNull { it.isNotEmpty() } ?: full.ringtone
        merged.extra = mergeExtra(everyone.map { it.extra })

        val titles = everyone.flatMap { it.groups }.distinctBy { it.title }
        merged.groups = if (full.isPrivate()) {
            context.privateGroupsNamed(titles)
        } else {
            val type = helper.getDeviceContactSources().firstOrNull { it.name == full.source }?.type
            context.systemGroupsNamed(titles, full.source, type)
        }

        // The photo: the one 白い熊 picked when the entries' pictures differ, else the target's own, else
        // the first other entry's.
        var photoStatus = PHOTO_UNCHANGED
        val donor = when {
            photoFrom != null && !same(photoFrom, full) -> photoFrom
            photoFrom != null -> null
            !hasPhoto(full) -> others.firstOrNull { hasPhoto(it) }
            else -> null
        }
        if (donor != null) {
            photoUriOf(context, donor)?.let {
                merged.photoUri = it
                photoStatus = if (hasPhoto(full)) PHOTO_CHANGED else PHOTO_ADDED
            }
        }

        val saved = if (full.isPrivate()) LocalContactsHelper(context).insertOrUpdateContact(merged) else helper.updateContact(merged, photoStatus)
        if (!saved) return false
        return helper.deleteContacts(ArrayList(others))
    }

    /** Delete every entry of [group] — a contact not wanted at all. Backed up first. Blocking. */
    fun deleteAll(context: Context, group: Group): Boolean {
        val all = group.clusters.flatMap { listOf(it.keeper) + it.copies }
        backup(context, all, "delete-all")
        return ContactsHelper(context).deleteContacts(ArrayList(all))
    }

    /** Mark this group as different contacts that merely share a name — it is not offered again. */
    fun markNotDuplicates(context: Context, group: Group) {
        context.config.notDuplicateNames = context.config.notDuplicateNames + group.key
    }

    // ---- helpers ------------------------------------------------------------------------------------

    private fun same(a: Contact, b: Contact) = a.id == b.id && a.isPrivate() == b.isPrivate()

    private fun mergeExtra(extras: List<ContactExtraData>): ContactExtraData {
        val first = extras.first().copy()
        fun pick(get: (ContactExtraData) -> String) = extras.map(get).firstOrNull { it.isNotEmpty() }.orEmpty()
        val withReading = extras.firstOrNull { it.hasPhonetic() }
        if (withReading != null) {
            first.phoneticFamily = withReading.phoneticFamily
            first.phoneticMiddle = withReading.phoneticMiddle
            first.phoneticGiven = withReading.phoneticGiven
        }
        first.department = pick { it.department }
        first.jobDescription = pick { it.jobDescription }
        first.officeLocation = pick { it.officeLocation }
        first.companyPhonetic = pick { it.companyPhonetic }
        first.symbol = pick { it.symbol }
        first.sendToVoicemail = extras.any { it.sendToVoicemail }
        first.relations = extras.flatMap { it.relations }.distinctBy { "${it.type}:${it.name.trim()}" }.toCollection(ArrayList())
        first.sipAddresses = extras.flatMap { it.sipAddresses }.distinctBy { it.value.trim() }.toCollection(ArrayList())
        first.rows = extras.flatMap { it.rows }.distinctBy { "${it.mimetype}:${it.values}" }.toCollection(ArrayList())
        first.loaded = true
        return first
    }

    // The donor's own picture, handed over through a fresh cache file (never the aggregate's photo URI,
    // which may point at nothing).
    private fun photoUriOf(context: Context, contact: Contact): String? {
        val bitmap = loadPhoto(context, contact) ?: return null
        return try {
            val file = context.getCachePhoto()
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            context.getCachePhotoUri(file).toString()
        } catch (e: Exception) {
            null
        }
    }

    /** The backup directory shown to 白い熊. */
    fun backupDir(context: Context): File? = context.getExternalFilesDir(null)?.let { File(it, "cleanup-backups") }

    fun backup(context: Context, contacts: List<Contact>, what: String) {
        val dir = backupDir(context) ?: return
        dir.mkdirs()
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(Date())
        val file = File(dir, "${what}_$stamp.vcf")
        try {
            VcfExporter().exportContacts(context, FileOutputStream(file), ArrayList(contacts), showExportingToast = false) { }
        } catch (ignored: Exception) {
        }
    }
}
