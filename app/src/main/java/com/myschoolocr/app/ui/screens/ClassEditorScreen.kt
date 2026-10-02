package com.myschoolocr.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.myschoolocr.app.data.EditableStudentRow

/**
 * Création ou édition d'une classe réutilisable : juste un nom + une liste d'élèves.
 * Même logique d'édition que GridEditorScreen (renommer garde l'identité de la ligne).
 * Un seul LazyColumn scrollable pour tout l'écran (pas de liste imbriquée).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassEditorScreen(
    classId: Long? = null,
    initialName: String = "",
    initialStudents: List<EditableStudentRow> = emptyList(),
    scannedNamesToAppend: List<String> = emptyList(),
    onScannedNamesConsumed: () -> Unit = {},
    onScanClassList: () -> Unit,
    onSave: (name: String, students: List<EditableStudentRow>) -> Unit,
    onDelete: (() -> Unit)? = null,
    onCancel: () -> Unit
) {
    val isEditing = classId != null
    var className by remember(classId) { mutableStateOf(initialName) }
    val students = remember(classId) { mutableStateListOf<EditableStudentRow>().apply { addAll(initialStudents) } }
    var newStudent by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(scannedNamesToAppend) {
        if (scannedNamesToAppend.isNotEmpty()) {
            students.addAll(scannedNamesToAppend.map { EditableStudentRow(id = null, name = it) })
            onScannedNamesConsumed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Modifier la classe" else "Nouvelle classe") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            item {
                OutlinedTextField(
                    value = className, onValueChange = { className = it },
                    label = { Text("Nom de la classe (ex: 6ème A)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Élèves (${students.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = onScanClassList) {
                        Icon(Icons.Filled.DocumentScanner, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Scanner la liste")
                    }
                }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newStudent, onValueChange = { newStudent = it },
                        label = { Text("Ajouter un élève") },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        if (newStudent.isNotBlank()) {
                            students.add(EditableStudentRow(id = null, name = newStudent.trim()))
                            newStudent = ""
                        }
                    }) { Text("Ajouter") }
                }
            }

            items(students.size, key = { index -> "stu_${students[index].id ?: "new_$index"}" }) { index ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = students[index].name,
                        onValueChange = { newName -> students[index] = students[index].copy(name = newName) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    IconButton(onClick = { students.removeAt(index) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Supprimer")
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (isEditing && onDelete != null) {
                        TextButton(onClick = { showDeleteConfirm = true }) {
                            Text("Supprimer la classe", color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }
                    Row {
                        TextButton(onClick = onCancel) { Text("Annuler") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                            enabled = className.isNotBlank() && students.all { it.name.isNotBlank() },
                            onClick = { onSave(className.trim(), students.map { it.copy(name = it.name.trim()) }) }
                        ) { Text(if (isEditing) "Enregistrer" else "Créer la classe") }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Supprimer cette classe ?") },
            text = { Text("Les grilles déjà créées à partir de cette classe ne sont pas affectées.") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete?.invoke() }) {
                    Text("Supprimer", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Annuler") } }
        )
    }
}
