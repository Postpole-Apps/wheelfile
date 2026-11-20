package com.postpole.wheelfile.data

import com.postpole.wheelfile.data.db.EnergyLog
import com.postpole.wheelfile.data.db.EnergyLogDao
import com.postpole.wheelfile.data.db.Expense
import com.postpole.wheelfile.data.db.ExpenseDao
import com.postpole.wheelfile.data.db.ServiceRecord
import com.postpole.wheelfile.data.db.ServiceRecordDao
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.data.db.VehicleDao
import com.postpole.wheelfile.domain.vehicle.VehicleCap
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AddVehicleResult {
    data class Added(val id: Long) : AddVehicleResult

    /** The free cap of [VehicleCap.FREE_ACTIVE_VEHICLES] active vehicles is reached. */
    data object LimitReached : AddVehicleResult
}

/** Vehicles, enforcing the free-tier cap of two active vehicles (archived ones do not count). */
@Singleton
class VehicleRepository @Inject constructor(
    private val vehicles: VehicleDao,
    private val energy: EnergyLogDao,
    private val services: ServiceRecordDao,
    private val expenses: ExpenseDao,
    private val clock: Clock = Clock.System,
) {
    fun observeAll(): Flow<List<Vehicle>> = vehicles.observeAll()

    fun observe(id: Long): Flow<Vehicle?> = vehicles.observe(id)

    fun observeActiveCount(): Flow<Int> = vehicles.observeActiveCount()

    fun observeEnergyLogs(vehicleId: Long): Flow<List<EnergyLog>> = energy.observeForVehicle(vehicleId)

    fun observeServices(vehicleId: Long): Flow<List<ServiceRecord>> = services.observeForVehicle(vehicleId)

    fun observeExpenses(vehicleId: Long): Flow<List<Expense>> = expenses.observeForVehicle(vehicleId)

    suspend fun get(id: Long): Vehicle? = vehicles.get(id)

    suspend fun energyLogs(vehicleId: Long): List<EnergyLog> =
        energy.getAllForVehicle(vehicleId)

    /** Adds [vehicle] unless it would be a 3rd active vehicle on the free tier. */
    suspend fun add(vehicle: Vehicle, isPro: Boolean): AddVehicleResult {
        if (!vehicle.archived && !VehicleCap.canAddActive(vehicles.activeCount(), isPro)) {
            return AddVehicleResult.LimitReached
        }
        val now = clock.now()
        return AddVehicleResult.Added(vehicles.insert(vehicle.copy(createdAt = now, updatedAt = now)))
    }

    suspend fun archive(id: Long) {
        val vehicle = vehicles.get(id) ?: return
        vehicles.update(vehicle.copy(archived = true, updatedAt = clock.now()))
    }

    /** Returns false (and changes nothing) when restoring would exceed the free cap. */
    suspend fun unarchive(id: Long, isPro: Boolean): Boolean {
        val vehicle = vehicles.get(id) ?: return false
        if (!vehicle.archived) return true
        if (!VehicleCap.canUnarchive(vehicles.activeCount(), isPro)) return false
        vehicles.update(vehicle.copy(archived = false, updatedAt = clock.now()))
        return true
    }

    /** Saves a fill-up or charge. Archived vehicles are read-only, so this returns false for them. */
    suspend fun addEnergyLog(log: EnergyLog): Boolean {
        val vehicle = vehicles.get(log.vehicleId) ?: return false
        if (vehicle.archived) return false
        val now = clock.now()
        energy.insert(log.copy(createdAt = now, updatedAt = now))
        return true
    }
}

/** Device locale, replaceable in tests. */
fun interface LocaleProvider {
    fun locale(): java.util.Locale
}

/** Time source, replaceable in tests. */
fun interface Clock {
    fun now(): Long

    companion object {
        val System = Clock { java.lang.System.currentTimeMillis() }
    }
}
