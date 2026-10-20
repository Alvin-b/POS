package com.alvinb.counterpos.data.service

import com.alvinb.counterpos.data.model.BusinessHours
import com.alvinb.counterpos.data.model.DayBusinessHours
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

sealed interface BusinessHoursValidationResult {
    val isOpen: Boolean

    data class Open(
        val currentDay: String,
        val closesAt: String,
        val minutesRemaining: Int,
        val isNearClosing: Boolean = minutesRemaining <= 30
    ) : BusinessHoursValidationResult {
        override val isOpen: Boolean get() = true
    }

    data class Closed(
        val reason: String,
        val nextOpeningDay: String,
        val nextOpeningTime: String,
        val minutesUntilOpen: Int
    ) : BusinessHoursValidationResult {
        override val isOpen: Boolean get() = false
    }
}

/**
 * Validation service that checks the current time against configured BusinessHours.
 * Supports standard business hours, multi-day schedules, and overnight shifts (e.g. clubs open 18:00 to 04:00).
 */
class BusinessHoursValidationService {

    /**
     * Checks if operating the POS is currently permitted.
     * Owners always bypass active business hours locks.
     */
    fun isPosOperatingAllowed(
        hours: BusinessHours,
        isOwner: Boolean,
        nowCalendar: Calendar = Calendar.getInstance()
    ): Boolean {
        if (isOwner) return true
        if (!hours.isEnabled) return true
        return validate(hours, nowCalendar) is BusinessHoursValidationResult.Open
    }

