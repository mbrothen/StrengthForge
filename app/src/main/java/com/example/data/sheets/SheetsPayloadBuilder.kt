package com.example.data.sheets

import com.example.data.analytics.WorkoutAnalyticsHelper
import com.example.data.model.DailyNutrition
import com.example.data.model.ExerciseRecord
import com.example.data.model.ExerciseSet
import com.example.data.model.ExerciseTrackingType
import com.example.data.model.UserProfile
import com.example.data.model.WorkoutExerciseLog
import com.example.data.model.WorkoutSession
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SheetTabIds(
    val dashboardId: Int = 0,
    val sessionsId: Int = 1,
    val progressionId: Int = 2,
    val prsId: Int = 3
)

data class SheetExportData(
    val batchClearJson: String,
    val batchUpdateValuesJson: String,
    val sessionRowCount: Int,
    val progressionRowCount: Int,
    val prRowCount: Int,
    val muscleDataStartRow: Int,
    val muscleDataEndRow: Int,
    val featuredHeaderRow: Int,
    val featuredEndRow: Int,
    val featuredExerciseName: String
)

object SheetsPayloadBuilder {

    const val SHEET_DASHBOARD = "Progress Dashboard"
    const val SHEET_SESSIONS = "Workout Sessions"
    const val SHEET_PROGRESSION = "Exercise Progression"
    const val SHEET_PRS = "Exercise PRs"

    val EXPECTED_TABS = listOf(
        SHEET_DASHBOARD,
        SHEET_SESSIONS,
        SHEET_PROGRESSION,
        SHEET_PRS
    )

    fun buildCreateSpreadsheetJson(
        title: String = "StrengthForge - Workout & Progress Tracker"
    ): String {
        val sheets = JSONArray()
        sheets.put(sheetSpec(0, SHEET_DASHBOARD, frozenRows = 1))
        sheets.put(sheetSpec(1, SHEET_SESSIONS, frozenRows = 1))
        sheets.put(sheetSpec(2, SHEET_PROGRESSION, frozenRows = 1))
        sheets.put(sheetSpec(3, SHEET_PRS, frozenRows = 1))

        return JSONObject().apply {
            put("properties", JSONObject().apply {
                put("title", title)
            })
            put("sheets", sheets)
        }.toString()
    }

    private fun sheetSpec(sheetId: Int, title: String, frozenRows: Int): JSONObject {
        return JSONObject().apply {
            put("properties", JSONObject().apply {
                put("sheetId", sheetId)
                put("title", title)
                put("gridProperties", JSONObject().apply {
                    put("frozenRowCount", frozenRows)
                })
            })
        }
    }

    fun buildAddMissingTabsJson(existingTitles: Set<String>): String? {
        val missing = EXPECTED_TABS.filterNot { existingTitles.contains(it) }
        if (missing.isEmpty()) return null

        val requests = JSONArray()
        missing.forEach { title ->
            requests.put(JSONObject().apply {
                put("addSheet", JSONObject().apply {
                    put("properties", JSONObject().apply {
                        put("title", title)
                        put("gridProperties", JSONObject().apply {
                            put("frozenRowCount", 1)
                        })
                    })
                })
            })
        }
        return JSONObject().apply { put("requests", requests) }.toString()
    }

