package com.postpole.wheelfile.domain.economy

import com.postpole.wheelfile.data.db.EnergyLog
import com.postpole.wheelfile.data.db.EnergyStream
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.domain.units.UnitConversion

/** Normalises a stored [EnergyLog] to km and L / kWh / kg using the vehicle's units. */
fun EnergyLog.toEntry(vehicle: Vehicle): EnergyEntry = EnergyEntry(
    id = id,
    dateTime = dateTime,
    odometerKm = UnitConversion.toKm(odometer, vehicle.distanceUnit),
    quantity = if (stream == EnergyStream.LIQUID) UnitConversion.toBaseQuantity(quantity, vehicle.volumeUnit) else quantity,
    isFull = isFull,
    missedPrevious = missedPrevious,
)

/** Splits a mixed log (for example a PHEV) into one [EconomyResult] per stream. */
fun computeByStream(logs: List<EnergyLog>, vehicle: Vehicle): Map<EnergyStream, EconomyResult> =
    logs.groupBy { it.stream }.mapValues { (_, list) -> EconomyEngine.compute(list.map { it.toEntry(vehicle) }) }
