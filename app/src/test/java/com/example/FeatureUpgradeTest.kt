package com.example

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.example.data.analytics.PlateCalculator
import com.example.data.analytics.WeightUnit
import com.example.data.analytics.WeightUnitHelper
import com.example.data.model.BodyweightEntry
import com.example.data.model.DailyNutrition
import com.example.data.model.ExerciseRecord
import com.example.data.model.ExerciseSet
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
import com.example.data.schedule.Weekday
import com.example.notifications.RestTimerAlarmReceiver
import com.example.notifications.RestTimerNotificationHelper
import com.example.viewmodel.UiState
import com.example.viewmodel.WorkoutViewModel
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.Date

private class FeatureUpgradeFakeRepository : WorkoutDataRepository {
    val profileFlow = MutableStateFlow(
        UserProfile(
            id = "u_upgrade",
            userId = "u_upgrade",
            onboardingCompleted = false
        )
    )
    val workoutsFlow = MutableStateFlow<List<WorkoutSession>>(emptyList())
    val exercisesFlow = MutableStateFlow<List<ExerciseRecord>>(emptyList())
    val routinesFlow = MutableStateFlow<List<WorkoutRoutine>>(emptyList())
    val logsFlow = MutableStateFlow<List<WorkoutExerciseLog>>(emptyList())
    val nutritionFlow = MutableStateFlow<DailyNutrition?>(null)
    val nutritionHistoryFlow = MutableStateFlow<List<DailyNutrition>>(emptyList())
    val bodyweightFlow = MutableStateFlow<List<BodyweightEntry>>(emptyList())

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
    ): String = "w_1"

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
    ) {}

    override suspend fun saveMealPresets(userId: String, presets: List<MealPreset>) {}
    override suspend fun updateMacroTargets(
        userId: String,
        targetCalories: Int,
        targetMin: Int,
        targetMax: Int,
        proteinG: Int,
        carbsG: Int,
        fatsG: Int
    ) {}

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

    override suspend fun deleteAllUserData(userId: String) {}
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FeatureUpgradeTest {

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
    fun `kg plate calculator computes 20kg bar and fractional 1_25kg plates accurately`() {
        val result = PlateCalculator.calculatePlatesForUnit(
            targetDisplayWeight = 102.5,
            barDisplayWeight = PlateCalculator.DEFAULT_BAR_KG,
            unit = WeightUnit.KG
        )
        assertEquals(0.0, result.remainderLbs, 0.001)
        assertEquals(102.5, result.achievableWeightLbs, 0.001)
        assertEquals("25 + 15 + 1.25 per side", result.formatPerSideSummary())

        val emptyBar = PlateCalculator.calculatePlatesForUnit(
            targetDisplayWeight = 20.0,
            barDisplayWeight = PlateCalculator.DEFAULT_BAR_KG,
            unit = WeightUnit.KG
        )
        assertEquals("Empty bar (20 kg)", emptyBar.formatPerSideSummary())
        assertEquals(listOf(20.0, 15.0, 7.0, 0.0), PlateCalculator.availableBarsForUnit(WeightUnit.KG))
    }

    @Test
    fun `weight unit helper round trips common kg increments cleanly`() {
        val weightsKg = listOf(22.5, 60.0, 100.0, 142.5)
        weightsKg.forEach { kg ->
            val lbs = WeightUnitHelper.toLbs(kg, WeightUnit.KG)
            val backToKg = WeightUnitHelper.fromLbs(lbs, WeightUnit.KG)
            assertEquals(kg, backToKg, 0.05)
        }
        assertEquals("100 kg", WeightUnitHelper.formatWeightWithUnit(WeightUnitHelper.toLbs(100.0, WeightUnit.KG), WeightUnit.KG))
        assertEquals("135 lbs", WeightUnitHelper.formatWeightWithUnit(135.0, WeightUnit.LBS))
    }

    @Test
    fun `goal onboarding helper builds starter routines for all splits`() {
        val ppl = GoalOnboardingHelper.buildStarterRoutines("u1", TrainingSplit.PPL, defaultRestSeconds = 120)
        assertEquals(3, ppl.size)
        assertTrue(ppl.all { it.restSeconds == 120 })
        assertEquals(listOf(1, 4), ppl[0].scheduledDays)

        val upperLower = GoalOnboardingHelper.buildStarterRoutines("u1", TrainingSplit.UPPER_LOWER)
        assertEquals(2, upperLower.size)
        assertEquals("Upper Strength A", upperLower[0].name)

        val fullBody = GoalOnboardingHelper.buildStarterRoutines("u1", TrainingSplit.FULL_BODY)
        assertEquals(3, fullBody.size)
        assertEquals(listOf(listOf(1), listOf(3), listOf(5)), fullBody.map { it.scheduledDays })
    }

    @Test
    fun `view model handles onboarding settings and kg last session formatting`() = runTest(testDispatcher) {
        val repo = FeatureUpgradeFakeRepository()
        val bench = ExerciseRecord(
            id = "bench_press",
            name = "Bench Press",
            category = "Chest",
            startingWeightLbs = WeightUnitHelper.toLbs(60.0, WeightUnit.KG),
            currentMaxWeightLbs = WeightUnitHelper.toLbs(100.0, WeightUnit.KG)
        )
        repo.exercisesFlow.value = listOf(bench)
        repo.logsFlow.value = listOf(
            WorkoutExerciseLog(
                id = "log_1",
                workoutId = "w_old",
                exerciseId = "bench_press",
                exerciseName = "Bench Press",
                category = "Chest",
                maxWeightLbs = WeightUnitHelper.toLbs(100.0, WeightUnit.KG),
                sets = listOf(ExerciseSet(1, WeightUnitHelper.toLbs(100.0, WeightUnit.KG), 5, true).toMap()),
                date = Timestamp(Date()),
                dateString = "2026-11-25"
            )
        )

        val vm = WorkoutViewModel(repository = repo, currentUserId = "u_upgrade")
        val jobs = listOf(
            launch { vm.userProfile.collect {} },
            launch { vm.routines.collect {} },
            launch { vm.lastSessionByExerciseId.collect {} }
        )
        advanceUntilIdle()

        var callbackDone = false
        vm.completeOnboarding(
            goal = FitnessGoal.BULK,
            split = TrainingSplit.UPPER_LOWER,
            weightUnit = WeightUnit.KG,
            onDone = { callbackDone = true }
        )
        advanceUntilIdle()

        assertTrue(callbackDone)
        assertEquals(WeightUnit.KG, vm.weightUnit.value)
        val profileSuccess = vm.userProfile.value as UiState.Success
        assertTrue(profileSuccess.data.onboardingCompleted)
        assertEquals(FitnessGoal.BULK.targetCalories, profileSuccess.data.targetCalories)
        assertEquals(2, (vm.routines.value as UiState.Success).data.size)

        val lastBench = vm.lastSessionByExerciseId.value["bench_press"]
        assertNotNull(lastBench)
        assertTrue(lastBench!!.summaryText.contains("100 kg"))

        val jobs2 = listOf(
            launch { vm.weeklySchedule.collect {} }
        )
        vm.updateSettingsPreferences(
            weightUnit = WeightUnit.LBS,
            defaultRestSeconds = 150,
            restNotificationsEnabled = false,
            firstDayOfWeek = Weekday.SUNDAY
        )
        advanceUntilIdle()

        assertEquals(WeightUnit.LBS, vm.weightUnit.value)
        assertEquals(150, vm.defaultRestSeconds.value)
        assertFalse(vm.restNotificationsEnabled.value)
        assertEquals(Weekday.SUNDAY, vm.firstDayOfWeek.value)
        assertEquals(Weekday.SUNDAY, vm.weeklySchedule.value.days.first().weekday)

        // Reset onboarding flag then verify dismissOnboarding marks it complete
        repo.profileFlow.value = repo.profileFlow.value.copy(onboardingCompleted = false)
        advanceUntilIdle()
        vm.dismissOnboarding()
        advanceUntilIdle()
        assertTrue((vm.userProfile.value as UiState.Success).data.onboardingCompleted)

        jobs.forEach { it.cancel() }
        jobs2.forEach { it.cancel() }
    }

    @Test
    fun `rest timer notification helper creates channel schedules alarms and posts notification`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val shadowApp = Shadows.shadowOf(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            shadowApp.grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        RestTimerNotificationHelper.ensureNotificationChannel(context)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = notificationManager.getNotificationChannel(RestTimerNotificationHelper.CHANNEL_ID)
        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)

        RestTimerNotificationHelper.scheduleRestAlarm(context, delaySeconds = 45)
        RestTimerNotificationHelper.scheduleHoldAlarm(context, delaySeconds = 30)
        RestTimerNotificationHelper.cancelRestAlarm(context)
        RestTimerNotificationHelper.cancelHoldAlarm(context)

        val receiver = RestTimerAlarmReceiver()
        val intent = Intent(context, RestTimerAlarmReceiver::class.java).apply {
            putExtra(RestTimerNotificationHelper.EXTRA_NOTIFICATION_ID, RestTimerNotificationHelper.REQUEST_CODE_REST_TIMER)
            putExtra(RestTimerNotificationHelper.EXTRA_TITLE, "Rest Complete")
            putExtra(RestTimerNotificationHelper.EXTRA_BODY, "Next set ready")
        }
        receiver.onReceive(context, intent)

        val shadowNm = Shadows.shadowOf(notificationManager)
        assertTrue(shadowNm.allNotifications.isNotEmpty())
    }
}
