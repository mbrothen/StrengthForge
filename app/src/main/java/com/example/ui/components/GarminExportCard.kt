package com.example.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.core.content.FileProvider
import com.example.data.model.WorkoutSession
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.ElectricTeal
import com.example.ui.theme.GainGreen
import com.example.ui.theme.LossRed
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.GarminExportUiState
import com.example.viewmodel.WorkoutViewModel
import java.io.File

private const val GARMIN_IMPORT_URL = "https://connect.garmin.com/modern/import-data"

@Composable
fun GarminExportCard(
    viewModel: WorkoutViewModel,
    workouts: List<WorkoutSession>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exportState by viewModel.garminExportState.collectAsState()
    val exportedIds by viewModel.exportedGarminWorkoutIds.collectAsState()
    val lastExportAt by viewModel.lastGarminExportAt.collectAsState()

    val isExporting = exportState is GarminExportUiState.Exporting
    val unexportedCount = workouts.count { !exportedIds.contains(it.id) }
    var filePendingSave by remember { mutableStateOf<File?>(null) }

    val saveDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { destinationUri: Uri? ->
        val sourceFile = filePendingSave
        filePendingSave = null
        if (destinationUri != null && sourceFile != null && sourceFile.exists()) {
            try {
                context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                    sourceFile.inputStream().use { input ->
                        input.copyTo(out)
                    }
                }
                Toast.makeText(
                    context,
                    "Saved ${sourceFile.name} — ready to upload to Garmin Connect.",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "Failed to save file: ${e.localizedMessage}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("garmin_export_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.45f))
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
                            .background(Color(0xFF0C2A3E))
                            .border(1.dp, CyanAccent.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Watch,
                            contentDescription = "Garmin Connect Export",
                            tint = CyanAccent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Garmin Connect (.FIT)",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (lastExportAt.isNotBlank()) {
                                "${workouts.size} workouts · $unexportedCount new · Last: $lastExportAt"
                            } else {
                                "${workouts.size} workouts ready to export for Garmin"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0C2A3E))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = ".FIT",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Generates official Garmin Strength .FIT files (sets, reps, weight & timestamps). Uses deterministic Activity IDs so Garmin Connect automatically ignores any duplicate workouts when importing all sessions.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFCBD5E1),
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { viewModel.exportAllWorkoutsToGarminZip(onlyNewWorkouts = false) },
                    enabled = !isExporting && workouts.isNotEmpty(),
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("garmin_export_all_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanAccent,
                        contentColor = Color.Black
                    )
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.Black,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Building...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export All (.ZIP)", fontWeight = FontWeight.Bold)
                    }
                }

                if (unexportedCount in 1 until workouts.size) {
                    OutlinedButton(
                        onClick = { viewModel.exportAllWorkoutsToGarminZip(onlyNewWorkouts = true) },
                        enabled = !isExporting,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("garmin_export_new_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, AmberGold),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberGold)
                    ) {
                        Text(
                            text = "New Only ($unexportedCount)",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = { openGarminImportPage(context) },
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("garmin_open_import_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ElectricTeal.copy(alpha = 0.7f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricTeal)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Garmin Import", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            AnimatedVisibility(visible = exportState is GarminExportUiState.Error) {
                val errMsg = (exportState as? GarminExportUiState.Error)?.message ?: ""
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF3F151A))
                        .border(1.dp, LossRed.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = LossRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = errMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { viewModel.clearGarminExportStatus() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss Garmin export status",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }

    val readyState = exportState as? GarminExportUiState.Ready
    if (readyState != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearGarminExportStatus() },
            containerColor = SurfaceCard,
            modifier = Modifier.testTag("garmin_export_ready_dialog"),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GainGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Garmin Export Ready", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = readyState.displayTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = CyanAccent,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "File: ${readyState.file.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                    Text(
                        text = buildString {
                            append("Ready to upload to Garmin Connect (Import Data). ")
                            if (readyState.skippedDuplicatesCount > 0) {
                                append("If you already imported earlier workouts, Garmin Connect matches the deterministic Activity ID & timestamp and ignores duplicates automatically.")
                            } else {
                                append("Re-exporting later keeps the exact same Activity IDs so Garmin Connect ignores duplicates.")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = {
                            filePendingSave = readyState.file
                            saveDocumentLauncher.launch(readyState.file.name)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("garmin_save_file_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = AmberGold, contentColor = Color.Black)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save to Device / Downloads", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { shareExportedFile(context, readyState) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("garmin_share_file_button"),
                        border = BorderStroke(1.dp, CyanAccent),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAccent)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share File (Drive / Email / Files)", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { openGarminImportPage(context) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("garmin_dialog_open_connect_button"),
                        border = BorderStroke(1.dp, ElectricTeal),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricTeal)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Garmin Connect Import", fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.clearGarminExportStatus() }) {
                    Text("Done", color = AmberGold, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

private fun shareExportedFile(context: Context, ready: GarminExportUiState.Ready) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            ready.file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = ready.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "StrengthForge Garmin Workout Export: ${ready.displayTitle}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Export for Garmin Connect"))
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to share file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

private fun openGarminImportPage(context: Context) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GARMIN_IMPORT_URL))
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No browser app found to open Garmin Connect.", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to open Garmin Connect: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}
