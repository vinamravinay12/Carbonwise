package com.rivi.carbonwise.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rivi.carbonwise.data.CarbonRepository
import com.rivi.carbonwise.data.LoggedDay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** One calendar day's aggregated total, for the trend chart. */
data class TrendDay(
    val epochDay: Long,
    val date: LocalDate,
    val totalKg: Double,
)

class HistoryViewModel(private val repository: CarbonRepository) : ViewModel() {

    val history: StateFlow<List<LoggedDay>> =
        repository.observeHistory()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Last 7 calendar days summed, oldest → newest, including empty days as 0. */
    val trend: StateFlow<List<TrendDay>> =
        repository.observeHistory()
            .map { days -> buildTrend(days, daysBack = 7) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    private fun buildTrend(days: List<LoggedDay>, daysBack: Int): List<TrendDay> {
        val totalsByDay = days.groupBy { it.epochDay }
            .mapValues { (_, entries) -> entries.sumOf { it.footprint.netKg } }
        val today = LocalDate.now().toEpochDay()
        return (daysBack - 1 downTo 0).map { offset ->
            val epochDay = today - offset
            TrendDay(
                epochDay = epochDay,
                date = LocalDate.ofEpochDay(epochDay),
                totalKg = totalsByDay[epochDay] ?: 0.0,
            )
        }
    }
}
