package com.postpole.wheelfile.domain.economy

import com.postpole.wheelfile.data.db.EnergyLog
import com.postpole.wheelfile.data.db.EnergyStream
import com.postpole.wheelfile.data.db.Expense
import com.postpole.wheelfile.data.db.ServiceRecord
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.domain.fillup.PriceMath
import com.postpole.wheelfile.domain.units.UnitConversion
import java.util.Currency

/** How the latest full-to-full segment compares with the average of the segments before it. */
data class EconomyDelta(
    /** Absolute relative change in percent, rounded to one decimal. */
    val percent: Double,
    /** True when the latest segment used less energy per distance than the earlier average. */
    val better: Boolean,
)

/** Economy of one energy stream, in base units per 100 km (L, kWh or kg). Display conversion happens in the UI. */
data class StreamEconomy(
    val stream: EnergyStream,
    val result: EconomyResult,
    val lastValidSegment: Segment?,
    val average100Km: Double?,
    val delta: EconomyDelta?,
) {
    val last100Km: Double? get() = lastValidSegment?.per100Km
}

data class CostSummary(
    val costPerKmMinor: Double?,
    val totalMinor: Long,
    val perMonthMinor: Long?,
)

object VehicleSummary {

    fun streams(logs: List<EnergyLog>, vehicle: Vehicle): List<StreamEconomy> =
        computeByStream(logs, vehicle).entries
            .sortedBy { it.key.ordinal }
            .map { (stream, result) -> streamEconomy(stream, result) }

    fun streamEconomy(stream: EnergyStream, result: EconomyResult): StreamEconomy {
        val valid = result.validSegments
        val last = valid.lastOrNull()
        val earlier = valid.dropLast(1)
        val delta = if (last != null && earlier.isNotEmpty()) {
            val earlierAvg = earlier.sumOf { it.quantity } / earlier.sumOf { it.distanceKm } * 100.0
            val change = (last.per100Km!! - earlierAvg) / earlierAvg * 100.0
            EconomyDelta(PriceMath.round(kotlin.math.abs(change), 1), better = change < 0.0)
        } else {
            null
        }
        return StreamEconomy(stream, result, last, result.average100Km, delta)
    }

    /** Highest odometer reading across all entries of the vehicle, in the vehicle's distance unit. */
    fun latestOdometer(logs: List<EnergyLog>): Double? = logs.maxOfOrNull { it.odometer }

    /**
     * Costs over the last 12 months before [now] (the free window): energy, service and expenses.
     * Cost per distance follows spec §4.3 point 9 (min/max odometer across the period).
     */
    fun costs(
        vehicle: Vehicle,
        logs: List<EnergyLog>,
        services: List<ServiceRecord>,
        expenses: List<Expense>,
        now: Long,
    ): CostSummary {
        val windowStart = now - TWELVE_MONTHS_MS
        val digits = fractionDigits(vehicle.currency)
        val energyInWindow = logs.filter { it.dateTime in windowStart..now }
        val energyMinor = energyInWindow.sumOf { it.totalMinor ?: derivedTotal(it, digits) }
        val serviceInWindow = services.filter { it.dateTime in windowStart..now }
        val expenseInWindow = expenses.filter { it.dateTime in windowStart..now }
        val serviceMinor = serviceInWindow.sumOf { it.totalMinor ?: 0L }
        val expenseMinor = expenseInWindow.sumOf { it.amountMinor }
        val total = energyMinor + serviceMinor + expenseMinor

        val odometersKm = energyInWindow.map { UnitConversion.toKm(it.odometer, vehicle.distanceUnit) } +
            serviceInWindow.mapNotNull { it.odometer }.map { UnitConversion.toKm(it, vehicle.distanceUnit) } +
            expenseInWindow.mapNotNull { it.odometer }.map { UnitConversion.toKm(it, vehicle.distanceUnit) }
        val distanceKm = EconomyEngine.periodDistanceKm(odometersKm)
        val perKm = EconomyEngine.costPerKmMinor(energyMinor, serviceMinor, expenseMinor, distanceKm)

        val dates = energyInWindow.map { it.dateTime } + serviceInWindow.map { it.dateTime } + expenseInWindow.map { it.dateTime }
        val perMonth = dates.minOrNull()?.let { first ->
            val months = ((now - first) / MONTH_MS).coerceIn(1L, 12L)
            total / months
        }
        return CostSummary(perKm, total, perMonth)
    }

    private fun derivedTotal(log: EnergyLog, digits: Int): Long {
        val price = log.pricePerUnitMinor ?: return 0L
        return PriceMath.toMinor(PriceMath.fromMinor(price, digits) * log.quantity, digits)
    }

    fun fractionDigits(currencyCode: String): Int =
        runCatching { Currency.getInstance(currencyCode).defaultFractionDigits }.getOrDefault(2).coerceAtLeast(0)

    private const val MONTH_MS = 30L * 24 * 60 * 60 * 1_000
    private const val TWELVE_MONTHS_MS = 365L * 24 * 60 * 60 * 1_000
}
