package com.myschoolocr.app.ui.theme

import android.content.Context
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Stratégie typographique à 3 polices :
 * - Roboto (FontFamily.Default, police système Android — aucun fichier à charger) pour
 *   le corps de texte : tableaux, noms d'élèves, résultats OCR. Très lisible, optimisée
 *   Android, c'est le bon choix pour de la donnée dense.
 * - Inter pour les titres, boutons et labels d'interface : plus moderne/élégante que
 *   Roboto pour la chrome de l'appli, sans sacrifier la lisibilité.
 * - Poppins n'est PAS dans cette échelle : utilisée avec parcimonie uniquement sur 2-3
 *   éléments de marque (voir BrandTitle dans PremiumComponents.kt), jamais pour de la
 *   donnée ou du texte long — elle est moins pratique pour ça, comme recommandé.
 */
fun appTypography(context: Context): Typography {
    val interFamily = AppFonts.interFamily(context)
    val robotoFamily = FontFamily.Default // Roboto : police système par défaut sur Android

    return Typography(
        displaySmall = TextStyle(fontFamily = interFamily, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp),
        headlineMedium = TextStyle(fontFamily = interFamily, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
        headlineSmall = TextStyle(fontFamily = interFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
        titleLarge = TextStyle(fontFamily = interFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
        titleMedium = TextStyle(fontFamily = interFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp),
        titleSmall = TextStyle(fontFamily = interFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
        // Corps de texte : Roboto, pour les tableaux/noms/résultats OCR.
        bodyLarge = TextStyle(fontFamily = robotoFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontFamily = robotoFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontFamily = robotoFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
        labelLarge = TextStyle(fontFamily = interFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
        labelMedium = TextStyle(fontFamily = interFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
        labelSmall = TextStyle(fontFamily = interFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp)
    )
}
