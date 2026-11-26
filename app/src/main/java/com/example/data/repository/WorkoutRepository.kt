package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.analytics.WorkoutAnalyticsHelper
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
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

interface WorkoutDataRepository {
    fun observeUserProfile(userId: String): Flow<UserProfile>
    fun observeWorkouts(userId: String): Flow<List<WorkoutSession>>
    fun observeExerciseRecords(userId: String): Flow<List<ExerciseRecord>>
    fun observeRoutines(userId: String): Flow<List<WorkoutRoutine>>
    fun observeLogs(userId: String): Flow<List<WorkoutExerciseLog>>
    fun observeNutrition(userId: String, dateString: String): Flow<DailyNutrition?>
    fun observeNutritionHistory(userId: String): Flow<List<DailyNutrition>>
    suspend fun saveExercise(userId: String, exercise: ExerciseRecord): String
    suspend fun deleteExercise(userId: String, exerciseId: String)
    suspend fun saveRoutine(userId: String, routine: WorkoutRoutine): String
    suspend fun deleteRoutine(userId: String, routineId: String)
    suspend fun seedInitialDataIfNeeded(userId: String)
    suspend fun saveWorkout(
        userId: String,
        workoutName: String,
        durationMinutes: Int,
        notes: String,
        exerciseEntries: List<LoggedExerciseData>,
        customDate: Date? = null,
        restSeconds: Int = 90
    ): String
    suspend fun updateWorkoutWithExercises(
        userId: String,
        workoutId: String,
        workoutName: String,
        durationMinutes: Int,
        notes: String,
        exerciseEntries: List<LoggedExerciseData>,
        customDate: Date? = null,
        restSeconds: Int = 90
    )
    suspend fun updateWorkoutMetadata(
        userId: String,
        workout: WorkoutSession,
        newName: String,
        newDate: Date,
        newDurationMinutes: Int,
        newNotes: String,
        newTotalVolumeLbs: Double
    )
    suspend fun deleteWorkout(userId: String, workout: WorkoutSession)
    suspend fun updateNutrition(
        userId: String,
        dateString: String,
        calories: Int,
        protein: Int,
        carbs: Int,
        fats: Int,
        meals: List<MealEntry> = emptyList()
    )
    suspend fun saveMealPresets(userId: String, presets: List<MealPreset>)
    suspend fun updateMacroTargets(
        userId: String,
        targetCalories: Int,
        targetMin: Int,
        targetMax: Int,
        proteinG: Int,
        carbsG: Int,
        fatsG: Int
    )
    suspend fun updateGoogleSheetMetadata(
        userId: String,
        sheetId: String,
        sheetUrl: String,
        lastSyncAt: String
    )
    suspend fun updateAutoSyncSheets(userId: String, enabled: Boolean)
    suspend fun deleteAllUserData(userId: String)
}

class WorkoutRepository(private val db: FirebaseFirestore) : WorkoutDataRepository {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    companion object {
        fun computeUpdatedExerciseMaxStats(
            startingWeightLbs: Double,
            currentMaxWeightLbs: Double,
            current1RMLbs: Double,
            loggedMaxWeightLbs: Double,
            logged1RMLbs: Double
        ): Triple<Double, Double, Double> {
            val newMax = maxOf(currentMaxWeightLbs, loggedMaxWeightLbs)
            val new1RM = maxOf(current1RMLbs, logged1RMLbs)
            val percentGain = if (startingWeightLbs > 0.0) {
                ((newMax - startingWeightLbs) / startingWeightLbs * 100.0)
            } else {
                0.0
            }
            val roundedGain = Math.round(percentGain * 10.0) / 10.0
            return Triple(newMax, new1RM, roundedGain)
        }
    }

