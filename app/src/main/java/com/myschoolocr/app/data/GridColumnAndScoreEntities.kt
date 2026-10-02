package com.myschoolocr.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Une colonne de notation d'une grille (ex: "Interrogation" /20, "Devoir" /20, "Examen" /60).
 * Le Total de la grille est la somme des [maxPoints] de toutes ses colonnes.
 */
@Entity(
    tableName = "grid_columns",
    foreignKeys = [ForeignKey(
        entity = GridEntity::class,
        parentColumns = ["id"],
        childColumns = ["gridId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class GridColumnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gridId: Long,
    val name: String,       // ex: "Interrogation"
    val maxPoints: Double,  // ex: 20.0
    val position: Int = 0,  // ordre d'affichage des colonnes (détermine aussi sa lettre A/B/C...)
    val formula: String? = null // ex: "A+B-C/10" ; si non-null, colonne calculée automatiquement
)

/**
 * La note d'un élève pour une colonne donnée. Une ligne par (élève, colonne) ;
 * l'absence de ligne = pas encore noté pour cette colonne.
 */
@Entity(
    tableName = "student_scores",
    foreignKeys = [
        ForeignKey(entity = StudentEntity::class, parentColumns = ["id"], childColumns = ["studentId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = GridColumnEntity::class, parentColumns = ["id"], childColumns = ["columnId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["studentId", "columnId"], unique = true)]
)
data class StudentScoreEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: Long,
    val columnId: Long,
    val value: Double,
    val scannedAt: Long = System.currentTimeMillis(),
    val ocrUnconfirmed: Boolean = false // true si la valeur vient d'un OCR non confirmé manuellement
)
