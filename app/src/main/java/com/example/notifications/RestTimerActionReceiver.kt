package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class RestTimerActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        RestTimerNotificationHelper.handleLockscreenAction(context, intent?.action)
    }
}
