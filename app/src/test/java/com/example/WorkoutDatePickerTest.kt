package com.example

import com.example.ui.components.WorkoutDatePickerUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WorkoutDatePickerTest {

    @Test
    fun `utc round trip preserves local calendar year month and day`() {
        val originalCal = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 24, 21, 45, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val utcMillis = WorkoutDatePickerUtils.toUtcDateMillis(originalCal.time)
        val restored = WorkoutDatePickerUtils.fromUtcDateMillis(utcMillis)
        val restoredCal = Calendar.getInstance().apply { time = restored }

        assertEquals(2026, restoredCal.get(Calendar.YEAR))
        assertEquals(Calendar.OCTOBER, restoredCal.get(Calendar.MONTH))
        assertEquals(24, restoredCal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `past and current dates are selectable while future dates are rejected`() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 15, 8, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        val pastUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(2026, Calendar.NOVEMBER, 10)
        }.timeInMillis
        val todayUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(2026, Calendar.NOVEMBER, 15)
        }.timeInMillis
        val futureUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(2026, Calendar.NOVEMBER, 16)
        }.timeInMillis

        assertTrue(WorkoutDatePickerUtils.isPastOrToday(pastUtc, now))
        assertTrue(WorkoutDatePickerUtils.isPastOrToday(todayUtc, now))
        assertFalse(WorkoutDatePickerUtils.isPastOrToday(futureUtc, now))
    }
}
