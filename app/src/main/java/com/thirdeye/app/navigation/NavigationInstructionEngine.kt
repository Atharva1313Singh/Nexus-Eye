package com.thirdeye.app.navigation

import android.location.Location
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

data class RoutePoint(
    val latitude: Double,
    val longitude: Double
)

enum class NavigationDirection {
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

data class NavigationInstruction(
    val pointIndex: Int,
    val direction: NavigationDirection,
    val distanceMeters: Double,
    val steps: Int,
    val spokenText: String
)

class NavigationInstructionEngine {

    companion object {

        private const val DEFAULT_STRIDE_METERS =
            0.70

        private const val TURN_THRESHOLD_DEGREES =
            25.0

        private const val SLIGHT_TURN_THRESHOLD =
            45.0

        private const val SHARP_TURN_THRESHOLD =
            135.0
    }

    fun createInstructions(
        routePoints: List<RoutePoint>,
        strideMeters: Double =
            DEFAULT_STRIDE_METERS
    ): List<NavigationInstruction> {

        validateStride(
            strideMeters
        )

        if (
            routePoints.isEmpty()
        ) {

            return emptyList()
        }

        if (
            routePoints.size == 1
        ) {

            return listOf(
                NavigationInstruction(
                    pointIndex = 0,
                    direction =
                        NavigationDirection.ARRIVE,
                    distanceMeters = 0.0,
                    steps = 0,
                    spokenText =
                        "You have arrived at your destination."
                )
            )
        }

        val instructions =
            ArrayList<NavigationInstruction>()

        val firstDistance =
            distanceBetween(
                routePoints[0],
                routePoints[1]
            )

        val firstSteps =
            stepsForDistance(
                firstDistance,
                strideMeters
            )

        instructions +=
            NavigationInstruction(
                pointIndex = 0,
                direction =
                    NavigationDirection.START,
                distanceMeters =
                    firstDistance,
                steps =
                    firstSteps,
                spokenText =
                    "Navigation started. Continue straight for about $firstSteps steps."
            )

        for (
        index in 1 until routePoints.lastIndex
        ) {

            val previous =
                routePoints[
                    index - 1
                ]

            val current =
                routePoints[
                    index
                ]

            val next =
                routePoints[
                    index + 1
                ]

            val incomingBearing =
                bearingBetween(
                    previous,
                    current
                )

            val outgoingBearing =
                bearingBetween(
                    current,
                    next
                )

            val delta =
                normalizeBearingDifference(
                    outgoingBearing -
                            incomingBearing
                )

            if (
                abs(delta) <
                TURN_THRESHOLD_DEGREES
            ) {

                continue
            }

            val direction =
                directionFromDelta(
                    delta
                )

            val distanceToTurn =
                distanceFromStartToIndex(
                    routePoints,
                    index
                )

            val steps =
                stepsForDistance(
                    distanceToTurn,
                    strideMeters
                )

            instructions +=
                NavigationInstruction(
                    pointIndex =
                        index,
                    direction =
                        direction,
                    distanceMeters =
                        distanceToTurn,
                    steps =
                        steps,
                    spokenText =
                        spokenDirection(
                            direction,
                            distanceToTurn,
                            steps
                        )
                )
        }

        val finalIndex =
            routePoints.lastIndex

        val finalDistance =
            distanceBetween(
                routePoints[
                    max(
                        0,
                        finalIndex - 1
                    )
                ],
                routePoints[
                    finalIndex
                ]
            )

        instructions +=
            NavigationInstruction(
                pointIndex =
                    finalIndex,
                direction =
                    NavigationDirection.ARRIVE,
                distanceMeters =
                    finalDistance,
                steps =
                    stepsForDistance(
                        finalDistance,
                        strideMeters
                    ),
                spokenText =
                    "You have arrived at your destination."
            )

        return instructions
    }

    fun buildInstructions(
        routePoints: List<RoutePoint>,
        strideMeters: Double =
            DEFAULT_STRIDE_METERS
    ): List<NavigationInstruction> {

        return createInstructions(
            routePoints =
                routePoints,
            strideMeters =
                strideMeters
        )
    }

