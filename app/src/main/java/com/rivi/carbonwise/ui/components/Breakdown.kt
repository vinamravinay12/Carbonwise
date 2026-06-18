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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rivi.carbonwise.domain.Category
import com.rivi.carbonwise.domain.ComputedActivity
import com.rivi.carbonwise.ui.formatKg
import com.rivi.carbonwise.ui.formatQty
import com.rivi.carbonwise.ui.style

/** Per-category share as labelled, animated bars. The "understand" moment. */
@Composable
fun CategoryBreakdown(
    byCategory: Map<Category, Double>,
    total: Double,
    modifier: Modifier = Modifier,
) {
    val max = byCategory.values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Category.entries.forEach { cat ->
            val value = byCategory[cat] ?: return@forEach
            if (value <= 0) return@forEach
            val style = cat.style()
            val pct = if (total > 0) (value / total * 100).toInt() else 0
            val fraction by animateFloatAsState(
                targetValue = (value / max).toFloat(),
                animationSpec = tween(800),
                label = "bar_${cat.name}",
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = style.icon,
                        contentDescription = null,
                        tint = style.color,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = style.label,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${formatKg(value)} kg",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "· $pct%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            .fillMaxWidth(fraction)
                            .height(10.dp)
                            .clip(CircleShape)
                            .background(style.color),
                    )
                }
            }
        }
    }
}

/** A single activity line: what it was, how much, the factor used, and its kg. */
@Composable
fun ActivityRow(activity: ComputedActivity, modifier: Modifier = Modifier) {
    val style = activity.factor.category.style()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(style.color.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = style.icon,
                contentDescription = null,
                tint = style.color,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = activity.factor.displayName,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "${formatQty(activity.quantity)} ${activity.factor.unit.symbol} × " +
                    "${formatKg(activity.factor.kgCo2PerUnit)} kg/${activity.factor.unit.symbol}" +
                    if (activity.factor.estimated) "  ·  AI estimate" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${formatKg(activity.kgCo2)} kg",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = style.color,
        )
    }
}
