package com.thirdeye.app.audio

import android.content.Context

import com.thirdeye.app.bluetooth.NexusEyeBleManager
import com.thirdeye.app.language.NexusEyeLanguage
import com.thirdeye.app.vision.VisionSpeechResult
import com.thirdeye.app.vision.VisionSpeechSource

import java.io.File

class NexusEyeAssistantAudioRouter(
    private val context: Context,
    private val bleManager: NexusEyeBleManager,
    private val speechLanguage: NexusEyeLanguage,
    providedTtsManager: NexusEyeTtsManager? = null
) {

    private val ttsManager =
        providedTtsManager
            ?: NexusEyeTtsManager(
                context.applicationContext
            )

    private val ownsTtsManager =
        providedTtsManager == null

    private val wearableAudioPreparer =
        WearableSpeechAudioPreparer(
            context.applicationContext
        )

    /*
     * True only when the active BLE connection is ready.
     *
     * This does not pretend that an ESP32 is present.
     */
    val isWearableAudioAvailable: Boolean
        get() =
            bleManager.isWearableAudioAvailable

    /*
     * Route text to the appropriate output.
     *
     * When the wearable is connected:
     *   text -> Android TTS file -> MP3 validation -> BLE transport
     *
     * When the wearable is not connected:
     *   phone TTS fallback is used.
     *
     * The phone does not play the answer when the wearable
     * transport path is available.
     */
    fun routeText(
        text: String,
        language: NexusEyeLanguage = speechLanguage,
        onSuccess: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {

        val cleanText =
            text.trim()

        if (
            cleanText.isBlank()
        ) {

            onError(
                "There is no text to speak."
            )

            return
        }

        if (
            bleManager.isWearableAudioAvailable
        ) {

            routeToWearable(
                text = cleanText,
                language = language,
                onSuccess = onSuccess,
                onError = onError
            )

        } else {

            routeToPhone(
                text = cleanText,
                language = language,
                onSuccess = onSuccess,
                onError = onError
            )
        }
    }

    /*
     * Compatibility overload for callers that provide only
     * a text string and use the router's selected language.
     */
    fun routeText(
        text: String,
        onSuccess: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {

        routeText(
            text = text,
            language = speechLanguage,
            onSuccess = onSuccess,
            onError = onError
        )
    }

    private fun routeToWearable(
        text: String,
        language: NexusEyeLanguage,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {

        val languageId =
            language.id
                .trim()
                .ifBlank {
                    "en"
                }

        val speechResult =
            VisionSpeechResult(
                text = text,
                languageId = languageId,
                source = VisionSpeechSource.KNOWN_PERSON
            )

        wearableAudioPreparer.prepare(

            result =
                speechResult,

            onSuccess = { audioFile ->

                sendPreparedAudio(
                    audioFile = audioFile.file,
                    text = text,
                    onSuccess = onSuccess,
                    onError = onError
                )
            },

            onError = { error ->

                onError(
                    error
                )
            }
        )
    }

    private fun sendPreparedAudio(
        audioFile: File,
        text: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {

        val started =
            bleManager.sendAudioFile(
                file =
                    audioFile,
                onComplete = { success, error ->

                    safeDelete(
                        audioFile
                    )

                    if (
                        success
                    ) {

                        onSuccess(
                            "Wearable audio transfer completed."
                        )

                    } else {

                        onError(
                            error
                                ?: "Wearable audio transfer failed."
                        )
                    }
                }
            )

        if (
            !started
        ) {

            safeDelete(
                audioFile
            )

            onError(
                "Could not start wearable audio transfer."
            )
        }
    }

    private fun routeToPhone(
        text: String,
        language: NexusEyeLanguage,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {

        val languageAccepted =
            ttsManager.setLanguage(
                language
            )

        if (
            !languageAccepted
        ) {

            onError(
                "The selected speech language is not available in the installed TTS engine."
            )

            return
        }

        ttsManager.speakOnPhoneFallback(

            text =
                text,

            language =
                language,

            onComplete = {

                onSuccess(
                    "Phone speech completed."
                )
            },

            onError = { error ->

                onError(
                    error
                )
            }
        )
    }

    /*
     * Stops phone TTS immediately.
     *
     * This does not cancel a BLE transfer.
     */
    fun stopPhoneSpeech() {

        try {

            ttsManager.stop()

        } catch (_: Exception) {
        }
    }

    /*
     * Cancels an active wearable audio transfer.
     */
    fun stopWearableSpeech() {

        try {

            bleManager.cancelAudioTransfer()

        } catch (_: Exception) {
        }
    }

    /*
     * Stops both possible outputs.
     */
    fun stop() {

        stopPhoneSpeech()
        stopWearableSpeech()
    }

    fun shutdown() {

        try {
            stopPhoneSpeech()
        } catch (_: Exception) {
        }

        try {
            stopWearableSpeech()
        } catch (_: Exception) {
        }

        try {
            wearableAudioPreparer.shutdown()
        } catch (_: Exception) {
        }

        if (
            ownsTtsManager
        ) {

            try {
                ttsManager.shutdown()
            } catch (_: Exception) {
            }
        }
    }

    private fun safeDelete(
        file: File?
    ) {

        if (
            file == null
        ) {
            return
        }

        try {

            if (
                file.exists()
            ) {

                file.delete()
            }

        } catch (_: Exception) {
        }
    }
}