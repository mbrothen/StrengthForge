package com.example.ui.dashboard

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.example.R
import com.example.data.analytics.WeightUnit
import com.example.data.model.UserProfile
import com.example.data.schedule.Weekday
import com.example.notifications.RestTimerNotificationHelper
import com.example.notifications.WorkoutReminderHelper
import com.example.notifications.WorkoutReminderPlan
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.ElectricTeal
import com.example.ui.theme.GainGreen
import com.example.ui.theme.LossRed
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun openExternalUrl(context: Context, url: String) {
    val result = runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
    if (result.isFailure) {
        Toast.makeText(context, "Unable to open link: $url", Toast.LENGTH_LONG).show()
    }
}

private fun openAppNotificationSettings(context: Context) {
    val result = runCatching {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
        }.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
    if (result.isFailure) {
        Toast.makeText(context, "Open Android Settings to enable notifications.", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountAndPrivacyDialog(
    profileDisplayName: String,
    profile: UserProfile = UserProfile(),
    weightUnit: WeightUnit = WeightUnit.LBS,
    defaultRestSeconds: Int = 90,
    restNotificationsEnabled: Boolean = true,
    firstDayOfWeek: Weekday = Weekday.MONDAY,
    workoutRemindersEnabled: Boolean = false,
    workoutReminderHour: Int = 18,
    workoutReminderMinute: Int = 0,
    nextReminderPlan: WorkoutReminderPlan? = null,
    onWeightUnitChange: (WeightUnit) -> Unit = {},
    onDefaultRestChange: (Int) -> Unit = {},
    onRestNotificationsChange: (Boolean) -> Unit = {},
    onFirstDayOfWeekChange: (Weekday) -> Unit = {},
    onWorkoutRemindersChange: (Boolean) -> Unit = {},
    onWorkoutReminderTimeChange: (Int, Int) -> Unit = { _, _ -> },
    onOpenGoalOnboarding: () -> Unit = {},
    onDismiss: () -> Unit,
    onDeleteUserData: suspend () -> Result<Unit>
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val currentUser = remember { Firebase.auth.currentUser }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var isSigningOut by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    var hasNotificationPermission by remember {
        mutableStateOf(RestTimerNotificationHelper.hasNotificationPermission(context))
    }
    var notificationPermissionDenied by remember { mutableStateOf(false) }
    var pendingNotificationTarget by remember { mutableStateOf("rest") }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted || RestTimerNotificationHelper.hasNotificationPermission(context)
        if (granted) {
            notificationPermissionDenied = false
            RestTimerNotificationHelper.ensureNotificationChannel(context)
            WorkoutReminderHelper.ensureNotificationChannel(context)
            if (pendingNotificationTarget == "workout_reminder") {
                onWorkoutRemindersChange(true)
            } else {
                onRestNotificationsChange(true)
            }
        } else {
            notificationPermissionDenied = true
            if (pendingNotificationTarget == "workout_reminder") {
                onWorkoutRemindersChange(false)
            } else {
                onRestNotificationsChange(false)
            }
            Toast.makeText(
                context,
                "Notification permission is required for background timer and workout reminders.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val resolvedName = currentUser?.displayName?.takeIf { it.isNotBlank() }
        ?: profileDisplayName.ifBlank { "Strength Athlete" }
    val resolvedEmail = currentUser?.email?.takeIf { it.isNotBlank() }
        ?: "Signed in with Google"

    val privacyPolicyUrl = context.getString(R.string.privacy_policy_url)
    val accountDeletionUrl = context.getString(R.string.account_deletion_url)
    val activeGoal = profile.resolvedFitnessGoal()
    val activeSplit = profile.resolvedTrainingSplit()

    ModalBottomSheet(
        onDismissRequest = { if (!isSigningOut && !isDeleting) onDismiss() },
        sheetState = sheetState,
        containerColor = SurfaceCard,
        contentColor = TextPrimary,
        modifier = Modifier.testTag("settings_profile_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = AmberGold,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Profile & Settings",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(
                    onClick = { if (!isSigningOut && !isDeleting) onDismiss() },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("close_settings_sheet_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Settings",
                        tint = TextSecondary
                    )
                }
            }

            // Signed-in user identity
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF0B0F17),
                border = BorderStroke(1.dp, SurfaceCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E293B))
                            .border(1.dp, AmberGold.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = resolvedName.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AmberGold
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = resolvedName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = resolvedEmail,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            // Training Preferences Card (Units, Default Rest, Background Alerts)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF0B0F17),
                border = BorderStroke(1.dp, SurfaceCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Scale,
                            contentDescription = null,
                            tint = ElectricTeal,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "WEIGHT UNITS",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = ElectricTeal,
                            letterSpacing = 0.6.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WeightUnit.entries.forEach { unit ->
                            val selected = weightUnit == unit
                            FilterChip(
                                selected = selected,
                                onClick = { onWeightUnitChange(unit) },
                                label = {
                                    Text(
                                        text = unit.label,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("settings_unit_${unit.id.lowercase()}"),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricTeal,
                                    selectedLabelColor = Color.Black,
                                    containerColor = Color(0xFF1E293B),
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }

                    Text(
                        text = if (weightUnit == WeightUnit.KG) {
                            "Metric mode active: 20 kg Olympic barbell & 25/20/15/10/5/2.5/1.25 kg plates."
                        } else {
                            "Imperial mode active: 45 lb barbell & 45/35/25/10/5/2.5 lb plates."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )

                    HorizontalDivider(color = SurfaceCardBorder)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = AmberGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DEFAULT REST DURATION (${defaultRestSeconds}s)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = AmberGold,
                            letterSpacing = 0.6.sp
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(45, 60, 90, 120, 180).forEach { seconds ->
                            val selected = defaultRestSeconds == seconds
                            FilterChip(
                                selected = selected,
                                onClick = { onDefaultRestChange(seconds) },
                                label = {
                                    Text(
                                        text = "${seconds}s",
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("settings_rest_$seconds"),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AmberGold,
                                    selectedLabelColor = Color.Black,
                                    containerColor = Color(0xFF1E293B),
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }

                    HorizontalDivider(color = SurfaceCardBorder)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "FIRST DAY OF WEEK (${firstDayOfWeek.fullName.uppercase()})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = CyanAccent,
                            letterSpacing = 0.6.sp
                        )
                    }

                    val dayOptions = remember {
                        listOf(
                            Weekday.SUNDAY,
                            Weekday.MONDAY,
                            Weekday.SATURDAY,
                            Weekday.TUESDAY,
                            Weekday.WEDNESDAY,
                            Weekday.THURSDAY,
                            Weekday.FRIDAY
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        dayOptions.forEach { day ->
                            val selected = firstDayOfWeek == day
                            FilterChip(
                                selected = selected,
                                onClick = { onFirstDayOfWeekChange(day) },
                                label = {
                                    Text(
                                        text = if (day == Weekday.SUNDAY || day == Weekday.MONDAY || day == Weekday.SATURDAY) {
                                            day.fullName
                                        } else {
                                            day.shortLabel
                                        },
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("settings_first_day_${day.shortLabel.lowercase()}"),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CyanAccent,
                                    selectedLabelColor = Color.Black,
                                    containerColor = Color(0xFF1E293B),
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }

                    Text(
                        text = "Weekly workout schedule & \"This Week\" metrics start on ${firstDayOfWeek.fullName}.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )

                    HorizontalDivider(color = SurfaceCardBorder)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = GainGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.padding(end = 8.dp)) {
                                Text(
                                    text = "Background Rest & Hold Alerts",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = if (hasNotificationPermission) {
                                        "Notify with sound & vibration when screen locks between sets"
                                    } else {
                                        "Permission needed on Android 13+ for lock-screen alerts"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }

                        Switch(
                            checked = restNotificationsEnabled && hasNotificationPermission,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        !RestTimerNotificationHelper.hasNotificationPermission(context)
                                    ) {
                                        pendingNotificationTarget = "rest"
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        RestTimerNotificationHelper.ensureNotificationChannel(context)
                                        hasNotificationPermission = true
                                        notificationPermissionDenied = false
                                        onRestNotificationsChange(true)
                                    }
                                } else {
                                    onRestNotificationsChange(false)
                                }
                            },
                            modifier = Modifier.testTag("settings_rest_notifications_switch"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = GainGreen,
                                uncheckedThumbColor = TextSecondary,
                                uncheckedTrackColor = Color(0xFF1E293B)
                            )
                        )
                    }

                    HorizontalDivider(color = SurfaceCardBorder)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.EventAvailable,
                                contentDescription = null,
                                tint = AmberGold,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.padding(end = 8.dp)) {
                                Text(
                                    text = "Workout Day Reminders",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = if (workoutRemindersEnabled && hasNotificationPermission) {
                                        nextReminderPlan?.let { plan ->
                                            val dayText = if (plan.isToday) "Today" else plan.weekday.fullName
                                            "Next: $dayText at ${WorkoutReminderHelper.formatReminderTime(workoutReminderHour, workoutReminderMinute)} (${plan.routineLabel})"
                                        } ?: "No scheduled routine days yet — assign days in Routines"
                                    } else {
                                        "Remind me on scheduled training days to protect my streak"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }

                        Switch(
                            checked = workoutRemindersEnabled && hasNotificationPermission,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        !RestTimerNotificationHelper.hasNotificationPermission(context)
                                    ) {
                                        pendingNotificationTarget = "workout_reminder"
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        WorkoutReminderHelper.ensureNotificationChannel(context)
                                        hasNotificationPermission = true
                                        notificationPermissionDenied = false
                                        onWorkoutRemindersChange(true)
                                    }
                                } else {
                                    onWorkoutRemindersChange(false)
                                }
                            },
                            modifier = Modifier.testTag("settings_workout_reminders_switch"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = AmberGold,
                                uncheckedThumbColor = TextSecondary,
                                uncheckedTrackColor = Color(0xFF1E293B)
                            )
                        )
                    }

                    if (workoutRemindersEnabled && hasNotificationPermission) {
                        val reminderPresets = remember {
                            listOf(
                                7 to 0,
                                9 to 0,
                                12 to 0,
                                17 to 0,
                                18 to 0,
                                19 to 30
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            reminderPresets.forEach { (hour, minute) ->
                                val selected = workoutReminderHour == hour && workoutReminderMinute == minute
                                FilterChip(
                                    selected = selected,
                                    onClick = { onWorkoutReminderTimeChange(hour, minute) },
                                    label = {
                                        Text(
                                            text = WorkoutReminderHelper.formatReminderTime(hour, minute),
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("settings_reminder_time_${hour}_${minute}"),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AmberGold,
                                        selectedLabelColor = Color.Black,
                                        containerColor = Color(0xFF1E293B),
                                        labelColor = TextSecondary
                                    )
                                )
                            }
                        }
                    }

                    if (notificationPermissionDenied && !hasNotificationPermission) {
                        OutlinedButton(
                            onClick = { openAppNotificationSettings(context) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, AmberGold.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberGold)
                        ) {
                            Text("Open Android Notification Settings", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Goal & Split Plan Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF0B0F17),
                border = BorderStroke(1.dp, SurfaceCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "GOAL & TRAINING SPLIT",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = CyanAccent,
                            letterSpacing = 0.6.sp
                        )
                    }

                    Text(
                        text = "${activeGoal.label} · ${activeSplit.label}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "${profile.targetCalories} kcal · ${profile.proteinGrams}g P / ${profile.carbsGrams}g C / ${profile.fatsGrams}g F",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onOpenGoalOnboarding()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("settings_change_goal_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.65f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAccent)
                    ) {
                        Text("Change Goal & Training Split", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Privacy & Legal Links
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF0F172A),
                border = BorderStroke(1.dp, ElectricTeal.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PrivacyTip,
                            contentDescription = null,
                            tint = ElectricTeal,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Privacy & Data Control",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = ElectricTeal
                        )
                    }
                    Text(
                        text = "Your workouts, exercises, routines, and nutrition logs are stored securely under your Google Account in Cloud Firestore. StrengthForge contains no ads and never sells your data.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { openExternalUrl(context, privacyPolicyUrl) },
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("settings_privacy_policy_button"),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, ElectricTeal.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricTeal)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Privacy Policy", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { openExternalUrl(context, accountDeletionUrl) },
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("settings_web_delete_button"),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, SurfaceCardBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Deletion Guide", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            HorizontalDivider(color = SurfaceCardBorder)

            // Sign Out button
            OutlinedButton(
                onClick = {
                    isSigningOut = true
                    coroutineScope.launch {
                        try {
                            credentialManager.clearCredentialState(ClearCredentialStateRequest())
                        } catch (_: Exception) {
                        }
                        Firebase.auth.signOut()
                        isSigningOut = false
                        onDismiss()
                    }
                },
                enabled = !isSigningOut && !isDeleting,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("sign_out_button"),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, AmberGold.copy(alpha = 0.65f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberGold)
            ) {
                if (isSigningOut) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = AmberGold,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Out", fontWeight = FontWeight.Bold)
                }
            }

            // Delete Account & All Data button
            OutlinedButton(
                onClick = {
                    deleteError = null
                    showDeleteConfirm = true
                },
                enabled = !isSigningOut && !isDeleting,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("open_delete_account_button"),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, LossRed.copy(alpha = 0.75f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = LossRed)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Delete Account & All Data", fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteConfirm = false },
            containerColor = SurfaceCard,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = LossRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Delete Account & All Data?",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "This action is permanent and cannot be undone. All of your Cloud Firestore workout sessions, exercise logs, custom exercises, grouped routines, meal presets, and nutrition logs will be erased immediately, and your StrengthForge account will be deleted.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Text(
                        text = "You will be prompted to verify with your Google Account before deletion proceeds. Local exported CSV/.FIT files and Google Drive Sheets remain under your control.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    if (deleteError != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF3B151E),
                            border = BorderStroke(1.dp, LossRed.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = deleteError ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFFF9CA3),
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isDeleting = true
                        deleteError = null
                        coroutineScope.launch {
                            try {
                                val user = Firebase.auth.currentUser
                                val activity = context.findActivity()
                                if (user == null || activity == null) {
                                    deleteError = "Active Google session unavailable. Please sign in again."
                                    isDeleting = false
                                    return@launch
                                }

                                val serverClientId = context.getString(R.string.default_web_client_id)
                                val googleIdOption = GetSignInWithGoogleOption.Builder(serverClientId)
                                    .build()
                                val request = GetCredentialRequest.Builder()
                                    .addCredentialOption(googleIdOption)
                                    .build()

                                val credentialResponse = credentialManager.getCredential(
                                    request = request,
                                    context = activity
                                )
                                val credential = credentialResponse.credential
                                if (credential !is CustomCredential ||
                                    credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                                ) {
                                    deleteError = "Unexpected Google credential response."
                                    isDeleting = false
                                    return@launch
                                }

                                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data)
                                val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken.idToken, null)
                                user.reauthenticate(firebaseCredential).await()

                                val deleteDataResult = onDeleteUserData()
                                if (deleteDataResult.isFailure) {
                                    deleteError = deleteDataResult.exceptionOrNull()?.localizedMessage
                                        ?: "Failed to delete cloud records. Please retry."
                                    isDeleting = false
                                    return@launch
                                }

                                user.delete().await()
                                try {
                                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                                } catch (_: Exception) {
                                }

                                Toast.makeText(
                                    context,
                                    "StrengthForge account and data deleted.",
                                    Toast.LENGTH_LONG
                                ).show()
                                isDeleting = false
                                showDeleteConfirm = false
                                onDismiss()
                            } catch (_: GetCredentialCancellationException) {
                                deleteError = "Google verification cancelled. Account was not deleted."
                                isDeleting = false
                            } catch (e: GetCredentialException) {
                                deleteError = e.localizedMessage ?: "Google re-authentication failed."
                                isDeleting = false
                            } catch (e: Exception) {
                                deleteError = e.localizedMessage ?: "Account deletion failed. Please try again."
                                isDeleting = false
                            }
                        }
                    },
                    enabled = !isDeleting,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("confirm_delete_account_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LossRed,
                        contentColor = Color.White
                    )
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Confirm Delete", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirm = false },
                    enabled = !isDeleting,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}
