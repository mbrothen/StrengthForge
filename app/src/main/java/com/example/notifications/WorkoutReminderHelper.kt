package com.example.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.WorkoutRoutine
import com.example.data.model.WorkoutSession
import com.example.data.schedule.Weekday
import com.example.data.schedule.WorkoutScheduleHelper
import java.util.Calendar
import java.util.Date

data class WorkoutReminderPlan(
    val triggerAtMillis: Long,
    val weekday: Weekday,
    val routineLabel: String,
    val isToday: Boolean
)

object WorkoutReminderHelper {

    const val CHANNEL_ID = "workout_day_reminders"
    const val REQUEST_CODE_WORKOUT_REMINDER = 4201
    const val EXTRA_ROUTINE_LABEL = "extra_routine_label"
    const val EXTRA_STREAK = "extra_streak"

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.workout_reminder_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.workout_reminder_channel_description)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    fun formatReminderTime(hour: Int, minute: Int): String {
        val h24 = hour.coerceIn(0, 23)
        val m = minute.coerceIn(0, 59)
        val suffix = if (h24 >= 12) "PM" else "AM"
        val h12 = when {
            h24 == 0 -> 12
            h24 > 12 -> h24 - 12
            else -> h24
        }
        return String.format("%d:%02d %s", h12, m, suffix)
    }

    fun computeNextReminderPlan(
        routines: List<WorkoutRoutine>,
        workouts: List<WorkoutSession>,
        hour: Int,
        minute: Int,
        now: Date = Date()
    ): WorkoutReminderPlan? {
        if (routines.none { it.scheduledDays.isNotEmpty() }) return null

        val todayKey = WorkoutScheduleHelper.normalizedDateKey(now)
        val loggedDateKeys = workouts.mapNotNull { session ->
            session.createdAt?.toDate()?.let { WorkoutScheduleHelper.normalizedDateKey(it) }
                ?: session.dateString.trim().takeIf { it.isNotBlank() }
        }.toSet()
        val workedOutToday = loggedDateKeys.contains(todayKey)

        for (dayOffset in 0..7) {
            if (dayOffset == 0 && workedOutToday) continue

            val candidate = Calendar.getInstance().apply {
                time = now
                add(Calendar.DAY_OF_YEAR, dayOffset)
                set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
                set(Calendar.MINUTE, minute.coerceIn(0, 59))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            if (candidate.timeInMillis <= now.time) continue

            val weekday = WorkoutScheduleHelper.weekdayForDate(candidate.time)
            val matchingRoutines = routines.filter { it.scheduledDays.contains(weekday.isoDay) }
            if (matchingRoutines.isNotEmpty()) {
                val label = matchingRoutines.joinToString(" & ") { it.name }
                return WorkoutReminderPlan(
                    triggerAtMillis = candidate.timeInMillis,
                    weekday = weekday,
                    routineLabel = label,
                    isToday = dayOffset == 0
                )
            }
        }
        return null
    }

    fun scheduleNextWorkoutReminder(
        context: Context,
        routines: List<WorkoutRoutine>,
        workouts: List<WorkoutSession>,
        enabled: Boolean,
        hour: Int,
        minute: Int,
        weeklyStreak: Int,
        now: Date = Date()
    ): WorkoutReminderPlan? {
        if (!enabled || !RestTimerNotificationHelper.hasNotificationPermission(context)) {
            cancelWorkoutReminder(context)
            return null
        }

        val plan = computeNextReminderPlan(
            routines = routines,
            workouts = workouts,
            hour = hour,
            minute = minute,
            now = now
        )
        if (plan == null) {
            cancelWorkoutReminder(context)
            return null
        }

        runCatching {
            ensureNotificationChannel(context)
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return@runCatching
            val intent = Intent(context, WorkoutReminderReceiver::class.java).apply {
                putExtra(EXTRA_ROUTINE_LABEL, plan.routineLabel)
                putExtra(EXTRA_STREAK, weeklyStreak)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_WORKOUT_REMINDER,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                plan.triggerAtMillis,
                pendingIntent
            )
        }
        return plan
    }

    fun cancelWorkoutReminder(context: Context) {
        runCatching {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, WorkoutReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_WORKOUT_REMINDER,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun showWorkoutReminderNotification(
        context: Context,
        routineLabel: String,
        weeklyStreak: Int
    ) {
        ensureNotificationChannel(context)
        if (!RestTimerNotificationHelper.hasNotificationPermission(context)) return

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_WORKOUT_REMINDER,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val resolvedRoutine = routineLabel.ifBlank { "Scheduled Workout" }
        val bodyText = if (weeklyStreak > 0) {
            "Scheduled for today · Keep your ${weeklyStreak}-week streak alive!"
        } else {
            "Scheduled for today · Log your first set and build your weekly streak!"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Today's Workout: $resolvedRoutine")
            .setContentText(bodyText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(REQUEST_CODE_WORKOUT_REMINDER, notification)
        } catch (_: SecurityException) {
        }
    }
}