    fun buildExportData(
        profile: UserProfile,
        workouts: List<WorkoutSession>,
        logs: List<WorkoutExerciseLog>,
        exercises: List<ExerciseRecord>,
        todayNutrition: DailyNutrition?,
        syncedAtLabel: String,
        nutritionHistory: List<DailyNutrition> = emptyList()
    ): SheetExportData {
        val isoSdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        // Chronological order (oldest -> newest) so time-series charts flow left-to-right
        val sortedWorkouts = workouts.sortedBy { it.createdAt?.toDate()?.time ?: 0L }
        val sortedLogs = logs.sortedBy { WorkoutAnalyticsHelper.resolveLogDate(it)?.time ?: 0L }
        val sortedExercises = exercises.sortedWith(
            compareByDescending<ExerciseRecord> { it.isPrimary }.thenBy { it.name }
        )

        // 1. Workout Sessions tab
        val sessionRows = JSONArray()
        sessionRows.put(
            jsonRow(
                "Date",
                "Workout Name",
                "Duration (min)",
                "Exercises",
                "Sets Completed",
                "Total Volume (lbs)",
                "Notes"
            )
        )
        sortedWorkouts.forEach { w ->
            val dateVal = w.createdAt?.toDate()?.let { isoSdf.format(it) } ?: w.dateString
            sessionRows.put(
                jsonRow(
                    dateVal,
                    w.name,
                    w.durationMinutes,
                    w.exerciseCount,
                    w.completedSetsCount,
                    roundOneDecimal(w.totalVolumeLbs),
                    w.notes
                )
            )
        }

        // 2. Exercise Progression tab
        val progressionRows = JSONArray()
        progressionRows.put(
            jsonRow(
                "Date",
                "Exercise",
                "Muscle Category",
                "Max Weight (lbs)",
                "Est. 1RM (lbs)",
                "Total Volume (lbs)",
                "Set Details"
            )
        )
        sortedLogs.forEach { log ->
            val dateVal = log.date?.toDate()?.let { isoSdf.format(it) } ?: log.dateString
            val tracking = log.resolvedTrackingType()
            val setSummary = log.sets.joinToString(", ") { map ->
                val s = ExerciseSet.fromMap(map)
                val wLabel = if (s.weightLbs % 1.0 == 0.0) s.weightLbs.toInt().toString() else s.weightLbs.toString()
                when (tracking) {
                    ExerciseTrackingType.TIMED_HOLD -> {
                        if (s.weightLbs > 0.0) "+${wLabel}lb×${s.durationSeconds}s" else "${s.durationSeconds}s"
                    }
                    ExerciseTrackingType.BODYWEIGHT_REPS -> {
                        if (s.weightLbs > 0.0) "BW+${wLabel}×${s.reps}" else "BW×${s.reps}"
                    }
                    ExerciseTrackingType.WEIGHTED -> "${wLabel}x${s.reps}"
                }
            }
            progressionRows.put(
                jsonRow(
                    dateVal,
                    log.exerciseName,
                    log.category.ifBlank { "General" },
                    roundOneDecimal(log.maxWeightLbs),
                    roundOneDecimal(log.estimatedOneRepMaxLbs),
                    roundOneDecimal(log.totalVolumeLbs),
                    setSummary
                )
            )
        }

        // 3. Exercise PRs tab (only exercises that have actually been performed, computed from logs)
        val performedPrs = WorkoutAnalyticsHelper.computePerformedExercisePrs(sortedLogs, sortedExercises)
        val prRows = JSONArray()
        prRows.put(
            jsonRow(
                "Exercise",
                "Muscle Category",
                "Starting Weight (lbs)",
                "Current Max (lbs)",
                "Est. 1RM (lbs)",
                "Gain (%)",
                "Sessions Logged",
                "Notes"
            )
        )
        performedPrs.forEach { pr ->
            val trackingSuffix = when (pr.trackingType) {
                ExerciseTrackingType.TIMED_HOLD -> "[Timed: best ${pr.bestDurationSeconds}s]"
                ExerciseTrackingType.BODYWEIGHT_REPS -> "[Bodyweight: ${pr.formatPrimaryMetricLabel()}]"
                ExerciseTrackingType.WEIGHTED -> ""
            }
            val combinedNotes = listOf(trackingSuffix, pr.exercise.notes).filter { it.isNotBlank() }.joinToString(" ")
            prRows.put(
                jsonRow(
                    pr.exercise.name,
                    pr.exercise.category,
                    roundOneDecimal(pr.startingWeightLbs),
                    roundOneDecimal(pr.maxWeightLbs),
                    roundOneDecimal(pr.best1RM),
                    roundOneDecimal(pr.percentIncrease),
                    pr.sessionsCount,
                    combinedNotes
                )
            )
        }

        // 4. Progress Dashboard tab (KPIs + Muscle Breakdown + Featured Lift Timeline)
        val totalVolComputed = if (sortedWorkouts.isNotEmpty()) {
            sortedWorkouts.sumOf { it.totalVolumeLbs }
        } else {
            profile.totalLifetimeVolumeLbs
        }
        val workoutsCount = if (sortedWorkouts.isNotEmpty()) sortedWorkouts.size else profile.totalWorkoutsLogged

        val dashboardRows = JSONArray()
        // Row 0
        dashboardRows.put(
            jsonRow(
                "STRENGTHFORGE — TRAINING & PROGRESS DASHBOARD",
                "",
                "Last Synced",
                syncedAtLabel
            )
        )
        val liveWeeklyStreak = if (sortedWorkouts.isNotEmpty()) {
            WorkoutAnalyticsHelper.computeWeeklyStreak(sortedWorkouts)
        } else {
            profile.currentWeeklyStreak
        }
        // Row 1
        dashboardRows.put(
            jsonRow(
                "Athlete",
                profile.displayName,
                "Weekly Streak",
                liveWeeklyStreak
            )
        )
        // Row 2
        dashboardRows.put(
            jsonRow(
                "Total Workouts Logged",
                workoutsCount,
                "Lifetime Volume (lbs)",
                roundOneDecimal(totalVolComputed)
            )
        )
        // Row 3
        dashboardRows.put(
            jsonRow(
                "Daily Calorie Target",
                profile.targetCalories,
                "Today's Logged Calories",
                todayNutrition?.calories ?: 0
            )
        )
        // Row 4
        dashboardRows.put(
            jsonRow(
                "Macro Targets (P / C / F g)",
                "${profile.proteinGrams}g / ${profile.carbsGrams}g / ${profile.fatsGrams}g",
                "Today's Protein (g)",
                todayNutrition?.proteinG ?: 0
            )
        )
        // Row 5 (spacer)
        dashboardRows.put(jsonRow())
        // Row 6 (Muscle table header)
        dashboardRows.put(
            jsonRow(
                "Muscle Group",
                "Total Volume (lbs)",
                "Volume Share (%)",
                ""
            )
        )

        val muscleTotals = linkedMapOf(
            "Chest" to 0.0,
            "Back" to 0.0,
            "Legs" to 0.0,
            "Shoulders" to 0.0,
            "Arms" to 0.0,
            "Core" to 0.0
        )
        sortedLogs.forEach { log ->
            val cat = log.category.ifBlank { "Chest" }
            muscleTotals[cat] = (muscleTotals[cat] ?: 0.0) + log.totalVolumeLbs
        }
        val nonZeroMuscles = muscleTotals.filterValues { it > 0.0 }
            .ifEmpty { mapOf("No Muscle Logs Yet" to 0.0) }
        val grandMuscleVol = nonZeroMuscles.values.sum().coerceAtLeast(1.0)

        val muscleDataStartRow = 7
        nonZeroMuscles.forEach { (cat, vol) ->
            val sharePct = roundOneDecimal((vol / grandMuscleVol) * 100.0)
            dashboardRows.put(jsonRow(cat, roundOneDecimal(vol), sharePct, ""))
        }
        val muscleDataEndRow = muscleDataStartRow + nonZeroMuscles.size

        // Blank spacer
        dashboardRows.put(jsonRow())

        // Choose featured lift: prefer bench_press if logs exist, else most-logged exercise
        val benchRecord = exercises.find { it.id == "bench_press" }
        val benchLogs = sortedLogs.filter { log ->
            if (benchRecord != null) {
                benchRecord.matchesLog(log)
            } else {
                log.exerciseId == "bench_press" ||
                    log.exerciseName.equals("Barbell Bench Press", ignoreCase = true)
            }
        }
        val featuredLogs: List<WorkoutExerciseLog>
        val featuredName: String
        if (benchLogs.isNotEmpty()) {
            featuredLogs = benchLogs
            featuredName = benchLogs.first().exerciseName.ifBlank { "Barbell Bench Press" }
        } else {
            val mostLoggedGroup = sortedLogs.groupBy { it.exerciseId.ifBlank { it.exerciseName } }
                .maxByOrNull { it.value.size }
            featuredLogs = mostLoggedGroup?.value ?: emptyList()
            featuredName = featuredLogs.firstOrNull()?.exerciseName
                ?: "Featured Lift"
        }

        val featuredHeaderRow = muscleDataEndRow + 1
        dashboardRows.put(
            jsonRow(
                "$featuredName (Date)",
                "Working Weight (lbs)",
                "Est. 1RM (lbs)",
                "Volume (lbs)"
            )
        )

        featuredLogs.forEach { log ->
            val dateVal = log.date?.toDate()?.let { isoSdf.format(it) } ?: log.dateString
            val est1RM = if (log.estimatedOneRepMaxLbs > 0.0) {
                log.estimatedOneRepMaxLbs
            } else {
                val firstReps = log.sets.firstOrNull()?.let { (it["reps"] as? Number)?.toInt() } ?: 8
                log.maxWeightLbs * (1.0 + firstReps / 30.0)
            }
            dashboardRows.put(
                jsonRow(
                    dateVal,
                    roundOneDecimal(log.maxWeightLbs),
                    roundOneDecimal(est1RM),
                    roundOneDecimal(log.totalVolumeLbs)
                )
            )
        }
        val featuredEndRow = featuredHeaderRow + 1 + featuredLogs.size

        // Multi-day nutrition compliance section (placed after chart source ranges so chart anchors remain fixed)
        val combinedNutrition = (nutritionHistory + listOfNotNull(todayNutrition))
            .distinctBy { it.dateString.ifBlank { it.id } }
            .filter { it.dateString.isNotBlank() || it.id.isNotBlank() }
            .sortedBy { it.dateString.ifBlank { it.id } }
        if (combinedNutrition.isNotEmpty()) {
            dashboardRows.put(jsonRow())
            dashboardRows.put(
                jsonRow(
                    "Nutrition Date",
                    "Calories (kcal)",
                    "Macros (P / C / F g)",
                    "Within Target (${profile.targetCaloriesMin}-${profile.targetCaloriesMax})"
                )
            )
            combinedNutrition.forEach { day ->
                val dateLabel = day.dateString.ifBlank { day.id }
                val inRange = if (day.calories in profile.targetCaloriesMin..profile.targetCaloriesMax) "Yes" else "No"
                dashboardRows.put(
                    jsonRow(
                        dateLabel,
                        day.calories,
                        "${day.proteinG}g / ${day.carbsG}g / ${day.fatsG}g",
                        inRange
                    )
                )
            }
        }

        val clearRanges = JSONArray().apply {
            put("'$SHEET_DASHBOARD'!A1:D1000")
            put("'$SHEET_SESSIONS'!A1:G5000")
            put("'$SHEET_PROGRESSION'!A1:G10000")
            put("'$SHEET_PRS'!A1:H1000")
        }
        val batchClearJson = JSONObject().apply {
            put("ranges", clearRanges)
        }.toString()

        val valueRanges = JSONArray().apply {
            put(valueRange("'$SHEET_DASHBOARD'!A1", dashboardRows))
            put(valueRange("'$SHEET_SESSIONS'!A1", sessionRows))
            put(valueRange("'$SHEET_PROGRESSION'!A1", progressionRows))
            put(valueRange("'$SHEET_PRS'!A1", prRows))
        }
        val batchUpdateValuesJson = JSONObject().apply {
            put("valueInputOption", "USER_ENTERED")
            put("data", valueRanges)
        }.toString()

        return SheetExportData(
            batchClearJson = batchClearJson,
            batchUpdateValuesJson = batchUpdateValuesJson,
            sessionRowCount = sortedWorkouts.size,
            progressionRowCount = sortedLogs.size,
            prRowCount = performedPrs.size,
            muscleDataStartRow = muscleDataStartRow,
            muscleDataEndRow = muscleDataEndRow,
            featuredHeaderRow = featuredHeaderRow,
            featuredEndRow = featuredEndRow,
            featuredExerciseName = featuredName
        )
    }

