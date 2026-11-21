package com.postpole.wheelfile.domain.entry

import com.postpole.wheelfile.data.db.ChargerType
import com.postpole.wheelfile.data.db.DistanceUnit
import com.postpole.wheelfile.data.db.EconomyUnit
import com.postpole.wheelfile.data.db.EnergyLog
import com.postpole.wheelfile.data.db.EnergyStream
import com.postpole.wheelfile.data.db.Powertrain
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.data.db.VehicleType
import com.postpole.wheelfile.data.db.VolumeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class EntryFormTest {
    private val day = 24L * 60 * 60 * 1000
    private val t0 = 1_700_000_000_000L

    private fun vehicle(
        powertrain: Powertrain = Powertrain.PETROL,
        distance: DistanceUnit = DistanceUnit.KM,
        volume: VolumeUnit = VolumeUnit.L,
        currency: String = "EUR",
    ) = Vehicle(
        id = 1, name = "Golf", type = VehicleType.CAR, powertrain = powertrain, distanceUnit = distance,
        volumeUnit = volume, economyDisplay = EconomyUnit.L_PER_100KM, currency = currency, createdAt = 0, updatedAt = 0,
    )

    private fun log(id: Long, at: Long, odo: Double, qty: Double = 40.0) = EnergyLog(
        id = id, vehicleId = 1, stream = EnergyStream.LIQUID, dateTime = at, odometer = odo, quantity = qty,
        isFull = true, createdAt = 0, updatedAt = 0,
    )

    private fun state(
        odometer: String = "12500",
        quantity: String = "40",
        unitPrice: String = "",
        total: String = "74.36",
        charge: Boolean = false,
        at: Long = t0 + 10 * day,
    ) = EntryFormState(
        isCharge = charge, dateTime = at, odometer = odometer, quantity = quantity, unitPrice = unitPrice, total = total,
        editOrder = listOf(PriceField.QUANTITY, PriceField.TOTAL),
    )

    @Test fun validFillUpHasNoErrors() {
        val v = EntryForm.validate(state(), vehicle(), emptyList())
        assertTrue(v.isValid)
        assertEquals(PriceField.UNIT_PRICE, v.price.derived)
    }

    @Test fun odometerAndVolumeAreRequired() {
        val v = EntryForm.validate(state(odometer = "", quantity = "", total = ""), vehicle(), emptyList())
        assertEquals(FieldError.REQUIRED, v.errors[FormField.ODOMETER])
        assertEquals(FieldError.REQUIRED, v.errors[FormField.QUANTITY])
    }

    @Test fun priceAndTotalAreOptional() {
        val v = EntryForm.validate(state(total = ""), vehicle(), emptyList())
        assertTrue(v.isValid)
        assertNull(v.price.derived)
    }

    @Test fun volumeMayBeDerivedFromTotalAndPrice() {
        val s = state(quantity = "", unitPrice = "1.859", total = "74.36")
            .copy(editOrder = listOf(PriceField.UNIT_PRICE, PriceField.TOTAL))
        val v = EntryForm.validate(s, vehicle(), emptyList())
        assertTrue(v.isValid)
        assertEquals(BigDecimal("40.00"), v.price.quantity)
    }

    @Test fun malformedNegativeAndZeroNumbersAreInvalid() {
        assertEquals(FieldError.INVALID, EntryForm.validate(state(odometer = "abc"), vehicle(), emptyList()).errors[FormField.ODOMETER])
        assertEquals(FieldError.INVALID, EntryForm.validate(state(odometer = "-5"), vehicle(), emptyList()).errors[FormField.ODOMETER])
        assertEquals(FieldError.INVALID, EntryForm.validate(state(odometer = "0"), vehicle(), emptyList()).errors[FormField.ODOMETER])
        assertEquals(FieldError.INVALID, EntryForm.validate(state(quantity = "0"), vehicle(), emptyList()).errors[FormField.QUANTITY])
        assertEquals(FieldError.INVALID, EntryForm.validate(state(unitPrice = "1.2.3"), vehicle(), emptyList()).errors[FormField.UNIT_PRICE])
    }

    @Test fun hugeNumbersAreRejected() {
        assertEquals(FieldError.TOO_LARGE, EntryForm.validate(state(odometer = "99999999"), vehicle(), emptyList()).errors[FormField.ODOMETER])
        assertEquals(FieldError.TOO_LARGE, EntryForm.validate(state(quantity = "10000"), vehicle(), emptyList()).errors[FormField.QUANTITY])
    }

    @Test fun decimalCommaIsAccepted() {
        assertEquals(BigDecimal("1.859"), EntryForm.parseDecimal("1,859"))
        assertNull(EntryForm.parseDecimal("1,8,5"))
    }

    @Test fun chargeSocMustBeZeroToHundred() {
        val s = state(charge = true).copy(socStart = "20", socEnd = "120")
        val v = EntryForm.validate(s, vehicle(Powertrain.ELECTRIC), emptyList())
        assertEquals(FieldError.INVALID, v.errors[FormField.SOC_END])
        assertNull(v.errors[FormField.SOC_START])
    }

    @Test fun odometerLowerThanEarlierEntryWarnsButDoesNotBlock() {
        val existing = listOf(log(1, t0, 12_345.0))
        val v = EntryForm.validate(state(odometer = "12000"), vehicle(), existing)
        assertTrue(v.isValid)
        val w = v.warning
        assertNotNull(w)
        assertTrue(w!!.tooLow)
        assertEquals(12_345.0, w.reference, 0.0)
    }

    @Test fun odometerHigherThanLaterEntryWarns() {
        val existing = listOf(log(1, t0 + 20 * day, 13_000.0))
        val v = EntryForm.validate(state(odometer = "14000", at = t0 + 10 * day), vehicle(), existing)
        val w = v.warning
        assertNotNull(w)
        assertFalse(w!!.tooLow)
        assertEquals(13_000.0, w.reference, 0.0)
    }

    @Test fun increasingOdometerDoesNotWarn() {
        val existing = listOf(log(1, t0, 12_000.0))
        assertNull(EntryForm.validate(state(odometer = "12500"), vehicle(), existing).warning)
    }

    @Test fun warningComparesInKilometresAcrossUnits() {
        // 7,000 mi = 11,265 km; an earlier 11,000 mi entry is higher than 7,000 mi.
        val v = vehicle(distance = DistanceUnit.MI)
        val existing = listOf(log(1, t0, 11_000.0))
        assertNotNull(EntryForm.validate(state(odometer = "7000"), v, existing).warning)
    }

    @Test fun editingIgnoresTheEntryItself() {
        val existing = listOf(log(5, t0 + 20 * day, 13_000.0))
        assertNull(EntryForm.validate(state(odometer = "14000", at = t0 + 10 * day), vehicle(), existing, editingId = 5).warning)
    }

    @Test fun toEnergyLogStoresMinorUnitsAndStream() {
        val s = state(unitPrice = "", total = "74.36").copy(isFull = false, missedPrevious = true, station = " Shell ")
        val log = EntryForm.toEnergyLog(s, vehicle(), now = 99)
        assertEquals(EnergyStream.LIQUID, log.stream)
        assertEquals(7436L, log.totalMinor)
        assertEquals(185_900L, log.pricePerUnitMinor)
        assertEquals(12_500.0, log.odometer, 0.0)
        assertFalse(log.isFull)
        assertTrue(log.missedPrevious)
        assertEquals("Shell", log.station)
        assertNull(log.chargerType)
    }

    @Test fun chargeKeepsChargerAndSocOnElectricStream() {
        val s = state(charge = true, quantity = "30", total = "9").copy(charger = ChargerType.DC_FAST, socStart = "20", socEnd = "80")
        val log = EntryForm.toEnergyLog(s, vehicle(Powertrain.PHEV), now = 1)
        assertEquals(EnergyStream.ELECTRIC, log.stream)
        assertEquals(ChargerType.DC_FAST, log.chargerType)
        assertEquals(20, log.socStart)
        assertEquals(80, log.socEnd)
    }

    @Test fun streamFollowsThePowertrain() {
        assertEquals(EnergyStream.LIQUID, EntryForm.stream(vehicle(Powertrain.DIESEL), false))
        assertEquals(EnergyStream.ELECTRIC, EntryForm.stream(vehicle(Powertrain.ELECTRIC), false))
        assertEquals(EnergyStream.LIQUID, EntryForm.stream(vehicle(Powertrain.PHEV), false))
        assertEquals(EnergyStream.ELECTRIC, EntryForm.stream(vehicle(Powertrain.PHEV), true))
        assertEquals(EnergyStream.GAS_KG, EntryForm.stream(vehicle(Powertrain.HYDROGEN), false))
    }

    @Test fun fractionDigitsComeFromTheCurrency() {
        assertEquals(2, EntryForm.fractionDigits("EUR"))
        assertEquals(0, EntryForm.fractionDigits("JPY"))
        assertEquals(3, EntryForm.fractionDigits("KWD"))
    }
}
