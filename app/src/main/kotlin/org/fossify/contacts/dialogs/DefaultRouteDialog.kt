package org.fossify.contacts.dialogs

import org.fossify.commons.extensions.rippleDrawable
import org.fossify.contacts.extensions.styleSimBadge
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
import androidx.core.graphics.ColorUtils
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.contacts.R
import org.fossify.contacts.extensions.ThemeSlot
import org.fossify.contacts.extensions.config
import org.fossify.contacts.extensions.themeColor
import org.fossify.contacts.helpers.ROUTE_OFF
import org.fossify.contacts.helpers.ROUTE_RIN
import org.fossify.contacts.helpers.ROUTE_RIN_PREFIX
import org.fossify.contacts.helpers.ROUTE_SIM1
import org.fossify.contacts.helpers.ROUTE_SIM2
import org.fossify.contacts.helpers.Rindenwa
import org.fossify.contacts.helpers.rinBadge
import org.fossify.contacts.helpers.rinBadgeColors
import org.fossify.contacts.helpers.rinBadgeFor

private const val PADDING_DP = 20
private const val TITLE_GAP_DP = 12
private const val ROW_PAD_DP = 8
private const val GAP_DP = 12
private const val TITLE_SP = 21f
private const val OPTION_SP = 26f
private const val MARK_COLUMN_DP = 60
private const val RIN_BADGE_SP = 20f
private const val RIN_BADGE_HEIGHT_DP = 36
private const val RIN_BADGE_PAD_DP = 7
private const val RIN_BADGE_RADIUS_DP = 7f
private const val NONE_ICON_DP = 30
private const val DIVIDER_BLEND = 0.7f
private const val DIVIDER_GAP_DP = 6

/**
 * The selection menu's SIM icon: the route a contact is called by when nothing names one (a tap on its
 * Favorites tile, the call button, a tap on one of its numbers) — SIM 1, SIM 2, 白い熊 臨電話 through the
 * default account or through one 臨N, or none. [current] and the answer are call routes (helpers/Constants:
 * sim1, sim2, rin, rin:N, off). The 臨 lines appear when Rindenwa is installed; its account list is asked
 * afresh, and the last one it gave is offered when it does not answer.
 *
 * A picker in the family's look: one column of marks — each route's own badge, in its own colours — one
 * label edge, and a clear line between the SIMs, the 臨 accounts and “None”.
 */
fun BaseSimpleActivity.pickDefaultRoute(current: String, callback: (route: String) -> Unit) {
    val appContext = applicationContext
    ensureBackgroundThread {
        val hasRin = Rindenwa.isInstalled(appContext)
        if (hasRin) Rindenwa.refreshAccounts(appContext)
        val accounts = if (hasRin) Rindenwa.cachedAccounts(appContext).filter { it.number >= 0 }.sortedBy { it.number } else emptyList()
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            val groups = ArrayList<List<RouteLine>>()
            groups.add(
                listOf(
                    RouteLine(ROUTE_SIM1, getString(R.string.sim_slot_1)),
                    RouteLine(ROUTE_SIM2, getString(R.string.sim_slot_2)),
                )
            )
            if (hasRin) {
                val rin = ArrayList<RouteLine>()
                rin.add(RouteLine(ROUTE_RIN, getString(R.string.route_rin_default_short)))
                accounts.forEach { rin.add(RouteLine(ROUTE_RIN_PREFIX + it.number, it.name.ifEmpty { it.identity.removePrefix("sip:") })) }
                // an account that is set but no longer listed stays visible, so it can be seen and changed
                if (current.startsWith(ROUTE_RIN_PREFIX) && rin.none { it.route == current }) rin.add(RouteLine(current, ""))
                groups.add(rin)
            }
            groups.add(listOf(RouteLine(ROUTE_OFF, getString(org.fossify.commons.R.string.none))))
            DefaultRouteDialog(this, groups, current, callback)
        }
    }
}

private class RouteLine(val route: String, val label: String)

