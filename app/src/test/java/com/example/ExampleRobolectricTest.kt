package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("StrengthForge", appName)
  }

  @Test
  fun `verify Epley one rep max calculation`() {
    // Spreadsheet: Barbell Bench Press 150 lbs for 6 reps
    val weight = 150.0
    val reps = 6
    val e1RM = weight * (1.0 + reps / 30.0)
    assertEquals(180.0, e1RM, 0.01)

    // Starting weight: 135 lbs, Current max: 150 lbs -> 11.1% gain
    val starting = 135.0
    val current = 150.0
    val gainPercent = (current - starting) / starting * 100.0
    assertEquals(11.11, gainPercent, 0.1)
  }

  @Test
  fun `verify workout routine creation and exercise grouping`() {
    val routine = com.example.data.model.WorkoutRoutine(
        id = "test-routine",
        name = "Upper Body Hypertrophy",
        targetMuscle = "Upper Body",
        exerciseIds = listOf("bench", "pullup", "overhead_press"),
        defaultSets = 4
    )
    assertEquals("Upper Body Hypertrophy", routine.name)
    assertEquals(3, routine.exerciseIds.size)
    assertEquals(4, routine.defaultSets)
  }

  @Test
  fun `verify past date backdating calculation`() {
    val cal = java.util.Calendar.getInstance()
    cal.add(java.util.Calendar.DAY_OF_YEAR, -3)
    val pastDate = cal.time
    val now = java.util.Date()
    org.junit.Assert.assertTrue("Past date must be before now", pastDate.before(now))
  }
}
