package com.rivi.carbonwise.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rivi.carbonwise.domain.Benchmarks
import com.rivi.carbonwise.ui.TrendDay
import com.rivi.carbonwise.ui.formatKg
import com.rivi.carbonwise.ui.theme.BandHigh
import com.rivi.carbonwise.ui.theme.BandLow
import com.rivi.carbonwise.ui.theme.Emerald
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/** Seven-day bar chart with the climate-target line drawn across it. */
@Composable
fun TrendChart(days: List<TrendDay>, modifier: Modifier = Modifier) {
    val maxValue = maxOf(
        days.maxOfOrNull { it.totalKg } ?: 0.0,
        Benchmarks.AVERAGE_DAILY_KG,
    ).takeIf { it > 0 } ?: 1.0

    Column(modifier = modifier) {
        Box(modifier = Modifier.fillMaxWidth().height(180.dp)) {
            // Target reference line
            val targetFraction = (Benchmarks.TARGET_DAILY_KG / maxValue).toFloat()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(targetFraction)
                    .align(Alignment.BottomStart),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .align(Alignment.TopStart)
                        .background(BandLow.copy(alpha = 0.5f)),
                )
            }
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                days.forEach { day ->
                    val fraction by animateFloatAsState(
                        targetValue = (day.totalKg / maxValue).toFloat().coerceIn(0f, 1f),
                        animationSpec = tween(700),
                        label = "trendbar_${day.epochDay}",
                    )
                    val barColor = when {
                        day.totalKg <= 0.0 -> MaterialTheme.colorScheme.surfaceVariant
                        day.totalKg <= Benchmarks.TARGET_DAILY_KG -> BandLow
                        day.totalKg > Benchmarks.AVERAGE_DAILY_KG -> BandHigh
                        else -> Emerald
                    }
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.Bottom,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (day.totalKg > 0) {
                            Text(
                                text = formatKg(day.totalKg),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(2.dp))
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(fraction.coerceAtLeast(0.012f))
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                .background(barColor),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            days.forEach { day ->
                Text(
                    text = day.date.dayOfWeek
                        .getDisplayName(JavaTextStyle.SHORT, Locale.getDefault())
                        .take(1),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
