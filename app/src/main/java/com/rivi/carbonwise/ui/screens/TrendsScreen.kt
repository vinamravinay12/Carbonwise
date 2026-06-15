package com.rivi.carbonwise.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.dp
import com.rivi.carbonwise.domain.Benchmarks
import com.rivi.carbonwise.ui.HistoryViewModel
import com.rivi.carbonwise.ui.TrendDay
import com.rivi.carbonwise.ui.components.SectionCard
import com.rivi.carbonwise.ui.components.SectionLabel
import com.rivi.carbonwise.ui.components.TrendChart
import com.rivi.carbonwise.ui.formatKg

@Composable
fun TrendsScreen(
    viewModel: HistoryViewModel,
    modifier: Modifier = Modifier,
) {
    val trend by viewModel.trend.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()

    val logged = trend.filter { it.totalKg > 0 }
    val weekTotal = trend.sumOf { it.totalKg }
    val dailyAvg = if (logged.isNotEmpty()) weekTotal / logged.size else 0.0
    val bestDay = logged.minByOrNull { it.totalKg }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Trends",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )

        SectionCard {
            SectionLabel("Last 7 days")
            TrendChart(days = trend)
        }

        SectionCard {
            SectionLabel("This week at a glance")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat("Total", "${formatKg(weekTotal)} kg")
                Stat("Daily avg", if (dailyAvg > 0) "${formatKg(dailyAvg)} kg" else "—")
                Stat("Best day", bestDay?.let { "${formatKg(it.totalKg)} kg" } ?: "—")
            }
        }

        SectionCard {
            SectionLabel("How you compare")
            Text(
                text = comparisonText(dailyAvg),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun comparisonText(dailyAvg: Double): String = when {
    dailyAvg <= 0 -> "Log a few days to see how your footprint compares to the " +
        "${formatKg(Benchmarks.TARGET_DAILY_KG)} kg climate target."
    dailyAvg <= Benchmarks.TARGET_DAILY_KG -> "Your daily average is below the " +
        "${formatKg(Benchmarks.TARGET_DAILY_KG)} kg climate target — genuinely excellent."
    dailyAvg <= Benchmarks.AVERAGE_DAILY_KG -> "You're tracking below the typical " +
        "${formatKg(Benchmarks.AVERAGE_DAILY_KG)} kg daily average. One swap a day pushes you toward the target."
    else -> "Your daily average is above the typical ${formatKg(Benchmarks.AVERAGE_DAILY_KG)} kg. " +
        "The best-swap nudge on each entry is where to start."
}
