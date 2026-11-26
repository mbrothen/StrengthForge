package com.example.data.export

import com.example.data.model.DailyNutrition
import com.example.data.model.ExerciseRecord
import com.example.data.model.ExerciseSet
import com.example.data.model.ExerciseTrackingType
import com.example.data.model.UserProfile
import com.example.data.model.WorkoutExerciseLog
import com.example.data.model.WorkoutSession
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CsvExportResult(
    val file: File,
    val workoutCount: Int,
    val rowCount: Int
)

object WorkoutCsvExporter {

    val CSV_HEADERS = listOf(
        "Workout Date",
        "Display Date",
        "Workout ID",
        "Workout Name",
        "Workout Duration (min)",
        "Rest Period (sec)",
        "Session Total Volume (lbs)",
        "Session Completed Sets",
        "Workout Notes",
        "Exercise Name",
        "Muscle Category",
        "Tracking Type",
        "Set Number",
        "Weight (lbs)",
        "Reps",
        "Hold Duration (sec)",
        "Set Completed",
        "Set Volume (lbs)",
        "Exercise Max Weight (lbs)",
        "Exercise Est. 1RM (lbs)",
        "Exercise Total Volume (lbs)",
        "Exercise Best Hold (sec)"
    )

    fun buildWorkoutsCsvContent(
        workouts: List<WorkoutSession>,
        logs: List<WorkoutExerciseLog>,
        exercises: List<ExerciseRecord> = emptyList()
    ): Pair<String, Int> {
        val isoSdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val sortedWorkouts = workouts.sortedBy { it.createdAt?.toDate()?.time ?: 0L }
        val sortedLogs = logs.sortedBy { it.date?.toDate()?.time ?: 0L }
        val logsByWorkoutId = sortedLogs.groupBy { it.workoutId }
        val catalogById = exercises.associateBy { it.id }

        val sb = StringBuilder()
        sb.append(CSV_HEADERS.joinToString(",") { escapeCsv(it) }).append("\r\n")

        var rowCount = 0

        fun appendLogRows(workout: WorkoutSession?, log: WorkoutExerciseLog) {
            val isoDate = workout?.createdAt?.toDate()?.let { isoSdf.format(it) }
                ?: log.date?.toDate()?.let { isoSdf.format(it) }
                ?: workout?.dateString?.ifBlank { log.dateString }
                ?: log.dateString
            val displayDate = workout?.dateString?.ifBlank { log.dateString } ?: log.dateString
            val tracking = resolveTrackingType(log, catalogById[log.exerciseId])

            val parsedSets = log.sets.mapIndexed { idx, raw ->
                val s = ExerciseSet.fromMap(raw)
                if (s.setNumber <= 0) s.copy(setNumber = idx + 1) else s
            }.ifEmpty {
                listOf(
                    ExerciseSet(
                        setNumber = 1,
                        weightLbs = log.maxWeightLbs,
                        reps = if (tracking == ExerciseTrackingType.TIMED_HOLD) 1 else 8,
                        completed = true,
                        durationSeconds = log.bestDurationSeconds
                    )
                )
            }

            parsedSets.forEach { setItem ->
                val setVolume = if (tracking == ExerciseTrackingType.TIMED_HOLD) {
                    0.0
                } else {
                    setItem.weightLbs * setItem.reps
                }
                val row = listOf(
                    isoDate,
                    displayDate,
                    workout?.id ?: log.workoutId,
                    workout?.name ?: "",
                    (workout?.durationMinutes ?: 0).toString(),
                    (workout?.restSeconds ?: 90).toString(),
                    formatNumber(workout?.totalVolumeLbs ?: 0.0),
                    (workout?.completedSetsCount ?: 0).toString(),
                    workout?.notes ?: "",
                    log.exerciseName,
                    log.category,
                    tracking.id,
                    setItem.setNumber.toString(),
                    formatNumber(setItem.weightLbs),
                    setItem.reps.toString(),
                    setItem.durationSeconds.toString(),
                    setItem.completed.toString(),
                    formatNumber(setVolume),
                    formatNumber(log.maxWeightLbs),
                    formatNumber(log.estimatedOneRepMaxLbs),
                    formatNumber(log.totalVolumeLbs),
                    log.bestDurationSeconds.toString()
                )
                sb.append(row.joinToString(",") { escapeCsv(it) }).append("\r\n")
                rowCount++
            }
        }

        val consumedWorkoutIds = mutableSetOf<String>()
        sortedWorkouts.forEach { workout ->
            consumedWorkoutIds.add(workout.id)
            val matchedLogs = logsByWorkoutId[workout.id].orEmpty()
            if (matchedLogs.isEmpty()) {
                val isoDate = workout.createdAt?.toDate()?.let { isoSdf.format(it) } ?: workout.dateString
                val row = listOf(
                    isoDate,
                    workout.dateString,
                    workout.id,
                    workout.name,
                    workout.durationMinutes.toString(),
                    workout.restSeconds.toString(),
                    formatNumber(workout.totalVolumeLbs),
                    workout.completedSetsCount.toString(),
                    workout.notes,
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    ""
                )
                sb.append(row.joinToString(",") { escapeCsv(it) }).append("\r\n")
                rowCount++
            } else {
                matchedLogs.forEach { log -> appendLogRows(workout, log) }
            }
        }

        // Include any orphan exercise logs if a workout summary document is absent
        sortedLogs.filter { it.workoutId.isBlank() || !consumedWorkoutIds.contains(it.workoutId) }
            .forEach { orphanLog ->
                appendLogRows(null, orphanLog)
            }

        return sb.toString() to rowCount
    }

