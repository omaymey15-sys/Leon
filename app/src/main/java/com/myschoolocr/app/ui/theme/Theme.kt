package com.myschoolocr.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Indigo40,
    onPrimary = Color.White,
    primaryContainer = Indigo90,
    onPrimaryContainer = Indigo10,
    secondary = Violet40,
    onSecondary = Color.White,
    secondaryContainer = Violet90,
    onSecondaryContainer = Violet20,
    tertiary = Amber40,
    onTertiary = Color.White,
    tertiaryContainer = Amber90,
    onTertiaryContainer = Amber10,
    background = Neutral99,
    onBackground = Neutral10,
    surface = Neutral99,
    onSurface = Neutral10,
    surfaceVariant = Neutral95,
    onSurfaceVariant = NeutralVariant30,
    outline = NeutralVariant50,
    error = Error40,
    onError = Color.White,
    errorContainer = Error90,
    onErrorContainer = Error10
)

private val DarkColors = darkColorScheme(
    primary = Indigo80,
    onPrimary = Indigo20,
    primaryContainer = Indigo30,
    onPrimaryContainer = Indigo90,
    secondary = Violet80,
    onSecondary = Violet20,
    secondaryContainer = Color(0xFF4D3C7A),
    onSecondaryContainer = Violet90,
    tertiary = Amber80,
    onTertiary = Color(0xFF5C3F00),
    tertiaryContainer = Color(0xFF825800),
    onTertiaryContainer = Amber90,
    background = Neutral10,
    onBackground = Neutral90,
    surface = Neutral10,
    onSurface = Neutral90,
    surfaceVariant = Neutral20,
    onSurfaceVariant = NeutralVariant80,
    outline = NeutralVariant50,
    error = Error80,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Error90
)

/**
 * [darkTheme] est piloté par le réglage utilisateur (Réglages > Apparence),
 * pas par le thème système : bleu & blanc, ou bleu & noir.
 */
@Composable
fun MySchoolOcrTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val typography = remember { appTypography(context) }
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, typography = typography, shapes = AppShapes, content = content)
}
