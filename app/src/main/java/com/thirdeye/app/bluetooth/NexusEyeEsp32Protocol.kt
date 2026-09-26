package com.thirdeye.app.bluetooth

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * NEXUS EYE ESP32-S3 BLE protocol.
 *
 * Packet format:
 *
 *   4 bytes   Magic
 *   1 byte    Protocol version
 *   1 byte    Message type
 *   2 bytes   Flags
 *   8 bytes   Sequence number
 *   4 bytes   Payload length
 *   N bytes   Payload
 *   2 bytes   CRC-16-CCITT
 *
 * Integer values use BIG_ENDIAN byte order.
 */
object NexusEyeEsp32Protocol {

    const val MAGIC = "NEX1"

    const val PROTOCOL_VERSION: Byte = 1

    /**
     * 4 magic
     * + 1 version
     * + 1 message type
     * + 2 flags
     * + 8 sequence
     * + 4 payload length
     */
    const val HEADER_SIZE = 20

    const val CRC_SIZE = 2

    const val MAX_PAYLOAD_SIZE = 1_048_576

    enum class MessageType(
        val code: Byte
    ) {
        HELLO(0x01),
        HELLO_ACK(0x02),

        HEARTBEAT(0x03),
        HEARTBEAT_ACK(0x04),

        TEXT_FROM_ESP32(0x10),
        TEXT_TO_ESP32(0x11),

        VOICE_TRIGGER(0x12),

        AUDIO_START(0x20),
        AUDIO_CHUNK(0x21),
        AUDIO_END(0x22),

        CAMERA_FRAME_START(0x30),
        CAMERA_FRAME_CHUNK(0x31),
        CAMERA_FRAME_END(0x32),

        SENSOR_DISTANCE(0x40),
        SENSOR_STATUS(0x41),

        ACK(0x50),
        ERROR(0x51);

        companion object {

            fun fromCode(
                code: Byte
            ): MessageType? {

                return entries.firstOrNull {
                    it.code == code
                }
            }
        }
    }

    enum class AudioCodec(
        val code: Byte
    ) {
        MP3(0x01),
        PCM_S16LE(0x02);

        companion object {

            fun fromCode(
                code: Byte
            ): AudioCodec? {

                return entries.firstOrNull {
                    it.code == code
                }
            }
        }
    }

    enum class SensorType(
        val code: Byte
    ) {
        DISTANCE_CM(0x01),
        BATTERY_PERCENT(0x02),
        BUTTON_STATE(0x03),
        DEVICE_TEMPERATURE(0x04);

        companion object {

            fun fromCode(
                code: Byte
            ): SensorType? {

                return entries.firstOrNull {
                    it.code == code
                }
            }
        }
    }

    data class Packet(
        val messageType: MessageType,
        val flags: Int = 0,
        val sequence: Long = 0L,
        val payload: ByteArray = ByteArray(0)
    ) {

        init {

            require(
                flags in 0..0xFFFF
            ) {
                "Flags must be between 0 and 65535."
            }

            require(
                sequence >= 0L
            ) {
                "Sequence cannot be negative."
            }

            require(
                payload.size <= MAX_PAYLOAD_SIZE
            ) {
                "Payload is too large."
            }
        }

        fun encode(): ByteArray {

            return encodePacket(
                packet = this
            )
        }
    }

    data class DecodedText(
        val text: String
    )

    data class AudioStartInfo(
        val codec: AudioCodec,
        val sampleRate: Int,
        val channels: Int,
        val sessionId: Long,
        val totalBytes: Long
    )

    data class AudioChunkInfo(
        val sessionId: Long,
        val chunkIndex: Long,
        val audioBytes: ByteArray
    )

    data class CameraFrameStartInfo(
        val frameId: Long,
        val width: Int,
        val height: Int,
        val format: Int,
        val totalBytes: Long
    )

    data class CameraFrameChunkInfo(
        val frameId: Long,
        val chunkIndex: Long,
        val imageBytes: ByteArray
    )

