package com.myschoolocr.app.data

/**
 * Représente une ligne "élève" dans l'écran d'édition d'une grille.
 * - id == null  → nouvel élève, pas encore en base (sera inséré à l'enregistrement).
 * - id != null  → élève existant, on garde son historique (note) tant que le nom
 *                 n'est pas retiré de la liste ; renommer ne fait pas perdre la note.
 */
data class EditableStudentRow(
    val id: Long? = null,
    val name: String
)
