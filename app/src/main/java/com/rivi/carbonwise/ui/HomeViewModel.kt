package com.rivi.carbonwise.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rivi.carbonwise.data.CarbonRepository
import com.rivi.carbonwise.data.LoggedDay
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
)

class HomeViewModel(private val repository: CarbonRepository) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

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
}
