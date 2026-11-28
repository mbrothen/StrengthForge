package com.example

import com.example.data.analytics.WeightUnit
import com.example.data.model.BodyweightEntry
import com.example.data.model.DailyNutrition
import com.example.data.model.ExerciseRecord
import com.example.data.model.MealEntry
import com.example.data.model.MealPreset
import com.example.data.model.UserProfile
import com.example.data.model.WorkoutExerciseLog
import com.example.data.model.WorkoutRoutine
import com.example.data.model.WorkoutSession
import com.example.data.onboarding.FitnessGoal
import com.example.data.onboarding.GoalOnboardingHelper
import com.example.data.onboarding.TrainingSplit
import com.example.data.repository.LoggedExerciseData
import com.example.data.repository.WorkoutDataRepository
import com.example.data.repository.WorkoutRepository
import com.example.data.schedule.Weekday
import com.example.viewmodel.UiState
import com.example.viewmodel.WorkoutViewModel
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

private class FakeWorkoutRepository : WorkoutDataRepository {
    val profileFlow = MutableStateFlow(UserProfile(id = "u1", userId = "u1"))
    val workoutsFlow = MutableStateFlow<List<WorkoutSession>>(emptyList())
    val exercisesFlow = MutableStateFlow<List<ExerciseRecord>>(emptyList())
    val routinesFlow = MutableStateFlow<List<WorkoutRoutine>>(emptyList())
    val logsFlow = MutableStateFlow<List<WorkoutExerciseLog>>(emptyList())
    val nutritionFlow = MutableStateFlow<DailyNutrition?>(null)
    val nutritionHistoryFlow = MutableStateFlow<List<DailyNutrition>>(emptyList())
    val bodyweightFlow = MutableStateFlow<List<BodyweightEntry>>(emptyList())

    var lastSavedRestSeconds: Int? = null

    override fun observeUserProfile(userId: String): Flow<UserProfile> = profileFlow
    override fun observeWorkouts(userId: String): Flow<List<WorkoutSession>> = workoutsFlow
    override fun observeExerciseRecords(userId: String): Flow<List<ExerciseRecord>> = exercisesFlow
    override fun observeRoutines(userId: String): Flow<List<WorkoutRoutine>> = routinesFlow
    override fun observeLogs(userId: String): Flow<List<WorkoutExerciseLog>> = logsFlow
    override fun observeNutrition(userId: String, dateString: String): Flow<DailyNutrition?> = nutritionFlow
    override fun observeNutritionHistory(userId: String): Flow<List<DailyNutrition>> = nutritionHistoryFlow
    override fun observeBodyweightLogs(userId: String): Flow<List<BodyweightEntry>> = bodyweightFlow

    override suspend fun logBodyweight(
        userId: String,
        dateString: String,
        weightLbs: Double,
        bodyFatPercent: Double?,
        notes: String
    ) {
        val entry = BodyweightEntry(
            id = dateString,
            userId = userId,
            dateString = dateString,
            weightLbs = weightLbs,
            bodyFatPercent = bodyFatPercent,
            notes = notes
        )
        bodyweightFlow.value = (bodyweightFlow.value.filterNot { it.dateString == dateString } + entry)
            .sortedByDescending { it.dateString }
    }

    override suspend fun deleteBodyweightEntry(userId: String, dateString: String) {
        bodyweightFlow.value = bodyweightFlow.value.filterNot { it.dateString == dateString }
    }

    override suspend fun saveExercise(userId: String, exercise: ExerciseRecord): String {
        val id = exercise.id.ifBlank { "ex_${exercisesFlow.value.size + 1}" }
        val updated = exercise.copy(id = id)
        exercisesFlow.value = exercisesFlow.value.filterNot { it.id == id } + updated
        return id
    }

    override suspend fun deleteExercise(userId: String, exerciseId: String) {
        exercisesFlow.value = exercisesFlow.value.filterNot { it.id == exerciseId }
    }

    override suspend fun saveRoutine(userId: String, routine: WorkoutRoutine): String {
        val id = routine.id.ifBlank { "rt_${routinesFlow.value.size + 1}" }
        val updated = routine.copy(id = id)
        routinesFlow.value = routinesFlow.value.filterNot { it.id == id } + updated
        return id
    }

    override suspend fun deleteRoutine(userId: String, routineId: String) {
        routinesFlow.value = routinesFlow.value.filterNot { it.id == routineId }
    }

