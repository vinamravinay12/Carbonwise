package com.rivi.carbonwise.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rivi.carbonwise.domain.Benchmarks
import com.rivi.carbonwise.ui.formatKg
import com.rivi.carbonwise.ui.theme.BandAverage
import com.rivi.carbonwise.ui.theme.BandHigh
import com.rivi.carbonwise.ui.theme.BandLow

/**
 * A simple gauge that places the day's footprint against the climate target and the
 * daily average, so a raw number gains meaning at a glance.
 */
@Composable
fun BenchmarkGauge(totalKg: Double, modifier: Modifier = Modifier) {
    val band = Benchmarks.band(totalKg)
    val bandColor = when (band) {
        Benchmarks.Band.LOW -> BandLow
        Benchmarks.Band.AVERAGE -> BandAverage
        Benchmarks.Band.HIGH -> BandHigh
    }
    val scaleMax = maxOf(totalKg, Benchmarks.AVERAGE_DAILY_KG) * 1.25
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(bandColor),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = when (band) {
                    Benchmarks.Band.LOW -> "Below the climate target"
                    Benchmarks.Band.AVERAGE -> "Around the daily average"
                    Benchmarks.Band.HIGH -> "Above the daily average"
                },
                style = MaterialTheme.typography.titleMedium,
                color = bandColor,
            )
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp),
        ) {
            val h = size.height
            val barY = h / 2 - 5.dp.toPx()
            val barH = 10.dp.toPx()
            // Track
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(0f, barY),
                size = Size(size.width, barH),
                cornerRadius = CornerRadius(barH / 2, barH / 2),
            )
            // Filled portion up to the user's value
            val fillW = (totalKg / scaleMax).toFloat().coerceIn(0f, 1f) * size.width
            drawRoundRect(
                color = bandColor,
                topLeft = Offset(0f, barY),
                size = Size(fillW, barH),
                cornerRadius = CornerRadius(barH / 2, barH / 2),
            )
            // Target & average tick marks
            listOf(
                Benchmarks.TARGET_DAILY_KG to BandLow,
                Benchmarks.AVERAGE_DAILY_KG to BandAverage,
            ).forEach { (mark, color) ->
                val x = (mark / scaleMax).toFloat().coerceIn(0f, 1f) * size.width
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x - 1.5.dp.toPx(), barY - 5.dp.toPx()),
                    size = Size(3.dp.toPx(), barH + 10.dp.toPx()),
                    cornerRadius = CornerRadius(2f, 2f),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            LegendDot(BandLow, "Target ${formatKg(Benchmarks.TARGET_DAILY_KG)} kg")
            Spacer(Modifier.width(16.dp))
            LegendDot(BandAverage, "Average ${formatKg(Benchmarks.AVERAGE_DAILY_KG)} kg")
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
