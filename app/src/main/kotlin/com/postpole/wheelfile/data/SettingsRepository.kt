package com.postpole.wheelfile.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.postpole.wheelfile.domain.reminder.QuietHours
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Currency
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** ISO 4217 code of the currency for [locale], or USD if the locale has no country/currency. */
fun defaultCurrencyFor(locale: Locale): String =
    try {
        Currency.getInstance(locale).currencyCode
    } catch (_: IllegalArgumentException) {
        "USD"
    } catch (_: NullPointerException) {
        "USD"
    }

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        prefs[THEME_MODE]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.SYSTEM
    }

    /** User's choice, or the device-locale currency until they pick one. */
    val defaultCurrency: Flow<String> = dataStore.data.map {
        it[DEFAULT_CURRENCY] ?: defaultCurrencyFor(Locale.getDefault())
    }

    /** True once the first-run welcome and first vehicle flow has been completed. */
    val onboardingDone: Flow<Boolean> = dataStore.data.map { it[ONBOARDING_DONE] ?: false }

    suspend fun setOnboardingDone() {
        dataStore.edit { it[ONBOARDING_DONE] = true }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.name }
    }

    suspend fun setDefaultCurrency(code: String) {
        dataStore.edit { it[DEFAULT_CURRENCY] = code }
    }

    /** Quiet hours for reminder notifications (default 21:00 to 08:00 local). */
    val quietHours: Flow<QuietHours> = dataStore.data.map {
        val d = QuietHours()
        QuietHours(
            enabled = it[QUIET_ENABLED] ?: d.enabled,
            startHour = it[QUIET_START] ?: d.startHour,
            endHour = it[QUIET_END] ?: d.endHour,
        )
    }

    suspend fun setQuietHours(quiet: QuietHours) {
        dataStore.edit {
            it[QUIET_ENABLED] = quiet.enabled
            it[QUIET_START] = quiet.startHour.coerceIn(0, 23)
            it[QUIET_END] = quiet.endHour.coerceIn(0, 23)
        }
    }

    /** True once the in-context notification rationale has been shown (asked once, when the first reminder is saved). */
    val notificationPrompted: Flow<Boolean> = dataStore.data.map { it[NOTIFICATION_PROMPTED] ?: false }

    suspend fun setNotificationPrompted() {
        dataStore.edit { it[NOTIFICATION_PROMPTED] = true }
    }

    private companion object {
        val QUIET_ENABLED = booleanPreferencesKey("quiet_enabled")
        val QUIET_START = intPreferencesKey("quiet_start_hour")
        val QUIET_END = intPreferencesKey("quiet_end_hour")
        val NOTIFICATION_PROMPTED = booleanPreferencesKey("notification_prompted")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DEFAULT_CURRENCY = stringPreferencesKey("default_currency")
    }
}
