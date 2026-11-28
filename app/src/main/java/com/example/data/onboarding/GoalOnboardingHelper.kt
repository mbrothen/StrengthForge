package com.example.data.onboarding

import com.example.data.model.WorkoutRoutine

enum class FitnessGoal(
    val id: String,
    val label: String,
    val subtitle: String,
    val targetCalories: Int,
    val minCalories: Int,
    val maxCalories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatsG: Int
) {
    CUT(
        id = "CUT",
        label = "Cut / Fat Loss",
        subtitle = "High-protein caloric deficit to retain lean strength",
        targetCalories = 2050,
        minCalories = 1950,
        maxCalories = 2150,
        proteinG = 195,
        carbsG = 180,
        fatsG = 60
    ),
    MAINTAIN(
        id = "MAINTAIN",
        label = "Maintain / Recomp",
        subtitle = "Balanced fueling for steady progressive overload",
        targetCalories = 2550,
        minCalories = 2500,
        maxCalories = 2600,
        proteinG = 190,
        carbsG = 250,
        fatsG = 80
    ),
    BULK(
        id = "BULK",
        label = "Lean Bulk / Muscle",
        subtitle = "Controlled surplus to maximize hypertrophy & PRs",
        targetCalories = 2950,
        minCalories = 2850,
        maxCalories = 3050,
        proteinG = 200,
        carbsG = 350,
        fatsG = 85
    );

    companion object {
        fun fromString(value: String?): FitnessGoal {
            return entries.firstOrNull { it.id.equals(value?.trim(), ignoreCase = true) } ?: MAINTAIN
        }
    }
}

enum class TrainingSplit(
    val id: String,
    val label: String,
    val subtitle: String,
    val scheduleSummary: String
) {
    PPL(
        id = "PPL",
        label = "Push / Pull / Legs",
        subtitle = "Targeted push, pull, and leg hypertrophy rotation",
        scheduleSummary = "Mon, Tue, Wed, Thu, Fri, Sat"
    ),
    UPPER_LOWER(
        id = "UPPER_LOWER",
        label = "Upper / Lower",
        subtitle = "4-day compound upper & lower strength split",
        scheduleSummary = "Mon, Tue, Thu, Fri"
    ),
    FULL_BODY(
        id = "FULL_BODY",
        label = "Full Body",
        subtitle = "3-day high-frequency total-body compound sessions",
        scheduleSummary = "Mon, Wed, Fri"
    );

    companion object {
        fun fromString(value: String?): TrainingSplit {
            return entries.firstOrNull { it.id.equals(value?.trim(), ignoreCase = true) } ?: PPL
        }
    }
}

object GoalOnboardingHelper {

    fun buildStarterRoutines(
        userId: String,
        split: TrainingSplit,
        defaultRestSeconds: Int = 90
    ): List<WorkoutRoutine> {
        val rest = defaultRestSeconds.coerceIn(30, 300)
        return when (split) {
            TrainingSplit.PPL -> listOf(
                WorkoutRoutine(
                    id = "routine_push_a",
                    userId = userId,
                    name = "Push Day A",
                    description = "Chest, Shoulders & Triceps compound focus",
                    targetMuscle = "Push",
                    exerciseIds = listOf("bench_press", "incline_dumbbell_press", "overhead_press", "tricep_pushdown"),
                    defaultSets = 3,
                    restSeconds = rest,
                    scheduledDays = listOf(1, 4) // Mon, Thu
                ),
                WorkoutRoutine(
                    id = "routine_pull_b",
                    userId = userId,
                    name = "Pull Day B",
                    description = "Deadlift, Back width & Biceps focus",
                    targetMuscle = "Pull",
                    exerciseIds = listOf("barbell_deadlift", "cable_row", "bicep_curl"),
                    defaultSets = 3,
                    restSeconds = rest,
                    scheduledDays = listOf(2, 5) // Tue, Fri
                ),
                WorkoutRoutine(
                    id = "routine_leg_c",
                    userId = userId,
                    name = "Leg Day C",
                    description = "Squats, lower power & core stability",
                    targetMuscle = "Legs",
                    exerciseIds = listOf("barbell_squat", "leg_raises", "plank"),
                    defaultSets = 3,
                    restSeconds = rest,
                    scheduledDays = listOf(3, 6) // Wed, Sat
                )
            )
            TrainingSplit.UPPER_LOWER -> listOf(
                WorkoutRoutine(
                    id = "routine_upper_a",
                    userId = userId,
                    name = "Upper Strength A",
                    description = "Bench, Row, Overhead Press & Arms",
                    targetMuscle = "Upper",
                    exerciseIds = listOf("bench_press", "cable_row", "overhead_press", "bicep_curl", "tricep_pushdown"),
                    defaultSets = 3,
                    restSeconds = rest,
                    scheduledDays = listOf(1, 4) // Mon, Thu
                ),
                WorkoutRoutine(
                    id = "routine_lower_b",
                    userId = userId,
                    name = "Lower Power B",
                    description = "Squats, Deadlift & Core brace work",
                    targetMuscle = "Lower",
                    exerciseIds = listOf("barbell_squat", "barbell_deadlift", "weighted_crunches", "plank"),
                    defaultSets = 3,
                    restSeconds = rest,
                    scheduledDays = listOf(2, 5) // Tue, Fri
                )
            )
            TrainingSplit.FULL_BODY -> listOf(
                WorkoutRoutine(
                    id = "routine_full_body_a",
                    userId = userId,
                    name = "Full Body A (Squat & Push)",
                    description = "Back Squat, Bench Press, Cable Row & Plank",
                    targetMuscle = "Full Body",
                    exerciseIds = listOf("barbell_squat", "bench_press", "cable_row", "plank"),
                    defaultSets = 3,
                    restSeconds = rest,
                    scheduledDays = listOf(1) // Mon
                ),
                WorkoutRoutine(
                    id = "routine_full_body_b",
                    userId = userId,
                    name = "Full Body B (Hinge & Overhead)",
                    description = "Deadlift, Overhead Press, Incline DB & Core",
                    targetMuscle = "Full Body",
                    exerciseIds = listOf("barbell_deadlift", "overhead_press", "incline_dumbbell_press", "leg_raises"),
                    defaultSets = 3,
                    restSeconds = rest,
                    scheduledDays = listOf(3) // Wed
                ),
                WorkoutRoutine(
                    id = "routine_full_body_c",
                    userId = userId,
                    name = "Full Body C (Hypertrophy)",
                    description = "Bench, Squat, Biceps, Triceps & Weighted Core",
                    targetMuscle = "Full Body",
                    exerciseIds = listOf("bench_press", "barbell_squat", "bicep_curl", "tricep_pushdown", "weighted_crunches"),
                    defaultSets = 3,
                    restSeconds = rest,
                    scheduledDays = listOf(5) // Fri
                )
            )
        }
    }
}
