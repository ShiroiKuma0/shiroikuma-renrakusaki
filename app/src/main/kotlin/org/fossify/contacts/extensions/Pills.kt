package org.fossify.contacts.extensions

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.chip.ChipGroup
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.pillDrawable

/**
 * Actions as pills — outlined rounded buttons in the accent colour, so what DOES something stands out from
 * the settings and text around it. Never filled. [main] marks the main action of a block by weight: a
 * thick frame and bold text, where the others have a thin frame and normal text (commons' pillDrawable).
 * Commons draws dialog buttons the same way (BaseConfig.pillDialogButtons), so a dialog's Yes and this
 * page's main action match.
 */
fun Context.pill(text: String, main: Boolean = false, onClick: () -> Unit): TextView {
    val density = resources.displayMetrics.density
    val primary = getProperPrimaryColor()
    return TextView(this).apply {
        this.text = text
        setTextColor(primary)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, PILL_TEXT_SP)
        gravity = Gravity.CENTER
        val h = (PILL_PAD_H_DP * density).toInt()
        val v = (PILL_PAD_V_DP * density).toInt()
        setPadding(h, v, h, v)
        if (main) setTypeface(typeface, Typeface.BOLD)
        this.background = pillDrawable(primary, getProperBackgroundColor(), main)
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }
}

/** Pills side by side, wrapping onto the next line when the screen is narrow (the folded phone). */
fun Context.pillRow(vararg pills: View, startPaddingPx: Int = 0): ChipGroup {
    val density = resources.displayMetrics.density
    return ChipGroup(this).apply {
        chipSpacingHorizontal = (PILL_GAP_DP * density).toInt()
        chipSpacingVertical = (PILL_GAP_DP * density).toInt()
        val v = (PILL_ROW_PAD_V_DP * density).toInt()
        val end = (PILL_ROW_PAD_END_DP * density).toInt()
        setPaddingRelative(startPaddingPx, v, end, v)
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        pills.forEach { addView(it) }
    }
}

private const val PILL_TEXT_SP = 16f
private const val PILL_PAD_H_DP = 18f
private const val PILL_PAD_V_DP = 9f
private const val PILL_GAP_DP = 10f
private const val PILL_ROW_PAD_V_DP = 8f
private const val PILL_ROW_PAD_END_DP = 16f
