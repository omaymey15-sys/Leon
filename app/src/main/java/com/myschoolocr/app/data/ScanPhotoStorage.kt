package com.myschoolocr.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.util.UUID

/**
 * Les photos ne sont écrites sur le disque QUE lorsque l'OCR échoue à attribuer
 * une copie avec confiance (voir CopyScanScreen). Une copie traitée avec succès
 * ne laisse aucune trace image sur le disque : seule la note est enregistrée.
 */
object ScanPhotoStorage {

    private fun dir(context: Context): File =
        File(context.filesDir, "pending_scans").apply { if (!exists()) mkdirs() }

    fun save(context: Context, bitmap: Bitmap): String {
        val file = File(dir(context), "${UUID.randomUUID()}.jpg")
        file.outputStream().use { out ->
            // Qualité 80 : suffisant pour relire un nom/une note, plus léger à stocker.
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }
        return file.absolutePath
    }

    fun load(path: String): Bitmap? = BitmapFactory.decodeFile(path)

    fun delete(path: String) {
        val file = File(path)
        if (file.exists()) file.delete()
    }
}