    data class DistanceSensorInfo(
        val distanceCentimeters: Float
    )

    data class AckInfo(
        val acknowledgedSequence: Long
    )

    data class ErrorInfo(
        val errorCode: Int,
        val message: String
    )

    fun encodePacket(
        packet: Packet
    ): ByteArray {

        val payloadLength =
            packet.payload.size

        require(
            payloadLength <= MAX_PAYLOAD_SIZE
        ) {
            "Payload exceeds maximum allowed size."
        }

        val headerBody =
            ByteBuffer.allocate(
                1 +
                        1 +
                        2 +
                        8 +
                        4
            )
                .order(
                    ByteOrder.BIG_ENDIAN
                )

        headerBody.put(
            PROTOCOL_VERSION
        )

        headerBody.put(
            packet.messageType.code
        )

        headerBody.putShort(
            packet.flags.toShort()
        )

        headerBody.putLong(
            packet.sequence
        )

        headerBody.putInt(
            payloadLength
        )

        val bodyStream =
            ByteArrayOutputStream(
                headerBody.capacity() +
                        payloadLength
            )

        bodyStream.write(
            headerBody.array()
        )

        bodyStream.write(
            packet.payload
        )

        val body =
            bodyStream.toByteArray()

        val crc =
            crc16Ccitt(
                body
            )

        val output =
            ByteArrayOutputStream(
                MAGIC.length +
                        body.size +
                        CRC_SIZE
            )

        output.write(
            MAGIC.toByteArray(
                Charsets.US_ASCII
            )
        )

        output.write(
            body
        )

        val crcBuffer =
            ByteBuffer.allocate(
                CRC_SIZE
            )
                .order(
                    ByteOrder.BIG_ENDIAN
                )

        crcBuffer.putShort(
            crc
        )

        output.write(
            crcBuffer.array()
        )

        return output.toByteArray()
    }

    fun decodePacket(
        data: ByteArray
    ): Packet {

        require(
            data.size >=
                    HEADER_SIZE +
                    CRC_SIZE
        ) {
            "BLE packet is too short."
        }

        val magic =
            data.copyOfRange(
                0,
                MAGIC.length
            )
                .toString(
                    Charsets.US_ASCII
                )

        require(
            magic == MAGIC
        ) {
            "Invalid NEXUS EYE BLE packet magic."
        }

        val buffer =
            ByteBuffer.wrap(
                data
            )
                .order(
                    ByteOrder.BIG_ENDIAN
                )

        buffer.position(
            MAGIC.length
        )

        val version =
            buffer.get()

        require(
            version == PROTOCOL_VERSION
        ) {
            "Unsupported protocol version: $version"
        }

        val messageTypeCode =
            buffer.get()

        val messageType =
            MessageType.fromCode(
                messageTypeCode
            )
                ?: throw IllegalArgumentException(
                    "Unknown message type: 0x" +
                            messageTypeCode
                                .toUByte()
                                .toString(16)
                )

        val flags =
            buffer.short
                .toInt()
                .and(
                    0xFFFF
                )

        val sequence =
            buffer.long

        require(
            sequence >= 0L
        ) {
            "Invalid negative sequence."
        }

        val payloadLength =
            buffer.int

        require(
            payloadLength >= 0
        ) {
            "Negative payload length."
        }

        require(
            payloadLength <=
                    MAX_PAYLOAD_SIZE
        ) {
            "Payload is too large."
        }

        val payloadStart =
            HEADER_SIZE

        val payloadEnd =
            payloadStart +
                    payloadLength

        val expectedPacketSize =
            payloadEnd +
                    CRC_SIZE

        require(
            data.size == expectedPacketSize
        ) {
            "Invalid packet length. " +
                    "Expected $expectedPacketSize bytes but received ${data.size}."
        }

        val payload =
            data.copyOfRange(
                payloadStart,
                payloadEnd
            )

        val receivedCrc =
            ByteBuffer.wrap(
                data,
                payloadEnd,
                CRC_SIZE
            )
                .order(
                    ByteOrder.BIG_ENDIAN
                )
                .short

        val crcBody =
            data.copyOfRange(
                MAGIC.length,
                payloadEnd
            )

        val calculatedCrc =
            crc16Ccitt(
                crcBody
            )

        require(
            receivedCrc ==
                    calculatedCrc
        ) {
            "BLE packet CRC verification failed."
        }

        return Packet(
            messageType =
                messageType,
            flags =
                flags,
            sequence =
                sequence,
            payload =
                payload
        )
    }

