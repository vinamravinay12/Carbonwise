package com.rivi.carbonwise.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rivi.carbonwise.data.CarbonRepository
import com.rivi.carbonwise.data.CompareResult
import com.rivi.carbonwise.data.DetectedTrip
import com.rivi.carbonwise.data.LoggedDay
import com.rivi.carbonwise.recognition.ActivityRecognitionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One bubble in the Compare conversation. [image] is a thumbnail the user attached. */
data class CompareMessage(
    val fromUser: Boolean,
    val text: String,
    val result: CompareResult? = null,
    val image: android.graphics.Bitmap? = null,
)

enum class InputMode { LOG, COMPARE }

data class HomeUiState(
    val input: String = "",
    val mode: InputMode = InputMode.LOG,
    val isLogging: Boolean = false,
    val result: LoggedDay? = null,
    val compareMessages: List<CompareMessage> = emptyList(),
    val isComparing: Boolean = false,
    val error: String? = null,
    // Auto-tracking (Activity Recognition)
    val trackingEnabled: Boolean = false,
    val pendingDetections: List<DetectedTrip> = emptyList(),
    val requestPermission: Boolean = false,
    val needsBatteryExemption: Boolean = false,
    val needsLocationPermission: Boolean = false,
)

/**
 * Drives the Today tab: manual day-logging, the Compare/"Ask AI" conversation, and the
 * auto-tracking surface (toggle, permission prompts, pending detections, confirm-to-log).
 * Holds all screen state in a single immutable [HomeUiState] exposed as a [StateFlow].
 */
class HomeViewModel(
    private val repository: CarbonRepository,
    private val recognition: ActivityRecognitionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState(trackingEnabled = recognition.isEnabled()))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        refreshReliability()
        viewModelScope.launch {
            repository.observePendingDetections().collect { trips ->
                _state.update { it.copy(pendingDetections = trips) }
            }
        }
    }

    /** Re-checks tracking state, missing location, and battery-optimization exemption. */
    fun refreshReliability() {
        val enabled = recognition.isEnabled()
        _state.update {
            it.copy(
                trackingEnabled = enabled,
                needsLocationPermission = enabled && !recognition.hasLocationPermission(),
                needsBatteryExemption = enabled && !recognition.isIgnoringBatteryOptimizations(),
            )
        }
    }

    /** Re-run the permission flow (e.g. to grant location after tracking is already on). */
    fun requestTrackingPermissions() {
        _state.update { it.copy(requestPermission = true) }
    }

    fun onInputChange(text: String) {
        _state.update { it.copy(input = text, error = null) }
    }

    fun onModeChange(mode: InputMode) {
        _state.update { it.copy(mode = mode, error = null, input = "") }
    }

    /** Compare-mode: send a message (optionally with an image) in the ongoing conversation. */
    fun sendCompare(image: AttachedImage? = null) {
        val message = _state.value.input.trim()
        if ((message.isEmpty() && image == null) || _state.value.isComparing) return

        // With an image but no text, ask a sensible default question.
        val prompt = message.ifEmpty { "What is this, and what's its carbon impact?" }

        _state.update {
            it.copy(
                input = "",
                error = null,
                isComparing = true,
                compareMessages = it.compareMessages +
                    CompareMessage(fromUser = true, text = message, image = image?.preview),
            )
        }
        viewModelScope.launch {
            val reply = try {
                repository.askComparison(prompt, image?.base64, image?.mime)
            } catch (e: Exception) {
                com.rivi.carbonwise.data.CompareReply("Something went wrong: ${e.message}", null)
            }
            _state.update {
                it.copy(
                    isComparing = false,
                    compareMessages = it.compareMessages +
                        CompareMessage(fromUser = false, text = reply.reply, result = reply.result),
                )
            }
        }
    }

    /** Clear the Compare conversation and its Gemini context. */
    fun resetCompare() {
        repository.resetComparison()
        _state.update { it.copy(compareMessages = emptyList(), error = null) }
    }

    fun logDay() {
        val sentence = _state.value.input.trim()
        if (sentence.isEmpty() || _state.value.isLogging) return

        _state.update { it.copy(isLogging = true, error = null) }
        viewModelScope.launch {
            try {
                val day = repository.logDay(sentence)
                if (day.footprint.activities.isEmpty()) {
                    _state.update {
                        it.copy(
                            isLogging = false,
                            error = "I couldn't recognise any activities. Try mentioning " +
                                "transport, meals, or appliances — e.g. \"drove 10 km, had a veg thali\".",
                        )
                    }
                } else {
                    _state.update { it.copy(isLogging = false, result = day, input = "") }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLogging = false, error = "Something went wrong: ${e.message}")
                }
            }
        }
    }

    /** Dismiss the result card and return to the empty input. */
    fun clearResult() {
        _state.update { it.copy(result = null) }
    }

    /** Delete the just-logged entry and return to the input. */
    fun deleteResult() {
        val day = _state.value.result ?: return
        viewModelScope.launch {
            repository.delete(day.id)
            _state.update { it.copy(result = null) }
        }
    }

    /** Re-analyse the same sentence (useful since the AI estimate can vary run to run). */
    fun refreshResult() {
        val day = _state.value.result ?: return
        if (_state.value.isLogging) return
        _state.update { it.copy(isLogging = true, error = null) }
        viewModelScope.launch {
            try {
                repository.delete(day.id)
                val refreshed = repository.logDay(day.sentence)
                _state.update { it.copy(isLogging = false, result = refreshed) }
            } catch (e: Exception) {
                _state.update { it.copy(isLogging = false, error = "Couldn't refresh: ${e.message}") }
            }
        }
    }

    // ---- Auto-tracking ----

    fun onToggleTracking(enable: Boolean) {
        if (!enable) {
            recognition.stop()
            _state.update { it.copy(trackingEnabled = false, needsBatteryExemption = false) }
            return
        }
        // Always run the permission flow: this requests location (for GPS distance) and
        // notifications too, not just Activity Recognition. The launcher silently returns
        // anything already granted, so there's no extra dialog when nothing's missing.
        _state.update { it.copy(requestPermission = true) }
    }

    /** Called once the UI has launched the system permission dialog. */
    fun consumePermissionRequest() {
        _state.update { it.copy(requestPermission = false) }
    }

    fun onActivityPermissionResult(granted: Boolean) {
        if (granted) {
            val started = recognition.start()
            _state.update { it.copy(trackingEnabled = started) }
        } else {
            _state.update { it.copy(trackingEnabled = false) }
        }
        // Recompute after the dialog so the location / battery nudges reflect the new grants.
        refreshReliability()
    }

    fun confirmDetection(trip: DetectedTrip, factorType: String, distanceKm: Double) {
        if (distanceKm <= 0 || _state.value.isLogging) return
        _state.update { it.copy(isLogging = true, error = null) }
        viewModelScope.launch {
            try {
                val day = repository.confirmDetection(trip.id, factorType, distanceKm)
                _state.update { it.copy(isLogging = false, result = day) }
            } catch (e: Exception) {
                _state.update { it.copy(isLogging = false, error = "Couldn't log that trip: ${e.message}") }
            }
        }
    }

    fun dismissDetection(trip: DetectedTrip) {
        viewModelScope.launch { repository.dismissDetection(trip.id) }
    }
}
