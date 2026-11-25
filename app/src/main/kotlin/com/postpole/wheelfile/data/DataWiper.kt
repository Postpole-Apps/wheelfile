package com.postpole.wheelfile.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.postpole.wheelfile.billing.EntitlementKeys
import com.postpole.wheelfile.data.attachments.AttachmentStore
import com.postpole.wheelfile.data.db.AttachmentDao
import com.postpole.wheelfile.data.db.CustomCategoryDao
import com.postpole.wheelfile.data.db.ReminderDao
import com.postpole.wheelfile.data.db.VehicleDao
import com.postpole.wheelfile.reminders.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.core.app.NotificationManagerCompat
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Settings › Delete all data (spec §6.1). Wipes the database in one transaction, then the attachment files, the
 * scheduled reminder work and posted notifications, and the DataStore. Only the cached purchase flags survive: they
 * belong to the Google account and are re-checked with Play anyway.
 */
class DataWiper(
    private val transactor: Transactor,
    private val vehicles: VehicleDao,
    private val reminders: ReminderDao,
    private val attachments: AttachmentDao,
    private val categories: CustomCategoryDao,
    private val store: AttachmentStore,
    private val dataStore: DataStore<Preferences>,
    private val cancelReminders: () -> Unit,
) {
    @Singleton
    class Factory @Inject constructor(
        @ApplicationContext private val context: Context,
        private val transactor: Transactor,
        private val vehicles: VehicleDao,
        private val reminders: ReminderDao,
        private val attachments: AttachmentDao,
        private val categories: CustomCategoryDao,
        private val store: AttachmentStore,
        private val dataStore: DataStore<Preferences>,
    ) {
        fun create() = DataWiper(transactor, vehicles, reminders, attachments, categories, store, dataStore) {
            val scheduler = ReminderScheduler(context)
            scheduler.cancelAll()
            NotificationManagerCompat.from(context).cancelAll()
            scheduler.scheduleDaily() // the daily check keeps running for whatever the user adds next
        }
    }

    suspend fun wipe() {
        transactor.run {
            vehicles.deleteAll() // cascades to entries, documents and reminders
            reminders.getAll().forEach { reminders.delete(it) }
            attachments.deleteAll()
            categories.deleteAll()
        }
        // Files and settings are not transactional; they go after the rows so a failure can only leave unused files.
        store.deleteAllFiles()
        cancelReminders()
        dataStore.edit { prefs ->
            val keep = EntitlementKeys.ALL.mapNotNull { key -> prefs.asMap()[key]?.let { key to it } }
            prefs.clear()
            @Suppress("UNCHECKED_CAST")
            keep.forEach { (key, value) -> prefs[key as Preferences.Key<Any>] = value }
        }
    }
}
