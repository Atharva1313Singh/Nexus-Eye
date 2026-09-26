package com.thirdeye.app.audio

import android.util.Base64
import com.thirdeye.app.language.NexusEyeLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Produces real MP3 audio using Google Cloud Text-to-Speech.
 *
 * The API key is NEVER stored in this class.
 * It is supplied at runtime through apiKeyProvider.
 */
class GoogleCloudMp3TtsProvider(
    private val apiKeyProvider: () -> String?
) {

    companion object {
        private const val ENDPOINT =
            "https://texttospeech.googleapis.com/v1/text:synthesize"

        private const val CONNECT_TIMEOUT_MS = 5000
        private const val READ_TIMEOUT_MS = 5000
    }

    sealed class Result {

        data class Success(
            val audio: NexusEyeAudioData
        ) : Result()

        data class Failure(
            val message: String
        ) : Result()
    }

    suspend fun synthesize(
        text: String,
        language: NexusEyeLanguage,
        speakingRate: Double = 1.0
    ): Result = withContext(Dispatchers.IO) {

        if (text.isBlank()) {
            return@withContext Result.Failure(
                "Text to synthesize is empty."
            )
        }

        val apiKey =
            apiKeyProvider()
                ?.trim()
                .orEmpty()

        if (apiKey.isEmpty()) {
            return@withContext Result.Failure(
                "Google Cloud Text-to-Speech API key is not configured."
            )
        }

        val languageCode =
            language.localeTag

        val requestJson =
            JSONObject().apply {

                put(
                    "input",
                    JSONObject().apply {
                        put(
                            "text",
                            text
                        )
                    }
                )

                put(
                    "voice",
                    JSONObject().apply {
                        put(
                            "languageCode",
                            languageCode
                        )

                        put(
                            "ssmlGender",
                            "MALE"
                        )
                    }
                )

                put(
                    "audioConfig",
                    JSONObject().apply {

                        put(
                            "audioEncoding",
                            "MP3"
                        )

                        put(
                            "speakingRate",
                            speakingRate.coerceIn(
                                0.25,
                                4.0
                            )
                        )
                    }
                )
            }

        var connection: HttpURLConnection? = null

        try {

            val url =
                URL(
                    "$ENDPOINT?key=$apiKey"
                )

            connection =
                url.openConnection()
                        as HttpURLConnection

            connection.requestMethod = "POST"
            connection.connectTimeout =
                CONNECT_TIMEOUT_MS
            connection.readTimeout =
                READ_TIMEOUT_MS

            connection.doInput = true
            connection.doOutput = true

            connection.setRequestProperty(
                "Content-Type",
                "application/json; charset=UTF-8"
            )

            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            OutputStreamWriter(
                connection.outputStream,
                Charsets.UTF_8
            ).use { writer ->

                writer.write(
                    requestJson.toString()
                )

                writer.flush()
            }

            val responseCode =
                connection.responseCode

            val responseText =
                if (responseCode in 200..299) {

                    BufferedReader(
                        InputStreamReader(
                            connection.inputStream,
                            Charsets.UTF_8
                        )
                    ).use { reader ->
                        reader.readText()
                    }

                } else {

                    val errorStream =
                        connection.errorStream

                    if (errorStream != null) {

                        BufferedReader(
                            InputStreamReader(
                                errorStream,
                                Charsets.UTF_8
                            )
                        ).use { reader ->
                            reader.readText()
                        }

                    } else {
                        ""
                    }
                }

            if (responseCode !in 200..299) {

                val readableError =
                    extractGoogleError(
                        responseText
                    )

                return@withContext Result.Failure(
                    if (readableError.isNotBlank()) {
                        "Google TTS request failed: $readableError"
                    } else {
                        "Google TTS request failed with HTTP $responseCode."
                    }
                )
            }

            val json =
                JSONObject(
                    responseText
                )

            val audioContent =
                json.optString(
                    "audioContent",
                    ""
                )

            if (audioContent.isBlank()) {

                return@withContext Result.Failure(
                    "Google TTS returned no audio."
                )
            }

            val audioBytes =
                try {

                    Base64.decode(
                        audioContent,
                        Base64.DEFAULT
                    )

                } catch (_: IllegalArgumentException) {

                    return@withContext Result.Failure(
                        "Google TTS returned invalid audio data."
                    )
                }

            if (audioBytes.isEmpty()) {

                return@withContext Result.Failure(
                    "Google TTS returned an empty MP3."
                )
            }

            val audio =
                NexusEyeAudioDataFactory.fromBytes(
                    audioBytes
                )

            if (!audio.isMp3) {

                return@withContext Result.Failure(
                    "TTS returned audio, but it was not detected as MP3."
                )
            }

            Result.Success(
                audio
            )

        } catch (exception: Exception) {

            Result.Failure(
                when {

                    exception.message
                        ?.contains(
                            "timeout",
                            ignoreCase = true
                        ) == true -> {
                        "Google TTS request timed out."
                    }

                    else -> {
                        "Unable to reach the Google TTS service."
                    }
                }
            )

        } finally {

            connection?.disconnect()
        }
    }

    private fun extractGoogleError(
        responseText: String
    ): String {

        if (responseText.isBlank()) {
            return ""
        }

        return try {

            val root =
                JSONObject(
                    responseText
                )

            val error =
                root.optJSONObject(
                    "error"
                )

            error?.optString(
                "message",
                ""
            ).orEmpty()

        } catch (_: Exception) {

            ""
        }
    }
}