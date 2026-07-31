package org.fossify.contacts.helpers

import org.fossify.commons.helpers.SHOW_ADDRESSES_FIELD
import org.fossify.commons.helpers.SHOW_CONTACT_SOURCE_FIELD
import org.fossify.commons.helpers.SHOW_EMAILS_FIELD
import org.fossify.commons.helpers.SHOW_EVENTS_FIELD
import org.fossify.commons.helpers.SHOW_FIRST_NAME_FIELD
import org.fossify.commons.helpers.SHOW_GROUPS_FIELD
import org.fossify.commons.helpers.SHOW_IMS_FIELD
import org.fossify.commons.helpers.SHOW_MIDDLE_NAME_FIELD
import org.fossify.commons.helpers.SHOW_NICKNAME_FIELD
import org.fossify.commons.helpers.SHOW_NOTES_FIELD
import org.fossify.commons.helpers.SHOW_ORGANIZATION_FIELD
import org.fossify.commons.helpers.SHOW_PHONE_NUMBERS_FIELD
import org.fossify.commons.helpers.SHOW_PREFIX_FIELD
import org.fossify.commons.helpers.SHOW_RINGTONE_FIELD
import org.fossify.commons.helpers.SHOW_STRUCTURED_ADDRESSES_FIELD
import org.fossify.commons.helpers.SHOW_SUFFIX_FIELD
import org.fossify.commons.helpers.SHOW_SURNAME_FIELD
import org.fossify.commons.helpers.SHOW_WEBSITES_FIELD
import org.fossify.commons.helpers.TAB_CONTACTS
import org.fossify.commons.helpers.TAB_FAVORITES
import org.fossify.commons.helpers.TAB_GROUPS
import org.joda.time.DateTime

const val GROUP = "group"
const val IS_FROM_SIMPLE_CONTACTS = "is_from_simple_contacts"
const val ADD_NEW_CONTACT_NUMBER = "add_new_contact_number"
const val DEFAULT_FILE_NAME = "contacts.vcf"
const val AVOID_CHANGING_TEXT_TAG = "avoid_changing_text_tag"
const val AVOID_CHANGING_VISIBILITY_TAG = "avoid_changing_visibility_tag"
const val FORMAT_PHONE_NUMBERS = "format_phone_numbers"

// When on, ViewContactActivity ignores the per-field "Manage shown contact fields" mask and
// shows every field a contact actually has. Configurable from Settings.
const val SHOW_ALL_FIELDS_WHEN_VIEWING = "show_all_fields_when_viewing"

// Configurable contacts-list rows: which fields show, in what order, and how they wrap into columns.
// Encoded by ContactsListConfig as "key:checked:sameLine" entries joined by "|".
const val CONTACTS_LIST_FIELDS = "contacts_list_fields"

// Bumped on any contacts-list field/styling edit so MainActivity knows to rebuild the list.
const val CONTACTS_LIST_REVISION = "contacts_list_revision"

// One-time flip of stored layouts from the stock display name to "Lastname, Firstname" done.
const val SURNAME_FIRST_DEFAULT_APPLIED = "surname_first_default_applied"

// Main contacts list: gap between rows (dp), the horizontal divider between rows and the vertical divider
// between columns (dp, 0 = off). All three apply to both the single-column list and the 2–4 column grid.
const val CONTACTS_LIST_SPACING = "contacts_list_spacing"
const val CONTACTS_LIST_DIVIDER_THICKNESS = "contacts_list_divider_thickness"
const val CONTACTS_LIST_VDIVIDER_THICKNESS = "contacts_list_vdivider_thickness"
const val DEFAULT_CONTACTS_LIST_SPACING_DP = 2 // matches the stock tiny_margin row gap
const val MAX_CONTACTS_LIST_SPACING_DP = 40
const val MAX_CONTACTS_LIST_DIVIDER_DP = 12

// Int extra (a commons TAB_* mask) telling MainActivity which tab to open;
// sent by our Phone fork (shiroikuma-denwa) when its Contacts/Favorites tabs hand off to this app.
const val OPEN_TAB_INTENT_EXTRA = "shiroikuma_open_tab"