    fun buildFormattingAndChartsBatchUpdateJson(
        tabIds: SheetTabIds,
        exportData: SheetExportData,
        existingDashboardChartIds: List<Int>
    ): String {
        val requests = JSONArray()

        // 1. Delete existing charts on dashboard so re-syncing cleanly updates chart ranges without duplicates
        existingDashboardChartIds.forEach { chartId ->
            requests.put(JSONObject().apply {
                put("deleteEmbeddedObject", JSONObject().apply {
                    put("objectId", chartId)
                })
            })
        }

        // 2. Header formatting across tabs
        requests.put(
            headerStyleRequest(
                sheetId = tabIds.dashboardId,
                startRow = 0,
                endRow = 1,
                endCol = 4,
                bgRgb = Triple(0.06, 0.09, 0.16),
                fgRgb = Triple(0.96, 0.62, 0.04),
                fontSize = 11
            )
        )
        requests.put(
            headerStyleRequest(
                sheetId = tabIds.dashboardId,
                startRow = 6,
                endRow = 7,
                endCol = 3,
                bgRgb = Triple(0.12, 0.16, 0.23),
                fgRgb = Triple(0.18, 0.83, 0.75),
                fontSize = 10
            )
        )
        requests.put(
            headerStyleRequest(
                sheetId = tabIds.dashboardId,
                startRow = exportData.featuredHeaderRow,
                endRow = exportData.featuredHeaderRow + 1,
                endCol = 4,
                bgRgb = Triple(0.12, 0.16, 0.23),
                fgRgb = Triple(0.96, 0.62, 0.04),
                fontSize = 10
            )
        )
        requests.put(
            headerStyleRequest(
                sheetId = tabIds.sessionsId,
                startRow = 0,
                endRow = 1,
                endCol = 7,
                bgRgb = Triple(0.06, 0.09, 0.16),
                fgRgb = Triple(0.96, 0.62, 0.04),
                fontSize = 10
            )
        )
        requests.put(
            headerStyleRequest(
                sheetId = tabIds.progressionId,
                startRow = 0,
                endRow = 1,
                endCol = 7,
                bgRgb = Triple(0.06, 0.09, 0.16),
                fgRgb = Triple(0.96, 0.62, 0.04),
                fontSize = 10
            )
        )
        requests.put(
            headerStyleRequest(
                sheetId = tabIds.prsId,
                startRow = 0,
                endRow = 1,
                endCol = 8,
                bgRgb = Triple(0.06, 0.09, 0.16),
                fgRgb = Triple(0.96, 0.62, 0.04),
                fontSize = 10
            )
        )

        // 3. Auto-resize data columns on all 4 sheets
        requests.put(autoResizeColumnsRequest(tabIds.dashboardId, 0, 4))
        requests.put(autoResizeColumnsRequest(tabIds.sessionsId, 0, 7))
        requests.put(autoResizeColumnsRequest(tabIds.progressionId, 0, 7))
        requests.put(autoResizeColumnsRequest(tabIds.prsId, 0, 8))

        // 4. Embedded Progress Charts anchored cleanly in Column E (columnIndex = 4) of Progress Dashboard
        var nextAnchorRow = 0

        // Chart 1: Workout Volume Over Time (AREA chart)
        if (exportData.sessionRowCount > 0) {
            val endRow = exportData.sessionRowCount + 1
            requests.put(
                basicChartRequest(
                    dashboardSheetId = tabIds.dashboardId,
                    anchorRow = nextAnchorRow,
                    anchorCol = 4,
                    title = "Workout Volume Over Time (lbs)",
                    chartType = "AREA",
                    xAxisTitle = "Session Date",
                    yAxisTitle = "Total Volume (lbs)",
                    domainRange = gridRange(tabIds.sessionsId, 0, endRow, 0, 1),
                    seriesSpecs = listOf(
                        SeriesSpec(
                            range = gridRange(tabIds.sessionsId, 0, endRow, 5, 6),
                            rgb = Triple(0.96, 0.62, 0.04) // AmberGold
                        )
                    )
                )
            )
            nextAnchorRow += 18
        }

        // Chart 2: Featured Lift Weight & Est. 1RM Progression (LINE chart)
        if (exportData.featuredEndRow > exportData.featuredHeaderRow + 1) {
            requests.put(
                basicChartRequest(
                    dashboardSheetId = tabIds.dashboardId,
                    anchorRow = nextAnchorRow,
                    anchorCol = 4,
                    title = "${exportData.featuredExerciseName} — Weight & Est. 1RM (lbs)",
                    chartType = "LINE",
                    xAxisTitle = "Date",
                    yAxisTitle = "Weight (lbs)",
                    domainRange = gridRange(
                        tabIds.dashboardId,
                        exportData.featuredHeaderRow,
                        exportData.featuredEndRow,
                        0,
                        1
                    ),
                    seriesSpecs = listOf(
                        SeriesSpec(
                            range = gridRange(
                                tabIds.dashboardId,
                                exportData.featuredHeaderRow,
                                exportData.featuredEndRow,
                                1,
                                2
                            ),
                            rgb = Triple(0.96, 0.62, 0.04) // Working Weight
                        ),
                        SeriesSpec(
                            range = gridRange(
                                tabIds.dashboardId,
                                exportData.featuredHeaderRow,
                                exportData.featuredEndRow,
                                2,
                                3
                            ),
                            rgb = Triple(0.02, 0.71, 0.83) // Est 1RM
                        )
                    )
                )
            )
            nextAnchorRow += 18
        }

        // Chart 3: Exercise PRs Starting vs Current Max (COLUMN chart)
        if (exportData.prRowCount > 0) {
            val endRow = exportData.prRowCount + 1
            requests.put(
                basicChartRequest(
                    dashboardSheetId = tabIds.dashboardId,
                    anchorRow = nextAnchorRow,
                    anchorCol = 4,
                    title = "Strength Gains: Starting vs Current Max (lbs)",
                    chartType = "COLUMN",
                    xAxisTitle = "Exercise",
                    yAxisTitle = "Weight (lbs)",
                    domainRange = gridRange(tabIds.prsId, 0, endRow, 0, 1),
                    seriesSpecs = listOf(
                        SeriesSpec(
                            range = gridRange(tabIds.prsId, 0, endRow, 2, 3),
                            rgb = Triple(0.39, 0.45, 0.55) // Starting Weight
                        ),
                        SeriesSpec(
                            range = gridRange(tabIds.prsId, 0, endRow, 3, 4),
                            rgb = Triple(0.08, 0.72, 0.65) // Current Max
                        )
                    )
                )
            )
            nextAnchorRow += 18
        }

        // Chart 4: Muscle Group Volume Distribution (Doughnut Pie Chart)
        if (exportData.muscleDataEndRow > exportData.muscleDataStartRow &&
            exportData.progressionRowCount > 0
        ) {
            requests.put(
                pieChartRequest(
                    dashboardSheetId = tabIds.dashboardId,
                    anchorRow = nextAnchorRow,
                    anchorCol = 4,
                    title = "Training Volume by Muscle Group (lbs)",
                    domainRange = gridRange(
                        tabIds.dashboardId,
                        exportData.muscleDataStartRow,
                        exportData.muscleDataEndRow,
                        0,
                        1
                    ),
                    seriesRange = gridRange(
                        tabIds.dashboardId,
                        exportData.muscleDataStartRow,
                        exportData.muscleDataEndRow,
                        1,
                        2
                    )
                )
            )
        }

        return JSONObject().apply {
            put("requests", requests)
        }.toString()
    }

