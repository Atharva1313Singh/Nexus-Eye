package com.thirdeye.app.bluetooth

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * NEXUS EYE ESP32 packet router.
 *
 * This class is deliberately independent from BluetoothGatt and from
 * NexusEyeBleManager. It provides the protocol-processing layer that
 * converts raw BLE bytes into NexusEyeEsp32Protocol.Packet objects.
 *
 * The existing BLE manager can feed incoming notification bytes into
 * receiveBytes() and can later send the result of the packet-building
 * functions through its existing BLE write path.
 */
class NexusEyeEsp32PacketRouter {

    private val packetFramer =
        NexusEyeEsp32Protocol.PacketFramer()

    private val _incomingPackets =
        MutableSharedFlow<NexusEyeEsp32Protocol.Packet>(
            replay = 0,
            extraBufferCapacity = 32
        )

    val incomingPackets:
            SharedFlow<NexusEyeEsp32Protocol.Packet> =
        _incomingPackets

    private val _textMessages =
        MutableSharedFlow<String>(
            replay = 0,
            extraBufferCapacity = 32
        )

    val textMessages:
            SharedFlow<String> =
        _textMessages

    private val _lastError =
        MutableStateFlow<String?>(null)

    val lastError:
            StateFlow<String?> =
        _lastError

    private var sequenceNumber =
        1L

    /**
     * Receive raw BLE notification bytes.
     *
     * The bytes may contain:
     * - part of one packet,
     * - one complete packet,
     * - several complete packets.
     *
     * PacketFramer handles all three cases.
     */
    fun receiveBytes(
        bytes: ByteArray
    ) {

        if (bytes.isEmpty()) {
            return
        }

        try {

            val packets =
                packetFramer.append(
                    bytes
                )

            packets.forEach { packet ->

                _incomingPackets.tryEmit(
                    packet
                )

                handlePacket(
                    packet
                )
            }

        } catch (exception: Exception) {

            _lastError.value =
                exception.message
                    ?: "Unable to process ESP32 BLE packet."
        }
    }

    /**
     * Build a HELLO packet for the ESP32-S3.
     */
    fun createHelloPacket(
        deviceName: String
    ): NexusEyeEsp32Protocol.Packet {

        return nextPacket(
            NexusEyeEsp32Protocol.helloPacket(
                sequence =
                    nextSequence(),
                deviceName =
                    deviceName
            )
        )
    }

    /**
     * Build a HELLO_ACK packet.
     */
    fun createHelloAckPacket(
        deviceName: String
    ): NexusEyeEsp32Protocol.Packet {

        return nextPacket(
            NexusEyeEsp32Protocol.helloAckPacket(
                sequence =
                    nextSequence(),
                deviceName =
                    deviceName
            )
        )
    }

    /**
     * Build a heartbeat packet.
     */
    fun createHeartbeatPacket():
            NexusEyeEsp32Protocol.Packet {

        return nextPacket(
            NexusEyeEsp32Protocol.heartbeatPacket(
                sequence =
                    nextSequence()
            )
        )
    }

    /**
     * Build a text packet for the ESP32.
     */
    fun createTextPacket(
        text: String
    ):
            NexusEyeEsp32Protocol.Packet {

        return nextPacket(
            NexusEyeEsp32Protocol.textPacket(
                text =
                    text,
                sequence =
                    nextSequence()
            )
        )
    }

    /**
     * Build a manual voice-trigger packet.
     */
    fun createManualVoiceTriggerPacket():
            NexusEyeEsp32Protocol.Packet {

        return nextPacket(
            NexusEyeEsp32Protocol.voiceTriggerPacket(
                sequence =
                    nextSequence(),
                triggerCode =
                    NexusEyeEsp32Protocol
                        .VOICE_TRIGGER_MANUAL
            )
        )
    }

    /**
     * Build a button-trigger packet.
     */
    fun createButtonVoiceTriggerPacket():
            NexusEyeEsp32Protocol.Packet {

        return nextPacket(
            NexusEyeEsp32Protocol.voiceTriggerPacket(
                sequence =
                    nextSequence(),
                triggerCode =
                    NexusEyeEsp32Protocol
                        .VOICE_TRIGGER_BUTTON
            )
        )
    }

    /**
     * Build a wake-trigger packet.
     */
    fun createWakeVoiceTriggerPacket():
            NexusEyeEsp32Protocol.Packet {

        return nextPacket(
            NexusEyeEsp32Protocol.voiceTriggerPacket(
                sequence =
                    nextSequence(),
                triggerCode =
                    NexusEyeEsp32Protocol
                        .VOICE_TRIGGER_WAKE
            )
        )
    }

    /**
     * Build an ACK packet.
     */
    fun createAckPacket(
        acknowledgedSequence: Long
    ):
            NexusEyeEsp32Protocol.Packet {

        return nextPacket(
            NexusEyeEsp32Protocol.ackPacket(
                sequence =
                    nextSequence(),
                acknowledgedSequence =
                    acknowledgedSequence
            )
        )
    }

    /**
     * Build a heartbeat ACK packet.
     */
    fun createHeartbeatAckPacket(
        acknowledgedSequence: Long
    ):
            NexusEyeEsp32Protocol.Packet {

        return nextPacket(
            NexusEyeEsp32Protocol.heartbeatAckPacket(
                sequence =
                    nextSequence(),
                acknowledgedSequence =
                    acknowledgedSequence
            )
        )
    }

