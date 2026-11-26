package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Lifter",
    val targetCalories: Int = 2550,
    val targetCaloriesMin: Int = 2500,
    val targetCaloriesMax: Int = 2600,
    val proteinGrams: Int = 190,
    val carbsGrams: Int = 250,
    val fatsGrams: Int = 80,
    val totalWorkoutsLogged: Int = 5,
    val totalLifetimeVolumeLbs: Double = 6275.0,
    val currentWeeklyStreak: Int = 1,
    val googleSheetId: String = "",
    val googleSheetUrl: String = "",
    val lastSheetSyncAt: String = "",
    val autoSyncSheets: Boolean = true,
    val mealPresets: List<Map<String, Any>> = emptyList(),
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedMealPresets(): List<MealPreset> {
        val parsed = mealPresets.mapNotNull { MealPreset.fromMap(it) }
        return parsed.ifEmpty { MealPreset.DEFAULT_PRESETS }
    }
}

@IgnoreExtraProperties
data class WorkoutSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val totalVolumeLbs: Double = 0.0,
    val durationMinutes: Int = 45,
    val notes: String = "",
    val exerciseCount: Int = 0,
    val completedSetsCount: Int = 0,
    val restSeconds: Int = 90,
    val dateString: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

enum class ExerciseTrackingType(
    val id: String,
    val label: String,
    val shortLabel: String
) {
    WEIGHTED("WEIGHTED", "Weighted (lbs × reps)", "Weighted"),
    BODYWEIGHT_REPS("BODYWEIGHT_REPS", "Bodyweight / +Weight (reps)", "BW / +lbs"),
    TIMED_HOLD("TIMED_HOLD", "Timed Hold (seconds)", "Timed (sec)");

    companion object {
        fun fromString(value: String?): ExerciseTrackingType {
            return entries.firstOrNull { it.id.equals(value, ignoreCase = true) } ?: WEIGHTED
        }
    }
}

data class ExerciseSet(
    val setNumber: Int = 1,
    val weightLbs: Double = 0.0,
    val reps: Int = 0,
    val completed: Boolean = true,
    val durationSeconds: Int = 0
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "setNumber" to setNumber,
            "weightLbs" to weightLbs,
            "reps" to reps,
            "completed" to completed,
            "durationSeconds" to durationSeconds
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any?>): ExerciseSet {
            val setNumber = (map["setNumber"] as? Number)?.toInt() ?: 1
            val weightLbs = (map["weightLbs"] as? Number)?.toDouble() ?: 0.0
            val reps = (map["reps"] as? Number)?.toInt() ?: 0
            val completed = (map["completed"] as? Boolean) ?: true
            val durationSeconds = (map["durationSeconds"] as? Number)?.toInt() ?: 0
            return ExerciseSet(setNumber, weightLbs, reps, completed, durationSeconds)
        }
    }
}

@IgnoreExtraProperties
data class WorkoutExerciseLog(
    @DocumentId val id: String = "",
    val workoutId: String = "",
    val userId: String = "",
    val exerciseId: String = "",
    val exerciseName: String = "",
    val category: String = "Chest",
    val trackingType: String = ExerciseTrackingType.WEIGHTED.id,
    val sets: List<Map<String, Any>> = emptyList(),
    val maxWeightLbs: Double = 0.0,
    val totalVolumeLbs: Double = 0.0,
    val estimatedOneRepMaxLbs: Double = 0.0,
    val bestDurationSeconds: Int = 0,
    val totalDurationSeconds: Int = 0,
    val totalReps: Int = 0,
    val dateString: String = "",
    @ServerTimestamp val date: Timestamp? = null
) {
    fun resolvedTrackingType(): ExerciseTrackingType = ExerciseTrackingType.fromString(trackingType)
}

@IgnoreExtraProperties
data class ExerciseRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val category: String = "Chest",
    val trackingType: String = ExerciseTrackingType.WEIGHTED.id,
    val startingWeightLbs: Double = 135.0,
    val currentMaxWeightLbs: Double = 150.0,
    val personalRecord1RMLbs: Double = 175.0,
    val percentIncrease: Double = 11.1,
    val historyCount: Int = 5,
    val defaultSets: Int = 3,
    val defaultReps: Int = 8,
    val defaultDurationSeconds: Int = 60,
    val startingDurationSeconds: Int = 60,
    val currentBestDurationSeconds: Int = 60,
    val bestReps: Int = 0,
    val isPrimary: Boolean = false,
    val notes: String = "",
    @ServerTimestamp val lastLoggedDate: Timestamp? = null
) {
    fun resolvedTrackingType(): ExerciseTrackingType = ExerciseTrackingType.fromString(trackingType)

    fun matchesLog(log: WorkoutExerciseLog): Boolean {
        return (id.isNotBlank() && log.exerciseId == id) ||
            (name.isNotBlank() && log.exerciseName.equals(name, ignoreCase = true))
    }

    fun isCompletedIn(logs: List<WorkoutExerciseLog>): Boolean {
        return logs.any { matchesLog(it) }
    }

    fun formatPrimaryMetricLabel(): String {
        return when (resolvedTrackingType()) {
            ExerciseTrackingType.TIMED_HOLD -> {
                val bestSec = maxOf(currentBestDurationSeconds, defaultDurationSeconds)
                if (currentMaxWeightLbs > 0.0) "${bestSec}s (+${formatWeightNumber(currentMaxWeightLbs)} lbs)"
                else "${bestSec}s hold"
            }
            ExerciseTrackingType.BODYWEIGHT_REPS -> {
                if (currentMaxWeightLbs > 0.0) "BW + ${formatWeightNumber(currentMaxWeightLbs)} lbs"
                else if (bestReps > 0) "BW (${bestReps} reps)"
                else "Bodyweight"
            }
            ExerciseTrackingType.WEIGHTED -> "${formatWeightNumber(currentMaxWeightLbs)} lbs"
        }
    }

    fun formatCompactPrescription(): String {
        return when (resolvedTrackingType()) {
            ExerciseTrackingType.TIMED_HOLD -> {
                val wtNote = if (currentMaxWeightLbs > 0.0) " · +${formatWeightNumber(currentMaxWeightLbs)} lbs" else " · BW"
                "${defaultSets}×${defaultDurationSeconds}s$wtNote"
            }
            ExerciseTrackingType.BODYWEIGHT_REPS -> {
                val wtNote = if (currentMaxWeightLbs > 0.0) "BW + ${formatWeightNumber(currentMaxWeightLbs)} lbs" else "BW"
                "${defaultSets}×${defaultReps} · $wtNote"
            }
            ExerciseTrackingType.WEIGHTED -> {
                "${defaultSets}×${defaultReps} · ${formatWeightNumber(currentMaxWeightLbs)} lbs"
            }
        }
    }

    private fun formatWeightNumber(value: Double): String {
        return if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
    }
}

