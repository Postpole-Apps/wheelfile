package com.postpole.wheelfile.domain.document

import com.postpole.wheelfile.data.db.DocumentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/** One test group per region of spec §2.6, plus the generic list and the region default. */
class RegionPresetsTest {
    private fun preset(code: String) = checkNotNull(RegionPresets.find(code)) { "missing preset $code" }

    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    // ---- GB ----

    @Test fun gbMotFirstAtThreeYearsThenEveryYear() {
        val mot = preset("GB_MOT")
        val registered = d(2023, 3, 10)
        assertEquals(d(2026, 3, 10), RegionPresets.firstDue(mot, registered))
        // Tested on 5 March 2026: the next one is a year on, and every year after that.
        assertEquals(d(2027, 3, 5), RegionPresets.nextAfter(mot, registered, d(2026, 3, 5)))
        assertEquals(d(2028, 3, 5), RegionPresets.nextAfter(mot, registered, d(2027, 3, 5)))
    }

    @Test fun gbMotWithoutALastTestWalksTheScheduleFromRegistration() {
        val mot = preset("GB_MOT")
        // First test 10 Mar 2026 has passed by 15 Jun 2026, so the next yearly date is shown.
        assertEquals(d(2027, 3, 10), RegionPresets.nextDue(mot, d(2023, 3, 10), null, d(2026, 6, 15)))
        // Before the first test it is the first test.
        assertEquals(d(2026, 3, 10), RegionPresets.nextDue(mot, d(2023, 3, 10), null, d(2025, 1, 1)))
    }

    @Test fun gbVehicleTaxRepeatsYearlyFromTheDateTheUserEnters() {
        val tax = preset("GB_TAX")
        assertEquals(d(2027, 4, 1), RegionPresets.nextAfter(tax, null, d(2026, 4, 1)))
        assertNull(RegionPresets.nextDue(tax, d(2020, 1, 1), null, d(2026, 6, 1)))
    }

    // ---- DE ----

    @Test fun deHuFirstAtThirtySixMonthsThenEveryTwentyFour() {
        val hu = preset("DE_HU")
        val registered = d(2023, 3, 10)
        assertEquals(d(2026, 3, 10), RegionPresets.firstDue(hu, registered))
        assertEquals(d(2028, 3, 10), RegionPresets.nextAfter(hu, registered, d(2026, 3, 10)))
        assertEquals(d(2030, 3, 10), RegionPresets.nextAfter(hu, registered, d(2028, 3, 10)))
        assertEquals(d(2028, 3, 10), RegionPresets.nextDue(hu, registered, null, d(2026, 6, 15)))
    }

    // ---- FR ----

    @Test fun frControleTechniqueBeforeTheFourthAnniversaryThenEveryTwoYears() {
        val ct = preset("FR_CT")
        val registered = d(2022, 5, 20)
        // The deadline is the 4th anniversary; the window opens 6 months earlier.
        assertEquals(d(2026, 5, 20), RegionPresets.firstDue(ct, registered))
        val plan = RegionPresets.plan(ct, registered, null, d(2025, 1, 1))
        assertEquals(d(2026, 5, 20), plan.expiry)
        val windowDays = ChronoUnit.DAYS.between(d(2025, 11, 20), d(2026, 5, 20)).toInt()
        assertEquals(windowDays, plan.alertDays.first())
        assertEquals(DocumentAlerts.DEFAULT_DAYS, plan.alertDays.drop(1))
        // Then every 2 years from the test.
        assertEquals(d(2028, 5, 1), RegionPresets.nextAfter(ct, registered, d(2026, 5, 1)))
        assertEquals(d(2030, 5, 1), RegionPresets.nextAfter(ct, registered, d(2028, 5, 1)))
    }

    // ---- ES ----

    @Test fun esItvFourTwoOneRule() {
        val itv = preset("ES_ITV")
        val registered = d(2020, 1, 15)
        // Exempt for 4 years.
        assertEquals(d(2024, 1, 15), RegionPresets.firstDue(itv, registered))
        // Every 2 years until the vehicle is 10.
        assertEquals(d(2026, 1, 15), RegionPresets.nextAfter(itv, registered, d(2024, 1, 15)))
        assertEquals(d(2030, 1, 15), RegionPresets.nextAfter(itv, registered, d(2028, 1, 15)))
        // From 10 years on, every year.
        assertEquals(d(2031, 1, 15), RegionPresets.nextAfter(itv, registered, d(2030, 1, 15)))
        assertEquals(d(2032, 1, 15), RegionPresets.nextAfter(itv, registered, d(2031, 1, 15)))
        // Walking the schedule from registration reaches the same dates.
        assertEquals(d(2026, 1, 15), RegionPresets.nextDue(itv, registered, null, d(2024, 6, 1)))
        assertEquals(d(2031, 1, 15), RegionPresets.nextDue(itv, registered, null, d(2030, 6, 1)))
    }

