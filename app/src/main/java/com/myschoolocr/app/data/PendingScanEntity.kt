package com.myschoolocr.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Une copie scannée que l'app n'a pas pu attribuer avec confiance (nom ambigu,
 * aucune correspondance, ou aucune note détectée). La photo est conservée sur
 * le disque (voir [ScanPhotoStorage]) uniquement pour ces cas-là, le temps que
 * le prof la résolve manuellement dans l'écran "Vérification" — dès résolue,
 * la photo est supprimée pour économiser du stockage.
 */
@Entity(
    tableName = "pending_scans",
    foreignKeys = [
        ForeignKey(entity = GridEntity::class, parentColumns = ["id"], childColumns = ["gridId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = GridColumnEntity::class, parentColumns = ["id"], childColumns = ["columnId"], onDelete = ForeignKey.CASCADE)
    ]
)
data class PendingScanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gridId: Long,
    val columnId: Long,
    val photoPath: String,
    val suggestedStudentId: Long? = null,   // meilleure ressemblance trouvée, même sous le seuil de confiance
    val suggestedNoteValue: Double? = null,
    val createdAt: Long = System.currentTimeMillis()
)
