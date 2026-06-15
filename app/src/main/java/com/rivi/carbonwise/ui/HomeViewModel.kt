package com.rivi.carbonwise.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rivi.carbonwise.data.CarbonRepository
import com.rivi.carbonwise.data.DetectedTrip
import com.rivi.carbonwise.data.LoggedDay
import com.rivi.carbonwise.recognition.ActivityRecognitionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val input: String = "",
    val isLogging: Boolean = false,
    val result: LoggedDay? = null,
    val error: String? = null,
    // Auto-tracking (Activity Recognition)
    val trackingEnabled: Boolean = false,
    val pendingDetections: List<DetectedTrip> = emptyList(),
    val requestPermission: Boolean = false,
)

class HomeViewModel(
    private val repository: CarbonRepository,
    private val recognition: ActivityRecognitionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState(trackingEnabled = recognition.isEnabled()))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observePendingDetections().collect { trips ->
                _state.update { it.copy(pendingDetections = trips) }
            }
        }
    }

    fun onInputChange(text: String) {
        _state.update { it.copy(input = text, error = null) }
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

    // ---- Auto-tracking ----

    fun onToggleTracking(enable: Boolean) {
        if (!enable) {
            recognition.stop()
            _state.update { it.copy(trackingEnabled = false) }
            return
        }
        if (recognition.hasPermission()) {
            val started = recognition.start()
            _state.update { it.copy(trackingEnabled = started) }
        } else {
            _state.update { it.copy(requestPermission = true) }
        }
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
    }

    fun confirmDetection(trip: DetectedTrip, factorType: String, distanceKm: Double) {
        if (distanceKm <= 0) return
        viewModelScope.launch {
            try {
                val day = repository.confirmDetection(trip.id, factorType, distanceKm)
                _state.update { it.copy(result = day) }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Couldn't log that trip: ${e.message}") }
            }
        }
    }

    fun dismissDetection(trip: DetectedTrip) {
        viewModelScope.launch { repository.dismissDetection(trip.id) }
    }
}
