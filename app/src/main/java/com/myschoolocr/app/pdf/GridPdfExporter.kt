package com.myschoolocr.app.pdf

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.myschoolocr.app.data.GradeMentions
import com.myschoolocr.app.data.GridColumnEntity
import com.myschoolocr.app.data.GridEntity
import com.myschoolocr.app.data.GridTableRow
import java.io.File
import java.io.FileOutputStream

/**
 * Génère un PDF de la grille de cotation, dans le même esprit visuel que le document
 * papier de référence : en-tête (Classe/Matière/Professeur/Date/Trimestre), tableau
 * numéroté avec pastilles de couleur pour la Mention, légende, ligne de signature.
 * Utilise l'API PdfDocument native d'Android (aucune dépendance externe, fonctionne
 * hors-ligne). À appeler depuis un thread d'arrière-plan (écriture de fichier).
 */
object GridPdfExporter {

    // A4 paysage en points (72 dpi) : plus large, mieux adapté à un tableau qu'un portrait.
    private const val PAGE_WIDTH = 842
    private const val PAGE_HEIGHT = 595
    private const val MARGIN = 32f

    fun export(
        context: Context,
        grid: GridEntity,
        columns: List<GridColumnEntity>,
        inputColumns: List<GridColumnEntity>,
        rows: List<GridTableRow>
    ): File {
        val document = PdfDocument()
        val totalMaxPoints = inputColumns.sumOf { it.maxPoints }

        val titlePaint = Paint().apply { color = Color.BLACK; textSize = 18f; isFakeBoldText = true; isAntiAlias = true }
        val labelPaint = Paint().apply { color = Color.rgb(68, 70, 84); textSize = 9f; isAntiAlias = true }
        val valuePaint = Paint().apply { color = Color.BLACK; textSize = 11f; isAntiAlias = true }
        val headerCellPaint = Paint().apply { color = Color.WHITE; textSize = 10f; isFakeBoldText = true; isAntiAlias = true }
        val cellPaint = Paint().apply { color = Color.BLACK; textSize = 10f; isAntiAlias = true }
        val letterPaint = Paint().apply { color = Color.rgb(46, 90, 172); textSize = 11f; isFakeBoldText = true; isAntiAlias = true }
        val linePaint = Paint().apply { color = Color.rgb(220, 220, 224); strokeWidth = 1f }
        val headerBgPaint = Paint().apply { color = Color.rgb(46, 90, 172) }
        val legendPaint = Paint().apply { color = Color.rgb(68, 70, 84); textSize = 9f; isAntiAlias = true }

        val nameColWidth = 160f
        val numColWidth = 36f
        val mentionColWidth = 70f
        val totalColWidth = 70f
        val fixedWidth = numColWidth + nameColWidth + mentionColWidth + totalColWidth
        val availableForGrades = (PAGE_WIDTH - 2 * MARGIN - fixedWidth).coerceAtLeast(60f)
        val gradeColWidth = if (columns.isNotEmpty()) availableForGrades / columns.size else availableForGrades

        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas
        var y = MARGIN

        fun newPage() {
            document.finishPage(page)
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            canvas = page.canvas
            y = MARGIN
        }

        fun drawTableHeader() {
            val headerHeight = 30f
            canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + headerHeight, headerBgPaint)
            var x = MARGIN
            fun cell(text: String, width: Float) {
                canvas.drawText(text, x + 6f, y + headerHeight / 2 + 4f, headerCellPaint)
                x += width
            }
            cell("N°", numColWidth)
            cell("Nom", nameColWidth)
            columns.forEach { col -> cell("${col.name} /${col.maxPoints.toInt()}", gradeColWidth) }
            cell("Total /${totalMaxPoints.toInt()}", totalColWidth)
            cell("Mention", mentionColWidth)
            y += headerHeight
        }

        // Titre
        canvas.drawText(grid.name, MARGIN, y + 18f, titlePaint)
        y += 30f

