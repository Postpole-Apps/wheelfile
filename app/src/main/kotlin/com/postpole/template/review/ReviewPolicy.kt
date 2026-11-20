package com.postpole.template.review

import android.app.Activity
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Decides when it is a good moment to ask for a Play review: after N positive actions, rate limited. */
interface ReviewPrompter {
    /** Records a positive action (e.g. item saved). Returns true if a review prompt should be shown now. */
    suspend fun recordPositiveAction(): Boolean
}

@Singleton
class ReviewPolicy(
    private val dataStore: DataStore<Preferences>,
    private val requiredActions: Int,
    private val minIntervalMs: Long,
    private val clock: () -> Long,
) : ReviewPrompter {

    @Inject
    constructor(dataStore: DataStore<Preferences>) : this(
        dataStore,
        requiredActions = DEFAULT_REQUIRED_ACTIONS,
        minIntervalMs = TimeUnit.DAYS.toMillis(DEFAULT_MIN_INTERVAL_DAYS),
        clock = System::currentTimeMillis,
    )

    override suspend fun recordPositiveAction(): Boolean {
        var show = false
        dataStore.edit { prefs ->
            val count = (prefs[ACTIONS] ?: 0) + 1
            val last = prefs[LAST_PROMPT]
            val now = clock()
            if (count >= requiredActions && (last == null || now - last >= minIntervalMs)) {
                show = true
                prefs[ACTIONS] = 0
                prefs[LAST_PROMPT] = now
            } else {
                prefs[ACTIONS] = count
            }
        }
        return show
    }

    /** Test/debug helper. */
    internal suspend fun actionCount(): Int = dataStore.data.first()[ACTIONS] ?: 0

    companion object {
        const val DEFAULT_REQUIRED_ACTIONS = 5
        const val DEFAULT_MIN_INTERVAL_DAYS = 60L
        private val ACTIONS = intPreferencesKey("review_positive_actions")
        private val LAST_PROMPT = longPreferencesKey("review_last_prompt")
    }
}

/** Shows the Play In-App Review sheet. Play decides whether it actually appears (quota); failures are ignored. */
suspend fun launchInAppReview(activity: Activity, onError: (Throwable) -> Unit = {}) {
    try {
        val manager = ReviewManagerFactory.create(activity)
        manager.launchReview(activity, manager.requestReview())
    } catch (e: Exception) {
        onError(e)
    }
}
