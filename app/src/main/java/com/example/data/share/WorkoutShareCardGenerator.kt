package com.example.data.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.example.data.model.ExerciseSet
import com.example.data.model.ExerciseTrackingType
import com.example.data.model.UserProfile
import com.example.data.model.WorkoutExerciseLog
import com.example.data.model.WorkoutSession
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.util.Locale

object WorkoutShareCardGenerator {

    private const val CARD_WIDTH = 1080
    private const val CARD_HEIGHT = 1350

    fun buildShareCaption(
        workout: WorkoutSession,
        logs: List<WorkoutExerciseLog>,
        profile: UserProfile
    ): String {
        val numberFmt = NumberFormat.getNumberInstance(Locale.US)
        val matchedLogs = logs.filter { it.workoutId == workout.id }
        val volumeInt = workout.totalVolumeLbs.toInt()
        val setsCount = if (workout.completedSetsCount > 0) {
            workout.completedSetsCount
        } else {
            matchedLogs.sumOf { it.sets.size }
        }
        val exCount = if (workout.exerciseCount > 0) {
            workout.exerciseCount
        } else {
            matchedLogs.size
        }

        return buildString {
            append("💪 ${workout.name.ifBlank { "Strength Session" }}")
            if (workout.dateString.isNotBlank()) {
                append(" (${workout.dateString})")
            }
            append("\n")
            append("📊 Volume: ${numberFmt.format(volumeInt)} lbs")
            append(" • ⏱ ${workout.durationMinutes} min")
            if (setsCount > 0) {
                append(" • ✅ $setsCount sets")
            }
            if (exCount > 0) {
                append(" • 🏋️ $exCount exercises")
            }
            if (matchedLogs.isNotEmpty()) {
                append("\n\nTop Lifts:")
                matchedLogs.take(5).forEach { log ->
                    val parsedSets = log.sets.map { ExerciseSet.fromMap(it) }
                    val bestSet = parsedSets.maxByOrNull { it.weightLbs }
                    val maxW = if (log.maxWeightLbs > 0) {
                        log.maxWeightLbs
                    } else {
                        bestSet?.weightLbs ?: 0.0
                    }
                    val tracking = log.resolvedTrackingType()
                    when (tracking) {
                        ExerciseTrackingType.TIMED_HOLD -> {
                            val bestDur = maxOf(
                                log.bestDurationSeconds,
                                parsedSets.maxOfOrNull { it.durationSeconds } ?: 0
                            )
                            val wtNote = if (maxW > 0.0) " (+${formatWeight(maxW)} lbs)" else " (BW)"
                            append("\n• ${log.exerciseName}: ${parsedSets.size.coerceAtLeast(1)} sets × ${bestDur}s$wtNote")
                        }
                        ExerciseTrackingType.BODYWEIGHT_REPS -> {
                            val repsDisplay = bestSet?.reps ?: parsedSets.maxOfOrNull { it.reps } ?: 12
                            val wtNote = if (maxW > 0.0) "BW + ${formatWeight(maxW)} lbs" else "Bodyweight"
                            append("\n• ${log.exerciseName}: $wtNote × $repsDisplay reps")
                        }
                        ExerciseTrackingType.WEIGHTED -> {
                            val repsDisplay = bestSet?.reps ?: 8
                            append("\n• ${log.exerciseName}: ${formatWeight(maxW)} lbs × $repsDisplay reps")
                        }
                    }
                }
            }
            if (profile.currentWeeklyStreak > 0) {
                append("\n\n🔥 ${profile.currentWeeklyStreak}-Week Consistency Streak")
            }
            if (workout.notes.isNotBlank()) {
                append("\n📝 \"${workout.notes.trim()}\"")
            }
            append("\n\n#StrengthForge #StrengthTraining #ProgressiveOverload")
        }
    }

    fun renderWorkoutShareBitmap(
        workout: WorkoutSession,
        logs: List<WorkoutExerciseLog>,
        profile: UserProfile,
        widthPx: Int = CARD_WIDTH,
        heightPx: Int = CARD_HEIGHT
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val numberFmt = NumberFormat.getNumberInstance(Locale.US)
        val matchedLogs = logs.filter { it.workoutId == workout.id }

        val scaleX = widthPx / CARD_WIDTH.toFloat()
        val scaleY = heightPx / CARD_HEIGHT.toFloat()
        canvas.save()
        canvas.scale(scaleX, scaleY)

        // 1. Deep dark obsidian gradient background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat(),
                intArrayOf(
                    Color.parseColor("#070B14"),
                    Color.parseColor("#0F172A"),
                    Color.parseColor("#090E1A")
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat(), bgPaint)

        // 2. Subtle atmospheric radial glows (Amber top-right, Teal bottom-left)
        val amberGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                920f, 180f, 460f,
                Color.argb(56, 245, 158, 11),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(920f, 180f, 460f, amberGlow)

        val tealGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                160f, 1180f, 480f,
                Color.argb(46, 20, 184, 166),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(160f, 1180f, 480f, tealGlow)

        // 3. Outer subtle card border
        val frameRect = RectF(36f, 36f, CARD_WIDTH - 36f, CARD_HEIGHT - 36f)
        val frameBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            color = Color.argb(90, 245, 158, 11)
        }
        canvas.drawRoundRect(frameRect, 40f, 40f, frameBorder)

