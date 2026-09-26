package com.thirdeye.app.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.thirdeye.app.language.NexusEyeLanguage
import java.util.Locale

class NexusEyeSpeechRecognizer(
    context: Context
) {

    private val appContext =
        context.applicationContext

    private val speechRecognizer: SpeechRecognizer? =
        if (
            SpeechRecognizer.isRecognitionAvailable(
                appContext
            )
        ) {
            SpeechRecognizer.createSpeechRecognizer(
                appContext
            )
        } else {
            null
        }

    private var listening = false

    private var currentPartialResult:
            ((String) -> Unit)? = null

    private var currentFinalResult:
            ((String) -> Unit)? = null

    private var currentError:
            ((String) -> Unit)? = null

    private var finalResultDelivered = false

    init {

        speechRecognizer?.setRecognitionListener(
            object : RecognitionListener {

                override fun onReadyForSpeech(
                    params: Bundle?
                ) {
                    listening = true
                    finalResultDelivered = false
                }

                override fun onBeginningOfSpeech() {
                    listening = true
                }

                override fun onRmsChanged(
                    rmsdB: Float
                ) {
                    // Not required.
                }

                override fun onBufferReceived(
                    buffer: ByteArray?
                ) {
                    // Not required.
                }

                override fun onEndOfSpeech() {
                    listening = false
                }

                override fun onError(
                    error: Int
                ) {

                    listening = false

                    if (finalResultDelivered) {
                        clearCallbacks()
                        return
                    }

                    val message =
                        errorMessage(
                            error
                        )

                    val callback =
                        currentError

                    clearCallbacks()

                    callback?.invoke(
                        message
                    )
                }

                override fun onResults(
                    results: Bundle?
                ) {

                    listening = false

                    if (finalResultDelivered) {
                        clearCallbacks()
                        return
                    }

                    val matches =
                        results?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )

                    val finalText =
                        matches
                            ?.firstOrNull()
                            ?.trim()
                            .orEmpty()

                    finalResultDelivered = true

                    val successCallback =
                        currentFinalResult

                    val errorCallback =
                        currentError

                    clearCallbacks()

                    if (finalText.isBlank()) {

                        errorCallback?.invoke(
                            "I could not understand what you said."
                        )

                    } else {

                        successCallback?.invoke(
                            finalText
                        )
                    }
                }

                override fun onPartialResults(
                    partialResults: Bundle?
                ) {

                    val matches =
                        partialResults?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )

                    val partialText =
                        matches
                            ?.firstOrNull()
                            ?.trim()
                            .orEmpty()

                    if (partialText.isNotBlank()) {

                        currentPartialResult?.invoke(
                            partialText
                        )
                    }
                }

                override fun onEvent(
                    eventType: Int,
                    params: Bundle?
                ) {
                    // Not required.
                }
            }
        )
    }

    fun startListening(
        language: NexusEyeLanguage,
        onPartialResult: (String) -> Unit = {},
        onFinalResult: (String) -> Unit,
        onError: (String) -> Unit = {}
    ) {

        val recognizer =
            speechRecognizer

        if (recognizer == null) {

            onError(
                "Speech recognition is not available on this device."
            )

            return
        }

        stopListening()

        currentPartialResult =
            onPartialResult

        currentFinalResult =
            onFinalResult

        currentError =
            onError

        finalResultDelivered = false
        listening = true

        val locale =
            languageToLocale(
                language.id
            )

        val intent =
            Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            ).apply {

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    locale.toLanguageTag()
                )

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                    locale.toLanguageTag()
                )

                putExtra(
                    RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                    true
                )

                putExtra(
                    RecognizerIntent.EXTRA_MAX_RESULTS,
                    3
                )
            }

        try {

            recognizer.startListening(
                intent
            )

        } catch (
            securityException: SecurityException
        ) {

            listening = false

            clearCallbacks()

            onError(
                "Microphone permission is required for speech recognition."
            )

        } catch (
            exception: Exception
        ) {

            listening = false

            clearCallbacks()

            onError(
                exception.message
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?: "Could not start speech recognition."
            )
        }
    }

    fun stopListening() {

        listening = false
        finalResultDelivered = true

        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {
        }

        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {
        }

        clearCallbacks()
    }

    fun isListening(): Boolean {
        return listening
    }

    fun shutdown() {

        listening = false
        finalResultDelivered = true

        clearCallbacks()

        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {
        }

        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }
    }

    private fun clearCallbacks() {

        currentPartialResult = null
        currentFinalResult = null
        currentError = null
    }

    private fun languageToLocale(
        languageId: String
    ): Locale {

        val cleanId =
            languageId
                .trim()
                .replace(
                    "_",
                    "-"
                )

        if (cleanId.isBlank()) {
            return Locale.US
        }

        return when {

            cleanId.equals(
                "hi",
                ignoreCase = true
            ) -> {
                Locale(
                    "hi",
                    "IN"
                )
            }

            cleanId.equals(
                "en",
                ignoreCase = true
            ) -> {
                Locale.US
            }

            cleanId.equals(
                "en-IN",
                ignoreCase = true
            ) -> {
                Locale(
                    "en",
                    "IN"
                )
            }

            else -> {

                val parsed =
                    Locale.forLanguageTag(
                        cleanId
                    )

                if (
                    parsed.language.isBlank()
                ) {
                    Locale.US
                } else {
                    parsed
                }
            }
        }
    }

    private fun errorMessage(
        error: Int
    ): String {

        return when (error) {

            SpeechRecognizer.ERROR_AUDIO ->
                "There was a microphone audio error."

            SpeechRecognizer.ERROR_CLIENT ->
                "Speech recognition could not start correctly."

            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                "Microphone permission is required."

            SpeechRecognizer.ERROR_NETWORK ->
                "Speech recognition network error."

            SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                "Speech recognition network timed out."

            SpeechRecognizer.ERROR_NO_MATCH ->
                "I could not understand what you said."

            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                "Speech recognition is busy. Please try again."

            SpeechRecognizer.ERROR_SERVER ->
                "The speech recognition service returned an error."

            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                "I did not hear any speech."

            else ->
                "Speech recognition failed. Please try again."
        }
    }
}