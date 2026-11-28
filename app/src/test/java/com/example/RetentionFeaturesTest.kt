package com.example

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.data.analytics.BodyweightAnalyticsHelper
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
import com.example.data.onboarding.TrainingSplit
import com.example.data.repository.LoggedExerciseData
import com.example.data.repository.WorkoutDataRepository
import com.example.data.schedule.Weekday
import com.example.notifications.RestTimerActionReceiver
import com.example.notifications.RestTimerNotificationAction
import com.example.notifications.RestTimerNotificationHelper
import com.example.notifications.WorkoutReminderHelper
import com.example.notifications.WorkoutReminderReceiver
import com.example.viewmodel.WorkoutViewModel
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private class RetentionFakeRepository : WorkoutDataRepository {
    val profileFlow = MutableStateFlow(
        UserProfile(
            id = "u_ret",
            userId = "u_ret",
            fitnessGoal = FitnessGoal.CUT.id,
            onboardingCompleted = true
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
    ): String = "w_ret"

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
    ) {}

    override suspend fun deleteAllUserData(userId: String) {
        bodyweightFlow.value = emptyList()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RetentionFeaturesTest {

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
    fun `lockscreen rest notification displays +15s and Skip actions and handles both cleanly`() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowManager = Shadows.shadowOf(manager)

        RestTimerNotificationHelper.scheduleRestAlarm(
            context = app,
            delaySeconds = 60,
            workoutName = "Push Day A",
            showLockscreenControls = true
        )

        val activeNotif = shadowManager.getNotification(RestTimerNotificationHelper.NOTIFICATION_ID_REST_ACTIVE)
        assertNotNull(activeNotif)
        assertEquals(2, activeNotif.actions?.size)
        assertEquals("+15s", activeNotif.actions[0].title.toString())
        assertEquals("Skip", activeNotif.actions[1].title.toString())

        val initialEnd = RestTimerNotificationHelper.readPersistedEndElapsedMs(app)
        assertFalse(RestTimerNotificationHelper.wasPersistedRestSkipped(app))

        val extendEvent = async { RestTimerNotificationHelper.actionFlow.first() }
        RestTimerActionReceiver().onReceive(
            app,
            Intent(app, RestTimerActionReceiver::class.java).apply {
                action = RestTimerNotificationHelper.ACTION_EXTEND_REST_15S
            }
        )
        val emittedExtend = extendEvent.await() as RestTimerNotificationAction.Extend
        assertEquals(15, emittedExtend.addedSeconds)
        assertTrue(emittedExtend.newRemainingSeconds >= 70)
        assertTrue(RestTimerNotificationHelper.readPersistedEndElapsedMs(app) > initialEnd)

        val skipEvent = async { RestTimerNotificationHelper.actionFlow.first() }
        RestTimerActionReceiver().onReceive(
            app,
            Intent(app, RestTimerActionReceiver::class.java).apply {
                action = RestTimerNotificationHelper.ACTION_SKIP_REST
            }
        )
        assertEquals(RestTimerNotificationAction.Skip, skipEvent.await())
        assertTrue(RestTimerNotificationHelper.wasPersistedRestSkipped(app))
        assertNull(shadowManager.getNotification(RestTimerNotificationHelper.NOTIFICATION_ID_REST_ACTIVE))
    }

    @Test
    fun `workout reminder planner skips worked-out today and finds next scheduled day`() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 6, 10, 0, 0) // Monday 10:00 AM
            set(Calendar.MILLISECOND, 0)
        }
        val mondayMorning = cal.time

        val routines = listOf(
            WorkoutRoutine(id = "r_push", name = "Push Day A", scheduledDays = listOf(1, 4)), // Mon & Thu
            WorkoutRoutine(id = "r_pull", name = "Pull Day A", scheduledDays = listOf(2, 5))  // Tue & Fri
        )

        // No workouts yet -> schedules today (Monday) at 6:00 PM
        val todayPlan = WorkoutReminderHelper.computeNextReminderPlan(
            routines = routines,
            workouts = emptyList(),
            hour = 18,
            minute = 0,
            now = mondayMorning
        )
        assertNotNull(todayPlan)
        assertTrue(todayPlan!!.isToday)
        assertEquals(Weekday.MONDAY, todayPlan.weekday)
        assertEquals("Push Day A", todayPlan.routineLabel)

        // Already worked out today -> skips Monday and schedules Tuesday Pull Day A
        val loggedToday = listOf(
            WorkoutSession(
                id = "w1",
                name = "Push Day A",
                dateString = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(mondayMorning),
                createdAt = Timestamp(mondayMorning)
            )
        )
        val tomorrowPlan = WorkoutReminderHelper.computeNextReminderPlan(
            routines = routines,
            workouts = loggedToday,
            hour = 18,
            minute = 0,
            now = mondayMorning
        )
        assertNotNull(tomorrowPlan)
        assertFalse(tomorrowPlan!!.isToday)
        assertEquals(Weekday.TUESDAY, tomorrowPlan.weekday)
        assertEquals("Pull Day A", tomorrowPlan.routineLabel)
    }

    @Test
    fun `workout reminder receiver posts notification with streak copy`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowManager = Shadows.shadowOf(manager)

        WorkoutReminderReceiver().onReceive(
            app,
            Intent(app, WorkoutReminderReceiver::class.java).apply {
                putExtra(WorkoutReminderHelper.EXTRA_ROUTINE_LABEL, "Upper Power")
                putExtra(WorkoutReminderHelper.EXTRA_STREAK, 4)
            }
        )

        val posted = shadowManager.getNotification(WorkoutReminderHelper.REQUEST_CODE_WORKOUT_REMINDER)
        assertNotNull(posted)
        val extras = posted.extras
        assertTrue(extras.getString("android.title")?.contains("Upper Power") == true)
        assertTrue(extras.getString("android.text")?.contains("4-week streak") == true)
    }

    @Test
    fun `bodyweight analytics computes 7-day moving average and goal alignment across units`() {
        val entries = listOf(
            BodyweightEntry(id = "2026-04-01", dateString = "2026-04-01", weightLbs = 182.0),
            BodyweightEntry(id = "2026-04-03", dateString = "2026-04-03", weightLbs = 181.0),
            BodyweightEntry(id = "2026-04-08", dateString = "2026-04-08", weightLbs = 180.0),
            BodyweightEntry(id = "2026-04-10", dateString = "2026-04-10", weightLbs = 179.0)
        )

        val cutSummary = BodyweightAnalyticsHelper.computeBodyweightSummary(
            entries = entries,
            goal = FitnessGoal.CUT,
            unit = WeightUnit.LBS
        )

        assertEquals(4, cutSummary.trendPoints.size)
        assertEquals(179.0, cutSummary.latestWeightLbs!!, 0.01)
        // Last 7 days from 2026-04-10 includes 2026-04-08 (180) and 2026-04-10 (179) -> avg 179.5
        assertEquals(179.5, cutSummary.sevenDayAvgLbs!!, 0.05)
        assertTrue(cutSummary.goalAlignmentLabel.contains("On Cut Pace"))
        assertTrue(cutSummary.goalAlignmentLabel.contains("lbs/wk"))

        val kgSummary = BodyweightAnalyticsHelper.computeBodyweightSummary(
            entries = entries,
            goal = FitnessGoal.CUT,
            unit = WeightUnit.KG
        )
        assertTrue(kgSummary.goalAlignmentLabel.contains("kg/wk"))
    }

    @Test
    fun `viewmodel updates reminder preferences and logs or deletes bodyweight in kg mode`() = runTest {
        val repo = RetentionFakeRepository()
        val vm = WorkoutViewModel(repo, "u_ret")
        val collectJob = launch { vm.bodyweightSummary.collect {} }
        val unitJob = launch { vm.weightUnit.collect {} }
        val reminderJob = launch { vm.nextWorkoutReminderPlan.collect {} }

        repo.routinesFlow.value = listOf(
            WorkoutRoutine(id = "r1", name = "Full Body A", scheduledDays = listOf(1, 2, 3, 4, 5, 6, 7))
        )
        advanceUntilIdle()

        vm.updateSettingsPreferences(
            weightUnit = WeightUnit.KG,
            workoutRemindersEnabled = true,
            workoutReminderHour = 7,
            workoutReminderMinute = 30
        )
        advanceUntilIdle()

        assertTrue(vm.workoutRemindersEnabled.value)
        assertEquals(7, vm.workoutReminderHour.value)
        assertEquals(30, vm.workoutReminderMinute.value)
        assertNotNull(vm.nextWorkoutReminderPlan.value)

        // Log 80.0 kg -> ~176.37 lbs stored canonically
        vm.logBodyweight(
            displayWeight = 80.0,
            dateString = "2026-04-10",
            bodyFatPercent = 14.5,
            notes = "Morning fasted"
        )
        advanceUntilIdle()

        val summaryAfterLog = vm.bodyweightSummary.value
        assertEquals(1, summaryAfterLog.entriesDescending.size)
        assertEquals(176.37, summaryAfterLog.latestWeightLbs!!, 0.15)
        assertEquals(14.5, summaryAfterLog.entriesDescending.first().bodyFatPercent!!, 0.01)

        vm.deleteBodyweightEntry("2026-04-10")
        advanceUntilIdle()
        assertTrue(vm.bodyweightSummary.value.entriesDescending.isEmpty())

        collectJob.cancel()
        unitJob.cancel()
        reminderJob.cancel()
    }
}
