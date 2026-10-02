package com.thirdeye.app.voice

import android.content.Intent
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.util.Log

/**
 * Creates voice interaction sessions for the Android Digital Assistant.
 *
 * This is separate from NexusEyeWakeWordService.
 */
class NexusEyeVoiceInteractionSessionService :
    VoiceInteractionSessionService() {

    companion object {
        private const val TAG = "NexusEyeSessionService"
    }

    override fun onNewSession(
        args: android.os.Bundle
    ): VoiceInteractionSession {

        Log.i(TAG, "Creating Nexus-Eye voice interaction session")

        return NexusEyeVoiceInteractionSession(this)
    }
}