package com.thirdeye.app.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper

import androidx.core.content.ContextCompat

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.ArrayDeque
import java.util.UUID
import java.util.zip.CRC32

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NexusEyeBleDevice(
    val device: BluetoothDevice,
    val name: String,
    val address: String,
    val rssi: Int
)

enum class NexusEyeConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    READY,
    DISCONNECTING,
    ERROR
}

private data class PendingBleWrite(
    val bytes: ByteArray,
    val isAudio: Boolean
)

class NexusEyeBleManager(
    private val context: Context
) {

    private val appContext =
        context.applicationContext

    private val bluetoothManager =
        appContext.getSystemService(
            Context.BLUETOOTH_SERVICE
        ) as BluetoothManager

    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager.adapter

    private var scanner: BluetoothLeScanner? = null

    private var bluetoothGatt: BluetoothGatt? = null

    private var writeCharacteristic:
            BluetoothGattCharacteristic? = null

    private var notifyCharacteristic:
            BluetoothGattCharacteristic? = null

    private var mtuPayloadSize =
        DEFAULT_MTU_PAYLOAD

    private val handler =
        Handler(
            Looper.getMainLooper()
        )

    private val pendingWrites =
        ArrayDeque<PendingBleWrite>()

    private var writeInProgress =
        false

    private var currentWriteIsAudio =
        false

    private var audioFramesRemaining =
        0

    private var audioTransferInProgress =
        false

    private var audioCompletion:
            ((Boolean, String?) -> Unit)? =
        null

    private val _devices =
        MutableStateFlow<List<NexusEyeBleDevice>>(
            emptyList()
        )

    val devices:
            StateFlow<List<NexusEyeBleDevice>> =
        _devices.asStateFlow()

    private val _connectionState =
        MutableStateFlow(
            NexusEyeConnectionState.DISCONNECTED
        )

    val connectionState:
            StateFlow<NexusEyeConnectionState> =
        _connectionState.asStateFlow()

    private val _lastReceivedMessage =
        MutableStateFlow("")

    val lastReceivedMessage:
            StateFlow<String> =
        _lastReceivedMessage.asStateFlow()

    private val _errorMessage =
        MutableStateFlow<String?>(null)

    val errorMessage:
            StateFlow<String?> =
        _errorMessage.asStateFlow()

    private val discoveredDevices =
        LinkedHashMap<String, NexusEyeBleDevice>()

    /*
     * Compatibility property for NexusEyeAssistantAudioRouter.
     *
     * This only means the BLE path is currently usable.
     * It does NOT claim that an ESP32-S3 is physically connected.
     */
    val isWearableAudioAvailable: Boolean
        get() =
            writeCharacteristic != null &&
                    _connectionState.value ==
                    NexusEyeConnectionState.READY

    private val scanStopRunnable =
        Runnable {
            stopScan()
        }

    private val scanCallback =
        object : ScanCallback() {

            override fun onScanResult(
                callbackType: Int,
                result: ScanResult
            ) {

                handleScanResult(
                    result
                )
            }

            override fun onBatchScanResults(
                results: MutableList<ScanResult>
            ) {

                results.forEach(
                    ::handleScanResult
                )
            }

            override fun onScanFailed(
                errorCode: Int
            ) {

                stopScanInternal()

                _connectionState.value =
                    NexusEyeConnectionState.ERROR

                _errorMessage.value =
                    "BLE scan failed. Error code: $errorCode"
            }
        }

    private val gattCallback =
        object : BluetoothGattCallback() {

            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int
            ) {

                when (newState) {

                    BluetoothProfile.STATE_CONNECTED -> {

                        _connectionState.value =
                            NexusEyeConnectionState.CONNECTED

                        handler.post {
                            requestMtu(
                                gatt
                            )
                        }
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {

                        writeCharacteristic =
                            null

                        notifyCharacteristic =
                            null

                        writeInProgress =
                            false

                        currentWriteIsAudio =
                            false

                        pendingWrites.clear()

                        resetAudioTransferState(
                            notifyFailure = false
                        )

                        if (
                            _connectionState.value !=
                            NexusEyeConnectionState.DISCONNECTING
                        ) {

                            _connectionState.value =
                                NexusEyeConnectionState.DISCONNECTED
                        }

                        safeCloseGatt()
                    }
                }

                if (
                    status !=
                    BluetoothGatt.GATT_SUCCESS &&
                    newState ==
                    BluetoothProfile.STATE_DISCONNECTED
                ) {

                    _errorMessage.value =
                        "BLE connection closed. Status: $status"
                }
            }

            override fun onMtuChanged(
                gatt: BluetoothGatt,
                mtu: Int,
                status: Int
            ) {

                if (
                    status ==
                    BluetoothGatt.GATT_SUCCESS &&
                    mtu > 3
                ) {

                    mtuPayloadSize =
                        mtu - 3

                } else {

                    mtuPayloadSize =
                        DEFAULT_MTU_PAYLOAD
                }

                gatt.discoverServices()
            }

            override fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int
            ) {

                if (
                    status !=
                    BluetoothGatt.GATT_SUCCESS
                ) {

                    _connectionState.value =
                        NexusEyeConnectionState.ERROR

                    _errorMessage.value =
                        "BLE service discovery failed."

                    return
                }

                val service =
                    gatt.getService(
                        NexusEyeBleProtocol.SERVICE_UUID
                    )

                if (
                    service == null
                ) {

                    _connectionState.value =
                        NexusEyeConnectionState.ERROR

                    _errorMessage.value =
                        "NEXUS EYE BLE service was not found."

                    return
                }

                writeCharacteristic =
                    service.getCharacteristic(
                        NexusEyeBleProtocol
                            .WRITE_CHARACTERISTIC_UUID
                    )

                notifyCharacteristic =
                    service.getCharacteristic(
                        NexusEyeBleProtocol
                            .NOTIFY_CHARACTERISTIC_UUID
                    )

                if (
                    writeCharacteristic == null
                ) {

                    _connectionState.value =
                        NexusEyeConnectionState.ERROR

                    _errorMessage.value =
                        "NEXUS EYE write characteristic was not found."

                    return
                }

                if (
                    notifyCharacteristic != null
                ) {

                    enableNotifications(
                        gatt,
                        notifyCharacteristic!!
                    )

                } else {

                    _connectionState.value =
                        NexusEyeConnectionState.READY
                }
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int
            ) {

                if (
                    descriptor.uuid ==
                    NexusEyeBleProtocol.CCCD_UUID
                ) {

                    if (
                        status ==
                        BluetoothGatt.GATT_SUCCESS
                    ) {

                        _connectionState.value =
                            NexusEyeConnectionState.READY

                        sendRaw(
                            NexusEyeBleProtocol.command(
                                NexusEyeBleProtocol
                                    .Commands
                                    .HELLO
                            )
                        )

                    } else {

                        _connectionState.value =
                            NexusEyeConnectionState.ERROR

                        _errorMessage.value =
                            "Could not enable BLE notifications."
                    }
                }
            }

            @Suppress("DEPRECATION")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic:
                BluetoothGattCharacteristic
            ) {

                if (
                    characteristic.uuid ==
                    NexusEyeBleProtocol
                        .NOTIFY_CHARACTERISTIC_UUID
                ) {

                    val message =
                        characteristic.value
                            ?.toString(
                                Charsets.UTF_8
                            )
                            ?: ""

                    if (
                        message.isNotEmpty()
                    ) {

                        _lastReceivedMessage.value =
                            message
                    }
                }
            }

            @Suppress("DEPRECATION")
            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic:
                BluetoothGattCharacteristic,
                status: Int
            ) {

                val completedAudioFrame =
                    currentWriteIsAudio

                writeInProgress =
                    false

                currentWriteIsAudio =
                    false

                if (
                    status !=
                    BluetoothGatt.GATT_SUCCESS
                ) {

                    val error =
                        if (
                            completedAudioFrame
                        ) {

                            "BLE audio packet write failed. Status: $status"

                        } else {

                            "BLE message write failed. Status: $status"
                        }

                    _errorMessage.value =
                        error

                    if (
                        completedAudioFrame
                    ) {

                        finishAudioTransfer(
                            success = false,
                            error = error
                        )
                    }

                    pendingWrites.clear()

                    return
                }

                if (
                    completedAudioFrame &&
                    audioFramesRemaining > 0
                ) {

                    audioFramesRemaining--
                }

                if (
                    completedAudioFrame &&
                    audioFramesRemaining == 0 &&
                    audioTransferInProgress
                ) {

                    finishAudioTransfer(
                        success = true,
                        error = null
                    )
                }

                writeNextChunk()
            }
        }

    fun hasBluetooth(): Boolean {
        return bluetoothAdapter != null
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    fun isConnected(): Boolean {
        return _connectionState.value ==
                NexusEyeConnectionState.READY ||
                _connectionState.value ==
                NexusEyeConnectionState.CONNECTED
    }

    fun hasRequiredPermissions(): Boolean {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.S
        ) {

            return ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.BLUETOOTH_SCAN
            ) ==
                    PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        appContext,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) ==
                    PackageManager.PERMISSION_GRANTED
        }

        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.BLUETOOTH
        ) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.BLUETOOTH_ADMIN
                ) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) ==
                PackageManager.PERMISSION_GRANTED
    }

    fun startScan() {

        if (
            !hasRequiredPermissions()
        ) {

            _errorMessage.value =
                "Bluetooth permissions are required."

            return
        }

        val adapter =
            bluetoothAdapter

        if (
            adapter == null
        ) {

            _errorMessage.value =
                "Bluetooth is not available on this phone."

            _connectionState.value =
                NexusEyeConnectionState.ERROR

            return
        }

        if (
            !adapter.isEnabled
        ) {

            _errorMessage.value =
                "Bluetooth is turned off."

            return
        }

        stopScanInternal()

        discoveredDevices.clear()

        _devices.value =
            emptyList()

        scanner =
            adapter.bluetoothLeScanner

        if (
            scanner == null
        ) {

            _errorMessage.value =
                "BLE scanner is unavailable."

            _connectionState.value =
                NexusEyeConnectionState.ERROR

            return
        }

        _errorMessage.value =
            null

        _connectionState.value =
            NexusEyeConnectionState.SCANNING

        val settings =
            ScanSettings.Builder()
                .setScanMode(
                    ScanSettings.SCAN_MODE_LOW_LATENCY
                )
                .build()

        try {

            scanner?.startScan(
                null,
                settings,
                scanCallback
            )

            handler.postDelayed(
                scanStopRunnable,
                SCAN_DURATION_MS
            )

        } catch (
            securityException:
            SecurityException
        ) {

            _connectionState.value =
                NexusEyeConnectionState.ERROR

            _errorMessage.value =
                "Bluetooth permission was not granted."
        }
    }

    fun stopScan() {

        stopScanInternal()

        if (
            _connectionState.value ==
            NexusEyeConnectionState.SCANNING
        ) {

            _connectionState.value =
                NexusEyeConnectionState.DISCONNECTED
        }
    }

    private fun stopScanInternal() {

        handler.removeCallbacks(
            scanStopRunnable
        )

        try {

            scanner?.stopScan(
                scanCallback
            )

        } catch (
            _: SecurityException
        ) {
        }

        scanner =
            null
    }

    private fun handleScanResult(
        result: ScanResult
    ) {

        val device =
            result.device

        val name =
            try {
                device.name
            } catch (
                _: SecurityException
            ) {
                null
            }
                ?: result.scanRecord
                    ?.deviceName
                ?: "Unknown BLE Device"

        val item =
            NexusEyeBleDevice(
                device = device,
                name = name,
                address = device.address,
                rssi = result.rssi
            )

        discoveredDevices[
            item.address
        ] =
            item

        _devices.value =
            discoveredDevices.values
                .sortedByDescending {
                    it.rssi
                }
    }

    fun connect(
        item: NexusEyeBleDevice
    ) {

        if (
            !hasRequiredPermissions()
        ) {

            _errorMessage.value =
                "Bluetooth permissions are required."

            return
        }

        stopScanInternal()

        safeCloseGatt()

        writeCharacteristic =
            null

        notifyCharacteristic =
            null

        pendingWrites.clear()

        writeInProgress =
            false

        currentWriteIsAudio =
            false

        mtuPayloadSize =
            DEFAULT_MTU_PAYLOAD

        resetAudioTransferState(
            notifyFailure = false
        )

        _errorMessage.value =
            null

        _connectionState.value =
            NexusEyeConnectionState.CONNECTING

        try {

            bluetoothGatt =
                item.device.connectGatt(
                    appContext,
                    false,
                    gattCallback,
                    BluetoothDevice.TRANSPORT_LE
                )

        } catch (
            securityException:
            SecurityException
        ) {

            _connectionState.value =
                NexusEyeConnectionState.ERROR

            _errorMessage.value =
                "Bluetooth connection permission was not granted."
        }
    }

    fun disconnect() {

        resetAudioTransferState(
            notifyFailure = true,
            error =
                "BLE audio transfer was cancelled because the device disconnected."
        )

        pendingWrites.clear()

        writeInProgress =
            false

        currentWriteIsAudio =
            false

        _connectionState.value =
            NexusEyeConnectionState.DISCONNECTING

        try {

            bluetoothGatt?.disconnect()

        } catch (
            _: SecurityException
        ) {

            safeCloseGatt()
        }
    }

    fun reconnect(
        item: NexusEyeBleDevice
    ) {

        disconnect()

        connect(
            item
        )
    }

    fun sendCommand(
        command: String,
        value: String? = null
    ) {

        sendRaw(
            NexusEyeBleProtocol.command(
                command,
                value
            )
        )
    }

    fun sendMessage(
        message: String
    ) {

        sendRaw(
            "$message${NexusEyeBleProtocol.COMMAND_SEPARATOR}"
        )
    }

    private fun sendRaw(
        message: String
    ) {

        if (
            message.isEmpty()
        ) {
            return
        }

        if (
            writeCharacteristic == null
        ) {

            _errorMessage.value =
                "NEXUS EYE is not ready for BLE messages."

            return
        }

        if (
            _connectionState.value !=
            NexusEyeConnectionState.READY
        ) {

            _errorMessage.value =
                "NEXUS EYE BLE connection is not ready."

            return
        }

        val data =
            message.toByteArray(
                Charsets.UTF_8
            )

        queueBytes(
            bytes = data,
            isAudio = false
        )
    }

    /*
     * ============================================================
     * AUDIO API
     * ============================================================
     */

    /*
     * Compatibility API used by older audio routing code.
     *
     * The ByteArray is treated as the complete MP3 file.
     */
    fun sendAudioFile(
        audioBytes: ByteArray,
        onComplete:
            (Boolean, String?) -> Unit = { _, _ -> }
    ): Boolean {

        if (
            audioBytes.isEmpty()
        ) {

            _errorMessage.value =
                "Wearable audio data is empty."

            onComplete(
                false,
                "Wearable audio data is empty."
            )

            return false
        }

        val directory =
            File(
                appContext.cacheDir,
                "nexus_eye_ble_audio"
            )

        return try {

            if (
                !directory.exists() &&
                !directory.mkdirs()
            ) {

                val error =
                    "Could not create the BLE audio cache directory."

                _errorMessage.value =
                    error

                onComplete(
                    false,
                    error
                )

                false

            } else {

                val file =
                    File(
                        directory,
                        "audio_${UUID.randomUUID()}.mp3"
                    )

                FileOutputStream(
                    file
                ).use { output ->
                    output.write(
                        audioBytes
                    )
                    output.flush()
                }

                sendAudioFile(
                    file = file,
                    onComplete = { success, error ->

                        try {
                            file.delete()
                        } catch (
                            _: Exception
                        ) {
                        }

                        onComplete(
                            success,
                            error
                        )
                    }
                )
            }

        } catch (
            exception: Exception
        ) {

            val error =
                exception.message
                    ?: "Could not create temporary wearable audio file."

            _errorMessage.value =
                error

            onComplete(
                false,
                error
            )

            false
        }
    }

    /*
     * Compatibility API for older code that sends one audio
     * byte array at a time.
     *
     * The byte array is treated as a complete MP3 payload.
     */
    fun sendAudioPacket(
        packet: ByteArray,
        onComplete:
            (Boolean, String?) -> Unit = { _, _ -> }
    ): Boolean {

        return sendAudioFile(
            audioBytes = packet,
            onComplete = onComplete
        )
    }

    /*
     * Real File-based wearable audio transport.
     */
    fun sendAudioFile(
        file: File,
        onComplete:
            (Boolean, String?) -> Unit = { _, _ -> }
    ): Boolean {

        if (
            audioTransferInProgress
        ) {

            _errorMessage.value =
                "Another wearable audio transfer is already running."

            onComplete(
                false,
                "Another wearable audio transfer is already running."
            )

            return false
        }

        if (
            !hasRequiredPermissions()
        ) {

            val error =
                "Bluetooth permissions are required."

            _errorMessage.value =
                error

            onComplete(
                false,
                error
            )

            return false
        }

        if (
            writeCharacteristic == null ||
            _connectionState.value !=
            NexusEyeConnectionState.READY
        ) {

            val error =
                "NEXUS EYE BLE connection is not ready for audio."

            _errorMessage.value =
                error

            onComplete(
                false,
                error
            )

            return false
        }

        if (
            !file.exists()
        ) {

            val error =
                "Wearable audio file does not exist."

            _errorMessage.value =
                error

            onComplete(
                false,
                error
            )

            return false
        }

        if (
            file.length() <= 0L
        ) {

            val error =
                "Wearable audio file is empty."

            _errorMessage.value =
                error

            onComplete(
                false,
                error
            )

            return false
        }

        if (
            file.length() >
            MAX_AUDIO_FILE_SIZE
        ) {

            val error =
                "Wearable audio file is too large."

            _errorMessage.value =
                error

            onComplete(
                false,
                error
            )

            return false
        }

        val looksLikeMp3 =
            try {

                isLikelyMp3(
                    file
                )

            } catch (
                _: Exception
            ) {

                false
            }

        if (
            !looksLikeMp3
        ) {

            val error =
                "Audio file was rejected because it does not look like MP3."

            _errorMessage.value =
                error

            onComplete(
                false,
                error
            )

            return false
        }

        if (
            writeInProgress ||
            pendingWrites.isNotEmpty()
        ) {

            val error =
                "BLE is busy with another transfer."

            _errorMessage.value =
                error

            onComplete(
                false,
                error
            )

            return false
        }

        val frames =
            try {

                buildAudioFrames(
                    file
                )

            } catch (
                exception: Exception
            ) {

                val error =
                    exception.message
                        ?: "Could not prepare BLE audio packets."

                _errorMessage.value =
                    error

                onComplete(
                    false,
                    error
                )

                return false
            }

        if (
            frames.isEmpty()
        ) {

            val error =
                "No audio packets were created."

            _errorMessage.value =
                error

            onComplete(
                false,
                error
            )

            return false
        }

        audioTransferInProgress =
            true

        audioCompletion =
            onComplete

        audioFramesRemaining =
            frames.size

        frames.forEach { frame ->

            pendingWrites.addLast(
                PendingBleWrite(
                    bytes =
                        frame,
                    isAudio =
                        true
                )
            )
        }

        _errorMessage.value =
            null

        writeNextChunk()

        return true
    }

    private fun buildAudioFrames(
        file: File
    ): List<ByteArray> {

        val frames =
            ArrayList<ByteArray>()

        val totalLength =
            file.length()

        frames.add(
            buildAudioFrame(
                frameType =
                    AUDIO_FRAME_START,
                sequence =
                    0L,
                totalLength =
                    totalLength,
                payload =
                    ByteArray(0)
            )
        )

        val dataPayloadSize =
            maxOf(
                1,
                mtuPayloadSize -
                        AUDIO_HEADER_SIZE
            )

        val crc =
            CRC32()

        FileInputStream(
            file
        ).use { input ->

            val buffer =
                ByteArray(
                    dataPayloadSize
                )

            var sequence =
                1L

            while (true) {

                val count =
                    input.read(
                        buffer
                    )

                if (
                    count <= 0
                ) {
                    break
                }

                val payload =
                    buffer.copyOf(
                        count
                    )

                crc.update(
                    payload
                )

                frames.add(
                    buildAudioFrame(
                        frameType =
                            AUDIO_FRAME_DATA,
                        sequence =
                            sequence,
                        totalLength =
                            totalLength,
                        payload =
                            payload
                    )
                )

                sequence++
            }

            val crcBytes =
                ByteBuffer
                    .allocate(4)
                    .order(
                        ByteOrder.BIG_ENDIAN
                    )
                    .putInt(
                        crc.value.toInt()
                    )
                    .array()

            frames.add(
                buildAudioFrame(
                    frameType =
                        AUDIO_FRAME_END,
                    sequence =
                        sequence,
                    totalLength =
                        totalLength,
                    payload =
                        crcBytes
                )
            )
        }

        return frames
    }

    private fun buildAudioFrame(
        frameType: Int,
        sequence: Long,
        totalLength: Long,
        payload: ByteArray
    ): ByteArray {

        val frame =
            ByteArray(
                AUDIO_HEADER_SIZE +
                        payload.size
            )

        frame[0] =
            'N'.code.toByte()

        frame[1] =
            'X'.code.toByte()

        frame[2] =
            'A'.code.toByte()

        frame[3] =
            'U'.code.toByte()

        frame[4] =
            AUDIO_PROTOCOL_VERSION
                .toByte()

        frame[5] =
            frameType.toByte()

        ByteBuffer
            .allocate(4)
            .order(
                ByteOrder.BIG_ENDIAN
            )
            .putInt(
                sequence.toInt()
            )
            .array()
            .copyInto(
                frame,
                6
            )

        ByteBuffer
            .allocate(4)
            .order(
                ByteOrder.BIG_ENDIAN
            )
            .putInt(
                totalLength.toInt()
            )
            .array()
            .copyInto(
                frame,
                10
            )

        payload.copyInto(
            frame,
            AUDIO_HEADER_SIZE
        )

        return frame
    }

    private fun isLikelyMp3(
        file: File
    ): Boolean {

        FileInputStream(
            file
        ).use { input ->

            val header =
                ByteArray(4)

            val count =
                input.read(
                    header
                )

            if (
                count < 2
            ) {
                return false
            }

            if (
                count >= 3 &&
                header[0].toInt() ==
                'I'.code &&
                header[1].toInt() ==
                'D'.code &&
                header[2].toInt() ==
                '3'.code
            ) {
                return true
            }

            val first =
                header[0].toInt() and 0xFF

            val second =
                header[1].toInt() and 0xFF

            return first == 0xFF &&
                    (second and 0xE0) == 0xE0
        }
    }

    /*
     * ============================================================
     * GENERAL BLE WRITE
     * ============================================================
     */

    private fun queueBytes(
        bytes: ByteArray,
        isAudio: Boolean
    ) {

        if (
            bytes.isEmpty()
        ) {
            return
        }

        var start =
            0

        while (
            start < bytes.size
        ) {

            val end =
                minOf(
                    start + mtuPayloadSize,
                    bytes.size
                )

            pendingWrites.addLast(
                PendingBleWrite(
                    bytes =
                        bytes.copyOfRange(
                            start,
                            end
                        ),
                    isAudio =
                        isAudio
                )
            )

            start =
                end
        }

        writeNextChunk()
    }

    fun sendCommandBytes(
        bytes: ByteArray
    ): Boolean {

        if (
            bytes.isEmpty()
        ) {
            return false
        }

        if (
            writeCharacteristic == null ||
            _connectionState.value !=
            NexusEyeConnectionState.READY
        ) {
            return false
        }

        queueBytes(
            bytes = bytes,
            isAudio = false
        )

        return true
    }

    private fun writeNextChunk() {

        if (
            writeInProgress
        ) {
            return
        }

        if (
            pendingWrites.isEmpty()
        ) {
            return
        }

        val gatt =
            bluetoothGatt
                ?: return

        val characteristic =
            writeCharacteristic
                ?: return

        val next =
            pendingWrites.removeFirst()

        characteristic.writeType =
            BluetoothGattCharacteristic
                .WRITE_TYPE_DEFAULT

        characteristic.value =
            next.bytes

        currentWriteIsAudio =
            next.isAudio

        writeInProgress =
            true

        try {

            val started =
                gatt.writeCharacteristic(
                    characteristic
                )

            if (
                !started
            ) {

                writeInProgress =
                    false

                currentWriteIsAudio =
                    false

                if (
                    next.isAudio
                ) {

                    finishAudioTransfer(
                        success = false,
                        error =
                            "BLE audio write could not start."
                    )
                }

                _errorMessage.value =
                    "BLE write could not start."

                pendingWrites.clear()
            }

        } catch (
            securityException:
            SecurityException
        ) {

            writeInProgress =
                false

            currentWriteIsAudio =
                false

            pendingWrites.clear()

            if (
                next.isAudio
            ) {

                finishAudioTransfer(
                    success = false,
                    error =
                        "Bluetooth write permission was not granted."
                )
            }

            _errorMessage.value =
                "Bluetooth write permission was not granted."
        }
    }

    private fun finishAudioTransfer(
        success: Boolean,
        error: String?
    ) {

        val callback =
            audioCompletion

        audioCompletion =
            null

        audioTransferInProgress =
            false

        audioFramesRemaining =
            0

        if (
            !success &&
            error != null
        ) {

            _errorMessage.value =
                error
        }

        callback?.invoke(
            success,
            error
        )
    }

    private fun resetAudioTransferState(
        notifyFailure: Boolean,
        error: String =
            "Wearable audio transfer was stopped."
    ) {

        val active =
            audioTransferInProgress

        if (
            active &&
            notifyFailure
        ) {

            finishAudioTransfer(
                success = false,
                error = error
            )

        } else {

            audioTransferInProgress =
                false

            audioFramesRemaining =
                0

            audioCompletion =
                null
        }
    }

    fun cancelAudioTransfer() {

        if (
            !audioTransferInProgress
        ) {
            return
        }

        pendingWrites.removeIf {
            it.isAudio
        }

        writeInProgress =
            false

        currentWriteIsAudio =
            false

        finishAudioTransfer(
            success = false,
            error =
                "Wearable audio transfer was cancelled."
        )
    }

    @Suppress("DEPRECATION")
    private fun enableNotifications(
        gatt: BluetoothGatt,
        characteristic:
        BluetoothGattCharacteristic
    ) {

        try {

            val notificationEnabled =
                gatt.setCharacteristicNotification(
                    characteristic,
                    true
                )

            if (
                !notificationEnabled
            ) {

                _connectionState.value =
                    NexusEyeConnectionState.ERROR

                _errorMessage.value =
                    "Could not enable BLE notifications."

                return
            }

            val descriptor =
                characteristic.getDescriptor(
                    NexusEyeBleProtocol.CCCD_UUID
                )

            if (
                descriptor == null
            ) {

                _connectionState.value =
                    NexusEyeConnectionState.ERROR

                _errorMessage.value =
                    "BLE notification descriptor not found."

                return
            }

            descriptor.value =
                BluetoothGattDescriptor
                    .ENABLE_NOTIFICATION_VALUE

            gatt.writeDescriptor(
                descriptor
            )

        } catch (
            securityException:
            SecurityException
        ) {

            _connectionState.value =
                NexusEyeConnectionState.ERROR

            _errorMessage.value =
                "Bluetooth permission was not granted."
        }
    }

    @Suppress("DEPRECATION")
    private fun requestMtu(
        gatt: BluetoothGatt
    ) {

        try {

            val started =
                gatt.requestMtu(
                    DEFAULT_MTU
                )

            if (
                !started
            ) {

                gatt.discoverServices()
            }

        } catch (
            securityException:
            SecurityException
        ) {

            _errorMessage.value =
                "Bluetooth connection permission was not granted."

            _connectionState.value =
                NexusEyeConnectionState.ERROR
        }
    }

    private fun safeCloseGatt() {

        try {
            bluetoothGatt?.close()
        } catch (
            _: SecurityException
        ) {
        }

        bluetoothGatt =
            null
    }

    fun clearError() {

        _errorMessage.value =
            null
    }

    fun close() {

        stopScanInternal()

        handler.removeCallbacks(
            scanStopRunnable
        )

        pendingWrites.clear()

        resetAudioTransferState(
            notifyFailure = true,
            error =
                "Wearable audio transfer stopped because BLE manager closed."
        )

        writeInProgress =
            false

        currentWriteIsAudio =
            false

        try {
            bluetoothGatt?.disconnect()
        } catch (
            _: SecurityException
        ) {
        }

        safeCloseGatt()

        writeCharacteristic =
            null

        notifyCharacteristic =
            null

        _connectionState.value =
            NexusEyeConnectionState.DISCONNECTED
    }

    companion object {

        private const val SCAN_DURATION_MS =
            10_000L

        private const val DEFAULT_MTU =
            247

        private const val DEFAULT_MTU_PAYLOAD =
            20

        private const val AUDIO_PROTOCOL_VERSION =
            1

        private const val AUDIO_FRAME_START =
            1

        private const val AUDIO_FRAME_DATA =
            2

        private const val AUDIO_FRAME_END =
            3

        private const val AUDIO_HEADER_SIZE =
            14

        private const val MAX_AUDIO_FILE_SIZE =
            10L * 1024L * 1024L
    }
}