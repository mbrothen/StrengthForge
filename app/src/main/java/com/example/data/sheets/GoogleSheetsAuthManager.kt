package com.example.data.sheets

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await

sealed interface SheetAuthResult {
    data class Authorized(val accessToken: String) : SheetAuthResult
    data class NeedsConsent(val intentSender: IntentSender) : SheetAuthResult
    data class Error(val message: String) : SheetAuthResult
}

object GoogleSheetsAuthManager {
    private val SHEETS_SCOPE = Scope("https://www.googleapis.com/auth/spreadsheets")

    suspend fun authorizeSheetsAccess(context: Context): SheetAuthResult {
        return try {
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(SHEETS_SCOPE))
                .build()

            val result = Identity.getAuthorizationClient(context)
                .authorize(request)
                .await()

            when {
                result.hasResolution() && result.pendingIntent != null -> {
                    SheetAuthResult.NeedsConsent(result.pendingIntent!!.intentSender)
                }
                !result.accessToken.isNullOrBlank() -> {
                    SheetAuthResult.Authorized(result.accessToken!!)
                }
                else -> {
                    SheetAuthResult.Error("Unable to obtain Google Sheets access token.")
                }
            }
        } catch (e: ApiException) {
            SheetAuthResult.Error(e.localizedMessage ?: "Google authorization error (${e.statusCode})")
        } catch (e: Exception) {
            SheetAuthResult.Error(e.localizedMessage ?: "Google authorization failed")
        }
    }

    suspend fun tryGetSilentToken(context: Context): String? {
        return when (val res = authorizeSheetsAccess(context)) {
            is SheetAuthResult.Authorized -> res.accessToken
            else -> null
        }
    }

    fun extractAccessToken(context: Context, data: Intent?): String? {
        if (data == null) return null
        return try {
            val result = Identity.getAuthorizationClient(context)
                .getAuthorizationResultFromIntent(data)
            result.accessToken
        } catch (_: Exception) {
            null
        }
    }
}
