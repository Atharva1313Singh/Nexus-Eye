package com.thirdeye.app.environment

import android.content.Context
import android.location.Geocoder
import android.os.Build
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Stores the user's Home as exact coordinates.
 *
 * The address is only a human-readable label. Navigation must use
 * latitude/longitude from this object so a geocoder cannot move Home to a
 * nearby colony/road/building centroid.
 */
data class NexusEyeHomeLocation(
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val accuracyMeters: Float? = null,
    val savedAtMillis: Long = System.currentTimeMillis()
)

class NexusEyeHomeLocationManager(
    context: Context
) {

    private val appContext = context.applicationContext

    private val preferences =
        appContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )

    fun getSavedHomeLocation(): NexusEyeHomeLocation? {

        val latitude =
            preferences
                .getString(KEY_LATITUDE, null)
                ?.toDoubleOrNull()
                ?: return null

        val longitude =
            preferences
                .getString(KEY_LONGITUDE, null)
                ?.toDoubleOrNull()
                ?: return null

        if (
            latitude !in -90.0..90.0 ||
            longitude !in -180.0..180.0
        ) {
            return null
        }

        val address =
            preferences
                .getString(
                    KEY_ADDRESS,
                    null
                )
                ?.trim()
                .orEmpty()
                .ifBlank {
                    "Saved Home Location"
                }

        val accuracy =
            preferences
                .getString(
                    KEY_ACCURACY_METERS,
                    null
                )
                ?.toFloatOrNull()

        val savedAt =
            preferences
                .getLong(
                    KEY_SAVED_AT,
                    0L
                )

        return NexusEyeHomeLocation(
            latitude = latitude,
            longitude = longitude,
            address = address,
            accuracyMeters = accuracy,
            savedAtMillis = savedAt
        )
    }

    fun hasSavedHome(): Boolean {
        return getSavedHomeLocation() != null
    }

    fun hasSavedHomeLocation(): Boolean {
        return hasSavedHome()
    }

    fun getHomeCoordinateString(): String? {
        val home = getSavedHomeLocation() ?: return null
        return "${home.latitude},${home.longitude}"
    }

    fun saveHomeLocation(
        home: NexusEyeHomeLocation
    ) {

        require(
            home.latitude in -90.0..90.0
        ) {
            "Home latitude must be between -90 and 90."
        }

        require(
            home.longitude in -180.0..180.0
        ) {
            "Home longitude must be between -180 and 180."
        }

        val editor =
            preferences.edit()
                .putString(
                    KEY_LATITUDE,
                    java.lang.Double.toString(home.latitude)
                )
                .putString(
                    KEY_LONGITUDE,
                    java.lang.Double.toString(home.longitude)
                )
                .putString(
                    KEY_ADDRESS,
                    home.address.trim().ifBlank {
                        "Saved Home Location"
                    }
                )
                .putLong(
                    KEY_SAVED_AT,
                    if (home.savedAtMillis > 0L) {
                        home.savedAtMillis
                    } else {
                        System.currentTimeMillis()
                    }
                )

        if (home.accuracyMeters != null &&
            home.accuracyMeters.isFinite() &&
            home.accuracyMeters >= 0f
        ) {
            editor.putString(
                KEY_ACCURACY_METERS,
                home.accuracyMeters.toString()
            )
        } else {
            editor.remove(KEY_ACCURACY_METERS)
        }

        editor.apply()
    }

    fun saveHomeLocation(
        latitude: Double,
        longitude: Double,
        address: String = "Saved Home Location",
        accuracyMeters: Float? = null
    ) {
        saveHomeLocation(
            NexusEyeHomeLocation(
                latitude = latitude,
                longitude = longitude,
                address = address,
                accuracyMeters = accuracyMeters
            )
        )
    }

    fun clearHomeLocation() {
        preferences
            .edit()
            .remove(KEY_LATITUDE)
            .remove(KEY_LONGITUDE)
            .remove(KEY_ADDRESS)
            .remove(KEY_ACCURACY_METERS)
            .remove(KEY_SAVED_AT)
            .apply()
    }

    suspend fun resolveAddress(
        latitude: Double,
        longitude: Double
    ): String {

        if (
            latitude !in -90.0..90.0 ||
            longitude !in -180.0..180.0
        ) {
            return "Coordinates $latitude, $longitude"
        }

        if (!Geocoder.isPresent()) {
            return "Coordinates $latitude, $longitude"
        }

        return try {
            val addresses =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine { continuation ->
                        try {
                            Geocoder(
                                appContext,
                                Locale.getDefault()
                            ).getFromLocation(
                                latitude,
                                longitude,
                                1,
                                object : Geocoder.GeocodeListener {
                                    override fun onGeocode(
                                        results: MutableList<android.location.Address>
                                    ) {
                                        if (continuation.isActive) {
                                            continuation.resume(results)
                                        }
                                    }

                                    override fun onError(
                                        errorMessage: String?
                                    ) {
                                        if (continuation.isActive) {
                                            continuation.resume(emptyList())
                                        }
                                    }
                                }
                            )
                        } catch (_: Exception) {
                            if (continuation.isActive) {
                                continuation.resume(emptyList())
                            }
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    Geocoder(
                        appContext,
                        Locale.getDefault()
                    ).getFromLocation(
                        latitude,
                        longitude,
                        1
                    ) ?: emptyList()
                }

            addresses
                .firstOrNull()
                ?.getAddressLine(0)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: "Coordinates $latitude, $longitude"

        } catch (_: Exception) {
            "Coordinates $latitude, $longitude"
        }
    }

    companion object {
        private const val PREFERENCES_NAME =
            "nexus_eye_home_location"

        private const val KEY_LATITUDE =
            "home_latitude"

        private const val KEY_LONGITUDE =
            "home_longitude"

        private const val KEY_ADDRESS =
            "home_address"

        private const val KEY_ACCURACY_METERS =
            "home_accuracy_meters"

        private const val KEY_SAVED_AT =
            "home_saved_at"
    }
}
