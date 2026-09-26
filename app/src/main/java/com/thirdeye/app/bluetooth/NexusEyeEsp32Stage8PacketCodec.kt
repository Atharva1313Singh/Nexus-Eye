package com.thirdeye.app.bluetooth

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Stage 8.3 packet codec for the documented NEXUS EYE ESP32-S3 transport.
 *
 * Documented wire layout:
 *
 *   4 bytes  MAGIC = "NEX1"
 *   1 byte   protocol version
 *   1 byte   message type
 *   2 bytes  flags
 *   8 bytes  sequence number
 *   4 bytes  payload length
 *   N bytes  payload
 *   2 bytes  CRC16-CCITT
 *
 * The final firmware-side packet type byte assignments remain owned by the
 * existing NEXUS EYE protocol definition. This codec therefore uses the raw
 * type byte instead of redefining those values here.
 */
data class NexusEyeEsp32Stage8Packet(
    val protocolVersion: Int,
    val messageType: Int,
    val flags: Int,
    val sequenceNumber: Long,
    val payload: ByteArray
) {
    fun copyWithPayload(
        newPayload: ByteArray
    ): NexusEyeEsp32Stage8Packet {
        return copy(
            payload = newPayload
        )
    }
}

data class NexusEyeEsp32Stage8DecodeResult(
    val packets: List<NexusEyeEsp32Stage8Packet>,
    val remainingBytes: ByteArray,
    val errors: List<String>
)

