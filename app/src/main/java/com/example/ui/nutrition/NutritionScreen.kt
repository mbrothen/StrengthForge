package com.example.ui.nutrition

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.model.MealEntry
import com.example.data.model.UserProfile
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
import com.example.viewmodel.CsvExportUiState
import com.example.viewmodel.UiState
import com.example.viewmodel.WorkoutViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs

@Composable
fun NutritionScreen(viewModel: WorkoutViewModel) {
    val context = LocalContext.current
    val profileState by viewModel.userProfile.collectAsState()
    val dayNutrition by viewModel.selectedDayNutrition.collectAsState()
    val selectedDate by viewModel.selectedNutritionDate.collectAsState()
    val nutritionHistory by viewModel.nutritionHistory.collectAsState()
    val csvExportState by viewModel.csvExportState.collectAsState()

    val profile = (profileState as? UiState.Success)?.data ?: UserProfile()
    val isToday = selectedDate == viewModel.todayDateString

    var showQuickAddDialog by remember { mutableStateOf(false) }
    var showEditTargetsDialog by remember { mutableStateOf(false) }
    var showEditTotalsDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var filePendingSave by remember { mutableStateOf<File?>(null) }

    val saveCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { destinationUri: Uri? ->
        val sourceFile = filePendingSave
        filePendingSave = null
        if (destinationUri != null && sourceFile != null && sourceFile.exists()) {
            try {
                context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                    sourceFile.inputStream().use { input -> input.copyTo(out) }
                }
                Toast.makeText(context, "Saved ${sourceFile.name} to device.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to save CSV: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val consumedCals = dayNutrition?.calories ?: 0
    val consumedP = dayNutrition?.proteinG ?: 0
    val consumedC = dayNutrition?.carbsG ?: 0
    val consumedF = dayNutrition?.fatsG ?: 0
    val loggedMeals = dayNutrition?.parsedMeals().orEmpty()

    val targetCals = profile.targetCalories
    val targetP = profile.proteinGrams
    val targetC = profile.carbsGrams
    val targetF = profile.fatsGrams

    val formattedDateLabel = remember(selectedDate, isToday) {
        val raw = try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val formatter = SimpleDateFormat("EEE, MMM d", Locale.US)
            parser.parse(selectedDate)?.let { formatter.format(it) } ?: selectedDate
        } catch (_: Exception) {
            selectedDate
        }
        if (isToday) "Today · $raw" else raw
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 96.dp)
            .testTag("nutrition_screen")
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Restaurant,
                        contentDescription = null,
                        tint = AmberGold,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Macro & Calories",
                        style = MaterialTheme.typography.headlineLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "Daily nutrition plan for muscle gain & fat loss",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            IconButtonWithBorder(
                icon = Icons.Default.Edit,
                contentDescription = "Edit Targets",
                onClick = { showEditTargetsDialog = true }
            )
        }

        // Date Navigation Strip
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, SurfaceCardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.shiftSelectedNutritionDate(-1) },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("nutrition_prev_day_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous Day",
                        tint = AmberGold
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formattedDateLabel,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("nutrition_selected_date_label")
                    )
                    if (!isToday) {
                        Text(
                            text = "Tap to jump to Today",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricTeal,
                            modifier = Modifier
                                .clickable { viewModel.setSelectedNutritionDate(viewModel.todayDateString) }
                                .padding(vertical = 2.dp)
                                .testTag("nutrition_jump_today_button")
                        )
                    }
                }

                IconButton(
                    onClick = { viewModel.shiftSelectedNutritionDate(1) },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("nutrition_next_day_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next Day",
                        tint = AmberGold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Hero Calorie Ring & Target Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, SurfaceCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isToday) "TODAY'S CALORIE INTAKE" else "CALORIE INTAKE ($selectedDate)",
                    style = MaterialTheme.typography.labelSmall,
                    color = AmberGold,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "$consumedCals",
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.testTag("nutrition_consumed_calories")
                )

                Text(
                    text = "of ${profile.targetCaloriesMin} - ${profile.targetCaloriesMax} kcal target",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF94A3B8)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Status pill
                val inRange = consumedCals in profile.targetCaloriesMin..profile.targetCaloriesMax
                val overMax = consumedCals > profile.targetCaloriesMax
                val remaining = targetCals - consumedCals
                val (statusBg, statusColor, statusLabel) = when {
                    consumedCals == 0 -> Triple(
                        Color(0xFF1E293B),
                        TextSecondary,
                        "0 logged · Tap Log Food below"
                    )
                    inRange -> Triple(
                        Color(0xFF064E3B),
                        GainGreen,
                        "Within Target Range"
                    )
                    overMax -> Triple(
                        Color(0xFF3F151A),
                        LossRed,
                        "${consumedCals - profile.targetCaloriesMax} kcal over target"
                    )
                    else -> Triple(
                        Color(0xFF1E293B),
                        ElectricTeal,
                        "${abs(remaining)} kcal remaining"
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(statusBg)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = statusColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Macro Cards (Protein, Carbs, Fats)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MacroPillCard(
                name = "Protein",
                current = consumedP,
                target = targetP,
                targetRange = "Target ${targetP}g",
                color = AmberGold,
                modifier = Modifier.weight(1f)
            )
            MacroPillCard(
                name = "Carbs",
                current = consumedC,
                target = targetC,
                targetRange = "Target ${targetC}g",
                color = CyanAccent,
                modifier = Modifier.weight(1f)
            )
            MacroPillCard(
                name = "Fats",
                current = consumedF,
                target = targetF,
                targetRange = "Target ${targetF}g",
                color = Color(0xFFA855F7),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Log + Edit Totals + Reset Action Row
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Button(
                onClick = { showQuickAddDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("log_food_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AmberGold,
                    contentColor = Color.Black
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Log Food / Snack", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { showEditTotalsDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("edit_nutrition_totals_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, ElectricTeal.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricTeal)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Edit Totals", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { showResetConfirmDialog = true },
                    enabled = consumedCals > 0 || loggedMeals.isNotEmpty(),
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("reset_nutrition_day_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, LossRed.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = LossRed)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reset Day", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Logged Meals List for Selected Day
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, SurfaceCardBorder)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isToday) "LOGGED MEALS TODAY (${loggedMeals.size})" else "LOGGED MEALS (${loggedMeals.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricTeal,
                        fontWeight = FontWeight.Bold
                    )
                    if (nutritionHistory.isNotEmpty()) {
                        Text(
                            text = "${nutritionHistory.size} day history",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (loggedMeals.isEmpty()) {
                    Text(
                        text = if (consumedCals > 0) {
                            "Totals set manually ($consumedCals kcal). Tap \"Log Food / Snack\" to add itemized meals."
                        } else {
                            "No meals logged for $selectedDate yet. Tap \"Log Food / Snack\" or choose a saved preset."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                } else {
                    loggedMeals.forEachIndexed { idx, meal ->
                        LoggedMealRow(
                            meal = meal,
                            onDelete = { viewModel.removeMealFromDay(meal.id, selectedDate) }
                        )
                        if (idx < loggedMeals.lastIndex) {
                            HorizontalDivider(
                                color = Color(0xFF1E293B),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedButton(
                    onClick = { viewModel.exportNutritionToCsv() },
                    enabled = (nutritionHistory.isNotEmpty() || dayNutrition != null) && csvExportState !is CsvExportUiState.Exporting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("export_nutrition_csv_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, ElectricTeal),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricTeal)
                ) {
                    if (csvExportState is CsvExportUiState.Exporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = ElectricTeal,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generating CSV...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export Nutrition CSV", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Dynamic Strategy Card reading from profile targets
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, SurfaceCardBorder)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "NUTRITION TARGETS & STRATEGY",
                    style = MaterialTheme.typography.labelSmall,
                    color = AmberGold,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(10.dp))

                NutritionRuleItem(
                    title = "High Protein Target (${targetP}g)",
                    desc = "Preserves lean muscle mass and promotes muscle protein synthesis across training blocks."
                )

                Spacer(modifier = Modifier.height(10.dp))

                NutritionRuleItem(
                    title = "Carbohydrate Fuel (${targetC}g)",
                    desc = "Supports heavy compound lifts and recovery inside your ${profile.targetCaloriesMin}-${profile.targetCaloriesMax} kcal window."
                )

                Spacer(modifier = Modifier.height(10.dp))

                NutritionRuleItem(
                    title = "Dietary Fats (${targetF}g)",
                    desc = "Essential for hormonal optimization and joint recovery."
                )
            }
        }
    }

    // Quick Add Food Dialog with Saved Meal Presets
    if (showQuickAddDialog) {
        val presets = profile.resolvedMealPresets()
        var mealName by remember { mutableStateOf("Meal / Snack") }
        var addCals by remember { mutableStateOf("400") }
        var addP by remember { mutableStateOf("35") }
        var addC by remember { mutableStateOf("40") }
        var addF by remember { mutableStateOf("10") }
        var saveAsPreset by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showQuickAddDialog = false },
            title = { Text("Log Food / Meal", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "Quick Presets (tap to load)",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricTeal,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presets.forEach { preset ->
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF1E293B))
                                    .border(1.dp, SurfaceCardBorder, RoundedCornerShape(10.dp))
                                    .clickable {
                                        mealName = preset.name
                                        addCals = preset.calories.toString()
                                        addP = preset.proteinG.toString()
                                        addC = preset.carbsG.toString()
                                        addF = preset.fatsG.toString()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = preset.name,
                                        color = AmberGold,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${preset.calories} kcal · ${preset.proteinG}P",
                                        color = TextSecondary,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp
                                    )
                                }
                                if (!preset.isDefault) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Delete preset ${preset.name}",
                                        tint = LossRed,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { viewModel.deleteMealPreset(preset.id) }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = mealName,
                        onValueChange = { mealName = it },
                        label = { Text("Meal / Snack Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("meal_name_input"),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = addCals,
                        onValueChange = { addCals = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Calories (kcal)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = addP,
                        onValueChange = { addP = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Protein (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = addC,
                        onValueChange = { addC = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Carbs (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = addF,
                        onValueChange = { addF = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Fats (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { saveAsPreset = !saveAsPreset }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = saveAsPreset,
                            onCheckedChange = { saveAsPreset = it },
                            colors = CheckboxDefaults.colors(checkedColor = ElectricTeal, checkmarkColor = Color.Black)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save as reusable preset", color = Color.White, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val c = addCals.toIntOrNull() ?: 0
                        val p = addP.toIntOrNull() ?: 0
                        val cb = addC.toIntOrNull() ?: 0
                        val f = addF.toIntOrNull() ?: 0
                        val cleanName = mealName.trim().ifBlank { "Meal / Snack" }
                        viewModel.logNutrition(
                            calories = c,
                            protein = p,
                            carbs = cb,
                            fats = f,
                            mealName = cleanName,
                            dateString = selectedDate
                        )
                        if (saveAsPreset) {
                            viewModel.saveMealPreset(cleanName, c, p, cb, f)
                        }
                        showQuickAddDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberGold, contentColor = Color.Black)
                ) {
                    Text("Add to Log", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuickAddDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceCard
        )
    }

    // Edit Day Totals Dialog
    if (showEditTotalsDialog) {
        var totalCalsText by remember(dayNutrition) { mutableStateOf(consumedCals.toString()) }
        var totalPText by remember(dayNutrition) { mutableStateOf(consumedP.toString()) }
        var totalCText by remember(dayNutrition) { mutableStateOf(consumedC.toString()) }
        var totalFText by remember(dayNutrition) { mutableStateOf(consumedF.toString()) }

        AlertDialog(
            onDismissRequest = { showEditTotalsDialog = false },
            containerColor = SurfaceCard,
            title = { Text("Edit Day Totals ($selectedDate)", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = totalCalsText,
                        onValueChange = { totalCalsText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Calories (kcal)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                    OutlinedTextField(
                        value = totalPText,
                        onValueChange = { totalPText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Protein (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                    OutlinedTextField(
                        value = totalCText,
                        onValueChange = { totalCText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Carbs (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                    OutlinedTextField(
                        value = totalFText,
                        onValueChange = { totalFText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Fats (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setDailyNutritionTotals(
                            calories = totalCalsText.toIntOrNull() ?: consumedCals,
                            protein = totalPText.toIntOrNull() ?: consumedP,
                            carbs = totalCText.toIntOrNull() ?: consumedC,
                            fats = totalFText.toIntOrNull() ?: consumedF,
                            dateString = selectedDate
                        )
                        showEditTotalsDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberGold, contentColor = Color.Black)
                ) {
                    Text("Save Totals", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditTotalsDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // Confirm Reset Day Dialog
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            containerColor = SurfaceCard,
            title = { Text("Reset $selectedDate?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Clear all logged calories, macros, and meal items for $selectedDate back to 0?",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetDailyNutrition(selectedDate)
                        showResetConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LossRed, contentColor = Color.White)
                ) {
                    Text("Reset to 0", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // Edit Targets Dialog
    if (showEditTargetsDialog) {
        var minCalsText by remember { mutableStateOf(profile.targetCaloriesMin.toString()) }
        var maxCalsText by remember { mutableStateOf(profile.targetCaloriesMax.toString()) }
        var pText by remember { mutableStateOf(profile.proteinGrams.toString()) }
        var cText by remember { mutableStateOf(profile.carbsGrams.toString()) }
        var fText by remember { mutableStateOf(profile.fatsGrams.toString()) }

        AlertDialog(
            onDismissRequest = { showEditTargetsDialog = false },
            title = { Text("Adjust Targets", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = minCalsText,
                            onValueChange = { minCalsText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Min kcal") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                        )
                        OutlinedTextField(
                            value = maxCalsText,
                            onValueChange = { maxCalsText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Max kcal") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pText,
                        onValueChange = { pText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Protein (g)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = cText,
                        onValueChange = { cText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Carbs (g)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = fText,
                        onValueChange = { fText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Fats (g)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val minC = minCalsText.toIntOrNull() ?: profile.targetCaloriesMin
                        val maxC = maxCalsText.toIntOrNull() ?: profile.targetCaloriesMax
                        val p = pText.toIntOrNull() ?: profile.proteinGrams
                        val cb = cText.toIntOrNull() ?: profile.carbsGrams
                        val f = fText.toIntOrNull() ?: profile.fatsGrams
                        val avgC = (minC + maxC) / 2
                        viewModel.updateMacroTargets(avgC, minC, maxC, p, cb, f)
                        showEditTargetsDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberGold, contentColor = Color.Black)
                ) {
                    Text("Save Targets", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditTargetsDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceCard
        )
    }

    // CSV Export Ready Dialog
    val readyState = csvExportState as? CsvExportUiState.Ready
    if (readyState != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearCsvExportStatus() },
            containerColor = SurfaceCard,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GainGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Nutrition CSV Ready", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${readyState.rowCount} nutrition day rows exported",
                        style = MaterialTheme.typography.titleSmall,
                        color = ElectricTeal,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Local file: ${readyState.file.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                    Button(
                        onClick = {
                            filePendingSave = readyState.file
                            saveCsvLauncher.launch(readyState.file.name)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AmberGold, contentColor = Color.Black)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save to Device / Downloads", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { shareNutritionCsvFile(context, readyState.file) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp),
                        border = BorderStroke(1.dp, ElectricTeal),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricTeal)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share CSV File", fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.clearCsvExportStatus() }) {
                    Text("Done", color = AmberGold, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun LoggedMealRow(
    meal: MealEntry,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = meal.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                if (meal.loggedAtLabel.isNotBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = meal.loggedAtLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
            Text(
                text = "${meal.calories} kcal · ${meal.proteinG}g P · ${meal.carbsG}g C · ${meal.fatsG}g F",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF94A3B8)
            )
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .size(48.dp)
                .testTag("delete_meal_${meal.id}")
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Remove ${meal.name}",
                tint = LossRed,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun shareNutritionCsvFile(context: Context, csvFile: File) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            csvFile
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "StrengthForge Nutrition Log (${csvFile.name})")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Export Nutrition CSV"))
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to share CSV file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun MacroPillCard(
    name: String,
    current: Int,
    target: Int,
    targetRange: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val safeTarget = target.coerceAtLeast(1)
    val fraction = (current.toFloat() / safeTarget.toFloat()).coerceIn(0f, 1f)
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, SurfaceCardBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = name.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${current}g",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = targetRange,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF94A3B8)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF0F172A))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color)
                )
            }
        }
    }
}

@Composable
fun NutritionRuleItem(title: String, desc: String) {
    Column {
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color.White)
        Text(desc, style = MaterialTheme.typography.bodySmall, color = Color(0xFF94A3B8))
    }
}

@Composable
fun IconButtonWithBorder(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1E293B))
            .border(1.dp, SurfaceCardBorder, RoundedCornerShape(10.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = AmberGold,
            modifier = Modifier.size(20.dp)
        )
    }
}
