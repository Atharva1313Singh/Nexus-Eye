package com.thirdeye.app.environment

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class NexusEyeWeather(
    val latitude: Double,
    val longitude: Double,
    val currentTemperatureCelsius: Double,
    val apparentTemperatureCelsius: Double,
    val relativeHumidityPercent: Int,
    val precipitationMillimeters: Double,
    val windSpeedKmh: Double,
    val currentWeatherCode: Int,
    val currentCondition: String,
    val todayHighCelsius: Double?,
    val todayLowCelsius: Double?,
    val precipitationProbabilityPercent: Int?
) {

    fun speechText(languageId: String): String {
        val cleanLanguageId =
            languageId.trim().lowercase(Locale.ROOT)

        val currentTemperature =
            formatNumber(currentTemperatureCelsius)

        val apparentTemperature =
            formatNumber(apparentTemperatureCelsius)

        val humidity =
            relativeHumidityPercent

        val wind =
            formatNumber(windSpeedKmh)

        val rain =
            formatNumber(precipitationMillimeters)

        val high =
            todayHighCelsius?.let(::formatNumber)

        val low =
            todayLowCelsius?.let(::formatNumber)

        val rainChance =
            precipitationProbabilityPercent

        return when (cleanLanguageId) {

            "hi" -> {
                buildString {

                    append(
                        "आज का वर्तमान तापमान $currentTemperature डिग्री सेल्सियस है। "
                    )

                    append(
                        "मौसम $currentCondition है। "
                    )

                    append(
                        "महसूस होने वाला तापमान $apparentTemperature डिग्री है। "
                    )

                    if (high != null && low != null) {
                        append(
                            "आज का अधिकतम तापमान $high और न्यूनतम तापमान $low डिग्री रहेगा। "
                        )
                    }

                    append(
                        "नमी $humidity प्रतिशत है। "
                    )

                    append(
                        "हवा की गति $wind किलोमीटर प्रति घंटा है। "
                    )

                    if (rainChance != null) {
                        append(
                            "बारिश की संभावना $rainChance प्रतिशत है। "
                        )
                    }

                    append(
                        "वर्तमान वर्षा $rain मिलीमीटर है।"
                    )
                }
            }

            else -> {
                buildString {

                    append(
                        "The current temperature is $currentTemperature degrees Celsius. "
                    )

                    append(
                        "The weather is $currentCondition. "
                    )

                    append(
                        "The feels like temperature is $apparentTemperature degrees. "
                    )

                    if (high != null && low != null) {
                        append(
                            "Today's high is $high degrees and the low is $low degrees. "
                        )
                    }

                    append(
                        "Humidity is $humidity percent. "
                    )

                    append(
                        "Wind speed is $wind kilometers per hour. "
                    )

                    if (rainChance != null) {
                        append(
                            "The chance of precipitation is $rainChance percent. "
                        )
                    }

                    append(
                        "Current precipitation is $rain millimeters."
                    )
                }
            }
        }
    }

    companion object {

        private fun formatNumber(
            value: Double
        ): String {

            return String.format(
                Locale.US,
                "%.1f",
                value
            )
        }
    }
}

