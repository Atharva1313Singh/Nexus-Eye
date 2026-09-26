package com.thirdeye.app.navigation

import android.content.Context

class NexusEyeNavigationPreferences(
    context: Context
) {

    private val preferences =
        context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    fun getStrideLengthMeters(): Float {

        val stored =
            preferences.getFloat(
                KEY_STRIDE_LENGTH_METERS,
                DEFAULT_STRIDE_LENGTH_METERS
            )

        return stored.coerceIn(
            MIN_STRIDE_LENGTH_METERS,
            MAX_STRIDE_LENGTH_METERS
        )
    }

    fun saveStrideLengthMeters(
        strideLengthMeters: Float
    ) {

        val safeValue =
            strideLengthMeters.coerceIn(
                MIN_STRIDE_LENGTH_METERS,
                MAX_STRIDE_LENGTH_METERS
            )

        preferences.edit()
            .putFloat(
                KEY_STRIDE_LENGTH_METERS,
                safeValue
            )
            .apply()
    }

    fun resetStrideLength() {

        preferences.edit()
            .remove(
                KEY_STRIDE_LENGTH_METERS
            )
            .apply()
    }

    companion object {

        private const val PREFS_NAME =
            "nexus_eye_navigation_preferences"

        private const val KEY_STRIDE_LENGTH_METERS =
            "stride_length_meters"

        const val DEFAULT_STRIDE_LENGTH_METERS =
            0.70f

        const val MIN_STRIDE_LENGTH_METERS =
            0.30f

        const val MAX_STRIDE_LENGTH_METERS =
            2.00f
    }
}