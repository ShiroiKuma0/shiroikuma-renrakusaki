package org.fossify.contacts.extensions

import android.content.Context
import androidx.annotation.StringRes
import androidx.core.graphics.ColorUtils
import org.fossify.commons.helpers.FAMILY_YELLOW
import org.fossify.commons.helpers.UiRole
import org.fossify.commons.helpers.isUiColorSet
import org.fossify.commons.helpers.resetAllUiColors
import org.fossify.commons.helpers.resetUiColor
import org.fossify.commons.helpers.setUiColor
import org.fossify.commons.helpers.uiColor
import org.fossify.contacts.R
import org.fossify.contacts.helpers.THEME_UNSET

// Granular, per-element theming for 白い熊 連絡先.
//
// Each [ThemeSlot] is one colour role of this app's own surfaces, settable on the UI page in the section
// of its item. The roles of everything the commons draws (buttons, dialogs, switches, fields, bars…) are
// commons' [UiRole]s; a slot with a [ThemeSlot.role] IS that commons role, shown here under this app's
// label and section, and is stored in the role's key. Every other slot inherits from a role by default,
// so a single foundation change cascades, and only diverges once it is given an explicit override
// (stored as an Int; THEME_UNSET means "follow the default").
//
// No colour is written in this app's XML, and the two primaries (#FFFF00 / #000000) are written once, in
// commons' role table (helpers/UiRoles.kt). The only literals below are colours that carry meaning.

