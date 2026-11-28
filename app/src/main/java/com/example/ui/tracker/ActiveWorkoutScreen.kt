package com.example.ui.tracker

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.analytics.ExerciseLastPerformance
import com.example.data.analytics.PlateCalculator
import com.example.data.analytics.PlateCalculationResult
import com.example.data.analytics.WeightUnit
import com.example.data.analytics.WeightUnitHelper
import com.example.data.analytics.WorkoutAnalyticsHelper
import com.example.data.model.ExerciseRecord
import com.example.data.model.ExerciseSet
import com.example.data.model.ExerciseTrackingType
import com.example.data.schedule.WorkoutScheduleHelper
import com.example.ui.components.WorkoutCalendarDatePickerDialog
import com.example.ui.theme.AmberGold
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.ElectricTeal
import com.example.ui.theme.GainGreen
import com.example.ui.theme.LossRed
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.ActiveExerciseState
import com.example.viewmodel.UiState
import com.example.viewmodel.WorkoutViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun ActiveWorkoutScreen(
    viewModel: WorkoutViewModel,
    onWorkoutFinished: () -> Unit,
    onWorkoutDiscarded: () -> Unit
) {
    val context = LocalContext.current
    val workoutName by viewModel.activeWorkoutName.collectAsState()
    val activeDate by viewModel.activeWorkoutDate.collectAsState()
    val exercises by viewModel.activeExercises.collectAsState()
    val durationSeconds by viewModel.workoutDurationSeconds.collectAsState()
    val isWorkoutTimerPaused by viewModel.isWorkoutTimerPaused.collectAsState()
    val restSeconds by viewModel.restTimerRemainingSeconds.collectAsState()
    val restInitialSeconds by viewModel.restTimerInitialDuration.collectAsState()
    val isRestRunning by viewModel.isRestTimerRunning.collectAsState()
    val exerciseCatalogState by viewModel.exercises.collectAsState()
    val routinesState by viewModel.routines.collectAsState()
    val lastSessionMap by viewModel.lastSessionByExerciseId.collectAsState()
    val historicalBestMap by viewModel.historicalBestByExerciseId.collectAsState()
    val weightUnit by viewModel.weightUnit.collectAsState()

    val editingWorkoutId by viewModel.editingWorkoutId.collectAsState()
    val editingWorkoutNotes by viewModel.editingWorkoutNotes.collectAsState()
    val isEditMode = editingWorkoutId != null

    val allRoutines = (routinesState as? UiState.Success)?.data ?: emptyList()
    val effectiveSessionDate = activeDate ?: Date()
    val dateSuggestion = remember(allRoutines, effectiveSessionDate) {
        WorkoutScheduleHelper.suggestRoutineForDate(allRoutines, effectiveSessionDate)
    }

    var showFinishDialog by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showAddExerciseDialog by remember { mutableStateOf(false) }
    var showDatePickerDialog by remember { mutableStateOf(false) }
    var showRestDurationDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showPlateCalculatorForWeight by remember { mutableStateOf<Double?>(null) }
    var workoutNotes by remember(editingWorkoutId) { mutableStateOf(editingWorkoutNotes) }
    var durationMinutesText by remember(editingWorkoutId, showFinishDialog) {
        mutableStateOf(maxOf(1, durationSeconds / 60).toString())
    }

    val dateSdf = remember { SimpleDateFormat("EEE, MMM d, yyyy", Locale.US) }
    val displayDate = remember(activeDate) {
        if (activeDate != null) dateSdf.format(activeDate!!) else "Today (${dateSdf.format(Date())})"
    }

    val minutes = durationSeconds / 60
    val seconds = durationSeconds % 60
    val timeFormatted = String.format("%02d:%02d", minutes, seconds)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .navigationBarsPadding()
            .testTag("active_workout_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showRenameDialog = true }
                    ) {
                        if (isEditMode) {
                            Text(
                                text = "EDITING WORKOUT",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricTeal,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = workoutName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Rename Workout",
                                tint = AmberGold,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        if (isEditMode) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = AmberGold,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${maxOf(1, minutes)} min",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = AmberGold,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Row {
                        OutlinedButton(
                            onClick = { showDiscardDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text(if (isEditMode) "Cancel" else "Discard", style = MaterialTheme.typography.labelSmall)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = { showFinishDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AmberGold,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("finish_workout_button")
                        ) {
                            Text(
                                if (isEditMode) "Save" else "Finish",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (!isEditMode) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = "Workout Elapsed Timer",
                                tint = if (isWorkoutTimerPaused) TextSecondary else AmberGold,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isWorkoutTimerPaused) "$timeFormatted · PAUSED" else timeFormatted,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (isWorkoutTimerPaused) TextSecondary else AmberGold,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("workout_timer_text")
                            )
                        }

                        OutlinedButton(
                            onClick = { viewModel.toggleWorkoutTimerPaused() },
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .testTag("workout_timer_pause_button"),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isWorkoutTimerPaused) ElectricTeal else AmberGold.copy(alpha = 0.65f)
                            ),
                            colors = if (isWorkoutTimerPaused) {
                                ButtonDefaults.buttonColors(
                                    containerColor = ElectricTeal,
                                    contentColor = Color.Black
                                )
                            } else {
                                ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color(0xFF1E293B),
                                    contentColor = AmberGold
                                )
                            },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (isWorkoutTimerPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = if (isWorkoutTimerPaused) "Resume workout timer" else "Pause workout timer",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isWorkoutTimerPaused) "Resume" else "Pause",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Date Bar (Allows logging workouts in the past!)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .background(Color(0xFF161F30))
                    .clickable { showDatePickerDialog = true }
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .testTag("active_workout_date_picker_bar"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = "Select Workout Date",
                        tint = if (activeDate != null) AmberGold else ElectricTeal,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Session Date: $displayDate",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (activeDate != null) AmberGold else Color(0xFFCBD5E1),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.dp, AmberGold.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "Pick Date",
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberGold,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Per-Workout Rest Period Strip (Seconds + Audible Beep Alert)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .background(Color(0xFF111827))
                    .clickable { showRestDurationDialog = true }
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .testTag("active_workout_rest_bar"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Rest Period",
                        tint = ElectricTeal,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Rest: ${restInitialSeconds}s (beep on end)",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFFCBD5E1),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { viewModel.setRestTimerDurationSeconds(restInitialSeconds - 15) },
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("rest_minus_15_button"),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                    ) {
                        Text("-15s", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { viewModel.setRestTimerDurationSeconds(restInitialSeconds + 15) },
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("rest_plus_15_button"),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                    ) {
                        Text("+15s", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { viewModel.startRestTimer(restInitialSeconds) },
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("start_rest_timer_button"),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricTeal, contentColor = Color.Black)
                    ) {
                        Text("Start", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }

            val suggestedForDate = dateSuggestion.routine
            if (!isEditMode &&
                dateSuggestion.isScheduledForDate &&
                suggestedForDate != null &&
                !suggestedForDate.name.equals(workoutName, ignoreCase = true)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .background(Color(0xFF0E3A42))
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .testTag("active_workout_suggested_routine_bar"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${dateSuggestion.weekday.fullName} schedule: ${suggestedForDate.name}",
                        style = MaterialTheme.typography.labelMedium,
                        color = ElectricTeal,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.startWorkoutWithRoutine(suggestedForDate, activeDate) },
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("active_workout_switch_suggested_button"),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ElectricTeal),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricTeal)
                    ) {
                        Text("Load", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Exercise List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                itemsIndexed(exercises) { exIndex, ex ->
                    val lastPerformance = lastSessionMap[ex.exerciseId]
                    val historicalBest = historicalBestMap[ex.exerciseId]
                    val isNewPr = remember(ex.sets, ex.trackingType, historicalBest) {
                        WorkoutAnalyticsHelper.isNewPersonalRecord(
                            sets = ex.sets,
                            trackingType = ex.trackingType,
                            historicalBest = historicalBest
                        )
                    }
                    ExerciseCard(
                        exercise = ex,
                        exerciseIndex = exIndex,
                        weightUnit = weightUnit,
                        lastPerformance = lastPerformance,
                        isNewPr = isNewPr,
                        onOpenPlateCalculator = { w -> showPlateCalculatorForWeight = w },
                        onHoldTimerStart = { dur -> viewModel.notifyHoldTimerStarted(dur, ex.exerciseName) },
                        onHoldTimerCancel = { viewModel.notifyHoldTimerCancelled() },
                        onAddSet = { viewModel.addSet(exIndex) },
                        onRemoveSet = { setIndex -> viewModel.removeSet(exIndex, setIndex) },
                        onUpdateSet = { setIndex, weight, reps, completed, durationSecs ->
                            viewModel.updateSet(
                                exerciseIndex = exIndex,
                                setIndex = setIndex,
                                weight = weight,
                                reps = reps,
                                completed = completed,
                                durationSeconds = durationSecs
                            )
                        },
                        onRemoveExercise = { viewModel.removeExerciseFromWorkout(exIndex) }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                item {
                    Button(
                        onClick = { showAddExerciseDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E293B),
                            contentColor = AmberGold
                        )
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Exercise to Session", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(100.dp)) // padding for floating timer
                }
            }
        }

        // Live Floating Rest Timer at Bottom
        AnimatedVisibility(
            visible = isRestRunning || restSeconds > 0,
            enter = expandVertically(),
            exit = shrinkVertically(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            RestTimerCard(
                remainingSeconds = restSeconds,
                onAddSeconds = { viewModel.startRestTimer(restSeconds + 30) },
                onCancel = { viewModel.cancelRestTimer() }
            )
        }
    }

    // Calendar Date Selection Dialog
    if (showDatePickerDialog) {
        WorkoutCalendarDatePickerDialog(
            initialDate = activeDate ?: Date(),
            onDismiss = { showDatePickerDialog = false },
            onDateSelected = { pickedDate ->
                viewModel.setActiveWorkoutDate(pickedDate)
                showDatePickerDialog = false
            }
        )
    }

    // Rest Duration Seconds Dialog
    if (showRestDurationDialog) {
        var restSecondsText by remember(restInitialSeconds) { mutableStateOf(restInitialSeconds.toString()) }
        AlertDialog(
            onDismissRequest = { showRestDurationDialog = false },
            containerColor = SurfaceCard,
            title = { Text("Workout Rest Period (sec)", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Set the automatic rest countdown in seconds. An audible beep and vibration will alert you when time is up.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = restSecondsText,
                        onValueChange = { restSecondsText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Rest Duration (seconds)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("rest_seconds_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = ElectricTeal,
                            unfocusedBorderColor = SurfaceCardBorder
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(45, 60, 90, 120, 180).forEach { preset ->
                            OutlinedButton(
                                onClick = { restSecondsText = preset.toString() },
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = if (restSecondsText == preset.toString()) AmberGold else TextSecondary
                                )
                            ) {
                                Text("${preset}s", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val secs = restSecondsText.toIntOrNull()?.coerceIn(5, 900) ?: restInitialSeconds
                        viewModel.setRestTimerDurationSeconds(secs)
                        showRestDurationDialog = false
                    },
                    modifier = Modifier.testTag("save_rest_seconds_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = AmberGold, contentColor = Color.Black)
                ) {
                    Text("Save Rest Time", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestDurationDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // Rename Workout Dialog
    if (showRenameDialog) {
        var newName by remember(workoutName) { mutableStateOf(workoutName) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Workout", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Workout Name") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = AmberGold,
                        unfocusedBorderColor = SurfaceCardBorder
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateActiveWorkoutName(newName)
                        showRenameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberGold, contentColor = Color.Black)
                ) {
                    Text("Save Name", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceCard
        )
    }

    // Finish Workout Dialog
    if (showFinishDialog) {
        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            title = {
                Text(
                    text = if (isEditMode) "Save Workout Changes?" else "Finish & Save Workout?",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Column {
                    Text(
                        text = if (isEditMode) {
                            "Changes for $displayDate will update your workout log, analytics curves, and total volume."
                        } else {
                            "Great lift! This will be recorded on $displayDate, update your strength progression charts, and update lifetime volume."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0F172A))
                            .border(1.dp, ElectricTeal.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                            .clickable { showDatePickerDialog = true }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("finish_dialog_date_picker_field"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = "Workout Date",
                                tint = ElectricTeal,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = displayDate,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "Change",
                            style = MaterialTheme.typography.labelSmall,
                            color = AmberGold,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = durationMinutesText,
                        onValueChange = { durationMinutesText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Duration (minutes)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = AmberGold,
                            unfocusedBorderColor = SurfaceCardBorder
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = workoutNotes,
                        onValueChange = { workoutNotes = it },
                        label = { Text("Workout Notes (optional)") },
                        placeholder = {
                            Text("e.g. Felt great on bench, ${WeightUnitHelper.formatWeightWithUnit(150.0, weightUnit)} felt light!")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = AmberGold,
                            unfocusedBorderColor = SurfaceCardBorder
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFinishDialog = false
                        val mins = durationMinutesText.toIntOrNull()
                        if (mins != null && mins > 0) {
                            viewModel.updateActiveWorkoutDurationMinutes(mins)
                        }
                        viewModel.finishWorkout(workoutNotes, customDate = activeDate) {
                            onWorkoutFinished()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AmberGold,
                        contentColor = Color.Black
                    )
                ) {
                    Text(if (isEditMode) "Update Workout" else "Save & Log Workout", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFinishDialog = false }) {
                    Text("Keep Editing", color = TextSecondary)
                }
            },
            containerColor = SurfaceCard
        )
    }

    // Discard Dialog
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(if (isEditMode) "Discard Changes?" else "Discard Workout?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (isEditMode) "Are you sure you want to cancel editing? Your unsaved edits will be lost." else "Are you sure you want to discard this workout? Current sets will not be logged.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscardDialog = false
                        viewModel.discardWorkout()
                        onWorkoutDiscarded()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LossRed, contentColor = Color.White)
                ) {
                    Text("Discard", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceCard
        )
    }

    // Add Exercise Dialog
    if (showAddExerciseDialog) {
        val catalog = (exerciseCatalogState as? UiState.Success)?.data ?: emptyList()
        AlertDialog(
            onDismissRequest = { showAddExerciseDialog = false },
            title = { Text("Select Exercise", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.height(300.dp)) {
                    itemsIndexed(catalog) { _, ex ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.addExerciseToWorkout(ex)
                                    showAddExerciseDialog = false
                                }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ex.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                                Text("${ex.category} · ${ex.formatCompactPrescription(weightUnit)}", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                            Icon(Icons.Default.Add, contentDescription = null, tint = AmberGold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddExerciseDialog = false }) {
                    Text("Close", color = AmberGold)
                }
            },
            containerColor = SurfaceCard
        )
    }

    showPlateCalculatorForWeight?.let { initialWeight ->
        PlateCalculatorDialog(
            initialWeightLbs = initialWeight,
            weightUnit = weightUnit,
            onDismiss = { showPlateCalculatorForWeight = null }
        )
    }
}



@Composable
fun ExerciseCard(
    exercise: ActiveExerciseState,
    exerciseIndex: Int,
    weightUnit: WeightUnit = WeightUnit.LBS,
    lastPerformance: ExerciseLastPerformance? = null,
    isNewPr: Boolean = false,
    onOpenPlateCalculator: (Double) -> Unit = {},
    onHoldTimerStart: (Int) -> Unit = {},
    onHoldTimerCancel: () -> Unit = {},
    onAddSet: () -> Unit,
    onRemoveSet: (Int) -> Unit,
    onUpdateSet: (Int, Double, Int, Boolean, Int) -> Unit,
    onRemoveExercise: () -> Unit = {}
) {
    val trackingType = exercise.trackingType
    val unitHeader = if (weightUnit == WeightUnit.KG) "KG" else "LBS"
    val weightHeader = when (trackingType) {
        ExerciseTrackingType.WEIGHTED -> unitHeader
        ExerciseTrackingType.BODYWEIGHT_REPS,
        ExerciseTrackingType.TIMED_HOLD -> "+$unitHeader (BW)"
    }
    val secondaryHeader = when (trackingType) {
        ExerciseTrackingType.TIMED_HOLD -> "SEC"
        ExerciseTrackingType.WEIGHTED,
        ExerciseTrackingType.BODYWEIGHT_REPS -> "REPS"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isNewPr) GainGreen.copy(alpha = 0.7f) else SurfaceCardBorder
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Exercise Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = exercise.exerciseName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isNewPr) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(GainGreen.copy(alpha = 0.2f))
                                    .border(1.dp, GainGreen.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                    .testTag("exercise_new_pr_badge_${exercise.exerciseId}")
                            ) {
                                Text(
                                    text = "NEW PR!",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GainGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = exercise.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = AmberGold
                        )
                        if (trackingType != ExerciseTrackingType.WEIGHTED) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(ElectricTeal.copy(alpha = 0.16f))
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = trackingType.shortLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricTeal,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    if (lastPerformance != null) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Last: ${lastPerformance.summaryText}" +
                                if (lastPerformance.dateString.isNotBlank()) " (${lastPerformance.dateString})" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricTeal,
                            modifier = Modifier.testTag("exercise_last_perf_${exercise.exerciseId}")
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (trackingType == ExerciseTrackingType.WEIGHTED) {
                        val targetWeight = exercise.sets.firstOrNull { !it.completed }?.weightLbs
                            ?: exercise.sets.maxOfOrNull { it.weightLbs }
                            ?: 135.0
                        OutlinedButton(
                            onClick = { onOpenPlateCalculator(targetWeight) },
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .testTag("open_plate_calc_${exercise.exerciseId}"),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AmberGold.copy(alpha = 0.45f)),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberGold)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FitnessCenter,
                                contentDescription = "Open Plate Calculator",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Plates", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    IconButton(
                        onClick = onRemoveExercise,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Remove Exercise",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Set Headers
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("SET", style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.width(36.dp), textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.width(8.dp))
                Text(weightHeader, style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.width(8.dp))
                Text(secondaryHeader, style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.width(8.dp))
                Text("DONE", style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.width(44.dp), textAlign = TextAlign.Center)
            }

            // Sets
            exercise.sets.forEachIndexed { setIndex, setItem ->
                SetRow(
                    setItem = setItem,
                    trackingType = trackingType,
                    weightUnit = weightUnit,
                    onHoldTimerStart = onHoldTimerStart,
                    onHoldTimerCancel = onHoldTimerCancel,
                    onUpdate = { w, r, done, dur -> onUpdateSet(setIndex, w, r, done, dur) },
                    onDelete = { onRemoveSet(setIndex) }
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            Spacer(modifier = Modifier.height(6.dp))

            Button(
                onClick = onAddSet,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1E293B),
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("+ Add Set", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun SetRow(
    setItem: ExerciseSet,
    trackingType: ExerciseTrackingType = ExerciseTrackingType.WEIGHTED,
    weightUnit: WeightUnit = WeightUnit.LBS,
    onHoldTimerStart: (Int) -> Unit = {},
    onHoldTimerCancel: () -> Unit = {},
    onUpdate: (Double, Int, Boolean, Int) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isBodyweightMode = trackingType != ExerciseTrackingType.WEIGHTED

    fun formatWeightField(weightLbs: Double): String {
        if (isBodyweightMode && weightLbs == 0.0) return ""
        return WeightUnitHelper.formatDisplayNumber(WeightUnitHelper.fromLbs(weightLbs, weightUnit))
    }

    var weightText by remember(setItem.setNumber, trackingType, weightUnit) {
        mutableStateOf(formatWeightField(setItem.weightLbs))
    }
    var repsText by remember(setItem.setNumber) { mutableStateOf(setItem.reps.toString()) }
    var durationText by remember(setItem.setNumber) {
        mutableStateOf(if (setItem.durationSeconds > 0) setItem.durationSeconds.toString() else "60")
    }

    fun currentWeightLbs(rawText: String = weightText): Double {
        if (rawText.isBlank() && isBodyweightMode) return 0.0
        val parsed = rawText.toDoubleOrNull() ?: return setItem.weightLbs
        return WeightUnitHelper.toLbs(parsed, weightUnit)
    }

    val targetHoldSeconds = (durationText.toIntOrNull() ?: setItem.durationSeconds).coerceAtLeast(1)
    var isHoldRunning by remember(setItem.setNumber) { mutableStateOf(false) }
    var holdRemainingSeconds by remember(setItem.setNumber) { mutableIntStateOf(targetHoldSeconds) }

    LaunchedEffect(setItem.weightLbs, trackingType, weightUnit) {
        val currentParsed = if (weightText.isBlank() && isBodyweightMode) 0.0 else weightText.toDoubleOrNull()
        val expectedDisplay = WeightUnitHelper.fromLbs(setItem.weightLbs, weightUnit)
        if (currentParsed == null || abs(currentParsed - expectedDisplay) > 0.05) {
            weightText = formatWeightField(setItem.weightLbs)
        }
    }

    LaunchedEffect(setItem.reps) {
        if (repsText.toIntOrNull() != setItem.reps) {
            repsText = setItem.reps.toString()
        }
    }

    LaunchedEffect(setItem.durationSeconds) {
        if (trackingType == ExerciseTrackingType.TIMED_HOLD &&
            setItem.durationSeconds > 0 &&
            durationText.toIntOrNull() != setItem.durationSeconds
        ) {
            durationText = setItem.durationSeconds.toString()
            if (!isHoldRunning) {
                holdRemainingSeconds = setItem.durationSeconds
            }
        }
    }

    LaunchedEffect(isHoldRunning) {
        if (!isHoldRunning) return@LaunchedEffect
        while (holdRemainingSeconds > 0 && isHoldRunning) {
            delay(1000)
            if (isHoldRunning) {
                holdRemainingSeconds -= 1
            }
        }
        if (isHoldRunning && holdRemainingSeconds <= 0) {
            isHoldRunning = false
            onHoldTimerCancel()
            val w = currentWeightLbs()
            onUpdate(w, maxOf(1, setItem.reps), true, targetHoldSeconds)
            coroutineScope.launch {
                triggerRestEndAlert(context)
            }
            holdRemainingSeconds = targetHoldSeconds
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (setItem.completed) Color(0xFF0D3322) else Color(0xFF0B0F17))
            .padding(vertical = 4.dp, horizontal = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Set Number
            Text(
                text = "${setItem.setNumber}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (setItem.completed) GainGreen else Color.White,
                modifier = Modifier.width(36.dp),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Weight (or optional added +lbs/+kg for BW/Timed) Input
            OutlinedTextField(
                value = weightText,
                onValueChange = { raw ->
                    val filtered = buildString {
                        var hasDot = false
                        for (ch in raw) {
                            if (ch.isDigit()) append(ch)
                            else if (ch == '.' && !hasDot) {
                                append(ch)
                                hasDot = true
                            }
                        }
                    }
                    weightText = filtered
                    val w = currentWeightLbs(filtered)
                    val dur = durationText.toIntOrNull() ?: setItem.durationSeconds
                    onUpdate(w, setItem.reps, setItem.completed, dur)
                },
                placeholder = if (isBodyweightMode) {
                    {
                        Text(
                            text = "BW",
                            style = MaterialTheme.typography.bodySmall,
                            color = ElectricTeal,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("set_weight_input_${setItem.setNumber}"),
                textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center, color = Color.White),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AmberGold,
                    unfocusedBorderColor = Color(0xFF334155)
                ),
                shape = RoundedCornerShape(6.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Secondary Input: Duration (SEC) for TIMED_HOLD, Reps (REPS) otherwise
            if (trackingType == ExerciseTrackingType.TIMED_HOLD) {
                OutlinedTextField(
                    value = durationText,
                    onValueChange = { raw ->
                        val filtered = raw.filter { ch -> ch.isDigit() }
                        durationText = filtered
                        val dur = filtered.toIntOrNull() ?: setItem.durationSeconds
                        if (!isHoldRunning && dur > 0) {
                            holdRemainingSeconds = dur
                        }
                        val w = currentWeightLbs()
                        onUpdate(w, maxOf(1, setItem.reps), setItem.completed, dur)
                    },
                    placeholder = {
                        Text(
                            text = "60s",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("set_duration_input_${setItem.setNumber}"),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center, color = ElectricTeal, fontWeight = FontWeight.SemiBold),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricTeal,
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    shape = RoundedCornerShape(6.dp)
                )
            } else {
                OutlinedTextField(
                    value = repsText,
                    onValueChange = { raw ->
                        val filtered = raw.filter { ch -> ch.isDigit() }
                        repsText = filtered
                        val r = filtered.toIntOrNull() ?: setItem.reps
                        val w = currentWeightLbs()
                        onUpdate(w, r, setItem.completed, setItem.durationSeconds)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("set_reps_input_${setItem.setNumber}"),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center, color = Color.White),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AmberGold,
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    shape = RoundedCornerShape(6.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Checkbox Completion
            Checkbox(
                checked = setItem.completed,
                onCheckedChange = { isChecked ->
                    if (isChecked) {
                        isHoldRunning = false
                        onHoldTimerCancel()
                    }
                    val w = currentWeightLbs()
                    val r = if (trackingType == ExerciseTrackingType.TIMED_HOLD) {
                        maxOf(1, setItem.reps)
                    } else {
                        repsText.toIntOrNull() ?: setItem.reps
                    }
                    val dur = if (trackingType == ExerciseTrackingType.TIMED_HOLD) {
                        durationText.toIntOrNull() ?: setItem.durationSeconds
                    } else {
                        setItem.durationSeconds
                    }
                    onUpdate(w, r, isChecked, dur)
                },
                colors = CheckboxDefaults.colors(
                    checkedColor = GainGreen,
                    uncheckedColor = Color(0xFF64748B),
                    checkmarkColor = Color.Black
                ),
                modifier = Modifier.width(44.dp)
            )
        }

        if (trackingType == ExerciseTrackingType.TIMED_HOLD) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 44.dp, top = 4.dp, end = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = if (isHoldRunning) AmberGold else ElectricTeal,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isHoldRunning) "Hold: ${holdRemainingSeconds}s" else "Timer: ${holdRemainingSeconds}s",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isHoldRunning) AmberGold else ElectricTeal,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("set_hold_countdown_text_${setItem.setNumber}")
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = {
                            if (!isHoldRunning) {
                                if (holdRemainingSeconds <= 0) {
                                    holdRemainingSeconds = targetHoldSeconds
                                }
                                isHoldRunning = true
                                onHoldTimerStart(holdRemainingSeconds)
                            } else {
                                isHoldRunning = false
                                onHoldTimerCancel()
                            }
                        },
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("set_hold_timer_toggle_${setItem.setNumber}"),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isHoldRunning) AmberGold else ElectricTeal.copy(alpha = 0.7f)
                        ),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isHoldRunning) AmberGold else ElectricTeal
                        )
                    ) {
                        Icon(
                            imageVector = if (isHoldRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isHoldRunning) "Pause hold timer" else "Start hold timer",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isHoldRunning) "Pause" else "Start Hold",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = {
                            isHoldRunning = false
                            onHoldTimerCancel()
                            holdRemainingSeconds = targetHoldSeconds
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("set_hold_timer_reset_${setItem.setNumber}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset hold timer",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlateCalculatorDialog(
    initialWeightLbs: Double,
    weightUnit: WeightUnit = WeightUnit.LBS,
    onDismiss: () -> Unit
) {
    var selectedUnit by remember(weightUnit) { mutableStateOf(weightUnit) }
    var weightText by remember(initialWeightLbs, weightUnit) {
        mutableStateOf(PlateCalculationResult.formatPlate(WeightUnitHelper.fromLbs(initialWeightLbs, weightUnit)))
    }
    var selectedBarWeight by remember(weightUnit) { mutableStateOf(weightUnit.defaultBarWeight) }
    val targetWeight = weightText.toDoubleOrNull() ?: WeightUnitHelper.fromLbs(initialWeightLbs, selectedUnit)
    val result = remember(targetWeight, selectedBarWeight, selectedUnit) {
        PlateCalculator.calculatePlatesForUnit(
            targetDisplayWeight = targetWeight,
            barDisplayWeight = selectedBarWeight,
            unit = selectedUnit
        )
    }
    val step = selectedUnit.incrementStep
    val stepLabel = PlateCalculationResult.formatPlate(step)
    val availableBars = remember(selectedUnit) { PlateCalculator.availableBarsForUnit(selectedUnit) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        modifier = Modifier.testTag("plate_calculator_dialog"),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = AmberGold)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Barbell Plate Calculator", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WeightUnit.entries.forEach { unit ->
                        val isSel = selectedUnit == unit
                        FilterChip(
                            selected = isSel,
                            onClick = {
                                if (selectedUnit != unit) {
                                    val lbsEquivalent = WeightUnitHelper.toLbs(targetWeight, selectedUnit)
                                    val convertedTarget = WeightUnitHelper.fromLbs(lbsEquivalent, unit)
                                    selectedUnit = unit
                                    selectedBarWeight = unit.defaultBarWeight
                                    weightText = PlateCalculationResult.formatPlate(convertedTarget)
                                }
                            },
                            label = {
                                Text(
                                    text = unit.shortLabel.uppercase(),
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("plate_calc_unit_${unit.id.lowercase()}"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AmberGold,
                                selectedLabelColor = Color.Black,
                                containerColor = Color(0xFF1E293B),
                                labelColor = TextSecondary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            val next = (targetWeight - step).coerceAtLeast(0.0)
                            weightText = PlateCalculationResult.formatPlate(next)
                        },
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                    ) {
                        Text("-$stepLabel", fontWeight = FontWeight.Bold)
                    }

                    OutlinedTextField(
                        value = weightText,
                        onValueChange = { raw -> weightText = raw.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Target (${selectedUnit.shortLabel})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("plate_calc_weight_input"),
                        textStyle = MaterialTheme.typography.titleMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AmberGold,
                            unfocusedBorderColor = SurfaceCardBorder
                        )
                    )

                    OutlinedButton(
                        onClick = {
                            val next = targetWeight + step
                            weightText = PlateCalculationResult.formatPlate(next)
                        },
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberGold)
                    ) {
                        Text("+$stepLabel", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("Barbell Weight:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    availableBars.forEach { barOpt ->
                        val isSel = abs(selectedBarWeight - barOpt) < 0.05
                        FilterChip(
                            selected = isSel,
                            onClick = { selectedBarWeight = barOpt },
                            label = {
                                Text(
                                    text = if (barOpt == 0.0) {
                                        "No Bar"
                                    } else if (selectedUnit == WeightUnit.KG) {
                                        "${PlateCalculationResult.formatPlate(barOpt)}kg"
                                    } else {
                                        "${barOpt.toInt()}#"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricTeal,
                                selectedLabelColor = Color.Black,
                                containerColor = Color(0xFF1E293B),
                                labelColor = TextSecondary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.dp, SurfaceCardBorder, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Text(
                            text = "LOAD PER SIDE",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricTeal,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = result.formatPerSideSummary(),
                            style = MaterialTheme.typography.titleMedium,
                            color = AmberGold,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("plate_calc_summary_text")
                        )
                        if (result.remainderLbs > 0.1) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Closest standard load: ${PlateCalculationResult.formatPlate(result.achievableWeightLbs)} ${selectedUnit.shortLabel} (${PlateCalculationResult.formatPlate(result.remainderLbs)} ${selectedUnit.shortLabel} shy)",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = AmberGold, contentColor = Color.Black)
            ) {
                Text("Done", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun RestTimerCard(
    remainingSeconds: Int,
    onAddSeconds: () -> Unit,
    onCancel: () -> Unit
) {
    val m = remainingSeconds / 60
    val s = remainingSeconds % 60
    val timeStr = String.format("%02d:%02d", m, s)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, AmberGold)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = "Rest Timer",
                    tint = AmberGold,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "REST TIMER",
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberGold,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Row {
                Button(
                    onClick = onAddSeconds,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("+30s", color = AmberGold, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel Timer", tint = TextSecondary)
                }
            }
        }
    }
}

internal suspend fun triggerRestEndAlert(context: Context) {
    triggerVibration(context)
    withContext(Dispatchers.IO) {
        val played = playSynthesizedBeep(context)
        if (!played) {
            playToneGeneratorFallback(context)
        }
    }
}

private suspend fun playSynthesizedBeep(context: Context): Boolean {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    val mediaVol = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 1
    val usage = if (mediaVol > 0) AudioAttributes.USAGE_MEDIA else AudioAttributes.USAGE_ALARM

    val sampleRate = 44100
    val samples = generateDualBeepPcm(sampleRate)
    val bufferBytes = samples.size * 2

    val attributes = AudioAttributes.Builder()
        .setUsage(usage)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    val format = AudioFormat.Builder()
        .setSampleRate(sampleRate)
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
        .build()

    var focusRequest: AudioFocusRequest? = null
    if (audioManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .build()
        try {
            audioManager.requestAudioFocus(focusRequest)
        } catch (_: Exception) {}
    } else if (audioManager != null) {
        @Suppress("DEPRECATION")
        try {
            audioManager.requestAudioFocus(
                null,
                if (usage == AudioAttributes.USAGE_MEDIA) AudioManager.STREAM_MUSIC else AudioManager.STREAM_ALARM,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        } catch (_: Exception) {}
    }

    var track: AudioTrack? = null
    return try {
        track = AudioTrack(
            attributes,
            format,
            bufferBytes,
            AudioTrack.MODE_STATIC,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            return false
        }
        val written = track.write(samples, 0, samples.size)
        if (written <= 0) {
            return false
        }
        track.play()
        kotlinx.coroutines.delay(650)
        true
    } catch (_: Exception) {
        false
    } finally {
        try {
            track?.stop()
        } catch (_: Exception) {}
        try {
            track?.release()
        } catch (_: Exception) {}
        if (audioManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
                try {
                    audioManager.abandonAudioFocusRequest(focusRequest)
                } catch (_: Exception) {}
            } else {
                @Suppress("DEPRECATION")
                try {
                    audioManager.abandonAudioFocus(null)
                } catch (_: Exception) {}
            }
        }
    }
}

internal fun generateDualBeepPcm(sampleRate: Int = 44100): ShortArray {
    val beep1Ms = 180
    val gapMs = 80
    val beep2Ms = 280
    val totalMs = beep1Ms + gapMs + beep2Ms
    val totalSamples = (sampleRate * totalMs) / 1000
    val samples = ShortArray(totalSamples)

    val beep1Samples = (sampleRate * beep1Ms) / 1000
    val gapSamples = (sampleRate * gapMs) / 1000
    val beep2Start = beep1Samples + gapSamples
    val beep2Samples = totalSamples - beep2Start
    val fadeSamples = (sampleRate * 12) / 1000 // 12ms anti-click envelope

    fun writeTone(startIdx: Int, count: Int, freqHz: Double) {
        for (i in 0 until count) {
            val env = when {
                i < fadeSamples -> i.toDouble() / fadeSamples
                i > count - fadeSamples -> (count - i).toDouble() / fadeSamples
                else -> 1.0
            }.coerceIn(0.0, 1.0)
            val angle = 2.0 * PI * freqHz * i / sampleRate
            val amplitude = sin(angle) * env * 0.85 * Short.MAX_VALUE
            samples[startIdx + i] = amplitude.roundToInt().toShort()
        }
    }

    writeTone(0, beep1Samples, 880.0)
    writeTone(beep2Start, beep2Samples, 1174.66)
    return samples
}

private suspend fun playToneGeneratorFallback(context: Context) {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    val mediaVol = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 1
    val stream = if (mediaVol > 0) AudioManager.STREAM_MUSIC else AudioManager.STREAM_ALARM
    try {
        val toneGen = ToneGenerator(stream, 100)
        try {
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 350)
            kotlinx.coroutines.delay(420)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 250)
            kotlinx.coroutines.delay(300)
        } finally {
            toneGen.release()
        }
    } catch (_: Exception) {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(context, uri)?.play()
        } catch (_: Exception) {}
    }
}

private fun triggerVibration(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            @Suppress("DEPRECATION")
            vibrator?.vibrate(500)
        }
    } catch (_: Exception) {}
}
