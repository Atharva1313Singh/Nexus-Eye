package com.thirdeye.app.voice

object NexusEyeWearableVoiceTrigger {

    const val START_VOICE =
        "VOICE_TRIGGER"

    const val STOP_VOICE =
        "VOICE_STOP"

    fun isStartVoiceCommand(
        message: String
    ): Boolean {

        val normalized =
            message
                .trim()
                .uppercase()

        return normalized ==
                START_VOICE ||
                normalized == "START_VOICE" ||
                normalized == "VOICE_BUTTON" ||
                normalized == "BUTTON_VOICE"
    }

    fun isStopVoiceCommand(
        message: String
    ): Boolean {

        val normalized =
            message
                .trim()
                .uppercase()

        return normalized ==
                STOP_VOICE ||
                normalized == "STOP_VOICE" ||
                normalized == "STOP_LISTENING"
    }
}