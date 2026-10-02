package com.myschoolocr.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.myschoolocr.app.ocr.GradeParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "myschool_settings")

data class AppSettings(
    val ocrLanguage: String = "fra",              // "fra" ou "eng"
    val nameConfidenceThreshold: Float = GradeParser.NAME_CONFIDENCE_THRESHOLD.toFloat(),
    val darkTheme: Boolean = false,                // false = bleu & blanc, true = bleu & noir
    val hasSeenOnboarding: Boolean = false
)

/** Réglages persistés localement (aucune donnée envoyée en ligne). */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val OCR_LANGUAGE = stringPreferencesKey("ocr_language")
        val NAME_THRESHOLD = floatPreferencesKey("name_threshold")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val HAS_SEEN_ONBOARDING = booleanPreferencesKey("has_seen_onboarding")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            ocrLanguage = prefs[Keys.OCR_LANGUAGE] ?: "fra",
            nameConfidenceThreshold = prefs[Keys.NAME_THRESHOLD] ?: GradeParser.NAME_CONFIDENCE_THRESHOLD.toFloat(),
            darkTheme = prefs[Keys.DARK_THEME] ?: false,
            hasSeenOnboarding = prefs[Keys.HAS_SEEN_ONBOARDING] ?: false
        )
    }

    suspend fun setOcrLanguage(language: String) {
        context.dataStore.edit { it[Keys.OCR_LANGUAGE] = language }
    }

    suspend fun setNameConfidenceThreshold(value: Float) {
        context.dataStore.edit { it[Keys.NAME_THRESHOLD] = value }
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DARK_THEME] = enabled }
    }

    suspend fun setHasSeenOnboarding(seen: Boolean) {
        context.dataStore.edit { it[Keys.HAS_SEEN_ONBOARDING] = seen }
    }
}