    fun generateInstructions(
        routePoints: List<RoutePoint>,
        strideMeters: Double =
            DEFAULT_STRIDE_METERS
    ): List<NavigationInstruction> {

        return createInstructions(
            routePoints =
                routePoints,
            strideMeters =
                strideMeters
        )
    }

    fun createInstructionsFromBRouterRoute(
        route: NexusEyeBRouterRoute,
        strideMeters: Double =
            DEFAULT_STRIDE_METERS
    ): List<NavigationInstruction> {

        val points =
            route.points.map { point ->

                RoutePoint(
                    latitude =
                        point.latitude,
                    longitude =
                        point.longitude
                )
            }

        return createInstructions(
            routePoints =
                points,
            strideMeters =
                strideMeters
        )
    }

    private fun directionFromDelta(
        delta: Double
    ): NavigationDirection {

        return when {

            delta >=
                    SHARP_TURN_THRESHOLD -> {

                NavigationDirection.SHARP_RIGHT
            }

            delta >=
                    SLIGHT_TURN_THRESHOLD -> {

                NavigationDirection.RIGHT
            }

            delta >=
                    TURN_THRESHOLD_DEGREES -> {

                NavigationDirection.SLIGHT_RIGHT
            }

            delta <=
                    -SHARP_TURN_THRESHOLD -> {

                NavigationDirection.SHARP_LEFT
            }

            delta <=
                    -SLIGHT_TURN_THRESHOLD -> {

                NavigationDirection.LEFT
            }

            else -> {

                NavigationDirection.SLIGHT_LEFT
            }
        }
    }

    private fun spokenDirection(
        direction: NavigationDirection,
        distanceMeters: Double,
        steps: Int
    ): String {

        val directionText =
            when (direction) {

                NavigationDirection.SLIGHT_LEFT ->
                    "Slightly left"

                NavigationDirection.LEFT ->
                    "Turn left"

                NavigationDirection.SHARP_LEFT ->
                    "Sharp left"

                NavigationDirection.SLIGHT_RIGHT ->
                    "Slightly right"

                NavigationDirection.RIGHT ->
                    "Turn right"

                NavigationDirection.SHARP_RIGHT ->
                    "Sharp right"

                NavigationDirection.STRAIGHT ->
                    "Continue straight"

                NavigationDirection.START ->
                    "Continue straight"

                NavigationDirection.ARRIVE ->
                    "Arrive"
            }

        return "$directionText in about ${
            distanceMeters.toInt().coerceAtLeast(1)
        } meters, approximately $steps steps."
    }

    private fun stepsForDistance(
        distanceMeters: Double,
        strideMeters: Double
    ): Int {

        if (
            distanceMeters <= 0.0
        ) {

            return 0
        }

        return max(
            1,
            ceil(
                distanceMeters /
                        strideMeters
            ).toInt()
        )
    }

    private fun distanceFromStartToIndex(
        points: List<RoutePoint>,
        index: Int
    ): Double {

        var total =
            0.0

        for (
        currentIndex in 1..index
        ) {

            total +=
                distanceBetween(
                    points[
                        currentIndex - 1
                    ],
                    points[
                        currentIndex
                    ]
                )
        }

        return total
    }

    private fun distanceBetween(
        first: RoutePoint,
        second: RoutePoint
    ): Double {

        val result =
            FloatArray(1)

        Location.distanceBetween(
            first.latitude,
            first.longitude,
            second.latitude,
            second.longitude,
            result
        )

        return result[0]
            .toDouble()
    }

    private fun bearingBetween(
        first: RoutePoint,
        second: RoutePoint
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

    private fun validateStride(
        strideMeters: Double
    ) {

        require(
            strideMeters.isFinite()
        ) {
            "Stride must be a valid number."
        }

        require(
            strideMeters >= 0.25 &&
                    strideMeters <= 2.0
        ) {
            "Stride must be between 0.25 and 2.0 meters."
        }
    }
}