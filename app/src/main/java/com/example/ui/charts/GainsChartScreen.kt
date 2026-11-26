package com.example.ui.charts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.analytics.WorkoutAnalyticsHelper
import com.example.data.model.ExerciseRecord
import com.example.data.model.ExerciseTrackingType
import com.example.data.model.ProgressDataPoint
import com.example.ui.components.GoogleSheetsSyncCard
import com.example.ui.theme.AmberGold
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.ElectricTeal
import com.example.ui.theme.GainGreen
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.UiState
import com.example.viewmodel.WorkoutViewModel
import java.util.Locale

private enum class ProgressionRange(val label: String, val limit: Int?) {
    LAST_5("Last 5", 5),
    LAST_10("Last 10", 10),
    ALL("All", null)
}

@Composable
fun GainsChartScreen(viewModel: WorkoutViewModel) {
    val exercisesState by viewModel.exercises.collectAsState()
    val logsState by viewModel.logs.collectAsState()
    val workoutsState by viewModel.workouts.collectAsState()
    val profileState by viewModel.userProfile.collectAsState()
    val progressPoints by viewModel.selectedExerciseProgressPoints.collectAsState()
    val selectedExId by viewModel.selectedExerciseId.collectAsState()
    val volumeMilestones by viewModel.volumeMilestones.collectAsState()
    val muscleGroupFocus by viewModel.muscleGroupFocus.collectAsState()

    var selectedMetricTab by remember { mutableIntStateOf(0) } // 0 = Max Weight, 1 = Estimated 1RM
    var selectedRange by remember { mutableStateOf(ProgressionRange.ALL) }
    var selectedPointIndex by remember { mutableStateOf<Int?>(null) }

    val exercises = (exercisesState as? UiState.Success)?.data ?: emptyList()
    val allLogs = (logsState as? UiState.Success)?.data ?: emptyList()
    val allWorkouts = (workoutsState as? UiState.Success)?.data ?: emptyList()
    val profile = (profileState as? UiState.Success)?.data
    val performedPrs = remember(exercises, allLogs) {
        WorkoutAnalyticsHelper.computePerformedExercisePrs(allLogs, exercises)
    }

    LaunchedEffect(performedPrs, selectedExId) {
        val firstCompleted = performedPrs.firstOrNull()
        if (firstCompleted != null && performedPrs.none { it.exercise.id == selectedExId }) {
            viewModel.setSelectedExercise(firstCompleted.exercise.id)
        }
    }

    val currentPr = performedPrs.find { it.exercise.id == selectedExId } ?: performedPrs.firstOrNull()
    val currentExercise = currentPr?.exercise

    LaunchedEffect(currentExercise?.id) {
        selectedPointIndex = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 96.dp)
            .testTag("gains_chart_screen")
    ) {
        // Screen Header
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                    contentDescription = null,
                    tint = AmberGold,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Strength Analytics",
                    style = MaterialTheme.typography.headlineLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "Visual progression curves & progressive overload metrics",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Exercise Selector Chips (only exercises with completed logs)
        if (performedPrs.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(performedPrs, key = { it.exercise.id }) { pr ->
                    val ex = pr.exercise
                    val isSelected = ex.id == currentExercise?.id
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            viewModel.setSelectedExercise(ex.id)
                            selectedPointIndex = null
                        },
                        label = {
                            Text(
                                text = ex.name,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AmberGold,
                            selectedLabelColor = Color.Black,
                            containerColor = SurfaceCard,
                            labelColor = TextPrimary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = if (isSelected) AmberGold else SurfaceCardBorder,
                            enabled = true,
                            selected = isSelected
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }

        val trackingType = currentPr?.trackingType ?: ExerciseTrackingType.WEIGHTED

        // Hero Metric Card for Selected Completed Exercise
        if (currentPr == null || currentExercise == null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .testTag("gains_no_completed_exercises_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = AmberGold,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "No Completed Exercises Yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Complete a workout to see personal records and progression charts for the lifts you've performed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${currentExercise.name.uppercase()} · ${trackingType.shortLabel.uppercase()}",
                                style = MaterialTheme.typography.labelMedium,
                                color = AmberGold,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = currentPr.formatPrimaryMetricLabel(),
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        // Gain Percentage / Mode Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF064E3B))
                                .border(1.dp, GainGreen, RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                    contentDescription = null,
                                    tint = GainGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (currentPr.percentIncrease > 0.0 || trackingType == ExerciseTrackingType.WEIGHTED) {
                                        "+${currentPr.percentIncrease}%"
                                    } else {
                                        trackingType.shortLabel
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = GainGreen,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Starting vs Current Stats Row (derived strictly from performed logs)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0B0F17))
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        when (trackingType) {
                            ExerciseTrackingType.TIMED_HOLD -> {
                                val startSec = currentPr.startingDurationSeconds
                                val bestSec = currentPr.bestDurationSeconds
                                val secGain = maxOf(0, bestSec - startSec)
                                ProgressionStatItem(
                                    label = "Starting Hold",
                                    value = "${startSec}s"
                                )
                                ProgressionStatItem(
                                    label = "Hold Gain",
                                    value = "+${secGain}s",
                                    valueColor = GainGreen
                                )
                                ProgressionStatItem(
                                    label = "Added Wt",
                                    value = if (currentPr.maxWeightLbs > 0.0) {
                                        "+${currentPr.maxWeightLbs.toInt()} lbs"
                                    } else {
                                        "BW"
                                    },
                                    valueColor = CyanAccent
                                )
                                ProgressionStatItem(
                                    label = "Sessions",
                                    value = "${currentPr.sessionsCount}"
                                )
                            }
                            ExerciseTrackingType.BODYWEIGHT_REPS -> {
                                ProgressionStatItem(
                                    label = "Target",
                                    value = "${currentExercise.defaultSets}×${currentExercise.defaultReps}"
                                )
                                ProgressionStatItem(
                                    label = "Best Reps",
                                    value = "${currentPr.bestReps} reps",
                                    valueColor = GainGreen
                                )
                                ProgressionStatItem(
                                    label = "Added Max",
                                    value = if (currentPr.maxWeightLbs > 0.0) {
                                        "+${currentPr.maxWeightLbs.toInt()} lbs"
                                    } else {
                                        "BW"
                                    },
                                    valueColor = CyanAccent
                                )
                                ProgressionStatItem(
                                    label = "Sessions",
                                    value = "${currentPr.sessionsCount}"
                                )
                            }
                            ExerciseTrackingType.WEIGHTED -> {
                                ProgressionStatItem(
                                    label = "Starting Weight",
                                    value = "${currentPr.startingWeightLbs.toInt()} lbs"
                                )
                                ProgressionStatItem(
                                    label = "Weight Gain",
                                    value = "+${maxOf(0, (currentPr.maxWeightLbs - currentPr.startingWeightLbs).toInt())} lbs",
                                    valueColor = GainGreen
                                )
                                ProgressionStatItem(
                                    label = "Estimated 1RM",
                                    value = "${currentPr.best1RM.toInt()} lbs",
                                    valueColor = CyanAccent
                                )
                                ProgressionStatItem(
                                    label = "Logged Sets",
                                    value = "${currentPr.totalSetsLogged}"
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Visual Graph Section with Interactive Canvas
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                val primaryMetricLabel = when (trackingType) {
                    ExerciseTrackingType.TIMED_HOLD -> "Hold (sec)"
                    ExerciseTrackingType.BODYWEIGHT_REPS -> "Reps / Set"
                    ExerciseTrackingType.WEIGHTED -> "Working Wt"
                }
                val secondaryMetricLabel = when (trackingType) {
                    ExerciseTrackingType.TIMED_HOLD -> "+Weight"
                    ExerciseTrackingType.BODYWEIGHT_REPS -> "Added Wt"
                    ExerciseTrackingType.WEIGHTED -> "1RM Peak"
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PROGRESSION TIMELINE",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold
                    )

                    // Metric Switcher Pills
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F172A))
                            .padding(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (selectedMetricTab == 0) AmberGold else Color.Transparent)
                                .clickable { selectedMetricTab = 0 }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = primaryMetricLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selectedMetricTab == 0) Color.Black else TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (selectedMetricTab == 1) CyanAccent else Color.Transparent)
                                .clickable { selectedMetricTab = 1 }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = secondaryMetricLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selectedMetricTab == 1) Color.Black else TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (progressPoints.size > 5) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ProgressionRange.entries.forEach { range ->
                            val isRangeSelected = selectedRange == range
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isRangeSelected) Color(0xFF1E293B) else Color(0xFF0F172A))
                                    .border(
                                        1.dp,
                                        if (isRangeSelected) AmberGold else SurfaceCardBorder,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        selectedRange = range
                                        selectedPointIndex = null
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = range.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isRangeSelected) AmberGold else TextSecondary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                }

                val pointsToDraw = remember(progressPoints, selectedRange) {
                    val limit = selectedRange.limit
                    if (limit != null && progressPoints.size > limit) {
                        progressPoints.takeLast(limit)
                    } else {
                        progressPoints
                    }
                }

                // If user tapped a point, show interactive details banner
                val activePoint = selectedPointIndex?.let { pointsToDraw.getOrNull(it) }
                if (activePoint != null) {
                    val pointDetailText = when (trackingType) {
                        ExerciseTrackingType.TIMED_HOLD -> {
                            val wtText = if (activePoint.weightLbs > 0.0) " (+${activePoint.weightLbs.toInt()} lbs)" else " (BW)"
                            "${activePoint.durationSeconds}s hold$wtText"
                        }
                        ExerciseTrackingType.BODYWEIGHT_REPS -> {
                            val wtText = if (activePoint.weightLbs > 0.0) "BW + ${activePoint.weightLbs.toInt()} lbs" else "Bodyweight"
                            "$wtText × ${activePoint.reps} reps (${activePoint.totalReps} total)"
                        }
                        ExerciseTrackingType.WEIGHTED -> {
                            "${activePoint.weightLbs.toInt()} lbs × ${activePoint.reps} reps (1RM ~${activePoint.estimated1RM.toInt()} lbs)"
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1E293B))
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${activePoint.date} • ${activePoint.label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = AmberGold,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = pointDetailText,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (pointsToDraw.isNotEmpty()) {
                    InteractiveStrengthChart(
                        points = pointsToDraw,
                        trackingType = trackingType,
                        useSecondaryMetric = selectedMetricTab == 1,
                        selectedIndex = selectedPointIndex,
                        onPointSelected = { index ->
                            selectedPointIndex = index
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tip: Tap on any node to view session details and progression.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF64748B),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF090D14))
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ShowChart,
                                contentDescription = null,
                                tint = ElectricTeal,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No sessions logged for ${currentExercise?.name ?: "this exercise"} yet.\nLog a workout to chart your progression!",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Live Lifetime Volume Milestones Card
        val liveLifetimeVolume = if (allWorkouts.isNotEmpty()) {
            allWorkouts.sumOf { it.totalVolumeLbs }.toInt()
        } else {
            (profile?.totalLifetimeVolumeLbs ?: 0.0).toInt()
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .testTag("lifetime_volume_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ShowChart,
                        contentDescription = null,
                        tint = ElectricTeal,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "LIFETIME WORKOUT VOLUME (${String.format(Locale.US, "%,d", liveLifetimeVolume)} LBS)",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (volumeMilestones.isEmpty()) {
                    Text(
                        text = "Complete workouts to see your weekly and session volume milestones here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                } else {
                    val maxBarVolume = (volumeMilestones.maxOfOrNull { it.volumeLbs } ?: 1.0).coerceAtLeast(1.0)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        volumeMilestones.forEachIndexed { idx, milestone ->
                            val isLatest = idx == volumeMilestones.lastIndex
                            val fraction = (milestone.volumeLbs / maxBarVolume).toFloat()
                            val volInt = milestone.volumeLbs.toInt()
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "${volInt}#",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isLatest) AmberGold else Color(0xFF94A3B8),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .width(28.dp)
                                        .height((80 * fraction).coerceAtLeast(14f).dp)
                                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                        .background(
                                            if (isLatest) Brush.verticalGradient(
                                                listOf(AmberGold, Color(0xFFD97706))
                                            ) else Brush.verticalGradient(
                                                listOf(ElectricTeal, Color(0xFF065F46))
                                            )
                                        )
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = milestone.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Live Muscle Group Distribution (including Core)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .testTag("muscle_balance_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PieChart,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "MUSCLE GROUP FOCUS & BALANCE",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (muscleGroupFocus.isEmpty()) {
                    Text(
                        text = "Log exercises to track volume distribution across Chest, Back, Legs, Shoulders, Arms, and Core.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                } else {
                    muscleGroupFocus.forEach { stat ->
                        val barColor = when (stat.category) {
                            "Chest" -> AmberGold
                            "Back" -> ElectricTeal
                            "Legs" -> CyanAccent
                            "Arms" -> Color(0xFFA855F7)
                            "Shoulders" -> Color(0xFFF97316)
                            "Core" -> GainGreen
                            else -> AmberGold
                        }
                        MuscleProgressRow(
                            name = stat.displayName,
                            percent = stat.percent,
                            fraction = stat.fraction,
                            color = barColor
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        GoogleSheetsSyncCard(
            viewModel = viewModel,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
    }
}

@Composable
fun InteractiveStrengthChart(
    points: List<ProgressDataPoint>,
    trackingType: ExerciseTrackingType = ExerciseTrackingType.WEIGHTED,
    useSecondaryMetric: Boolean,
    selectedIndex: Int?,
    onPointSelected: (Int) -> Unit
) {
    val accentColor = if (useSecondaryMetric) CyanAccent else AmberGold
    val axisLabelUnit = when (trackingType) {
        ExerciseTrackingType.TIMED_HOLD -> if (useSecondaryMetric) "lb" else "s"
        ExerciseTrackingType.BODYWEIGHT_REPS -> if (useSecondaryMetric) "lb" else ""
        ExerciseTrackingType.WEIGHTED -> "lb"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF090D14))
            .padding(horizontal = 12.dp, vertical = 14.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(points, trackingType, useSecondaryMetric) {
                    detectTapGestures { offset ->
                        val count = points.size
                        if (count > 0) {
                            val leftMargin = 42.dp.toPx()
                            val plotWidth = (size.width - leftMargin).coerceAtLeast(1f)
                            val spacing = plotWidth / (count + 1).toFloat()
                            var bestIdx = 0
                            var minDist = Float.MAX_VALUE
                            points.forEachIndexed { i, _ ->
                                val x = leftMargin + spacing * (i + 1)
                                val dist = kotlin.math.abs(offset.x - x)
                                if (dist < minDist) {
                                    minDist = dist
                                    bestIdx = i
                                }
                            }
                            if (minDist < spacing * 0.85f) {
                                onPointSelected(bestIdx)
                            }
                        }
                    }
                }
        ) {
            val count = points.size
            if (count < 1) return@Canvas

            val values = points.map { pt ->
                when (trackingType) {
                    ExerciseTrackingType.TIMED_HOLD -> {
                        if (useSecondaryMetric) pt.weightLbs.toFloat() else pt.durationSeconds.toFloat()
                    }
                    ExerciseTrackingType.BODYWEIGHT_REPS -> {
                        if (useSecondaryMetric) pt.weightLbs.toFloat() else pt.reps.toFloat()
                    }
                    ExerciseTrackingType.WEIGHTED -> {
                        if (useSecondaryMetric) pt.estimated1RM.toFloat() else pt.weightLbs.toFloat()
                    }
                }
            }
            val rawMin = values.minOrNull() ?: 0f
            val rawMax = values.maxOrNull() ?: 100f
            val pad = maxOf(5f, (rawMax - rawMin) * 0.25f)
            val minVal = maxOf(0f, rawMin - pad)
            val maxVal = rawMax + pad
            val valRange = (maxVal - minVal).coerceAtLeast(10f)

            val leftMargin = 42.dp.toPx()
            val topMargin = 8.dp.toPx()
            val bottomMargin = 24.dp.toPx()
            val plotWidth = (size.width - leftMargin).coerceAtLeast(1f)
            val heightAvail = (size.height - topMargin - bottomMargin).coerceAtLeast(1f)
            val spacing = plotWidth / (count + 1).toFloat()

            val yAxisPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#64748B")
                textSize = 10.sp.toPx()
                textAlign = android.graphics.Paint.Align.RIGHT
                isAntiAlias = true
            }
            val xAxisPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#64748B")
                textSize = 10.sp.toPx()
                isAntiAlias = true
            }

            // Draw horizontal grid lines + Y-axis min/mid/max labels
            val gridLines = 2
            for (g in 0..gridLines) {
                val frac = g.toFloat() / gridLines
                val y = topMargin + heightAvail * frac
                drawLine(
                    color = Color(0xFF1E293B),
                    start = Offset(leftMargin, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f
                )
                val valAtLine = maxVal - (valRange * frac)
                val labelStr = "${Math.round(valAtLine)}$axisLabelUnit"
                drawContext.canvas.nativeCanvas.drawText(
                    labelStr,
                    leftMargin - 6.dp.toPx(),
                    y + 4.dp.toPx(),
                    yAxisPaint
                )
            }

            // Calculate coordinates
            val coords = points.mapIndexed { idx, _ ->
                val x = leftMargin + spacing * (idx + 1)
                val normalizedY = (values[idx] - minVal) / valRange
                val y = topMargin + heightAvail - (normalizedY * heightAvail)
                Offset(x, y)
            }

            // Draw X-axis date labels (first, middle if >=4, last)
            val dateY = size.height - 4.dp.toPx()
            if (coords.isNotEmpty()) {
                xAxisPaint.textAlign = android.graphics.Paint.Align.LEFT
                drawContext.canvas.nativeCanvas.drawText(
                    points.first().date,
                    leftMargin,
                    dateY,
                    xAxisPaint
                )
            }
            if (coords.size >= 4) {
                val midIdx = coords.size / 2
                xAxisPaint.textAlign = android.graphics.Paint.Align.CENTER
                drawContext.canvas.nativeCanvas.drawText(
                    points[midIdx].date,
                    coords[midIdx].x,
                    dateY,
                    xAxisPaint
                )
            }
            if (coords.size >= 2) {
                xAxisPaint.textAlign = android.graphics.Paint.Align.RIGHT
                drawContext.canvas.nativeCanvas.drawText(
                    points.last().date,
                    size.width,
                    dateY,
                    xAxisPaint
                )
            }

            val baselineY = topMargin + heightAvail
            // Draw area gradient fill
            val fillPath = Path().apply {
                moveTo(coords.first().x, baselineY)
                coords.forEach { lineTo(it.x, it.y) }
                lineTo(coords.last().x, baselineY)
                close()
            }

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(accentColor.copy(alpha = 0.28f), Color.Transparent),
                    startY = topMargin,
                    endY = baselineY
                )
            )

            // Draw smooth connecting line
            val strokePath = Path().apply {
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
                path = strokePath,
                color = accentColor,
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            )

            // Draw point markers
            coords.forEachIndexed { i, pt ->
                val isSelected = (i == selectedIndex)

                if (isSelected) {
                    drawCircle(
                        color = accentColor.copy(alpha = 0.35f),
                        radius = 12.dp.toPx(),
                        center = pt
                    )
                }

                drawCircle(
                    color = Color(0xFF0F172A),
                    radius = 6.dp.toPx(),
                    center = pt
                )

                drawCircle(
                    color = if (isSelected) Color.White else accentColor,
                    radius = 4.dp.toPx(),
                    center = pt
                )
            }
        }
    }
}

@Composable
private fun ProgressionStatItem(
    label: String,
    value: String,
    valueColor: Color = Color.White
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            fontSize = 11.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = valueColor,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun MuscleProgressRow(
    name: String,
    percent: Int,
    fraction: Float,
    color: Color
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.bodySmall,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF0F172A))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }
    }
}
