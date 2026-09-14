package org.fossify.contacts.helpers

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.provider.ContactsContract.CommonDataKinds.Im
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.widget.Toast
import androidx.core.net.toUri
import ezvcard.Ezvcard
import ezvcard.VCard
import ezvcard.util.PartialDate
import org.fossify.commons.extensions.getCachePhoto
import org.fossify.commons.extensions.groupsDB
import org.fossify.commons.extensions.normalizePhoneNumber
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.DEFAULT_MIMETYPE
import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.contacts.Address
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.models.contacts.Email
import org.fossify.commons.models.contacts.Event
import org.fossify.commons.models.contacts.Group
import org.fossify.commons.models.contacts.IM
import org.fossify.commons.models.contacts.Organization
import org.fossify.contacts.extensions.getCachePhotoUri
import org.fossify.contacts.helpers.VcfImporter.ImportResult.IMPORT_FAIL
import org.fossify.contacts.helpers.VcfImporter.ImportResult.IMPORT_OK
import org.fossify.contacts.helpers.VcfImporter.ImportResult.IMPORT_PARTIAL
import java.io.File
import java.io.FileOutputStream
import java.net.URLDecoder
import java.time.LocalDate
import java.util.Locale

// SettingsProvider, where the stock "default ringtone" pointers live.
private const val SETTINGS_AUTHORITY = "settings"

// An explicit no on a favourite flag. Anything else — including a bare property with no value at all,
// which is how some exporters write it — marks the contact as a favourite.
private val FALSE_VALUES = setOf("0", "false", "no")

/**
 * [activity] is a plain [Context], not an Activity. It was a `SimpleActivity` until contract v2's data
 * door (automation/) needed to import headlessly from a foreground service, with no Activity in the
 * process at all — and nothing here ever needed more than a Context: it opens assets, touches the groups
 * DB, writes a cache photo and toasts an error, all Context-level.
 */
class VcfImporter(val activity: Context) {
    enum class ImportResult {
        IMPORT_FAIL, IMPORT_OK, IMPORT_PARTIAL
    }

    private var contactsImported = 0
    private var contactsFailed = 0

