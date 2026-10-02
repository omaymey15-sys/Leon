package com.myschoolocr.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.myschoolocr.app.ocr.OcrAnalyzer
import kotlinx.coroutines.launch

/**
 * 1) Capture une photo de la feuille de classe / grille papier
 * 2) Lance l'OCR
 * 3) Laisse le prof cocher/corriger les lignes détectées comme noms d'élèves
 * 4) Renvoie la liste finale validée
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GridScanScreen(
    ocrLanguage: String = "fra",
    onConfirmed: (List<String>) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var detectedLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    when {
        capturedBitmap == null -> {
            Box(Modifier.fillMaxSize()) {
                CameraCaptureScreen(
                    title = "Cadrez la liste des élèves",
                    onCaptured = { bmp ->
                        capturedBitmap = bmp
                        isProcessing = true
                        errorMessage = null
                        scope.launch {
                            try {
                                val ocrResult = OcrAnalyzer.recognize(context, bmp, ocrLanguage)
                                detectedLines = ocrResult.lines.map { it.text }.filter { it.isNotBlank() }
                            } catch (e: Exception) {
                                errorMessage = "Erreur pendant l'analyse (${e.javaClass.simpleName}). Reprends la photo."
                                capturedBitmap = null
                            } finally {
                                isProcessing = false
                            }
                        }
                    },
                    onCancel = onCancel
                )
                errorMessage?.let { msg ->
                    Card(
                        Modifier
                            .align(androidx.compose.ui.Alignment.TopCenter)
                            .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                    ) {
                        Text(msg, Modifier.padding(16.dp))
                    }
                }
            }
        }
        isProcessing -> {
            Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Analyse de la liste en cours…")
                }
            }
        }
        else -> {
            val editableLines = remember { mutableStateListOf<String>().apply { addAll(detectedLines) } }
            Scaffold(
                topBar = { TopAppBar(title = { Text("Vérifie la liste détectée") }) },
                bottomBar = {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = {
                            capturedBitmap = null
                            detectedLines = emptyList()
                        }) { Text("Reprendre la photo") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            enabled = editableLines.isNotEmpty(),
                            onClick = { onConfirmed(editableLines.toList()) }
                        ) { Text("Valider (${editableLines.size} élèves)") }
                    }
                }
            ) { padding ->
                if (editableLines.isEmpty()) {
                    Column(
                        Modifier.fillMaxSize().padding(padding).padding(24.dp),
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Aucun texte détecté. Reprends la photo avec plus de lumière et un cadrage net.")
                    }
                } else {
                    Column(Modifier.padding(padding)) {
                        Text(
                            "Supprime les lignes qui ne sont pas des noms d'élèves.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        LazyColumn(Modifier.weight(1f)) {
                            items(editableLines.size) { index ->
                                ListItem(
                                    headlineContent = { Text(editableLines[index]) },
                                    trailingContent = {
                                        IconButton(onClick = { editableLines.removeAt(index) }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Supprimer")
                                        }
                                    }
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}
