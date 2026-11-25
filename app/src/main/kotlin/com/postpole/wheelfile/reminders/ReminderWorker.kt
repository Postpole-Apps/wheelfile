package com.postpole.wheelfile.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.postpole.wheelfile.ads.DataStoreReminderFiredStore
import com.postpole.wheelfile.data.SettingsRepository
import com.postpole.wheelfile.data.db.ReminderDao
import com.postpole.wheelfile.data.db.VehicleDao
import com.postpole.wheelfile.data.db.VehicleDocumentDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderWorkerEntryPoint {
    fun reminderDao(): ReminderDao
    fun vehicleDao(): VehicleDao
    fun documentDao(): VehicleDocumentDao
    fun settings(): SettingsRepository
    fun notifier(): AndroidReminderNotifier
    fun dataStore(): DataStore<Preferences>
}

/**
 * Daily check for due reminders (date, odometer, whichever first). Inexact by design: no exact alarms.
 * When quiet hours block a run it queues one more run for when they end.
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, ReminderWorkerEntryPoint::class.java)
        val checker = ReminderChecker(
            reminders = deps.reminderDao(),
            vehicles = deps.vehicleDao(),
            notifier = deps.notifier(),
            quietHours = { deps.settings().quietHours.first() },
            clock = System::currentTimeMillis,
            firedStore = DataStoreReminderFiredStore(deps.dataStore()),
            documentAlerts = { deps.documentDao().getAll().associate { it.id to it.alertDays } },
        )
        val result = checker.run()
        result.deferUntil?.let { ReminderScheduler(applicationContext).scheduleDeferred(it - System.currentTimeMillis()) }
        return Result.success()
    }
}
