package com.myschoolocr.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myschoolocr.app.data.DashboardStats
import com.myschoolocr.app.data.GridEntity
import com.myschoolocr.app.data.Repository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val grids: List<GridEntity> = emptyList(),
    val stats: DashboardStats = DashboardStats(0, 0, 0, null),
    val searchQuery: String = ""
) {
    val filteredGrids: List<GridEntity>
        get() = if (searchQuery.isBlank()) grids else grids.filter { it.name.contains(searchQuery, ignoreCase = true) }
}

/**
 * ViewModel de l'écran d'accueil (MVVM) : combine la liste des grilles, les statistiques
 * du tableau de bord et la recherche locale en un seul état observable par l'UI.
 * Le Repository reste la seule source de vérité pour les données ; ce ViewModel ne fait
 * que les exposer sous une forme prête à afficher.
 */
class HomeViewModel(private val repository: Repository) : ViewModel() {

    private val searchQueryState = MutableStateFlow("")

    val uiState: StateFlow<HomeUiState> = combine(
        repository.grids,
        repository.observeDashboardStats(),
        searchQueryState
    ) { grids, stats, query ->
        HomeUiState(grids = grids, stats = stats, searchQuery = query)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    fun onSearchQueryChange(query: String) {
        searchQueryState.value = query
    }
}
