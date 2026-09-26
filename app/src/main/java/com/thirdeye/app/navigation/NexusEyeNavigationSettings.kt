package com.thirdeye.app.navigation

import android.content.Context

/**
 * Persistent Android-side navigation settings used by NEXUS EYE.
 *
 * Stage 7 currently exposes the user stride setting here. The setting is consumed
 * by MainActivity when starting navigation and passed into NexusEyeNavigationManager.
 * The existing offline-first BRouter routing behavior is intentionally unchanged.
 */
class NexusEyeNavigationSettings(
    context: Context
) {

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun getStrideMeters(): Double {
        return preferences.getFloat(
            KEY_STRIDE_METERS,
            DEFAULT_STRIDE_METERS.toFloat()
        )
            .toDouble()
            .coerceIn(MIN_STRIDE_METERS, MAX_STRIDE_METERS)
    }

    fun setStrideMeters(value: Double) {
        val safeValue = value.coerceIn(
            MIN_STRIDE_METERS,
            MAX_STRIDE_METERS
        )

        preferences.edit()
            .putFloat(KEY_STRIDE_METERS, safeValue.toFloat())
            .apply()
    }

    fun resetToDefaults() {
        preferences.edit()
            .remove(KEY_STRIDE_METERS)
            .apply()
    }

    companion object {
        const val MIN_STRIDE_METERS = 0.30
        const val DEFAULT_STRIDE_METERS = 0.70
        const val MAX_STRIDE_METERS = 2.00
        private const val PREFS_NAME = "nexus_eye_navigation_settings"
        private const val KEY_STRIDE_METERS = "stride_meters"
    }
}
