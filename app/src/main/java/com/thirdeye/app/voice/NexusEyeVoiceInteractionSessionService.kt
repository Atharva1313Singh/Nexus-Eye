package com.thirdeye.app.voice

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.util.Log

/**
 * NEXUS EYE voice interaction session service.
 *
 * Android creates a VoiceInteractionSession through this service
 * when NEXUS EYE is selected as the system voice interaction service.
 *
 * The existing NexusEyeWakeWordService remains responsible for:
 *
 *     Hey Nexus
 *          ↓
 *     openWakeWord
 *          ↓
 *     SpeechRecognizer
 *          ↓
 *     TaskRouter
 *
 * This class intentionally stays lightweight.
 */
class NexusEyeVoiceInteractionSessionService :
    VoiceInteractionSessionService() {

    companion object {

        private const val TAG =
            "NexusEyeVoiceSession"

        @Volatile
        private var activeSession:
                NexusEyeVoiceInteractionSession? =
            null

        fun getActiveSession():
                NexusEyeVoiceInteractionSession? {
            return activeSession
        }

        internal fun setActiveSession(
            session: NexusEyeVoiceInteractionSession?
        ) {
            activeSession = session
        }
    }

    override fun onCreate() {
        super.onCreate()

        Log.d(
            TAG,
            "VoiceInteractionSessionService created"
        )
    }

    override fun onNewSession(
        args: Bundle
    ): VoiceInteractionSession {

        Log.d(
            TAG,
            "Creating new NEXUS EYE voice session"
        )

        return NexusEyeVoiceInteractionSession(
            context = this
        )
    }

    override fun onDestroy() {

        Log.d(
            TAG,
            "VoiceInteractionSessionService destroyed"
        )

        activeSession = null

        super.onDestroy()
    }
}


/**
 * Actual voice interaction session.
 *
 * We don't put the existing wake-word / command engine here.
 * This session exists so Android has a valid VoiceInteractionSession
 * associated with NEXUS EYE.
 */
class NexusEyeVoiceInteractionSession(
    private val context: android.content.Context
) : VoiceInteractionSession(context) {

    companion object {

        private const val TAG =
            "NexusEyeVoiceSession"
    }

    override fun onCreate() {
        super.onCreate()

        NexusEyeVoiceInteractionSessionService
            .setActiveSession(this)

        Log.d(
            TAG,
            "VoiceInteractionSession created"
        )
    }

    override fun onShow(
        args: Bundle?,
        showFlags: Int
    ) {
        super.onShow(
            args,
            showFlags
        )

        Log.d(
            TAG,
            "Voice interaction session shown"
        )
    }

    override fun onHide() {

        Log.d(
            TAG,
            "Voice interaction session hidden"
        )

        super.onHide()
    }

    override fun onDestroy() {

        Log.d(
            TAG,
            "Voice interaction session destroyed"
        )

        NexusEyeVoiceInteractionSessionService
            .setActiveSession(null)

        super.onDestroy()
    }
}
