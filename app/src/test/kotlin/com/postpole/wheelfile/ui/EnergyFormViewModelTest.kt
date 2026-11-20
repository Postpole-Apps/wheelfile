package com.postpole.wheelfile.ui

import androidx.lifecycle.SavedStateHandle
import com.postpole.wheelfile.TestEnv
import com.postpole.wheelfile.ads.InterstitialDecision
import com.postpole.wheelfile.ads.AdsConfig
import com.postpole.wheelfile.data.db.ChargerType
import com.postpole.wheelfile.data.db.EnergyStream
import com.postpole.wheelfile.data.db.Powertrain
import com.postpole.wheelfile.domain.fillup.PriceField
import com.postpole.wheelfile.ui.energy.EnergyFormEvent
import com.postpole.wheelfile.ui.energy.EnergyFormViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class EnergyFormViewModelTest {
    private lateinit var env: TestEnv

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        env = TestEnv(locale = Locale.US)
    }

    @After
    fun tearDown() {
        env.close()
        Dispatchers.resetMain()
    }

    private fun viewModel(vehicleId: Long, stream: EnergyStream = EnergyStream.LIQUID) = EnergyFormViewModel(
        env.repository, env.adsPolicy, env.analytics, env.localeProvider, env.clock,
        SavedStateHandle(mapOf(EnergyFormViewModel.ARG_VEHICLE_ID to vehicleId, EnergyFormViewModel.ARG_STREAM to stream.name)),
    )

    @Test
    fun totalIsDerivedFromPriceAndVolume() = runTest {
        val vm = viewModel(env.addVehicle())
        vm.onQuantity("40")
        vm.onPrice("1.859")
        val s = vm.state.value
        assertEquals(PriceField.TOTAL, s.derived)
        assertEquals("74.36", s.total)
    }

    @Test
    fun priceIsDerivedFromTotalAndVolume() = runTest {
        val vm = viewModel(env.addVehicle())
        vm.onQuantity("40")
        vm.onTotal("74.36")
        assertEquals(PriceField.PRICE, vm.state.value.derived)
        assertEquals("1.859", vm.state.value.price)
    }

    @Test
    fun volumeIsDerivedFromTotalAndPrice() = runTest {
        val vm = viewModel(env.addVehicle())
        vm.onTotal("50")
        vm.onPrice("2")
        assertEquals(PriceField.VOLUME, vm.state.value.derived)
        assertEquals("25", vm.state.value.quantity)
    }

    @Test
    fun editingTheDerivedFieldMakesItAnInput() = runTest {
        val vm = viewModel(env.addVehicle())
        vm.onQuantity("40")
        vm.onPrice("2")
        assertEquals("80", vm.state.value.total.removeSuffix(".00").removeSuffix(".0"))
        vm.onTotal("60") // total is now an input; the oldest input (volume) is recalculated
        assertEquals(PriceField.VOLUME, vm.state.value.derived)
        assertEquals("30", vm.state.value.quantity)
    }

    @Test
    fun clearingAnInputClearsTheCalculatedValue() = runTest {
        val vm = viewModel(env.addVehicle())
        vm.onQuantity("40")
        vm.onPrice("2")
        vm.onPrice("")
        assertEquals("", vm.state.value.total)
        assertNull(vm.state.value.derived)
        assertEquals("40", vm.state.value.quantity)
    }

    @Test
    fun saveRequiresOdometerAndPositiveVolume() = runTest {
        val vm = viewModel(env.addVehicle())
        vm.save()
        assertTrue(vm.state.value.odometerError)
        assertTrue(vm.state.value.quantityError)
        assertEquals(0, env.db.energyLogDao().getAllForVehicle(1).size)

        vm.onOdometer("1000")
        vm.onQuantity("0")
        vm.save()
        assertFalse(vm.state.value.odometerError)
        assertTrue(vm.state.value.quantityError)
    }

    @Test
    fun invalidCharactersAreRejected() = runTest {
        val vm = viewModel(env.addVehicle())
        vm.onOdometer("12ab")
        assertEquals("", vm.state.value.odometer)
        vm.onOdometer("12,5")
        assertEquals("12,5", vm.state.value.odometer)
    }

    @Test
    fun savesFillUpWithExactMinorTotal() = runTest {
        val id = env.addVehicle()
        val vm = viewModel(id)
        vm.onOdometer("1000")
        vm.onQuantity("40")
        vm.onPrice("1.859")
        vm.save()
        assertEquals(EnergyFormEvent.Saved, vm.events.first())
        val log = env.db.energyLogDao().getAllForVehicle(id).single()
        assertEquals(7436L, log.totalMinor)
        assertEquals(40.0, log.quantity, 0.0)
        assertTrue(log.isFull)
        assertEquals(EnergyStream.LIQUID, log.stream)
    }

    @Test
    fun savingWithoutPriceStoresNoTotal() = runTest {
        val id = env.addVehicle()
        val vm = viewModel(id)
        vm.onOdometer("1000")
        vm.onQuantity("40")
        vm.save()
        assertNull(env.db.energyLogDao().getAllForVehicle(id).single().totalMinor)
    }

    @Test
    fun odometerLowerThanEarlierEntryWarnsButDoesNotBlock() = runTest {
        val id = env.addVehicle()
        env.addLog(id, odometer = 5000.0, dateTime = env.now - 10_000)
        val vm = viewModel(id)
        vm.onOdometer("4000")
        assertTrue(vm.state.value.odometerWarning)
        assertEquals(5000.0, vm.state.value.lastOdometer!!, 0.0)
        vm.onQuantity("30")
        vm.save()
        assertEquals(EnergyFormEvent.Saved, vm.events.first())
        assertEquals(2, env.db.energyLogDao().getAllForVehicle(id).size)
    }

    @Test
    fun higherOdometerDoesNotWarn() = runTest {
        val id = env.addVehicle()
        env.addLog(id, odometer = 5000.0, dateTime = env.now - 10_000)
        val vm = viewModel(id)
        vm.onOdometer("5400")
        assertFalse(vm.state.value.odometerWarning)
    }

    @Test
    fun chargeStoresChargerAndSocAndValidatesRange() = runTest {
        val id = env.addVehicle("Ioniq", powertrain = Powertrain.ELECTRIC)
        val vm = viewModel(id, EnergyStream.ELECTRIC)
        vm.onOdometer("2000")
        vm.onQuantity("30")
        vm.onCharger(ChargerType.DC_FAST)
        vm.onSocStart("22")
        vm.onSocEnd("101")
        vm.save()
        assertTrue(vm.state.value.socError)
        vm.onSocEnd("80")
        vm.save()
        assertEquals(EnergyFormEvent.Saved, vm.events.first())
        val log = env.db.energyLogDao().getAllForVehicle(id).single()
        assertEquals(EnergyStream.ELECTRIC, log.stream)
        assertEquals(ChargerType.DC_FAST, log.chargerType)
        assertEquals(22, log.socStart)
        assertEquals(80, log.socEnd)
    }

    @Test
    fun savingAFillUpStartsThePumpSession() = runTest {
        val id = env.addVehicle()
        val config = AdsConfig()
        assertNotEquals(InterstitialDecision.PUMP_SESSION, env.adsPolicy.evaluate(config, adsHidden = false, canRequestAds = true))
        val vm = viewModel(id)
        vm.onOdometer("1000")
        vm.onQuantity("40")
        vm.save()
        assertEquals(InterstitialDecision.PUMP_SESSION, env.adsPolicy.evaluate(config, adsHidden = false, canRequestAds = true))
    }

    @Test
    fun archivedVehicleIsReadOnly() = runTest {
        val id = env.addVehicle(archived = true)
        val vm = viewModel(id)
        assertTrue(vm.state.value.readOnly)
        vm.onOdometer("1000")
        vm.onQuantity("40")
        vm.save()
        assertEquals(0, env.db.energyLogDao().getAllForVehicle(id).size)
    }
}
