package com.myschoolocr.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Une grille de cotation = un examen / une évaluation pour une classe donnée.
 * Le barème global n'est plus un simple nombre : il résulte de la somme des colonnes
 * de notation (voir GridColumnEntity), comme sur le document papier de référence
 * (Interrogation /20 + Devoir /20 + Examen /60 = Total /100).
 */
@Entity(tableName = "grids")
data class GridEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,               // ex: "Maths 6ème A - Interro 3"
    val subject: String = "",       // matière
    val className: String = "",     // ex: "1ère"
    val professorName: String = "", // ex: "M. Dupont"
    val gridDate: String = "",      // ex: "16/09/2025" (texte libre, pas de logique de date)
    val term: String = "",          // ex: "1er trimestre"
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Un élève rattaché à une grille. Ses notes par colonne sont dans StudentScoreEntity.
 */
@Entity(
    tableName = "students",
    foreignKeys = [ForeignKey(
        entity = GridEntity::class,
        parentColumns = ["id"],
        childColumns = ["gridId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class StudentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gridId: Long,
    val name: String
)
