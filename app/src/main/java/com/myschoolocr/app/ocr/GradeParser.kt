package com.myschoolocr.app.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import com.myschoolocr.app.data.StudentEntity
import kotlin.math.max
import kotlin.math.min

data class NameMatch(val student: StudentEntity, val score: Double, val matchedText: String)
data class GradeCandidate(val value: Double, val rawText: String, val score: Double)

/**
 * Le format des copies varie, donc on ne devine jamais "en aveugle" :
 * on combine plusieurs indices (texte, position, couleur, taille) pour classer
 * les candidats par probabilité, et seul un score suffisant déclenche une
 * pré-sélection automatique. Le prof valide/corrige toujours avant enregistrement.
 */
object GradeParser {

    /** Seuil demandé : en dessous, on ne pré-remplit pas automatiquement le nom. */
    const val NAME_CONFIDENCE_THRESHOLD = 0.75

    private val gradeRegexes = listOf(
        Regex("""(\d{1,2}[.,]\d)\s*/\s*20"""),
        Regex("""(\d{1,2})\s*/\s*20"""),
        Regex("""note\s*[:\-]?\s*(\d{1,2}[.,]?\d?)""", RegexOption.IGNORE_CASE),
        Regex("""total\s*[:\-]?\s*(\d{1,2}[.,]?\d?)""", RegexOption.IGNORE_CASE),
        Regex("""cote\s*[:\-]?\s*(\d{1,2}[.,]?\d?)""", RegexOption.IGNORE_CASE),
        Regex("""^(\d{1,2}[.,]?\d?)$""")             // nombre isolé (ex: juste "14" écrit en gros au stylo de correction)
    )

    // -------------------- NOTE (grande taille + encre de couleur) --------------------

    /**
     * Cherche les notes plausibles. Une ligne écrite à l'encre de couleur (rouge, bleu, vert...)
     * et/ou dont la police est nettement plus grande que la moyenne du texte de la copie
     * obtient un score plus élevé, car c'est typiquement ainsi qu'un prof note une copie
     * (peu importe la couleur du stylo utilisé).
     */
    fun findGradeCandidates(lines: List<OcrLine>, maxNote: Double, bitmap: Bitmap?): List<GradeCandidate> {
        if (lines.isEmpty()) return emptyList()
        val medianHeight = lines.map { it.boundingBox.height() }.sorted().let { it[it.size / 2] }.coerceAtLeast(1)

        val candidates = mutableListOf<GradeCandidate>()
        for (line in lines) {
            for ((index, regex) in gradeRegexes.withIndex()) {
                val match = regex.find(line.text.trim()) ?: continue
                val raw = match.groupValues[1].replace(',', '.')
                val value = raw.toDoubleOrNull() ?: continue
                if (value !in 0.0..maxNote) continue

                // Score de base : plus fort pour les patterns explicites ("/20", "note:"...)
                // que pour un simple nombre isolé (dernier regex de la liste = le moins fiable seul).
                var score = 1.0 - (index.toDouble() / gradeRegexes.size) * 0.5

                val heightRatio = line.boundingBox.height().toDouble() / medianHeight
                if (heightRatio >= 1.3) score += 0.25 // écrit "en grand"

                val coloredRatio = bitmap?.let {
                    ImagePreprocessor.coloredInkRatio(it, line.boundingBox.left, line.boundingBox.top, line.boundingBox.width(), line.boundingBox.height())
                        ?: coloredInkRatioFallback(it, line.boundingBox) // OpenCV indisponible : repli Kotlin pur
                } ?: 0.0
                if (coloredRatio >= 0.12) score += 0.35 // encre de couleur détectée (rouge, bleu, vert...)

                candidates.add(GradeCandidate(value, line.text.trim(), score.coerceAtMost(1.0)))
            }
        }
        return candidates
            .distinctBy { it.value to it.rawText }
            .sortedByDescending { it.score }
    }

    // -------------------- NOM (majuscules + haut de copie) --------------------

    /**
     * Cherche le nom d'élève parmi la liste connue de la grille, en combinant :
     * - la ressemblance textuelle (tolérante aux fautes d'OCR),
     * - un bonus si la ligne est en haut de la copie (souvent où le nom est écrit),
     * - un bonus si la ligne est entièrement en majuscules (souvent le cas des noms).
     */
    fun findNameMatches(lines: List<OcrLine>, students: List<StudentEntity>, imageHeight: Int): List<NameMatch> {
        if (lines.isEmpty() || students.isEmpty()) return emptyList()
        val results = mutableListOf<NameMatch>()

        for (line in lines) {
            val cleaned = normalize(line.text)
            if (cleaned.length < 3) continue

            val isTopOfPage = imageHeight > 0 && line.boundingBox.top < imageHeight * 0.35
            val isMostlyUpperCase = isMostlyUpperCase(line.text)

            for (student in students) {
                val textScore = similarity(cleaned, normalize(student.name))
                if (textScore < 0.4) continue // trop différent, pas la peine de considérer

                var score = textScore
                if (isTopOfPage) score += 0.05
                if (isMostlyUpperCase) score += 0.05
                score = score.coerceAtMost(1.0)

                results.add(NameMatch(student, score, line.text.trim()))
            }
        }
        return results.sortedByDescending { it.score }
    }

