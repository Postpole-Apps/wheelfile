package com.postpole.wheelfile.domain.document

import com.postpole.wheelfile.data.db.DocumentType
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Regional document presets (docs/spec.md §2.6). Suggestions only: the user can edit every date, and the UI always
 * labels them "Suggested schedule; check your local authority". This is not legal advice.
 *
 * The table is versioned ([RegionPresets.VERSION]) so a rule change is a visible, reviewed edit.
 */
enum class PresetRegion(val code: String) {
    GB("GB"),
    DE("DE"),
    FR("FR"),
    ES("ES"),
    US_CA("US-CA"),
    AU_NSW("AU-NSW"),
    GENERIC("generic"),
}

/** How a preset turns dates into a due date. */
sealed interface PresetRule {
    /** No rule is encoded: the user sets the date. [repeatMonths] is a renewal interval when one is known. */
    data class UserSet(val repeatMonths: Int? = null) : PresetRule

    /**
     * An inspection that depends on the age of the vehicle, counted from first registration.
     * - first due [firstAfterMonths] after registration, with a [windowMonths] window that ends on the due date
     * - then every [repeatMonths]
     * - once the vehicle was [laterFromAgeMonths] old at the last inspection, every [laterRepeatMonths] instead
     */
    data class AgeBased(
        val firstAfterMonths: Int,
        val repeatMonths: Int,
        val windowMonths: Int = 0,
        val laterFromAgeMonths: Int? = null,
        val laterRepeatMonths: Int? = null,
    ) : PresetRule
}

data class DocumentPreset(
    /** Stable code, also stored on the reminder (`presetCode`). */
    val code: String,
    val region: PresetRegion,
    val type: DocumentType,
    val rule: PresetRule,
    /** Official page the rule was taken from (docs/spec.md §2.6). */
    val source: String? = null,
)

/** A computed due date. [windowStart] is set when the rule allows the inspection from an earlier date (France). */
data class PresetDue(val dueDate: LocalDate?, val windowStart: LocalDate? = null)

object RegionPresets {
    const val VERSION = 1

    private const val MONTHS_PER_YEAR = 12

    val GB_MOT = DocumentPreset(
        "gb_mot", PresetRegion.GB, DocumentType.INSPECTION,
        PresetRule.AgeBased(firstAfterMonths = 36, repeatMonths = 12),
        "https://www.gov.uk/getting-an-mot",
    )
    val GB_VEHICLE_TAX = DocumentPreset(
        "gb_vehicle_tax", PresetRegion.GB, DocumentType.REGISTRATION,
        PresetRule.UserSet(repeatMonths = 12),
        "https://www.gov.uk/vehicle-tax",
    )
    val DE_HU = DocumentPreset(
        "de_hu", PresetRegion.DE, DocumentType.INSPECTION,
        PresetRule.AgeBased(firstAfterMonths = 36, repeatMonths = 24),
        "https://www.gesetze-im-internet.de (StVZO Anlage VIII)",
    )
    val FR_CT = DocumentPreset(
        "fr_ct", PresetRegion.FR, DocumentType.INSPECTION,
        PresetRule.AgeBased(firstAfterMonths = 48, repeatMonths = 24, windowMonths = 6),
        "https://www.service-public.gouv.fr/particuliers/vosdroits/F2878",
    )
    val ES_ITV = DocumentPreset(
        "es_itv", PresetRegion.ES, DocumentType.INSPECTION,
        PresetRule.AgeBased(
            firstAfterMonths = 48, repeatMonths = 24,
            laterFromAgeMonths = 10 * MONTHS_PER_YEAR, laterRepeatMonths = 12,
        ),
        "https://administracion.gob.es (BOE-A-2017-12841)",
    )
    val US_CA_SMOG = DocumentPreset(
        "us_ca_smog", PresetRegion.US_CA, DocumentType.INSPECTION,
        PresetRule.UserSet(),
        "https://www.dmv.ca.gov",
    )
    val AU_NSW_REGO = DocumentPreset(
        "au_nsw_rego", PresetRegion.AU_NSW, DocumentType.REGISTRATION,
        PresetRule.UserSet(repeatMonths = 12),
        "https://www.service.nsw.gov.au",
    )

