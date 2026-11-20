package com.postpole.template.review

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ReviewPolicyTest {
    @get:Rule val tmp = TemporaryFolder()

    private var now = 1_000L

    private fun policy() = ReviewPolicy(
        PreferenceDataStoreFactory.create { File(tmp.root, "r.preferences_pb") },
        requiredActions = 3,
        minIntervalMs = 100,
        clock = { now },
    )

    @Test fun prompts_onlyAfterRequiredActions() = runBlocking {
        val p = policy()
        assertFalse(p.recordPositiveAction())
        assertFalse(p.recordPositiveAction())
        assertTrue(p.recordPositiveAction())
    }

    @Test fun rateLimited_afterPrompt() = runBlocking {
        val p = policy()
        repeat(2) { p.recordPositiveAction() }
        assertTrue(p.recordPositiveAction())
        repeat(5) { assertFalse(p.recordPositiveAction()) } // enough actions, but too soon
        assertEquals(5, p.actionCount())
        now += 100
        assertTrue(p.recordPositiveAction())
        assertEquals(0, p.actionCount())
    }
}
