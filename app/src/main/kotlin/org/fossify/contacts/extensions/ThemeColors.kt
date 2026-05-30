package org.fossify.contacts.extensions

import android.content.Context
import androidx.annotation.StringRes
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.contacts.R
import org.fossify.contacts.helpers.PALETTE_BLACK
import org.fossify.contacts.helpers.PALETTE_YELLOW
import org.fossify.contacts.helpers.THEME_UNSET

// Granular, per-element theming for 白い熊 連絡先.
//
// Each [ThemeSlot] is one customizable color. Foundation slots reuse the stock commons colors
// (background / primary / text); every other slot inherits from a foundation slot by default
// (two-tier), so the whole app stays coherent and a single foundation change cascades. A slot
// only diverges once the user gives it an explicit override (stored as an Int; THEME_UNSET means
// "follow the default"). The default look is seeded to black background + yellow text/accents.

// A top-level section in the Theme screen (large accent header + full-width rule).
enum class ThemeGroup(@StringRes val labelRes: Int) {
    FOUNDATION(R.string.theme_group_foundation),
    SEARCH(R.string.theme_group_search),
    TABS(R.string.theme_group_tabs),
    LISTS(R.string.theme_group_lists),
}

enum class ThemeSlot(
    val key: String,
    val group: ThemeGroup,
    @StringRes val labelRes: Int,
    // Optional subgroup header (accent text + short underline) the slot sits under; null = directly under the section.
    @StringRes val subgroupLabelRes: Int? = null,
    val isFoundation: Boolean = false,
) {
    // Foundation — reuse the stock commons colors (editing these repaints the whole app)
    BACKGROUND("theme_background", ThemeGroup.FOUNDATION, R.string.theme_background, isFoundation = true),
    PRIMARY("theme_primary", ThemeGroup.FOUNDATION, R.string.theme_primary, isFoundation = true),
    TEXT("theme_text", ThemeGroup.FOUNDATION, R.string.theme_text, isFoundation = true),
    TEXT_SECONDARY("theme_text_secondary", ThemeGroup.FOUNDATION, R.string.theme_text_secondary),

    // Search bar
    SEARCH_FILL("theme_search_fill", ThemeGroup.SEARCH, R.string.theme_search_fill),
    SEARCH_TEXT("theme_search_text", ThemeGroup.SEARCH, R.string.theme_search_text),
    SEARCH_HINT("theme_search_hint", ThemeGroup.SEARCH, R.string.theme_search_hint),
    SEARCH_ICON("theme_search_icon", ThemeGroup.SEARCH, R.string.theme_search_icon),
    SEARCH_BORDER("theme_search_border", ThemeGroup.SEARCH, R.string.theme_search_border),

    // Tabs
    TAB_BACKGROUND("theme_tab_background", ThemeGroup.TABS, R.string.theme_tab_background),
    TAB_SELECTED("theme_tab_selected", ThemeGroup.TABS, R.string.theme_tab_selected),
    TAB_UNSELECTED("theme_tab_unselected", ThemeGroup.TABS, R.string.theme_tab_unselected),

    // Contact lists — one subgroup per tab (name + phone number + fast-scroller)
    CONTACT_NAME(
        "theme_contact_name", ThemeGroup.LISTS, R.string.theme_slot_name,
        subgroupLabelRes = R.string.theme_group_contacts,
    ),
    CONTACT_NUMBER(
        "theme_contact_number", ThemeGroup.LISTS, R.string.theme_slot_number,
        subgroupLabelRes = R.string.theme_group_contacts,
    ),
    CONTACT_FASTSCROLLER(
        "theme_contact_fastscroller", ThemeGroup.LISTS, R.string.theme_slot_fastscroller,
        subgroupLabelRes = R.string.theme_group_contacts,
    ),
    FAVORITE_NAME(
        "theme_favorite_name", ThemeGroup.LISTS, R.string.theme_slot_name,
        subgroupLabelRes = R.string.theme_group_favorites,
    ),
    FAVORITE_NUMBER(
        "theme_favorite_number", ThemeGroup.LISTS, R.string.theme_slot_number,
        subgroupLabelRes = R.string.theme_group_favorites,
    ),
    FAVORITE_FASTSCROLLER(
        "theme_favorite_fastscroller", ThemeGroup.LISTS, R.string.theme_slot_fastscroller,
        subgroupLabelRes = R.string.theme_group_favorites,
    ),
    GROUP_NAME(
        "theme_group_name", ThemeGroup.LISTS, R.string.theme_slot_name,
        subgroupLabelRes = R.string.theme_group_groups,
    ),
    GROUP_FASTSCROLLER(
        "theme_group_fastscroller", ThemeGroup.LISTS, R.string.theme_slot_fastscroller,
        subgroupLabelRes = R.string.theme_group_groups,
    ),
}

