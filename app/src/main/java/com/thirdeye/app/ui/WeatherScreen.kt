package com.thirdeye.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.thirdeye.app.audio.NexusEyeAssistantAudioRouter
import com.thirdeye.app.audio.NexusEyeTtsManager
import com.thirdeye.app.bluetooth.NexusEyeBleManager
import com.thirdeye.app.environment.NexusEyeWeather
import com.thirdeye.app.environment.NexusEyeWeatherManager
import com.thirdeye.app.language.NexusEyeLanguage
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun WeatherScreen(
    context: Context,
    bleManager: NexusEyeBleManager,
    ttsManager: NexusEyeTtsManager,
    speechLanguage: NexusEyeLanguage,
    onBack: () -> Unit
) {

    val appContext =
        context.applicationContext

    val weatherManager =
        remember {
            NexusEyeWeatherManager(
                appContext
            )
        }

    val audioRouter =
        remember(bleManager, speechLanguage) {
            NexusEyeAssistantAudioRouter(
                context = appContext,
                bleManager = bleManager,
                speechLanguage = speechLanguage,
                providedTtsManager = ttsManager
            )
        }

    val scope =
        rememberCoroutineScope()

    var weather by remember {
        mutableStateOf<NexusEyeWeather?>(null)
    }

    var loading by remember {
        mutableStateOf(false)
    }

    var errorText by remember {
        mutableStateOf("")
    }

    var hasStartedInitialLoad by remember {
        mutableStateOf(false)
    }

    fun hasLocationPermission(): Boolean {

        val fine =
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        val coarse =
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        return fine || coarse
    }

    fun loadWeather() {

        loading = true
        errorText = ""

        scope.launch {

            try {

                weather =
                    weatherManager
                        .getCurrentWeather()

            } catch (exception: SecurityException) {

                weather = null

                errorText =
                    "Location permission is required."

            } catch (exception: Exception) {

                weather = null

                errorText =
                    exception.message
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?: "Weather is not available right now."

            } finally {

                loading = false
            }
        }
    }

    val locationPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->

            val granted =
                result[
                    Manifest.permission.ACCESS_FINE_LOCATION
                ] == true ||
                        result[
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ] == true

            if (granted) {
                loadWeather()
            } else {
                errorText =
                    "Location permission is required to get local weather."
            }
        }

    fun requestLocationAndLoad() {

        if (hasLocationPermission()) {

            loadWeather()

        } else {

            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    LaunchedEffect(Unit) {

        if (!hasStartedInitialLoad) {

            hasStartedInitialLoad = true

            requestLocationAndLoad()
        }
    }

    LaunchedEffect(
        weather?.currentTemperatureCelsius
    ) {

        val result = weather

        if (result != null) {

            audioRouter.routeText(
                text = result.speechText(speechLanguage.id),
                language = speechLanguage
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(
                rememberScrollState()
            )
            .padding(20.dp),
        verticalArrangement =
            Arrangement.spacedBy(14.dp)
    ) {

        Text(
            text = "NEXUS EYE Weather",
            style =
                MaterialTheme.typography.headlineLarge
        )

        Text(
            text = when {

                loading ->
                    "Getting your current weather..."

                weather != null ->
                    "Current weather"

                else ->
                    "Weather"
            },
            style =
                MaterialTheme.typography.titleMedium
        )

        if (loading) {

            Card(
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    text =
                        "Please wait while NEXUS EYE gets your location and weather.",
                    modifier =
                        Modifier.padding(16.dp)
                )
            }
        }

        val currentWeather =
            weather

        if (currentWeather != null) {

            Card(
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Column(
                    modifier =
                        Modifier.padding(16.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {

                    Text(
                        text =
                            "Condition: ${currentWeather.currentCondition}"
                    )

                    Text(
                        text =
                            String.format(
                                Locale.US,
                                "Temperature: %.1f °C",
                                currentWeather.currentTemperatureCelsius
                            )
                    )

                    Text(
                        text =
                            String.format(
                                Locale.US,
                                "Feels like: %.1f °C",
                                currentWeather.apparentTemperatureCelsius
                            )
                    )

                    Text(
                        text =
                            "Humidity: ${currentWeather.relativeHumidityPercent}%"
                    )

                    Text(
                        text =
                            String.format(
                                Locale.US,
                                "Wind: %.1f km/h",
                                currentWeather.windSpeedKmh
                            )
                    )

                    Text(
                        text =
                            String.format(
                                Locale.US,
                                "Current precipitation: %.1f mm",
                                currentWeather.precipitationMillimeters
                            )
                    )

                    if (
                        currentWeather.todayHighCelsius != null &&
                        currentWeather.todayLowCelsius != null
                    ) {

                        Text(
                            text =
                                String.format(
                                    Locale.US,
                                    "Today's range: %.1f °C to %.1f °C",
                                    currentWeather.todayLowCelsius,
                                    currentWeather.todayHighCelsius
                                )
                        )
                    }

                    if (
                        currentWeather
                            .precipitationProbabilityPercent
                        != null
                    ) {

                        Text(
                            text =
                                "Precipitation probability: " +
                                        "${currentWeather.precipitationProbabilityPercent}%"
                        )
                    }
                }
            }
        }

        if (errorText.isNotBlank()) {

            Card(
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    text = errorText,
                    modifier =
                        Modifier.padding(16.dp),
                    color =
                        MaterialTheme
                            .colorScheme
                            .error
                )
            }
        }

        Button(
            onClick = {
                requestLocationAndLoad()
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth()
        ) {

            Text(
                text =
                    if (loading) {
                        "LOADING"
                    } else {
                        "GET WEATHER"
                    }
            )
        }

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {

            Text(
                text = "BACK"
            )
        }

        Spacer(
            modifier =
                Modifier.height(20.dp)
        )
    }
}