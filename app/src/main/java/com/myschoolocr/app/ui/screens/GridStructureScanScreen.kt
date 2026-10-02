package com.myschoolocr.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.myschoolocr.app.data.EditableColumnRow
import com.myschoolocr.app.ocr.GridTableParser
import com.myschoolocr.app.ocr.OcrAnalyzer
import com.myschoolocr.app.ocr.TableGridDetector
import com.myschoolocr.app.ui.components.LoadingScreen
import kotlinx.coroutines.launch

/**
 * Scanne une grille de cotation papier complète (comme le modèle avec colonnes
 * Interrogation/Devoir/Examen) et en reconstitue la structure : colonnes de notation,
 * liste d'élèves, et notes déjà écrites sur le papier. Le résultat atterrit directement
 * dans l'écran d'édition de grille, déjà pré-rempli mais entièrement modifiable — rien
 * n'est enregistré automatiquement.
 *
 * Deux méthodes, dans l'ordre :
 * 1. Détection géométrique réelle du quadrillage (TableGridDetector, via OpenCV) : compte
 *    les vraies lignes/colonnes du tableau et lit le contenu de chaque cellule — la plus
 *    fiable, y compris pour les notes déjà écrites.
 * 2. Repli sur l'estimation par position du texte (GridTableParser.parse) si aucun
 *    quadrillage exploitable n'est détecté (photo sans lignes visibles, tableau trop
 *    incliné...). Dans ce cas, seule la structure (colonnes + élèves) est récupérée, pas
 *    les notes déjà écrites.
 */
@Composable
fun GridStructureScanScreen(
    ocrLanguage: String,
    onParsed: (columns: List<EditableColumnRow>, studentNames: List<String>, scores: List<Triple<String, String, Double>>) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var isProcessing by remember { mutableStateOf(false) }
    var processingMessage by remember { mutableStateOf("Analyse de la grille…") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        CameraCaptureScreen(
            title = "Cadre toute la grille papier (en-têtes de colonnes + élèves)",
            onCaptured = { bmp ->
                isProcessing = true
                errorMessage = null
                scope.launch {
                    try {
                        val outcome = analyzeGrid(context, bmp, ocrLanguage) { msg -> processingMessage = msg }
                        if (outcome.studentNames.isEmpty()) {
                            errorMessage = "Aucun élève détecté. Reprends la photo bien à plat, cadrée sur tout le tableau, avec un bon éclairage."
                        } else {
                            onParsed(outcome.columns, outcome.studentNames, outcome.scores)
                        }
                    } catch (e: Exception) {
                        errorMessage = "Erreur pendant l'analyse (${e.javaClass.simpleName}). Reprends la photo, ou essaie avec un meilleur éclairage."
                    } finally {
                        isProcessing = false
                    }
                }
            },
            onCancel = onCancel
        )

        if (isProcessing) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                LoadingScreen(message = processingMessage)
            }
        }

        errorMessage?.let { msg ->
            Card(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(msg, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { errorMessage = null }) { Text("OK, reprendre une photo") }
                }
            }
        }
    }
}

private data class GridScanOutcome(
    val columns: List<EditableColumnRow>,
    val studentNames: List<String>,
    val scores: List<Triple<String, String, Double>>
)

/**
 * Tente la détection géométrique réelle du quadrillage ; si elle échoue ou ne trouve rien
 * d'exploitable, retombe sur l'estimation par position du texte.
 */
private suspend fun analyzeGrid(
    context: android.content.Context,
    bitmap: Bitmap,
    language: String,
    onProgress: (String) -> Unit
): GridScanOutcome {
    onProgress("Redressement de l'image…")
    val alignedBitmap = com.myschoolocr.app.ocr.ImagePreprocessor.deskew(bitmap)

    onProgress("Détection du quadrillage…")
    val gridLines = TableGridDetector.detect(alignedBitmap)

    if (gridLines != null && gridLines.rowCount >= 2 && gridLines.columnCount >= 2) {
        onProgress("Lecture des ${gridLines.rowCount} lignes × ${gridLines.columnCount} colonnes…")
        val cells = mutableListOf<List<String>>()
        for (r in 0 until gridLines.rowPositions.size - 1) {
            val top = gridLines.rowPositions[r]
            val bottom = gridLines.rowPositions[r + 1]
            val rowTexts = mutableListOf<String>()
            for (c in 0 until gridLines.columnPositions.size - 1) {
                val left = gridLines.columnPositions[c]
                val right = gridLines.columnPositions[c + 1]
                val margin = 4
                val safeLeft = (left + margin).coerceIn(0, alignedBitmap.width - 1)
                val safeTop = (top + margin).coerceIn(0, alignedBitmap.height - 1)
                val width = (right - left - 2 * margin).coerceAtLeast(1).coerceAtMost(alignedBitmap.width - safeLeft)
                val height = (bottom - top - 2 * margin).coerceAtLeast(1).coerceAtMost(alignedBitmap.height - safeTop)
                val cellBitmap = Bitmap.createBitmap(alignedBitmap, safeLeft, safeTop, width, height)
                val text = OcrAnalyzer.recognizeCellText(context, cellBitmap, language)
                rowTexts.add(text)
            }
            cells.add(rowTexts)
        }

        val result = GridTableParser.fromCellGrid(cells)
        if (result.studentNames.isNotEmpty()) {
            return GridScanOutcome(
                columns = result.columns.map { EditableColumnRow(id = null, name = it.name, maxPoints = it.maxPoints) },
                studentNames = result.studentNames,
                scores = result.scores.map { Triple(it.studentName, it.columnName, it.value) }
            )
        }
        // Quadrillage détecté mais rien d'exploitable dedans (photo vide, cellules mal
        // découpées...) : on retombe sur l'estimation par position du texte ci-dessous.
    }

    onProgress("Analyse du texte…")
    val wordsResult = OcrAnalyzer.recognizeWords(context, alignedBitmap, language)
    val parsed = GridTableParser.parse(wordsResult.words)
    return GridScanOutcome(
        columns = parsed.columns.map { EditableColumnRow(id = null, name = it.name, maxPoints = it.maxPoints) },
        studentNames = parsed.studentNames,
        scores = emptyList() // l'estimation par position ne lit pas les notes déjà écrites
    )
}