// Alpha factors for the muted (inherited) defaults.
private const val ALPHA_SECONDARY_TEXT = 0.6f
private const val ALPHA_SEARCH_HINT = 0.5f
private const val ALPHA_UNSELECTED_TAB = 0.6f

/** The effective color for a slot: the user's override if set, otherwise its inherited default. */
fun Context.themeColor(slot: ThemeSlot): Int {
    val override = config.getThemeOverride(slot.key)
    return if (override != THEME_UNSET) override else themeDefault(slot)
}

// One arm per slot — high cyclomatic count is inherent to an exhaustive enum map, not real complexity.
@Suppress("CyclomaticComplexMethod")
private fun Context.themeDefault(slot: ThemeSlot): Int = when (slot) {
    // Foundation reads the stock commons colors (seeded to black/yellow on first run)
    ThemeSlot.BACKGROUND -> getProperBackgroundColor()
    ThemeSlot.PRIMARY -> getProperPrimaryColor()
    ThemeSlot.TEXT -> getProperTextColor()
    ThemeSlot.TEXT_SECONDARY -> themeColor(ThemeSlot.TEXT).adjustAlpha(ALPHA_SECONDARY_TEXT)

    // Search bar: black fill, yellow text/icon/border by inheriting foundation
    ThemeSlot.SEARCH_FILL -> themeColor(ThemeSlot.BACKGROUND)
    ThemeSlot.SEARCH_TEXT -> themeColor(ThemeSlot.PRIMARY)
    ThemeSlot.SEARCH_HINT -> themeColor(ThemeSlot.PRIMARY).adjustAlpha(ALPHA_SEARCH_HINT)
    ThemeSlot.SEARCH_ICON -> themeColor(ThemeSlot.PRIMARY)
    ThemeSlot.SEARCH_BORDER -> themeColor(ThemeSlot.PRIMARY)

    // Tabs
    ThemeSlot.TAB_BACKGROUND -> themeColor(ThemeSlot.BACKGROUND)
    ThemeSlot.TAB_SELECTED -> themeColor(ThemeSlot.PRIMARY)
    ThemeSlot.TAB_UNSELECTED -> themeColor(ThemeSlot.TEXT).adjustAlpha(ALPHA_UNSELECTED_TAB)

    // Per-tab list colors all inherit from the foundation text / primary by default.
    // Numbers inherit the muted secondary text (the rows render at full opacity, so the slot
    // color is exactly what shows — see the alpha = 1f reset in the adapters).
    ThemeSlot.CONTACT_NAME -> themeColor(ThemeSlot.TEXT)
    ThemeSlot.CONTACT_NUMBER -> themeColor(ThemeSlot.TEXT_SECONDARY)
    ThemeSlot.CONTACT_FASTSCROLLER -> themeColor(ThemeSlot.PRIMARY)
    ThemeSlot.FAVORITE_NAME -> themeColor(ThemeSlot.TEXT)
    ThemeSlot.FAVORITE_NUMBER -> themeColor(ThemeSlot.TEXT_SECONDARY)
    ThemeSlot.FAVORITE_FASTSCROLLER -> themeColor(ThemeSlot.PRIMARY)
    ThemeSlot.GROUP_NAME -> themeColor(ThemeSlot.TEXT)
    ThemeSlot.GROUP_FASTSCROLLER -> themeColor(ThemeSlot.PRIMARY)
}

/** Set an explicit override for a slot. Foundation slots write through to the stock commons colors. */
fun Context.setThemeColor(slot: ThemeSlot, color: Int) {
    when (slot) {
        ThemeSlot.PRIMARY -> {
            config.isSystemThemeEnabled = false
            config.primaryColor = color
        }

        ThemeSlot.BACKGROUND -> {
            config.isSystemThemeEnabled = false
            config.backgroundColor = color
        }

        ThemeSlot.TEXT -> {
            config.isSystemThemeEnabled = false
            config.textColor = color
        }

        else -> config.setThemeOverride(slot.key, color)
    }
}

/** Revert a slot to its default (palette for the editable foundation colors, inherited otherwise). */
fun Context.resetThemeColor(slot: ThemeSlot) {
    when (slot) {
        ThemeSlot.BACKGROUND -> setThemeColor(slot, PALETTE_BLACK)
        ThemeSlot.PRIMARY, ThemeSlot.TEXT -> setThemeColor(slot, PALETTE_YELLOW)
        else -> config.clearThemeOverride(slot.key)
    }
}

/** One-time seed of the default black/yellow look across the whole app (via the stock colors). */
fun Context.seedBlackYellowThemeIfNeeded() {
    if (config.themeV1Seeded) {
        return
    }

    config.isSystemThemeEnabled = false
    config.backgroundColor = PALETTE_BLACK
    config.textColor = PALETTE_YELLOW
    config.primaryColor = PALETTE_YELLOW
    config.accentColor = PALETTE_YELLOW
    config.themeV1Seeded = true
}
