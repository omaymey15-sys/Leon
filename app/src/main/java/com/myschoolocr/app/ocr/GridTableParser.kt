package com.myschoolocr.app.ocr

import kotlin.math.abs

data class ParsedGridColumn(val name: String, val maxPoints: Double)

data class ParsedGridResult(
    val columns: List<ParsedGridColumn>,
    val studentNames: List<String>
) {
    val isEmpty: Boolean get() = columns.isEmpty() && studentNames.isEmpty()
}

/**
 * Reconstitue la structure d'une grille de cotation papier (colonnes de notation + noms
 * d'élèves) à partir des mots détectés par l'OCR et de leur position.
 *
 * Heuristique par regroupement en lignes (position verticale) puis en colonnes (position
 * horizontale du texte) — ne lit PAS les traits du tableau lui-même (pas de détection
 * géométrique OpenCV des lignes/cellules, volontairement laissée de côté). Fonctionne
 * raisonnablement bien sur une photo droite et nette d'un tableau simple, mais reste une
 * approximation : le résultat atterrit toujours dans l'écran d'édition de grille, où tout
 * est modifiable avant d'être enregistré.
 */
object GridTableParser {

    private val HEADER_KEYWORDS = setOf("classe", "matière", "matiere", "professeur", "date", "trimestre")
    private val NAME_KEYWORDS = setOf("nom", "élève", "eleve", "élèves", "eleves")
    private val IGNORED_COLUMN_KEYWORDS = setOf("total", "mention", "signature")
    private val ROW_NUMBER_HEADER = setOf("n", "n°", "no", "num")

    fun parse(words: List<OcrWord>): ParsedGridResult {
        if (words.isEmpty()) return ParsedGridResult(emptyList(), emptyList())

        // 1. Regrouper les mots en lignes par position verticale.
        val sortedByY = words.sortedBy { it.boundingBox.centerY() }
        val rowThreshold = (words.map { it.boundingBox.height() }.average() * 0.7).toInt().coerceAtLeast(8)
        val rows = mutableListOf<MutableList<OcrWord>>()
        for (word in sortedByY) {
            val lastRow = rows.lastOrNull()
            val lastCenterY = lastRow?.let { r -> r.sumOf { it.boundingBox.centerY() } / r.size }
            if (lastRow != null && lastCenterY != null && abs(word.boundingBox.centerY() - lastCenterY) <= rowThreshold) {
                lastRow.add(word)
            } else {
                rows.add(mutableListOf(word))
            }
        }
        val orderedRows = rows.map { row -> row.sortedBy { it.boundingBox.left } }

        // 2. Écarter les lignes d'en-tête administratif (Classe/Matière/Professeur/Date...).
        val candidateRows = orderedRows.filter { row ->
            val joined = row.joinToString(" ") { it.text }.lowercase()
            HEADER_KEYWORDS.none { joined.contains(it) }
        }
        if (candidateRows.isEmpty()) return ParsedGridResult(emptyList(), emptyList())

        // 3. La ligne d'en-tête des colonnes est la première contenant "Nom"/"Élève".
        val headerRowIndex = candidateRows.indexOfFirst { row ->
            row.any { w -> NAME_KEYWORDS.any { kw -> w.text.lowercase().contains(kw) } }
        }.let { if (it >= 0) it else 0 }

        val headerRow = candidateRows.getOrNull(headerRowIndex) ?: return ParsedGridResult(emptyList(), emptyList())
        val studentRows = candidateRows.drop(headerRowIndex + 1)
            .filter { row -> row.isNotEmpty() && row.any { w -> w.text.any { it.isLetter() } } }

        // 4. Construire les colonnes de notation à partir de l'en-tête (hors N°, Nom, Total,
        //    Mention, Signature — déjà gérés ailleurs dans l'app).
        val columns = mutableListOf<ParsedGridColumn>()
        var i = 0
        while (i < headerRow.size) {
            val word = headerRow[i]
            val lower = word.text.lowercase().trim('°', ':', '.')
            when {
                lower in ROW_NUMBER_HEADER -> i++
                NAME_KEYWORDS.any { lower.contains(it) } -> i++
                IGNORED_COLUMN_KEYWORDS.any { lower.contains(it) } -> i++
                else -> {
                    var label = word.text
                    var maxPoints = extractMaxPoints(label)
                    if (maxPoints == null && i + 1 < headerRow.size) {
                        val next = headerRow[i + 1]
                        val combinedMax = extractMaxPoints("${label} ${next.text}")
                        if (combinedMax != null) {
                            maxPoints = combinedMax
                            i++ // le mot suivant (ex: "/20") fait partie de cette même colonne
                        }
                    }
                    val cleanName = cleanColumnName(label)
                    if (cleanName.isNotBlank()) {
                        columns.add(ParsedGridColumn(name = cleanName, maxPoints = maxPoints ?: 20.0))
                    }
                    i++
                }
            }
        }

        // 5. Pour chaque ligne élève : le texte (hors numéro de ligne et notes chiffrées)
        //    devient le nom. Les notes déjà écrites sur le papier ne sont pas préremplies
        //    dans cette version — seule la structure (colonnes + élèves) est reconstituée.
        val studentNames = studentRows.mapNotNull { row ->
            val withoutRowNumber = row.filterIndexed { index, w -> !(index == 0 && w.text.all { it.isDigit() }) }
            val nameWords = withoutRowNumber.takeWhile { w -> !isLikelyScore(w.text) }
            val name = nameWords.joinToString(" ") { it.text }.trim()
            name.takeIf { it.length >= 2 }
        }

        return ParsedGridResult(
            columns = columns.ifEmpty { listOf(ParsedGridColumn(name = "Note", maxPoints = 20.0)) },
            studentNames = studentNames
        )
    }

