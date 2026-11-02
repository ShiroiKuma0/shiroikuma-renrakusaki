package org.fossify.contacts.helpers

import android.provider.ContactsContract.CommonDataKinds
import android.util.Base64
import com.google.gson.Gson
import ezvcard.VCard
import ezvcard.parameter.RelatedType
import ezvcard.property.Related
import ezvcard.property.Role
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.models.contacts.ContactExtraData
import org.fossify.commons.models.contacts.Relation
import org.fossify.commons.models.contacts.SipAddress

/**
 * Contact.extra (commons sk11) in a vCard — so a backup carries every field and a restore brings it all
 * back.
 *
 * Two layers:
 * - **X-SHIROIKUMA-EXTRA**: the whole ContactExtraData as base64 JSON. Authoritative on import: our own
 *   backups round-trip exactly, verbatim provider rows included.
 * - **Standard properties** for every other reader: RELATED (relationships), X-SIP (internet-call
 *   addresses, Android's own vCard name for them), the department as ORG's second component, ROLE (job
 *   description), X-PHONETIC-ORG, X-OFFICE-LOCATION, X-SYMBOL, X-SEND-TO-VOICEMAIL. A card from another
 *   app is read from these when the authoritative property is absent.
 */
object VcfExtra {
    private const val X_EXTRA = "X-SHIROIKUMA-EXTRA"
    private const val X_SIP = "X-SIP"
    private const val X_PHONETIC_ORG = "X-PHONETIC-ORG"
    private const val X_OFFICE_LOCATION = "X-OFFICE-LOCATION"
    private const val X_SYMBOL = "X-SYMBOL"
    private const val X_SEND_TO_VOICEMAIL = "X-SEND-TO-VOICEMAIL"
    private const val PARAM_TYPE = "TYPE"
    private const val PARAM_LABEL = "X-LABEL"
    private const val PARAM_ANDROID_TYPE = "X-ANDROID-TYPE"

    fun write(card: VCard, contact: Contact) {
        val extra = contact.extra
        if (extra.copy(loaded = false) == ContactExtraData()) return

        card.addExtendedProperty(X_EXTRA, Base64.encodeToString(Gson().toJson(extra).toByteArray(), Base64.NO_WRAP))

        extra.relations.forEach { relation ->
            card.addRelated(Related().apply {
                text = relation.name
                relatedTypeOf(relation.type)?.let { types.add(it) }
                setParameter(PARAM_ANDROID_TYPE, relation.type.toString())
                if (relation.label.isNotEmpty()) setParameter(PARAM_LABEL, relation.label)
            })
        }
        extra.sipAddresses.forEach { sip ->
            card.addExtendedProperty(X_SIP, sip.value).apply {
                setParameter(PARAM_ANDROID_TYPE, sip.type.toString())
                if (sip.label.isNotEmpty()) setParameter(PARAM_LABEL, sip.label)
            }
        }
        if (extra.department.isNotEmpty()) {
            card.organization?.values?.let { values -> if (values.size == 1) values.add(extra.department) }
        }
        if (extra.jobDescription.isNotEmpty()) card.addRole(Role(extra.jobDescription))
        if (extra.companyPhonetic.isNotEmpty()) card.addExtendedProperty(X_PHONETIC_ORG, extra.companyPhonetic)
        if (extra.officeLocation.isNotEmpty()) card.addExtendedProperty(X_OFFICE_LOCATION, extra.officeLocation)
        if (extra.symbol.isNotEmpty()) card.addExtendedProperty(X_SYMBOL, extra.symbol)
        if (extra.sendToVoicemail) card.addExtendedProperty(X_SEND_TO_VOICEMAIL, "1")
    }

    /** The extra data a card carries — ours exactly when it has X-SHIROIKUMA-EXTRA, else what the standard properties give. */
    fun read(card: VCard): ContactExtraData {
        card.getExtendedProperty(X_EXTRA)?.value?.let { encoded ->
            try {
                return Gson().fromJson(String(Base64.decode(encoded, Base64.NO_WRAP)), ContactExtraData::class.java) ?: ContactExtraData()
            } catch (ignored: Exception) {
            }
        }

        val extra = ContactExtraData()
        card.relations.forEach { related ->
            val name = related.text ?: related.uri ?: return@forEach
            val type = related.getParameter(PARAM_ANDROID_TYPE)?.toIntOrNull() ?: androidRelationType(related.types.firstOrNull())
            extra.relations.add(Relation(name, type, related.getParameter(PARAM_LABEL).orEmpty()))
        }
        card.getExtendedProperties(X_SIP).forEach { property ->
            val value = property.value?.trim().orEmpty().removePrefix("sip:")
            if (value.isNotEmpty()) {
                val type = property.getParameter(PARAM_ANDROID_TYPE)?.toIntOrNull() ?: CommonDataKinds.SipAddress.TYPE_OTHER
                extra.sipAddresses.add(SipAddress(value, type, property.getParameter(PARAM_LABEL).orEmpty()))
            }
        }
        extra.department = card.organization?.values?.getOrNull(1).orEmpty()
        extra.jobDescription = card.roles.firstOrNull()?.value.orEmpty()
        extra.companyPhonetic = card.getExtendedProperty(X_PHONETIC_ORG)?.value.orEmpty()
        extra.officeLocation = card.getExtendedProperty(X_OFFICE_LOCATION)?.value.orEmpty()
        extra.symbol = card.getExtendedProperty(X_SYMBOL)?.value.orEmpty()
        extra.sendToVoicemail = card.getExtendedProperty(X_SEND_TO_VOICEMAIL)?.value == "1"
        return extra
    }

    private fun relatedTypeOf(type: Int): RelatedType? = when (type) {
        CommonDataKinds.Relation.TYPE_SPOUSE, CommonDataKinds.Relation.TYPE_PARTNER,
        CommonDataKinds.Relation.TYPE_DOMESTIC_PARTNER -> RelatedType.SPOUSE
        CommonDataKinds.Relation.TYPE_CHILD -> RelatedType.CHILD
        CommonDataKinds.Relation.TYPE_PARENT, CommonDataKinds.Relation.TYPE_MOTHER, CommonDataKinds.Relation.TYPE_FATHER -> RelatedType.PARENT
        CommonDataKinds.Relation.TYPE_BROTHER, CommonDataKinds.Relation.TYPE_SISTER -> RelatedType.SIBLING
        CommonDataKinds.Relation.TYPE_RELATIVE -> RelatedType.KIN
        CommonDataKinds.Relation.TYPE_FRIEND -> RelatedType.FRIEND
        CommonDataKinds.Relation.TYPE_MANAGER, CommonDataKinds.Relation.TYPE_ASSISTANT -> RelatedType.CO_WORKER
        else -> null
    }

    private fun androidRelationType(type: RelatedType?): Int = when (type) {
        RelatedType.SPOUSE -> CommonDataKinds.Relation.TYPE_SPOUSE
        RelatedType.CHILD -> CommonDataKinds.Relation.TYPE_CHILD
        RelatedType.PARENT -> CommonDataKinds.Relation.TYPE_PARENT
        RelatedType.SIBLING -> CommonDataKinds.Relation.TYPE_RELATIVE
        RelatedType.KIN -> CommonDataKinds.Relation.TYPE_RELATIVE
        RelatedType.FRIEND -> CommonDataKinds.Relation.TYPE_FRIEND
        RelatedType.CO_WORKER -> CommonDataKinds.Relation.TYPE_MANAGER
        else -> CommonDataKinds.Relation.TYPE_RELATIVE
    }
}
