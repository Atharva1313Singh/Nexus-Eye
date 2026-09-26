package com.thirdeye.app.ui

import android.content.Context
import android.location.Location
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.thirdeye.app.navigation.NexusEyeBRouterPoint
import com.thirdeye.app.navigation.NexusEyeBRouterRoute
import com.thirdeye.app.navigation.NexusEyeNavigationManager
import kotlinx.coroutines.delay
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import android.preference.PreferenceManager

@Composable
fun NavigationScreen(
    savedHomeAddress: String?,
    navigationManager: NexusEyeNavigationManager,
    onStartNavigation: (String) -> Unit,
    onGoHome: () -> Boolean,
    onBack: () -> Unit
) {
    val context =
        LocalContext.current

    var destination by remember {
        mutableStateOf("")
    }

    var statusText by remember {
        mutableStateOf(
            "Enter a destination and start navigation."
        )
    }

    var currentLocation by remember {
        mutableStateOf<Location?>(null)
    }

    var currentRoute by remember {
        mutableStateOf<NexusEyeBRouterRoute?>(null)
    }

    val resolvedDestination by
    navigationManager
        .destination
        .collectAsState()

    val navigationStatus by
    navigationManager
        .status
        .collectAsState()

    val isNavigating by
    navigationManager
        .isNavigating
        .collectAsState()

    LaunchedEffect(
        navigationManager
    ) {
        while (true) {
            currentLocation =
                navigationManager
                    .currentLocation()

            currentRoute =
                navigationManager
                    .currentRoute()

            delay(1000L)
        }
    }

    LaunchedEffect(
        navigationStatus
    ) {
        if (
            navigationStatus.isNotBlank()
        ) {
            statusText =
                navigationStatus
        }
    }

    val mapView =
        remember {
            createNexusEyeMapView(
                context
            )
        }

    DisposableEffect(
        mapView
    ) {
        mapView.onResume()

        onDispose {
            mapView.onPause()
        }
    }

    Scaffold { innerPadding ->

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(
                        start = 12.dp,
                        end = 12.dp,
                        top = 8.dp,
                        bottom = 8.dp
                    ),

            verticalArrangement =
                Arrangement.spacedBy(
                    8.dp
                )
        ) {

            Text(
                text =
                    "Navigation",

                style =
                    MaterialTheme
                        .typography
                        .headlineLarge
            )

            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(
                            1f
                        ),
                shape =
                    RoundedCornerShape(
                        12.dp
                    )
            ) {

                Box(
                    modifier =
                        Modifier.fillMaxSize()
                ) {

                    AndroidView(
                        factory = {
                            mapView
                        },

                        modifier =
                            Modifier.fillMaxSize(),

                        update = { view ->

                            updateNexusEyeMap(
                                mapView = view,
                                location = currentLocation,
                                route = currentRoute,
                                destinationLatitude =
                                    resolvedDestination
                                        ?.latitude,
                                destinationLongitude =
                                    resolvedDestination
                                        ?.longitude
                            )
                        }
                    )

                    Card(
                        modifier =
                            Modifier
                                .align(
                                    Alignment
                                        .TopStart
                                )
                                .padding(
                                    10.dp
                                )
                    ) {

                        Text(
                            text =
                                if (
                                    isNavigating
                                ) {
                                    "Live navigation"
                                } else {
                                    "Map"
                                },

                            modifier =
                                Modifier.padding(
                                    horizontal = 12.dp,
                                    vertical = 8.dp
                                ),

                            style =
                                MaterialTheme
                                    .typography
                                    .labelLarge
                        )
                    }
                }
            }

            OutlinedTextField(
                value =
                    destination,

                onValueChange = {
                    destination = it
                },

                modifier =
                    Modifier.fillMaxWidth(),

                label = {
                    Text(
                        text =
                            "Place or address"
                    )
                },

                placeholder = {
                    Text(
                        text =
                            "Example: Railway Station"
                    )
                },

                singleLine = true
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.spacedBy(
                        8.dp
                    )
            ) {

                Button(
                    onClick = {

                        val safeDestination =
                            destination.trim()

                        if (
                            safeDestination.isBlank()
                        ) {

                            statusText =
                                "Please enter a destination."

                        } else {

                            statusText =
                                "Starting navigation to $safeDestination."

                            onStartNavigation(
                                safeDestination
                            )
                        }
                    },

                    modifier =
                        Modifier.weight(
                            1f
                        )
                ) {

                    Text(
                        text =
                            "Start Navigation"
                    )
                }

                if (
                    isNavigating
                ) {

                    OutlinedButton(
                        onClick = {
                            navigationManager
                                .stopNavigation()

                            statusText =
                                "Navigation stopped."
                        },

                        modifier =
                            Modifier.weight(
                                0.65f
                            )
                    ) {

                        Text(
                            text =
                                "Stop"
                        )
                    }
                }
            }

            Card(
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Column(
                    modifier =
                        Modifier.padding(
                            12.dp
                        ),

                    verticalArrangement =
                        Arrangement.spacedBy(
                            6.dp
                        )
                ) {

                    Text(
                        text =
                            resolvedDestination
                                ?.displayName
                                ?: "Destination not selected",

                        style =
                            MaterialTheme
                                .typography
                                .titleMedium
                    )

                    Text(
                        text =
                            statusText,

                        style =
                            MaterialTheme
                                .typography
                                .bodyMedium
                    )

                    if (
                        savedHomeAddress != null
                    ) {

                        Button(
                            onClick = {

                                val started =
                                    onGoHome()

                                statusText =
                                    if (
                                        started
                                    ) {
                                        "Starting navigation to your saved home."
                                    } else {
                                        "Could not start navigation to saved home."
                                    }
                            },

                            modifier =
                                Modifier.fillMaxWidth()
                        ) {

                            Text(
                                text =
                                    "Go Home"
                            )
                        }
                    } else {

                        Text(
                            text =
                                "Home location has not been saved. Set it in Settings.",

                            style =
                                MaterialTheme
                                    .typography
                                    .bodySmall
                        )
                    }
                }
            }

            OutlinedButton(
                onClick =
                    onBack,

                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    text =
                        "Back"
                )
            }
        }
    }
}

