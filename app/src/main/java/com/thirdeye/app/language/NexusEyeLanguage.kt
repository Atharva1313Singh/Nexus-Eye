package com.thirdeye.app.language

data class NexusEyeLanguage(
    val id: String,
    val displayName: String,
    val localeTag: String
)

object NexusEyeLanguages {

    val English = NexusEyeLanguage(
        id = "en",
        displayName = "English",
        localeTag = "en-IN"
    )

    val Hindi = NexusEyeLanguage(
        id = "hi",
        displayName = "हिन्दी",
        localeTag = "hi-IN"
    )

    val supportedLanguages = listOf(
        English,
        Hindi
    )

    fun fromId(id: String): NexusEyeLanguage {
        return supportedLanguages.firstOrNull { it.id == id }
            ?: English
    }
}