class NexusEyeWeatherManager(
    context: Context
) {

    private val appContext =
        context.applicationContext

    private val locationClient:
            FusedLocationProviderClient =
        LocationServices
            .getFusedLocationProviderClient(
                appContext
            )

    suspend fun getCurrentWeather(): NexusEyeWeather {
        return withContext(
            Dispatchers.IO
        ) {

            val location =
                getCurrentLocation()

            requestWeather(
                latitude = location.latitude,
                longitude = location.longitude
            )
        }
    }

    private suspend fun getCurrentLocation(): Location {

        if (!hasLocationPermission()) {
            throw SecurityException(
                "Location permission is required."
            )
        }

        val currentLocation =
            try {

                locationClient
                    .getCurrentLocation(
                        Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                        null
                    )
                    .awaitTask()

            } catch (_: Exception) {

                null
            }

        if (currentLocation != null) {
            return currentLocation
        }

        val lastLocation =
            try {

                locationClient
                    .lastLocation
                    .awaitTask()

            } catch (_: Exception) {

                null
            }

        return lastLocation
            ?: throw IOException(
                "Current location is not available. Set a location in the emulator and try again."
            )
    }

    private fun hasLocationPermission(): Boolean {

        val fineGranted =
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted =
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        return fineGranted || coarseGranted
    }

    private fun requestWeather(
        latitude: Double,
        longitude: Double
    ): NexusEyeWeather {

        val latitudeText =
            String.format(
                Locale.US,
                "%.6f",
                latitude
            )

        val longitudeText =
            String.format(
                Locale.US,
                "%.6f",
                longitude
            )

        val urlString =
            "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=$latitudeText" +
                    "&longitude=$longitudeText" +
                    "&current=" +
                    "temperature_2m," +
                    "relative_humidity_2m," +
                    "apparent_temperature," +
                    "precipitation," +
                    "weather_code," +
                    "wind_speed_10m" +
                    "&daily=" +
                    "temperature_2m_max," +
                    "temperature_2m_min," +
                    "precipitation_probability_max" +
                    "&forecast_days=1" +
                    "&timezone=auto"

        val connection =
            URL(urlString)
                .openConnection() as HttpURLConnection

        try {

            connection.requestMethod = "GET"

            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000

            connection.useCaches = false
            connection.instanceFollowRedirects = true

            val responseCode =
                connection.responseCode

            if (responseCode !in 200..299) {

                throw IOException(
                    "Weather service returned HTTP $responseCode."
                )
            }

            val responseBody =
                connection
                    .inputStream
                    .bufferedReader()
                    .use {
                        it.readText()
                    }

            return parseWeatherResponse(
                responseBody,
                latitude,
                longitude
            )

        } finally {

            connection.disconnect()
        }
    }

    private fun parseWeatherResponse(
        responseBody: String,
        latitude: Double,
        longitude: Double
    ): NexusEyeWeather {

        val root =
            JSONObject(responseBody)

        val current =
            root.optJSONObject("current")
                ?: throw IOException(
                    "Weather service returned no current weather."
                )

        val daily =
            root.optJSONObject("daily")

        val currentTemperature =
            current.optDouble(
                "temperature_2m",
                Double.NaN
            )

        val apparentTemperature =
            current.optDouble(
                "apparent_temperature",
                Double.NaN
            )

        val humidity =
            current.optInt(
                "relative_humidity_2m",
                -1
            )

        val precipitation =
            current.optDouble(
                "precipitation",
                Double.NaN
            )

        val windSpeed =
            current.optDouble(
                "wind_speed_10m",
                Double.NaN
            )

        val weatherCode =
            current.optInt(
                "weather_code",
                -1
            )

        if (
            currentTemperature.isNaN() ||
            apparentTemperature.isNaN() ||
            humidity < 0 ||
            precipitation.isNaN() ||
            windSpeed.isNaN() ||
            weatherCode < 0
        ) {

            throw IOException(
                "Weather data was incomplete."
            )
        }

        val todayHigh =
            daily
                ?.optJSONArray("temperature_2m_max")
                ?.let {
                    if (it.length() > 0) {
                        it.optDouble(
                            0,
                            Double.NaN
                        )
                    } else {
                        Double.NaN
                    }
                }
                ?.takeUnless { it.isNaN() }

        val todayLow =
            daily
                ?.optJSONArray("temperature_2m_min")
                ?.let {
                    if (it.length() > 0) {
                        it.optDouble(
                            0,
                            Double.NaN
                        )
                    } else {
                        Double.NaN
                    }
                }
                ?.takeUnless { it.isNaN() }

        val rainProbability =
            daily
                ?.optJSONArray(
                    "precipitation_probability_max"
                )
                ?.let {
                    if (it.length() > 0) {
                        it.optInt(
                            0,
                            -1
                        )
                    } else {
                        -1
                    }
                }
                ?.takeUnless { it < 0 }

        return NexusEyeWeather(
            latitude = latitude,
            longitude = longitude,
            currentTemperatureCelsius =
                currentTemperature,
            apparentTemperatureCelsius =
                apparentTemperature,
            relativeHumidityPercent =
                humidity,
            precipitationMillimeters =
                precipitation,
            windSpeedKmh =
                windSpeed,
            currentWeatherCode =
                weatherCode,
            currentCondition =
                weatherCodeToDescription(
                    weatherCode
                ),
            todayHighCelsius =
                todayHigh,
            todayLowCelsius =
                todayLow,
            precipitationProbabilityPercent =
                rainProbability
        )
    }

    private fun weatherCodeToDescription(
        code: Int
    ): String {

        return when (code) {

            0 ->
                "clear sky"

            1 ->
                "mainly clear"

            2 ->
                "partly cloudy"

            3 ->
                "overcast"

            45, 48 ->
                "foggy"

            51, 53, 55 ->
                "drizzle"

            56, 57 ->
                "freezing drizzle"

            61, 63, 65 ->
                "rain"

            66, 67 ->
                "freezing rain"

            71, 73, 75 ->
                "snow"

            77 ->
                "snow grains"

            80, 81, 82 ->
                "rain showers"

            85, 86 ->
                "snow showers"

            95 ->
                "thunderstorm"

            96, 99 ->
                "thunderstorm with hail"

            else ->
                "unknown conditions"
        }
    }
}

private suspend fun <T> Task<T>.awaitTask(): T? {

    return suspendCancellableCoroutine { continuation ->

        addOnSuccessListener { value ->

            if (continuation.isActive) {
                continuation.resume(value)
            }
        }

        addOnFailureListener { exception ->

            if (continuation.isActive) {
                continuation.resumeWithException(
                    exception
                )
            }
        }

        addOnCanceledListener {

            if (continuation.isActive) {
                continuation.cancel()
            }
        }
    }
}