    private data class SeriesSpec(
        val range: JSONObject,
        val rgb: Triple<Double, Double, Double>
    )

    private fun basicChartRequest(
        dashboardSheetId: Int,
        anchorRow: Int,
        anchorCol: Int,
        title: String,
        chartType: String,
        xAxisTitle: String,
        yAxisTitle: String,
        domainRange: JSONObject,
        seriesSpecs: List<SeriesSpec>
    ): JSONObject {
        val seriesArr = JSONArray()
        seriesSpecs.forEach { spec ->
            seriesArr.put(JSONObject().apply {
                put("series", JSONObject().apply {
                    put("sourceRange", JSONObject().apply {
                        put("sources", JSONArray().put(spec.range))
                    })
                })
                put("targetAxis", "LEFT_AXIS")
                put("colorStyle", JSONObject().apply {
                    put("rgbColor", rgbObject(spec.rgb))
                })
            })
        }

        return JSONObject().apply {
            put("addChart", JSONObject().apply {
                put("chart", JSONObject().apply {
                    put("spec", JSONObject().apply {
                        put("title", title)
                        put("basicChart", JSONObject().apply {
                            put("chartType", chartType)
                            put("legendPosition", "BOTTOM_LEGEND")
                            put("headerCount", 1)
                            if (chartType in setOf("LINE", "AREA", "SCATTER")) {
                                put("interpolateNulls", true)
                            }
                            put("axis", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("position", "BOTTOM_AXIS")
                                    put("title", xAxisTitle)
                                })
                                put(JSONObject().apply {
                                    put("position", "LEFT_AXIS")
                                    put("title", yAxisTitle)
                                })
                            })
                            put("domains", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("domain", JSONObject().apply {
                                        put("sourceRange", JSONObject().apply {
                                            put("sources", JSONArray().put(domainRange))
                                        })
                                    })
                                })
                            })
                            put("series", seriesArr)
                        })
                    })
                    put("position", chartPosition(dashboardSheetId, anchorRow, anchorCol))
                })
            })
        }
    }

    private fun pieChartRequest(
        dashboardSheetId: Int,
        anchorRow: Int,
        anchorCol: Int,
        title: String,
        domainRange: JSONObject,
        seriesRange: JSONObject
    ): JSONObject {
        return JSONObject().apply {
            put("addChart", JSONObject().apply {
                put("chart", JSONObject().apply {
                    put("spec", JSONObject().apply {
                        put("title", title)
                        put("pieChart", JSONObject().apply {
                            put("legendPosition", "RIGHT_LEGEND")
                            put("threeDimensional", false)
                            put("pieHole", 0.4)
                            put("domain", JSONObject().apply {
                                put("sourceRange", JSONObject().apply {
                                    put("sources", JSONArray().put(domainRange))
                                })
                            })
                            put("series", JSONObject().apply {
                                put("sourceRange", JSONObject().apply {
                                    put("sources", JSONArray().put(seriesRange))
                                })
                            })
                        })
                    })
                    put("position", chartPosition(dashboardSheetId, anchorRow, anchorCol))
                })
            })
        }
    }

    private fun chartPosition(sheetId: Int, anchorRow: Int, anchorCol: Int): JSONObject {
        return JSONObject().apply {
            put("overlayPosition", JSONObject().apply {
                put("anchorCell", JSONObject().apply {
                    put("sheetId", sheetId)
                    put("rowIndex", anchorRow)
                    put("columnIndex", anchorCol)
                })
                put("offsetXPixels", 16)
                put("offsetYPixels", 4)
                put("widthPixels", 660)
                put("heightPixels", 330)
            })
        }
    }

    private fun headerStyleRequest(
        sheetId: Int,
        startRow: Int,
        endRow: Int,
        endCol: Int,
        bgRgb: Triple<Double, Double, Double>,
        fgRgb: Triple<Double, Double, Double>,
        fontSize: Int
    ): JSONObject {
        return JSONObject().apply {
            put("repeatCell", JSONObject().apply {
                put("range", gridRange(sheetId, startRow, endRow, 0, endCol))
                put("cell", JSONObject().apply {
                    put("userEnteredFormat", JSONObject().apply {
                        put("backgroundColor", rgbObject(bgRgb))
                        put("textFormat", JSONObject().apply {
                            put("foregroundColor", rgbObject(fgRgb))
                            put("bold", true)
                            put("fontSize", fontSize)
                        })
                    })
                })
                put("fields", "userEnteredFormat(backgroundColor,textFormat)")
            })
        }
    }

    private fun autoResizeColumnsRequest(sheetId: Int, startCol: Int, endCol: Int): JSONObject {
        return JSONObject().apply {
            put("autoResizeDimensions", JSONObject().apply {
                put("dimensions", JSONObject().apply {
                    put("sheetId", sheetId)
                    put("dimension", "COLUMNS")
                    put("startIndex", startCol)
                    put("endIndex", endCol)
                })
            })
        }
    }

    private fun gridRange(
        sheetId: Int,
        startRow: Int,
        endRow: Int,
        startCol: Int,
        endCol: Int
    ): JSONObject {
        return JSONObject().apply {
            put("sheetId", sheetId)
            put("startRowIndex", startRow)
            put("endRowIndex", endRow)
            put("startColumnIndex", startCol)
            put("endColumnIndex", endCol)
        }
    }

    private fun rgbObject(rgb: Triple<Double, Double, Double>): JSONObject {
        return JSONObject().apply {
            put("red", rgb.first)
            put("green", rgb.second)
            put("blue", rgb.third)
        }
    }

    private fun valueRange(range: String, values: JSONArray): JSONObject {
        return JSONObject().apply {
            put("range", range)
            put("majorDimension", "ROWS")
            put("values", values)
        }
    }

    private fun jsonRow(vararg items: Any): JSONArray {
        val arr = JSONArray()
        items.forEach { arr.put(it) }
        return arr
    }

    private fun roundOneDecimal(value: Double): Double {
        return Math.round(value * 10.0) / 10.0
    }
}
