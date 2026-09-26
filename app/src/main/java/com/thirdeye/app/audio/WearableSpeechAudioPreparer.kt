package com.thirdeye.app.audio

import android.content.Context

import com.thirdeye.app.language.NexusEyeLanguages
import com.thirdeye.app.vision.VisionSpeechResult

import java.io.File
import java.io.FileInputStream
import java.util.UUID


/**
 * Represents an audio file prepared for the future
 * NEXUS EYE wearable transport layer.
 *
 * This class does NOT play the file on the phone.
 */
data class WearableSpeechAudioFile(
    val file: File,
    val text: String,
    val languageId: String
)


/**
 * Converts a VisionSpeechResult into an audio file.
 *
 * Important:
 * Android TextToSpeech does not guarantee that the generated
 * output is MP3 merely because the filename has a ".mp3"
 * extension.
 *
 * Therefore this class:
 *
 * 1. Generates the speech file.
 * 2. Checks that the file exists and is non-empty.
 * 3. Checks its header for an MP3 signature.
 * 4. Rejects the file when it is not plausibly MP3.
 *
 * No phone playback is performed.
 */
class WearableSpeechAudioPreparer(
    context: Context
) {

    private val appContext =
        context.applicationContext

    private val ttsManager =
        NexusEyeTtsManager(
            appContext
        )


    /**
     * Prepare speech audio for the wearable.
     */
    fun prepare(
        result: VisionSpeechResult,
        onSuccess: (
            WearableSpeechAudioFile
        ) -> Unit,
        onError: (
            String
        ) -> Unit
    ) {

        val cleanText =
            result.text
                .trim()

        if (
            cleanText.isBlank()
        ) {

            onError(
                "Wearable speech text is empty."
            )

            return
        }


        val languageId =
            result.languageId
                .trim()
                .ifBlank {
                    "en"
                }


        val language =
            findLanguageById(
                languageId
            )


        if (
            language == null
        ) {

            onError(
                "Unsupported speech language: $languageId"
            )

            return
        }


        val directory =
            File(
                appContext.cacheDir,
                "nexus_eye_wearable_audio"
            )


        try {

            if (
                !directory.exists() &&
                !directory.mkdirs()
            ) {

                onError(
                    "Could not create the wearable audio directory."
                )

                return
            }

        } catch (
            exception: Exception
        ) {

            onError(
                exception.message
                    ?: "Could not prepare the wearable audio directory."
            )

            return
        }


        val file =
            try {

                File(
                    directory,
                    "speech_${UUID.randomUUID()}.mp3"
                )

            } catch (
                exception: Exception
            ) {

                onError(
                    exception.message
                        ?: "Could not create the wearable audio file path."
                )

                return
            }


        ttsManager.synthesizeToFile(

            text =
                cleanText,

            language =
                language,

            outputFilePath =
                file.absolutePath,

            onSuccess = {

                verifyAndReturn(
                    file =
                        file,

                    text =
                        cleanText,

                    languageId =
                        languageId,

                    onSuccess =
                        onSuccess,

                    onError =
                        onError
                )
            },

            onError = { error ->

                safeDelete(
                    file
                )

                onError(
                    error
                )
            }
        )
    }


    /**
     * Verify the generated file before it is ever sent
     * to the wearable.
     */
    private fun verifyAndReturn(
        file: File,
        text: String,
        languageId: String,
        onSuccess: (
            WearableSpeechAudioFile
        ) -> Unit,
        onError: (
            String
        ) -> Unit
    ) {

        if (
            !file.exists()
        ) {

            onError(
                "The speech engine reported success, but the audio file was not created."
            )

            return
        }


        if (
            file.length() <= 0L
        ) {

            safeDelete(
                file
            )

            onError(
                "The generated wearable audio file is empty."
            )

            return
        }


        val looksLikeMp3 =
            try {

                isLikelyMp3(
                    file
                )

            } catch (
                exception: Exception
            ) {

                false
            }


        if (
            !looksLikeMp3
        ) {

            safeDelete(
                file
            )

            onError(
                "The installed Android TTS engine did not generate MP3 audio. " +
                        "The file was rejected instead of being sent as a false MP3."
            )

            return
        }


        onSuccess(

            WearableSpeechAudioFile(

                file =
                    file,

                text =
                    text,

                languageId =
                    languageId
            )
        )
    }


    /**
     * Checks common MP3 signatures.
     *
     * Accepted forms:
     *
     * - ID3 metadata header
     * - MPEG audio frame sync
     *
     * This is intentionally a lightweight validation.
     * A full MPEG parser will be added only when the final
     * wearable transport format is fixed.
     */
    private fun isLikelyMp3(
        file: File
    ): Boolean {

        FileInputStream(
            file
        ).use { input ->

            val header =
                ByteArray(
                    4
                )

            val count =
                input.read(
                    header
                )

            if (
                count < 2
            ) {

                return false
            }


            /*
             * ID3v2 header.
             */
            if (
                header[0].toInt() == 'I'.code &&
                header[1].toInt() == 'D'.code &&
                count >= 3 &&
                header[2].toInt() == '3'.code
            ) {

                return true
            }


            /*
             * MPEG frame sync.
             *
             * First byte:
             * 11111111
             *
             * Second byte:
             * 111xxxxx
             */
            val first =
                header[0]
                    .toInt() and 0xFF

            val second =
                header[1]
                    .toInt() and 0xFF


            return first == 0xFF &&
                    (second and 0xE0) == 0xE0
        }
    }


    /**
     * Find the project's configured language object.
     */
    private fun findLanguageById(
        languageId: String
    ) = NexusEyeLanguages
        .supportedLanguages
        .firstOrNull {

            it.id.equals(
                languageId,
                ignoreCase = true
            )
        }


    private fun safeDelete(
        file: File
    ) {

        try {

            if (
                file.exists()
            ) {

                file.delete()
            }

        } catch (_: Exception) {
        }
    }


    /**
     * Cleanly release Android TTS resources.
     */
    fun shutdown() {

        try {

            ttsManager.shutdown()

        } catch (_: Exception) {
        }
    }
}