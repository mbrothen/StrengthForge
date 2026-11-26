package com.example.data.garmin

import android.content.Context
import com.example.data.model.ExerciseRecord
import com.example.data.model.ExerciseSet
import com.example.data.model.ExerciseTrackingType
import com.example.data.model.WorkoutExerciseLog
import com.example.data.model.WorkoutSession
import com.garmin.fit.Activity
import com.garmin.fit.ActivityMesg
import com.garmin.fit.DateTime
import com.garmin.fit.DeviceIndex
import com.garmin.fit.DeviceInfoMesg
import com.garmin.fit.Event
import com.garmin.fit.EventMesg
import com.garmin.fit.EventType
import com.garmin.fit.ExerciseCategory
import com.garmin.fit.File as FitFileType
import com.garmin.fit.FileEncoder
import com.garmin.fit.FileIdMesg
import com.garmin.fit.Fit
import com.garmin.fit.FitBaseUnit
import com.garmin.fit.LapMesg
import com.garmin.fit.Manufacturer
import com.garmin.fit.SessionMesg
import com.garmin.fit.SessionTrigger
import com.garmin.fit.SetMesg
import com.garmin.fit.SetType
import com.garmin.fit.Sport
import com.garmin.fit.SportMesg
import com.garmin.fit.SubSport
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class GarminBatchExportResult(
    val archiveFile: File,
    val exportedWorkoutIds: List<String>,
    val skippedAlreadyExportedCount: Int,
    val totalRequestedCount: Int
)

object GarminFitExporter {

    private const val LBS_TO_KG = 0.45359237
    private const val STRENGTHFORGE_PRODUCT_ID = 4201

    /**
     * Produces a deterministic non-zero 32-bit serial number per userId so that
     * Garmin Connect matches (manufacturer, product, serial_number, time_created)
     * across repeated exports and ignores duplicates automatically.
     */
    fun deriveDeterministicSerialNumber(userId: String): Long {
        val normalized = userId.ifBlank { "strengthforge_default" }
        var hash = 0x811C9DC5L
        for (ch in normalized) {
            hash = hash xor ch.code.toLong()
            hash = (hash * 0x01000193L) and 0xFFFFFFFFL
        }
        val bounded = (hash and 0x7FFFFFFFL)
        return if (bounded == 0L) 100_001L else bounded
    }

    /**
     * Computes a deterministic start Date for [workout].
     * - Re-exporting the same workout ALWAYS yields the exact same timestamp, so
     *   Garmin Connect detects identical FileId.timeCreated + Session.startTime and ignores duplicates.
     * - Past workouts logged via date picker default to local 12:00:00 noon; adding a
     *   deterministic per-workout-ID sub-hour offset (0..3599s) prevents two *different*
     *   workouts logged on the same day from colliding as false duplicates in Garmin Connect.
     */
    fun resolveDeterministicStartDate(workout: WorkoutSession): Date {
        val baseDate = workout.createdAt?.toDate()
            ?: parseDisplayDate(workout.dateString)
            ?: Date(1704067200000L) // 2024-01-01 fallback

        val cal = Calendar.getInstance().apply { time = baseDate }
        val isExactNoonOrMidnight = cal.get(Calendar.MINUTE) == 0 &&
            cal.get(Calendar.SECOND) == 0 &&
            (cal.get(Calendar.HOUR_OF_DAY) == 12 || cal.get(Calendar.HOUR_OF_DAY) == 0)

        if (isExactNoonOrMidnight && workout.id.isNotBlank()) {
            val offsetSeconds = (workout.id.hashCode().toLong() and 0x7FFFFFFFL) % 3600L
            return Date(baseDate.time + offsetSeconds * 1000L)
        }
        return baseDate
    }

