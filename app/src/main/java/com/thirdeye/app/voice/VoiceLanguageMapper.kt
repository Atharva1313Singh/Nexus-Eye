package com.thirdeye.app.voice

import com.thirdeye.app.language.NexusEyeLanguage
import java.util.Locale

object VoiceLanguageMapper {

    fun toLocale(
        language: NexusEyeLanguage
    ): Locale {
        return Locale.forLanguageTag(
            language.localeTag
        )
    }
}