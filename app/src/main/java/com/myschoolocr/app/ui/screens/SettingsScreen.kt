package com.myschoolocr.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.myschoolocr.app.ui.components.PremiumCard
import com.myschoolocr.app.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import java.io.File

/** Paliers proposés pour le seuil de confiance du nom (au lieu d'un curseur continu). */
private val THRESHOLD_PRESETS = listOf(0.9f, 0.8f, 0.75f, 0.7f, 0.6f, 0.5f)

/** Écran Réglages (MVVM, voir SettingsViewModel). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val settings by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var isExporting by remember { mutableStateOf(false) }
    var showWipeConfirm by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("Réglages") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            PremiumCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("Apparence", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text("Choisis l'affichage de l'application.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    SettingsOption(label = "Clair — bleu & blanc", selected = !settings.darkTheme, onSelect = { viewModel.setDarkTheme(false) })
                    SettingsOption(label = "Sombre — bleu & noir", selected = settings.darkTheme, onSelect = { viewModel.setDarkTheme(true) })
                }
            }

            PremiumCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("Langue de reconnaissance (OCR)", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Choisis la langue dans laquelle les noms et notes sont écrits sur les copies.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    SettingsOption(label = "Français", selected = settings.ocrLanguage == "fra", onSelect = { viewModel.setOcrLanguage("fra") })
                    SettingsOption(label = "Anglais", selected = settings.ocrLanguage == "eng", onSelect = { viewModel.setOcrLanguage("eng") })
                }
            }

            PremiumCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("Seuil de confiance pour le nom", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Le nom d'un élève n'est pré-rempli automatiquement lors du scan d'une copie " +
                            "que si la correspondance dépasse ce seuil. En dessous (ou en cas d'ambiguïté), " +
                            "tu choisis manuellement.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(12.dp))
                    THRESHOLD_PRESETS.forEach { preset ->
                        SettingsOption(
                            label = "${(preset * 100).toInt()}%",
                            selected = settings.nameConfidenceThreshold == preset,
                            onSelect = { viewModel.setNameConfidenceThreshold(preset) }
                        )
                    }
                }
            }

            PremiumCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("Sauvegarde", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Exporte toutes tes grilles, notes et classes dans un fichier de sauvegarde " +
                            "(sans les photos). Utile avant de changer de téléphone. La ré-importation " +
                            "automatique n'existe pas encore : garde ce fichier de côté au cas où.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        enabled = !isExporting,
                        shape = RoundedCornerShape(16.dp),
                        onClick = {
                            isExporting = true
                            scope.launch {
                                val json = viewModel.exportAllDataAsJson()
                                val file = File(context.cacheDir, "myschool_ocr_sauvegarde.json")
                                file.writeText(json)
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(android.content.Intent.createChooser(intent, "Exporter la sauvegarde"))
                                isExporting = false
                            }
                        }
                    ) { Text(if (isExporting) "Export en cours…" else "Exporter toutes les données") }
                }
            }

            PremiumCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("Gestion des données", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Vide les fichiers temporaires (exports CSV/PDF déjà partagés) pour libérer de " +
                            "l'espace, sans toucher à tes grilles.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        shape = RoundedCornerShape(16.dp),
                        onClick = {
                            context.cacheDir.listFiles()?.forEach { file ->
                                if (file.isFile && (file.extension == "csv" || file.extension == "pdf" || file.extension == "json")) {
                                    file.delete()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Vider le cache d'export") }

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(16.dp))

                    Text(
                        "Zone dangereuse",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Supprime définitivement toutes les grilles, classes, élèves et notes. Les réglages " +
                            "(apparence, langue...) sont conservés. Cette action est irréversible.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        onClick = { showWipeConfirm = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Réinitialiser toutes les données") }
                }
            }

            PremiumCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("À propos", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text("MySchool OCR — version 1.0", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Tout fonctionne hors-ligne : aucune donnée (grilles, notes, photos) n'est envoyée " +
                            "sur internet.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        shape = RoundedCornerShape(16.dp),
                        onClick = { viewModel.resetOnboarding() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Revoir le message de bienvenue") }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    if (showWipeConfirm) {
        AlertDialog(
            onDismissRequest = { showWipeConfirm = false },
            title = { Text("Réinitialiser toutes les données ?") },
            text = {
                Text(
                    "Toutes tes grilles, classes, élèves et notes seront supprimés définitivement. " +
                        "Cette action est irréversible. Pense à exporter une sauvegarde avant si besoin."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showWipeConfirm = false
                    viewModel.wipeAllData(onDone = {})
                }) { Text("Tout supprimer", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showWipeConfirm = false }) { Text("Annuler") } }
        )
    }
}

@Composable
private fun SettingsOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}
