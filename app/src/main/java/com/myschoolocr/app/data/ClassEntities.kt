package com.myschoolocr.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Une classe réutilisable (ex: "6ème A") : une liste d'élèves qu'on peut réutiliser
 * pour créer plusieurs grilles de cotation sans retaper/rescanner à chaque fois.
 * Indépendante des grilles : modifier une classe ne modifie pas les grilles déjà créées.
 */
@Entity(tableName = "classes")
data class ClassEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "class_students",
    foreignKeys = [ForeignKey(
        entity = ClassEntity::class,
        parentColumns = ["id"],
        childColumns = ["classId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class ClassStudentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val classId: Long,
    val name: String
)
