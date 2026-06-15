package com.rivi.carbonwise.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.rivi.carbonwise.data.LoggedDay
import com.rivi.carbonwise.domain.InsightPhraser
import com.rivi.carbonwise.ui.components.ActivityRow
import com.rivi.carbonwise.ui.components.BenchmarkGauge
import com.rivi.carbonwise.ui.components.CategoryBreakdown
import com.rivi.carbonwise.ui.components.FootprintRing
import com.rivi.carbonwise.ui.components.SectionCard
import com.rivi.carbonwise.ui.components.SectionLabel
import com.rivi.carbonwise.ui.components.SwapCard

/**
 * The full transparent breakdown for one logged day. Shared by the Home result view and
 * the History detail screen so a past day looks identical to a fresh one.
 */
@Composable
fun DayDetailContent(day: LoggedDay, modifier: Modifier = Modifier) {
    val footprint = day.footprint
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ---- Hero: ring + headline + benchmark ----
        SectionCard {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                FootprintRing(
                    totalKg = footprint.totalKg,
                    byCategory = footprint.byCategory,
                )
            }
            Text(
                text = InsightPhraser.dailyHeadline(footprint),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            BenchmarkGauge(totalKg = footprint.totalKg)
        }

        // ---- The single best swap ----
        day.swap?.let { SwapCard(swap = it) }

        // ---- Per-category breakdown ----
        SectionCard {
            SectionLabel("Where it came from")
            CategoryBreakdown(
                byCategory = footprint.byCategory,
                total = footprint.totalKg,
            )
        }

        // ---- Every activity, with the factor used ----
        SectionCard {
            SectionLabel("Every activity · factor used")
            footprint.activities.forEachIndexed { index, activity ->
                ActivityRow(activity = activity)
                if (index < footprint.activities.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }

        // ---- Anything the parser couldn't place ----
        if (day.unrecognized.isNotEmpty()) {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    SectionLabel("Not counted")
                }
                Text(
                    text = "I couldn't confidently place: " +
                        day.unrecognized.joinToString("; ") + ". " +
                        "It wasn't included, so the total stays honest.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ---- The original sentence ----
        SectionCard {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Filled.FormatQuote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = day.sentence,
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
