package com.postpole.wheelfile.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.postpole.wheelfile.billing.EntitlementKeys
import com.postpole.wheelfile.data.attachments.AttachmentStore
import com.postpole.wheelfile.data.attachments.SourceOpener
import com.postpole.wheelfile.data.attachments.StageResult
import com.postpole.wheelfile.data.db.CustomCategory
import com.postpole.wheelfile.data.db.DistanceUnit
import com.postpole.wheelfile.data.db.DocumentType
import com.postpole.wheelfile.data.db.EconomyUnit
import com.postpole.wheelfile.data.db.OwnerType
import com.postpole.wheelfile.data.db.Powertrain
import com.postpole.wheelfile.data.db.Reminder
import com.postpole.wheelfile.data.db.ReminderKind
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.data.db.VehicleDocument
import com.postpole.wheelfile.data.db.VehicleType
import com.postpole.wheelfile.data.db.VolumeUnit
import com.postpole.wheelfile.ui.Routes
import com.postpole.wheelfile.ui.StartViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.File

/** Settings › Delete all data (spec §6.1): database, attachments, reminders and DataStore are wiped; onboarding returns. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DataWiperTest {
    @get:Rule val temp = TemporaryFolder()
    private lateinit var db: AppDatabase
    private lateinit var dir: File
    private lateinit var store: AttachmentStore
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var settings: SettingsRepository
    private var remindersCancelled = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        dir = File(temp.newFolder("files"), "attachments")
        store = AttachmentStore(
            dir = dir, dao = db.attachmentDao(), opener = SourceOpener { ByteArrayInputStream("bytes".toByteArray()) },
            mimeOf = { "image/jpeg" }, isPro = { true }, clock = { 1L },
        )
        dataStore = PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) {
            File(temp.root, "settings.preferences_pb")
        }
        settings = SettingsRepository(dataStore)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun wiper() = DataWiper(
        RoomTransactor(db), db.vehicleDao(), db.reminderDao(), db.attachmentDao(), db.customCategoryDao(), store, dataStore,
    ) { remindersCancelled++ }

    private suspend fun fill() {
        val v = db.vehicleDao().insert(
            Vehicle(
                name = "Golf", type = VehicleType.CAR, powertrain = Powertrain.PETROL, distanceUnit = DistanceUnit.KM,
                volumeUnit = VolumeUnit.L, economyDisplay = EconomyUnit.L_PER_100KM, currency = "EUR", createdAt = 1, updatedAt = 1,
            ),
        )
        val doc = db.vehicleDocumentDao().insert(VehicleDocument(vehicleId = v, type = DocumentType.INSURANCE, title = "Policy"))
        db.reminderDao().insert(Reminder(vehicleId = v, title = "Oil", kind = ReminderKind.DATE, dueDate = 1, linkedDocumentId = doc))
        db.customCategoryDao().insert(CustomCategory(name = "Parking", kind = "EXPENSE"))
        repeat(3) { check(store.add("src", OwnerType.DOCUMENT, doc) is StageResult.Staged) }
        settings.setOnboardingDone()
        settings.setDefaultCurrency("EUR")
        settings.setNotificationPrompted()
        dataStore.edit {
            it[EntitlementKeys.IS_PRO] = true
            it[stringPreferencesKey("anything_else")] = "x"
        }
    }

    @Test fun wipesDatabaseAttachmentsRemindersAndSettings() = runBlocking {
        fill()
        assertEquals(3, dir.list()!!.size)

        wiper().wipe()

        assertTrue(db.vehicleDao().getAll().isEmpty())
        assertTrue(db.vehicleDocumentDao().getAll().isEmpty())
        assertTrue(db.reminderDao().getAll().isEmpty())
        assertEquals(0, db.attachmentDao().count())
        assertTrue(db.customCategoryDao().getAll().isEmpty())
        assertTrue("attachment files are gone", dir.list().orEmpty().isEmpty())
        assertEquals("scheduled reminder work is cancelled", 1, remindersCancelled)
        val prefs = dataStore.data.first()
        assertFalse(settings.onboardingDone.first())
        assertFalse(prefs.contains(stringPreferencesKey("default_currency")))
        assertFalse(prefs.contains(booleanPreferencesKey("notification_prompted")))
        assertFalse(prefs.contains(stringPreferencesKey("anything_else")))
    }

    @Test fun keepsTheCachedPurchaseFlagsBecausePlayOwnsThem() = runBlocking {
        fill()
        wiper().wipe()
        assertEquals(true, dataStore.data.first()[EntitlementKeys.IS_PRO])
    }

    @Test fun afterTheWipeTheAppStartsOnTheWelcomeScreen() = runBlocking {
        fill()
        assertEquals(Routes.GARAGE, StartViewModel(settings, db.vehicleDao()).startRoute.first { it != null })

        wiper().wipe()

        assertEquals(Routes.WELCOME, StartViewModel(settings, db.vehicleDao()).startRoute.first { it != null })
    }

    @Test fun aFailedWipeLeavesTheDatabaseIntact() = runBlocking {
        fill()
        val failing = DataWiper(
            object : Transactor {
                override suspend fun <T> run(block: suspend () -> T): T = RoomTransactor(db).run { block(); error("boom") }
            },
            db.vehicleDao(), db.reminderDao(), db.attachmentDao(), db.customCategoryDao(), store, dataStore,
        ) { remindersCancelled++ }

        assertTrue(runCatching { failing.wipe() }.isFailure)

        assertEquals(1, db.vehicleDao().getAll().size)
        assertEquals(3, db.attachmentDao().count())
        assertEquals("files and settings are untouched when the rows could not be removed", 3, dir.list()!!.size)
        assertTrue(settings.onboardingDone.first())
        assertEquals(0, remindersCancelled)
    }
}
