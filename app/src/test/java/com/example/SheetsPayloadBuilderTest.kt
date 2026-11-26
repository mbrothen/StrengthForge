package com.example

import com.example.data.model.DailyNutrition
import com.example.data.model.ExerciseRecord
import com.example.data.model.UserProfile
import com.example.data.model.WorkoutExerciseLog
import com.example.data.model.WorkoutSession
import com.example.data.sheets.SheetTabIds
import com.example.data.sheets.SheetsPayloadBuilder
import com.google.firebase.Timestamp
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SheetsPayloadBuilderTest {

    @Test
    fun `create spreadsheet json defines four expected tabs`() {
        val raw = SheetsPayloadBuilder.buildCreateSpreadsheetJson("StrengthForge Test")
        val json = JSONObject(raw)
        assertEquals("StrengthForge Test", json.getJSONObject("properties").getString("title"))

        val sheets = json.getJSONArray("sheets")
        assertEquals(4, sheets.length())
        val titles = (0 until sheets.length()).map {
            sheets.getJSONObject(it).getJSONObject("properties").getString("title")
        }
        assertEquals(SheetsPayloadBuilder.EXPECTED_TABS, titles)
    }

    @Test
    fun `export data sorts workouts chronologically and builds 4 embedded charts`() {
        val olderDate = Timestamp(Date(1_700_000_000_000L))
        val newerDate = Timestamp(Date(1_700_172_800_000L))

        val workouts = listOf(
            WorkoutSession(
                id = "w2",
                name = "Push Day B",
                totalVolumeLbs = 2400.0,
                durationMinutes = 50,
                exerciseCount = 2,
                completedSetsCount = 6,
                dateString = "Nov 16, 2023",
                createdAt = newerDate
            ),
            WorkoutSession(
                id = "w1",
                name = "Push Day A",
                totalVolumeLbs = 1800.0,
                durationMinutes = 40,
                exerciseCount = 2,
                completedSetsCount = 6,
                dateString = "Nov 14, 2023",
                createdAt = olderDate
            )
        )

        val logs = listOf(
            WorkoutExerciseLog(
                id = "l2",
                workoutId = "w2",
                exerciseId = "bench_press",
                exerciseName = "Barbell Bench Press",
                category = "Chest",
                sets = listOf(mapOf("setNumber" to 1, "weightLbs" to 150.0, "reps" to 6, "completed" to true)),
                maxWeightLbs = 150.0,
                totalVolumeLbs = 900.0,
                estimatedOneRepMaxLbs = 180.0,
                dateString = "Nov 16, 2023",
                date = newerDate
            ),
            WorkoutExerciseLog(
                id = "l1",
                workoutId = "w1",
                exerciseId = "bench_press",
                exerciseName = "Barbell Bench Press",
                category = "Chest",
                sets = listOf(mapOf("setNumber" to 1, "weightLbs" to 135.0, "reps" to 8, "completed" to true)),
                maxWeightLbs = 135.0,
                totalVolumeLbs = 1080.0,
                estimatedOneRepMaxLbs = 171.0,
                dateString = "Nov 14, 2023",
                date = olderDate
            )
        )

        val exercises = listOf(
            ExerciseRecord(
                id = "bench_press",
                name = "Barbell Bench Press",
                category = "Chest",
                startingWeightLbs = 135.0,
                currentMaxWeightLbs = 150.0,
                personalRecord1RMLbs = 180.0,
                percentIncrease = 11.1,
                historyCount = 2,
                isPrimary = true
            )
        )

        val exportData = SheetsPayloadBuilder.buildExportData(
            profile = UserProfile(displayName = "Alex", totalWorkoutsLogged = 2, totalLifetimeVolumeLbs = 4200.0),
            workouts = workouts,
            logs = logs,
            exercises = exercises,
            todayNutrition = DailyNutrition(calories = 2500, proteinG = 190, carbsG = 250, fatsG = 80),
            syncedAtLabel = "Nov 15, 2026 7:15 AM"
        )

        assertEquals(2, exportData.sessionRowCount)
        assertEquals(2, exportData.progressionRowCount)
        assertEquals(1, exportData.prRowCount)
        assertEquals("Barbell Bench Press", exportData.featuredExerciseName)

        // Verify Workout Sessions tab sorted oldest first
        val valuesJson = JSONObject(exportData.batchUpdateValuesJson)
        assertEquals("USER_ENTERED", valuesJson.getString("valueInputOption"))
        val dataArr = valuesJson.getJSONArray("data")
        val sessionsRange = dataArr.getJSONObject(1)
        val sessionsRows = sessionsRange.getJSONArray("values")
        assertEquals("Push Day A", sessionsRows.getJSONArray(1).getString(1))
        assertEquals("Push Day B", sessionsRows.getJSONArray(2).getString(1))
        assertEquals(1800.0, sessionsRows.getJSONArray(1).getDouble(5), 0.01)

        // Verify batchUpdate formatting + 4 embedded charts + deletion of old chart IDs
        val batchJson = JSONObject(
            SheetsPayloadBuilder.buildFormattingAndChartsBatchUpdateJson(
                tabIds = SheetTabIds(0, 1, 2, 3),
                exportData = exportData,
                existingDashboardChartIds = listOf(999)
            )
        )
        val requests = batchJson.getJSONArray("requests")
        var deleteCount = 0
        val chartTypes = mutableListOf<String>()
        for (i in 0 until requests.length()) {
            val req = requests.getJSONObject(i)
            if (req.has("deleteEmbeddedObject")) deleteCount++
            if (req.has("addChart")) {
                val chart = req.getJSONObject("addChart").getJSONObject("chart")
                val anchorCol = chart.getJSONObject("position")
                    .getJSONObject("overlayPosition")
                    .getJSONObject("anchorCell")
                    .getInt("columnIndex")
                assertEquals(4, anchorCol)
                val spec = chart.getJSONObject("spec")
                if (spec.has("basicChart")) {
                    val basicChart = spec.getJSONObject("basicChart")
                    val type = basicChart.getString("chartType")
                    chartTypes.add(type)
                    if (type == "COLUMN") {
                        assertTrue(
                            "COLUMN chart must not specify interpolateNulls",
                            !basicChart.has("interpolateNulls")
                        )
                    } else if (type == "LINE" || type == "AREA") {
                        assertTrue(
                            "$type chart should specify interpolateNulls",
                            basicChart.optBoolean("interpolateNulls", false)
                        )
                    }
                } else if (spec.has("pieChart")) {
                    chartTypes.add("PIE")
                }
            }
        }
        assertEquals(1, deleteCount)
        assertTrue(chartTypes.containsAll(listOf("AREA", "LINE", "COLUMN", "PIE")))
    }

    @Test
    fun `exercise prs tab excludes exercises that have not been completed in logs`() {
        val logTime = Timestamp(Date(1_700_000_000_000L))
        val workouts = listOf(
            WorkoutSession(
                id = "w1",
                name = "Push Day A",
                totalVolumeLbs = 1080.0,
                durationMinutes = 40,
                exerciseCount = 1,
                completedSetsCount = 3,
                dateString = "Nov 14, 2023",
                createdAt = logTime
            )
        )
        val logs = listOf(
            WorkoutExerciseLog(
                id = "l1",
                workoutId = "w1",
                exerciseId = "bench_press",
                exerciseName = "Barbell Bench Press",
                category = "Chest",
                sets = listOf(mapOf("setNumber" to 1, "weightLbs" to 135.0, "reps" to 8, "completed" to true)),
                maxWeightLbs = 135.0,
                totalVolumeLbs = 1080.0,
                estimatedOneRepMaxLbs = 171.0,
                dateString = "Nov 14, 2023",
                date = logTime
            )
        )
        val exercises = listOf(
            ExerciseRecord(
                id = "bench_press",
                name = "Barbell Bench Press",
                category = "Chest",
                startingWeightLbs = 135.0,
                currentMaxWeightLbs = 150.0,
                personalRecord1RMLbs = 180.0,
                percentIncrease = 11.1,
                historyCount = 1,
                isPrimary = true
            ),
            ExerciseRecord(
                id = "barbell_squat",
                name = "Barbell Back Squat",
                category = "Legs",
                startingWeightLbs = 185.0,
                currentMaxWeightLbs = 215.0,
                personalRecord1RMLbs = 245.0,
                percentIncrease = 16.2,
                historyCount = 3,
                isPrimary = true
            ),
            ExerciseRecord(
                id = "plank",
                name = "Plank",
                category = "Core",
                trackingType = "TIMED_HOLD",
                startingWeightLbs = 0.0,
                currentMaxWeightLbs = 0.0,
                personalRecord1RMLbs = 0.0,
                historyCount = 0
            )
        )

        val exportData = SheetsPayloadBuilder.buildExportData(
            profile = UserProfile(displayName = "Alex"),
            workouts = workouts,
            logs = logs,
            exercises = exercises,
            todayNutrition = null,
            syncedAtLabel = "Nov 17, 2026"
        )

        assertEquals(1, exportData.prRowCount)
        val dataArr = JSONObject(exportData.batchUpdateValuesJson).getJSONArray("data")
        val prsValues = dataArr.getJSONObject(3).getJSONArray("values")
        assertEquals(2, prsValues.length()) // 1 header + 1 completed exercise (bench_press)
        val benchPrRow = prsValues.getJSONArray(1)
        assertEquals("Barbell Bench Press", benchPrRow.getString(0))
        // Must reflect actual performed log (135 lbs, 171.0 1RM, 0% gain), NOT Exercise Settings (150 / 180 / 11.1%)
        assertEquals(135.0, benchPrRow.getDouble(2), 0.01)
        assertEquals(135.0, benchPrRow.getDouble(3), 0.01)
        assertEquals(171.0, benchPrRow.getDouble(4), 0.01)
        assertEquals(0.0, benchPrRow.getDouble(5), 0.01)
        assertEquals(1, benchPrRow.getInt(6))
        val serializedPrs = prsValues.toString()
        assertTrue(!serializedPrs.contains("Barbell Back Squat"))
        assertTrue(!serializedPrs.contains("Plank"))
    }

    @Test
    fun `featured bench chart excludes other exercises containing Bench in name`() {
        val t1 = Timestamp(Date(1_700_000_000_000L))
        val t2 = Timestamp(Date(1_700_100_000_000L))
        val logs = listOf(
            WorkoutExerciseLog(
                id = "l1",
                workoutId = "w1",
                exerciseId = "bench_press",
                exerciseName = "Barbell Bench Press",
                category = "Chest",
                sets = listOf(mapOf("setNumber" to 1, "weightLbs" to 95.0, "reps" to 8, "completed" to true)),
                maxWeightLbs = 95.0,
                totalVolumeLbs = 760.0,
                estimatedOneRepMaxLbs = 120.3,
                dateString = "Nov 14, 2023",
                date = t1
            ),
            WorkoutExerciseLog(
                id = "l2",
                workoutId = "w1",
                exerciseId = "db_bench",
                exerciseName = "Dumbbell Bench Press",
                category = "Chest",
                sets = listOf(mapOf("setNumber" to 1, "weightLbs" to 15.0, "reps" to 12, "completed" to true)),
                maxWeightLbs = 15.0,
                totalVolumeLbs = 180.0,
                estimatedOneRepMaxLbs = 21.0,
                dateString = "Nov 15, 2023",
                date = t2
            )
        )
        val exercises = listOf(
            ExerciseRecord(id = "bench_press", name = "Barbell Bench Press", category = "Chest", historyCount = 1),
            ExerciseRecord(id = "db_bench", name = "Dumbbell Bench Press", category = "Chest", historyCount = 1)
        )

        val exportData = SheetsPayloadBuilder.buildExportData(
            profile = UserProfile(displayName = "Alex"),
            workouts = emptyList(),
            logs = logs,
            exercises = exercises,
            todayNutrition = null,
            syncedAtLabel = "Nov 19, 2026"
        )

        val dashboardRows = JSONObject(exportData.batchUpdateValuesJson)
            .getJSONArray("data")
            .getJSONObject(0)
            .getJSONArray("values")
        val serializedDashboard = dashboardRows.toString()
        assertTrue(serializedDashboard.contains("95"))
        assertTrue(!serializedDashboard.contains("15.0"))
        assertTrue(!serializedDashboard.contains(",15,"))
    }
}
