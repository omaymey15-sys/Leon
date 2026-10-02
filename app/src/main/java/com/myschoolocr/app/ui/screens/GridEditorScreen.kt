package com.myschoolocr.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myschoolocr.app.data.DEFAULT_GRID_COLUMNS
import com.myschoolocr.app.data.EditableColumnRow
import com.myschoolocr.app.data.EditableStudentRow
import com.myschoolocr.app.data.Repository
import kotlinx.coroutines.launch

/**
 * Écran unique pour créer une nouvelle grille OU modifier une grille existante.
 * - Mode création : [gridId] == null, colonnes par défaut = modèle papier (Interrogation/Devoir/Examen).
 * - Mode édition : [gridId] != null, tout pré-rempli avec les valeurs actuelles.
 *   Renommer un élève ou une colonne conserve son historique de notes ; le retirer le supprime.
 *
 * Tout l'écran est un seul LazyColumn scrollable (pas de Column fixe + liste imbriquée),
 * pour rester utilisable même avec beaucoup de colonnes/élèves sur petit écran.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GridEditorScreen(
    repository: Repository,
    gridId: Long? = null,
    initialName: String = "",
    initialSubject: String = "",
    initialClassName: String = "",
    initialProfessorName: String = "",
    initialGridDate: String = "",
    initialTerm: String = "",
    initialColumns: List<EditableColumnRow> = DEFAULT_GRID_COLUMNS,
    initialStudents: List<EditableStudentRow> = emptyList(),
    scannedNamesToAppend: List<String> = emptyList(),
    scannedColumnsToApply: List<EditableColumnRow>? = null,
    onScannedColumnsConsumed: () -> Unit = {},
    onScannedNamesConsumed: () -> Unit = {},
    onScanClassList: () -> Unit,
    onScanFullGrid: () -> Unit,
    onSave: (
        name: String, subject: String,
        className: String, professorName: String, gridDate: String, term: String,
        columns: List<EditableColumnRow>, students: List<EditableStudentRow>
    ) -> Unit,
    onCancel: () -> Unit
) {
    val isEditing = gridId != null
    var gridName by remember(gridId) { mutableStateOf(initialName) }
    var subject by remember(gridId) { mutableStateOf(initialSubject) }
    var className by remember(gridId) { mutableStateOf(initialClassName) }
    var professorName by remember(gridId) { mutableStateOf(initialProfessorName) }
    var gridDate by remember(gridId) { mutableStateOf(initialGridDate) }
    var term by remember(gridId) { mutableStateOf(initialTerm) }
    val columns = remember(gridId) { mutableStateListOf<EditableColumnRow>().apply { addAll(initialColumns) } }
    val students = remember(gridId) { mutableStateListOf<EditableStudentRow>().apply { addAll(initialStudents) } }
    var newStudent by remember { mutableStateOf("") }
    var showClassPicker by remember { mutableStateOf(false) }
    var formulaEditorIndex by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    val classes by repository.classes.collectAsState(initial = emptyList())

    val totalMaxPoints = columns.sumOf { it.maxPoints }

    // Les noms scannés (liste de classe papier) sont AJOUTÉS à la liste actuelle,
    // sans écraser ce qui a déjà été saisi ou modifié à la main.
    LaunchedEffect(scannedNamesToAppend) {
        if (scannedNamesToAppend.isNotEmpty()) {
            students.addAll(scannedNamesToAppend.map { EditableStudentRow(id = null, name = it) })
            onScannedNamesConsumed()
        }
    }

    // Le scan d'une grille papier complète REMPLACE les colonnes actuelles (nouvelle
    // structure détectée), contrairement au scan simple de liste qui ne fait qu'ajouter
    // des élèves. Les colonnes existantes avec un id (déjà en base) sont conservées si
    // elles ne sont pas concernées par le scan — ici on part du principe qu'un scan
    // complet redéfinit tout, donc on ne fait ce remplacement qu'en mode création.
    LaunchedEffect(scannedColumnsToApply) {
        val newColumns = scannedColumnsToApply
        if (newColumns != null) {
            if (!isEditing) {
                columns.clear()
                columns.addAll(newColumns)
            }
            onScannedColumnsConsumed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Modifier la grille" else "Nouvelle grille") },
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
                    value = gridName, onValueChange = { gridName = it },
                    label = { Text("Nom de la grille (ex: Maths 6A - Interro 3)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = subject, onValueChange = { subject = it },
                    label = { Text("Matière (optionnel)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = className, onValueChange = { className = it },
                        label = { Text("Classe") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = professorName, onValueChange = { professorName = it },
                        label = { Text("Professeur") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = gridDate, onValueChange = { gridDate = it },
                        label = { Text("Date") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = term, onValueChange = { term = it },
                        label = { Text("Trimestre") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    "Colonnes de notation — Total sur ${totalMaxPoints.toInt()}",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Comme sur le modèle papier : Interrogation, Devoir, Examen… Ajoute, renomme ou retire des colonnes selon ton évaluation.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Touche la lettre d'une colonne pour définir une formule de calcul (ex: D = A+B-C/10).",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                if (!isEditing) {
                    OutlinedButton(onClick = onScanFullGrid, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.DocumentScanner, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Scanner une grille papier complète")
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            items(columns.size, key = { index -> "col_${columns[index].id ?: "new_$index"}" }) { index ->
                val column = columns[index]
                Column(Modifier.padding(vertical = 4.dp).fillParentMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clickable { formulaEditorIndex = index },
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                                Text(('A' + index).toString(), modifier = Modifier.padding(8.dp), fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = column.name,
                            onValueChange = { newName -> columns[index] = columns[index].copy(name = newName) },
                            label = { Text("Nom") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = if (column.maxPoints == 0.0) "" else column.maxPoints.toInt().toString(),
                            onValueChange = { text ->
                                val digits = text.filter { it.isDigit() }
                                columns[index] = columns[index].copy(maxPoints = digits.toDoubleOrNull() ?: 0.0)
                            },
                            label = { Text("/ pts") },
                            modifier = Modifier.width(90.dp),
                            singleLine = true
                        )
                        IconButton(onClick = { columns.removeAt(index) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Supprimer la colonne")
                        }
                    }
                    column.formula?.let { formula ->
                        Text(
                            "= $formula",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 48.dp)
                        )
                    }
                }
            }

            item {
                TextButton(onClick = { columns.add(EditableColumnRow(id = null, name = "", maxPoints = 20.0)) }) {
                    Text("+ Ajouter une colonne de notation")
                }

                if (isEditing) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Renommer un élève ou une colonne garde son historique. Le retirer supprime ses notes.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Élèves (${students.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { showClassPicker = true }) {
                        Icon(Icons.Filled.Groups, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Importer une classe")
                    }
                }
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
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
                Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onCancel) { Text("Annuler") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        enabled = gridName.isNotBlank() &&
                            students.isNotEmpty() && students.all { it.name.isNotBlank() } &&
                            columns.isNotEmpty() && columns.all { it.name.isNotBlank() && it.maxPoints > 0.0 },
                        onClick = {
                            onSave(
                                gridName.trim(),
                                subject.trim(),
                                className.trim(),
                                professorName.trim(),
                                gridDate.trim(),
                                term.trim(),
                                columns.map { it.copy(name = it.name.trim()) },
                                students.map { it.copy(name = it.name.trim()) }
                            )
                        }
                    ) { Text(if (isEditing) "Enregistrer" else "Créer la grille") }
                }
            }
        }

        formulaEditorIndex?.let { index ->
            val availableLetters = columns.indices.filter { it != index }.map { ('A' + it) }.toSet()
            FormulaEditorDialog(
                columnLetter = ('A' + index),
                availableLetters = availableLetters,
                initialFormula = columns[index].formula,
                onDismiss = { formulaEditorIndex = null },
                onConfirm = { formula ->
                    columns[index] = columns[index].copy(formula = formula)
                    formulaEditorIndex = null
                },
                onRemoveFormula = {
                    columns[index] = columns[index].copy(formula = null)
                    formulaEditorIndex = null
                }
            )
        }
    }

    if (showClassPicker) {
        AlertDialog(
            onDismissRequest = { showClassPicker = false },
            title = { Text("Importer une classe") },
            text = {
                if (classes.isEmpty()) {
                    Text("Aucune classe enregistrée pour l'instant. Crée-en une depuis l'onglet Classes.")
                } else {
                    LazyColumn(Modifier.heightIn(max = 400.dp)) {
                        items(classes, key = { it.id }) { cls ->
                            ListItem(
                                headlineContent = { Text(cls.name) },
                                modifier = Modifier.clickable {
                                    scope.launch {
                                        val classStudents = repository.getClassStudentsOnce(cls.id)
                                        students.addAll(classStudents.map { EditableStudentRow(id = null, name = it.name) })
                                        showClassPicker = false
                                    }
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showClassPicker = false }) { Text("Fermer") } }
        )
    }
}

/**
 * Clavier personnalisé pour composer la formule d'une colonne calculée :
 * lettres A-Z (grisées si la colonne correspondante n'existe pas encore),
 * chiffres 0-9, les 4 opérations, et les parenthèses.
 */
