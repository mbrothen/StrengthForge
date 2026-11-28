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
import com.example.data.analytics.DashboardMetrics
import com.example.data.analytics.DashboardTimeframe
import com.example.data.analytics.PerformedExercisePr
import com.example.data.analytics.WeightUnit
import com.example.data.analytics.WeightUnitHelper
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
        val unit = profile.resolvedWeightUnit()
        val unitLabel = unit.shortLabel
        val matchedLogs = logs.filter { it.workoutId == workout.id }
        val volumeDisplay = WeightUnitHelper.formatVolumeNumber(workout.totalVolumeLbs, unit)
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
            append("📊 Volume: ${numberFmt.format(volumeDisplay)} $unitLabel")
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
                            val wtNote = if (maxW > 0.0) " (+${formatWeight(maxW, unit)} $unitLabel)" else " (BW)"
                            append("\n• ${log.exerciseName}: ${parsedSets.size.coerceAtLeast(1)} sets × ${bestDur}s$wtNote")
                        }
                        ExerciseTrackingType.BODYWEIGHT_REPS -> {
                            val repsDisplay = bestSet?.reps ?: parsedSets.maxOfOrNull { it.reps } ?: 12
                            val wtNote = if (maxW > 0.0) "BW + ${formatWeight(maxW, unit)} $unitLabel" else "Bodyweight"
                            append("\n• ${log.exerciseName}: $wtNote × $repsDisplay reps")
                        }
                        ExerciseTrackingType.WEIGHTED -> {
                            val repsDisplay = bestSet?.reps ?: 8
                            append("\n• ${log.exerciseName}: ${formatWeight(maxW, unit)} $unitLabel × $repsDisplay reps")
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
        val unit = profile.resolvedWeightUnit()
        val unitLabel = unit.shortLabel
        val unitUpper = unitLabel.uppercase(Locale.US)
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
        val formattedVol = "${numberFmt.format(WeightUnitHelper.formatVolumeNumber(workout.totalVolumeLbs, unit))} $unitUpper"
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
                        rightMetricText = if (maxW > 0.0) "${bestDur}s (+${formatWeight(maxW, unit)} $unitLabel)" else "${bestDur}s"
                    }
                    ExerciseTrackingType.BODYWEIGHT_REPS -> {
                        metaText = "${log.category} • $setCount sets × $bestReps reps"
                        rightMetricText = if (maxW > 0.0) "BW + ${formatWeight(maxW, unit)} $unitLabel" else "Bodyweight"
                    }
                    ExerciseTrackingType.WEIGHTED -> {
                        metaText = "${log.category} • $setCount sets × $bestReps reps"
                        rightMetricText = "${formatWeight(maxW, unit)} $unitLabel"
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
            val lifetimeDisplay = WeightUnitHelper.formatVolumeNumber(profile.totalLifetimeVolumeLbs, unit)
            canvas.drawText(
                "Lifetime Volume: ${numberFmt.format(lifetimeDisplay)} $unitLabel across ${profile.totalWorkoutsLogged} workouts",
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

    fun buildSummaryShareCaption(
        timeframe: DashboardTimeframe,
        metrics: DashboardMetrics,
        performedPrs: List<PerformedExercisePr>,
        profile: UserProfile
    ): String {
        val numberFmt = NumberFormat.getNumberInstance(Locale.US)
        val unit = profile.resolvedWeightUnit()
        val unitLabel = unit.shortLabel
        val volumeDisplay = WeightUnitHelper.formatVolumeNumber(metrics.totalVolumeLbs, unit)

        return buildString {
            append("🏋️ StrengthForge ${timeframe.label} Summary\n")
            append("📊 Volume: ${numberFmt.format(volumeDisplay)} $unitLabel")
            if (metrics.gainPercent > 0.0) {
                append(" (+${metrics.gainPercent}%)")
            }
            append("\n")
            append("✅ ${metrics.sessionCount} workouts • ${metrics.completedSets} sets • ⏱ ${metrics.avgDurationMinutes} min avg")
            if (performedPrs.isNotEmpty()) {
                append("\n\n🏆 Top Personal Records:")
                performedPrs.take(4).forEach { pr ->
                    val gainNote = if (pr.percentIncrease > 0.0) " (+${pr.percentIncrease}%)" else ""
                    append("\n• ${pr.exercise.name}: ${pr.formatPrimaryMetricLabel(unit)}$gainNote")
                }
            }
            if (profile.currentWeeklyStreak > 0) {
                append("\n\n🔥 ${profile.currentWeeklyStreak}-Week Consistency Streak")
            }
            append("\n\n#StrengthForge #WorkoutSummary #ProgressiveOverload")
        }
    }

    fun renderSummaryShareBitmap(
        timeframe: DashboardTimeframe,
        metrics: DashboardMetrics,
        performedPrs: List<PerformedExercisePr>,
        profile: UserProfile,
        widthPx: Int = CARD_WIDTH,
        heightPx: Int = CARD_HEIGHT
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val numberFmt = NumberFormat.getNumberInstance(Locale.US)
        val unit = profile.resolvedWeightUnit()
        val unitLabel = unit.shortLabel
        val unitUpper = unitLabel.uppercase(Locale.US)

        val scaleX = widthPx / CARD_WIDTH.toFloat()
        val scaleY = heightPx / CARD_HEIGHT.toFloat()
        canvas.save()
        canvas.scale(scaleX, scaleY)

        drawCardFrameAndHeader(
            canvas = canvas,
            eyebrow = "${timeframe.label.uppercase(Locale.US)} TRAINING SUMMARY",
            title = "Workout Summary",
            streakWeeks = profile.currentWeeklyStreak
        )

        val cardFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#141E33") }
        val cardStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.parseColor("#24324D")
        }
        val subLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.08f
        }

        // Hero Volume Card
        val heroCard = RectF(84f, 336f, 996f, 520f)
        canvas.drawRoundRect(heroCard, 28f, 28f, cardFill)
        canvas.drawRoundRect(heroCard, 28f, 28f, cardStroke)
        canvas.drawText("TOTAL TRAINING VOLUME", 120f, 386f, subLabelPaint)

        val heroValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F59E0B")
            textSize = 84f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val formattedVol = "${numberFmt.format(WeightUnitHelper.formatVolumeNumber(metrics.totalVolumeLbs, unit))} $unitUpper"
        canvas.drawText(formattedVol, 120f, 480f, heroValuePaint)

        if (metrics.gainPercent != 0.0) {
            val isGain = metrics.gainPercent > 0.0
            val gainPill = RectF(786f, 368f, 962f, 418f)
            val gainBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor(if (isGain) "#064E3B" else "#3F1D2B")
            }
            val gainText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor(if (isGain) "#22C55E" else "#EF4444")
                textSize = 24f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawRoundRect(gainPill, 14f, 14f, gainBg)
            val prefix = if (isGain) "+" else ""
            canvas.drawText("$prefix${metrics.gainPercent}%", gainPill.centerX(), 401f, gainText)
        }

        // 3 KPI Tiles
        val tiles = listOf(
            Triple("WORKOUTS", "${metrics.sessionCount} SESSIONS", "#2DD4BF"),
            Triple("SETS DONE", "${metrics.completedSets} SETS", "#22C55E"),
            Triple("AVG DURATION", "${metrics.avgDurationMinutes} MIN", "#38BDF8")
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
                textSize = 36f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText(value, left + 26f, 658f, valPaint)
        }

        // Top PRs Highlight Box
        canvas.drawText("TOP PERFORMED PRs", 84f, 756f, subLabelPaint)
        val prBox = RectF(84f, 782f, 996f, 1186f)
        canvas.drawRoundRect(prBox, 28f, 28f, cardFill)
        canvas.drawRoundRect(prBox, 28f, 28f, cardStroke)

        drawPrRowsInBox(
            canvas = canvas,
            prs = performedPrs.take(4),
            unit = unit,
            boxTop = 782f,
            rowHeight = 90f,
            emptyHeading = "Consistency Builds Strength",
            emptySub = metrics.adherenceLabel.ifBlank { "Log lifts to unlock your personal records" }
        )

        drawCardFooter(
            canvas = canvas,
            leftLabel = profile.displayName.ifBlank { "Strength Athlete" },
            rightLabel = "Logged with StrengthForge"
        )

        canvas.restore()
        return bitmap
    }

    fun writeSummarySharePngToFile(
        outputFile: File,
        timeframe: DashboardTimeframe,
        metrics: DashboardMetrics,
        performedPrs: List<PerformedExercisePr>,
        profile: UserProfile
    ): File {
        outputFile.parentFile?.mkdirs()
        val bitmap = renderSummaryShareBitmap(timeframe, metrics, performedPrs, profile)
        try {
            FileOutputStream(outputFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } finally {
            bitmap.recycle()
        }
        return outputFile
    }

    fun buildPrsShareCaption(
        performedPrs: List<PerformedExercisePr>,
        workoutsCount: Int,
        profile: UserProfile
    ): String {
        val unit = profile.resolvedWeightUnit()
        return buildString {
            append("🏆 My StrengthForge Personal Records (PRs)\n")
            if (performedPrs.isEmpty()) {
                append("Ready to chase new progressive overload milestones!")
            } else {
                performedPrs.take(6).forEach { pr ->
                    val gainNote = if (pr.percentIncrease > 0.0) " (+${pr.percentIncrease}% gain)" else ""
                    append("\n• ${pr.exercise.name}: ${pr.formatPrimaryMetricLabel(unit)}$gainNote")
                }
            }
            if (workoutsCount > 0) {
                append("\n\n📊 $workoutsCount total sessions logged")
            }
            if (profile.currentWeeklyStreak > 0) {
                append(" • 🔥 ${profile.currentWeeklyStreak}-Week Streak")
            }
            append("\n\n#StrengthForge #PersonalRecord #NewPR #StrengthTraining")
        }
    }

    fun renderPrsShareBitmap(
        performedPrs: List<PerformedExercisePr>,
        workoutsCount: Int,
        profile: UserProfile,
        widthPx: Int = CARD_WIDTH,
        heightPx: Int = CARD_HEIGHT
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val unit = profile.resolvedWeightUnit()

        val scaleX = widthPx / CARD_WIDTH.toFloat()
        val scaleY = heightPx / CARD_HEIGHT.toFloat()
        canvas.save()
        canvas.scale(scaleX, scaleY)

        drawCardFrameAndHeader(
            canvas = canvas,
            eyebrow = "ALL-TIME PERFORMED MILESTONES",
            title = "Personal Records",
            streakWeeks = profile.currentWeeklyStreak
        )

        val cardFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#141E33") }
        val cardStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.parseColor("#24324D")
        }
        val subLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.08f
        }

        val spotlightPr = performedPrs.firstOrNull { it.exercise.isPrimary } ?: performedPrs.firstOrNull()
        val heroCard = RectF(84f, 336f, 996f, 560f)
        val heroStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            color = Color.parseColor("#F59E0B")
        }
        canvas.drawRoundRect(heroCard, 28f, 28f, cardFill)
        canvas.drawRoundRect(heroCard, 28f, 28f, heroStroke)

        if (spotlightPr != null) {
            canvas.drawText("HEADLINE LIFT PR", 120f, 386f, subLabelPaint)
            val liftTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 36f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText(
                ellipsizeText(spotlightPr.exercise.name, liftTitlePaint, 620f),
                120f,
                434f,
                liftTitlePaint
            )
            val heroMetricPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#F59E0B")
                textSize = 72f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText(spotlightPr.formatPrimaryMetricLabel(unit), 120f, 520f, heroMetricPaint)

            if (spotlightPr.percentIncrease > 0.0) {
                val gainPill = RectF(786f, 368f, 962f, 418f)
                val gainBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#064E3B") }
                val gainText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#22C55E")
                    textSize = 24f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawRoundRect(gainPill, 14f, 14f, gainBg)
                canvas.drawText("+${spotlightPr.percentIncrease}%", gainPill.centerX(), 401f, gainText)
            }
        } else {
            canvas.drawText("PERSONAL RECORDS", 120f, 396f, subLabelPaint)
            val emptyHero = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#F59E0B")
                textSize = 48f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText("Ready for First PR", 120f, 480f, emptyHero)
        }

        // Remaining PR Leaderboard Card
        canvas.drawText("PR LEADERBOARD", 84f, 620f, subLabelPaint)
        val listCard = RectF(84f, 646f, 996f, 1186f)
        canvas.drawRoundRect(listCard, 28f, 28f, cardFill)
        canvas.drawRoundRect(listCard, 28f, 28f, cardStroke)

        val secondaryPrs = if (spotlightPr != null) {
            performedPrs.filterNot { it.exercise.id == spotlightPr.exercise.id }.take(5)
        } else {
            emptyList()
        }
        val rowsToRender = if (secondaryPrs.isNotEmpty()) secondaryPrs else performedPrs.take(5)
        drawPrRowsInBox(
            canvas = canvas,
            prs = rowsToRender,
            unit = unit,
            boxTop = 646f,
            rowHeight = 102f,
            emptyHeading = "Log Working Sets to Unlock PRs",
            emptySub = "Performed maxes & % gains appear here automatically"
        )

        val rightFooter = if (workoutsCount > 0) {
            "$workoutsCount Workouts • StrengthForge"
        } else {
            "Logged with StrengthForge"
        }
        drawCardFooter(
            canvas = canvas,
            leftLabel = profile.displayName.ifBlank { "Strength Athlete" },
            rightLabel = rightFooter
        )

        canvas.restore()
        return bitmap
    }

    fun writePrsSharePngToFile(
        outputFile: File,
        performedPrs: List<PerformedExercisePr>,
        workoutsCount: Int,
        profile: UserProfile
    ): File {
        outputFile.parentFile?.mkdirs()
        val bitmap = renderPrsShareBitmap(performedPrs, workoutsCount, profile)
        try {
            FileOutputStream(outputFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } finally {
            bitmap.recycle()
        }
        return outputFile
    }

    private fun drawCardFrameAndHeader(
        canvas: Canvas,
        eyebrow: String,
        title: String,
        streakWeeks: Int
    ) {
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

        val frameRect = RectF(36f, 36f, CARD_WIDTH - 36f, CARD_HEIGHT - 36f)
        val frameBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            color = Color.argb(90, 245, 158, 11)
        }
        canvas.drawRoundRect(frameRect, 40f, 40f, frameBorder)

        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F59E0B")
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.14f
        }
        canvas.drawText("STRENGTHFORGE", 84f, 118f, brandPaint)

        val streakText = "${maxOf(1, streakWeeks)} WK STREAK"
        val streakBox = RectF(772f, 80f, 996f, 132f)
        val streakBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2E1705") }
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

        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2DD4BF")
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(eyebrow, 84f, 194f, datePaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 56f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(ellipsizeText(title, titlePaint, 912f), 84f, 264f, titlePaint)

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
    }

    private fun drawPrRowsInBox(
        canvas: Canvas,
        prs: List<PerformedExercisePr>,
        unit: WeightUnit,
        boxTop: Float,
        rowHeight: Float,
        emptyHeading: String,
        emptySub: String
    ) {
        if (prs.isEmpty()) {
            val emptyHeadingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 30f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val emptyBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#94A3B8")
                textSize = 23f
            }
            canvas.drawText(emptyHeading, 122f, boxTop + 120f, emptyHeadingPaint)
            canvas.drawText(ellipsizeText(emptySub, emptyBodyPaint, 840f), 122f, boxTop + 170f, emptyBodyPaint)
            return
        }

        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 21f
        }
        val prMetricPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F59E0B")
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val gainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#22C55E")
            textSize = 21f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
            strokeWidth = 2f
        }

        prs.forEachIndexed { idx, pr ->
            val yBase = boxTop + 62f + idx * rowHeight
            canvas.drawText(ellipsizeText(pr.exercise.name, namePaint, 520f), 118f, yBase, namePaint)
            val metaText = "${pr.exercise.category.ifBlank { "Strength" }} • ${pr.sessionsCount.coerceAtLeast(1)} sessions"
            canvas.drawText(metaText, 118f, yBase + 28f, metaPaint)

            canvas.drawText(pr.formatPrimaryMetricLabel(unit), 962f, yBase, prMetricPaint)
            val gainStr = if (pr.percentIncrease > 0.0) "+${pr.percentIncrease}% gain" else "${pr.totalSetsLogged.coerceAtLeast(1)} sets"
            canvas.drawText(gainStr, 962f, yBase + 28f, if (pr.percentIncrease > 0.0) gainPaint else metaPaint)

            if (idx < prs.lastIndex) {
                val divY = yBase + 46f
                canvas.drawLine(118f, divY, 962f, divY, dividerPaint)
            }
        }
    }

    private fun drawCardFooter(
        canvas: Canvas,
        leftLabel: String,
        rightLabel: String
    ) {
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
        canvas.drawText(leftLabel, 84f, 1278f, athletePaint)

        val footerRightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 24f
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(rightLabel, 996f, 1278f, footerRightPaint)
    }

    private fun formatWeight(weightLbs: Double, unit: WeightUnit): String {
        return WeightUnitHelper.formatWeightNumber(weightLbs, unit)
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
