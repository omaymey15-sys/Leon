package com.myschoolocr.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.myschoolocr.app.data.AppSettings
import com.myschoolocr.app.ui.navigation.AppNav
import com.myschoolocr.app.ui.theme.MySchoolOcrTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as MyApp

        setContent {
            val settings by app.settingsRepository.settings.collectAsState(initial = AppSettings())

            MySchoolOcrTheme(darkTheme = settings.darkTheme) {
                // Barre de statut système alignée sur le thème choisi (clair/sombre) :
                // icônes claires sur fond sombre, icônes sombres sur fond clair.
                val view = LocalView.current
                val statusBarColor = MaterialTheme.colorScheme.surface
                if (!view.isInEditMode) {
                    SideEffect {
                        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
                        window.statusBarColor = statusBarColor.toArgb()
                        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !settings.darkTheme
                    }
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNav(repository = app.repository, settingsRepository = app.settingsRepository)
                }
            }
        }
    }
}
