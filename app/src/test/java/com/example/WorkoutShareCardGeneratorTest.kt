package com.example

import android.graphics.BitmapFactory
import com.example.data.analytics.DashboardMetrics
import com.example.data.analytics.DashboardTimeframe
import com.example.data.analytics.PerformedExercisePr
import com.example.data.analytics.WeightUnit
import com.example.data.analytics.WeightUnitHelper
import com.example.data.model.ExerciseRecord
import com.example.data.model.ExerciseSet
import com.example.data.model.ExerciseTrackingType
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

    @Test
    fun `summary and prs share generators build captions and valid 1080x1350 png cards`() {
        val kgBench = ExerciseRecord(
            id = "bench_press",
            name = "Barbell Bench Press",
            category = "Chest",
            isPrimary = true
        )
        val kgSquat = ExerciseRecord(
            id = "barbell_squat",
            name = "Barbell Back Squat",
            category = "Legs"
        )
        val prs = listOf(
            PerformedExercisePr(
                exercise = kgBench,
                trackingType = ExerciseTrackingType.WEIGHTED,
                startingWeightLbs = WeightUnitHelper.toLbs(80.0, WeightUnit.KG),
                maxWeightLbs = WeightUnitHelper.toLbs(100.0, WeightUnit.KG),
                best1RM = WeightUnitHelper.toLbs(116.7, WeightUnit.KG),
                startingDurationSeconds = 0,
                bestDurationSeconds = 0,
                bestReps = 5,
                percentIncrease = 25.0,
                sessionsCount = 6,
                totalSetsLogged = 18
            ),
            PerformedExercisePr(
                exercise = kgSquat,
                trackingType = ExerciseTrackingType.WEIGHTED,
                startingWeightLbs = WeightUnitHelper.toLbs(100.0, WeightUnit.KG),
                maxWeightLbs = WeightUnitHelper.toLbs(140.0, WeightUnit.KG),
                best1RM = WeightUnitHelper.toLbs(163.3, WeightUnit.KG),
                startingDurationSeconds = 0,
                bestDurationSeconds = 0,
                bestReps = 5,
                percentIncrease = 40.0,
                sessionsCount = 5,
                totalSetsLogged = 15
            )
        )
        val metrics = DashboardMetrics(
            totalVolumeLbs = WeightUnitHelper.toLbs(12500.0, WeightUnit.KG),
            sessionCount = 4,
            avgDurationMinutes = 52,
            completedSets = 48,
            gainPercent = 18.5,
            sparklinePoints = listOf(2800f, 3100f, 3200f, 3400f),
            adherenceLabel = "On track"
        )
        val profile = UserProfile(
            displayName = "Alex",
            currentWeeklyStreak = 5,
            weightUnit = WeightUnit.KG.id
        )

        val summaryCaption = WorkoutShareCardGenerator.buildSummaryShareCaption(
            timeframe = DashboardTimeframe.THIS_WEEK,
            metrics = metrics,
            performedPrs = prs,
            profile = profile
        )
        assertTrue(summaryCaption.contains("This Week Summary"))
        assertTrue(summaryCaption.contains("12,500 kg"))
        assertTrue(summaryCaption.contains("4 workouts"))
        assertTrue(summaryCaption.contains("Barbell Bench Press: 100 kg"))
        assertTrue(summaryCaption.contains("5-Week Consistency Streak"))

        val summaryFile = tempFolder.newFile("summary_card.png")
        WorkoutShareCardGenerator.writeSummarySharePngToFile(
            outputFile = summaryFile,
            timeframe = DashboardTimeframe.THIS_WEEK,
            metrics = metrics,
            performedPrs = prs,
            profile = profile
        )
        val summaryBmp = BitmapFactory.decodeFile(summaryFile.absolutePath)
        assertNotNull(summaryBmp)
        assertEquals(1080, summaryBmp.width)
        assertEquals(1350, summaryBmp.height)
        summaryBmp.recycle()

        val prsCaption = WorkoutShareCardGenerator.buildPrsShareCaption(
            performedPrs = prs,
            workoutsCount = 12,
            profile = profile
        )
        assertTrue(prsCaption.contains("Personal Records (PRs)"))
        assertTrue(prsCaption.contains("Barbell Bench Press: 100 kg (+25.0% gain)"))
        assertTrue(prsCaption.contains("Barbell Back Squat: 140 kg (+40.0% gain)"))

        val prsFile = tempFolder.newFile("prs_card.png")
        WorkoutShareCardGenerator.writePrsSharePngToFile(
            outputFile = prsFile,
            performedPrs = prs,
            workoutsCount = 12,
            profile = profile
        )
        val prsBmp = BitmapFactory.decodeFile(prsFile.absolutePath)
        assertNotNull(prsBmp)
        assertEquals(1080, prsBmp.width)
        assertEquals(1350, prsBmp.height)
        prsBmp.recycle()
    }
}
