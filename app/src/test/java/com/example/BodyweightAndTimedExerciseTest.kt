package com.example

import com.example.data.garmin.GarminFitExporter
import com.example.data.model.ExerciseRecord
import com.example.data.model.ExerciseSet
import com.example.data.model.ExerciseTrackingType
import com.example.data.model.UserProfile
import com.example.data.model.WorkoutExerciseLog
import com.example.data.model.WorkoutSession
import com.example.data.share.WorkoutShareCardGenerator
import com.example.data.sheets.SheetsPayloadBuilder
import com.garmin.fit.Decode
import com.garmin.fit.ExerciseCategory
import com.garmin.fit.MesgBroadcaster
import com.garmin.fit.SetMesg
import com.garmin.fit.SetType
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BodyweightAndTimedExerciseTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `exercise set preserves durationSeconds through map serialization and handles legacy maps`() {
        val plankSet = ExerciseSet(
            setNumber = 1,
            weightLbs = 0.0,
            reps = 1,
            completed = true,
            durationSeconds = 60
        )
        val roundTripped = ExerciseSet.fromMap(plankSet.toMap())
        assertEquals(60, roundTripped.durationSeconds)
        assertEquals(0.0, roundTripped.weightLbs, 0.001)

        val legacyMap = mapOf<String, Any?>(
            "setNumber" to 2,
            "weightLbs" to 135.0,
            "reps" to 8,
            "completed" to true
        )
        val legacyParsed = ExerciseSet.fromMap(legacyMap)
        assertEquals(0, legacyParsed.durationSeconds)
        assertEquals(135.0, legacyParsed.weightLbs, 0.001)
    }

    @Test
    fun `exercise record formats timed hold bodyweight and weighted labels cleanly`() {
        val plank = ExerciseRecord(
            id = "plank",
            name = "Plank",
            category = "Core",
            trackingType = ExerciseTrackingType.TIMED_HOLD.id,
            startingWeightLbs = 0.0,
            currentMaxWeightLbs = 0.0,
            defaultSets = 3,
            defaultReps = 1,
            defaultDurationSeconds = 60,
            startingDurationSeconds = 60,
            currentBestDurationSeconds = 60
        )
        assertEquals("60s hold", plank.formatPrimaryMetricLabel())
        assertEquals("3×60s · BW", plank.formatCompactPrescription())

        val legRaisesBW = ExerciseRecord(
            id = "leg_raises",
            name = "Leg Raises",
            category = "Core",
            trackingType = ExerciseTrackingType.BODYWEIGHT_REPS.id,
            startingWeightLbs = 0.0,
            currentMaxWeightLbs = 0.0,
            defaultSets = 3,
            defaultReps = 12,
            bestReps = 12
        )
        assertEquals("BW (12 reps)", legRaisesBW.formatPrimaryMetricLabel())
        assertEquals("3×12 · BW", legRaisesBW.formatCompactPrescription())

        val legRaisesWeightedLater = legRaisesBW.copy(currentMaxWeightLbs = 10.0)
        assertEquals("BW + 10 lbs", legRaisesWeightedLater.formatPrimaryMetricLabel())
        assertEquals("3×12 · BW + 10 lbs", legRaisesWeightedLater.formatCompactPrescription())

        val weightedCrunches = ExerciseRecord(
            id = "weighted_crunches",
            name = "Weighted Crunches",
            category = "Core",
            trackingType = ExerciseTrackingType.WEIGHTED.id,
            startingWeightLbs = 25.0,
            currentMaxWeightLbs = 25.0,
            defaultSets = 3,
            defaultReps = 15
        )
        assertEquals("25 lbs", weightedCrunches.formatPrimaryMetricLabel())
        assertEquals("3×15 · 25 lbs", weightedCrunches.formatCompactPrescription())
    }

    @Test
    fun `share caption and sheets export format plank leg raises and weighted crunches accurately`() {
        val workout = WorkoutSession(
            id = "core_session_1",
            name = "Core Finisher",
            totalVolumeLbs = 1125.0,
            durationMinutes = 25,
            exerciseCount = 3,
            completedSetsCount = 5,
            dateString = "Nov 16, 2026"
        )
        val plankLog = WorkoutExerciseLog(
            id = "log_plank",
            workoutId = "core_session_1",
            exerciseId = "plank",
            exerciseName = "Plank",
            category = "Core",
            trackingType = ExerciseTrackingType.TIMED_HOLD.id,
            sets = listOf(
                ExerciseSet(1, 0.0, 1, true, 60).toMap(),
                ExerciseSet(2, 0.0, 1, true, 60).toMap()
            ),
            maxWeightLbs = 0.0,
            bestDurationSeconds = 60,
            totalDurationSeconds = 120,
            totalVolumeLbs = 0.0
        )
        val legRaiseLog = WorkoutExerciseLog(
            id = "log_leg_raises",
            workoutId = "core_session_1",
            exerciseId = "leg_raises",
            exerciseName = "Leg Raises",
            category = "Core",
            trackingType = ExerciseTrackingType.BODYWEIGHT_REPS.id,
            sets = listOf(
                ExerciseSet(1, 0.0, 12, true).toMap(),
                ExerciseSet(2, 10.0, 12, true).toMap()
            ),
            maxWeightLbs = 10.0,
            totalReps = 24,
            totalVolumeLbs = 120.0
        )
        val crunchLog = WorkoutExerciseLog(
            id = "log_crunch",
            workoutId = "core_session_1",
            exerciseId = "weighted_crunches",
            exerciseName = "Weighted Crunches",
            category = "Core",
            trackingType = ExerciseTrackingType.WEIGHTED.id,
            sets = listOf(ExerciseSet(1, 25.0, 15, true).toMap()),
            maxWeightLbs = 25.0,
            totalReps = 15,
            totalVolumeLbs = 375.0
        )

        val logs = listOf(plankLog, legRaiseLog, crunchLog)
        val caption = WorkoutShareCardGenerator.buildShareCaption(workout, logs, UserProfile(displayName = "Alex"))
        assertTrue(caption.contains("Plank: 2 sets × 60s (BW)"))
        assertTrue(caption.contains("Leg Raises: BW + 10 lbs × 12 reps"))
        assertTrue(caption.contains("Weighted Crunches: 25 lbs × 15 reps"))

        val sheetData = SheetsPayloadBuilder.buildExportData(
            profile = UserProfile(displayName = "Alex"),
            workouts = listOf(workout),
            logs = logs,
            exercises = emptyList(),
            todayNutrition = null,
            syncedAtLabel = "Nov 16, 2026"
        )
        assertTrue(sheetData.batchUpdateValuesJson.contains("60s, 60s"))
        assertTrue(sheetData.batchUpdateValuesJson.contains("BW×12, BW+10×12"))
        assertTrue(sheetData.batchUpdateValuesJson.contains("25x15"))
    }

    @Test
    fun `garmin fit export encodes 60s plank duration bodyweight leg raises and weighted crunches`() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 16, 8, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val workout = WorkoutSession(
            id = "core_fit_1",
            userId = "user_core",
            name = "Core Circuit",
            totalVolumeLbs = 375.0,
            durationMinutes = 20,
            restSeconds = 45,
            dateString = "Nov 16, 2026",
            createdAt = Timestamp(cal.time)
        )
        val logs = listOf(
            WorkoutExerciseLog(
                id = "l_plank",
                workoutId = "core_fit_1",
                exerciseId = "plank",
                exerciseName = "Plank",
                category = "Core",
                trackingType = ExerciseTrackingType.TIMED_HOLD.id,
                sets = listOf(ExerciseSet(1, 0.0, 1, true, 60).toMap()),
                bestDurationSeconds = 60
            ),
            WorkoutExerciseLog(
                id = "l_lr",
                workoutId = "core_fit_1",
                exerciseId = "leg_raises",
                exerciseName = "Leg Raises",
                category = "Core",
                trackingType = ExerciseTrackingType.BODYWEIGHT_REPS.id,
                sets = listOf(ExerciseSet(1, 0.0, 12, true).toMap()),
                totalReps = 12
            ),
            WorkoutExerciseLog(
                id = "l_wc",
                workoutId = "core_fit_1",
                exerciseId = "weighted_crunches",
                exerciseName = "Weighted Crunches",
                category = "Core",
                trackingType = ExerciseTrackingType.WEIGHTED.id,
                sets = listOf(ExerciseSet(1, 25.0, 15, true).toMap()),
                maxWeightLbs = 25.0,
                totalReps = 15
            )
        )

        val fitFile = tempFolder.newFile("core_circuit.fit")
        GarminFitExporter.writeWorkoutFitFile(fitFile, "user_core", workout, logs)
        val bytes = fitFile.readBytes()
        assertTrue(Decode().checkFileIntegrity(ByteArrayInputStream(bytes)))

        val activeSets = mutableListOf<SetMesg>()
        val broadcaster = MesgBroadcaster()
        broadcaster.addListener { mesg: SetMesg ->
            if (mesg.setType == SetType.ACTIVE) activeSets.add(mesg)
        }
        assertTrue(Decode().read(ByteArrayInputStream(bytes), broadcaster))

        assertEquals(3, activeSets.size)
        // Plank set: 60s duration, PLANK category, 0 kg weight
        assertEquals(60.0f, activeSets[0].duration!!, 0.1f)
        assertEquals(ExerciseCategory.PLANK, activeSets[0].getCategory(0)!!.toInt())
        assertEquals(0.0f, activeSets[0].weight!!, 0.01f)

        // Leg Raises set: 12 reps, CORE category, 0 kg weight
        assertEquals(12, activeSets[1].repetitions!!.toInt())
        assertEquals(ExerciseCategory.CORE, activeSets[1].getCategory(0)!!.toInt())
        assertEquals(0.0f, activeSets[1].weight!!, 0.01f)

        // Weighted Crunches set: 15 reps, CRUNCH category, ~11.34 kg weight
        assertEquals(15, activeSets[2].repetitions!!.toInt())
        assertEquals(ExerciseCategory.CRUNCH, activeSets[2].getCategory(0)!!.toInt())
        assertEquals(25.0f * 0.45359237f, activeSets[2].weight!!, 0.1f)
    }
}
