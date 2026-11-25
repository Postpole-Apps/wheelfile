package com.postpole.wheelfile.ads

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * App-level "a reminder fired" signal for the `service_logged` Pro prompt trigger. Separate from
 * `Reminder.lastNotifiedAt` (per-reminder dedupe). Prompt-related app state: never exported in CSV or `.wheelfile`.
 */
interface ReminderFiredStore {
    /** Epoch millis of the latest successfully posted reminder or document-expiry notification, or null if none yet. */
    suspend fun lastFiredAt(): Long?

    suspend fun recordFired(at: Long)
}

class DataStoreReminderFiredStore(private val dataStore: DataStore<Preferences>) : ReminderFiredStore {
    override suspend fun lastFiredAt(): Long? = dataStore.data.first()[LAST_FIRED]

    override suspend fun recordFired(at: Long) {
        dataStore.edit { it[LAST_FIRED] = at }
    }

    private companion object {
        val LAST_FIRED = longPreferencesKey("pro_prompt_reminder_fired_at")
    }
}
