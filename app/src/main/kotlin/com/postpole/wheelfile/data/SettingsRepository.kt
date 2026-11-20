package com.postpole.wheelfile.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.name }
    }

    suspend fun setDefaultCurrency(code: String) {
        dataStore.edit { it[DEFAULT_CURRENCY] = code }
    }

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DEFAULT_CURRENCY = stringPreferencesKey("default_currency")
    }
}
