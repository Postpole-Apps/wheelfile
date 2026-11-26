package com.postpole.wheelfile.ads

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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

/**
 * The once-ever rule of the `service_logged` trigger: the first service saved after a reminder has fired.
 * Prompt-related app state: never exported in CSV or `.wheelfile`.
 */
interface ServiceLoggedGate {
    /**
     * Call for every service saved at [savedAt]. True exactly once ever: when a reminder fired at or before [savedAt]
     * and no earlier save used it up. A service saved with no prior fire returns false and leaves the gate open.
     */
    suspend fun tryConsume(savedAt: Long): Boolean
}

class DataStoreServiceLoggedGate(private val dataStore: DataStore<Preferences>) : ServiceLoggedGate {
    override suspend fun tryConsume(savedAt: Long): Boolean {
        var consumed = false
        dataStore.edit {
            val fired = it[LAST_FIRED]
            if (fired != null && fired <= savedAt && it[DONE] != true) {
                it[DONE] = true
                consumed = true
            }
        }
        return consumed
    }

    private companion object {
        val LAST_FIRED = longPreferencesKey("pro_prompt_reminder_fired_at")
        val DONE = booleanPreferencesKey("pro_prompt_service_logged_done")
    }
}