// A top-level section in the Theme screen (large accent header + full-width rule). Declaration order
// is the page order: chrome first (foundation, search, tabs, top bar), then the Contacts-tab letter
// sections, then the shared row layout, then the per-tab list colors.
enum class ThemeGroup(@StringRes val labelRes: Int) {
    FOUNDATION(R.string.theme_group_foundation),
    SEARCH(R.string.theme_group_search),
    TABS(R.string.theme_group_tabs),
    TOPBAR(R.string.theme_group_topbar),
    PHOTO_ICONS(R.string.theme_group_photo_icons),
    CARD(R.string.contact_card),
    SECTIONS(R.string.theme_sections_group),
    ROWS(R.string.theme_group_rows),
    LISTS(R.string.theme_group_lists),
    ROUTES(R.string.call_routes),
    SWIPE_CALL(R.string.swipe_to_call),
    MENUS(R.string.theme_group_menus),
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
    // The commons role this slot is (see the head of the file); null = a role of this app alone.
    val role: UiRole? = null,
) {
    // Foundation — reuse the stock commons colors (editing these repaints the whole app)
    BACKGROUND("theme_background", ThemeGroup.FOUNDATION, R.string.theme_background, isFoundation = true, role = UiRole.BACKGROUND),
    PRIMARY("theme_primary", ThemeGroup.FOUNDATION, R.string.theme_primary, isFoundation = true, role = UiRole.ACCENT),
    TEXT("theme_text", ThemeGroup.FOUNDATION, R.string.theme_text, isFoundation = true, role = UiRole.TEXT),
    TEXT_SECONDARY("theme_text_secondary", ThemeGroup.FOUNDATION, R.string.theme_text_secondary),
    // The explanations on the UI settings page and the thin rule down their left edge (black-yellow theme).
    SETTINGS_NOTE("theme_settings_note", ThemeGroup.FOUNDATION, R.string.theme_settings_note),
    // A switch that is off: its outline and dot, a darker opaque yellow (on is the accent). Commons draws the
    // switches (sk17), so the override is mirrored into BaseConfig.switchOffColor.
    SWITCH_OFF("theme_switch_off", ThemeGroup.FOUNDATION, R.string.theme_switch_off, role = UiRole.SWITCH_OFF_OUTLINE),
    // The icon of each section of the UI page, on its heading and its chip: coloured by what kind of thing
    // the section is (black-yellow theme, "The seven colours — each with one meaning").
    SECTION_ICON_BASE("theme_section_base", ThemeGroup.FOUNDATION, R.string.theme_section_base, R.string.theme_sub_section_icons),
    SECTION_ICON_CONTROLS("theme_section_controls", ThemeGroup.FOUNDATION, R.string.theme_section_controls, R.string.theme_sub_section_icons),
    SECTION_ICON_WORDS("theme_section_words", ThemeGroup.FOUNDATION, R.string.theme_section_words, R.string.theme_sub_section_icons),
    SECTION_ICON_FRAME("theme_section_frame", ThemeGroup.FOUNDATION, R.string.theme_section_frame, R.string.theme_sub_section_icons),
    SECTION_ICON_REACH("theme_section_reach", ThemeGroup.FOUNDATION, R.string.theme_section_reach, R.string.theme_sub_section_icons),
    SECTION_ICON_WARNING("theme_section_warning", ThemeGroup.FOUNDATION, R.string.theme_section_warning, R.string.theme_sub_section_icons),
    SECTION_ICON_FLASH("theme_section_flash", ThemeGroup.FOUNDATION, R.string.theme_section_flash, R.string.theme_sub_section_icons),

    // Search bar
    SEARCH_FILL("theme_search_fill", ThemeGroup.SEARCH, R.string.theme_search_fill, role = UiRole.SEARCH_GROUND),
    SEARCH_TEXT("theme_search_text", ThemeGroup.SEARCH, R.string.theme_search_text, hasFont = true, role = UiRole.SEARCH_TEXT),
    SEARCH_HINT("theme_search_hint", ThemeGroup.SEARCH, R.string.theme_search_hint, role = UiRole.SEARCH_HINT),
    SEARCH_ICON("theme_search_icon", ThemeGroup.SEARCH, R.string.theme_search_icon, role = UiRole.SEARCH_ICON),
    SEARCH_ACTION_ICON("theme_search_action_icon", ThemeGroup.SEARCH, R.string.theme_search_action_icon),
    SEARCH_MENU_TEXT("theme_search_menu_text", ThemeGroup.SEARCH, R.string.theme_search_menu_text, role = UiRole.MENU_LABEL),
    SEARCH_BORDER("theme_search_border", ThemeGroup.SEARCH, R.string.theme_search_border, role = UiRole.SEARCH_FRAME),
    COLUMN_BUTTONS("theme_column_buttons", ThemeGroup.SEARCH, R.string.theme_slot_column_buttons, hasFont = true),

    // Tabs
    TAB_BACKGROUND("theme_tab_background", ThemeGroup.TABS, R.string.theme_tab_background, role = UiRole.BOTTOM_BAR_GROUND),
    TAB_SELECTED("theme_tab_selected", ThemeGroup.TABS, R.string.theme_tab_selected, role = UiRole.BOTTOM_TAB_ACTIVE),
    TAB_UNSELECTED("theme_tab_unselected", ThemeGroup.TABS, R.string.theme_tab_unselected, role = UiRole.BOTTOM_TAB_IDLE),

    // Top bar (secondary screens: Settings, this page, group contacts…)
    TOPBAR_TITLE("theme_topbar_title", ThemeGroup.TOPBAR, R.string.theme_topbar_title, role = UiRole.TOOLBAR_TITLE),
    TOPBAR_NAV("theme_topbar_nav", ThemeGroup.TOPBAR, R.string.theme_topbar_nav, role = UiRole.TOOLBAR_ICON),

    // Icons drawn over a contact's photo, on the view and edit contact screens: the top bar's icons
    // (back arrow, edit, share, delete, ⋮) and the actions on the photo's bottom edge (favorite, call,
    // SMS, email, change photo), each over a halo in the outline color so they never merge into the photo.
    PHOTO_TOOLBAR_ICON("theme_photo_toolbar_icon", ThemeGroup.PHOTO_ICONS, R.string.theme_photo_toolbar_icon),
    PHOTO_ACTION_ICON("theme_photo_action_icon", ThemeGroup.PHOTO_ICONS, R.string.theme_photo_action_icon),
    PHOTO_ICON_OUTLINE("theme_photo_icon_outline", ThemeGroup.PHOTO_ICONS, R.string.theme_photo_icon_outline),
    // The shade behind those icons (it fades out toward the photo's middle) and the ground behind the
    // enlarged photo. Both keep their alpha: the photo must show through.
    PHOTO_SHADE("theme_photo_shade", ThemeGroup.PHOTO_ICONS, R.string.theme_photo_shade),
    PHOTO_SCRIM("theme_photo_scrim", ThemeGroup.PHOTO_ICONS, R.string.theme_photo_scrim),

    // The contact card: each tile wears the colour of its kind — frame, caption, faint icon and tint.
    CARD_NAME("theme_card_name", ThemeGroup.CARD, R.string.card_color_name),
    CARD_PHONE("theme_card_phone", ThemeGroup.CARD, R.string.card_color_phone),
    CARD_MESSAGE("theme_card_message", ThemeGroup.CARD, R.string.card_color_message),
    CARD_WEB("theme_card_web", ThemeGroup.CARD, R.string.card_color_web),
    CARD_ORG("theme_card_org", ThemeGroup.CARD, R.string.card_color_org),
    CARD_NOTE("theme_card_note", ThemeGroup.CARD, R.string.card_color_note),
    CARD_STORAGE("theme_card_storage", ThemeGroup.CARD, R.string.card_color_storage),
    CARD_CALLS("theme_card_calls", ThemeGroup.CARD, R.string.card_color_calls),
    // …while what a tile says stays in two colours: the value, and what the value is.
    CARD_VALUE("theme_card_value", ThemeGroup.CARD, R.string.card_color_value),
    CARD_LABEL("theme_card_label", ThemeGroup.CARD, R.string.card_color_label),

    // Swipe to call: the band revealed behind a swiped row, per SIM, and the 1/2 glyph + "Dialing…" on it
    SWIPE_SIM1("theme_swipe_sim1", ThemeGroup.SWIPE_CALL, R.string.swipe_sim1_color),
    SWIPE_SIM2("theme_swipe_sim2", ThemeGroup.SWIPE_CALL, R.string.swipe_sim2_color),
    SWIPE_TEXT("theme_swipe_text", ThemeGroup.SWIPE_CALL, R.string.swipe_text_color),
    // …and the same for a side that dials through 白い熊 臨電話: its band and the 臨 mark + "Dialing…" on it
    SWIPE_RIN("theme_swipe_rin", ThemeGroup.SWIPE_CALL, R.string.swipe_rin_color),
    SWIPE_RIN_TEXT("theme_swipe_rin_text", ThemeGroup.SWIPE_CALL, R.string.swipe_rin_text_color),

    // Route badges on the contact screen's numbers: 1 and 2 (SIM), 臨N (Rindenwa account) — ground and text
    ROUTE_SIM1("theme_route_sim1", ThemeGroup.ROUTES, R.string.route_sim1_color),
    ROUTE_SIM2("theme_route_sim2", ThemeGroup.ROUTES, R.string.route_sim2_color),
    ROUTE_SIM_TEXT("theme_route_sim_text", ThemeGroup.ROUTES, R.string.route_sim_text_color),
    ROUTE_RIN("theme_route_rin", ThemeGroup.ROUTES, R.string.route_rin_color),
    ROUTE_RIN_TEXT("theme_route_rin_text", ThemeGroup.ROUTES, R.string.route_rin_text_color),
    // The default-SIM badge on a list photo: the card per SIM, its digit and the halo around the digit.
    PHOTO_BADGE_SIM1("theme_photo_badge_sim1", ThemeGroup.ROUTES, R.string.photo_badge_sim1_color),
    PHOTO_BADGE_SIM2("theme_photo_badge_sim2", ThemeGroup.ROUTES, R.string.photo_badge_sim2_color),
    PHOTO_BADGE_TEXT("theme_photo_badge_text", ThemeGroup.ROUTES, R.string.photo_badge_text_color),
    PHOTO_BADGE_HALO("theme_photo_badge_halo", ThemeGroup.ROUTES, R.string.photo_badge_halo_color),

    // Menus: an icon is coloured by what its row does; a destructive row is the warning colour throughout.
    // (Frame and label of the other rows: Search bar → the ⋮ menu text.)
    MENU_ICON_CALL("theme_menu_icon_call", ThemeGroup.MENUS, R.string.menu_icon_call),
    MENU_ICON_MESSAGE("theme_menu_icon_message", ThemeGroup.MENUS, R.string.menu_icon_message),
    MENU_ICON_CONTACT("theme_menu_icon_contact", ThemeGroup.MENUS, R.string.menu_icon_contact),
    MENU_ICON_SHARE("theme_menu_icon_share", ThemeGroup.MENUS, R.string.menu_icon_share),
    MENU_ICON_VIEW("theme_menu_icon_view", ThemeGroup.MENUS, R.string.menu_icon_view),
    MENU_ICON_BLOCK("theme_menu_icon_block", ThemeGroup.MENUS, R.string.menu_icon_block),
    MENU_WARNING("theme_menu_warning", ThemeGroup.MENUS, R.string.menu_warning),

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

    // Visibility badge on a contact photo (crossed-out lock = system contact, eye = private but visible to
    // other apps; private-only has none). Shared by all contact lists.
    BADGE_SYSTEM(
        "theme_badge_system", ThemeGroup.LISTS, R.string.theme_badge_system,
        subgroupLabelRes = R.string.theme_group_visibility_badges,
    ),
    BADGE_VISIBLE(
        "theme_badge_visible", ThemeGroup.LISTS, R.string.theme_badge_visible,
        subgroupLabelRes = R.string.theme_group_visibility_badges,
    ),
    BADGE_OUTLINE(
        "theme_badge_outline", ThemeGroup.LISTS, R.string.theme_badge_outline,
        subgroupLabelRes = R.string.theme_group_visibility_badges,
    ),

    // Contacts'-list row fields — one styling slot per RowField (font / weight / size / color), shared by
    // all three contact lists. Rendered specially in ThemeActivity (only enabled fields show a control).
    ROW_DISPLAY_NAME("row_display_name", ThemeGroup.ROWS, R.string.field_display_name, hasFont = true),
    ROW_SURNAME_FIRST("row_surname_first", ThemeGroup.ROWS, R.string.field_surname_first, hasFont = true),
    ROW_FIRST_SURNAME("row_first_surname", ThemeGroup.ROWS, R.string.field_first_surname, hasFont = true),
    ROW_FIRST_SURNAME_CAPS(
        "row_first_surname_caps", ThemeGroup.ROWS, R.string.field_first_surname_caps, hasFont = true,
    ),
    ROW_SURNAME_CAPS_FIRST(
        "row_surname_caps_first", ThemeGroup.ROWS, R.string.field_surname_caps_first, hasFont = true,
    ),
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

    // Color of the optional horizontal divider between rows / vertical divider between columns (thickness via sliders).
    CONTACT_DIVIDER("contacts_list_divider", ThemeGroup.ROWS, R.string.theme_slot_divider),
    CONTACT_VDIVIDER("contacts_list_vdivider", ThemeGroup.ROWS, R.string.theme_slot_vdivider),

    // Letter sections on the Contacts tab: the big header letter, its underline, and the framing divider.
    SECTION_HEADER("contacts_section_header", ThemeGroup.SECTIONS, R.string.theme_sections_header, hasFont = true),
    SECTION_UNDERLINE("contacts_section_underline", ThemeGroup.SECTIONS, R.string.theme_sections_underline),
    SECTION_DIVIDER("contacts_section_divider", ThemeGroup.SECTIONS, R.string.theme_sections_divider),

    // The separator text shown between two fields that share a line (a moved-right column).
    COLUMN_SPACER("contacts_list_column_spacer", ThemeGroup.ROWS, R.string.theme_rows_spacer, hasFont = true),

    // The 詳 detail-mode info lines (last call / last message) appended under a contact's fields.
    DETAIL_CALL("detail_call_line", ThemeGroup.ROWS, R.string.theme_detail_call, hasFont = true),
    DETAIL_SMS("detail_sms_line", ThemeGroup.ROWS, R.string.theme_detail_sms, hasFont = true),
    // …and the arrow of a call on such a line, by its direction.
    DETAIL_INCOMING("detail_incoming", ThemeGroup.ROWS, R.string.theme_detail_incoming),
    DETAIL_OUTGOING("detail_outgoing", ThemeGroup.ROWS, R.string.theme_detail_outgoing),
    DETAIL_MISSED("detail_missed", ThemeGroup.ROWS, R.string.theme_detail_missed),
}

