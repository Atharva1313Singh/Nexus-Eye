package com.thirdeye.app.voice

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionService
import android.util.Log

/**
 * NEXUS EYE global VoiceInteractionService.
 *
 * Android keeps the currently selected VoiceInteractionService
 * available so it can participate in voice interaction/hotword
 * workflows.
 *
 * IMPORTANT:
 *
 * This does NOT replace NexusEyeWakeWordService.
 *
 * NEXUS EYE still uses:
 *
 *     openWakeWord
 *          ↓
 *     "Hey Nexus"
 *          ↓
 *     stop wake-word microphone
 *          ↓
 *     Android SpeechRecognizer
 *          ↓
 *     TaskRouter
 *
 * This service gives Android an official voice-interaction
 * component for NEXUS EYE.
 */
class NexusEyeVoiceInteractionService :
    VoiceInteractionService() {

    companion object {

        private const val TAG =
            "NexusEyeVoiceInteraction"

        @Volatile
        private var instance:
                NexusEyeVoiceInteractionService? =
            null

        fun getInstance():
                NexusEyeVoiceInteractionService? {
            return instance
        }

        fun isActive(
            context: android.content.Context
        ): Boolean {

            return try {

                VoiceInteractionService
                    .isActiveService(
                        context,
                        ComponentName(
                            context,
                            NexusEyeVoiceInteractionService::class.java
                        )
                    )

            } catch (
                exception: Exception
            ) {

                false
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        instance = this

        Log.d(
            TAG,
            "NEXUS EYE VoiceInteractionService created"
        )
    }

    override fun onReady() {
        super.onReady()

        Log.d(
            TAG,
            "NEXUS EYE VoiceInteractionService ready"
        )

        /*
         * Do not start SpeechRecognizer here.
         *
         * Do not start another microphone listener here.
         *
         * The existing NexusEyeWakeWordService owns the
         * openWakeWord microphone pipeline.
         */
    }

    override fun onPrepareToShowSession(
        args: Bundle,
        flags: Int
    ) {
        super.onPrepareToShowSession(
            args,
            flags
        )

        Log.d(
            TAG,
            "Preparing voice interaction session"
        )
    }

    override fun onLaunchVoiceAssistFromKeyguard() {
        super.onLaunchVoiceAssistFromKeyguard()

        Log.d(
            TAG,
            "Voice assist launched from keyguard"
        )
    }

    /**
     * Opens an activity through the active voice-interaction
     * service.
     *
     * This is different from calling startActivity() from an
     * ordinary background foreground-service process.
     */
    fun launchVoiceActivity(
        intent: Intent
    ): Boolean {

        return try {

            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            )

            startActivity(
                intent
            )

            Log.d(
                TAG,
                "VoiceInteractionService launched: " +
                        intent.component
            )

            true

        } catch (
            exception: Exception
        ) {

            Log.e(
                TAG,
                "VoiceInteractionService failed to launch activity",
                exception
            )

            false
        }
    }

    /**
     * Request Android to show the voice interaction session.
     *
     * This can be used later if you want NEXUS EYE to show a
     * visible assistant UI.
     */
    fun showNexusSession() {

        try {

            showSession(
                Bundle(),
                VoiceInteractionSession.SHOW_WITH_ASSIST
            )

        } catch (
            exception: Exception
        ) {

            Log.e(
                TAG,
                "Could not show NEXUS EYE voice session",
                exception
            )
        }
    }

    override fun onGetSupportedVoiceActions(
        actions: Set<String>
    ): Set<String> {

        /*
         * We don't claim support for arbitrary Android
         * extended voice actions.
         *
         * Our own TaskRouter continues handling commands.
         */

        return emptySet()
    }

    override fun onShutdown() {

        Log.d(
            TAG,
            "NEXUS EYE VoiceInteractionService shutting down"
        )

        super.onShutdown()
    }

    override fun onDestroy() {

        Log.d(
            TAG,
            "NEXUS EYE VoiceInteractionService destroyed"
        )

        if (
            instance === this
        ) {
            instance = null
        }

        super.onDestroy()
    }
}
