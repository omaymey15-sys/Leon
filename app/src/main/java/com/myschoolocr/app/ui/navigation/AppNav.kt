package com.myschoolocr.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.myschoolocr.app.data.AppSettings
import com.myschoolocr.app.data.ClassEntity
import com.myschoolocr.app.data.DEFAULT_GRID_COLUMNS
import com.myschoolocr.app.data.EditableColumnRow
import com.myschoolocr.app.data.EditableStudentRow
import com.myschoolocr.app.data.GridEntity
import com.myschoolocr.app.data.Repository
import com.myschoolocr.app.data.SettingsRepository
import com.myschoolocr.app.ui.screens.*
import com.myschoolocr.app.ui.viewmodel.ClassesViewModel
import com.myschoolocr.app.ui.viewmodel.GenericViewModelFactory
import com.myschoolocr.app.ui.viewmodel.GridDetailViewModel
import com.myschoolocr.app.ui.viewmodel.HomeViewModel
import com.myschoolocr.app.ui.viewmodel.SettingsViewModel
import com.myschoolocr.app.ui.viewmodel.VerificationViewModel
import kotlinx.coroutines.launch

private object Routes {
    const val HOME = "home"
    const val CLASSES = "classes"
    const val SETTINGS = "settings"
    const val GRID_SCAN = "grid_scan"
    const val GRID_STRUCTURE_SCAN = "grid_structure_scan"
    const val CREATE_GRID = "editor_create"
    const val EDIT_GRID = "editor_edit/{gridId}"
    const val DETAIL = "detail/{gridId}"
    const val COPY_SCAN = "copy_scan/{gridId}"
    const val VERIFICATION = "verification/{gridId}"
    const val CREATE_CLASS = "class_create"
    const val EDIT_CLASS = "class_edit/{classId}"

    fun detail(id: Long) = "detail/$id"
    fun editGrid(id: Long) = "editor_edit/$id"
    fun copyScan(id: Long) = "copy_scan/$id"
    fun verification(id: Long) = "verification/$id"
    fun editClass(id: Long) = "class_edit/$id"

    val topLevel = setOf(HOME, CLASSES, SETTINGS)
}

private const val TRANSITION_DURATION_MS = 280

/**
 * Enregistre une destination avec des transitions animées cohérentes dans toute l'app :
 * glissement + fondu vers la gauche en avançant, vers la droite en revenant en arrière.
 */
private fun NavGraphBuilder.composableWithTransitions(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable androidx.compose.animation.AnimatedContentScope.(NavBackStackEntry) -> Unit
) {
    composable(
        route = route,
        arguments = arguments,
        enterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(TRANSITION_DURATION_MS)) +
                fadeIn(tween(TRANSITION_DURATION_MS))
        },
        exitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(TRANSITION_DURATION_MS)) +
                fadeOut(tween(TRANSITION_DURATION_MS))
        },
        popEnterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(TRANSITION_DURATION_MS)) +
                fadeIn(tween(TRANSITION_DURATION_MS))
        },
        popExitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(TRANSITION_DURATION_MS)) +
                fadeOut(tween(TRANSITION_DURATION_MS))
        },
        content = content
    )
}

