package org.fossify.contacts.contentproviders

import android.os.Bundle
import org.fossify.contacts.helpers.isCallRoute
import org.fossify.contacts.helpers.numberDefaultRoute
import org.fossify.contacts.helpers.setNumberDefaultRoute
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.telephony.PhoneNumberUtils
import com.google.gson.Gson
import org.fossify.commons.extensions.contactsDB
import org.fossify.commons.helpers.LocalContactsHelper
import org.fossify.commons.helpers.MyContactsContentProvider
import org.fossify.contacts.extensions.config
import org.fossify.contacts.helpers.PHOTO_PROVIDER_PATH
import org.fossify.contacts.helpers.SIM_SLOT_PROVIDER_PATH
import java.io.File
import java.io.FileNotFoundException

class MyContactsContentProvider : ContentProvider() {
    override fun insert(uri: Uri, contentValues: ContentValues?) = null

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val ctx = context ?: return null

        if (!isCallerAllowed()) {
            return null
        }

        // A reader that is about to watch for changes must find the watch attached to the live database.
        org.fossify.contacts.helpers.PrivateContactsWatch.ensure(ctx)

        // Per-contact default SIM lookup for the Phone fork: content://<authority>/sim_slot,
        // selectionArgs[0] = the dialed number. Returns a one-row cursor with the SIM slot (1/2, 0 = none).
        if (uri.lastPathSegment == SIM_SLOT_PROVIDER_PATH) {
            return querySimSlot(selectionArgs)
        }

