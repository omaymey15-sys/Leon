package com.myschoolocr.app.data

/**
 * Ligne éditable représentant une colonne de notation dans l'écran d'édition d'une grille.
 * - id == null → nouvelle colonne, sera insérée à l'enregistrement.
 * - id != null → colonne existante ; renommer/changer le barème garde son historique de notes.
 */
data class EditableColumnRow(
    val id: Long? = null,
    val name: String,
    val maxPoints: Double,
    val formula: String? = null
)

/** Colonnes par défaut proposées à la création d'une nouvelle grille, calquées sur le modèle papier. */
val DEFAULT_GRID_COLUMNS = listOf(
    EditableColumnRow(name = "Interrogation", maxPoints = 20.0),
    EditableColumnRow(name = "Devoir", maxPoints = 20.0),
    EditableColumnRow(name = "Examen", maxPoints = 60.0)
)
