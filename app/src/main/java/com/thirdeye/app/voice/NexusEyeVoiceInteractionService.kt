package com.thirdeye.app.voice

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.util.Log

/**
 * Minimal voice interaction session.
 *
 * The existing Nexus-Eye command architecture remains unchanged.
 * This class is only the Android Digital Assistant entry point.
 */
class NexusEyeVoiceInteractionSession(
    context: android.content.Context
) : VoiceInteractionSession(context) {

    companion object {
        private const val TAG = "NexusEyeVoiceSession"
    }

    override fun onShow(
        args: Bundle?,
        showFlags: Int
    ) {
        super.onShow(args, showFlags)

        Log.i(
            TAG,
            "Nexus-Eye Digital Assistant session shown"
        )

        /*
         * Do not create another SpeechRecognizer here.
         *
         * Your existing:
         *
         * NexusEyeWakeWordService
         *       ->
         * NexusEyeSpeechRecognizer
         *       ->
         * TaskRouter
         *
         * remains the command pipeline.
         */
    }

    override fun onHide() {
        Log.i(
            TAG,
            "Nexus-Eye Digital Assistant session hidden"
        )

        super.onHide()
    }
}