    /** Résultat de la meilleure devinette pour une copie scannée. */
    data class NameGuessResult(
        val student: StudentEntity?,           // non-null seulement si un seul élève dépasse le seuil
        val topScore: Double,                  // meilleur score trouvé, même si aucun élève n'est retenu
        val isAmbiguous: Boolean,              // true si plusieurs élèves dépassent le seuil à la fois
        val suggestedStudent: StudentEntity?   // meilleure ressemblance trouvée, même sous le seuil (aide à la vérification manuelle)
    )

    /**
     * Meilleure devinette à proposer au prof.
     * - Le nom n'est retourné que si UN SEUL élève de la grille dépasse [NAME_CONFIDENCE_THRESHOLD] (75%).
     *   Si aucun élève ne l'atteint, ou si plusieurs l'atteignent en même temps (ambiguïté), on préfère
     *   laisser le prof choisir manuellement plutôt que de risquer une fausse correspondance silencieuse.
     * - La note retournée est toujours la meilleure candidate trouvée, à confirmer.
     */
    fun bestGuess(
        lines: List<OcrLine>,
        students: List<StudentEntity>,
        maxNote: Double,
        bitmap: Bitmap?,
        imageHeight: Int,
        confidenceThreshold: Double = NAME_CONFIDENCE_THRESHOLD
    ): Pair<NameGuessResult, GradeCandidate?> {
        val allMatches = findNameMatches(lines, students, imageHeight)
        // Une même ligne peut matcher plusieurs élèves à des scores différents ;
        // on ne garde que le meilleur score obtenu par chaque élève.
        val bestPerStudent = allMatches
            .groupBy { it.student.id }
            .map { (_, matches) -> matches.maxByOrNull { it.score }!! }
            .sortedByDescending { it.score }

        val aboveThreshold = bestPerStudent.filter { it.score >= confidenceThreshold }
        val bestOverall = bestPerStudent.firstOrNull()
        val nameResult = when {
            aboveThreshold.size == 1 -> NameGuessResult(
                aboveThreshold.first().student, aboveThreshold.first().score, isAmbiguous = false,
                suggestedStudent = aboveThreshold.first().student
            )
            aboveThreshold.size > 1 -> NameGuessResult(
                null, aboveThreshold.first().score, isAmbiguous = true,
                suggestedStudent = bestOverall?.student
            )
            else -> NameGuessResult(
                null, bestOverall?.score ?: 0.0, isAmbiguous = false,
                suggestedStudent = bestOverall?.student
            )
        }

        val grade = findGradeCandidates(lines, maxNote, bitmap).firstOrNull()
        return nameResult to grade
    }

    // -------------------- Utilitaires --------------------

    private fun isMostlyUpperCase(text: String): Boolean {
        val letters = text.filter { it.isLetter() }
        if (letters.length < 2) return false
        return letters.count { it.isUpperCase() }.toDouble() / letters.length >= 0.8
    }

    private fun normalize(s: String): String =
        s.lowercase()
            .replace(Regex("[^a-zà-ÿ ]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun similarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val dist = levenshtein(a, b)
        val maxLen = max(a.length, b.length)
        return 1.0 - (dist.toDouble() / maxLen)
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = min(min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost)
            }
        }
        return dp[a.length][b.length]
    }

    /**
     * Repli utilisé UNIQUEMENT si OpenCV n'a pas pu s'initialiser sur l'appareil
     * (voir ImagePreprocessor.isAvailable) : même logique, en Kotlin pur, pixel par pixel.
     */
    private fun coloredInkRatioFallback(bitmap: Bitmap, box: Rect): Double {
        val left = box.left.coerceIn(0, bitmap.width - 1)
        val top = box.top.coerceIn(0, bitmap.height - 1)
        val right = box.right.coerceIn(left + 1, bitmap.width)
        val bottom = box.bottom.coerceIn(top + 1, bitmap.height)
        if (right <= left || bottom <= top) return 0.0

        val stepX = max(1, (right - left) / 20)
        val stepY = max(1, (bottom - top) / 20)
        var total = 0
        var coloredCount = 0

        var y = top
        while (y < bottom) {
            var x = left
            while (x < right) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                total++
                val maxChannel = maxOf(r, g, b)
                val minChannel = minOf(r, g, b)
                val saturationSpread = maxChannel - minChannel
                // Un pixel gris/noir/blanc a ses 3 canaux proches ; un pixel d'encre colorée
                // (rouge, bleu, vert...) a un écart net entre son canal dominant et les autres.
                // On exclut aussi les pixels quasi blancs (fond de page) et quasi noirs (ombre).
                if (saturationSpread > 45 && maxChannel > 60 && maxChannel < 250) coloredCount++
                x += stepX
            }
            y += stepY
        }
        return if (total == 0) 0.0 else coloredCount.toDouble() / total
    }
}
