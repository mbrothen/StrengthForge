package com.example.data.analytics

import com.example.data.model.BodyweightEntry
import com.example.data.onboarding.FitnessGoal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

data class BodyweightTrendPoint(
    val dateString: String,
    val shortLabel: String,
    val weightLbs: Double,
    val sevenDayAvgLbs: Double,
    val bodyFatPercent: Double? = null
)

data class BodyweightSummary(
    val entriesDescending: List<BodyweightEntry>,
    val trendPoints: List<BodyweightTrendPoint>,
    val latestWeightLbs: Double?,
    val sevenDayAvgLbs: Double?,
    val weeklyDeltaLbs: Double?,
    val goalAlignmentLabel: String
)

object BodyweightAnalyticsHelper {

    private const val DAY_MS = 86_400_000L

    fun computeBodyweightSummary(
        entries: List<BodyweightEntry>,
        goal: FitnessGoal = FitnessGoal.MAINTAIN,
        unit: WeightUnit = WeightUnit.LBS
    ): BodyweightSummary {
        val validEntries = entries.filter { it.dateString.isNotBlank() && it.weightLbs > 0.0 }
        if (validEntries.isEmpty()) {
            return BodyweightSummary(
                entriesDescending = emptyList(),
                trendPoints = emptyList(),
                latestWeightLbs = null,
                sevenDayAvgLbs = null,
                weeklyDeltaLbs = null,
                goalAlignmentLabel = "Log weigh-ins to track your 7-day trend"
            )
        }

        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val shortFmt = SimpleDateFormat("M/d", Locale.US)

        val parsedAscending = validEntries
            .distinctBy { it.dateString }
            .mapNotNull { entry ->
                val date = runCatching { parser.parse(entry.dateString) }.getOrNull() ?: return@mapNotNull null
                Triple(entry, date, shortFmt.format(date))
            }
            .sortedBy { it.second.time }

        if (parsedAscending.isEmpty()) {
            return BodyweightSummary(
                entriesDescending = validEntries.sortedByDescending { it.dateString },
                trendPoints = emptyList(),
                latestWeightLbs = validEntries.maxByOrNull { it.dateString }?.weightLbs,
                sevenDayAvgLbs = null,
                weeklyDeltaLbs = null,
                goalAlignmentLabel = "Log weigh-ins to track your 7-day trend"
            )
        }

        val points = parsedAscending.mapIndexed { idx, (entry, date, label) ->
            val windowStartMs = date.time - 6L * DAY_MS
            val windowWeights = parsedAscending
                .subList(0, idx + 1)
                .filter { it.second.time in windowStartMs..date.time }
                .map { it.first.weightLbs }

            val avg = if (windowWeights.isNotEmpty()) {
                windowWeights.average()
            } else {
                entry.weightLbs
            }

            BodyweightTrendPoint(
                dateString = entry.dateString,
                shortLabel = label,
                weightLbs = entry.weightLbs,
                sevenDayAvgLbs = Math.round(avg * 10.0) / 10.0,
                bodyFatPercent = entry.bodyFatPercent
            )
        }

        val latest = points.last()
        val latestDateMs = parsedAscending.last().second.time
        val priorCutoffMs = latestDateMs - 6L * DAY_MS
        val priorPoint = parsedAscending
            .zip(points)
            .filter { it.first.second.time < priorCutoffMs }
            .maxByOrNull { it.first.second.time }
            ?.second
            ?: points.firstOrNull()?.takeIf { points.size >= 2 && it != latest }

        val weeklyDeltaLbs = priorPoint?.let {
            Math.round((latest.sevenDayAvgLbs - it.sevenDayAvgLbs) * 10.0) / 10.0
        }

        val goalLabel = buildGoalAlignmentLabel(
            goal = goal,
            weeklyDeltaLbs = weeklyDeltaLbs,
            unit = unit
        )

        return BodyweightSummary(
            entriesDescending = parsedAscending.map { it.first }.reversed(),
            trendPoints = points,
            latestWeightLbs = latest.weightLbs,
            sevenDayAvgLbs = latest.sevenDayAvgLbs,
            weeklyDeltaLbs = weeklyDeltaLbs,
            goalAlignmentLabel = goalLabel
        )
    }

    private fun buildGoalAlignmentLabel(
        goal: FitnessGoal,
        weeklyDeltaLbs: Double?,
        unit: WeightUnit
    ): String {
        if (weeklyDeltaLbs == null) {
            return "Add 1+ week of weigh-ins for pace check"
        }
        val displayDelta = WeightUnitHelper.fromLbs(abs(weeklyDeltaLbs), unit)
        val sign = if (weeklyDeltaLbs > 0.05) "+" else if (weeklyDeltaLbs < -0.05) "-" else ""
        val formatted = "${sign}${WeightUnitHelper.formatWeightNumber(if (sign.isEmpty()) 0.0 else displayDelta, unit)} ${unit.shortLabel}/wk"

        return when (goal) {
            FitnessGoal.CUT -> when {
                weeklyDeltaLbs <= -0.3 -> "$formatted · On Cut Pace"
                weeklyDeltaLbs <= 0.2 -> "$formatted · Plateauing"
                else -> "$formatted · Trending Above Cut"
            }
            FitnessGoal.MAINTAIN -> when {
                abs(weeklyDeltaLbs) <= 0.5 -> "$formatted · Steady Maintenance"
                weeklyDeltaLbs > 0.5 -> "$formatted · Slight Surplus"
                else -> "$formatted · Slight Deficit"
            }
            FitnessGoal.BULK -> when {
                weeklyDeltaLbs in 0.25..1.2 -> "$formatted · Lean Bulk Pace"
                weeklyDeltaLbs > 1.2 -> "$formatted · Fast Surplus"
                else -> "$formatted · Needs More Fuel"
            }
        }
    }
}
