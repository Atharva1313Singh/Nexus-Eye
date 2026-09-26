package com.thirdeye.app.bluetooth

import android.content.Context

/**
 * Persistent Android-side preferences for the future NEXUS EYE ESP32-S3 wearable.
 *
 * These values describe how the Android app should behave around the wearable.
 * Actual BLE connection, packet transport, audio transport, camera transport,
 * and hardware behavior are implemented separately and belong to the hardware stage.
 */
data class NexusEyeEsp32Settings(
    val preferredDeviceName: String,
    val autoReconnect: Boolean,
    val useWearableAudioWhenConnected: Boolean,
    val voiceTriggerEnabled: Boolean
)

class NexusEyeEsp32SettingsManager(
    context: Context
) {

    private val preferences =
        context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    fun getSettings(): NexusEyeEsp32Settings {
        return NexusEyeEsp32Settings(
            preferredDeviceName =
                preferences.getString(
                    KEY_PREFERRED_DEVICE_NAME,
                    DEFAULT_DEVICE_NAME
                )?.trim().orEmpty().ifBlank {
                    DEFAULT_DEVICE_NAME
                },
            autoReconnect =
                preferences.getBoolean(
                    KEY_AUTO_RECONNECT,
                    DEFAULT_AUTO_RECONNECT
                ),
            useWearableAudioWhenConnected =
                preferences.getBoolean(
                    KEY_USE_WEARABLE_AUDIO,
                    DEFAULT_USE_WEARABLE_AUDIO
                ),
            voiceTriggerEnabled =
                preferences.getBoolean(
                    KEY_VOICE_TRIGGER_ENABLED,
                    DEFAULT_VOICE_TRIGGER_ENABLED
                )
        )
    }

    fun saveSettings(
        settings: NexusEyeEsp32Settings
    ) {
        preferences.edit()
            .putString(
                KEY_PREFERRED_DEVICE_NAME,
                settings.preferredDeviceName.trim()
                    .ifBlank { DEFAULT_DEVICE_NAME }
            )
            .putBoolean(
                KEY_AUTO_RECONNECT,
                settings.autoReconnect
            )
            .putBoolean(
                KEY_USE_WEARABLE_AUDIO,
                settings.useWearableAudioWhenConnected
            )
            .putBoolean(
                KEY_VOICE_TRIGGER_ENABLED,
                settings.voiceTriggerEnabled
            )
            .apply()
    }

    fun resetToDefaults() {
        saveSettings(
            NexusEyeEsp32Settings(
                preferredDeviceName = DEFAULT_DEVICE_NAME,
                autoReconnect = DEFAULT_AUTO_RECONNECT,
                useWearableAudioWhenConnected = DEFAULT_USE_WEARABLE_AUDIO,
                voiceTriggerEnabled = DEFAULT_VOICE_TRIGGER_ENABLED
            )
        )
    }

    companion object {
        private const val PREFS_NAME =
            "nexus_eye_esp32_settings"

        private const val KEY_PREFERRED_DEVICE_NAME =
            "preferred_device_name"

        private const val KEY_AUTO_RECONNECT =
            "auto_reconnect"

        private const val KEY_USE_WEARABLE_AUDIO =
            "use_wearable_audio_when_connected"

        private const val KEY_VOICE_TRIGGER_ENABLED =
            "voice_trigger_enabled"

        const val DEFAULT_DEVICE_NAME =
            "NEXUS EYE"

        const val DEFAULT_AUTO_RECONNECT =
            true

        const val DEFAULT_USE_WEARABLE_AUDIO =
            true

        const val DEFAULT_VOICE_TRIGGER_ENABLED =
            true
    }
}
