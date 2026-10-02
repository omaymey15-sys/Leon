package com.myschoolocr.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myschoolocr.app.data.GridColumnEntity
import com.myschoolocr.app.data.GridEntity
import com.myschoolocr.app.data.GridTableRow
import com.myschoolocr.app.data.PendingScanEntity
import com.myschoolocr.app.data.Repository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GridDetailUiState(
    val grid: GridEntity? = null,
    val columns: List<GridColumnEntity> = emptyList(),
    val rows: List<GridTableRow> = emptyList(),
    val pendingScans: List<PendingScanEntity> = emptyList(),
    val isLoading: Boolean = true
)

/**
 * ViewModel de l'écran de détail d'une grille (MVVM) : combine grille, colonnes,
 * tableau élèves/notes et scans en attente en un seul état, et expose les actions
 * (note, recalcul de formule, suppression) sans que l'UI ait à gérer les coroutines.
 */
class GridDetailViewModel(
    private val repository: Repository,
    private val gridId: Long
) : ViewModel() {

    private val gridState = MutableStateFlow<GridEntity?>(null)

    val uiState: StateFlow<GridDetailUiState> = combine(
        gridState,
        repository.observeColumns(gridId),
        repository.observeGridTable(gridId),
        repository.observePendingScans(gridId)
    ) { grid, columns, rows, pending ->
        GridDetailUiState(
            grid = grid,
            columns = columns,
            rows = rows,
            pendingScans = pending,
            isLoading = grid == null
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = GridDetailUiState()
    )

    init {
        viewModelScope.launch {
            gridState.value = repository.getGrid(gridId)
        }
    }

    fun setScore(studentId: Long, columnId: Long, value: Double, confirmedByUser: Boolean) {
        viewModelScope.launch { repository.setScore(studentId, columnId, value, confirmedByUser) }
    }

    fun computeFormulaColumn(column: GridColumnEntity) {
        viewModelScope.launch { repository.computeFormulaColumn(gridId, column) }
    }

    fun deleteGrid(onDeleted: () -> Unit) {
        val grid = gridState.value ?: return
        viewModelScope.launch {
            repository.deleteGrid(grid)
            onDeleted()
        }
    }
}
