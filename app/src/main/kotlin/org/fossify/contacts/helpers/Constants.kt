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
const val ALL_CONTACT_FIELDS = SHOW_PREFIX_FIELD or SHOW_FIRST_NAME_FIELD or SHOW_MIDDLE_NAME_FIELD or
    SHOW_SURNAME_FIELD or SHOW_SUFFIX_FIELD or SHOW_NICKNAME_FIELD or SHOW_PHONE_NUMBERS_FIELD or
    SHOW_EMAILS_FIELD or SHOW_ADDRESSES_FIELD or SHOW_IMS_FIELD or SHOW_STRUCTURED_ADDRESSES_FIELD or
    SHOW_EVENTS_FIELD or SHOW_NOTES_FIELD or SHOW_ORGANIZATION_FIELD or SHOW_WEBSITES_FIELD or
    SHOW_GROUPS_FIELD or SHOW_CONTACT_SOURCE_FIELD or SHOW_RINGTONE_FIELD

// Granular theming
const val THEME_V1_SEEDED = "theme_v1_seeded"
const val THEME_UNSET = Int.MIN_VALUE // a slot with this stored value follows its inherited default
const val PALETTE_BLACK = 0xFF000000.toInt()
const val PALETTE_YELLOW = 0xFFFFEB3B.toInt()

// Per-element fonts: one entry per text slot, keyed by the slot key.
const val FONT_FAMILY_PREFIX = "font_family_" // String, "" = system/global default
const val FONT_WEIGHT_PREFIX = "font_weight_" // Int, 0 = default, else 100..900
const val FONT_SIZE_PREFIX = "font_size_"     // Int sp, 0 = default
const val MAX_FONT_SIZE_SP = 40

const val AUTOMATIC_BACKUP_REQUEST_CODE = 10001
const val AUTO_BACKUP_INTERVAL_IN_DAYS = 1

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
