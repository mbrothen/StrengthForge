package com.example.data.analytics

import com.example.data.model.ExerciseRecord
import com.example.data.model.ExerciseSet
import com.example.data.model.ExerciseTrackingType
import com.example.data.model.UserProfile
import com.example.data.model.WorkoutExerciseLog
import com.example.data.model.WorkoutRoutine
import com.example.data.model.WorkoutSession
import com.example.data.schedule.Weekday
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class DashboardTimeframe(val label: String) {
    THIS_WEEK("This Week"),
    LAST_30_DAYS("Last 30 Days"),
    ALL_TIME("All Time")
}

data class DashboardMetrics(
    val totalVolumeLbs: Double,
    val sessionCount: Int,
    val avgDurationMinutes: Int,
    val completedSets: Int,
    val gainPercent: Double,
    val sparklinePoints: List<Float>,
    val adherenceLabel: String
)

data class VolumeMilestone(
    val label: String,
    val volumeLbs: Double
)

data class MuscleGroupStat(
    val category: String,
    val displayName: String,
    val volumeOrScore: Double,
    val percent: Int,
    val fraction: Float
)

data class ExerciseLastPerformance(
    val exerciseId: String,
    val dateString: String,
    val summaryText: String,
    val weightLbs: Double,
    val reps: Int,
    val durationSeconds: Int,
    val estimated1RM: Double
)

data class ExerciseHistoricalBest(
    val exerciseId: String,
    val maxWeightLbs: Double,
    val best1RM: Double,
    val bestDurationSeconds: Int,
    val bestReps: Int
)

data class PerformedExercisePr(
    val exercise: ExerciseRecord,
    val trackingType: ExerciseTrackingType,
    val startingWeightLbs: Double,
    val maxWeightLbs: Double,
    val best1RM: Double,
    val startingDurationSeconds: Int,
    val bestDurationSeconds: Int,
    val bestReps: Int,
    val percentIncrease: Double,
    val sessionsCount: Int,
    val totalSetsLogged: Int
) {
    fun formatPrimaryMetricLabel(unit: WeightUnit = WeightUnit.LBS): String {
        val unitLabel = unit.shortLabel
        return when (trackingType) {
            ExerciseTrackingType.TIMED_HOLD -> {
                if (maxWeightLbs > 0.0) "${bestDurationSeconds}s (+${WeightUnitHelper.formatWeightNumber(maxWeightLbs, unit)} $unitLabel)"
                else "${bestDurationSeconds}s hold"
            }
            ExerciseTrackingType.BODYWEIGHT_REPS -> {
                if (maxWeightLbs > 0.0) "BW + ${WeightUnitHelper.formatWeightNumber(maxWeightLbs, unit)} $unitLabel"
                else if (bestReps > 0) "BW ($bestReps reps)"
                else "Bodyweight"
            }
            ExerciseTrackingType.WEIGHTED -> "${WeightUnitHelper.formatWeightNumber(maxWeightLbs, unit)} $unitLabel"
        }
    }
}

object WorkoutAnalyticsHelper {

    private val displayDateSdf = SimpleDateFormat("MMM d, yyyy", Locale.US)

    fun resolveWorkoutDate(workout: WorkoutSession): Date? {
        workout.createdAt?.toDate()?.let { return it }
        if (workout.dateString.isNotBlank()) {
            try {
                return displayDateSdf.parse(workout.dateString)
            } catch (_: Exception) {
            }
        }
        return null
    }

    fun resolveLogDate(log: WorkoutExerciseLog): Date? {
        log.date?.toDate()?.let { return it }
        if (log.dateString.isNotBlank()) {
            try {
                return displayDateSdf.parse(log.dateString)
            } catch (_: Exception) {
            }
        }
        return null
    }

