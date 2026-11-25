package com.postpole.wheelfile.ads

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * Keeps [ProPromptState] in the app's settings DataStore. It lives only there: prompt state is app state, so it is
 * not part of CSV export or `.wheelfile` backups (those only read the database).
 */
class DataStoreProPromptStateStore(private val dataStore: DataStore<Preferences>) : ProPromptStateStore {
    override suspend fun load(): ProPromptState {
        val p = dataStore.data.first()
        return ProPromptState(
            sessionCount = p[SESSION_COUNT] ?: 0,
            installTimeMs = p[INSTALL_TIME] ?: 0L,
            shownCounts = decodeMap(p[SHOWN_COUNTS]) { it.toIntOrNull() },
            dismissedCounts = decodeMap(p[DISMISSED_COUNTS]) { it.toIntOrNull() },
            lastShownAt = decodeMap(p[LAST_SHOWN]) { it.toLongOrNull() },
            lastDismissedAt = decodeMap(p[LAST_DISMISSED]) { it.toLongOrNull() },
            pending = decodePending(p[PENDING]),
            lastHardGateAt = p[LAST_HARD_GATE],
            interstitialThisSession = p[INTERSTITIAL_THIS_SESSION] ?: false,
            proPromptThisSession = p[PROMPT_THIS_SESSION] ?: false,
            pumpSession = p[PUMP_SESSION] ?: false,
        )
    }

    override suspend fun save(state: ProPromptState) {
        dataStore.edit { p ->
            p[SESSION_COUNT] = state.sessionCount
            p[INSTALL_TIME] = state.installTimeMs
            p[SHOWN_COUNTS] = encodeMap(state.shownCounts)
            p[DISMISSED_COUNTS] = encodeMap(state.dismissedCounts)
            p[LAST_SHOWN] = encodeMap(state.lastShownAt)
            p[LAST_DISMISSED] = encodeMap(state.lastDismissedAt)
            p[PENDING] = encodePending(state.pending)
            state.lastHardGateAt?.let { p[LAST_HARD_GATE] = it } ?: p.remove(LAST_HARD_GATE)
            p[INTERSTITIAL_THIS_SESSION] = state.interstitialThisSession
            p[PROMPT_THIS_SESSION] = state.proPromptThisSession
            p[PUMP_SESSION] = state.pumpSession
        }
    }

    internal companion object {
        private val SESSION_COUNT = intPreferencesKey("pro_prompt_session_count")
        private val INSTALL_TIME = longPreferencesKey("pro_prompt_install_time")
        private val SHOWN_COUNTS = stringPreferencesKey("pro_prompt_shown_counts")
        private val DISMISSED_COUNTS = stringPreferencesKey("pro_prompt_dismissed_counts")
        private val LAST_SHOWN = stringPreferencesKey("pro_prompt_last_shown")
        private val LAST_DISMISSED = stringPreferencesKey("pro_prompt_last_dismissed")
        private val PENDING = stringPreferencesKey("pro_prompt_pending")
        private val LAST_HARD_GATE = longPreferencesKey("pro_prompt_last_hard_gate")
        private val INTERSTITIAL_THIS_SESSION = booleanPreferencesKey("pro_prompt_interstitial_this_session")
        private val PROMPT_THIS_SESSION = booleanPreferencesKey("pro_prompt_shown_this_session")
        private val PUMP_SESSION = booleanPreferencesKey("pro_prompt_pump_session")

        /** `key=value;key=value`. Keys are enum ids, so they never contain `=` or `;`. */
        fun <T> encodeMap(map: Map<String, T>): String = map.entries.joinToString(";") { "${it.key}=${it.value}" }

        fun <T : Any> decodeMap(raw: String?, parse: (String) -> T?): Map<String, T> =
            raw.orEmpty().split(';').mapNotNull { entry ->
                val key = entry.substringBefore('=', "")
                val value = parse(entry.substringAfter('=', ""))
                if (key.isEmpty() || value == null) null else key to value
            }.toMap()

        /** `trigger,surface,qualifiedAt,minSession;...` */
        fun encodePending(map: Map<String, PendingProPrompt>): String =
            map.entries.joinToString(";") { (k, v) -> "$k,${v.surface.id},${v.qualifiedAt},${v.minSession}" }

        fun decodePending(raw: String?): Map<String, PendingProPrompt> =
            raw.orEmpty().split(';').mapNotNull { entry ->
                val f = entry.split(',')
                val surface = ProPromptSurface.entries.firstOrNull { it.id == f.getOrNull(1) }
                val qualifiedAt = f.getOrNull(2)?.toLongOrNull()
                val minSession = f.getOrNull(3)?.toIntOrNull()
                val trigger = ProPromptTrigger.entries.firstOrNull { it.id == f[0] }
                if (trigger == null || surface == null || qualifiedAt == null || minSession == null) {
                    null
                } else {
                    trigger.id to PendingProPrompt(surface, qualifiedAt, minSession)
                }
            }.toMap()
    }
}
