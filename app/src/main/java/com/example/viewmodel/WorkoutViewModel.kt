package com.example.viewmodel

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.analytics.BodyweightAnalyticsHelper
import com.example.data.analytics.BodyweightSummary
import com.example.data.analytics.DashboardTimeframe
import com.example.data.analytics.ExerciseHistoricalBest
import com.example.data.analytics.ExerciseLastPerformance
import com.example.data.analytics.MuscleGroupStat
import com.example.data.analytics.VolumeMilestone
import com.example.data.analytics.WeightUnit
import com.example.data.analytics.WeightUnitHelper
import com.example.data.analytics.WorkoutAnalyticsHelper
import com.example.data.export.WorkoutCsvExporter
import com.example.data.garmin.GarminExportTracker
import com.example.data.garmin.GarminFitExporter
import com.example.data.model.BodyweightEntry
import com.example.data.model.DailyNutrition
import com.example.data.model.ExerciseRecord
import com.example.data.model.ExerciseSet
import com.example.data.model.ExerciseTrackingType
import com.example.data.model.MealEntry
import com.example.data.model.MealPreset
import com.example.data.model.ProgressDataPoint
import com.example.data.model.UserProfile
import com.example.data.model.WorkoutExerciseLog
import com.example.data.model.WorkoutRoutine
import com.example.data.model.WorkoutSession
import com.example.data.onboarding.FitnessGoal
import com.example.data.onboarding.TrainingSplit
import com.example.data.repository.LoggedExerciseData
import com.example.data.repository.WorkoutDataRepository
import com.example.data.schedule.RoutineSuggestion
import com.example.data.schedule.Weekday
import com.example.data.schedule.WeeklyScheduleSummary
import com.example.data.schedule.WorkoutScheduleHelper
import com.example.data.share.WorkoutShareCardGenerator
import com.example.data.sheets.GoogleSheetsAuthManager
import com.example.data.sheets.GoogleSheetsSyncRepository
import com.example.notifications.RestTimerNotificationAction
import com.example.notifications.RestTimerNotificationHelper
import com.example.notifications.WorkoutReminderHelper
import com.example.notifications.WorkoutReminderPlan
import com.google.firebase.Firebase
import com.google.firebase.crashlytics.crashlytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class ActiveExerciseState(
    val exerciseId: String,
    val exerciseName: String,
    val category: String,
    val trackingType: ExerciseTrackingType = ExerciseTrackingType.WEIGHTED,
    val sets: List<ExerciseSet> = listOf(
        ExerciseSet(setNumber = 1, weightLbs = 135.0, reps = 8, completed = false),
        ExerciseSet(setNumber = 2, weightLbs = 135.0, reps = 8, completed = false),
        ExerciseSet(setNumber = 3, weightLbs = 135.0, reps = 8, completed = false)
    )
)

sealed interface SheetSyncUiState {
    data object Idle : SheetSyncUiState
    data object Syncing : SheetSyncUiState
    data class Success(val message: String, val spreadsheetUrl: String) : SheetSyncUiState
    data class Error(val message: String) : SheetSyncUiState
}

sealed interface GarminExportUiState {
    data object Idle : GarminExportUiState
    data object Exporting : GarminExportUiState
    data class Ready(
        val file: File,
        val displayTitle: String,
        val isZip: Boolean,
        val exportedCount: Int,
        val skippedDuplicatesCount: Int,
        val mimeType: String
    ) : GarminExportUiState
    data class Error(val message: String) : GarminExportUiState
}

sealed interface SocialShareUiState {
    data object Idle : SocialShareUiState
    data object Generating : SocialShareUiState
    data class Ready(
        val workoutName: String,
        val imageFile: File,
        val caption: String
    ) : SocialShareUiState
    data class Error(val message: String) : SocialShareUiState
}

sealed interface CsvExportUiState {
    data object Idle : CsvExportUiState
    data object Exporting : CsvExportUiState
    data class Ready(
        val file: File,
        val workoutCount: Int,
        val rowCount: Int
    ) : CsvExportUiState
    data class Error(val message: String) : CsvExportUiState
}

