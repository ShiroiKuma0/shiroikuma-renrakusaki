package org.fossify.contacts.helpers

import android.content.Context
import org.fossify.commons.helpers.BaseConfig
import org.fossify.commons.helpers.SHOW_TABS
import java.security.MessageDigest
import java.security.SecureRandom

@Suppress("TooManyFunctions") // a SharedPreferences wrapper naturally exposes many small accessors
class Config(context: Context) : BaseConfig(context) {
    companion object {
        fun newInstance(context: Context) = Config(context)
    }

    var showTabs: Int
        get() = prefs.getInt(SHOW_TABS, ALL_TABS_MASK)
        set(showTabs) = prefs.edit().putInt(SHOW_TABS, showTabs).apply()

    // When on, viewing a contact shows every saved field, ignoring the "Manage shown contact fields" mask.
    var showAllFieldsWhenViewing: Boolean
        get() = prefs.getBoolean(SHOW_ALL_FIELDS_WHEN_VIEWING, true)
        set(value) = prefs.edit().putBoolean(SHOW_ALL_FIELDS_WHEN_VIEWING, value).apply()

    // Configurable contacts-list rows (see ContactsListConfig). "" = the built-in default layout.
    var contactsListFields: String
        get() = prefs.getString(CONTACTS_LIST_FIELDS, "")!!
        set(value) = prefs.edit().putString(CONTACTS_LIST_FIELDS, value).apply()

    // Revision counter bumped whenever the contacts-list field layout or per-field styling changes.
    var contactsListRevision: Int
        get() = prefs.getInt(CONTACTS_LIST_REVISION, 0)
        set(value) = prefs.edit().putInt(CONTACTS_LIST_REVISION, value).apply()

    // One-time "Lastname, Firstname" default switch performed (see applySurnameFirstDefaultIfNeeded).
    var surnameFirstDefaultApplied: Boolean
        get() = prefs.getBoolean(SURNAME_FIRST_DEFAULT_APPLIED, false)
        set(value) = prefs.edit().putBoolean(SURNAME_FIRST_DEFAULT_APPLIED, value).apply()

    // Vertical gap between contact rows, in dp (0 = rows touch).
    var contactsListSpacing: Int
        get() = prefs.getInt(CONTACTS_LIST_SPACING, DEFAULT_CONTACTS_LIST_SPACING_DP)
        set(value) = prefs.edit().putInt(CONTACTS_LIST_SPACING, value).apply()

    // Horizontal divider line drawn between contact rows, in dp (0 = no divider).
    var contactsListDividerThickness: Int
        get() = prefs.getInt(CONTACTS_LIST_DIVIDER_THICKNESS, 0)
        set(value) = prefs.edit().putInt(CONTACTS_LIST_DIVIDER_THICKNESS, value).apply()

    // Vertical divider line drawn between columns in multi-column mode, in dp (0 = no divider).
    var contactsListVerticalDividerThickness: Int
        get() = prefs.getInt(CONTACTS_LIST_VDIVIDER_THICKNESS, 0)
        set(value) = prefs.edit().putInt(CONTACTS_LIST_VDIVIDER_THICKNESS, value).apply()

    // Separator shown between two fields sharing a line (a moved-right column). Default: a comma.
    var contactsListColumnSpacer: String
        get() = prefs.getString(CONTACTS_LIST_COLUMN_SPACER, DEFAULT_CONTACTS_LIST_COLUMN_SPACER)!!
        set(value) = prefs.edit().putString(CONTACTS_LIST_COLUMN_SPACER, value).apply()

    // Contact thumbnail size in the main Contacts list rows, in dp.
    var contactsListThumbnailSize: Int
        get() = prefs.getInt(CONTACTS_LIST_THUMBNAIL_SIZE, DEFAULT_CONTACTS_LIST_THUMBNAIL_DP)
            .coerceIn(MIN_CONTACTS_LIST_THUMBNAIL_DP, MAX_CONTACTS_LIST_THUMBNAIL_DP)
        set(value) = prefs.edit().putInt(CONTACTS_LIST_THUMBNAIL_SIZE, value).apply()

