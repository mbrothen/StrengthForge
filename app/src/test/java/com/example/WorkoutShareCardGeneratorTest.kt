package com.example

import android.graphics.BitmapFactory
import com.example.data.model.ExerciseSet
import com.example.data.model.UserProfile
import com.example.data.model.WorkoutExerciseLog
import com.example.data.model.WorkoutSession
import com.example.data.share.WorkoutShareCardGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WorkoutShareCardGeneratorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `caption includes workout title volume duration sets and top lifts`() {
        val workout = WorkoutSession(
            id = "w_share_1",
            name = "Push Day A - Bench PR",
            totalVolumeLbs = 4250.0,
            durationMinutes = 48,
            exerciseCount = 2,
            completedSetsCount = 6,
            notes = "155 lbs felt explosive!",
            dateString = "Nov 15, 2026"
        )
        val logs = listOf(
            WorkoutExerciseLog(
                id = "l1",
                workoutId = "w_share_1",
                exerciseName = "Barbell Bench Press",
                category = "Chest",
                sets = listOf(
                    ExerciseSet(1, 145.0, 8, true).toMap(),
                    ExerciseSet(2, 155.0, 6, true).toMap()
                ),
                maxWeightLbs = 155.0,
                totalVolumeLbs = 2090.0
            ),
            WorkoutExerciseLog(
                id = "l2",
                workoutId = "w_share_1",
                exerciseName = "Incline Dumbbell Press",
                category = "Chest",
                sets = listOf(ExerciseSet(1, 60.0, 10, true).toMap()),
                maxWeightLbs = 60.0,
                totalVolumeLbs = 1800.0
            )
        )
        val profile = UserProfile(displayName = "Alex", currentWeeklyStreak = 3)

        val caption = WorkoutShareCardGenerator.buildShareCaption(workout, logs, profile)
        assertTrue(caption.contains("Push Day A - Bench PR"))
        assertTrue(caption.contains("4,250 lbs"))
        assertTrue(caption.contains("48 min"))
        assertTrue(caption.contains("6 sets"))
        assertTrue(caption.contains("Barbell Bench Press: 155 lbs"))
        assertTrue(caption.contains("3-Week Consistency Streak"))
        assertTrue(caption.contains("#StrengthForge"))
    }

    @Test
    fun `renders valid 1080x1350 png workout stats graphic file`() {
        val workout = WorkoutSession(
            id = "w_share_2",
            name = "Leg Day C",
            totalVolumeLbs = 5100.0,
            durationMinutes = 52,
            exerciseCount = 1,
            completedSetsCount = 4,
            dateString = "Nov 14, 2026"
        )
        val logs = listOf(
            WorkoutExerciseLog(
                id = "l_sq",
                workoutId = "w_share_2",
                exerciseName = "Barbell Back Squat",
                category = "Legs",
                sets = listOf(ExerciseSet(1, 225.0, 5, true).toMap()),
                maxWeightLbs = 225.0,
                totalVolumeLbs = 4500.0
            )
        )
        val profile = UserProfile(displayName = "Strength Athlete", currentWeeklyStreak = 2)

        val outFile = tempFolder.newFile("share_card.png")
        WorkoutShareCardGenerator.writeSharePngToFile(outFile, workout, logs, profile)

        assertTrue(outFile.exists())
        assertTrue(outFile.length() > 1000L)

        val decoded = BitmapFactory.decodeFile(outFile.absolutePath)
        assertNotNull(decoded)
        assertEquals(1080, decoded.width)
        assertEquals(1350, decoded.height)
        decoded.recycle()
    }
}
