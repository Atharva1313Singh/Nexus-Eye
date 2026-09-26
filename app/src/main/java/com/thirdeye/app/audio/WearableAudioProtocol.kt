package com.thirdeye.app.audio

import com.thirdeye.app.bluetooth.NexusEyeBleProtocol
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min

object WearableAudioProtocol {

    const val PROTOCOL_VERSION: Byte = 1

    const val PACKET_TYPE_START: Byte = 1
    const val PACKET_TYPE_DATA: Byte = 2
    const val PACKET_TYPE_END: Byte = 3
    const val PACKET_TYPE_CANCEL: Byte = 4

    const val HEADER_SIZE = 10

    /*
     * Preferred maximum audio payload size.
     *
     * The real BLE packet size is controlled by the negotiated
     * MTU and is supplied to splitAudio().
     */
    const val MAX_PAYLOAD_SIZE = 180

    /*
     * Audio uses the same BLE service as the normal NEXUS EYE
     * communication channel.
     */
    val AUDIO_SERVICE_UUID: UUID =
        NexusEyeBleProtocol.SERVICE_UUID

    /*
     * Android -> ESP32 audio write characteristic.
     */
    val AUDIO_WRITE_CHARACTERISTIC_UUID: UUID =
        UUID.fromString(
            "12345678-1234-5678-1234-56789abcdef3"
        )

    /*
     * ESP32 -> Android audio notification characteristic.
     *
     * This is reserved for future wearable audio acknowledgements
     * or transfer status messages.
     */
    val AUDIO_NOTIFY_CHARACTERISTIC_UUID: UUID =
        UUID.fromString(
            "12345678-1234-5678-1234-56789abcdef4"
        )

    private val sessionCounter =
        AtomicInteger(1)

    data class AudioPacket(
        val type: Byte,
        val sessionId: Int,
        val packetIndex: Int,
        val totalPackets: Int,
        val payload: ByteArray
    )

    /**
     * Splits an MP3 file into START, DATA and END packets.
     *
     * maxBlePayloadSize is the maximum number of bytes that
     * Android can place into one BLE characteristic write.
     *
     * For normal Android BLE operation this is generally:
     *
     *     negotiated MTU - 3
     */
    fun splitAudio(
        audioBytes: ByteArray,
        maxBlePayloadSize: Int =
            MAX_PAYLOAD_SIZE + HEADER_SIZE
    ): List<ByteArray> {

        require(
            audioBytes.isNotEmpty()
        ) {
            "Audio data must not be empty."
        }

        require(
            maxBlePayloadSize > HEADER_SIZE
        ) {
            "BLE payload size must be greater than the audio protocol header size."
        }

        val sessionId =
            newSessionId()

        /*
         * Never exceed our protocol's preferred maximum packet size,
         * and never exceed the actual BLE write size.
         */
        val safePacketSize =
            min(
                maxBlePayloadSize,
                MAX_PAYLOAD_SIZE + HEADER_SIZE
            )

        /*
         * Reserve 10 bytes for the protocol header.
         */
        val audioPayloadSize =
            max(
                1,
                safePacketSize - HEADER_SIZE
            )

        val dataPacketCount =
            (
                    audioBytes.size +
                            audioPayloadSize -
                            1
                    ) /
                    audioPayloadSize

        /*
         * START + DATA packets + END
         */
        val totalPackets =
            dataPacketCount + 2

        require(
            totalPackets <= 0xFFFF
        ) {
            "MP3 file requires too many BLE audio packets."
        }

        val packets =
            ArrayList<ByteArray>(
                totalPackets
            )

        /*
         * START
         */
        packets += encodePacket(
            AudioPacket(
                type = PACKET_TYPE_START,
                sessionId = sessionId,
                packetIndex = 0,
                totalPackets = totalPackets,
                payload = ByteArray(0)
            )
        )

        /*
         * DATA
         */
        var offset = 0
        var packetIndex = 1

        while (
            offset < audioBytes.size
        ) {

            val end =
                min(
                    offset + audioPayloadSize,
                    audioBytes.size
                )

            val chunk =
                audioBytes.copyOfRange(
                    offset,
                    end
                )

            packets += encodePacket(
                AudioPacket(
                    type = PACKET_TYPE_DATA,
                    sessionId = sessionId,
                    packetIndex = packetIndex,
                    totalPackets = totalPackets,
                    payload = chunk
                )
            )

            offset = end
            packetIndex++
        }

        /*
         * END
         */
        packets += encodePacket(
            AudioPacket(
                type = PACKET_TYPE_END,
                sessionId = sessionId,
                packetIndex = totalPackets - 1,
                totalPackets = totalPackets,
                payload = ByteArray(0)
            )
        )

        /*
         * Final safety check.
         */
        packets.forEach { packet ->
            require(
                packet.size <= maxBlePayloadSize
            ) {
                "Generated audio packet exceeds the BLE payload limit."
            }
        }

        return packets
    }

