package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.analytics.WeightUnit
import com.example.data.analytics.WeightUnitHelper
import com.example.data.model.ExerciseRecord
import com.example.data.model.ExerciseSet
import com.example.data.model.ExerciseTrackingType
import com.example.data.model.WorkoutExerciseLog
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.ElectricTeal
import com.example.ui.theme.GainGreen
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.TextSecondary
import java.util.Locale

data class FormattedSetDetail(
    val setNumber: Int,
    val primaryLabel: String,
    val secondaryLabel: String,
    val completed: Boolean
)

data class FormattedExerciseBreakdown(
    val logId: String,
    val exerciseName: String,
    val category: String,
    val trackingType: ExerciseTrackingType,
    val summaryLine: String,
    val sets: List<FormattedSetDetail>
)

object WorkoutBreakdownFormatter {

    fun formatWeight(weightLbs: Double, unit: WeightUnit = WeightUnit.LBS): String {
        return WeightUnitHelper.formatWeightNumber(weightLbs, unit)
    }

    fun resolveTrackingType(
        log: WorkoutExerciseLog,
        catalog: List<ExerciseRecord> = emptyList()
    ): ExerciseTrackingType {
        if (log.trackingType.isNotBlank() && log.trackingType != ExerciseTrackingType.WEIGHTED.id) {
            return log.resolvedTrackingType()
        }
        return catalog.find { it.id == log.exerciseId }?.resolvedTrackingType()
            ?: log.resolvedTrackingType()
    }

    fun parseSets(
        log: WorkoutExerciseLog,
        trackingType: ExerciseTrackingType
    ): List<ExerciseSet> {
        val parsed = log.sets.mapIndexed { idx, raw ->
            val item = ExerciseSet.fromMap(raw)
            if (item.setNumber <= 0) item.copy(setNumber = idx + 1) else item
        }
        if (parsed.isNotEmpty()) return parsed

        return listOf(
            ExerciseSet(
                setNumber = 1,
                weightLbs = log.maxWeightLbs,
                reps = if (trackingType == ExerciseTrackingType.TIMED_HOLD) 1 else 8,
                completed = true,
                durationSeconds = log.bestDurationSeconds
            )
        )
    }

    fun formatSetDetail(
        set: ExerciseSet,
        trackingType: ExerciseTrackingType,
        unit: WeightUnit = WeightUnit.LBS
    ): FormattedSetDetail {
        val wStr = formatWeight(set.weightLbs, unit)
        val unitLabel = unit.shortLabel
        return when (trackingType) {
            ExerciseTrackingType.TIMED_HOLD -> {
                val secs = maxOf(0, set.durationSeconds)
                val wtLabel = if (set.weightLbs > 0.0) "+$wStr $unitLabel" else "Bodyweight"
                FormattedSetDetail(
                    setNumber = set.setNumber,
                    primaryLabel = wtLabel,
                    secondaryLabel = "${secs}s hold",
                    completed = set.completed
                )
            }
            ExerciseTrackingType.BODYWEIGHT_REPS -> {
                val wtLabel = if (set.weightLbs > 0.0) "BW + $wStr $unitLabel" else "Bodyweight"
                FormattedSetDetail(
                    setNumber = set.setNumber,
                    primaryLabel = wtLabel,
                    secondaryLabel = "${set.reps} reps",
                    completed = set.completed
                )
            }
            ExerciseTrackingType.WEIGHTED -> {
                FormattedSetDetail(
                    setNumber = set.setNumber,
                    primaryLabel = "$wStr $unitLabel",
                    secondaryLabel = "${set.reps} reps",
                    completed = set.completed
                )
            }
        }
    }

    fun buildExerciseBreakdown(
        log: WorkoutExerciseLog,
        catalog: List<ExerciseRecord> = emptyList(),
        unit: WeightUnit = WeightUnit.LBS
    ): FormattedExerciseBreakdown {
        val trackingType = resolveTrackingType(log, catalog)
        val sets = parseSets(log, trackingType)
        val setCount = sets.size
        val bestSet = sets.maxByOrNull { it.weightLbs }
        val maxWeight = maxOf(log.maxWeightLbs, bestSet?.weightLbs ?: 0.0)
        val unitLabel = unit.shortLabel

        val summaryLine = when (trackingType) {
            ExerciseTrackingType.TIMED_HOLD -> {
                val bestDur = maxOf(log.bestDurationSeconds, sets.maxOfOrNull { it.durationSeconds } ?: 0)
                val wtSuffix = if (maxWeight > 0.0) " · +${formatWeight(maxWeight, unit)} $unitLabel" else " · BW"
                "$setCount sets · Best ${bestDur}s$wtSuffix"
            }
            ExerciseTrackingType.BODYWEIGHT_REPS -> {
                val bestReps = sets.maxOfOrNull { it.reps } ?: 0
                val wtPrefix = if (maxWeight > 0.0) "BW + ${formatWeight(maxWeight, unit)} $unitLabel" else "Bodyweight"
                "$setCount sets · $wtPrefix × $bestReps reps"
            }
            ExerciseTrackingType.WEIGHTED -> {
                val topReps = bestSet?.reps ?: 0
                "$setCount sets · ${formatWeight(maxWeight, unit)} $unitLabel × $topReps reps"
            }
        }

        return FormattedExerciseBreakdown(
            logId = log.id.ifBlank { "${log.workoutId}_${log.exerciseId}_${log.exerciseName}" },
            exerciseName = log.exerciseName.ifBlank { "Exercise" },
            category = log.category.ifBlank { "General" },
            trackingType = trackingType,
            summaryLine = summaryLine,
            sets = sets.map { formatSetDetail(it, trackingType, unit) }
        )
    }
}

