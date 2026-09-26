package com.thirdeye.app.voice

import android.content.Context
import com.thirdeye.app.language.NexusEyeLanguage

class NexusEyeVoiceController(
    context: Context
) {

    private val speechRecognizer =
        NexusEyeSpeechRecognizer(
            context.applicationContext
        )

    var isListening: Boolean = false
        private set

    var lastRecognizedText: String = ""
        private set

    fun startListening(
        language: NexusEyeLanguage,
        onPartialResult: (String) -> Unit = {},
        onCommand: (NexusEyeVoiceCommand) -> Unit,
        onError: (String) -> Unit = {}
    ) {

        if (isListening) {
            return
        }

        isListening = true

        speechRecognizer.startListening(
            language,
            onPartialResult = { partialText ->

                onPartialResult(
                    partialText
                )
            },
            onFinalResult = { finalText ->

                isListening = false

                val cleanText =
                    finalText.trim()

                lastRecognizedText =
                    cleanText

                if (cleanText.isBlank()) {

                    onError(
                        "I could not understand what you said."
                    )

                    return@startListening
                }

                val command =
                    NexusEyeVoiceCommandParser.parse(
                        cleanText
                    )

                onCommand(
                    command
                )
            },
            onError = { error ->

                isListening = false

                onError(
                    error
                )
            }
        )
    }

    fun stopListening() {

        try {
            speechRecognizer.stopListening()
        } catch (_: Exception) {
        }

        isListening = false
    }

    fun parse(
        text: String
    ): NexusEyeVoiceCommand {

        return NexusEyeVoiceCommandParser.parse(
            text
        )
    }

    fun shutdown() {

        try {
            speechRecognizer.shutdown()
        } catch (_: Exception) {
        }

        isListening = false
    }
}