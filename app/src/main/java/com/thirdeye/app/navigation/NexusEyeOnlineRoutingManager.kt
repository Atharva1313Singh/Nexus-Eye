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

class NexusEyeOnlineRoutingManager(
    private val credentialStore: NexusEyeApiCredentialStore
) {

    companion object {

        private const val DIRECTIONS_URL =
            "https://api.heigit.org/openrouteservice/v2/directions/foot-walking"

        private const val REQUEST_TIMEOUT_MS =
            20_000L
    }

    fun isConfigured(): Boolean {
        return credentialStore.hasOnlineNavigationApiKey()
    }

    suspend fun calculateRoute(
        start: Location,
        destinationLatitude: Double,
        destinationLongitude: Double
    ): NexusEyeBRouterRoute {

        require(start.latitude.isFinite()) {
            "Current latitude is invalid."
        }

        require(start.longitude.isFinite()) {
            "Current longitude is invalid."
        }

        require(destinationLatitude.isFinite()) {
            "Destination latitude is invalid."
        }

        require(destinationLongitude.isFinite()) {
            "Destination longitude is invalid."
        }

        val apiKey =
            credentialStore.getOnlineNavigationApiKey()
                ?: throw IllegalStateException(
                    "Online navigation API key is not configured."
                )

        val body = JSONObject().apply {
            put(
                "coordinates",
                JSONArray().apply {
                    put(
                        JSONArray().apply {
                            put(start.longitude)
                            put(start.latitude)
                        }
                    )
                    put(
                        JSONArray().apply {
                            put(destinationLongitude)
                            put(destinationLatitude)
                        }
                    )
                }
            )
            put("instructions", false)
            put("geometry", true)
        }.toString()

        val response =
            withTimeout(REQUEST_TIMEOUT_MS) {
                withContext(Dispatchers.IO) {
                    request(
                        apiKey = apiKey,
                        body = body
                    )
                }
            }

        return parseRoute(response)
    }

    private fun request(
        apiKey: String,
        body: String
    ): String {

        val connection =
            (URL(DIRECTIONS_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = REQUEST_TIMEOUT_MS.toInt()
                readTimeout = REQUEST_TIMEOUT_MS.toInt()
                doOutput = true
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

        return try {
            connection.outputStream.use { output ->
                output.write(
                    body.toByteArray(Charsets.UTF_8)
                )
            }

            val statusCode =
                connection.responseCode

            val stream =
                if (statusCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

            val responseText =
                stream?.bufferedReader(Charsets.UTF_8)?.use {
                    it.readText()
                }.orEmpty()

            if (statusCode !in 200..299) {
                val detail =
                    extractErrorMessage(responseText)

                throw IllegalStateException(
                    if (detail.isNullOrBlank()) {
                        "Online routing request failed with HTTP $statusCode."
                    } else {
                        "Online routing request failed: $detail"
                    }
                )
            }

            if (responseText.isBlank()) {
                throw IllegalStateException(
                    "Online routing returned an empty response."
                )
            }

            responseText

        } finally {
            connection.disconnect()
        }
    }

    private fun parseRoute(
        responseText: String
    ): NexusEyeBRouterRoute {

        val root =
            try {
                JSONObject(responseText)
            } catch (exception: Exception) {
                throw IllegalStateException(
                    "Online routing returned invalid JSON.",
                    exception
                )
            }

        val features =
            root.optJSONArray("features")
                ?: throw IllegalStateException(
                    "Online routing response contains no route features."
                )

        if (features.length() == 0) {
            throw IllegalStateException(
                "Online routing returned no route."
            )
        }

        val feature =
            features.optJSONObject(0)
                ?: throw IllegalStateException(
                    "Online routing returned an invalid route feature."
                )

        val geometry =
            feature.optJSONObject("geometry")
                ?: throw IllegalStateException(
                    "Online routing response contains no route geometry."
                )

        val coordinates =
            geometry.optJSONArray("coordinates")
                ?: throw IllegalStateException(
                    "Online routing response contains no route coordinates."
                )

        val points =
            ArrayList<NexusEyeBRouterPoint>()

        for (index in 0 until coordinates.length()) {
            val coordinate =
                coordinates.optJSONArray(index)
                    ?: continue

            if (coordinate.length() < 2) {
                continue
            }

            val longitude =
                coordinate.optDouble(0, Double.NaN)

            val latitude =
                coordinate.optDouble(1, Double.NaN)

            if (
                longitude.isFinite() &&
                latitude.isFinite() &&
                latitude in -90.0..90.0 &&
                longitude in -180.0..180.0
            ) {
                points +=
                    NexusEyeBRouterPoint(
                        latitude = latitude,
                        longitude = longitude
                    )
            }
        }

        if (points.size < 2) {
            throw IllegalStateException(
                "Online routing returned too few route points."
            )
        }

        val summary =
            feature.optJSONObject("properties")
                ?.optJSONObject("summary")

        val reportedDistance =
            summary?.optDouble(
                "distance",
                Double.NaN
            ) ?: Double.NaN

        val distance =
            if (reportedDistance.isFinite() && reportedDistance > 0.0) {
                reportedDistance
            } else {
                calculateRouteDistance(points)
            }

        if (!distance.isFinite() || distance <= 0.0) {
            throw IllegalStateException(
                "Online routing returned an invalid route distance."
            )
        }

        return NexusEyeBRouterRoute(
            points = points,
            distanceMeters = distance
        )
    }

    private fun calculateRouteDistance(
        points: List<NexusEyeBRouterPoint>
    ): Double {

        var total = 0.0

        for (index in 1 until points.size) {
            val result = FloatArray(1)

            Location.distanceBetween(
                points[index - 1].latitude,
                points[index - 1].longitude,
                points[index].latitude,
                points[index].longitude,
                result
            )

            total += result[0].toDouble()
        }

        return total
    }

    private fun extractErrorMessage(
        responseText: String
    ): String? {

        if (responseText.isBlank()) {
            return null
        }

        return try {
            val json = JSONObject(responseText)

            json.optString("error", null)
                ?.takeIf { it.isNotBlank() }
                ?: json.optString("message", null)
                    ?.takeIf { it.isNotBlank() }
                ?: json.optJSONObject("error")
                    ?.optString("message", null)
                    ?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            responseText
                .trim()
                .replace(Regex("\\s+"), " ")
                .take(240)
        }
    }
}
