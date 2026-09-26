package org.fossify.contacts.dialogs

import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getPhoneNumberTypeText
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.contacts.Contact
import org.fossify.contacts.R

private const val PADDING_DP = 20
private const val TITLE_GAP_DP = 12
private const val ROW_PAD_DP = 8
private const val GAP_DP = 14
private const val NAME_SP = 21f
private const val SUBTITLE_SP = 15f
private const val NUMBER_SP = 24f
private const val TYPE_SP = 15f
private const val SUBTITLE_ALPHA = 0.7f

/**
 * "Which number do we call?" — asked when a contact with several numbers becomes a favorite, and by
 * the sweep over favorites that never got asked. The answer is stored as the platform's own default
 * number (IS_SUPER_PRIMARY), so it is the number every app calls, not only ours.
 *
 * The contact's name is the title: the sweep walks several contacts in a row, and a bare "Number to
 * call" would not say whose. Dismissing the dialog is a skip — [callback] is then never invoked and
 * [onSkip] runs instead, so a walk carries on to the next contact.
 */
class ChooseCallNumberDialog(
    val activity: BaseSimpleActivity,
    val contact: Contact,
    val onSkip: () -> Unit = {},
    val callback: (number: PhoneNumber) -> Unit,
) {
    private val density = activity.resources.displayMetrics.density
    private var dialog: AlertDialog? = null
    private var chosen = false

    init {
        val pad = (PADDING_DP * density).toInt()
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(TextView(activity).apply {
                text = contact.getNameToDisplay()
                setTextColor(activity.getProperTextColor())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, NAME_SP)
                setTypeface(typeface, Typeface.BOLD)
            })
            addView(TextView(activity).apply {
                text = activity.getString(R.string.choose_number_to_call)
                setTextColor(activity.getProperTextColor())
                alpha = SUBTITLE_ALPHA
                setTextSize(TypedValue.COMPLEX_UNIT_SP, SUBTITLE_SP)
                setPadding(0, 0, 0, (TITLE_GAP_DP * density).toInt())
            })
            contact.phoneNumbers.forEach { addView(buildRow(it)) }
        }

        activity.getAlertDialogBuilder()
            .setNegativeButton(org.fossify.commons.R.string.skip) { _, _ -> dialog?.dismiss() }
            .setOnDismissListener { if (!chosen) onSkip() }
            .apply {
                activity.setupDialogStuff(container, this) { alertDialog ->
                    dialog = alertDialog
                }
            }
    }

    private fun buildRow(number: PhoneNumber): View {
        val rowPad = (ROW_PAD_DP * density).toInt()
        val gap = (GAP_DP * density).toInt()
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, rowPad, 0, rowPad)
            isClickable = true
            setOnClickListener { select(number) }
        }

        val radioLayout = org.fossify.commons.R.layout.radio_button
        val radio = (activity.layoutInflater.inflate(radioLayout, row, false) as RadioButton).apply {
            text = ""
            isChecked = false
            isClickable = false
            isFocusable = false
        }
        // radio_button.xml is width=match_parent; force wrap so the number has room beside it.
        row.addView(radio, wrapWithMargin(0))

        val labels = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(activity).apply {
                text = number.value
                setTextColor(activity.getProperTextColor())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, NUMBER_SP)
            })
            addView(TextView(activity).apply {
                text = activity.getPhoneNumberTypeText(number.type, number.label)
                setTextColor(activity.getProperTextColor())
                alpha = SUBTITLE_ALPHA
                setTextSize(TypedValue.COMPLEX_UNIT_SP, TYPE_SP)
            })
        }
        row.addView(labels, wrapWithMargin(gap))

        row.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        return row
    }

    private fun wrapWithMargin(margin: Int): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = margin }
    }

    private fun select(number: PhoneNumber) {
        chosen = true
        dialog?.dismiss()
        callback(number)
    }
}
