package org.fossify.contacts.helpers

import android.content.Context
import android.net.Uri
import androidx.room.InvalidationTracker
import org.fossify.commons.databases.ContactsDatabase
import org.fossify.commons.helpers.ensureBackgroundThread

/**
 * Tells the readers of our private-contacts provider that the contacts changed:
 * `notifyChange(content://<our id>.contactsprovider/contacts)` after any write to the private contacts
 * table — a save, a delete, a move, a new photo, a star. 臨電話 keeps a ContentObserver on exactly that
 * URI and refreshes its names at once; readers without one simply reload when they next look.
 *
 * It hangs on Room's own invalidation tracker rather than on each place that writes, so no write path can
 * be forgotten. The database singleton is thrown away when MainActivity goes (ContactsDatabase.destroyInstance),
 * so [ensure] is called wherever a fresh instance may have appeared — app start, every activity, the provider —
 * and attaches to whichever instance is current.
 */
object PrivateContactsWatch {
    private var watched: ContactsDatabase? = null

    fun ensure(context: Context) {
        val appContext = context.applicationContext
        ensureBackgroundThread { attach(appContext) }
    }

    @Synchronized
    private fun attach(appContext: Context) {
        try {
            val db = ContactsDatabase.getInstance(appContext)
            if (db === watched) return
            val uri = Uri.parse("content://${appContext.packageName}.contactsprovider/contacts")
            db.invalidationTracker.addObserver(object : InvalidationTracker.Observer("contacts") {
                override fun onInvalidated(tables: Set<String>) {
                    appContext.contentResolver.notifyChange(uri, null)
                }
            })
            watched = db
        } catch (ignored: Exception) {
        }
    }
}
