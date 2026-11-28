package com.example

import com.example.data.model.WorkoutRoutine
import com.example.data.model.WorkoutSession
import com.example.data.schedule.Weekday
import com.example.data.schedule.WorkoutScheduleHelper
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class WorkoutScheduleHelperTest {

    private fun dateFor(year: Int, monthZeroBased: Int, day: Int): java.util.Date {
        return Calendar.getInstance().apply {
            set(year, monthZeroBased, day, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
    }

    @Test
    fun `formatScheduledDays handles empty, partial, and full week`() {
        assertEquals("Unscheduled", WorkoutScheduleHelper.formatScheduledDays(emptyList()))
        assertEquals("Mon, Thu", WorkoutScheduleHelper.formatScheduledDays(listOf(4, 1, 1)))
        assertEquals("Every Day", WorkoutScheduleHelper.formatScheduledDays(listOf(1, 2, 3, 4, 5, 6, 7)))
    }

    @Test
    fun `suggestRoutineForDate picks routine scheduled on the recorded day`() {
        val push = WorkoutRoutine(id = "push", name = "Push Day A", targetMuscle = "Push", scheduledDays = listOf(1, 4))
        val pull = WorkoutRoutine(id = "pull", name = "Pull Day B", targetMuscle = "Pull", scheduledDays = listOf(3))
        val legs = WorkoutRoutine(id = "legs", name = "Leg Day C", targetMuscle = "Legs", scheduledDays = listOf(5))
        val routines = listOf(push, pull, legs)

        // 2026-11-11 is Wednesday (ISO 3)
        val wednesday = dateFor(2026, Calendar.NOVEMBER, 11)
        val wedSuggestion = WorkoutScheduleHelper.suggestRoutineForDate(routines, wednesday)
        assertTrue(wedSuggestion.isScheduledForDate)
        assertEquals(Weekday.WEDNESDAY, wedSuggestion.weekday)
        assertEquals("pull", wedSuggestion.routine?.id)

        // 2026-11-12 is Thursday (ISO 4) -> Push Day A
        val thursday = dateFor(2026, Calendar.NOVEMBER, 12)
        val thuSuggestion = WorkoutScheduleHelper.suggestRoutineForDate(routines, thursday)
        assertTrue(thuSuggestion.isScheduledForDate)
        assertEquals("push", thuSuggestion.routine?.id)

        // 2026-11-15 is Sunday (ISO 7) -> Rest day, next scheduled is Monday (Push Day A)
        val sunday = dateFor(2026, Calendar.NOVEMBER, 15)
        val sunSuggestion = WorkoutScheduleHelper.suggestRoutineForDate(routines, sunday)
        assertFalse(sunSuggestion.isScheduledForDate)
        assertEquals(Weekday.SUNDAY, sunSuggestion.weekday)
        assertEquals("push", sunSuggestion.routine?.id)
    }

    @Test
    fun `buildWeeklySchedule combines routine schedule and logged workouts`() {
        val push = WorkoutRoutine(id = "push", name = "Push Day A", targetMuscle = "Push", scheduledDays = listOf(1))
        val pull = WorkoutRoutine(id = "pull", name = "Pull Day B", targetMuscle = "Pull", scheduledDays = listOf(3))
        val legs = WorkoutRoutine(id = "legs", name = "Leg Day C", targetMuscle = "Legs", scheduledDays = listOf(5))

        // Week of Mon Nov 9, 2026 .. Sun Nov 15, 2026
        val monday = dateFor(2026, Calendar.NOVEMBER, 9)
        val wednesday = dateFor(2026, Calendar.NOVEMBER, 11)
        val sunday = dateFor(2026, Calendar.NOVEMBER, 15)

        val workouts = listOf(
            WorkoutSession(
                id = "w_mon",
                name = "Push Day A",
                dateString = WorkoutScheduleHelper.normalizedDateKey(monday),
                createdAt = Timestamp(monday)
            ),
            WorkoutSession(
                id = "w_wed",
                name = "Pull Day B",
                dateString = WorkoutScheduleHelper.normalizedDateKey(wednesday),
                createdAt = Timestamp(wednesday)
            )
        )

        val summary = WorkoutScheduleHelper.buildWeeklySchedule(
            routines = listOf(push, pull, legs),
            workouts = workouts,
            referenceDate = sunday
        )

        assertEquals(7, summary.days.size)
        assertEquals(3, summary.scheduledDaysCount)
        assertEquals(2, summary.completedScheduledDaysCount)
        assertEquals(2, summary.completedDaysCount)

        val monDay = summary.days[0]
        assertEquals(Weekday.MONDAY, monDay.weekday)
        assertTrue(monDay.isScheduled)
        assertTrue(monDay.isCompleted)
        assertEquals("Push Day A", monDay.scheduledRoutines.firstOrNull()?.name)

        val friDay = summary.days[4]
        assertEquals(Weekday.FRIDAY, friDay.weekday)
        assertTrue(friDay.isScheduled)
        assertFalse(friDay.isCompleted)
        assertEquals("Leg Day C", friDay.scheduledRoutines.firstOrNull()?.name)

        val sunDay = summary.days[6]
        assertEquals(Weekday.SUNDAY, sunDay.weekday)
        assertTrue(sunDay.isToday)
        assertNotNull(sunDay.date)
    }

    @Test
    fun `buildWeeklySchedule and formatScheduledDays respect custom first day of week`() {
        assertEquals(
            listOf(
                Weekday.SUNDAY,
                Weekday.MONDAY,
                Weekday.TUESDAY,
                Weekday.WEDNESDAY,
                Weekday.THURSDAY,
                Weekday.FRIDAY,
                Weekday.SATURDAY
            ),
            WorkoutScheduleHelper.orderedWeekdays(Weekday.SUNDAY)
        )
        assertEquals(
            "Sun, Tue, Thu",
            WorkoutScheduleHelper.formatScheduledDays(listOf(2, 4, 7), firstDay = Weekday.SUNDAY)
        )

        val sunRoutine = WorkoutRoutine(id = "sun_r", name = "Full Body", scheduledDays = listOf(7, 3))
        val wedNov18 = dateFor(2026, Calendar.NOVEMBER, 18) // Week Sun Nov 15 .. Sat Nov 21
        val sunNov15 = dateFor(2026, Calendar.NOVEMBER, 15)

        val summary = WorkoutScheduleHelper.buildWeeklySchedule(
            routines = listOf(sunRoutine),
            workouts = listOf(
                WorkoutSession(
                    id = "w_sun",
                    name = "Full Body",
                    dateString = WorkoutScheduleHelper.normalizedDateKey(sunNov15),
                    createdAt = Timestamp(sunNov15)
                )
            ),
            referenceDate = wedNov18,
            firstDayOfWeek = Weekday.SUNDAY
        )

        assertEquals(Weekday.SUNDAY, summary.days.first().weekday)
        assertEquals(Weekday.SATURDAY, summary.days.last().weekday)
        assertEquals("Nov 15 – Nov 21", summary.weekRangeLabel)
        assertTrue(summary.days.first().isScheduled)
        assertTrue(summary.days.first().isCompleted)
    }
}
