package com.thirdeye.app.navigation

import com.thirdeye.app.navigation.NexusEyeNavigationPreferences

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

enum class NexusEyeLiveNavigationState {
    IDLE,
    WAITING_FOR_LOCATION,
    ROUTING_OFFLINE,
    ROUTING_ONLINE,
    NAVIGATING,
    RECALCULATING,
    ARRIVED,
    STOPPED,
    ERROR
}

private enum class InternalDirection {
    START,
    STRAIGHT,
    SLIGHT_LEFT,
    LEFT,
    SHARP_LEFT,
    SLIGHT_RIGHT,
    RIGHT,
    SHARP_RIGHT,
    ARRIVE
}

private data class InternalInstruction(
    val pointIndex: Int,
    val direction: InternalDirection
)

class LiveNavigationSession(
    context: Context,
    private val offlineNavigator:
    BRouterOfflineNavigator,
    private val onlineRoutingManager:
    NexusEyeOnlineRoutingManager
) {

    companion object {

        private const val ARRIVAL_DISTANCE_METERS =
            10.0

        private const val OFF_ROUTE_DISTANCE_METERS =
            35.0

        private const val INSTRUCTION_TRIGGER_DISTANCE_METERS =
            25.0

        private const val LOCATION_INTERVAL_MS =
            1_000L

        private const val LOCATION_MIN_INTERVAL_MS =
            500L
    }

    private val appContext =
        context.applicationContext

    private val navigationPreferences =
        NexusEyeNavigationPreferences(
            appContext
        )

    private val locationClient:
            FusedLocationProviderClient =
        LocationServices
            .getFusedLocationProviderClient(
                appContext
            )

    private val scope =
        CoroutineScope(
            SupervisorJob() +
                    Dispatchers.Main.immediate
        )

    private var navigationJob:
            Job? =
        null

    private var recalculationJob:
            Job? =
        null

    private var route:
            NexusEyeBRouterRoute? =
        null

    private var destinationLatitude =
        0.0

    private var destinationLongitude =
        0.0

    private var strideMeters =
        0.70

    private var instructions:
            List<InternalInstruction> =
        emptyList()

    private var nextInstructionIndex =
        0

    private var lastSpokenInstructionIndex =
        -1

    private var lastLocation:
            Location? =
        null

    private var recalculating =
        false

    private var internalState =
        NexusEyeLiveNavigationState.IDLE

    var onSpeak:
            ((String) -> Unit)? =
        null

    var onStatus:
            ((NexusEyeLiveNavigationState, String) -> Unit)? =
        null

    var onLocationChanged:
            ((Location) -> Unit)? =
        null

    val currentLocation:
            Location?
        get() = lastLocation

    val currentRoute:
            NexusEyeBRouterRoute?
        get() = route

    private val locationCallback =
        object :
            LocationCallback() {

            override fun onLocationResult(
                result: LocationResult
            ) {

                val location =
                    result.lastLocation
                        ?: return

                lastLocation =
                    location

                onLocationChanged?.invoke(
                    location
                )

                scope.launch {

                    processLocation(
                        location
                    )
                }
            }
        }

    @SuppressLint("MissingPermission")
    fun start(
        destinationLatitude: Double,
        destinationLongitude: Double,
        @Suppress("UNUSED_PARAMETER")
        strideMeters: Double
    ) {

        startWithConfiguredStride(
            destinationLatitude = destinationLatitude,
            destinationLongitude = destinationLongitude
        )
    }

    @SuppressLint("MissingPermission")
    fun start(
        destinationLatitude: Double,
        destinationLongitude: Double
    ) {

        startWithConfiguredStride(
            destinationLatitude = destinationLatitude,
            destinationLongitude = destinationLongitude
        )
    }

    @SuppressLint("MissingPermission")
    private fun startWithConfiguredStride(
        destinationLatitude: Double,
        destinationLongitude: Double
    ) {

        val configuredStrideMeters =
            navigationPreferences
                .getStrideLengthMeters()
                .toDouble()

        require(
            configuredStrideMeters.isFinite() &&
                    configuredStrideMeters >= 0.30 &&
                    configuredStrideMeters <= 2.00
        ) {
            "Configured stride must be between 0.30 and 2.00 meters."
        }

        stop(
            announceStop = false
        )

        this.destinationLatitude =
            destinationLatitude

        this.destinationLongitude =
            destinationLongitude

        this.strideMeters =
            strideMeters

        route =
            null

        instructions =
            emptyList()

        nextInstructionIndex =
            0

        lastSpokenInstructionIndex =
            -1

        updateState(
            NexusEyeLiveNavigationState
                .WAITING_FOR_LOCATION,
            "Waiting for current location."
        )

        navigationJob =
            scope.launch {

                try {

                    if (
                        !hasLocationPermission()
                    ) {

                        updateState(
                            NexusEyeLiveNavigationState.ERROR,
                            "Location permission is required for navigation."
                        )

                        return@launch
                    }

                    val location =
                        obtainCurrentLocation()
                            ?: run {

                                updateState(
                                    NexusEyeLiveNavigationState.ERROR,
                                    "Could not obtain the current location."
                                )

                                return@launch
                            }

                    lastLocation =
                        location

                    onLocationChanged?.invoke(
                        location
                    )

                    updateState(
                        NexusEyeLiveNavigationState
                            .ROUTING_OFFLINE,
                        "Calculating offline route."
                    )

                    try {
                        createRoute(
                            location
                        )

                        startLocationUpdates()

                        speak(
                            "Offline navigation started."
                        )
                    } catch (offlineException: Exception) {
                        updateState(
                            NexusEyeLiveNavigationState
                                .ROUTING_ONLINE,
                            "Offline routing failed. Trying online navigation."
                        )

                        speak(
                            "Offline routing failed. Trying online navigation."
                        )

                        try {
                            createOnlineRoute(
                                location
                            )

                            startLocationUpdates()

                            speak(
                                "Online navigation started."
                            )
                        } catch (onlineException: Exception) {
                            throw IllegalStateException(
                                "Offline routing failed and online navigation failed: ${onlineException.message ?: "unknown online routing error"}",
                                offlineException
                            )
                        }
                    }

                    processLocation(
                        location
                    )

                } catch (
                    securityException:
                    SecurityException
                ) {

                    updateState(
                        NexusEyeLiveNavigationState.ERROR,
                        "Location permission was not granted."
                    )

                } catch (
                    exception: Exception
                ) {

                    updateState(
                        NexusEyeLiveNavigationState.ERROR,
                        exception.message
                            ?: "Navigation could not be started."
                    )
                }
            }
    }

    fun stop(
        announceStop: Boolean = true
    ) {

        navigationJob?.cancel()
        navigationJob = null

        recalculationJob?.cancel()
        recalculationJob = null

        removeLocationUpdates()

        recalculating =
            false

        route =
            null

        instructions =
            emptyList()

        nextInstructionIndex =
            0

        lastSpokenInstructionIndex =
            -1

        if (
            announceStop
        ) {

            speak(
                "Navigation stopped."
            )
        }

        updateState(
            NexusEyeLiveNavigationState.STOPPED,
            "Navigation stopped."
        )
    }

    fun shutdown() {

        stop(
            announceStop = false
        )

        onSpeak =
            null

        onStatus =
            null

        onLocationChanged =
            null

        scope.coroutineContext[Job]
            ?.cancel()
    }

    private suspend fun createRoute(
        currentLocation: Location
    ) {

        val newRoute =
            withContext(
                Dispatchers.IO
            ) {

                offlineNavigator
                    .calculateRoute(
                        start =
                            currentLocation,
                        destinationLatitude =
                            destinationLatitude,
                        destinationLongitude =
                            destinationLongitude
                    )
            }

        if (
            newRoute.points.size < 2
        ) {

            throw IllegalStateException(
                "Offline route contains too few points."
            )
        }

        route =
            newRoute

        instructions =
            buildInstructions(
                newRoute.points
            )

        nextInstructionIndex =
            0

        lastSpokenInstructionIndex =
            -1

        updateState(
            NexusEyeLiveNavigationState.NAVIGATING,
            "Offline navigation is active."
        )
    }

    private suspend fun createOnlineRoute(
        currentLocation: Location
    ) {

        val newRoute =
            withContext(
                Dispatchers.IO
            ) {
                onlineRoutingManager.calculateRoute(
                    start = currentLocation,
                    destinationLatitude = destinationLatitude,
                    destinationLongitude = destinationLongitude
                )
            }

        if (newRoute.points.size < 2) {
            throw IllegalStateException(
                "Online route contains too few points."
            )
        }

        route = newRoute

        instructions =
            buildInstructions(
                newRoute.points
            )

        nextInstructionIndex = 0
        lastSpokenInstructionIndex = -1

        updateState(
            NexusEyeLiveNavigationState.NAVIGATING,
            "Online navigation is active."
        )
    }

    private suspend fun processLocation(
        location: Location
    ) {

        val state =
            internalState

        if (
            state !=
            NexusEyeLiveNavigationState.NAVIGATING &&
            state !=
            NexusEyeLiveNavigationState.RECALCULATING
        ) {

            return
        }

        val destinationDistance =
            distanceToDestination(
                location
            )

        if (
            destinationDistance <=
            ARRIVAL_DISTANCE_METERS
        ) {

            handleArrival()
            return
        }

        val activeRoute =
            route
                ?: return

        val routeDistance =
            nearestRouteDistance(
                location,
                activeRoute.points
            )

        if (
            routeDistance >
            OFF_ROUTE_DISTANCE_METERS &&
            !recalculating
        ) {

            requestRecalculation(
                location
            )

            return
        }

        advanceInstructions(
            location,
            activeRoute.points
        )
    }

    private fun requestRecalculation(
        location: Location
    ) {

        if (
            recalculating
        ) {

            return
        }

        recalculating =
            true

        updateState(
            NexusEyeLiveNavigationState
                .RECALCULATING,
            "Off route. Recalculating offline."
        )

        speak(
            "You are off route. Recalculating."
        )

        recalculationJob =
            scope.launch {

                try {

                    var usedOnlineFallback =
                        false

                    val newRoute =
                        try {
                            withContext(
                                Dispatchers.IO
                            ) {
                                offlineNavigator.calculateRoute(
                                    start = location,
                                    destinationLatitude = destinationLatitude,
                                    destinationLongitude = destinationLongitude
                                )
                            }
                        } catch (offlineException: Exception) {
                            usedOnlineFallback =
                                true

                            updateState(
                                NexusEyeLiveNavigationState
                                    .ROUTING_ONLINE,
                                "Offline recalculation failed. Trying online navigation."
                            )

                            speak(
                                "Offline recalculation failed. Trying online navigation."
                            )

                            try {
                                withContext(
                                    Dispatchers.IO
                                ) {
                                    onlineRoutingManager.calculateRoute(
                                        start = location,
                                        destinationLatitude = destinationLatitude,
                                        destinationLongitude = destinationLongitude
                                    )
                                }
                            } catch (onlineException: Exception) {
                                throw IllegalStateException(
                                    "Offline recalculation failed and online navigation failed: ${onlineException.message ?: "unknown online routing error"}",
                                    offlineException
                                )
                            }
                        }

                    route =
                        newRoute

                    instructions =
                        buildInstructions(
                            newRoute.points
                        )

                    nextInstructionIndex =
                        0

                    lastSpokenInstructionIndex =
                        -1

                    recalculating =
                        false

                    updateState(
                        NexusEyeLiveNavigationState
                            .NAVIGATING,
                        if (usedOnlineFallback) {
                            "Online route active."
                        } else {
                            "Route recalculated."
                        }
                    )

                    speak(
                        if (usedOnlineFallback) {
                            "Online route ready. Continue."
                        } else {
                            "Route recalculated. Continue."
                        }
                    )

                    processLocation(
                        location
                    )

                } catch (
                    exception: Exception
                ) {

                    recalculating =
                        false

                    updateState(
                        NexusEyeLiveNavigationState.ERROR,
                        exception.message
                            ?: "Route recalculation failed."
                    )

                    speak(
                        "I could not recalculate the route offline or online."
                    )
                } finally {

                    recalculationJob =
                        null
                }
            }
    }

    private fun advanceInstructions(
        location: Location,
        points: List<NexusEyeBRouterPoint>
    ) {

        if (
            instructions.isEmpty()
        ) {

            return
        }

        while (
            nextInstructionIndex <
            instructions.lastIndex
        ) {

            val nextIndex =
                nextInstructionIndex + 1

            val instruction =
                instructions[
                    nextIndex
                ]

            val point =
                points[
                    instruction.pointIndex
                ]

            val distance =
                distanceBetween(
                    location.latitude,
                    location.longitude,
                    point.latitude,
                    point.longitude
                )

            if (
                distance <=
                INSTRUCTION_TRIGGER_DISTANCE_METERS
            ) {

                nextInstructionIndex =
                    nextIndex

                if (
                    lastSpokenInstructionIndex !=
                    nextIndex
                ) {

                    speakInstruction(
                        instruction,
                        location,
                        points
                    )

                    lastSpokenInstructionIndex =
                        nextIndex
                }

            } else {

                break
            }
        }
    }

    private fun speakInstruction(
        instruction: InternalInstruction,
        currentLocation: Location,
        points: List<NexusEyeBRouterPoint>
    ) {

        when (
            instruction.direction
        ) {

            InternalDirection.START -> {

                speak(
                    "Continue straight."
                )
            }

            InternalDirection.ARRIVE -> {

                speak(
                    "You have arrived at your destination."
                )
            }

            else -> {

                val point =
                    points[
                        instruction.pointIndex
                    ]

                val distance =
                    distanceBetween(
                        currentLocation.latitude,
                        currentLocation.longitude,
                        point.latitude,
                        point.longitude
                    )

                val steps =
                    estimateSteps(
                        distanceMeters = distance
                    )

                val directionText =
                    directionText(
                        instruction.direction
                    )

                if (
                    distance <= 3.0
                ) {

                    speak(
                        "$directionText now. About $steps steps."
                    )

                } else {

                    speak(
                        "$directionText in about ${distance.toInt().coerceAtLeast(1)} meters. About $steps steps."
                    )
                }
            }
        }
    }

    private fun handleArrival() {

        removeLocationUpdates()

        navigationJob?.cancel()
        navigationJob = null

        updateState(
            NexusEyeLiveNavigationState.ARRIVED,
            "Destination reached."
        )

        speak(
            "You have arrived at your destination."
        )
    }

    private fun buildInstructions(
        points: List<NexusEyeBRouterPoint>
    ): List<InternalInstruction> {

        if (
            points.size < 2
        ) {

            return emptyList()
        }

        val result =
            ArrayList<InternalInstruction>()

        result +=
            InternalInstruction(
                pointIndex = 0,
                direction =
                    InternalDirection.START
            )

        for (
        index in 1 until points.lastIndex
        ) {

            val before =
                points[
                    index - 1
                ]

            val current =
                points[
                    index
                ]

            val after =
                points[
                    index + 1
                ]

            val incomingBearing =
                bearingBetween(
                    before,
                    current
                )

            val outgoingBearing =
                bearingBetween(
                    current,
                    after
                )

            val delta =
                normalizeBearingDifference(
                    outgoingBearing -
                            incomingBearing
                )

            if (
                kotlin.math.abs(delta) >=
                25.0
            ) {

                result +=
                    InternalInstruction(
                        pointIndex =
                            index,
                        direction =
                            directionFromDelta(
                                delta
                            )
                    )
            }
        }

        result +=
            InternalInstruction(
                pointIndex =
                    points.lastIndex,
                direction =
                    InternalDirection.ARRIVE
            )

        return result
    }

    private fun directionFromDelta(
        delta: Double
    ): InternalDirection {

        return when {

            delta >= 135.0 ->
                InternalDirection.SHARP_RIGHT

            delta >= 70.0 ->
                InternalDirection.RIGHT

            delta >= 25.0 ->
                InternalDirection.SLIGHT_RIGHT

            delta <= -135.0 ->
                InternalDirection.SHARP_LEFT

            delta <= -70.0 ->
                InternalDirection.LEFT

            else ->
                InternalDirection.SLIGHT_LEFT
        }
    }

    private fun estimateSteps(
        distanceMeters: Double
    ): Int {

        if (
            !distanceMeters.isFinite() ||
            distanceMeters <= 0.0
        ) {
            return 1
        }

        return max(
            1,
            ceil(
                distanceMeters /
                        strideMeters
            ).toInt()
        )
    }

    private fun directionText(
        direction: InternalDirection
    ): String {

        return when (direction) {

            InternalDirection.SLIGHT_LEFT ->
                "Slightly left"

            InternalDirection.LEFT ->
                "Turn left"

            InternalDirection.SHARP_LEFT ->
                "Sharp left"

            InternalDirection.SLIGHT_RIGHT ->
                "Slightly right"

            InternalDirection.RIGHT ->
                "Turn right"

            InternalDirection.SHARP_RIGHT ->
                "Sharp right"

            InternalDirection.STRAIGHT ->
                "Continue straight"

            else ->
                "Continue"
        }
    }

    private fun nearestRouteDistance(
        location: Location,
        points: List<NexusEyeBRouterPoint>
    ): Double {

        var minimum =
            Double.MAX_VALUE

        for (
        point in points
        ) {

            val distance =
                distanceBetween(
                    location.latitude,
                    location.longitude,
                    point.latitude,
                    point.longitude
                )

            minimum =
                min(
                    minimum,
                    distance
                )
        }

        return minimum
    }

    private fun distanceToDestination(
        location: Location
    ): Double {

        return distanceBetween(
            location.latitude,
            location.longitude,
            destinationLatitude,
            destinationLongitude
        )
    }

    private fun distanceBetween(
        latitude1: Double,
        longitude1: Double,
        latitude2: Double,
        longitude2: Double
    ): Double {

        val result =
            FloatArray(1)

        Location.distanceBetween(
            latitude1,
            longitude1,
            latitude2,
            longitude2,
            result
        )

        return result[0]
            .toDouble()
    }

    private fun bearingBetween(
        first: NexusEyeBRouterPoint,
        second: NexusEyeBRouterPoint
    ): Double {

        val result =
            FloatArray(2)

        Location.distanceBetween(
            first.latitude,
            first.longitude,
            second.latitude,
            second.longitude,
            result
        )

        return (
                result[1].toDouble() +
                        360.0
                ) % 360.0
    }

    private fun normalizeBearingDifference(
        value: Double
    ): Double {

        var result =
            value % 360.0

        if (
            result > 180.0
        ) {

            result -=
                360.0
        }

        if (
            result < -180.0
        ) {

            result +=
                360.0
        }

        return result
    }

    private fun hasLocationPermission():
            Boolean {

        val fineGranted =
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) ==
                    PackageManager.PERMISSION_GRANTED

        val coarseGranted =
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) ==
                    PackageManager.PERMISSION_GRANTED

        return fineGranted ||
                coarseGranted
    }

    @SuppressLint("MissingPermission")
    private suspend fun obtainCurrentLocation():
            Location? {

        val lastKnown =
            suspendCancellableCoroutine<Location?> {
                    continuation ->

                try {

                    locationClient
                        .lastLocation
                        .addOnSuccessListener {
                                location ->

                            if (
                                continuation.isActive
                            ) {

                                continuation.resume(
                                    location
                                )
                            }
                        }
                        .addOnFailureListener {

                            if (
                                continuation.isActive
                            ) {

                                continuation.resume(
                                    null
                                )
                            }
                        }

                } catch (
                    securityException:
                    SecurityException
                ) {

                    if (
                        continuation.isActive
                    ) {

                        continuation
                            .resumeWithException(
                                securityException
                            )
                    }
                }
            }

        if (
            lastKnown != null
        ) {

            return lastKnown
        }

        return requestSingleLocation()
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestSingleLocation():
            Location? {

        return suspendCancellableCoroutine {
                continuation ->

            val callback =
                object :
                    LocationCallback() {

                    override fun onLocationResult(
                        result: LocationResult
                    ) {

                        removeLocationUpdates(
                            this
                        )

                        if (
                            continuation.isActive
                        ) {

                            continuation.resume(
                                result.lastLocation
                            )
                        }
                    }
                }

            try {

                val request =
                    LocationRequest.Builder(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        LOCATION_INTERVAL_MS
                    )
                        .setMinUpdateIntervalMillis(
                            LOCATION_MIN_INTERVAL_MS
                        )
                        .setWaitForAccurateLocation(
                            false
                        )
                        .build()

                locationClient
                    .requestLocationUpdates(
                        request,
                        callback,
                        Looper.getMainLooper()
                    )

                continuation
                    .invokeOnCancellation {

                        removeLocationUpdates(
                            callback
                        )
                    }

            } catch (
                securityException:
                SecurityException
            ) {

                if (
                    continuation.isActive
                ) {

                    continuation
                        .resumeWithException(
                            securityException
                        )
                }

            } catch (
                exception: Exception
            ) {

                if (
                    continuation.isActive
                ) {

                    continuation.resume(
                        null
                    )
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {

        if (
            !hasLocationPermission()
        ) {

            throw SecurityException(
                "Location permission is not granted."
            )
        }

        val request =
            LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                LOCATION_INTERVAL_MS
            )
                .setMinUpdateIntervalMillis(
                    LOCATION_MIN_INTERVAL_MS
                )
                .setWaitForAccurateLocation(
                    false
                )
                .build()

        locationClient
            .requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper()
            )
    }

    private fun removeLocationUpdates() {

        try {

            locationClient
                .removeLocationUpdates(
                    locationCallback
                )

        } catch (_: Exception) {
        }
    }

    private fun removeLocationUpdates(
        callback: LocationCallback
    ) {

        try {

            locationClient
                .removeLocationUpdates(
                    callback
                )

        } catch (_: Exception) {
        }
    }

    private fun speak(
        text: String
    ) {

        if (
            text.isNotBlank()
        ) {

            onSpeak?.invoke(
                text
            )
        }
    }

    private fun updateState(
        state:
        NexusEyeLiveNavigationState,
        message: String
    ) {

        internalState =
            state

        onStatus?.invoke(
            state,
            message
        )
    }
}