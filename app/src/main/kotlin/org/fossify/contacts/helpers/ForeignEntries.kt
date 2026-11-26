package org.fossify.contacts.helpers

import android.content.ContentResolver
import android.content.Context
import android.provider.ContactsContract
import android.provider.ContactsContract.RawContacts
import org.fossify.commons.extensions.hasPermission
import org.fossify.commons.helpers.PERMISSION_READ_CONTACTS

/**
 * Entries another app keeps in system storage for itself — Molly / Signal, WhatsApp, Telegram, Threema,
 * Viber, Huawei MeeTime. Such an app, once it may read contacts, writes a raw contact of its own (name and
 * number, in its own account) next to every contact it recognises: a link into that app, not a contact.
 *
 * They are **not contacts of ours** (白い熊, 2026-11-26): on 2026-11-26 Molly's entry beside a visible
 * private contact made the row "Mixed", and choosing "Private" then moved Molly's entry into private
 * storage as a second contact. So no list shows them, no visibility state counts them, nothing moves them
 * and the system-storage status leaves them out — every load passes through `getOwnContacts`, which drops
 * them by [rawIds].
 *
 * How one is recognised: its account type belongs to a contacts sync adapter that does not take uploads
 * (`supportsUploading = false` — the app writes there, nobody else can), or is one of the known types
 * below (a sync adapter of a package we cannot see is not listed to us). The phone's own accounts and the
 * SIM accounts have no such adapter; a real synced account (Google, DAV) takes uploads.
 */
object ForeignEntries {
    private val KNOWN_TYPES = setOf(
        "im.molly.app", "org.thoughtcrime.securesms", "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger",
        "ch.threema.app", "com.viber.voip", "com.huawei.meetime.account",
    )

    /** The account types whose entries belong to another app. */
    fun accountTypes(): Set<String> {
        val types = HashSet(KNOWN_TYPES)
        try {
            ContentResolver.getSyncAdapterTypes().forEach { adapter ->
                if (adapter.authority == ContactsContract.AUTHORITY && !adapter.supportsUploading()) types.add(adapter.accountType)
            }
        } catch (ignored: Exception) {
        }
        return types
    }

    fun isForeign(accountType: String?) = !accountType.isNullOrEmpty() && accountType in accountTypes()

    /** The raw contact ids of every such entry. Empty without READ_CONTACTS. Blocking. */
    fun rawIds(context: Context): Set<Int> {
        if (!context.hasPermission(PERMISSION_READ_CONTACTS)) return emptySet()
        val types = accountTypes().toTypedArray()
        val ids = HashSet<Int>()
        try {
            context.contentResolver.query(
                RawContacts.CONTENT_URI,
                arrayOf(RawContacts._ID),
                "${RawContacts.ACCOUNT_TYPE} IN (${types.joinToString(",") { "?" }}) AND ${RawContacts.DELETED} = 0",
                types,
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) ids.add(cursor.getInt(0))
            }
        } catch (ignored: Exception) {
        }
        return ids
    }
}