    fun writeWorkoutsCsvFile(
        outputFile: File,
        workouts: List<WorkoutSession>,
        logs: List<WorkoutExerciseLog>,
        exercises: List<ExerciseRecord> = emptyList()
    ): CsvExportResult {
        outputFile.parentFile?.mkdirs()
        val (csvContent, rowCount) = buildWorkoutsCsvContent(workouts, logs, exercises)
        outputFile.writeText(csvContent, Charsets.UTF_8)
        return CsvExportResult(
            file = outputFile,
            workoutCount = workouts.size,
            rowCount = rowCount
        )
    }

    fun defaultCsvFilename(date: Date = Date()): String {
        val dateStamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)
        return "StrengthForge_Workouts_$dateStamp.csv"
    }

    val NUTRITION_CSV_HEADERS = listOf(
        "Date",
        "Calories (kcal)",
        "Target Min (kcal)",
        "Target Max (kcal)",
        "Protein (g)",
        "Target Protein (g)",
        "Carbs (g)",
        "Target Carbs (g)",
        "Fats (g)",
        "Target Fats (g)",
        "Within Calorie Target",
        "Meals Logged"
    )

    fun buildNutritionCsvContent(
        nutritionLogs: List<DailyNutrition>,
        profile: UserProfile = UserProfile()
    ): Pair<String, Int> {
        val sb = StringBuilder()
        sb.append(NUTRITION_CSV_HEADERS.joinToString(",") { escapeCsv(it) }).append("\r\n")
        val sorted = nutritionLogs.sortedBy { it.dateString.ifBlank { it.id } }
        var rows = 0
        sorted.forEach { day ->
            val dateVal = day.dateString.ifBlank { day.id }
            val within = day.calories in profile.targetCaloriesMin..profile.targetCaloriesMax
            val mealsSummary = day.parsedMeals().joinToString("; ") {
                "${it.name} (${it.calories} kcal, ${it.proteinG}P/${it.carbsG}C/${it.fatsG}F)"
            }
            val row = listOf(
                dateVal,
                day.calories.toString(),
                profile.targetCaloriesMin.toString(),
                profile.targetCaloriesMax.toString(),
                day.proteinG.toString(),
                profile.proteinGrams.toString(),
                day.carbsG.toString(),
                profile.carbsGrams.toString(),
                day.fatsG.toString(),
                profile.fatsGrams.toString(),
                within.toString(),
                mealsSummary
            )
            sb.append(row.joinToString(",") { escapeCsv(it) }).append("\r\n")
            rows++
        }
        return sb.toString() to rows
    }

    fun writeNutritionCsvFile(
        outputFile: File,
        nutritionLogs: List<DailyNutrition>,
        profile: UserProfile = UserProfile()
    ): CsvExportResult {
        outputFile.parentFile?.mkdirs()
        val (content, rowCount) = buildNutritionCsvContent(nutritionLogs, profile)
        outputFile.writeText(content, Charsets.UTF_8)
        return CsvExportResult(
            file = outputFile,
            workoutCount = rowCount,
            rowCount = rowCount
        )
    }

    fun defaultNutritionCsvFilename(date: Date = Date()): String {
        val dateStamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)
        return "StrengthForge_Nutrition_$dateStamp.csv"
    }

    private fun resolveTrackingType(
        log: WorkoutExerciseLog,
        catalogMatch: ExerciseRecord?
    ): ExerciseTrackingType {
        if (log.trackingType.isNotBlank() && log.trackingType != ExerciseTrackingType.WEIGHTED.id) {
            return log.resolvedTrackingType()
        }
        return catalogMatch?.resolvedTrackingType() ?: log.resolvedTrackingType()
    }

    fun escapeCsv(value: String): String {
        val needsQuoting = value.contains(',') ||
            value.contains('"') ||
            value.contains('\n') ||
            value.contains('\r')
        return if (needsQuoting) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    private fun formatNumber(value: Double): String {
        val rounded = Math.round(value * 10.0) / 10.0
        return if (rounded % 1.0 == 0.0) {
            rounded.toInt().toString()
        } else {
            String.format(Locale.US, "%.1f", rounded)
        }
    }
}
