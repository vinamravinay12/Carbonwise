package com.rivi.carbonwise.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rivi.carbonwise.domain.Category
import com.rivi.carbonwise.ui.formatKg
import com.rivi.carbonwise.ui.style

/**
 * The hero: a segmented ring where each arc is a category's share of the day, with the
 * total kg CO₂ in the centre. Animates in on first composition.
 */
@Composable
fun FootprintRing(
    totalKg: Double,
    byCategory: Map<Category, Double>,
    modifier: Modifier = Modifier,
    subtitle: String = "kg CO₂ today",
    size: Int = 220,
    strokeWidth: Float = 34f,
) {
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(900),
        label = "ring",
    )
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val ordered = Category.entries.mapNotNull { cat ->
        byCategory[cat]?.takeIf { it > 0 }?.let { cat to it }
    }
    val sum = ordered.sumOf { it.second }.takeIf { it > 0 } ?: 1.0

    Box(modifier = modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size.dp)) {
            val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            // Track
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth),
            )
            // Segments with small gaps between them
            var start = -90f
            val gap = if (ordered.size > 1) 6f else 0f
            ordered.forEach { (cat, value) ->
                val full = (value / sum).toFloat() * 360f
                val sweep = (full - gap).coerceAtLeast(0f) * progress
                drawArc(
                    color = cat.style().color,
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = stroke,
                )
                start += full
            }
        }
        Box(contentAlignment = Alignment.Center) {
            androidx.compose.foundation.layout.Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = formatKg(totalKg),
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
