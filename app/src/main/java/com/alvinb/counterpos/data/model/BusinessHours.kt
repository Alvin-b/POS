package com.alvinb.counterpos.data.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Represents a single time of day in hours and minutes.
 */
data class TimeSlot(
    val hour: Int, // 0..23
    val minute: Int = 0 // 0..59
) {
    init {
        require(hour in 0..23) { "Hour must be between 0 and 23" }
        require(minute in 0..59) { "Minute must be between 0 and 59" }
    }

    fun format(): String = String.format(Locale.US, "%02d:%02d", hour, minute)

    fun toMinutes(): Int = hour * 60 + minute

    companion object {
        fun parse(timeStr: String, defaultHour: Int = 8, defaultMinute: Int = 0): TimeSlot {
            val parts = timeStr.trim().split(":")
            val h = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: defaultHour
            val m = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: defaultMinute
            return TimeSlot(h, m)
        }

        fun fromMinutes(minutes: Int): TimeSlot {
            val normalized = ((minutes % 1440) + 1440) % 1440
            return TimeSlot(normalized / 60, normalized % 60)
        }
    }
}

/**
 * Schedule for a single day of the week.
 */
data class DayBusinessHours(
    val dayOfWeek: Int, // Calendar.SUNDAY (1) .. Calendar.SATURDAY (7)
    val dayName: String, // e.g. "Monday"
    val isOpen: Boolean = true,
    val openTime: TimeSlot = TimeSlot(8, 0),
    val closeTime: TimeSlot = TimeSlot(23, 0)
) {
    /**
     * True if the shift crosses midnight (e.g. open at 18:00, close at 04:00 next day).
     */
    val isOvernight: Boolean
        get() = isOpen && (closeTime.toMinutes() < openTime.toMinutes())

    fun toJson(): JSONObject = JSONObject().apply {
        put("day_of_week", dayOfWeek)
        put("day_name", dayName)
        put("is_open", isOpen)
        put("open_time", openTime.format())
        put("close_time", closeTime.format())
    }

    companion object {
        fun fromJson(json: JSONObject): DayBusinessHours {
            val dow = json.optInt("day_of_week", Calendar.MONDAY)
            val name = json.optString("day_name", defaultDayName(dow))
            val open = json.optBoolean("is_open", true)
            val openSlot = TimeSlot.parse(json.optString("open_time", "08:00"), 8, 0)
            val closeSlot = TimeSlot.parse(json.optString("close_time", "23:00"), 23, 0)
            return DayBusinessHours(
                dayOfWeek = dow,
                dayName = name,
                isOpen = open,
                openTime = openSlot,
                closeTime = closeSlot
            )
        }

        private fun defaultDayName(dow: Int): String = when (dow) {
            Calendar.SUNDAY -> "Sunday"
            Calendar.MONDAY -> "Monday"
            Calendar.TUESDAY -> "Tuesday"
            Calendar.WEDNESDAY -> "Wednesday"
            Calendar.THURSDAY -> "Thursday"
            Calendar.FRIDAY -> "Friday"
            Calendar.SATURDAY -> "Saturday"
            else -> "Day $dow"
        }
    }
}

/**
 * Business hours configuration model for a business tenant.
 */
