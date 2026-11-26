package org.fossify.contacts

import org.fossify.commons.FossifyApp
import org.fossify.contacts.extensions.applyAppLanguage
import org.fossify.contacts.extensions.migrateSlotsToRolesIfNeeded
import org.fossify.contacts.extensions.reassertAppLanguage
import org.fossify.contacts.extensions.migrateToPureYellowIfNeeded
import org.fossify.contacts.extensions.seedBlackYellowThemeIfNeeded
import org.fossify.contacts.extensions.seedDialogFrame
import org.fossify.contacts.helpers.applySurnameFirstDefaultIfNeeded

class App : FossifyApp() {
    override fun onCreate() {
        super.onCreate()
        // The language chosen on the UI page, before any activity draws: EMUI forgets the per-app locale
        // across a sideload update.
        reassertAppLanguage()
        // Apply the default black/yellow look once, before any activity themes itself.
        seedBlackYellowThemeIfNeeded()
        // Rewrite any persisted material-yellow color to pure yellow once (palette change).
        migrateToPureYellowIfNeeded()
        // Colours set on slots that became commons roles (sk18) move into the roles' keys, once.
        migrateSlotsToRolesIfNeeded()
        // Give dialogs a yellow accent frame so they read against the black background.
        seedDialogFrame()
        // Switch saved list layouts to the "Lastname, Firstname" name field once.
        applySurnameFirstDefaultIfNeeded()
        // 臨電話 reads the private contacts since +123: admit it where the list was stored before that, once.
        org.fossify.contacts.helpers.Config.newInstance(this).addRindenwaToPrivateDataAppsOnce()
        // Readers of the private-contacts provider hear of every change (helpers/PrivateContactsWatch).
        org.fossify.contacts.helpers.PrivateContactsWatch.ensure(this)
        // Fold the 1.6.0+097/+098 side file of private-contact extras into the contacts (commons sk11).
        org.fossify.commons.helpers.ensureBackgroundThread { org.fossify.contacts.helpers.PrivateExtras.migrateIntoContacts(this) }
        // A temporary visible copy whose alarm EMUI held back is removed now (helpers/TemporaryVisibility).
        org.fossify.commons.helpers.ensureBackgroundThread { org.fossify.contacts.helpers.TemporaryVisibility.sweep(this) }
    }

    // The application's own resources are reset by every configuration change (a fold, a rotation).
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        applyAppLanguage()
    }
}
