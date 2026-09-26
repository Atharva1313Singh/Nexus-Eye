package com.thirdeye.app.navigation

import android.location.Location
import com.thirdeye.app.security.NexusEyeApiCredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max

class NexusEyeOnlineRoutingManager(
    private val credentialStore: NexusEyeApiCredentialStore
) {

    companion object {
        private const val DIRECTIONS_URL =
            "https://api.heigit.org/openrouteservice/v2/directions/foot-walking"

        private const val REQUEST_TIMEOUT_MS =
            20_000L
    }

    fun isConfigured(): Boolean =
        credentialStore.hasOnlineNavigationApiKey()

    suspend fun calculateRoute(
        start: Location,
        destinationLatitude: Double,
        destinationLongitude: Double
    ): NexusEyeBRouterRoute {

        val apiKey =
            credentialStore.getOnlineNavigationApiKey()
                ?: throw IllegalStateException(
                    "Online navigation is not configured. Add an OpenRouteService API key in Settings."
                )

        return withTimeout(
            REQUEST_TIMEOUT_MS
        ) {
            withContext(
                Dispatchers.IO
            ) {
                requestRoute(
                    apiKey = apiKey,
                    startLatitude = start.latitude,
                    startLongitude = start.longitude,
                    destinationLatitude = destinationLatitude,
                    destinationLongitude = destinationLongitude
                )
            }
        }
    }

    private fun requestRoute(
        apiKey: String,
        startLatitude: Double,
        startLongitude: Double,
        destinationLatitude: Double,
        destinationLongitude: Double
    ): NexusEyeBRouterRoute {

        val connection =
            (
                    URL(
                        DIRECTIONS_URL
                    ).openConnection() as HttpURLConnection
                    ).apply {

                    requestMethod =
                        "POST"

                    connectTimeout =
                        REQUEST_TIMEOUT_MS.toInt()

                    readTimeout =
                        REQUEST_TIMEOUT_MS.toInt()

                    doOutput =
                        true

                    setRequestProperty(
                        "Authorization",
                        apiKey
                    )

                    setRequestProperty(
                        "Content-Type",
                        "application/json"
                    )

                    setRequestProperty(
                        "Accept",
                        "application/geo+json, application/json"
                    )
                }

        try {

            val requestBody =
                JSONObject().apply {

                    put(
                        "coordinates",
                        JSONArray().apply {

                            put(
                                JSONArray().apply {
                                    put(
                                        startLongitude
                                    )
                                    put(
                                        startLatitude
                                    )
                                }
                            )

                            put(
                                JSONArray().apply {
                                    put(
                                        destinationLongitude
                                    )
                                    put(
                                        destinationLatitude
                                    )
                                }
                            )
                        }
                    )

                    put(
                        "instructions",
                        false
                    )

                    put(
                        "geometry",
                        true
                    )
                }

            connection.outputStream.use { output ->

                output.write(
                    requestBody
                        .toString()
                        .toByteArray(
                            Charsets.UTF_8
                        )
                )

                output.flush()
            }

            val responseCode =
                connection.responseCode

            val responseText =
                if (
                    responseCode in 200..299
                ) {

                    connection
                        .inputStream
                        .bufferedReader()
                        .use {
                            it.readText()
                        }

                } else {

                    val errorStream =
                        connection.errorStream

                    if (
                        errorStream != null
                    ) {

                        errorStream
                            .bufferedReader()
                            .use {
                                it.readText()
                            }

                    } else {

                        ""
                    }
                }

            if (
                responseCode !in 200..299
            ) {

                throw IllegalStateException(
                    "Online routing request failed (HTTP $responseCode). " +
                            extractErrorMessage(
                                responseText
                            )
                )
            }

            return parseRoute(
                responseText
            )

        } finally {

            connection.disconnect()
        }
    }

    private fun parseRoute(
        responseText: String
    ): NexusEyeBRouterRoute {

        val root =
            try {

                JSONObject(
                    responseText
                )

            } catch (_: Exception) {

                throw IllegalStateException(
                    "Online routing returned an invalid response."
                )
            }

        val features =
            root.optJSONArray(
                "features"
            )
                ?: throw IllegalStateException(
                    extractErrorMessage(
                        responseText
                    )
                )

        if (
            features.length() == 0
        ) {

            throw IllegalStateException(
                "Online routing returned no route."
            )
        }

        val feature =
            features.optJSONObject(
                0
            )
                ?: throw IllegalStateException(
                    "Online routing returned an invalid route feature."
                )

        val geometry =
            feature.optJSONObject(
                "geometry"
            )
                ?: throw IllegalStateException(
                    "Online routing returned no route geometry."
                )

        val coordinates =
            geometry.optJSONArray(
                "coordinates"
            )
                ?: throw IllegalStateException(
                    "Online routing returned no route coordinates."
                )

        val points =
            ArrayList<NexusEyeBRouterPoint>(
                coordinates.length()
            )

        for (
        index in
        0 until coordinates.length()
        ) {

            val pair =
                coordinates.optJSONArray(
                    index
                )
                    ?: continue

            if (
                pair.length() < 2
            ) {
                continue
            }

            val longitude =
                pair.optDouble(
                    0,
                    Double.NaN
                )

            val latitude =
                pair.optDouble(
                    1,
                    Double.NaN
                )

            if (
                !latitude.isFinite() ||
                !longitude.isFinite()
            ) {
                continue
            }

            if (
                latitude !in -90.0..90.0 ||
                longitude !in -180.0..180.0
            ) {
                continue
            }

            points +=
                NexusEyeBRouterPoint(
                    latitude =
                        latitude,
                    longitude =
                        longitude
                )
        }

        if (
            points.size < 2
        ) {

            throw IllegalStateException(
                "Online routing returned too few route points."
            )
        }

        val distanceMeters =
            calculateRouteDistance(
                points
            )

        if (
            !distanceMeters.isFinite() ||
            distanceMeters <= 0.0
        ) {

            throw IllegalStateException(
                "Online routing returned an invalid route distance."
            )
        }

        return NexusEyeBRouterRoute(
            points =
                points,

            distanceMeters =
                max(
                    0.1,
                    distanceMeters
                )
        )
    }

    private fun calculateRouteDistance(
        points: List<NexusEyeBRouterPoint>
    ): Double {

        var totalDistance =
            0.0

        for (
        index in
        1 until points.size
        ) {

            val previous =
                points[
                    index - 1
                ]

            val current =
                points[
                    index
                ]

            val result =
                FloatArray(
                    1
                )

            Location.distanceBetween(
                previous.latitude,
                previous.longitude,
                current.latitude,
                current.longitude,
                result
            )

            val segmentDistance =
                result[0]
                    .toDouble()

            if (
                segmentDistance.isFinite()
            ) {

                totalDistance +=
                    segmentDistance
            }
        }

        return totalDistance
    }

    private fun extractErrorMessage(
        responseText: String
    ): String {

        if (
            responseText.isBlank()
        ) {

            return "No error details were returned."
        }

        return try {

            val json =
                JSONObject(
                    responseText
                )

            val error =
                json.optJSONObject(
                    "error"
                )

            val message =
                error
                    ?.optString(
                        "message"
                    )
                    .orEmpty()

            if (
                message.isNotBlank()
            ) {

                message

            } else {

                json.optString(
                    "message"
                ).ifBlank {

                    responseText
                        .take(
                            240
                        )
                }
            }

        } catch (_: Exception) {

            responseText
                .replace(
                    Regex(
                        "\\s+"
                    ),
                    " "
                )
                .trim()
                .take(
                    240
                )
        }
    }
}
