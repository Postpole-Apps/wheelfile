package com.postpole.wheelfile.ui

import androidx.lifecycle.SavedStateHandle
import com.postpole.wheelfile.TestEnv
import com.postpole.wheelfile.data.AddVehicleResult
import com.postpole.wheelfile.data.db.DistanceUnit
import com.postpole.wheelfile.data.db.EconomyUnit
import com.postpole.wheelfile.data.db.Powertrain
import com.postpole.wheelfile.data.db.VolumeUnit
import com.postpole.wheelfile.domain.vehicle.VehicleCap
import com.postpole.wheelfile.ui.garage.GarageEvent
import com.postpole.wheelfile.ui.garage.GarageViewModel
import com.postpole.wheelfile.ui.vehicle.VehicleFormEvent
import com.postpole.wheelfile.ui.vehicle.VehicleFormStep
import com.postpole.wheelfile.ui.vehicle.VehicleFormViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class GarageViewModelTest {
    private lateinit var env: TestEnv

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        env = TestEnv()
    }

    @After
    fun tearDown() {
        env.close()
        Dispatchers.resetMain()
    }

    private fun garage() = GarageViewModel(env.repository, env.pro)

    @Test
    fun capRuleIsTwoActiveVehiclesForFreeAndUnlimitedForPro() {
        assertTrue(VehicleCap.canAddActive(0, isPro = false))
        assertTrue(VehicleCap.canAddActive(1, isPro = false))
        assertFalse(VehicleCap.canAddActive(2, isPro = false))
        assertTrue(VehicleCap.canAddActive(5, isPro = true))
    }

    @Test
    fun twoActiveVehiclesAreFree() = runTest {
        env.addVehicle("A")
        val vm = garage()
        vm.onAddVehicle()
        assertEquals(GarageEvent.OpenAddVehicle, vm.events.first())
    }

    @Test
    fun thirdActiveVehicleShowsTheFreeLimitDialog() = runTest {
        env.addVehicle("A")
        env.addVehicle("B")
        val vm = garage()
        vm.onAddVehicle()
        assertEquals(GarageEvent.ShowFreeLimit, vm.events.first())
    }

    @Test
    fun archivedVehiclesDoNotCountTowardsTheCap() = runTest {
        env.addVehicle("A")
        env.addVehicle("B", archived = true)
        env.addVehicle("C", archived = true)
        val vm = garage()
        vm.onAddVehicle()
        assertEquals(GarageEvent.OpenAddVehicle, vm.events.first())
    }

    @Test
    fun archivingFromTheDialogFreesASlot() = runTest {
        val a = env.addVehicle("A")
        env.addVehicle("B")
        val vm = garage()
        vm.archiveThenAdd(a)
        assertEquals(GarageEvent.OpenAddVehicle, vm.events.first())
        assertEquals(1, env.repository.observeActiveCount().first())
        assertTrue(env.repository.get(a)!!.archived)
    }

    @Test
    fun proLiftsTheCap() = runTest {
        env.addVehicle("A")
        env.addVehicle("B")
        env.setPro(true)
        val vm = garage()
        vm.onAddVehicle()
        assertEquals(GarageEvent.OpenAddVehicle, vm.events.first())
    }

    @Test
    fun restoringAtTheCapIsBlocked() = runTest {
        env.addVehicle("A")
        env.addVehicle("B")
        val archived = env.addVehicle("C", archived = true)
        val vm = garage()
        vm.unarchive(archived)
        assertEquals(GarageEvent.ShowFreeLimit, vm.events.first())
        assertTrue(env.repository.get(archived)!!.archived)
    }

    @Test
    fun repositoryRefusesAThirdActiveVehicleEvenIfTheUiMissedIt() = runTest {
        env.addVehicle("A")
        env.addVehicle("B")
        assertEquals(AddVehicleResult.LimitReached, env.repository.add(env.vehicle("C"), isPro = false))
        assertTrue(env.repository.add(env.vehicle("D", archived = true), isPro = false) is AddVehicleResult.Added)
    }

    @Test
    fun archivedVehicleRejectsNewEntries() = runTest {
        val id = env.addVehicle("A", archived = true)
        val log = com.postpole.wheelfile.data.db.EnergyLog(
            vehicleId = id, stream = com.postpole.wheelfile.data.db.EnergyStream.LIQUID, dateTime = 1, odometer = 1.0,
            quantity = 1.0, isFull = true, createdAt = 1, updatedAt = 1,
        )
        assertFalse(env.repository.addEnergyLog(log))
    }

    @Test
    fun emptyGarageHasNoBannerAndOneVehicleHasOne() = runTest {
        val vm = garage()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        assertFalse(vm.state.value.showsBanner)
        env.addVehicle("A")
        assertTrue(vm.state.value.showsBanner)
    }

    // ---- add-vehicle form -------------------------------------------------------------------

    private fun form(locale: Locale = Locale.FRANCE, onboarding: Boolean = false) = VehicleFormViewModel(
        env.repository, env.settings, env.pro, env.analytics, { locale },
        SavedStateHandle(mapOf(VehicleFormViewModel.ARG_ONBOARDING to onboarding)),
    )

    @Test
    fun unitsArePrefilledFromTheDeviceLocale() {
        val de = form(Locale.GERMANY).state.value
        assertEquals(DistanceUnit.KM, de.distance)
        assertEquals(VolumeUnit.L, de.volume)
        assertEquals(EconomyUnit.L_PER_100KM, de.economy)

        val us = form(Locale.US).state.value
        assertEquals(DistanceUnit.MI, us.distance)
        assertEquals(VolumeUnit.US_GAL, us.volume)
        assertEquals(EconomyUnit.MPG_US, us.economy)

        val uk = form(Locale.UK).state.value
        assertEquals(DistanceUnit.MI, uk.distance)
        assertEquals(VolumeUnit.L, uk.volume)
        assertEquals(EconomyUnit.MPG_UK, uk.economy)
    }

    @Test
    fun nameIsRequiredBeforeTheUnitsStep() = runTest {
        val vm = form()
        vm.next()
        assertTrue(vm.state.value.nameError)
        assertEquals(VehicleFormStep.DETAILS, vm.state.value.step)
        vm.onName("Golf")
        vm.next()
        assertEquals(VehicleFormStep.UNITS, vm.state.value.step)
    }

    @Test
    fun invalidYearBlocksButVinLengthOnlyWarns() = runTest {
        val vm = form()
        vm.onName("Golf")
        vm.onYear("1700")
        vm.next()
        assertTrue(vm.state.value.yearError)
        vm.onYear("2019")
        vm.onVin("abc123")
        assertTrue(vm.state.value.vinLooksOdd)
        vm.next()
        assertEquals(VehicleFormStep.UNITS, vm.state.value.step)
    }

    @Test
    fun electricPowertrainUsesElectricEconomyAndPhevKeepsFuelEconomy() = runTest {
        val vm = form(Locale.FRANCE)
        vm.onPowertrain(Powertrain.ELECTRIC)
        assertEquals(EconomyUnit.KWH_PER_100KM, vm.state.value.economy)
        vm.onPowertrain(Powertrain.PHEV)
        assertEquals(EconomyUnit.L_PER_100KM, vm.state.value.economy)
        vm.onPowertrain(Powertrain.HYDROGEN)
        assertEquals(VolumeUnit.KG, vm.state.value.volume)
        assertEquals(EconomyUnit.KG_PER_100KM, vm.state.value.economy)
    }

    @Test
    fun savingMarksOnboardingDoneAndStoresTheVehicle() = runTest {
        val vm = form(onboarding = true)
        vm.onName("  Golf ")
        vm.onPowertrain(Powertrain.DIESEL)
        vm.next()
        vm.save()
        val event = vm.events.first() as VehicleFormEvent.Saved
        assertTrue(event.onboarding)
        assertTrue(env.settings.onboardingDone.first())
        val saved = env.repository.get(event.vehicleId)!!
        assertEquals("Golf", saved.name)
        assertEquals(Powertrain.DIESEL, saved.powertrain)
        assertNull(saved.vin)
    }

    @Test
    fun thirdVehicleFromTheFormReportsTheLimit() = runTest {
        env.addVehicle("A")
        env.addVehicle("B")
        val vm = form()
        vm.onName("C")
        vm.next()
        vm.save()
        assertEquals(VehicleFormEvent.LimitReached, vm.events.first())
        assertEquals(2, env.repository.observeActiveCount().first())
    }
}
