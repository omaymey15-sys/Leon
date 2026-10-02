package com.myschoolocr.app.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Niveaux d'itération Tesseract (constantes RIL_*, valeurs officielles de l'API C++
 * Tesseract). La classe PageIteratorLevel n'existe pas dans cette version de
 * tesseract4android, donc on utilise directement ces entiers. */
private const val RIL_TEXTLINE = 2
private const val RIL_WORD = 3
// Modes de segmentation de page Tesseract (valeurs officielles de l'API C++ Tesseract) :
// AUTO = analyse de mise en page complète (par défaut) ; SINGLE_LINE = une seule ligne de
// texte, plus fiable pour lire une petite cellule de tableau isolée.
private const val PSM_AUTO = 3
private const val PSM_SINGLE_LINE = 7

/**
 * Résultat brut de l'OCR : une ligne de texte détectée avec sa position exacte
 * sur le bitmap d'origine.
 */
data class OcrLine(val text: String, val boundingBox: Rect)

/**
 * Résultat complet d'une reconnaissance : les lignes détectées, et le bitmap "aligné"
 * (redressé par OpenCV si besoin, couleur conservée) dont les coordonnées correspondent
 * exactement à [OcrLine.boundingBox] — c'est CE bitmap qu'il faut réutiliser pour analyser
 * la couleur de l'encre (voir GradeParser), pas le bitmap brut capturé par la caméra.
 */
data class OcrResult(val lines: List<OcrLine>, val alignedBitmap: Bitmap)

/** Un mot détecté avec sa position — plus fin qu'une ligne entière, nécessaire pour
 * reconstituer les colonnes d'un tableau à partir de la position horizontale du texte. */
data class OcrWord(val text: String, val boundingBox: Rect)
data class OcrWordsResult(val words: List<OcrWord>, val alignedBitmap: Bitmap)

/**
 * OCR 100% embarqué via Tesseract4Android : aucune connexion internet requise,
 * même au tout premier lancement. Le fichier de données linguistiques correspondant
 * (fra.traineddata / eng.traineddata) est copié depuis les assets vers le stockage
 * interne de l'app au premier appel pour chaque langue.
 *
 * Avant la reconnaissance, l'image est redressée (deskew) et son contraste amélioré via
 * OpenCV (voir ImagePreprocessor) — utile pour des photos prises légèrement de travers
 * ou avec un éclairage inégal. Si OpenCV n'est pas disponible, ces étapes sont ignorées
 * et l'OCR tourne directement sur le bitmap d'origine.
 */
object OcrAnalyzer {

    @Volatile private var api: TessBaseAPI? = null
    @Volatile private var currentLanguage: String? = null

    private fun getOrCreateApi(context: Context, language: String): TessBaseAPI {
        synchronized(this) {
            val existing = api
            if (existing != null && currentLanguage == language) return existing

            existing?.recycle()

            val dataDir = File(context.filesDir, "tesseract")
            val tessdataDir = File(dataDir, "tessdata")
            if (!tessdataDir.exists()) tessdataDir.mkdirs()

            val trainedData = File(tessdataDir, "$language.traineddata")
            if (!trainedData.exists()) {
                context.assets.open("tessdata/$language.traineddata").use { input ->
                    trainedData.outputStream().use { output -> input.copyTo(output) }
                }
            }

            val newApi = TessBaseAPI()
            newApi.init(dataDir.absolutePath, language)
            api = newApi
            currentLanguage = language
            return newApi
        }
    }

    /**
     * Lance la reconnaissance de texte ligne par ligne sur un bitmap capturé par la caméra.
     * [language] doit être un code Tesseract valide dont le fichier .traineddata est présent
     * dans les assets : "fra" ou "eng" pour l'instant.
     */
    suspend fun recognize(context: Context, bitmap: Bitmap, language: String = "fra"): OcrResult =
        withContext(Dispatchers.Default) {
            // Redressement (couleur conservée) : sert à la fois de base pour l'OCR et pour
            // l'analyse de couleur ultérieure, afin que les coordonnées restent cohérentes.
            val alignedBitmap = ImagePreprocessor.deskew(bitmap)
            // Version niveaux de gris + contraste, UNIQUEMENT pour l'entrée de Tesseract.
            val ocrInputBitmap = ImagePreprocessor.enhanceForOcr(alignedBitmap)

            val lines = mutableListOf<OcrLine>()
            synchronized(this@OcrAnalyzer) {
                val tess = getOrCreateApi(context.applicationContext, language)
                tess.setImage(ocrInputBitmap)
                val level = RIL_TEXTLINE
                val iterator = tess.resultIterator
                if (iterator != null) {
                    iterator.begin()
                    do {
                        val text = iterator.getUTF8Text(level)?.trim()
                        val rect = iterator.getBoundingRect(level)
                        if (!text.isNullOrEmpty()) {
                            lines.add(OcrLine(text, rect))
                        }
                    } while (iterator.next(level))
                    iterator.delete()
                }
                tess.clear()
            }
            OcrResult(lines.sortedBy { it.boundingBox.top }, alignedBitmap)
        }

    /**
     * Comme [recognize], mais au niveau du mot plutôt que de la ligne entière : nécessaire
     * pour reconstituer la structure d'un tableau (colonnes) à partir de la position
     * horizontale de chaque mot — voir GridTableParser.
     */
    suspend fun recognizeWords(context: Context, bitmap: Bitmap, language: String = "fra"): OcrWordsResult =
        withContext(Dispatchers.Default) {
            val alignedBitmap = ImagePreprocessor.deskew(bitmap)
            val ocrInputBitmap = ImagePreprocessor.enhanceForOcr(alignedBitmap)

            val words = mutableListOf<OcrWord>()
            synchronized(this@OcrAnalyzer) {
                val tess = getOrCreateApi(context.applicationContext, language)
                tess.setImage(ocrInputBitmap)
                val level = RIL_WORD
                val iterator = tess.resultIterator
                if (iterator != null) {
                    iterator.begin()
                    do {
                        val text = iterator.getUTF8Text(level)?.trim()
                        val rect = iterator.getBoundingRect(level)
                        if (!text.isNullOrEmpty()) {
                            words.add(OcrWord(text, rect))
                        }
                    } while (iterator.next(level))
                    iterator.delete()
                }
                tess.clear()
            }
            OcrWordsResult(words, alignedBitmap)
        }

    /**
     * Reconnaît le texte d'une petite portion d'image (une cellule de tableau détectée
     * par TableGridDetector), en mode "ligne unique" — plus fiable qu'une analyse de mise
     * en page complète sur un aussi petit morceau d'image. Remet le mode par défaut après
     * l'appel pour ne pas affecter les autres reconnaissances (recognize/recognizeWords).
     */
    suspend fun recognizeCellText(context: Context, cellBitmap: Bitmap, language: String = "fra"): String =
        withContext(Dispatchers.Default) {
            synchronized(this@OcrAnalyzer) {
                val tess = getOrCreateApi(context.applicationContext, language)
                tess.setPageSegMode(PSM_SINGLE_LINE)
                tess.setImage(cellBitmap)
                val text = tess.getUTF8Text()?.trim().orEmpty()
                tess.clear()
                tess.setPageSegMode(PSM_AUTO)
                text
            }
        }
}
