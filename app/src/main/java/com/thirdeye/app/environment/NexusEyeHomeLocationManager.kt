package com.thirdeye.app.environment

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

data class NexusEyeHomeLocation(
    val latitude: Double,
    val longitude: Double,
    val address: String
)

class NexusEyeHomeLocationManager(
    context: Context
) {

    private val appContext =
        context.applicationContext

    private val preferences =
        appContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    fun hasSavedHomeLocation(): Boolean {
        return preferences.getBoolean(
            KEY_HAS_HOME,
            false
        )
    }

    fun getSavedHomeLocation(): NexusEyeHomeLocation? {

        if (!hasSavedHomeLocation()) {
            return null
        }

        val latitude =
            preferences.getString(
                KEY_LATITUDE,
                null
            )?.toDoubleOrNull()
                ?: return null

        val longitude =
            preferences.getString(
                KEY_LONGITUDE,
                null
            )?.toDoubleOrNull()
                ?: return null

        val address =
            preferences.getString(
                KEY_ADDRESS,
                null
            ).orEmpty()

        if (address.isBlank()) {
            return null
        }

        return NexusEyeHomeLocation(
            latitude = latitude,
            longitude = longitude,
            address = address
        )
    }

    fun saveHomeLocation(
        location: NexusEyeHomeLocation
    ) {

        preferences.edit()
            .putBoolean(
                KEY_HAS_HOME,
                true
            )
            .putString(
                KEY_LATITUDE,
                location.latitude.toString()
            )
            .putString(
                KEY_LONGITUDE,
                location.longitude.toString()
            )
            .putString(
                KEY_ADDRESS,
                location.address
            )
            .apply()
    }

    fun clearHomeLocation() {

        preferences.edit()
            .remove(KEY_HAS_HOME)
            .remove(KEY_LATITUDE)
            .remove(KEY_LONGITUDE)
            .remove(KEY_ADDRESS)
            .apply()
    }

    suspend fun resolveAddress(
        latitude: Double,
        longitude: Double
    ): String {

        if (!Geocoder.isPresent()) {
            return fallbackCoordinateAddress(
                latitude = latitude,
                longitude = longitude
            )
        }

        val geocoder =
            Geocoder(
                appContext,
                Locale.getDefault()
            )

        return try {

            val addresses =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

                    geocodeAsync(
                        geocoder = geocoder,
                        latitude = latitude,
                        longitude = longitude
                    )

                } else {

                    withContext(Dispatchers.IO) {

                        @Suppress("DEPRECATION")
                        geocoder.getFromLocation(
                            latitude,
                            longitude,
                            3
                        )
                    }
                }

            val bestAddress =
                addresses
                    ?.firstOrNull()

            formatAddress(
                bestAddress = bestAddress,
                latitude = latitude,
                longitude = longitude
            )

        } catch (
            _: Exception
        ) {

            fallbackCoordinateAddress(
                latitude = latitude,
                longitude = longitude
            )
        }
    }

    private suspend fun geocodeAsync(
        geocoder: Geocoder,
        latitude: Double,
        longitude: Double
    ): List<Address> =
        suspendCoroutine { continuation ->

            try {

                geocoder.getFromLocation(
                    latitude,
                    longitude,
                    3,
                    object : Geocoder.GeocodeListener {

                        override fun onGeocode(
                            addresses: MutableList<Address>
                        ) {

                            continuation.resume(
                                addresses
                            )
                        }

                        override fun onError(
                            errorMessage: String?
                        ) {

                            continuation.resume(
                                emptyList()
                            )
                        }
                    }
                )

            } catch (
                _: Exception
            ) {

                continuation.resume(
                    emptyList()
                )
            }
        }

    private fun formatAddress(
        bestAddress: Address?,
        latitude: Double,
        longitude: Double
    ): String {

        if (bestAddress == null) {
            return fallbackCoordinateAddress(
                latitude = latitude,
                longitude = longitude
            )
        }

        val parts =
            mutableListOf<String>()

        fun addPart(
            value: String?
        ) {

            if (
                !value.isNullOrBlank() &&
                !parts.contains(value)
            ) {

                parts += value
            }
        }

        addPart(
            bestAddress.subThoroughfare
        )

        addPart(
            bestAddress.thoroughfare
        )

        addPart(
            bestAddress.subLocality
        )

        addPart(
            bestAddress.locality
        )

        addPart(
            bestAddress.subAdminArea
        )

        addPart(
            bestAddress.adminArea
        )

        addPart(
            bestAddress.postalCode
        )

        addPart(
            bestAddress.countryName
        )

        if (parts.isNotEmpty()) {
            return parts.joinToString(
                separator = ", "
            )
        }

        val addressLine =
            bestAddress.getAddressLine(0)

        if (
            !addressLine.isNullOrBlank()
        ) {
            return addressLine
        }

        return fallbackCoordinateAddress(
            latitude = latitude,
            longitude = longitude
        )
    }

    private fun fallbackCoordinateAddress(
        latitude: Double,
        longitude: Double
    ): String {

        return String.format(
            Locale.US,
            "Location at %.6f, %.6f",
            latitude,
            longitude
        )
    }

    companion object {

        private const val PREFS_NAME =
            "nexus_eye_home_location"

        private const val KEY_HAS_HOME =
            "has_home_location"

        private const val KEY_LATITUDE =
            "home_latitude"

        private const val KEY_LONGITUDE =
            "home_longitude"

        private const val KEY_ADDRESS =
            "home_address"
    }
}