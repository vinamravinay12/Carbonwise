package com.rivi.carbonwise.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
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
import com.rivi.carbonwise.data.CompareOption
import com.rivi.carbonwise.data.CompareResult
import com.rivi.carbonwise.ui.formatKg
import com.rivi.carbonwise.ui.theme.BandHigh
import com.rivi.carbonwise.ui.theme.BandLow
import com.rivi.carbonwise.ui.theme.Emerald

/** Ranked answer to "which emits more carbon?" — heaviest first, with the verdict on top. */
@Composable
fun ComparisonCard(result: CompareResult, modifier: Modifier = Modifier) {
    val max = result.options.maxOfOrNull { it.kgCo2 }?.takeIf { it > 0 } ?: 1.0
    SectionCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Verdict")
            Spacer(Modifier.width(8.dp))
            if (result.aiEstimated) AiEstimateChip()
        }
        Text(
            text = result.verdict,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        result.options.forEachIndexed { index, option ->
            val color = when (index) {
                0 -> BandHigh
                result.options.lastIndex -> BandLow
                else -> Emerald
            }
            ComparisonRow(
                option = option,
                color = color,
                fraction = (option.kgCo2 / max).toFloat(),
                tag = when (index) {
                    0 -> "Most"
                    result.options.lastIndex -> "Least"
                    else -> null
                },
            )
        }
    }
}

@Composable
private fun AiEstimateChip() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.height(12.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "AI estimate",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun ComparisonRow(
    option: CompareOption,
    color: androidx.compose.ui.graphics.Color,
    fraction: Float,
    tag: String?,
) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0.02f, 1f),
        animationSpec = tween(700),
        label = "cmp_${option.label}",
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = option.label,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (tag != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(color.copy(alpha = 0.16f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = "${formatKg(option.kgCo2)} kg",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animated)
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
        if (option.detail.isNotBlank()) {
            Text(
                text = option.detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
