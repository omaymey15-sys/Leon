package com.myschoolocr.app

import android.app.Application
import com.myschoolocr.app.data.AppDatabase
import com.myschoolocr.app.data.Repository
import com.myschoolocr.app.data.SettingsRepository
import com.myschoolocr.app.ocr.ImagePreprocessor

class MyApp : Application() {
    lateinit var repository: Repository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.getInstance(this)
        repository = Repository(db)
        settingsRepository = SettingsRepository(this)
        // Si l'initialisation échoue (rare), l'app continue sans prétraitement d'image
        // plutôt que de planter — voir ImagePreprocessor.isAvailable.
        ImagePreprocessor.init()
    }
}
