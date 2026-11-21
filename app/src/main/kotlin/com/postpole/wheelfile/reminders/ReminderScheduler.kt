package com.postpole.wheelfile.reminders

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** Schedules the daily reminder check with WorkManager. No exact alarms; WorkManager survives reboots. */
class ReminderScheduler(private val workManager: WorkManager) {

    constructor(context: Context) : this(WorkManager.getInstance(context))

    /** Keeps one daily job. The first run lands [FIRST_RUN_HOUR] local time so notifications don't arrive at night. */
    fun scheduleDaily(now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()) {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelayMillis(now, zone), TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(DAILY_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** One extra run after quiet hours end. */
    fun scheduleDeferred(delayMillis: Long) {
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMillis.coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniqueWork(DEFERRED_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    /** A run as soon as possible, for example right after a reminder is saved. */
    fun runNow() {
        workManager.enqueueUniqueWork(NOW_WORK, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<ReminderWorker>().build())
    }

    companion object {
        const val DAILY_WORK = "reminders_daily"
        const val DEFERRED_WORK = "reminders_deferred"
        const val NOW_WORK = "reminders_now"
        const val FIRST_RUN_HOUR = 9

        fun initialDelayMillis(now: Long, zone: ZoneId): Long {
            val local = java.time.Instant.ofEpochMilli(now).atZone(zone)
            var first = local.toLocalDate().atTime(FIRST_RUN_HOUR, 0).atZone(zone)
            if (!first.isAfter(local)) first = first.plusDays(1)
            return first.toInstant().toEpochMilli() - now
        }
    }
}