@Composable
fun AppNav(repository: Repository, settingsRepository: SettingsRepository) {
    val navController = rememberNavController()
    val settings by settingsRepository.settings.collectAsState(initial = AppSettings())
    // "Panier" temporaire pour transmettre les noms scannés (liste de classe papier)
    // jusqu'à l'écran de création/édition (grille OU classe), quel que soit le mode.
    var pendingScannedNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingScannedColumns by remember { mutableStateOf<List<EditableColumnRow>?>(null) }
    var pendingParsedScores by remember { mutableStateOf<List<Triple<String, String, Double>>>(emptyList()) }
    val scope = rememberCoroutineScope()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute in Routes.topLevel) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == Routes.HOME,
                        onClick = { navController.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } } },
                        icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                        label = { Text("Accueil") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.CLASSES,
                        onClick = { navController.navigate(Routes.CLASSES) { popUpTo(Routes.HOME) } },
                        icon = { Icon(Icons.Filled.Groups, contentDescription = null) },
                        label = { Text("Classes") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.SETTINGS,
                        onClick = { navController.navigate(Routes.SETTINGS) { popUpTo(Routes.HOME) } },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        label = { Text("Réglages") }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding)
        ) {

            // -------------------- Accueil (MVVM) --------------------
            composableWithTransitions(Routes.HOME) {
                val homeViewModel: HomeViewModel = viewModel(
                    factory = GenericViewModelFactory { HomeViewModel(repository) }
                )
                HomeScreen(
                    viewModel = homeViewModel,
                    settingsRepository = settingsRepository,
                    onOpenGrid = { id -> navController.navigate(Routes.detail(id)) },
                    onCreateGrid = {
                        pendingScannedNames = emptyList()
                        navController.navigate(Routes.CREATE_GRID)
                    }
                )
            }

            // -------------------- Classes --------------------
            composableWithTransitions(Routes.CLASSES) {
                val classesViewModel: ClassesViewModel = viewModel(
                    factory = GenericViewModelFactory { ClassesViewModel(repository) }
                )
                ClassesScreen(
                    viewModel = classesViewModel,
                    onOpenClass = { id -> navController.navigate(Routes.editClass(id)) },
                    onCreateClass = {
                        pendingScannedNames = emptyList()
                        navController.navigate(Routes.CREATE_CLASS)
                    }
                )
            }

            composableWithTransitions(Routes.CREATE_CLASS) {
                ClassEditorScreen(
                    classId = null,
                    scannedNamesToAppend = pendingScannedNames,
                    onScannedNamesConsumed = { pendingScannedNames = emptyList() },
                    onScanClassList = { navController.navigate(Routes.GRID_SCAN) },
                    onSave = { name, rows ->
                        scope.launch {
                            repository.createClass(name, rows.map { it.name })
                            navController.popBackStack()
                        }
                    },
                    onCancel = { navController.popBackStack() }
                )
            }

            composableWithTransitions(
                Routes.EDIT_CLASS,
                arguments = listOf(navArgument("classId") { type = NavType.LongType })
            ) { backStackEntry2 ->
                val classId = backStackEntry2.arguments?.getLong("classId") ?: return@composableWithTransitions
                var loadedClass by remember { mutableStateOf<ClassEntity?>(null) }
                var loadedStudents by remember { mutableStateOf<List<EditableStudentRow>?>(null) }

                LaunchedEffect(classId) {
                    loadedClass = repository.getClass(classId)
                    loadedStudents = repository.getClassStudentsOnce(classId).map { EditableStudentRow(id = it.id, name = it.name) }
                }

                val cls = loadedClass
                val clsStudents = loadedStudents
                if (cls == null || clsStudents == null) {
                    com.myschoolocr.app.ui.components.LoadingScreen()
                } else {
                    ClassEditorScreen(
                        classId = classId,
                        initialName = cls.name,
                        initialStudents = clsStudents,
                        scannedNamesToAppend = pendingScannedNames,
                        onScannedNamesConsumed = { pendingScannedNames = emptyList() },
                        onScanClassList = { navController.navigate(Routes.GRID_SCAN) },
                        onSave = { newName, rows ->
                            scope.launch {
                                repository.updateClassWithStudents(cls.copy(name = newName), rows)
                                navController.popBackStack()
                            }
                        },
                        onDelete = {
                            scope.launch {
                                repository.deleteClass(cls)
                                navController.popBackStack()
                            }
                        },
                        onCancel = { navController.popBackStack() }
                    )
                }
            }

            // -------------------- Réglages --------------------
            composableWithTransitions(Routes.SETTINGS) {
                val settingsViewModel: SettingsViewModel = viewModel(
                    factory = GenericViewModelFactory { SettingsViewModel(repository, settingsRepository) }
                )
                SettingsScreen(viewModel = settingsViewModel)
            }

            // -------------------- Grilles : création / édition --------------------
            composableWithTransitions(Routes.CREATE_GRID) {
                GridEditorScreen(
                    repository = repository,
                    gridId = null,
                    initialColumns = DEFAULT_GRID_COLUMNS,
                    scannedNamesToAppend = pendingScannedNames,
                    onScannedNamesConsumed = { pendingScannedNames = emptyList() },
                    scannedColumnsToApply = pendingScannedColumns,
                    onScannedColumnsConsumed = { pendingScannedColumns = null },
                    onScanClassList = { navController.navigate(Routes.GRID_SCAN) },
                    onScanFullGrid = { navController.navigate(Routes.GRID_STRUCTURE_SCAN) },
                    onSave = { name, subject, className, professorName, gridDate, term, columnRows, studentRows ->
                        scope.launch {
                            val id = repository.createGrid(
                                name, subject, columnRows, studentRows.map { it.name },
                                className, professorName, gridDate, term
                            )
                            // Applique les notes déjà écrites détectées sur le papier (si la
                            // grille vient d'un scan complet). Correspondance par NOM (pas par
                            // position), pour rester correcte même si le prof a modifié une
                            // ligne avant d'enregistrer ; une note dont l'élève/la colonne ne
                            // correspond plus exactement est simplement ignorée, sans risque
                            // de l'attribuer à la mauvaise personne.
                            if (pendingParsedScores.isNotEmpty()) {
                                val insertedStudents = repository.getStudentsOnce(id)
                                val insertedColumns = repository.getColumnsOnce(id)
                                pendingParsedScores.forEach { (studentName, columnName, value) ->
                                    val student = insertedStudents.find { it.name.equals(studentName, ignoreCase = true) }
                                    val column = insertedColumns.find { it.name.equals(columnName, ignoreCase = true) }
                                    if (student != null && column != null) {
                                        repository.setScore(student.id, column.id, value, confirmedByUser = false)
                                    }
                                }
                                pendingParsedScores = emptyList()
                            }
                            navController.popBackStack(Routes.HOME, inclusive = false)
                            navController.navigate(Routes.detail(id))
                        }
                    },
                    onCancel = { navController.popBackStack() }
                )
            }

            composableWithTransitions(
                Routes.EDIT_GRID,
                arguments = listOf(navArgument("gridId") { type = NavType.LongType })
            ) { backStackEntry2 ->
                val gridId = backStackEntry2.arguments?.getLong("gridId") ?: return@composableWithTransitions
                var loadedGrid by remember { mutableStateOf<GridEntity?>(null) }
                var loadedStudents by remember { mutableStateOf<List<EditableStudentRow>?>(null) }
                var loadedColumns by remember { mutableStateOf<List<EditableColumnRow>?>(null) }

                LaunchedEffect(gridId) {
                    loadedGrid = repository.getGrid(gridId)
                    loadedStudents = repository.getStudentsOnce(gridId).map { EditableStudentRow(id = it.id, name = it.name) }
                    loadedColumns = repository.getColumnsOnce(gridId).map { EditableColumnRow(id = it.id, name = it.name, maxPoints = it.maxPoints) }
                }

                val grid = loadedGrid
                val gridStudents = loadedStudents
                val gridColumns = loadedColumns
                if (grid == null || gridStudents == null || gridColumns == null) {
                    com.myschoolocr.app.ui.components.LoadingScreen()
                } else {
                    GridEditorScreen(
                        repository = repository,
                        gridId = gridId,
                        initialName = grid.name,
                        initialSubject = grid.subject,
                        initialClassName = grid.className,
                        initialProfessorName = grid.professorName,
                        initialGridDate = grid.gridDate,
                        initialTerm = grid.term,
                        initialColumns = gridColumns,
                        initialStudents = gridStudents,
                        scannedNamesToAppend = pendingScannedNames,
                        onScannedNamesConsumed = { pendingScannedNames = emptyList() },
                        onScanClassList = { navController.navigate(Routes.GRID_SCAN) },
                        onScanFullGrid = { navController.navigate(Routes.GRID_STRUCTURE_SCAN) },
                        onSave = { newName, newSubject, newClassName, newProfessorName, newGridDate, newTerm, columnRows, studentRows ->
                            scope.launch {
                                repository.updateGridWithStudentsAndColumns(
                                    grid.copy(
                                        name = newName, subject = newSubject,
                                        className = newClassName, professorName = newProfessorName,
                                        gridDate = newGridDate, term = newTerm
                                    ),
                                    studentRows,
                                    columnRows
                                )
                                navController.popBackStack()
                            }
                        },
                        onCancel = { navController.popBackStack() }
                    )
                }
            }

            composableWithTransitions(Routes.GRID_SCAN) {
                GridScanScreen(
                    ocrLanguage = settings.ocrLanguage,
                    onConfirmed = { names ->
                        pendingScannedNames = names
                        navController.popBackStack()
                    },
                    onCancel = { navController.popBackStack() }
                )
            }

            composableWithTransitions(Routes.GRID_STRUCTURE_SCAN) {
                GridStructureScanScreen(
                    ocrLanguage = settings.ocrLanguage,
                    onParsed = { columns, names, scores ->
                        pendingScannedColumns = columns
                        pendingScannedNames = names
                        pendingParsedScores = scores
                        navController.popBackStack()
                    },
                    onCancel = { navController.popBackStack() }
                )
            }

            // -------------------- Détail d'une grille (MVVM) --------------------
            composableWithTransitions(
                Routes.DETAIL,
                arguments = listOf(navArgument("gridId") { type = NavType.LongType })
            ) { backStackEntry2 ->
                val gridId = backStackEntry2.arguments?.getLong("gridId") ?: return@composableWithTransitions
                val detailViewModel: GridDetailViewModel = viewModel(
                    factory = GenericViewModelFactory { GridDetailViewModel(repository, gridId) }
                )
                GridDetailScreen(
                    viewModel = detailViewModel,
                    onEditGrid = { navController.navigate(Routes.editGrid(gridId)) },
                    onScanCopies = { navController.navigate(Routes.copyScan(gridId)) },
                    onOpenVerification = { navController.navigate(Routes.verification(gridId)) },
                    onGridDeleted = { navController.popBackStack(Routes.HOME, inclusive = false) },
                    onBack = { navController.popBackStack() }
                )
            }

            composableWithTransitions(
                Routes.COPY_SCAN,
                arguments = listOf(navArgument("gridId") { type = NavType.LongType })
            ) { backStackEntry2 ->
                val gridId = backStackEntry2.arguments?.getLong("gridId") ?: return@composableWithTransitions
                CopyScanScreen(
                    repository = repository,
                    gridId = gridId,
                    ocrLanguage = settings.ocrLanguage,
                    nameConfidenceThreshold = settings.nameConfidenceThreshold.toDouble(),
                    onOpenVerification = { navController.navigate(Routes.verification(gridId)) },
                    onFinished = { navController.popBackStack() }
                )
            }

            composableWithTransitions(
                Routes.VERIFICATION,
                arguments = listOf(navArgument("gridId") { type = NavType.LongType })
            ) { backStackEntry2 ->
                val gridId = backStackEntry2.arguments?.getLong("gridId") ?: return@composableWithTransitions
                val verificationViewModel: VerificationViewModel = viewModel(
                    factory = GenericViewModelFactory { VerificationViewModel(repository, gridId) }
                )
                VerificationScreen(
                    viewModel = verificationViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
