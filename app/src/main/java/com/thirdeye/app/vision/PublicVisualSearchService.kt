package com.thirdeye.app.vision

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class PublicVisualSearchEntity(
    val description: String,
    val score: Float
)

data class PublicVisualSearchPage(
    val url: String,
    val pageTitle: String,
    val imageUrl: String
)

data class PublicVisualSearchResult(
    val bestGuessLabels: List<String>,
    val webEntities: List<PublicVisualSearchEntity>,
    val matchingPages: List<PublicVisualSearchPage>,
    val fullMatchingImageUrls: List<String>,
    val partialMatchingImageUrls: List<String>,
    val visuallySimilarImageUrls: List<String>
) {

    fun hasUsefulResult(): Boolean {
        return bestGuessLabels.isNotEmpty() ||
                webEntities.isNotEmpty() ||
                matchingPages.isNotEmpty() ||
                fullMatchingImageUrls.isNotEmpty() ||
                partialMatchingImageUrls.isNotEmpty() ||
                visuallySimilarImageUrls.isNotEmpty()
    }

    fun createSpokenSummary(
        language: String
    ): String {

        val safeLanguage =
            language
                .trim()
                .lowercase(Locale.US)

        val primaryLabel =
            bestGuessLabels
                .firstOrNull()
                ?.trim()
                .orEmpty()

        val primaryEntity =
            webEntities
                .sortedByDescending {
                    it.score
                }
                .firstOrNull()
                ?.description
                ?.trim()
                .orEmpty()

        val primaryPage =
            matchingPages
                .firstOrNull()
                ?.pageTitle
                ?.trim()
                .orEmpty()

        val resultLabel =
            when {
                primaryLabel.isNotBlank() ->
                    primaryLabel

                primaryEntity.isNotBlank() ->
                    primaryEntity

                primaryPage.isNotBlank() ->
                    primaryPage

                else ->
                    ""
            }

        if (resultLabel.isBlank()) {
            return if (
                safeLanguage == "hi"
            ) {
                "ऑनलाइन दृश्य खोज में कोई स्पष्ट परिणाम नहीं मिला।"
            } else {
                "No clear public visual search result was found."
            }
        }

        return if (
            safeLanguage == "hi"
        ) {
            "ऑनलाइन खोज में $resultLabel से संबंधित परिणाम मिले।"
        } else {
            "The online visual search found results related to $resultLabel."
        }
    }
}

class PublicVisualSearchService {

    companion object {

        private const val ENDPOINT =
            "https://vision.googleapis.com/v1/images:annotate"

        private const val USER_AGENT =
            "NexusEye/1.0 Android"

        private const val MAX_IMAGE_BYTES =
            10 * 1024 * 1024

        private const val MAX_RESULTS =
            10

        private const val CONNECT_TIMEOUT_MS =
            20_000

        private const val READ_TIMEOUT_MS =
            45_000
    }

