package com.example.ui.components

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.ui.theme.AmberGold
import com.example.ui.theme.ElectricTeal
import com.example.ui.theme.LossRed
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.SocialShareUiState
import com.example.viewmodel.WorkoutViewModel
import java.io.File

@Composable
fun WorkoutSocialShareHostDialog(viewModel: WorkoutViewModel) {
    val shareState by viewModel.socialShareState.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    when (val state = shareState) {
        is SocialShareUiState.Generating -> {
            AlertDialog(
                onDismissRequest = { viewModel.clearSocialShareStatus() },
                containerColor = SurfaceCard,
                title = { Text("Creating Workout Graphic...", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = AmberGold)
                    }
                },
                confirmButton = {}
            )
        }
        is SocialShareUiState.Ready -> {
            var editableCaption by remember(state.imageFile.absolutePath, state.caption) {
                mutableStateOf(state.caption)
            }
            val previewBitmap = remember(state.imageFile.absolutePath) {
                BitmapFactory.decodeFile(state.imageFile.absolutePath)?.asImageBitmap()
            }

            AlertDialog(
                onDismissRequest = { viewModel.clearSocialShareStatus() },
                containerColor = SurfaceCard,
                modifier = Modifier.testTag("social_share_preview_dialog"),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = AmberGold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share ${state.workoutName}", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (previewBitmap != null) {
                            Image(
                                bitmap = previewBitmap,
                                contentDescription = "Workout summary graphic preview",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1080f / 1350f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF070B14))
                                    .testTag("social_share_graphic_preview")
                            )
                        }

                        OutlinedTextField(
                            value = editableCaption,
                            onValueChange = { editableCaption = it },
                            label = { Text("Post Caption") },
                            minLines = 3,
                            maxLines = 6,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("social_share_caption_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = ElectricTeal,
                                unfocusedBorderColor = SurfaceCardBorder
                            )
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            launchSocialShareIntent(
                                context = context,
                                imageFile = state.imageFile,
                                workoutName = state.workoutName,
                                caption = editableCaption
                            )
                        },
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("share_social_confirm_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = AmberGold, contentColor = Color.Black)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share Image + Text", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(editableCaption))
                                Toast.makeText(context, "Caption copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("share_copy_caption_button"),
                            border = BorderStroke(1.dp, ElectricTeal.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricTeal)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            onClick = { viewModel.clearSocialShareStatus() },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text("Close", color = TextSecondary)
                        }
                    }
                }
            )
        }
        is SocialShareUiState.Error -> {
            AlertDialog(
                onDismissRequest = { viewModel.clearSocialShareStatus() },
                containerColor = SurfaceCard,
                title = { Text("Share Unavailable", color = LossRed, fontWeight = FontWeight.Bold) },
                text = { Text(state.message, color = TextSecondary) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearSocialShareStatus() }) {
                        Text("OK", color = AmberGold)
                    }
                }
            )
        }
        SocialShareUiState.Idle -> Unit
    }
}

private fun launchSocialShareIntent(
    context: Context,
    imageFile: File,
    workoutName: String,
    caption: String
) {
    try {
        val imageUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            putExtra(Intent.EXTRA_TEXT, caption)
            putExtra(Intent.EXTRA_SUBJECT, "StrengthForge: $workoutName")
            clipData = ClipData.newUri(context.contentResolver, workoutName, imageUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share via StrengthForge"))
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to open share sheet: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}
