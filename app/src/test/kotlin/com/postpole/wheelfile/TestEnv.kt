package com.postpole.wheelfile

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.postpole.wheelfile.ads.AdsPolicy
import com.postpole.wheelfile.analytics.NoOpAnalytics
import com.postpole.wheelfile.billing.ProStatus
import com.postpole.wheelfile.data.AppDatabase
import com.postpole.wheelfile.data.Clock
import com.postpole.wheelfile.data.LocaleProvider
import com.postpole.wheelfile.data.SettingsRepository
import com.postpole.wheelfile.data.VehicleRepository
import com.postpole.wheelfile.data.db.DistanceUnit
import com.postpole.wheelfile.data.db.EconomyUnit
import com.postpole.wheelfile.data.db.EnergyLog
import com.postpole.wheelfile.data.db.EnergyStream
import com.postpole.wheelfile.data.db.Powertrain
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.data.db.VehicleType
import com.postpole.wheelfile.data.db.VolumeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.Locale

/** In-memory database and fakes shared by the ViewModel and UI tests (run under Robolectric). */
class TestEnv(val locale: Locale = Locale.FRANCE, var now: Long = 1_700_000_000_000L) {
    private val context: Context = ApplicationProvider.getApplicationContext()

    val db: AppDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .setQueryExecutor(Runnable::run)
        .setTransactionExecutor(Runnable::run)
        .build()

    val clock = Clock { now }
    val localeProvider = LocaleProvider { locale }
    val repository = VehicleRepository(db.vehicleDao(), db.energyLogDao(), db.serviceRecordDao(), db.expenseDao(), clock)

    private val proFlow = MutableStateFlow(false)
    val pro = object : ProStatus {
        override val isPro: StateFlow<Boolean> = proFlow
    }

    fun setPro(value: Boolean) {
        proFlow.value = value
    }

    val settings = SettingsRepository(
        PreferenceDataStoreFactory.create { File.createTempFile("settings", ".preferences_pb").also { it.delete() } },
    )

    val adsPolicy = AdsPolicy(clock = { now }, installTimeMs = 0L, isFirstSession = false)
    val analytics = NoOpAnalytics

    fun vehicle(
        name: String = "Golf",
        powertrain: Powertrain = Powertrain.PETROL,
        archived: Boolean = false,
        distance: DistanceUnit = DistanceUnit.KM,
    ) = Vehicle(
        name = name, type = VehicleType.CAR, powertrain = powertrain,
        distanceUnit = distance, volumeUnit = VolumeUnit.L, economyDisplay = EconomyUnit.L_PER_100KM,
        currency = "EUR", archived = archived, createdAt = 1, updatedAt = 1,
    )

    suspend fun addVehicle(name: String = "Golf", archived: Boolean = false, powertrain: Powertrain = Powertrain.PETROL): Long =
        db.vehicleDao().insert(vehicle(name, powertrain, archived))

    suspend fun addLog(
        vehicleId: Long,
        odometer: Double,
        quantity: Double = 40.0,
        full: Boolean = true,
        dateTime: Long = odometer.toLong(),
        stream: EnergyStream = EnergyStream.LIQUID,
    ) = db.energyLogDao().insert(
        EnergyLog(
            vehicleId = vehicleId, stream = stream, dateTime = dateTime, odometer = odometer, quantity = quantity,
            isFull = full, createdAt = 1, updatedAt = 1,
        ),
    )

    fun close() = db.close()
}
