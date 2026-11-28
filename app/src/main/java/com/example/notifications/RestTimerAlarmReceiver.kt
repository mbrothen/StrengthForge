package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class RestTimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val notificationId = intent?.getIntExtra(
            RestTimerNotificationHelper.EXTRA_NOTIFICATION_ID,
            RestTimerNotificationHelper.REQUEST_CODE_REST_TIMER
        ) ?: RestTimerNotificationHelper.REQUEST_CODE_REST_TIMER

        val title = intent?.getStringExtra(RestTimerNotificationHelper.EXTRA_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?: "Rest Complete"

        val body = intent?.getStringExtra(RestTimerNotificationHelper.EXTRA_BODY)
            ?.takeIf { it.isNotBlank() }
            ?: "Time to hit your next working set!"

        RestTimerNotificationHelper.showTimerFinishedNotification(
            context = context,
            notificationId = notificationId,
            title = title,
            body = body
        )
    }
}
