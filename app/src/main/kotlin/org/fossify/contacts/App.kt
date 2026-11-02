package org.fossify.contacts

import org.fossify.commons.FossifyApp
import org.fossify.contacts.extensions.migrateToPureYellowIfNeeded
import org.fossify.contacts.extensions.seedBlackYellowThemeIfNeeded
import org.fossify.contacts.extensions.seedDialogFrame
import org.fossify.contacts.helpers.applySurnameFirstDefaultIfNeeded

class App : FossifyApp() {
    override fun onCreate() {
        super.onCreate()
        // Apply the default black/yellow look once, before any activity themes itself.
        seedBlackYellowThemeIfNeeded()
        // Rewrite any persisted material-yellow color to pure yellow once (palette change).
        migrateToPureYellowIfNeeded()
        // Give dialogs a yellow accent frame so they read against the black background.
        seedDialogFrame()
        // Switch saved list layouts to the "Lastname, Firstname" name field once.
        applySurnameFirstDefaultIfNeeded()
        // Fold the 1.6.0+097/+098 side file of private-contact extras into the contacts (commons sk11).
        org.fossify.commons.helpers.ensureBackgroundThread { org.fossify.contacts.helpers.PrivateExtras.migrateIntoContacts(this) }
    }
}
