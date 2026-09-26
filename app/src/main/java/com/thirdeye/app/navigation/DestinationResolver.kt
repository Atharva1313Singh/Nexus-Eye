package com.thirdeye.app.navigation

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class DestinationResolver(
    context: Context
) {

    private val appContext =
        context.applicationContext

    data class Destination(
        val query: String,
        val displayName: String,
        val latitude: Double,
        val longitude: Double
    )

    sealed class Result {

        data class Success(
            val destination: Destination
        ) : Result()

        data class Failure(
            val reason: String
        ) : Result()
    }

    suspend fun resolve(
        query: String
    ): Result {

        val cleanedQuery =
            query.trim()

        if (cleanedQuery.isEmpty()) {
            return Result.Failure(
                "The destination was empty."
            )
        }

        parseCoordinates(
            cleanedQuery
        )?.let { coordinate ->

            return Result.Success(
                Destination(
                    query = cleanedQuery,
                    displayName =
                        "Coordinates ${coordinate.first}, ${coordinate.second}",
                    latitude = coordinate.first,
                    longitude = coordinate.second
                )
            )
        }

        if (!Geocoder.isPresent()) {
            return Result.Failure(
                "Location search is not available on this device."
            )
        }

        return try {

            val address =
                getBestAddress(
                    cleanedQuery
                )

            if (address == null) {

                Result.Failure(
                    "I could not find that destination."
                )

            } else {

                Result.Success(
                    Destination(
                        query = cleanedQuery,
                        displayName =
                            createDisplayName(address),
                        latitude =
                            address.latitude,
                        longitude =
                            address.longitude
                    )
                )
            }

        } catch (e: Exception) {

            Result.Failure(
                e.message
                    ?: "Destination search failed."
            )
        }
    }

    private suspend fun getBestAddress(
        query: String
    ): Address? {

        return if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {

            suspendCancellableCoroutine { continuation ->

                val geocoder =
                    Geocoder(appContext)

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
                                    chooseBestAddress(
                                        addresses
                                    )
                                )
                            }
                        }

                        override fun onError(
                            errorMessage: String?
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
                )
            }

        } else {

            withContext(
                Dispatchers.IO
            ) {

                val geocoder =
                    Geocoder(appContext)

                @Suppress("DEPRECATION")
                val addresses =
                    geocoder.getFromLocationName(
                        query,
                        5
                    )

                chooseBestAddress(
                    addresses
                )
            }
        }
    }

    private fun chooseBestAddress(
        addresses: List<Address>?
    ): Address? {

        if (
            addresses.isNullOrEmpty()
        ) {
            return null
        }

        return addresses
            .sortedByDescending { address ->

                var score = 0

                if (
                    !address.featureName
                        .isNullOrBlank()
                ) {
                    score += 3
                }

                if (
                    !address.thoroughfare
                        .isNullOrBlank()
                ) {
                    score += 3
                }

                if (
                    !address.locality
                        .isNullOrBlank()
                ) {
                    score += 2
                }

                if (
                    !address.adminArea
                        .isNullOrBlank()
                ) {
                    score += 1
                }

                if (
                    !address.countryName
                        .isNullOrBlank()
                ) {
                    score += 1
                }

                score
            }
            .first()
    }

    private fun createDisplayName(
        address: Address
    ): String {

        val parts =
            mutableListOf<String>()

        address.featureName
            ?.takeIf { it.isNotBlank() }
            ?.let {
                parts.add(it)
            }

        address.thoroughfare
            ?.takeIf { it.isNotBlank() }
            ?.let {
                if (!parts.contains(it)) {
                    parts.add(it)
                }
            }

        address.locality
            ?.takeIf { it.isNotBlank() }
            ?.let {
                if (!parts.contains(it)) {
                    parts.add(it)
                }
            }

        address.subAdminArea
            ?.takeIf { it.isNotBlank() }
            ?.let {
                if (!parts.contains(it)) {
                    parts.add(it)
                }
            }

        address.adminArea
            ?.takeIf { it.isNotBlank() }
            ?.let {
                if (!parts.contains(it)) {
                    parts.add(it)
                }
            }

        address.countryName
            ?.takeIf { it.isNotBlank() }
            ?.let {
                if (!parts.contains(it)) {
                    parts.add(it)
                }
            }

        return if (parts.isNotEmpty()) {
            parts.joinToString(", ")
        } else {
            "${address.latitude}, ${address.longitude}"
        }
    }

    private fun parseCoordinates(
        text: String
    ): Pair<Double, Double>? {

        val parts =
            text
                .split(",")
                .map { it.trim() }

        if (parts.size != 2) {
            return null
        }

        val latitude =
            parts[0].toDoubleOrNull()

        val longitude =
            parts[1].toDoubleOrNull()

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

        return Pair(
            latitude,
            longitude
        )
    }
}