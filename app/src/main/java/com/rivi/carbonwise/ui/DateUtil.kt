package com.rivi.carbonwise.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "Today · 9:30 PM" / "Yesterday · 8:00 AM" / "Mon, 14 Jun · 7:15 PM". */
fun formatTimestamp(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
    // Built per call against the current locale (it can change while the app runs).
    val locale = Locale.getDefault()
    val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", locale)
    val dateFormatter = DateTimeFormatter.ofPattern("EEE, d MMM", locale)

    val dateTime = Instant.ofEpochMilli(epochMillis).atZone(zoneId)
    val date = dateTime.toLocalDate()
    val today = LocalDate.now(zoneId)
    val dayLabel = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(dateFormatter)
    }
    return "$dayLabel · ${dateTime.format(timeFormatter)}"
}