// How far from the background toward the text a muted (secondary) text sits — opaque, never faded.
private const val SECONDARY_TEXT_BLEND = 0.6f

// Colours that carry meaning keep their settled defaults; each is the default of a role, nothing more.
// The house dim gold for settings explanations — a colour of its own, never a greyed-out text colour.
private const val SETTINGS_NOTE_DIM_GOLD = 0xFFC8C800.toInt()

// denwa's own SIM fills, so a swiped row and a route badge are the same colour in both apps.
private const val SIM1_RED = 0xFFFF0000.toInt()
private const val SIM2_BLUE = 0xFF0000FF.toInt()
// The SIM 2 card on a list photo: a lighter blue, since the card is a small glyph on a photo.
private const val SIM2_CARD_BLUE = 0xFF2962FF.toInt()
// The family palette for colours that tell things apart (白い熊, 2026-11-23): no violet and no magenta, a
// heavy green, two blues far apart. Menu icons, the UI page's section icons and the contact card's tiles all
// take their defaults from it, so one colour means one family of things.
private const val HEAVY_GREEN = 0xFF00A550.toInt()
private const val HEAVY_BLUE = 0xFF0066FF.toInt()
private const val LIGHT_BLUE = 0xFF00B0FF.toInt()
private const val ORANGE = 0xFFFF8000.toInt()
private const val ROSE = 0xFFFF3D7F.toInt()
private const val MENU_RED = 0xFFFF0000.toInt()
// A call's direction on a detail line: incoming blue, outgoing green, missed red (soft, they sit in text).
private const val CALL_INCOMING_BLUE = 0xFF64B5F6.toInt()
private const val CALL_OUTGOING_GREEN = 0xFF81C784.toInt()
private const val CALL_MISSED_RED = 0xFFE57373.toInt()
private const val PHOTO_SHADE_ALPHA = 0xCC
private const val PHOTO_SCRIM_ALPHA = 0xBB

