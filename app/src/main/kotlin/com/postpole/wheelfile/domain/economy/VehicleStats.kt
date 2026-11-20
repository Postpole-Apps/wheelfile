package com.postpole.wheelfile.domain.economy

import com.postpole.wheelfile.data.db.DistanceUnit
import com.postpole.wheelfile.data.db.EconomyUnit
import com.postpole.wheelfile.data.db.EnergyLog
import com.postpole.wheelfile.data.db.EnergyStream
import com.postpole.wheelfile.data.db.Expense
import com.postpole.wheelfile.data.db.Powertrain
import com.postpole.wheelfile.data.db.ServiceRecord
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.domain.units.UnitConstants
import com.postpole.wheelfile.domain.units.UnitConversion
import kotlin.math.abs

/** Economy of one energy stream, already converted for display. */
data class StreamEconomy(
    val stream: EnergyStream,
    val unit: EconomyUnit,
    /** Average over all valid segments (Σ quantity / Σ distance), rounded for display; null without a valid segment. */
    val average: Double?,
    /** Economy of the most recent valid segment, rounded for display. */
    val latest: Double?,
    /** Latest vs average: negative means the latest segment used less energy per distance (better). */
    val deltaPercent: Double?,
    val validSegments: Int,
    val invalidSegments: Int,
    /** Distance and quantity behind the average, for the "How calculated?" sheet (km, and L / kWh / kg). */
    val totalDistanceKm: Double,
    val totalQuantity: Double,
)

data class CostSummary(
    val energyMinor: Long,
    val serviceMinor: Long,
    val expensesMinor: Long,
    /** Per vehicle distance unit (km or mi), in minor units; null without distance. */
    val costPerDistanceMinor: Double?,
) {
    val totalMinor: Long get() = energyMinor + serviceMinor + expensesMinor
}

data class VehicleStats(
    /** Highest odometer reading, in the vehicle's distance unit. */
    val odometer: Double?,
    val streams: List<StreamEconomy>,
    val costs: CostSummary,
    val entryCount: Int,
)

object VehicleStatsCalculator {

    /** The electric-stream display unit: the vehicle's own for an EV, derived from the distance unit for a PHEV. */
    fun electricUnit(vehicle: Vehicle): EconomyUnit = when {
        vehicle.powertrain == Powertrain.ELECTRIC && vehicle.economyDisplay in electricUnits -> vehicle.economyDisplay
        vehicle.distanceUnit == DistanceUnit.MI -> EconomyUnit.MI_PER_KWH
        else -> EconomyUnit.KWH_PER_100KM
    }

    private val electricUnits = setOf(EconomyUnit.KWH_PER_100KM, EconomyUnit.MI_PER_KWH, EconomyUnit.KWH_PER_100MI)

    fun displayUnit(vehicle: Vehicle, stream: EnergyStream): EconomyUnit = when (stream) {
        EnergyStream.ELECTRIC -> electricUnit(vehicle)
        EnergyStream.GAS_KG -> EconomyUnit.KG_PER_100KM
        EnergyStream.LIQUID -> vehicle.economyDisplay.takeIf { it !in electricUnits && it != EconomyUnit.KG_PER_100KM }
            ?: EconomyUnit.L_PER_100KM
    }

    fun compute(
        vehicle: Vehicle,
        logs: List<EnergyLog>,
        services: List<ServiceRecord> = emptyList(),
        expenses: List<Expense> = emptyList(),
    ): VehicleStats {
        val byStream = computeByStream(logs, vehicle)
        val streams = byStream.entries
            .sortedBy { it.key.ordinal }
            .map { (stream, result) -> streamEconomy(vehicle, stream, result) }

        val odometersKm = logs.map { UnitConversion.toKm(it.odometer, vehicle.distanceUnit) } +
            services.mapNotNull { s -> s.odometer?.let { UnitConversion.toKm(it, vehicle.distanceUnit) } } +
            expenses.mapNotNull { e -> e.odometer?.let { UnitConversion.toKm(it, vehicle.distanceUnit) } }
        val distanceKm = EconomyEngine.periodDistanceKm(odometersKm)
        val energy = logs.sumOf { it.totalMinor ?: 0L }
        val service = services.sumOf { it.totalMinor ?: 0L }
        val expense = expenses.sumOf { it.amountMinor }
        val perKm = EconomyEngine.costPerKmMinor(energy, service, expense, distanceKm)
        val perDistance = perKm?.let { if (vehicle.distanceUnit == DistanceUnit.MI) it * UnitConstants.KM_PER_MILE else it }
        return VehicleStats(
            odometer = logs.maxOfOrNull { it.odometer },
            streams = streams,
            costs = CostSummary(energy, service, expense, perDistance),
            entryCount = logs.size,
        )
    }

    private fun streamEconomy(vehicle: Vehicle, stream: EnergyStream, result: EconomyResult): StreamEconomy {
        val unit = displayUnit(vehicle, stream)
        val valid = result.validSegments
        val averageBase = result.average100Km
        val latestBase = valid.lastOrNull()?.per100Km
        val delta = if (averageBase != null && latestBase != null && valid.size >= 2 && averageBase > 0.0) {
            (latestBase - averageBase) / averageBase * 100.0
        } else {
            null
        }
        return StreamEconomy(
            stream = stream,
            unit = unit,
            average = averageBase?.let { EconomyDisplay.value(it, unit) },
            latest = latestBase?.let { EconomyDisplay.value(it, unit) },
            deltaPercent = delta?.let { EconomyDisplay.round(it, 1) },
            validSegments = valid.size,
            invalidSegments = result.segments.size - valid.size,
            totalDistanceKm = result.totalDistanceKm,
            totalQuantity = result.totalQuantity,
        )
    }

    /** True when [delta] is worth a pill (a change under 0.1% is shown as no change). */
    fun isMeaningful(delta: Double?): Boolean = delta != null && abs(delta) >= 0.1
}
