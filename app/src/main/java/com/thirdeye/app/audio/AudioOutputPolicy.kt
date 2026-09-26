package com.thirdeye.app.audio

import com.thirdeye.app.bluetooth.NexusEyeConnectionState

enum class AudioDestination {
    ESP32_WEARABLE,
    PHONE_FALLBACK
}

object AudioOutputPolicy {

    fun destinationFor(
        connectionState: NexusEyeConnectionState
    ): AudioDestination {

        return when (connectionState) {

            NexusEyeConnectionState.READY,
            NexusEyeConnectionState.CONNECTED -> {
                AudioDestination.ESP32_WEARABLE
            }

            else -> {
                AudioDestination.PHONE_FALLBACK
            }
        }
    }

    fun allowsPhoneSpeakerForAssistant(
        connectionState: NexusEyeConnectionState
    ): Boolean {
        return destinationFor(connectionState) ==
                AudioDestination.PHONE_FALLBACK
    }
}