    fun importContacts(path: String, targetContactSource: String): ImportResult {
        try {
            val inputStream = if (path.contains("/")) {
                File(path).inputStream()
            } else {
                activity.assets.open(path)
            }

            val ezContacts = Ezvcard.parse(inputStream).all()
            for (ezContact in ezContacts) {
                val structuredName = ezContact.structuredName
                val prefix = structuredName?.prefixes?.firstOrNull() ?: ""
                val firstName = structuredName?.given ?: ""
                val middleName = structuredName?.additionalNames?.firstOrNull() ?: ""
                val surname = structuredName?.family ?: ""
                val suffix = structuredName?.suffixes?.firstOrNull() ?: ""
                val nickname = ezContact.nickname?.values?.firstOrNull() ?: ""
                val phoneticName = getPhoneticName(ezContact)
                var photoUri = ""

                val phoneNumbers = ArrayList<PhoneNumber>()
                ezContact.telephoneNumbers.forEach {
                    val number = it.text
                    val type = getPhoneNumberTypeId(
                        type = it.types.firstOrNull()?.value ?: MOBILE,
                        subtype = it.types.getOrNull(1)?.value
                    )
                    val label = if (type == Phone.TYPE_CUSTOM) {
                        it.types.firstOrNull()?.value ?: ""
                    } else {
                        ""
                    }

                    val preferred = getPreferredValue(it.types.lastOrNull()?.value) == 1
                    phoneNumbers.add(
                        PhoneNumber(
                            value = number,
                            type = type,
                            label = label,
                            normalizedNumber = number.normalizePhoneNumber(),
                            isPrimary = preferred
                        )
                    )
                }

                val emails = ArrayList<Email>()
                ezContact.emails.forEach {
                    val email = it.value
                    val type = getEmailTypeId(it.types.firstOrNull()?.value ?: HOME)
                    val label = if (type == CommonDataKinds.Email.TYPE_CUSTOM) {
                        it.types.firstOrNull()?.value ?: ""
                    } else {
                        ""
                    }

                    if (email.isNotEmpty()) {
                        emails.add(Email(email, type, label))
                    }
                }

                val addresses = ArrayList<Address>()
                ezContact.addresses.forEach {
                    var address = it.streetAddress ?: ""
                    val type = getAddressTypeId(it.types.firstOrNull()?.value ?: HOME)
                    val label = if (type == StructuredPostal.TYPE_CUSTOM) {
                        it.types.firstOrNull()?.value ?: ""
                    } else {
                        ""
                    }
                    val country = it.country ?: ""
                    val region = it.region ?: ""
                    val city = it.locality ?: ""
                    val postcode = it.postalCode ?: ""
                    val pobox = it.poBox ?: ""
                    val street = it.streetAddress ?: ""
                    val neighborhood = it.extendedAddress ?: ""

                    if (it.locality?.isNotEmpty() == true) {
                        address += " ${it.locality} "
                    }

                    if (it.region?.isNotEmpty() == true) {
                        if (address.isNotEmpty()) {
                            address = "${address.trim()}, "
                        }
                        address += "${it.region} "
                    }

                    if (it.postalCode?.isNotEmpty() == true) {
                        address += "${it.postalCode} "
                    }

                    if (it.country?.isNotEmpty() == true) {
                        address += "${it.country} "
                    }

                    address = address.trim()
                    if (address.isNotEmpty()) {
                        addresses.add(
                            Address(
                                value = address,
                                type = type,
                                label = label,
                                country = country,
                                region = region,
                                city = city,
                                postcode = postcode,
                                pobox = pobox,
                                street = street,
                                neighborhood = neighborhood
                            )
                        )
                    }
                }

                val events = ArrayList<Event>()
                ezContact.anniversaries.forEach { anniversary ->
                    val event = if (anniversary.date != null) {
                        Event(
                            formatDateToDayCode(LocalDate.from(anniversary.date)),
                            CommonDataKinds.Event.TYPE_ANNIVERSARY
                        )
                    } else {
                        Event(
                            formatPartialDateToDayCode(anniversary.partialDate),
                            CommonDataKinds.Event.TYPE_ANNIVERSARY
                        )
                    }
                    events.add(event)
                }

                ezContact.birthdays.forEach { birthday ->
                    val event = if (birthday.date != null) {
                        Event(
                            formatDateToDayCode(LocalDate.from(birthday.date)),
                            CommonDataKinds.Event.TYPE_BIRTHDAY
                        )
                    } else {
                        Event(
                            formatPartialDateToDayCode(birthday.partialDate),
                            CommonDataKinds.Event.TYPE_BIRTHDAY
                        )
                    }
                    events.add(event)
                }

                // Android's "other" and custom-label events, which vCard has no property for.
                ezContact.getExtendedProperties(X_EVENT).forEach { property ->
                    val value = property.value?.trim().orEmpty()
                    if (value.isNotEmpty()) {
                        events.add(Event(value, getEventTypeId(property.getParameter(X_EVENT_TYPE_PARAM))))
                    }
                }

                val starred = if (isFavorite(ezContact)) 1 else 0
                val contactId = 0
                val notes = ezContact.notes.firstOrNull()?.value ?: ""
                val groups = getContactGroups(ezContact)
                val company = ezContact.organization?.values?.firstOrNull() ?: ""
                val jobPosition = ezContact.titles?.firstOrNull()?.value ?: ""
                val organization = Organization(company, jobPosition)
                val websites = ezContact.urls.map { it.value } as ArrayList<String>
                val photoData = ezContact.photos.firstOrNull()?.data
                val photo = if (photoData != null) {
                    BitmapFactory.decodeByteArray(photoData, 0, photoData.size)
                } else {
                    null
                }

                val thumbnailUri = savePhoto(photoData)
                if (thumbnailUri.isNotEmpty()) {
                    photoUri = thumbnailUri
                }

                val ringtone = getResolvableRingtone(ezContact)

                val IMs = ArrayList<IM>()
                ezContact.impps.forEach {
                    val typeString = it.uri.scheme
                    val value = URLDecoder.decode(
                        it.uri.toString().substring(it.uri.scheme.length + 1),
                        "UTF-8"
                    )
                    val type = when {
                        it.isAim -> Im.PROTOCOL_AIM
                        it.isYahoo -> Im.PROTOCOL_YAHOO
                        it.isMsn -> Im.PROTOCOL_MSN
                        it.isIcq -> Im.PROTOCOL_ICQ
                        it.isSkype -> Im.PROTOCOL_SKYPE
                        typeString == HANGOUTS -> Im.PROTOCOL_GOOGLE_TALK
                        typeString == QQ -> Im.PROTOCOL_QQ
                        typeString == JABBER -> Im.PROTOCOL_JABBER
                        else -> Im.PROTOCOL_CUSTOM
                    }

                    val label = if (type == Im.PROTOCOL_CUSTOM) {
                        URLDecoder.decode(
                            typeString,
                            "UTF-8"
                        )
                    } else {
                        ""
                    }
                    val IM = IM(value, type, label)
                    IMs.add(IM)
                }

                val contact = Contact(
                    id = 0,
                    prefix = prefix,
                    firstName = firstName,
                    middleName = middleName,
                    surname = surname,
                    suffix = suffix,
                    nickname = nickname,
                    photoUri = photoUri,
                    phoneNumbers = phoneNumbers,
                    emails = emails,
                    addresses = addresses,
                    events = events,
                    source = targetContactSource,
                    starred = starred,
                    contactId = contactId,
                    thumbnailUri = thumbnailUri,
                    photo = photo,
                    notes = notes,
                    groups = groups,
                    organization = organization,
                    websites = websites,
                    IMs = IMs,
                    mimetype = DEFAULT_MIMETYPE,
                    ringtone = ringtone
                )

                // if there is no N and ORG fields at the given contact, only FN, treat it as an organization
                if (
                    contact.getNameToDisplay().isEmpty()
                    && contact.organization.isEmpty()
                    && ezContact.formattedName?.value?.isNotEmpty() == true
                ) {
                    contact.organization.company = ezContact.formattedName.value
                    contact.mimetype = CommonDataKinds.Organization.CONTENT_ITEM_TYPE
                }

                if (contact.isABusinessContact()) {
                    contact.mimetype = CommonDataKinds.Organization.CONTENT_ITEM_TYPE
                }

                if (ContactsHelper(activity).insertContact(contact)) {
                    contactsImported++
                    // A private contact lives in the local DB, which has no phonetic columns at all.
                    if (!phoneticName.isEmpty && !contact.isPrivate()) {
                        writePhoneticName(contact, phoneticName)
                    }
                }
            }
        } catch (e: Exception) {
            activity.showErrorToast(e, Toast.LENGTH_LONG)
            contactsFailed++
        }

        return when {
            contactsImported == 0 -> IMPORT_FAIL
            contactsFailed > 0 -> IMPORT_PARTIAL
            else -> IMPORT_OK
        }
    }

