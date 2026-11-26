package org.fossify.contacts.extensions

import android.app.Activity
import android.app.Application
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.icu.util.ULocale
import android.os.Build
import android.os.LocaleList
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.pillDrawable
import org.fossify.commons.extensions.rippleDrawable
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.helpers.UiRole
import org.fossify.commons.helpers.uiColor
import org.fossify.contacts.R
import org.xmlpull.v1.XmlPullParser
import java.util.Locale

// The app's own language (UI page → App language, the page's first section). The choice is applied through
// the AppCompat per-app locale and kept in our own preferences as well (Config.appLanguage, so it travels
// in the export): EMUI does not keep the system's per-app locale across a sideload update, so App.onCreate
// asserts it again at every start.

private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
private const val ALWAYS_FIRST = 2 // Japanese and English lead the list; the rest is sorted by name

// The picker's measures.
private const val ROW_PAD_H_DP = 14f
private const val ROW_PAD_V_DP = 7f
private const val RING_DP = 18f
private const val RING_STROKE_DP = 2f
private const val RING_DOT_INSET_DP = 5f
private const val RING_GAP_DP = 12f
private const val NAME_SP = 16f
private const val SMALL_SP = 12f
private const val LIST_PAD_DP = 12f

/** The languages the app offers, as BCP 47 tags in the order of `res/xml/locales_config.xml` — its one list. */
fun Context.offeredLanguageTags(): List<String> {
    val tags = ArrayList<String>()
    val parser = resources.getXml(R.xml.locales_config)
    try {
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "locale") {
                parser.getAttributeValue(ANDROID_NS, "name")?.let { tags.add(it) }
            }
        }
    } finally {
        parser.close()
    }
    return tags
}

/** The chosen language's tag, empty for "System default". A choice made before +138 is taken over from AppCompat, once. */
fun Context.appLanguageTag(): String =
    config.appLanguage ?: AppCompatDelegate.getApplicationLocales().toLanguageTags().also { config.appLanguage = it }

private fun localesFor(tag: String) =
    if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)

/** At every start, before any activity draws: the stored choice, asserted again. */
fun Application.reassertAppLanguage() {
    val tag = config.appLanguage ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // AppCompat reaches the system's locale manager only through a live activity; here there is none yet
        val manager = getSystemService(LocaleManager::class.java) ?: return
        val want = LocaleList.forLanguageTags(tag)
        if (manager.applicationLocales != want) manager.applicationLocales = want
        return
    }
    val want = localesFor(tag)
    if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != want.toLanguageTags()) {
        AppCompatDelegate.setApplicationLocales(want)
    }
    applyAppLanguage()
}

// Below Android 13 the per-app locale reaches activities only. What is built off the application context —
// a toast, a shortcut's label, an answer to another app — reads these resources, so they get the language too.
@Suppress("DEPRECATION")
fun Application.applyAppLanguage() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
    val tag = config.appLanguage ?: return
    val locales = if (tag.isEmpty()) Resources.getSystem().configuration.locales else LocaleList(Locale.forLanguageTag(tag))
    if (resources.configuration.locales == locales) return
    val changed = Configuration(resources.configuration).apply { setLocales(locales) }
    resources.updateConfiguration(changed, resources.displayMetrics)
}

/** The language's name in that language — 日本語, Čeština. */
private fun languageName(tag: String): String {
    val locale = Locale.forLanguageTag(tag)
    return locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
}

// The country a tag stands for (its own, or the likeliest one for a bare language), named in that language,
// and the tag written with it: "cs" → Česko, cs-CZ.
private fun regionOf(tag: String): Pair<String, String> {
    val full = ULocale.addLikelySubtags(ULocale.forLanguageTag(tag))
    val country = full.country
    if (country.isEmpty()) return "" to tag
    return full.getDisplayCountry(full) to "${full.language}-$country"
}

/** What the language row shows: the chosen language in its own name, or "System default". */
fun Context.appLanguageLabel(): String {
    val tag = appLanguageTag()
    return if (tag.isEmpty()) getString(R.string.language_system_default) else languageName(tag)
}

private fun Context.dp(value: Float) = (value * resources.displayMetrics.density).toInt()

// The selection mark: a ring, with a dot when chosen.
private fun Context.ring(chosen: Boolean): Drawable {
    val color = uiColor(if (chosen) UiRole.RADIO_ON else UiRole.SWITCH_OFF_OUTLINE)
    val outline = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setStroke(dp(RING_STROKE_DP), color)
    }
    if (!chosen) return outline
    val dot = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }
    return LayerDrawable(arrayOf(outline, dot)).apply {
        val inset = dp(RING_DOT_INSET_DP)
        setLayerInset(1, inset, inset, inset, inset)
    }
}

