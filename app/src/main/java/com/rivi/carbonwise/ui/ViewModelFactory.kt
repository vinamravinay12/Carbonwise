package com.rivi.carbonwise.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.rivi.carbonwise.ServiceLocator

/** Builds ViewModels with the shared repository from [ServiceLocator]. */
class ViewModelFactory(context: Context) : ViewModelProvider.Factory {
    private val appContext = context.applicationContext
    private val repository = ServiceLocator.repository(appContext)

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(HomeViewModel::class.java) ->
            HomeViewModel(repository, ServiceLocator.recognitionManager(appContext))
        modelClass.isAssignableFrom(HistoryViewModel::class.java) -> HistoryViewModel(repository)
        else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    } as T
}