/** The effective color for a slot: the user's override if set, otherwise its inherited default. */
fun Context.themeColor(slot: ThemeSlot): Int {
    slot.role?.let { return uiColor(it) }
    val override = config.getThemeOverride(slot.key)
    return if (override != THEME_UNSET) override else themeDefault(slot)
}

// One arm per slot — high cyclomatic count is inherent to an exhaustive enum map, not real complexity.
@Suppress("CyclomaticComplexMethod")
private fun Context.themeDefault(slot: ThemeSlot): Int = when (slot) {
    // A slot that is a commons role never gets here (themeColor returns the role); the arms keep the map total.
    ThemeSlot.BACKGROUND, ThemeSlot.PRIMARY, ThemeSlot.TEXT, ThemeSlot.SWITCH_OFF,
    ThemeSlot.SEARCH_FILL, ThemeSlot.SEARCH_TEXT, ThemeSlot.SEARCH_HINT, ThemeSlot.SEARCH_ICON,
    ThemeSlot.SEARCH_MENU_TEXT, ThemeSlot.SEARCH_BORDER,
    ThemeSlot.TAB_BACKGROUND, ThemeSlot.TAB_SELECTED, ThemeSlot.TAB_UNSELECTED,
    ThemeSlot.TOPBAR_TITLE, ThemeSlot.TOPBAR_NAV -> uiColor(slot.role!!)

    ThemeSlot.TEXT_SECONDARY ->
        ColorUtils.blendARGB(themeColor(ThemeSlot.BACKGROUND), themeColor(ThemeSlot.TEXT), SECONDARY_TEXT_BLEND)
    ThemeSlot.SETTINGS_NOTE -> SETTINGS_NOTE_DIM_GOLD
    // denwa's defaults, so a swipe looks the same in both apps: SIM 1 red, SIM 2 blue, accent marking
    ThemeSlot.SWIPE_SIM1 -> SIM1_RED
    ThemeSlot.SWIPE_SIM2 -> SIM2_BLUE
    ThemeSlot.SWIPE_TEXT -> themeColor(ThemeSlot.PRIMARY)
    ThemeSlot.SWIPE_RIN -> themeColor(ThemeSlot.ROUTE_RIN)
    ThemeSlot.SWIPE_RIN_TEXT -> themeColor(ThemeSlot.ROUTE_RIN_TEXT)
    ThemeSlot.ROUTE_SIM1 -> SIM1_RED
    ThemeSlot.ROUTE_SIM2 -> SIM2_BLUE
    ThemeSlot.ROUTE_SIM_TEXT -> themeColor(ThemeSlot.PRIMARY)
    // The 臨 mark: accent ground, blue text — the reverse of nothing else on screen, so it reads as its own route.
    ThemeSlot.ROUTE_RIN -> themeColor(ThemeSlot.PRIMARY)
    ThemeSlot.ROUTE_RIN_TEXT -> SIM2_BLUE
    ThemeSlot.PHOTO_BADGE_SIM1 -> SIM1_RED
    ThemeSlot.PHOTO_BADGE_SIM2 -> SIM2_CARD_BLUE
    ThemeSlot.PHOTO_BADGE_TEXT -> themeColor(ThemeSlot.PRIMARY)
    ThemeSlot.PHOTO_BADGE_HALO -> themeColor(ThemeSlot.BACKGROUND)
    ThemeSlot.DETAIL_INCOMING -> CALL_INCOMING_BLUE
    ThemeSlot.DETAIL_OUTGOING -> CALL_OUTGOING_GREEN
    ThemeSlot.DETAIL_MISSED -> CALL_MISSED_RED
    ThemeSlot.MENU_ICON_CALL -> HEAVY_GREEN
    ThemeSlot.MENU_ICON_MESSAGE -> HEAVY_BLUE
    ThemeSlot.MENU_ICON_CONTACT -> LIGHT_BLUE
    ThemeSlot.MENU_ICON_SHARE -> ORANGE
    ThemeSlot.MENU_ICON_VIEW -> themeColor(ThemeSlot.PRIMARY)
    ThemeSlot.MENU_ICON_BLOCK -> ROSE
    ThemeSlot.MENU_WARNING -> MENU_RED
    ThemeSlot.SECTION_ICON_BASE -> themeColor(ThemeSlot.PRIMARY)
    ThemeSlot.SECTION_ICON_CONTROLS -> HEAVY_GREEN
    ThemeSlot.SECTION_ICON_WORDS -> LIGHT_BLUE
    ThemeSlot.SECTION_ICON_FRAME -> HEAVY_BLUE
    ThemeSlot.SECTION_ICON_REACH -> ORANGE
    ThemeSlot.SECTION_ICON_WARNING -> MENU_RED
    ThemeSlot.SECTION_ICON_FLASH -> ROSE

    ThemeSlot.CARD_NAME, ThemeSlot.CARD_CALLS -> themeColor(ThemeSlot.PRIMARY)
    ThemeSlot.CARD_PHONE -> HEAVY_GREEN
    ThemeSlot.CARD_MESSAGE -> LIGHT_BLUE
    ThemeSlot.CARD_WEB -> HEAVY_BLUE
    ThemeSlot.CARD_ORG -> ORANGE
    ThemeSlot.CARD_NOTE -> ROSE
    ThemeSlot.CARD_STORAGE -> SETTINGS_NOTE_DIM_GOLD
    ThemeSlot.CARD_VALUE -> themeColor(ThemeSlot.TEXT)
    ThemeSlot.CARD_LABEL -> themeColor(ThemeSlot.TEXT_SECONDARY)

    ThemeSlot.SEARCH_ACTION_ICON -> themeColor(ThemeSlot.PRIMARY)
    ThemeSlot.COLUMN_BUTTONS -> themeColor(ThemeSlot.PRIMARY)

    // Icons over the contact photo: the accent, haloed in the background color so they stay legible
    // over a photo painted in that same accent (the default placeholder).
    ThemeSlot.PHOTO_TOOLBAR_ICON, ThemeSlot.PHOTO_ACTION_ICON -> themeColor(ThemeSlot.PRIMARY)
    ThemeSlot.PHOTO_ICON_OUTLINE -> themeColor(ThemeSlot.BACKGROUND)
    ThemeSlot.PHOTO_SHADE -> ColorUtils.setAlphaComponent(themeColor(ThemeSlot.BACKGROUND), PHOTO_SHADE_ALPHA)
    ThemeSlot.PHOTO_SCRIM -> ColorUtils.setAlphaComponent(themeColor(ThemeSlot.BACKGROUND), PHOTO_SCRIM_ALPHA)

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

    ThemeSlot.BADGE_SYSTEM, ThemeSlot.BADGE_VISIBLE -> themeColor(ThemeSlot.PRIMARY)
    ThemeSlot.BADGE_OUTLINE -> themeColor(ThemeSlot.BACKGROUND)

    // Contacts'-list row fields: name-like fields read as primary text, the rest as muted secondary text.
    ThemeSlot.ROW_DISPLAY_NAME, ThemeSlot.ROW_SURNAME_FIRST, ThemeSlot.ROW_FIRST_SURNAME,
    ThemeSlot.ROW_FIRST_SURNAME_CAPS, ThemeSlot.ROW_SURNAME_CAPS_FIRST, ThemeSlot.ROW_PREFIX,
    ThemeSlot.ROW_FIRST_NAME, ThemeSlot.ROW_MIDDLE_NAME, ThemeSlot.ROW_SURNAME, ThemeSlot.ROW_SUFFIX,
    ThemeSlot.ROW_NICKNAME ->
        themeColor(ThemeSlot.TEXT)

    ThemeSlot.ROW_PHONE, ThemeSlot.ROW_EMAIL, ThemeSlot.ROW_ADDRESS, ThemeSlot.ROW_ADDRESS_STREET,
    ThemeSlot.ROW_ADDRESS_CITY, ThemeSlot.ROW_ADDRESS_REGION, ThemeSlot.ROW_ADDRESS_POSTCODE,
    ThemeSlot.ROW_ADDRESS_COUNTRY, ThemeSlot.ROW_COMPANY, ThemeSlot.ROW_POSITION, ThemeSlot.ROW_WEBSITE,
    ThemeSlot.ROW_IM, ThemeSlot.ROW_BIRTHDAY, ThemeSlot.ROW_ANNIVERSARY, ThemeSlot.ROW_NOTE,
    ThemeSlot.ROW_GROUPS, ThemeSlot.ROW_CONTACT_SOURCE -> themeColor(ThemeSlot.TEXT_SECONDARY)

    ThemeSlot.CONTACT_DIVIDER -> themeColor(ThemeSlot.TEXT_SECONDARY)
    ThemeSlot.CONTACT_VDIVIDER -> themeColor(ThemeSlot.TEXT_SECONDARY)
    ThemeSlot.COLUMN_SPACER -> themeColor(ThemeSlot.TEXT_SECONDARY)
    ThemeSlot.DETAIL_CALL, ThemeSlot.DETAIL_SMS -> themeColor(ThemeSlot.TEXT_SECONDARY)

    // Letter sections read as accents (yellow on the default palette), like denwa's date headers.
    ThemeSlot.SECTION_HEADER, ThemeSlot.SECTION_UNDERLINE, ThemeSlot.SECTION_DIVIDER ->
        themeColor(ThemeSlot.PRIMARY)
}

