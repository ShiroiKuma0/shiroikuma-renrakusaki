package org.fossify.contacts.helpers

import android.content.Context
import android.provider.ContactsContract.CommonDataKinds
import android.provider.ContactsContract.Data
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.fossify.commons.extensions.contactsDB
import org.fossify.commons.models.contacts.DataRow
import org.fossify.commons.models.contacts.Relation
import org.fossify.commons.models.contacts.SipAddress
import java.io.File

/**
 * One-time migration. Builds 1.6.0+097/+098 kept private contacts' extra fields (reading, relations, SIP
 * addresses…) in a side file, `private_contact_extras.json`, because the private store had no column for
 * them. Since commons sk11 they are part of the contact itself (Contact.extra / LocalContact.extra), so
 * this folds whatever that file holds into the contacts and deletes it. Run once per start, off the main
 * thread; it does nothing when the file is gone.
 */
object PrivateExtras {
    data class StoredRow(val mimetype: String, val index: Int, val values: Map<String, String>)

    data class Extra(
        var phoneticFamily: String = "",
        var phoneticMiddle: String = "",
        var phoneticGiven: String = "",
        var rows: List<StoredRow> = emptyList(),
        var sendToVoicemail: Int = 0,
    )

    private const val FILE_NAME = "private_contact_extras.json"

    @Suppress("TooGenericExceptionCaught", "SwallowedException") // an unreadable file is left for the next start
    fun migrateIntoContacts(context: Context) {
        val file = File(context.applicationContext.filesDir, FILE_NAME)
        if (!file.exists()) return
        try {
            val type = object : TypeToken<HashMap<Int, Extra>>() {}.type
            val stored: Map<Int, Extra> = Gson().fromJson(file.readText(), type) ?: emptyMap()
            val dao = context.contactsDB
            stored.forEach { (localId, old) ->
                val local = dao.getContactWithId(localId) ?: return@forEach
                val extra = local.extra
                if (!extra.hasPhonetic()) {
                    extra.phoneticFamily = old.phoneticFamily
                    extra.phoneticMiddle = old.phoneticMiddle
                    extra.phoneticGiven = old.phoneticGiven
                }
                if (old.sendToVoicemail != 0) extra.sendToVoicemail = true
                old.rows.orEmpty().forEach { row ->
                    when (row.mimetype) {
                        CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> Unit
                        CommonDataKinds.Relation.CONTENT_ITEM_TYPE -> extra.relations.add(
                            Relation(row.values[Data.DATA1].orEmpty(), row.values[Data.DATA2]?.toIntOrNull() ?: 0, row.values[Data.DATA3].orEmpty())
                        )
                        CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE -> extra.sipAddresses.add(
                            SipAddress(row.values[Data.DATA1].orEmpty(), row.values[Data.DATA2]?.toIntOrNull() ?: 0, row.values[Data.DATA3].orEmpty())
                        )
                        else -> {
                            if (row.mimetype == CommonDataKinds.Organization.CONTENT_ITEM_TYPE && row.index == 0) {
                                extra.department = extra.department.ifEmpty { row.values[CommonDataKinds.Organization.DEPARTMENT].orEmpty() }
                                extra.jobDescription = extra.jobDescription.ifEmpty { row.values[CommonDataKinds.Organization.JOB_DESCRIPTION].orEmpty() }
                                extra.symbol = extra.symbol.ifEmpty { row.values[CommonDataKinds.Organization.SYMBOL].orEmpty() }
                                extra.companyPhonetic = extra.companyPhonetic.ifEmpty { row.values[CommonDataKinds.Organization.PHONETIC_NAME].orEmpty() }
                                extra.officeLocation = extra.officeLocation.ifEmpty { row.values[CommonDataKinds.Organization.OFFICE_LOCATION].orEmpty() }
                            }
                            extra.rows.add(DataRow(row.mimetype, row.index, row.values))
                        }
                    }
                }
                local.extra = extra
                dao.insertOrUpdate(local)
            }
            file.delete()
        } catch (e: Exception) {
            return
        }
    }
}
