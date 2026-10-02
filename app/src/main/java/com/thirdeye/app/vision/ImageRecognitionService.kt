package com.thirdeye.app.vision

import android.content.Context
import android.util.Base64
import com.thirdeye.app.security.NexusEyeApiCredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class ImageRecognitionResult(
    val answer: String,
    val language: String,
    val model: String
)

class ImageRecognitionService(
    context: Context? = null
) {

    private val apiCredentialStore: NexusEyeApiCredentialStore? =
        context?.applicationContext?.let {
            NexusEyeApiCredentialStore(it)
        }

    companion object {

        private const val GEMINI_API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"

        private const val MODEL_NAME =
            "gemini-2.5-flash"

        private const val USER_AGENT =
            "NexusEye/1.0 Android"

        private const val MAX_IMAGE_SIZE_BYTES =
            10 * 1024 * 1024
    }

    /*
     * ============================================================
     * SECURE-KEY ENTRY POINT
     * ============================================================
     *
     * This is the preferred method.
     *
     * The API key is loaded from NexusEyeApiCredentialStore and is
     * never passed into the UI as a plain configuration value.
     */
    suspend fun recognizeImage(
        imageBytes: ByteArray,
        mimeType: String,
        language: String
    ): Result<ImageRecognitionResult> {

        val apiKey =
            apiCredentialStore
                ?.getGeminiApiKey()
                .orEmpty()

        if (apiKey.isBlank()) {

            return Result.failure(
                IllegalStateException(
                    "Gemini API key is not configured."
                )
            )
        }

        return recognizeImage(
            imageBytes = imageBytes,
            mimeType = mimeType,
            language = language,
            apiKey = apiKey
        )
    }

    /*
     * ============================================================
     * COMPATIBILITY ENTRY POINT
     * ============================================================
     *
     * Existing callers that already provide an API key continue
     * to compile.
     *
     * New code should use the secure 3-parameter method above.
     */
    suspend fun recognizeImage(
        imageBytes: ByteArray,
        mimeType: String,
        language: String,
        apiKey: String
    ): Result<ImageRecognitionResult> {

        return withContext(
            Dispatchers.IO
        ) {

            try {

                if (
                    imageBytes.isEmpty()
                ) {

                    return@withContext Result.failure(
                        IllegalArgumentException(
                            "The received image is empty."
                        )
                    )
                }

                if (
                    imageBytes.size >
                    MAX_IMAGE_SIZE_BYTES
                ) {

                    return@withContext Result.failure(
                        IllegalArgumentException(
                            "The received image is too large."
                        )
                    )
                }

                val cleanApiKey =
                    apiKey.trim()

                if (
                    cleanApiKey.isBlank()
                ) {

                    return@withContext Result.failure(
                        IllegalArgumentException(
                            "Gemini API key is not configured."
                        )
                    )
                }

                val normalizedMimeType =
                    normalizeMimeType(
                        mimeType
                    )

                val prompt =
                    createRecognitionPrompt(
                        language
                    )

                val encodedImage =
                    Base64.encodeToString(
                        imageBytes,
                        Base64.NO_WRAP
                    )

                val requestBody =
                    createRequestBody(
                        imageBase64 =
                            encodedImage,
                        mimeType =
                            normalizedMimeType,
                        prompt =
                            prompt
                    )

                val response =
                    httpPost(
                        apiKey =
                            cleanApiKey,
                        body =
                            requestBody
                    )

                parseResponse(
                    response =
                        response,
                    language =
                        language
                )

            } catch (
                exception: Exception
            ) {

                Result.failure(
                    exception
                )
            }
        }
    }

    // ============================================================
    // PROMPT
    // ============================================================

    private fun createRecognitionPrompt(
        language: String
    ): String {

        val safeLanguage =
            language
                .trim()
                .ifBlank {
                    "English"
                }

        return """
            You are the visual assistance system for NEXUS EYE,
            an assistive partner for a blind person.

            Analyze the camera image carefully.

            Your response will be converted directly into speech,
            so make it short, clear, useful, and natural.

            Communication language:
            $safeLanguage

            Describe:
            1. The most important objects or people.
            2. Important obstacles or hazards relevant to movement.
            3. The relative position of important objects when it is
               reasonably clear, such as left, right, center, near,
               or ahead.
            4. Any clearly readable short text that is important.
            5. Any obvious action or guidance that would help the
               blind user understand the scene.

            Do not invent details that cannot be supported by the image.

            Do not mention that you are an AI.

            Keep the final spoken response concise, normally 1 to 4
            sentences.

            Respond only with the spoken answer in $safeLanguage.
        """.trimIndent()
    }

    // ============================================================
    // REQUEST BODY
    // ============================================================

    private fun createRequestBody(
        imageBase64: String,
        mimeType: String,
        prompt: String
    ): String {

        val imagePart =
            JSONObject().apply {

                put(
                    "inline_data",
                    JSONObject().apply {

                        put(
                            "mime_type",
                            mimeType
                        )

                        put(
                            "data",
                            imageBase64
                        )
                    }
                )
            }

        val textPart =
            JSONObject().apply {

                put(
                    "text",
                    prompt
                )
            }

        val parts =
            JSONArray().apply {

                put(
                    textPart
                )

                put(
                    imagePart
                )
            }

        val content =
            JSONObject().apply {

                put(
                    "parts",
                    parts
                )
            }

        return JSONObject().apply {

            put(
                "contents",
                JSONArray().apply {

                    put(
                        content
                    )
                }
            )
        }.toString()
    }

    // ============================================================
    // HTTP
    // ============================================================

    private fun httpPost(
        apiKey: String,
        body: String
    ): String {

        val connection =
            URL(
                GEMINI_API_URL
            )
                .openConnection()
                    as HttpURLConnection

        return try {

            connection.requestMethod =
                "POST"

            connection.doOutput =
                true

            connection.connectTimeout =
                20_000

            connection.readTimeout =
                45_000

            connection.setRequestProperty(
                "x-goog-api-key",
                apiKey
            )

            connection.setRequestProperty(
                "Content-Type",
                "application/json"
            )

            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            connection.setRequestProperty(
                "User-Agent",
                USER_AGENT
            )

            connection.outputStream
                .bufferedWriter(
                    Charsets.UTF_8
                )
                .use { writer ->

                    writer.write(
                        body
                    )
                }

            val responseCode =
                connection.responseCode

            val responseText =
                if (
                    responseCode in 200..299
                ) {

                    connection.inputStream
                        .bufferedReader()
                        .use {
                            it.readText()
                        }

                } else {

                    connection.errorStream
                        ?.bufferedReader()
                        ?.use {
                            it.readText()
                        }
                        ?: ""
                }

            if (
                responseCode !in 200..299
            ) {

                throw IllegalStateException(
                    parseHttpError(
                        responseCode =
                            responseCode,
                        responseBody =
                            responseText
                    )
                )
            }

            responseText

        } finally {

            connection.disconnect()
        }
    }

    // ============================================================
    // RESPONSE
    // ============================================================

    private fun parseResponse(
        response: String,
        language: String
    ): Result<ImageRecognitionResult> {

        return try {

            val root =
                JSONObject(
                    response
                )

            val candidates =
                root.optJSONArray(
                    "candidates"
                )

            if (
                candidates == null ||
                candidates.length() == 0
            ) {

                return Result.failure(
                    IllegalStateException(
                        "Gemini returned no visual answer."
                    )
                )
            }

            val candidate =
                candidates.getJSONObject(
                    0
                )

            val content =
                candidate.optJSONObject(
                    "content"
                )
                    ?: return Result.failure(
                        IllegalStateException(
                            "Gemini response has no content."
                        )
                    )

            val parts =
                content.optJSONArray(
                    "parts"
                )
                    ?: return Result.failure(
                        IllegalStateException(
                            "Gemini response has no text parts."
                        )
                    )

            val textBuilder =
                StringBuilder()

            for (
            index in
            0 until parts.length()
            ) {

                val part =
                    parts.optJSONObject(
                        index
                    )
                        ?: continue

                val text =
                    part.optString(
                        "text",
                        ""
                    )

                if (
                    text.isNotBlank()
                ) {

                    if (
                        textBuilder.isNotEmpty()
                    ) {

                        textBuilder.append(
                            " "
                        )
                    }

                    textBuilder.append(
                        text.trim()
                    )
                }
            }

            val answer =
                textBuilder
                    .toString()
                    .trim()

            if (
                answer.isBlank()
            ) {

                return Result.failure(
                    IllegalStateException(
                        "Gemini returned an empty visual answer."
                    )
                )
            }

            Result.success(
                ImageRecognitionResult(
                    answer =
                        answer,
                    language =
                        language,
                    model =
                        MODEL_NAME
                )
            )

        } catch (
            exception: Exception
        ) {

            Result.failure(
                exception
            )
        }
    }

    // ============================================================
    // HTTP ERROR
    // ============================================================

    private fun parseHttpError(
        responseCode: Int,
        responseBody: String
    ): String {

        if (
            responseBody.isBlank()
        ) {

            return when (
                responseCode
            ) {

                400 ->
                    "Gemini rejected the image request."

                401,
                403 ->
                    "Gemini API key was rejected."

                429 ->
                    "Gemini request limit was reached."

                else ->
                    "Gemini service returned HTTP $responseCode."
            }
        }

        return try {

            val root =
                JSONObject(
                    responseBody
                )

            val error =
                root.optJSONObject(
                    "error"
                )

            val message =
                error?.optString(
                    "message",
                    ""
                )

            if (
                !message.isNullOrBlank()
            ) {

                message

            } else {

                "Gemini service returned HTTP $responseCode."
            }

        } catch (_: Exception) {

            "Gemini service returned HTTP $responseCode."
        }
    }

    // ============================================================
    // MIME TYPE
    // ============================================================

    private fun normalizeMimeType(
        mimeType: String
    ): String {

        return when (
            mimeType
                .trim()
                .lowercase(
                    Locale.US
                )
        ) {

            "image/jpeg",
            "image/jpg" ->
                "image/jpeg"

            "image/png" ->
                "image/png"

            "image/webp" ->
                "image/webp"

            "image/heic" ->
                "image/heic"

            "image/heif" ->
                "image/heif"

            else ->
                "image/jpeg"
        }
    }
}