    // Contacts shown per row in the main Contacts list (1 = list view, 2–4 = grid).
    var contactsListColumns: Int
        get() = prefs.getInt(CONTACTS_LIST_COLUMNS, DEFAULT_CONTACTS_LIST_COLUMNS)
            .coerceIn(1, MAX_CONTACTS_LIST_COLUMNS)
        set(value) = prefs.edit()
            .putInt(CONTACTS_LIST_COLUMNS, value.coerceIn(1, MAX_CONTACTS_LIST_COLUMNS)).apply()

    // 詳 detail mode: one contact per row with last-call / last-SMS lines.
    var contactsListDetailMode: Boolean
        get() = prefs.getBoolean(CONTACTS_LIST_DETAIL_MODE, false)
        set(value) = prefs.edit().putBoolean(CONTACTS_LIST_DETAIL_MODE, value).apply()

    // Timestamp format on the 詳 lines (TIME_FORMAT_*).
    var detailTimeFormat: Int
        get() = prefs.getInt(DETAIL_TIME_FORMAT, TIME_FORMAT_JAPANESE)
        set(value) = prefs.edit().putInt(DETAIL_TIME_FORMAT, value).apply()

    // Editable datetime pattern per age bracket and 24-/12-hour mode ("" = the mode's default).
    fun getDetailPattern(kindPrefix: String, mode: Int): String =
        prefs.getString(kindPrefix + mode, "")!!.ifEmpty { defaultDetailPattern(kindPrefix, mode) }

    fun setDetailPattern(kindPrefix: String, mode: Int, value: String) {
        val editor = prefs.edit()
        if (value.isEmpty() || value == defaultDetailPattern(kindPrefix, mode)) {
            editor.remove(kindPrefix + mode)
        } else {
            editor.putString(kindPrefix + mode, value)
        }
        editor.apply()
    }

    // Letter-section grouping of the main Contacts list (headers + foldable sections).
    var contactsListGrouped: Boolean
        get() = prefs.getBoolean(CONTACTS_LIST_GROUPED, true)
        set(value) = prefs.edit().putBoolean(CONTACTS_LIST_GROUPED, value).apply()

    // Titles of the currently expanded letter sections; everything else renders folded.
    var expandedContactSections: Set<String>
        get() = prefs.getStringSet(EXPANDED_CONTACT_SECTIONS, setOf())!!
        set(value) = prefs.edit().remove(EXPANDED_CONTACT_SECTIONS)
            .putStringSet(EXPANDED_CONTACT_SECTIONS, value).apply()

    // Underline drawn below a section-header letter, in dp (0 = off).
    var contactsSectionUnderlineThickness: Int
        get() = prefs.getInt(CONTACTS_SECTION_UNDERLINE_THICKNESS, DEFAULT_SECTION_UNDERLINE_DP)
        set(value) = prefs.edit().putInt(CONTACTS_SECTION_UNDERLINE_THICKNESS, value).apply()

    // Full-width separator framing unfolded sections (above their header, below their last item), in dp (0 = off).
    var contactsSectionDividerThickness: Int
        get() = prefs.getInt(CONTACTS_SECTION_DIVIDER_THICKNESS, DEFAULT_SECTION_DIVIDER_DP)
        set(value) = prefs.edit().putInt(CONTACTS_SECTION_DIVIDER_THICKNESS, value).apply()

    // Vertical padding above and below a section header's letter row, in dp.
    var contactsSectionPadding: Int
        get() = prefs.getInt(CONTACTS_SECTION_PADDING, DEFAULT_SECTION_PADDING_DP)
        set(value) = prefs.edit().putInt(CONTACTS_SECTION_PADDING, value).apply()

    var autoBackupContactSources: Set<String>
        get() = prefs.getStringSet(AUTO_BACKUP_CONTACT_SOURCES, setOf())!!
        set(autoBackupContactSources) = prefs.edit().remove(AUTO_BACKUP_CONTACT_SOURCES).putStringSet(AUTO_BACKUP_CONTACT_SOURCES, autoBackupContactSources)
            .apply()

    // Granular theming: one Int override per color slot, THEME_UNSET means "follow the default".
    var themeV1Seeded: Boolean
        get() = prefs.getBoolean(THEME_V1_SEEDED, false)
        set(value) = prefs.edit().putBoolean(THEME_V1_SEEDED, value).apply()

