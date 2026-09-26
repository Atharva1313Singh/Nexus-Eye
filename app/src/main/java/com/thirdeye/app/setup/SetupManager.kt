package com.thirdeye.app.setup

import android.content.Context

class SetupManager(
    context: Context
) {

    private val preferences = context.getSharedPreferences(
        "nexus_eye_setup",
        Context.MODE_PRIVATE
    )

    fun isSetupComplete(): Boolean {
        return preferences.getBoolean(
            KEY_SETUP_COMPLETE,
            false
        )
    }

    fun getRole(): SetupRole? {
        val value = preferences.getString(
            KEY_ROLE,
            null
        )

        return when (value) {
            SetupRole.BLIND_USER.name -> SetupRole.BLIND_USER
            SetupRole.HELPER.name -> SetupRole.HELPER
            else -> null
        }
    }

    fun saveRole(role: SetupRole) {
        preferences.edit()
            .putString(
                KEY_ROLE,
                role.name
            )
            .apply()
    }

    fun completeSetup() {
        preferences.edit()
            .putBoolean(
                KEY_SETUP_COMPLETE,
                true
            )
            .apply()
    }

    fun resetSetup() {
        preferences.edit()
            .clear()
            .apply()
    }

    companion object {
        private const val KEY_SETUP_COMPLETE = "setup_complete"
        private const val KEY_ROLE = "role"
    }
}