    fun textPacket(
        text: String,
        sequence: Long
    ): Packet {

        return Packet(
            messageType =
                MessageType.TEXT_TO_ESP32,
            flags =
                FLAG_TEXT_UTF8,
            sequence =
                sequence,
            payload =
                text.toByteArray(
                    Charsets.UTF_8
                )
        )
    }

    fun decodeText(
        packet: Packet
    ): DecodedText {

        require(
            packet.messageType ==
                    MessageType.TEXT_FROM_ESP32 ||
                    packet.messageType ==
                    MessageType.TEXT_TO_ESP32
        ) {
            "Packet is not a text packet."
        }

        return DecodedText(
            text =
                packet.payload.toString(
                    Charsets.UTF_8
                )
        )
    }

    fun voiceTriggerPacket(
        sequence: Long,
        triggerCode: Int =
            VOICE_TRIGGER_MANUAL
    ): Packet {

        val buffer =
            ByteBuffer.allocate(
                4
            )
                .order(
                    ByteOrder.BIG_ENDIAN
                )

        buffer.putInt(
            triggerCode
        )

        return Packet(
            messageType =
                MessageType.VOICE_TRIGGER,
            sequence =
                sequence,
            payload =
                buffer.array()
        )
    }

    fun audioStartPacket(
        sequence: Long,
        sessionId: Long,
        codec: AudioCodec,
        sampleRate: Int,
        channels: Int,
        totalBytes: Long
    ): Packet {

        require(
            sessionId >= 0L
        ) {
            "Session ID cannot be negative."
        }

        require(
            sampleRate >= 0
        ) {
            "Sample rate cannot be negative."
        }

        require(
            channels >= 0
        ) {
            "Channel count cannot be negative."
        }

        require(
            totalBytes >= 0L
        ) {
            "Total audio bytes cannot be negative."
        }

        val buffer =
            ByteBuffer.allocate(
                1 +
                        4 +
                        2 +
                        8 +
                        8
            )
                .order(
                    ByteOrder.BIG_ENDIAN
                )

        buffer.put(
            codec.code
        )

        buffer.putInt(
            sampleRate
        )

        buffer.putShort(
            channels.toShort()
        )

        buffer.putLong(
            sessionId
        )

        buffer.putLong(
            totalBytes
        )

        return Packet(
            messageType =
                MessageType.AUDIO_START,
            sequence =
                sequence,
            payload =
                buffer.array()
        )
    }

    fun decodeAudioStart(
        packet: Packet
    ): AudioStartInfo {

        require(
            packet.messageType ==
                    MessageType.AUDIO_START
        ) {
            "Packet is not an AUDIO_START packet."
        }

        require(
            packet.payload.size ==
                    23
        ) {
            "Invalid AUDIO_START payload."
        }

        val buffer =
            ByteBuffer.wrap(
                packet.payload
            )
                .order(
                    ByteOrder.BIG_ENDIAN
                )

        val codec =
            AudioCodec.fromCode(
                buffer.get()
            )
                ?: throw IllegalArgumentException(
                    "Unknown audio codec."
                )

        val sampleRate =
            buffer.int

        val channels =
            buffer.short
                .toInt()
                .and(
                    0xFFFF
                )

        val sessionId =
            buffer.long

        val totalBytes =
            buffer.long

        return AudioStartInfo(
            codec =
                codec,
            sampleRate =
                sampleRate,
            channels =
                channels,
            sessionId =
                sessionId,
            totalBytes =
                totalBytes
        )
    }

