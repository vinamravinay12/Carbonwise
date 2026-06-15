package com.rivi.carbonwise.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
private val dateFormatter = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())

/** "Today · 9:30 PM" / "Yesterday · 8:00 AM" / "Mon, 14 Jun · 7:15 PM". */
fun formatTimestamp(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
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