    // ---- US-CA and AU-NSW ----

    @Test fun usCaliforniaSmogCheckIsADateTheUserSets() {
        val smog = preset("US_CA_SMOG")
        assertTrue(smog.schedule is PresetSchedule.UserSet)
        assertNull(RegionPresets.nextDue(smog, d(2018, 1, 1), null, d(2026, 6, 1)))
        assertNull(RegionPresets.nextDue(smog, d(2018, 1, 1), d(2026, 1, 1), d(2026, 6, 1)))
        assertNull(RegionPresets.plan(smog, d(2018, 1, 1), null, d(2026, 6, 1)).expiry)
        assertEquals(setOf("US_CA_SMOG", "US_CA_REG", "ALL_INSURANCE", "ALL_LICENCE"), RegionPresets.presetsFor(Region.US_CA).map { it.code }.toSet())
    }

    @Test fun auNswRegistrationRenewsYearly() {
        val rego = preset("AU_NSW_REGO")
        assertEquals(d(2027, 2, 28), RegionPresets.nextAfter(rego, null, d(2026, 2, 28)))
        assertEquals(d(2027, 3, 1), RegionPresets.nextAfter(rego, null, d(2026, 3, 1)))
    }

    @Test fun auNswSafetyInspectionShipsAsAUserSetDate() {
        // The official page for the pink slip could not be verified, so no rule is invented for it.
        val safety = preset("AU_NSW_SAFETY")
        assertTrue(safety.schedule is PresetSchedule.UserSet)
        assertNull(RegionPresets.nextDue(safety, d(2015, 1, 1), null, d(2026, 6, 1)))
    }

    // ---- generic ----

    @Test fun genericPresetsNeedADate() {
        val presets = RegionPresets.presetsFor(Region.GENERIC)
        assertTrue(presets.any { it.type == DocumentType.INSPECTION })
        presets.filter { it.schedule is PresetSchedule.UserSet }.forEach {
            assertNull(RegionPresets.plan(it, d(2020, 1, 1), null, d(2026, 6, 1)).expiry)
        }
        assertEquals(DocumentAlerts.DEFAULT_DAYS, RegionPresets.plan(presets.first(), null, null, d(2026, 6, 1)).alertDays)
    }

    // ---- table and region default ----

    @Test fun everyRegionOffersInsuranceAndLicenceAndUniqueCodes() {
        Region.entries.forEach { region ->
            val codes = RegionPresets.presetsFor(region).map { it.code }
            assertTrue("$region has insurance", DocumentType.INSURANCE in RegionPresets.presetsFor(region).map { it.type })
            assertTrue("$region has licence", DocumentType.LICENCE in RegionPresets.presetsFor(region).map { it.type })
            assertTrue(codes.isNotEmpty())
        }
        val all = Region.entries.flatMap { RegionPresets.presetsFor(it) }
        assertEquals(all.size, all.map { it.region.code + it.code }.toSet().size)
        all.forEach { assertNotNull(RegionPresets.find(it.code)) }
    }

    @Test fun ageBasedRulesCarryTheirSource() {
        Region.entries.flatMap { RegionPresets.presetsFor(it) }.filter { it.schedule is PresetSchedule.AgeBased }
            .forEach { assertTrue("${it.code} source", it.source.startsWith("https://")) }
    }

    @Test fun regionComesFromTheDeviceLocaleAndNeverAssumesIndia() {
        assertEquals(Region.GB, RegionPresets.regionFor(Locale.UK))
        assertEquals(Region.DE, RegionPresets.regionFor(Locale.GERMANY))
        assertEquals(Region.FR, RegionPresets.regionFor(Locale.FRANCE))
        assertEquals(Region.ES, RegionPresets.regionFor(Locale.forLanguageTag("es-ES")))
        assertEquals(Region.US_CA, RegionPresets.regionFor(Locale.US))
        assertEquals(Region.AU_NSW, RegionPresets.regionFor(Locale.forLanguageTag("en-AU")))
        assertEquals(Region.GENERIC, RegionPresets.regionFor(Locale.forLanguageTag("hi-IN")))
        assertEquals(Region.GENERIC, RegionPresets.regionFor(Locale.ENGLISH))
        assertEquals(Region.GENERIC, RegionPresets.regionFor(Locale.ROOT))
    }

    @Test fun registrationIsTakenAsTheFirstOfJanuaryOfTheModelYear() {
        assertEquals(d(2019, 1, 1), RegionPresets.registrationFromYear(2019))
        assertNull(RegionPresets.registrationFromYear(null))
        assertNull(RegionPresets.registrationFromYear(1500))
        // DE HU for a 2023 car, as of 15 Jun 2026: first test 1 Jan 2026 passed, so the next is 1 Jan 2028.
        val plans = RegionPresets.plans(listOf(preset("DE_HU")), 2023, d(2026, 6, 15))
        assertEquals(d(2028, 1, 1), plans.single().expiry)
    }
}
