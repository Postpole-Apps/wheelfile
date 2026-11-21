package com.postpole.wheelfile.domain.document

import com.postpole.wheelfile.data.db.DocumentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

/** One group of tests per region of docs/spec.md §2.6. The rules are suggestions, not legal advice. */
class RegionPresetsTest {
    private fun d(s: String) = LocalDate.parse(s)

    // ---- GB ----

    @Test fun gbMotIsDueAtThreeYearsThenEveryYear() {
        val first = RegionPresets.firstDue(RegionPresets.GB_MOT, d("2023-09-01"))
        assertEquals(d("2026-09-01"), first.dueDate)
        assertNull(first.windowStart)
        assertEquals(d("2027-08-20"), RegionPresets.nextDue(RegionPresets.GB_MOT, d("2026-08-20"), d("2023-09-01")))
        assertEquals(d("2028-08-20"), RegionPresets.nextDue(RegionPresets.GB_MOT, d("2027-08-20"), d("2023-09-01")))
    }

    @Test fun gbVehicleTaxIsAUserSetDateRenewedYearly() {
        assertNull(RegionPresets.firstDue(RegionPresets.GB_VEHICLE_TAX, d("2023-09-01")).dueDate)
        assertEquals(d("2027-04-01"), RegionPresets.nextDue(RegionPresets.GB_VEHICLE_TAX, d("2026-04-01"), null))
    }

    // ---- DE ----

    @Test fun deHuIsDueAt36MonthsThenEvery24() {
        assertEquals(d("2027-03-15"), RegionPresets.firstDue(RegionPresets.DE_HU, d("2024-03-15")).dueDate)
        assertEquals(d("2029-03-10"), RegionPresets.nextDue(RegionPresets.DE_HU, d("2027-03-10"), d("2024-03-15")))
        assertEquals(d("2031-03-10"), RegionPresets.nextDue(RegionPresets.DE_HU, d("2029-03-10"), d("2024-03-15")))
    }

    // ---- FR ----

    @Test fun frControleTechniqueIsDueBeforeTheFourthAnniversaryThenEveryTwoYears() {
        val first = RegionPresets.firstDue(RegionPresets.FR_CT, d("2022-06-30"))
        assertEquals(d("2026-06-30"), first.dueDate)
        // The test may be done in the 6 months before the 4th anniversary.
        assertEquals(d("2025-12-30"), first.windowStart)
        assertEquals(d("2028-05-01"), RegionPresets.nextDue(RegionPresets.FR_CT, d("2026-05-01"), d("2022-06-30")))
    }

    // ---- ES ----

    @Test fun esItvFirstDueAtFourYears() {
        assertEquals(d("2024-01-31"), RegionPresets.firstDue(RegionPresets.ES_ITV, d("2020-01-31")).dueDate)
    }

    @Test fun esItvIsEveryTwoYearsUpToTenYearsOldThenYearly() {
        val registered = d("2012-05-10")
        // 4 to 10 years: every 2 years.
        assertEquals(d("2018-05-09"), RegionPresets.nextDue(RegionPresets.ES_ITV, d("2016-05-09"), registered))
        // Still under 10 years old at the inspection (119 months): 2 years.
        assertEquals(d("2024-05-09"), RegionPresets.nextDue(RegionPresets.ES_ITV, d("2022-05-09"), registered))
        // Exactly 10 years old (120 months) and older: every year.
        assertEquals(d("2023-05-10"), RegionPresets.nextDue(RegionPresets.ES_ITV, d("2022-05-10"), registered))
        assertEquals(d("2027-02-01"), RegionPresets.nextDue(RegionPresets.ES_ITV, d("2026-02-01"), registered))
    }

    @Test fun esItvWithoutRegistrationDateFallsBackToTwoYears() {
        assertEquals(d("2028-01-15"), RegionPresets.nextDue(RegionPresets.ES_ITV, d("2026-01-15"), null))
    }

    // ---- US-CA ----

    @Test fun usCaliforniaSmogCheckIsAUserSetDate() {
        assertEquals(PresetRule.UserSet(), RegionPresets.US_CA_SMOG.rule)
        assertNull(RegionPresets.firstDue(RegionPresets.US_CA_SMOG, d("2020-01-01")).dueDate)
        assertNull(RegionPresets.nextDue(RegionPresets.US_CA_SMOG, d("2026-01-01"), d("2020-01-01")))
    }

    // ---- AU-NSW ----

