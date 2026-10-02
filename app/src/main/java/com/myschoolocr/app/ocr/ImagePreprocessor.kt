package com.myschoolocr.app.ocr

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Prétraitement d'image via OpenCV, exécuté avant l'OCR pour améliorer la précision :
 * - redressement (deskew) d'une photo prise de travers, en détectant l'angle dominant
 *   des lignes du tableau/texte (Hough Line Transform) ;
 * - détection d'encre de couleur via l'espace HSV (plus robuste que l'analyse pixel par
 *   pixel en Kotlin pur qui servait avant).
 *
 * Si OpenCV échoue à s'initialiser sur l'appareil (rare, mais possible selon l'ABI),
 * l'app continue de fonctionner sans prétraitement plutôt que de planter : [isAvailable]
 * doit être vérifié, et chaque fonction retourne une valeur de repli sûre sinon.
 */
object ImagePreprocessor {

    @Volatile var isAvailable = false
        private set

    /** À appeler une fois au démarrage de l'app (voir MyApp.onCreate). */
    fun init(): Boolean {
        isAvailable = try {
            @Suppress("DEPRECATION")
            OpenCVLoader.initDebug()
        } catch (e: Throwable) {
            false
        }
        return isAvailable
    }

    /**
     * Redresse une photo légèrement de travers. Conserve la couleur d'origine (nécessaire
     * pour la détection d'encre de couleur ensuite) — contrairement à [enhanceForOcr].
     * Retourne le bitmap d'origine tel quel si OpenCV n'est pas disponible ou si l'image
     * est déjà droite.
     */
    fun deskew(bitmap: Bitmap): Bitmap {
        if (!isAvailable) return bitmap
        val src = Mat()
        val gray = Mat()
        val edges = Mat()
        val lines = Mat()
        return try {
            Utils.bitmapToMat(bitmap, src)
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.Canny(gray, edges, 50.0, 150.0)
            Imgproc.HoughLinesP(edges, lines, 1.0, Math.PI / 180, 100, src.width() / 4.0, 20.0)

            val angles = mutableListOf<Double>()
            for (i in 0 until lines.rows()) {
                val l = lines.get(i, 0) ?: continue
                val dx = l[2] - l[0]
                val dy = l[3] - l[1]
                val angleDeg = Math.toDegrees(atan2(dy, dx))
                // Uniquement les lignes quasi horizontales (tableau/texte), pas les traits
                // verticaux qui fausseraient l'angle de redressement.
                if (abs(angleDeg) < 20.0) angles.add(angleDeg)
            }

            if (angles.isEmpty()) return bitmap
            val medianAngle = angles.sorted()[angles.size / 2]
            if (abs(medianAngle) < 0.5) return bitmap // déjà droit

            val center = Point(src.width() / 2.0, src.height() / 2.0)
            val rotationMatrix = Imgproc.getRotationMatrix2D(center, medianAngle, 1.0)
            val rotated = Mat()
            try {
                Imgproc.warpAffine(src, rotated, rotationMatrix, src.size(), Imgproc.INTER_LINEAR, Core.BORDER_REPLICATE)
                val output = Bitmap.createBitmap(rotated.cols(), rotated.rows(), Bitmap.Config.ARGB_8888)
                Utils.matToBitmap(rotated, output)
                output
            } finally {
                rotationMatrix.release()
                rotated.release()
            }
        } catch (e: Exception) {
            bitmap
        } finally {
            src.release(); gray.release(); edges.release(); lines.release()
        }
    }

    /**
     * Niveaux de gris + seuillage adaptatif : améliore la lisibilité du texte pour Tesseract
     * sous un éclairage inégal (ombre du téléphone, luminosité de la pièce). À utiliser
     * UNIQUEMENT pour l'entrée de l'OCR — l'information de couleur est perdue ici, ne jamais
     * s'en servir pour la détection d'encre de couleur (utiliser le bitmap de [deskew]).
     */
    fun enhanceForOcr(bitmap: Bitmap): Bitmap {
        if (!isAvailable) return bitmap
        val src = Mat()
        val gray = Mat()
        val thresholded = Mat()
        return try {
            Utils.bitmapToMat(bitmap, src)
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.adaptiveThreshold(
                gray, thresholded, 255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY,
                31, 15.0
            )
            val output = Bitmap.createBitmap(thresholded.cols(), thresholded.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(thresholded, output)
            output
        } catch (e: Exception) {
            bitmap
        } finally {
            src.release(); gray.release(); thresholded.release()
        }
    }

    /**
     * Proportion de pixels "encre de couleur" (rouge, bleu, vert...) dans une zone du bitmap,
     * via l'espace HSV (canal de saturation). Retourne null si OpenCV n'est pas disponible,
     * pour que l'appelant puisse basculer sur une méthode de repli.
     */
    fun coloredInkRatio(bitmap: Bitmap, left: Int, top: Int, width: Int, height: Int): Double? {
        if (!isAvailable || width <= 0 || height <= 0) return null
        val src = Mat()
        return try {
            Utils.bitmapToMat(bitmap, src)
            val safeLeft = left.coerceIn(0, src.cols() - 1)
            val safeTop = top.coerceIn(0, src.rows() - 1)
            val safeWidth = width.coerceAtMost(src.cols() - safeLeft).coerceAtLeast(1)
            val safeHeight = height.coerceAtMost(src.rows() - safeTop).coerceAtLeast(1)

            val roi = Mat(src, org.opencv.core.Rect(safeLeft, safeTop, safeWidth, safeHeight))
            val hsv = Mat()
            val mask = Mat()
            try {
                Imgproc.cvtColor(roi, hsv, Imgproc.COLOR_RGBA2RGB)
                Imgproc.cvtColor(hsv, hsv, Imgproc.COLOR_RGB2HSV)
                // Pixel "coloré" : saturation nette, ni trop sombre ni trop clair (exclut
                // le texte noir imprimé et le fond blanc de la page).
                Core.inRange(hsv, Scalar(0.0, 80.0, 60.0), Scalar(180.0, 255.0, 250.0), mask)
                val coloredPixels = Core.countNonZero(mask)
                val totalPixels = safeWidth * safeHeight
                if (totalPixels == 0) 0.0 else coloredPixels.toDouble() / totalPixels
            } finally {
                roi.release(); hsv.release(); mask.release()
            }
        } catch (e: Exception) {
            null
        } finally {
            src.release()
        }
    }
}