    /** Safety inspection (a "pink slip" in NSW). Needs a per-state check, so it ships as a user-set date only. */
    val AU_SAFETY_INSPECTION = DocumentPreset(
        "au_safety_inspection", PresetRegion.AU_NSW, DocumentType.INSPECTION,
        PresetRule.UserSet(),
    )
    val INSURANCE_RENEWAL = DocumentPreset(
        "insurance_renewal", PresetRegion.GENERIC, DocumentType.INSURANCE,
        PresetRule.UserSet(repeatMonths = 12),
    )
    val LICENCE_EXPIRY = DocumentPreset(
        "licence_expiry", PresetRegion.GENERIC, DocumentType.LICENCE,
        PresetRule.UserSet(),
    )
    val GENERIC_INSPECTION = DocumentPreset(
        "generic_inspection", PresetRegion.GENERIC, DocumentType.INSPECTION,
        PresetRule.UserSet(),
    )

    private val shared = listOf(INSURANCE_RENEWAL, LICENCE_EXPIRY)

    private val byRegion: Map<PresetRegion, List<DocumentPreset>> = mapOf(
        PresetRegion.GB to listOf(GB_MOT, GB_VEHICLE_TAX) + shared,
        PresetRegion.DE to listOf(DE_HU) + shared,
        PresetRegion.FR to listOf(FR_CT) + shared,
        PresetRegion.ES to listOf(ES_ITV) + shared,
        PresetRegion.US_CA to listOf(US_CA_SMOG) + shared,
        PresetRegion.AU_NSW to listOf(AU_NSW_REGO, AU_SAFETY_INSPECTION) + shared,
        PresetRegion.GENERIC to listOf(GENERIC_INSPECTION) + shared,
    )

    val all: List<DocumentPreset> = byRegion.values.flatten().distinctBy { it.code }

    fun forRegion(region: PresetRegion): List<DocumentPreset> = byRegion.getValue(region)

    fun byCode(code: String): DocumentPreset? = all.firstOrNull { it.code == code }

    /**
     * The region suggested for a device locale. Never assumes a region: an unknown country gets the generic list.
     * A US locale starts at US-CA because California is the only US preset; other states use the generic list.
     */
    fun regionFor(locale: Locale): PresetRegion = when (locale.country.uppercase(Locale.ROOT)) {
        "GB" -> PresetRegion.GB
        "DE" -> PresetRegion.DE
        "FR" -> PresetRegion.FR
        "ES" -> PresetRegion.ES
        "US" -> PresetRegion.US_CA
        "AU" -> PresetRegion.AU_NSW
        else -> PresetRegion.GENERIC
    }

    /** First due date from first registration. Null for user-set presets or without a registration date. */
    fun firstDue(preset: DocumentPreset, firstRegistration: LocalDate?): PresetDue {
        val rule = preset.rule as? PresetRule.AgeBased ?: return PresetDue(null)
        if (firstRegistration == null) return PresetDue(null)
        val due = firstRegistration.plusMonths(rule.firstAfterMonths.toLong())
        return PresetDue(due, due.minusMonths(rule.windowMonths.toLong()).takeIf { rule.windowMonths > 0 })
    }

    /**
     * The due date after an inspection or renewal done on [lastDone]. Intervals count from that date.
     * Null when the rule has no interval (user-set).
     */
    fun nextDue(preset: DocumentPreset, lastDone: LocalDate, firstRegistration: LocalDate?): LocalDate? =
        when (val rule = preset.rule) {
            is PresetRule.UserSet -> rule.repeatMonths?.let { lastDone.plusMonths(it.toLong()) }
            is PresetRule.AgeBased -> {
                val later = rule.laterFromAgeMonths
                val ageMonths = firstRegistration?.let { ChronoUnit.MONTHS.between(it, lastDone) }
                val months = if (later != null && rule.laterRepeatMonths != null && ageMonths != null && ageMonths >= later) {
                    rule.laterRepeatMonths
                } else {
                    rule.repeatMonths
                }
                lastDone.plusMonths(months.toLong())
            }
        }

    /** What to offer as the due date: the next one after [lastDone] when known, otherwise the first one. */
    fun suggestedDue(preset: DocumentPreset, firstRegistration: LocalDate?, lastDone: LocalDate?): PresetDue =
        if (lastDone != null) PresetDue(nextDue(preset, lastDone, firstRegistration)) else firstDue(preset, firstRegistration)
}
