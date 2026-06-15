package com.rivi.carbonwise.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HistoryToggleOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rivi.carbonwise.data.LoggedDay
import com.rivi.carbonwise.domain.Benchmarks
import com.rivi.carbonwise.domain.Category
import com.rivi.carbonwise.ui.HistoryViewModel
import com.rivi.carbonwise.ui.formatKg
import com.rivi.carbonwise.ui.formatTimestamp
import com.rivi.carbonwise.ui.style
import com.rivi.carbonwise.ui.theme.BandAverage
import com.rivi.carbonwise.ui.theme.BandHigh
import com.rivi.carbonwise.ui.theme.BandLow

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onOpenDay: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val history by viewModel.history.collectAsStateWithLifecycle()

    if (history.isEmpty()) {
        EmptyHistory(modifier)
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "History",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
        }
        items(history, key = { it.id }) { day ->
            HistoryRow(day = day, onClick = { onOpenDay(day.id) })
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun HistoryRow(day: LoggedDay, onClick: () -> Unit) {
    val bandColor = when (Benchmarks.band(day.footprint.totalKg)) {
        Benchmarks.Band.LOW -> BandLow
        Benchmarks.Band.AVERAGE -> BandAverage
        Benchmarks.Band.HIGH -> BandHigh
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatTimestamp(day.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = day.sentence,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                )
                Spacer(Modifier.height(10.dp))
                CategoryStrip(day)
            }
            Spacer(Modifier.width(14.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatKg(day.footprint.totalKg),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = bandColor,
                )
                Text(
                    text = "kg CO₂",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A thin proportional strip showing the day's category mix. */
@Composable
private fun CategoryStrip(day: LoggedDay) {
    val total = day.footprint.totalKg.takeIf { it > 0 } ?: 1.0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape),
    ) {
        Category.entries.forEach { cat ->
            val value = day.footprint.byCategory[cat] ?: 0.0
            if (value <= 0) return@forEach
            Box(
                modifier = Modifier
                    .weight((value / total).toFloat())
                    .fillMaxSize()
                    .background(cat.style().color),
            )
        }
    }
}

@Composable
private fun EmptyHistory(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().padding(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.HistoryToggleOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "No days logged yet",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Describe a day on the Today tab and it'll show up here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
