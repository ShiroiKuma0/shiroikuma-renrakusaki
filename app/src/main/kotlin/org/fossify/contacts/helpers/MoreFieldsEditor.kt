package org.fossify.contacts.helpers

import android.provider.ContactsContract.CommonDataKinds
import android.text.InputType
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.value
import org.fossify.commons.models.RadioItem
import org.fossify.commons.models.contacts.ContactExtraData
import org.fossify.commons.models.contacts.Relation
import org.fossify.commons.models.contacts.SipAddress
import org.fossify.commons.views.MyAppCompatCheckbox
import org.fossify.commons.views.MyEditText
import org.fossify.commons.views.MyTextView
import org.fossify.contacts.R
import org.fossify.contacts.databinding.ItemEditTypedValueBinding
import org.fossify.contacts.dialogs.CustomLabelDialog

/**
 * The edit screen's fields beyond Fossify's own (Contact.extra, commons sk11): the organization's
 * department, job description, office location, reading and ticker symbol; relationships; internet-call
 * (SIP) addresses; and send to voicemail. Built into `contact_more_holder`; [bind] fills it from a
 * contact, [collectInto] writes what is on screen back. The reading of the name lives with the name
 * fields themselves (EditContactActivity), and the verbatim rows in extra.rows are carried untouched.
 */
class MoreFieldsEditor(private val activity: BaseSimpleActivity, private val holder: LinearLayout) {
    private lateinit var department: MyEditText
    private lateinit var jobDescription: MyEditText
    private lateinit var officeLocation: MyEditText
    private lateinit var companyPhonetic: MyEditText
    private lateinit var symbol: MyEditText
    private lateinit var relationsHolder: LinearLayout
    private lateinit var sipHolder: LinearLayout
    private lateinit var sendToVoicemail: MyAppCompatCheckbox

    private class TypeTag(var type: Int, var label: String)

    fun bind(extra: ContactExtraData) {
        holder.removeAllViews()

        header(R.string.more_organization)
        department = field(R.string.more_department, extra.department)
        jobDescription = field(R.string.more_job_description, extra.jobDescription)
        officeLocation = field(R.string.more_office_location, extra.officeLocation)
        companyPhonetic = field(R.string.more_company_phonetic, extra.companyPhonetic)
        symbol = field(R.string.more_symbol, extra.symbol)

        header(R.string.more_relations)
        relationsHolder = column()
        extra.relations.forEach { addRelation(it) }
        if (extra.relations.isEmpty()) addRelation(null)
        addButton { addRelation(null, focus = true) }

        header(R.string.more_sip)
        sipHolder = column()
        extra.sipAddresses.forEach { addSip(it) }
        if (extra.sipAddresses.isEmpty()) addSip(null)
        addButton { addSip(null, focus = true) }

        sendToVoicemail = MyAppCompatCheckbox(activity).apply {
            text = activity.getString(R.string.more_send_to_voicemail)
            isChecked = extra.sendToVoicemail
            setTextSize(TypedValue.COMPLEX_UNIT_PX, activity.resources.getDimension(org.fossify.commons.R.dimen.bigger_text_size))
        }
        holder.addView(sendToVoicemail)

        activity.updateTextColors(holder)
        sendToVoicemail.setColors(activity.getProperTextColor(), activity.getProperPrimaryColor(), 0)
    }

    fun collectInto(extra: ContactExtraData) {
        extra.department = department.value.trim()
        extra.jobDescription = jobDescription.value.trim()
        extra.officeLocation = officeLocation.value.trim()
        extra.companyPhonetic = companyPhonetic.value.trim()
        extra.symbol = symbol.value.trim()
        extra.relations = rows(relationsHolder).map { (value, tag) -> Relation(value, tag.type, tag.label) }.toCollection(ArrayList())
        extra.sipAddresses = rows(sipHolder).map { (value, tag) -> SipAddress(value, tag.type, tag.label) }.toCollection(ArrayList())
        extra.sendToVoicemail = sendToVoicemail.isChecked
    }

    // ---- building blocks ----------------------------------------------------------------------------

    private fun header(titleRes: Int) {
        holder.addView(MyTextView(activity).apply {
            text = activity.getString(titleRes)
            setTextColor(activity.getProperPrimaryColor())
            val pad = activity.resources.getDimensionPixelSize(org.fossify.commons.R.dimen.medium_margin)
            setPadding(0, pad, 0, 0)
        })
    }