    override suspend fun seedInitialDataIfNeeded(userId: String) {}

    override suspend fun saveWorkout(
        userId: String,
        workoutName: String,
        durationMinutes: Int,
        notes: String,
        exerciseEntries: List<LoggedExerciseData>,
        customDate: Date?,
        restSeconds: Int
    ): String {
        lastSavedRestSeconds = restSeconds
        val workoutId = "w_${workoutsFlow.value.size + 1}"
        val session = WorkoutSession(
            id = workoutId,
            userId = userId,
            name = workoutName,
            totalVolumeLbs = exerciseEntries.sumOf { it.totalVolume },
            durationMinutes = durationMinutes,
            notes = notes,
            exerciseCount = exerciseEntries.size,
            completedSetsCount = exerciseEntries.sumOf { it.sets.count { s -> s.completed } },
            restSeconds = restSeconds,
            createdAt = Timestamp(customDate ?: Date())
        )
        workoutsFlow.value = listOf(session) + workoutsFlow.value

        exercisesFlow.value = exercisesFlow.value.map { ex ->
            val logged = exerciseEntries.find { it.exerciseId == ex.id }
            if (logged != null) {
                val (newMax, new1RM, gainPct) = WorkoutRepository.computeUpdatedExerciseMaxStats(
                    startingWeightLbs = ex.startingWeightLbs,
                    currentMaxWeightLbs = ex.currentMaxWeightLbs,
                    current1RMLbs = ex.personalRecord1RMLbs,
                    loggedMaxWeightLbs = logged.maxWeight,
                    logged1RMLbs = logged.estimated1RM
                )
                ex.copy(
                    currentMaxWeightLbs = newMax,
                    personalRecord1RMLbs = new1RM,
                    percentIncrease = gainPct,
                    historyCount = ex.historyCount + 1
                )
            } else {
                ex
            }
        }
        return workoutId
    }

