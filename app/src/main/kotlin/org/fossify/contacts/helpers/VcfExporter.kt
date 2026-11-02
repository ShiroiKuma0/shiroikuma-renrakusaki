package org.fossify.contacts.helpers

import android.content.Context
import android.graphics.Bitmap
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Im
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import androidx.core.net.toUri
import ezvcard.Ezvcard
import ezvcard.VCard
import ezvcard.VCardVersion
import ezvcard.parameter.ImageType
import ezvcard.property.*
import ezvcard.util.PartialDate
import org.fossify.commons.extensions.getDateTimeFromDateString
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.models.contacts.Contact
import org.fossify.contacts.helpers.VcfExporter.ExportResult.EXPORT_FAIL
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.time.LocalDate

private const val PHOTO_JPEG_QUALITY = 95

/**
 * [onProgress] is called with (contacts converted so far, total) after every contact, so a headless
 * caller can report real counts — see SettingsExport.exportBlocking. Unthrottled: the caller decides how
 * often to surface it. It is a constructor parameter rather than one of exportContacts', to leave that
 * heavily-called signature untouched.
 */
class VcfExporter(private val onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }) {
    enum class ExportResult {
        EXPORT_FAIL, EXPORT_OK, EXPORT_PARTIAL
    }

    private var contactsExported = 0
    private var contactsFailed = 0