@Composable
private fun FormulaEditorDialog(
    columnLetter: Char,
    availableLetters: Set<Char>,
    initialFormula: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onRemoveFormula: () -> Unit
) {
    var formulaText by remember { mutableStateOf(initialFormula ?: "") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(
                Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Formule de la colonne $columnLetter", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Utilise les lettres des autres colonnes (ex: $columnLetter = A+B-C/10).",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = formulaText,
                    onValueChange = { formulaText = it.uppercase() },
                    label = { Text("$columnLetter =") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                errorMessage?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(12.dp))

                ('A'..'Z').toList().chunked(6).forEach { rowLetters ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        rowLetters.forEach { letter ->
                            FormulaKey(letter.toString(), enabled = letter in availableLetters) {
                                formulaText += letter
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                ('0'..'9').toList().chunked(5).forEach { rowDigits ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        rowDigits.forEach { digit ->
                            FormulaKey(digit.toString()) { formulaText += digit }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf('+', '-', '*', '/', '(', ')').forEach { symbol ->
                        FormulaKey(symbol.toString()) { formulaText += symbol }
                    }
                }
                Spacer(Modifier.height(8.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { if (formulaText.isNotEmpty()) formulaText = formulaText.dropLast(1) }) {
                        Text("⌫ Effacer un caractère")
                    }
                    TextButton(onClick = { formulaText = "" }) { Text("Tout effacer") }
                }

                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (initialFormula != null) {
                        TextButton(onClick = onRemoveFormula) {
                            Text("Retirer la formule", color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    TextButton(onClick = onDismiss) { Text("Annuler") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        if (formulaText.isBlank()) {
                            errorMessage = "Entre une formule."
                        } else if (com.myschoolocr.app.data.FormulaEvaluator.isSyntaxValid(formulaText, availableLetters)) {
                            onConfirm(formulaText)
                        } else {
                            errorMessage = "Formule invalide : vérifie les lettres utilisées et la syntaxe."
                        }
                    }) { Text("Calculer") }
                }
            }
        }
    }
}

@Composable
private fun FormulaKey(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.padding(2.dp).size(38.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