/** Set an explicit override for a slot. A slot that is a commons role is stored as that role. */
fun Context.setThemeColor(slot: ThemeSlot, color: Int) {
    val role = slot.role
    if (role == null) {
        config.setThemeOverride(slot.key, color)
        return
    }
    if (role.isFoundation) config.isSystemThemeEnabled = false
    setUiColor(role, color)
}

/** Put a slot back on its default. */
fun Context.resetThemeColor(slot: ThemeSlot) {
    slot.role?.let { resetUiColor(it) } ?: config.clearThemeOverride(slot.key)
}

/** True when the slot has a colour of its own rather than its default. */
fun Context.isThemeColorSet(slot: ThemeSlot): Boolean =
    slot.role?.let { isUiColorSet(it) } ?: (config.getThemeOverride(slot.key) != THEME_UNSET)

/** Every colour role back on its default: this app's slots and the commons roles alike. */
fun Context.resetAllThemeColors() {
    ThemeSlot.entries.forEach { config.clearThemeOverride(it.key) }
    resetAllUiColors()
}

/** One-time switch away from the stock (system / dynamic) theme; the colours themselves are role defaults. */
fun Context.seedBlackYellowThemeIfNeeded() {
    if (config.themeV1Seeded) {
        return
    }

    config.isSystemThemeEnabled = false
    config.themeV1Seeded = true
}

