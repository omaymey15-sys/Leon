package com.myschoolocr.app.ocr

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/** Positions (en pixels) des lignes horizontales et verticales détectées du quadrillage. */
data class DetectedGridLines(val rowPositions: List<Int>, val columnPositions: List<Int>) {
    val rowCount: Int get() = (rowPositions.size - 1).coerceAtLeast(0)
    val columnCount: Int get() = (columnPositions.size - 1).coerceAtLeast(0)
}

/**
 * Détecte le vrai quadrillage d'un tableau papier via OpenCV (érosion/dilatation
 * directionnelle pour isoler les lignes horizontales puis verticales, ensuite localisées
 * par profil de projection). Contrairement à GridTableParser (qui devine la structure à
 * partir de la position du texte), ceci lit les traits du tableau eux-mêmes : compte
 * réellement le nombre de lignes/colonnes et permet de découper chaque cellule pour l'OCR.
 *
 * Retourne null si OpenCV n'est pas disponible ou si aucun quadrillage exploitable n'a
 * été trouvé (photo sans lignes visibles, tableau trop incliné malgré le redressement...).
 * L'appelant doit alors se rabattre sur l'heuristique de GridTableParser.
 */
object TableGridDetector {

    fun detect(bitmap: Bitmap): DetectedGridLines? {
        if (!ImagePreprocessor.isAvailable) return null

        val src = Mat()
        val gray = Mat()
        val binary = Mat()
        val horizontal = Mat()
        val vertical = Mat()
        return try {
            Utils.bitmapToMat(bitmap, src)
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
            // Seuillage inversé : les traits du tableau (sombres) deviennent blancs (255),
            // le fond (clair) devient noir — nécessaire pour que l'érosion/dilatation
            // isole bien les lignes plutôt que le fond.
            Imgproc.adaptiveThreshold(
                gray, binary, 255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV,
                15, 10.0
            )

            val horizontalKernelSize = (src.width() / 30).coerceAtLeast(15)
            val horizontalKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(horizontalKernelSize.toDouble(), 1.0))
            Imgproc.erode(binary, horizontal, horizontalKernel)
            Imgproc.dilate(horizontal, horizontal, horizontalKernel)

            val verticalKernelSize = (src.height() / 30).coerceAtLeast(15)
            val verticalKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(1.0, verticalKernelSize.toDouble()))
            Imgproc.erode(binary, vertical, verticalKernel)
            Imgproc.dilate(vertical, vertical, verticalKernel)

            val rowPositions = findLinePositions(horizontal, reduceDimension = 1)
            val columnPositions = findLinePositions(vertical, reduceDimension = 0)

            if (rowPositions.size < 2 || columnPositions.size < 2) null
            else DetectedGridLines(rowPositions, columnPositions)
        } catch (e: Exception) {
            null
        } finally {
            src.release(); gray.release(); binary.release(); horizontal.release(); vertical.release()
        }
    }

    /**
     * Réduit le masque de lignes à un profil 1D (somme par ligne ou par colonne selon
     * [reduceDimension] : 1 = somme de chaque ligne → position des lignes horizontales,
     * 0 = somme de chaque colonne → position des lignes verticales), puis repère les
     * segments dont l'intensité dépasse un tiers du maximum comme étant une vraie ligne
     * du quadrillage (fusionne les pixels adjacents d'une même ligne épaisse en une seule
     * position, son centre).
     */
    private fun findLinePositions(lineMask: Mat, reduceDimension: Int): List<Int> {
        val profile = Mat()
        Core.reduce(lineMask, profile, reduceDimension, Core.REDUCE_SUM, CvType.CV_32S)
        val length = if (reduceDimension == 1) profile.rows() else profile.cols()
        val values = IntArray(length)
        val buffer = IntArray(1)
        for (i in 0 until length) {
            if (reduceDimension == 1) profile.get(i, 0, buffer) else profile.get(0, i, buffer)
            values[i] = buffer[0]
        }
        profile.release()

        val maxValue = values.maxOrNull() ?: 0
        if (maxValue <= 0) return emptyList()
        val threshold = maxValue / 3

        val positions = mutableListOf<Int>()
        var i = 0
        while (i < values.size) {
            if (values[i] > threshold) {
                val start = i
                while (i < values.size && values[i] > threshold) i++
                positions.add((start + i - 1) / 2)
            } else {
                i++
            }
        }
        return positions
    }
}
