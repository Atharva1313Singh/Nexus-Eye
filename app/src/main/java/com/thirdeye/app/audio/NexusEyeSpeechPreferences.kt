package com.thirdeye.app.audio

import android.content.Context

class NexusEyeSpeechPreferences(
    context: Context
) {

    private val preferences =
        context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )

    fun getSpeechRate(): Float {

        val storedRate =
            preferences.getFloat(
                KEY_SPEECH_RATE,
                DEFAULT_SPEECH_RATE
            )

        return clamp(
            storedRate
        )
    }

    fun setSpeechRate(
        speechRate: Float
    ): Float {

        val safeRate =
            clamp(
                speechRate
            )

        preferences
            .edit()
            .putFloat(
                KEY_SPEECH_RATE,
                safeRate
            )
            .apply()

        return safeRate
    }

    fun resetSpeechRate(): Float {

        val defaultRate =
            DEFAULT_SPEECH_RATE

        preferences
            .edit()
            .putFloat(
                KEY_SPEECH_RATE,
                defaultRate
            )
            .apply()

        return defaultRate
    }

    private fun clamp(
        value: Float
    ): Float {

        return value.coerceIn(
            MIN_SPEECH_RATE,
            MAX_SPEECH_RATE
        )
    }

    companion object {

        private const val PREFERENCES_NAME =
            "nexus_eye_speech_preferences"

        private const val KEY_SPEECH_RATE =
            "speech_rate"

        const val MIN_SPEECH_RATE =
            0.50f

        const val DEFAULT_SPEECH_RATE =
            0.88f

        const val MAX_SPEECH_RATE =
            1.50f
    }
}