private class DefaultRouteDialog(
    val activity: BaseSimpleActivity,
    groups: List<List<RouteLine>>,
    val current: String,
    val callback: (route: String) -> Unit,
) {
    private val density = activity.resources.displayMetrics.density
    private var dialog: AlertDialog? = null

    init {
        val pad = dp(PADDING_DP)
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(TextView(activity).apply {
                text = activity.getString(R.string.set_default_sim)
                setTextColor(activity.getProperTextColor())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, TITLE_SP)
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, 0, 0, dp(TITLE_GAP_DP))
            })
            groups.forEachIndexed { index, lines ->
                if (index > 0) addView(divider())
                lines.forEach { addView(buildRow(it)) }
            }
        }
        val scroll = ScrollView(activity).apply { addView(container) }
        activity.getAlertDialogBuilder().apply {
            activity.setupDialogStuff(scroll, this) { alertDialog -> dialog = alertDialog }
        }
    }

    private fun dp(value: Int) = (value * density).toInt()

    // The family's group divider: a clear 1 dp line in an opaque darker accent.
    private fun divider() = View(activity).apply {
        setBackgroundColor(ColorUtils.blendARGB(activity.getProperBackgroundColor(), activity.getProperPrimaryColor(), DIVIDER_BLEND))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1).coerceAtLeast(1)).apply {
            topMargin = dp(DIVIDER_GAP_DP)
            bottomMargin = dp(DIVIDER_GAP_DP)
        }
    }

    private fun buildRow(line: RouteLine): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(ROW_PAD_DP), 0, dp(ROW_PAD_DP))
            background = activity.rippleDrawable()
            isClickable = true
            setOnClickListener {
                callback(line.route)
                dialog?.dismiss()
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val radio = (activity.layoutInflater.inflate(org.fossify.commons.R.layout.radio_button, row, false) as RadioButton).apply {
            text = ""
            isChecked = line.route == current
            isClickable = false
            isFocusable = false
        }
        // radio_button.xml is width=match_parent; force wrap so the mark and label have room beside it
        row.addView(radio, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // one column for every mark, so the labels share one left edge
        val markColumn = FrameLayout(activity)
        markColumn.addView(
            mark(line.route, markColumn),
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        )
        row.addView(
            markColumn,
            LinearLayout.LayoutParams(dp(MARK_COLUMN_DP), ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(GAP_DP) }
        )

        row.addView(
            TextView(activity).apply {
                text = line.label
                setTextColor(activity.getProperTextColor())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, OPTION_SP)
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(GAP_DP) }
        )
        return row
    }

    // The route's own badge: the red 1 / blue 2 SIM card, the 臨N in its account's colours, a crossed circle for none.
    private fun mark(route: String, parent: ViewGroup): View = when {
        route == ROUTE_SIM1 || route == ROUTE_SIM2 -> {
            val first = route == ROUTE_SIM1
            activity.layoutInflater.inflate(R.layout.sim_badge, parent, false).apply {
                styleSimBadge(if (first) 1 else 2)
            }
        }

        route == ROUTE_RIN || route.startsWith(ROUTE_RIN_PREFIX) -> {
            val account = if (route == ROUTE_RIN) activity.config.rinDefaultAccount else route.removePrefix(ROUTE_RIN_PREFIX).toIntOrNull() ?: -1
            val (ground, text) = activity.rinBadgeColors(account)
            TextView(activity).apply {
                this.text = if (route == ROUTE_RIN) activity.rinBadgeFor(account) else rinBadge(account)
                setTextColor(text)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, RIN_BADGE_SP)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                includeFontPadding = false
                setPadding(dp(RIN_BADGE_PAD_DP), 0, dp(RIN_BADGE_PAD_DP), 0)
                minHeight = dp(RIN_BADGE_HEIGHT_DP)
                background = GradientDrawable().apply {
                    cornerRadius = RIN_BADGE_RADIUS_DP * density
                    setColor(ground)
                }
            }
        }

        else -> ImageView(activity).apply {
            setImageResource(org.fossify.commons.R.drawable.ic_block_vector)
            applyColorFilter(activity.themeColor(ThemeSlot.MENU_ICON_BLOCK))
            layoutParams = ViewGroup.LayoutParams(dp(NONE_ICON_DP), dp(NONE_ICON_DP))
        }
    }
}