        if (!context!!.config.showPrivateContacts) {
            return null
        } else {
            val matrixCursor = MatrixCursor(
                arrayOf(
                    MyContactsContentProvider.COL_RAW_ID,
                    MyContactsContentProvider.COL_CONTACT_ID,
                    MyContactsContentProvider.COL_NAME,
                    MyContactsContentProvider.COL_PHOTO_URI,
                    MyContactsContentProvider.COL_PHONE_NUMBERS,
                    MyContactsContentProvider.COL_BIRTHDAYS,
                    MyContactsContentProvider.COL_ANNIVERSARIES
                )
            )

            val favoritesOnly = selectionArgs?.getOrNull(0)?.equals("1") ?: false
            val withPhoneNumbersOnly = selectionArgs?.getOrNull(1)?.equals("1") ?: true

            // A private contact keeps its picture as bytes in our Room DB — its stored photo_uri is
            // always empty, so callers (denwa) got no picture at all. Hand them a URI into this
            // provider instead (served by openFile below). The ?v= is a hash of the bytes, so an image
            // cache keyed by URI (Glide) picks up a changed photo.
            val photoUris = ctx.contactsDB.getContacts().mapNotNull { local ->
                val id = local.id ?: return@mapNotNull null
                val bytes = local.photo?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                id to photoUri(id, bytes)
            }.toMap()

            LocalContactsHelper(context!!).getPrivateSimpleContactsSync(favoritesOnly, withPhoneNumbersOnly).forEach {
                val phoneNumbers = Gson().toJson(it.phoneNumbers)
                val birthdays = Gson().toJson(it.birthdays)
                val anniversaries = Gson().toJson(it.anniversaries)

                matrixCursor.newRow()
                    .add(MyContactsContentProvider.COL_RAW_ID, it.rawId)
                    .add(MyContactsContentProvider.COL_CONTACT_ID, it.contactId)
                    .add(MyContactsContentProvider.COL_NAME, it.name)
                    .add(MyContactsContentProvider.COL_PHOTO_URI, it.photoUri.ifEmpty { photoUris[it.rawId].orEmpty() })
                    .add(MyContactsContentProvider.COL_PHONE_NUMBERS, phoneNumbers)
                    .add(MyContactsContentProvider.COL_BIRTHDAYS, birthdays)
                    .add(MyContactsContentProvider.COL_ANNIVERSARIES, anniversaries)
            }

            return matrixCursor
        }
    }

    @Suppress("DEPRECATION")
    private fun querySimSlot(selectionArgs: Array<out String>?): Cursor {
        // Defensive: this is called cross-process by the dialer; never let it throw (it would crash
        // our process). Any failure just yields "no preference" (slot 0).
        val slot = try {
            val number = selectionArgs?.getOrNull(0).orEmpty()
            val ctx = context
            if (number.isEmpty() || ctx == null) {
                0
            } else {
                ctx.config.getAllSimSlots().entries
                    .firstOrNull { PhoneNumberUtils.compare(it.key, number) }
                    ?.value ?: 0
            }
        } catch (ignored: Exception) {
            0
        }

        return MatrixCursor(arrayOf("slot")).apply {
            newRow().add("slot", slot)
        }
    }

    /**
     * The call-route door (from +133), for the sister apps: one contact has one route everywhere.
     *  - `get_route`, arg or extra `number` → `{result: "OK", route: sim1 | sim2 | rin | rin:N | off}`
     *  - `set_route`, extras `{number, route}` → `{result: "OK"}`; `off` clears the route
     * Errors: `ERROR:not allowed | no number | bad route`. Any other method returns null, and so does an
     * older 連絡先 for these two — that is how a caller tells "no route set" (OK + off) from "no door".
     * Same gate as the rest of the provider. Never throws: it is called from another process.
     */
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != METHOD_GET_ROUTE && method != METHOD_SET_ROUTE) return null
        fun answer(result: String, route: String? = null) = Bundle().apply {
            putString("result", result)
            route?.let { putString("route", it) }
        }
        val ctx = context ?: return answer("ERROR:not allowed")
        if (!isCallerAllowed()) return answer("ERROR:not allowed")
        return try {
            val number = (extras?.getString("number") ?: arg).orEmpty().trim()
            if (number.isEmpty()) return answer("ERROR:no number")
            if (method == METHOD_GET_ROUTE) {
                answer("OK", ctx.numberDefaultRoute(number))
            } else {
                val route = extras?.getString("route").orEmpty()
                if (!isCallRoute(route)) return answer("ERROR:bad route")
                ctx.setNumberDefaultRoute(number, route)
                answer("OK")
            }
        } catch (ignored: Exception) {
            answer("ERROR:failed")
        }
    }

    // The gate: only apps 白い熊 has allowed (UI settings → Private contacts) read anything here —
    // contacts, photos and SIM slot alike. callingPackage is vouched for by the framework against the
    // binder uid, so it cannot be spoofed by the caller.
    private fun isCallerAllowed(): Boolean {
        val ctx = context ?: return false
        val caller = callingPackage
        return caller == ctx.packageName || ctx.config.isPrivateDataAppAllowed(caller)
    }

    private fun photoUri(id: Int, bytes: ByteArray): String =
        Uri.Builder()
            .scheme("content")
            .authority("${context!!.packageName}.contactsprovider")
            .appendPath(PHOTO_PROVIDER_PATH)
            .appendPath(id.toString())
            .appendQueryParameter("v", Integer.toHexString(bytes.contentHashCode()))
            .build()
            .toString()

    private fun isPhotoUri(uri: Uri) = uri.pathSegments.size == 2 && uri.pathSegments[0] == PHOTO_PROVIDER_PATH

    // content://<authority>/photo/<private id>: the picture of a private contact, read by denwa through
    // ContentResolver.loadThumbnail / openInputStream. The bytes go through a short-lived file in our
    // cache that is unlinked as soon as it is opened, so the caller gets a real, seekable descriptor
    // (ImageDecoder wants one) and nothing is left behind.
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val ctx = context ?: throw FileNotFoundException(uri.toString())
        if (!isPhotoUri(uri) || mode != "r" || !isCallerAllowed() || !ctx.config.showPrivateContacts) {
            throw FileNotFoundException(uri.toString())
        }

        val id = uri.pathSegments[1].toIntOrNull() ?: throw FileNotFoundException(uri.toString())
        val bytes = ctx.contactsDB.getContactWithId(id)?.photo?.takeIf { it.isNotEmpty() }
            ?: throw FileNotFoundException(uri.toString())

        val dir = File(ctx.cacheDir, "provider-photos").apply { mkdirs() }
        val file = File.createTempFile("photo-$id-", ".jpg", dir)
        return try {
            file.writeBytes(bytes)
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } finally {
            file.delete()
        }
    }

    override fun onCreate() = true

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 1

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    // loadThumbnail asks for "image/*", which the default openTypedAssetFile grants only when getType matches.
    override fun getType(uri: Uri) = if (isPhotoUri(uri)) "image/jpeg" else ""
}

private const val METHOD_GET_ROUTE = "get_route"
private const val METHOD_SET_ROUTE = "set_route"