// Contact thumbnail (photo) size in the main Contacts list rows, in dp.
const val CONTACTS_LIST_THUMBNAIL_SIZE = "contacts_list_thumbnail_size"
const val DEFAULT_CONTACTS_LIST_THUMBNAIL_DP = 40 // matches the stock list_icon_size_medium
const val MIN_CONTACTS_LIST_THUMBNAIL_DP = 16
const val MAX_CONTACTS_LIST_THUMBNAIL_DP = 96

// Number of contacts shown per row in the main Contacts list (1 = list, 2–4 = grid). Set via the toolbar buttons.
const val CONTACTS_LIST_COLUMNS = "contacts_list_columns"
const val DEFAULT_CONTACTS_LIST_COLUMNS = 1
const val MAX_CONTACTS_LIST_COLUMNS = 4

// 詳 detail mode: single-column rows with last-call / last-SMS lines appended. Toggled by the 詳
// toolbar button (which overrides the 一二三四 column choice while on).
const val CONTACTS_LIST_DETAIL_MODE = "contacts_list_detail_mode"

// Timestamp format of the 詳 lines (TIME_FORMAT_* in TimeFormats.kt); default Japanese readings.
const val DETAIL_TIME_FORMAT = "detail_time_format"

// Editable SimpleDateFormat patterns for the 24-/12-hour detail formats, one per age bracket
// (today / earlier this year / older). Keys are suffixed with the mode int, so each mode keeps
// its own set; "" = the mode's built-in default (see defaultDetailPattern).
const val DETAIL_PATTERN_TODAY_PREFIX = "detail_pattern_today_"
const val DETAIL_PATTERN_YEAR_PREFIX = "detail_pattern_year_"
const val DETAIL_PATTERN_OLDER_PREFIX = "detail_pattern_older_"

// Arrow colors on the 詳 lines: incoming blue, outgoing green, missed/rejected red.
const val DETAIL_INCOMING_COLOR = 0xFF64B5F6.toInt()
const val DETAIL_OUTGOING_COLOR = 0xFF81C784.toInt()
const val DETAIL_MISSED_COLOR = 0xFFE57373.toInt()

// Letter sections on the main Contacts list: contacts grouped under big underlined per-letter headers
// (the denwa call-history look), each section foldable by tapping its header. Folded is the default;
// the set of expanded section titles persists. The 1–4 column layout applies within each section.
const val CONTACTS_LIST_GROUPED = "contacts_list_grouped"
const val EXPANDED_CONTACT_SECTIONS = "expanded_contact_sections"
const val CONTACTS_SECTION_UNDERLINE_THICKNESS = "contacts_section_underline_thickness"
const val CONTACTS_SECTION_DIVIDER_THICKNESS = "contacts_section_divider_thickness"
const val CONTACTS_SECTION_PADDING = "contacts_section_padding"
const val DEFAULT_SECTION_UNDERLINE_DP = 4
const val DEFAULT_SECTION_DIVIDER_DP = 0
const val DEFAULT_SECTION_PADDING_DP = 4
const val MAX_SECTION_PADDING_DP = 24

// Text placed between two fields that share a line (a "moved-right" column). Default: a comma.
// NOTE: this must NOT match ThemeSlot.COLUMN_SPACER.key ("contacts_list_column_spacer", which stores the
// spacer's color as an Int) — sharing one key makes getString()/getInt() collide and crash on read.
const val CONTACTS_LIST_COLUMN_SPACER = "contacts_list_spacer_text"
const val DEFAULT_CONTACTS_LIST_COLUMN_SPACER = ","
const val ALL_CONTACT_FIELDS = SHOW_PREFIX_FIELD or SHOW_FIRST_NAME_FIELD or SHOW_MIDDLE_NAME_FIELD or
    SHOW_SURNAME_FIELD or SHOW_SUFFIX_FIELD or SHOW_NICKNAME_FIELD or SHOW_PHONE_NUMBERS_FIELD or
    SHOW_EMAILS_FIELD or SHOW_ADDRESSES_FIELD or SHOW_IMS_FIELD or SHOW_STRUCTURED_ADDRESSES_FIELD or
    SHOW_EVENTS_FIELD or SHOW_NOTES_FIELD or SHOW_ORGANIZATION_FIELD or SHOW_WEBSITES_FIELD or
    SHOW_GROUPS_FIELD or SHOW_CONTACT_SOURCE_FIELD or SHOW_RINGTONE_FIELD