        // 4. Header: Brand Pill & Date
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F59E0B")
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.14f
        }
        canvas.drawText("STRENGTHFORGE", 84f, 118f, brandPaint)

        // Streak badge on top right
        val streakText = "${maxOf(1, profile.currentWeeklyStreak)} WK STREAK"
        val streakBox = RectF(772f, 80f, 996f, 132f)
        val streakBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2E1705")
        }
        val streakStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.parseColor("#F97316")
        }
        canvas.drawRoundRect(streakBox, 16f, 16f, streakBg)
        canvas.drawRoundRect(streakBox, 16f, 16f, streakStroke)
        val streakLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FB923C")
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(streakText, streakBox.centerX(), 114f, streakLabelPaint)

        // Workout Name & Date
        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2DD4BF")
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(
            workout.dateString.ifBlank { "WORKOUT LOG" }.uppercase(Locale.US),
            84f,
            194f,
            datePaint
        )

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 56f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val clippedTitle = ellipsizeText(workout.name.ifBlank { "Strength Session" }, titlePaint, 912f)
        canvas.drawText(clippedTitle, 84f, 264f, titlePaint)

        // Accent divider bar
        val accentBar = RectF(84f, 294f, 300f, 302f)
        val accentBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                84f, 0f, 300f, 0f,
                Color.parseColor("#F59E0B"),
                Color.parseColor("#14B8A6"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(accentBar, 4f, 4f, accentBarPaint)

        // 5. Hero Volume Card
        val heroCard = RectF(84f, 336f, 996f, 520f)
        val cardFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#141E33")
        }
        val cardStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.parseColor("#24324D")
        }
        canvas.drawRoundRect(heroCard, 28f, 28f, cardFill)
        canvas.drawRoundRect(heroCard, 28f, 28f, cardStroke)

        val subLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.08f
        }
        canvas.drawText("TOTAL VOLUME MOVED", 120f, 386f, subLabelPaint)

        val heroValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F59E0B")
            textSize = 84f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val formattedVol = "${numberFmt.format(workout.totalVolumeLbs.toInt())} LBS"
        canvas.drawText(formattedVol, 120f, 480f, heroValuePaint)

        // 6. 3-Metric KPI Tiles Row
        val setsVal = if (workout.completedSetsCount > 0) {
            workout.completedSetsCount
        } else {
            matchedLogs.sumOf { it.sets.size }
        }
        val exVal = if (workout.exerciseCount > 0) {
            workout.exerciseCount
        } else {
            matchedLogs.size
        }

        val tiles = listOf(
            Triple("DURATION", "${workout.durationMinutes} MIN", "#2DD4BF"),
            Triple("SETS DONE", "$setsVal SETS", "#22C55E"),
            Triple("EXERCISES", "$exVal LIFTS", "#38BDF8")
        )
        val tileWidth = 288f
        val tileGap = 24f
        tiles.forEachIndexed { idx, (label, value, colorHex) ->
            val left = 84f + idx * (tileWidth + tileGap)
            val tileRect = RectF(left, 550f, left + tileWidth, 696f)
            canvas.drawRoundRect(tileRect, 22f, 22f, cardFill)
            canvas.drawRoundRect(tileRect, 22f, 22f, cardStroke)

            canvas.drawText(label, left + 26f, 598f, subLabelPaint)
            val valPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor(colorHex)
                textSize = 40f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText(value, left + 26f, 658f, valPaint)
        }

        // 7. Lift Breakdown Section
        canvas.drawText("SESSION BREAKDOWN", 84f, 756f, subLabelPaint)

        val listTop = 782f
        val listBottom = 1186f
        val breakdownCard = RectF(84f, listTop, 996f, listBottom)
        canvas.drawRoundRect(breakdownCard, 28f, 28f, cardFill)
        canvas.drawRoundRect(breakdownCard, 28f, 28f, cardStroke)

        if (matchedLogs.isNotEmpty()) {
            val maxItemVolume = matchedLogs.maxOf { maxOf(1.0, it.totalVolumeLbs) }
            val visibleLogs = matchedLogs.take(5)
            val rowHeight = 72f

            val liftNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 27f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val liftMetaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#94A3B8")
                textSize = 21f
            }
            val liftWeightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#F59E0B")
                textSize = 28f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }
            val barTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1E293B")
            }
            val barFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#14B8A6")
            }

            visibleLogs.forEachIndexed { idx, log ->
                val yBase = listTop + 56f + idx * rowHeight
                val parsedSets = log.sets.map { ExerciseSet.fromMap(it) }
                val setCount = parsedSets.size.coerceAtLeast(1)
                val bestReps = parsedSets.maxOfOrNull { it.reps } ?: 8
                val maxW = if (log.maxWeightLbs > 0) log.maxWeightLbs else (parsedSets.maxOfOrNull { it.weightLbs } ?: 0.0)
                val tracking = log.resolvedTrackingType()

                val metaText: String
                val rightMetricText: String
                when (tracking) {
                    ExerciseTrackingType.TIMED_HOLD -> {
                        val bestDur = maxOf(
                            log.bestDurationSeconds,
                            parsedSets.maxOfOrNull { it.durationSeconds } ?: 0
                        )
                        metaText = "${log.category} • $setCount sets × ${bestDur}s hold"
                        rightMetricText = if (maxW > 0.0) "${bestDur}s (+${formatWeight(maxW)})" else "${bestDur}s"
                    }
                    ExerciseTrackingType.BODYWEIGHT_REPS -> {
                        metaText = "${log.category} • $setCount sets × $bestReps reps"
                        rightMetricText = if (maxW > 0.0) "BW + ${formatWeight(maxW)} lbs" else "Bodyweight"
                    }
                    ExerciseTrackingType.WEIGHTED -> {
                        metaText = "${log.category} • $setCount sets × $bestReps reps"
                        rightMetricText = "${formatWeight(maxW)} lbs"
                    }
                }

                val nameClipped = ellipsizeText(log.exerciseName, liftNamePaint, 520f)
                canvas.drawText(nameClipped, 118f, yBase, liftNamePaint)
                canvas.drawText(
                    metaText,
                    118f,
                    yBase + 26f,
                    liftMetaPaint
                )
                canvas.drawText(
                    rightMetricText,
                    962f,
                    yBase + 6f,
                    liftWeightPaint
                )

                // Mini horizontal volume bar
                val barTop = yBase + 36f
                val trackRect = RectF(118f, barTop, 962f, barTop + 6f)
                canvas.drawRoundRect(trackRect, 3f, 3f, barTrackPaint)
                val ratio = (log.totalVolumeLbs / maxItemVolume).toFloat().coerceIn(0.12f, 1f)
                val fillRect = RectF(118f, barTop, 118f + (962f - 118f) * ratio, barTop + 6f)
                canvas.drawRoundRect(fillRect, 3f, 3f, barFillPaint)
            }
        } else {
            val emptyHeadingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 32f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val emptyBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#94A3B8")
                textSize = 24f
            }
            canvas.drawText("Strength Session Completed", 122f, 890f, emptyHeadingPaint)
            val noteLine = workout.notes.ifBlank { "Consistent progressive overload logged." }
            canvas.drawText(ellipsizeText(noteLine, emptyBodyPaint, 840f), 122f, 940f, emptyBodyPaint)
            canvas.drawText(
                "Lifetime Volume: ${numberFmt.format(profile.totalLifetimeVolumeLbs.toInt())} lbs across ${profile.totalWorkoutsLogged} workouts",
                122f,
                1000f,
                emptyBodyPaint
            )
        }

        // 8. Footer: Athlete & Brand Tagline
        val footerDivider = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
            strokeWidth = 2f
        }
        canvas.drawLine(84f, 1224f, 996f, 1224f, footerDivider)

        val athletePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(
            profile.displayName.ifBlank { "Strength Athlete" },
            84f,
            1278f,
            athletePaint
        )

        val footerRightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 24f
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("Logged with StrengthForge", 996f, 1278f, footerRightPaint)

        canvas.restore()
        return bitmap
    }

    fun writeSharePngToFile(
        outputFile: File,
        workout: WorkoutSession,
        logs: List<WorkoutExerciseLog>,
        profile: UserProfile
    ): File {
        outputFile.parentFile?.mkdirs()
        val bitmap = renderWorkoutShareBitmap(workout, logs, profile)
        try {
            FileOutputStream(outputFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } finally {
            bitmap.recycle()
        }
        return outputFile
    }

    private fun formatWeight(weight: Double): String {
        return if (weight % 1.0 == 0.0) weight.toInt().toString() else String.format(Locale.US, "%.1f", weight)
    }

    private fun ellipsizeText(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var trimmed = text
        while (trimmed.isNotEmpty() && paint.measureText("$trimmed…") > maxWidth) {
            trimmed = trimmed.dropLast(1)
        }
        return if (trimmed.isEmpty()) "…" else "$trimmed…"
    }
}