    private fun extractMaxPoints(text: String): Double? =
        Regex("""/\s*(\d{1,3})""").find(text)?.groupValues?.get(1)?.toDoubleOrNull()

    private fun cleanColumnName(text: String): String =
        text.replace(Regex("""/\s*\d+"""), "").trim().ifBlank { "Colonne" }

    private fun isLikelyScore(text: String): Boolean =
        text.matches(Regex("""\d{1,3}([.,]\d+)?"""))

    /** Une note déjà écrite sur le papier, détectée dans une cellule du tableau. */
    data class ParsedScore(val studentName: String, val columnName: String, val value: Double)

    data class ParsedGridFromCellsResult(
        val columns: List<ParsedGridColumn>,
        val studentNames: List<String>,
        val scores: List<ParsedScore>
    )

    /**
     * Convertit une grille de cellules déjà découpées (via TableGridDetector + OCR par
     * cellule) en structure de grille : colonnes + élèves + notes déjà écrites sur le
     * papier. Bien plus fiable que [parse] car les lignes/colonnes viennent de vraies
     * cellules du tableau (traits détectés), pas d'une estimation par position de texte.
     */
    fun fromCellGrid(cells: List<List<String>>): ParsedGridFromCellsResult {
        if (cells.isEmpty()) return ParsedGridFromCellsResult(emptyList(), emptyList(), emptyList())
        val headerRow = cells.first()
        val dataRows = cells.drop(1)

        var nameColumnIndex = headerRow.indexOfFirst { cell -> NAME_KEYWORDS.any { cell.lowercase().contains(it) } }
        if (nameColumnIndex < 0) nameColumnIndex = if (headerRow.size > 1) 1 else 0 // repli : 2e colonne, souvent le nom

        val gradeColumnIndices = mutableListOf<Int>()
        val columns = mutableListOf<ParsedGridColumn>()
        headerRow.forEachIndexed { index, cellText ->
            val lower = cellText.lowercase().trim('°', ':', '.')
            val isRowNumber = lower in ROW_NUMBER_HEADER
            val isName = index == nameColumnIndex
            val isIgnored = IGNORED_COLUMN_KEYWORDS.any { lower.contains(it) }
            if (!isRowNumber && !isName && !isIgnored && cellText.isNotBlank()) {
                gradeColumnIndices.add(index)
                columns.add(ParsedGridColumn(name = cleanColumnName(cellText), maxPoints = extractMaxPoints(cellText) ?: 20.0))
            }
        }

        val studentNames = mutableListOf<String>()
        val scores = mutableListOf<ParsedScore>()
        for (row in dataRows) {
            val name = row.getOrNull(nameColumnIndex)?.trim().orEmpty()
            if (name.length < 2 || !name.any { it.isLetter() }) continue
            studentNames.add(name)
            gradeColumnIndices.forEachIndexed { colPos, cellIndex ->
                val raw = row.getOrNull(cellIndex)?.replace(',', '.')?.trim()
                val value = raw?.toDoubleOrNull()
                if (value != null) {
                    scores.add(ParsedScore(studentName = name, columnName = columns[colPos].name, value = value))
                }
            }
        }

        return ParsedGridFromCellsResult(
            columns = columns.ifEmpty { listOf(ParsedGridColumn(name = "Note", maxPoints = 20.0)) },
            studentNames = studentNames,
            scores = scores
        )
    }
}