@Composable
fun WorkoutBreakdownSection(
    workoutId: String,
    logs: List<WorkoutExerciseLog>,
    exercisesCatalog: List<ExerciseRecord> = emptyList(),
    weightUnit: WeightUnit = WeightUnit.LBS,
    modifier: Modifier = Modifier
) {
    val breakdowns = remember(logs, exercisesCatalog, weightUnit) {
        logs.map { WorkoutBreakdownFormatter.buildExerciseBreakdown(it, exercisesCatalog, weightUnit) }
    }
    var expandAllSets by remember(workoutId) { mutableStateOf(false) }
    var expandedExerciseKeys by remember(workoutId) { mutableStateOf(emptySet<String>()) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .testTag("workout_breakdown_section_$workoutId")
    ) {
        if (breakdowns.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0B0F17))
                    .border(1.dp, SurfaceCardBorder, RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "No per-exercise set details recorded for this session. Tap \"Edit Sets\" to add exercises, weights, and reps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        } else {
            // Section header with "Expand All / Hide Sets" toggle inside the same card box
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FitnessCenter,
                        contentDescription = null,
                        tint = AmberGold,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "WORKOUT BREAKDOWN (${breakdowns.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberGold,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (expandAllSets) ElectricTeal.copy(alpha = 0.18f) else Color(0xFF0F172A),
                    border = BorderStroke(
                        1.dp,
                        if (expandAllSets) ElectricTeal else SurfaceCardBorder
                    ),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            val next = !expandAllSets
                            expandAllSets = next
                            expandedExerciseKeys = if (next) {
                                breakdowns.mapIndexed { idx, item -> "${idx}_${item.logId}" }.toSet()
                            } else {
                                emptySet()
                            }
                        }
                        .testTag("toggle_all_sets_button_$workoutId")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (expandAllSets) "Hide Sets" else "Show Weight & Reps",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (expandAllSets) ElectricTeal else Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (expandAllSets) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (expandAllSets) "Hide set details" else "Show all sets weight and reps",
                            tint = if (expandAllSets) ElectricTeal else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                breakdowns.forEachIndexed { idx, item ->
                    val itemKey = "${idx}_${item.logId}"
                    val isExerciseExpanded = expandAllSets || expandedExerciseKeys.contains(itemKey)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0B0F17))
                            .border(
                                1.dp,
                                if (isExerciseExpanded) ElectricTeal.copy(alpha = 0.45f) else Color(0xFF1E293B),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                if (expandAllSets) {
                                    expandAllSets = false
                                    expandedExerciseKeys = breakdowns.mapIndexed { i, b -> "${i}_${b.logId}" }.toSet() - itemKey
                                } else {
                                    expandedExerciseKeys = if (isExerciseExpanded) {
                                        expandedExerciseKeys - itemKey
                                    } else {
                                        expandedExerciseKeys + itemKey
                                    }
                                }
                            }
                            .padding(12.dp)
                            .testTag("workout_breakdown_exercise_${workoutId}_$idx")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 40.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = item.exerciseName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFF1E293B))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = item.category,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AmberGold,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = item.summaryLine,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CyanAccent,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Icon(
                                imageVector = if (isExerciseExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isExerciseExpanded) "Collapse ${item.exerciseName} sets" else "Expand ${item.exerciseName} sets",
                                tint = if (isExerciseExpanded) ElectricTeal else TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        AnimatedVisibility(
                            visible = isExerciseExpanded,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                item.sets.forEach { setDetail ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF111827))
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Set ${setDetail.setNumber}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = TextSecondary,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.width(56.dp)
                                        )
                                        Text(
                                            text = setDetail.primaryLabel,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = setDetail.secondaryLabel,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = ElectricTeal,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            if (setDetail.completed) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Set completed",
                                                    tint = GainGreen,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
