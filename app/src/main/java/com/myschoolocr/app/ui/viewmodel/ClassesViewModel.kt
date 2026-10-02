package com.myschoolocr.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myschoolocr.app.data.ClassEntity
import com.myschoolocr.app.data.Repository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class ClassesUiState(
    val classes: List<ClassEntity> = emptyList(),
    val isLoading: Boolean = true
)

/** ViewModel de l'écran Classes (MVVM) : expose la liste des classes réutilisables. */
class ClassesViewModel(repository: Repository) : ViewModel() {

    val uiState: StateFlow<ClassesUiState> = repository.classes
        .map { classes -> ClassesUiState(classes = classes, isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ClassesUiState())
}
