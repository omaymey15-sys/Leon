package com.myschoolocr.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myschoolocr.app.data.GridColumnEntity
import com.myschoolocr.app.data.PendingScanEntity
import com.myschoolocr.app.data.Repository
import com.myschoolocr.app.data.StudentEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VerificationUiState(
    val pendingScans: List<PendingScanEntity> = emptyList(),
    val students: List<StudentEntity> = emptyList(),
    val columns: List<GridColumnEntity> = emptyList()
)

/**
 * ViewModel de l'écran de vérification (MVVM) : combine les scans en attente avec les
 * élèves/colonnes de la grille, et expose les actions de résolution/rejet.
 */
class VerificationViewModel(
    private val repository: Repository,
    private val gridId: Long
) : ViewModel() {

    private val studentsState = MutableStateFlow<List<StudentEntity>>(emptyList())
    private val columnsState = MutableStateFlow<List<GridColumnEntity>>(emptyList())

    val uiState: StateFlow<VerificationUiState> = combine(
        repository.observePendingScans(gridId),
        studentsState,
        columnsState
    ) { pending, students, columns ->
        VerificationUiState(pendingScans = pending, students = students, columns = columns)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VerificationUiState())

    init {
        viewModelScope.launch {
            studentsState.value = repository.getStudentsOnce(gridId)
            columnsState.value = repository.getColumnsOnce(gridId)
        }
    }

    fun resolve(pending: PendingScanEntity, studentId: Long, value: Double) {
        viewModelScope.launch { repository.resolvePendingScan(pending, studentId, value) }
    }

    fun discard(pending: PendingScanEntity) {
        viewModelScope.launch { repository.discardPendingScan(pending) }
    }
}
