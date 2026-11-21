package com.postpole.wheelfile.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.postpole.wheelfile.ads.AdsConfig
import com.postpole.wheelfile.ads.AdsPolicy
import com.postpole.wheelfile.ads.InterstitialDecision
import com.postpole.wheelfile.analytics.NoOpAnalytics
import com.postpole.wheelfile.data.AppDatabase
import com.postpole.wheelfile.data.VehicleRepository
import com.postpole.wheelfile.data.db.DistanceUnit
import com.postpole.wheelfile.data.db.EconomyUnit
import com.postpole.wheelfile.data.db.EnergyLog
import com.postpole.wheelfile.data.db.EnergyStream
import com.postpole.wheelfile.data.db.Powertrain
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.data.db.VehicleType
import com.postpole.wheelfile.data.db.VolumeUnit
import com.postpole.wheelfile.domain.economy.VehicleStatsCalculator
import com.postpole.wheelfile.ui.entry.EntryEvent
import com.postpole.wheelfile.ui.entry.EntryFormViewModel
import com.postpole.wheelfile.ui.vehicle.AddVehicleEvent
import com.postpole.wheelfile.ui.vehicle.AddVehicleStep
import com.postpole.wheelfile.ui.vehicle.AddVehicleViewModel
import com.postpole.wheelfile.ui.vehicle.VehicleDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class VehicleFlowTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: VehicleRepository
    private val install = 1_700_000_000_000L
    private val now = install + 10L * 24 * 60 * 60 * 1000
    private var onboardingDone = false

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = VehicleRepository(db.vehicleDao(), db.energyLogDao(), db.serviceRecordDao(), db.expenseDao())
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun <T> await(flow: Flow<T>, predicate: (T) -> Boolean = { true }): T =
        runBlocking { withTimeout(10_000) { flow.first(predicate) } }

    private fun newVehicle(name: String, archived: Boolean = false, powertrain: Powertrain = Powertrain.PETROL) = Vehicle(
        name = name, type = VehicleType.CAR, powertrain = powertrain, distanceUnit = DistanceUnit.KM,
        volumeUnit = VolumeUnit.L, economyDisplay = EconomyUnit.L_PER_100KM, currency = "EUR",
        archived = archived, createdAt = 1, updatedAt = 1,
    )

    private fun insert(v: Vehicle): Long = runBlocking { db.vehicleDao().insert(v) }

    private fun addVm(isPro: Boolean = false) = AddVehicleViewModel(
        repo = repo, isPro = { isPro }, markOnboardingDone = { onboardingDone = true },
        analytics = NoOpAnalytics, locale = Locale.GERMANY, clock = { now },
    )

    private fun policy() = AdsPolicy(clock = { now }, installTimeMs = install, isFirstSession = false)

    private fun entryVm(vehicleId: Long, policy: AdsPolicy = policy(), charge: Boolean = false) = EntryFormViewModel(
        repo = repo, adsPolicy = policy, analytics = NoOpAnalytics, vehicleId = vehicleId, isCharge = charge, clock = { now },
    )

    // ---- Add vehicle and the 2-vehicle free cap ----

    @Test fun addVehicle_unitsArePrefilledFromTheDeviceLocale() {
        val vm = addVm()
        assertEquals(DistanceUnit.KM, vm.state.value.units.distance)
        assertEquals(EconomyUnit.L_PER_100KM, vm.state.value.units.economy)
        assertEquals("EUR", vm.state.value.units.currency)
    }

    @Test fun addVehicle_nameIsRequired() {
        val vm = addVm()
        vm.onContinue()
        assertTrue(vm.state.value.nameMissing)
        assertEquals(AddVehicleStep.DETAILS, vm.state.value.step)
    }

    @Test fun addVehicle_twoActiveVehiclesAreFree() {
        insert(newVehicle("A"))
        val vm = addVm()
        vm.setName("B")
        vm.onContinue()
        assertEquals(AddVehicleStep.UNITS, await(vm.state) { it.step == AddVehicleStep.UNITS }.step)
    }

    @Test fun addVehicle_thirdActiveVehicleShowsTheFreeLimitDialog() {
        insert(newVehicle("A")); insert(newVehicle("B"))
        val vm = addVm()
        vm.setName("C")
        vm.onContinue()
        val s = await(vm.state) { it.showFreeLimit }
        assertEquals(AddVehicleStep.DETAILS, s.step)
        assertEquals(2, s.archiveCandidates.size)
    }

    @Test fun addVehicle_archivedVehiclesDoNotCount() {
        insert(newVehicle("A")); insert(newVehicle("B")); insert(newVehicle("Old", archived = true))
        // 2 active + 1 archived: the third active vehicle is still blocked, the archived one is not counted.
        assertEquals(2, runBlocking { db.vehicleDao().activeCount() })
        insert(newVehicle("C", archived = true))
        assertEquals(2, runBlocking { db.vehicleDao().activeCount() })
    }

    @Test fun addVehicle_oneActiveAndOneArchivedAllowsAnother() {
        insert(newVehicle("A")); insert(newVehicle("Old", archived = true))
        val vm = addVm()
        vm.setName("B")
        vm.onContinue()
        await(vm.state) { it.step == AddVehicleStep.UNITS }
        assertFalse(vm.state.value.showFreeLimit)
    }

    @Test fun addVehicle_proHasNoCap() {
        insert(newVehicle("A")); insert(newVehicle("B"))
        val vm = addVm(isPro = true)
        vm.setName("C")
        vm.onContinue()
        await(vm.state) { it.step == AddVehicleStep.UNITS }
        assertFalse(vm.state.value.showFreeLimit)
    }

    @Test fun addVehicle_archiveToMakeRoomThenContinue() {
        val a = insert(newVehicle("A")); insert(newVehicle("B"))
        val vm = addVm()
        vm.setName("C")
        vm.onContinue()
        await(vm.state) { it.showFreeLimit }
        vm.openArchivePicker()
        vm.archiveAndContinue(a)
        await(vm.state) { it.step == AddVehicleStep.UNITS }
        assertEquals(1, runBlocking { db.vehicleDao().activeCount() })
        assertTrue(runBlocking { db.vehicleDao().get(a) }!!.archived)
    }

    @Test fun addVehicle_seePro_opensTheProScreen() {
        insert(newVehicle("A")); insert(newVehicle("B"))
        val vm = addVm()
        vm.setName("C")
        vm.onContinue()
        await(vm.state) { it.showFreeLimit }
        vm.seePro()
        assertEquals(AddVehicleEvent.OpenPro, await(vm.events))
        assertFalse(vm.state.value.showFreeLimit)
    }

    @Test fun addVehicle_saveStoresTheVehicleAndFinishesOnboarding() {
        val vm = addVm()
        vm.setName("  Golf ")
        vm.setPowertrain(Powertrain.PHEV)
        vm.setYear("2019")
        vm.onContinue()
        await(vm.state) { it.step == AddVehicleStep.UNITS }
        vm.save()
        val saved = await(vm.events) as AddVehicleEvent.Saved
        val v = runBlocking { db.vehicleDao().get(saved.vehicleId) }!!
        assertEquals("Golf", v.name)
        assertEquals(Powertrain.PHEV, v.powertrain)
        assertEquals(2019, v.year)
        assertEquals("EUR", v.currency)
        assertTrue(onboardingDone)
    }

    @Test fun addVehicle_gasPowertrainUsesKilograms() {
        val vm = addVm()
        vm.setPowertrain(Powertrain.HYDROGEN)
        assertEquals(VolumeUnit.KG, vm.state.value.units.volume)
        assertEquals(EconomyUnit.KG_PER_100KM, vm.state.value.units.economy)
    }

    @Test fun addVehicle_invalidYearBlocksStepOne() {
        val vm = addVm()
        vm.setName("X")
        vm.setYear("1700")
        vm.onContinue()
        assertTrue(vm.state.value.yearInvalid)
    }

    @Test fun restoringAnArchivedVehicleIsBlockedAtTheCap() {
        insert(newVehicle("A")); insert(newVehicle("B"))
        val archived = insert(newVehicle("Old", archived = true))
        val vm = VehicleDetailViewModel(repo, archived, { false }, { now })
        await(vm.state) { it.loaded && it.vehicle != null }
        vm.restore()
        await(vm.state) { it.showFreeLimit }
        assertTrue(runBlocking { db.vehicleDao().get(archived) }!!.archived)
    }

    @Test fun restoringAtTheCap_archiveFromTheDialogMakesRoomAndRestores() {
        val a = insert(newVehicle("A")); val b = insert(newVehicle("B"))
        val old = insert(newVehicle("Old", archived = true))
        val vm = VehicleDetailViewModel(repo, old, { false }, { now })
        await(vm.state) { it.loaded && it.vehicle != null }
        vm.restore()
        await(vm.state) { it.showFreeLimit }
        vm.openArchivePicker()
        val picker = await(vm.state) { it.showArchivePicker }
        assertFalse(picker.showFreeLimit)
        assertEquals(setOf(a, b), picker.switcher.map { it.id }.toSet())
        vm.archiveAndRestore(a)
        await(vm.state) { it.vehicle?.archived == false && !it.showArchivePicker }
        assertTrue(runBlocking { db.vehicleDao().get(a) }!!.archived)
        assertFalse(runBlocking { db.vehicleDao().get(old) }!!.archived)
        assertFalse(runBlocking { db.vehicleDao().get(b) }!!.archived)
    }

    @Test fun restoringAtTheCap_cancelKeepsTheVehicleArchived() {
        insert(newVehicle("A")); insert(newVehicle("B"))
        val old = insert(newVehicle("Old", archived = true))
        val vm = VehicleDetailViewModel(repo, old, { false }, { now })
        await(vm.state) { it.loaded && it.vehicle != null }
        vm.restore()
        await(vm.state) { it.showFreeLimit }
        vm.openArchivePicker()
        await(vm.state) { it.showArchivePicker }
        vm.dismissFreeLimit()
        await(vm.state) { !it.showArchivePicker && !it.showFreeLimit }
        assertTrue(runBlocking { db.vehicleDao().get(old) }!!.archived)
    }

    @Test fun restoringWorksWhenThereIsRoom() {
        insert(newVehicle("A"))
        val archived = insert(newVehicle("Old", archived = true))
        val vm = VehicleDetailViewModel(repo, archived, { false }, { now })
        await(vm.state) { it.vehicle != null }
        vm.restore()
        await(vm.state) { it.vehicle?.archived == false }
    }

    // ---- Fill-up form ----

    private fun fill(vm: EntryFormViewModel, odometer: String, volume: String, total: String) {
        vm.setOdometer(odometer); vm.setQuantity(volume); vm.setTotal(total)
    }

    @Test fun fillUp_savesAndStartsThePumpSession() {
        val id = insert(newVehicle("A"))
        val policy = policy()
        val vm = entryVm(id, policy)
        await(vm.state) { it.vehicle != null }
        val config = AdsConfig.DEFAULTS
        assertEquals(InterstitialDecision.ALLOWED, policy.evaluate(config, adsHidden = false, canRequestAds = true))

        fill(vm, "10000", "40", "74.36")
        vm.save()
        assertEquals(EntryEvent.Saved, await(vm.events))

        val logs = runBlocking { db.energyLogDao().getAll(id) }
        assertEquals(1, logs.size)
        assertEquals(7436L, logs[0].totalMinor)
        assertEquals(InterstitialDecision.PUMP_SESSION, policy.evaluate(config, adsHidden = false, canRequestAds = true))
    }

    @Test fun charge_alsoStartsThePumpSession() {
        val id = insert(newVehicle("EV", powertrain = Powertrain.ELECTRIC))
        val policy = policy()
        val vm = entryVm(id, policy, charge = true)
        await(vm.state) { it.vehicle != null }
        fill(vm, "5000", "30", "9")
        vm.save()
        await(vm.events)
        assertEquals(EnergyStream.ELECTRIC, runBlocking { db.energyLogDao().getAll(id) }.single().stream)
        assertEquals(InterstitialDecision.PUMP_SESSION, policy.evaluate(AdsConfig.DEFAULTS, false, true))
    }

    @Test fun fillUp_invalidFormDoesNotSave() {
        val id = insert(newVehicle("A"))
        val policy = policy()
        val vm = entryVm(id, policy)
        await(vm.state) { it.vehicle != null }
        vm.save()
        val s = vm.state.value
        assertTrue(s.showErrors)
        assertFalse(s.validation!!.isValid)
        assertEquals(0, runBlocking { db.energyLogDao().getAll(id) }.size)
        assertEquals(InterstitialDecision.ALLOWED, policy.evaluate(AdsConfig.DEFAULTS, false, true))
    }

    @Test fun fillUp_derivedTotalAppearsInTheValidation() {
        val id = insert(newVehicle("A"))
        val vm = entryVm(id)
        await(vm.state) { it.vehicle != null }
        vm.setQuantity("40"); vm.setUnitPrice("1.859")
        assertEquals("74.36", vm.state.value.validation!!.price.total.toString())
    }

    @Test fun fillUp_odometerWarningNeedsASecondSave() {
        val id = insert(newVehicle("A"))
        runBlocking {
            db.energyLogDao().insert(
                EnergyLog(vehicleId = id, stream = EnergyStream.LIQUID, dateTime = now - 1000, odometer = 20_000.0, quantity = 40.0, isFull = true, createdAt = 1, updatedAt = 1),
            )
        }
        val vm = entryVm(id)
        await(vm.state) { it.vehicle != null }
        fill(vm, "19000", "35", "60")
        assertNotNull(vm.state.value.validation!!.warning)
        assertEquals(20_000.0, vm.state.value.lastOdometer!!, 0.0)

        vm.save() // first press: only arms "Save anyway"
        assertTrue(vm.state.value.saveAnyway)
        assertEquals(1, runBlocking { db.energyLogDao().getAll(id) }.size)

        vm.save()
        assertEquals(EntryEvent.Saved, await(vm.events))
        assertEquals(2, runBlocking { db.energyLogDao().getAll(id) }.size)
    }

    @Test fun fillUp_changingTheOdometerClearsTheConfirmation() {
        val id = insert(newVehicle("A"))
        runBlocking {
            db.energyLogDao().insert(
                EnergyLog(vehicleId = id, stream = EnergyStream.LIQUID, dateTime = now - 1000, odometer = 20_000.0, quantity = 40.0, isFull = true, createdAt = 1, updatedAt = 1),
            )
        }
        val vm = entryVm(id)
        await(vm.state) { it.vehicle != null }
        fill(vm, "19000", "35", "60")
        vm.save()
        assertTrue(vm.state.value.saveAnyway)
        vm.setOdometer("18000")
        assertFalse(vm.state.value.saveAnyway)
    }

    @Test fun fillUp_saveFailureEmitsAnEventAndKeepsTheForm() {
        val id = insert(newVehicle("A"))
        val vm = entryVm(id)
        await(vm.state) { it.vehicle != null }
        fill(vm, "10000", "40", "74.36")
        // The vehicle disappears (foreign key) so the insert fails.
        runBlocking { db.vehicleDao().delete(db.vehicleDao().get(id)!!) }
        vm.save()
        assertEquals(EntryEvent.SaveFailed, await(vm.events))
        assertFalse(vm.state.value.saving)
        assertEquals("10000", vm.state.value.form.odometer)
    }

    @Test fun archivedVehicleIsReadOnly() {
        val id = insert(newVehicle("Old", archived = true))
        val vm = entryVm(id)
        await(vm.state) { it.vehicle != null }
        fill(vm, "10000", "40", "74.36")
        vm.save()
        assertTrue(vm.isReadOnly())
        assertEquals(0, runBlocking { db.energyLogDao().getAll(id) }.size)
    }

    @Test fun economyUpdatesAfterTheSecondFullFillUp() {
        val id = insert(newVehicle("A"))
        val vehicle = runBlocking { db.vehicleDao().get(id) }!!
        assertTrue(VehicleStatsCalculator.compute(vehicle, emptyList()).streams.isEmpty())

        val vm1 = entryVm(id)
        await(vm1.state) { it.vehicle != null }
        fill(vm1, "10000", "40", "70")
        vm1.save(); await(vm1.events)

        val vm2 = entryVm(id)
        await(vm2.state) { it.vehicle != null }
        fill(vm2, "10500", "30", "52")
        vm2.save(); await(vm2.events)

        val logs = runBlocking { db.energyLogDao().getAll(id) }
        val stats = VehicleStatsCalculator.compute(vehicle, logs)
        assertEquals(6.0, stats.streams.single().average!!, 0.0)
        assertEquals(10_500.0, stats.odometer!!, 0.0)
        assertEquals(12_200L, stats.costs.totalMinor)
    }
}