    override suspend fun updateWorkoutWithExercises(
        userId: String,
        workoutId: String,
        workoutName: String,
        durationMinutes: Int,
        notes: String,
        exerciseEntries: List<LoggedExerciseData>,
        customDate: Date?,
        restSeconds: Int
    ) {
        lastSavedRestSeconds = restSeconds
    }

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
    ) {}
    override suspend fun saveMealPresets(userId: String, presets: List<MealPreset>) {}
    override suspend fun updateMacroTargets(userId: String, targetCalories: Int, targetMin: Int, targetMax: Int, proteinG: Int, carbsG: Int, fatsG: Int) {}
    override suspend fun updateGoogleSheetMetadata(userId: String, sheetId: String, sheetUrl: String, lastSyncAt: String) {}
    override suspend fun updateAutoSyncSheets(userId: String, enabled: Boolean) {}
    override suspend fun updateUserPreferences(
        userId: String,
        weightUnit: WeightUnit?,
        defaultRestSeconds: Int?,
        restNotificationsEnabled: Boolean?,
        onboardingCompleted: Boolean?,
        firstDayOfWeek: Weekday?,
        workoutRemindersEnabled: Boolean?,
        workoutReminderHour: Int?,
        workoutReminderMinute: Int?
    ) {
        val current = profileFlow.value
        profileFlow.value = current.copy(
            weightUnit = weightUnit?.id ?: current.weightUnit,
            defaultRestSeconds = defaultRestSeconds ?: current.defaultRestSeconds,
            restNotificationsEnabled = restNotificationsEnabled ?: current.restNotificationsEnabled,
            onboardingCompleted = onboardingCompleted ?: current.onboardingCompleted,
            firstDayOfWeek = firstDayOfWeek?.isoDay ?: current.firstDayOfWeek,
            workoutRemindersEnabled = workoutRemindersEnabled ?: current.workoutRemindersEnabled,
            workoutReminderHour = workoutReminderHour ?: current.workoutReminderHour,
            workoutReminderMinute = workoutReminderMinute ?: current.workoutReminderMinute
        )
    }

    override suspend fun completeOnboarding(
        userId: String,
        goal: FitnessGoal,
        split: TrainingSplit,
        weightUnit: WeightUnit
    ) {
        val current = profileFlow.value
        profileFlow.value = current.copy(
            fitnessGoal = goal.id,
            trainingSplit = split.id,
            weightUnit = weightUnit.id,
            onboardingCompleted = true,
            targetCalories = goal.targetCalories,
            targetCaloriesMin = goal.minCalories,
            targetCaloriesMax = goal.maxCalories,
            proteinGrams = goal.proteinG,
            carbsGrams = goal.carbsG,
            fatsGrams = goal.fatsG
        )
        routinesFlow.value = GoalOnboardingHelper.buildStarterRoutines(
            userId = userId,
            split = split,
            defaultRestSeconds = current.defaultRestSeconds
        )
    }

    override suspend fun deleteAllUserData(userId: String) {
        workoutsFlow.value = emptyList()
        exercisesFlow.value = emptyList()
        routinesFlow.value = emptyList()
        logsFlow.value = emptyList()
        nutritionFlow.value = null
        nutritionHistoryFlow.value = emptyList()
        profileFlow.value = UserProfile(id = userId, userId = userId)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WorkoutProgressionAndTimerTest {

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
    fun `exercise default sets and reps populate workout sets at current max weight`() = runTest(testDispatcher) {
        val repo = FakeWorkoutRepository()
        val bench = ExerciseRecord(
            id = "bench_press",
            name = "Barbell Bench Press",
            category = "Chest",
            startingWeightLbs = 135.0,
            currentMaxWeightLbs = 165.0,
            defaultSets = 5,
            defaultReps = 5
        )
        val curl = ExerciseRecord(
            id = "bicep_curl",
            name = "Dumbbell Bicep Curl",
            category = "Arms",
            startingWeightLbs = 25.0,
            currentMaxWeightLbs = 35.0,
            defaultSets = 4,
            defaultReps = 12
        )
        repo.exercisesFlow.value = listOf(bench, curl)

        val vm = WorkoutViewModel(repository = repo, currentUserId = "u1")
        val collectJob = launch { vm.exercises.collect {} }
        advanceUntilIdle()
        assertTrue(vm.exercises.value is UiState.Success)

        vm.startCustomWorkout(
            workoutName = "Upper Strength",
            selectedExercises = listOf(bench),
            restSeconds = 120
        )

        var active = vm.activeExercises.value
        assertEquals(1, active.size)
        assertEquals(5, active[0].sets.size)
        assertTrue(active[0].sets.all { it.reps == 5 && it.weightLbs == 165.0 })

        vm.addExerciseToWorkout(curl)
        active = vm.activeExercises.value
        assertEquals(2, active.size)
        assertEquals(4, active[1].sets.size)
        assertTrue(active[1].sets.all { it.reps == 12 && it.weightLbs == 35.0 })

        vm.discardWorkout()
        collectJob.cancel()
    }

    @Test
    fun `rest period seconds configure per workout and emit beep event only on natural completion`() = runTest(testDispatcher) {
        val repo = FakeWorkoutRepository()
        val routine = WorkoutRoutine(
            id = "r1",
            name = "Heavy Push",
            exerciseIds = emptyList(),
            restSeconds = 75
        )
        val vm = WorkoutViewModel(repository = repo, currentUserId = "u1")

        var beepCount = 0
        val eventJob = launch {
            vm.restTimerCompletedEvent.collect { beepCount++ }
        }

        vm.startWorkoutWithRoutine(routine)
        assertEquals(75, vm.restTimerInitialDuration.value)

        // Clamp bounds check (min 5s, max 900s)
        vm.setRestTimerDurationSeconds(2)
        assertEquals(5, vm.restTimerInitialDuration.value)
        vm.setRestTimerDurationSeconds(1500)
        assertEquals(900, vm.restTimerInitialDuration.value)

        // Start a 5s rest timer and let it finish naturally -> emits completion event
        vm.startRestTimer(5)
        runCurrent()
        assertTrue(vm.isRestTimerRunning.value)
        assertEquals(5, vm.restTimerRemainingSeconds.value)

        advanceTimeBy(5000)
        runCurrent()
        assertFalse(vm.isRestTimerRunning.value)
        assertEquals(0, vm.restTimerRemainingSeconds.value)
        assertEquals(1, beepCount)

        // Start another timer and cancel early -> should NOT emit beep
        vm.startRestTimer(30)
        runCurrent()
        advanceTimeBy(2000)
        vm.cancelRestTimer()
        advanceTimeBy(30000)
        runCurrent()
        assertEquals(1, beepCount)

        // Editing an existing workout restores its persisted restSeconds
        vm.startEditingWorkout(
            WorkoutSession(id = "w_past", name = "Past Pull", restSeconds = 135, durationMinutes = 40)
        )
        assertEquals(135, vm.restTimerInitialDuration.value)

        vm.discardWorkout()
        eventJob.cancel()
    }

    @Test
    fun `logging heavier weight automatically adjusts max weight for future workouts`() = runTest(testDispatcher) {
        val repo = FakeWorkoutRepository()
        val squat = ExerciseRecord(
            id = "barbell_squat",
            name = "Barbell Back Squat",
            category = "Legs",
            startingWeightLbs = 185.0,
            currentMaxWeightLbs = 215.0,
            personalRecord1RMLbs = 245.0,
            defaultSets = 3,
            defaultReps = 6
        )
        repo.exercisesFlow.value = listOf(squat)

        val vm = WorkoutViewModel(repository = repo, currentUserId = "u1")
        val collectJob = launch { vm.exercises.collect {} }
        advanceUntilIdle()

        // Start workout: starts prefilled with currentMaxWeightLbs = 215.0
        vm.startCustomWorkout(
            workoutName = "Leg Day",
            selectedExercises = listOf(squat),
            restSeconds = 150
        )
        assertEquals(215.0, vm.activeExercises.value.first().sets.first().weightLbs, 0.001)

        // User logs a heavier set at 225 lbs
        vm.updateSet(exerciseIndex = 0, setIndex = 0, weight = 225.0, reps = 6, completed = true)
        vm.finishWorkout(notes = "New squat PR!")
        advanceUntilIdle()

        assertEquals(150, repo.lastSavedRestSeconds)
        val updatedSquat = repo.exercisesFlow.value.first { it.id == "barbell_squat" }
        assertEquals(225.0, updatedSquat.currentMaxWeightLbs, 0.001)
        assertEquals(21.6, updatedSquat.percentIncrease, 0.01)

        // Starting the next workout automatically prefills 225.0 lbs
        val currentExercises = (vm.exercises.value as UiState.Success).data
        vm.startCustomWorkout(workoutName = "Next Leg Day", selectedExercises = currentExercises)
        assertEquals(225.0, vm.activeExercises.value.first().sets.first().weightLbs, 0.001)

        // Logging a lighter deload workout does NOT lower the recorded max weight
        val (unchangedMax, _, _) = WorkoutRepository.computeUpdatedExerciseMaxStats(
            startingWeightLbs = 185.0,
            currentMaxWeightLbs = 225.0,
            current1RMLbs = 270.0,
            loggedMaxWeightLbs = 195.0,
            logged1RMLbs = 230.0
        )
        assertEquals(225.0, unchangedMax, 0.001)

        vm.discardWorkout()
        collectJob.cancel()
    }

    @Test
    fun `workout timer pauses and resumes cleanly without losing elapsed time`() = runTest(testDispatcher) {
        val repo = FakeWorkoutRepository()
        val squat = ExerciseRecord(
            id = "barbell_squat",
            name = "Barbell Back Squat",
            category = "Legs"
        )
        val vm = WorkoutViewModel(repository = repo, currentUserId = "u1")

        vm.startCustomWorkout(workoutName = "Squat Day", selectedExercises = listOf(squat))
        assertFalse(vm.isWorkoutTimerPaused.value)
        assertEquals(0, vm.workoutDurationSeconds.value)

        advanceTimeBy(3000)
        runCurrent()
        assertEquals(3, vm.workoutDurationSeconds.value)

        vm.toggleWorkoutTimerPaused()
        assertTrue(vm.isWorkoutTimerPaused.value)
        advanceTimeBy(5000)
        runCurrent()
        assertEquals(3, vm.workoutDurationSeconds.value)

        vm.toggleWorkoutTimerPaused()
        assertFalse(vm.isWorkoutTimerPaused.value)
        advanceTimeBy(2000)
        runCurrent()
        assertEquals(5, vm.workoutDurationSeconds.value)

        vm.discardWorkout()
        assertFalse(vm.isWorkoutTimerPaused.value)
    }

    @Test
    fun `navigation backstack returns to previous screens and only exits at root`() {
        val stack = mutableListOf(NavDestination.DASHBOARD.ordinal)
        pushNavDestination(stack, NavDestination.HISTORY.ordinal)
        pushNavDestination(stack, NavDestination.HISTORY.ordinal) // duplicate ignored
        pushNavDestination(stack, NavDestination.GAINS.ordinal)
        assertEquals(
            listOf(NavDestination.DASHBOARD.ordinal, NavDestination.HISTORY.ordinal, NavDestination.GAINS.ordinal),
            stack
        )

        assertTrue(popNavDestination(stack))
        assertEquals(NavDestination.HISTORY.ordinal, stack.last())

        assertTrue(popNavDestination(stack))
        assertEquals(NavDestination.DASHBOARD.ordinal, stack.last())

        // At root: should not pop further so system back can exit
        assertFalse(popNavDestination(stack))
    }

    @Test
    fun `rest timer dual beep pcm waveform contains two non-silent tones separated by silence`() {
        val sampleRate = 44100
        val samples = com.example.ui.tracker.generateDualBeepPcm(sampleRate)
        assertTrue(samples.isNotEmpty())
        val firstBeepPeak = samples.slice(1000 until 6000).maxOf { kotlin.math.abs(it.toInt()) }
        val gapPeak = samples.slice(8200 until 11000).maxOf { kotlin.math.abs(it.toInt()) }
        val secondBeepPeak = samples.slice(13000 until 20000).maxOf { kotlin.math.abs(it.toInt()) }
        assertTrue(firstBeepPeak > 10000)
        assertEquals(0, gapPeak)
        assertTrue(secondBeepPeak > 10000)
    }

    @Test
    fun `bench press progression chart excludes other exercises containing Bench in name`() = runTest(testDispatcher) {
        val repo = FakeWorkoutRepository()
        val barbellBench = ExerciseRecord(
            id = "bench_press",
            name = "Barbell Bench Press",
            category = "Chest",
            startingWeightLbs = 95.0,
            currentMaxWeightLbs = 95.0,
            historyCount = 2
        )
        val dumbbellBench = ExerciseRecord(
            id = "db_bench",
            name = "Dumbbell Bench Press",
            category = "Chest",
            startingWeightLbs = 15.0,
            currentMaxWeightLbs = 15.0,
            historyCount = 2
        )
        repo.exercisesFlow.value = listOf(barbellBench, dumbbellBench)
        repo.logsFlow.value = listOf(
            WorkoutExerciseLog(
                id = "l1",
                workoutId = "w1",
                exerciseId = "bench_press",
                exerciseName = "Barbell Bench Press",
                maxWeightLbs = 95.0,
                dateString = "Nov 10, 2026",
                date = Timestamp(Date(1_700_000_000_000L))
            ),
            WorkoutExerciseLog(
                id = "l2",
                workoutId = "w1",
                exerciseId = "db_bench",
                exerciseName = "Dumbbell Bench Press",
                maxWeightLbs = 15.0,
                dateString = "Nov 10, 2026",
                date = Timestamp(Date(1_700_000_000_000L))
            ),
            WorkoutExerciseLog(
                id = "l3",
                workoutId = "w2",
                exerciseId = "bench_press",
                exerciseName = "Barbell Bench Press",
                maxWeightLbs = 95.0,
                dateString = "Nov 14, 2026",
                date = Timestamp(Date(1_700_300_000_000L))
            ),
            WorkoutExerciseLog(
                id = "l4",
                workoutId = "w2",
                exerciseId = "db_bench",
                exerciseName = "Dumbbell Bench Press",
                maxWeightLbs = 15.0,
                dateString = "Nov 14, 2026",
                date = Timestamp(Date(1_700_300_000_000L))
            )
        )

        val vm = WorkoutViewModel(repository = repo, currentUserId = "u1")
        val collectJobs = listOf(
            launch { vm.exercises.collect {} },
            launch { vm.logs.collect {} },
            launch { vm.selectedExerciseProgressPoints.collect {} }
        )
        vm.setSelectedExercise("bench_press")
        advanceUntilIdle()

        val benchPoints = vm.selectedExerciseProgressPoints.value
        assertEquals(2, benchPoints.size)
        assertTrue(benchPoints.all { it.weightLbs == 95.0 })

        vm.setSelectedExercise("db_bench")
        advanceUntilIdle()

        val dbPoints = vm.selectedExerciseProgressPoints.value
        assertEquals(2, dbPoints.size)
        assertTrue(dbPoints.all { it.weightLbs == 15.0 })

        collectJobs.forEach { it.cancel() }
    }
}