    fun filterWorkoutsByTimeframe(
        workouts: List<WorkoutSession>,
        timeframe: DashboardTimeframe,
        now: Date = Date(),
        firstDayOfWeek: Weekday = Weekday.MONDAY
    ): List<WorkoutSession> {
        if (timeframe == DashboardTimeframe.ALL_TIME) return workouts

        val cutoffMillis = when (timeframe) {
            DashboardTimeframe.THIS_WEEK -> {
                val cal = Calendar.getInstance().apply {
                    time = now
                    this.firstDayOfWeek = firstDayOfWeek.calendarDayOfWeek
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    val currentIso = Weekday.fromCalendarDayOfWeek(get(Calendar.DAY_OF_WEEK)).isoDay
                    val daysSinceStart = (currentIso - firstDayOfWeek.isoDay + 7) % 7
                    add(Calendar.DAY_OF_YEAR, -daysSinceStart)
                }
                cal.timeInMillis
            }
            DashboardTimeframe.LAST_30_DAYS -> {
                val cal = Calendar.getInstance().apply {
                    time = now
                    add(Calendar.DAY_OF_YEAR, -30)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                cal.timeInMillis
            }
            DashboardTimeframe.ALL_TIME -> 0L
        }

        return workouts.filter { w ->
            val time = resolveWorkoutDate(w)?.time ?: return@filter false
            time >= cutoffMillis && time <= now.time + 86_400_000L
        }
    }

    fun computeWeeklyStreak(
        workouts: List<WorkoutSession>,
        now: Date = Date(),
        firstDayOfWeek: Weekday = Weekday.MONDAY
    ): Int {
        if (workouts.isEmpty()) return 0

        val workoutWeekStarts = workouts.mapNotNull { resolveWorkoutDate(it) }
            .map { startOfWeekMillis(it, firstDayOfWeek) }
            .toSet()

        if (workoutWeekStarts.isEmpty()) return 0

        val currentWeekStart = startOfWeekMillis(now, firstDayOfWeek)
        val cal = Calendar.getInstance().apply {
            timeInMillis = currentWeekStart
            this.firstDayOfWeek = firstDayOfWeek.calendarDayOfWeek
        }
        if (!workoutWeekStarts.contains(currentWeekStart)) {
            // Allow streak to continue if the user hasn't lifted yet this week, as long as last week has a session
            cal.add(Calendar.DAY_OF_YEAR, -7)
            if (!workoutWeekStarts.contains(cal.timeInMillis)) {
                return 0
            }
        }

        var streak = 0
        while (workoutWeekStarts.contains(cal.timeInMillis)) {
            streak++
            cal.add(Calendar.DAY_OF_YEAR, -7)
        }
        return streak
    }

    private fun startOfWeekMillis(date: Date, firstDayOfWeek: Weekday): Long {
        val cal = Calendar.getInstance().apply {
            time = date
            this.firstDayOfWeek = firstDayOfWeek.calendarDayOfWeek
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            val currentIso = Weekday.fromCalendarDayOfWeek(get(Calendar.DAY_OF_WEEK)).isoDay
            val daysSinceStart = (currentIso - firstDayOfWeek.isoDay + 7) % 7
            add(Calendar.DAY_OF_YEAR, -daysSinceStart)
        }
        return cal.timeInMillis
    }

    fun computeDashboardMetrics(
        allWorkouts: List<WorkoutSession>,
        timeframe: DashboardTimeframe,
        profile: UserProfile,
        completedExercises: List<ExerciseRecord>,
        routines: List<WorkoutRoutine>,
        now: Date = Date(),
        firstDayOfWeek: Weekday = profile.resolvedFirstDayOfWeek()
    ): DashboardMetrics {
        val filtered = filterWorkoutsByTimeframe(allWorkouts, timeframe, now, firstDayOfWeek)
        val sortedChronological = filtered.sortedBy { resolveWorkoutDate(it)?.time ?: 0L }

        val totalVolume = sortedChronological.sumOf { it.totalVolumeLbs }
        val sessionCount = sortedChronological.size
        val completedSets = sortedChronological.sumOf { it.completedSetsCount }
        val avgDuration = if (sortedChronological.isNotEmpty()) {
            sortedChronological.sumOf { it.durationMinutes } / sortedChronological.size
        } else {
            0
        }

        val gainPercent = computeVolumeOrStrengthGainPercent(sortedChronological, completedExercises)
        val sparkline = sortedChronological.takeLast(8).map { it.totalVolumeLbs.toFloat() }

        val scheduledDaysCount = routines.flatMap { it.scheduledDays }.distinct().size
        val adherenceLabel = when {
            timeframe == DashboardTimeframe.THIS_WEEK && scheduledDaysCount > 0 -> {
                val uniqueDays = sortedChronological.mapNotNull { w ->
                    resolveWorkoutDate(w)?.let {
                        val c = Calendar.getInstance().apply { time = it }
                        "${c.get(Calendar.YEAR)}-${c.get(Calendar.DAY_OF_YEAR)}"
                    }
                }.distinct().size
                val pct = ((uniqueDays.toDouble() / scheduledDaysCount) * 100.0).toInt().coerceIn(0, 100)
                "$uniqueDays/$scheduledDaysCount days ($pct%)"
            }
            sessionCount > 0 -> "$sessionCount logged"
            else -> "No sessions yet"
        }

        return DashboardMetrics(
            totalVolumeLbs = totalVolume,
            sessionCount = sessionCount,
            avgDurationMinutes = avgDuration,
            completedSets = completedSets,
            gainPercent = gainPercent,
            sparklinePoints = sparkline,
            adherenceLabel = adherenceLabel
        )
    }

    private fun computeVolumeOrStrengthGainPercent(
        sortedWorkouts: List<WorkoutSession>,
        completedExercises: List<ExerciseRecord>
    ): Double {
        if (sortedWorkouts.size >= 2) {
            val firstVol = sortedWorkouts.first().totalVolumeLbs
            val lastVol = sortedWorkouts.last().totalVolumeLbs
            if (firstVol > 0.0) {
                val pct = ((lastVol - firstVol) / firstVol) * 100.0
                return Math.round(pct * 10.0) / 10.0
            }
        }
        val bestExerciseGain = completedExercises.maxOfOrNull { it.percentIncrease } ?: 0.0
        return Math.round(bestExerciseGain * 10.0) / 10.0
    }

    fun buildVolumeMilestones(
        workouts: List<WorkoutSession>,
        maxBars: Int = 6,
        firstDayOfWeek: Weekday = Weekday.MONDAY
    ): List<VolumeMilestone> {
        if (workouts.isEmpty()) return emptyList()
        val sorted = workouts.sortedBy { resolveWorkoutDate(it)?.time ?: 0L }
        val weekBuckets = linkedMapOf<Long, Pair<String, Double>>()
        val weekLabelSdf = SimpleDateFormat("M/d", Locale.US)

        sorted.forEach { workout ->
            val date = resolveWorkoutDate(workout)
            if (date != null) {
                val weekStart = startOfWeekMillis(date, firstDayOfWeek)
                val label = weekLabelSdf.format(Date(weekStart))
                val prev = weekBuckets[weekStart]
                weekBuckets[weekStart] = label to ((prev?.second ?: 0.0) + workout.totalVolumeLbs)
            }
        }

        // If all workouts fall in 1–2 weeks, show individual chronological sessions for richer visual feedback
        if (weekBuckets.size <= 2) {
            val recent = sorted.takeLast(maxBars)
            val dateShort = SimpleDateFormat("M/d", Locale.US)
            return recent.mapIndexed { idx, w ->
                val lbl = resolveWorkoutDate(w)?.let { dateShort.format(it) } ?: "S${idx + 1}"
                VolumeMilestone(label = lbl, volumeLbs = w.totalVolumeLbs)
            }
        }

        return weekBuckets.values.toList()
            .takeLast(maxBars)
            .map { (label, vol) -> VolumeMilestone(label = label, volumeLbs = vol) }
    }

    fun computeMuscleGroupFocus(logs: List<WorkoutExerciseLog>): List<MuscleGroupStat> {
        if (logs.isEmpty()) return emptyList()

        val displayNames = mapOf(
            "Chest" to "Chest (Push Focus)",
            "Back" to "Back (Pull & Rows)",
            "Legs" to "Legs (Squats)",
            "Arms" to "Arms (Biceps & Triceps)",
            "Shoulders" to "Shoulders (Pressing)",
            "Core" to "Core (Planks & Abs)"
        )

        val scores = linkedMapOf(
            "Chest" to 0.0,
            "Back" to 0.0,
            "Legs" to 0.0,
            "Arms" to 0.0,
            "Shoulders" to 0.0,
            "Core" to 0.0
        )

        logs.forEach { log ->
            val cat = log.category.trim().ifBlank { "Chest" }
            val resolvedScore = when {
                log.totalVolumeLbs > 0.0 -> log.totalVolumeLbs
                log.totalReps > 0 -> log.totalReps * 15.0
                log.totalDurationSeconds > 0 -> log.totalDurationSeconds * 5.0
                else -> maxOf(1, log.sets.size) * 100.0
            }
            scores[cat] = (scores[cat] ?: 0.0) + resolvedScore
        }

        val active = scores.filterValues { it > 0.0 }
        val total = active.values.sum()
        if (total <= 0.0) return emptyList()

        return active.entries
            .sortedByDescending { it.value }
            .map { (cat, score) ->
                val ratio = (score / total).toFloat().coerceIn(0f, 1f)
                MuscleGroupStat(
                    category = cat,
                    displayName = displayNames[cat] ?: cat,
                    volumeOrScore = score,
                    percent = Math.round(ratio * 100f),
                    fraction = ratio
                )
            }
    }

    fun computeLastPerformanceByExercise(
        logs: List<WorkoutExerciseLog>,
        exercises: List<ExerciseRecord>,
        excludeWorkoutId: String? = null,
        unit: WeightUnit = WeightUnit.LBS
    ): Map<String, ExerciseLastPerformance> {
        val eligibleLogs = logs
            .filter { excludeWorkoutId == null || it.workoutId != excludeWorkoutId }
            .sortedBy { resolveLogDate(it)?.time ?: 0L }

        val unitLabel = unit.shortLabel
        val result = mutableMapOf<String, ExerciseLastPerformance>()
        for (record in exercises) {
            val latest = eligibleLogs.lastOrNull { record.matchesLog(it) } ?: continue
            val parsedSets = latest.sets.map { ExerciseSet.fromMap(it) }
            val tracking = if (latest.trackingType.isNotBlank()) latest.resolvedTrackingType() else record.resolvedTrackingType()
            val maxW = if (latest.maxWeightLbs > 0.0) latest.maxWeightLbs else (parsedSets.maxOfOrNull { it.weightLbs } ?: 0.0)
            val bestReps = parsedSets.maxOfOrNull { it.reps } ?: record.defaultReps
            val bestDur = maxOf(latest.bestDurationSeconds, parsedSets.maxOfOrNull { it.durationSeconds } ?: 0)
            val e1RM = if (latest.estimatedOneRepMaxLbs > 0.0) latest.estimatedOneRepMaxLbs else maxW * (1.0 + bestReps / 30.0)

            val summary = when (tracking) {
                ExerciseTrackingType.TIMED_HOLD -> {
                    if (maxW > 0.0) "${bestDur}s (+${WeightUnitHelper.formatWeightNumber(maxW, unit)} $unitLabel)" else "${bestDur}s hold"
                }
                ExerciseTrackingType.BODYWEIGHT_REPS -> {
                    if (maxW > 0.0) "BW + ${WeightUnitHelper.formatWeightNumber(maxW, unit)} $unitLabel × $bestReps" else "BW × $bestReps reps"
                }
                ExerciseTrackingType.WEIGHTED -> "${WeightUnitHelper.formatWeightNumber(maxW, unit)} $unitLabel × $bestReps"
            }

            result[record.id] = ExerciseLastPerformance(
                exerciseId = record.id,
                dateString = latest.dateString,
                summaryText = summary,
                weightLbs = maxW,
                reps = bestReps,
                durationSeconds = bestDur,
                estimated1RM = Math.round(e1RM * 10.0) / 10.0
            )
        }
        return result
    }

    fun computeHistoricalBestByExercise(
        logs: List<WorkoutExerciseLog>,
        exercises: List<ExerciseRecord>,
        excludeWorkoutId: String? = null
    ): Map<String, ExerciseHistoricalBest> {
        val eligibleLogs = logs.filter { excludeWorkoutId == null || it.workoutId != excludeWorkoutId }
        val result = mutableMapOf<String, ExerciseHistoricalBest>()
        for (record in exercises) {
            val matching = eligibleLogs.filter { record.matchesLog(it) }
            if (matching.isEmpty()) continue
            val maxW = matching.maxOf { it.maxWeightLbs }
            val best1RM = matching.maxOf { it.estimatedOneRepMaxLbs }
            val bestDur = matching.maxOf { log ->
                maxOf(log.bestDurationSeconds, log.sets.maxOfOrNull { ExerciseSet.fromMap(it).durationSeconds } ?: 0)
            }
            val bestReps = matching.maxOf { log ->
                log.sets.maxOfOrNull { ExerciseSet.fromMap(it).reps } ?: 0
            }
            result[record.id] = ExerciseHistoricalBest(
                exerciseId = record.id,
                maxWeightLbs = maxW,
                best1RM = best1RM,
                bestDurationSeconds = bestDur,
                bestReps = bestReps
            )
        }
        return result
    }

    fun isNewPersonalRecord(
        sets: List<ExerciseSet>,
        trackingType: ExerciseTrackingType,
        historicalBest: ExerciseHistoricalBest?
    ): Boolean {
        if (historicalBest == null || sets.isEmpty()) return false
        return when (trackingType) {
            ExerciseTrackingType.TIMED_HOLD -> {
                val maxDur = sets.maxOfOrNull { it.durationSeconds } ?: 0
                val maxW = sets.maxOfOrNull { it.weightLbs } ?: 0.0
                maxDur > historicalBest.bestDurationSeconds || maxW > historicalBest.maxWeightLbs
            }
            ExerciseTrackingType.BODYWEIGHT_REPS -> {
                val maxW = sets.maxOfOrNull { it.weightLbs } ?: 0.0
                val maxReps = sets.maxOfOrNull { it.reps } ?: 0
                maxW > historicalBest.maxWeightLbs ||
                    (maxW >= historicalBest.maxWeightLbs && maxReps > historicalBest.bestReps)
            }
            ExerciseTrackingType.WEIGHTED -> {
                val maxW = sets.maxOfOrNull { it.weightLbs } ?: 0.0
                val max1RM = sets.maxOfOrNull { it.weightLbs * (1.0 + it.reps / 30.0) } ?: 0.0
                maxW > historicalBest.maxWeightLbs ||
                    (historicalBest.best1RM > 0.0 && max1RM > historicalBest.best1RM + 0.5)
            }
        }
    }

    fun computePerformedExercisePrFor(
        record: ExerciseRecord,
        logs: List<WorkoutExerciseLog>
    ): PerformedExercisePr? {
        val matchingLogs = logs
            .filter { record.matchesLog(it) }
            .sortedBy { resolveLogDate(it)?.time ?: 0L }
        if (matchingLogs.isEmpty()) return null

        val nonDefaultLogType = matchingLogs.lastOrNull {
            it.trackingType.isNotBlank() && it.trackingType != ExerciseTrackingType.WEIGHTED.id
        }?.resolvedTrackingType()
        val resolvedTracking = nonDefaultLogType ?: record.resolvedTrackingType()

        data class LogSummary(
            val weight: Double,
            val e1RM: Double,
            val duration: Int,
            val maxReps: Int,
            val loggedSetCount: Int
        )

        val summaries = matchingLogs.map { log ->
            val parsedSets = log.sets.map { ExerciseSet.fromMap(it) }
            val completedSets = parsedSets.filter { it.completed }
            val effectiveSets = completedSets.ifEmpty { parsedSets }

            val setsMaxWeight = effectiveSets.maxOfOrNull { it.weightLbs }
            val logWeight = if (setsMaxWeight != null && (setsMaxWeight > 0.0 || resolvedTracking != ExerciseTrackingType.WEIGHTED)) {
                setsMaxWeight
            } else {
                log.maxWeightLbs
            }

            val sets1RM = effectiveSets.maxOfOrNull { it.weightLbs * (1.0 + it.reps / 30.0) }
            val log1RM = if (sets1RM != null && sets1RM > 0.0) {
                sets1RM
            } else if (log.estimatedOneRepMaxLbs > 0.0) {
                log.estimatedOneRepMaxLbs
            } else {
                logWeight
            }

            val setsBestDur = effectiveSets.maxOfOrNull { it.durationSeconds } ?: 0
            val logDur = maxOf(log.bestDurationSeconds, setsBestDur)
            val logReps = effectiveSets.maxOfOrNull { it.reps } ?: 0
            val setCount = if (completedSets.isNotEmpty()) completedSets.size else parsedSets.size.coerceAtLeast(1)

            LogSummary(
                weight = logWeight,
                e1RM = log1RM,
                duration = logDur,
                maxReps = logReps,
                loggedSetCount = setCount
            )
        }

        val startWeight = summaries.first().weight
        val maxWeight = summaries.maxOf { it.weight }
        val best1RM = Math.round(summaries.maxOf { it.e1RM } * 10.0) / 10.0
        val startDur = summaries.first().duration
        val bestDur = summaries.maxOf { it.duration }
        val firstReps = summaries.first().maxReps
        val bestReps = summaries.maxOf { it.maxReps }
        val totalSets = summaries.sumOf { it.loggedSetCount }

        val percentGain = when (resolvedTracking) {
            ExerciseTrackingType.TIMED_HOLD -> {
                if (startDur > 0) Math.round(((bestDur - startDur).toDouble() / startDur * 100.0) * 10.0) / 10.0 else 0.0
            }
            ExerciseTrackingType.BODYWEIGHT_REPS -> {
                when {
                    startWeight > 0.0 -> Math.round(((maxWeight - startWeight) / startWeight * 100.0) * 10.0) / 10.0
                    firstReps > 0 -> Math.round(((bestReps - firstReps).toDouble() / firstReps * 100.0) * 10.0) / 10.0
                    else -> 0.0
                }
            }
            ExerciseTrackingType.WEIGHTED -> {
                if (startWeight > 0.0) Math.round(((maxWeight - startWeight) / startWeight * 100.0) * 10.0) / 10.0 else 0.0
            }
        }

        return PerformedExercisePr(
            exercise = record,
            trackingType = resolvedTracking,
            startingWeightLbs = startWeight,
            maxWeightLbs = maxWeight,
            best1RM = best1RM,
            startingDurationSeconds = startDur,
            bestDurationSeconds = bestDur,
            bestReps = bestReps,
            percentIncrease = percentGain,
            sessionsCount = matchingLogs.size,
            totalSetsLogged = totalSets
        )
    }

    fun computePerformedExercisePrs(
        logs: List<WorkoutExerciseLog>,
        exercises: List<ExerciseRecord>
    ): List<PerformedExercisePr> {
        return exercises.mapNotNull { record -> computePerformedExercisePrFor(record, logs) }
    }

    private fun formatNum(value: Double): String {
        return if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)
    }
}
