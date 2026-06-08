package org.fossify.contacts.extensions

import android.content.Context
import androidx.annotation.StringRes
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.getContrastColor
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
    TOPBAR(R.string.theme_group_topbar),
    LISTS(R.string.theme_group_lists),
    ROWS(R.string.theme_group_rows),
}

enum class ThemeSlot(
    val key: String,
    val group: ThemeGroup,
    @StringRes val labelRes: Int,
    // Optional subgroup header (accent text + short underline) the slot sits under; null = directly under the section.
    @StringRes val subgroupLabelRes: Int? = null,
    val isFoundation: Boolean = false,
    // hasFont = true for concrete text views (family / weight / size are configurable per element)
    val hasFont: Boolean = false,
) {
    // Foundation — reuse the stock commons colors (editing these repaints the whole app)
    BACKGROUND("theme_background", ThemeGroup.FOUNDATION, R.string.theme_background, isFoundation = true),
    PRIMARY("theme_primary", ThemeGroup.FOUNDATION, R.string.theme_primary, isFoundation = true),
    TEXT("theme_text", ThemeGroup.FOUNDATION, R.string.theme_text, isFoundation = true),
    TEXT_SECONDARY("theme_text_secondary", ThemeGroup.FOUNDATION, R.string.theme_text_secondary),

    // Search bar
    SEARCH_FILL("theme_search_fill", ThemeGroup.SEARCH, R.string.theme_search_fill),
    SEARCH_TEXT("theme_search_text", ThemeGroup.SEARCH, R.string.theme_search_text, hasFont = true),
    SEARCH_HINT("theme_search_hint", ThemeGroup.SEARCH, R.string.theme_search_hint),
    SEARCH_ICON("theme_search_icon", ThemeGroup.SEARCH, R.string.theme_search_icon),
    SEARCH_ACTION_ICON("theme_search_action_icon", ThemeGroup.SEARCH, R.string.theme_search_action_icon),
    SEARCH_MENU_TEXT("theme_search_menu_text", ThemeGroup.SEARCH, R.string.theme_search_menu_text),
    SEARCH_BORDER("theme_search_border", ThemeGroup.SEARCH, R.string.theme_search_border),

    // Tabs
    TAB_BACKGROUND("theme_tab_background", ThemeGroup.TABS, R.string.theme_tab_background),
    TAB_SELECTED("theme_tab_selected", ThemeGroup.TABS, R.string.theme_tab_selected),
    TAB_UNSELECTED("theme_tab_unselected", ThemeGroup.TABS, R.string.theme_tab_unselected),

    // Top bar (secondary screens: Settings, this page, group contacts…)
    TOPBAR_TITLE("theme_topbar_title", ThemeGroup.TOPBAR, R.string.theme_topbar_title),
    TOPBAR_NAV("theme_topbar_nav", ThemeGroup.TOPBAR, R.string.theme_topbar_nav),

    // Contact lists — one subgroup per tab (name + phone number + fast-scroller)
    CONTACT_NAME(
        "theme_contact_name", ThemeGroup.LISTS, R.string.theme_slot_name,
        subgroupLabelRes = R.string.theme_group_contacts, hasFont = true,
    ),
    CONTACT_NUMBER(
        "theme_contact_number", ThemeGroup.LISTS, R.string.theme_slot_number,
        subgroupLabelRes = R.string.theme_group_contacts, hasFont = true,
    ),
    CONTACT_FASTSCROLLER(
        "theme_contact_fastscroller", ThemeGroup.LISTS, R.string.theme_slot_fastscroller,
        subgroupLabelRes = R.string.theme_group_contacts,
    ),
    FAVORITE_NAME(
        "theme_favorite_name", ThemeGroup.LISTS, R.string.theme_slot_name,
        subgroupLabelRes = R.string.theme_group_favorites, hasFont = true,
    ),
    FAVORITE_NUMBER(
        "theme_favorite_number", ThemeGroup.LISTS, R.string.theme_slot_number,
        subgroupLabelRes = R.string.theme_group_favorites, hasFont = true,
    ),
    FAVORITE_FASTSCROLLER(
        "theme_favorite_fastscroller", ThemeGroup.LISTS, R.string.theme_slot_fastscroller,
        subgroupLabelRes = R.string.theme_group_favorites,
    ),
    GROUP_NAME(
        "theme_group_name", ThemeGroup.LISTS, R.string.theme_slot_name,
        subgroupLabelRes = R.string.theme_group_groups, hasFont = true,
    ),
    GROUP_FASTSCROLLER(
        "theme_group_fastscroller", ThemeGroup.LISTS, R.string.theme_slot_fastscroller,
        subgroupLabelRes = R.string.theme_group_groups,
    ),

    // Contacts'-list row fields — one styling slot per RowField (font / weight / size / color), shared by
    // all three contact lists. Rendered specially in ThemeActivity (only enabled fields show a control).
    ROW_DISPLAY_NAME("row_display_name", ThemeGroup.ROWS, R.string.field_display_name, hasFont = true),
    ROW_PREFIX("row_prefix", ThemeGroup.ROWS, R.string.field_prefix, hasFont = true),
    ROW_FIRST_NAME("row_first_name", ThemeGroup.ROWS, R.string.field_first_name, hasFont = true),
    ROW_MIDDLE_NAME("row_middle_name", ThemeGroup.ROWS, R.string.field_middle_name, hasFont = true),
    ROW_SURNAME("row_surname", ThemeGroup.ROWS, R.string.field_surname, hasFont = true),
    ROW_SUFFIX("row_suffix", ThemeGroup.ROWS, R.string.field_suffix, hasFont = true),
    ROW_NICKNAME("row_nickname", ThemeGroup.ROWS, R.string.field_nickname, hasFont = true),
    ROW_PHONE("row_phone", ThemeGroup.ROWS, R.string.field_phone, hasFont = true),
    ROW_EMAIL("row_email", ThemeGroup.ROWS, R.string.field_email, hasFont = true),
    ROW_ADDRESS("row_address", ThemeGroup.ROWS, R.string.field_address, hasFont = true),
    ROW_ADDRESS_STREET("row_address_street", ThemeGroup.ROWS, R.string.field_address_street, hasFont = true),
    ROW_ADDRESS_CITY("row_address_city", ThemeGroup.ROWS, R.string.field_address_city, hasFont = true),
    ROW_ADDRESS_REGION("row_address_region", ThemeGroup.ROWS, R.string.field_address_region, hasFont = true),
    ROW_ADDRESS_POSTCODE("row_address_postcode", ThemeGroup.ROWS, R.string.field_address_postcode, hasFont = true),
    ROW_ADDRESS_COUNTRY("row_address_country", ThemeGroup.ROWS, R.string.field_address_country, hasFont = true),
    ROW_COMPANY("row_company", ThemeGroup.ROWS, R.string.field_company, hasFont = true),
    ROW_POSITION("row_position", ThemeGroup.ROWS, R.string.field_position, hasFont = true),
    ROW_WEBSITE("row_website", ThemeGroup.ROWS, R.string.field_website, hasFont = true),
    ROW_IM("row_im", ThemeGroup.ROWS, R.string.field_im, hasFont = true),
    ROW_BIRTHDAY("row_birthday", ThemeGroup.ROWS, R.string.field_birthday, hasFont = true),
    ROW_ANNIVERSARY("row_anniversary", ThemeGroup.ROWS, R.string.field_anniversary, hasFont = true),
    ROW_NOTE("row_note", ThemeGroup.ROWS, R.string.field_note, hasFont = true),
    ROW_GROUPS("row_groups", ThemeGroup.ROWS, R.string.field_groups, hasFont = true),
    ROW_CONTACT_SOURCE("row_contact_source", ThemeGroup.ROWS, R.string.field_contact_source, hasFont = true),

    // Color of the optional divider line drawn between contact rows (thickness set via a slider).
    CONTACT_DIVIDER("contacts_list_divider", ThemeGroup.ROWS, R.string.theme_slot_divider),

    // The separator text shown between two fields that share a line (a moved-right column).
    COLUMN_SPACER("contacts_list_column_spacer", ThemeGroup.ROWS, R.string.theme_rows_spacer, hasFont = true),
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
    ThemeSlot.SEARCH_ACTION_ICON -> themeColor(ThemeSlot.PRIMARY)
    // overflow ("⋮") popup item text — its background keeps following the foundation background
    ThemeSlot.SEARCH_MENU_TEXT -> themeColor(ThemeSlot.TEXT)
    ThemeSlot.SEARCH_BORDER -> themeColor(ThemeSlot.PRIMARY)

    // Tabs
    ThemeSlot.TAB_BACKGROUND -> themeColor(ThemeSlot.BACKGROUND)
    ThemeSlot.TAB_SELECTED -> themeColor(ThemeSlot.PRIMARY)
    ThemeSlot.TAB_UNSELECTED -> themeColor(ThemeSlot.TEXT).adjustAlpha(ALPHA_UNSELECTED_TAB)

    // Top bar: title + back arrow contrast the primary-coloured toolbar by default
    ThemeSlot.TOPBAR_TITLE -> themeColor(ThemeSlot.PRIMARY).getContrastColor()
    ThemeSlot.TOPBAR_NAV -> themeColor(ThemeSlot.PRIMARY).getContrastColor()

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

    // Contacts'-list row fields: name-like fields read as primary text, the rest as muted secondary text.
    ThemeSlot.ROW_DISPLAY_NAME, ThemeSlot.ROW_PREFIX, ThemeSlot.ROW_FIRST_NAME, ThemeSlot.ROW_MIDDLE_NAME,
    ThemeSlot.ROW_SURNAME, ThemeSlot.ROW_SUFFIX, ThemeSlot.ROW_NICKNAME -> themeColor(ThemeSlot.TEXT)

    ThemeSlot.ROW_PHONE, ThemeSlot.ROW_EMAIL, ThemeSlot.ROW_ADDRESS, ThemeSlot.ROW_ADDRESS_STREET,
    ThemeSlot.ROW_ADDRESS_CITY, ThemeSlot.ROW_ADDRESS_REGION, ThemeSlot.ROW_ADDRESS_POSTCODE,
    ThemeSlot.ROW_ADDRESS_COUNTRY, ThemeSlot.ROW_COMPANY, ThemeSlot.ROW_POSITION, ThemeSlot.ROW_WEBSITE,
    ThemeSlot.ROW_IM, ThemeSlot.ROW_BIRTHDAY, ThemeSlot.ROW_ANNIVERSARY, ThemeSlot.ROW_NOTE,
    ThemeSlot.ROW_GROUPS, ThemeSlot.ROW_CONTACT_SOURCE -> themeColor(ThemeSlot.TEXT_SECONDARY)

    ThemeSlot.CONTACT_DIVIDER -> themeColor(ThemeSlot.TEXT_SECONDARY)
    ThemeSlot.COLUMN_SPACER -> themeColor(ThemeSlot.TEXT_SECONDARY)
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