data class BusinessHours(
    val isEnabled: Boolean = true,
    val weeklySchedule: List<DayBusinessHours> = defaultWeeklySchedule(),
    val timezoneId: String = TimeZone.getDefault().id
) {
    fun toJson(): String {
        val root = JSONObject()
        root.put("is_enabled", isEnabled)
        root.put("timezone_id", timezoneId)
        val scheduleArray = JSONArray()
        for (day in weeklySchedule) {
            scheduleArray.put(day.toJson())
        }
        root.put("weekly_schedule", scheduleArray)
        return root.toString()
    }

    companion object {
        fun defaultWeeklySchedule(
            openHour: Int = 8,
            openMinute: Int = 0,
            closeHour: Int = 23,
            closeMinute: Int = 0
        ): List<DayBusinessHours> = listOf(
            DayBusinessHours(Calendar.MONDAY, "Monday", true, TimeSlot(openHour, openMinute), TimeSlot(closeHour, closeMinute)),
            DayBusinessHours(Calendar.TUESDAY, "Tuesday", true, TimeSlot(openHour, openMinute), TimeSlot(closeHour, closeMinute)),
            DayBusinessHours(Calendar.WEDNESDAY, "Wednesday", true, TimeSlot(openHour, openMinute), TimeSlot(closeHour, closeMinute)),
            DayBusinessHours(Calendar.THURSDAY, "Thursday", true, TimeSlot(openHour, openMinute), TimeSlot(closeHour, closeMinute)),
            DayBusinessHours(Calendar.FRIDAY, "Friday", true, TimeSlot(openHour, openMinute), TimeSlot(closeHour, closeMinute)),
            DayBusinessHours(Calendar.SATURDAY, "Saturday", true, TimeSlot(openHour, openMinute), TimeSlot(closeHour, closeMinute)),
            DayBusinessHours(Calendar.SUNDAY, "Sunday", true, TimeSlot(openHour, openMinute), TimeSlot(closeHour, closeMinute))
        )

        fun clubNightSchedule(): List<DayBusinessHours> = listOf(
            DayBusinessHours(Calendar.MONDAY, "Monday", false, TimeSlot(18, 0), TimeSlot(4, 0)),
            DayBusinessHours(Calendar.TUESDAY, "Tuesday", true, TimeSlot(18, 0), TimeSlot(4, 0)),
            DayBusinessHours(Calendar.WEDNESDAY, "Wednesday", true, TimeSlot(18, 0), TimeSlot(4, 0)),
            DayBusinessHours(Calendar.THURSDAY, "Thursday", true, TimeSlot(18, 0), TimeSlot(4, 0)),
            DayBusinessHours(Calendar.FRIDAY, "Friday", true, TimeSlot(16, 0), TimeSlot(5, 0)),
            DayBusinessHours(Calendar.SATURDAY, "Saturday", true, TimeSlot(16, 0), TimeSlot(5, 0)),
            DayBusinessHours(Calendar.SUNDAY, "Sunday", true, TimeSlot(16, 0), TimeSlot(2, 0))
        )

        fun full24HoursSchedule(): List<DayBusinessHours> = listOf(
            DayBusinessHours(Calendar.MONDAY, "Monday", true, TimeSlot(0, 0), TimeSlot(23, 59)),
            DayBusinessHours(Calendar.TUESDAY, "Tuesday", true, TimeSlot(0, 0), TimeSlot(23, 59)),
            DayBusinessHours(Calendar.WEDNESDAY, "Wednesday", true, TimeSlot(0, 0), TimeSlot(23, 59)),
            DayBusinessHours(Calendar.THURSDAY, "Thursday", true, TimeSlot(0, 0), TimeSlot(23, 59)),
            DayBusinessHours(Calendar.FRIDAY, "Friday", true, TimeSlot(0, 0), TimeSlot(23, 59)),
            DayBusinessHours(Calendar.SATURDAY, "Saturday", true, TimeSlot(0, 0), TimeSlot(23, 59)),
            DayBusinessHours(Calendar.SUNDAY, "Sunday", true, TimeSlot(0, 0), TimeSlot(23, 59))
        )

        fun fromJson(jsonStr: String?): BusinessHours {
            if (jsonStr.isNullOrBlank()) return BusinessHours()
            return runCatching {
                val obj = JSONObject(jsonStr)
                val enabled = obj.optBoolean("is_enabled", true)
                val tz = obj.optString("timezone_id", TimeZone.getDefault().id)
                val arr = obj.optJSONArray("weekly_schedule")
                val schedule = if (arr != null && arr.length() > 0) {
                    val list = mutableListOf<DayBusinessHours>()
                    for (i in 0 until arr.length()) {
                        arr.optJSONObject(i)?.let { list.add(DayBusinessHours.fromJson(it)) }
                    }
                    list
                } else {
                    defaultWeeklySchedule()
                }
                BusinessHours(isEnabled = enabled, weeklySchedule = schedule, timezoneId = tz)
            }.getOrDefault(BusinessHours())
        }
    }
}