    fun audioChunkPacket(
        sequence: Long,
        sessionId: Long,
        chunkIndex: Long,
        audioBytes: ByteArray
    ): Packet {

        require(
            sessionId >= 0L
        ) {
            "Session ID cannot be negative."
        }

        require(
            chunkIndex >= 0L
        ) {
            "Chunk index cannot be negative."
        }

        val header =
            ByteBuffer.allocate(
                16
            )
                .order(
                    ByteOrder.BIG_ENDIAN
                )

        header.putLong(
            sessionId
        )

        header.putLong(
            chunkIndex
        )

        val output =
            ByteArrayOutputStream(
                16 +
                        audioBytes.size
            )

        output.write(
            header.array()
        )

        output.write(
            audioBytes
        )

        return Packet(
            messageType =
                MessageType.AUDIO_CHUNK,
            sequence =
                sequence,
            payload =
                output.toByteArray()
        )
    }

    fun decodeAudioChunk(
        packet: Packet
    ): AudioChunkInfo {

        require(
            packet.messageType ==
                    MessageType.AUDIO_CHUNK
        ) {
            "Packet is not an AUDIO_CHUNK packet."
        }

        require(
            packet.payload.size >=
                    16
        ) {
            "Invalid AUDIO_CHUNK payload."
        }

        val buffer =
            ByteBuffer.wrap(
                packet.payload
            )
                .order(
                    ByteOrder.BIG_ENDIAN
                )

        val sessionId =
            buffer.long

        val chunkIndex =
            buffer.long

        val audioBytes =
            ByteArray(
                packet.payload.size -
                        16
            )

        buffer.get(
            audioBytes
        )

        return AudioChunkInfo(
            sessionId =
                sessionId,
            chunkIndex =
                chunkIndex,
            audioBytes =
                audioBytes
        )
    }

    fun audioEndPacket(
        sequence: Long,
        sessionId: Long
    ): Packet {

        require(
            sessionId >= 0L
        ) {
            "Session ID cannot be negative."
        }

        val buffer =
            ByteBuffer.allocate(
                8
            )
                .order(
                    ByteOrder.BIG_ENDIAN
                )

        buffer.putLong(
            sessionId
        )

        return Packet(
            messageType =
                MessageType.AUDIO_END,
            sequence =
                sequence,
            payload =
                buffer.array()
        )
    }

    fun cameraFrameStartPacket(
        sequence: Long,
        frameId: Long,
        width: Int,
        height: Int,
        format: Int,
        totalBytes: Long
    ): Packet {

        require(
            frameId >= 0L
        ) {
            "Frame ID cannot be negative."
        }

        require(
            width >= 0
        ) {
            "Width cannot be negative."
        }

        require(
            height >= 0
        ) {
            "Height cannot be negative."
        }

        require(
            totalBytes >= 0L
        ) {
            "Total image size cannot be negative."
        }

        val buffer =
            ByteBuffer.allocate(
                8 +
                        4 +
                        4 +
                        4 +
                        8
            )
                .order(
                    ByteOrder.BIG_ENDIAN
                )

        buffer.putLong(
            frameId
        )

        buffer.putInt(
            width
        )

        buffer.putInt(
            height
        )

        buffer.putInt(
            format
        )

        buffer.putLong(
            totalBytes
        )

        return Packet(
            messageType =
                MessageType.CAMERA_FRAME_START,
            sequence =
                sequence,
            payload =
                buffer.array()
        )
    }

    fun decodeCameraFrameStart(
        packet: Packet
    ): CameraFrameStartInfo {

        require(
            packet.messageType ==
                    MessageType.CAMERA_FRAME_START
        ) {
            "Packet is not a CAMERA_FRAME_START packet."
        }

        require(
            packet.payload.size ==
                    28
        ) {
            "Invalid CAMERA_FRAME_START payload."
        }

        val buffer =
            ByteBuffer.wrap(
                packet.payload
            )
                .order(
                    ByteOrder.BIG_ENDIAN
                )

        return CameraFrameStartInfo(
            frameId =
                buffer.long,
            width =
                buffer.int,
            height =
                buffer.int,
            format =
                buffer.int,
            totalBytes =
                buffer.long
        )
    }

