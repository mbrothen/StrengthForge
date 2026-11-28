package com.example.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface RestTimerNotificationAction {
    data class Extend(val addedSeconds: Int = 15, val newRemainingSeconds: Int) : RestTimerNotificationAction
    data object Skip : RestTimerNotificationAction
}

object RestTimerNotificationHelper {

    const val CHANNEL_ID = "rest_timer_alerts"
    const val CHANNEL_ACTIVE_ID = "rest_timer_active"

    const val NOTIFICATION_ID_REST_ACTIVE = 4100
    const val REQUEST_CODE_REST_TIMER = 4101
    const val REQUEST_CODE_HOLD_TIMER = 4102
    const val REQUEST_CODE_ACTION_EXTEND = 4103
    const val REQUEST_CODE_ACTION_SKIP = 4104

    const val ACTION_EXTEND_REST_15S = "com.example.action.EXTEND_REST_15S"
    const val ACTION_SKIP_REST = "com.example.action.SKIP_REST"

    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    const val EXTRA_TITLE = "extra_title"
    const val EXTRA_BODY = "extra_body"

    private const val PREFS_NAME = "strengthforge_rest_timer_prefs"
    private const val KEY_END_ELAPSED_MS = "rest_end_elapsed_ms"
    private const val KEY_WORKOUT_NAME = "rest_workout_name"
    private const val KEY_WAS_SKIPPED = "rest_was_skipped"

