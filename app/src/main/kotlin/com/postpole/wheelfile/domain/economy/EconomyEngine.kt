package com.postpole.wheelfile.domain.economy

import com.postpole.wheelfile.data.db.EconomyUnit
import com.postpole.wheelfile.domain.units.UnitConversion
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * One fill-up or charge, already normalised to km and L / kWh / kg.
 * Build it from an `EnergyLog` and its vehicle's units with [toEntry].
 */
data class EnergyEntry(
    val id: Long,
    val dateTime: Long,
    val odometerKm: Double,
    val quantity: Double,
    val isFull: Boolean,
    val missedPrevious: Boolean = false,
)

enum class InvalidReason {
    /** An entry in the segment is flagged "missed previous fill". */
    MISSED_FILL,

    /** Odometer did not increase between the two full entries ("check odometer"). */
    NON_POSITIVE_DISTANCE,
}

/** A full-to-full segment: from one full entry (exclusive) to the next full entry (inclusive). */
data class Segment(
    val startEntryId: Long,
    val endEntryId: Long,
    val distanceKm: Double,
    /** Sum of all entries after the starting full entry up to and including the closing full entry. */
    val quantity: Double,
    val invalidReason: InvalidReason?,
) {
    val isValid: Boolean get() = invalidReason == null

    /** Unrounded L/100km (or kWh/100km, kg/100km); null for an invalid segment. */
    val per100Km: Double? get() = if (isValid) quantity / distanceKm * 100.0 else null
}

data class EconomyResult(
    val segments: List<Segment>,
    /** Entries before the first full entry only form a baseline; they never produce economy. */
    val baselineEntryId: Long?,
) {
    val validSegments: List<Segment> get() = segments.filter { it.isValid }
    val totalDistanceKm: Double get() = validSegments.sumOf { it.distanceKm }
    val totalQuantity: Double get() = validSegments.sumOf { it.quantity }

    /**
     * Period average = Σ quantity / Σ distance over valid segments (not an average of segment averages),
     * unrounded. Null without any valid segment.
     */
    val average100Km: Double? get() = totalDistanceKm.takeIf { it > 0.0 }?.let { totalQuantity / it * 100.0 }
}

object EconomyEngine {

    /**
     * Computes the segments for ONE energy stream (spec §4.3). Entries may be passed in any order:
     * they are sorted by odometer, then date, then id, so an out-of-order insert or an odometer
     * correction is handled by simply recomputing.
     */
    fun compute(entries: List<EnergyEntry>): EconomyResult {
        val sorted = entries.sortedWith(compareBy<EnergyEntry> { it.odometerKm }.thenBy { it.dateTime }.thenBy { it.id })
        val firstFull = sorted.indexOfFirst { it.isFull }
        if (firstFull < 0) return EconomyResult(emptyList(), null)

        val segments = mutableListOf<Segment>()
        var start = sorted[firstFull]
        var quantity = 0.0
        var missed = false
        for (i in firstFull + 1 until sorted.size) {
            val e = sorted[i]
            quantity += e.quantity
            missed = missed || e.missedPrevious
            if (!e.isFull) continue
            val distance = e.odometerKm - start.odometerKm
            val reason = when {
                missed -> InvalidReason.MISSED_FILL
                distance <= 0.0 -> InvalidReason.NON_POSITIVE_DISTANCE
                else -> null
            }
            segments += Segment(start.id, e.id, distance, quantity, reason)
            // The baseline resets at the closing full entry whether or not the segment was valid.
            start = e
            quantity = 0.0
            missed = false
        }
        return EconomyResult(segments, sorted[firstFull].id)
    }

    /**
     * Soft warning on save (spec §4.3 point 7): true when [candidate]'s odometer contradicts the
     * date order of [existing], i.e. it is lower than an earlier entry or higher than a later one.
     * [candidate] itself is ignored if already present in [existing] (editing).
     */
    fun odometerLooksWrong(existing: List<EnergyEntry>, candidate: EnergyEntry): Boolean =
        existing.any { other ->
            other.id != candidate.id && when {
                other.dateTime < candidate.dateTime -> other.odometerKm > candidate.odometerKm
                other.dateTime > candidate.dateTime -> other.odometerKm < candidate.odometerKm
                else -> false
            }
        }

    /**
     * Distance driven in a period, from the min and max odometer of all entries in it (spec §4.3 point 9).
     */
    fun periodDistanceKm(odometersKm: List<Double>): Double =
        if (odometersKm.size < 2) 0.0 else odometersKm.max() - odometersKm.min()

    /**
     * Cost per km in minor currency units (spec §4.3 points 8–9): total of energy cost (every stream,
     * so a PHEV combines liquid and electric), service and expenses over the distance driven.
     * Null when no distance was driven.
     */
    fun costPerKmMinor(energyMinor: Long, serviceMinor: Long, expensesMinor: Long, distanceKm: Double): Double? =
        if (distanceKm > 0.0) (energyMinor + serviceMinor + expensesMinor) / distanceKm else null
}

/** Display helpers. The engine never rounds; values are rounded here, once, for display. */
object EconomyDisplay {
    fun value(per100Km: Double, unit: EconomyUnit, fractionDigits: Int = 1): Double? =
        UnitConversion.economyForDisplay(per100Km, unit)?.let { round(it, fractionDigits) }

    fun round(value: Double, fractionDigits: Int): Double {
        val factor = 10.0.pow(fractionDigits)
        return (value * factor).roundToLong() / factor
    }
}
