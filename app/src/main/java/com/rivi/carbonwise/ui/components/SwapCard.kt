package com.rivi.carbonwise.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rivi.carbonwise.domain.Swap
import com.rivi.carbonwise.ui.formatKg
import com.rivi.carbonwise.ui.theme.Citron
import com.rivi.carbonwise.ui.theme.Emerald
import com.rivi.carbonwise.ui.theme.EmeraldDark
import com.rivi.carbonwise.ui.theme.InkGreen

/** The single best swap — one clear, achievable action, presented with weight. */
@Composable
fun SwapCard(swap: Swap, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(listOf(EmeraldDark, Emerald)),
            )
            .padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Citron),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = InkGreen,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (swap.aiChosen) "Smart swap · AI pick" else "Your best swap",
                    style = MaterialTheme.typography.labelLarge,
                    color = Citron,
                )
            }
            Text(
                text = swap.message.ifBlank {
                    "Swap ${swap.from.factor.displayName.lowercase()} for " +
                        "${swap.toFactor.displayName.lowercase()} to save " +
                        "${formatKg(swap.savingKg)} kg CO₂."
                },
                style = MaterialTheme.typography.headlineSmall,
                color = androidx.compose.ui.graphics.Color.White,
                fontWeight = FontWeight.SemiBold,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "−${formatKg(swap.savingKg)}",
                    style = MaterialTheme.typography.displayLarge,
                    color = Citron,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "kg CO₂ saved today",
                    style = MaterialTheme.typography.titleMedium,
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
            // What that one change adds up to — the "why it matters" projection.
            if (swap.savingKgPerYear > 0 || swap.treesPerYear > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (swap.savingKgPerYear > 0) {
                        ProjectionPill("≈ ${formatKg(swap.savingKgPerYear)} kg / year")
                    }
                    if (swap.treesPerYear > 0) {
                        ProjectionPill("≈ ${formatKg(swap.treesPerYear)} trees 🌳")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectionPill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(InkGreen.copy(alpha = 0.35f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = androidx.compose.ui.graphics.Color.White,
        )
    }
}