// One row: the mark, the language in its own name with its country beneath, and the tag in a column at the
// right. Only the current language sits in a pill (black-yellow theme, "App language").
private fun Context.languageRow(tag: String, chosen: Boolean, onClick: () -> Unit): View {
    val note = themeColor(ThemeSlot.SETTINGS_NOTE)
    val (country, shownTag) = if (tag.isEmpty()) "" to "" else regionOf(tag)
    val wrap = ViewGroup.LayoutParams.WRAP_CONTENT

    val names = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(TextView(context).apply {
            text = if (tag.isEmpty()) getString(R.string.language_system_default) else languageName(tag)
            setTextColor(uiColor(UiRole.DIALOG_TEXT))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, NAME_SP)
            if (chosen) setTypeface(typeface, Typeface.BOLD)
            // a right-to-left name stays at the row's start side, like the others
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        })
        if (country.isNotEmpty()) {
            addView(TextView(context).apply {
                text = country
                setTextColor(note)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, SMALL_SP)
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            })
        }
    }
    return LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(ROW_PAD_H_DP), dp(ROW_PAD_V_DP), dp(ROW_PAD_H_DP), dp(ROW_PAD_V_DP))
        background = if (chosen) {
            pillDrawable(uiColor(UiRole.DIALOG_BUTTON_FRAME), uiColor(UiRole.DIALOG_GROUND), main = true)
        } else {
            rippleDrawable()
        }
        addView(View(context).apply { background = ring(chosen) }, LinearLayout.LayoutParams(dp(RING_DP), dp(RING_DP)))
        addView(names, LinearLayout.LayoutParams(0, wrap, 1f).apply { marginStart = dp(RING_GAP_DP) })
        addView(
            TextView(context).apply {
                text = shownTag
                typeface = Typeface.MONOSPACE
                setTextColor(note)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, SMALL_SP)
            },
            LinearLayout.LayoutParams(wrap, wrap).apply { marginStart = dp(RING_GAP_DP) },
        )
        setOnClickListener { onClick() }
    }
}

/**
 * The language picker: "System default", Japanese, English, then the other offered languages by name. A
 * language chosen earlier that is no longer offered stays in force and is listed last, as the current row.
 * Choosing a row applies it at once — AppCompat rebuilds the open screens — and closes the list.
 */
fun Activity.showAppLanguagePicker() {
    val current = appLanguageTag()
    val offered = offeredLanguageTags()
    val sorted = offered.take(ALWAYS_FIRST) + offered.drop(ALWAYS_FIRST).sortedBy { languageName(it).lowercase(Locale.ROOT) }
    val known = current.isEmpty() || sorted.any { it.equals(current, ignoreCase = true) }
    val tags = listOf("") + sorted + if (known) emptyList() else listOf(current)

    var dialog: AlertDialog? = null
    var currentRow: View? = null
    val list = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(LIST_PAD_DP), dp(LIST_PAD_DP), dp(LIST_PAD_DP), 0)
    }
    val scroll = ScrollView(this).apply { addView(list) }
    val match = ViewGroup.LayoutParams.MATCH_PARENT
    tags.forEachIndexed { index, tag ->
        val chosen = tag.equals(current, ignoreCase = true)
        // a hairline between two rows, inset to the text; none directly above or below the current row's pill
        val previousChosen = index > 0 && tags[index - 1].equals(current, ignoreCase = true)
        if (index > 0 && !chosen && !previousChosen) {
            list.addView(
                View(this).apply { setBackgroundColor(uiColor(UiRole.LIST_DIVIDER)) },
                LinearLayout.LayoutParams(match, 1).apply {
                    marginStart = dp(ROW_PAD_H_DP + RING_DP + RING_GAP_DP)
                    marginEnd = dp(ROW_PAD_H_DP)
                },
            )
        }
        val row = languageRow(tag, chosen) {
            if (!chosen) {
                config.appLanguage = tag
                (application as Application).applyAppLanguage()
                AppCompatDelegate.setApplicationLocales(localesFor(tag))
            }
            dialog?.dismiss()
        }
        if (chosen) currentRow = row
        list.addView(row, LinearLayout.LayoutParams(match, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    getAlertDialogBuilder()
        .setNegativeButton(org.fossify.commons.R.string.cancel, null)
        .apply {
            setupDialogStuff(scroll, this, R.string.theme_app_language) {
                dialog = it
                // open on the current language, not on the top of a long list
                scroll.post { currentRow?.let { row -> scroll.scrollTo(0, (row.top - row.height).coerceAtLeast(0)) } }
            }
        }
}