    fun cameraFrameChunkPacket(
        sequence: Long,
        frameId: Long,
        chunkIndex: Long,
        imageBytes: ByteArray
    ): Packet {

        require(
            frameId >= 0L
        ) {
            "Frame ID cannot be negative."
        }

        require(
            chunkIndex >= 0L
        ) {
            "Chunk index cannot be negative."
        }

        val header =
            ByteBuffer.allocate(
                16
            )
                .order(
                    ByteOrder.BIG_ENDIAN
                )

        header.putLong(
            frameId
        )

        header.putLong(
            chunkIndex
        )

        val output =
            ByteArrayOutputStream(
                16 +
                        imageBytes.size
            )

        output.write(
            header.array()
        )

        output.write(
            imageBytes
        )

        return Packet(
            messageType =
                MessageType.CAMERA_FRAME_CHUNK,
            sequence =
                sequence,
            payload =
                output.toByteArray()
        )
    }

    fun decodeCameraFrameChunk(
        packet: Packet
    ): CameraFrameChunkInfo {

        require(
            packet.messageType ==
                    MessageType.CAMERA_FRAME_CHUNK
        ) {
            "Packet is not a CAMERA_FRAME_CHUNK packet."
        }

        require(
            packet.payload.size >=
                    16
        ) {
            "Invalid CAMERA_FRAME_CHUNK payload."
        }

        val buffer =
            ByteBuffer.wrap(
                packet.payload
            )
                .order(
                    ByteOrder.BIG_ENDIAN
                )

        val frameId =
            buffer.long

        val chunkIndex =
            buffer.long

        val imageBytes =
            ByteArray(
                packet.payload.size -
                        16
            )

        buffer.get(
            imageBytes
        )

        return CameraFrameChunkInfo(
            frameId =
                frameId,
            chunkIndex =
                chunkIndex,
            imageBytes =
                imageBytes
        )
    }

    fun cameraFrameEndPacket(
        sequence: Long,
        frameId: Long
    ): Packet {

        require(
            frameId >= 0L
        ) {
            "Frame ID cannot be negative."
        }

        val buffer =
            ByteBuffer.allocate(
                8
            )
                .order(
                    ByteOrder.BIG_ENDIAN
                )

        buffer.putLong(
            frameId
        )

        return Packet(
            messageType =
                MessageType.CAMERA_FRAME_END,
            sequence =
                sequence,
            payload =
                buffer.array()
        )
    }

    fun distanceSensorPacket(
        sequence: Long,
        distanceCentimeters: Float
    ): Packet {

        val buffer =
            ByteBuffer.allocate(
                4
            )
                .order(
                    ByteOrder.BIG_ENDIAN
                )

        buffer.putFloat(
            distanceCentimeters
        )

        return Packet(
            messageType =
                MessageType.SENSOR_DISTANCE,
            sequence =
                sequence,
            payload =
                buffer.array()
        )
    }

    fun decodeDistanceSensor(
        packet: Packet
    ): DistanceSensorInfo {

        require(
            packet.messageType ==
                    MessageType.SENSOR_DISTANCE
        ) {
            "Packet is not a SENSOR_DISTANCE packet."
        }

        require(
            packet.payload.size ==
                    4
        ) {
            "Invalid SENSOR_DISTANCE payload."
        }

        val buffer =
            ByteBuffer.wrap(
                packet.payload
            )
                .order(
                    ByteOrder.BIG_ENDIAN
                )

        return DistanceSensorInfo(
            distanceCentimeters =
                buffer.float
        )
    }

    fun ackPacket(
        sequence: Long,
        acknowledgedSequence: Long
    ): Packet {

        require(
            acknowledgedSequence >= 0L
        ) {
            "Acknowledged sequence cannot be negative."
        }

        val buffer =
            ByteBuffer.allocate(
                8
            )
                .order(
                    ByteOrder.BIG_ENDIAN
                )

        buffer.putLong(
            acknowledgedSequence
        )

        return Packet(
            messageType =
                MessageType.ACK,
            sequence =
                sequence,
            payload =
                buffer.array()
        )
    }