// Granular theming
const val THEME_V1_SEEDED = "theme_v1_seeded"
const val PURE_YELLOW_MIGRATED = "pure_yellow_migrated" // one-time #FFEB3B → #FFFF00 rewrite done
const val THEME_UNSET = Int.MIN_VALUE // a slot with this stored value follows its inherited default
const val PALETTE_BLACK = 0xFF000000.toInt()
const val PALETTE_YELLOW = 0xFFFFFF00.toInt()

// Per-contact sort-field override: one entry per contact (keyed by the provider lookup key, or the
// "contact:<contactId>" fallback), value = which field supplies the sort/bucketing key in the grouped
// Contacts list. Absent/DEFAULT = reading if present, else the name per the global sort setting.
const val SORT_FIELD_PREFIX = "sort_field_"
const val SORT_FIELD_DEFAULT = 0
const val SORT_FIELD_READING = 1
const val SORT_FIELD_NICKNAME = 2
const val SORT_FIELD_ORGANIZATION = 3

// Per-contact default SIM: one entry per phone number (keyed by the number), value = SIM slot (1 or 2).
// Read by our Phone fork (shiroikuma-denwa) via MyContactsContentProvider to pick the SIM for
// outgoing calls, including from Android Auto. Absent/0 = no preference.
const val SIM_SLOT_PREFIX = "sim_slot_"
const val SIM_SLOT_PROVIDER_PATH = "sim_slot" // ContentProvider path denwa queries (number → slot)

// Favorites SIM badge colors: SIM 1 red (bottom-start), SIM 2 blue (bottom-end), number in pure yellow.
const val SIM1_BADGE_COLOR = 0xFFFF0000.toInt()
const val SIM2_BADGE_COLOR = 0xFF2962FF.toInt()

// Per-element fonts: one entry per text slot, keyed by the slot key.
const val FONT_FAMILY_PREFIX = "font_family_" // String, "" = system/global default
const val FONT_WEIGHT_PREFIX = "font_weight_" // Int, 0 = default, else 100..900
const val FONT_SIZE_PREFIX = "font_size_"     // Int sp, 0 = default
const val MAX_FONT_SIZE_SP = 40

const val AUTOMATIC_BACKUP_REQUEST_CODE = 10001
const val AUTO_BACKUP_INTERVAL_IN_DAYS = 1

// On-demand backup over a token-gated broadcast, sent by our automation fork (shiroikuma-jiyusagyoban)
// before risky system operations — EMUI recreates contacts2.db on a system-locale change, which wiped
// every contact on 2026-07-22. The task backs up, then waits for the OK:/ERROR: line to come back
// as a plain reply broadcast to its own exported receiver (reply_action/reply_package/reply_id) —
// the only channel that works on this EMUI: the ordered-broadcast result is severed between
// third-party apps, and any Binder-bearing extra (ResultReceiver, PendingIntent) is dropped or
// never arrives — see receivers/BackupContactsReceiver. The token lives in Settings → Automation.
const val ACTION_BACKUP_CONTACTS = "shiroikuma.renrakusaki.action.BACKUP_CONTACTS"
const val EXTRA_AUTOMATION_TOKEN = "token"
const val EXTRA_BACKUP_PATH = "path"
const val EXTRA_REPLY_ACTION = "reply_action"
const val EXTRA_REPLY_PACKAGE = "reply_package"
const val EXTRA_REPLY_ID = "reply_id"
const val EXTRA_REPLY_RESULT = "result"
const val AUTOMATION_ENABLED = "automation_enabled"
const val AUTOMATION_TOKEN = "automation_token"

