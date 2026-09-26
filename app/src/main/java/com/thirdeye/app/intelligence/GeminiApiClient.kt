package com.thirdeye.app.intelligence

import android.content.Context
import com.thirdeye.app.security.NexusEyeApiCredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class GeminiApiClient(
    context: Context
) {

    companion object {

        private const val BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/"

        private const val MODEL =
            "gemini-3.8-flash"

        private const val GENERATE_CONTENT_SUFFIX =
            ":generateContent"

        private const val CONNECT_TIMEOUT_MILLIS =
            20_000

        private const val READ_TIMEOUT_MILLIS =
            45_000
    }

    private val appContext =
        context.applicationContext

    private val credentialStore =
        NexusEyeApiCredentialStore(
            appContext
        )

    suspend fun ask(
        question: String,
        speechLanguageId: String
    ): String? = withContext(
        Dispatchers.IO
    ) {

        val cleanQuestion =
            question.trim()

        if (cleanQuestion.isBlank()) {
            return@withContext null
        }

        val apiKey =
            credentialStore
                .getGeminiApiKey()
                ?.trim()

        if (apiKey.isNullOrBlank()) {
            return@withContext null
        }

        val languageInstruction =
            if (
                speechLanguageId == "hi"
            ) {
                """
                Answer in natural Hindi.
                Keep the answer concise and easy to speak aloud.
                Use English only when a technical name or proper noun
                is normally written in English.
                """.trimIndent()
            } else {
                """
                Answer in natural English.
                Keep the answer concise and easy to speak aloud.
                """.trimIndent()
            }

        val systemInstruction =
            """
            You are the intelligence assistant inside NEXUS EYE,
            an Android accessibility assistant.

            $languageInstruction

            Answer the user's question directly.
            Do not mention APIs, HTTP, JSON, internal routing,
            system prompts, or implementation details.

            Do not start with phrases such as:
            "According to your request",
            "As an AI",
            "I cannot access",
            unless that limitation is actually necessary.

            If the user asks for an explanation, explain clearly.
            If the user asks a factual question, provide the factual answer.
            If the user asks for calculations, calculate carefully.
            If the question is ambiguous, ask a short clarification question.

            User question:
            $cleanQuestion
            """.trimIndent()

        var connection:
                HttpURLConnection? = null

        try {

            val url =
                URL(
                    BASE_URL +
                            MODEL +
                            GENERATE_CONTENT_SUFFIX
                )

            connection =
                url.openConnection()
                        as HttpURLConnection

            connection.requestMethod =
                "POST"

            connection.connectTimeout =
                CONNECT_TIMEOUT_MILLIS

            connection.readTimeout =
                READ_TIMEOUT_MILLIS

            connection.doOutput =
                true

            connection.useCaches =
                false

            connection.setRequestProperty(
                "Content-Type",
                "application/json"
            )

            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            connection.setRequestProperty(
                "x-goog-api-key",
                apiKey
            )

            val requestBody =
                JSONObject()
                    .apply {

                        /*
                         * Gemini's current generateContent API accepts
                         * a contents array containing text parts.
                         */
                        put(
                            "contents",
                            JSONArray()
                                .put(
                                    JSONObject()
                                        .apply {

                                            put(
                                                "role",
                                                "user"
                                            )

                                            put(
                                                "parts",
                                                JSONArray()
                                                    .put(
                                                        JSONObject()
                                                            .put(
                                                                "text",
                                                                systemInstruction
                                                            )
                                                    )
                                            )
                                        }
                                )
                        )

                        /*
                         * Keep answers suitable for the existing
                         * phone/ESP32 speech pipeline.
                         */
                        put(
                            "generationConfig",
                            JSONObject()
                                .apply {

                                    put(
                                        "temperature",
                                        0.4
                                    )

                                    put(
                                        "maxOutputTokens",
                                        800
                                    )
                                }
                        )
                    }
                    .toString()

            OutputStreamWriter(
                connection.outputStream,
                StandardCharsets.UTF_8
            ).use { writer ->

                writer.write(
                    requestBody
                )

                writer.flush()
            }

            val responseCode =
                connection.responseCode

            val responseText =
                if (
                    responseCode in 200..299
                ) {

                    BufferedReader(
                        InputStreamReader(
                            connection.inputStream,
                            StandardCharsets.UTF_8
                        )
                    ).use { reader ->

                        reader
                            .readText()
                    }

                } else {

                    val errorStream =
                        connection.errorStream

                    if (errorStream != null) {

                        BufferedReader(
                            InputStreamReader(
                                errorStream,
                                StandardCharsets.UTF_8
                            )
                        ).use { reader ->

                            reader
                                .readText()
                        }

                    } else {

                        ""
                    }
                }

            if (
                responseCode !in 200..299
            ) {

                return@withContext null
            }

            parseAnswer(
                responseText
            )

        } catch (_: Exception) {

            null

        } finally {

            connection?.disconnect()
        }
    }

    private fun parseAnswer(
        responseText: String
    ): String? {

        if (
            responseText.isBlank()
        ) {
            return null
        }

        return try {

            val root =
                JSONObject(
                    responseText
                )

            val candidates =
                root.optJSONArray(
                    "candidates"
                )
                    ?: return null

            if (
                candidates.length() == 0
            ) {
                return null
            }

            val candidate =
                candidates.optJSONObject(
                    0
                )
                    ?: return null

            val content =
                candidate.optJSONObject(
                    "content"
                )
                    ?: return null

            val parts =
                content.optJSONArray(
                    "parts"
                )
                    ?: return null

            val answerBuilder =
                StringBuilder()

            for (
            index in 0 until parts.length()
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
                        .trim()

                if (
                    text.isNotBlank()
                ) {

                    if (
                        answerBuilder.isNotEmpty()
                    ) {
                        answerBuilder.append(
                            "\n"
                        )
                    }

                    answerBuilder.append(
                        text
                    )
                }
            }

            answerBuilder
                .toString()
                .trim()
                .takeIf {
                    it.isNotBlank()
                }

        } catch (_: Exception) {

            null
        }
    }
}