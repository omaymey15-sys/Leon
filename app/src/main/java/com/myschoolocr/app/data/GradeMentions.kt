package com.myschoolocr.app.data

/**
 * Barème des mentions calqué sur le document de référence :
 * TB ≥ 80% · B 60–79% · AB 50–59% · P 40–49% · I < 40%
 */
object GradeMentions {
    fun mentionCode(totalPercent: Double): String = when {
        totalPercent >= 80.0 -> "TB"
        totalPercent >= 60.0 -> "B"
        totalPercent >= 50.0 -> "AB"
        totalPercent >= 40.0 -> "P"
        else -> "I"
    }

    fun mentionLabel(code: String): String = when (code) {
        "TB" -> "Très Bien"
        "B" -> "Bien"
        "AB" -> "Assez Bien"
        "P" -> "Passable"
        else -> "Insuffisant"
    }
}
