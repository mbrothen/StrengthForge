package com.example.ui.dashboard

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.example.R
import com.example.ui.theme.AmberGold
import com.example.ui.theme.ElectricTeal
import com.example.ui.theme.LossRed
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun AccountAndPrivacyDialog(
    profileDisplayName: String,
    onDismiss: () -> Unit,
    onDeleteUserData: suspend () -> Result<Unit>
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val currentUser = remember { Firebase.auth.currentUser }

    var isSigningOut by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    val resolvedName = currentUser?.displayName?.takeIf { it.isNotBlank() }
        ?: profileDisplayName.ifBlank { "Strength Athlete" }
    val resolvedEmail = currentUser?.email?.takeIf { it.isNotBlank() }
        ?: "Signed in with Google"

    AlertDialog(
        onDismissRequest = { if (!isSigningOut && !isDeleting) onDismiss() },
        containerColor = SurfaceCard,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = AmberGold,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Account & Settings",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Signed-in user identity
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF0B0F17),
                    border = BorderStroke(1.dp, SurfaceCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E293B))
                                .border(1.dp, AmberGold.copy(alpha = 0.6f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = resolvedName.take(1).uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = AmberGold
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = resolvedName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = resolvedEmail,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }

                // Privacy & Data Safety summary
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF0F172A),
                    border = BorderStroke(1.dp, ElectricTeal.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PrivacyTip,
                                contentDescription = null,
                                tint = ElectricTeal,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Privacy & Data Control",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = ElectricTeal
                            )
                        }
                        Text(
                            text = "Your workouts, exercises, routines, and nutrition logs are stored securely under your Google Account in Cloud Firestore. StrengthForge contains no ads and never sells your data.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                HorizontalDivider(color = SurfaceCardBorder)

                // Sign Out button
                OutlinedButton(
                    onClick = {
                        isSigningOut = true
                        coroutineScope.launch {
                            try {
                                credentialManager.clearCredentialState(ClearCredentialStateRequest())
                            } catch (_: Exception) {
                            }
                            Firebase.auth.signOut()
                            isSigningOut = false
                            onDismiss()
                        }
                    },
                    enabled = !isSigningOut && !isDeleting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("sign_out_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AmberGold.copy(alpha = 0.65f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberGold)
                ) {
                    if (isSigningOut) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = AmberGold,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign Out", fontWeight = FontWeight.Bold)
                    }
                }

                // Delete Account & All Data button
                OutlinedButton(
                    onClick = {
                        deleteError = null
                        showDeleteConfirm = true
                    },
                    enabled = !isSigningOut && !isDeleting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("open_delete_account_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, LossRed.copy(alpha = 0.75f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = LossRed)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Account & All Data", fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSigningOut && !isDeleting,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                Text("Close", color = TextSecondary, fontWeight = FontWeight.SemiBold)
            }
        }
    )

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteConfirm = false },
            containerColor = SurfaceCard,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = LossRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Delete Account & All Data?",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "This action is permanent and cannot be undone. All of your Cloud Firestore workout sessions, exercise logs, custom exercises, grouped routines, meal presets, and nutrition logs will be erased immediately, and your StrengthForge account will be deleted.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Text(
                        text = "You will be prompted to verify with your Google Account before deletion proceeds. Local exported CSV/.FIT files and Google Drive Sheets remain under your control.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    if (deleteError != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF3B151E),
                            border = BorderStroke(1.dp, LossRed.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = deleteError ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFFF9CA3),
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isDeleting = true
                        deleteError = null
                        coroutineScope.launch {
                            try {
                                val user = Firebase.auth.currentUser
                                val activity = context.findActivity()
                                if (user == null || activity == null) {
                                    deleteError = "Active Google session unavailable. Please sign in again."
                                    isDeleting = false
                                    return@launch
                                }

                                val serverClientId = context.getString(R.string.default_web_client_id)
                                val googleIdOption = GetSignInWithGoogleOption.Builder(serverClientId)
                                    .build()
                                val request = GetCredentialRequest.Builder()
                                    .addCredentialOption(googleIdOption)
                                    .build()

                                val credentialResponse = credentialManager.getCredential(
                                    request = request,
                                    context = activity
                                )
                                val credential = credentialResponse.credential
                                if (credential !is CustomCredential ||
                                    credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                                ) {
                                    deleteError = "Unexpected Google credential response."
                                    isDeleting = false
                                    return@launch
                                }

                                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data)
                                val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken.idToken, null)
                                user.reauthenticate(firebaseCredential).await()

                                val deleteDataResult = onDeleteUserData()
                                if (deleteDataResult.isFailure) {
                                    deleteError = deleteDataResult.exceptionOrNull()?.localizedMessage
                                        ?: "Failed to delete cloud records. Please retry."
                                    isDeleting = false
                                    return@launch
                                }

                                user.delete().await()
                                try {
                                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                                } catch (_: Exception) {
                                }

                                Toast.makeText(
                                    context,
                                    "StrengthForge account and data deleted.",
                                    Toast.LENGTH_LONG
                                ).show()
                                isDeleting = false
                                showDeleteConfirm = false
                                onDismiss()
                            } catch (_: GetCredentialCancellationException) {
                                deleteError = "Google verification cancelled. Account was not deleted."
                                isDeleting = false
                            } catch (e: GetCredentialException) {
                                deleteError = e.localizedMessage ?: "Google re-authentication failed."
                                isDeleting = false
                            } catch (e: Exception) {
                                deleteError = e.localizedMessage ?: "Account deletion failed. Please try again."
                                isDeleting = false
                            }
                        }
                    },
                    enabled = !isDeleting,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("confirm_delete_account_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LossRed,
                        contentColor = Color.White
                    )
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Confirm Delete", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirm = false },
                    enabled = !isDeleting,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}
