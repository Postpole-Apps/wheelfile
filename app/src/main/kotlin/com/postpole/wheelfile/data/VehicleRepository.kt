package com.postpole.wheelfile.data

import com.postpole.wheelfile.data.db.EnergyLog
import com.postpole.wheelfile.data.db.EnergyLogDao
import com.postpole.wheelfile.data.db.Expense
import com.postpole.wheelfile.data.db.ExpenseDao
import com.postpole.wheelfile.data.db.ServiceRecord
import com.postpole.wheelfile.data.db.ServiceRecordDao
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.data.db.VehicleDao
import com.postpole.wheelfile.domain.economy.VehicleStats
import com.postpole.wheelfile.domain.economy.VehicleStatsCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/** Everything the screens need about one vehicle, recomputed whenever any of its rows change. */
data class VehicleData(
    val vehicle: Vehicle,
    val logs: List<EnergyLog>,
    val services: List<ServiceRecord>,
    val expenses: List<Expense>,
    val stats: VehicleStats,
)

/** Thin layer over the DAOs so view models share one way to read stats and to archive vehicles. */
@Singleton
class VehicleRepository @Inject constructor(
    val vehicles: VehicleDao,
    val energyLogs: EnergyLogDao,
    private val services: ServiceRecordDao,
    private val expenses: ExpenseDao,
) {
    fun observeData(vehicle: Vehicle): Flow<VehicleData> = combine(
        energyLogs.observeForVehicle(vehicle.id),
        services.observeForVehicle(vehicle.id),
        expenses.observeForVehicle(vehicle.id),
    ) { logs, svc, exp ->
        VehicleData(vehicle, logs, svc, exp, VehicleStatsCalculator.compute(vehicle, logs, svc, exp))
    }

    /** Saved fill-ups, charges, services and expenses across all vehicles (the `entries_milestone` count). */
    suspend fun totalEntryCount(): Int = energyLogs.countAll() + services.countAll() + expenses.countAll()

    suspend fun setArchived(id: Long, archived: Boolean, now: Long) {
        val v = vehicles.get(id) ?: return
        vehicles.update(v.copy(archived = archived, updatedAt = now))
    }
}
