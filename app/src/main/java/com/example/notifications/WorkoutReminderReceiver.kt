package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class WorkoutReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val routineLabel = intent?.getStringExtra(WorkoutReminderHelper.EXTRA_ROUTINE_LABEL)
            ?.takeIf { it.isNotBlank() }
            ?: "Scheduled Workout"
        val streak = intent?.getIntExtra(WorkoutReminderHelper.EXTRA_STREAK, 0) ?: 0

        WorkoutReminderHelper.showWorkoutReminderNotification(
            context = context,
            routineLabel = routineLabel,
            weeklyStreak = streak
        )
    }
}
