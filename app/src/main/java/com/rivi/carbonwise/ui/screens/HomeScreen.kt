package com.rivi.carbonwise.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.rivi.carbonwise.ServiceLocator
import com.rivi.carbonwise.data.DetectedTrip
import com.rivi.carbonwise.ui.AttachedImage
import com.rivi.carbonwise.ui.CompareMessage
import com.rivi.carbonwise.ui.HomeViewModel
import com.rivi.carbonwise.ui.ImageEncoder
import com.rivi.carbonwise.ui.components.ComparisonCard
import com.rivi.carbonwise.ui.components.SectionCard
import com.rivi.carbonwise.ui.components.SectionLabel

private val examples = listOf(
    "Drove 15 km to work, had a chicken thali for lunch, ran the AC for 6 hours.",
    "Took the metro 12 km, ate a veg lunch, watched TV for 3 hours.",
    "Cycled to college, had eggs for breakfast and fish curry for dinner.",
)

private val compareExamples = listOf(
    "Hyundai i10 vs Swift Dzire for 20 km",
    "What are the biggest sources of my carbon footprint?",
    "Chicken thali vs paneer",
    "How can I cut my daily commute emissions?",
)

// ACTIVITY_RECOGNITION is API 29+, but it's only ever requested under an SDK_INT >= Q guard.
@android.annotation.SuppressLint("InlinedApi")
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()
    val context = LocalContext.current
    var tripToConfirm by remember { mutableStateOf<DetectedTrip?>(null) }

    // Re-check tracking + battery-exemption status when returning to the screen.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshReliability() }

    // System permission flow for Activity Recognition (+ notifications on API 33+).
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result[Manifest.permission.ACTIVITY_RECOGNITION] ?: true
        viewModel.onActivityPermissionResult(granted)
    }
    LaunchedEffect(state.requestPermission) {
        if (state.requestPermission) {
            val perms = buildList {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    add(Manifest.permission.ACTIVITY_RECOGNITION)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
                // Location lets the GPS service measure trip distance; optional — tracking
                // still works without it (distance falls back to estimate / manual entry).
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            viewModel.consumePermissionRequest()
            if (perms.isNotEmpty()) permissionLauncher.launch(perms.toTypedArray())
            else viewModel.onActivityPermissionResult(true)
        }
    }

    tripToConfirm?.let { trip ->
        ConfirmDetectionDialog(
            trip = trip,
            onConfirm = { type, km ->
                viewModel.confirmDetection(trip, type, km)
                tripToConfirm = null
            },
            onCancel = { tripToConfirm = null },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Header(usingAi = ServiceLocator.usingAi)
        Spacer(Modifier.height(18.dp))

        ModeSelector(mode = state.mode, onModeChange = viewModel::onModeChange)
        Spacer(Modifier.height(18.dp))

        when (state.mode) {
            com.rivi.carbonwise.ui.InputMode.LOG -> {
                AutoTrackingCard(
                    enabled = state.trackingEnabled,
                    needsLocationPermission = state.needsLocationPermission,
                    needsBatteryExemption = state.needsBatteryExemption,
                    onToggle = viewModel::onToggleTracking,
                    onGrantLocation = viewModel::requestTrackingPermissions,
                    onFixBattery = {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(
                                    android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
                                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    },
                )
                if (state.pendingDetections.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    PendingDetectionsSection(
                        detections = state.pendingDetections,
                        onPick = { tripToConfirm = it },
                        onDismiss = viewModel::dismissDetection,
                    )
                }
                Spacer(Modifier.height(20.dp))

                if (state.result != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Today's footprint",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = viewModel::refreshResult, enabled = !state.isLogging) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Re-analyse")
                        }
                        IconButton(onClick = viewModel::deleteResult) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete entry")
                        }
                    }
                    TextButton(onClick = viewModel::clearResult) { Text("Log another day") }
                    Spacer(Modifier.height(12.dp))
                    DayDetailContent(day = state.result!!)
                } else if (state.isLogging) {
                    CalculatingCard()
                } else {
                    InputArea(
                        input = state.input,
                        isLogging = state.isLogging,
                        error = state.error,
                        onChange = viewModel::onInputChange,
                        onSubmit = viewModel::logDay,
                    )
                }
            }

            com.rivi.carbonwise.ui.InputMode.COMPARE -> {
                CompareArea(
                    input = state.input,
                    messages = state.compareMessages,
                    isComparing = state.isComparing,
                    error = state.error,
                    onChange = viewModel::onInputChange,
                    onSend = { image -> viewModel.sendCompare(image) },
                    onReset = viewModel::resetCompare,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ModeSelector(
    mode: com.rivi.carbonwise.ui.InputMode,
    onModeChange: (com.rivi.carbonwise.ui.InputMode) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.material3.FilterChip(
            selected = mode == com.rivi.carbonwise.ui.InputMode.LOG,
            onClick = { onModeChange(com.rivi.carbonwise.ui.InputMode.LOG) },
            label = { Text("Log my day") },
        )
        androidx.compose.material3.FilterChip(
            selected = mode == com.rivi.carbonwise.ui.InputMode.COMPARE,
            onClick = { onModeChange(com.rivi.carbonwise.ui.InputMode.COMPARE) },
            label = { Text("Ask AI") },
        )
    }
}

@Composable
private fun Header(usingAi: Boolean) {
    Column {
        Text(
            text = "CarbonWise",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "One sentence a day.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            ParserBadge(usingAi)
        }
    }
}

@Composable
private fun ParserBadge(usingAi: Boolean) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (usingAi) Icons.Filled.AutoAwesome else Icons.Filled.Bolt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.height(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = if (usingAi) "Gemini" else "On-device",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun CalculatingCard() {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.height(24.dp).width(24.dp),
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = "Calculating your footprint…",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Working out the impact and your best swap.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CompareArea(
    input: String,
    messages: List<CompareMessage>,
    isComparing: Boolean,
    error: String?,
    onChange: (String) -> Unit,
    onSend: (AttachedImage?) -> Unit,
    onReset: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<AttachedImage?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val img = withContext(Dispatchers.IO) { ImageEncoder.fromUri(context, uri) }
                if (img != null) pending = img
            }
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview(),
    ) { bitmap ->
        if (bitmap != null) {
            scope.launch { pending = withContext(Dispatchers.IO) { ImageEncoder.encode(bitmap) } }
        }
    }

    fun submit() {
        onSend(pending)
        pending = null
    }

    val canSend = (input.isNotBlank() || pending != null) && !isComparing

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel("Carbon assistant")
            if (messages.isNotEmpty()) {
                TextButton(onClick = onReset) { Text("New chat") }
            }
        }
        Spacer(Modifier.height(10.dp))

        // Conversation thread
        messages.forEach { message ->
            CompareBubble(message)
            Spacer(Modifier.height(12.dp))
        }
        if (isComparing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.height(18.dp).width(18.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Thinking…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        // Attached-image preview
        pending?.let { img ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    bitmap = img.preview.asImageBitmap(),
                    contentDescription = "Attached image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Image attached",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { pending = null }) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove image")
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        OutlinedTextField(
            value = input,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    if (messages.isEmpty()) "Ask anything, or attach a photo to identify…" else "Ask a follow-up, e.g. why?",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            shape = RoundedCornerShape(20.dp),
            enabled = !isComparing,
            keyboardActions = KeyboardActions(onDone = { if (canSend) submit() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
            ),
        )

        AnimatedVisibility(visible = error != null) {
            Text(
                text = error.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                enabled = !isComparing,
            ) { Icon(Icons.Filled.Image, contentDescription = "Attach a photo") }
            IconButton(onClick = { cameraLauncher.launch(null) }, enabled = !isComparing) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = "Take a photo")
            }
            Button(
                onClick = { submit() },
                enabled = canSend,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(if (messages.isEmpty()) "Ask" else "Send", style = MaterialTheme.typography.labelLarge)
            }
        }

        if (messages.isEmpty()) {
            Spacer(Modifier.height(28.dp))
            SectionLabel("Try one")
            Spacer(Modifier.height(10.dp))
            compareExamples.forEach { example ->
                ExampleChip(text = example, enabled = !isComparing, onClick = { onChange(example) })
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun CompareBubble(message: CompareMessage) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start,
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (message.fromUser) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface
                },
                tonalElevation = if (message.fromUser) 0.dp else 1.dp,
                modifier = Modifier.fillMaxWidth(0.88f),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    message.image?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Shared image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                        if (message.text.isNotBlank()) Spacer(Modifier.height(8.dp))
                    }
                    if (message.text.isNotBlank()) {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (message.fromUser) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }
        }
        // A comparison turn also shows the ranked card.
        message.result?.let {
            Spacer(Modifier.height(10.dp))
            ComparisonCard(result = it)
        }
    }
}

@Composable
private fun InputArea(
    input: String,
    isLogging: Boolean,
    error: String?,
    onChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column {
        SectionLabel("Describe your day")
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = input,
            onValueChange = onChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 130.dp),
            placeholder = {
                Text(
                    "e.g. Drove 15 km to work, had a chicken thali, ran the AC for 6 hours.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            shape = RoundedCornerShape(20.dp),
            enabled = !isLogging,
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
            ),
        )

        AnimatedVisibility(visible = error != null) {
            Text(
                text = error.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onSubmit,
            enabled = input.isNotBlank() && !isLogging,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            if (isLogging) {
                CircularProgressIndicator(
                    modifier = Modifier.height(22.dp).width(22.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.height(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Log my day", style = MaterialTheme.typography.labelLarge)
            }
        }

        Spacer(Modifier.height(28.dp))
        SectionLabel("Need a nudge? Try one")
        Spacer(Modifier.height(10.dp))
        examples.forEach { example ->
            ExampleChip(text = example, enabled = !isLogging, onClick = { onChange(example) })
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun ExampleChip(text: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "“$text”",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
