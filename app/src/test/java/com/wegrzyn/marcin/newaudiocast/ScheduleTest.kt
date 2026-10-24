package com.wegrzyn.marcin.newaudiocast

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class ScheduleTest {

    // Wednesday 2026-10-21 12:00:00 local time.
    private val wednesdayNoon = at(2026, Calendar.OCTOBER, 21, 12, 0)

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, hour, minute)
        }.timeInMillis

    private fun entry(hour: Int, minute: Int, days: Set<Int> = emptySet()) =
        ScheduleEntry(id = 1, stationName = "Trójka", hour = hour, minute = minute, days = days)

    @Test
    fun oneOffLaterTodayFiresToday() {
        assertEquals(
            at(2026, Calendar.OCTOBER, 21, 20, 0),
            Schedule.nextTrigger(entry(20, 0), wednesdayNoon)
        )
    }

    @Test
    fun oneOffEarlierTodayFiresTomorrow() {
        assertEquals(
            at(2026, Calendar.OCTOBER, 22, 8, 30),
            Schedule.nextTrigger(entry(8, 30), wednesdayNoon)
        )
    }

    @Test
    fun exactlyNowFiresNextTimeNotNow() {
        assertEquals(
            at(2026, Calendar.OCTOBER, 22, 12, 0),
            Schedule.nextTrigger(entry(12, 0), wednesdayNoon)
        )
    }

    @Test
    fun weekdaysOnFridayEveningSkipToMonday() {
        val fridayEvening = at(2026, Calendar.OCTOBER, 23, 21, 0)
        val weekdays = setOf(
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY
        )
        assertEquals(
            at(2026, Calendar.OCTOBER, 26, 20, 0),
            Schedule.nextTrigger(entry(20, 0, weekdays), fridayEvening)
        )
    }

    @Test
    fun weeklyOnSameDayAfterTimeWaitsAWeek() {
        assertEquals(
            at(2026, Calendar.OCTOBER, 28, 9, 0),
            Schedule.nextTrigger(entry(9, 0, setOf(Calendar.WEDNESDAY)), wednesdayNoon)
        )
    }
}