@IgnoreExtraProperties
data class WorkoutRoutine(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val description: String = "",
    val targetMuscle: String = "Push", // "Push", "Pull", "Legs", "Full Body", "Upper", "Lower", "Custom"
    val exerciseIds: List<String> = emptyList(),
    val defaultSets: Int = 3,
    val restSeconds: Int = 90,
    val scheduledDays: List<Int> = emptyList(), // ISO weekdays: 1 = Mon .. 7 = Sun
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class DailyNutrition(
    @DocumentId val id: String = "",
    val userId: String = "",
    val dateString: String = "",
    val calories: Int = 2520,
    val proteinG: Int = 188,
    val carbsG: Int = 248,
    val fatsG: Int = 79,
    val meals: List<Map<String, Any>> = emptyList(),
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun parsedMeals(): List<MealEntry> = meals.map { MealEntry.fromMap(it) }
}

data class MealEntry(
    val id: String = "",
    val name: String = "",
    val calories: Int = 0,
    val proteinG: Int = 0,
    val carbsG: Int = 0,
    val fatsG: Int = 0,
    val loggedAtLabel: String = ""
) {
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "name" to name,
        "calories" to calories,
        "proteinG" to proteinG,
        "carbsG" to carbsG,
        "fatsG" to fatsG,
        "loggedAtLabel" to loggedAtLabel
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): MealEntry {
            return MealEntry(
                id = (map["id"] as? String).orEmpty(),
                name = (map["name"] as? String).orEmpty().ifBlank { "Meal" },
                calories = (map["calories"] as? Number)?.toInt() ?: 0,
                proteinG = (map["proteinG"] as? Number)?.toInt() ?: 0,
                carbsG = (map["carbsG"] as? Number)?.toInt() ?: 0,
                fatsG = (map["fatsG"] as? Number)?.toInt() ?: 0,
                loggedAtLabel = (map["loggedAtLabel"] as? String).orEmpty()
            )
        }
    }
}

data class MealPreset(
    val id: String = "",
    val name: String = "",
    val calories: Int = 0,
    val proteinG: Int = 0,
    val carbsG: Int = 0,
    val fatsG: Int = 0,
    val isDefault: Boolean = false
) {
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "name" to name,
        "calories" to calories,
        "proteinG" to proteinG,
        "carbsG" to carbsG,
        "fatsG" to fatsG,
        "isDefault" to isDefault
    )

    companion object {
        val DEFAULT_PRESETS = listOf(
            MealPreset(
                id = "preset_shake",
                name = "Protein Shake",
                calories = 350,
                proteinG = 40,
                carbsG = 25,
                fatsG = 5,
                isDefault = true
            ),
            MealPreset(
                id = "preset_lunch",
                name = "Chicken & Rice",
                calories = 650,
                proteinG = 50,
                carbsG = 75,
                fatsG = 15,
                isDefault = true
            )
        )

        fun fromMap(map: Map<String, Any?>): MealPreset? {
            val name = (map["name"] as? String).orEmpty().trim()
            if (name.isBlank()) return null
            return MealPreset(
                id = (map["id"] as? String).orEmpty().ifBlank { "preset_${name.lowercase().replace(" ", "_")}" },
                name = name,
                calories = (map["calories"] as? Number)?.toInt() ?: 0,
                proteinG = (map["proteinG"] as? Number)?.toInt() ?: 0,
                carbsG = (map["carbsG"] as? Number)?.toInt() ?: 0,
                fatsG = (map["fatsG"] as? Number)?.toInt() ?: 0,
                isDefault = (map["isDefault"] as? Boolean) ?: false
            )
        }
    }
}

// Helper model for visual progression data points
data class ProgressDataPoint(
    val label: String,
    val weightLbs: Double,
    val reps: Int,
    val estimated1RM: Double,
    val date: String,
    val trackingType: ExerciseTrackingType = ExerciseTrackingType.WEIGHTED,
    val durationSeconds: Int = 0,
    val totalReps: Int = 0
)