// Fork: dialog accent frame width, in dp.
private const val DIALOG_FRAME_WIDTH_DP = 3

// Fork: dialogs are black-on-black and invisible without a frame, so commons' dialog frame is switched on
// (its colour is the role UiRole.DIALOG_FRAME). Set on every launch so existing installs pick it up too.
fun Context.seedDialogFrame() {
    config.dialogBorderWidth = DIALOG_FRAME_WIDTH_DP
    // Dialog buttons as pills (commons sk12), switched on once; the switch in UI settings → Buttons wins after.
    if (!config.pillButtonsSeeded) {
        config.pillDialogButtons = true
        config.pillButtonsSeeded = true
    }
}

/**
 * One-time move (+133, commons sk18) of the overrides of slots that became commons roles into the roles'
 * own keys, so a colour set before the rebuild is kept. The warning colour used to be mirrored into
 * commons' destructive dialog button; that is a role of its own now and starts from the same value.
 */
fun Context.migrateSlotsToRolesIfNeeded() {
    if (config.slotsMovedToRoles) {
        return
    }

    ThemeSlot.entries.forEach { slot ->
        val role = slot.role ?: return@forEach
        if (role.isFoundation) return@forEach
        val override = config.getThemeOverride(slot.key)
        if (override != THEME_UNSET) {
            setUiColor(role, override)
            config.clearThemeOverride(slot.key)
        }
    }
    val warning = config.getThemeOverride(ThemeSlot.MENU_WARNING.key)
    if (warning != THEME_UNSET && !isUiColorSet(UiRole.BUTTON_DESTRUCTIVE)) {
        setUiColor(UiRole.BUTTON_DESTRUCTIVE, warning)
    }
    config.slotsMovedToRoles = true
}

