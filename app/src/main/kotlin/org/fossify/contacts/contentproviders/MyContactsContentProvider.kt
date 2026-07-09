package org.fossify.contacts.contentproviders

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.telephony.PhoneNumberUtils
import com.google.gson.Gson
import org.fossify.commons.helpers.LocalContactsHelper
import org.fossify.commons.helpers.MyContactsContentProvider
import org.fossify.contacts.extensions.config
import org.fossify.contacts.helpers.SIM_SLOT_PROVIDER_PATH

class MyContactsContentProvider : ContentProvider() {
    override fun insert(uri: Uri, contentValues: ContentValues?) = null

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        if (context == null) {
            return null
        }

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

            LocalContactsHelper(context!!).getPrivateSimpleContactsSync(favoritesOnly, withPhoneNumbersOnly).forEach {
                val phoneNumbers = Gson().toJson(it.phoneNumbers)
                val birthdays = Gson().toJson(it.birthdays)
                val anniversaries = Gson().toJson(it.anniversaries)

                matrixCursor.newRow()
                    .add(MyContactsContentProvider.COL_RAW_ID, it.rawId)
                    .add(MyContactsContentProvider.COL_CONTACT_ID, it.contactId)
                    .add(MyContactsContentProvider.COL_NAME, it.name)
                    .add(MyContactsContentProvider.COL_PHOTO_URI, it.photoUri)
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

    override fun onCreate() = true

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 1

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun getType(uri: Uri) = ""
}
