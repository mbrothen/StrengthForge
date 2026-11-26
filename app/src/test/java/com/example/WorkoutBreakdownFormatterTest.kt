package com.example

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.data.model.ExerciseRecord
import com.example.data.model.ExerciseSet
import com.example.data.model.ExerciseTrackingType
import com.example.data.model.WorkoutExerciseLog
import com.example.data.model.WorkoutSession
import com.example.ui.components.WorkoutBreakdownFormatter
import com.example.ui.history.ExpandableWorkoutHistoryCard
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WorkoutBreakdownFormatterTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `formatter builds weighted bodyweight and timed hold set breakdowns`() {
        val weightedLog = WorkoutExerciseLog(
            id = "log_bench",
            workoutId = "w1",
            exerciseId = "bench_press",
            exerciseName = "Barbell Bench Press",
            category = "Chest",
            trackingType = ExerciseTrackingType.WEIGHTED.id,
            sets = listOf(
                ExerciseSet(1, 140.0, 10, true).toMap(),
                ExerciseSet(2, 150.0, 8, true).toMap()
            ),
            maxWeightLbs = 150.0
        )
        val plankLog = WorkoutExerciseLog(
            id = "log_plank",
            workoutId = "w1",
            exerciseId = "plank",
            exerciseName = "Plank",
            category = "Core",
            trackingType = ExerciseTrackingType.TIMED_HOLD.id,
            sets = listOf(ExerciseSet(1, 0.0, 1, true, 60).toMap()),
            bestDurationSeconds = 60
        )
        val legRaiseLog = WorkoutExerciseLog(
            id = "log_lr",
            workoutId = "w1",
            exerciseId = "leg_raises",
            exerciseName = "Leg Raises",
            category = "Core",
            // Simulate older log without explicit trackingType, relying on catalog match
            trackingType = "",
            sets = listOf(
                ExerciseSet(1, 0.0, 12, true).toMap(),
                ExerciseSet(2, 10.0, 10, true).toMap()
            ),
            maxWeightLbs = 10.0
        )
        val catalog = listOf(
            ExerciseRecord(
                id = "leg_raises",
                name = "Leg Raises",
                category = "Core",
                trackingType = ExerciseTrackingType.BODYWEIGHT_REPS.id
            )
        )

        val weightedBreakdown = WorkoutBreakdownFormatter.buildExerciseBreakdown(weightedLog, catalog)
        assertEquals("2 sets · 150 lbs × 8 reps", weightedBreakdown.summaryLine)
        assertEquals("140 lbs", weightedBreakdown.sets[0].primaryLabel)
        assertEquals("10 reps", weightedBreakdown.sets[0].secondaryLabel)

        val plankBreakdown = WorkoutBreakdownFormatter.buildExerciseBreakdown(plankLog, catalog)
        assertEquals("1 sets · Best 60s · BW", plankBreakdown.summaryLine)
        assertEquals("Bodyweight", plankBreakdown.sets[0].primaryLabel)
        assertEquals("60s hold", plankBreakdown.sets[0].secondaryLabel)

        val lrBreakdown = WorkoutBreakdownFormatter.buildExerciseBreakdown(legRaiseLog, catalog)
        assertTrue(lrBreakdown.summaryLine.contains("BW + 10 lbs"))
        assertEquals("Bodyweight", lrBreakdown.sets[0].primaryLabel)
        assertEquals("BW + 10 lbs", lrBreakdown.sets[1].primaryLabel)
        assertEquals("10 reps", lrBreakdown.sets[1].secondaryLabel)
    }

    @Test
    fun `clicking workout history card reveals exercises and expands further to show weight and reps in same box`() {
        val workout = WorkoutSession(
            id = "w_expand_1",
            name = "Push Day A",
            totalVolumeLbs = 2600.0,
            durationMinutes = 45,
            exerciseCount = 2,
            completedSetsCount = 4,
            dateString = "Nov 16, 2026"
        )
        val logs = listOf(
            WorkoutExerciseLog(
                id = "l_bench",
                workoutId = "w_expand_1",
                exerciseId = "bench_press",
                exerciseName = "Barbell Bench Press",
                category = "Chest",
                sets = listOf(
                    ExerciseSet(1, 145.0, 8, true).toMap(),
                    ExerciseSet(2, 155.0, 6, true).toMap()
                ),
                maxWeightLbs = 155.0
            ),
            WorkoutExerciseLog(
                id = "l_plank",
                workoutId = "w_expand_1",
                exerciseId = "plank",
                exerciseName = "Plank",
                category = "Core",
                trackingType = ExerciseTrackingType.TIMED_HOLD.id,
                sets = listOf(ExerciseSet(1, 0.0, 1, true, 60).toMap()),
                bestDurationSeconds = 60
            )
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    ExpandableWorkoutHistoryCard(
                        workout = workout,
                        workoutLogs = logs
                    )
                }
            }
        }

        // Step 1: Click workout card -> shows exercises done inside the card
        composeTestRule.onNodeWithTag("history_workout_card_w_expand_1").performClick()
        composeTestRule.onNodeWithText("Barbell Bench Press").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Plank").performScrollTo().assertIsDisplayed()

        // Step 2: Expand further via "Show Weight & Reps" -> reveals individual sets in the same box
        composeTestRule.onNodeWithTag("toggle_all_sets_button_w_expand_1").performScrollTo().performClick()
        composeTestRule.onNodeWithText("145 lbs").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("8 reps").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("155 lbs").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("6 reps").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("60s hold").performScrollTo().assertIsDisplayed()
    }
}
