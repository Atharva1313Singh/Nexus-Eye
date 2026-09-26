package com.thirdeye.app.language

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class LanguageState(
    val appLanguage: NexusEyeLanguage,
    val speechLanguage: NexusEyeLanguage
)

class LanguageManager(
    context: Context
) {

    private val appContext =
        context.applicationContext

    private val preferences =
        appContext.getSharedPreferences(
            "nexus_eye_language_preferences",
            Context.MODE_PRIVATE
        )

    private val _languageState =
        MutableStateFlow(
            loadLanguageState()
        )

    val languageState: StateFlow<LanguageState> =
        _languageState

    private fun loadLanguageState(): LanguageState {

        val appLanguageId =
            preferences.getString(
                KEY_APP_LANGUAGE,
                NexusEyeLanguages.English.id
            )
                ?: NexusEyeLanguages.English.id

        val speechLanguageId =
            preferences.getString(
                KEY_SPEECH_LANGUAGE,
                NexusEyeLanguages.English.id
            )
                ?: NexusEyeLanguages.English.id

        return LanguageState(
            appLanguage =
                NexusEyeLanguages.fromId(
                    appLanguageId
                ),
            speechLanguage =
                NexusEyeLanguages.fromId(
                    speechLanguageId
                )
        )
    }

    suspend fun saveLanguages(
        appLanguageId: String,
        speechLanguageId: String
    ) {

        val validAppLanguage =
            NexusEyeLanguages.fromId(
                appLanguageId
            )

        val validSpeechLanguage =
            NexusEyeLanguages.fromId(
                speechLanguageId
            )

        /*
         * Save permanently.
         */
        preferences.edit()
            .putString(
                KEY_APP_LANGUAGE,
                validAppLanguage.id
            )
            .putString(
                KEY_SPEECH_LANGUAGE,
                validSpeechLanguage.id
            )
            .apply()

        /*
         * Update the running app immediately.
         */
        _languageState.value =
            LanguageState(
                appLanguage =
                    validAppLanguage,
                speechLanguage =
                    validSpeechLanguage
            )
    }

    fun saveLanguagesNow(
        appLanguageId: String,
        speechLanguageId: String
    ) {

        val validAppLanguage =
            NexusEyeLanguages.fromId(
                appLanguageId
            )

        val validSpeechLanguage =
            NexusEyeLanguages.fromId(
                speechLanguageId
            )

        preferences.edit()
            .putString(
                KEY_APP_LANGUAGE,
                validAppLanguage.id
            )
            .putString(
                KEY_SPEECH_LANGUAGE,
                validSpeechLanguage.id
            )
            .apply()

        _languageState.value =
            LanguageState(
                appLanguage =
                    validAppLanguage,
                speechLanguage =
                    validSpeechLanguage
            )
    }

    fun getCurrentState(): LanguageState {
        return _languageState.value
    }

    companion object {

        private const val KEY_APP_LANGUAGE =
            "app_language_id"

        private const val KEY_SPEECH_LANGUAGE =
            "speech_language_id"
    }
}