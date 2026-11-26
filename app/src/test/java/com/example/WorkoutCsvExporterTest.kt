package com.example

import com.example.data.export.WorkoutCsvExporter
import com.example.data.model.ExerciseRecord
import com.example.data.model.ExerciseSet
import com.example.data.model.ExerciseTrackingType
import com.example.data.model.WorkoutExerciseLog
import com.example.data.model.WorkoutSession
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Calendar

class WorkoutCsvExporterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `csv export writes header and all logged workout set data with rfc4180 escaping`() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 16, 9, 15, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val workouts = listOf(
            WorkoutSession(
                id = "w_csv_1",
                name = "Push & Core, Day A",
                totalVolumeLbs = 2400.0,
                durationMinutes = 50,
                restSeconds = 90,
                completedSetsCount = 3,
                exerciseCount = 2,
                notes = "Felt \"strong\", crisp lockouts",
                dateString = "Nov 16, 2026",
                createdAt = Timestamp(cal.time)
            ),
            WorkoutSession(
                id = "w_csv_empty",
                name = "Quick Mobility",
                totalVolumeLbs = 0.0,
                durationMinutes = 15,
                restSeconds = 60,
                completedSetsCount = 0,
                exerciseCount = 0,
                notes = "Stretching",
                dateString = "Nov 15, 2026"
            )
        )
        val logs = listOf(
            WorkoutExerciseLog(
                id = "log_bench",
                workoutId = "w_csv_1",
                exerciseId = "bench_press",
                exerciseName = "Barbell Bench Press",
                category = "Chest",
                trackingType = ExerciseTrackingType.WEIGHTED.id,
                sets = listOf(
                    ExerciseSet(1, 145.0, 8, true).toMap(),
                    ExerciseSet(2, 155.0, 6, true).toMap()
                ),
                maxWeightLbs = 155.0,
                estimatedOneRepMaxLbs = 186.0,
                totalVolumeLbs = 2090.0,
                dateString = "Nov 16, 2026",
                date = Timestamp(cal.time)
            ),
            WorkoutExerciseLog(
                id = "log_plank",
                workoutId = "w_csv_1",
                exerciseId = "plank",
                exerciseName = "Plank",
                category = "Core",
                trackingType = ExerciseTrackingType.TIMED_HOLD.id,
                sets = listOf(ExerciseSet(1, 0.0, 1, true, 60).toMap()),
                maxWeightLbs = 0.0,
                bestDurationSeconds = 60,
                totalVolumeLbs = 0.0,
                dateString = "Nov 16, 2026",
                date = Timestamp(cal.time)
            )
        )
        val catalog = listOf(
            ExerciseRecord(id = "plank", name = "Plank", category = "Core", trackingType = ExerciseTrackingType.TIMED_HOLD.id)
        )

        val outFile = tempFolder.newFile("workouts_export.csv")
        val result = WorkoutCsvExporter.writeWorkoutsCsvFile(
            outputFile = outFile,
            workouts = workouts,
            logs = logs,
            exercises = catalog
        )

        assertTrue(outFile.exists())
        assertEquals(2, result.workoutCount)
        assertEquals(4, result.rowCount) // 2 bench sets + 1 plank set + 1 summary row for Quick Mobility

        val content = outFile.readText(Charsets.UTF_8)
        val lines = content.trimEnd('\r', '\n').lines()
        assertEquals(5, lines.size) // 1 header + 4 rows

        assertTrue(lines[0].startsWith("Workout Date,Display Date,Workout ID,Workout Name"))
        assertTrue(content.contains("\"Push & Core, Day A\""))
        assertTrue(content.contains("\"Felt \"\"strong\"\", crisp lockouts\""))
        assertTrue(content.contains("Barbell Bench Press,Chest,WEIGHTED,1,145,8,0,true,1160"))
        assertTrue(content.contains("Barbell Bench Press,Chest,WEIGHTED,2,155,6,0,true,930"))
        assertTrue(content.contains("Plank,Core,TIMED_HOLD,1,0,1,60,true,0"))
        assertTrue(content.contains("Quick Mobility,15,60,0,0,Stretching"))
    }
}
