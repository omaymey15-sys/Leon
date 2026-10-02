package com.myschoolocr.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.myschoolocr.app.data.GridColumnEntity
import com.myschoolocr.app.data.Repository
import com.myschoolocr.app.data.ScanPhotoStorage
import com.myschoolocr.app.data.StudentEntity
import com.myschoolocr.app.ocr.GradeParser
import com.myschoolocr.app.ocr.OcrAnalyzer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Scan en continu des copies pour UNE colonne de notation à la fois : le prof capture
 * une photo, l'app enchaîne immédiatement sur la suivante pendant que l'OCR travaille
 * en arrière-plan. Une copie identifiée avec confiance est enregistrée directement
 * (aucune photo conservée). Une copie ambiguë ou sans note détectée est mise de côté
 * dans "Vérification" (photo conservée le temps de la résoudre à la main).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CopyScanScreen(
    repository: Repository,
    gridId: Long,
    ocrLanguage: String,
    nameConfidenceThreshold: Double,
    onOpenVerification: () -> Unit,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    var columns by remember { mutableStateOf<List<GridColumnEntity>>(emptyList()) }
    var students by remember { mutableStateOf<List<StudentEntity>>(emptyList()) }
    var selectedColumn by remember { mutableStateOf<GridColumnEntity?>(null) }
    var showColumnPicker by remember { mutableStateOf(false) }
    var processingCount by remember { mutableIntStateOf(0) }
    var doneCount by remember { mutableIntStateOf(0) }
    var lastMessage by remember { mutableStateOf<String?>(null) }
    val pendingScans by repository.observePendingScans(gridId).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    LaunchedEffect(gridId) {
        columns = repository.getColumnsOnce(gridId)
        students = repository.getStudentsOnce(gridId)
        selectedColumn = columns.firstOrNull()
    }

    // Message transitoire (succès / mis en attente), disparaît tout seul.
    LaunchedEffect(lastMessage) {
        if (lastMessage != null) {
            delay(2200)
            lastMessage = null
        }
    }

    val column = selectedColumn
    if (column == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (columns.isEmpty()) {
                Text("Cette grille n'a aucune colonne de notation. Modifie-la pour en ajouter.")
            } else {
                CircularProgressIndicator()
            }
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        CameraCaptureScreen(
            title = buildString {
                append("${column.name} · $doneCount traitée(s)")
                if (processingCount > 0) append(" · $processingCount en cours")
            },
            onCaptured = { bmp ->
                processingCount++
                scope.launch {
                    try {
                        val ocrResult = OcrAnalyzer.recognize(context, bmp, ocrLanguage)
                        val (nameGuess, guessedGrade) = GradeParser.bestGuess(
                            ocrResult.lines, students, column.maxPoints, ocrResult.alignedBitmap, ocrResult.alignedBitmap.height, nameConfidenceThreshold
                        )
                        val confidentStudent = nameGuess.student
                        if (confidentStudent != null && guessedGrade != null) {
                            // Identification fiable : on enregistre directement, aucune photo conservée.
                            repository.setScore(confidentStudent.id, column.id, guessedGrade.value, confirmedByUser = false)
                            lastMessage = "✓ ${confidentStudent.name} — ${"%.1f".format(guessedGrade.value)}"
                        } else {
                            // Cas ambigu ou incomplet : on garde la photo (redressée) pour vérification manuelle.
                            val path = ScanPhotoStorage.save(context, ocrResult.alignedBitmap)
                            repository.addPendingScan(
                                gridId = gridId,
                                columnId = column.id,
                                photoPath = path,
                                suggestedStudentId = nameGuess.suggestedStudent?.id,
                                suggestedNoteValue = guessedGrade?.value
                            )
                            lastMessage = "⚠ Copie ajoutée à Vérification"
                        }
                    } catch (e: Exception) {
                        lastMessage = "❌ Erreur pendant l'analyse (${e.javaClass.simpleName}). Réessaie."
                    } finally {
                        processingCount--
                        doneCount++
                    }
                }
            },
            onCancel = onFinished
        )

        // Barre de navigation en haut : colonne active + accès à la vérification.
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AssistChip(onClick = { showColumnPicker = true }, label = { Text(column.name) })
            BadgedBox(badge = {
                if (pendingScans.isNotEmpty()) {
                    Badge { Text(pendingScans.size.toString()) }
                }
            }) {
                AssistChip(onClick = onOpenVerification, label = { Text("Vérification") })
            }
        }

        lastMessage?.let { msg ->
            Card(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 72.dp)
                    .padding(horizontal = 16.dp)
            ) {
                Text(msg, Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
            }
        }
    }

    if (showColumnPicker) {
        AlertDialog(
            onDismissRequest = { showColumnPicker = false },
            title = { Text("Quelle colonne corriges-tu ?") },
            text = {
                LazyColumn {
                    items(columns, key = { it.id }) { col ->
                        ListItem(
                            headlineContent = { Text("${col.name} / ${col.maxPoints.toInt()}") },
                            modifier = Modifier.clickable {
                                selectedColumn = col
                                showColumnPicker = false
                            }
                        )
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showColumnPicker = false }) { Text("Fermer") } }
        )
    }
}
