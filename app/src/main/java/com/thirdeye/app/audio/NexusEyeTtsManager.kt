package com.thirdeye.app.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.thirdeye.app.language.NexusEyeLanguage
import com.thirdeye.app.voice.VoiceLanguageMapper
import java.io.File
import java.util.Locale
import java.util.UUID

class NexusEyeTtsManager(
    context: Context
) {

    private val appContext =
        context.applicationContext

    private val speechPreferences =
        NexusEyeSpeechPreferences(
            appContext
        )

    private var textToSpeech:
            TextToSpeech? =
        null

    private var initialized =
        false

    private var currentLanguage:
            NexusEyeLanguage? =
        null

    private var currentVoice:
            Voice? =
        null

    private var currentSpeechRate:
            Float =
        speechPreferences.getSpeechRate()

    private var onFileComplete:
            (() -> Unit)? =
        null

    private var onFileError:
            ((String) -> Unit)? =
        null

    private var onPhoneSpeechComplete:
            (() -> Unit)? =
        null

    private var onPhoneSpeechError:
            ((String) -> Unit)? =
        null

    init {

        textToSpeech =
            TextToSpeech(
                appContext
            ) { status ->

                initialized =
                    status ==
                            TextToSpeech.SUCCESS

                if (
                    initialized
                ) {

                    try {

                        textToSpeech?.setSpeechRate(
                            currentSpeechRate
                        )

                    } catch (_: Exception) {
                    }
                }
            }

        textToSpeech
            ?.setOnUtteranceProgressListener(
                object :
                    UtteranceProgressListener() {

                    override fun onStart(
                        utteranceId: String
                    ) {
                    }

                    override fun onDone(
                        utteranceId: String
                    ) {

                        when {

                            utteranceId.startsWith(
                                "nexus_file_"
                            ) -> {

                                onFileComplete
                                    ?.invoke()
                            }

                            utteranceId.startsWith(
                                "nexus_phone_"
                            ) -> {

                                onPhoneSpeechComplete
                                    ?.invoke()
                            }
                        }
                    }

                    @Deprecated(
                        "Deprecated by Android API"
                    )
                    override fun onError(
                        utteranceId: String
                    ) {

                        handleUtteranceError(
                            utteranceId,
                            null
                        )
                    }

                    override fun onError(
                        utteranceId: String,
                        errorCode: Int
                    ) {

                        handleUtteranceError(
                            utteranceId,
                            errorCode
                        )
                    }
                }
            )
    }

    fun isInitialized():
            Boolean {

        return initialized
    }

    fun setLanguage(
        language: NexusEyeLanguage
    ): Boolean {

        if (
            !initialized
        ) {

            return false
        }

        val locale =
            VoiceLanguageMapper.toLocale(
                language
            )

        val languageResult =
            try {

                textToSpeech?.setLanguage(
                    locale
                )

            } catch (_: Exception) {

                null
            }

        val supported =
            languageResult != null &&
                    languageResult !=
                    TextToSpeech.LANG_MISSING_DATA &&
                    languageResult !=
                    TextToSpeech.LANG_NOT_SUPPORTED

        if (
            !supported
        ) {

            return false
        }

        val femaleVoice =
            findBestFemaleVoice(
                locale
            )

        if (
            femaleVoice != null
        ) {

            try {

                textToSpeech?.voice =
                    femaleVoice

                currentVoice =
                    textToSpeech?.voice

            } catch (_: Exception) {
            }

        } else {

            currentVoice =
                try {

                    textToSpeech?.voice

                } catch (_: Exception) {

                    null
                }
        }

        currentLanguage =
            language

        applySpeechSettings(
            language
        )

        return true
    }

    fun setSpeechRate(
        speechRate: Float
    ): Float {

        val safeRate =
            speechPreferences.setSpeechRate(
                speechRate
            )

        currentSpeechRate =
            safeRate

        try {

            textToSpeech?.setSpeechRate(
                safeRate
            )

        } catch (_: Exception) {
        }

        return safeRate
    }

    fun getSpeechRate():
            Float {

        currentSpeechRate =
            speechPreferences.getSpeechRate()

        return currentSpeechRate
    }

    fun resetSpeechRate():
            Float {

        val defaultRate =
            speechPreferences.resetSpeechRate()

        currentSpeechRate =
            defaultRate

        try {

            textToSpeech?.setSpeechRate(
                defaultRate
            )

        } catch (_: Exception) {
        }

        return defaultRate
    }

    fun getCurrentLanguage():
            NexusEyeLanguage? {

        return currentLanguage
    }

    fun getCurrentVoice():
            Voice? {

        return currentVoice
    }

    private fun applySpeechSettings(
        language: NexusEyeLanguage
    ) {

        currentSpeechRate =
            speechPreferences.getSpeechRate()

        try {

            textToSpeech?.setSpeechRate(
                currentSpeechRate
            )

        } catch (_: Exception) {
        }

        try {

            textToSpeech?.setPitch(
                if (
                    language.id == "hi"
                ) {

                    HINDI_SPEECH_PITCH

                } else {

                    DEFAULT_SPEECH_PITCH
                }
            )

        } catch (_: Exception) {
        }
    }

    private fun findBestFemaleVoice(
        locale: Locale
    ): Voice? {

        val engine =
            textToSpeech
                ?: return null

        val voices =
            try {

                engine.voices

            } catch (_: Exception) {

                emptySet()
            }

        if (
            voices.isEmpty()
        ) {

            return null
        }

        val languageVoices =
            voices.filter { voice ->

                voice.locale.language.equals(
                    locale.language,
                    ignoreCase = true
                )
            }

        if (
            languageVoices.isEmpty()
        ) {

            return null
        }

        val exactRegionVoices =
            languageVoices.filter { voice ->

                locale.country.isNotBlank() &&
                        voice.locale.country.equals(
                            locale.country,
                            ignoreCase = true
                        )
            }

        val candidates =
            if (
                exactRegionVoices.isNotEmpty()
            ) {

                exactRegionVoices

            } else {

                languageVoices
            }

        val femaleCandidates =
            candidates.filter { voice ->

                isFemaleVoice(
                    voice
                )
            }

        val pool =
            if (
                femaleCandidates.isNotEmpty()
            ) {

                femaleCandidates

            } else {

                candidates
            }

        return pool.maxByOrNull { voice ->

            scoreVoice(
                voice,
                locale
            )
        }
    }

    private fun isFemaleVoice(
        voice: Voice
    ): Boolean {

        val name =
            voice.name.lowercase(
                Locale.ROOT
            )

        if (
            MALE_TERMS.any { term ->

                name.contains(
                    term
                )
            }
        ) {

            return false
        }

        return FEMALE_TERMS.any { term ->

            name.contains(
                term
            )
        }
    }

    private fun scoreVoice(
        voice: Voice,
        locale: Locale
    ): Int {

        val name =
            voice.name.lowercase(
                Locale.ROOT
            )

        var score =
            0

        if (
            voice.locale.language.equals(
                locale.language,
                ignoreCase = true
            )
        ) {

            score += 100
        }

        if (
            locale.country.isNotBlank() &&
            voice.locale.country.equals(
                locale.country,
                ignoreCase = true
            )
        ) {

            score += 200
        }

        FEMALE_TERMS.forEach { term ->

            if (
                name.contains(
                    term
                )
            ) {

                score += 500
            }
        }

        MALE_TERMS.forEach { term ->

            if (
                name.contains(
                    term
                )
            ) {

                score -= 500
            }
        }

        try {

            score +=
                voice.quality / 2

        } catch (_: Exception) {
        }

        try {

            score -=
                voice.latency / 10

        } catch (_: Exception) {
        }

        try {

            if (
                !voice.isNetworkConnectionRequired
            ) {

                score += 40
            }

        } catch (_: Exception) {
        }

        return score
    }

    fun synthesizeToFile(
        text: String,
        language: NexusEyeLanguage,
        outputFilePath: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {

        if (
            !initialized
        ) {

            onError(
                "Text-to-speech is not initialized."
            )

            return
        }

        if (
            text.isBlank()
        ) {

            onError(
                "There is no text to synthesize."
            )

            return
        }

        if (
            !setLanguage(
                language
            )
        ) {

            onError(
                "The selected speech language is not available in the installed TTS engine."
            )

            return
        }

        val file =
            File(
                outputFilePath
            )

        try {

            file.parentFile?.mkdirs()

        } catch (
            exception: Exception
        ) {

            onError(
                exception.message
                    ?: "Could not prepare the audio file."
            )

            return
        }

        val utteranceId =
            "nexus_file_" +
                    UUID.randomUUID()
                        .toString()

        onFileComplete =
            onSuccess

        onFileError =
            onError

        val result =
            try {

                textToSpeech?.synthesizeToFile(
                    text,
                    Bundle(),
                    file,
                    utteranceId
                )

            } catch (_: Exception) {

                null
            }

        if (
            result !=
            TextToSpeech.SUCCESS
        ) {

            onError(
                "Could not generate the speech audio file."
            )
        }
    }

    fun speakOnPhoneFallback(
        text: String,
        language: NexusEyeLanguage,
        onComplete: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {

        if (
            !initialized
        ) {

            onError(
                "Text-to-speech is not initialized."
            )

            return
        }

        if (
            text.isBlank()
        ) {

            onError(
                "There is no text to speak."
            )

            return
        }

        if (
            !setLanguage(
                language
            )
        ) {

            onError(
                "The selected speech language is not available in the installed TTS engine."
            )

            return
        }

        stop()

        val utteranceId =
            "nexus_phone_" +
                    UUID.randomUUID()
                        .toString()

        onPhoneSpeechComplete =
            onComplete

        onPhoneSpeechError =
            onError

        val result =
            try {

                textToSpeech?.speak(
                    text,
                    TextToSpeech.QUEUE_FLUSH,
                    Bundle(),
                    utteranceId
                )

            } catch (_: Exception) {

                null
            }

        if (
            result !=
            TextToSpeech.SUCCESS
        ) {

            onError(
                "Could not speak the answer through the phone."
            )
        }
    }

    fun speakOnPhoneFallback(
        text: String,
        onComplete: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {

        val language =
            currentLanguage

        if (
            language == null
        ) {

            onError(
                "No speech language has been selected."
            )

            return
        }

        speakOnPhoneFallback(
            text =
                text,
            language =
                language,
            onComplete =
                onComplete,
            onError =
                onError
        )
    }

    fun stop() {

        try {

            textToSpeech?.stop()

        } catch (_: Exception) {
        }
    }

    fun shutdown() {

        try {

            textToSpeech?.stop()
            textToSpeech?.shutdown()

        } catch (_: Exception) {
        }

        textToSpeech =
            null

        initialized =
            false

        currentLanguage =
            null

        currentVoice =
            null

        currentSpeechRate =
            speechPreferences
                .getSpeechRate()

        onFileComplete =
            null

        onFileError =
            null

        onPhoneSpeechComplete =
            null

        onPhoneSpeechError =
            null
    }

    private fun handleUtteranceError(
        utteranceId: String,
        errorCode: Int?
    ) {

        val message =
            if (
                errorCode == null
            ) {

                "Text-to-speech playback failed."

            } else {

                "Text-to-speech error: $errorCode"
            }

        when {

            utteranceId.startsWith(
                "nexus_file_"
            ) -> {

                onFileError
                    ?.invoke(
                        message
                    )
            }

            utteranceId.startsWith(
                "nexus_phone_"
            ) -> {

                onPhoneSpeechError
                    ?.invoke(
                        message
                    )
            }
        }
    }

    companion object {

        private const val DEFAULT_SPEECH_PITCH =
            1.02f

        private const val HINDI_SPEECH_PITCH =
            1.06f

        private val FEMALE_TERMS =
            listOf(
                "female",
                "woman",
                "girl",
                "feminine",
                "samantha",
                "susan",
                "karen",
                "zira",
                "heera",
                "neerja",
                "lekha",
                "veena"
            )

        private val MALE_TERMS =
            listOf(
                "male",
                "man",
                "masculine",
                "david",
                "daniel",
                "george",
                "fred",
                "mark",
                "james",
                "john",
                "michael",
                "aaron",
                "arthur",
                "amit",
                "rahul",
                "arjun",
                "rohan",
                "liam",
                "matthew",
                "oliver",
                "robert",
                "thomas",
                "william"
            )
    }
}