    fun exportContacts(
        context: Context,
        outputStream: OutputStream?,
        contacts: ArrayList<Contact>,
        showExportingToast: Boolean,
        version: VCardVersion = VCardVersion.V4_0,
        callback: (result: ExportResult) -> Unit,
    ) {
        try {
            if (outputStream == null) {
                callback(EXPORT_FAIL)
                return
            }

            if (showExportingToast) {
                context.toast(org.fossify.commons.R.string.exporting)
            }

            // フリガナ lives in provider columns the commons Contact model doesn't carry, so it takes
            // a query of its own — one for the whole run, keyed by raw contact id.
            val phoneticNames = context.loadPhoneticNames()
            // Every field of every system contact (commons sk11): relations, SIP addresses, organization
            // details… — private contacts already carry theirs. One pass over the provider.
            ContactsHelper(context).fillExtras(contacts)
            // Private contacts that have a "visible to other apps" copy: flagged so an import restores the copy.
            val mirroredIds = SystemMirror.mirrors(context).keys

            val cards = ArrayList<VCard>()
            for (contact in contacts) {
                val card = VCard()

                val formattedName = arrayOf(
                    contact.prefix,
                    contact.firstName,
                    contact.middleName,
                    contact.surname,
                    contact.suffix
                )
                    .filter { it.isNotEmpty() }
                    .joinToString(separator = " ")
                card.formattedName = FormattedName(formattedName)

                // The reading: Contact.extra (commons sk11) — filled for private contacts by the DB and for
                // system ones by fillExtras below; the provider query covers a list that skipped that.
                val extraPhonetic = PhoneticName(contact.extra.phoneticFamily, contact.extra.phoneticMiddle, contact.extra.phoneticGiven)
                val phonetic = if (!extraPhonetic.isEmpty) {
                    extraPhonetic
                } else if (contact.isPrivate()) {
                    null
                } else {
                    phoneticNames[contact.id]
                }

                StructuredName().apply {
                    prefixes.add(contact.prefix)
                    given = contact.firstName
                    additionalNames.add(contact.middleName)
                    family = contact.surname
                    suffixes.add(contact.suffix)
                    if (phonetic != null) {
                        // The two-argument form emits SORT-AS="family,given"; with only one part
                        // filled in it would emit a stray empty component, so pass the joined string.
                        if (phonetic.family.isNotEmpty() && phonetic.given.isNotEmpty()) {
                            setSortAs(phonetic.family, phonetic.given)
                        } else {
                            setSortAs(phonetic.joined())
                        }
                    }
                    card.structuredName = this
                }

                if (phonetic != null) {
                    if (phonetic.family.isNotEmpty()) {
                        card.addExtendedProperty(X_PHONETIC_LAST_NAME, phonetic.family)
                    }
                    if (phonetic.middle.isNotEmpty()) {
                        card.addExtendedProperty(X_PHONETIC_MIDDLE_NAME, phonetic.middle)
                    }
                    if (phonetic.given.isNotEmpty()) {
                        card.addExtendedProperty(X_PHONETIC_FIRST_NAME, phonetic.given)
                    }
                }

                if (contact.nickname.isNotEmpty()) {
                    card.setNickname(contact.nickname)
                }

                contact.phoneNumbers.forEach {
                    val phoneNumber = Telephone(it.value)
                    phoneNumber.parameters.addType(getPhoneNumberTypeLabel(it.type, it.label))
                    if (it.isPrimary) {
                        phoneNumber.parameters.addType(getPreferredType(1))
                    }
                    card.addTelephoneNumber(phoneNumber)
                }

                contact.emails.forEach {
                    val email = Email(it.value)
                    email.parameters.addType(getEmailTypeLabel(it.type, it.label))
                    card.addEmail(email)
                }

                contact.events.forEach { event ->
                    if (event.type == Event.TYPE_ANNIVERSARY || event.type == Event.TYPE_BIRTHDAY) {
                        val dateTime = event.value.getDateTimeFromDateString(false)
                        if (event.value.startsWith("--")) {
                            val partial = PartialDate.builder()
                                .month(dateTime.monthOfYear)
                                .date(dateTime.dayOfMonth)
                                .build()

                            if (event.type == Event.TYPE_BIRTHDAY) {
                                card.birthdays.add(Birthday(partial))
                            } else {
                                card.anniversaries.add(Anniversary(partial))
                            }
                        } else {
                            val date = LocalDate
                                .of(dateTime.year, dateTime.monthOfYear, dateTime.dayOfMonth)

                            if (event.type == Event.TYPE_BIRTHDAY) {
                                card.birthdays.add(Birthday(date))
                            } else {
                                card.anniversaries.add(Anniversary(date))
                            }
                        }
                    } else {
                        // vCard names no date but BDAY and ANNIVERSARY, so Android's "other" and
                        // custom-label events had nowhere to go and were dropped. The provider's own
                        // string is written verbatim: a full date and a --MM-DD one both come back
                        // exactly as they went out.
                        if (event.value.isNotEmpty()) {
                            card.addExtendedProperty(X_EVENT, event.value).apply {
                                setParameter(X_EVENT_TYPE_PARAM, getEventTypeLabel(event.type))
                                if (event.label.isNotEmpty()) setParameter(X_EVENT_LABEL_PARAM, event.label)
                            }
                        }
                    }
                }

                contact.addresses.forEach {
                    val address = Address()
                    if (
                        listOf(
                            it.country,
                            it.region,
                            it.city,
                            it.postcode,
                            it.pobox,
                            it.street,
                            it.neighborhood
                        )
                            .map { it.isEmpty() }
                            .fold(false) { a, b -> a || b }
                    ) {
                        address.country = it.country
                        address.region = it.region
                        address.locality = it.city
                        address.postalCode = it.postcode
                        address.poBox = it.pobox
                        address.streetAddress = it.street
                        address.extendedAddress = it.neighborhood
                    } else {
                        address.streetAddress = it.value
                    }
                    address.parameters.addType(getAddressTypeLabel(it.type, it.label))
                    card.addAddress(address)
                }

                contact.IMs.forEach {
                    val impp = when (it.type) {
                        Im.PROTOCOL_AIM -> Impp.aim(it.value)
                        Im.PROTOCOL_YAHOO -> Impp.yahoo(it.value)
                        Im.PROTOCOL_MSN -> Impp.msn(it.value)
                        Im.PROTOCOL_ICQ -> Impp.icq(it.value)
                        Im.PROTOCOL_SKYPE -> Impp.skype(it.value)
                        Im.PROTOCOL_GOOGLE_TALK -> Impp(HANGOUTS, it.value)
                        Im.PROTOCOL_QQ -> Impp(QQ, it.value)
                        Im.PROTOCOL_JABBER -> Impp(JABBER, it.value)
                        else -> Impp(it.label, it.value)
                    }

                    card.addImpp(impp)
                }

                if (contact.notes.isNotEmpty()) {
                    card.addNote(contact.notes)
                }

                if (contact.organization.isNotEmpty()) {
                    val organization = Organization()
                    organization.values.add(contact.organization.company)
                    card.organization = organization
                    card.titles.add(Title(contact.organization.jobPosition))
                }

                contact.websites.forEach {
                    card.addUrl(it)
                }

                // Fork: a private contact keeps its picture as bytes in our own database; photoUri is only
                // where it was loaded from (often a cache file that has since been cleared). Upstream read
                // photoUri alone, so such a contact was exported silently without its photo. Fall back to
                // the stored bitmap whenever the URI yields nothing.
                val photoBytes = readPhotoUri(context, contact.photoUri) ?: contact.photo?.let { bitmap ->
                    ByteArrayOutputStream().use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, PHOTO_JPEG_QUALITY, out)
                        out.toByteArray()
                    }
                }
                if (photoBytes != null) {
                    card.addPhoto(Photo(photoBytes, ImageType.JPEG))
                }

                if (contact.isPrivate() && contact.id in mirroredIds) {
                    card.addExtendedProperty(X_SHIROIKUMA_VISIBLE, "1")
                }

                VcfExtra.write(card, contact)

                if (contact.groups.isNotEmpty()) {
                    val groupList = Categories()
                    contact.groups.forEach {
                        groupList.values.add(it.title)
                    }

                    card.categories = groupList
                }

                if (contact.starred == 1) {
                    card.addExtendedProperty(X_FAVORITE, "1")
                }

                // The URI is device-local — it is the importing phone that decides whether it still
                // names anything — so it is written as it stands and judged on the way back in.
                val ringtone = contact.ringtone?.trim().orEmpty()
                if (ringtone.isNotEmpty()) {
                    card.addExtendedProperty(X_CUSTOM_RINGTONE, ringtone)
                }

                cards.add(card)
                contactsExported++
                onProgress(contactsExported, contacts.size)
            }