    fun decodeAck(
        packet: Packet
    ): AckInfo {

        require(
            packet.messageType ==
                    MessageType.ACK
        ) {
            "Packet is not an ACK packet."
        }

        require(
            packet.payload.size ==
                    8
        ) {
            "Invalid ACK payload."
        }

        val buffer =
            ByteBuffer.wrap(
                packet.payload
            )
                .order(
                    ByteOrder.BIG_ENDIAN
                )

        return AckInfo(
            acknowledgedSequence =
                buffer.long
        )
    }

    fun errorPacket(
        sequence: Long,
        errorCode: Int,
        message: String
    ): Packet {

        require(
            errorCode >= 0
        ) {
            "Error code cannot be negative."
        }

        val messageBytes =
            message.toByteArray(
                Charsets.UTF_8
            )

        val buffer =
            ByteBuffer.allocate(
                4 +
                        messageBytes.size
            )
                .order(
                    ByteOrder.BIG_ENDIAN
                )

        buffer.putInt(
            errorCode
        )

        buffer.put(
            messageBytes
        )

        return Packet(
            messageType =
                MessageType.ERROR,
            sequence =
                sequence,
            payload =
                buffer.array()
        )
    }

    fun decodeError(
        packet: Packet
    ): ErrorInfo {

        require(
            packet.messageType ==
                    MessageType.ERROR
        ) {
            "Packet is not an ERROR packet."
        }

        require(
            packet.payload.size >=
                    4
        ) {
            "Invalid ERROR payload."
        }

        val buffer =
            ByteBuffer.wrap(
                packet.payload
            )
                .order(
                    ByteOrder.BIG_ENDIAN
                )

        val errorCode =
            buffer.int

        val messageBytes =
            ByteArray(
                packet.payload.size -
                        4
            )

        buffer.get(
            messageBytes
        )

        return ErrorInfo(
            errorCode =
                errorCode,
            message =
                messageBytes.toString(
                    Charsets.UTF_8
                )
        )
    }

    fun heartbeatPacket(
        sequence: Long
    ): Packet {

        return Packet(
            messageType =
                MessageType.HEARTBEAT,
            sequence =
                sequence
        )
    }

    fun heartbeatAckPacket(
        sequence: Long,
        acknowledgedSequence: Long
    ): Packet {

        return Packet(
            messageType =
                MessageType.HEARTBEAT_ACK,
            sequence =
                sequence,
            payload =
                ByteBuffer.allocate(
                    8
                )
                    .order(
                        ByteOrder.BIG_ENDIAN
                    )
                    .putLong(
                        acknowledgedSequence
                    )
                    .array()
        )
    }

    fun helloPacket(
        sequence: Long,
        deviceName: String
    ): Packet {

        return Packet(
            messageType =
                MessageType.HELLO,
            sequence =
                sequence,
            payload =
                deviceName.toByteArray(
                    Charsets.UTF_8
                )
        )
    }

    fun helloAckPacket(
        sequence: Long,
        deviceName: String
    ): Packet {

        return Packet(
            messageType =
                MessageType.HELLO_ACK,
            sequence =
                sequence,
            payload =
                deviceName.toByteArray(
                    Charsets.UTF_8
                )
        )
    }

    /**
     * BLE streaming packet parser.
     *
     * BLE notifications/indications can split one logical protocol
     * packet into multiple chunks, or combine multiple packets into
     * one callback. PacketFramer reconstructs complete packets.
     */
    class PacketFramer {

        private var buffer =
            ByteArray(0)