class NexusEyeEsp32Stage8PacketCodec(
    private val byteOrder: ByteOrder =
        ByteOrder.BIG_ENDIAN
) {

    fun encode(
        packet: NexusEyeEsp32Stage8Packet
    ): ByteArray {

        validatePacket(
            packet
        )

        val payloadLength =
            packet.payload.size

        val header =
            ByteBuffer.allocate(
                HEADER_SIZE
            )
                .order(
                    byteOrder
                )

        header.put(
            MAGIC
        )

        header.put(
            packet.protocolVersion.toByte()
        )

        header.put(
            packet.messageType.toByte()
        )

        header.putShort(
            packet.flags.toShort()
        )

        header.putLong(
            packet.sequenceNumber
        )

        header.putInt(
            payloadLength
        )

        val headerBytes =
            header.array()

        val crcInput =
            ByteArrayOutputStream(
                HEADER_SIZE +
                        payloadLength
            )
                .apply {
                    write(
                        headerBytes
                    )
                    write(
                        packet.payload
                    )
                }
                .toByteArray()

        val crc =
            crc16Ccitt(
                crcInput
            )

        val output =
            ByteBuffer.allocate(
                HEADER_SIZE +
                        payloadLength +
                        CRC_SIZE
            )
                .order(
                    byteOrder
                )

        output.put(
            headerBytes
        )

        output.put(
            packet.payload
        )

        output.putShort(
            crc.toShort()
        )

        return output.array()
    }

    fun decodeSingle(
        frame: ByteArray
    ): NexusEyeEsp32Stage8Packet {

        if (
            frame.size <
            MIN_PACKET_SIZE
        ) {
            throw IllegalArgumentException(
                "NEXUS EYE packet is shorter than the minimum packet size."
            )
        }

        validateMagic(
            frame
        )

        val packetLength =
            readPayloadLength(
                frame
            )

        val expectedLength =
            HEADER_SIZE +
                    packetLength +
                    CRC_SIZE

        if (
            frame.size !=
            expectedLength
        ) {
            throw IllegalArgumentException(
                "NEXUS EYE packet length mismatch. Expected $expectedLength bytes, received ${frame.size}."
            )
        }

        val expectedCrc =
            readStoredCrc(
                frame
            )

        val actualCrc =
            crc16Ccitt(
                frame,
                0,
                frame.size - CRC_SIZE
            )

        if (
            expectedCrc !=
            actualCrc
        ) {
            throw IllegalArgumentException(
                "NEXUS EYE packet CRC check failed."
            )
        }

        return parsePacket(
            frame
        )
    }

    fun decodeStream(
        incoming: ByteArray,
        previousRemainder: ByteArray = ByteArray(0)
    ): NexusEyeEsp32Stage8DecodeResult {

        val merged =
            ByteArray(
                previousRemainder.size +
                        incoming.size
            )

        System.arraycopy(
            previousRemainder,
            0,
            merged,
            0,
            previousRemainder.size
        )

        System.arraycopy(
            incoming,
            0,
            merged,
            previousRemainder.size,
            incoming.size
        )

        val packets =
            mutableListOf<NexusEyeEsp32Stage8Packet>()

        val errors =
            mutableListOf<String>()

        var cursor =
            0

        while (
            cursor <
            merged.size
        ) {

            val magicIndex =
                findMagic(
                    merged,
                    cursor
                )

            if (
                magicIndex < 0
            ) {
                val tail =
                    keepPossibleMagicTail(
                        merged
                    )

                if (
                    tail.isNotEmpty()
                ) {
                    return NexusEyeEsp32Stage8DecodeResult(
                        packets =
                            packets,
                        remainingBytes =
                            tail,
                        errors =
                            errors
                    )
                }

                return NexusEyeEsp32Stage8DecodeResult(
                    packets =
                        packets,
                    remainingBytes =
                        ByteArray(0),
                    errors =
                        errors
                )
            }

            if (
                magicIndex >
                cursor
            ) {
                errors +=
                    "Skipped ${magicIndex - cursor} non-protocol byte(s) before NEX1 magic."
            }

            cursor =
                magicIndex

            if (
                merged.size - cursor <
                HEADER_SIZE
            ) {
                return NexusEyeEsp32Stage8DecodeResult(
                    packets =
                        packets,
                    remainingBytes =
                        merged.copyOfRange(
                            cursor,
                            merged.size
                        ),
                    errors =
                        errors
                )
            }

            val payloadLength =
                try {
                    readPayloadLength(
                        merged,
                        cursor
                    )
                } catch (
                    exception: Exception
                ) {
                    errors +=
                        exception.message
                            ?: "Could not read NEXUS EYE payload length."

                    cursor +=
                        MAGIC.size

                    continue
                }

            if (
                payloadLength >
                MAX_PAYLOAD_SIZE
            ) {
                errors +=
                    "Rejected NEXUS EYE packet with payload length $payloadLength."

                cursor +=
                    MAGIC.size

                continue
            }

            val totalPacketLength =
                HEADER_SIZE +
                        payloadLength +
                        CRC_SIZE

            if (
                merged.size - cursor <
                totalPacketLength
            ) {
                return NexusEyeEsp32Stage8DecodeResult(
                    packets =
                        packets,
                    remainingBytes =
                        merged.copyOfRange(
                            cursor,
                            merged.size
                        ),
                    errors =
                        errors
                )
            }

            val frame =
                merged.copyOfRange(
                    cursor,
                    cursor +
                            totalPacketLength
                )

            try {
                packets +=
                    decodeSingle(
                        frame
                    )
            } catch (
                exception: Exception
            ) {
                errors +=
                    exception.message
                        ?: "Rejected an invalid NEXUS EYE packet."

                cursor +=
                    MAGIC.size

                continue
            }

            cursor +=
                totalPacketLength
        }

        return NexusEyeEsp32Stage8DecodeResult(
            packets =
                packets,
            remainingBytes =
                ByteArray(0),
            errors =
                errors
        )
    }

    private fun parsePacket(
        frame: ByteArray,
        offset: Int = 0
    ): NexusEyeEsp32Stage8Packet {

        val buffer =
            ByteBuffer.wrap(
                frame,
                offset,
                frame.size -
                        offset -
                        CRC_SIZE
            )
                .order(
                    byteOrder
                )

        val magic =
            ByteArray(
                MAGIC.size
            )

        buffer.get(
            magic
        )

        if (
            !magic.contentEquals(
                MAGIC
            )
        ) {
            throw IllegalArgumentException(
                "Invalid NEX1 magic."
            )
        }

        val protocolVersion =
            buffer.get()
                .toInt()
                .and(
                    0xFF
                )

        val messageType =
            buffer.get()
                .toInt()
                .and(
                    0xFF
                )

        val flags =
            buffer.short
                .toInt()
                .and(
                    0xFFFF
                )

        val sequenceNumber =
            buffer.long

        val payloadLength =
            buffer.int

        if (
            payloadLength < 0 ||
            payloadLength >
            MAX_PAYLOAD_SIZE
        ) {
            throw IllegalArgumentException(
                "Invalid NEXUS EYE payload length: $payloadLength."
            )
        }

        val payload =
            ByteArray(
                payloadLength
            )

        buffer.get(
            payload
        )

        return NexusEyeEsp32Stage8Packet(
            protocolVersion =
                protocolVersion,
            messageType =
                messageType,
            flags =
                flags,
            sequenceNumber =
                sequenceNumber,
            payload =
                payload
        )
    }

    private fun validatePacket(
        packet: NexusEyeEsp32Stage8Packet
    ) {

        require(
            packet.protocolVersion in
                    0..0xFF
        ) {
            "Protocol version must fit in one byte."
        }

        require(
            packet.messageType in
                    0..0xFF
        ) {
            "Message type must fit in one byte."
        }

        require(
            packet.flags in
                    0..0xFFFF
        ) {
            "Flags must fit in two bytes."
        }

        require(
            packet.payload.size <=
                    MAX_PAYLOAD_SIZE
        ) {
            "Payload exceeds the NEXUS EYE maximum payload size."
        }
    }

    private fun validateMagic(
        frame: ByteArray,
        offset: Int = 0
    ) {

        if (
            frame.size - offset <
            MAGIC.size
        ) {
            throw IllegalArgumentException(
                "Frame is too short for NEX1 magic."
            )
        }

        for (
        index in MAGIC.indices
        ) {
            if (
                frame[offset + index] !=
                MAGIC[index]
            ) {
                throw IllegalArgumentException(
                    "Invalid NEX1 magic."
                )
            }
        }
    }

    private fun readPayloadLength(
        frame: ByteArray,
        offset: Int = 0
    ): Int {

        if (
            frame.size -
            offset <
            HEADER_SIZE
        ) {
            throw IllegalArgumentException(
                "Frame is too short to contain the NEXUS EYE packet header."
            )
        }

        return ByteBuffer.wrap(
            frame,
            offset + PAYLOAD_LENGTH_OFFSET,
            PAYLOAD_LENGTH_SIZE
        )
            .order(
                byteOrder
            )
            .int
    }

    private fun readStoredCrc(
        frame: ByteArray
    ): Int {

        return ByteBuffer.wrap(
            frame,
            frame.size - CRC_SIZE,
            CRC_SIZE
        )
            .order(
                byteOrder
            )
            .short
            .toInt()
            .and(
                0xFFFF
            )
    }

    private fun findMagic(
        bytes: ByteArray,
        startIndex: Int
    ): Int {

        val lastStart =
            bytes.size -
                    MAGIC.size

        if (
            lastStart <
            startIndex
        ) {
            return -1
        }

        for (
        index in startIndex..lastStart
        ) {
            var matches =
                true

            for (
            magicIndex in MAGIC.indices
            ) {
                if (
                    bytes[
                        index +
                                magicIndex
                    ] !=
                    MAGIC[magicIndex]
                ) {
                    matches =
                        false
                    break
                }
            }

            if (
                matches
            ) {
                return index
            }
        }

        return -1
    }

    private fun keepPossibleMagicTail(
        bytes: ByteArray
    ): ByteArray {

        val maxTailLength =
            MAGIC.size - 1

        val tailLength =
            minOf(
                maxTailLength,
                bytes.size
            )

        if (
            tailLength <= 0
        ) {
            return ByteArray(0)
        }

        val start =
            bytes.size -
                    tailLength

        for (
        candidateLength in
        tailLength downTo 1
        ) {
            val candidateStart =
                bytes.size -
                        candidateLength

            var matches =
                true

            for (
            index in 0 until candidateLength
            ) {
                if (
                    bytes[
                        candidateStart +
                                index
                    ] !=
                    MAGIC[index]
                ) {
                    matches =
                        false
                    break
                }
            }

            if (
                matches
            ) {
                return bytes.copyOfRange(
                    start,
                    bytes.size
                )
            }
        }

        return ByteArray(0)
    }

    companion object {

        val MAGIC: ByteArray =
            byteArrayOf(
                0x4E,
                0x45,
                0x58,
                0x31
            )

        const val PROTOCOL_VERSION =
            1

        const val HEADER_SIZE =
            20

        const val CRC_SIZE =
            2

        const val MAX_PAYLOAD_SIZE =
            1_048_576

        private const val PAYLOAD_LENGTH_OFFSET =
            16

        private const val PAYLOAD_LENGTH_SIZE =
            4

        private const val MIN_PACKET_SIZE =
            HEADER_SIZE +
                    CRC_SIZE

        fun crc16Ccitt(
            bytes: ByteArray
        ): Int {
            return crc16Ccitt(
                bytes,
                0,
                bytes.size
            )
        }

        fun crc16Ccitt(
            bytes: ByteArray,
            offset: Int,
            length: Int
        ): Int {

            var crc =
                0xFFFF

            val end =
                offset +
                        length

            for (
            index in offset until end
            ) {

                crc =
                    crc xor
                            (
                                    (
                                            bytes[index]
                                                .toInt()
                                                .and(0xFF)
                                            ) shl 8
                                    )

                repeat(
                    8
                ) {
                    crc =
                        if (
                            (
                                    crc and
                                            0x8000
                                    ) !=
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

            return crc
        }
    }
}

/**
 * Stateful stream parser for BLE notifications.
 *
 * BLE notification boundaries do not have to match NEXUS EYE packet
 * boundaries, so callers should pass every received byte array here.
 */
class NexusEyeEsp32Stage8PacketStreamRouter(
    private val codec:
    NexusEyeEsp32Stage8PacketCodec =
        NexusEyeEsp32Stage8PacketCodec()
) {

    private var remainder =
        ByteArray(0)

    fun push(
        bytes: ByteArray
    ): NexusEyeEsp32Stage8DecodeResult {

        val result =
            codec.decodeStream(
                incoming =
                    bytes,
                previousRemainder =
                    remainder
            )

        remainder =
            result.remainingBytes

        return result
    }

    fun reset() {
        remainder =
            ByteArray(0)
    }

    fun pendingByteCount(): Int {
        return remainder.size
    }
}
