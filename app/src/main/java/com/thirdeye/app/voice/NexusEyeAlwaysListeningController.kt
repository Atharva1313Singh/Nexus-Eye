package com.thirdeye.app.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

object NexusEyeAlwaysListeningController {

    const val PREFS_NAME = "nexus_eye_voice_preferences"
    const val ENABLED_KEY = "always_listening_enabled"

    fun isEnabled(context: Context): Boolean {
        return context.applicationContext
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .getBoolean(
                ENABLED_KEY,
                false
            )
    }

    fun setEnabled(
        context: Context,
        enabled: Boolean
    ) {
        context.applicationContext
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .putBoolean(
                ENABLED_KEY,
                enabled
            )
            .apply()
    }

    fun start(
        context: Context
    ): Boolean {
        val appContext = context.applicationContext

        if (
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        setEnabled(
            appContext,
            true
        )

        val intent =
            Intent(
                appContext,
                NexusEyeAlwaysListeningService::class.java
            )

        return try {
            ContextCompat.startForegroundService(
                appContext,
                intent
            )
            true
        } catch (_: Exception) {
            setEnabled(
                appContext,
                false
            )
            false
        }
    }

    fun stop(
        context: Context
    ) {
        val appContext = context.applicationContext

        setEnabled(
            appContext,
            false
        )

        try {
            appContext.stopService(
                Intent(
                    appContext,
                    NexusEyeAlwaysListeningService::class.java
                )
            )
        } catch (_: Exception) {
        }
    }
}
