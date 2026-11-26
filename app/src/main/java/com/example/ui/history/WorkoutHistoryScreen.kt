package com.example.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.analytics.PerformedExercisePr
import com.example.data.analytics.WorkoutAnalyticsHelper
import com.example.data.model.ExerciseRecord
import com.example.data.model.ExerciseTrackingType
import com.example.data.model.WorkoutExerciseLog
import com.example.data.model.WorkoutRoutine
import com.example.data.model.WorkoutSession
import com.example.data.schedule.Weekday
import com.example.data.schedule.WorkoutScheduleHelper
import com.example.ui.theme.AmberGold
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.ElectricTeal
import com.example.ui.theme.GainGreen
import com.example.ui.theme.LossRed
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.components.CsvExportCard
import com.example.ui.components.GarminExportCard
import com.example.ui.components.GoogleSheetsSyncCard
import com.example.ui.components.WeeklyWorkoutScheduleCard
import com.example.ui.components.WorkoutBreakdownSection
import com.example.ui.components.WorkoutCalendarDatePickerDialog
import com.example.ui.tracker.AddNewWorkoutLauncherDialog
import com.example.viewmodel.UiState
import com.example.viewmodel.WorkoutViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkoutHistoryScreen(
    viewModel: WorkoutViewModel,
    onSelectExerciseForChart: (String) -> Unit,
    onStartRoutine: (WorkoutRoutine) -> Unit = {},
    onOpenWorkoutTab: () -> Unit = {}
) {
    val workoutsState by viewModel.workouts.collectAsState()
    val exercisesState by viewModel.exercises.collectAsState()
    val routinesState by viewModel.routines.collectAsState()
    val logsState by viewModel.logs.collectAsState()
    val weeklySchedule by viewModel.weeklySchedule.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Workout Logs, 1 = Routines, 2 = Exercises
    var logSearchQuery by remember { mutableStateOf("") }
    var exerciseSearchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("All") }

    val workouts = (workoutsState as? UiState.Success)?.data ?: emptyList()
    val exercises = (exercisesState as? UiState.Success)?.data ?: emptyList()
    val routines = (routinesState as? UiState.Success)?.data ?: emptyList()
    val allLogs = (logsState as? UiState.Success)?.data ?: emptyList()

    val logsByWorkoutId = remember(allLogs) {
        allLogs.groupBy { it.workoutId }
    }
    val filteredWorkouts = remember(workouts, logsByWorkoutId, logSearchQuery) {
        val q = logSearchQuery.trim()
        if (q.isBlank()) {
            workouts
        } else {
            workouts.filter { w ->
                w.name.contains(q, ignoreCase = true) ||
                    w.notes.contains(q, ignoreCase = true) ||
                    w.dateString.contains(q, ignoreCase = true) ||
                    (logsByWorkoutId[w.id]?.any { it.exerciseName.contains(q, ignoreCase = true) } == true)
            }
        }
    }
    val filteredExercises = remember(exercises, exerciseSearchQuery, selectedCategoryFilter) {
        val q = exerciseSearchQuery.trim()
        exercises.filter { ex ->
            val matchesCat = selectedCategoryFilter == "All" ||
                ex.category.equals(selectedCategoryFilter, ignoreCase = true)
            val matchesQuery = q.isBlank() ||
                ex.name.contains(q, ignoreCase = true) ||
                ex.category.contains(q, ignoreCase = true) ||
                ex.notes.contains(q, ignoreCase = true)
            matchesCat && matchesQuery
        }
    }

    // Add / Edit Workout Dialog State
    var showAddWorkoutDialog by remember { mutableStateOf(false) }
    var launcherInitialDate by remember { mutableStateOf<Date?>(null) }
    var workoutToEdit by remember { mutableStateOf<WorkoutSession?>(null) }
    var workoutToDelete by remember { mutableStateOf<WorkoutSession?>(null) }

    // Exercise Dialog State
    var showExerciseDialog by remember { mutableStateOf(false) }
    var exerciseToEdit by remember { mutableStateOf<ExerciseRecord?>(null) }

    // Routine Dialog State
    var showRoutineDialog by remember { mutableStateOf(false) }
    var routineToEdit by remember { mutableStateOf<WorkoutRoutine?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(bottom = 96.dp)
            .testTag("history_screen")
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = AmberGold,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "History & Workouts",
                        style = MaterialTheme.typography.headlineLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "Logs, grouped routines & exercise library",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }

        // Tab Row (3 Tabs: Workout Logs, Routines, Exercises)
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = AmberGold,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = AmberGold
                )
            },
            divider = {}
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Text(
                        "Logs (${workouts.size})",
                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                        color = if (selectedTab == 0) AmberGold else TextSecondary
                    )
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Text(
                        "Routines (${routines.size})",
                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                        color = if (selectedTab == 1) AmberGold else TextSecondary
                    )
                }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = {
                    Text(
                        "Exercises (${exercises.size})",
                        fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal,
                        color = if (selectedTab == 2) AmberGold else TextSecondary
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (selectedTab) {
            0 -> {
                // Workout History Logs List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp)
                ) {
                    item {
                        Button(
                            onClick = {
                                launcherInitialDate = null
                                showAddWorkoutDialog = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("history_add_workout_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1E293B),
                                contentColor = AmberGold
                            )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add / Log Workout", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        GoogleSheetsSyncCard(viewModel = viewModel)
                        Spacer(modifier = Modifier.height(14.dp))
                        GarminExportCard(viewModel = viewModel, workouts = workouts)
                        Spacer(modifier = Modifier.height(14.dp))
                        CsvExportCard(
                            viewModel = viewModel,
                            workouts = workouts,
                            logs = allLogs
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedTextField(
                            value = logSearchQuery,
                            onValueChange = { logSearchQuery = it },
                            placeholder = { Text("Search workouts, exercises, dates...", color = TextSecondary) },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = "Search Logs", tint = AmberGold)
                            },
                            trailingIcon = {
                                if (logSearchQuery.isNotEmpty()) {
                                    IconButton(onClick = { logSearchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear search", tint = TextSecondary)
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("history_logs_search_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AmberGold,
                                unfocusedBorderColor = SurfaceCardBorder,
                                focusedContainerColor = SurfaceCard,
                                unfocusedContainerColor = SurfaceCard
                            )
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    if (workouts.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("history_empty_workouts_card"),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FitnessCenter,
                                        contentDescription = null,
                                        tint = AmberGold,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "No workouts logged yet",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Tap \"Add / Log Workout\" above to record a session for today or any past date.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    } else if (filteredWorkouts.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "No workouts match \"$logSearchQuery\"",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    TextButton(onClick = { logSearchQuery = "" }) {
                                        Text("Clear Filter", color = AmberGold, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    } else {
                        items(filteredWorkouts, key = { it.id }) { workout ->
                            val logsForWorkout = logsByWorkoutId[workout.id] ?: emptyList()
                            ExpandableWorkoutHistoryCard(
                                workout = workout,
                                workoutLogs = logsForWorkout,
                                exercisesCatalog = exercises,
                                onEditDetails = { workoutToEdit = workout },
                                onEditExercises = {
                                    viewModel.startEditingWorkout(workout)
                                    onOpenWorkoutTab()
                                },
                                onExportGarminFit = { viewModel.exportSingleWorkoutToFit(workout) },
                                onShare = { viewModel.prepareWorkoutSocialShare(workout) },
                                onDelete = { workoutToDelete = workout }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }
            1 -> {
                // Routines Management List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp)
                ) {
                    item {
                        WeeklyWorkoutScheduleCard(
                            schedule = weeklySchedule,
                            onDayClick = { day ->
                                launcherInitialDate = day.date
                                showAddWorkoutDialog = true
                            }
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = {
                                routineToEdit = null
                                showRoutineDialog = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("history_create_routine_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1E293B),
                                contentColor = AmberGold
                            )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Create Grouped Routine", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    items(routines) { routine ->
                        RoutineCardItem(
                            routine = routine,
                            allExercises = exercises,
                            onStart = { onStartRoutine(routine) },
                            onEdit = {
                                routineToEdit = routine
                                showRoutineDialog = true
                            },
                            onDelete = { viewModel.deleteRoutine(routine.id) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
            2 -> {
                // Exercise Library Management List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp)
                ) {
                    item {
                        Button(
                            onClick = {
                                exerciseToEdit = null
                                showExerciseDialog = true
                            },
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
                            Text("Add Custom Exercise", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = exerciseSearchQuery,
                            onValueChange = { exerciseSearchQuery = it },
                            placeholder = { Text("Search exercises by name or muscle...", color = TextSecondary) },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = "Search Exercises", tint = AmberGold)
                            },
                            trailingIcon = {
                                if (exerciseSearchQuery.isNotEmpty()) {
                                    IconButton(onClick = { exerciseSearchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear search", tint = TextSecondary)
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("history_exercises_search_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AmberGold,
                                unfocusedBorderColor = SurfaceCardBorder,
                                focusedContainerColor = SurfaceCard,
                                unfocusedContainerColor = SurfaceCard
                            )
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        val filterCategories = listOf("All", "Chest", "Back", "Legs", "Shoulders", "Arms", "Core")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            filterCategories.forEach { cat ->
                                val isSel = selectedCategoryFilter == cat
                                FilterChip(
                                    selected = isSel,
                                    onClick = { selectedCategoryFilter = cat },
                                    label = {
                                        Text(
                                            text = cat,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    modifier = Modifier.testTag("history_exercise_filter_${cat.lowercase()}"),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AmberGold,
                                        selectedLabelColor = Color.Black,
                                        containerColor = SurfaceCard,
                                        labelColor = TextSecondary
                                    )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    if (filteredExercises.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "No matching exercises found",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    TextButton(onClick = {
                                        exerciseSearchQuery = ""
                                        selectedCategoryFilter = "All"
                                    }) {
                                        Text("Reset Filters", color = AmberGold, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    } else {
                        items(filteredExercises, key = { it.id }) { ex ->
                            val performedPr = remember(allLogs, ex.id, ex.name) {
                                WorkoutAnalyticsHelper.computePerformedExercisePrFor(ex, allLogs)
                            }
                            ExerciseLibraryItem(
                                exercise = ex,
                                performedPr = performedPr,
                                onClick = {
                                    if (performedPr != null) {
                                        onSelectExerciseForChart(ex.id)
                                    } else {
                                        exerciseToEdit = ex
                                        showExerciseDialog = true
                                    }
                                },
                                onEdit = {
                                    exerciseToEdit = ex
                                    showExerciseDialog = true
                                }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }
                }
            }
        }
    }

    // Add New / Past Workout Dialog
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
                onOpenWorkoutTab()
            },
            onStartCustom = { name, selectedExs, date, restSecs ->
                showAddWorkoutDialog = false
                launcherInitialDate = null
                viewModel.startCustomWorkout(name, selectedExs, date, restSeconds = restSecs)
                onOpenWorkoutTab()
            }
        )
    }

    // Edit Workout Dialog
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
                onOpenWorkoutTab()
            },
            onDelete = {
                workoutToEdit = null
                workoutToDelete = targetWorkout
            }
        )
    }

    // Confirm Delete Workout Dialog
    workoutToDelete?.let { targetWorkout ->
        AlertDialog(
            onDismissRequest = { workoutToDelete = null },
            modifier = Modifier.testTag("delete_workout_dialog"),
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
                    modifier = Modifier.testTag("confirm_delete_workout_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = LossRed, contentColor = Color.White)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { workoutToDelete = null },
                    modifier = Modifier.testTag("cancel_delete_workout_button")
                ) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // Add / Edit Exercise Dialog
    if (showExerciseDialog) {
        AddEditExerciseDialog(
            initialExercise = exerciseToEdit,
            onDismiss = { showExerciseDialog = false },
            onSave = { record ->
                viewModel.saveExercise(record) {
                    showExerciseDialog = false
                }
            },
            onDelete = { exId ->
                viewModel.deleteExercise(exId) {
                    showExerciseDialog = false
                }
            }
        )
    }

    // Add / Edit Grouped Routine Dialog
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
}

@Composable
fun ExpandableWorkoutHistoryCard(
    workout: WorkoutSession,
    workoutLogs: List<WorkoutExerciseLog> = emptyList(),
    exercisesCatalog: List<ExerciseRecord> = emptyList(),
    onEditDetails: () -> Unit = {},
    onEditExercises: () -> Unit = {},
    onExportGarminFit: () -> Unit = {},
    onShare: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .testTag("history_workout_card_${workout.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${workout.dateString} · ${workout.durationMinutes} mins · ${workout.exerciseCount} exercises",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${workout.totalVolumeLbs.toInt()} lbs",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AmberGold
                        )
                        Text(
                            text = "total volume",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse workout details" else "Expand workout details",
                        tint = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Always-accessible Edit & Delete bar with full >=48dp touch target height
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onEditExercises,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("history_edit_sets_${workout.id}"),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AmberGold.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberGold)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit Sets", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onEditDetails,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("history_edit_info_${workout.id}"),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ElectricTeal.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricTeal)
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit Info", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }

                IconButton(
                    onClick = onShare,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("history_share_workout_${workout.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share Workout Graphic",
                        tint = AmberGold,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onExportGarminFit,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("history_export_fit_${workout.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Watch,
                        contentDescription = "Export Workout to Garmin .FIT",
                        tint = CyanAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("history_delete_workout_${workout.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Workout",
                        tint = LossRed,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(10.dp))
                    if (workout.notes.isNotBlank()) {
                        Text(
                            text = "Notes: ${workout.notes}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFCBD5E1)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    Text(
                        text = "Completed ${workout.completedSetsCount} total sets across ${workout.exerciseCount} exercises.",
                        style = MaterialTheme.typography.bodySmall,
                        color = GainGreen
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    WorkoutBreakdownSection(
                        workoutId = workout.id,
                        logs = workoutLogs,
                        exercisesCatalog = exercisesCatalog
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditWorkoutMetadataDialog(
    workout: WorkoutSession,
    onDismiss: () -> Unit,
    onSaveDetails: (String, Date, Int, String, Double) -> Unit,
    onEditSetsAndExercises: () -> Unit,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(workout.name) }
    var durationText by remember { mutableStateOf(workout.durationMinutes.toString()) }
    var volumeText by remember { mutableStateOf(workout.totalVolumeLbs.toInt().toString()) }
    var notes by remember { mutableStateOf(workout.notes) }
    var selectedDate by remember { mutableStateOf(workout.createdAt?.toDate() ?: Date()) }
    var showCalendar by remember { mutableStateOf(false) }

    val sdf = remember { SimpleDateFormat("EEE, MMM d, yyyy", Locale.US) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        title = {
            Text("Edit Workout", color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedButton(
                    onClick = onEditSetsAndExercises,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberGold),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AmberGold)
                ) {
                    Icon(Icons.Default.FitnessCenter, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Edit Exercises & Sets", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Workout Name") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = AmberGold,
                        unfocusedBorderColor = SurfaceCardBorder
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0F172A))
                        .clickable { showCalendar = true }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = ElectricTeal, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(sdf.format(selectedDate), color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("Change Date", color = AmberGold, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = durationText,
                        onValueChange = { durationText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Minutes") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = AmberGold,
                            unfocusedBorderColor = SurfaceCardBorder
                        )
                    )
                    OutlinedTextField(
                        value = volumeText,
                        onValueChange = { volumeText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Volume (lbs)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = AmberGold,
                            unfocusedBorderColor = SurfaceCardBorder
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
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
                    val dur = durationText.toIntOrNull() ?: workout.durationMinutes
                    val vol = volumeText.toDoubleOrNull() ?: workout.totalVolumeLbs
                    onSaveDetails(name.ifBlank { workout.name }, selectedDate, dur, notes, vol)
                },
                colors = ButtonDefaults.buttonColors(containerColor = AmberGold, contentColor = Color.Black)
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) {
                    Text("Delete", color = LossRed)
                }
                Spacer(modifier = Modifier.width(6.dp))
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        }
    )

    if (showCalendar) {
        WorkoutCalendarDatePickerDialog(
            initialDate = selectedDate,
            onDismiss = { showCalendar = false },
            onDateSelected = { pickedDate ->
                selectedDate = pickedDate
                showCalendar = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoutineCardItem(
    routine: WorkoutRoutine,
    allExercises: List<ExerciseRecord>,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
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
                            color = Color.White,
                            modifier = Modifier.weight(1f, fill = false)
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

                    Spacer(modifier = Modifier.height(4.dp))
                    val scheduleLabel = WorkoutScheduleHelper.formatScheduledDays(routine.scheduledDays)
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
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Routine", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Routine", tint = LossRed, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Exercise pills in routine
            val exerciseNames = routine.exerciseIds.mapNotNull { id ->
                allExercises.find { it.id == id }?.name
            }

            if (exerciseNames.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    exerciseNames.forEach { name ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E293B))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(name, color = Color(0xFFCBD5E1), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Start Routine Button
            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AmberGold,
                    contentColor = Color.Black
                )
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Start Routine", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun ExerciseLibraryItem(
    exercise: ExerciseRecord,
    performedPr: PerformedExercisePr? = null,
    onClick: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = exercise.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (exercise.isPrimary) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Primary Lift",
                            tint = AmberGold,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${exercise.category} · ${exercise.formatCompactPrescription()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                if (exercise.notes.isNotBlank()) {
                    Text(
                        text = exercise.notes,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF64748B)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (performedPr != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = performedPr.formatPrimaryMetricLabel(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        val secondaryLabel = if (performedPr.percentIncrease > 0.0) {
                            "+${performedPr.percentIncrease}%"
                        } else if (performedPr.trackingType != ExerciseTrackingType.WEIGHTED) {
                            performedPr.trackingType.shortLabel
                        } else {
                            "+0.0%"
                        }
                        Text(
                            text = secondaryLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (performedPr.percentIncrease > 0.0) GainGreen else ElectricTeal
                        )
                    }
                } else {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Not logged",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary
                        )
                        Text(
                            text = exercise.resolvedTrackingType().shortLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Exercise",
                        tint = AmberGold,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditExerciseDialog(
    initialExercise: ExerciseRecord?,
    onDismiss: () -> Unit,
    onSave: (ExerciseRecord) -> Unit,
    onDelete: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialExercise?.name ?: "") }
    var category by remember { mutableStateOf(initialExercise?.category ?: "Chest") }
    var trackingType by remember {
        mutableStateOf(initialExercise?.resolvedTrackingType() ?: ExerciseTrackingType.WEIGHTED)
    }
    var startingWeight by remember {
        mutableStateOf(
            initialExercise?.startingWeightLbs?.let {
                if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()
            } ?: "135"
        )
    }
    var currentMaxWeight by remember {
        mutableStateOf(
            initialExercise?.currentMaxWeightLbs?.let {
                if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()
            } ?: "135"
        )
    }
    var defaultSetsText by remember { mutableStateOf((initialExercise?.defaultSets ?: 3).toString()) }
    var defaultRepsText by remember { mutableStateOf((initialExercise?.defaultReps ?: 8).toString()) }
    var defaultDurationText by remember {
        mutableStateOf((initialExercise?.defaultDurationSeconds ?: 60).toString())
    }
    var bestDurationText by remember {
        mutableStateOf(
            (initialExercise?.currentBestDurationSeconds ?: initialExercise?.defaultDurationSeconds ?: 60).toString()
        )
    }
    var isPrimary by remember { mutableStateOf(initialExercise?.isPrimary ?: false) }
    var notes by remember { mutableStateOf(initialExercise?.notes ?: "") }

    val categories = listOf("Chest", "Back", "Legs", "Shoulders", "Arms", "Core")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialExercise != null) "Edit Exercise" else "Add New Exercise",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Exercise Name") },
                    placeholder = { Text("e.g. Plank, Leg Raises, Weighted Crunch") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("exercise_name_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = AmberGold,
                        unfocusedBorderColor = SurfaceCardBorder
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text("Tracking Mode", style = MaterialTheme.typography.labelSmall, color = ElectricTeal, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ExerciseTrackingType.entries.forEach { mode ->
                        val selected = trackingType == mode
                        FilterChip(
                            selected = selected,
                            onClick = {
                                val previous = trackingType
                                trackingType = mode
                                if (initialExercise == null) {
                                    if (mode == ExerciseTrackingType.WEIGHTED && previous != ExerciseTrackingType.WEIGHTED) {
                                        if (startingWeight == "0") startingWeight = "135"
                                        if (currentMaxWeight == "0") currentMaxWeight = "135"
                                    } else if (mode != ExerciseTrackingType.WEIGHTED) {
                                        if (startingWeight == "135") startingWeight = "0"
                                        if (currentMaxWeight == "135") currentMaxWeight = "0"
                                        if (mode == ExerciseTrackingType.BODYWEIGHT_REPS && defaultRepsText == "8") {
                                            defaultRepsText = "12"
                                        }
                                    }
                                }
                            },
                            label = { Text(mode.shortLabel, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("exercise_tracking_chip_${mode.id.lowercase()}"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricTeal,
                                selectedLabelColor = Color.Black,
                                containerColor = Color(0xFF1E293B),
                                labelColor = TextSecondary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text("Muscle Category", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { cat ->
                        val selected = category == cat
                        FilterChip(
                            selected = selected,
                            onClick = { category = cat },
                            label = { Text(cat, style = MaterialTheme.typography.labelSmall) },
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

                if (trackingType == ExerciseTrackingType.TIMED_HOLD) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = defaultDurationText,
                            onValueChange = { defaultDurationText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Default Hold (s)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("exercise_default_duration_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = ElectricTeal,
                                unfocusedBorderColor = SurfaceCardBorder
                            )
                        )
                        OutlinedTextField(
                            value = bestDurationText,
                            onValueChange = { bestDurationText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Target Hold (s)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("exercise_best_duration_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = ElectricTeal,
                                unfocusedBorderColor = SurfaceCardBorder
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = defaultSetsText,
                            onValueChange = { defaultSetsText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Default Sets") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("exercise_default_sets_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = ElectricTeal,
                                unfocusedBorderColor = SurfaceCardBorder
                            )
                        )
                        OutlinedTextField(
                            value = currentMaxWeight,
                            onValueChange = { currentMaxWeight = it },
                            label = { Text("+Wt (lbs, 0=BW)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AmberGold,
                                unfocusedBorderColor = SurfaceCardBorder
                            )
                        )
                    }
                } else {
                    val startLabel = if (trackingType == ExerciseTrackingType.BODYWEIGHT_REPS) "Start +Wt (0=BW)" else "Start Wt (lbs)"
                    val maxLabel = if (trackingType == ExerciseTrackingType.BODYWEIGHT_REPS) "Working +Wt (lbs)" else "Working Wt (lbs)"

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = startingWeight,
                            onValueChange = { startingWeight = it },
                            label = { Text(startLabel) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AmberGold,
                                unfocusedBorderColor = SurfaceCardBorder
                            )
                        )
                        OutlinedTextField(
                            value = currentMaxWeight,
                            onValueChange = { currentMaxWeight = it },
                            label = { Text(maxLabel) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AmberGold,
                                unfocusedBorderColor = SurfaceCardBorder
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = defaultSetsText,
                            onValueChange = { defaultSetsText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Default Sets") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("exercise_default_sets_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = ElectricTeal,
                                unfocusedBorderColor = SurfaceCardBorder
                            )
                        )
                        OutlinedTextField(
                            value = defaultRepsText,
                            onValueChange = { defaultRepsText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Default Reps") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("exercise_default_reps_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = ElectricTeal,
                                unfocusedBorderColor = SurfaceCardBorder
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { isPrimary = !isPrimary }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isPrimary,
                        onCheckedChange = { isPrimary = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = AmberGold,
                            checkmarkColor = Color.Black
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pin as Primary Lift (⭐)", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Form Notes / Cue (optional)") },
                    placeholder = { Text("e.g. Brace core, 60s hold") },
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
                    if (name.isNotBlank()) {
                        val defaultZeroOrWeighted = if (trackingType == ExerciseTrackingType.WEIGHTED) 100.0 else 0.0
                        val max = (currentMaxWeight.toDoubleOrNull() ?: defaultZeroOrWeighted).coerceAtLeast(0.0)
                        val start = if (trackingType == ExerciseTrackingType.TIMED_HOLD) {
                            initialExercise?.startingWeightLbs ?: max
                        } else {
                            (startingWeight.toDoubleOrNull() ?: max).coerceAtLeast(0.0)
                        }
                        val setsCount = defaultSetsText.toIntOrNull()?.coerceIn(1, 20) ?: 3
                        val repsCount = if (trackingType == ExerciseTrackingType.TIMED_HOLD) {
                            1
                        } else {
                            defaultRepsText.toIntOrNull()?.coerceIn(1, 100) ?: 8
                        }
                        val defDuration = defaultDurationText.toIntOrNull()?.coerceIn(5, 3600) ?: 60
                        val startDuration = initialExercise?.startingDurationSeconds?.takeIf { it > 0 } ?: defDuration
                        val updated = (initialExercise ?: ExerciseRecord(historyCount = 0)).copy(
                            name = name.trim(),
                            category = category,
                            trackingType = trackingType.id,
                            startingWeightLbs = start,
                            currentMaxWeightLbs = max,
                            personalRecord1RMLbs = initialExercise?.personalRecord1RMLbs ?: 0.0,
                            percentIncrease = initialExercise?.percentIncrease ?: 0.0,
                            defaultSets = setsCount,
                            defaultReps = repsCount,
                            defaultDurationSeconds = defDuration,
                            startingDurationSeconds = startDuration,
                            currentBestDurationSeconds = initialExercise?.currentBestDurationSeconds ?: defDuration,
                            bestReps = initialExercise?.bestReps ?: 0,
                            isPrimary = isPrimary,
                            notes = notes
                        )
                        onSave(updated)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AmberGold, contentColor = Color.Black),
                modifier = Modifier.testTag("save_exercise_confirm_button")
            ) {
                Text("Save Exercise", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                if (initialExercise != null) {
                    TextButton(onClick = { onDelete(initialExercise.id) }) {
                        Text("Delete", color = LossRed)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        },
        containerColor = SurfaceCard
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditRoutineDialog(
    initialRoutine: WorkoutRoutine?,
    availableExercises: List<ExerciseRecord>,
    onDismiss: () -> Unit,
    onSave: (WorkoutRoutine) -> Unit
) {
    var name by remember { mutableStateOf(initialRoutine?.name ?: "") }
    var description by remember { mutableStateOf(initialRoutine?.description ?: "") }
    var targetMuscle by remember { mutableStateOf(initialRoutine?.targetMuscle ?: "Push") }
    var selectedExerciseIds by remember { mutableStateOf(initialRoutine?.exerciseIds?.toSet() ?: emptySet()) }
    var defaultSets by remember { mutableIntStateOf(initialRoutine?.defaultSets ?: 3) }
    var restSecondsText by remember { mutableStateOf((initialRoutine?.restSeconds ?: 90).toString()) }
    var selectedScheduledDays by remember {
        mutableStateOf(initialRoutine?.scheduledDays?.filter { it in 1..7 }?.toSet() ?: emptySet())
    }

    val muscleOptions = listOf("Push", "Pull", "Legs", "Upper", "Lower", "Full Body", "Arms", "Core")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialRoutine != null) "Edit Routine" else "Create Grouped Routine",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Routine Name") },
                    placeholder = { Text("e.g. Push Hypertrophy A") },
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
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    placeholder = { Text("e.g. Heavy chest compound & tricep burnout") },
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
                    value = restSecondsText,
                    onValueChange = { restSecondsText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Rest Period (seconds)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("routine_rest_seconds_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = ElectricTeal,
                        unfocusedBorderColor = SurfaceCardBorder
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Scheduled Day(s) of Week (${WorkoutScheduleHelper.formatScheduledDays(selectedScheduledDays.toList())})",
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricTeal,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Weekday.entries.forEach { day ->
                        val isDaySelected = selectedScheduledDays.contains(day.isoDay)
                        FilterChip(
                            selected = isDaySelected,
                            onClick = {
                                selectedScheduledDays = if (isDaySelected) {
                                    selectedScheduledDays - day.isoDay
                                } else {
                                    selectedScheduledDays + day.isoDay
                                }
                            },
                            label = {
                                Text(
                                    text = day.shortLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isDaySelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            modifier = Modifier.testTag("routine_day_chip_${day.shortLabel.lowercase()}"),
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

                Text("Target Split", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    muscleOptions.forEach { opt ->
                        FilterChip(
                            selected = targetMuscle == opt,
                            onClick = { targetMuscle = opt },
                            label = { Text(opt, style = MaterialTheme.typography.labelSmall) },
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

                Text(
                    text = "Select Exercises for this Routine (${selectedExerciseIds.size} selected):",
                    style = MaterialTheme.typography.labelSmall,
                    color = AmberGold,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                availableExercises.forEach { ex ->
                    val isChecked = selectedExerciseIds.contains(ex.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                selectedExerciseIds = if (isChecked) {
                                    selectedExerciseIds - ex.id
                                } else {
                                    selectedExerciseIds + ex.id
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { checked ->
                                selectedExerciseIds = if (checked) {
                                    selectedExerciseIds + ex.id
                                } else {
                                    selectedExerciseIds - ex.id
                                }
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = AmberGold,
                                checkmarkColor = Color.Black
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(ex.name, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("${ex.category} · ${ex.formatCompactPrescription()}", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val restSecs = restSecondsText.toIntOrNull()?.coerceIn(5, 900) ?: 90
                        val routine = (initialRoutine ?: WorkoutRoutine()).copy(
                            name = name,
                            description = description,
                            targetMuscle = targetMuscle,
                            exerciseIds = selectedExerciseIds.toList(),
                            defaultSets = defaultSets,
                            restSeconds = restSecs,
                            scheduledDays = selectedScheduledDays.sorted()
                        )
                        onSave(routine)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AmberGold, contentColor = Color.Black),
                modifier = Modifier.testTag("save_routine_confirm_button")
            ) {
                Text("Save Routine", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = SurfaceCard
    )
}