    suspend fun searchImage(
        imageFile: File,
        apiKey: String
    ): Result<PublicVisualSearchResult> {

        return withContext(
            Dispatchers.IO
        ) {

            try {

                if (
                    !imageFile.exists()
                ) {

                    return@withContext Result.failure(
                        IllegalArgumentException(
                            "Image file does not exist."
                        )
                    )
                }

                if (
                    !imageFile.isFile
                ) {

                    return@withContext Result.failure(
                        IllegalArgumentException(
                            "Image path is not a file."
                        )
                    )
                }

                val imageBytes =
                    imageFile.readBytes()

                searchImage(
                    imageBytes = imageBytes,
                    apiKey = apiKey
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

    suspend fun searchImage(
        imageBytes: ByteArray,
        apiKey: String
    ): Result<PublicVisualSearchResult> {

        return withContext(
            Dispatchers.IO
        ) {

            try {

                if (
                    imageBytes.isEmpty()
                ) {

                    return@withContext Result.failure(
                        IllegalArgumentException(
                            "Image data is empty."
                        )
                    )
                }

                if (
                    imageBytes.size >
                    MAX_IMAGE_BYTES
                ) {

                    return@withContext Result.failure(
                        IllegalArgumentException(
                            "Image is too large for online visual search."
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
                            "Google Vision API key is not configured."
                        )
                    )
                }

                val base64Image =
                    Base64.encodeToString(
                        imageBytes,
                        Base64.NO_WRAP
                    )

                val requestBody =
                    createRequestBody(
                        base64Image
                    )

                val response =
                    postRequest(
                        apiKey = cleanApiKey,
                        requestBody = requestBody
                    )

                parseResponse(
                    response
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

    private fun createRequestBody(
        base64Image: String
    ): String {

        val image =
            JSONObject().apply {

                put(
                    "content",
                    base64Image
                )
            }

        val feature =
            JSONObject().apply {

                put(
                    "type",
                    "WEB_DETECTION"
                )

                put(
                    "maxResults",
                    MAX_RESULTS
                )
            }

        val request =
            JSONObject().apply {

                put(
                    "image",
                    image
                )

                put(
                    "features",
                    JSONArray().apply {

                        put(
                            feature
                        )
                    }
                )
            }

        return JSONObject().apply {

            put(
                "requests",
                JSONArray().apply {

                    put(
                        request
                    )
                }
            )

        }.toString()
    }

    private fun postRequest(
        apiKey: String,
        requestBody: String
    ): String {

        val url =
            URL(
                ENDPOINT
            )

        val connection =
            url.openConnection()
                    as HttpURLConnection

        return try {

            connection.requestMethod =
                "POST"

            connection.doOutput =
                true

            connection.connectTimeout =
                CONNECT_TIMEOUT_MS

            connection.readTimeout =
                READ_TIMEOUT_MS

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
                        requestBody
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
                        responseCode,
                        responseText
                    )
                )
            }

            responseText

        } finally {

            connection.disconnect()
        }
    }

    private fun parseResponse(
        response: String
    ): Result<PublicVisualSearchResult> {

        return try {

            val root =
                JSONObject(
                    response
                )

            val responses =
                root.optJSONArray(
                    "responses"
                )

            if (
                responses == null ||
                responses.length() == 0
            ) {

                return Result.failure(
                    IllegalStateException(
                        "Google Vision returned no response."
                    )
                )
            }

            val firstResponse =
                responses.optJSONObject(
                    0
                )
                    ?: return Result.failure(
                        IllegalStateException(
                            "Google Vision returned an invalid response."
                        )
                    )

            val errorObject =
                firstResponse.optJSONObject(
                    "error"
                )

            if (
                errorObject != null
            ) {

                val errorMessage =
                    errorObject.optString(
                        "message",
                        "Google Vision visual search failed."
                    )

                return Result.failure(
                    IllegalStateException(
                        errorMessage
                    )
                )
            }

            val webDetection =
                firstResponse.optJSONObject(
                    "webDetection"
                )

            if (
                webDetection == null
            ) {

                return Result.success(
                    PublicVisualSearchResult(
                        bestGuessLabels =
                            emptyList(),
                        webEntities =
                            emptyList(),
                        matchingPages =
                            emptyList(),
                        fullMatchingImageUrls =
                            emptyList(),
                        partialMatchingImageUrls =
                            emptyList(),
                        visuallySimilarImageUrls =
                            emptyList()
                    )
                )
            }

            val bestGuessLabels =
                parseBestGuessLabels(
                    webDetection
                )

            val webEntities =
                parseWebEntities(
                    webDetection
                )

            val matchingPages =
                parseMatchingPages(
                    webDetection
                )

            val fullMatches =
                parseImageUrls(
                    webDetection.optJSONArray(
                        "fullMatchingImages"
                    )
                )

            val partialMatches =
                parseImageUrls(
                    webDetection.optJSONArray(
                        "partialMatchingImages"
                    )
                )

            val visuallySimilar =
                parseImageUrls(
                    webDetection.optJSONArray(
                        "visuallySimilarImages"
                    )
                )

            Result.success(
                PublicVisualSearchResult(
                    bestGuessLabels =
                        bestGuessLabels,
                    webEntities =
                        webEntities,
                    matchingPages =
                        matchingPages,
                    fullMatchingImageUrls =
                        fullMatches,
                    partialMatchingImageUrls =
                        partialMatches,
                    visuallySimilarImageUrls =
                        visuallySimilar
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

    private fun parseBestGuessLabels(
        webDetection: JSONObject
    ): List<String> {

        val array =
            webDetection.optJSONArray(
                "bestGuessLabels"
            )
                ?: return emptyList()

        val result =
            mutableListOf<String>()

        for (
        index in
        0 until array.length()
        ) {

            val item =
                array.optJSONObject(
                    index
                )
                    ?: continue

            val label =
                item.optString(
                    "label",
                    ""
                )
                    .trim()

            if (
                label.isNotBlank()
            ) {

                result += label
            }
        }

        return result
    }

    private fun parseWebEntities(
        webDetection: JSONObject
    ): List<PublicVisualSearchEntity> {

        val array =
            webDetection.optJSONArray(
                "webEntities"
            )
                ?: return emptyList()

        val result =
            mutableListOf<PublicVisualSearchEntity>()

        for (
        index in
        0 until array.length()
        ) {

            val item =
                array.optJSONObject(
                    index
                )
                    ?: continue

            val description =
                item.optString(
                    "description",
                    ""
                )
                    .trim()

            if (
                description.isBlank()
            ) {
                continue
            }

            val score =
                item.optDouble(
                    "score",
                    0.0
                )
                    .toFloat()

            result +=
                PublicVisualSearchEntity(
                    description =
                        description,
                    score =
                        score
                )
        }

        return result
    }

    private fun parseMatchingPages(
        webDetection: JSONObject
    ): List<PublicVisualSearchPage> {

        val array =
            webDetection.optJSONArray(
                "pagesWithMatchingImages"
            )
                ?: return emptyList()

        val result =
            mutableListOf<PublicVisualSearchPage>()

        for (
        index in
        0 until array.length()
        ) {

            val item =
                array.optJSONObject(
                    index
                )
                    ?: continue

            val url =
                item.optString(
                    "url",
                    ""
                )
                    .trim()

            val pageTitle =
                item.optString(
                    "pageTitle",
                    ""
                )
                    .trim()

            val fullMatchingImage =
                item.optString(
                    "fullMatchingImages",
                    ""
                )
                    .trim()

            if (
                url.isBlank()
            ) {
                continue
            }

            result +=
                PublicVisualSearchPage(
                    url =
                        url,
                    pageTitle =
                        pageTitle,
                    imageUrl =
                        fullMatchingImage
                )
        }

        return result
    }

    private fun parseImageUrls(
        array: JSONArray?
    ): List<String> {

        if (
            array == null
        ) {
            return emptyList()
        }

        val result =
            mutableListOf<String>()

        for (
        index in
        0 until array.length()
        ) {

            val item =
                array.optJSONObject(
                    index
                )
                    ?: continue

            val url =
                item.optString(
                    "url",
                    ""
                )
                    .trim()

            if (
                url.isNotBlank()
            ) {

                result += url
            }
        }

        return result
    }

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
                    "Google Vision rejected the image request."

                401,
                403 ->
                    "Google Vision API authentication was rejected."

                429 ->
                    "Google Vision request limit was reached."

                else ->
                    "Google Vision returned HTTP $responseCode."
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
                    ?.trim()

            if (
                !message.isNullOrBlank()
            ) {

                message

            } else {

                "Google Vision returned HTTP $responseCode."
            }

        } catch (
            _: Exception
        ) {

            "Google Vision returned HTTP $responseCode."
        }
    }
}