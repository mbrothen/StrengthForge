package com.example.ui.tracker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ExerciseRecord
import com.example.data.model.WorkoutRoutine
import com.example.data.model.WorkoutSession
import com.example.data.schedule.WorkoutScheduleHelper
import com.example.ui.components.WeeklyWorkoutScheduleCard
import com.example.ui.components.WorkoutCalendarDatePickerDialog
import com.example.ui.history.AddEditRoutineDialog
import com.example.ui.history.EditWorkoutMetadataDialog
import com.example.ui.theme.AmberGold
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.ElectricTeal
import com.example.ui.theme.LossRed
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.UiState
import com.example.viewmodel.WorkoutViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkoutHubScreen(
    viewModel: WorkoutViewModel,
    onOpenHistoryTab: () -> Unit = {}
) {
    val routinesState by viewModel.routines.collectAsState()
    val exercisesState by viewModel.exercises.collectAsState()
    val workoutsState by viewModel.workouts.collectAsState()
    val weeklySchedule by viewModel.weeklySchedule.collectAsState()

    val routines = (routinesState as? UiState.Success)?.data ?: emptyList()
    val exercises = (exercisesState as? UiState.Success)?.data ?: emptyList()
    val workouts = (workoutsState as? UiState.Success)?.data ?: emptyList()

    var showAddWorkoutDialog by remember { mutableStateOf(false) }
    var launcherInitialDate by remember { mutableStateOf<Date?>(null) }
    var showRoutineDialog by remember { mutableStateOf(false) }
    var routineToEdit by remember { mutableStateOf<WorkoutRoutine?>(null) }
    var workoutToEdit by remember { mutableStateOf<WorkoutSession?>(null) }
    var workoutToDelete by remember { mutableStateOf<WorkoutSession?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(horizontal = 20.dp)
            .testTag("workout_hub_screen"),
        contentPadding = PaddingValues(top = 20.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FitnessCenter,
                        contentDescription = null,
                        tint = AmberGold,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Workouts & Routines",
                        style = MaterialTheme.typography.headlineLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "Add a workout, pick a past date, or edit your sessions",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }

        // Primary CTA Card: Add / Log Workout
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        launcherInitialDate = null
                        showAddWorkoutDialog = true
                    }
                    .testTag("add_workout_hub_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161F30)),
                border = BorderStroke(1.5.dp, AmberGold)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF1E293B), Color(0xFF111827))
                            )
                        )
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "LOG NEW OR PAST SESSION",
                            style = MaterialTheme.typography.labelSmall,
                            color = AmberGold,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "+ Add Workout",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Pick today or any past calendar date & choose lifts",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(AmberGold),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Workout",
                            tint = Color.Black,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }

        // Weekly Workout Schedule
        item {
            WeeklyWorkoutScheduleCard(
                schedule = weeklySchedule,
                onDayClick = { day ->
                    launcherInitialDate = day.date
                    showAddWorkoutDialog = true
                }
            )
        }

        // Grouped Routines Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = null,
                        tint = AmberGold,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "GROUPED ROUTINES (${routines.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                }

                TextButton(
                    onClick = {
                        routineToEdit = null
                        showRoutineDialog = true
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = AmberGold, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New Routine", color = AmberGold, fontWeight = FontWeight.Bold)
                }
            }
        }

        items(routines, key = { it.id }) { routine ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = BorderStroke(1.dp, SurfaceCardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = routine.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF0F172A))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = routine.targetMuscle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AmberGold,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            val scheduleLabel = WorkoutScheduleHelper.formatScheduledDays(routine.scheduledDays)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = if (routine.scheduledDays.isNotEmpty()) ElectricTeal else TextSecondary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Scheduled: $scheduleLabel",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (routine.scheduledDays.isNotEmpty()) ElectricTeal else TextSecondary,
                                    fontWeight = if (routine.scheduledDays.isNotEmpty()) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                            if (routine.description.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = routine.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }

                        Row {
                            IconButton(onClick = {
                                routineToEdit = routine
                                showRoutineDialog = true
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Routine", tint = AmberGold, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { viewModel.deleteRoutine(routine.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Routine", tint = LossRed, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    val exerciseNames = routine.exerciseIds.mapNotNull { id ->
                        exercises.find { it.id == id }?.name
                    }
                    if (exerciseNames.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            exerciseNames.forEach { exName ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF1E293B))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(exName, color = Color(0xFFCBD5E1), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { viewModel.startWorkoutWithRoutine(routine) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AmberGold,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Start Routine", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Recent Logged Workouts (Editable)
        if (workouts.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = ElectricTeal,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "RECENT WORKOUTS (TAP TO EDIT)",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    TextButton(onClick = onOpenHistoryTab) {
                        Text("All Logs", color = AmberGold, fontWeight = FontWeight.Bold)
                    }
                }
            }

            items(workouts.take(5), key = { it.id }) { workout ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { workoutToEdit = workout },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    border = BorderStroke(1.dp, SurfaceCardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = workout.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "${workout.dateString} · ${workout.durationMinutes} min · ${workout.totalVolumeLbs.toInt()} lbs",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { workoutToEdit = workout }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Workout",
                                    tint = AmberGold,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(onClick = { workoutToDelete = workout }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Workout",
                                    tint = LossRed,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddWorkoutDialog) {
        AddNewWorkoutLauncherDialog(
            routines = routines,
            exercises = exercises,
            initialDate = launcherInitialDate,
            onDismiss = {
                showAddWorkoutDialog = false
                launcherInitialDate = null
            },
            onStartWithRoutine = { routine, date, restSecs ->
                showAddWorkoutDialog = false
                launcherInitialDate = null
                viewModel.startWorkoutWithRoutine(routine, date, restSecs)
            },
            onStartCustom = { name, selectedExs, date, restSecs ->
                showAddWorkoutDialog = false
                launcherInitialDate = null
                viewModel.startCustomWorkout(name, selectedExs, date, restSeconds = restSecs)
            }
        )
    }

    if (showRoutineDialog) {
        AddEditRoutineDialog(
            initialRoutine = routineToEdit,
            availableExercises = exercises,
            onDismiss = { showRoutineDialog = false },
            onSave = { routine ->
                viewModel.saveRoutine(routine) {
                    showRoutineDialog = false
                }
            }
        )
    }

    workoutToEdit?.let { targetWorkout ->
        EditWorkoutMetadataDialog(
            workout = targetWorkout,
            onDismiss = { workoutToEdit = null },
            onSaveDetails = { newName, newDate, newDuration, newNotes, newVolume ->
                viewModel.updateWorkoutMetadata(
                    workout = targetWorkout,
                    newName = newName,
                    newDate = newDate,
                    newDurationMinutes = newDuration,
                    newNotes = newNotes,
                    newTotalVolumeLbs = newVolume
                ) {
                    workoutToEdit = null
                }
            },
            onEditSetsAndExercises = {
                workoutToEdit = null
                viewModel.startEditingWorkout(targetWorkout)
            },
            onDelete = {
                workoutToEdit = null
                workoutToDelete = targetWorkout
            }
        )
    }

    workoutToDelete?.let { targetWorkout ->
        AlertDialog(
            onDismissRequest = { workoutToDelete = null },
            containerColor = SurfaceCard,
            title = { Text("Delete Workout?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Remove \"${targetWorkout.name}\" (${targetWorkout.dateString}) from your logs and adjust lifetime volume?",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteWorkout(targetWorkout) {
                            workoutToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LossRed, contentColor = Color.White)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { workoutToDelete = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddNewWorkoutLauncherDialog(
    routines: List<WorkoutRoutine>,
    exercises: List<ExerciseRecord>,
    initialDate: Date? = null,
    onDismiss: () -> Unit,
    onStartWithRoutine: (WorkoutRoutine, Date?, Int) -> Unit,
    onStartCustom: (String, List<ExerciseRecord>, Date?, Int) -> Unit
) {
    val initialIsPast = remember(initialDate) {
        initialDate != null &&
            WorkoutScheduleHelper.normalizedDateKey(initialDate) != WorkoutScheduleHelper.normalizedDateKey(Date())
    }
    var usePastDate by remember(initialDate) { mutableStateOf(initialIsPast) }
    var selectedDate by remember(initialDate) { mutableStateOf(initialDate ?: Date()) }
    var showCalendarPicker by remember { mutableStateOf(false) }

    val effectiveDate = if (usePastDate) selectedDate else Date()
    val effectiveDateKey = WorkoutScheduleHelper.normalizedDateKey(effectiveDate)
    val suggestion = remember(routines, effectiveDateKey) {
        WorkoutScheduleHelper.suggestRoutineForDate(routines, effectiveDate)
    }

    var mode by remember { mutableStateOf(if (routines.isNotEmpty()) 0 else 1) } // 0 = From Routine, 1 = Custom Exercises
    var selectedRoutine by remember { mutableStateOf(suggestion.routine ?: routines.firstOrNull()) }
    var customName by remember { mutableStateOf("") }
    var selectedExerciseIds by remember { mutableStateOf(setOf<String>()) }
    var restSecondsText by remember(selectedRoutine) {
        mutableStateOf((selectedRoutine?.restSeconds ?: 90).toString())
    }

    // Whenever the workout recording date changes, auto-suggest the routine scheduled on that day
    LaunchedEffect(effectiveDateKey, routines) {
        suggestion.routine?.let { suggested ->
            selectedRoutine = suggested
            if (suggestion.isScheduledForDate) {
                mode = 0
            }
        }
    }

    val sdf = remember { SimpleDateFormat("EEE, MMM d, yyyy", Locale.US) }

    val quickDatePresets = remember {
        listOf(
            "Today" to 0,
            "Yesterday" to -1,
            "2d Ago" to -2,
            "3d Ago" to -3,
            "1w Ago" to -7
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Add, contentDescription = null, tint = AmberGold)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add / Log Workout", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Step 1: Date Picker
                Text(
                    text = "Workout Date (tap to open calendar)",
                    style = MaterialTheme.typography.labelMedium,
                    color = AmberGold,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.dp, ElectricTeal.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .clickable { showCalendarPicker = true }
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                        .testTag("workout_date_picker_field"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = "Select Date",
                            tint = ElectricTeal,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (usePastDate) sdf.format(selectedDate) else "Today (${sdf.format(Date())})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "Pick Date",
                        color = AmberGold,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickDatePresets.forEach { (label, offset) ->
                        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offset) }
                        val isSelected = if (offset == 0) !usePastDate else usePastDate && sdf.format(selectedDate) == sdf.format(cal.time)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (offset == 0) {
                                    usePastDate = false
                                    selectedDate = Date()
                                } else {
                                    usePastDate = true
                                    selectedDate = cal.time
                                }
                            },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AmberGold,
                                selectedLabelColor = Color.Black,
                                containerColor = Color(0xFF1E293B),
                                labelColor = TextSecondary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                // Step 2: Routine vs Custom
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = mode == 0,
                        onClick = { mode = 0 },
                        label = { Text("Use Routine") },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AmberGold,
                            selectedLabelColor = Color.Black,
                            containerColor = Color(0xFF1E293B),
                            labelColor = TextSecondary
                        )
                    )
                    FilterChip(
                        selected = mode == 1,
                        onClick = { mode = 1 },
                        label = { Text("Custom Lifts") },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AmberGold,
                            selectedLabelColor = Color.Black,
                            containerColor = Color(0xFF1E293B),
                            labelColor = TextSecondary
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (mode == 0 && routines.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (suggestion.isScheduledForDate) Color(0xFF0E3A42) else Color(0xFF0F172A),
                        border = BorderStroke(
                            1.dp,
                            if (suggestion.isScheduledForDate) ElectricTeal else SurfaceCardBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .testTag("launcher_routine_suggestion_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = if (suggestion.isScheduledForDate) ElectricTeal else AmberGold,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (suggestion.isScheduledForDate) {
                                        "Suggested for ${suggestion.weekday.fullName}: ${suggestion.routine?.name.orEmpty()}"
                                    } else {
                                        suggestion.reasonLabel
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (suggestion.isScheduledForDate) ElectricTeal else Color(0xFFCBD5E1),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        routines.forEach { routine ->
                            val isSelected = selectedRoutine?.id == routine.id
                            val isScheduledForSelectedDay = suggestion.matchingRoutines.any { it.id == routine.id }
                            val scheduleText = WorkoutScheduleHelper.formatScheduledDays(routine.scheduledDays)
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) Color(0xFF1E293B) else Color(0xFF0F172A),
                                border = BorderStroke(
                                    1.dp,
                                    when {
                                        isSelected -> AmberGold
                                        isScheduledForSelectedDay -> ElectricTeal.copy(alpha = 0.7f)
                                        else -> SurfaceCardBorder
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedRoutine = routine }
                                    .testTag("launcher_routine_option_${routine.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = routine.name,
                                                color = if (isSelected) AmberGold else Color.White,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (isScheduledForSelectedDay) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(ElectricTeal.copy(alpha = 0.2f))
                                                        .border(1.dp, ElectricTeal, RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                                ) {
                                                    Text(
                                                        text = "SUGGESTED",
                                                        color = ElectricTeal,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = "${routine.targetMuscle} · ${routine.exerciseIds.size} lifts · $scheduleText",
                                            color = TextSecondary,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = AmberGold, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text("Workout Name") },
                        placeholder = { Text("e.g. Upper Hypertrophy") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = AmberGold,
                            unfocusedBorderColor = SurfaceCardBorder
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Pick Exercises (${selectedExerciseIds.size} selected):",
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberGold,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    exercises.forEach { ex ->
                        val checked = selectedExerciseIds.contains(ex.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedExerciseIds = if (checked) selectedExerciseIds - ex.id else selectedExerciseIds + ex.id
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    selectedExerciseIds = if (isChecked) selectedExerciseIds + ex.id else selectedExerciseIds - ex.id
                                },
                                colors = CheckboxDefaults.colors(checkedColor = AmberGold, checkmarkColor = Color.Black)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(ex.name, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${ex.category} · ${ex.formatCompactPrescription()}",
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Rest Period Between Sets (seconds)",
                    style = MaterialTheme.typography.labelMedium,
                    color = ElectricTeal,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = restSecondsText,
                    onValueChange = { restSecondsText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Rest (sec)") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("launcher_rest_seconds_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = ElectricTeal,
                        unfocusedBorderColor = SurfaceCardBorder
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(45, 60, 90, 120, 180).forEach { preset ->
                        FilterChip(
                            selected = restSecondsText == preset.toString(),
                            onClick = { restSecondsText = preset.toString() },
                            label = { Text("${preset}s", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricTeal,
                                selectedLabelColor = Color.Black,
                                containerColor = Color(0xFF1E293B),
                                labelColor = TextSecondary
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val targetDate = if (usePastDate) selectedDate else null
                    val restSecs = restSecondsText.toIntOrNull()?.coerceIn(5, 900) ?: 90
                    val routine = selectedRoutine
                    if (mode == 0 && routine != null) {
                        onStartWithRoutine(routine, targetDate, restSecs)
                    } else {
                        val chosen = exercises.filter { selectedExerciseIds.contains(it.id) }
                        onStartCustom(customName.ifBlank { "Custom Workout" }, chosen, targetDate, restSecs)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AmberGold, contentColor = Color.Black),
                modifier = Modifier.testTag("confirm_add_workout_button")
            ) {
                Text("Start Logging", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )

    if (showCalendarPicker) {
        WorkoutCalendarDatePickerDialog(
            initialDate = selectedDate,
            onDismiss = { showCalendarPicker = false },
            onDateSelected = { pickedDate ->
                selectedDate = pickedDate
                usePastDate = true
                showCalendarPicker = false
            }
        )
    }
}
