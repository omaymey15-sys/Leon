package com.myschoolocr.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myschoolocr.app.data.AppSettings
import com.myschoolocr.app.data.DashboardStats
import com.myschoolocr.app.data.GridEntity
import com.myschoolocr.app.data.SettingsRepository
import com.myschoolocr.app.ui.components.BrandTitle
import com.myschoolocr.app.ui.components.EmptyState
import com.myschoolocr.app.ui.viewmodel.HomeUiState
import com.myschoolocr.app.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.launch

/**
 * Écran d'accueil (MVVM) : carte de statistiques en dégradé, recherche, liste des grilles.
 * Affiche un message de bienvenue une seule fois au tout premier lancement.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    settingsRepository: SettingsRepository,
    onOpenGrid: (Long) -> Unit,
    onCreateGrid: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val settings by settingsRepository.settings.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { TopAppBar(title = { BrandTitle("MySchool OCR", style = MaterialTheme.typography.titleLarge) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateGrid,
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Nouvelle grille") },
                shape = RoundedCornerShape(16.dp)
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item { StatsHeroCard(uiState.stats) }

            if (uiState.grids.isNotEmpty()) {
                item {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = viewModel::onSearchQueryChange,
                        label = { Text("Rechercher une grille") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            when {
                uiState.grids.isEmpty() -> item {
                    EmptyState(
                        icon = { Icon(Icons.Filled.Assignment, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer) },
                        title = "Aucune grille pour l'instant",
                        subtitle = "Crée ta première grille de cotation pour commencer à corriger."
                    )
                }
                uiState.filteredGrids.isEmpty() -> item {
                    Text(
                        "Aucune grille ne correspond à \"${uiState.searchQuery}\".",
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                else -> {
                    item {
                        Text(
                            "Grilles de cotation",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    items(uiState.filteredGrids, key = { it.id }) { grid: GridEntity ->
                        GridRow(grid = grid, onClick = { onOpenGrid(grid.id) })
                    }
                    item { Spacer(Modifier.height(80.dp)) } // laisse de la place sous le FAB
                }
            }
        }
    }

    if (!settings.hasSeenOnboarding) {
        AlertDialog(
            onDismissRequest = { },
            title = { BrandTitle("Bienvenue sur MySchool OCR") },
            text = {
                Column {
                    Text("Trois onglets en bas de l'écran :")
                    Spacer(Modifier.height(8.dp))
                    Text("• Accueil : tes grilles de cotation et leurs statistiques")
                    Text("• Classes : des listes d'élèves réutilisables entre plusieurs grilles")
                    Text("• Réglages : langue de reconnaissance, apparence, seuil de confiance")
                    Spacer(Modifier.height(8.dp))
                    Text("Tout fonctionne hors-ligne, aucune donnée n'est envoyée en ligne.")
                }
            },
            confirmButton = {
                TextButton(onClick = { scope.launch { settingsRepository.setHasSeenOnboarding(true) } }) {
                    Text("Compris")
                }
            }
        )
    }
}

@Composable
private fun StatsHeroCard(stats: DashboardStats) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                )
            )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(value = stats.totalGrids.toString(), label = "Grilles")
            StatItem(value = "${stats.totalStudentsWithAtLeastOneScore}/${stats.totalStudents}", label = "Copies corrigées")
            StatItem(value = stats.averageOn20?.let { "%.1f".format(it) } ?: "—", label = "Moyenne /20")
        }
    }
}

@Composable
private fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.9f))
    }
}

@Composable
private fun GridRow(grid: GridEntity, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Assignment, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(grid.name, style = MaterialTheme.typography.titleSmall)
                if (grid.subject.isNotBlank()) {
                    Text(grid.subject, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
