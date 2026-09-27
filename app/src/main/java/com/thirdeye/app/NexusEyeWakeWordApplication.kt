package com.thirdeye.app

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.thirdeye.app.voice.NexusEyeWakeWordService

/**
 * Starts the Hey Nexus microphone foreground service whenever the app has a
 * visible activity and microphone permission has already been granted.
 *
 * Starting the microphone foreground service from a visible activity is
 * important on modern Android because microphone access is a while-in-use
 * permission.
 */
class NexusEyeWakeWordApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        registerActivityLifecycleCallbacks(
            object : ActivityLifecycleCallbacks {

                override fun onActivityResumed(
                    activity: android.app.Activity
                ) {
                    startWakeWordServiceIfAllowed()
                }

                override fun onActivityCreated(
                    activity: android.app.Activity,
                    savedInstanceState: android.os.Bundle?
                ) = Unit

                override fun onActivityStarted(
                    activity: android.app.Activity
                ) = Unit

                override fun onActivityPaused(
                    activity: android.app.Activity
                ) = Unit

                override fun onActivityStopped(
                    activity: android.app.Activity
                ) = Unit

                override fun onActivitySaveInstanceState(
                    activity: android.app.Activity,
                    outState: android.os.Bundle
                ) = Unit

                override fun onActivityDestroyed(
                    activity: android.app.Activity
                ) = Unit
            }
        )
    }

    private fun startWakeWordServiceIfAllowed() {
        val microphoneGranted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

        if (!microphoneGranted) {
            return
        }

        val intent =
            Intent(
                this,
                NexusEyeWakeWordService::class.java
            )

        try {
            if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O
            ) {
                startForegroundService(
                    intent
                )
            } else {
                startService(
                    intent
                )
            }
        } catch (_: Exception) {
            // Android may reject the start if the app is no longer visible.
            // The next activity-resume callback will try again.
        }
    }
}
