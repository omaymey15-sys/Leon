package com.myschoolocr.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.myschoolocr.app.data.GridColumnEntity
import com.myschoolocr.app.data.PendingScanEntity
import com.myschoolocr.app.data.ScanPhotoStorage
import com.myschoolocr.app.data.StudentEntity
import com.myschoolocr.app.ui.components.EmptyState
import com.myschoolocr.app.ui.viewmodel.VerificationViewModel

/**
 * Liste des copies que l'OCR n'a pas pu attribuer avec confiance (MVVM, voir
 * VerificationViewModel). Chaque copie garde sa photo pour que le prof puisse la
 * résoudre manuellement ; la photo est supprimée dès résolue.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerificationScreen(
    viewModel: VerificationViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var resolvingItem by remember { mutableStateOf<PendingScanEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vérification (${uiState.pendingScans.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.pendingScans.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                EmptyState(
                    icon = { Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer) },
                    title = "Rien à vérifier",
                    subtitle = "Toutes les copies scannées ont été identifiées avec confiance."
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(uiState.pendingScans, key = { it.id }) { pending ->
                    val columnName = uiState.columns.find { it.id == pending.columnId }?.name ?: "?"
                    val suggestedName = uiState.students.find { it.id == pending.suggestedStudentId }?.name
                    ListItem(
                        headlineContent = { Text(suggestedName?.let { "Peut-être : $it" } ?: "Élève non identifié") },
                        supportingContent = {
                            Text(columnName + (pending.suggestedNoteValue?.let { " · note probable : %.1f".format(it) } ?: " · aucune note détectée"))
                        },
                        modifier = Modifier.clickable { resolvingItem = pending }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    resolvingItem?.let { pending ->
        ResolvePendingDialog(
            pending = pending,
            column = uiState.columns.find { it.id == pending.columnId },
            students = uiState.students,
            onDismiss = { resolvingItem = null },
            onResolve = { studentId, value ->
                viewModel.resolve(pending, studentId, value)
                resolvingItem = null
            },
            onDiscard = {
                viewModel.discard(pending)
                resolvingItem = null
            }
        )
    }
}

@Composable
private fun ResolvePendingDialog(
    pending: PendingScanEntity,
    column: GridColumnEntity?,
    students: List<StudentEntity>,
    onDismiss: () -> Unit,
    onResolve: (studentId: Long, value: Double) -> Unit,
    onDiscard: () -> Unit
) {
    val bitmap = remember(pending.photoPath) { ScanPhotoStorage.load(pending.photoPath) }
    var selectedStudent by remember(pending.id) { mutableStateOf(students.find { it.id == pending.suggestedStudentId }) }
    var noteText by remember(pending.id) { mutableStateOf(pending.suggestedNoteValue?.toString() ?: "") }
    var showStudentPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(column?.name ?: "Copie à vérifier") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Photo de la copie",
                        modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                }
                OutlinedButton(onClick = { showStudentPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(selectedStudent?.name ?: "Choisir l'élève")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.replace(',', '.') },
                    label = { Text(column?.let { "${it.name} / ${it.maxPoints.toInt()}" } ?: "Note") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedStudent != null && noteText.toDoubleOrNull() != null,
                onClick = {
                    val student = selectedStudent ?: return@TextButton
                    val value = noteText.toDoubleOrNull() ?: return@TextButton
                    onResolve(student.id, value)
                }
            ) { Text("Enregistrer") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDiscard) { Text("Ignorer", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Fermer") }
            }
        }
    )

    if (showStudentPicker) {
        AlertDialog(
            onDismissRequest = { showStudentPicker = false },
            title = { Text("Choisir l'élève") },
            text = {
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(students, key = { it.id }) { s ->
                        ListItem(
                            headlineContent = { Text(s.name) },
                            modifier = Modifier.clickable {
                                selectedStudent = s
                                showStudentPicker = false
                            }
                        )
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showStudentPicker = false }) { Text("Fermer") } }
        )
    }
}