class WorkoutViewModel(
    private val repository: WorkoutDataRepository,
    private val currentUserId: String,
    private val sheetsRepository: GoogleSheetsSyncRepository = GoogleSheetsSyncRepository(),
    private val appContext: Context? = null
) : ViewModel() {

    private val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val todayDateString: String = sdfDate.format(Date())

    // Initialize initial seed data if new user
    init {
        viewModelScope.launch {
            repository.seedInitialDataIfNeeded(currentUserId)
        }
    }

    val userProfile: StateFlow<UiState<UserProfile>> = repository.observeUserProfile(currentUserId)
        .map<UserProfile, UiState<UserProfile>> { UiState.Success(it) }
        .catch { emit(UiState.Error(it.message ?: "Failed to load profile")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Loading)

    val weightUnit: StateFlow<WeightUnit> = userProfile.map { state ->
        (state as? UiState.Success)?.data?.resolvedWeightUnit() ?: WeightUnit.LBS
    }.stateIn(viewModelScope, SharingStarted.Eagerly, WeightUnit.LBS)

    val defaultRestSeconds: StateFlow<Int> = userProfile.map { state ->
        ((state as? UiState.Success)?.data?.defaultRestSeconds ?: 90).coerceIn(15, 300)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 90)

    val restNotificationsEnabled: StateFlow<Boolean> = userProfile.map { state ->
        (state as? UiState.Success)?.data?.restNotificationsEnabled ?: true
    }.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val firstDayOfWeek: StateFlow<Weekday> = userProfile.map { state ->
        (state as? UiState.Success)?.data?.resolvedFirstDayOfWeek() ?: Weekday.MONDAY
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Weekday.MONDAY)

    val workoutRemindersEnabled: StateFlow<Boolean> = userProfile.map { state ->
        (state as? UiState.Success)?.data?.workoutRemindersEnabled ?: false
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val workoutReminderHour: StateFlow<Int> = userProfile.map { state ->
        ((state as? UiState.Success)?.data?.workoutReminderHour ?: 18).coerceIn(0, 23)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 18)

    val workoutReminderMinute: StateFlow<Int> = userProfile.map { state ->
        ((state as? UiState.Success)?.data?.workoutReminderMinute ?: 0).coerceIn(0, 59)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    init {
        viewModelScope.launch {
            userProfile.collect { state ->
                if (state is UiState.Success) {
                    val profile = state.data
                    if (!_isWorkoutActive.value && !_isRestTimerRunning.value) {
                        _restTimerInitialDuration.value = profile.defaultRestSeconds.coerceIn(15, 300)
                    }
                    runCatching {
                        val crashlytics = Firebase.crashlytics
                        crashlytics.setCustomKey("weight_unit", profile.resolvedWeightUnit().id)
                        crashlytics.setCustomKey("fitness_goal", profile.resolvedFitnessGoal().id)
                        crashlytics.setCustomKey("training_split", profile.resolvedTrainingSplit().id)
                        crashlytics.setCustomKey("first_day_of_week", profile.resolvedFirstDayOfWeek().shortLabel)
                    }
                }
            }
        }
    }

    val workouts: StateFlow<UiState<List<WorkoutSession>>> = repository.observeWorkouts(currentUserId)
        .map<List<WorkoutSession>, UiState<List<WorkoutSession>>> { UiState.Success(it) }
        .catch { emit(UiState.Error(it.message ?: "Failed to load workouts")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Loading)

    val exercises: StateFlow<UiState<List<ExerciseRecord>>> = repository.observeExerciseRecords(currentUserId)
        .map<List<ExerciseRecord>, UiState<List<ExerciseRecord>>> { UiState.Success(it) }
        .catch { emit(UiState.Error(it.message ?: "Failed to load exercises")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Loading)

    val routines: StateFlow<UiState<List<WorkoutRoutine>>> = repository.observeRoutines(currentUserId)
        .map<List<WorkoutRoutine>, UiState<List<WorkoutRoutine>>> { UiState.Success(it) }
        .catch { emit(UiState.Error(it.message ?: "Failed to load routines")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Loading)

    val logs: StateFlow<UiState<List<WorkoutExerciseLog>>> = repository.observeLogs(currentUserId)
        .map<List<WorkoutExerciseLog>, UiState<List<WorkoutExerciseLog>>> { UiState.Success(it) }
        .catch { emit(UiState.Error(it.message ?: "Failed to load logs")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Loading)

    val todayNutrition: StateFlow<DailyNutrition?> = repository.observeNutrition(currentUserId, todayDateString)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val nutritionHistory: StateFlow<List<DailyNutrition>> = repository.observeNutritionHistory(currentUserId)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bodyweightLogs: StateFlow<List<BodyweightEntry>> = repository.observeBodyweightLogs(currentUserId)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bodyweightSummary: StateFlow<BodyweightSummary> = combine(
        bodyweightLogs,
        userProfile,
        weightUnit
    ) { entries, profileState, unit ->
        val goal = (profileState as? UiState.Success)?.data?.resolvedFitnessGoal() ?: FitnessGoal.MAINTAIN
        BodyweightAnalyticsHelper.computeBodyweightSummary(
            entries = entries,
            goal = goal,
            unit = unit
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        BodyweightAnalyticsHelper.computeBodyweightSummary(emptyList())
    )

    private val _selectedNutritionDate = MutableStateFlow(todayDateString)
    val selectedNutritionDate: StateFlow<String> = _selectedNutritionDate.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedDayNutrition: StateFlow<DailyNutrition?> = _selectedNutritionDate
        .flatMapLatest { dateStr -> repository.observeNutrition(currentUserId, dateStr) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setSelectedNutritionDate(dateString: String) {
        if (dateString.isNotBlank()) {
            _selectedNutritionDate.value = dateString
        }
    }

    fun shiftSelectedNutritionDate(daysDelta: Int) {
        val parsed = try {
            sdfDate.parse(_selectedNutritionDate.value)
        } catch (_: Exception) {
            null
        } ?: Date()
        val cal = Calendar.getInstance().apply {
            time = parsed
            add(Calendar.DAY_OF_YEAR, daysDelta)
        }
        _selectedNutritionDate.value = sdfDate.format(cal.time)
    }

    val computedWeeklyStreak: StateFlow<Int> = combine(
        workouts,
        firstDayOfWeek
    ) { workoutsState, startDay ->
        val list = (workoutsState as? UiState.Success)?.data ?: emptyList()
        WorkoutAnalyticsHelper.computeWeeklyStreak(list, firstDayOfWeek = startDay)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val weeklySchedule: StateFlow<WeeklyScheduleSummary> = combine(
        routines,
        workouts,
        firstDayOfWeek
    ) { routinesState, workoutsState, startDay ->
        val routineList = (routinesState as? UiState.Success)?.data ?: emptyList()
        val workoutList = (workoutsState as? UiState.Success)?.data ?: emptyList()
        WorkoutScheduleHelper.buildWeeklySchedule(
            routines = routineList,
            workouts = workoutList,
            referenceDate = Date(),
            firstDayOfWeek = startDay
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        WorkoutScheduleHelper.buildWeeklySchedule(emptyList(), emptyList(), Date(), Weekday.MONDAY)
    )

    fun suggestRoutineForDate(date: Date = Date()): RoutineSuggestion {
        val routineList = (routines.value as? UiState.Success)?.data ?: emptyList()
        return WorkoutScheduleHelper.suggestRoutineForDate(routineList, date)
    }

    val nextWorkoutReminderPlan: StateFlow<WorkoutReminderPlan?> = combine(
        routines,
        workouts,
        workoutRemindersEnabled,
        workoutReminderHour,
        workoutReminderMinute
    ) { routinesState, workoutsState, enabled, hour, minute ->
        if (!enabled) {
            null
        } else {
            val routineList = (routinesState as? UiState.Success)?.data ?: emptyList()
            val workoutList = (workoutsState as? UiState.Success)?.data ?: emptyList()
            WorkoutReminderHelper.computeNextReminderPlan(
                routines = routineList,
                workouts = workoutList,
                hour = hour,
                minute = minute
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        if (appContext != null) {
            viewModelScope.launch {
                combine(
                    routines,
                    workouts,
                    workoutRemindersEnabled,
                    workoutReminderHour,
                    workoutReminderMinute
                ) { routinesState, workoutsState, enabled, hour, minute ->
                    Triple(
                        (routinesState as? UiState.Success)?.data ?: emptyList(),
                        (workoutsState as? UiState.Success)?.data ?: emptyList(),
                        Triple(enabled, hour, minute)
                    )
                }.collect { (routineList, workoutList, prefs) ->
                    val (enabled, hour, minute) = prefs
                    WorkoutReminderHelper.scheduleNextWorkoutReminder(
                        context = appContext,
                        routines = routineList,
                        workouts = workoutList,
                        enabled = enabled,
                        hour = hour,
                        minute = minute,
                        weeklyStreak = computedWeeklyStreak.value
                    )
                }
            }
        }
    }

    // Selected Exercise for Progression Charts
    private val _selectedExerciseId = MutableStateFlow("bench_press")
    val selectedExerciseId: StateFlow<String> = _selectedExerciseId.asStateFlow()

    fun setSelectedExercise(exerciseId: String) {
        _selectedExerciseId.value = exerciseId
    }

    // Active Workout State
    private val _isWorkoutActive = MutableStateFlow(false)
    val isWorkoutActive: StateFlow<Boolean> = _isWorkoutActive.asStateFlow()

    private val _activeWorkoutName = MutableStateFlow("Push Day A")
    val activeWorkoutName: StateFlow<String> = _activeWorkoutName.asStateFlow()

    private val _activeWorkoutDate = MutableStateFlow<Date?>(null)
    val activeWorkoutDate: StateFlow<Date?> = _activeWorkoutDate.asStateFlow()

    private val _editingWorkoutId = MutableStateFlow<String?>(null)
    val editingWorkoutId: StateFlow<String?> = _editingWorkoutId.asStateFlow()

    private val _editingWorkoutNotes = MutableStateFlow("")
    val editingWorkoutNotes: StateFlow<String> = _editingWorkoutNotes.asStateFlow()

    fun setActiveWorkoutDate(date: Date?) {
        _activeWorkoutDate.value = date
    }

    fun updateActiveWorkoutName(name: String) {
        if (name.isNotBlank()) {
            _activeWorkoutName.value = name
        }
    }

    fun updateActiveWorkoutDurationMinutes(minutes: Int) {
        _workoutDurationSeconds.value = maxOf(1, minutes) * 60
    }

    private val _activeExercises = MutableStateFlow<List<ActiveExerciseState>>(emptyList())
    val activeExercises: StateFlow<List<ActiveExerciseState>> = _activeExercises.asStateFlow()

    private val _workoutDurationSeconds = MutableStateFlow(0)
    val workoutDurationSeconds: StateFlow<Int> = _workoutDurationSeconds.asStateFlow()

    private val _isWorkoutTimerPaused = MutableStateFlow(false)
    val isWorkoutTimerPaused: StateFlow<Boolean> = _isWorkoutTimerPaused.asStateFlow()

    private var workoutTimerJob: Job? = null

    private fun startWorkoutTimerLoop() {
        workoutTimerJob?.cancel()
        _isWorkoutTimerPaused.value = false
        workoutTimerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _workoutDurationSeconds.value += 1
            }
        }
    }

    fun pauseWorkoutTimer() {
        if (_isWorkoutActive.value && _editingWorkoutId.value == null) {
            workoutTimerJob?.cancel()
            workoutTimerJob = null
            _isWorkoutTimerPaused.value = true
        }
    }

    fun resumeWorkoutTimer() {
        if (_isWorkoutActive.value && _editingWorkoutId.value == null && _isWorkoutTimerPaused.value) {
            startWorkoutTimerLoop()
        }
    }

    fun toggleWorkoutTimerPaused() {
        if (_isWorkoutTimerPaused.value) {
            resumeWorkoutTimer()
        } else {
            pauseWorkoutTimer()
        }
    }

    // Rest Timer State
    private val _restTimerRemainingSeconds = MutableStateFlow(0)
    val restTimerRemainingSeconds: StateFlow<Int> = _restTimerRemainingSeconds.asStateFlow()

    private val _isRestTimerRunning = MutableStateFlow(false)
    val isRestTimerRunning: StateFlow<Boolean> = _isRestTimerRunning.asStateFlow()

    private val _restTimerInitialDuration = MutableStateFlow(90)
    val restTimerInitialDuration: StateFlow<Int> = _restTimerInitialDuration.asStateFlow()

    private val _restTimerCompletedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val restTimerCompletedEvent: SharedFlow<Unit> = _restTimerCompletedEvent.asSharedFlow()

    private var restTimerJob: Job? = null
    private var restEndElapsedMs: Long = 0L

    fun setRestTimerDurationSeconds(seconds: Int) {
        _restTimerInitialDuration.value = seconds.coerceIn(5, 900)
    }

    fun setWeightUnit(unit: WeightUnit) {
        viewModelScope.launch {
            repository.updateUserPreferences(userId = currentUserId, weightUnit = unit)
        }
    }

    fun updateSettingsPreferences(
        weightUnit: WeightUnit? = null,
        defaultRestSeconds: Int? = null,
        restNotificationsEnabled: Boolean? = null,
        firstDayOfWeek: Weekday? = null,
        workoutRemindersEnabled: Boolean? = null,
        workoutReminderHour: Int? = null,
        workoutReminderMinute: Int? = null
    ) {
        if (defaultRestSeconds != null && !_isRestTimerRunning.value) {
            _restTimerInitialDuration.value = defaultRestSeconds.coerceIn(15, 300)
        }
        viewModelScope.launch {
            repository.updateUserPreferences(
                userId = currentUserId,
                weightUnit = weightUnit,
                defaultRestSeconds = defaultRestSeconds,
                restNotificationsEnabled = restNotificationsEnabled,
                firstDayOfWeek = firstDayOfWeek,
                workoutRemindersEnabled = workoutRemindersEnabled,
                workoutReminderHour = workoutReminderHour,
                workoutReminderMinute = workoutReminderMinute
            )
        }
    }

    fun logBodyweight(
        displayWeight: Double,
        dateString: String = _selectedNutritionDate.value,
        bodyFatPercent: Double? = null,
        notes: String = ""
    ) {
        if (displayWeight <= 0.0) return
        val weightLbs = WeightUnitHelper.toLbs(displayWeight, weightUnit.value)
        viewModelScope.launch {
            repository.logBodyweight(
                userId = currentUserId,
                dateString = dateString.ifBlank { todayDateString },
                weightLbs = weightLbs,
                bodyFatPercent = bodyFatPercent,
                notes = notes
            )
        }
    }

    fun deleteBodyweightEntry(dateString: String) {
        viewModelScope.launch {
            repository.deleteBodyweightEntry(currentUserId, dateString)
        }
    }

    fun completeOnboarding(
        goal: FitnessGoal,
        split: TrainingSplit,
        weightUnit: WeightUnit,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.completeOnboarding(
                userId = currentUserId,
                goal = goal,
                split = split,
                weightUnit = weightUnit
            )
            onDone()
        }
    }

    fun dismissOnboarding() {
        viewModelScope.launch {
            repository.updateUserPreferences(
                userId = currentUserId,
                onboardingCompleted = true
            )
        }
    }

    // Progression Chart Data for currently selected exercise
    val selectedExerciseProgressPoints: StateFlow<List<ProgressDataPoint>> = combine(
        logs,
        exercises,
        _selectedExerciseId
    ) { logsState, exercisesState, exerciseId ->
        if (logsState is UiState.Success) {
            val selectedRecord = (exercisesState as? UiState.Success)?.data?.find { it.id == exerciseId }
            val fallbackTracking = selectedRecord?.resolvedTrackingType() ?: ExerciseTrackingType.WEIGHTED
            logsState.data
                .filter { log ->
                    if (selectedRecord != null) {
                        selectedRecord.matchesLog(log)
                    } else {
                        log.exerciseId == exerciseId
                    }
                }
                .sortedBy { it.date?.toDate()?.time ?: 0L }
                .mapIndexed { index, log ->
                    val parsedSets = log.sets.map { ExerciseSet.fromMap(it) }
                    val weight = if (log.maxWeightLbs > 0.0) {
                        log.maxWeightLbs
                    } else {
                        parsedSets.maxOfOrNull { it.weightLbs } ?: 0.0
                    }
                    val reps = parsedSets.maxOfOrNull { it.reps } ?: 8
                    val totalReps = if (log.totalReps > 0) log.totalReps else parsedSets.sumOf { it.reps }
                    val bestDur = maxOf(
                        log.bestDurationSeconds,
                        parsedSets.maxOfOrNull { it.durationSeconds } ?: 0
                    )
                    val resolvedType = if (log.trackingType.isNotBlank()) log.resolvedTrackingType() else fallbackTracking
                    val e1RM = if (log.estimatedOneRepMaxLbs > 0) log.estimatedOneRepMaxLbs else (weight * (1.0 + reps / 30.0))
                    ProgressDataPoint(
                        label = "S${index + 1}",
                        weightLbs = weight,
                        reps = reps,
                        estimated1RM = Math.round(e1RM * 10.0) / 10.0,
                        date = log.dateString.ifEmpty { "Session ${index + 1}" },
                        trackingType = resolvedType,
                        durationSeconds = bestDur,
                        totalReps = totalReps
                    )
                }
        } else {
            emptyList()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Volume Distribution across muscle categories (includes Core)
    val muscleGroupVolume: StateFlow<Map<String, Double>> = logs.map { logsState ->
        if (logsState is UiState.Success) {
            val map = linkedMapOf(
                "Chest" to 0.0,
                "Back" to 0.0,
                "Legs" to 0.0,
                "Shoulders" to 0.0,
                "Arms" to 0.0,
                "Core" to 0.0
            )
            logsState.data.forEach { log ->
                val cat = log.category.ifEmpty { "Chest" }
                map[cat] = (map[cat] ?: 0.0) + log.totalVolumeLbs
            }
            map
        } else {
            emptyMap()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val muscleGroupFocus: StateFlow<List<MuscleGroupStat>> = logs.map { logsState ->
        val logList = (logsState as? UiState.Success)?.data ?: emptyList()
        WorkoutAnalyticsHelper.computeMuscleGroupFocus(logList)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val volumeMilestones: StateFlow<List<VolumeMilestone>> = combine(
        workouts,
        firstDayOfWeek
    ) { workoutsState, startDay ->
        val list = (workoutsState as? UiState.Success)?.data ?: emptyList()
        WorkoutAnalyticsHelper.buildVolumeMilestones(list, firstDayOfWeek = startDay)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lastSessionByExerciseId: StateFlow<Map<String, ExerciseLastPerformance>> = combine(
        logs,
        exercises,
        _editingWorkoutId,
        weightUnit
    ) { logsState, exercisesState, editingId, unit ->
        val logList = (logsState as? UiState.Success)?.data ?: emptyList()
        val exList = (exercisesState as? UiState.Success)?.data ?: emptyList()
        WorkoutAnalyticsHelper.computeLastPerformanceByExercise(
            logs = logList,
            exercises = exList,
            excludeWorkoutId = editingId,
            unit = unit
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val historicalBestByExerciseId: StateFlow<Map<String, ExerciseHistoricalBest>> = combine(
        logs,
        exercises,
        _editingWorkoutId
    ) { logsState, exercisesState, editingId ->
        val logList = (logsState as? UiState.Success)?.data ?: emptyList()
        val exList = (exercisesState as? UiState.Success)?.data ?: emptyList()
        WorkoutAnalyticsHelper.computeHistoricalBestByExercise(
            logs = logList,
            exercises = exList,
            excludeWorkoutId = editingId
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun startWorkout(
        routineName: String = "Push Day A",
        targetDate: Date? = null,
        restSeconds: Int? = null
    ) {
        _editingWorkoutId.value = null
        _editingWorkoutNotes.value = ""
        _activeWorkoutName.value = routineName
        _activeWorkoutDate.value = targetDate
        _restTimerInitialDuration.value = (restSeconds ?: defaultRestSeconds.value).coerceIn(5, 900)
        _workoutDurationSeconds.value = 0
        _isWorkoutActive.value = true

        val allExercises = (exercises.value as? UiState.Success)?.data ?: emptyList()
        val defaultList = buildRoutineExercises(routineName, allExercises)
        _activeExercises.value = defaultList

        startWorkoutTimerLoop()
    }

    fun startWorkoutWithRoutine(
        routine: WorkoutRoutine,
        targetDate: Date? = null,
        restSecondsOverride: Int? = null
    ) {
        _editingWorkoutId.value = null
        _editingWorkoutNotes.value = ""
        _activeWorkoutName.value = routine.name
        _activeWorkoutDate.value = targetDate
        _restTimerInitialDuration.value = (restSecondsOverride ?: routine.restSeconds).coerceIn(5, 900)
        _workoutDurationSeconds.value = 0
        _isWorkoutActive.value = true

        val allExercises = (exercises.value as? UiState.Success)?.data ?: emptyList()
        val selected = routine.exerciseIds.mapNotNull { id ->
            allExercises.find { it.id == id }
        }

        val isPast = targetDate != null
        val activeList = if (selected.isNotEmpty()) {
            selected.map { ex ->
                ActiveExerciseState(
                    exerciseId = ex.id,
                    exerciseName = ex.name,
                    category = ex.category,
                    trackingType = ex.resolvedTrackingType(),
                    sets = buildSetsForExercise(ex, completed = isPast)
                )
            }
        } else {
            buildRoutineExercises(routine.name, allExercises)
        }

        _activeExercises.value = activeList

        startWorkoutTimerLoop()
    }

    fun startCustomWorkout(
        workoutName: String,
        selectedExercises: List<ExerciseRecord>,
        targetDate: Date? = null,
        durationMinutes: Int = 45,
        restSeconds: Int? = null
    ) {
        _editingWorkoutId.value = null
        _editingWorkoutNotes.value = ""
        _activeWorkoutName.value = workoutName.ifBlank { "Custom Workout" }
        _activeWorkoutDate.value = targetDate
        _restTimerInitialDuration.value = (restSeconds ?: defaultRestSeconds.value).coerceIn(5, 900)
        _workoutDurationSeconds.value = if (targetDate != null) durationMinutes * 60 else 0
        _isWorkoutActive.value = true

        val isPast = targetDate != null
        _activeExercises.value = selectedExercises.map { ex ->
            ActiveExerciseState(
                exerciseId = ex.id,
                exerciseName = ex.name,
                category = ex.category,
                trackingType = ex.resolvedTrackingType(),
                sets = buildSetsForExercise(ex, completed = isPast)
            )
        }

        workoutTimerJob?.cancel()
        _isWorkoutTimerPaused.value = false
        if (!isPast) {
            startWorkoutTimerLoop()
        }
    }

    fun startEditingWorkout(workout: WorkoutSession) {
        _editingWorkoutId.value = workout.id
        _editingWorkoutNotes.value = workout.notes
        _activeWorkoutName.value = workout.name
        _activeWorkoutDate.value = workout.createdAt?.toDate() ?: Date()
        _restTimerInitialDuration.value = workout.restSeconds.coerceIn(5, 900)
        _workoutDurationSeconds.value = maxOf(1, workout.durationMinutes) * 60
        _isWorkoutTimerPaused.value = false
        _isWorkoutActive.value = true

        val allLogs = (logs.value as? UiState.Success)?.data ?: emptyList()
        val allExercises = (exercises.value as? UiState.Success)?.data ?: emptyList()
        val matchingLogs = allLogs.filter { it.workoutId == workout.id }

        val mappedExercises = if (matchingLogs.isNotEmpty()) {
            matchingLogs.map { log ->
                val catalogMatch = allExercises.find { it.id == log.exerciseId }
                val resolvedTracking = if (log.trackingType.isNotBlank() && log.trackingType != ExerciseTrackingType.WEIGHTED.id) {
                    log.resolvedTrackingType()
                } else {
                    catalogMatch?.resolvedTrackingType() ?: log.resolvedTrackingType()
                }
                val parsedSets = log.sets.mapIndexed { idx, map ->
                    val s = ExerciseSet.fromMap(map)
                    if (s.setNumber <= 0) s.copy(setNumber = idx + 1) else s
                }.ifEmpty {
                    listOf(
                        ExerciseSet(
                            setNumber = 1,
                            weightLbs = log.maxWeightLbs,
                            reps = if (resolvedTracking == ExerciseTrackingType.TIMED_HOLD) 1 else 8,
                            completed = true,
                            durationSeconds = log.bestDurationSeconds
                        )
                    )
                }
                ActiveExerciseState(
                    exerciseId = log.exerciseId,
                    exerciseName = log.exerciseName,
                    category = log.category,
                    trackingType = resolvedTracking,
                    sets = parsedSets
                )
            }
        } else {
            emptyList()
        }

        _activeExercises.value = mappedExercises
        workoutTimerJob?.cancel()
    }

    fun buildSetsForExercise(exercise: ExerciseRecord, completed: Boolean = false): List<ExerciseSet> {
        val setCount = exercise.defaultSets.coerceAtLeast(1)
        val repCount = exercise.defaultReps.coerceAtLeast(1)
        val tracking = exercise.resolvedTrackingType()
        val durationSec = if (tracking == ExerciseTrackingType.TIMED_HOLD) {
            exercise.defaultDurationSeconds.coerceAtLeast(5)
        } else {
            0
        }
        return (1..setCount).map { setNum ->
            ExerciseSet(
                setNumber = setNum,
                weightLbs = exercise.currentMaxWeightLbs.coerceAtLeast(0.0),
                reps = if (tracking == ExerciseTrackingType.TIMED_HOLD) 1 else repCount,
                completed = completed,
                durationSeconds = durationSec
            )
        }
    }

    private fun buildRoutineExercises(routineName: String, allExercises: List<ExerciseRecord>): List<ActiveExerciseState> {
        return when {
            routineName.contains("Push", ignoreCase = true) -> {
                val bench = allExercises.find { it.id == "bench_press" }
                val incline = allExercises.find { it.id == "incline_dumbbell_press" }
                val tricep = allExercises.find { it.id == "tricep_pushdown" }
                listOf(
                    ActiveExerciseState(
                        exerciseId = bench?.id ?: "bench_press",
                        exerciseName = bench?.name ?: "Barbell Bench Press",
                        category = bench?.category ?: "Chest",
                        sets = bench?.let { buildSetsForExercise(it) } ?: listOf(
                            ExerciseSet(1, 150.0, 8, false),
                            ExerciseSet(2, 150.0, 8, false),
                            ExerciseSet(3, 150.0, 8, false)
                        )
                    ),
                    ActiveExerciseState(
                        exerciseId = incline?.id ?: "incline_dumbbell_press",
                        exerciseName = incline?.name ?: "Incline Dumbbell Press",
                        category = incline?.category ?: "Chest",
                        sets = incline?.let { buildSetsForExercise(it) } ?: listOf(
                            ExerciseSet(1, 60.0, 8, false),
                            ExerciseSet(2, 60.0, 8, false),
                            ExerciseSet(3, 60.0, 8, false)
                        )
                    ),
                    ActiveExerciseState(
                        exerciseId = tricep?.id ?: "tricep_pushdown",
                        exerciseName = tricep?.name ?: "Tricep Rope Pushdown",
                        category = tricep?.category ?: "Arms",
                        sets = tricep?.let { buildSetsForExercise(it) } ?: listOf(
                            ExerciseSet(1, 52.5, 10, false),
                            ExerciseSet(2, 52.5, 10, false),
                            ExerciseSet(3, 52.5, 10, false)
                        )
                    )
                )
            }
            routineName.contains("Pull", ignoreCase = true) -> {
                val deadlift = allExercises.find { it.id == "barbell_deadlift" }
                val row = allExercises.find { it.id == "cable_row" }
                val curl = allExercises.find { it.id == "bicep_curl" }
                listOf(
                    ActiveExerciseState(
                        exerciseId = deadlift?.id ?: "barbell_deadlift",
                        exerciseName = deadlift?.name ?: "Barbell Deadlift",
                        category = deadlift?.category ?: "Back",
                        sets = deadlift?.let { buildSetsForExercise(it) } ?: listOf(
                            ExerciseSet(1, 245.0, 6, false),
                            ExerciseSet(2, 245.0, 6, false),
                            ExerciseSet(3, 245.0, 6, false)
                        )
                    ),
                    ActiveExerciseState(
                        exerciseId = row?.id ?: "cable_row",
                        exerciseName = row?.name ?: "Seated Cable Row",
                        category = row?.category ?: "Back",
                        sets = row?.let { buildSetsForExercise(it) } ?: listOf(
                            ExerciseSet(1, 130.0, 8, false),
                            ExerciseSet(2, 130.0, 8, false),
                            ExerciseSet(3, 130.0, 8, false)
                        )
                    ),
                    ActiveExerciseState(
                        exerciseId = curl?.id ?: "bicep_curl",
                        exerciseName = curl?.name ?: "Dumbbell Bicep Curl",
                        category = curl?.category ?: "Arms",
                        sets = curl?.let { buildSetsForExercise(it) } ?: listOf(
                            ExerciseSet(1, 32.5, 10, false),
                            ExerciseSet(2, 32.5, 10, false),
                            ExerciseSet(3, 32.5, 10, false)
                        )
                    )
                )
            }
            else -> {
                val squat = allExercises.find { it.id == "barbell_squat" }
                listOf(
                    ActiveExerciseState(
                        exerciseId = squat?.id ?: "barbell_squat",
                        exerciseName = squat?.name ?: "Barbell Back Squat",
                        category = squat?.category ?: "Legs",
                        sets = squat?.let { buildSetsForExercise(it) } ?: listOf(
                            ExerciseSet(1, 215.0, 8, false),
                            ExerciseSet(2, 215.0, 8, false),
                            ExerciseSet(3, 215.0, 8, false)
                        )
                    )
                )
            }
        }
    }

    fun addExerciseToWorkout(exercise: ExerciseRecord) {
        val current = _activeExercises.value.toMutableList()
        current.add(
            ActiveExerciseState(
                exerciseId = exercise.id,
                exerciseName = exercise.name,
                category = exercise.category,
                trackingType = exercise.resolvedTrackingType(),
                sets = buildSetsForExercise(exercise, completed = false)
            )
        )
        _activeExercises.value = current
    }

    fun removeExerciseFromWorkout(exerciseIndex: Int) {
        val current = _activeExercises.value.toMutableList()
        if (exerciseIndex in current.indices) {
            current.removeAt(exerciseIndex)
            _activeExercises.value = current
        }
    }

    fun addSet(exerciseIndex: Int) {
        val current = _activeExercises.value.toMutableList()
        if (exerciseIndex in current.indices) {
            val ex = current[exerciseIndex]
            val fallbackWeight = if (ex.trackingType == ExerciseTrackingType.WEIGHTED) 135.0 else 0.0
            val fallbackDur = if (ex.trackingType == ExerciseTrackingType.TIMED_HOLD) 60 else 0
            val lastSet = ex.sets.lastOrNull() ?: ExerciseSet(
                setNumber = 1,
                weightLbs = fallbackWeight,
                reps = if (ex.trackingType == ExerciseTrackingType.TIMED_HOLD) 1 else 8,
                completed = false,
                durationSeconds = fallbackDur
            )
            val newSets = ex.sets.toMutableList()
            newSets.add(
                ExerciseSet(
                    setNumber = ex.sets.size + 1,
                    weightLbs = lastSet.weightLbs,
                    reps = lastSet.reps,
                    completed = false,
                    durationSeconds = lastSet.durationSeconds
                )
            )
            current[exerciseIndex] = ex.copy(sets = newSets)
            _activeExercises.value = current
        }
    }

    fun removeSet(exerciseIndex: Int, setIndex: Int) {
        val current = _activeExercises.value.toMutableList()
        if (exerciseIndex in current.indices) {
            val ex = current[exerciseIndex]
            val newSets = ex.sets.toMutableList()
            if (setIndex in newSets.indices && newSets.size > 1) {
                newSets.removeAt(setIndex)
                val renumbered = newSets.mapIndexed { idx, s -> s.copy(setNumber = idx + 1) }
                current[exerciseIndex] = ex.copy(sets = renumbered)
                _activeExercises.value = current
            }
        }
    }

    fun updateSet(
        exerciseIndex: Int,
        setIndex: Int,
        weight: Double,
        reps: Int,
        completed: Boolean,
        durationSeconds: Int? = null
    ) {
        val current = _activeExercises.value.toMutableList()
        if (exerciseIndex in current.indices) {
            val ex = current[exerciseIndex]
            val newSets = ex.sets.toMutableList()
            if (setIndex in newSets.indices) {
                val existingSet = newSets[setIndex]
                val wasCompleted = existingSet.completed
                newSets[setIndex] = existingSet.copy(
                    weightLbs = weight,
                    reps = reps,
                    completed = completed,
                    durationSeconds = durationSeconds ?: existingSet.durationSeconds
                )
                current[exerciseIndex] = ex.copy(sets = newSets)
                _activeExercises.value = current

                // If user just checked off a completed set, auto-trigger the rest timer!
                if (!wasCompleted && completed) {
                    startRestTimer(_restTimerInitialDuration.value)
                }
            }
        }
    }

    init {
        viewModelScope.launch {
            RestTimerNotificationHelper.actionFlow.collect { action ->
                when (action) {
                    is RestTimerNotificationAction.Extend -> {
                        if (_isRestTimerRunning.value || _restTimerRemainingSeconds.value > 0) {
                            startRestTimer(action.newRemainingSeconds)
                        }
                    }
                    RestTimerNotificationAction.Skip -> {
                        cancelRestTimer()
                    }
                }
            }
        }
    }

    fun startRestTimer(seconds: Int) {
        val clamped = seconds.coerceIn(5, 900)
        _restTimerInitialDuration.value = clamped
        _restTimerRemainingSeconds.value = clamped
        _isRestTimerRunning.value = true

        val ctx = appContext
        if (ctx != null) {
            restEndElapsedMs = SystemClock.elapsedRealtime() + clamped * 1000L
            if (restNotificationsEnabled.value) {
                val workoutLabel = _activeWorkoutName.value.ifBlank { "Workout" }
                RestTimerNotificationHelper.scheduleRestAlarm(
                    context = ctx,
                    delaySeconds = clamped,
                    title = "Rest Complete",
                    body = "Your ${clamped}s rest for $workoutLabel is done — time for your next set!",
                    workoutName = workoutLabel,
                    showLockscreenControls = true
                )
            }
        }

        restTimerJob?.cancel()
        restTimerJob = viewModelScope.launch {
            while (_restTimerRemainingSeconds.value > 0) {
                delay(1000)
                _restTimerRemainingSeconds.value -= 1
            }
            restEndElapsedMs = 0L
            appContext?.let { RestTimerNotificationHelper.cancelRestAlarm(it) }
            _isRestTimerRunning.value = false
            _restTimerCompletedEvent.tryEmit(Unit)
        }
    }

    fun cancelRestTimer() {
        restTimerJob?.cancel()
        restEndElapsedMs = 0L
        appContext?.let { RestTimerNotificationHelper.cancelRestAlarm(it) }
        _isRestTimerRunning.value = false
        _restTimerRemainingSeconds.value = 0
    }

    fun notifyHoldTimerStarted(durationSeconds: Int, exerciseName: String) {
        val ctx = appContext ?: return
        if (!restNotificationsEnabled.value || durationSeconds <= 0) return
        val label = exerciseName.ifBlank { "Timed Hold" }
        RestTimerNotificationHelper.scheduleHoldAlarm(
            context = ctx,
            delaySeconds = durationSeconds,
            title = "$label Hold Complete",
            body = "${durationSeconds}s target reached — log your set!"
        )
    }

    fun notifyHoldTimerCancelled() {
        appContext?.let { RestTimerNotificationHelper.cancelHoldAlarm(it) }
    }

    fun syncRestTimerOnResume() {
        val ctx = appContext ?: return
        if (!_isRestTimerRunning.value || restEndElapsedMs <= 0L) return

        if (RestTimerNotificationHelper.wasPersistedRestSkipped(ctx)) {
            cancelRestTimer()
            return
        }

        val persistedEnd = RestTimerNotificationHelper.readPersistedEndElapsedMs(ctx)
        if (persistedEnd > restEndElapsedMs) {
            restEndElapsedMs = persistedEnd
        }

        val remainingMs = restEndElapsedMs - SystemClock.elapsedRealtime()
        if (remainingMs <= 0L) {
            restTimerJob?.cancel()
            restEndElapsedMs = 0L
            RestTimerNotificationHelper.cancelActiveRestNotification(ctx)
            _isRestTimerRunning.value = false
            _restTimerRemainingSeconds.value = 0
            if (!restNotificationsEnabled.value || !RestTimerNotificationHelper.hasNotificationPermission(ctx)) {
                _restTimerCompletedEvent.tryEmit(Unit)
            }
        } else {
            _restTimerRemainingSeconds.value = ((remainingMs + 999L) / 1000L).toInt().coerceAtLeast(1)
        }
    }

    fun finishWorkout(notes: String = "", customDate: Date? = null, onFinished: (String) -> Unit = {}) {
        val durationMins = maxOf(1, _workoutDurationSeconds.value / 60)
        val finalDate = customDate ?: _activeWorkoutDate.value
        val existingId = _editingWorkoutId.value
        val restSecs = _restTimerInitialDuration.value

        val entries = _activeExercises.value.map { ex ->
            val completedSets = ex.sets.filter { it.completed }
            val setsToLog = if (completedSets.isNotEmpty()) completedSets else ex.sets
            val maxWeight = setsToLog.maxOfOrNull { it.weightLbs } ?: 0.0
            val totalVol = if (ex.trackingType == ExerciseTrackingType.TIMED_HOLD) {
                setsToLog.sumOf { it.weightLbs }
            } else {
                setsToLog.sumOf { it.weightLbs * it.reps }
            }
            val bestSet = setsToLog.maxByOrNull { it.weightLbs * (1.0 + it.reps / 30.0) }
            val best1RM = bestSet?.let { it.weightLbs * (1.0 + it.reps / 30.0) } ?: maxWeight
            val bestDur = setsToLog.maxOfOrNull { it.durationSeconds } ?: 0
            val totalDur = setsToLog.sumOf { it.durationSeconds }
            val totalReps = setsToLog.sumOf { it.reps }

            LoggedExerciseData(
                exerciseId = ex.exerciseId,
                exerciseName = ex.exerciseName,
                category = ex.category,
                sets = setsToLog,
                maxWeight = maxWeight,
                totalVolume = totalVol,
                estimated1RM = Math.round(best1RM * 10.0) / 10.0,
                trackingType = ex.trackingType,
                bestDurationSeconds = bestDur,
                totalDurationSeconds = totalDur,
                totalReps = totalReps
            )
        }

        viewModelScope.launch {
            val resultId = if (existingId != null) {
                repository.updateWorkoutWithExercises(
                    userId = currentUserId,
                    workoutId = existingId,
                    workoutName = _activeWorkoutName.value,
                    durationMinutes = durationMins,
                    notes = notes,
                    exerciseEntries = entries,
                    customDate = finalDate,
                    restSeconds = restSecs
                )
                existingId
            } else {
                repository.saveWorkout(
                    userId = currentUserId,
                    workoutName = _activeWorkoutName.value,
                    durationMinutes = durationMins,
                    notes = notes,
                    exerciseEntries = entries,
                    customDate = finalDate,
                    restSeconds = restSecs
                )
            }
            discardWorkout()
            onFinished(resultId)
            triggerBackgroundSheetSyncIfLinked()
        }
    }

    fun updateWorkoutMetadata(
        workout: WorkoutSession,
        newName: String,
        newDate: Date,
        newDurationMinutes: Int,
        newNotes: String,
        newTotalVolumeLbs: Double,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.updateWorkoutMetadata(
                userId = currentUserId,
                workout = workout,
                newName = newName,
                newDate = newDate,
                newDurationMinutes = newDurationMinutes,
                newNotes = newNotes,
                newTotalVolumeLbs = newTotalVolumeLbs
            )
            onDone()
            triggerBackgroundSheetSyncIfLinked()
        }
    }

    fun deleteWorkout(workout: WorkoutSession, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteWorkout(currentUserId, workout)
            onDone()
            triggerBackgroundSheetSyncIfLinked()
        }
    }

    fun discardWorkout() {
        workoutTimerJob?.cancel()
        workoutTimerJob = null
        cancelRestTimer()
        notifyHoldTimerCancelled()
        _isWorkoutTimerPaused.value = false
        _isWorkoutActive.value = false
        _editingWorkoutId.value = null
        _editingWorkoutNotes.value = ""
        _workoutDurationSeconds.value = 0
        _activeWorkoutDate.value = null
        _activeExercises.value = emptyList()
    }

    // Exercise Management Methods
    fun saveExercise(exercise: ExerciseRecord, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.saveExercise(currentUserId, exercise)
            onDone()
            triggerBackgroundSheetSyncIfLinked()
        }
    }

    fun deleteExercise(exerciseId: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteExercise(currentUserId, exerciseId)
            onDone()
        }
    }

    // Routine Management Methods
    fun saveRoutine(routine: WorkoutRoutine, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.saveRoutine(currentUserId, routine)
            onDone()
        }
    }

    fun deleteRoutine(routineId: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteRoutine(currentUserId, routineId)
            onDone()
        }
    }

    private val nutritionMutex = Mutex()
    private val latestNutritionCache = mutableMapOf<String, DailyNutrition>()

    private fun resolveCurrentDayNutrition(dateString: String): DailyNutrition? {
        return latestNutritionCache[dateString]
            ?: (if (dateString == _selectedNutritionDate.value) selectedDayNutrition.value else null)
            ?: nutritionHistory.value.find { it.dateString == dateString || it.id == dateString }
    }

    fun logNutrition(
        calories: Int,
        protein: Int,
        carbs: Int,
        fats: Int,
        mealName: String = "Meal / Snack",
        dateString: String = _selectedNutritionDate.value
    ) {
        viewModelScope.launch {
            nutritionMutex.withLock {
                val current = resolveCurrentDayNutrition(dateString)
                val newCals = ((current?.calories ?: 0) + calories).coerceAtLeast(0)
                val newP = ((current?.proteinG ?: 0) + protein).coerceAtLeast(0)
                val newC = ((current?.carbsG ?: 0) + carbs).coerceAtLeast(0)
                val newF = ((current?.fatsG ?: 0) + fats).coerceAtLeast(0)
                val timeLabel = SimpleDateFormat("h:mm a", Locale.US).format(Date())
                val entry = MealEntry(
                    id = "meal_${UUID.randomUUID().toString().take(8)}",
                    name = mealName.trim().ifBlank { "Meal / Snack" },
                    calories = calories.coerceAtLeast(0),
                    proteinG = protein.coerceAtLeast(0),
                    carbsG = carbs.coerceAtLeast(0),
                    fatsG = fats.coerceAtLeast(0),
                    loggedAtLabel = timeLabel
                )
                val updatedMeals = current?.parsedMeals().orEmpty() + entry

                latestNutritionCache[dateString] = DailyNutrition(
                    id = dateString,
                    userId = currentUserId,
                    dateString = dateString,
                    calories = newCals,
                    proteinG = newP,
                    carbsG = newC,
                    fatsG = newF,
                    meals = updatedMeals.map { it.toMap() }
                )

                repository.updateNutrition(
                    userId = currentUserId,
                    dateString = dateString,
                    calories = newCals,
                    protein = newP,
                    carbs = newC,
                    fats = newF,
                    meals = updatedMeals
                )
            }
        }
    }

    fun removeMealFromDay(mealId: String, dateString: String = _selectedNutritionDate.value) {
        viewModelScope.launch {
            nutritionMutex.withLock {
                val current = resolveCurrentDayNutrition(dateString) ?: return@withLock
                val existingMeals = current.parsedMeals()
                val target = existingMeals.find { it.id == mealId } ?: return@withLock
                val remainingMeals = existingMeals.filterNot { it.id == mealId }
                val newCals = (current.calories - target.calories).coerceAtLeast(0)
                val newP = (current.proteinG - target.proteinG).coerceAtLeast(0)
                val newC = (current.carbsG - target.carbsG).coerceAtLeast(0)
                val newF = (current.fatsG - target.fatsG).coerceAtLeast(0)

                latestNutritionCache[dateString] = current.copy(
                    calories = newCals,
                    proteinG = newP,
                    carbsG = newC,
                    fatsG = newF,
                    meals = remainingMeals.map { it.toMap() }
                )

                repository.updateNutrition(
                    userId = currentUserId,
                    dateString = dateString,
                    calories = newCals,
                    protein = newP,
                    carbs = newC,
                    fats = newF,
                    meals = remainingMeals
                )
            }
        }
    }

    fun setDailyNutritionTotals(
        calories: Int,
        protein: Int,
        carbs: Int,
        fats: Int,
        dateString: String = _selectedNutritionDate.value
    ) {
        viewModelScope.launch {
            nutritionMutex.withLock {
                val current = resolveCurrentDayNutrition(dateString)
                val existingMeals = current?.parsedMeals().orEmpty()
                val newCals = calories.coerceAtLeast(0)
                val newP = protein.coerceAtLeast(0)
                val newC = carbs.coerceAtLeast(0)
                val newF = fats.coerceAtLeast(0)

                latestNutritionCache[dateString] = DailyNutrition(
                    id = dateString,
                    userId = currentUserId,
                    dateString = dateString,
                    calories = newCals,
                    proteinG = newP,
                    carbsG = newC,
                    fatsG = newF,
                    meals = existingMeals.map { it.toMap() }
                )

                repository.updateNutrition(
                    userId = currentUserId,
                    dateString = dateString,
                    calories = newCals,
                    protein = newP,
                    carbs = newC,
                    fats = newF,
                    meals = existingMeals
                )
            }
        }
    }

    fun resetDailyNutrition(dateString: String = _selectedNutritionDate.value) {
        viewModelScope.launch {
            nutritionMutex.withLock {
                latestNutritionCache[dateString] = DailyNutrition(
                    id = dateString,
                    userId = currentUserId,
                    dateString = dateString,
                    calories = 0,
                    proteinG = 0,
                    carbsG = 0,
                    fatsG = 0,
                    meals = emptyList()
                )
                repository.updateNutrition(
                    userId = currentUserId,
                    dateString = dateString,
                    calories = 0,
                    protein = 0,
                    carbs = 0,
                    fats = 0,
                    meals = emptyList()
                )
            }
        }
    }

    fun saveMealPreset(name: String, calories: Int, protein: Int, carbs: Int, fats: Int) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        viewModelScope.launch {
            val profile = (userProfile.value as? UiState.Success)?.data ?: UserProfile(userId = currentUserId)
            val currentPresets = profile.resolvedMealPresets()
            val newPreset = MealPreset(
                id = "preset_${UUID.randomUUID().toString().take(8)}",
                name = cleanName,
                calories = calories.coerceAtLeast(0),
                proteinG = protein.coerceAtLeast(0),
                carbsG = carbs.coerceAtLeast(0),
                fatsG = fats.coerceAtLeast(0),
                isDefault = false
            )
            repository.saveMealPresets(currentUserId, currentPresets + newPreset)
        }
    }

    fun deleteMealPreset(presetId: String) {
        viewModelScope.launch {
            val profile = (userProfile.value as? UiState.Success)?.data ?: return@launch
            val updated = profile.resolvedMealPresets().filterNot { it.id == presetId }
            repository.saveMealPresets(currentUserId, updated)
        }
    }

    fun updateMacroTargets(targetCalories: Int, minCal: Int, maxCal: Int, protein: Int, carbs: Int, fats: Int) {
        viewModelScope.launch {
            repository.updateMacroTargets(
                userId = currentUserId,
                targetCalories = targetCalories,
                targetMin = minCal,
                targetMax = maxCal,
                proteinG = protein,
                carbsG = carbs,
                fatsG = fats
            )
        }
    }

    // Google Sheets Sync State & Actions
    private val _sheetSyncState = MutableStateFlow<SheetSyncUiState>(SheetSyncUiState.Idle)
    val sheetSyncState: StateFlow<SheetSyncUiState> = _sheetSyncState.asStateFlow()

    fun setSheetSyncError(message: String) {
        _sheetSyncState.value = SheetSyncUiState.Error(message)
    }

    fun clearSheetSyncStatus() {
        _sheetSyncState.value = SheetSyncUiState.Idle
    }

    fun setAutoSyncSheets(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateAutoSyncSheets(currentUserId, enabled)
        }
    }

    fun syncToGoogleSheets(accessToken: String, forceCreateNew: Boolean = false) {
        viewModelScope.launch {
            _sheetSyncState.value = SheetSyncUiState.Syncing
            try {
                val profile = (userProfile.value as? UiState.Success)?.data
                    ?: repository.observeUserProfile(currentUserId).firstOrNull()
                    ?: UserProfile(userId = currentUserId)
                val workoutList = (workouts.value as? UiState.Success)?.data
                    ?: repository.observeWorkouts(currentUserId).firstOrNull()
                    ?: emptyList()
                val logList = (logs.value as? UiState.Success)?.data
                    ?: repository.observeLogs(currentUserId).firstOrNull()
                    ?: emptyList()
                val exerciseList = (exercises.value as? UiState.Success)?.data
                    ?: repository.observeExerciseRecords(currentUserId).firstOrNull()
                    ?: emptyList()
                val nutrition = todayNutrition.value
                val nutritionHist = nutritionHistory.value

                val result = sheetsRepository.createOrSyncSpreadsheet(
                    accessToken = accessToken,
                    existingSpreadsheetId = profile.googleSheetId,
                    forceCreateNew = forceCreateNew,
                    profile = profile,
                    workouts = workoutList,
                    logs = logList,
                    exercises = exerciseList,
                    todayNutrition = nutrition,
                    nutritionHistory = nutritionHist
                )

                repository.updateGoogleSheetMetadata(
                    userId = currentUserId,
                    sheetId = result.spreadsheetId,
                    sheetUrl = result.spreadsheetUrl,
                    lastSyncAt = result.syncedAtLabel
                )

                val verb = if (result.createdNewSheet) "Created Google Sheet" else "Synced Google Sheet"
                val chartNote = if (result.chartCount > 0) " with ${result.chartCount} progress charts" else ""
                _sheetSyncState.value = SheetSyncUiState.Success(
                    message = "$verb$chartNote (${result.syncedAtLabel})",
                    spreadsheetUrl = result.spreadsheetUrl
                )
            } catch (e: Exception) {
                _sheetSyncState.value = SheetSyncUiState.Error(
                    e.localizedMessage ?: "Failed to sync with Google Sheets"
                )
            }
        }
    }

    private fun triggerBackgroundSheetSyncIfLinked() {
        val ctx = appContext ?: return
        val profile = (userProfile.value as? UiState.Success)?.data ?: return
        if (!profile.autoSyncSheets || profile.googleSheetId.isBlank()) return

        viewModelScope.launch {
            // Allow Firestore snapshot listeners a moment to receive updated workout/logs
            delay(500)
            val silentToken = GoogleSheetsAuthManager.tryGetSilentToken(ctx) ?: return@launch
            syncToGoogleSheets(accessToken = silentToken, forceCreateNew = false)
        }
    }

    // Garmin Connect .FIT Export State & Actions
    private val garminTracker: GarminExportTracker? by lazy {
        appContext?.let { GarminExportTracker(it, currentUserId) }
    }

    private val _exportedGarminWorkoutIds = MutableStateFlow(
        appContext?.let { GarminExportTracker(it, currentUserId).getExportedWorkoutIds() } ?: emptySet()
    )
    val exportedGarminWorkoutIds: StateFlow<Set<String>> = _exportedGarminWorkoutIds.asStateFlow()

    private val _lastGarminExportAt = MutableStateFlow(
        appContext?.let { GarminExportTracker(it, currentUserId).getLastExportAt() } ?: ""
    )
    val lastGarminExportAt: StateFlow<String> = _lastGarminExportAt.asStateFlow()

    private val _garminExportState = MutableStateFlow<GarminExportUiState>(GarminExportUiState.Idle)
    val garminExportState: StateFlow<GarminExportUiState> = _garminExportState.asStateFlow()

    fun clearGarminExportStatus() {
        _garminExportState.value = GarminExportUiState.Idle
    }

    fun exportSingleWorkoutToFit(workout: WorkoutSession) {
        val ctx = appContext ?: run {
            _garminExportState.value = GarminExportUiState.Error("Missing application context for file export.")
            return
        }
        viewModelScope.launch {
            _garminExportState.value = GarminExportUiState.Exporting
            try {
                val logList = (logs.value as? UiState.Success)?.data
                    ?: repository.observeLogs(currentUserId).firstOrNull()
                    ?: emptyList()
                val exerciseList = (exercises.value as? UiState.Success)?.data
                    ?: repository.observeExerciseRecords(currentUserId).firstOrNull()
                    ?: emptyList()

                val fitFile = withContext(Dispatchers.IO) {
                    val exportDir = File(ctx.cacheDir, "garmin_exports").apply { mkdirs() }
                    val filename = GarminFitExporter.buildSafeFitFilename(workout)
                    GarminFitExporter.writeWorkoutFitFile(
                        outputFile = File(exportDir, filename),
                        userId = currentUserId,
                        workout = workout,
                        workoutLogs = logList,
                        exerciseCatalog = exerciseList
                    )
                }

                val wasPreviouslyExported = _exportedGarminWorkoutIds.value.contains(workout.id)
                garminTracker?.recordExportedWorkouts(listOf(workout.id))?.let { (ids, label) ->
                    _exportedGarminWorkoutIds.value = ids
                    _lastGarminExportAt.value = label
                }

                _garminExportState.value = GarminExportUiState.Ready(
                    file = fitFile,
                    displayTitle = workout.name.ifBlank { "Strength Workout" },
                    isZip = false,
                    exportedCount = 1,
                    skippedDuplicatesCount = if (wasPreviouslyExported) 1 else 0,
                    mimeType = "application/octet-stream"
                )
            } catch (e: Exception) {
                _garminExportState.value = GarminExportUiState.Error(
                    e.localizedMessage ?: "Failed to generate Garmin .FIT file."
                )
            }
        }
    }

    fun exportAllWorkoutsToGarminZip(onlyNewWorkouts: Boolean = false) {
        val ctx = appContext ?: run {
            _garminExportState.value = GarminExportUiState.Error("Missing application context for file export.")
            return
        }
        viewModelScope.launch {
            _garminExportState.value = GarminExportUiState.Exporting
            try {
                val workoutList = (workouts.value as? UiState.Success)?.data
                    ?: repository.observeWorkouts(currentUserId).firstOrNull()
                    ?: emptyList()
                if (workoutList.isEmpty()) {
                    _garminExportState.value = GarminExportUiState.Error("No workouts logged yet to export.")
                    return@launch
                }

                val logList = (logs.value as? UiState.Success)?.data
                    ?: repository.observeLogs(currentUserId).firstOrNull()
                    ?: emptyList()
                val exerciseList = (exercises.value as? UiState.Success)?.data
                    ?: repository.observeExerciseRecords(currentUserId).firstOrNull()
                    ?: emptyList()

                val alreadyExported = _exportedGarminWorkoutIds.value
                val result = withContext(Dispatchers.IO) {
                    val exportDir = File(ctx.cacheDir, "garmin_exports").apply { mkdirs() }
                    val tempDir = File(exportDir, "tmp").apply { mkdirs() }
                    val dateStamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                    val suffix = if (onlyNewWorkouts) "new" else "all"
                    val zipFile = File(exportDir, "StrengthForge_Garmin_${suffix}_$dateStamp.zip")
                    GarminFitExporter.exportWorkoutsBatchZip(
                        outputZipFile = zipFile,
                        tempDir = tempDir,
                        userId = currentUserId,
                        workouts = workoutList,
                        logs = logList,
                        exercises = exerciseList,
                        onlyUnexported = onlyNewWorkouts,
                        alreadyExportedIds = alreadyExported
                    )
                }

                if (result.exportedWorkoutIds.isEmpty()) {
                    _garminExportState.value = GarminExportUiState.Error(
                        "All ${result.totalRequestedCount} workouts were already exported. Use \"Export All (.ZIP)\" to re-export; Garmin will ignore duplicates."
                    )
                    return@launch
                }

                val previouslyExportedOverlap = result.exportedWorkoutIds.count { alreadyExported.contains(it) }
                garminTracker?.recordExportedWorkouts(result.exportedWorkoutIds)?.let { (ids, label) ->
                    _exportedGarminWorkoutIds.value = ids
                    _lastGarminExportAt.value = label
                }

                _garminExportState.value = GarminExportUiState.Ready(
                    file = result.archiveFile,
                    displayTitle = if (onlyNewWorkouts) {
                        "New Workouts (${result.exportedWorkoutIds.size} .FIT files)"
                    } else {
                        "All Previous Workouts (${result.exportedWorkoutIds.size} .FIT files)"
                    },
                    isZip = true,
                    exportedCount = result.exportedWorkoutIds.size,
                    skippedDuplicatesCount = if (onlyNewWorkouts) result.skippedAlreadyExportedCount else previouslyExportedOverlap,
                    mimeType = "application/zip"
                )
            } catch (e: Exception) {
                _garminExportState.value = GarminExportUiState.Error(
                    e.localizedMessage ?: "Failed to build Garmin .ZIP archive."
                )
            }
        }
    }

    // Social Media Share Card State & Actions
    private val _socialShareState = MutableStateFlow<SocialShareUiState>(SocialShareUiState.Idle)
    val socialShareState: StateFlow<SocialShareUiState> = _socialShareState.asStateFlow()

    fun clearSocialShareStatus() {
        _socialShareState.value = SocialShareUiState.Idle
    }

    fun prepareWorkoutSocialShare(workout: WorkoutSession) {
        val ctx = appContext ?: run {
            _socialShareState.value = SocialShareUiState.Error("Missing application context for image share.")
            return
        }
        viewModelScope.launch {
            _socialShareState.value = SocialShareUiState.Generating
            try {
                val profile = (userProfile.value as? UiState.Success)?.data
                    ?: repository.observeUserProfile(currentUserId).firstOrNull()
                    ?: UserProfile(userId = currentUserId)
                val logList = (logs.value as? UiState.Success)?.data
                    ?: repository.observeLogs(currentUserId).firstOrNull()
                    ?: emptyList()

                val caption = WorkoutShareCardGenerator.buildShareCaption(
                    workout = workout,
                    logs = logList,
                    profile = profile
                )

                val imageFile = withContext(Dispatchers.IO) {
                    val shareDir = File(ctx.cacheDir, "social_shares").apply { mkdirs() }
                    val safeSlug = workout.name
                        .replace(Regex("[^A-Za-z0-9]+"), "_")
                        .trim('_')
                        .take(24)
                        .ifBlank { "Workout" }
                    val file = File(shareDir, "StrengthForge_${safeSlug}_${workout.id.takeLast(6)}.png")
                    WorkoutShareCardGenerator.writeSharePngToFile(
                        outputFile = file,
                        workout = workout,
                        logs = logList,
                        profile = profile
                    )
                }

                _socialShareState.value = SocialShareUiState.Ready(
                    workoutName = workout.name.ifBlank { "Strength Session" },
                    imageFile = imageFile,
                    caption = caption
                )
            } catch (e: Exception) {
                _socialShareState.value = SocialShareUiState.Error(
                    e.localizedMessage ?: "Could not generate share graphic."
                )
            }
        }
    }

    fun prepareSummarySocialShare(timeframe: DashboardTimeframe) {
        val ctx = appContext ?: run {
            _socialShareState.value = SocialShareUiState.Error("Missing application context for image share.")
            return
        }
        viewModelScope.launch {
            _socialShareState.value = SocialShareUiState.Generating
            try {
                val profile = (userProfile.value as? UiState.Success)?.data
                    ?: repository.observeUserProfile(currentUserId).firstOrNull()
                    ?: UserProfile(userId = currentUserId)
                val workoutList = (workouts.value as? UiState.Success)?.data
                    ?: repository.observeWorkouts(currentUserId).firstOrNull()
                    ?: emptyList()
                val logList = (logs.value as? UiState.Success)?.data
                    ?: repository.observeLogs(currentUserId).firstOrNull()
                    ?: emptyList()
                val exerciseList = (exercises.value as? UiState.Success)?.data
                    ?: repository.observeExerciseRecords(currentUserId).firstOrNull()
                    ?: emptyList()
                val routineList = (routines.value as? UiState.Success)?.data
                    ?: repository.observeRoutines(currentUserId).firstOrNull()
                    ?: emptyList()

                val performedPrs = WorkoutAnalyticsHelper.computePerformedExercisePrs(logList, exerciseList)
                val metrics = WorkoutAnalyticsHelper.computeDashboardMetrics(
                    allWorkouts = workoutList,
                    timeframe = timeframe,
                    profile = profile,
                    completedExercises = performedPrs.map { it.exercise.copy(percentIncrease = it.percentIncrease) },
                    routines = routineList,
                    firstDayOfWeek = profile.resolvedFirstDayOfWeek()
                )

                val caption = WorkoutShareCardGenerator.buildSummaryShareCaption(
                    timeframe = timeframe,
                    metrics = metrics,
                    performedPrs = performedPrs,
                    profile = profile
                )

                val imageFile = withContext(Dispatchers.IO) {
                    val shareDir = File(ctx.cacheDir, "social_shares").apply { mkdirs() }
                    val file = File(shareDir, "StrengthForge_Summary_${timeframe.name}.png")
                    WorkoutShareCardGenerator.writeSummarySharePngToFile(
                        outputFile = file,
                        timeframe = timeframe,
                        metrics = metrics,
                        performedPrs = performedPrs,
                        profile = profile
                    )
                }

                _socialShareState.value = SocialShareUiState.Ready(
                    workoutName = "${timeframe.label} Summary",
                    imageFile = imageFile,
                    caption = caption
                )
            } catch (e: Exception) {
                _socialShareState.value = SocialShareUiState.Error(
                    e.localizedMessage ?: "Could not generate summary share graphic."
                )
            }
        }
    }

    fun preparePrsSocialShare() {
        val ctx = appContext ?: run {
            _socialShareState.value = SocialShareUiState.Error("Missing application context for image share.")
            return
        }
        viewModelScope.launch {
            _socialShareState.value = SocialShareUiState.Generating
            try {
                val profile = (userProfile.value as? UiState.Success)?.data
                    ?: repository.observeUserProfile(currentUserId).firstOrNull()
                    ?: UserProfile(userId = currentUserId)
                val workoutList = (workouts.value as? UiState.Success)?.data
                    ?: repository.observeWorkouts(currentUserId).firstOrNull()
                    ?: emptyList()
                val logList = (logs.value as? UiState.Success)?.data
                    ?: repository.observeLogs(currentUserId).firstOrNull()
                    ?: emptyList()
                val exerciseList = (exercises.value as? UiState.Success)?.data
                    ?: repository.observeExerciseRecords(currentUserId).firstOrNull()
                    ?: emptyList()

                val performedPrs = WorkoutAnalyticsHelper.computePerformedExercisePrs(logList, exerciseList)
                val caption = WorkoutShareCardGenerator.buildPrsShareCaption(
                    performedPrs = performedPrs,
                    workoutsCount = workoutList.size,
                    profile = profile
                )

                val imageFile = withContext(Dispatchers.IO) {
                    val shareDir = File(ctx.cacheDir, "social_shares").apply { mkdirs() }
                    val file = File(shareDir, "StrengthForge_PersonalRecords.png")
                    WorkoutShareCardGenerator.writePrsSharePngToFile(
                        outputFile = file,
                        performedPrs = performedPrs,
                        workoutsCount = workoutList.size,
                        profile = profile
                    )
                }

                _socialShareState.value = SocialShareUiState.Ready(
                    workoutName = "Personal Records (PRs)",
                    imageFile = imageFile,
                    caption = caption
                )
            } catch (e: Exception) {
                _socialShareState.value = SocialShareUiState.Error(
                    e.localizedMessage ?: "Could not generate PRs share graphic."
                )
            }
        }
    }

    // CSV Local File Export State & Actions
    private val _csvExportState = MutableStateFlow<CsvExportUiState>(CsvExportUiState.Idle)
    val csvExportState: StateFlow<CsvExportUiState> = _csvExportState.asStateFlow()

    fun clearCsvExportStatus() {
        _csvExportState.value = CsvExportUiState.Idle
    }

    fun exportAllWorkoutsToCsv() {
        val ctx = appContext ?: run {
            _csvExportState.value = CsvExportUiState.Error("Missing application context for CSV export.")
            return
        }
        viewModelScope.launch {
            _csvExportState.value = CsvExportUiState.Exporting
            try {
                val workoutList = (workouts.value as? UiState.Success)?.data
                    ?: repository.observeWorkouts(currentUserId).firstOrNull()
                    ?: emptyList()
                val logList = (logs.value as? UiState.Success)?.data
                    ?: repository.observeLogs(currentUserId).firstOrNull()
                    ?: emptyList()
                val exerciseList = (exercises.value as? UiState.Success)?.data
                    ?: repository.observeExerciseRecords(currentUserId).firstOrNull()
                    ?: emptyList()

                if (workoutList.isEmpty() && logList.isEmpty()) {
                    _csvExportState.value = CsvExportUiState.Error("No logged workouts found yet to export.")
                    return@launch
                }

                val activeUnit = weightUnit.value
                val result = withContext(Dispatchers.IO) {
                    val exportDir = File(ctx.cacheDir, "csv_exports").apply { mkdirs() }
                    val csvFile = File(exportDir, WorkoutCsvExporter.defaultCsvFilename())
                    WorkoutCsvExporter.writeWorkoutsCsvFile(
                        outputFile = csvFile,
                        workouts = workoutList,
                        logs = logList,
                        exercises = exerciseList,
                        unit = activeUnit
                    )
                }

                _csvExportState.value = CsvExportUiState.Ready(
                    file = result.file,
                    workoutCount = result.workoutCount,
                    rowCount = result.rowCount
                )
            } catch (e: Exception) {
                _csvExportState.value = CsvExportUiState.Error(
                    e.localizedMessage ?: "Failed to generate CSV workout export."
                )
            }
        }
    }

    fun exportNutritionToCsv() {
        val ctx = appContext ?: run {
            _csvExportState.value = CsvExportUiState.Error("Missing application context for CSV export.")
            return
        }
        viewModelScope.launch {
            _csvExportState.value = CsvExportUiState.Exporting
            try {
                val profile = (userProfile.value as? UiState.Success)?.data
                    ?: repository.observeUserProfile(currentUserId).firstOrNull()
                    ?: UserProfile(userId = currentUserId)
                val history = nutritionHistory.value.ifEmpty {
                    listOfNotNull(todayNutrition.value)
                }
                if (history.isEmpty()) {
                    _csvExportState.value = CsvExportUiState.Error("No nutrition logs found yet to export.")
                    return@launch
                }

                val result = withContext(Dispatchers.IO) {
                    val exportDir = File(ctx.cacheDir, "csv_exports").apply { mkdirs() }
                    val csvFile = File(exportDir, WorkoutCsvExporter.defaultNutritionCsvFilename())
                    WorkoutCsvExporter.writeNutritionCsvFile(
                        outputFile = csvFile,
                        nutritionLogs = history,
                        profile = profile
                    )
                }

                _csvExportState.value = CsvExportUiState.Ready(
                    file = result.file,
                    workoutCount = result.workoutCount,
                    rowCount = result.rowCount
                )
            } catch (e: Exception) {
                _csvExportState.value = CsvExportUiState.Error(
                    e.localizedMessage ?: "Failed to generate CSV nutrition export."
                )
            }
        }
    }

    suspend fun deleteAllUserData(): Result<Unit> {
        return try {
            discardWorkout()
            nutritionMutex.withLock {
                latestNutritionCache.clear()
            }
            repository.deleteAllUserData(currentUserId)
            garminTracker?.clearExportHistory()
            _exportedGarminWorkoutIds.value = emptySet()
            _lastGarminExportAt.value = ""
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
