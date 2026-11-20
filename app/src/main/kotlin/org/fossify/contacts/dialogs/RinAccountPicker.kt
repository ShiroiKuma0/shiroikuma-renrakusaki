package org.fossify.contacts.dialogs

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.contacts.R
import org.fossify.contacts.extensions.ThemeSlot
import org.fossify.contacts.extensions.themeColor
import org.fossify.contacts.helpers.RIN_ACCOUNT_SELECTED
import org.fossify.contacts.helpers.Rindenwa
import org.fossify.contacts.helpers.rinBadge
import org.fossify.contacts.helpers.rinBadgeColors

private const val PADDING_DP = 20
private const val ROW_PAD_DP = 8
private const val GAP_DP = 12
private const val OPTION_SP = 18f
private const val BADGE_SP = 16f
private const val BADGE_HEIGHT_DP = 30
private const val BADGE_PAD_DP = 7
private const val BADGE_RADIUS_DP = 7f
private const val MARK_COLUMN_DP = 48
private const val ICON_DP = 24

/**
 * Picks a 臨電話 account by its 臨 number. The first line is [noAccountLabel] — "as selected in 臨電話" for
 * the default, "the default" for a contact's own account — and gives [RIN_ACCOUNT_SELECTED]. The list is
 * asked from Rindenwa afresh; when it does not answer, the last list it gave is offered instead. Only
 * accounts that carry a 臨 number (set in Rindenwa's account settings) can be picked. Each line shows the
 * account's 臨N badge in the colours 白い熊 gave it in Rindenwa.
 */
fun BaseSimpleActivity.pickRinAccount(current: Int, noAccountLabel: String, callback: (account: Int) -> Unit) {
    val appContext = applicationContext
    ensureBackgroundThread {
        val fresh = Rindenwa.refreshAccounts(appContext)
        val accounts = (fresh ?: Rindenwa.cachedAccounts(appContext)).filter { it.number >= 0 }.sortedBy { it.number }
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            if (fresh == null) toast(R.string.rin_error_unavailable)
            val lines = ArrayList<Triple<Int, String?, String>>() // account, badge (null = none), label
            lines.add(Triple(RIN_ACCOUNT_SELECTED, null, noAccountLabel))
            accounts.forEach { lines.add(Triple(it.number, it.badge, it.name.ifEmpty { it.identity.removePrefix("sip:") })) }
            // an account that is set but no longer listed stays visible, so it can be seen and changed
            if (current >= 0 && accounts.none { it.number == current }) lines.add(Triple(current, rinBadge(current), ""))
            RinAccountDialog(this, lines, if (current >= 0) current else RIN_ACCOUNT_SELECTED, callback)
        }
    }
}

private class RinAccountDialog(
    val activity: BaseSimpleActivity,
    lines: List<Triple<Int, String?, String>>,
    val current: Int,
    val callback: (account: Int) -> Unit,
) {
    private val density = activity.resources.displayMetrics.density
    private var dialog: AlertDialog? = null

    init {
        val pad = (PADDING_DP * density).toInt()
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            lines.forEach { (account, badge, label) -> addView(buildRow(account, badge, label)) }
        }
        val scroll = ScrollView(activity).apply { addView(container) }
        activity.getAlertDialogBuilder().apply {
            activity.setupDialogStuff(scroll, this) { alertDialog -> dialog = alertDialog }
        }
    }

    private fun buildRow(account: Int, badge: String?, label: String): View {
        val rowPad = (ROW_PAD_DP * density).toInt()
        val gap = (GAP_DP * density).toInt()
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, rowPad, 0, rowPad)
            isClickable = true
            setOnClickListener {
                callback(account)
                dialog?.dismiss()
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val radio = (activity.layoutInflater.inflate(org.fossify.commons.R.layout.radio_button, row, false) as RadioButton).apply {
            text = ""
            isChecked = account == current
            isClickable = false
            isFocusable = false
        }
        // radio_button.xml is width=match_parent; force wrap so the badge and label have room beside it
        row.addView(radio, wrap(0))

        // One column for every mark — the account's 臨N badge, or a cog on the line that names no account —
        // so the labels share one left edge.
        val mark: View = if (badge != null) {
            val (ground, text) = activity.rinBadgeColors(account)
            TextView(activity).apply {
                this.text = badge
                setTextColor(text)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, BADGE_SP)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                includeFontPadding = false
                val h = (BADGE_PAD_DP * density).toInt()
                setPadding(h, 0, h, 0)
                minHeight = (BADGE_HEIGHT_DP * density).toInt()
                background = GradientDrawable().apply {
                    cornerRadius = BADGE_RADIUS_DP * density
                    setColor(ground)
                }
            }
        } else {
            ImageView(activity).apply {
                setImageResource(org.fossify.commons.R.drawable.ic_settings_cog_vector)
                applyColorFilter(activity.themeColor(ThemeSlot.MENU_ICON_VIEW))
                layoutParams = ViewGroup.LayoutParams((ICON_DP * density).toInt(), (ICON_DP * density).toInt())
            }
        }
        val markColumn = FrameLayout(activity)
        markColumn.addView(
            mark, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        )
        row.addView(
            markColumn,
            LinearLayout.LayoutParams((MARK_COLUMN_DP * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = gap }
        )

        row.addView(
            TextView(activity).apply {
                text = label
                setTextColor(activity.getProperTextColor())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, OPTION_SP)
            },
            wrap(gap)
        )
        return row
    }

    private fun wrap(margin: Int) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { marginStart = margin }
}
