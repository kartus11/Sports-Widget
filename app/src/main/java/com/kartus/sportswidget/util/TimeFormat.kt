package com.kartus.sportswidget.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * StatsAPI speaks UTC; the user thinks in local time. Every conversion between the
 * two goes through here so a game that starts at 7:10 PM Eastern is never shown on
 * the wrong calendar day.
 */
object TimeFormat {

    private val clockFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
    private val headerFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
    private val shortDayFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)

    /** Epoch millis -> "7:10 PM" in the device's zone. */
    fun clock(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(clockFormatter)

    /** "Today" / "Yesterday" / "Tomorrow", else "Sat, Jul 26". */
    fun dateHeader(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        today.plusDays(1) -> "Tomorrow"
        else -> date.format(headerFormatter)
    }

    fun shortDate(date: LocalDate): String = date.format(shortDayFormatter)

    /** "just now" / "3m ago" — used to show how fresh the widget's snapshot is. */
    fun relativeAge(fetchedAtMillis: Long, now: Long = System.currentTimeMillis()): String {
        val minutes = (now - fetchedAtMillis) / 60_000
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "${minutes}m ago"
            else -> "${minutes / 60}h ago"
        }
    }
}