            Ezvcard.write(cards).version(version).go(outputStream)
        } catch (e: Exception) {
            context.showErrorToast(e)
        }

        callback(
            when {
                contactsExported == 0 -> EXPORT_FAIL
                contactsFailed > 0 -> ExportResult.EXPORT_PARTIAL
                else -> ExportResult.EXPORT_OK
            }
        )
    }

    private fun getPhoneNumberTypeLabel(type: Int, label: String) = when (type) {
        Phone.TYPE_MOBILE -> CELL
        Phone.TYPE_HOME -> HOME
        Phone.TYPE_WORK -> WORK
        Phone.TYPE_MAIN -> MAIN
        Phone.TYPE_FAX_WORK -> WORK_FAX
        Phone.TYPE_FAX_HOME -> HOME_FAX
        Phone.TYPE_PAGER -> PAGER
        Phone.TYPE_OTHER -> OTHER
        else -> label
    }

    private fun getEmailTypeLabel(type: Int, label: String) = when (type) {
        Email.TYPE_HOME -> HOME
        Email.TYPE_WORK -> WORK
        Email.TYPE_MOBILE -> MOBILE
        Email.TYPE_OTHER -> OTHER
        else -> label
    }

    private fun getEventTypeLabel(type: Int) = when (type) {
        Event.TYPE_OTHER -> OTHER
        Event.TYPE_CUSTOM -> CUSTOM
        // Not a type this app can make, but a foreign importer's might: keep the raw id rather than
        // flatten it to "other", so a re-export of an imported card is still the card that arrived.
        else -> type.toString()
    }

    private fun getAddressTypeLabel(type: Int, label: String) = when (type) {
        StructuredPostal.TYPE_HOME -> HOME
        StructuredPostal.TYPE_WORK -> WORK
        StructuredPostal.TYPE_OTHER -> OTHER
        else -> label
    }

    private fun getPreferredType(value: Int) = "$PREF=$value"

    private fun readPhotoUri(context: Context, photoUri: String): ByteArray? {
        if (photoUri.isEmpty()) return null
        return try {
            context.contentResolver.openInputStream(photoUri.toUri())?.use { it.readBytes() }?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }
}

