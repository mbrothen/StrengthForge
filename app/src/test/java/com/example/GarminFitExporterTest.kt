package com.example

import com.example.data.garmin.GarminFitExporter
import com.example.data.model.ExerciseRecord
import com.example.data.model.ExerciseSet
import com.example.data.model.WorkoutExerciseLog
import com.example.data.model.WorkoutSession
import com.garmin.fit.Decode
import com.garmin.fit.ExerciseCategory
import com.garmin.fit.File as FitFileType
import com.garmin.fit.FileIdMesg
import com.garmin.fit.FitBaseUnit
import com.garmin.fit.MesgBroadcaster
import com.garmin.fit.SessionMesg
import com.garmin.fit.SetMesg
import com.garmin.fit.SetType
import com.garmin.fit.Sport
import com.garmin.fit.SubSport
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Calendar
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GarminFitExporterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private data class DecodedFitSummary(
        val fileId: FileIdMesg?,
        val session: SessionMesg?,
        val sets: List<SetMesg>
    )

    private fun decodeFitBytes(bytes: ByteArray): DecodedFitSummary {
        var fileId: FileIdMesg? = null
        var session: SessionMesg? = null
        val sets = mutableListOf<SetMesg>()

        val integrityOk = Decode().checkFileIntegrity(ByteArrayInputStream(bytes))
        assertTrue("FIT file integrity and CRC check must pass", integrityOk)

        val broadcaster = MesgBroadcaster()
        broadcaster.addListener { mesg: FileIdMesg -> fileId = mesg }
        broadcaster.addListener { mesg: SessionMesg -> session = mesg }
        broadcaster.addListener { mesg: SetMesg -> sets.add(mesg) }

        val readOk = Decode().read(ByteArrayInputStream(bytes), broadcaster)
        assertTrue("FIT decode must succeed", readOk)
        return DecodedFitSummary(fileId = fileId, session = session, sets = sets)
    }

    @Test
    fun `single workout fit export encodes valid strength activity with sets reps weight and deterministic ids`() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 10, 14, 30, 15)
            set(Calendar.MILLISECOND, 0)
        }
        val workout = WorkoutSession(
            id = "workout_push_001",
            userId = "user_abc",
            name = "Push Day A",
            totalVolumeLbs = 2400.0,
            durationMinutes = 45,
            exerciseCount = 1,
            completedSetsCount = 2,
            restSeconds = 90,
            dateString = "Nov 10, 2026",
            createdAt = Timestamp(cal.time)
        )
        val benchLog = WorkoutExerciseLog(
            id = "log_1",
            workoutId = "workout_push_001",
            userId = "user_abc",
            exerciseId = "bench_press",
            exerciseName = "Barbell Bench Press",
            category = "Chest",
            sets = listOf(
                ExerciseSet(1, 150.0, 8, true).toMap(),
                ExerciseSet(2, 155.0, 6, true).toMap()
            ),
            maxWeightLbs = 155.0,
            totalVolumeLbs = 2130.0
        )

        val fitFileA = tempFolder.newFile("push_a.fit")
        val fitFileB = tempFolder.newFile("push_b.fit")

        GarminFitExporter.writeWorkoutFitFile(fitFileA, "user_abc", workout, listOf(benchLog))
        GarminFitExporter.writeWorkoutFitFile(fitFileB, "user_abc", workout, listOf(benchLog))

        val decodedA = decodeFitBytes(fitFileA.readBytes())
        val decodedB = decodeFitBytes(fitFileB.readBytes())

        // FileId & Strength sport verification
        assertEquals(FitFileType.ACTIVITY, decodedA.fileId?.type)
        assertEquals(Sport.TRAINING, decodedA.session?.sport)
        assertEquals(SubSport.STRENGTH_TRAINING, decodedA.session?.subSport)

        // Deterministic FileId & start time across re-exports (so Garmin Connect ignores duplicates)
        assertEquals(decodedA.fileId?.serialNumber, decodedB.fileId?.serialNumber)
        assertEquals(decodedA.fileId?.timeCreated?.timestamp, decodedB.fileId?.timeCreated?.timestamp)
        assertEquals(decodedA.session?.startTime?.timestamp, decodedB.session?.startTime?.timestamp)

        // Active & rest sets verification (2 active sets + 1 rest interval = 3 SetMesgs)
        val activeSets = decodedA.sets.filter { it.setType == SetType.ACTIVE }
        val restSets = decodedA.sets.filter { it.setType == SetType.REST }
        assertEquals(2, activeSets.size)
        assertEquals(1, restSets.size)
        assertEquals(8, activeSets[0].repetitions)
        assertEquals(6, activeSets[1].repetitions)
        assertEquals(FitBaseUnit.POUND, activeSets[0].weightDisplayUnit)
        assertEquals(ExerciseCategory.BENCH_PRESS, activeSets[0].getCategory(0))
        // 150 lbs ≈ 68.04 kg
        assertEquals(150.0 * 0.45359237, activeSets[0].weight!!.toDouble(), 0.2)
    }

    @Test
    fun `two distinct workouts logged on the same past date receive distinct timestamps`() {
        val noonCal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 5, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val morningPush = WorkoutSession(
            id = "workout_alpha_1",
            name = "Morning Push",
            createdAt = Timestamp(noonCal.time)
        )
        val eveningPull = WorkoutSession(
            id = "workout_beta_2",
            name = "Evening Pull",
            createdAt = Timestamp(noonCal.time)
        )

        val date1 = GarminFitExporter.resolveDeterministicStartDate(morningPush)
        val date2 = GarminFitExporter.resolveDeterministicStartDate(eveningPull)

        assertNotEquals("Different workouts on the same day must not share identical timestamps", date1.time, date2.time)
        // Re-computing for the same workout remains deterministic
        assertEquals(date1.time, GarminFitExporter.resolveDeterministicStartDate(morningPush).time)
    }

    @Test
    fun `batch zip export handles all workouts and skips previously exported when requested`() {
        val w1 = WorkoutSession(
            id = "w1",
            userId = "u1",
            name = "Push Day",
            durationMinutes = 40,
            dateString = "Nov 1, 2026"
        )
        val w2 = WorkoutSession(
            id = "w2",
            userId = "u1",
            name = "Leg Day",
            durationMinutes = 50,
            dateString = "Nov 3, 2026"
        )
        val logs = listOf(
            WorkoutExerciseLog(
                id = "l1",
                workoutId = "w1",
                exerciseId = "bench_press",
                exerciseName = "Barbell Bench Press",
                category = "Chest",
                sets = listOf(ExerciseSet(1, 145.0, 8, true).toMap())
            ),
            WorkoutExerciseLog(
                id = "l2",
                workoutId = "w2",
                exerciseId = "barbell_squat",
                exerciseName = "Barbell Back Squat",
                category = "Legs",
                sets = listOf(ExerciseSet(1, 225.0, 5, true).toMap())
            )
        )

        val workDir = tempFolder.newFolder("batch_test")
        val allZip = File(workDir, "all.zip")
        val allResult = GarminFitExporter.exportWorkoutsBatchZip(
            outputZipFile = allZip,
            tempDir = File(workDir, "tmp1"),
            userId = "u1",
            workouts = listOf(w1, w2),
            logs = logs,
            exercises = emptyList<ExerciseRecord>(),
            onlyUnexported = false,
            alreadyExportedIds = setOf("w1")
        )

        assertEquals(listOf("w1", "w2"), allResult.exportedWorkoutIds)

        val zipEntriesAll = mutableListOf<ByteArray>()
        ZipInputStream(allZip.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                assertTrue(entry.name.endsWith(".fit"))
                zipEntriesAll.add(zis.readBytes())
                entry = zis.nextEntry
            }
        }
        assertEquals(2, zipEntriesAll.size)
        zipEntriesAll.forEach { fitBytes -> decodeFitBytes(fitBytes) }

        val newOnlyZip = File(workDir, "new_only.zip")
        val newOnlyResult = GarminFitExporter.exportWorkoutsBatchZip(
            outputZipFile = newOnlyZip,
            tempDir = File(workDir, "tmp2"),
            userId = "u1",
            workouts = listOf(w1, w2),
            logs = logs,
            exercises = emptyList(),
            onlyUnexported = true,
            alreadyExportedIds = setOf("w1")
        )
        assertEquals(listOf("w2"), newOnlyResult.exportedWorkoutIds)
        assertEquals(1, newOnlyResult.skippedAlreadyExportedCount)
    }
}
