package org.fossify.contacts.dialogs

import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.contacts.R
import org.fossify.contacts.helpers.SIM1_BADGE_COLOR
import org.fossify.contacts.helpers.SIM2_BADGE_COLOR

private const val PADDING_DP = 20
private const val TITLE_GAP_DP = 16
private const val ROW_PAD_DP = 8
private const val GAP_DP = 14
private const val TITLE_SP = 21f
private const val OPTION_SP = 26f

// "Set default SIM for contact" picker. Each SIM option shows its colored SIM badge (red 1 / blue 2)
// next to the label; the yellow accent frame comes from the commons dialog border (seeded in App).
// Calls back with the chosen slot: 1 = SIM 1, 2 = SIM 2, 0 = none.
class SetDefaultSimDialog(
    val activity: BaseSimpleActivity,
    val currentSlot: Int,
    val callback: (slot: Int) -> Unit
) {
    private val density = activity.resources.displayMetrics.density
    private val radios = mutableListOf<Pair<Int, RadioButton>>()
    private var dialog: AlertDialog? = null

    init {
        val pad = (PADDING_DP * density).toInt()
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(TextView(activity).apply {
                text = activity.getString(R.string.set_default_sim)
                setTextColor(activity.getProperTextColor())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, TITLE_SP)
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, 0, 0, (TITLE_GAP_DP * density).toInt())
            })
            addView(buildRow(1, activity.getString(R.string.sim_slot_1)))
            addView(buildRow(2, activity.getString(R.string.sim_slot_2)))
            addView(buildRow(0, activity.getString(org.fossify.commons.R.string.none)))
        }

        activity.getAlertDialogBuilder().apply {
            activity.setupDialogStuff(container, this) { alertDialog ->
                dialog = alertDialog
            }
        }
    }

    private fun buildRow(slot: Int, label: String): View {
        val rowPad = (ROW_PAD_DP * density).toInt()
        val gap = (GAP_DP * density).toInt()
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, rowPad, 0, rowPad)
            isClickable = true
            setOnClickListener { select(slot) }
        }

        val radioLayout = org.fossify.commons.R.layout.radio_button
        val radio = (activity.layoutInflater.inflate(radioLayout, row, false) as RadioButton).apply {
            text = ""
            isChecked = slot == currentSlot
            isClickable = false
            isFocusable = false
        }
        radios.add(slot to radio)
        // radio_button.xml is width=match_parent; force wrap so the badge + label have room beside it.
        row.addView(radio, marginStart(0))

        if (slot == 1 || slot == 2) {
            val badge = activity.layoutInflater.inflate(R.layout.sim_badge, row, false)
            badge.findViewById<ImageView>(R.id.sim_badge_icon)
                .applyColorFilter(if (slot == 1) SIM1_BADGE_COLOR else SIM2_BADGE_COLOR)
            badge.findViewById<TextView>(R.id.sim_badge_number).text = slot.toString()
            row.addView(badge, marginStart(gap))
        }

        row.addView(
            TextView(activity).apply {
                text = label
                setTextColor(activity.getProperTextColor())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, OPTION_SP)
            },
            marginStart(gap)
        )

        row.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        return row
    }

    private fun marginStart(margin: Int): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = margin }
    }

    private fun select(slot: Int) {
        radios.forEach { (s, radio) -> radio.isChecked = s == slot }
        callback(slot)
        dialog?.dismiss()
    }
}