    @Test fun auNswRegistrationRenewsYearly() {
        assertNull(RegionPresets.firstDue(RegionPresets.AU_NSW_REGO, d("2020-01-01")).dueDate)
        assertEquals(d("2027-02-01"), RegionPresets.nextDue(RegionPresets.AU_NSW_REGO, d("2026-02-01"), null))
    }

    @Test fun auSafetyInspectionShipsAsUserSetDateOnly() {
        assertEquals(PresetRule.UserSet(), RegionPresets.AU_SAFETY_INSPECTION.rule)
        assertNull(RegionPresets.nextDue(RegionPresets.AU_SAFETY_INSPECTION, d("2026-02-01"), null))
    }

    // ---- generic ----

    @Test fun genericPresetsAreUserSet() {
        val generic = RegionPresets.forRegion(PresetRegion.GENERIC)
        assertTrue(generic.isNotEmpty())
        generic.forEach { assertTrue("${it.code} must be user-set", it.rule is PresetRule.UserSet) }
        assertEquals(d("2027-03-05"), RegionPresets.nextDue(RegionPresets.INSURANCE_RENEWAL, d("2026-03-05"), null))
        assertNull(RegionPresets.nextDue(RegionPresets.LICENCE_EXPIRY, d("2026-03-05"), null))
        assertNull(RegionPresets.nextDue(RegionPresets.GENERIC_INSPECTION, d("2026-03-05"), null))
    }

    // ---- shared behaviour ----

    @Test fun everyRegionOffersInsuranceAndLicence() {
        PresetRegion.entries.forEach { region ->
            val codes = RegionPresets.forRegion(region).map { it.code }
            assertTrue("$region insurance", "insurance_renewal" in codes)
            assertTrue("$region licence", "licence_expiry" in codes)
        }
    }

    @Test fun presetCodesAreUniqueAndResolvable() {
        assertEquals(RegionPresets.all.size, RegionPresets.all.map { it.code }.toSet().size)
        RegionPresets.all.forEach { assertNotNull(RegionPresets.byCode(it.code)) }
    }

    @Test fun inspectionPresetsUseTheInspectionDocumentType() {
        listOf(RegionPresets.GB_MOT, RegionPresets.DE_HU, RegionPresets.FR_CT, RegionPresets.ES_ITV, RegionPresets.US_CA_SMOG)
            .forEach { assertEquals(it.code, DocumentType.INSPECTION, it.type) }
    }

    @Test fun ageBasedPresetsNeedARegistrationDate() {
        assertNull(RegionPresets.firstDue(RegionPresets.DE_HU, null).dueDate)
    }

    @Test fun suggestedDueUsesTheLastDoneDateWhenKnown() {
        assertEquals(d("2027-03-15"), RegionPresets.suggestedDue(RegionPresets.DE_HU, d("2024-03-15"), null).dueDate)
        assertEquals(d("2029-03-10"), RegionPresets.suggestedDue(RegionPresets.DE_HU, d("2024-03-15"), d("2027-03-10")).dueDate)
    }

    @Test fun monthEndDatesClampInsteadOfOverflowing() {
        // 29 Feb 2020 + 36 months has no 29 Feb in 2023.
        assertEquals(d("2023-02-28"), RegionPresets.firstDue(RegionPresets.GB_MOT, d("2020-02-29")).dueDate)
    }

    // ---- region from the device locale ----

    @Test fun regionComesFromTheDeviceLocale() {
        assertEquals(PresetRegion.GB, RegionPresets.regionFor(Locale.UK))
        assertEquals(PresetRegion.DE, RegionPresets.regionFor(Locale.GERMANY))
        assertEquals(PresetRegion.FR, RegionPresets.regionFor(Locale.FRANCE))
        assertEquals(PresetRegion.ES, RegionPresets.regionFor(Locale.forLanguageTag("es-ES")))
        assertEquals(PresetRegion.US_CA, RegionPresets.regionFor(Locale.US))
        assertEquals(PresetRegion.AU_NSW, RegionPresets.regionFor(Locale.forLanguageTag("en-AU")))
    }

    @Test fun unknownOrMissingCountriesGetTheGenericListNeverIndia() {
        assertEquals(PresetRegion.GENERIC, RegionPresets.regionFor(Locale.forLanguageTag("en-IN")))
        assertEquals(PresetRegion.GENERIC, RegionPresets.regionFor(Locale.forLanguageTag("hi-IN")))
        assertEquals(PresetRegion.GENERIC, RegionPresets.regionFor(Locale.ENGLISH))
        assertEquals(PresetRegion.GENERIC, RegionPresets.regionFor(Locale.ROOT))
    }
}