// The 保存復元 state-export contract, implemented by receivers/StateExportReceiver: 自由作業盤's
// 保存復元 project backs up every sister app in one run, firing EXPORT_STATE at each app so it writes
// its own category ZIP headlessly (no Activity) and replies with the written path and size, plus
// LIST_CATEGORIES so the picker can offer this app's categories. Same token gate and same plain
// reply-broadcast channel as ACTION_BACKUP_CONTACTS above; "token", "path", "reply_action",
// "reply_package" and "reply_id" are shared with it verbatim.
const val ACTION_EXPORT_STATE = "shiroikuma.renrakusaki.action.EXPORT_STATE"
const val ACTION_LIST_CATEGORIES = "shiroikuma.renrakusaki.action.LIST_CATEGORIES"

// Stop a running EXPORT_STATE from outside (contract addition, 2026-07-28): 保存復元's 中止 button used
// to only stop listening, so a cancelled run carried on and delivered a backup 白い熊 had stopped.
// Token-gated like the others, takes an optional "reply_id" (absent = the export that is running,
// unambiguous because two at once are forbidden), sends no reply of its own, and is a silent no-op
// when nothing is running or the export already finished.
const val ACTION_CANCEL_EXPORT = "shiroikuma.renrakusaki.action.CANCEL_EXPORT"
const val EXTRA_EXPORT_ITEMS = "items"
const val EXTRA_PROGRESS_ACTION = "progress_action"

// Progress-broadcast payload. 白い熊's requirement: real counts, never a percentage — "text" is the
// numbers-first line shown in 自由作業盤's notification, current/total/unit the structured form.
const val EXTRA_PROGRESS_APP = "app"
const val EXTRA_PROGRESS_TEXT = "text"
const val EXTRA_PROGRESS_CURRENT = "current"
const val EXTRA_PROGRESS_TOTAL = "total"
const val EXTRA_PROGRESS_UNIT = "unit"

// At most one progress broadcast per this many ms (the completion one is always sent).
const val PROGRESS_THROTTLE_MS = 500L

const val AUTO_BACKUP_CONTACT_SOURCES = "auto_backup_contact_sources"

// extras used at third party intents
const val KEY_NAME = "name"
const val KEY_EMAIL = "email"
const val KEY_MAILTO = "mailto"

const val LOCATION_CONTACTS_TAB = 0
const val LOCATION_FAVORITES_TAB = 1
const val LOCATION_GROUP_CONTACTS = 2
const val LOCATION_INSERT_OR_EDIT = 3

val tabsList = arrayListOf(
    TAB_CONTACTS,
    TAB_FAVORITES,
    TAB_GROUPS
)
const val ALL_TABS_MASK = TAB_CONTACTS or TAB_FAVORITES or TAB_GROUPS

// phone number/email types
const val CELL = "CELL"
const val WORK = "WORK"
const val HOME = "HOME"
const val OTHER = "OTHER"
const val PREF = "PREF"
const val MAIN = "MAIN"
const val FAX = "FAX"
const val WORK_FAX = "WORK;FAX"
const val HOME_FAX = "HOME;FAX"
const val PAGER = "PAGER"
const val MOBILE = "MOBILE"

// IMs not supported by Ez-vcard
const val HANGOUTS = "Hangouts"
const val QQ = "QQ"
const val JABBER = "Jabber"

const val WHATSAPP = "whatsapp"
const val SIGNAL = "signal"
const val VIBER = "viber"
const val TELEGRAM = "telegram"
const val THREEMA = "threema"


// 6 am is the hardcoded automatic backup time, intervals shorter than 1 day are not yet supported.
fun getNextAutoBackupTime(): DateTime {
    val now = DateTime.now()
    val sixHour = now.withHourOfDay(6)
    return if (now.millis < sixHour.millis) {
        sixHour
    } else {
        sixHour.plusDays(AUTO_BACKUP_INTERVAL_IN_DAYS)
    }
}

fun getPreviousAutoBackupTime(): DateTime {
    val nextBackupTime = getNextAutoBackupTime()
    return nextBackupTime.minusDays(AUTO_BACKUP_INTERVAL_IN_DAYS)
}
