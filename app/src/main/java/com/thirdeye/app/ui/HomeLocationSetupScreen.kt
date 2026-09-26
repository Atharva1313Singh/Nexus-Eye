package com.thirdeye.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.thirdeye.app.audio.NexusEyeTtsManager
import com.thirdeye.app.environment.NexusEyeHomeLocation
import com.thirdeye.app.environment.NexusEyeHomeLocationManager
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Composable
fun HomeLocationSetupScreen(
    onComplete: () -> Unit
) {

    val context =
        LocalContext.current

    val homeLocationManager =
        remember {
            NexusEyeHomeLocationManager(
                context
            )
        }

    val ttsManager =
        remember {
            NexusEyeTtsManager(
                context
            )
        }

    var status by remember {
        mutableStateOf(
            HomeSetupStatus.DETECTING
        )
    }

    var detectedAddress by remember {
        mutableStateOf("")
    }

    var detectedLatitude by remember {
        mutableStateOf<Double?>(null)
    }

    var detectedLongitude by remember {
        mutableStateOf<Double?>(null)
    }

    var errorMessage by remember {
        mutableStateOf("")
    }

    fun speak(
        text: String
    ) {

        try {
            ttsManager.speakOnPhoneFallback(
                text
            )
        } catch (
            _: Exception
        ) {
        }
    }

    DisposableEffect(Unit) {

        onDispose {

            try {
                ttsManager.shutdown()
            } catch (
                _: Exception
            ) {
            }
        }
    }

    val locationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->

            val preciseGranted =
                permissions[
                    Manifest.permission.ACCESS_FINE_LOCATION
                ] == true

            val approximateGranted =
                permissions[
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ] == true

            if (
                preciseGranted ||
                approximateGranted
            ) {

                status =
                    HomeSetupStatus.DETECTING

                errorMessage = ""

            } else {

                status =
                    HomeSetupStatus.ERROR

                errorMessage =
                    "Location permission was not granted."

                speak(
                    "Location permission was not granted. Home location cannot be detected."
                )
            }
        }

    LaunchedEffect(Unit) {

        val fineGranted =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        if (
            fineGranted ||
            coarseGranted
        ) {

            detectHomeLocation(
                context = context,
                homeLocationManager = homeLocationManager,
                onLoading = {
                    status =
                        HomeSetupStatus.DETECTING
                },
                onSuccess = { latitude, longitude, address ->

                    detectedLatitude =
                        latitude

                    detectedLongitude =
                        longitude

                    detectedAddress =
                        address

                    status =
                        HomeSetupStatus.CONFIRM

                    speak(
                        "I detected this as your home location: $address. Please confirm whether this is your correct home location."
                    )
                },
                onError = { message ->

                    errorMessage =
                        message

                    status =
                        HomeSetupStatus.ERROR

                    speak(
                        message
                    )
                }
            )

        } else {

            status =
                HomeSetupStatus.NEEDS_PERMISSION

            speak(
                "NEXUS EYE needs location permission to detect your home location."
            )
        }
    }

    Surface(
        modifier =
            Modifier.fillMaxSize(),

        color =
            MaterialTheme
                .colorScheme
                .background
    ) {

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),

            horizontalAlignment =
                Alignment.CenterHorizontally,

            verticalArrangement =
                Arrangement.Center
        ) {

            Text(
                text =
                    "NEXUS EYE",

                style =
                    MaterialTheme
                        .typography
                        .headlineLarge,

                textAlign =
                    TextAlign.Center
            )

            Spacer(
                modifier =
                    Modifier.height(16.dp)
            )

            Text(
                text =
                    "Home Location Setup",

                style =
                    MaterialTheme
                        .typography
                        .headlineSmall,

                textAlign =
                    TextAlign.Center
            )

            Spacer(
                modifier =
                    Modifier.height(16.dp)
            )

            when (
                status
            ) {

                HomeSetupStatus.NEEDS_PERMISSION -> {

                    Text(
                        text =
                            "Location permission is needed to detect and save your home location.",

                        textAlign =
                            TextAlign.Center
                    )

                    Spacer(
                        modifier =
                            Modifier.height(24.dp)
                    )

                    Button(
                        modifier =
                            Modifier.fillMaxWidth(),

                        onClick = {

                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    ) {

                        Text(
                            text =
                                "Allow Location"
                        )
                    }

                    Spacer(
                        modifier =
                            Modifier.height(12.dp)
                    )

                    OutlinedButton(
                        modifier =
                            Modifier.fillMaxWidth(),

                        onClick = {
                            onComplete()
                        }
                    ) {

                        Text(
                            text =
                                "Skip for Now"
                        )
                    }
                }

                HomeSetupStatus.DETECTING -> {

                    CircularProgressIndicator()

                    Spacer(
                        modifier =
                            Modifier.height(20.dp)
                    )

                    Text(
                        text =
                            "Detecting your current location...",

                        textAlign =
                            TextAlign.Center
                    )
                }

                HomeSetupStatus.CONFIRM -> {

                    Text(
                        text =
                            "I detected this as your home location:",

                        textAlign =
                            TextAlign.Center
                    )

                    Spacer(
                        modifier =
                            Modifier.height(12.dp)
                    )

                    Text(
                        text =
                            detectedAddress,

                        style =
                            MaterialTheme
                                .typography
                                .titleMedium,

                        textAlign =
                            TextAlign.Center
                    )

                    Spacer(
                        modifier =
                            Modifier.height(24.dp)
                    )

                    Button(
                        modifier =
                            Modifier.fillMaxWidth(),

                        onClick = {

                            val latitude =
                                detectedLatitude

                            val longitude =
                                detectedLongitude

                            if (
                                latitude != null &&
                                longitude != null &&
                                detectedAddress.isNotBlank()
                            ) {

                                homeLocationManager
                                    .saveHomeLocation(
                                        NexusEyeHomeLocation(
                                            latitude =
                                                latitude,
                                            longitude =
                                                longitude,
                                            address =
                                                detectedAddress
                                        )
                                    )

                                speak(
                                    "Your home location has been saved."
                                )

                                onComplete()
                            }
                        }
                    ) {

                        Text(
                            text =
                                "YES — Save Home"
                        )
                    }

                    Spacer(
                        modifier =
                            Modifier.height(12.dp)
                    )

                    OutlinedButton(
                        modifier =
                            Modifier.fillMaxWidth(),

                        onClick = {

                            speak(
                                "Home location was not saved."
                            )

                            onComplete()
                        }
                    ) {

                        Text(
                            text =
                                "NO — Don't Save"
                        )
                    }
                }

                HomeSetupStatus.ERROR -> {

                    Text(
                        text =
                            errorMessage,

                        textAlign =
                            TextAlign.Center
                    )

                    Spacer(
                        modifier =
                            Modifier.height(20.dp)
                    )

                    Button(
                        modifier =
                            Modifier.fillMaxWidth(),

                        onClick = {

                            status =
                                HomeSetupStatus.DETECTING

                            errorMessage = ""

                            val fineGranted =
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.ACCESS_FINE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED

                            val coarseGranted =
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED

                            if (
                                fineGranted ||
                                coarseGranted
                            ) {

                                status =
                                    HomeSetupStatus.DETECTING

                            } else {

                                status =
                                    HomeSetupStatus.NEEDS_PERMISSION
                            }
                        }
                    ) {

                        Text(
                            text =
                                "Try Again"
                        )
                    }

                    Spacer(
                        modifier =
                            Modifier.height(12.dp)
                    )

                    OutlinedButton(
                        modifier =
                            Modifier.fillMaxWidth(),

                        onClick = {
                            onComplete()
                        }
                    ) {

                        Text(
                            text =
                                "Skip for Now"
                        )
                    }
                }
            }
        }
    }
}

