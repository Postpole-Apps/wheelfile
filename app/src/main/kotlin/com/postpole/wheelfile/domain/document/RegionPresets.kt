package com.postpole.wheelfile.domain.document

import com.postpole.wheelfile.data.db.DocumentType
import java.time.LocalDate
import java.util.Locale

/** Regions that ship presets (spec §2.6). [GENERIC] is the fallback for every other country. */
enum class Region(val code: String) {
    GB("GB"),
    DE("DE"),
    FR("FR"),
    ES("ES"),
    US_CA("US-CA"),
    AU_NSW("AU-NSW"),
    GENERIC("generic"),
}

/** How often a preset comes round. */
sealed interface PresetSchedule {
    /**
     * Inspection tied to the vehicle's age: the first one falls [firstAfterMonths] after first registration, then the
     * interval depends on the vehicle's age on the day of the inspection (the entry with the largest `fromAgeMonths`
     * that is not above that age). [windowMonthsBefore] is how long before the deadline the test may be done (FR).
     */
    data class AgeBased(
        val firstAfterMonths: Int,
        val intervals: List<AgeInterval>,
        val windowMonthsBefore: Int = 0,
    ) : PresetSchedule

    /** A renewal that repeats every [everyMonths] from a date the user enters. */
    data class Renewal(val everyMonths: Int) : PresetSchedule

    /** A date the user sets; nothing is predicted. */
    data object UserSet : PresetSchedule
}

data class AgeInterval(val fromAgeMonths: Int, val everyMonths: Int)

/** One suggested document with its rule. [title] is the proper name of the document in that country. */
data class DocumentPreset(
    val code: String,
    val region: Region,
    val title: String,
    val type: DocumentType,
    val schedule: PresetSchedule,
    /** Where the rule comes from (spec §2.6). */
    val source: String,
)

/** What applying a preset produces. [expiry] is null when the rule needs a date the user has not given. */
data class PresetPlan(
    val preset: DocumentPreset,
    val expiry: LocalDate?,
    /** Days before [expiry] to alert; the first entry is the earliest (FR: when the test window opens). */
    val alertDays: List<Int>,
)

/**
 * The versioned table of regional document presets (spec §2.6). These are suggestions, not legal advice: the UI always
 * says "Suggested schedule; check your local authority". Each rule has a unit test in `RegionPresetsTest`.
 */
object RegionPresets {
    const val VERSION = 1

    private const val GB_MOT = "https://www.gov.uk/getting-an-mot"
    private const val GB_TAX = "https://www.gov.uk/vehicle-tax"
    private const val DE_HU = "https://www.gesetze-im-internet.de/stvzo_2012/anlage_viii.html"
    private const val FR_CT = "https://www.service-public.gouv.fr/particuliers/vosdroits/F2878"
    private const val ES_ITV = "https://www.boe.es/buscar/act.php?id=BOE-A-2017-12841"
    private const val CA_SMOG = "https://www.dmv.ca.gov/portal/vehicle-registration/smog-check-and-emissions/"
    private const val NSW_REGO = "https://www.service.nsw.gov.au/transaction/renew-vehicle-registration"

    private val insurance = DocumentPreset(
        "ALL_INSURANCE", Region.GENERIC, "Insurance renewal", DocumentType.INSURANCE, PresetSchedule.UserSet, "",
    )
    private val licence = DocumentPreset(
        "ALL_LICENCE", Region.GENERIC, "Driving licence", DocumentType.LICENCE, PresetSchedule.UserSet, "",
    )

    private fun common(region: Region) = listOf(
        insurance.copy(region = region),
        licence.copy(region = region),
    )

    private val table: Map<Region, List<DocumentPreset>> = mapOf(
        Region.GB to listOf(
            DocumentPreset(
                "GB_MOT", Region.GB, "MOT", DocumentType.INSPECTION,
                PresetSchedule.AgeBased(36, listOf(AgeInterval(0, 12))), GB_MOT,
            ),
            DocumentPreset("GB_TAX", Region.GB, "Vehicle tax", DocumentType.REGISTRATION, PresetSchedule.Renewal(12), GB_TAX),
        ) + common(Region.GB),
        Region.DE to listOf(
            DocumentPreset(
                "DE_HU", Region.DE, "HU (TÜV)", DocumentType.INSPECTION,
                PresetSchedule.AgeBased(36, listOf(AgeInterval(0, 24))), DE_HU,
            ),
        ) + common(Region.DE),
        Region.FR to listOf(
            DocumentPreset(
                "FR_CT", Region.FR, "Contrôle technique", DocumentType.INSPECTION,
                PresetSchedule.AgeBased(48, listOf(AgeInterval(0, 24)), windowMonthsBefore = 6), FR_CT,
            ),
        ) + common(Region.FR),
        Region.ES to listOf(
            DocumentPreset(
                "ES_ITV", Region.ES, "ITV", DocumentType.INSPECTION,
                PresetSchedule.AgeBased(48, listOf(AgeInterval(0, 24), AgeInterval(120, 12))), ES_ITV,
            ),
        ) + common(Region.ES),
        Region.US_CA to listOf(
            DocumentPreset("US_CA_SMOG", Region.US_CA, "Smog check", DocumentType.INSPECTION, PresetSchedule.UserSet, CA_SMOG),
            DocumentPreset("US_CA_REG", Region.US_CA, "Registration", DocumentType.REGISTRATION, PresetSchedule.Renewal(12), CA_SMOG),
        ) + common(Region.US_CA),
        Region.AU_NSW to listOf(
            DocumentPreset("AU_NSW_REGO", Region.AU_NSW, "Registration (rego)", DocumentType.REGISTRATION, PresetSchedule.Renewal(12), NSW_REGO),
            // The NSW page for the safety inspection ("pink slip") could not be verified, so it ships as a user-set date.
            DocumentPreset("AU_NSW_SAFETY", Region.AU_NSW, "Safety inspection", DocumentType.INSPECTION, PresetSchedule.UserSet, ""),
        ) + common(Region.AU_NSW),
        Region.GENERIC to listOf(
            DocumentPreset("GEN_INSPECTION", Region.GENERIC, "Emissions / inspection", DocumentType.INSPECTION, PresetSchedule.UserSet, ""),
            DocumentPreset("GEN_REGISTRATION", Region.GENERIC, "Registration / road tax", DocumentType.REGISTRATION, PresetSchedule.Renewal(12), ""),
        ) + common(Region.GENERIC),
    )