        // En-tête d'informations (Classe / Matière / Professeur / Date / Trimestre)
        val infoPairs = listOf(
            "Classe" to grid.className, "Matière" to grid.subject,
            "Professeur" to grid.professorName, "Date" to grid.gridDate, "Trimestre" to grid.term
        ).filter { it.second.isNotBlank() }
        if (infoPairs.isNotEmpty()) {
            val boxHeight = 24f * ((infoPairs.size + 1) / 2)
            canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + boxHeight, linePaint)
            infoPairs.forEachIndexed { index, (label, value) ->
                val col = index % 2
                val rowIdx = index / 2
                val x = MARGIN + 12f + col * ((PAGE_WIDTH - 2 * MARGIN) / 2)
                val infoY = y + 16f + rowIdx * 24f
                canvas.drawText("$label :", x, infoY, labelPaint)
                canvas.drawText(value, x + 70f, infoY, valuePaint)
            }
            y += boxHeight + 16f
        } else {
            y += 8f
        }

        // Lettres de colonnes (A, B, C...) au-dessus du tableau
        run {
            var x = MARGIN + numColWidth + nameColWidth
            columns.forEachIndexed { index, _ ->
                canvas.drawText(('A' + index).toString(), x + 6f, y + 10f, letterPaint)
                x += gradeColWidth
            }
            y += 16f
        }

        drawTableHeader()

        rows.forEachIndexed { rowIndex, row ->
            if (y > PAGE_HEIGHT - MARGIN - 60f) {
                newPage()
                drawTableHeader()
            }
            val rowHeight = 22f
            var x = MARGIN
            fun cell(text: String, width: Float) {
                canvas.drawText(text, x + 6f, y + rowHeight / 2 + 4f, cellPaint)
                x += width
            }
            cell((rowIndex + 1).toString(), numColWidth)
            cell(row.student.name, nameColWidth)
            columns.forEach { col ->
                val score = row.scoresByColumnId[col.id]
                cell(score?.value?.let { "%.1f".format(it) } ?: "—", gradeColWidth)
            }
            val total = row.totalObtained(inputColumns)
            val percent = if (totalMaxPoints > 0.0) (total / totalMaxPoints) * 100.0 else 0.0
            val mentionCode = if (row.isComplete(inputColumns)) GradeMentions.mentionCode(percent) else null
            cell(if (row.hasAnyScore()) "%.1f".format(total) else "—", totalColWidth)

            if (mentionCode != null) {
                val pillColor = when (mentionCode) {
                    "TB" -> Color.rgb(46, 125, 50)
                    "B" -> Color.rgb(46, 90, 172)
                    "AB" -> Color.rgb(103, 80, 164)
                    "P" -> Color.rgb(180, 83, 9)
                    else -> Color.rgb(186, 26, 26)
                }
                val pillPaint = Paint().apply { color = pillColor; isAntiAlias = true }
                val pillRect = RectF(x + 6f, y + 3f, x + 6f + 34f, y + rowHeight - 3f)
                canvas.drawRoundRect(pillRect, 10f, 10f, pillPaint)
                val pillTextPaint = Paint().apply {
                    color = Color.WHITE; textSize = 9f; isFakeBoldText = true
                    isAntiAlias = true; textAlign = Paint.Align.CENTER
                }
                canvas.drawText(mentionCode, pillRect.centerX(), pillRect.centerY() + 3f, pillTextPaint)
            } else {
                canvas.drawText("—", x + 6f, y + rowHeight / 2 + 4f, cellPaint)
            }

            canvas.drawLine(MARGIN, y + rowHeight, PAGE_WIDTH - MARGIN, y + rowHeight, linePaint)
            y += rowHeight
        }

        // Légende
        y += 16f
        if (y > PAGE_HEIGHT - MARGIN - 40f) newPage()
        canvas.drawText(
            "Barème des mentions : TB = Très Bien (≥80%) · B = Bien (60-79%) · AB = Assez Bien (50-59%) · " +
                "P = Passable (40-49%) · I = Insuffisant (<40%)",
            MARGIN, y, legendPaint
        )
        y += 24f

        // Ligne de signature
        canvas.drawText("Signature du professeur :", MARGIN, y, labelPaint)
        canvas.drawLine(MARGIN + 130f, y, MARGIN + 320f, y, linePaint)

        document.finishPage(page)

        val safeName = grid.name.ifBlank { "grille" }.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = File(context.cacheDir, "$safeName.pdf")
        FileOutputStream(file).use { out -> document.writeTo(out) }
        document.close()
        return file
    }
}
