package com.myschoolocr.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myschoolocr.app.data.AppSettings
import com.myschoolocr.app.data.Repository
import com.myschoolocr.app.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel de l'écran Réglages (MVVM) : expose les réglages persistés et leurs setters,
 * plus l'action d'export de sauvegarde (délègue au Repository, l'écran ne gère que le
 * partage du fichier via Context/Intent, spécifique à l'UI).
 */
class SettingsViewModel(
    private val repository: Repository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val uiState: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDarkTheme(enabled) }
    }

    fun setOcrLanguage(language: String) {
        viewModelScope.launch { settingsRepository.setOcrLanguage(language) }
    }

    fun setNameConfidenceThreshold(value: Float) {
        viewModelScope.launch { settingsRepository.setNameConfidenceThreshold(value) }
    }

    suspend fun exportAllDataAsJson(): String = repository.exportAllDataAsJson()

    fun wipeAllData(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.wipeAllData()
            onDone()
        }
    }

    fun resetOnboarding() {
        viewModelScope.launch { settingsRepository.setHasSeenOnboarding(false) }
    }
}
