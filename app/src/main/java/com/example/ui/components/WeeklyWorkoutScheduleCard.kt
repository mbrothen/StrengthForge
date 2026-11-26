package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.schedule.ScheduledWeekDay
import com.example.data.schedule.WeeklyScheduleSummary
import com.example.ui.theme.AmberGold
import com.example.ui.theme.ElectricTeal
import com.example.ui.theme.GainGreen
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeeklyWorkoutScheduleCard(
    schedule: WeeklyScheduleSummary,
    modifier: Modifier = Modifier,
    onDayClick: (ScheduledWeekDay) -> Unit = {}
) {
    val badgeText = when {
        schedule.scheduledDaysCount > 0 ->
            "${schedule.completedScheduledDaysCount}/${schedule.scheduledDaysCount} Scheduled"
        else ->
            "${schedule.completedDaysCount} Logged"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("weekly_workout_schedule_card"),
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = AmberGold,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "WEEKLY WORKOUT SCHEDULE",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = schedule.weekRangeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF64748B),
                            fontSize = 10.sp
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (schedule.completedScheduledDaysCount > 0 || schedule.completedDaysCount > 0)
                                Color(0xFF064E3B)
                            else
                                Color(0xFF0F172A)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = GainGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                schedule.days.forEach { day ->
                    val primaryRoutine = day.scheduledRoutines.firstOrNull()
                    val circleBg = when {
                        day.isCompleted -> AmberGold
                        day.isScheduled && day.isToday -> Color(0xFF0E3A42)
                        day.isScheduled -> Color(0xFF1E293B)
                        else -> Color(0xFF0F172A)
                    }
                    val circleBorder = when {
                        day.isToday -> ElectricTeal
                        day.isCompleted -> AmberGold
                        day.isScheduled -> AmberGold.copy(alpha = 0.55f)
                        else -> SurfaceCardBorder
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onDayClick(day) }
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                            .testTag("schedule_day_cell_${day.weekday.shortLabel.lowercase()}")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(circleBg)
                                .border(if (day.isToday) 2.dp else 1.dp, circleBorder, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                day.isCompleted -> {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "${day.weekday.fullName} completed",
                                        tint = Color.Black,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                day.isScheduled -> {
                                    Icon(
                                        imageVector = Icons.Default.FitnessCenter,
                                        contentDescription = "${day.weekday.fullName} scheduled",
                                        tint = if (day.isToday) ElectricTeal else AmberGold,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                else -> {
                                    Text(
                                        text = "-",
                                        color = TextSecondary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = day.weekday.shortLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = when {
                                day.isToday -> ElectricTeal
                                day.isCompleted || day.isScheduled -> Color.White
                                else -> TextSecondary
                            },
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        val slotLabel = when {
                            primaryRoutine != null -> primaryRoutine.targetMuscle.ifBlank {
                                primaryRoutine.name.take(6)
                            }
                            day.isCompleted -> "Done"
                            else -> "Rest"
                        }
                        Text(
                            text = slotLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = when {
                                primaryRoutine != null && day.isToday -> ElectricTeal
                                primaryRoutine != null -> AmberGold
                                day.isCompleted -> GainGreen
                                else -> Color(0xFF64748B)
                            },
                            fontWeight = if (primaryRoutine != null) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }

            val scheduledEntries = schedule.days.filter { it.isScheduled }
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)
            Spacer(modifier = Modifier.height(10.dp))

            if (scheduledEntries.isEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Edit any routine to assign a day of the week and build your schedule.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    scheduledEntries.forEach { day ->
                        val routineLabel = day.scheduledRoutines.joinToString(" & ") { it.name }
                        val chipBorder = when {
                            day.isCompleted -> GainGreen.copy(alpha = 0.6f)
                            day.isToday -> ElectricTeal
                            else -> SurfaceCardBorder
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (day.isToday) Color(0xFF0E3A42) else Color(0xFF0F172A),
                            border = BorderStroke(1.dp, chipBorder),
                            modifier = Modifier.clickable { onDayClick(day) }
                        ) {
                            Text(
                                text = "${day.weekday.shortLabel}: $routineLabel",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (day.isToday) ElectricTeal else Color(0xFFE2E8F0),
                                fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