private suspend fun detectCurrentLocation(
    context: Context
): android.location.Location? =
    suspendCoroutine { continuation ->

        try {

            val fusedLocationClient =
                LocationServices
                    .getFusedLocationProviderClient(
                        context
                    )

            val locationManager =
                context.getSystemService(
                    Context.LOCATION_SERVICE
                ) as? LocationManager

            val locationEnabled =
                try {

                    locationManager?.isProviderEnabled(
                        LocationManager.GPS_PROVIDER
                    ) == true ||
                            locationManager?.isProviderEnabled(
                                LocationManager.NETWORK_PROVIDER
                            ) == true

                } catch (
                    _: Exception
                ) {

                    true
                }

            if (!locationEnabled) {

                continuation.resume(
                    null
                )

                return@suspendCoroutine
            }

            val cancellationTokenSource =
                com.google.android.gms.tasks.CancellationTokenSource()

            fusedLocationClient
                .getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cancellationTokenSource.token
                )
                .addOnSuccessListener { location ->

                    continuation.resume(
                        location
                    )
                }
                .addOnFailureListener {

                    fusedLocationClient
                        .lastLocation
                        .addOnSuccessListener { lastLocation ->

                            continuation.resume(
                                lastLocation
                            )
                        }
                        .addOnFailureListener {

                            continuation.resume(
                                null
                            )
                        }
                }

        } catch (
            _: SecurityException
        ) {

            continuation.resume(
                null
            )

        } catch (
            _: Exception
        ) {

            continuation.resume(
                null
            )
        }
    }

private suspend fun detectHomeLocation(
    context: Context,
    homeLocationManager: NexusEyeHomeLocationManager,
    onLoading: () -> Unit,
    onSuccess: (
        latitude: Double,
        longitude: Double,
        address: String
    ) -> Unit,
    onError: (
        message: String
    ) -> Unit
) {

    onLoading()

    val location =
        detectCurrentLocation(
            context
        )

    if (location == null) {

        onError(
            "I could not get your current location. Please make sure location services are enabled."
        )

        return
    }

    val address =
        homeLocationManager.resolveAddress(
            latitude =
                location.latitude,
            longitude =
                location.longitude
        )

    onSuccess(
        location.latitude,
        location.longitude,
        address
    )
}

private enum class HomeSetupStatus {

    NEEDS_PERMISSION,
    DETECTING,
    CONFIRM,
    ERROR
}