    /**
     * Validates current time against configured business hours and returns comprehensive status.
     */
    fun validate(
        hours: BusinessHours,
        nowCalendar: Calendar = Calendar.getInstance()
    ): BusinessHoursValidationResult {
        if (!hours.isEnabled) {
            return BusinessHoursValidationResult.Open(
                currentDay = "Always",
                closesAt = "24/7 Open",
                minutesRemaining = 1440,
                isNearClosing = false
            )
        }

        // Adjust timezone if configured
        val calendar = if (hours.timezoneId.isNotBlank()) {
            Calendar.getInstance(TimeZone.getTimeZone(hours.timezoneId)).apply {
                timeInMillis = nowCalendar.timeInMillis
            }
        } else nowCalendar

        val todayDow = calendar.get(Calendar.DAY_OF_WEEK)
        val nowHour = calendar.get(Calendar.HOUR_OF_DAY)
        val nowMinute = calendar.get(Calendar.MINUTE)
        val nowTotalMinutes = nowHour * 60 + nowMinute

        val scheduleMap = hours.weeklySchedule.associateBy { it.dayOfWeek }
        val todaySchedule = scheduleMap[todayDow] ?: DayBusinessHours(todayDow, "Today")

        // 1. Check if yesterday's shift was overnight and still active in early morning
        val yesterdayDow = if (todayDow == Calendar.SUNDAY) Calendar.SATURDAY else todayDow - 1
        val yesterdaySchedule = scheduleMap[yesterdayDow]
        if (yesterdaySchedule != null && yesterdaySchedule.isOpen && yesterdaySchedule.isOvernight) {
            val overnightEndMinutes = yesterdaySchedule.closeTime.toMinutes()
            if (nowTotalMinutes < overnightEndMinutes) {
                val remaining = overnightEndMinutes - nowTotalMinutes
                return BusinessHoursValidationResult.Open(
                    currentDay = yesterdaySchedule.dayName,
                    closesAt = yesterdaySchedule.closeTime.format(),
                    minutesRemaining = remaining
                )
            }
        }

        // 2. Check today's schedule
        if (!todaySchedule.isOpen) {
            val (nextDay, nextTime, minsUntil) = findNextOpening(hours, calendar)
            return BusinessHoursValidationResult.Closed(
                reason = "Closed today (${todaySchedule.dayName})",
                nextOpeningDay = nextDay,
                nextOpeningTime = nextTime,
                minutesUntilOpen = minsUntil
            )
        }

        val todayOpenMinutes = todaySchedule.openTime.toMinutes()
        val todayCloseMinutes = todaySchedule.closeTime.toMinutes()

        if (todaySchedule.isOvernight) {
            // e.g. Open 18:00, Close 04:00
            if (nowTotalMinutes >= todayOpenMinutes) {
                // Currently in the night shift! Closes tomorrow morning
                val minutesUntilMidnight = 1440 - nowTotalMinutes
                val totalRemaining = minutesUntilMidnight + todayCloseMinutes
                return BusinessHoursValidationResult.Open(
                    currentDay = todaySchedule.dayName,
                    closesAt = "${todaySchedule.closeTime.format()} (Tomorrow)",
                    minutesRemaining = totalRemaining
                )
            } else {
                // Early in the day before shift starts
                val minutesUntilOpen = todayOpenMinutes - nowTotalMinutes
                return BusinessHoursValidationResult.Closed(
                    reason = "Opens this evening at ${todaySchedule.openTime.format()}",
                    nextOpeningDay = "Today",
                    nextOpeningTime = todaySchedule.openTime.format(),
                    minutesUntilOpen = minutesUntilOpen
                )
            }
        } else {
            // Standard daytime shift (e.g. 08:00 to 23:00)
            if (nowTotalMinutes in todayOpenMinutes until todayCloseMinutes) {
                val remaining = todayCloseMinutes - nowTotalMinutes
                return BusinessHoursValidationResult.Open(
                    currentDay = todaySchedule.dayName,
                    closesAt = todaySchedule.closeTime.format(),
                    minutesRemaining = remaining
                )
            } else if (nowTotalMinutes < todayOpenMinutes) {
                val minsUntil = todayOpenMinutes - nowTotalMinutes
                return BusinessHoursValidationResult.Closed(
                    reason = "Opens today at ${todaySchedule.openTime.format()}",
                    nextOpeningDay = "Today",
                    nextOpeningTime = todaySchedule.openTime.format(),
                    minutesUntilOpen = minsUntil
                )
            } else {
                // Past today's closing time
                val (nextDay, nextTime, minsUntil) = findNextOpening(hours, calendar)
                return BusinessHoursValidationResult.Closed(
                    reason = "Closed for the day (Closed at ${todaySchedule.closeTime.format()})",
                    nextOpeningDay = nextDay,
                    nextOpeningTime = nextTime,
                    minutesUntilOpen = minsUntil
                )
            }
        }
    }

    /**
     * Looks up the next opening day and time within the weekly cycle.
     */
    fun findNextOpening(
        hours: BusinessHours,
        calendar: Calendar
    ): Triple<String, String, Int> {
        val scheduleMap = hours.weeklySchedule.associateBy { it.dayOfWeek }
        val todayDow = calendar.get(Calendar.DAY_OF_WEEK)
        val nowTotalMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)

        // Check the next 7 days in sequence
        for (offset in 1..7) {
            val nextDow = ((todayDow - 1 + offset) % 7) + 1
            val daySchedule = scheduleMap[nextDow]
            if (daySchedule != null && daySchedule.isOpen) {
                val dayLabel = when (offset) {
                    1 -> "Tomorrow"
                    else -> daySchedule.dayName
                }
                val minutesToDay = (offset * 1440) - nowTotalMinutes + daySchedule.openTime.toMinutes()
                return Triple(dayLabel, daySchedule.openTime.format(), minutesToDay)
            }
        }

        return Triple("No upcoming open hours", "--:--", -1)
    }

    /**
     * Formats minutes remaining into human-readable duration (e.g. "3h 15m").
     */
    fun formatRemaining(minutes: Int): String {
        if (minutes < 0) return "Indefinite"
        val hours = minutes / 60
        val mins = minutes % 60
        return when {
            hours > 0 && mins > 0 -> "${hours}h ${mins}m"
            hours > 0 -> "${hours}h"
            else -> "${mins}m"
        }
    }
}
