package com.example

import com.example.data.analytics.DashboardTimeframe
import com.example.data.analytics.ExerciseHistoricalBest
import com.example.data.analytics.PlateCalculator
import com.example.data.analytics.WorkoutAnalyticsHelper
import com.example.data.export.WorkoutCsvExporter
import com.example.data.model.DailyNutrition
import com.example.data.model.ExerciseRecord
import com.example.data.model.ExerciseSet
import com.example.data.model.ExerciseTrackingType
import com.example.data.model.MealEntry
import com.example.data.model.MealPreset
import com.example.data.model.UserProfile
import com.example.data.model.WorkoutExerciseLog
import com.example.data.model.WorkoutRoutine
import com.example.data.model.WorkoutSession
import com.example.data.repository.LoggedExerciseData
import com.example.data.repository.WorkoutDataRepository
import com.example.data.sheets.SheetsPayloadBuilder
import com.example.viewmodel.WorkoutViewModel
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private class AnalyticsFakeRepository : WorkoutDataRepository {
    val profileFlow = MutableStateFlow(UserProfile(id = "u1", userId = "u1"))
    val workoutsFlow = MutableStateFlow<List<WorkoutSession>>(emptyList())
    val exercisesFlow = MutableStateFlow<List<ExerciseRecord>>(emptyList())
    val routinesFlow = MutableStateFlow<List<WorkoutRoutine>>(emptyList())
    val logsFlow = MutableStateFlow<List<WorkoutExerciseLog>>(emptyList())
    val nutritionMapFlow = MutableStateFlow<Map<String, DailyNutrition>>(emptyMap())

    override fun observeUserProfile(userId: String): Flow<UserProfile> = profileFlow
    override fun observeWorkouts(userId: String): Flow<List<WorkoutSession>> = workoutsFlow
    override fun observeExerciseRecords(userId: String): Flow<List<ExerciseRecord>> = exercisesFlow
    override fun observeRoutines(userId: String): Flow<List<WorkoutRoutine>> = routinesFlow
    override fun observeLogs(userId: String): Flow<List<WorkoutExerciseLog>> = logsFlow

    override fun observeNutrition(userId: String, dateString: String): Flow<DailyNutrition?> {
        return nutritionMapFlow.map { it[dateString] }
    }

    override fun observeNutritionHistory(userId: String): Flow<List<DailyNutrition>> {
        return nutritionMapFlow.map { it.values.sortedByDescending { d -> d.dateString } }
    }

    override suspend fun saveExercise(userId: String, exercise: ExerciseRecord): String = exercise.id
    override suspend fun deleteExercise(userId: String, exerciseId: String) {}
    override suspend fun saveRoutine(userId: String, routine: WorkoutRoutine): String = routine.id
    override suspend fun deleteRoutine(userId: String, routineId: String) {}
    override suspend fun seedInitialDataIfNeeded(userId: String) {}
    override suspend fun saveWorkout(
        userId: String,
        workoutName: String,
        durationMinutes: Int,
        notes: String,
        exerciseEntries: List<LoggedExerciseData>,
        customDate: Date?,
        restSeconds: Int
    ): String = "w_new"

    override suspend fun updateWorkoutWithExercises(
        userId: String,
        workoutId: String,
        workoutName: String,
        durationMinutes: Int,
        notes: String,
        exerciseEntries: List<LoggedExerciseData>,
        customDate: Date?,
        restSeconds: Int
    ) {}

    override suspend fun updateWorkoutMetadata(
        userId: String,
        workout: WorkoutSession,
        newName: String,
        newDate: Date,
        newDurationMinutes: Int,
        newNotes: String,
        newTotalVolumeLbs: Double
    ) {}

    override suspend fun deleteWorkout(userId: String, workout: WorkoutSession) {}

    override suspend fun updateNutrition(
        userId: String,
        dateString: String,
        calories: Int,
        protein: Int,
        carbs: Int,
        fats: Int,
        meals: List<MealEntry>
    ) {
        val updated = DailyNutrition(
            id = dateString,
            userId = userId,
            dateString = dateString,
            calories = calories,
            proteinG = protein,
            carbsG = carbs,
            fatsG = fats,
            meals = meals.map { it.toMap() }
        )
        nutritionMapFlow.value = nutritionMapFlow.value + (dateString to updated)
    }

    override suspend fun saveMealPresets(userId: String, presets: List<MealPreset>) {
        profileFlow.value = profileFlow.value.copy(
            mealPresets = presets.map { it.toMap() }
        )
    }

    override suspend fun updateMacroTargets(
        userId: String,
        targetCalories: Int,
        targetMin: Int,
        targetMax: Int,
        proteinG: Int,
        carbsG: Int,
        fatsG: Int
    ) {
        profileFlow.value = profileFlow.value.copy(
            targetCalories = targetCalories,
            targetCaloriesMin = targetMin,
            targetCaloriesMax = targetMax,
            proteinGrams = proteinG,
            carbsGrams = carbsG,
            fatsGrams = fatsG
        )
    }

    override suspend fun updateGoogleSheetMetadata(userId: String, sheetId: String, sheetUrl: String, lastSyncAt: String) {}
    override suspend fun updateAutoSyncSheets(userId: String, enabled: Boolean) {}
    override suspend fun deleteAllUserData(userId: String) {
        workoutsFlow.value = emptyList()
        exercisesFlow.value = emptyList()
        routinesFlow.value = emptyList()
        logsFlow.value = emptyList()
        nutritionMapFlow.value = emptyMap()
        profileFlow.value = UserProfile(id = userId, userId = userId)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WorkoutAnalyticsAndNutritionTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `timeframe filtering and dashboard metrics compute live stats accurately`() {
        val nowCal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 18, 12, 0, 0) // Wednesday Nov 18, 2026
            set(Calendar.MILLISECOND, 0)
        }
        val now = nowCal.time

        val twoDaysAgo = Calendar.getInstance().apply {
            time = now
            add(Calendar.DAY_OF_YEAR, -2) // Monday Nov 16 (This Week)
        }.time
        val tenDaysAgo = Calendar.getInstance().apply {
            time = now
            add(Calendar.DAY_OF_YEAR, -10) // Last 30 Days, not This Week
        }.time
        val sixtyDaysAgo = Calendar.getInstance().apply {
            time = now
            add(Calendar.DAY_OF_YEAR, -60) // Older (All Time only)
        }.time

        val workouts = listOf(
            WorkoutSession(id = "w1", name = "Old", totalVolumeLbs = 2000.0, durationMinutes = 30, completedSetsCount = 10, createdAt = Timestamp(sixtyDaysAgo)),
            WorkoutSession(id = "w2", name = "Mid", totalVolumeLbs = 4000.0, durationMinutes = 50, completedSetsCount = 14, createdAt = Timestamp(tenDaysAgo)),
            WorkoutSession(id = "w3", name = "Recent", totalVolumeLbs = 5000.0, durationMinutes = 60, completedSetsCount = 16, createdAt = Timestamp(twoDaysAgo))
        )

        val thisWeek = WorkoutAnalyticsHelper.filterWorkoutsByTimeframe(workouts, DashboardTimeframe.THIS_WEEK, now)
        assertEquals(listOf("w3"), thisWeek.map { it.id })

        val last30 = WorkoutAnalyticsHelper.filterWorkoutsByTimeframe(workouts, DashboardTimeframe.LAST_30_DAYS, now)
        assertEquals(listOf("w2", "w3"), last30.map { it.id })

        val allMetrics = WorkoutAnalyticsHelper.computeDashboardMetrics(
            allWorkouts = workouts,
            timeframe = DashboardTimeframe.ALL_TIME,
            profile = UserProfile(),
            completedExercises = emptyList(),
            routines = listOf(WorkoutRoutine(scheduledDays = listOf(1, 3, 5))),
            now = now
        )
        assertEquals(11000.0, allMetrics.totalVolumeLbs, 0.01)
        assertEquals(3, allMetrics.sessionCount)
        assertEquals(46, allMetrics.avgDurationMinutes)
        assertEquals(40, allMetrics.completedSets)
        assertEquals(150.0, allMetrics.gainPercent, 0.01)
        assertEquals(listOf(2000f, 4000f, 5000f), allMetrics.sparklinePoints)
    }

    @Test
    fun `weekly streak counts consecutive weeks including active week and stops at gaps`() {
        val nowCal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 18, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = nowCal.time
        fun daysAgo(days: Int): Timestamp {
            val c = Calendar.getInstance().apply {
                time = now
                add(Calendar.DAY_OF_YEAR, -days)
            }
            return Timestamp(c.time)
        }

        val sessions = listOf(
            WorkoutSession(id = "s1", createdAt = daysAgo(0)),
            WorkoutSession(id = "s2", createdAt = daysAgo(7)),
            WorkoutSession(id = "s3", createdAt = daysAgo(14)),
            WorkoutSession(id = "s_gap", createdAt = daysAgo(35))
        )
        assertEquals(3, WorkoutAnalyticsHelper.computeWeeklyStreak(sessions, now))
    }

    @Test
    fun `muscle focus includes Core timed holds and bodyweight logs alongside weighted lifts`() {
        val logs = listOf(
            WorkoutExerciseLog(
                workoutId = "w1",
                exerciseId = "bench",
                exerciseName = "Bench Press",
                category = "Chest",
                totalVolumeLbs = 3000.0,
                trackingType = ExerciseTrackingType.WEIGHTED.id
            ),
            WorkoutExerciseLog(
                workoutId = "w1",
                exerciseId = "plank",
                exerciseName = "Plank",
                category = "Core",
                totalVolumeLbs = 0.0,
                totalDurationSeconds = 180,
                trackingType = ExerciseTrackingType.TIMED_HOLD.id
            )
        )
        val focus = WorkoutAnalyticsHelper.computeMuscleGroupFocus(logs)
        assertEquals(2, focus.size)
        assertEquals("Chest", focus[0].category)
        assertEquals("Core", focus[1].category)
        assertTrue(focus.sumOf { it.percent } in 99..101)
    }

    @Test
    fun `last performance and PR detection work for weighted and timed exercises`() {
        val bench = ExerciseRecord(id = "bench", name = "Barbell Bench Press", category = "Chest")
        val plank = ExerciseRecord(id = "plank", name = "Plank", category = "Core", trackingType = ExerciseTrackingType.TIMED_HOLD.id)
        val logs = listOf(
            WorkoutExerciseLog(
                workoutId = "w1",
                exerciseId = "bench",
                exerciseName = "Barbell Bench Press",
                dateString = "Nov 10, 2026",
                maxWeightLbs = 185.0,
                estimatedOneRepMaxLbs = 215.8,
                sets = listOf(ExerciseSet(1, 185.0, 5, true).toMap()),
                date = Timestamp(Date(1_700_000_000_000L))
            ),
            WorkoutExerciseLog(
                workoutId = "w1",
                exerciseId = "plank",
                exerciseName = "Plank",
                dateString = "Nov 10, 2026",
                bestDurationSeconds = 60,
                trackingType = ExerciseTrackingType.TIMED_HOLD.id,
                sets = listOf(ExerciseSet(1, 0.0, 1, true, durationSeconds = 60).toMap()),
                date = Timestamp(Date(1_700_000_000_000L))
            )
        )

        val lastPerf = WorkoutAnalyticsHelper.computeLastPerformanceByExercise(logs, listOf(bench, plank))
        assertEquals("185 lbs × 5", lastPerf["bench"]?.summaryText)
        assertEquals("60s hold", lastPerf["plank"]?.summaryText)

        val bests = WorkoutAnalyticsHelper.computeHistoricalBestByExercise(logs, listOf(bench, plank))
        assertFalse(
            WorkoutAnalyticsHelper.isNewPersonalRecord(
                sets = listOf(ExerciseSet(1, 175.0, 5, false)),
                trackingType = ExerciseTrackingType.WEIGHTED,
                historicalBest = bests["bench"]
            )
        )
        assertTrue(
            WorkoutAnalyticsHelper.isNewPersonalRecord(
                sets = listOf(ExerciseSet(1, 190.0, 5, false)),
                trackingType = ExerciseTrackingType.WEIGHTED,
                historicalBest = bests["bench"]
            )
        )
        assertTrue(
            WorkoutAnalyticsHelper.isNewPersonalRecord(
                sets = listOf(ExerciseSet(1, 0.0, 1, false, durationSeconds = 75)),
                trackingType = ExerciseTrackingType.TIMED_HOLD,
                historicalBest = bests["plank"]
            )
        )
    }

    @Test
    fun `plate calculator decomposes standard barbell loads accurately`() {
        val r135 = PlateCalculator.calculatePlates(135.0, 45.0)
        assertEquals("45 per side", r135.formatPerSideSummary())
        assertEquals(0.0, r135.remainderLbs, 0.01)

        val r225 = PlateCalculator.calculatePlates(225.0, 45.0)
        assertEquals("2×45 per side", r225.formatPerSideSummary())

        val r185 = PlateCalculator.calculatePlates(185.0, 45.0)
        assertEquals("45 + 25 per side", r185.formatPerSideSummary())

        val rEmpty = PlateCalculator.calculatePlates(45.0, 45.0)
        assertEquals("Empty bar (45 lbs)", rEmpty.formatPerSideSummary())

        val rOdd = PlateCalculator.calculatePlates(102.0, 45.0)
        assertEquals(100.0, rOdd.achievableWeightLbs, 0.01)
        assertEquals(2.0, rOdd.remainderLbs, 0.01)
    }

    @Test
    fun `nutrition logging, meal removal, reset, presets, CSV export, and Sheets history work end-to-end`() = runTest(testDispatcher) {
        val repo = AnalyticsFakeRepository()
        val vm = WorkoutViewModel(repository = repo, currentUserId = "u1")
        val jobs = listOf(
            launch { vm.userProfile.collect {} },
            launch { vm.selectedDayNutrition.collect {} },
            launch { vm.nutritionHistory.collect {} }
        )
        advanceUntilIdle()

        val today = vm.todayDateString
        vm.logNutrition(calories = 500, protein = 45, carbs = 50, fats = 12, mealName = "Chicken Bowl", dateString = today)
        vm.logNutrition(calories = 350, protein = 40, carbs = 25, fats = 5, mealName = "Whey Shake", dateString = today)
        advanceUntilIdle()

        val dayAfterTwo = vm.selectedDayNutrition.value
        assertNotNull(dayAfterTwo)
        assertEquals(850, dayAfterTwo!!.calories)
        assertEquals(2, dayAfterTwo.parsedMeals().size)

        val firstMealId = dayAfterTwo.parsedMeals().first().id
        vm.removeMealFromDay(firstMealId, today)
        advanceUntilIdle()

        val dayAfterRemove = vm.selectedDayNutrition.value!!
        assertEquals(350, dayAfterRemove.calories)
        assertEquals(1, dayAfterRemove.parsedMeals().size)

        // Custom preset save & delete
        vm.saveMealPreset("Post Lift Bagel", 420, 22, 70, 4)
        advanceUntilIdle()
        val profileWithPreset = (vm.userProfile.value as com.example.viewmodel.UiState.Success).data
        val customPreset = profileWithPreset.resolvedMealPresets().find { it.name == "Post Lift Bagel" }
        assertNotNull(customPreset)

        vm.deleteMealPreset(customPreset!!.id)
        advanceUntilIdle()
        val profileAfterDelete = (vm.userProfile.value as com.example.viewmodel.UiState.Success).data
        assertTrue(profileAfterDelete.resolvedMealPresets().none { it.name == "Post Lift Bagel" })

        // Nutrition CSV export
        val tempFile = File.createTempFile("nutrition_test", ".csv")
        tempFile.deleteOnExit()
        val csvRes = WorkoutCsvExporter.writeNutritionCsvFile(
            outputFile = tempFile,
            nutritionLogs = vm.nutritionHistory.value,
            profile = profileAfterDelete
        )
        assertEquals(1, csvRes.rowCount)
        val csvText = tempFile.readText()
        assertTrue(csvText.contains("Whey Shake"))
        assertTrue(csvText.contains("350"))

        // Google Sheets multi-day nutrition table
        val sheetsPayload = SheetsPayloadBuilder.buildExportData(
            profile = profileAfterDelete,
            workouts = emptyList(),
            logs = emptyList(),
            exercises = emptyList(),
            todayNutrition = dayAfterRemove,
            syncedAtLabel = "Now",
            nutritionHistory = vm.nutritionHistory.value
        )
        assertTrue(sheetsPayload.batchUpdateValuesJson.contains("Nutrition Date"))
        assertTrue(sheetsPayload.batchUpdateValuesJson.contains(today))

        // Reset day & date navigation
        vm.resetDailyNutrition(today)
        advanceUntilIdle()
        assertEquals(0, vm.selectedDayNutrition.value?.calories ?: -1)

        vm.shiftSelectedNutritionDate(-1)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance().apply {
            time = sdf.parse(today)!!
            add(Calendar.DAY_OF_YEAR, -1)
        }
        assertEquals(sdf.format(cal.time), vm.selectedNutritionDate.value)

        jobs.forEach { it.cancel() }
    }

    @Test
    fun `deleteAllUserData clears active workout, caches, and all user repository collections`() = runTest(testDispatcher) {
        val repo = AnalyticsFakeRepository()
        val bench = ExerciseRecord(id = "bench", name = "Barbell Bench Press", category = "Chest")
        repo.exercisesFlow.value = listOf(bench)
        repo.workoutsFlow.value = listOf(WorkoutSession(id = "w1", name = "Push Day"))
        repo.routinesFlow.value = listOf(WorkoutRoutine(id = "r1", name = "Push"))
        repo.logsFlow.value = listOf(WorkoutExerciseLog(id = "l1", workoutId = "w1", exerciseId = "bench"))

        val vm = WorkoutViewModel(repository = repo, currentUserId = "u1")
        val jobs = listOf(
            launch { vm.workouts.collect {} },
            launch { vm.exercises.collect {} },
            launch { vm.selectedDayNutrition.collect {} }
        )
        advanceUntilIdle()

        vm.startCustomWorkout(workoutName = "Live Test", selectedExercises = listOf(bench))
        assertTrue(vm.isWorkoutActive.value)
        vm.logNutrition(calories = 400, protein = 30, carbs = 40, fats = 10, mealName = "Snack")
        advanceUntilIdle()
        assertEquals(400, vm.selectedDayNutrition.value?.calories)

        val result = vm.deleteAllUserData()
        advanceUntilIdle()

        assertTrue(result.isSuccess)
        assertFalse(vm.isWorkoutActive.value)
        assertTrue(repo.workoutsFlow.value.isEmpty())
        assertTrue(repo.exercisesFlow.value.isEmpty())
        assertTrue(repo.routinesFlow.value.isEmpty())
        assertTrue(repo.logsFlow.value.isEmpty())
        assertTrue(repo.nutritionMapFlow.value.isEmpty())

        jobs.forEach { it.cancel() }
    }

    @Test
    fun `performed exercise PRs derive strictly from completed workout logs and ignore exercise settings numbers`() {
        val benchWithInflatedSettings = ExerciseRecord(
            id = "bench_press",
            name = "Barbell Bench Press",
            category = "Chest",
            startingWeightLbs = 135.0,
            currentMaxWeightLbs = 225.0,
            personalRecord1RMLbs = 285.0,
            percentIncrease = 66.7,
            historyCount = 10,
            isPrimary = true
        )
        val unloggedSquatWithSettings = ExerciseRecord(
            id = "barbell_squat",
            name = "Barbell Back Squat",
            category = "Legs",
            startingWeightLbs = 185.0,
            currentMaxWeightLbs = 315.0,
            personalRecord1RMLbs = 365.0,
            percentIncrease = 70.3,
            historyCount = 5
        )
        val plankWithInflatedSettings = ExerciseRecord(
            id = "plank",
            name = "Plank",
            category = "Core",
            trackingType = ExerciseTrackingType.TIMED_HOLD.id,
            defaultDurationSeconds = 60,
            startingDurationSeconds = 45,
            currentBestDurationSeconds = 180
        )

        val day1 = Timestamp(Date(1_700_000_000_000L))
        val day2 = Timestamp(Date(1_700_172_800_000L))
        val logs = listOf(
            WorkoutExerciseLog(
                id = "l1",
                workoutId = "w1",
                exerciseId = "bench_press",
                exerciseName = "Barbell Bench Press",
                sets = listOf(ExerciseSet(1, 145.0, 5, true).toMap()),
                maxWeightLbs = 145.0,
                estimatedOneRepMaxLbs = 169.2,
                date = day1
            ),
            WorkoutExerciseLog(
                id = "l2",
                workoutId = "w2",
                exerciseId = "bench_press",
                exerciseName = "Barbell Bench Press",
                sets = listOf(ExerciseSet(1, 165.0, 5, true).toMap()),
                maxWeightLbs = 165.0,
                estimatedOneRepMaxLbs = 192.5,
                date = day2
            ),
            WorkoutExerciseLog(
                id = "l3",
                workoutId = "w2",
                exerciseId = "plank",
                exerciseName = "Plank",
                trackingType = ExerciseTrackingType.TIMED_HOLD.id,
                sets = listOf(ExerciseSet(1, 0.0, 1, true, durationSeconds = 75).toMap()),
                bestDurationSeconds = 75,
                date = day2
            )
        )

        val prs = WorkoutAnalyticsHelper.computePerformedExercisePrs(
            logs = logs,
            exercises = listOf(benchWithInflatedSettings, unloggedSquatWithSettings, plankWithInflatedSettings)
        )

        assertEquals(2, prs.size)
        val benchPr = prs.first { it.exercise.id == "bench_press" }
        assertEquals(145.0, benchPr.startingWeightLbs, 0.01)
        assertEquals(165.0, benchPr.maxWeightLbs, 0.01)
        assertEquals(192.5, benchPr.best1RM, 0.01)
        assertEquals(13.8, benchPr.percentIncrease, 0.01)
        assertEquals("165 lbs", benchPr.formatPrimaryMetricLabel())
        assertEquals(2, benchPr.sessionsCount)

        val plankPr = prs.first { it.exercise.id == "plank" }
        assertEquals(75, plankPr.bestDurationSeconds)
        assertEquals("75s hold", plankPr.formatPrimaryMetricLabel())
    }
}
