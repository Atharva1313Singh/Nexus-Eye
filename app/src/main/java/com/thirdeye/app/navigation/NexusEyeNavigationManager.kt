package com.thirdeye.app.navigation

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import com.thirdeye.app.audio.NexusEyeTtsManager
import com.thirdeye.app.security.NexusEyeApiCredentialStore
import com.thirdeye.app.language.NexusEyeLanguage
import com.thirdeye.app.language.NexusEyeLanguages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

data class NexusEyeResolvedDestination(
    val query: String,
    val displayName: String,
    val latitude: Double,
    val longitude: Double
)

class NexusEyeNavigationManager(
    context: Context
) {

    private val appContext =
        context.applicationContext

    private val scope =
        CoroutineScope(
            SupervisorJob() +
                    Dispatchers.Main.immediate
        )

    private val credentialStore =
        NexusEyeApiCredentialStore(
            appContext
        )

    private val offlineNavigator =
        BRouterOfflineNavigator(
            appContext
        )

    private val onlineRoutingManager =
        NexusEyeOnlineRoutingManager(
            credentialStore
        )

    private val liveSession =
        LiveNavigationSession(
            context =
                appContext,
            offlineNavigator =
                offlineNavigator,
            onlineRoutingManager =
                onlineRoutingManager
        )

    private val ttsManager =
        NexusEyeTtsManager(
            appContext
        )

    private val _status =
        kotlinx.coroutines.flow.MutableStateFlow(
            "Navigation ready."
        )

    val status:
            kotlinx.coroutines.flow.StateFlow<String> =
        _status

            .let {
                it
            }

    private val _destination =
        kotlinx.coroutines.flow.MutableStateFlow<
                NexusEyeResolvedDestination?
                >(null)

    val destination:
            kotlinx.coroutines.flow.StateFlow<
                    NexusEyeResolvedDestination?
                    > =
        _destination

    private val _isResolvingDestination =
        kotlinx.coroutines.flow.MutableStateFlow(
            false
        )

    val isResolvingDestination:
            kotlinx.coroutines.flow.StateFlow<Boolean> =
        _isResolvingDestination

    private val _isNavigating =
        kotlinx.coroutines.flow.MutableStateFlow(
            false
        )

    val isNavigating:
            kotlinx.coroutines.flow.StateFlow<Boolean> =
        _isNavigating

    private var destinationJob:
            Job? =
        null

    private var currentSpeechLanguage:
            NexusEyeLanguage =
        NexusEyeLanguages.English

    init {

        liveSession.onSpeak =
            { message ->

                speak(
                    message
                )
            }

        liveSession.onStatus =
            { state, message ->

                _status.value =
                    message

                _isNavigating.value =
                    when (state) {

                        NexusEyeLiveNavigationState
                            .WAITING_FOR_LOCATION,

                        NexusEyeLiveNavigationState
                            .ROUTING_OFFLINE,

                        NexusEyeLiveNavigationState
                            .NAVIGATING,

                        NexusEyeLiveNavigationState
                            .RECALCULATING -> {

                            true
                        }

                        else -> {

                            false
                        }
                    }
            }
    }

    fun setSpeechLanguage(
        language: NexusEyeLanguage
    ) {

        currentSpeechLanguage =
            language

        try {

            ttsManager.setLanguage(
                language
            )

        } catch (_: Exception) {
        }
    }

    fun navigateTo(
        destinationText: String,
        strideMeters: Double = 0.70
    ) {

        val query =
            destinationText.trim()

        if (
            query.isBlank()
        ) {

            _status.value =
                "Destination is empty."

            speak(
                "Please provide a destination."
            )

            return
        }

        destinationJob?.cancel()

        destinationJob =
            scope.launch {

                _isResolvingDestination.value =
                    true

                _status.value =
                    "Finding destination."

                stopNavigationInternal(
                    announceStop = false
                )

                try {

                    val resolved =
                        resolveDestination(
                            query
                        )

                    _destination.value =
                        resolved

                    _status.value =
                        "Destination found: ${resolved.displayName}"

                    speak(
                        "Starting navigation to ${resolved.displayName}."
                    )

                    liveSession.start(
                        destinationLatitude =
                            resolved.latitude,
                        destinationLongitude =
                            resolved.longitude,
                        strideMeters =
                            strideMeters
                    )

                } catch (
                    exception: Exception
                ) {

                    val message =
                        exception.message
                            ?: "I could not find the destination."

                    _status.value =
                        message

                    speak(
                        message
                    )

                } finally {

                    _isResolvingDestination.value =
                        false
                }
            }
    }

    fun stopNavigation() {

        stopNavigationInternal(
            announceStop = true
        )
    }

    fun navigateToCoordinates(
        latitude: Double,
        longitude: Double,
        displayName: String,
        strideMeters: Double = 0.70
    ) {

        if (
            latitude !in -90.0..90.0 ||
            longitude !in -180.0..180.0
        ) {

            _status.value =
                "Saved Home coordinates are invalid."

            speak(
                _status.value
            )

            return
        }

        destinationJob?.cancel()

        destinationJob =
            scope.launch {

                _isResolvingDestination.value =
                    true

                _status.value =
                    "Starting navigation to $displayName."

                stopNavigationInternal(
                    announceStop = false
                )

                try {

                    val resolved =
                        NexusEyeResolvedDestination(
                            query =
                                "$latitude,$longitude",
                            displayName =
                                displayName,
                            latitude =
                                latitude,
                            longitude =
                                longitude
                        )

                    _destination.value =
                        resolved

                    speak(
                        "Starting navigation to $displayName."
                    )

                    liveSession.start(
                        destinationLatitude =
                            latitude,
                        destinationLongitude =
                            longitude,
                        strideMeters =
                            strideMeters
                    )

                } catch (
                    exception: Exception
                ) {

                    val message =
                        exception.message
                            ?: "I could not start navigation home."

                    _status.value =
                        message

                    speak(
                        message
                    )

                } finally {

                    _isResolvingDestination.value =
                        false
                }
            }
    }

    fun shutdown() {

        destinationJob?.cancel()
        destinationJob = null

        liveSession.shutdown()

        try {

            ttsManager.shutdown()

        } catch (_: Exception) {
        }

        scope.coroutineContext[Job]
            ?.cancel()
    }

    fun currentRoute():
            NexusEyeBRouterRoute? {

        return liveSession.currentRoute
    }

    fun currentLocation():
            Location? {

        return liveSession.currentLocation
    }

    private fun stopNavigationInternal(
        announceStop: Boolean
    ) {

        liveSession.stop(
            announceStop = false
        )

        _isNavigating.value =
            false

        if (
            announceStop
        ) {

            speak(
                "Navigation stopped."
            )
        }

        _status.value =
            "Navigation stopped."
    }

    private suspend fun resolveDestination(
        query: String
    ): NexusEyeResolvedDestination {

        val coordinate =
            parseCoordinateDestination(
                query
            )

        if (
            coordinate != null
        ) {

            return coordinate
        }

        if (
            !Geocoder.isPresent()
        ) {

            throw IllegalStateException(
                "Address search is not available on this device."
            )
        }

        val addresses =
            withTimeout(
                15_000L
            ) {

                withContext(
                    Dispatchers.IO
                ) {

                    geocode(
                        query
                    )
                }
            }

        val address =
            addresses.firstOrNull()
                ?: throw IllegalStateException(
                    "I could not find that destination."
                )

        return NexusEyeResolvedDestination(
            query =
                query,
            displayName =
                buildDisplayName(
                    address,
                    query
                ),
            latitude =
                address.latitude,
            longitude =
                address.longitude
        )
    }

    private suspend fun geocode(
        query: String
    ): List<Address> {

        val geocoder =
            Geocoder(
                appContext
            )

        return if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {

            suspendCancellableCoroutine {
                    continuation ->

                try {

                    geocoder.getFromLocationName(
                        query,
                        5,
                        object :
                            Geocoder.GeocodeListener {

                            override fun onGeocode(
                                addresses:
                                MutableList<Address>
                            ) {

                                if (
                                    continuation.isActive
                                ) {

                                    continuation.resume(
                                        addresses
                                    )
                                }
                            }

                            override fun onError(
                                errorMessage:
                                String?
                            ) {

                                if (
                                    continuation.isActive
                                ) {

                                    continuation.resume(
                                        emptyList()
                                    )
                                }
                            }
                        }
                    )

                } catch (_: Exception) {

                    if (
                        continuation.isActive
                    ) {

                        continuation.resume(
                            emptyList()
                        )
                    }
                }
            }

        } else {

            @Suppress(
                "DEPRECATION"
            )
            try {

                geocoder
                    .getFromLocationName(
                        query,
                        5
                    )
                    ?: emptyList()

            } catch (_: Exception) {

                emptyList()
            }
        }
    }

    private fun parseCoordinateDestination(
        query: String
    ): NexusEyeResolvedDestination? {

        val cleaned =
            query
                .replace(
                    "(",
                    ""
                )
                .replace(
                    ")",
                    ""
                )
                .trim()

        val parts =
            cleaned.split(
                ","
            )

        if (
            parts.size != 2
        ) {

            return null
        }

        val latitude =
            parts[0]
                .trim()
                .toDoubleOrNull()

        val longitude =
            parts[1]
                .trim()
                .toDoubleOrNull()

        if (
            latitude == null ||
            longitude == null
        ) {

            return null
        }

        if (
            latitude !in -90.0..90.0 ||
            longitude !in -180.0..180.0
        ) {

            return null
        }

        return NexusEyeResolvedDestination(
            query =
                query,
            displayName =
                "coordinates $latitude, $longitude",
            latitude =
                latitude,
            longitude =
                longitude
        )
    }

    private fun buildDisplayName(
        address: Address,
        fallback: String
    ): String {

        for (
        index in
        0..address.maxAddressLineIndex
        ) {

            val line =
                address
                    .getAddressLine(
                        index
                    )
                    ?.trim()

            if (
                !line.isNullOrBlank()
            ) {

                return line
            }
        }

        if (
            !address.featureName
                .isNullOrBlank()
        ) {

            return address.featureName
        }

        return fallback
    }

    private fun speak(
        text: String
    ) {

        if (
            text.isBlank()
        ) {

            return
        }

        try {

            ttsManager.setLanguage(
                currentSpeechLanguage
            )

            ttsManager.speakOnPhoneFallback(
                text =
                    text,
                language =
                    currentSpeechLanguage
            )

        } catch (_: Exception) {
        }
    }
}
