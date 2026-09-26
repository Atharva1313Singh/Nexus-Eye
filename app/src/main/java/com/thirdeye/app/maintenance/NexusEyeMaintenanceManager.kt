package com.thirdeye.app.maintenance

import android.content.Context
import com.thirdeye.app.audio.NexusEyeTtsManager
import com.thirdeye.app.bluetooth.NexusEyeEsp32SettingsManager
import com.thirdeye.app.navigation.NexusEyeNavigationSettings
import com.thirdeye.app.offline.NexusEyeOfflineDataManager
import com.thirdeye.app.vision.NexusEyeVisionSettingsManager

/**
 * Stage 7 maintenance operations for NEXUS EYE.
 *
 * This reset intentionally restores ordinary application settings while leaving
 * protected credentials, the saved Home location, the application setup role,
 * and the Vision photo library untouched.
 */
class NexusEyeMaintenanceManager(
    context: Context
) {

    private val appContext =
        context.applicationContext

    private val ttsManager =
        NexusEyeTtsManager(appContext)

    private val esp32SettingsManager =
        NexusEyeEsp32SettingsManager(appContext)

    private val visionSettingsManager =
        NexusEyeVisionSettingsManager(appContext)

    private val navigationSettings =
        NexusEyeNavigationSettings(appContext)

    private val offlineDataManager =
        NexusEyeOfflineDataManager(appContext)

    fun resetUserSettings() {

        resetLanguageSettings()

        ttsManager.resetSpeechRate()

        esp32SettingsManager.resetToDefaults()

        visionSettingsManager.resetToDefaults()

        navigationSettings.resetToDefaults()

        offlineDataManager.clearCustomEntries()
    }

    private fun resetLanguageSettings() {

        appContext
            .getSharedPreferences(
                LANGUAGE_PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .remove(KEY_APP_LANGUAGE)
            .remove(KEY_SPEECH_LANGUAGE)
            .apply()
    }

    fun close() {

        try {
            ttsManager.shutdown()
        } catch (_: Exception) {
        }
    }

    companion object {

        private const val LANGUAGE_PREFS_NAME =
            "nexus_eye_language_preferences"

        private const val KEY_APP_LANGUAGE =
            "app_language_id"

        private const val KEY_SPEECH_LANGUAGE =
            "speech_language_id"
    }
}