    /**
     * Creates a CANCEL packet for an active transfer.
     */
    fun createCancelPacket(
        sessionId: Int,
        totalPackets: Int = 0
    ): ByteArray {

        return encodePacket(
            AudioPacket(
                type = PACKET_TYPE_CANCEL,
                sessionId = sessionId,
                packetIndex = 0,
                totalPackets = totalPackets,
                payload = ByteArray(0)
            )
        )
    }

    /**
     * Encodes one protocol packet.
     *
     * Packet layout:
     *
     * byte 0      protocol version
     * byte 1      packet type
     * bytes 2-5   session ID
     * bytes 6-7   packet index
     * bytes 8-9   total packet count
     * bytes 10+   audio payload
     */
    fun encodePacket(
        packet: AudioPacket
    ): ByteArray {

        require(
            packet.packetIndex >= 0
        ) {
            "Packet index cannot be negative."
        }

        require(
            packet.totalPackets >= 0
        ) {
            "Total packet count cannot be negative."
        }

        require(
            packet.packetIndex <= 0xFFFF
        ) {
            "Packet index is too large."
        }

        require(
            packet.totalPackets <= 0xFFFF
        ) {
            "Total packet count is too large."
        }

        val payload =
            packet.payload

        val buffer =
            ByteBuffer
                .allocate(
                    HEADER_SIZE +
                            payload.size
                )
                .order(
                    ByteOrder.BIG_ENDIAN
                )

        buffer.put(
            PROTOCOL_VERSION
        )

        buffer.put(
            packet.type
        )

        buffer.putInt(
            packet.sessionId
        )

        buffer.putShort(
            packet.packetIndex.toShort()
        )

        buffer.putShort(
            packet.totalPackets.toShort()
        )

        buffer.put(
            payload
        )

        return buffer.array()
    }

    /**
     * Parses a received audio protocol packet.
     */
    fun parsePacket(
        bytes: ByteArray
    ): AudioPacket? {

        if (
            bytes.size < HEADER_SIZE
        ) {
            return null
        }

        return try {

            val buffer =
                ByteBuffer
                    .wrap(bytes)
                    .order(
                        ByteOrder.BIG_ENDIAN
                    )

            val version =
                buffer.get()

            if (
                version !=
                PROTOCOL_VERSION
            ) {
                return null
            }

            val type =
                buffer.get()

            val sessionId =
                buffer.getInt()

            val packetIndex =
                buffer.getShort()
                    .toInt() and 0xFFFF

            val totalPackets =
                buffer.getShort()
                    .toInt() and 0xFFFF

            val payloadSize =
                bytes.size -
                        HEADER_SIZE

            val payload =
                ByteArray(
                    payloadSize
                )

            if (
                payloadSize > 0
            ) {
                buffer.get(
                    payload
                )
            }

            AudioPacket(
                type = type,
                sessionId = sessionId,
                packetIndex = packetIndex,
                totalPackets = totalPackets,
                payload = payload
            )

        } catch (_: Exception) {
            null
        }
    }

    /**
     * Generates a unique transfer session ID.
     */
    fun newSessionId(): Int {

        var value =
            sessionCounter.getAndIncrement()

        if (
            value <= 0
        ) {
            sessionCounter.set(2)
            value = 1
        }

        return value
    }

    /**
     * Calculates how many MP3 bytes can safely fit into
     * one DATA packet for the specified BLE payload size.
     */
    fun calculateSafeAudioPayloadSize(
        maxBlePayloadSize: Int
    ): Int {

        if (
            maxBlePayloadSize <=
            HEADER_SIZE
        ) {
            return 1
        }

        return min(
            MAX_PAYLOAD_SIZE,
            maxBlePayloadSize -
                    HEADER_SIZE
        )
    }

    /**
     * Checks whether an already encoded packet fits into
     * the currently negotiated BLE payload.
     */
    fun fitsBlePayload(
        packet: ByteArray,
        maxBlePayloadSize: Int
    ): Boolean {

        return packet.size <=
                maxBlePayloadSize
    }

    /**
     * Reconstructs the original audio data from DATA packets.
     *
     * Packets are sorted by packetIndex before joining.
     */
    fun extractDataPayloads(
        packets: List<ByteArray>
    ): ByteArray {

        val dataPackets =
            packets
                .mapNotNull {
                    parsePacket(it)
                }
                .filter {
                    it.type ==
                            PACKET_TYPE_DATA
                }
                .sortedBy {
                    it.packetIndex
                }

        if (
            dataPackets.isEmpty()
        ) {
            return ByteArray(0)
        }

        val totalSize =
            dataPackets.sumOf {
                it.payload.size
            }

        val result =
            ByteArray(
                totalSize
            )

        var offset = 0

        for (
        packet in dataPackets
        ) {

            packet.payload.copyInto(
                destination = result,
                destinationOffset = offset
            )

            offset +=
                packet.payload.size
        }

        return result
    }
}