private fun createNexusEyeMapView(
    context: Context
): MapView {

    Configuration
        .getInstance()
        .load(
            context,
            PreferenceManager
                .getDefaultSharedPreferences(
                    context
                )
        )

    Configuration
        .getInstance()
        .userAgentValue =
        context.packageName

    return MapView(
        context
    ).apply {

        setTileSource(
            TileSourceFactory.MAPNIK
        )

        setMultiTouchControls(
            true
        )

        setUseDataConnection(
            true
        )

        minZoomLevel =
            3.0

        maxZoomLevel =
            20.0

        controller
            .setZoom(
                14.0
            )

        controller
            .setCenter(
                GeoPoint(
                    20.5937,
                    78.9629
                )
            )
    }
}

private fun updateNexusEyeMap(
    mapView: MapView,
    location: Location?,
    route: NexusEyeBRouterRoute?,
    destinationLatitude: Double?,
    destinationLongitude: Double?
) {
    val overlays =
        mapView.overlays

    overlays.clear()

    val routePoints =
        route
            ?.points
            ?.map {
                GeoPoint(
                    it.latitude,
                    it.longitude
                )
            }
            ?: emptyList()

    if (
        routePoints.size >= 2
    ) {

        val routeLine =
            Polyline(
                mapView
            ).apply {

                setPoints(
                    routePoints
                )

                width =
                    12f
            }

        overlays.add(
            routeLine
        )
    }

    if (
        location != null
    ) {

        val currentMarker =
            Marker(
                mapView
            ).apply {

                position =
                    GeoPoint(
                        location.latitude,
                        location.longitude
                    )

                title =
                    "Current location"

                snippet =
                    "You are here"

                icon =
                    ContextCompat
                        .getDrawable(
                            mapView.context,
                            org.osmdroid.library.R.drawable.marker_default
                        )
            }

        overlays.add(
            currentMarker
        )
    }

    if (
        destinationLatitude != null &&
        destinationLongitude != null
    ) {

        val destinationMarker =
            Marker(
                mapView
            ).apply {

                position =
                    GeoPoint(
                        destinationLatitude,
                        destinationLongitude
                    )

                title =
                    "Destination"

                snippet =
                    "Navigation destination"

                icon =
                    ContextCompat
                        .getDrawable(
                            mapView.context,
                            org.osmdroid.library.R.drawable.marker_default
                        )
            }

        overlays.add(
            destinationMarker
        )
    }

    mapView.invalidate()

    if (
        location != null &&
        routePoints.isEmpty()
    ) {

        mapView.controller
            .animateTo(
                GeoPoint(
                    location.latitude,
                    location.longitude
                )
            )

        mapView.controller
            .setZoom(
                17.0
            )

    } else if (
        routePoints.size >= 2
    ) {

        val box =
            BoundingBox
                .fromGeoPoints(
                    routePoints
                )

        mapView
            .zoomToBoundingBox(
                box,
                true,
                80
            )
    }
}
