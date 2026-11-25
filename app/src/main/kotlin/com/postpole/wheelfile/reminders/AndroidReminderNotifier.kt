package com.postpole.wheelfile.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.postpole.wheelfile.MainActivity
import com.postpole.wheelfile.R
import com.postpole.wheelfile.data.db.Reminder
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.domain.reminder.ReminderState
import com.postpole.wheelfile.domain.reminder.ReminderStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToLong

/** Posts reminders on the "Reminders" channel. Without the notification permission it does nothing: reminders stay in-app. */
class AndroidReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReminderNotifier {

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.reminders_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.reminders_channel_description) },
        )
    }

    @SuppressLint("MissingPermission")
    override fun post(reminder: Reminder, vehicle: Vehicle, state: ReminderState): Boolean {
        if (!canNotify(context)) return false
        ensureChannel()
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_wheelfile)
            .setContentTitle(reminder.title)
            .setContentText("${vehicle.name} · ${describe(state)}")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(reminder.id.toInt(), notification)
        return true
    }

    private fun describe(state: ReminderState): String {
        val days = state.daysLeft
        val distance = state.distanceLeft
        return when {
            state.status == ReminderStatus.OVERDUE -> context.getString(R.string.reminder_overdue)
            days != null && days <= 0 -> context.getString(R.string.reminder_due_today)
            days != null && (distance == null || distance > 0) && days <= MAX_DAYS_TEXT ->
                context.resources.getQuantityString(R.plurals.reminder_in_days, days.toInt(), days.toInt())
            distance != null -> context.getString(R.string.reminder_about_left, abs(distance).roundToLong())
            else -> context.getString(R.string.reminder_due_soon)
        }
    }

    companion object {
        const val CHANNEL_ID = "reminders"
        private const val MAX_DAYS_TEXT = 365L

        fun canNotify(context: Context): Boolean =
            NotificationManagerCompat.from(context).areNotificationsEnabled() &&
                (
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED
                    )
    }
}
