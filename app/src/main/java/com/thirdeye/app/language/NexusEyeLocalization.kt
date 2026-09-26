package com.thirdeye.app.language

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

val LocalNexusEyeLanguage =
    compositionLocalOf {
        NexusEyeLanguages.English
    }

object NexusEyeLocalization {

    private var activeAppLanguage by mutableStateOf(
        NexusEyeLanguages.English
    )

    val currentAppLanguage: NexusEyeLanguage
        get() = activeAppLanguage

    fun setAppLanguage(
        language: NexusEyeLanguage
    ) {
        activeAppLanguage = language
    }

    fun setAppLanguageById(
        languageId: String
    ) {
        activeAppLanguage =
            NexusEyeLanguages.fromId(
                languageId
            )
    }

    fun resetToEnglish() {
        activeAppLanguage =
            NexusEyeLanguages.English
    }
}

@Composable
@ReadOnlyComposable
fun nexusText(
    key: AppTextKey
): String {

    /*
     * Read the Compose state so every screen using
     * nexusText() recomposes immediately when the
     * app language changes.
     */

    val language =
        NexusEyeLocalization.currentAppLanguage

    return AppStrings.getText(
        language.id,
        key
    )
}