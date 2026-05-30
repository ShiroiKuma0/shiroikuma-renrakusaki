package org.fossify.contacts

import org.fossify.commons.FossifyApp
import org.fossify.contacts.extensions.seedBlackYellowThemeIfNeeded

class App : FossifyApp() {
    override fun onCreate() {
        super.onCreate()
        // Apply the default black/yellow look once, before any activity themes itself.
        seedBlackYellowThemeIfNeeded()
    }
}
