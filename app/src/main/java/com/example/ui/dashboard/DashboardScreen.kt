package com.example.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import com.example.data.model.WorkoutRoutine
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.analytics.DashboardTimeframe
import com.example.data.analytics.WeightUnit
import com.example.data.analytics.WeightUnitHelper
import com.example.data.analytics.WorkoutAnalyticsHelper
import com.example.data.model.ExerciseRecord
import com.example.data.model.UserProfile
import com.example.data.model.WorkoutExerciseLog
import com.example.data.model.WorkoutSession
import com.example.data.schedule.WorkoutScheduleHelper
import com.example.ui.components.WeeklyWorkoutScheduleCard
import com.example.ui.components.WorkoutBreakdownSection
import com.example.ui.history.EditWorkoutMetadataDialog
import com.example.ui.theme.AmberGold
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.ElectricTeal
import com.example.ui.theme.GainGreen
import com.example.ui.theme.LossRed
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.tracker.AddNewWorkoutLauncherDialog
import com.example.viewmodel.UiState
import com.example.viewmodel.WorkoutViewModel

@Composable
fun DashboardScreen(
    viewModel: WorkoutViewModel,
    onNavigateToWorkout: () -> Unit,
    onNavigateToAnalytics: () -> Unit,
    onNavigateToNutrition: () -> Unit,
    onOpenGoalOnboarding: () -> Unit = {}
) {
    val profileState by viewModel.userProfile.collectAsState()
    val workoutsState by viewModel.workouts.collectAsState()
    val exercisesState by viewModel.exercises.collectAsState()
    val nutrition by viewModel.todayNutrition.collectAsState()
    val routinesState by viewModel.routines.collectAsState()
    val logsState by viewModel.logs.collectAsState()
    val weeklySchedule by viewModel.weeklySchedule.collectAsState()
    val weeklyStreak by viewModel.computedWeeklyStreak.collectAsState()
    val weightUnit by viewModel.weightUnit.collectAsState()
    val defaultRestSeconds by viewModel.defaultRestSeconds.collectAsState()
    val restNotificationsEnabled by viewModel.restNotificationsEnabled.collectAsState()
    val firstDayOfWeek by viewModel.firstDayOfWeek.collectAsState()
    val workoutRemindersEnabled by viewModel.workoutRemindersEnabled.collectAsState()
    val workoutReminderHour by viewModel.workoutReminderHour.collectAsState()
    val workoutReminderMinute by viewModel.workoutReminderMinute.collectAsState()
    val nextWorkoutReminderPlan by viewModel.nextWorkoutReminderPlan.collectAsState()
    val bodyweightSummary by viewModel.bodyweightSummary.collectAsState()

    val profile = (profileState as? UiState.Success)?.data ?: UserProfile()
    val workouts = (workoutsState as? UiState.Success)?.data ?: emptyList()
    val exercises = (exercisesState as? UiState.Success)?.data ?: emptyList()
    val routines = (routinesState as? UiState.Success)?.data ?: emptyList()
    val allLogs = (logsState as? UiState.Success)?.data ?: emptyList()

    val todaySuggestion = remember(routines) {
        WorkoutScheduleHelper.suggestRoutineForDate(routines, Date())
    }

    var selectedTimeframe by remember { mutableStateOf(DashboardTimeframe.THIS_WEEK) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var showShareOptionsSheet by remember { mutableStateOf(false) }
    var showLogPastWorkoutDialog by remember { mutableStateOf(false) }
    var launcherInitialDate by remember { mutableStateOf<Date?>(null) }
    var workoutToEdit by remember { mutableStateOf<WorkoutSession?>(null) }
    var workoutToDelete by remember { mutableStateOf<WorkoutSession?>(null) }

    val performedPrs = remember(exercises, allLogs) {
        WorkoutAnalyticsHelper.computePerformedExercisePrs(allLogs, exercises)
    }
    val primaryPR = performedPrs.firstOrNull { it.exercise.isPrimary } ?: performedPrs.firstOrNull()

    val metrics = remember(workouts, selectedTimeframe, profile, performedPrs, routines, firstDayOfWeek) {
        WorkoutAnalyticsHelper.computeDashboardMetrics(
            allWorkouts = workouts,
            timeframe = selectedTimeframe,
            profile = profile,
            completedExercises = performedPrs.map { it.exercise.copy(percentIncrease = it.percentIncrease) },
            routines = routines,
            firstDayOfWeek = firstDayOfWeek
        )
    }
    val totalVolume = metrics.totalVolumeLbs.toInt()
    val totalWorkouts = metrics.sessionCount
    val totalSets = metrics.completedSets
    val avgDuration = metrics.avgDurationMinutes

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 96.dp)
            .testTag("dashboard_screen")
    ) {
        // App Bar / Top Identity
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(GainGreen)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ACTIVE TRAINING PHASE",
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberGold,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                // Streak Flame Pill with tonal container
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF2E1705),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF97316))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalFireDepartment,
                            contentDescription = "Streak",
                            tint = Color(0xFFF97316),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$weeklyStreak Wk Streak",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFF97316),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Workout Summary",
                    style = MaterialTheme.typography.headlineLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showShareOptionsSheet = true },
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("dashboard_share_button"),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AmberGold.copy(alpha = 0.7f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0xFF1F1608),
                            contentColor = AmberGold
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Share",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = SurfaceCard,
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
                        modifier = Modifier.size(48.dp)
                    ) {
                        IconButton(
                            onClick = { showAccountDialog = true },
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("dashboard_account_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Account and Settings",
                                tint = AmberGold,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }

        // Timeframe Selector Chips (Material 3 FilterChips)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DashboardTimeframe.entries.forEach { timeframe ->
                val isSelected = selectedTimeframe == timeframe
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedTimeframe = timeframe },
                    label = {
                        Text(
                            text = timeframe.label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AmberGold,
                        selectedLabelColor = Color.Black,
                        containerColor = SurfaceCard,
                        labelColor = TextSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = if (isSelected) AmberGold else SurfaceCardBorder,
                        enabled = true,
                        selected = isSelected
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Hero Volume & Overload Metric Card with Mini Sparkline
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = SurfaceCard),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text(
                            text = "TOTAL TRAINING VOLUME",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = WeightUnitHelper.formatVolumeWithUnit(metrics.totalVolumeLbs, weightUnit),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    val isPositiveGain = metrics.gainPercent >= 0.0
                    val gainBg = if (isPositiveGain) Color(0xFF064E3B) else Color(0xFF3F1D2B)
                    val gainTint = if (isPositiveGain) GainGreen else LossRed
                    val gainPrefix = if (metrics.gainPercent > 0.0) "+" else ""
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(gainBg)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                contentDescription = null,
                                tint = gainTint,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$gainPrefix${metrics.gainPercent}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = gainTint,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Mini progression sparkline directly on card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0B0F17))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    MiniVolumeSparkline(
                        points = metrics.sparklinePoints,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "$totalWorkouts sessions · ${WeightUnitHelper.formatVolumeWithUnit(metrics.totalVolumeLbs, weightUnit)} (${selectedTimeframe.label.lowercase()})",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Text(
                        text = "Tap for full curves →",
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberGold,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { onNavigateToAnalytics() }
                    )
                }

                workouts.firstOrNull()?.let { latestWorkout ->
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { viewModel.prepareWorkoutSocialShare(latestWorkout) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("dashboard_share_latest_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AmberGold.copy(alpha = 0.65f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberGold)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share Latest Workout Graphic", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4-Card Statistics Grid
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            M3StatCard(
                title = "Workouts",
                value = "$totalWorkouts",
                subtext = metrics.adherenceLabel,
                subtextColor = if (totalWorkouts > 0) GainGreen else TextSecondary,
                icon = Icons.AutoMirrored.Filled.ShowChart,
                iconColor = CyanAccent,
                modifier = Modifier.weight(1f)
            )
            M3StatCard(
                title = if (primaryPR != null) "${primaryPR.exercise.name} PR" else "Top Lift PR",
                value = primaryPR?.formatPrimaryMetricLabel(weightUnit) ?: "No PR yet",
                subtext = when {
                    primaryPR == null -> "Complete an exercise"
                    primaryPR.percentIncrease > 0.0 -> "+${primaryPR.percentIncrease}% gain"
                    else -> "${primaryPR.sessionsCount.coerceAtLeast(1)} logged"
                },
                subtextColor = if (primaryPR != null && primaryPR.percentIncrease > 0.0) GainGreen else TextSecondary,
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                iconColor = AmberGold,
                onShareClick = if (primaryPR != null) {
                    { viewModel.preparePrsSocialShare() }
                } else null,
                shareTestTag = "dashboard_share_pr_card_button",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            M3StatCard(
                title = "Avg Duration",
                value = "$avgDuration min",
                subtext = "High density",
                subtextColor = TextSecondary,
                icon = Icons.Default.Timer,
                iconColor = ElectricTeal,
                modifier = Modifier.weight(1f)
            )
            M3StatCard(
                title = "Sets Completed",
                value = "$totalSets sets",
                subtext = "Progressive reps",
                subtextColor = AmberGold,
                icon = Icons.Default.FitnessCenter,
                iconColor = Color(0xFFA855F7),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Weekly Workout Schedule (Derived from Routines + Logged Workouts)
        WeeklyWorkoutScheduleCard(
            schedule = weeklySchedule,
            modifier = Modifier.padding(horizontal = 20.dp),
            onDayClick = { day ->
                launcherInitialDate = day.date
                showLogPastWorkoutDialog = true
            }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Quick Action Bar: Start Today's / Next Scheduled Routine
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clickable {
                    val suggestedRoutine = todaySuggestion.routine
                    if (suggestedRoutine != null) {
                        viewModel.startWorkoutWithRoutine(suggestedRoutine)
                    } else {
                        viewModel.startWorkout("Push Day A - Chest & Triceps")
                    }
                    onNavigateToWorkout()
                }
                .testTag("start_workout_banner"),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161F30)),
            border = androidx.compose.foundation.BorderStroke(
                1.5.dp,
                if (todaySuggestion.isScheduledForDate) ElectricTeal else AmberGold
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF1E293B), Color(0xFF111827))
                        )
                    )
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (todaySuggestion.isScheduledForDate) {
                            "TODAY'S SCHEDULED ROUTINE · ${todaySuggestion.weekday.fullName.uppercase()}"
                        } else {
                            "NEXT SCHEDULED ROUTINE"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (todaySuggestion.isScheduledForDate) ElectricTeal else AmberGold,
                        fontWeight = FontWeight.Bold
                    )
                    val suggestedRoutine = todaySuggestion.routine
                    val routineName = suggestedRoutine?.name ?: "Push Day A"
                    val routineDesc = suggestedRoutine?.description?.ifBlank {
                        todaySuggestion.reasonLabel
                    } ?: todaySuggestion.reasonLabel
                    Text(
                        text = routineName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = routineDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(if (todaySuggestion.isScheduledForDate) ElectricTeal else AmberGold),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Start Workout",
                        tint = Color.Black,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Secondary Action: Log Past Workout (Backdating)
        OutlinedButton(
            onClick = {
                launcherInitialDate = null
                showLogPastWorkoutDialog = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(48.dp)
                .testTag("log_past_workout_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = ElectricTeal,
                containerColor = Color(0xFF0F172A)
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, ElectricTeal.copy(alpha = 0.6f))
        ) {
            Icon(
                imageVector = Icons.Default.CalendarMonth,
                contentDescription = null,
                tint = ElectricTeal,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Add / Log Past Workout (Any Date)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = ElectricTeal
            )
        }

        // Grouped Routines Carousel
        if (routines.isNotEmpty()) {
            Spacer(modifier = Modifier.height(18.dp))
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = null,
                            tint = AmberGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "GROUPED WORKOUT ROUTINES (${routines.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(routines, key = { it.id }) { routine ->
                    OutlinedCard(
                        modifier = Modifier
                            .width(220.dp)
                            .clickable {
                                viewModel.startWorkoutWithRoutine(routine)
                                onNavigateToWorkout()
                            },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.outlinedCardColors(containerColor = SurfaceCard),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF0F172A))
                                        .border(1.dp, AmberGold.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = routine.targetMuscle.ifEmpty { "Split" },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AmberGold,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                }

                                Text(
                                    text = "${routine.exerciseIds.size} lifts",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    fontSize = 10.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = routine.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1
                            )
                            Text(
                                text = WorkoutScheduleHelper.formatScheduledDays(routine.scheduledDays, firstDayOfWeek),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (routine.scheduledDays.isNotEmpty()) ElectricTeal else TextSecondary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                            if (routine.description.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = routine.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    maxLines = 1
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    viewModel.startWorkoutWithRoutine(routine)
                                    onNavigateToWorkout()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AmberGold,
                                    contentColor = Color.Black
                                ),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Start Routine", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Recent Workouts Section with Detailed Expandable Cards
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "RECENT WORKOUT SESSIONS",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tap to expand set telemetry & key PRs",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64748B)
                    )
                }

                Text(
                    text = "See All (${workouts.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = AmberGold,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onNavigateToAnalytics() }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            workouts.take(5).forEachIndexed { index, workout ->
                val logsForWorkout = remember(allLogs, workout.id) {
                    allLogs.filter { it.workoutId == workout.id }
                }
                RecentWorkoutDetailCard(
                    workout = workout,
                    workoutLogs = logsForWorkout,
                    exercisesCatalog = exercises,
                    weightUnit = weightUnit,
                    isLatest = index == 0,
                    onEdit = { workoutToEdit = workout },
                    onShare = { viewModel.prepareWorkoutSocialShare(workout) },
                    onDelete = { workoutToDelete = workout },
                    onRepeat = {
                        viewModel.startWorkout(workout.name)
                        onNavigateToWorkout()
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Daily Macro & Calorie Target Glance
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clickable { onNavigateToNutrition() },
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Restaurant,
                            contentDescription = null,
                            tint = AmberGold,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DAILY MACRO COMPLIANCE",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF0E3A42))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = profile.resolvedFitnessGoal().label,
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricTeal,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Nutrition Details",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                val cals = nutrition?.calories ?: 0
                val pLogged = nutrition?.proteinG ?: 0
                val cLogged = nutrition?.carbsG ?: 0
                val fLogged = nutrition?.fatsG ?: 0
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = "$cals / ${profile.targetCalories} kcal",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Target: ${profile.targetCaloriesMin} - ${profile.targetCaloriesMax}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (cals in profile.targetCaloriesMin..profile.targetCaloriesMax) GainGreen else TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Macro mini pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MacroMiniBadge("Protein", "${pLogged}g / ${profile.proteinGrams}g", AmberGold, Modifier.weight(1f))
                    MacroMiniBadge("Carbs", "${cLogged}g / ${profile.carbsGrams}g", CyanAccent, Modifier.weight(1f))
                    MacroMiniBadge("Fats", "${fLogged}g / ${profile.fatsGrams}g", Color(0xFFA855F7), Modifier.weight(1f))
                }

                bodyweightSummary.sevenDayAvgLbs?.let { avgLbs ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0F172A))
                            .border(1.dp, SurfaceCardBorder, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "7-Day Avg Weight: ${WeightUnitHelper.formatWeightWithUnit(avgLbs, weightUnit)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = ElectricTeal,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = bodyweightSummary.goalAlignmentLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        if (showLogPastWorkoutDialog) {
            AddNewWorkoutLauncherDialog(
                routines = routines,
                exercises = exercises,
                initialDate = launcherInitialDate,
                defaultRestSeconds = defaultRestSeconds,
                weightUnit = weightUnit,
                onDismiss = {
                    showLogPastWorkoutDialog = false
                    launcherInitialDate = null
                },
                onStartWithRoutine = { routine, date, restSecs ->
                    showLogPastWorkoutDialog = false
                    launcherInitialDate = null
                    viewModel.startWorkoutWithRoutine(routine, date, restSecs)
                    onNavigateToWorkout()
                },
                onStartCustom = { name, selectedExs, date, restSecs ->
                    showLogPastWorkoutDialog = false
                    launcherInitialDate = null
                    viewModel.startCustomWorkout(name, selectedExs, date, restSeconds = restSecs)
                    onNavigateToWorkout()
                }
            )
        }

        workoutToEdit?.let { targetWorkout ->
            EditWorkoutMetadataDialog(
                workout = targetWorkout,
                weightUnit = weightUnit,
                onDismiss = { workoutToEdit = null },
                onSaveDetails = { newName, newDate, newDuration, newNotes, newVolume ->
                    viewModel.updateWorkoutMetadata(
                        workout = targetWorkout,
                        newName = newName,
                        newDate = newDate,
                        newDurationMinutes = newDuration,
                        newNotes = newNotes,
                        newTotalVolumeLbs = newVolume
                    ) {
                        workoutToEdit = null
                    }
                },
                onEditSetsAndExercises = {
                    workoutToEdit = null
                    viewModel.startEditingWorkout(targetWorkout)
                    onNavigateToWorkout()
                },
                onDelete = {
                    workoutToEdit = null
                    workoutToDelete = targetWorkout
                }
            )
        }

        workoutToDelete?.let { targetWorkout ->
            AlertDialog(
                onDismissRequest = { workoutToDelete = null },
                containerColor = SurfaceCard,
                title = { Text("Delete Workout?", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "Remove \"${targetWorkout.name}\" (${targetWorkout.dateString}) from your logs and adjust lifetime volume?",
                        color = TextSecondary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteWorkout(targetWorkout) {
                                workoutToDelete = null
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = LossRed, contentColor = Color.White)
                    ) {
                        Text("Delete", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { workoutToDelete = null }) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
            )
        }

        if (showAccountDialog) {
            AccountAndPrivacyDialog(
                profileDisplayName = profile.displayName,
                profile = profile,
                weightUnit = weightUnit,
                defaultRestSeconds = defaultRestSeconds,
                restNotificationsEnabled = restNotificationsEnabled,
                firstDayOfWeek = firstDayOfWeek,
                workoutRemindersEnabled = workoutRemindersEnabled,
                workoutReminderHour = workoutReminderHour,
                workoutReminderMinute = workoutReminderMinute,
                nextReminderPlan = nextWorkoutReminderPlan,
                onWeightUnitChange = { unit -> viewModel.setWeightUnit(unit) },
                onDefaultRestChange = { secs -> viewModel.updateSettingsPreferences(defaultRestSeconds = secs) },
                onRestNotificationsChange = { enabled -> viewModel.updateSettingsPreferences(restNotificationsEnabled = enabled) },
                onFirstDayOfWeekChange = { day -> viewModel.updateSettingsPreferences(firstDayOfWeek = day) },
                onWorkoutRemindersChange = { enabled ->
                    viewModel.updateSettingsPreferences(workoutRemindersEnabled = enabled)
                },
                onWorkoutReminderTimeChange = { hour, minute ->
                    viewModel.updateSettingsPreferences(
                        workoutReminderHour = hour,
                        workoutReminderMinute = minute
                    )
                },
                onOpenGoalOnboarding = onOpenGoalOnboarding,
                onDismiss = { showAccountDialog = false },
                onDeleteUserData = { viewModel.deleteAllUserData() }
            )
        }

        if (showShareOptionsSheet) {
            DashboardShareOptionsSheet(
                selectedTimeframe = selectedTimeframe,
                totalVolumeLabel = WeightUnitHelper.formatVolumeWithUnit(metrics.totalVolumeLbs, weightUnit),
                totalWorkouts = totalWorkouts,
                totalSets = totalSets,
                primaryPrSummary = primaryPR?.let {
                    "${it.exercise.name}: ${it.formatPrimaryMetricLabel(weightUnit)} (${performedPrs.size} PRs)"
                } ?: "Create your Personal Records trophy card",
                latestWorkout = workouts.firstOrNull(),
                weightUnit = weightUnit,
                onSharePrs = {
                    showShareOptionsSheet = false
                    viewModel.preparePrsSocialShare()
                },
                onShareSummary = {
                    showShareOptionsSheet = false
                    viewModel.prepareSummarySocialShare(selectedTimeframe)
                },
                onShareLatestWorkout = { latest ->
                    showShareOptionsSheet = false
                    viewModel.prepareWorkoutSocialShare(latest)
                },
                onDismiss = { showShareOptionsSheet = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardShareOptionsSheet(
    selectedTimeframe: DashboardTimeframe,
    totalVolumeLabel: String,
    totalWorkouts: Int,
    totalSets: Int,
    primaryPrSummary: String,
    latestWorkout: WorkoutSession?,
    weightUnit: WeightUnit,
    onSharePrs: () -> Unit,
    onShareSummary: () -> Unit,
    onShareLatestWorkout: (WorkoutSession) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceCard,
        modifier = Modifier.testTag("dashboard_share_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    tint = AmberGold,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Share to Social Media",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Text(
                text = "Generate a ready-to-post graphic & caption for Instagram, X, Facebook, WhatsApp, or Messages.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            ShareOptionCard(
                title = "Share Personal Records (PRs)",
                subtitle = primaryPrSummary,
                icon = Icons.Default.EmojiEvents,
                accentColor = AmberGold,
                testTag = "share_option_prs",
                onClick = onSharePrs
            )

            ShareOptionCard(
                title = "Share ${selectedTimeframe.label} Summary",
                subtitle = "$totalVolumeLabel · $totalWorkouts workouts · $totalSets sets",
                icon = Icons.AutoMirrored.Filled.ShowChart,
                accentColor = ElectricTeal,
                testTag = "share_option_summary",
                onClick = onShareSummary
            )

            if (latestWorkout != null) {
                ShareOptionCard(
                    title = "Share Latest Workout",
                    subtitle = "${latestWorkout.name} (${WeightUnitHelper.formatVolumeWithUnit(latestWorkout.totalVolumeLbs, weightUnit)})",
                    icon = Icons.Default.FitnessCenter,
                    accentColor = CyanAccent,
                    testTag = "share_option_latest_workout",
                    onClick = { onShareLatestWorkout(latestWorkout) }
                )
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.End)
                    .defaultMinSize(minHeight = 48.dp)
            ) {
                Text("Cancel", color = TextSecondary)
            }
        }
    }
}

@Composable
private fun ShareOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF0F172A),
        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun M3StatCard(
    title: String,
    value: String,
    subtext: String,
    subtextColor: Color,
    icon: ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier,
    onShareClick: (() -> Unit)? = null,
    shareTestTag: String? = null
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F172A)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (onShareClick != null) {
                    IconButton(
                        onClick = onShareClick,
                        modifier = Modifier
                            .size(48.dp)
                            .then(if (shareTestTag != null) Modifier.testTag(shareTestTag) else Modifier)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share PRs",
                            tint = AmberGold,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtext,
                style = MaterialTheme.typography.labelSmall,
                color = subtextColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun RecentWorkoutDetailCard(
    workout: WorkoutSession,
    workoutLogs: List<WorkoutExerciseLog> = emptyList(),
    exercisesCatalog: List<ExerciseRecord> = emptyList(),
    weightUnit: WeightUnit = WeightUnit.LBS,
    isLatest: Boolean,
    onEdit: () -> Unit = {},
    onShare: () -> Unit = {},
    onDelete: () -> Unit = {},
    onRepeat: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isLatest) Color(0xFF161F30) else SurfaceCard
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isLatest) AmberGold.copy(alpha = 0.5f) else SurfaceCardBorder
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = workout.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isLatest) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(AmberGold)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "LATEST PR",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "${workout.dateString} · ${workout.durationMinutes} mins · ${workout.exerciseCount} exercises",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = WeightUnitHelper.formatVolumeWithUnit(workout.totalVolumeLbs, weightUnit),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AmberGold
                        )
                        Text(
                            text = "total volume",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onShare,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("dashboard_share_workout_${workout.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share Workout Graphic",
                            tint = AmberGold,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Workout",
                            tint = ElectricTeal,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Workout",
                            tint = LossRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(10.dp))

                    if (workout.notes.isNotBlank()) {
                        Text(
                            text = "Coach Notes: \"${workout.notes}\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFE2E8F0),
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    WorkoutBreakdownSection(
                        workoutId = workout.id,
                        logs = workoutLogs,
                        exercisesCatalog = exercisesCatalog,
                        weightUnit = weightUnit
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Sets Logged: ${workout.completedSetsCount} completed sets",
                            style = MaterialTheme.typography.labelSmall,
                            color = GainGreen,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = onEdit,
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricTeal),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Edit", style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(
                                onClick = onRepeat,
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberGold),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Icon(Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Repeat", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MacroMiniBadge(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0B0F17))
            .padding(8.dp)
    ) {
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun MiniVolumeSparkline(
    points: List<Float>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (points.isEmpty()) {
            drawLine(
                color = Color(0xFF334155),
                start = Offset(0f, size.height * 0.65f),
                end = Offset(size.width, size.height * 0.65f),
                strokeWidth = 2.dp.toPx()
            )
            return@Canvas
        }
        val min = points.minOrNull() ?: 0f
        val max = points.maxOrNull() ?: 100f
        val range = (max - min).coerceAtLeast(10f)
        val h = size.height

        if (points.size == 1) {
            val y = h * 0.5f
            drawLine(
                color = AmberGold,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawCircle(color = AmberGold, radius = 4.dp.toPx(), center = Offset(size.width * 0.5f, y))
            return@Canvas
        }

        val stepX = size.width / (points.size - 1)
        val coords = points.mapIndexed { idx, v ->
            val x = idx * stepX
            val y = h - ((v - min) / range * h * 0.8f) - (h * 0.1f)
            Offset(x, y)
        }

        // Area fill
        val fillPath = Path().apply {
            moveTo(coords.first().x, h)
            coords.forEach { lineTo(it.x, it.y) }
            lineTo(coords.last().x, h)
            close()
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                listOf(AmberGold.copy(alpha = 0.35f), Color.Transparent),
                startY = 0f,
                endY = h
            )
        )

        // Line
        val path = Path().apply {
            coords.forEachIndexed { i, pt ->
                if (i == 0) moveTo(pt.x, pt.y)
                else {
                    val prev = coords[i - 1]
                    val midX = (prev.x + pt.x) / 2
                    cubicTo(midX, prev.y, midX, pt.y, pt.x, pt.y)
                }
            }
        }

        drawPath(
            path = path,
            color = AmberGold,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )

        // Draw last point dot
        drawCircle(
            color = AmberGold,
            radius = 4.dp.toPx(),
            center = coords.last()
        )
    }
}