// The material yellow the palette used before PALETTE_YELLOW became pure yellow (#FFFF00).
private const val OLD_MATERIAL_YELLOW_RGB = 0xFFEB3B
private const val RGB_MASK = 0xFFFFFF

/** [color] with a material-yellow RGB part rewritten to pure yellow; the alpha byte is preserved. */
private fun materialToPureYellow(color: Int): Int = if (color and RGB_MASK == OLD_MATERIAL_YELLOW_RGB) {
    (color and RGB_MASK.inv()) or (FAMILY_YELLOW and RGB_MASK)
} else {
    color
}

/** One-time rewrite of every persisted material-yellow color to pure yellow (the palette change). */
fun Context.migrateToPureYellowIfNeeded() {
    if (config.pureYellowMigrated) {
        return
    }

    // The stock commons colors the foundation slots write through to
    config.backgroundColor = materialToPureYellow(config.backgroundColor)
    config.textColor = materialToPureYellow(config.textColor)
    config.primaryColor = materialToPureYellow(config.primaryColor)
    config.accentColor = materialToPureYellow(config.accentColor)

    // Per-slot overrides; unset slots follow their inherited defaults, so there is nothing to rewrite
    for (slot in ThemeSlot.entries) {
        val override = config.getThemeOverride(slot.key)
        if (override != THEME_UNSET) {
            config.setThemeOverride(slot.key, materialToPureYellow(override))
        }
    }

    config.pureYellowMigrated = true
}

private const val SIM_BADGE_HALO_RADIUS = 4f

/** The default-SIM badge (layout/sim_badge): the card in its SIM's role, the digit and its halo in theirs. */
fun android.view.View.styleSimBadge(slot: Int) {
    val card = context.themeColor(if (slot == 1) ThemeSlot.PHOTO_BADGE_SIM1 else ThemeSlot.PHOTO_BADGE_SIM2)
    findViewById<android.widget.ImageView>(R.id.sim_badge_icon).setColorFilter(card, android.graphics.PorterDuff.Mode.SRC_IN)
    findViewById<android.widget.TextView>(R.id.sim_badge_number).apply {
        text = slot.toString()
        setTextColor(context.themeColor(ThemeSlot.PHOTO_BADGE_TEXT))
        setShadowLayer(SIM_BADGE_HALO_RADIUS, 0f, 0f, context.themeColor(ThemeSlot.PHOTO_BADGE_HALO))
    }
}