        fun append(
            bytes: ByteArray
        ): List<Packet> {

            if (bytes.isEmpty()) {
                return emptyList()
            }

            buffer =
                buffer + bytes

            val packets =
                mutableListOf<Packet>()

            while (true) {

                if (
                    buffer.size <
                    MAGIC.length
                ) {
                    break
                }

                val magicIndex =
                    findMagic(
                        buffer
                    )

                if (magicIndex < 0) {

                    buffer =
                        if (
                            buffer.size >=
                            MAGIC.length - 1
                        ) {
                            buffer.takeLast(
                                MAGIC.length - 1
                            )
                                .toByteArray()
                        } else {
                            buffer
                        }

                    break
                }

                if (magicIndex > 0) {

                    buffer =
                        buffer.copyOfRange(
                            magicIndex,
                            buffer.size
                        )
                }

                if (
                    buffer.size <
                    HEADER_SIZE
                ) {
                    break
                }

                val payloadLength =
                    ByteBuffer.wrap(
                        buffer,
                        16,
                        4
                    )
                        .order(
                            ByteOrder.BIG_ENDIAN
                        )
                        .int

                if (
                    payloadLength < 0 ||
                    payloadLength >
                    MAX_PAYLOAD_SIZE
                ) {

                    buffer =
                        buffer.copyOfRange(
                            1,
                            buffer.size
                        )

                    continue
                }

                val packetSize =
                    HEADER_SIZE +
                            payloadLength +
                            CRC_SIZE

                if (
                    buffer.size <
                    packetSize
                ) {
                    break
                }

                val packetBytes =
                    buffer.copyOfRange(
                        0,
                        packetSize
                    )

                try {

                    packets +=
                        decodePacket(
                            packetBytes
                        )

                    buffer =
                        buffer.copyOfRange(
                            packetSize,
                            buffer.size
                        )

                } catch (_: Exception) {

                    buffer =
                        buffer.copyOfRange(
                            1,
                            buffer.size
                        )
                }
            }

            return packets
        }

        fun clear() {

            buffer =
                ByteArray(0)
        }

        private fun findMagic(
            source: ByteArray
        ): Int {

            val magicBytes =
                MAGIC.toByteArray(
                    Charsets.US_ASCII
                )

            if (
                source.size <
                magicBytes.size
            ) {
                return -1
            }

            outer@ for (
            index in
            0..source.size -
                    magicBytes.size
            ) {

                for (
                offset in
                magicBytes.indices
                ) {

                    if (
                        source[
                            index + offset
                        ] !=
                        magicBytes[
                            offset
                        ]
                    ) {
                        continue@outer
                    }
                }

                return index
            }

            return -1
        }
    }

    private fun crc16Ccitt(
        data: ByteArray
    ): Short {

        var crc =
            0xFFFF

        for (
        value in data
        ) {

            crc =
                crc xor
                        (
                                value.toInt()
                                    .and(0xFF)
                                    .shl(8)
                                )

            repeat(
                8
            ) {

                crc =
                    if (
                        (crc and 0x8000) !=
                        0
                    ) {
                        (
                                crc shl 1
                                ) xor
                                0x1021
                    } else {
                        crc shl 1
                    }

                crc =
                    crc and
                            0xFFFF
            }
        }

        return crc.toShort()
    }

    const val FLAG_TEXT_UTF8 = 0x0001

    const val VOICE_TRIGGER_MANUAL = 0x00000001

    const val VOICE_TRIGGER_BUTTON = 0x00000002

    const val VOICE_TRIGGER_WAKE = 0x00000003

    const val IMAGE_FORMAT_JPEG = 0x00000001

    const val IMAGE_FORMAT_RGB565 = 0x00000002

    const val IMAGE_FORMAT_GRAYSCALE = 0x00000003

    const val ERROR_UNKNOWN_COMMAND = 0x0001

    const val ERROR_UNSUPPORTED_VERSION = 0x0002

    const val ERROR_INVALID_PACKET = 0x0003

    const val ERROR_CRC_FAILED = 0x0004

    const val ERROR_BUSY = 0x0005

    const val ERROR_AUDIO_FAILED = 0x0006

    const val ERROR_CAMERA_FAILED = 0x0007

    const val ERROR_SENSOR_FAILED = 0x0008
}