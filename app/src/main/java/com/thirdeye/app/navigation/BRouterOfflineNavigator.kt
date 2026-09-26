package com.thirdeye.app.navigation

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import btools.routingapp.IBRouterService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import kotlin.math.max

data class NexusEyeBRouterPoint(
    val latitude: Double,
    val longitude: Double
)

data class NexusEyeBRouterRoute(
    val points: List<NexusEyeBRouterPoint>,
    val distanceMeters: Double
)

class BRouterOfflineNavigator(
    context: Context
) {

    companion object {

        private const val BROUTER_PACKAGE =
            "btools.routingapp"

        private const val BROUTER_SERVICE =
            "btools.routingapp.BRouterService"

        private const val ROUTE_TIMEOUT_MS =
            20_000L
    }

    private val appContext =
        context.applicationContext

    fun isBRouterInstalled(): Boolean {

        return try {

            appContext.packageManager.getPackageInfo(
                BROUTER_PACKAGE,
                0
            )

            true

        } catch (
            _: PackageManager.NameNotFoundException
        ) {

            false
        }
    }

    suspend fun calculateRoute(
        start: Location,
        destinationLatitude: Double,
        destinationLongitude: Double
    ): NexusEyeBRouterRoute {

        require(
            start.latitude.isFinite()
        ) {
            "Current latitude is invalid."
        }

        require(
            start.longitude.isFinite()
        ) {
            "Current longitude is invalid."
        }

        require(
            destinationLatitude.isFinite()
        ) {
            "Destination latitude is invalid."
        }

        require(
            destinationLongitude.isFinite()
        ) {
            "Destination longitude is invalid."
        }

        if (
            !isBRouterInstalled()
        ) {

            throw IllegalStateException(
                "BRouter is not installed on this device."
            )
        }

        val result =
            requestRouteFromBRouter(
                startLatitude =
                    start.latitude,
                startLongitude =
                    start.longitude,
                destinationLatitude =
                    destinationLatitude,
                destinationLongitude =
                    destinationLongitude
            )

        return parseGpxRoute(
            result
        )
    }

    private suspend fun requestRouteFromBRouter(
        startLatitude: Double,
        startLongitude: Double,
        destinationLatitude: Double,
        destinationLongitude: Double
    ): String {

        val params =
            Bundle()

        params.putString(
            "lonlats",
            "$startLongitude,$startLatitude|$destinationLongitude,$destinationLatitude"
        )

        params.putString(
            "profile",
            "trekking"
        )

        params.putString(
            "v",
            "foot"
        )

        params.putString(
            "fast",
            "1"
        )

        params.putString(
            "trackFormat",
            "gpx"
        )

        params.putInt(
            "timode",
            3
        )

        params.putString(
            "maxRunningTime",
            "30"
        )

        params.putString(
            "acceptCompressedResult",
            "false"
        )

        return withContext(
            Dispatchers.IO
        ) {

            val bound =
                bindToBRouter()

            try {

                val result =
                    withTimeout(
                        ROUTE_TIMEOUT_MS
                    ) {

                        try {

                            bound.service
                                .getTrackFromParams(
                                    params
                                )

                        } catch (
                            exception: RemoteException
                        ) {

                            throw IllegalStateException(
                                "Could not communicate with BRouter.",
                                exception
                            )
                        }
                    }

                if (
                    result.isNullOrBlank()
                ) {

                    throw IllegalStateException(
                        "BRouter returned an empty route."
                    )
                }

                val trimmed =
                    result.trimStart()

                if (
                    !trimmed.startsWith("<")
                ) {

                    throw IllegalStateException(
                        cleanBRouterError(
                            result
                        )
                    )
                }

                result

            } finally {

                unbindQuietly(
                    bound.connection
                )
            }
        }
    }

    private suspend fun bindToBRouter():
            BoundBRouterService {

        val intent =
            Intent().apply {

                component =
                    ComponentName(
                        BROUTER_PACKAGE,
                        BROUTER_SERVICE
                    )
            }

        val serviceDeferred =
            CompletableDeferred<IBRouterService>()

        val connection =
            object :
                ServiceConnection {

                override fun onServiceConnected(
                    name: ComponentName?,
                    service: IBinder?
                ) {

                    if (
                        service == null
                    ) {

                        serviceDeferred
                            .completeExceptionally(
                                IllegalStateException(
                                    "BRouter service returned no binder."
                                )
                            )

                        return
                    }

                    val router =
                        IBRouterService.Stub
                            .asInterface(
                                service
                            )

                    if (
                        router == null
                    ) {

                        serviceDeferred
                            .completeExceptionally(
                                IllegalStateException(
                                    "BRouter service interface is unavailable."
                                )
                            )

                        return
                    }

                    serviceDeferred.complete(
                        router
                    )
                }

                override fun onServiceDisconnected(
                    name: ComponentName?
                ) {

                    if (
                        !serviceDeferred.isCompleted
                    ) {

                        serviceDeferred
                            .completeExceptionally(
                                IllegalStateException(
                                    "BRouter service disconnected."
                                )
                            )
                    }
                }

                override fun onBindingDied(
                    name: ComponentName?
                ) {

                    if (
                        !serviceDeferred.isCompleted
                    ) {

                        serviceDeferred
                            .completeExceptionally(
                                IllegalStateException(
                                    "BRouter service binding died."
                                )
                            )
                    }
                }

                override fun onNullBinding(
                    name: ComponentName?
                ) {

                    if (
                        !serviceDeferred.isCompleted
                    ) {

                        serviceDeferred
                            .completeExceptionally(
                                IllegalStateException(
                                    "BRouter returned a null binding."
                                )
                            )
                    }
                }
            }

        val bound =
            try {

                appContext.bindService(
                    intent,
                    connection,
                    Context.BIND_AUTO_CREATE
                )

            } catch (
                exception: Exception
            ) {

                throw IllegalStateException(
                    "Unable to bind to BRouter.",
                    exception
                )
            }

        if (
            !bound
        ) {

            throw IllegalStateException(
                "BRouter service could not be bound."
            )
        }

        return try {

            val service =
                withTimeout(
                    ROUTE_TIMEOUT_MS
                ) {

                    serviceDeferred.await()
                }

            BoundBRouterService(
                service = service,
                connection = connection
            )

        } catch (
            exception: Exception
        ) {

            unbindQuietly(
                connection
            )

            throw exception
        }
    }

    private fun unbindQuietly(
        connection: ServiceConnection
    ) {

        try {

            appContext.unbindService(
                connection
            )

        } catch (_: Exception) {
        }
    }

    private fun parseGpxRoute(
        gpx: String
    ): NexusEyeBRouterRoute {

        val factory =
            XmlPullParserFactory
                .newInstance()

        factory.isNamespaceAware =
            true

        val parser =
            factory.newPullParser()

        parser.setInput(
            StringReader(gpx)
        )

        val points =
            ArrayList<NexusEyeBRouterPoint>()

        var eventType =
            parser.eventType

        while (
            eventType !=
            XmlPullParser.END_DOCUMENT
        ) {

            if (
                eventType ==
                XmlPullParser.START_TAG &&
                parser.name.equals(
                    "trkpt",
                    ignoreCase = true
                )
            ) {

                val latitudeText =
                    parser.getAttributeValue(
                        null,
                        "lat"
                    )

                val longitudeText =
                    parser.getAttributeValue(
                        null,
                        "lon"
                    )

                val latitude =
                    latitudeText
                        ?.toDoubleOrNull()

                val longitude =
                    longitudeText
                        ?.toDoubleOrNull()

                if (
                    latitude != null &&
                    longitude != null &&
                    latitude.isFinite() &&
                    longitude.isFinite()
                ) {

                    points +=
                        NexusEyeBRouterPoint(
                            latitude =
                                latitude,
                            longitude =
                                longitude
                        )
                }
            }

            eventType =
                parser.next()
        }

        if (
            points.size < 2
        ) {

            throw IllegalStateException(
                "BRouter returned a route with too few points."
            )
        }

        val distance =
            calculateRouteDistance(
                points
            )

        if (
            !distance.isFinite() ||
            distance <= 0.0
        ) {

            throw IllegalStateException(
                "BRouter returned an invalid route distance."
            )
        }

        return NexusEyeBRouterRoute(
            points =
                points,
            distanceMeters =
                max(
                    0.1,
                    distance
                )
        )
    }

    private fun calculateRouteDistance(
        points: List<NexusEyeBRouterPoint>
    ): Double {

        var totalDistance =
            0.0

        for (
        index in 1 until points.size
        ) {

            val previous =
                points[index - 1]

            val current =
                points[index]

            val result =
                FloatArray(1)

            Location.distanceBetween(
                previous.latitude,
                previous.longitude,
                current.latitude,
                current.longitude,
                result
            )

            val segment =
                result[0]
                    .toDouble()

            if (
                segment.isFinite()
            ) {

                totalDistance +=
                    segment
            }
        }

        return totalDistance
    }

    private fun cleanBRouterError(
        raw: String
    ): String {

        val text =
            raw.trim()

        return when {

            text.contains(
                "does not exists",
                ignoreCase = true
            ) -> {

                "The BRouter profile is missing."
            }

            text.contains(
                "not found",
                ignoreCase = true
            ) &&
                    text.contains(
                        "segment",
                        ignoreCase = true
                    ) -> {

                "BRouter map data is missing for this area."
            }

            text.contains(
                "config",
                ignoreCase = true
            ) -> {

                "BRouter configuration is incomplete."
            }

            else -> {

                "BRouter route calculation failed: $text"
            }
        }
    }

    private data class BoundBRouterService(
        val service: IBRouterService,
        val connection: ServiceConnection
    )
}