    // One-time migration of persisted material-yellow colors to pure yellow (see migrateToPureYellowIfNeeded).
    var pureYellowMigrated: Boolean
        get() = prefs.getBoolean(PURE_YELLOW_MIGRATED, false)
        set(value) = prefs.edit().putBoolean(PURE_YELLOW_MIGRATED, value).apply()

    fun getThemeOverride(key: String): Int = prefs.getInt(key, THEME_UNSET)

    fun setThemeOverride(key: String, color: Int) = prefs.edit().putInt(key, color).apply()

    fun clearThemeOverride(key: String) = prefs.edit().remove(key).apply()

    // Per-element fonts: family (filename, "" = default), weight (0 = default), size (sp, 0 = default).
    fun getFontFamily(slotKey: String): String = prefs.getString(FONT_FAMILY_PREFIX + slotKey, "")!!

    fun setFontFamily(slotKey: String, value: String) =
        prefs.edit().putString(FONT_FAMILY_PREFIX + slotKey, value).apply()

    fun getFontWeight(slotKey: String): Int = prefs.getInt(FONT_WEIGHT_PREFIX + slotKey, 0)

    fun setFontWeight(slotKey: String, value: Int) =
        prefs.edit().putInt(FONT_WEIGHT_PREFIX + slotKey, value).apply()

    fun getFontSize(slotKey: String): Int = prefs.getInt(FONT_SIZE_PREFIX + slotKey, 0)

    fun setFontSize(slotKey: String, value: Int) =
        prefs.edit().putInt(FONT_SIZE_PREFIX + slotKey, value).apply()

    // Per-contact sort-field override (SORT_FIELD_*; DEFAULT clears it). Keyed by the lookup key.
    fun getSortField(key: String): Int = prefs.getInt(SORT_FIELD_PREFIX + key, SORT_FIELD_DEFAULT)

    fun setSortField(key: String, value: Int) {
        val editor = prefs.edit()
        if (value == SORT_FIELD_DEFAULT) {
            editor.remove(SORT_FIELD_PREFIX + key)
        } else {
            editor.putInt(SORT_FIELD_PREFIX + key, value)
        }
        editor.apply()
    }

    // Per-number default SIM slot (1 or 2; slot 0 clears it). Keyed by the phone number.
    fun getSimSlot(number: String): Int = prefs.getInt(SIM_SLOT_PREFIX + number, 0)

    fun setSimSlot(number: String, slot: Int) {
        val editor = prefs.edit()
        if (slot == 0) {
            editor.remove(SIM_SLOT_PREFIX + number)
        } else {
            editor.putInt(SIM_SLOT_PREFIX + number, slot)
        }
        editor.apply()
    }

    // All stored number → slot entries, for the ContentProvider's number matching (PhoneNumberUtils.compare).
    fun getAllSimSlots(): Map<String, Int> = prefs.all
        .filterKeys { it.startsWith(SIM_SLOT_PREFIX) }
        .mapNotNull { (key, value) -> (value as? Int)?.let { key.removePrefix(SIM_SLOT_PREFIX) to it } }
        .toMap()

    // External-automation intent surface (BackupContactsReceiver): a master switch plus a shared secret
    // that every automation broadcast must carry. Same model as the jami fork's AutomationPrefs.
    var automationEnabled: Boolean
        get() = prefs.getBoolean(AUTOMATION_ENABLED, false)
        set(value) = prefs.edit().putBoolean(AUTOMATION_ENABLED, value).apply()

    // The shared secret; generated on first read so the settings row always shows a value.
    val automationToken: String
        get() = prefs.getString(AUTOMATION_TOKEN, null)?.takeIf { it.isNotEmpty() } ?: regenerateAutomationToken()

    fun regenerateAutomationToken(): String {
        val bytes = ByteArray(24).also { SecureRandom().nextBytes(it) }
        val token = bytes.joinToString("") { "%02x".format(it) }
        prefs.edit().putString(AUTOMATION_TOKEN, token).apply()
        return token
    }

    // True when the caller's token matches the stored secret (constant-time). The enabled check is
    // kept separate so callers can report "disabled" and "bad token" as distinct failures.
    fun isAutomationTokenValid(token: String?): Boolean {
        if (token.isNullOrEmpty()) return false
        return MessageDigest.isEqual(token.toByteArray(), automationToken.toByteArray())
    }
}
