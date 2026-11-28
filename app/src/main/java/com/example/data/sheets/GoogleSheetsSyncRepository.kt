package com.example.data.sheets

import com.example.data.model.DailyNutrition
import com.example.data.model.ExerciseRecord
import com.example.data.model.UserProfile
import com.example.data.model.WorkoutExerciseLog
import com.example.data.model.WorkoutSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class SheetSyncResult(
    val spreadsheetId: String,
    val spreadsheetUrl: String,
    val syncedAtLabel: String,
    val createdNewSheet: Boolean,
    val chartCount: Int
)

class SheetsApiException(
    val httpCode: Int,
    message: String
) : Exception(message)

class GoogleSheetsSyncRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    private val baseUrl = "https://sheets.googleapis.com/v4/spreadsheets"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun createOrSyncSpreadsheet(
        accessToken: String,
        existingSpreadsheetId: String?,
        forceCreateNew: Boolean,
        profile: UserProfile,
        workouts: List<WorkoutSession>,
        logs: List<WorkoutExerciseLog>,
        exercises: List<ExerciseRecord>,
        todayNutrition: DailyNutrition?,
        nutritionHistory: List<DailyNutrition> = emptyList()
    ): SheetSyncResult = withContext(Dispatchers.IO) {
        val syncTimeSdf = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.US)
        val syncedAtLabel = syncTimeSdf.format(Date())

        var targetSpreadsheetId = if (forceCreateNew) "" else existingSpreadsheetId.orEmpty().trim()
        var targetSpreadsheetUrl = ""
        var createdNew = false
        var tabIds = SheetTabIds()
        var existingChartIds = emptyList<Int>()

        if (targetSpreadsheetId.isNotBlank()) {
            val metadata = fetchSpreadsheetMetadataOrNull(accessToken, targetSpreadsheetId)
            if (metadata != null) {
                targetSpreadsheetUrl = metadata.spreadsheetUrl
                val missingTabsJson = SheetsPayloadBuilder.buildAddMissingTabsJson(metadata.tabIdsByTitle.keys)
                if (missingTabsJson != null) {
                    executePost(
                        url = "$baseUrl/$targetSpreadsheetId:batchUpdate",
                        accessToken = accessToken,
                        jsonBody = missingTabsJson
                    )
                    val refreshed = fetchSpreadsheetMetadataOrNull(accessToken, targetSpreadsheetId)
                    if (refreshed != null) {
                        tabIds = refreshed.toSheetTabIds()
                        existingChartIds = refreshed.dashboardChartIds
                    }
                } else {
                    tabIds = metadata.toSheetTabIds()
                    existingChartIds = metadata.dashboardChartIds
                }
            } else {
                // Sheet was deleted or inaccessible; create a fresh one
                targetSpreadsheetId = ""
            }
        }

        if (targetSpreadsheetId.isBlank()) {
            val createJson = SheetsPayloadBuilder.buildCreateSpreadsheetJson(
                title = "StrengthForge - ${profile.displayName.ifBlank { "Workout" }} Progress"
            )
            val createdResp = executePost(
                url = baseUrl,
                accessToken = accessToken,
                jsonBody = createJson
            )
            val json = JSONObject(createdResp)
            targetSpreadsheetId = json.getString("spreadsheetId")
            targetSpreadsheetUrl = json.optString(
                "spreadsheetUrl",
                "https://docs.google.com/spreadsheets/d/$targetSpreadsheetId/edit"
            )
            createdNew = true
            tabIds = SheetTabIds(0, 1, 2, 3)
            existingChartIds = emptyList()
        }

        val exportData = SheetsPayloadBuilder.buildExportData(
            profile = profile,
            workouts = workouts,
            logs = logs,
            exercises = exercises,
            todayNutrition = todayNutrition,
            syncedAtLabel = syncedAtLabel,
            nutritionHistory = nutritionHistory
        )

        // Clear old values so deleted workouts/exercises never leave trailing rows
        executePost(
            url = "$baseUrl/$targetSpreadsheetId/values:batchClear",
            accessToken = accessToken,
            jsonBody = exportData.batchClearJson
        )

        // Write updated tabular data
        executePost(
            url = "$baseUrl/$targetSpreadsheetId/values:batchUpdate",
            accessToken = accessToken,
            jsonBody = exportData.batchUpdateValuesJson
        )

        // Apply formatting and regenerate progress charts cleanly
        val chartsBatchJson = SheetsPayloadBuilder.buildFormattingAndChartsBatchUpdateJson(
            tabIds = tabIds,
            exportData = exportData,
            existingDashboardChartIds = existingChartIds,
            unit = profile.resolvedWeightUnit()
        )
        executePost(
            url = "$baseUrl/$targetSpreadsheetId:batchUpdate",
            accessToken = accessToken,
            jsonBody = chartsBatchJson
        )

        var chartCount = 0
        if (exportData.sessionRowCount > 0) chartCount++
        if (exportData.featuredEndRow > exportData.featuredHeaderRow + 1) chartCount++
        if (exportData.prRowCount > 0) chartCount++
        if (exportData.muscleDataEndRow > exportData.muscleDataStartRow && exportData.progressionRowCount > 0) chartCount++

        SheetSyncResult(
            spreadsheetId = targetSpreadsheetId,
            spreadsheetUrl = targetSpreadsheetUrl.ifBlank {
                "https://docs.google.com/spreadsheets/d/$targetSpreadsheetId/edit"
            },
            syncedAtLabel = syncedAtLabel,
            createdNewSheet = createdNew,
            chartCount = chartCount
        )
    }

    private data class ExistingSheetMetadata(
        val spreadsheetId: String,
        val spreadsheetUrl: String,
        val tabIdsByTitle: Map<String, Int>,
        val dashboardChartIds: List<Int>
    ) {
        fun toSheetTabIds(): SheetTabIds {
            return SheetTabIds(
                dashboardId = tabIdsByTitle[SheetsPayloadBuilder.SHEET_DASHBOARD] ?: 0,
                sessionsId = tabIdsByTitle[SheetsPayloadBuilder.SHEET_SESSIONS] ?: 1,
                progressionId = tabIdsByTitle[SheetsPayloadBuilder.SHEET_PROGRESSION] ?: 2,
                prsId = tabIdsByTitle[SheetsPayloadBuilder.SHEET_PRS] ?: 3
            )
        }
    }

    private fun fetchSpreadsheetMetadataOrNull(
        accessToken: String,
        spreadsheetId: String
    ): ExistingSheetMetadata? {
        val url = "$baseUrl/$spreadsheetId?fields=spreadsheetId,spreadsheetUrl,sheets(properties(sheetId,title),charts.chartId)"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 404) return null
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw SheetsApiException(
                    response.code,
                    parseGoogleError(body, "Failed to inspect spreadsheet (${response.code})")
                )
            }
            val json = JSONObject(body)
            val sheetUrl = json.optString(
                "spreadsheetUrl",
                "https://docs.google.com/spreadsheets/d/$spreadsheetId/edit"
            )
            val sheetsArr = json.optJSONArray("sheets") ?: return null
            val tabMap = mutableMapOf<String, Int>()
            val chartIds = mutableListOf<Int>()

            for (i in 0 until sheetsArr.length()) {
                val sheetObj = sheetsArr.getJSONObject(i)
                val props = sheetObj.getJSONObject("properties")
                val title = props.getString("title")
                val id = props.getInt("sheetId")
                tabMap[title] = id

                if (title == SheetsPayloadBuilder.SHEET_DASHBOARD) {
                    val charts = sheetObj.optJSONArray("charts")
                    if (charts != null) {
                        for (c in 0 until charts.length()) {
                            chartIds.add(charts.getJSONObject(c).getInt("chartId"))
                        }
                    }
                }
            }

            return ExistingSheetMetadata(
                spreadsheetId = spreadsheetId,
                spreadsheetUrl = sheetUrl,
                tabIdsByTitle = tabMap,
                dashboardChartIds = chartIds
            )
        }
    }

    private fun executePost(
        url: String,
        accessToken: String,
        jsonBody: String
    ): String {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .post(jsonBody.toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw SheetsApiException(
                    response.code,
                    parseGoogleError(body, "Google Sheets API error (${response.code})")
                )
            }
            return body
        }
    }

    private fun parseGoogleError(body: String, fallback: String): String {
        return try {
            val err = JSONObject(body).optJSONObject("error")
            err?.optString("message")?.takeIf { it.isNotBlank() } ?: fallback
        } catch (_: Exception) {
            fallback
        }
    }
}
