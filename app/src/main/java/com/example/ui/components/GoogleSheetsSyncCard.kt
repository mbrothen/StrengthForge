package com.example.ui.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserProfile
import com.example.data.sheets.GoogleSheetsAuthManager
import com.example.data.sheets.SheetAuthResult
import com.example.ui.theme.AmberGold
import com.example.ui.theme.ElectricTeal
import com.example.ui.theme.GainGreen
import com.example.ui.theme.LossRed
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.SheetSyncUiState
import com.example.viewmodel.UiState
import com.example.viewmodel.WorkoutViewModel
import kotlinx.coroutines.launch

@Composable
fun GoogleSheetsSyncCard(
    viewModel: WorkoutViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profileState by viewModel.userProfile.collectAsState()
    val syncState by viewModel.sheetSyncState.collectAsState()

    val profile = (profileState as? UiState.Success)?.data ?: UserProfile()
    val isLinked = profile.googleSheetId.isNotBlank()
    val isSyncing = syncState is SheetSyncUiState.Syncing

    var pendingForceNew by remember { mutableStateOf(false) }

    val authLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        if (activityResult.resultCode == Activity.RESULT_OK) {
            val token = GoogleSheetsAuthManager.extractAccessToken(context, activityResult.data)
            if (!token.isNullOrBlank()) {
                viewModel.syncToGoogleSheets(accessToken = token, forceCreateNew = pendingForceNew)
            } else {
                viewModel.setSheetSyncError("Could not read Google Sheets access token from consent result.")
            }
        } else {
            viewModel.setSheetSyncError("Google Sheets authorization was canceled.")
        }
    }

    fun triggerSync(forceNew: Boolean) {
        pendingForceNew = forceNew
        scope.launch {
            when (val auth = GoogleSheetsAuthManager.authorizeSheetsAccess(context)) {
                is SheetAuthResult.Authorized -> {
                    viewModel.syncToGoogleSheets(accessToken = auth.accessToken, forceCreateNew = forceNew)
                }
                is SheetAuthResult.NeedsConsent -> {
                    authLauncher.launch(IntentSenderRequest.Builder(auth.intentSender).build())
                }
                is SheetAuthResult.Error -> {
                    viewModel.setSheetSyncError(auth.message)
                }
            }
        }
    }

    fun openSheetUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No browser or Google Sheets app found to open link.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to open Google Sheet: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("google_sheets_sync_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, if (isLinked) ElectricTeal.copy(alpha = 0.6f) else SurfaceCardBorder)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF064E3B))
                            .border(1.dp, GainGreen.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.TableChart,
                            contentDescription = "Google Sheets",
                            tint = GainGreen,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Google Sheets & Charts",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isLinked) {
                                "Last synced: ${profile.lastSheetSyncAt.ifBlank { "Connected" }}"
                            } else {
                                "Export workout logs & auto-generate charts"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isLinked) Color(0xFF064E3B) else Color(0xFF1E293B))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isLinked) "LINKED" else "NOT LINKED",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isLinked) GainGreen else TextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Creates 4 tabs (Progress Dashboard, Workout Sessions, Exercise Progression, Exercise PRs) plus 4 embedded charts: Workout Volume, Lift 1RM Trend, Strength Gains & Muscle Balance.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFCBD5E1),
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (!isLinked) {
                Button(
                    onClick = { triggerSync(forceNew = false) },
                    enabled = !isSyncing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("sheets_create_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GainGreen,
                        contentColor = Color.Black
                    )
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.Black,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Creating Sheet & Charts...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(
                            imageVector = Icons.Default.TableChart,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create & Sync Google Sheet", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { triggerSync(forceNew = false) },
                        enabled = !isSyncing,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("sheets_sync_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricTeal,
                            contentColor = Color.Black
                        )
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Syncing...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sync Now", fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = { openSheetUrl(profile.googleSheetUrl) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("sheets_open_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, AmberGold),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberGold)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Sheet", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0B0F17))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Auto-sync on workout save/edit",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = profile.autoSyncSheets,
                        onCheckedChange = { viewModel.setAutoSyncSheets(it) },
                        modifier = Modifier.testTag("sheets_autosync_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = ElectricTeal,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = Color(0xFF1E293B)
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { triggerSync(forceNew = true) },
                        enabled = !isSyncing,
                        modifier = Modifier.testTag("sheets_create_new_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddCircleOutline,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Create Fresh Sheet",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = syncState is SheetSyncUiState.Success || syncState is SheetSyncUiState.Error
            ) {
                val isError = syncState is SheetSyncUiState.Error
                val statusMsg = when (val s = syncState) {
                    is SheetSyncUiState.Success -> s.message
                    is SheetSyncUiState.Error -> s.message
                    else -> ""
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isError) Color(0xFF3F151A) else Color(0xFF064E3B))
                        .border(
                            1.dp,
                            if (isError) LossRed.copy(alpha = 0.6f) else GainGreen.copy(alpha = 0.6f),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isError) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (isError) LossRed else GainGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = statusMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { viewModel.clearSheetSyncStatus() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss status",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
