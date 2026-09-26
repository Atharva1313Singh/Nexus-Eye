package com.thirdeye.app.intelligence

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class WikipediaFallback {

    suspend fun search(
        query: String,
        languageId: String
    ): IntelligenceResult {

        return withContext(Dispatchers.IO) {

            try {

                val language =
                    if (languageId == "hi") {
                        "hi"
                    } else {
                        "en"
                    }

                val searchUrl =
                    "https://$language.wikipedia.org/w/api.php" +
                            "?action=query" +
                            "&list=search" +
                            "&srsearch=" +
                            URLEncoder.encode(
                                query,
                                "UTF-8"
                            ) +
                            "&format=json" +
                            "&utf8=1" +
                            "&srlimit=1"

                val searchJson =
                    getJson(searchUrl)
                        ?: return@withContext unknownResult(
                            languageId
                        )

                val searchResults =
                    searchJson
                        .optJSONObject("query")
                        ?.optJSONArray("search")

                if (
                    searchResults == null ||
                    searchResults.length() == 0
                ) {
                    return@withContext unknownResult(
                        languageId
                    )
                }

                val title =
                    searchResults
                        .optJSONObject(0)
                        ?.optString("title")
                        ?.trim()
                        .orEmpty()

                if (title.isBlank()) {
                    return@withContext unknownResult(
                        languageId
                    )
                }

                val summaryUrl =
                    "https://$language.wikipedia.org/api/rest_v1/page/summary/" +
                            URLEncoder.encode(
                                title,
                                "UTF-8"
                            )

                val summaryJson =
                    getJson(summaryUrl)
                        ?: return@withContext unknownResult(
                            languageId
                        )

                val extract =
                    summaryJson
                        .optString("extract")
                        .trim()

                val pageUrl =
                    summaryJson
                        .optJSONObject("content_urls")
                        ?.optJSONObject("desktop")
                        ?.optString("page")
                        .orEmpty()

                if (extract.isBlank()) {
                    return@withContext unknownResult(
                        languageId
                    )
                }

                val answer =
                    if (pageUrl.isBlank()) {
                        extract
                    } else {
                        "$extract\n\nSource: $pageUrl"
                    }

                IntelligenceResult(
                    answer = answer,
                    source = ResponseSource.WIKIPEDIA
                )

            } catch (exception: Exception) {

                IntelligenceResult(
                    answer = if (languageId == "hi") {
                        "ऑनलाइन जानकारी प्राप्त नहीं हो सकी।"
                    } else {
                        "Online information could not be retrieved."
                    },
                    source = ResponseSource.UNKNOWN
                )
            }
        }
    }

    private fun getJson(
        address: String
    ): JSONObject? {

        val connection =
            URL(address)
                .openConnection()
                    as HttpURLConnection

        return try {

            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 7000
            connection.setRequestProperty(
                "User-Agent",
                "NEXUS-EYE/1.0"
            )

            if (
                connection.responseCode
                !in 200..299
            ) {
                null
            } else {

                connection
                    .inputStream
                    .bufferedReader()
                    .use {
                        JSONObject(
                            it.readText()
                        )
                    }
            }

        } finally {
            connection.disconnect()
        }
    }

    private fun unknownResult(
        languageId: String
    ): IntelligenceResult {

        return IntelligenceResult(
            answer = if (languageId == "hi") {
                "मुझे इस प्रश्न का विश्वसनीय स्थानीय या ऑनलाइन उत्तर नहीं मिला।"
            } else {
                "I could not find a reliable local or online answer."
            },
            source = ResponseSource.UNKNOWN
        )
    }
}