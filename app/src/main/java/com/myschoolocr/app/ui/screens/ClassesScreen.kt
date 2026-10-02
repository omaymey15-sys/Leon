package com.myschoolocr.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.myschoolocr.app.data.ClassEntity
import com.myschoolocr.app.ui.components.EmptyState
import com.myschoolocr.app.ui.components.LoadingScreen
import com.myschoolocr.app.ui.viewmodel.ClassesViewModel

/**
 * Liste des classes réutilisables (MVVM, voir ClassesViewModel). Une classe = une liste
 * d'élèves qu'on peut importer directement dans une nouvelle grille de cotation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassesScreen(
    viewModel: ClassesViewModel,
    onOpenClass: (Long) -> Unit,
    onCreateClass: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Classes") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateClass,
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Nouvelle classe") },
                shape = RoundedCornerShape(16.dp)
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> LoadingScreen(modifier = Modifier.padding(padding))
            uiState.classes.isEmpty() -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = { Icon(Icons.Filled.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer) },
                    title = "Aucune classe enregistrée",
                    subtitle = "Crée une classe pour réutiliser sa liste d'élèves dans plusieurs grilles."
                )
            }
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(uiState.classes, key = { it.id }) { cls: ClassEntity ->
                    ClassRow(cls = cls, onClick = { onOpenClass(cls.id) })
                }
            }
        }
    }
}

@Composable
private fun ClassRow(cls: ClassEntity, onClick: () -> Unit) {
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
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.width(12.dp))
            Text(cls.name, style = MaterialTheme.typography.titleSmall)
        }
    }
}