    override fun observeUserProfile(userId: String): Flow<UserProfile> = callbackFlow {
        val docRef = db.collection("users").document(userId)
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.GET, docRef.path)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val profile = snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: UserProfile(id = snapshot.id, userId = userId)
                trySend(profile)
            } else {
                trySend(UserProfile(id = userId, userId = userId))
            }
        }
        awaitClose { listener.remove() }
    }

    override fun observeWorkouts(userId: String): Flow<List<WorkoutSession>> = callbackFlow {
        val collRef = db.collection("users").document(userId).collection("workouts")
        val listener = collRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, collRef.path)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val items = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(WorkoutSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }.sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                trySend(items)
            }
        }
        awaitClose { listener.remove() }
    }

    override fun observeExerciseRecords(userId: String): Flow<List<ExerciseRecord>> = callbackFlow {
        val collRef = db.collection("users").document(userId).collection("exercises")
        val listener = collRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, collRef.path)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val items = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(ExerciseRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }.sortedWith(compareByDescending<ExerciseRecord> { it.isPrimary }.thenBy { it.name })
                trySend(items)
            }
        }
        awaitClose { listener.remove() }
    }

    override fun observeRoutines(userId: String): Flow<List<WorkoutRoutine>> = callbackFlow {
        val collRef = db.collection("users").document(userId).collection("routines")
        val listener = collRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, collRef.path)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val items = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(WorkoutRoutine::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }.sortedBy { it.name }
                trySend(items)
            }
        }
        awaitClose { listener.remove() }
    }

    override fun observeLogs(userId: String): Flow<List<WorkoutExerciseLog>> = callbackFlow {
        val collRef = db.collection("users").document(userId).collection("logs")
        val listener = collRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, collRef.path)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val items = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(WorkoutExerciseLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }.sortedBy { it.date?.toDate()?.time ?: 0L }
                trySend(items)
            }
        }
        awaitClose { listener.remove() }
    }

    override fun observeNutrition(userId: String, dateString: String): Flow<DailyNutrition?> = callbackFlow {
        val docRef = db.collection("users").document(userId).collection("nutrition").document(dateString)
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.GET, docRef.path)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val nutrition = snapshot.toObject(DailyNutrition::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                trySend(nutrition)
            } else {
                trySend(null)
            }
        }
        awaitClose { listener.remove() }
    }

    override fun observeNutritionHistory(userId: String): Flow<List<DailyNutrition>> = callbackFlow {
        val collRef = db.collection("users").document(userId).collection("nutrition")
        val listener = collRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, collRef.path)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val items = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(DailyNutrition::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }.sortedByDescending { it.dateString.ifBlank { it.id } }
                trySend(items)
            }
        }
        awaitClose { listener.remove() }
    }

    override suspend fun saveExercise(userId: String, exercise: ExerciseRecord): String {
        val exId = if (exercise.id.isNotBlank()) exercise.id else "ex_${System.currentTimeMillis()}"
        val docRef = db.collection("users").document(userId).collection("exercises").document(exId)
        val tracking = exercise.resolvedTrackingType()

        val map = mapOf(
            "userId" to userId,
            "name" to exercise.name.trim(),
            "category" to exercise.category,
            "trackingType" to tracking.id,
            "startingWeightLbs" to exercise.startingWeightLbs,
            "currentMaxWeightLbs" to exercise.currentMaxWeightLbs,
            "personalRecord1RMLbs" to if (exercise.historyCount > 0) exercise.personalRecord1RMLbs else 0.0,
            "percentIncrease" to if (exercise.historyCount > 0) exercise.percentIncrease else 0.0,
            "historyCount" to exercise.historyCount,
            "defaultSets" to exercise.defaultSets.coerceAtLeast(1),
            "defaultReps" to exercise.defaultReps.coerceAtLeast(1),
            "defaultDurationSeconds" to exercise.defaultDurationSeconds.coerceAtLeast(5),
            "startingDurationSeconds" to exercise.startingDurationSeconds.coerceAtLeast(5),
            "currentBestDurationSeconds" to exercise.currentBestDurationSeconds.coerceAtLeast(0),
            "bestReps" to exercise.bestReps.coerceAtLeast(0),
            "isPrimary" to exercise.isPrimary,
            "notes" to exercise.notes,
            "lastLoggedDate" to (exercise.lastLoggedDate ?: FieldValue.serverTimestamp())
        )
        docRef.set(map).await()
        return exId
    }

    override suspend fun deleteExercise(userId: String, exerciseId: String) {
        db.collection("users").document(userId).collection("exercises").document(exerciseId).delete().await()
    }

    override suspend fun saveRoutine(userId: String, routine: WorkoutRoutine): String {
        val routineId = if (routine.id.isNotBlank()) routine.id else "routine_${System.currentTimeMillis()}"
        val docRef = db.collection("users").document(userId).collection("routines").document(routineId)
        val map = mapOf(
            "userId" to userId,
            "name" to routine.name.trim(),
            "description" to routine.description.trim(),
            "targetMuscle" to routine.targetMuscle,
            "exerciseIds" to routine.exerciseIds,
            "defaultSets" to routine.defaultSets.coerceAtLeast(1),
            "restSeconds" to routine.restSeconds.coerceAtLeast(5),
            "scheduledDays" to routine.scheduledDays.filter { it in 1..7 }.distinct().sorted(),
            "createdAt" to (routine.createdAt ?: FieldValue.serverTimestamp())
        )
        docRef.set(map).await()
        return routineId
    }

    override suspend fun deleteRoutine(userId: String, routineId: String) {
        db.collection("users").document(userId).collection("routines").document(routineId).delete().await()
    }

    override suspend fun seedInitialDataIfNeeded(userId: String) {
        val userDoc = db.collection("users").document(userId).get().await()
        if (userDoc.exists()) {
            // Check if routines exist for this existing user
            val routinesSnap = db.collection("users").document(userId).collection("routines").get().await()
            if (routinesSnap.isEmpty) {
                seedDefaultRoutines(userId)
            }
            if (userDoc.getBoolean("coreBodyweightSeeded") != true) {
                seedCoreBodyweightExercisesIfMissing(userId)
            }
            if (userDoc.getBoolean("prHistoryReconciled") != true ||
                userDoc.getBoolean("prFromLogsReconciled") != true
            ) {
                val allExIds = db.collection("users").document(userId).collection("exercises")
                    .get().await().documents.map { it.id }.toSet()
                recalculateExerciseStatsFromLogs(userId, allExIds)
                db.collection("users").document(userId).set(
                    mapOf(
                        "prHistoryReconciled" to true,
                        "prFromLogsReconciled" to true
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                ).await()
            }
            return
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val displayDateSdf = SimpleDateFormat("MMM d, yyyy", Locale.US)

        // Pre-seed profile with spreadsheet stats
        val profileData = mapOf(
            "userId" to userId,
            "displayName" to "Strength Athlete",
            "targetCalories" to 2550,
            "targetCaloriesMin" to 2500,
            "targetCaloriesMax" to 2600,
            "proteinGrams" to 190,
            "carbsGrams" to 250,
            "fatsGrams" to 80,
            "totalWorkoutsLogged" to 5,
            "totalLifetimeVolumeLbs" to 6275.0,
            "currentWeeklyStreak" to 1,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("users").document(userId).set(profileData).await()

        // Pre-seed Exercise Catalog (Bench Press starting at 135 -> current 150 lbs = 11.1% gain)
        val defaultExercises = listOf(
            ExerciseRecord(
                id = "bench_press",
                userId = userId,
                name = "Barbell Bench Press",
                category = "Chest",
                startingWeightLbs = 135.0,
                currentMaxWeightLbs = 150.0,
                personalRecord1RMLbs = 180.0,
                percentIncrease = 11.1,
                historyCount = 5,
                isPrimary = true,
                notes = "Spreadsheet cornerstone lift",
                lastLoggedDate = Timestamp.now()
            ),
            ExerciseRecord(
                id = "overhead_press",
                userId = userId,
                name = "Overhead Press",
                category = "Shoulders",
                startingWeightLbs = 85.0,
                currentMaxWeightLbs = 85.0,
                personalRecord1RMLbs = 0.0,
                percentIncrease = 0.0,
                historyCount = 0,
                isPrimary = false,
                notes = "Strict barbell press",
                lastLoggedDate = Timestamp.now()
            ),
            ExerciseRecord(
                id = "barbell_squat",
                userId = userId,
                name = "Barbell Back Squat",
                category = "Legs",
                startingWeightLbs = 185.0,
                currentMaxWeightLbs = 185.0,
                personalRecord1RMLbs = 0.0,
                percentIncrease = 0.0,
                historyCount = 0,
                isPrimary = true,
                notes = "Full depth below parallel",
                lastLoggedDate = Timestamp.now()
            ),
            ExerciseRecord(
                id = "barbell_deadlift",
                userId = userId,
                name = "Barbell Deadlift",
                category = "Back",
                startingWeightLbs = 205.0,
                currentMaxWeightLbs = 205.0,
                personalRecord1RMLbs = 0.0,
                percentIncrease = 0.0,
                historyCount = 0,
                isPrimary = true,
                notes = "Conventional stance",
                lastLoggedDate = Timestamp.now()
            ),
            ExerciseRecord(
                id = "incline_dumbbell_press",
                userId = userId,
                name = "Incline Dumbbell Press",
                category = "Chest",
                startingWeightLbs = 50.0,
                currentMaxWeightLbs = 50.0,
                personalRecord1RMLbs = 0.0,
                percentIncrease = 0.0,
                historyCount = 0,
                isPrimary = false,
                notes = "30 degree incline",
                lastLoggedDate = Timestamp.now()
            ),
            ExerciseRecord(
                id = "cable_row",
                userId = userId,
                name = "Seated Cable Row",
                category = "Back",
                startingWeightLbs = 110.0,
                currentMaxWeightLbs = 110.0,
                personalRecord1RMLbs = 0.0,
                percentIncrease = 0.0,
                historyCount = 0,
                isPrimary = false,
                notes = "Neutral close grip",
                lastLoggedDate = Timestamp.now()
            ),
            ExerciseRecord(
                id = "bicep_curl",
                userId = userId,
                name = "Dumbbell Bicep Curl",
                category = "Arms",
                startingWeightLbs = 25.0,
                currentMaxWeightLbs = 25.0,
                personalRecord1RMLbs = 0.0,
                percentIncrease = 0.0,
                historyCount = 0,
                isPrimary = false,
                notes = "Controlled eccentric",
                lastLoggedDate = Timestamp.now()
            ),
            ExerciseRecord(
                id = "tricep_pushdown",
                userId = userId,
                name = "Tricep Rope Pushdown",
                category = "Arms",
                startingWeightLbs = 40.0,
                currentMaxWeightLbs = 40.0,
                personalRecord1RMLbs = 0.0,
                percentIncrease = 0.0,
                historyCount = 0,
                isPrimary = false,
                notes = "Flared rope lockout",
                lastLoggedDate = Timestamp.now()
            ),
            ExerciseRecord(
                id = "plank",
                userId = userId,
                name = "Plank",
                category = "Core",
                trackingType = ExerciseTrackingType.TIMED_HOLD.id,
                startingWeightLbs = 0.0,
                currentMaxWeightLbs = 0.0,
                personalRecord1RMLbs = 0.0,
                percentIncrease = 0.0,
                historyCount = 0,
                defaultSets = 3,
                defaultReps = 1,
                defaultDurationSeconds = 60,
                startingDurationSeconds = 60,
                currentBestDurationSeconds = 60,
                isPrimary = false,
                notes = "Bodyweight isometric hold (add weight when ready)",
                lastLoggedDate = Timestamp.now()
            ),
            ExerciseRecord(
                id = "leg_raises",
                userId = userId,
                name = "Leg Raises",
                category = "Core",
                trackingType = ExerciseTrackingType.BODYWEIGHT_REPS.id,
                startingWeightLbs = 0.0,
                currentMaxWeightLbs = 0.0,
                personalRecord1RMLbs = 0.0,
                percentIncrease = 0.0,
                historyCount = 0,
                defaultSets = 3,
                defaultReps = 12,
                bestReps = 12,
                isPrimary = false,
                notes = "Bodyweight currently; add +lbs when ready",
                lastLoggedDate = Timestamp.now()
            ),
            ExerciseRecord(
                id = "weighted_crunches",
                userId = userId,
                name = "Weighted Crunches",
                category = "Core",
                trackingType = ExerciseTrackingType.WEIGHTED.id,
                startingWeightLbs = 25.0,
                currentMaxWeightLbs = 25.0,
                personalRecord1RMLbs = 0.0,
                percentIncrease = 0.0,
                historyCount = 0,
                defaultSets = 3,
                defaultReps = 15,
                isPrimary = false,
                notes = "Controlled core flexion with plate/dumbbell",
                lastLoggedDate = Timestamp.now()
            )
        )

        for (ex in defaultExercises) {
            saveExercise(userId, ex)
        }
        db.collection("users").document(userId).set(
            mapOf(
                "coreBodyweightSeeded" to true,
                "prHistoryReconciled" to true,
                "prFromLogsReconciled" to true
            ),
            com.google.firebase.firestore.SetOptions.merge()
        ).await()

        seedDefaultRoutines(userId)

        // Pre-seed the 5 historical workouts summing to exactly 6,275 lbs lifetime volume!
        val pastSessions = listOf(
            Triple("Push Day A - Chest & Triceps", 1180.0, 12),
            Triple("Pull Day B - Back & Biceps", 1220.0, 9),
            Triple("Leg Day C - Squat & Core", 1250.0, 6),
            Triple("Upper Strength & Hypertrophy", 1290.0, 3),
            Triple("Push Day A - Bench Press PR", 1335.0, 1)
        )

        val benchLogs = listOf(
            Triple(135.0, 8, 1080.0), // Session 1
            Triple(135.0, 10, 1350.0), // Session 2
            Triple(140.0, 8, 1120.0),  // Session 3
            Triple(145.0, 7, 1015.0),  // Session 4
            Triple(150.0, 6, 900.0)    // Session 5 -> New Max 150 lbs! (+11.1%)
        )

        for ((index, sessionInfo) in pastSessions.withIndex()) {
            val (name, volume, daysAgo) = sessionInfo
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }
            val dateStr = displayDateSdf.format(cal.time)
            val sessionDoc = db.collection("users").document(userId).collection("workouts").document()
            
            val sessionMap = mapOf(
                "userId" to userId,
                "name" to name,
                "totalVolumeLbs" to volume,
                "durationMinutes" to (42 + index * 2),
                "notes" to if (index == 4) "New Bench Press Max: 150 lbs! Solid form." else "Great mind-muscle connection.",
                "exerciseCount" to 4,
                "completedSetsCount" to 12,
                "dateString" to dateStr,
                "createdAt" to Timestamp(cal.time)
            )
            sessionDoc.set(sessionMap).await()

            // Bench press log for this session
            val (weight, reps, benchVolume) = benchLogs[index]
            val e1RM = weight * (1.0 + reps / 30.0)
            val setsList = listOf(
                mapOf("setNumber" to 1, "weightLbs" to (weight - 10), "reps" to 10, "completed" to true),
                mapOf("setNumber" to 2, "weightLbs" to weight, "reps" to reps, "completed" to true),
                mapOf("setNumber" to 3, "weightLbs" to weight, "reps" to reps, "completed" to true)
            )

            val logDoc = db.collection("users").document(userId).collection("logs").document()
            val logMap = mapOf(
                "workoutId" to sessionDoc.id,
                "userId" to userId,
                "exerciseId" to "bench_press",
                "exerciseName" to "Barbell Bench Press",
                "category" to "Chest",
                "sets" to setsList,
                "maxWeightLbs" to weight,
                "totalVolumeLbs" to benchVolume,
                "estimatedOneRepMaxLbs" to e1RM,
                "dateString" to dateStr,
                "date" to Timestamp(cal.time)
            )
            logDoc.set(logMap).await()
        }

        // Pre-seed today's nutrition matching spreadsheet targets
        val todayStr = sdf.format(Date())
        val nutritionMap = mapOf(
            "userId" to userId,
            "dateString" to todayStr,
            "calories" to 2520,
            "proteinG" to 188,
            "carbsG" to 248,
            "fatsG" to 79,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("users").document(userId).collection("nutrition").document(todayStr).set(nutritionMap).await()
    }

    private suspend fun seedDefaultRoutines(userId: String) {
        val defaultRoutines = listOf(
            WorkoutRoutine(
                id = "routine_push_a",
                userId = userId,
                name = "Push Day A",
                description = "Chest, Shoulders & Triceps focus",
                targetMuscle = "Push",
                exerciseIds = listOf("bench_press", "incline_dumbbell_press", "overhead_press", "tricep_pushdown"),
                defaultSets = 3,
                scheduledDays = listOf(1) // Monday
            ),
            WorkoutRoutine(
                id = "routine_pull_b",
                userId = userId,
                name = "Pull Day B",
                description = "Deadlift, Back width & Biceps focus",
                targetMuscle = "Pull",
                exerciseIds = listOf("barbell_deadlift", "cable_row", "bicep_curl"),
                defaultSets = 3,
                scheduledDays = listOf(3) // Wednesday
            ),
            WorkoutRoutine(
                id = "routine_leg_c",
                userId = userId,
                name = "Leg Day C",
                description = "Squats & Quad/Hamstring Hypertrophy",
                targetMuscle = "Legs",
                exerciseIds = listOf("barbell_squat"),
                defaultSets = 3,
                scheduledDays = listOf(5) // Friday
            ),
            WorkoutRoutine(
                id = "routine_upper_power",
                userId = userId,
                name = "Upper Body Hypertrophy",
                description = "Full upper torso compound & accessory circuit",
                targetMuscle = "Upper",
                exerciseIds = listOf("bench_press", "cable_row", "overhead_press", "bicep_curl", "tricep_pushdown"),
                defaultSets = 3,
                scheduledDays = listOf(6) // Saturday
            )
        )

        for (rt in defaultRoutines) {
            saveRoutine(userId, rt)
        }
    }

    override suspend fun saveWorkout(
        userId: String,
        workoutName: String,
        durationMinutes: Int,
        notes: String,
        exerciseEntries: List<LoggedExerciseData>,
        customDate: Date?,
        restSeconds: Int
    ): String {
        val totalVolume = exerciseEntries.sumOf { it.totalVolume }
        val dateSdf = SimpleDateFormat("MMM d, yyyy", Locale.US)
        val targetDate = customDate ?: Date()
        val dateString = dateSdf.format(targetDate)
        val timestamp = Timestamp(targetDate)

        val workoutDoc = db.collection("users").document(userId).collection("workouts").document()
        val workoutData = mapOf(
            "userId" to userId,
            "name" to workoutName,
            "totalVolumeLbs" to totalVolume,
            "durationMinutes" to durationMinutes,
            "notes" to notes,
            "exerciseCount" to exerciseEntries.size,
            "completedSetsCount" to exerciseEntries.sumOf { it.sets.count { s -> s.completed } },
            "restSeconds" to restSeconds.coerceAtLeast(5),
            "dateString" to dateString,
            "createdAt" to timestamp
        )
        workoutDoc.set(workoutData).await()

        // Save each exercise log
        val affectedExerciseIds = mutableSetOf<String>()
        for (entry in exerciseEntries) {
            if (entry.exerciseId.isNotBlank()) {
                affectedExerciseIds.add(entry.exerciseId)
            }
            val logDoc = db.collection("users").document(userId).collection("logs").document()
            val logData = mapOf(
                "workoutId" to workoutDoc.id,
                "userId" to userId,
                "exerciseId" to entry.exerciseId,
                "exerciseName" to entry.exerciseName,
                "category" to entry.category,
                "trackingType" to entry.trackingType.id,
                "sets" to entry.sets.map { it.toMap() },
                "maxWeightLbs" to entry.maxWeight,
                "totalVolumeLbs" to entry.totalVolume,
                "estimatedOneRepMaxLbs" to entry.estimated1RM,
                "bestDurationSeconds" to entry.bestDurationSeconds,
                "totalDurationSeconds" to entry.totalDurationSeconds,
                "totalReps" to entry.totalReps,
                "dateString" to dateString,
                "date" to timestamp
            )
            logDoc.set(logData).await()
        }

        // Recompute exercise PR stats strictly from actual performed logs
        recalculateExerciseStatsFromLogs(userId, affectedExerciseIds)

        // Update User Profile stats: increment total workouts, add volume
        val userRef = db.collection("users").document(userId)
        userRef.update(
            mapOf(
                "totalWorkoutsLogged" to FieldValue.increment(1),
                "totalLifetimeVolumeLbs" to FieldValue.increment(totalVolume),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        return workoutDoc.id
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
        val workoutRef = db.collection("users").document(userId).collection("workouts").document(workoutId)
        try {
            val existingSnap = workoutRef.get().await()
            val oldVolume = (existingSnap.get("totalVolumeLbs") as? Number)?.toDouble() ?: 0.0
            val newVolume = exerciseEntries.sumOf { it.totalVolume }

            val dateSdf = SimpleDateFormat("MMM d, yyyy", Locale.US)
            val targetDate = customDate ?: (existingSnap.getTimestamp("createdAt")?.toDate() ?: Date())
            val dateString = dateSdf.format(targetDate)
            val timestamp = Timestamp(targetDate)

            workoutRef.set(
                mapOf(
                    "userId" to userId,
                    "name" to workoutName.trim(),
                    "totalVolumeLbs" to newVolume,
                    "durationMinutes" to durationMinutes,
                    "notes" to notes,
                    "exerciseCount" to exerciseEntries.size,
                    "completedSetsCount" to exerciseEntries.sumOf { it.sets.count { s -> s.completed } },
                    "restSeconds" to restSeconds.coerceAtLeast(5),
                    "dateString" to dateString,
                    "createdAt" to timestamp
                )
            ).await()

            // Remove old logs for this workoutId and write updated logs
            val logsColl = db.collection("users").document(userId).collection("logs")
            val oldLogs = logsColl.whereEqualTo("workoutId", workoutId).get().await()
            val affectedExerciseIds = mutableSetOf<String>()
            for (doc in oldLogs.documents) {
                doc.getString("exerciseId")?.takeIf { it.isNotBlank() }?.let { affectedExerciseIds.add(it) }
                doc.reference.delete().await()
            }

            for (entry in exerciseEntries) {
                if (entry.exerciseId.isNotBlank()) {
                    affectedExerciseIds.add(entry.exerciseId)
                }
                val logDoc = logsColl.document()
                val logData = mapOf(
                    "workoutId" to workoutId,
                    "userId" to userId,
                    "exerciseId" to entry.exerciseId,
                    "exerciseName" to entry.exerciseName,
                    "category" to entry.category,
                    "trackingType" to entry.trackingType.id,
                    "sets" to entry.sets.map { it.toMap() },
                    "maxWeightLbs" to entry.maxWeight,
                    "totalVolumeLbs" to entry.totalVolume,
                    "estimatedOneRepMaxLbs" to entry.estimated1RM,
                    "bestDurationSeconds" to entry.bestDurationSeconds,
                    "totalDurationSeconds" to entry.totalDurationSeconds,
                    "totalReps" to entry.totalReps,
                    "dateString" to dateString,
                    "date" to timestamp
                )
                logDoc.set(logData).await()
            }

            recalculateExerciseStatsFromLogs(userId, affectedExerciseIds)

            // Adjust lifetime volume on profile
            val volumeDelta = newVolume - oldVolume
            if (volumeDelta != 0.0) {
                val userRef = db.collection("users").document(userId)
                val userSnap = userRef.get().await()
                val currentLifetime = (userSnap.get("totalLifetimeVolumeLbs") as? Number)?.toDouble() ?: 0.0
                userRef.update(
                    mapOf(
                        "totalLifetimeVolumeLbs" to maxOf(0.0, currentLifetime + volumeDelta),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, workoutRef.path)
        }
    }

    override suspend fun updateWorkoutMetadata(
        userId: String,
        workout: WorkoutSession,
        newName: String,
        newDate: Date,
        newDurationMinutes: Int,
        newNotes: String,
        newTotalVolumeLbs: Double
    ) {
        val workoutRef = db.collection("users").document(userId).collection("workouts").document(workout.id)
        try {
            val dateSdf = SimpleDateFormat("MMM d, yyyy", Locale.US)
            val dateString = dateSdf.format(newDate)
            val timestamp = Timestamp(newDate)

            workoutRef.update(
                mapOf(
                    "name" to newName.trim(),
                    "dateString" to dateString,
                    "createdAt" to timestamp,
                    "durationMinutes" to newDurationMinutes,
                    "notes" to newNotes,
                    "totalVolumeLbs" to newTotalVolumeLbs
                )
            ).await()

            // Sync date across associated logs so analytics charts match
            val logsColl = db.collection("users").document(userId).collection("logs")
            val associatedLogs = logsColl.whereEqualTo("workoutId", workout.id).get().await()
            for (doc in associatedLogs.documents) {
                doc.reference.update(
                    mapOf(
                        "dateString" to dateString,
                        "date" to timestamp
                    )
                ).await()
            }

            val volumeDelta = newTotalVolumeLbs - workout.totalVolumeLbs
            if (volumeDelta != 0.0) {
                val userRef = db.collection("users").document(userId)
                val userSnap = userRef.get().await()
                val currentLifetime = (userSnap.get("totalLifetimeVolumeLbs") as? Number)?.toDouble() ?: 0.0
                userRef.update(
                    mapOf(
                        "totalLifetimeVolumeLbs" to maxOf(0.0, currentLifetime + volumeDelta),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, workoutRef.path)
        }
    }

    override suspend fun deleteWorkout(userId: String, workout: WorkoutSession) {
        val workoutRef = db.collection("users").document(userId).collection("workouts").document(workout.id)
        try {
            workoutRef.delete().await()

            val logsColl = db.collection("users").document(userId).collection("logs")
            val associatedLogs = logsColl.whereEqualTo("workoutId", workout.id).get().await()
            val affectedExerciseIds = mutableSetOf<String>()
            for (doc in associatedLogs.documents) {
                doc.getString("exerciseId")?.takeIf { it.isNotBlank() }?.let { affectedExerciseIds.add(it) }
                doc.reference.delete().await()
            }

            recalculateExerciseStatsFromLogs(userId, affectedExerciseIds)

            val userRef = db.collection("users").document(userId)
            val userSnap = userRef.get().await()
            if (userSnap.exists()) {
                val currentCount = (userSnap.get("totalWorkoutsLogged") as? Number)?.toInt() ?: 0
                val currentVolume = (userSnap.get("totalLifetimeVolumeLbs") as? Number)?.toDouble() ?: 0.0
                userRef.update(
                    mapOf(
                        "totalWorkoutsLogged" to maxOf(0, currentCount - 1),
                        "totalLifetimeVolumeLbs" to maxOf(0.0, currentVolume - workout.totalVolumeLbs),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, workoutRef.path)
        }
    }

    private suspend fun recalculateExerciseStatsFromLogs(userId: String, exerciseIds: Set<String>) {
        if (exerciseIds.isEmpty()) return
        val logsColl = db.collection("users").document(userId).collection("logs")
        val exercisesColl = db.collection("users").document(userId).collection("exercises")

        for (exId in exerciseIds) {
            val exRef = exercisesColl.document(exId)
            val exSnap = exRef.get().await()
            if (!exSnap.exists()) continue

            val starting = (exSnap.get("startingWeightLbs") as? Number)?.toDouble() ?: 0.0
            val defaultDur = (exSnap.get("defaultDurationSeconds") as? Number)?.toInt() ?: 60
            val tracking = ExerciseTrackingType.fromString(exSnap.getString("trackingType"))
            val exLogs = logsColl.whereEqualTo("exerciseId", exId).get().await()
            val logsList = exLogs.documents.mapNotNull { doc ->
                doc.toObject(WorkoutExerciseLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }.sortedBy { WorkoutAnalyticsHelper.resolveLogDate(it)?.time ?: 0L }

            if (logsList.isNotEmpty()) {
                val exRecord = exSnap.toObject(ExerciseRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?.copy(id = exSnap.id)
                    ?: ExerciseRecord(id = exSnap.id, trackingType = tracking.id)
                val prStats = WorkoutAnalyticsHelper.computePerformedExercisePrFor(exRecord, logsList)
                val latestTimestamp = logsList.mapNotNull { it.date }.maxByOrNull { it.toDate().time }

                val updates = mutableMapOf<String, Any>(
                    "currentMaxWeightLbs" to (prStats?.maxWeightLbs ?: 0.0),
                    "personalRecord1RMLbs" to (prStats?.best1RM ?: 0.0),
                    "currentBestDurationSeconds" to (prStats?.bestDurationSeconds ?: 0),
                    "bestReps" to (prStats?.bestReps ?: 0),
                    "percentIncrease" to (prStats?.percentIncrease ?: 0.0),
                    "historyCount" to logsList.size
                )
                if (prStats != null && prStats.startingWeightLbs > 0.0) {
                    updates["startingWeightLbs"] = prStats.startingWeightLbs
                }
                if (prStats != null && prStats.startingDurationSeconds > 0) {
                    updates["startingDurationSeconds"] = prStats.startingDurationSeconds
                }
                if (latestTimestamp != null) {
                    updates["lastLoggedDate"] = latestTimestamp
                }
                exRef.update(updates).await()
            } else {
                exRef.update(
                    mapOf(
                        "currentMaxWeightLbs" to starting,
                        "personalRecord1RMLbs" to 0.0,
                        "currentBestDurationSeconds" to defaultDur,
                        "bestReps" to 0,
                        "percentIncrease" to 0.0,
                        "historyCount" to 0
                    )
                ).await()
            }
        }
    }

    override suspend fun updateNutrition(
        userId: String,
        dateString: String,
        calories: Int,
        protein: Int,
        carbs: Int,
        fats: Int,
        meals: List<MealEntry>
    ) {
        val docRef = db.collection("users").document(userId).collection("nutrition").document(dateString)
        val data = mapOf(
            "userId" to userId,
            "dateString" to dateString,
            "calories" to calories.coerceAtLeast(0),
            "proteinG" to protein.coerceAtLeast(0),
            "carbsG" to carbs.coerceAtLeast(0),
            "fatsG" to fats.coerceAtLeast(0),
            "meals" to meals.map { it.toMap() },
            "updatedAt" to FieldValue.serverTimestamp()
        )
        docRef.set(data).await()
    }

    override suspend fun saveMealPresets(userId: String, presets: List<MealPreset>) {
        val docRef = db.collection("users").document(userId)
        docRef.set(
            mapOf(
                "mealPresets" to presets.map { it.toMap() },
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            com.google.firebase.firestore.SetOptions.merge()
        ).await()
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
        val docRef = db.collection("users").document(userId)
        docRef.update(
            mapOf(
                "targetCalories" to targetCalories,
                "targetCaloriesMin" to targetMin,
                "targetCaloriesMax" to targetMax,
                "proteinGrams" to proteinG,
                "carbsGrams" to carbsG,
                "fatsGrams" to fatsG,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    override suspend fun updateGoogleSheetMetadata(
        userId: String,
        sheetId: String,
        sheetUrl: String,
        lastSyncAt: String
    ) {
        val docRef = db.collection("users").document(userId)
        try {
            docRef.set(
                mapOf(
                    "googleSheetId" to sheetId,
                    "googleSheetUrl" to sheetUrl,
                    "lastSheetSyncAt" to lastSyncAt,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
        }
    }

    override suspend fun updateAutoSyncSheets(userId: String, enabled: Boolean) {
        val docRef = db.collection("users").document(userId)
        try {
            docRef.set(
                mapOf(
                    "autoSyncSheets" to enabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
        }
    }
    private suspend fun seedCoreBodyweightExercisesIfMissing(userId: String) {
        val exercisesColl = db.collection("users").document(userId).collection("exercises")
        val existingIds = exercisesColl.get().await().documents.map { it.id }.toSet()
        val coreDefaults = listOf(
            ExerciseRecord(
                id = "plank",
                userId = userId,
                name = "Plank",
                category = "Core",
                trackingType = ExerciseTrackingType.TIMED_HOLD.id,
                startingWeightLbs = 0.0,
                currentMaxWeightLbs = 0.0,
                personalRecord1RMLbs = 0.0,
                percentIncrease = 0.0,
                historyCount = 0,
                defaultSets = 3,
                defaultReps = 1,
                defaultDurationSeconds = 60,
                startingDurationSeconds = 60,
                currentBestDurationSeconds = 60,
                isPrimary = false,
                notes = "Bodyweight isometric hold (add weight when ready)",
                lastLoggedDate = Timestamp.now()
            ),
            ExerciseRecord(
                id = "leg_raises",
                userId = userId,
                name = "Leg Raises",
                category = "Core",
                trackingType = ExerciseTrackingType.BODYWEIGHT_REPS.id,
                startingWeightLbs = 0.0,
                currentMaxWeightLbs = 0.0,
                personalRecord1RMLbs = 0.0,
                percentIncrease = 0.0,
                historyCount = 0,
                defaultSets = 3,
                defaultReps = 12,
                bestReps = 12,
                isPrimary = false,
                notes = "Bodyweight currently; add +lbs when ready",
                lastLoggedDate = Timestamp.now()
            ),
            ExerciseRecord(
                id = "weighted_crunches",
                userId = userId,
                name = "Weighted Crunches",
                category = "Core",
                trackingType = ExerciseTrackingType.WEIGHTED.id,
                startingWeightLbs = 25.0,
                currentMaxWeightLbs = 25.0,
                personalRecord1RMLbs = 0.0,
                percentIncrease = 0.0,
                historyCount = 0,
                defaultSets = 3,
                defaultReps = 15,
                isPrimary = false,
                notes = "Controlled core flexion with plate/dumbbell",
                lastLoggedDate = Timestamp.now()
            )
        )
        for (ex in coreDefaults) {
            if (!existingIds.contains(ex.id)) {
                saveExercise(userId, ex)
            }
        }
        db.collection("users").document(userId).set(
            mapOf("coreBodyweightSeeded" to true),
            com.google.firebase.firestore.SetOptions.merge()
        ).await()
    }

    override suspend fun deleteAllUserData(userId: String) {
        val userDocRef = db.collection("users").document(userId)
        val subcollections = listOf("workouts", "exercises", "logs", "nutrition", "routines")
        val docRefsToDelete = mutableListOf<com.google.firebase.firestore.DocumentReference>()

        for (sub in subcollections) {
            val snap = userDocRef.collection(sub).get().await()
            snap.documents.forEach { docRefsToDelete.add(it.reference) }
        }
        docRefsToDelete.add(userDocRef)

        for (chunk in docRefsToDelete.chunked(450)) {
            val batch = db.batch()
            chunk.forEach { ref -> batch.delete(ref) }
            batch.commit().await()
        }
    }
}

data class LoggedExerciseData(
    val exerciseId: String,
    val exerciseName: String,
    val category: String,
    val sets: List<ExerciseSet>,
    val maxWeight: Double,
    val totalVolume: Double,
    val estimated1RM: Double,
    val trackingType: ExerciseTrackingType = ExerciseTrackingType.WEIGHTED,
    val bestDurationSeconds: Int = 0,
    val totalDurationSeconds: Int = 0,
    val totalReps: Int = 0
)