    private fun field(hintRes: Int, value: String) = MyEditText(activity).apply {
        hint = activity.getString(hintRes)
        setText(value)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        setTextSize(TypedValue.COMPLEX_UNIT_PX, activity.resources.getDimension(org.fossify.commons.R.dimen.bigger_text_size))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginEnd = activity.resources.getDimensionPixelSize(org.fossify.commons.R.dimen.activity_margin)
        }
        holder.addView(this)
    }

    private fun column() = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        holder.addView(this)
    }

    private fun addButton(onClick: () -> Unit) {
        holder.addView(ImageView(activity).apply {
            setImageResource(org.fossify.commons.R.drawable.ic_plus_vector)
            setBackgroundResource(org.fossify.commons.R.drawable.button_background)
            val h = activity.resources.getDimensionPixelSize(org.fossify.commons.R.dimen.activity_margin)
            val v = activity.resources.getDimensionPixelSize(org.fossify.commons.R.dimen.medium_margin)
            setPadding(h, v, h, v)
            // Same colouring as the screen's own "+" buttons: primary icon on a text-coloured frame.
            applyColorFilter(activity.getProperPrimaryColor())
            background?.applyColorFilter(activity.getProperTextColor())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                topMargin = activity.resources.getDimensionPixelSize(org.fossify.commons.R.dimen.small_margin)
            }
            setOnClickListener { onClick() }
        })
    }

    private fun addRelation(relation: Relation?, focus: Boolean = false) =
        addTyped(relationsHolder, R.string.more_relation_name, relation?.name.orEmpty(),
            TypeTag(relation?.type ?: CommonDataKinds.Relation.TYPE_SPOUSE, relation?.label.orEmpty()), ::relationLabel, ::pickRelationType, focus)

    private fun addSip(sip: SipAddress?, focus: Boolean = false) =
        addTyped(sipHolder, R.string.more_sip_address, sip?.value.orEmpty(),
            TypeTag(sip?.type ?: CommonDataKinds.SipAddress.TYPE_HOME, sip?.label.orEmpty()), ::sipLabel, ::pickSipType, focus)

    private fun addTyped(
        parent: LinearLayout, hintRes: Int, value: String, tag: TypeTag,
        labelOf: (TypeTag) -> String, pick: (TypeTag, () -> Unit) -> Unit, focus: Boolean,
    ) {
        val row = ItemEditTypedValueBinding.inflate(activity.layoutInflater, parent, false)
        row.typedValue.hint = activity.getString(hintRes)
        row.typedValue.setText(value)
        row.root.tag = tag
        row.typedValueType.text = labelOf(tag)
        row.typedValueType.setOnClickListener { pick(tag) { row.typedValueType.text = labelOf(tag) } }
        parent.addView(row.root)
        activity.updateTextColors(row.root)
        row.typedValueType.setTextColor(activity.getProperPrimaryColor())
        if (focus) row.typedValue.requestFocus()
    }

    private fun rows(parent: LinearLayout): List<Pair<String, TypeTag>> = (0 until parent.childCount).mapNotNull { i ->
        val row = ItemEditTypedValueBinding.bind(parent.getChildAt(i))
        val value = row.typedValue.value.trim()
        if (value.isEmpty()) null else value to (row.root.tag as TypeTag)
    }

    private fun relationLabel(tag: TypeTag) =
        CommonDataKinds.Relation.getTypeLabel(activity.resources, tag.type, tag.label).toString()

    private fun sipLabel(tag: TypeTag) =
        CommonDataKinds.SipAddress.getTypeLabel(activity.resources, tag.type, tag.label).toString()

    private fun pickRelationType(tag: TypeTag, done: () -> Unit) {
        val types = listOf(
            CommonDataKinds.Relation.TYPE_SPOUSE, CommonDataKinds.Relation.TYPE_PARTNER, CommonDataKinds.Relation.TYPE_DOMESTIC_PARTNER,
            CommonDataKinds.Relation.TYPE_CHILD, CommonDataKinds.Relation.TYPE_PARENT, CommonDataKinds.Relation.TYPE_MOTHER,
            CommonDataKinds.Relation.TYPE_FATHER, CommonDataKinds.Relation.TYPE_SISTER, CommonDataKinds.Relation.TYPE_BROTHER,
            CommonDataKinds.Relation.TYPE_RELATIVE, CommonDataKinds.Relation.TYPE_FRIEND, CommonDataKinds.Relation.TYPE_MANAGER,
            CommonDataKinds.Relation.TYPE_ASSISTANT, CommonDataKinds.Relation.TYPE_REFERRED_BY,
        )
        pickType(types, CommonDataKinds.Relation.TYPE_CUSTOM, tag, ::relationLabel, done)
    }

    private fun pickSipType(tag: TypeTag, done: () -> Unit) {
        val types = listOf(CommonDataKinds.SipAddress.TYPE_HOME, CommonDataKinds.SipAddress.TYPE_WORK, CommonDataKinds.SipAddress.TYPE_OTHER)
        pickType(types, CommonDataKinds.SipAddress.TYPE_CUSTOM, tag, ::sipLabel, done)
    }

    private fun pickType(types: List<Int>, customType: Int, tag: TypeTag, labelOf: (TypeTag) -> String, done: () -> Unit) {
        val items = ArrayList(types.map { RadioItem(it, labelOf(TypeTag(it, ""))) })
        items.add(RadioItem(customType, activity.getString(org.fossify.commons.R.string.custom)))
        RadioGroupDialog(activity, items, tag.type) { chosen ->
            if (chosen as Int == customType) {
                CustomLabelDialog(activity) { label ->
                    tag.type = customType
                    tag.label = label
                    done()
                }
            } else {
                tag.type = chosen
                tag.label = ""
                done()
            }
        }
    }
}
