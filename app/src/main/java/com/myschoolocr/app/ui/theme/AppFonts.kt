package com.myschoolocr.app.ui.theme

import android.content.Context
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * Charge Inter et Poppins depuis les assets (téléchargées par le workflow GitHub Actions,
 * voir android-build.yml). Chaque fichier est vérifié avant utilisation : si un seul
 * manque (téléchargement raté, build local sans avoir lancé l'étape), on retombe
 * silencieusement sur FontFamily.Default (Roboto, police système) plutôt que de planter.
 * Roboto n'a besoin d'aucun fichier : c'est déjà la police par défaut sur Android.
 */
object AppFonts {

    private fun assetExists(context: Context, path: String): Boolean =
        try {
            context.assets.open(path).close()
            true
        } catch (e: Exception) {
            false
        }

    fun interFamily(context: Context): FontFamily {
        val required = listOf(
            "fonts/inter_regular.ttf" to FontWeight.Normal,
            "fonts/inter_medium.ttf" to FontWeight.Medium,
            "fonts/inter_semibold.ttf" to FontWeight.SemiBold,
            "fonts/inter_bold.ttf" to FontWeight.Bold
        )
        if (required.any { (path, _) -> !assetExists(context, path) }) return FontFamily.Default
        return try {
            FontFamily(required.map { (path, weight) -> Font(path, context.assets, weight) })
        } catch (e: Exception) {
            FontFamily.Default
        }
    }

    fun poppinsFamily(context: Context): FontFamily {
        val required = listOf(
            "fonts/poppins_medium.ttf" to FontWeight.Medium,
            "fonts/poppins_semibold.ttf" to FontWeight.SemiBold,
            "fonts/poppins_bold.ttf" to FontWeight.Bold
        )
        if (required.any { (path, _) -> !assetExists(context, path) }) return FontFamily.Default
        return try {
            FontFamily(required.map { (path, weight) -> Font(path, context.assets, weight) })
        } catch (e: Exception) {
            FontFamily.Default
        }
    }
}