    private fun parseDisplayDate(dateString: String): Date? {
        if (dateString.isBlank()) return null
        val formats = listOf("MMM d, yyyy", "yyyy-MM-dd", "EEE, MMM d, yyyy")
        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                val parsed = sdf.parse(dateString)
                if (parsed != null) {
                    val cal = Calendar.getInstance().apply {
                        time = parsed
                        set(Calendar.HOUR_OF_DAY, 12)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    return cal.time
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    fun mapExerciseToFitCategory(exerciseName: String, category: String): Int {
        val lowerName = exerciseName.lowercase(Locale.US)
        return when {
            lowerName.contains("bench") || lowerName.contains("chest press") -> ExerciseCategory.BENCH_PRESS
            lowerName.contains("fly") || lowerName.contains("pec") -> ExerciseCategory.FLYE
            lowerName.contains("push-up") || lowerName.contains("pushup") -> ExerciseCategory.PUSH_UP
            lowerName.contains("squat") -> ExerciseCategory.SQUAT
            lowerName.contains("lunge") || lowerName.contains("split") -> ExerciseCategory.LUNGE
            lowerName.contains("deadlift") || lowerName.contains("rdl") -> ExerciseCategory.DEADLIFT
            lowerName.contains("row") -> ExerciseCategory.ROW
            lowerName.contains("pull-up") || lowerName.contains("pullup") ||
                lowerName.contains("chin") || lowerName.contains("pulldown") -> ExerciseCategory.PULL_UP
            lowerName.contains("overhead") || lowerName.contains("shoulder press") ||
                lowerName.contains("military") -> ExerciseCategory.SHOULDER_PRESS
            lowerName.contains("lateral raise") || lowerName.contains("front raise") -> ExerciseCategory.LATERAL_RAISE
            lowerName.contains("shrug") -> ExerciseCategory.SHRUG
            lowerName.contains("leg curl") || lowerName.contains("hamstring curl") -> ExerciseCategory.LEG_CURL
            lowerName.contains("curl") -> ExerciseCategory.CURL
            lowerName.contains("tricep") || lowerName.contains("pushdown") ||
                lowerName.contains("skull") || lowerName.contains("dip") -> ExerciseCategory.TRICEPS_EXTENSION
            lowerName.contains("calf") -> ExerciseCategory.CALF_RAISE
            lowerName.contains("plank") -> ExerciseCategory.PLANK
            lowerName.contains("crunch") || lowerName.contains("sit-up") -> ExerciseCategory.CRUNCH
            lowerName.contains("leg raise") || lowerName.contains("knee raise") ||
                lowerName.contains("hanging leg") -> ExerciseCategory.CORE
            else -> when (category.lowercase(Locale.US)) {
                "chest" -> ExerciseCategory.BENCH_PRESS
                "back" -> ExerciseCategory.ROW
                "legs" -> ExerciseCategory.SQUAT
                "shoulders" -> ExerciseCategory.SHOULDER_PRESS
                "arms" -> ExerciseCategory.CURL
                "core" -> ExerciseCategory.CORE
                else -> ExerciseCategory.UNKNOWN
            }
        }
    }

    fun buildSafeFitFilename(workout: WorkoutSession): String {
        val startDate = resolveDeterministicStartDate(workout)
        val datePrefix = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(startDate)
        val slug = workout.name
            .replace(Regex("[^A-Za-z0-9]+"), "_")
            .trim('_')
            .take(32)
            .ifBlank { "Strength_Workout" }
        val shortId = workout.id
            .replace(Regex("[^A-Za-z0-9]"), "")
            .takeLast(6)
            .ifBlank { (workout.name.hashCode() and 0xFFFF).toString(16) }
        return "${datePrefix}_${slug}_${shortId}.fit"
    }

    fun writeWorkoutFitFile(
        outputFile: File,
        userId: String,
        workout: WorkoutSession,
        workoutLogs: List<WorkoutExerciseLog>,
        exerciseCatalog: List<ExerciseRecord> = emptyList()
    ): File {
        outputFile.parentFile?.mkdirs()

        val startDate = resolveDeterministicStartDate(workout)
        val startTime = DateTime(startDate)
        val durationSeconds = maxOf(60, workout.durationMinutes * 60)
        val serialNumber = deriveDeterministicSerialNumber(userId)

        val matchedLogs = workoutLogs.filter { it.workoutId == workout.id }

        // Expand sets from logs so we know exact timeline & elapsed seconds
        data class EncodedSetEntry(
            val fitCategory: Int,
            val trackingType: ExerciseTrackingType,
            val set: ExerciseSet
        )
        val expandedSets = mutableListOf<EncodedSetEntry>()
        for (log in matchedLogs) {
            val catalogMatch = exerciseCatalog.find { it.id == log.exerciseId }
            val fitCat = mapExerciseToFitCategory(
                exerciseName = catalogMatch?.name ?: log.exerciseName,
                category = catalogMatch?.category ?: log.category
            )
            val resolvedTracking = if (log.trackingType.isNotBlank() && log.trackingType != ExerciseTrackingType.WEIGHTED.id) {
                log.resolvedTrackingType()
            } else {
                catalogMatch?.resolvedTrackingType() ?: log.resolvedTrackingType()
            }
            val parsedSets = log.sets.mapIndexed { idx, raw ->
                val parsed = ExerciseSet.fromMap(raw)
                if (parsed.setNumber <= 0) parsed.copy(setNumber = idx + 1) else parsed
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
            parsedSets.forEach { setItem ->
                expandedSets.add(
                    EncodedSetEntry(
                        fitCategory = fitCat,
                        trackingType = resolvedTracking,
                        set = setItem
                    )
                )
            }
        }

        val restSecs = workout.restSeconds.coerceIn(5, 900)
        val activePerSetSecs = 35
        val computedActiveAndRestSecs = if (expandedSets.isNotEmpty()) {
            val activeSum = expandedSets.sumOf { entry ->
                if (entry.trackingType == ExerciseTrackingType.TIMED_HOLD && entry.set.durationSeconds > 0) {
                    entry.set.durationSeconds
                } else {
                    activePerSetSecs
                }
            }
            val restSum = maxOf(0, expandedSets.size - 1) * restSecs
            maxOf(durationSeconds, activeSum + restSum)
        } else {
            durationSeconds
        }
        val computedTotalSecs = computedActiveAndRestSecs
        val endDate = Date(startDate.time + computedTotalSecs * 1000L)
        val endTime = DateTime(endDate)

        val encoder = FileEncoder(outputFile, Fit.ProtocolVersion.V2_0)
        try {
            // 1. Mandatory FileIdMesg (first message in every FIT file)
            val fileId = FileIdMesg().apply {
                type = FitFileType.ACTIVITY
                manufacturer = Manufacturer.DEVELOPMENT
                product = STRENGTHFORGE_PRODUCT_ID
                this.serialNumber = serialNumber
                timeCreated = startTime
                productName = "StrengthForge"
            }
            encoder.write(fileId)

            // 2. DeviceInfoMesg
            val deviceInfo = DeviceInfoMesg().apply {
                timestamp = startTime
                deviceIndex = DeviceIndex.CREATOR
                manufacturer = Manufacturer.DEVELOPMENT
                product = STRENGTHFORGE_PRODUCT_ID
                this.serialNumber = serialNumber
                softwareVersion = 1.0f
            }
            encoder.write(deviceInfo)

            // 3. SportMesg (Strength Training)
            val sportMesg = SportMesg().apply {
                sport = Sport.TRAINING
                subSport = SubSport.STRENGTH_TRAINING
                name = workout.name.ifBlank { "Strength Training" }.take(40)
            }
            encoder.write(sportMesg)

            // 4. Timer Start Event
            val startEvent = EventMesg().apply {
                timestamp = startTime
                event = Event.TIMER
                eventType = EventType.START
            }
            encoder.write(startEvent)

            // 5. Strength Set Messages (global mesg 225)
            var cursorMillis = startDate.time
            var setMessageIdx = 0
            expandedSets.forEachIndexed { index, entry ->
                val isTimed = entry.trackingType == ExerciseTrackingType.TIMED_HOLD && entry.set.durationSeconds > 0
                val setSecs = if (isTimed) entry.set.durationSeconds else activePerSetSecs
                val activeSetStart = DateTime(Date(cursorMillis))
                val activeDuration = setSecs.toFloat()
                cursorMillis += setSecs * 1000L
                val activeSetEnd = DateTime(Date(cursorMillis))

                val activeMesg = SetMesg().apply {
                    messageIndex = setMessageIdx++
                    timestamp = activeSetEnd
                    this.startTime = activeSetStart
                    duration = activeDuration
                    setType = SetType.ACTIVE
                    repetitions = if (isTimed) maxOf(1, entry.set.reps) else maxOf(0, entry.set.reps)
                    weight = (maxOf(0.0, entry.set.weightLbs) * LBS_TO_KG).toFloat()
                    weightDisplayUnit = FitBaseUnit.POUND
                    setCategory(0, entry.fitCategory)
                }
                encoder.write(activeMesg)

                // Emit rest interval between sets
                if (index < expandedSets.lastIndex) {
                    val restStart = DateTime(Date(cursorMillis))
                    cursorMillis += restSecs * 1000L
                    val restEnd = DateTime(Date(cursorMillis))

                    val restMesg = SetMesg().apply {
                        messageIndex = setMessageIdx++
                        timestamp = restEnd
                        this.startTime = restStart
                        duration = restSecs.toFloat()
                        setType = SetType.REST
                    }
                    encoder.write(restMesg)
                }
            }

            // 6. Timer Stop Event
            val stopEvent = EventMesg().apply {
                timestamp = endTime
                event = Event.TIMER
                eventType = EventType.STOP_ALL
            }
            encoder.write(stopEvent)

            // 7. LapMesg
            val estimatedCalories = maxOf(25, (workout.durationMinutes * 6) + (expandedSets.size * 5))
            val lapMesg = LapMesg().apply {
                messageIndex = 0
                timestamp = endTime
                this.startTime = startTime
                totalElapsedTime = computedTotalSecs.toFloat()
                totalTimerTime = computedTotalSecs.toFloat()
                sport = Sport.TRAINING
                subSport = SubSport.STRENGTH_TRAINING
                event = Event.LAP
                eventType = EventType.STOP
                totalCalories = estimatedCalories
            }
            encoder.write(lapMesg)

            // 8. SessionMesg
            val sessionMesg = SessionMesg().apply {
                messageIndex = 0
                timestamp = endTime
                this.startTime = startTime
                totalElapsedTime = computedTotalSecs.toFloat()
                totalTimerTime = computedTotalSecs.toFloat()
                sport = Sport.TRAINING
                subSport = SubSport.STRENGTH_TRAINING
                firstLapIndex = 0
                numLaps = 1
                event = Event.SESSION
                eventType = EventType.STOP
                trigger = SessionTrigger.ACTIVITY_END
                totalCalories = estimatedCalories
                sportProfileName = workout.name.ifBlank { "Strength Training" }.take(40)
            }
            encoder.write(sessionMesg)

            // 9. ActivityMesg
            val tzOffsetMillis = TimeZone.getDefault().getOffset(endDate.time)
            val localDateTime = DateTime(Date(endDate.time + tzOffsetMillis))
            val activityMesg = ActivityMesg().apply {
                timestamp = endTime
                totalTimerTime = computedTotalSecs.toFloat()
                numSessions = 1
                type = Activity.MANUAL
                event = Event.ACTIVITY
                eventType = EventType.STOP
                localTimestamp = localDateTime.timestamp
            }
            encoder.write(activityMesg)
        } finally {
            encoder.close()
        }

        return outputFile
    }

    fun exportWorkoutsBatchZip(
        outputZipFile: File,
        tempDir: File,
        userId: String,
        workouts: List<WorkoutSession>,
        logs: List<WorkoutExerciseLog>,
        exercises: List<ExerciseRecord>,
        onlyUnexported: Boolean,
        alreadyExportedIds: Set<String>
    ): GarminBatchExportResult {
        outputZipFile.parentFile?.mkdirs()
        tempDir.mkdirs()

        val sortedWorkouts = workouts.sortedBy { resolveDeterministicStartDate(it).time }
        val targetWorkouts = if (onlyUnexported) {
            sortedWorkouts.filter { !alreadyExportedIds.contains(it.id) }
        } else {
            sortedWorkouts
        }
        val skippedCount = sortedWorkouts.size - targetWorkouts.size

        val exportedIds = mutableListOf<String>()
        val usedFilenames = mutableSetOf<String>()

        ZipOutputStream(FileOutputStream(outputZipFile)).use { zipOut ->
            for (workout in targetWorkouts) {
                var entryName = buildSafeFitFilename(workout)
                if (!usedFilenames.add(entryName)) {
                    entryName = entryName.removeSuffix(".fit") + "_${exportedIds.size + 1}.fit"
                    usedFilenames.add(entryName)
                }

                val tempFit = File(tempDir, entryName)
                try {
                    writeWorkoutFitFile(
                        outputFile = tempFit,
                        userId = userId,
                        workout = workout,
                        workoutLogs = logs,
                        exerciseCatalog = exercises
                    )
                    zipOut.putNextEntry(ZipEntry(entryName))
                    tempFit.inputStream().use { input ->
                        input.copyTo(zipOut)
                    }
                    zipOut.closeEntry()
                    if (workout.id.isNotBlank()) {
                        exportedIds.add(workout.id)
                    }
                } finally {
                    tempFit.delete()
                }
            }
        }

        return GarminBatchExportResult(
            archiveFile = outputZipFile,
            exportedWorkoutIds = exportedIds,
            skippedAlreadyExportedCount = skippedCount,
            totalRequestedCount = sortedWorkouts.size
        )
    }
}

class GarminExportTracker(context: Context, private val userId: String) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "strengthforge_garmin_exports",
        Context.MODE_PRIVATE
    )

    private val idsKey = "exported_ids_$userId"
    private val lastSyncKey = "last_export_at_$userId"

    fun getExportedWorkoutIds(): Set<String> {
        return prefs.getStringSet(idsKey, emptySet())?.toSet() ?: emptySet()
    }

    fun getLastExportAt(): String {
        return prefs.getString(lastSyncKey, "") ?: ""
    }

    fun recordExportedWorkouts(workoutIds: Collection<String>): Pair<Set<String>, String> {
        val merged = (getExportedWorkoutIds() + workoutIds.filter { it.isNotBlank() }).toSet()
        val timestampLabel = SimpleDateFormat("MMM d, h:mm a", Locale.US).format(Date())
        prefs.edit()
            .putStringSet(idsKey, merged)
            .putString(lastSyncKey, timestampLabel)
            .apply()
        return merged to timestampLabel
    }

    fun clearExportHistory() {
        prefs.edit()
            .remove(idsKey)
            .remove(lastSyncKey)
            .apply()
    }
}
