package com.myschoolocr.app.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.myschoolocr.app.data.GradeMentions
import com.myschoolocr.app.data.GridColumnEntity
import com.myschoolocr.app.data.GridEntity
import com.myschoolocr.app.data.GridTableRow
import com.myschoolocr.app.pdf.GridPdfExporter
import com.myschoolocr.app.ui.components.LoadingScreen
import com.myschoolocr.app.ui.components.PremiumCard
import com.myschoolocr.app.ui.viewmodel.GridDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Détail d'une grille (MVVM, voir GridDetailViewModel) : en-tête (Classe/Matière/
 * Professeur/Date/Trimestre), tableau numéroté (élèves × colonnes de notation) façon
 * document papier, avec une lettre par colonne (A, B, C...), pastilles de couleur pour
 * la Mention, et Total/Mention calculés automatiquement à partir des colonnes "normales"
 * (une colonne "formule" ne compte pas deux fois dedans). Les notes posées
 * automatiquement par l'OCR (non confirmées par le prof) sont marquées d'un petit point
 * orange dans le tableau.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GridDetailScreen(
    viewModel: GridDetailViewModel,
    onEditGrid: () -> Unit,
    onScanCopies: () -> Unit,
    onOpenVerification: () -> Unit,
    onGridDeleted: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val grid = uiState.grid
    val columns = uiState.columns
    val rows = uiState.rows
    val pendingScans = uiState.pendingScans
    var editingRow by remember { mutableStateOf<GridTableRow?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    var isExportingPdf by remember { mutableStateOf(false) }

    // Seules les colonnes "normales" (sans formule) comptent dans le Total/Mention automatiques,
    // pour éviter de compter deux fois une éventuelle colonne "Total" définie par formule.
    val inputColumns = columns.filter { it.formula == null }
    val totalMaxPoints = inputColumns.sumOf { it.maxPoints }
    val completedCount = rows.count { it.isComplete(inputColumns) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(grid?.name ?: "Grille") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    if (pendingScans.isNotEmpty()) {
                        BadgedBox(
                            badge = { Badge { Text(pendingScans.size.toString()) } },
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            IconButton(onClick = onOpenVerification) {
                                Icon(Icons.Filled.Warning, contentDescription = "Copies à vérifier")
                            }
                        }
                    }
                    IconButton(onClick = onEditGrid) {
                        Icon(Icons.Filled.Edit, contentDescription = "Modifier la grille")
                    }
                    Box {
                        IconButton(onClick = { showExportMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Exporter")
                        }
                        DropdownMenu(expanded = showExportMenu, onDismissRequest = { showExportMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Exporter en CSV") },
                                onClick = {
                                    showExportMenu = false
                                    exportCsv(context, grid, columns, inputColumns, rows)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (isExportingPdf) "Génération du PDF…" else "Exporter en PDF") },
                                enabled = !isExportingPdf && grid != null,
                                onClick = {
                                    showExportMenu = false
                                    val currentGrid = grid ?: return@DropdownMenuItem
                                    isExportingPdf = true
                                    scope.launch {
                                        try {
                                            val file = withContext(Dispatchers.IO) {
                                                GridPdfExporter.export(context, currentGrid, columns, inputColumns, rows)
                                            }
                                            sharePdf(context, file)
                                        } catch (e: Exception) {
                                            // Échec de génération du PDF : pas de crash, juste pas d'export.
                                        } finally {
                                            isExportingPdf = false
                                        }
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Supprimer la grille", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showExportMenu = false
                                    showDeleteConfirm = true
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onScanCopies,
                icon = { Icon(Icons.Filled.DocumentScanner, null) },
                text = { Text("Scanner des copies") }
            )
        }
    ) { padding ->
        if (uiState.isLoading || grid == null) {
            LoadingScreen(message = "Chargement de la grille…", modifier = Modifier.padding(padding))
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding)) {
            LinearProgressIndicator(
                progress = { if (rows.isEmpty()) 0f else completedCount.toFloat() / rows.size },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Text(
                "$completedCount / ${rows.size} élèves entièrement notés",
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            if (rows.any { row -> row.scoresByColumnId.values.any { it.ocrUnconfirmed } }) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(Color(0xFFB45309), androidx.compose.foundation.shape.CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "= note posée automatiquement par l'OCR, jamais relue par toi",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            GridInfoHeader(grid)

            Spacer(Modifier.height(4.dp))

            val hScroll = rememberScrollState()
            PremiumCard(
                modifier = Modifier.weight(1f, fill = true).fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Column(Modifier.horizontalScroll(hScroll).padding(12.dp)) {
                    TableHeaderRow(
                        columns = columns,
                        totalMaxPoints = totalMaxPoints,
                        onRecompute = { col -> viewModel.computeFormulaColumn(col) }
                    )
                    Spacer(Modifier.height(4.dp))
                    LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 10000.dp)) {
                        itemsIndexed(rows, key = { _, row -> row.student.id }) { index, row ->
                            TableStudentRow(
                                rowNumber = index + 1,
                                row = row,
                                columns = columns,
                                inputColumns = inputColumns,
                                totalMaxPoints = totalMaxPoints,
                                onClick = { editingRow = row }
                            )
                            Spacer(Modifier.height(2.dp))
                        }
                    }
                }
            }

            LegendCard()
        }
    }

    editingRow?.let { row ->
        ScoreEditDialog(
            row = row,
            columns = columns,
            onDismiss = { editingRow = null },
            onSave = { newValues ->
                newValues.forEach { (columnId, value) ->
                    viewModel.setScore(row.student.id, columnId, value, confirmedByUser = true)
                }
                editingRow = null
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Supprimer cette grille ?") },
            text = { Text("Toutes les notes et élèves de cette grille seront définitivement supprimés. Cette action est irréversible.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteGrid(onDeleted = onGridDeleted)
                }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Annuler") } }
        )
    }
}

/** En-tête façon document papier : Classe / Matière / Professeur / Date / Trimestre. */
@Composable
private fun GridInfoHeader(grid: GridEntity) {
    val hasInfo = listOf(grid.className, grid.professorName, grid.gridDate, grid.term).any { it.isNotBlank() }
    if (!hasInfo) return
    PremiumCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth()) {
                InfoField("Classe", grid.className, Modifier.weight(1f))
                InfoField("Matière", grid.subject, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                InfoField("Professeur", grid.professorName, Modifier.weight(1f))
                InfoField("Date", grid.gridDate, Modifier.weight(1f))
            }
            if (grid.term.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                InfoField("Trimestre", grid.term, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun InfoField(label: String, value: String, modifier: Modifier = Modifier) {
    if (value.isBlank()) {
        Spacer(modifier)
        return
    }
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

/** Barème des mentions, affiché sous le tableau (comme sur le document papier de référence). */
@Composable
private fun LegendCard() {
    PremiumCard(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Barème des mentions", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Text("TB = Très Bien (≥ 80%)  ·  B = Bien (60–79%)", style = MaterialTheme.typography.bodySmall)
            Text("AB = Assez Bien (50–59%)  ·  P = Passable (40–49%)", style = MaterialTheme.typography.bodySmall)
            Text("I = Insuffisant (< 40%)", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TableHeaderRow(columns: List<GridColumnEntity>, totalMaxPoints: Double, onRecompute: (GridColumnEntity) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeaderCell("N°", width = 36.dp)
        HeaderCell("Élève", width = 168.dp)
        columns.forEachIndexed { index, col ->
            Column(Modifier.width(100.dp).padding(end = 8.dp)) {
                Text(
                    ('A' + index).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${col.name} /${col.maxPoints.toInt()}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    if (col.formula != null) {
                        IconButton(onClick = { onRecompute(col) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Filled.Calculate, contentDescription = "Recalculer ${col.name}", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
        HeaderCell("Total /${totalMaxPoints.toInt()}", width = 90.dp)
        HeaderCell("Mention", width = 90.dp)
    }
}

@Composable
private fun HeaderCell(text: String, width: Dp) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.width(width).padding(end = 8.dp)
    )
}

@Composable
private fun TableStudentRow(
    rowNumber: Int,
    row: GridTableRow,
    columns: List<GridColumnEntity>,
    inputColumns: List<GridColumnEntity>,
    totalMaxPoints: Double,
    onClick: () -> Unit
) {
    val total = row.totalObtained(inputColumns)
    val percent = if (totalMaxPoints > 0.0) (total / totalMaxPoints) * 100.0 else 0.0
    val mentionCode = if (row.isComplete(inputColumns)) GradeMentions.mentionCode(percent) else null
    val rowBackground = if (rowNumber % 2 == 0) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else Color.Transparent

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(rowBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            rowNumber.toString(),
            modifier = Modifier.width(36.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(modifier = Modifier.width(168.dp).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(28.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    initialsOf(row.student.name),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                row.student.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
        columns.forEach { col ->
            val score = row.scoresByColumnId[col.id]
            Row(modifier = Modifier.width(100.dp).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(score?.value?.let { "%.1f".format(it) } ?: "—")
                if (score?.ocrUnconfirmed == true) {
                    Spacer(Modifier.width(4.dp))
                    Box(Modifier.size(6.dp).background(Color(0xFFB45309), CircleShape))
                }
            }
        }
        Text(
            if (row.hasAnyScore()) "%.1f".format(total) else "—",
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(90.dp).padding(end = 8.dp)
        )
        Box(Modifier.width(90.dp)) { MentionPill(mentionCode) }
    }
}

private fun initialsOf(name: String): String =
    name.trim().split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifBlank { "?" }

/** Pastille de couleur pour la mention, comme sur le document papier de référence. */
@Composable
private fun MentionPill(code: String?) {
    if (code == null) {
        Text("—")
        return
    }
    val color = when (code) {
        "TB" -> Color(0xFF2E7D32)
        "B" -> Color(0xFF2E5AAC)
        "AB" -> Color(0xFF6750A4)
        "P" -> Color(0xFFB45309)
        else -> Color(0xFFBA1A1A)
    }
    Box(
        Modifier
            .background(color, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(code, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ScoreEditDialog(
    row: GridTableRow,
    columns: List<GridColumnEntity>,
    onDismiss: () -> Unit,
    onSave: (Map<Long, Double>) -> Unit
) {
    val values = remember(row.student.id) {
        mutableStateMapOf<Long, String>().apply {
            columns.forEach { col -> this[col.id] = row.scoresByColumnId[col.id]?.value?.toString() ?: "" }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(row.student.name) },
        text = {
            Column {
                columns.forEach { col ->
                    OutlinedTextField(
                        value = values[col.id] ?: "",
                        onValueChange = { text ->
                            values[col.id] = text.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.')
                        },
                        label = { Text(if (col.formula != null) "${col.name} (= ${col.formula})" else "${col.name} / ${col.maxPoints.toInt()}") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = values.mapNotNull { (id, text) -> text.toDoubleOrNull()?.let { id to it } }.toMap()
                onSave(parsed)
            }) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

private fun exportCsv(
    context: Context,
    grid: GridEntity?,
    columns: List<GridColumnEntity>,
    inputColumns: List<GridColumnEntity>,
    rows: List<GridTableRow>
) {
    val name = grid?.name ?: "grille"
    val totalMaxPoints = inputColumns.sumOf { it.maxPoints }
    val file = File(context.cacheDir, "${name.replace(" ", "_")}.csv")
    file.bufferedWriter().use { writer ->
        val header = listOf("Nom") + columns.map { "${it.name} /${it.maxPoints.toInt()}" } + listOf("Total /${totalMaxPoints.toInt()}", "Mention")
        writer.write(header.joinToString(";") + "\n")
        rows.forEach { row ->
            val total = row.totalObtained(inputColumns)
            val percent = if (totalMaxPoints > 0.0) (total / totalMaxPoints) * 100.0 else 0.0
            val mention = if (row.isComplete(inputColumns)) GradeMentions.mentionCode(percent) else ""
            val cells = listOf(row.student.name) +
                columns.map { col -> row.scoresByColumnId[col.id]?.value?.toString() ?: "" } +
                listOf(if (row.hasAnyScore()) total.toString() else "", mention)
            writer.write(cells.joinToString(";") + "\n")
        }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Exporter la grille (CSV)"))
}

private fun sharePdf(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Exporter la grille (PDF)"))
}