    /**
     * Build an error packet.
     */
    fun createErrorPacket(
        errorCode: Int,
        message: String
    ):
            NexusEyeEsp32Protocol.Packet {

        return nextPacket(
            NexusEyeEsp32Protocol.errorPacket(
                sequence =
                    nextSequence(),
                errorCode =
                    errorCode,
                message =
                    message
            )
        )
    }

    /**
     * Encode a protocol packet into raw BLE bytes.
     */
    fun encode(
        packet:
        NexusEyeEsp32Protocol.Packet
    ): ByteArray {

        return try {

            packet.encode()

        } catch (exception: Exception) {

            _lastError.value =
                exception.message
                    ?: "Unable to encode ESP32 BLE packet."

            ByteArray(0)
        }
    }

    /**
     * Convenience helper:
     * create and encode a text packet in one operation.
     */
    fun encodeText(
        text: String
    ): ByteArray {

        return encode(
            createTextPacket(
                text
            )
        )
    }

    /**
     * Convenience helper:
     * create and encode a manual voice trigger.
     */
    fun encodeManualVoiceTrigger():
            ByteArray {

        return encode(
            createManualVoiceTriggerPacket()
        )
    }

    /**
     * Convenience helper:
     * create and encode a button voice trigger.
     */
    fun encodeButtonVoiceTrigger():
            ByteArray {

        return encode(
            createButtonVoiceTriggerPacket()
        )
    }

    /**
     * Convenience helper:
     * create and encode a HELLO packet.
     */
    fun encodeHello(
        deviceName: String
    ): ByteArray {

        return encode(
            createHelloPacket(
                deviceName
            )
        )
    }

    /**
     * Reset the protocol stream parser.
     */
    fun clearFramer() {

        packetFramer.clear()
    }

    /**
     * Clear the last protocol error.
     */
    fun clearError() {

        _lastError.value =
            null
    }

    /**
     * Get the next sequence number.
     */
    fun currentSequence():
            Long {

        return sequenceNumber
    }

    private fun nextSequence():
            Long {

        val current =
            sequenceNumber

        sequenceNumber =
            if (
                sequenceNumber >=
                Long.MAX_VALUE - 1
            ) {
                1L
            } else {
                sequenceNumber + 1L
            }

        return current
    }

    private fun nextPacket(
        packet:
        NexusEyeEsp32Protocol.Packet
    ):
            NexusEyeEsp32Protocol.Packet {

        return packet
    }

    private fun handlePacket(
        packet:
        NexusEyeEsp32Protocol.Packet
    ) {

        when (
            packet.messageType
        ) {

            NexusEyeEsp32Protocol.MessageType.TEXT_FROM_ESP32 -> {

                try {

                    val text =
                        NexusEyeEsp32Protocol
                            .decodeText(
                                packet
                            )
                            .text

                    _textMessages.tryEmit(
                        text
                    )

                } catch (exception: Exception) {

                    _lastError.value =
                        exception.message
                            ?: "Invalid ESP32 text packet."
                }
            }

            NexusEyeEsp32Protocol.MessageType.TEXT_TO_ESP32 -> {

                // This packet is normally outgoing from Android.
                // It is still surfaced through incomingPackets for
                // protocol-level observability.
            }

            NexusEyeEsp32Protocol.MessageType.HEARTBEAT -> {

                // The owner of this router can respond with:
                // createHeartbeatAckPacket(packet.sequence)
            }

            NexusEyeEsp32Protocol.MessageType.HEARTBEAT_ACK -> {

                // Heartbeat acknowledgement received.
            }

            NexusEyeEsp32Protocol.MessageType.HELLO -> {

                // ESP32 has announced itself.
            }

            NexusEyeEsp32Protocol.MessageType.HELLO_ACK -> {

                // ESP32 accepted the Android HELLO.
            }

            NexusEyeEsp32Protocol.MessageType.VOICE_TRIGGER -> {

                // The wearable has requested a voice interaction.
            }

            NexusEyeEsp32Protocol.MessageType.AUDIO_START -> {

                // Audio-session processing will be added in the
                // dedicated wearable-audio step.
            }

            NexusEyeEsp32Protocol.MessageType.AUDIO_CHUNK -> {

                // Audio chunk processing will be added in the
                // dedicated wearable-audio step.
            }

            NexusEyeEsp32Protocol.MessageType.AUDIO_END -> {

                // Audio-session completion will be handled later.
            }

            NexusEyeEsp32Protocol.MessageType.CAMERA_FRAME_START -> {

                // Camera transport will be added in the OV7670 step.
            }

            NexusEyeEsp32Protocol.MessageType.CAMERA_FRAME_CHUNK -> {

                // Camera transport will be added in the OV7670 step.
            }

            NexusEyeEsp32Protocol.MessageType.CAMERA_FRAME_END -> {

                // Camera-frame completion will be handled later.
            }

            NexusEyeEsp32Protocol.MessageType.SENSOR_DISTANCE -> {

                // Distance sensor integration will be handled later.
            }

            NexusEyeEsp32Protocol.MessageType.SENSOR_STATUS -> {

                // General wearable sensor integration will be handled later.
            }

            NexusEyeEsp32Protocol.MessageType.ACK -> {

                // Generic acknowledgement received.
            }

            NexusEyeEsp32Protocol.MessageType.ERROR -> {

                try {

                    val error =
                        NexusEyeEsp32Protocol
                            .decodeError(
                                packet
                            )

                    _lastError.value =
                        "ESP32 error ${error.errorCode}: ${error.message}"

                } catch (exception: Exception) {

                    _lastError.value =
                        exception.message
                            ?: "Invalid ESP32 error packet."
                }
            }
        }
    }
}