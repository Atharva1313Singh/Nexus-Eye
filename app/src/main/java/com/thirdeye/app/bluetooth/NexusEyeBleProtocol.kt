package com.thirdeye.app.bluetooth

import java.util.UUID

object NexusEyeBleProtocol {

    /*
     * NEXUS EYE custom BLE service.
     *
     * The ESP32-S3 firmware built later will use
     * these exact UUIDs.
     */
    val SERVICE_UUID: UUID =
        UUID.fromString("12345678-1234-5678-1234-56789abcdef0")

    /*
     * Android -> ESP32
     */
    val WRITE_CHARACTERISTIC_UUID: UUID =
        UUID.fromString("12345678-1234-5678-1234-56789abcdef1")

    /*
     * ESP32 -> Android
     */
    val NOTIFY_CHARACTERISTIC_UUID: UUID =
        UUID.fromString("12345678-1234-5678-1234-56789abcdef2")

    /*
     * Bluetooth Client Characteristic Configuration Descriptor.
     */
    val CCCD_UUID: UUID =
        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val DEVICE_NAME_PREFIX = "NEXUS"

    const val COMMAND_SEPARATOR = "\n"

    fun command(name: String, value: String? = null): String {
        return if (value == null) {
            "$name$COMMAND_SEPARATOR"
        } else {
            "$name:$value$COMMAND_SEPARATOR"
        }
    }

    object Commands {

        const val PING = "PING"

        const val PONG = "PONG"

        const val STATUS = "STATUS"

        const val HELLO = "HELLO"

        const val ACK = "ACK"

        const val ERROR = "ERROR"

        const val QUESTION = "QUESTION"

        const val ANSWER = "ANSWER"
    }
}