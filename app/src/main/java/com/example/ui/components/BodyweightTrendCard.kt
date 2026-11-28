package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.analytics.BodyweightSummary
import com.example.data.analytics.BodyweightTrendPoint
import com.example.data.analytics.WeightUnit
import com.example.data.analytics.WeightUnitHelper
import com.example.data.onboarding.FitnessGoal
import com.example.ui.theme.AmberGold
import com.example.ui.theme.ElectricTeal
import com.example.ui.theme.GainGreen
import com.example.ui.theme.LossRed
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun BodyweightTrendCard(
    summary: BodyweightSummary,
    weightUnit: WeightUnit,
    fitnessGoal: FitnessGoal,
    selectedDateString: String,
    onLogBodyweight: (displayWeight: Double, dateString: String, bodyFatPercent: Double?, notes: String) -> Unit,
    onDeleteEntry: (dateString: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showLogDialog by remember { mutableStateOf(false) }
    var showAllEntries by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("bodyweight_trend_card"),
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Scale,
                        contentDescription = null,
                        tint = ElectricTeal,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "BODYWEIGHT & 7-DAY TREND",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricTeal,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp
                        )
                        Text(
                            text = "Goal: ${fitnessGoal.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }

                Button(
                    onClick = { showLogDialog = true },
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("log_bodyweight_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricTeal,
                        contentColor = Color.Black
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Weigh-In", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Metrics Summary Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BodyweightMetricBox(
                    label = "Latest",
                    value = summary.latestWeightLbs?.let {
                        WeightUnitHelper.formatWeightWithUnit(it, weightUnit)
                    } ?: "--",
                    accentColor = AmberGold,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("bodyweight_latest_value")
                )
                BodyweightMetricBox(
                    label = "7-Day Avg",
                    value = summary.sevenDayAvgLbs?.let {
                        WeightUnitHelper.formatWeightWithUnit(it, weightUnit)
                    } ?: "--",
                    accentColor = ElectricTeal,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("bodyweight_seven_day_avg_value")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Goal Alignment Pill
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF0F172A),
                border = BorderStroke(1.dp, SurfaceCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = summary.goalAlignmentLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        summary.goalAlignmentLabel.contains("On Cut") ||
                            summary.goalAlignmentLabel.contains("Steady") ||
                            summary.goalAlignmentLabel.contains("Lean Bulk") -> GainGreen
                        summary.goalAlignmentLabel.contains("Trending Above") ||
                            summary.goalAlignmentLabel.contains("Fast Surplus") -> AmberGold
                        else -> TextSecondary
                    },
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("bodyweight_goal_alignment_label")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (summary.trendPoints.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0B0F17),
                    border = BorderStroke(1.dp, SurfaceCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Log daily or weekly weigh-ins to plot your bodyweight curve alongside a smoothed 7-day moving average.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            } else {
                BodyweightMovingAverageChart(
                    points = summary.trendPoints.takeLast(30),
                    weightUnit = weightUnit
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Recent Weigh-ins List
                val entries = summary.entriesDescending
                val visibleEntries = if (showAllEntries) entries else entries.take(5)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RECENT WEIGH-INS (${entries.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                    if (entries.size > 5) {
                        TextButton(
                            onClick = { showAllEntries = !showAllEntries },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text(
                                text = if (showAllEntries) "Show Less" else "View All (${entries.size})",
                                color = ElectricTeal,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                visibleEntries.forEachIndexed { idx, entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = WeightUnitHelper.formatWeightWithUnit(entry.weightLbs, weightUnit),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                entry.bodyFatPercent?.let { bf ->
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${String.format("%.1f", bf)}% BF",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AmberGold,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            val subtitle = buildString {
                                append(entry.dateString)
                                if (entry.notes.isNotBlank()) {
                                    append(" · ")
                                    append(entry.notes)
                                }
                            }
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                maxLines = 1
                            )
                        }

                        IconButton(
                            onClick = { onDeleteEntry(entry.dateString) },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("delete_bodyweight_${entry.dateString}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete weigh-in for ${entry.dateString}",
                                tint = LossRed.copy(alpha = 0.85f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (idx < visibleEntries.lastIndex) {
                        HorizontalDivider(color = Color(0xFF1E293B))
                    }
                }
            }
        }
    }

    if (showLogDialog) {
        val suggestedDisplayWeight = summary.latestWeightLbs?.let {
            WeightUnitHelper.formatWeightNumber(WeightUnitHelper.fromLbs(it, weightUnit), weightUnit)
        } ?: if (weightUnit == WeightUnit.KG) "75" else "170"

        LogBodyweightDialog(
            initialWeightText = suggestedDisplayWeight,
            initialDateString = selectedDateString,
            weightUnit = weightUnit,
            onDismiss = { showLogDialog = false },
            onSave = { weightVal, dateStr, bodyFatVal, notesStr ->
                onLogBodyweight(weightVal, dateStr, bodyFatVal, notesStr)
                showLogDialog = false
            }
        )
    }
}

@Composable
private fun BodyweightMetricBox(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0F172A))
            .border(1.dp, SurfaceCardBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = accentColor,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun BodyweightMovingAverageChart(
    points: List<BodyweightTrendPoint>,
    weightUnit: WeightUnit
) {
    val rawValues = points.map { WeightUnitHelper.fromLbs(it.weightLbs, weightUnit) }
    val avgValues = points.map { WeightUnitHelper.fromLbs(it.sevenDayAvgLbs, weightUnit) }
    val combined = rawValues + avgValues
    val minVal = combined.minOrNull() ?: 0.0
    val maxVal = combined.maxOrNull() ?: 1.0
    val pad = maxOf(0.5, (maxVal - minVal) * 0.15)
    val chartMin = (minVal - pad).coerceAtLeast(0.0)
    val chartMax = maxVal + pad
    val range = maxOf(1.0, chartMax - chartMin)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0B0F17))
            .border(1.dp, SurfaceCardBorder, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        // Legend + High label
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(ElectricTeal)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "7-Day Avg",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricTeal,
                        fontSize = 10.sp
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(AmberGold)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Daily Weigh-In",
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberGold,
                        fontSize = 10.sp
                    )
                }
            }
            Text(
                text = "${WeightUnitHelper.formatWeightNumber(chartMax, weightUnit)} ${weightUnit.shortLabel}",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontSize = 10.sp
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val horizontalPad = 10f
                val verticalPad = 10f
                val drawW = (w - 2 * horizontalPad).coerceAtLeast(1f)
                val drawH = (h - 2 * verticalPad).coerceAtLeast(1f)

                // Dashed reference gridlines
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                listOf(0f, 0.5f, 1f).forEach { ratio ->
                    val y = verticalPad + drawH * ratio
                    drawLine(
                        color = Color(0xFF1E293B),
                        start = Offset(horizontalPad, y),
                        end = Offset(w - horizontalPad, y),
                        strokeWidth = 1.5f,
                        pathEffect = dashEffect
                    )
                }

                fun xForIndex(i: Int): Float {
                    return if (points.size <= 1) {
                        w / 2f
                    } else {
                        horizontalPad + (i.toFloat() / (points.size - 1).toFloat()) * drawW
                    }
                }

                fun yForValue(v: Double): Float {
                    val norm = ((v - chartMin) / range).toFloat().coerceIn(0f, 1f)
                    return verticalPad + drawH * (1f - norm)
                }

                val avgOffsets = avgValues.mapIndexed { i, v -> Offset(xForIndex(i), yForValue(v)) }
                val rawOffsets = rawValues.mapIndexed { i, v -> Offset(xForIndex(i), yForValue(v)) }

                if (avgOffsets.size >= 2) {
                    val linePath = Path().apply {
                        moveTo(avgOffsets.first().x, avgOffsets.first().y)
                        for (i in 1 until avgOffsets.size) {
                            val prev = avgOffsets[i - 1]
                            val curr = avgOffsets[i]
                            val midX = (prev.x + curr.x) / 2f
                            cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
                        }
                    }

                    val fillPath = Path().apply {
                        addPath(linePath)
                        lineTo(avgOffsets.last().x, h - verticalPad)
                        lineTo(avgOffsets.first().x, h - verticalPad)
                        close()
                    }

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                ElectricTeal.copy(alpha = 0.28f),
                                ElectricTeal.copy(alpha = 0.02f)
                            )
                        )
                    )

                    drawPath(
                        path = linePath,
                        color = ElectricTeal,
                        style = Stroke(
                            width = 5f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                } else if (avgOffsets.size == 1) {
                    drawLine(
                        color = ElectricTeal,
                        start = Offset(horizontalPad, avgOffsets.first().y),
                        end = Offset(w - horizontalPad, avgOffsets.first().y),
                        strokeWidth = 4f
                    )
                }

                rawOffsets.forEach { pt ->
                    drawCircle(
                        color = AmberGold,
                        radius = 6f,
                        center = pt
                    )
                    drawCircle(
                        color = Color(0xFF0B0F17),
                        radius = 2.5f,
                        center = pt
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = points.first().shortLabel,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontSize = 10.sp
            )
            Text(
                text = "${WeightUnitHelper.formatWeightNumber(chartMin, weightUnit)} ${weightUnit.shortLabel} min",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontSize = 10.sp
            )
            Text(
                text = points.last().shortLabel,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun LogBodyweightDialog(
    initialWeightText: String,
    initialDateString: String,
    weightUnit: WeightUnit,
    onDismiss: () -> Unit,
    onSave: (weight: Double, dateString: String, bodyFatPercent: Double?, notes: String) -> Unit
) {
    var weightInput by remember { mutableStateOf(initialWeightText) }
    var dateInput by remember { mutableStateOf(initialDateString) }
    var bodyFatInput by remember { mutableStateOf("") }
    var notesInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        focusedBorderColor = ElectricTeal,
        unfocusedBorderColor = SurfaceCardBorder,
        focusedLabelColor = ElectricTeal,
        unfocusedLabelColor = TextSecondary
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        title = {
            Text(
                text = "Log Bodyweight",
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = weightInput,
                    onValueChange = {
                        weightInput = it
                        errorMessage = null
                    },
                    label = { Text("Weight (${weightUnit.shortLabel})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bodyweight_weight_input")
                )

                OutlinedTextField(
                    value = dateInput,
                    onValueChange = { dateInput = it },
                    label = { Text("Date (YYYY-MM-DD)") },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bodyweight_date_input")
                )

                OutlinedTextField(
                    value = bodyFatInput,
                    onValueChange = { bodyFatInput = it },
                    label = { Text("Body Fat % (optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bodyweight_bf_input")
                )

                OutlinedTextField(
                    value = notesInput,
                    onValueChange = { notesInput = it },
                    label = { Text("Notes (e.g., morning fasted)") },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bodyweight_notes_input")
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = LossRed
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedWeight = weightInput.trim().toDoubleOrNull()
                    if (parsedWeight == null || parsedWeight <= 0.0) {
                        errorMessage = "Enter a valid positive weight in ${weightUnit.shortLabel}."
                        return@Button
                    }
                    val cleanDate = dateInput.trim().ifBlank { initialDateString }
                    val parsedBf = bodyFatInput.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
                    onSave(parsedWeight, cleanDate, parsedBf, notesInput.trim())
                },
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("save_bodyweight_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricTeal,
                    contentColor = Color.Black
                )
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
