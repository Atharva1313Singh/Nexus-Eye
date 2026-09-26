package com.thirdeye.app.audio

/**
 * Supported audio formats for the NEXUS EYE wearable.
 */
enum class NexusEyeAudioFormat {
    MP3,
    WAV,
    PCM,
    UNKNOWN
}

/**
 * Detects the format of an audio byte array.
 *
 * This is deliberately conservative.
 * We never label arbitrary synthesized audio as MP3.
 */
object NexusEyeAudioFormatDetector {

    fun detect(bytes: ByteArray): NexusEyeAudioFormat {
        if (bytes.size < 4) {
            return NexusEyeAudioFormat.UNKNOWN
        }

        // MP3 frame with MPEG Audio sync.
        if (
            isMp3FrameHeader(bytes[0], bytes[1]) ||
            hasId3Header(bytes)
        ) {
            return NexusEyeAudioFormat.MP3
        }

        // RIFF/WAVE
        if (
            bytes[0] == 'R'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == 'F'.code.toByte()
        ) {
            if (bytes.size >= 12) {
                if (
                    bytes[8] == 'W'.code.toByte() &&
                    bytes[9] == 'A'.code.toByte() &&
                    bytes[10] == 'V'.code.toByte() &&
                    bytes[11] == 'E'.code.toByte()
                ) {
                    return NexusEyeAudioFormat.WAV
                }
            }
        }

        return NexusEyeAudioFormat.UNKNOWN
    }

    private fun hasId3Header(bytes: ByteArray): Boolean {
        return bytes.size >= 3 &&
                bytes[0] == 'I'.code.toByte() &&
                bytes[1] == 'D'.code.toByte() &&
                bytes[2] == '3'.code.toByte()
    }

    private fun isMp3FrameHeader(
        first: Byte,
        second: Byte
    ): Boolean {

        val firstValue = first.toInt() and 0xFF
        val secondValue = second.toInt() and 0xFF

        // MPEG audio sync:
        // 11 consecutive 1-bits at the beginning.
        return firstValue == 0xFF &&
                (secondValue and 0xE0) == 0xE0
    }
}

/**
 * Represents an audio file together with its detected format.
 */
data class NexusEyeAudioData(
    val bytes: ByteArray,
    val format: NexusEyeAudioFormat
) {
    val isMp3: Boolean
        get() = format == NexusEyeAudioFormat.MP3
}

/**
 * Creates a validated audio object.
 */
object NexusEyeAudioDataFactory {

    fun fromBytes(bytes: ByteArray): NexusEyeAudioData {
        val format =
            NexusEyeAudioFormatDetector.detect(bytes)

        return NexusEyeAudioData(
            bytes = bytes,
            format = format
        )
    }
}