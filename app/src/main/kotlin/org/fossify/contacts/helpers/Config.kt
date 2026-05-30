package org.fossify.contacts.helpers

import android.content.Context
import org.fossify.commons.helpers.BaseConfig
import org.fossify.commons.helpers.SHOW_TABS

class Config(context: Context) : BaseConfig(context) {
    companion object {
        fun newInstance(context: Context) = Config(context)
    }

    var showTabs: Int
        get() = prefs.getInt(SHOW_TABS, ALL_TABS_MASK)
        set(showTabs) = prefs.edit().putInt(SHOW_TABS, showTabs).apply()

    var autoBackupContactSources: Set<String>
        get() = prefs.getStringSet(AUTO_BACKUP_CONTACT_SOURCES, setOf())!!
        set(autoBackupContactSources) = prefs.edit().remove(AUTO_BACKUP_CONTACT_SOURCES).putStringSet(AUTO_BACKUP_CONTACT_SOURCES, autoBackupContactSources)
            .apply()

    // Granular theming: one Int override per color slot, THEME_UNSET means "follow the default".
    var themeV1Seeded: Boolean
        get() = prefs.getBoolean(THEME_V1_SEEDED, false)
        set(value) = prefs.edit().putBoolean(THEME_V1_SEEDED, value).apply()

    fun getThemeOverride(key: String): Int = prefs.getInt(key, THEME_UNSET)

    fun setThemeOverride(key: String, color: Int) = prefs.edit().putInt(key, color).apply()

    fun clearThemeOverride(key: String) = prefs.edit().remove(key).apply()
}