    private val _actionFlow = MutableSharedFlow<RestTimerNotificationAction>(extraBufferCapacity = 8)
    val actionFlow: SharedFlow<RestTimerNotificationAction> = _actionFlow.asSharedFlow()

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.rest_timer_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.rest_timer_channel_description)
                enableVibration(true)
                vibrationPattern = longArrayOf(0L, 220L, 140L, 280L)
                setSound(soundUri, audioAttributes)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }

        if (manager.getNotificationChannel(CHANNEL_ACTIVE_ID) == null) {
            val activeChannel = NotificationChannel(
                CHANNEL_ACTIVE_ID,
                context.getString(R.string.rest_timer_active_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.rest_timer_active_channel_description)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(activeChannel)
        }
    }

    fun scheduleRestAlarm(
        context: Context,
        delaySeconds: Int,
        title: String = "Rest Complete",
        body: String = "Time to hit your next working set!",
        workoutName: String = "Workout",
        showLockscreenControls: Boolean = true
    ) {
        val clamped = delaySeconds.coerceIn(1, 900)
        val endElapsedMs = SystemClock.elapsedRealtime() + clamped * 1000L
        saveActiveRestState(context, endElapsedMs, workoutName, skipped = false)

        scheduleAlarm(
            context = context,
            requestCode = REQUEST_CODE_REST_TIMER,
            delaySeconds = clamped,
            title = title,
            body = body
        )

        if (showLockscreenControls) {
            showActiveRestNotification(
                context = context,
                remainingSeconds = clamped,
                workoutName = workoutName
            )
        }
    }

    fun showActiveRestNotification(
        context: Context,
        remainingSeconds: Int,
        workoutName: String = "Workout"
    ) {
        ensureNotificationChannel(context)
        if (!hasNotificationPermission(context) || remainingSeconds <= 0) return

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_REST_ACTIVE,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val extendIntent = Intent(context, RestTimerActionReceiver::class.java).apply {
            action = ACTION_EXTEND_REST_15S
        }
        val extendPendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_ACTION_EXTEND,
            extendIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val skipIntent = Intent(context, RestTimerActionReceiver::class.java).apply {
            action = ACTION_SKIP_REST
        }
        val skipPendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_ACTION_SKIP,
            skipIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val endWallTimeMs = System.currentTimeMillis() + remainingSeconds * 1000L
        val builder = NotificationCompat.Builder(context, CHANNEL_ACTIVE_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Rest Timer · ${workoutName.ifBlank { "Workout" }}")
            .setContentText("Resting between sets (${remainingSeconds}s)")
            .setWhen(endWallTimeMs)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentIntent)
            .addAction(0, "+15s", extendPendingIntent)
            .addAction(0, "Skip", skipPendingIntent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder.setChronometerCountDown(true)
        }

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_REST_ACTIVE, builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun cancelActiveRestNotification(context: Context) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_REST_ACTIVE)
        }
    }

    fun handleLockscreenAction(context: Context, action: String?) {
        when (action) {
            ACTION_EXTEND_REST_15S -> {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val currentEnd = prefs.getLong(KEY_END_ELAPSED_MS, 0L)
                val workoutName = prefs.getString(KEY_WORKOUT_NAME, "Workout").orEmpty().ifBlank { "Workout" }
                val remainingBefore = ((currentEnd - SystemClock.elapsedRealtime() + 999L) / 1000L)
                    .toInt()
                    .coerceAtLeast(0)
                val newRemaining = (remainingBefore + 15).coerceIn(5, 900)
                scheduleRestAlarm(
                    context = context,
                    delaySeconds = newRemaining,
                    title = "Rest Complete",
                    body = "Your ${newRemaining}s rest for $workoutName is done — time for your next set!",
                    workoutName = workoutName,
                    showLockscreenControls = true
                )
                _actionFlow.tryEmit(
                    RestTimerNotificationAction.Extend(
                        addedSeconds = 15,
                        newRemainingSeconds = newRemaining
                    )
                )
            }

            ACTION_SKIP_REST -> {
                cancelRestAlarm(context, markSkipped = true)
                _actionFlow.tryEmit(RestTimerNotificationAction.Skip)
            }
        }
    }

    fun readPersistedEndElapsedMs(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_END_ELAPSED_MS, 0L)
    }

    fun wasPersistedRestSkipped(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_WAS_SKIPPED, false)
    }

    private fun saveActiveRestState(
        context: Context,
        endElapsedMs: Long,
        workoutName: String,
        skipped: Boolean
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putLong(KEY_END_ELAPSED_MS, endElapsedMs)
            .putString(KEY_WORKOUT_NAME, workoutName)
            .putBoolean(KEY_WAS_SKIPPED, skipped)
            .apply()
    }

    fun scheduleHoldAlarm(
        context: Context,
        delaySeconds: Int,
        title: String = "Timed Hold Complete",
        body: String = "Hold target reached — set marked complete!"
    ) {
        scheduleAlarm(
            context = context,
            requestCode = REQUEST_CODE_HOLD_TIMER,
            delaySeconds = delaySeconds,
            title = title,
            body = body
        )
    }

    fun cancelRestAlarm(context: Context, markSkipped: Boolean = false) {
        cancelAlarm(context, REQUEST_CODE_REST_TIMER)
        cancelActiveRestNotification(context)
        saveActiveRestState(context, endElapsedMs = 0L, workoutName = "", skipped = markSkipped)
    }

    fun cancelHoldAlarm(context: Context) {
        cancelAlarm(context, REQUEST_CODE_HOLD_TIMER)
    }

    private fun scheduleAlarm(
        context: Context,
        requestCode: Int,
        delaySeconds: Int,
        title: String,
        body: String
    ) {
        if (delaySeconds <= 0) return
        runCatching {
            ensureNotificationChannel(context)
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val pendingIntent = buildAlarmPendingIntent(
                context = context,
                requestCode = requestCode,
                title = title,
                body = body
            )
            val triggerAt = SystemClock.elapsedRealtime() + delaySeconds * 1000L
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAt,
                pendingIntent
            )
        }
    }

    private fun cancelAlarm(context: Context, requestCode: Int) {
        runCatching {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, RestTimerAlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun buildAlarmPendingIntent(
        context: Context,
        requestCode: Int,
        title: String,
        body: String
    ): PendingIntent {
        val intent = Intent(context, RestTimerAlarmReceiver::class.java).apply {
            putExtra(EXTRA_NOTIFICATION_ID, requestCode)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_BODY, body)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun showTimerFinishedNotification(
        context: Context,
        notificationId: Int = REQUEST_CODE_REST_TIMER,
        title: String = "Rest Complete",
        body: String = "Time to hit your next working set!"
    ) {
        ensureNotificationChannel(context)
        if (notificationId == REQUEST_CODE_REST_TIMER) {
            cancelActiveRestNotification(context)
            saveActiveRestState(context, endElapsedMs = 0L, workoutName = "", skipped = false)
        }
        if (!hasNotificationPermission(context)) return

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .setContentIntent(contentIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
        }
    }
}
