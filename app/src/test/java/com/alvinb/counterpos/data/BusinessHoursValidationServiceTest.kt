package com.alvinb.counterpos.data

import com.alvinb.counterpos.data.model.BusinessHours
import com.alvinb.counterpos.data.model.DayBusinessHours
import com.alvinb.counterpos.data.model.TimeSlot
import com.alvinb.counterpos.data.service.BusinessHoursValidationResult
import com.alvinb.counterpos.data.service.BusinessHoursValidationService
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BusinessHoursValidationServiceTest {
    private val zone = TimeZone.getTimeZone("Africa/Nairobi")
    private val validator = BusinessHoursValidationService()

    @Test
    fun disabledEnforcementMeansUnrestricted() {
        assertTrue(validator.validate(BusinessHours.Unrestricted, localCalendar(Calendar.MONDAY, 3, 0)).isOpen)
    }

    @Test
    fun regularHoursIncludeOpeningButExcludeClosingTime() {
        val hours = withDay(
            DayBusinessHours(
                dayOfWeek = Calendar.MONDAY,
                dayName = "Monday",
                isOpen = true,
                openTime = TimeSlot(9, 0),
                closeTime = TimeSlot(18, 0)
            )
        )
        assertTrue(validator.validate(hours, localCalendar(Calendar.MONDAY, 9, 0)).isOpen)
        assertTrue(validator.validate(hours, localCalendar(Calendar.MONDAY, 17, 59)).isOpen)
        assertFalse(validator.validate(hours, localCalendar(Calendar.MONDAY, 18, 0)).isOpen)
        assertFalse(validator.validate(hours, localCalendar(Calendar.TUESDAY, 12, 0)).isOpen)
    }

    @Test
    fun overnightHoursContinueIntoTheFollowingWeekday() {
        val hours = withDay(
            DayBusinessHours(
                dayOfWeek = Calendar.MONDAY,
                dayName = "Monday",
                isOpen = true,
                openTime = TimeSlot(22, 0),
                closeTime = TimeSlot(2, 0)
            )
        )
        assertTrue(validator.validate(hours, localCalendar(Calendar.MONDAY, 23, 30)).isOpen)
        assertTrue(validator.validate(hours, localCalendar(Calendar.TUESDAY, 1, 59)).isOpen)
        assertFalse(validator.validate(hours, localCalendar(Calendar.TUESDAY, 2, 0)).isOpen)
    }

    @Test
    fun equalOpenAndCloseTimesNeverAllowOperations() {
        val hours = withDay(
            DayBusinessHours(
                dayOfWeek = Calendar.MONDAY,
                dayName = "Monday",
                isOpen = true,
                openTime = TimeSlot(9, 0),
                closeTime = TimeSlot(9, 0)
            )
        )
        assertTrue(validator.validate(hours, localCalendar(Calendar.MONDAY, 10, 0)) is BusinessHoursValidationResult.Closed)
    }

    private fun withDay(day: DayBusinessHours): BusinessHours {
        val closedWeek = BusinessHours.defaultWeeklySchedule(0, 0, 0, 0).map { it.copy(isOpen = false) }
        return BusinessHours(
            isEnabled = true,
            weeklySchedule = closedWeek.map { if (it.dayOfWeek == day.dayOfWeek) day else it },
            timezoneId = zone.id
        )
    }

    private fun localCalendar(dayOfWeek: Int, hour: Int, minute: Int): Calendar =
        Calendar.getInstance(zone).apply {
            clear()
            set(2024, Calendar.JANUARY, 1, 0, 0, 0) // Monday, 1 January 2024
            add(Calendar.DAY_OF_MONTH, (dayOfWeek - Calendar.MONDAY + 7) % 7)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }
}
