package com.example.data.schedule

import com.example.data.model.WorkoutRoutine
import com.example.data.model.WorkoutSession
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class Weekday(
    val isoDay: Int,
    val shortLabel: String,
    val singleLetter: String,
    val fullName: String,
    val calendarDayOfWeek: Int
) {
    MONDAY(1, "Mon", "M", "Monday", Calendar.MONDAY),
    TUESDAY(2, "Tue", "T", "Tuesday", Calendar.TUESDAY),
    WEDNESDAY(3, "Wed", "W", "Wednesday", Calendar.WEDNESDAY),
    THURSDAY(4, "Thu", "T", "Thursday", Calendar.THURSDAY),
    FRIDAY(5, "Fri", "F", "Friday", Calendar.FRIDAY),
    SATURDAY(6, "Sat", "S", "Saturday", Calendar.SATURDAY),
    SUNDAY(7, "Sun", "S", "Sunday", Calendar.SUNDAY);

    companion object {
        fun fromIsoDay(isoDay: Int): Weekday {
            return entries.firstOrNull { it.isoDay == isoDay } ?: MONDAY
        }

        fun fromCalendarDayOfWeek(calendarDay: Int): Weekday {
            return entries.firstOrNull { it.calendarDayOfWeek == calendarDay } ?: MONDAY
        }
    }
}

data class RoutineSuggestion(
    val routine: WorkoutRoutine?,
    val matchingRoutines: List<WorkoutRoutine>,
    val isScheduledForDate: Boolean,
    val weekday: Weekday,
    val reasonLabel: String
)

data class ScheduledWeekDay(
    val weekday: Weekday,
    val date: Date,
    val dateString: String,
    val scheduledRoutines: List<WorkoutRoutine>,
    val loggedWorkouts: List<WorkoutSession>,
    val isToday: Boolean
) {
    val isCompleted: Boolean get() = loggedWorkouts.isNotEmpty()
    val isScheduled: Boolean get() = scheduledRoutines.isNotEmpty()
}

data class WeeklyScheduleSummary(
    val days: List<ScheduledWeekDay>,
    val completedDaysCount: Int,
    val scheduledDaysCount: Int,
    val completedScheduledDaysCount: Int,
    val weekRangeLabel: String
)

object WorkoutScheduleHelper {

    private val sessionDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
    private val rangeDateFormat = SimpleDateFormat("MMM d", Locale.US)

    fun dayOfWeekIso(date: Date): Int {
        val cal = Calendar.getInstance().apply { time = date }
        return Weekday.fromCalendarDayOfWeek(cal.get(Calendar.DAY_OF_WEEK)).isoDay
    }

    fun weekdayForDate(date: Date): Weekday {
        return Weekday.fromIsoDay(dayOfWeekIso(date))
    }

    fun normalizedDateKey(date: Date): String {
        return sessionDateFormat.format(date)
    }

    fun formatScheduledDays(scheduledDays: List<Int>): String {
        val normalized = scheduledDays.filter { it in 1..7 }.distinct().sorted()
        if (normalized.isEmpty()) return "Unscheduled"
        if (normalized.size == 7) return "Every Day"
        return normalized.joinToString(", ") { Weekday.fromIsoDay(it).shortLabel }
    }

    fun suggestRoutineForDate(
        routines: List<WorkoutRoutine>,
        date: Date = Date()
    ): RoutineSuggestion {
        val weekday = weekdayForDate(date)
        if (routines.isEmpty()) {
            return RoutineSuggestion(
                routine = null,
                matchingRoutines = emptyList(),
                isScheduledForDate = false,
                weekday = weekday,
                reasonLabel = "No routines created yet"
            )
        }

        val matching = routines.filter { it.scheduledDays.contains(weekday.isoDay) }
        if (matching.isNotEmpty()) {
            val extraNote = if (matching.size > 1) " (${matching.size} scheduled)" else ""
            return RoutineSuggestion(
                routine = matching.first(),
                matchingRoutines = matching,
                isScheduledForDate = true,
                weekday = weekday,
                reasonLabel = "Scheduled for ${weekday.fullName}$extraNote"
            )
        }

        // Fallback: find the next upcoming scheduled routine in the weekly cycle
        for (offset in 1..6) {
            val nextIso = ((weekday.isoDay - 1 + offset) % 7) + 1
            val nextMatch = routines.firstOrNull { it.scheduledDays.contains(nextIso) }
            if (nextMatch != null) {
                val nextDay = Weekday.fromIsoDay(nextIso)
                return RoutineSuggestion(
                    routine = nextMatch,
                    matchingRoutines = emptyList(),
                    isScheduledForDate = false,
                    weekday = weekday,
                    reasonLabel = "No routine on ${weekday.shortLabel} · Next: ${nextDay.shortLabel} (${nextMatch.name})"
                )
            }
        }

        return RoutineSuggestion(
            routine = routines.firstOrNull(),
            matchingRoutines = emptyList(),
            isScheduledForDate = false,
            weekday = weekday,
            reasonLabel = "No scheduled day set yet"
        )
    }

    fun buildWeeklySchedule(
        routines: List<WorkoutRoutine>,
        workouts: List<WorkoutSession>,
        referenceDate: Date = Date()
    ): WeeklyScheduleSummary {
        val todayKey = normalizedDateKey(referenceDate)

        // Find Monday of referenceDate's week
        val weekStartCal = Calendar.getInstance().apply {
            time = referenceDate
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            val currentIso = Weekday.fromCalendarDayOfWeek(get(Calendar.DAY_OF_WEEK)).isoDay
            add(Calendar.DAY_OF_YEAR, -(currentIso - 1))
        }

        val workoutsByDateKey = workouts.groupBy { session ->
            session.createdAt?.toDate()?.let { normalizedDateKey(it) }
                ?: session.dateString.trim()
        }

        val days = (0..6).map { dayOffset ->
            val dayCal = (weekStartCal.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, dayOffset)
            }
            val dayDate = dayCal.time
            val weekday = Weekday.fromIsoDay(dayOffset + 1)
            val dateKey = normalizedDateKey(dayDate)
            val scheduledForDay = routines.filter { it.scheduledDays.contains(weekday.isoDay) }
            val loggedForDay = workoutsByDateKey[dateKey].orEmpty()

            ScheduledWeekDay(
                weekday = weekday,
                date = dayDate,
                dateString = dateKey,
                scheduledRoutines = scheduledForDay,
                loggedWorkouts = loggedForDay,
                isToday = dateKey == todayKey
            )
        }

        val startLabel = rangeDateFormat.format(days.first().date)
        val endLabel = rangeDateFormat.format(days.last().date)

        return WeeklyScheduleSummary(
            days = days,
            completedDaysCount = days.count { it.isCompleted },
            scheduledDaysCount = days.count { it.isScheduled },
            completedScheduledDaysCount = days.count { it.isScheduled && it.isCompleted },
            weekRangeLabel = "$startLabel – $endLabel"
        )
    }
}
