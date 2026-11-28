package com.example.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.analytics.WeightUnit
import com.example.data.onboarding.FitnessGoal
import com.example.data.onboarding.GoalOnboardingHelper
import com.example.data.onboarding.TrainingSplit
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.ElectricTeal
import com.example.ui.theme.GainGreen
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalOnboardingModal(
    initialGoal: FitnessGoal = FitnessGoal.MAINTAIN,
    initialSplit: TrainingSplit = TrainingSplit.PPL,
    initialWeightUnit: WeightUnit = WeightUnit.LBS,
    isFirstTimeOnboarding: Boolean = true,
    onSavePlan: (FitnessGoal, TrainingSplit, WeightUnit) -> Unit,
    onSkipOrDismiss: () -> Unit
) {
    var selectedGoal by remember(initialGoal) { mutableStateOf(initialGoal) }
    var selectedSplit by remember(initialSplit) { mutableStateOf(initialSplit) }
    var selectedUnit by remember(initialWeightUnit) { mutableStateOf(initialWeightUnit) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val starterCount = remember(selectedSplit) {
        GoalOnboardingHelper.buildStarterRoutines(userId = "", split = selectedSplit).size
    }

    ModalBottomSheet(
        onDismissRequest = onSkipOrDismiss,
        sheetState = sheetState,
        containerColor = SurfaceCard,
        contentColor = TextPrimary,
        modifier = Modifier.testTag("goal_onboarding_modal")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(AmberGold.copy(alpha = 0.16f))
                        .border(1.dp, AmberGold.copy(alpha = 0.65f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Flag,
                        contentDescription = null,
                        tint = AmberGold,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isFirstTimeOnboarding) "Welcome to StrengthForge" else "Customize Goal & Split",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Pick your unit, nutrition goal, and training split to auto-configure macros and starter routines.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            // Step 1: Weight Units
            SectionLabel(
                icon = Icons.Default.Scale,
                title = "1. PREFERRED WEIGHT UNIT",
                tint = ElectricTeal
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                WeightUnit.entries.forEach { unit ->
                    val isSelected = selectedUnit == unit
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) Color(0xFF0E3A42) else Color(0xFF0B0F17),
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) ElectricTeal else SurfaceCardBorder
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { selectedUnit = unit }
                            .testTag("onboarding_unit_${unit.id.lowercase()}")
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = unit.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) ElectricTeal else TextPrimary
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = ElectricTeal,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (unit == WeightUnit.KG) "20 kg bar · 1.25 kg plates" else "45 lb bar · 2.5 lb plates",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // Step 2: Fitness Goal
            SectionLabel(
                icon = Icons.Default.Restaurant,
                title = "2. FITNESS & NUTRITION GOAL",
                tint = AmberGold
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FitnessGoal.entries.forEach { goal ->
                    val isSelected = selectedGoal == goal
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) Color(0xFF1E293B) else Color(0xFF0B0F17),
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) AmberGold else SurfaceCardBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { selectedGoal = goal }
                            .testTag("goal_option_${goal.id.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = goal.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) AmberGold else TextPrimary
                                )
                                Text(
                                    text = goal.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "${goal.targetCalories} kcal · ${goal.proteinG}g P / ${goal.carbsG}g C / ${goal.fatsG}g F",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyanAccent,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (isSelected) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = AmberGold,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Step 3: Training Split
            SectionLabel(
                icon = Icons.Default.FitnessCenter,
                title = "3. WEEKLY TRAINING SPLIT",
                tint = GainGreen
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TrainingSplit.entries.forEach { split ->
                    val isSelected = selectedSplit == split
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) Color(0xFF0B2E22) else Color(0xFF0B0F17),
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) GainGreen else SurfaceCardBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { selectedSplit = split }
                            .testTag("split_option_${split.id.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = split.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) GainGreen else TextPrimary
                                )
                                Text(
                                    text = split.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "Schedule: ${split.scheduleSummary}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricTeal,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (isSelected) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = GainGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Live Plan Summary
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF0F172A),
                border = BorderStroke(1.dp, AmberGold.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "YOUR PERSONALIZED SETUP",
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberGold,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.7.sp
                    )
                    Text(
                        text = "${selectedGoal.label} (${selectedGoal.minCalories}–${selectedGoal.maxCalories} kcal) · ${selectedSplit.label} ($starterCount starter routines) · ${selectedUnit.shortLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (!isFirstTimeOnboarding) {
                        Text(
                            text = "Applying this plan updates your daily macro targets and resets starter routines to the selected split.",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            HorizontalDivider(color = SurfaceCardBorder)

            // Action Buttons
            Button(
                onClick = { onSavePlan(selectedGoal, selectedSplit, selectedUnit) },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 52.dp)
                    .testTag("save_onboarding_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AmberGold,
                    contentColor = Color.Black
                )
            ) {
                Text(
                    text = "Save & Apply Plan",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            TextButton(
                onClick = onSkipOrDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("skip_onboarding_button")
            ) {
                Text(
                    text = if (isFirstTimeOnboarding) "Keep Current & Skip" else "Cancel",
                    color = TextSecondary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    tint: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp
        )
    }
}
