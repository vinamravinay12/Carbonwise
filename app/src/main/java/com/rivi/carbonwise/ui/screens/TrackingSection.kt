package com.rivi.carbonwise.ui.screens

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rivi.carbonwise.data.DetectedTrip
import com.rivi.carbonwise.domain.EmissionFactors
import com.rivi.carbonwise.recognition.DetectedKind
import com.rivi.carbonwise.ui.components.SectionCard
import com.rivi.carbonwise.ui.components.SectionLabel

/** Toggle for auto-tracking via Activity Recognition. */
@Composable
fun AutoTrackingCard(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Sensors,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Auto-tracking",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (enabled) {
                        "Detecting trips in the background. You confirm the mode & distance."
                    } else {
                        "Let CarbonWise spot your trips automatically, then confirm them."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

/** Pending auto-detected trips awaiting one-tap confirmation. */
@Composable
fun PendingDetectionsSection(
    detections: List<DetectedTrip>,
    onPick: (DetectedTrip) -> Unit,
    onDismiss: (DetectedTrip) -> Unit,
) {
    if (detections.isEmpty()) return
    SectionCard {
        SectionLabel("Detected · confirm to log")
        detections.forEach { trip ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = trip.kind.label,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "About ${trip.durationMinutes} min",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { onDismiss(trip) }) { Text("Not me") }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = { onPick(trip) }) { Text("Log it") }
            }
        }
    }
}

/** Confirm a detected trip: pick the mode (for a vehicle) and enter the distance. */
@Composable
fun ConfirmDetectionDialog(
    trip: DetectedTrip,
    onConfirm: (factorType: String, distanceKm: Double) -> Unit,
    onCancel: () -> Unit,
) {
    var selectedType by remember(trip.id) {
        mutableStateOf(trip.suggestedType ?: trip.kind.defaultType)
    }
    // Prefer GPS-measured distance; fall back to a duration estimate the user can edit.
    val prefillKm = remember(trip.id) {
        trip.distanceKm?.takeIf { it > 0 } ?: trip.kind.estimatedDistanceKm(trip.durationMinutes)
    }
    val measured = trip.distanceKm?.takeIf { it > 0 } != null
    var distanceText by remember(trip.id) {
        mutableStateOf(prefillKm?.let { if (it > 0) "%.1f".format(it) else "" } ?: "")
    }
    val distance = distanceText.toDoubleOrNull()
    val canConfirm = distance != null && distance > 0

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Log this ${trip.kind.label.lowercase()} trip") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (trip.kind == DetectedKind.VEHICLE) {
                    Text(
                        text = "Which mode was it?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ModeChips(
                        types = trip.kind.candidateTypes,
                        selected = selectedType,
                        onSelect = { selectedType = it },
                    )
                }
                OutlinedTextField(
                    value = distanceText,
                    onValueChange = { distanceText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Distance (km)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (prefillKm != null) {
                    Text(
                        text = if (measured) {
                            "GPS-measured — adjust if it's off."
                        } else {
                            "Estimated from ${trip.durationMinutes} min — adjust if it's off."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (canConfirm) onConfirm(selectedType, distance!!) },
                enabled = canConfirm,
            ) { Text("Log trip") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun ModeChips(types: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        types.chunked(2).forEach { rowTypes ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowTypes.forEach { type ->
                    val name = EmissionFactors.byType(type)?.displayName ?: type
                    FilterChip(
                        selected = selected == type,
                        onClick = { onSelect(type) },
                        label = { Text(name) },
                    )
                }
            }
        }
    }
}
