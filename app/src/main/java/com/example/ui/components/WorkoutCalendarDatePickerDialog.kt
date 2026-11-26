package com.example.ui.components

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.example.ui.theme.AmberGold
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

object WorkoutDatePickerUtils {
    fun toUtcDateMillis(localDate: Date): Long {
        val local = Calendar.getInstance().apply { time = localDate }
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }

    fun fromUtcDateMillis(utcMillis: Long): Date {
        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = utcMillis
        }
        return Calendar.getInstance().apply {
            set(
                utcCal.get(Calendar.YEAR),
                utcCal.get(Calendar.MONTH),
                utcCal.get(Calendar.DAY_OF_MONTH),
                12,
                0,
                0
            )
            set(Calendar.MILLISECOND, 0)
        }.time
    }

    fun todayEndUtcMillis(now: Date = Date()): Long {
        val local = Calendar.getInstance().apply { time = now }
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(
                local.get(Calendar.YEAR),
                local.get(Calendar.MONTH),
                local.get(Calendar.DAY_OF_MONTH),
                23,
                59,
                59
            )
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
    }

    fun isPastOrToday(utcTimeMillis: Long, now: Date = Date()): Boolean {
        return utcTimeMillis <= todayEndUtcMillis(now)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutCalendarDatePickerDialog(
    initialDate: Date,
    allowFutureDates: Boolean = false,
    onDismiss: () -> Unit,
    onDateSelected: (Date) -> Unit
) {
    val initialUtcMillis = remember(initialDate) {
        WorkoutDatePickerUtils.toUtcDateMillis(initialDate)
    }
    val maxUtcMillis = remember { WorkoutDatePickerUtils.todayEndUtcMillis() }
    val maxYear = remember { Calendar.getInstance().get(Calendar.YEAR) }

    val selectableDates = remember(allowFutureDates, maxUtcMillis, maxYear) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                return allowFutureDates || utcTimeMillis <= maxUtcMillis
            }

            override fun isSelectableYear(year: Int): Boolean {
                return allowFutureDates || year <= maxYear
            }
        }
    }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialUtcMillis,
        selectableDates = selectableDates
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("workout_calendar_date_picker_dialog"),
        confirmButton = {
            TextButton(
                onClick = {
                    val utcMillis = datePickerState.selectedDateMillis
                    if (utcMillis != null) {
                        onDateSelected(WorkoutDatePickerUtils.fromUtcDateMillis(utcMillis))
                    }
                    onDismiss()
                },
                modifier = Modifier.testTag("confirm_date_picker_button")
            ) {
                Text("Select Date", color = AmberGold, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_date_picker_button")
            ) {
                Text("Cancel", color = TextSecondary)
            }
        },
        colors = DatePickerDefaults.colors(containerColor = SurfaceCard)
    ) {
        DatePicker(
            state = datePickerState,
            modifier = Modifier.testTag("workout_calendar_date_picker"),
            showModeToggle = true,
            colors = DatePickerDefaults.colors(
                containerColor = SurfaceCard,
                titleContentColor = AmberGold,
                headlineContentColor = Color.White,
                weekdayContentColor = TextSecondary,
                subheadContentColor = TextSecondary,
                navigationContentColor = Color.White,
                yearContentColor = TextPrimary,
                currentYearContentColor = AmberGold,
                selectedYearContentColor = Color.Black,
                selectedYearContainerColor = AmberGold,
                dayContentColor = TextPrimary,
                disabledDayContentColor = TextSecondary.copy(alpha = 0.35f),
                selectedDayContentColor = Color.Black,
                selectedDayContainerColor = AmberGold,
                todayContentColor = AmberGold,
                todayDateBorderColor = AmberGold
            )
        )
    }
}