    fun presetsFor(region: Region): List<DocumentPreset> = table.getValue(region)

    fun find(code: String): DocumentPreset? = table.values.flatten().firstOrNull { it.code == code }

    /** Region offered first, from the device locale. A locale with no preset (or no country) gets the generic list. */
    fun regionFor(locale: Locale): Region = when (locale.country.uppercase()) {
        "GB" -> Region.GB
        "DE" -> Region.DE
        "FR" -> Region.FR
        "ES" -> Region.ES
        "US" -> Region.US_CA
        "AU" -> Region.AU_NSW
        else -> Region.GENERIC
    }

    /**
     * Only the model year is known, so the registration is taken as 1 January of that year. That is the earliest it can
     * be, so a suggested date is never later than the real one.
     */
    fun registrationFromYear(year: Int?): LocalDate? = year?.takeIf { it in 1886..2100 }?.let { LocalDate.of(it, 1, 1) }

    /** Plans for the chosen presets of a vehicle with model [year], as of [today]. */
    fun plans(presets: List<DocumentPreset>, year: Int?, today: LocalDate): List<PresetPlan> =
        presets.map { plan(it, registrationFromYear(year), null, today) }

    /** Date of the first inspection of an age-based preset: [AgeBased.firstAfterMonths] after [registration]. Null for other rules. */
    fun firstDue(preset: DocumentPreset, registration: LocalDate): LocalDate? =
        (preset.schedule as? PresetSchedule.AgeBased)?.let { registration.plusMonths(it.firstAfterMonths.toLong()) }

    /** The due date that follows an inspection or renewal done on [lastDone]. Null for user-set rules. */
    fun nextAfter(preset: DocumentPreset, registration: LocalDate?, lastDone: LocalDate): LocalDate? =
        when (val s = preset.schedule) {
            is PresetSchedule.Renewal -> lastDone.plusMonths(s.everyMonths.toLong())
            is PresetSchedule.AgeBased -> lastDone.plusMonths(intervalAt(s, registration, lastDone).toLong())
            PresetSchedule.UserSet -> null
        }

    /**
     * The next due date as of [today]. With [lastDone] it counts on from there. Without it, an age-based preset walks
     * its schedule from [registration] until a date that is not in the past. Everything else needs [lastDone].
     */
    fun nextDue(preset: DocumentPreset, registration: LocalDate?, lastDone: LocalDate?, today: LocalDate): LocalDate? {
        if (lastDone != null) return nextAfter(preset, registration, lastDone)
        val schedule = preset.schedule as? PresetSchedule.AgeBased ?: return null
        var due = firstDue(preset, registration ?: return null) ?: return null
        var guard = 0
        while (due.isBefore(today) && guard++ < MAX_STEPS) {
            due = due.plusMonths(intervalAt(schedule, registration, due).toLong())
        }
        return due
    }

    /** Builds what to create for [preset]: the due date and the alert days. The window of FR opens 6 months before the deadline. */
    fun plan(preset: DocumentPreset, registration: LocalDate?, lastDone: LocalDate?, today: LocalDate): PresetPlan {
        val due = nextDue(preset, registration, lastDone, today)
        val window = (preset.schedule as? PresetSchedule.AgeBased)?.windowMonthsBefore ?: 0
        val alerts = if (due != null && window > 0) {
            listOf((due.toEpochDay() - due.minusMonths(window.toLong()).toEpochDay()).toInt()) + DocumentAlerts.DEFAULT_DAYS
        } else {
            DocumentAlerts.DEFAULT_DAYS
        }
        return PresetPlan(preset, due, alerts)
    }

    /** Interval after a test on [on]: by the vehicle's age then, or the first interval when the registration date is unknown. */
    private fun intervalAt(schedule: PresetSchedule.AgeBased, registration: LocalDate?, on: LocalDate): Int {
        val ordered = schedule.intervals.sortedBy { it.fromAgeMonths }
        if (registration == null) return ordered.first().everyMonths
        val ageMonths = java.time.temporal.ChronoUnit.MONTHS.between(registration, on).toInt()
        return ordered.lastOrNull { it.fromAgeMonths <= ageMonths }?.everyMonths ?: ordered.first().everyMonths
    }

    private const val MAX_STEPS = 200
}