    /**
     * The reading (フリガナ) a vCard carries. The X-PHONETIC-* properties win: they keep the
     * family/middle/given split that the provider's own columns want. SORT-AS (4.0) and SORT-STRING
     * (3.0) are the standard fallback and arrive as one opaque key, so they are split the way the edit
     * screen splits a typed reading.
     */
    private fun getPhoneticName(ezContact: VCard): PhoneticName {
        val fromExtended = PhoneticName(
            family = ezContact.getExtendedProperty(X_PHONETIC_LAST_NAME)?.value?.trim().orEmpty(),
            middle = ezContact.getExtendedProperty(X_PHONETIC_MIDDLE_NAME)?.value?.trim().orEmpty(),
            given = ezContact.getExtendedProperty(X_PHONETIC_FIRST_NAME)?.value?.trim().orEmpty(),
        )
        if (!fromExtended.isEmpty) {
            return fromExtended
        }

        val sortAs = ezContact.structuredName?.sortAs.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }
        return when {
            sortAs.isEmpty() -> PhoneticName.fromJoined(ezContact.sortString?.value.orEmpty())
            sortAs.size == 1 -> PhoneticName.fromJoined(sortAs.first())
            else -> PhoneticName(sortAs.first(), "", sortAs.drop(1).joinToString(" "))
        }
    }

    /**
     * Write the reading onto the contact commons has just inserted. Its insertContact() builds the
     * StructuredName row without the phonetic columns and hands back nothing but a Boolean, so the row
     * has to be found again: raw contact ids are handed out in ascending order, so the newest
     * StructuredName row is the one just written. The name on that row is checked against what was
     * inserted before anything is updated — if a sync adapter slipped an insert in between, the reading
     * is dropped rather than written onto somebody else's contact.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException") // a reading is not worth failing an import over
    private fun writePhoneticName(contact: Contact, phonetic: PhoneticName) {
        val projection = arrayOf(
            ContactsContract.Data._ID,
            CommonDataKinds.StructuredName.GIVEN_NAME,
            CommonDataKinds.StructuredName.FAMILY_NAME,
        )
        val selection = "${ContactsContract.Data.MIMETYPE} = ?"
        val args = arrayOf(CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
        // Deliberately no LIMIT in the sort order — OEM providers have been known to choke on one, and
        // the first row of a descending cursor is the same answer.
        val order = "${ContactsContract.Data.RAW_CONTACT_ID} DESC"

        try {
            var dataId = -1L
            activity.contentResolver.query(ContactsContract.Data.CONTENT_URI, projection, selection, args, order)
                ?.use { cursor ->
                    if (
                        cursor.moveToFirst() &&
                        cursor.getString(1).orEmpty() == contact.firstName &&
                        cursor.getString(2).orEmpty() == contact.surname
                    ) {
                        dataId = cursor.getLong(0)
                    }
                }

            if (dataId < 0) {
                return
            }

            val values = ContentValues().apply {
                put(CommonDataKinds.StructuredName.PHONETIC_FAMILY_NAME, phonetic.family)
                put(CommonDataKinds.StructuredName.PHONETIC_MIDDLE_NAME, phonetic.middle)
                put(CommonDataKinds.StructuredName.PHONETIC_GIVEN_NAME, phonetic.given)
            }
            activity.contentResolver.update(
                ContactsContract.Data.CONTENT_URI,
                values,
                "${ContactsContract.Data._ID} = ?",
                arrayOf(dataId.toString()),
            )
        } catch (e: Exception) {
            return
        }
    }

    /**
     * Whether the card marks a favourite. vCard has no property for one, so this reads the property our
     * exporter writes plus the spellings other exporters use, and treats anything but an explicit no as
     * a yes. Until it existed every import landed unstarred, and a restored phone came up with an empty
     * Favorites tab.
     */
    private fun isFavorite(ezContact: VCard) = X_FAVORITE_NAMES.any { name ->
        ezContact.getExtendedProperties(name).any {
            it.value.orEmpty().trim().lowercase(Locale.getDefault()) !in FALSE_VALUES
        }
    }

    /**
     * The custom ringtone, but only if it still names something on THIS phone. A ringtone URI is a
     * device-local id — content://media/external/audio/media/1234 points at a different track, or at
     * nothing, once the card is imported elsewhere — and a contact carrying a dead one rings silently.
     * So an unresolvable URI is dropped and the contact keeps the default ringtone.
     *
     * getType() is the probe deliberately: a provider must answer it whatever permissions the caller
     * holds, so a missing READ_MEDIA_AUDIO cannot make a live ringtone look dead.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException") // a ringtone is not worth failing an import over
    private fun getResolvableRingtone(ezContact: VCard): String? {
        val value = ezContact.getExtendedProperty(X_CUSTOM_RINGTONE)?.value?.trim().orEmpty()
        if (value.isEmpty()) {
            return null
        }

        return try {
            val uri = value.toUri()
            val resolves = when (uri.scheme?.lowercase(Locale.getDefault())) {
                // The stock default-ringtone pointers answer no MIME type, and need no probe: they
                // name the same thing on every device.
                ContentResolver.SCHEME_CONTENT ->
                    uri.authority == SETTINGS_AUTHORITY || activity.contentResolver.getType(uri) != null

                ContentResolver.SCHEME_FILE -> File(uri.path.orEmpty()).exists()
                ContentResolver.SCHEME_ANDROID_RESOURCE -> activity.contentResolver.getType(uri) != null
                else -> false
            }

            if (resolves) value else null
        } catch (e: Exception) {
            null
        }
    }

    private fun getEventTypeId(type: String?) = when (type?.uppercase(Locale.getDefault())) {
        OTHER -> CommonDataKinds.Event.TYPE_OTHER
        CUSTOM -> CommonDataKinds.Event.TYPE_CUSTOM
        // A type id this app cannot make, written by whatever exported the card: keep it as it came.
        else -> type?.toIntOrNull() ?: CommonDataKinds.Event.TYPE_OTHER
    }

    private fun formatDateToDayCode(date: LocalDate): String {
        if (date.year == 1900) {
            // for backward compatibility with old exports
            return "--%02d-%02d".format(date.monthValue, date.dayOfMonth)
        }

        return "%04d-%02d-%02d".format(
            date.year, date.monthValue, date.dayOfMonth
        )
    }

    private fun formatPartialDateToDayCode(partialDate: PartialDate): String {
        return "--%02d-%02d".format(
            partialDate.month, partialDate.date
        )
    }

    private fun getContactGroups(ezContact: VCard): ArrayList<Group> {
        val groups = ArrayList<Group>()
        if (ezContact.categories != null) {
            val groupNames = ezContact.categories.values

            if (groupNames != null) {
                val storedGroups = ContactsHelper(activity).getStoredGroupsSync()

                groupNames.forEach {
                    val groupName = it
                    val storedGroup = storedGroups.firstOrNull { it.title == groupName }

                    if (storedGroup != null) {
                        groups.add(storedGroup)
                    } else {
                        val newGroup = Group(null, groupName)
                        val id = activity.groupsDB.insertOrUpdate(newGroup)
                        newGroup.id = id
                        groups.add(newGroup)
                    }
                }
            }
        }
        return groups
    }

    private fun getPhoneNumberTypeId(type: String, subtype: String?) =
        when (type.uppercase(Locale.getDefault())) {
            CELL -> Phone.TYPE_MOBILE
            HOME -> {
                if (subtype?.uppercase(Locale.getDefault()) == FAX) {
                    Phone.TYPE_FAX_HOME
                } else {
                    Phone.TYPE_HOME
                }
            }

            WORK -> {
                if (subtype?.uppercase(Locale.getDefault()) == FAX) {
                    Phone.TYPE_FAX_WORK
                } else {
                    Phone.TYPE_WORK
                }
            }

            MAIN -> Phone.TYPE_MAIN
            WORK_FAX -> Phone.TYPE_FAX_WORK
            HOME_FAX -> Phone.TYPE_FAX_HOME
            FAX -> Phone.TYPE_FAX_WORK
            PAGER -> Phone.TYPE_PAGER
            OTHER -> Phone.TYPE_OTHER
            else -> Phone.TYPE_CUSTOM
        }

    private fun getEmailTypeId(type: String) = when (type.uppercase(Locale.getDefault())) {
        HOME -> CommonDataKinds.Email.TYPE_HOME
        WORK -> CommonDataKinds.Email.TYPE_WORK
        MOBILE -> CommonDataKinds.Email.TYPE_MOBILE
        OTHER -> CommonDataKinds.Email.TYPE_OTHER
        else -> CommonDataKinds.Email.TYPE_CUSTOM
    }

    private fun getAddressTypeId(type: String) = when (type.uppercase(Locale.getDefault())) {
        HOME -> StructuredPostal.TYPE_HOME
        WORK -> StructuredPostal.TYPE_WORK
        OTHER -> StructuredPostal.TYPE_OTHER
        else -> StructuredPostal.TYPE_CUSTOM
    }

    private fun savePhoto(byteArray: ByteArray?): String {
        if (byteArray == null) {
            return ""
        }

        val file = activity.getCachePhoto()
        val bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
        var fileOutputStream: FileOutputStream? = null
        try {
            fileOutputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fileOutputStream)
        } finally {
            fileOutputStream?.close()
        }

        return activity.getCachePhotoUri(file).toString()
    }

    private fun getPreferredValue(type: String?): Int {
        if (type != null) {
            if (type.startsWith("$PREF=".lowercase())) {
                return type.split("=")[1].toInt()
            }
        }

        return -1
    }
}
