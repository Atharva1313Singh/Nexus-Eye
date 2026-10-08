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
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.thirdeye.app.voice.NexusEyeVoskRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

enum class NexusEyeConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    READY,
    ERROR
}

data class NexusEyeBleDevice(
    val device: BluetoothDevice,
    val name: String,
    val address: String,
    val rssi: Int = 0
)

class NexusEyeBleManager(
    private val context: Context
) {

    companion object {
        private const val TAG = "NexusEyeBLE"

        const val DEVICE_NAME = "NEXUS EYE"

        const val SERVICE_UUID =
            "12345678-1234-5678-1234-56789abcdef0"

        const val RX_UUID =
            "12345678-1234-5678-1234-56789abcdef1"

        const val TX_UUID =
            "12345678-1234-5678-1234-56789abcdef2"

        private val SERVICE_UUID_OBJECT =
            UUID.fromString(SERVICE_UUID)

        private val RX_UUID_OBJECT =
            UUID.fromString(RX_UUID)

        private val TX_UUID_OBJECT =
            UUID.fromString(TX_UUID)

        private val CLIENT_CONFIG_UUID =
            UUID.fromString(
                "00002902-0000-1000-8000-00805f9b34fb"
            )

        // -----------------------------------------------------
        // NXAW
        // -----------------------------------------------------

        private val NXAW_MAGIC =
            byteArrayOf(
                'N'.code.toByte(),
                'X'.code.toByte(),
                'A'.code.toByte(),
                'W'.code.toByte()
            )

        private const val NXAW_VERSION: Byte = 1

        private const val NXAW_START: Byte = 1
        private const val NXAW_DATA: Byte = 2
        private const val NXAW_END: Byte = 3

        private const val NXAW_HEADER_SIZE = 14

        private const val MAX_WAV_SIZE =
            10 * 1024 * 1024

        private const val AUDIO_CHUNK_SIZE = 180

        private const val SCAN_PERIOD_MS = 10_000L
    }

    private val appContext =
        context.applicationContext

    private val bluetoothManager =
        appContext.getSystemService(
            Context.BLUETOOTH_SERVICE
        ) as BluetoothManager

    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager.adapter

    // ---------------------------------------------------------
    // Public state
    // ---------------------------------------------------------

    private val _devices =
        MutableStateFlow<List<NexusEyeBleDevice>>(emptyList())

    val devices: StateFlow<List<NexusEyeBleDevice>> =
        _devices.asStateFlow()

    private val _connectionState =
        MutableStateFlow(
            NexusEyeConnectionState.DISCONNECTED
        )

    val connectionState:
            StateFlow<NexusEyeConnectionState> =
        _connectionState.asStateFlow()

    private val _lastReceivedMessage =
        MutableStateFlow<String?>(null)

    val lastReceivedMessage:
            StateFlow<String?> =
        _lastReceivedMessage.asStateFlow()

    private val _errorMessage =
        MutableStateFlow<String?>(null)

    val errorMessage:
            StateFlow<String?> =
        _errorMessage.asStateFlow()

    // ---------------------------------------------------------
    // Callbacks
    // ---------------------------------------------------------

    var onConnected: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null

    var onTextReceived: ((String) -> Unit)? = null
    var onSpeechRecognized: ((String) -> Unit)? = null

    // ---------------------------------------------------------
    // BLE
    // ---------------------------------------------------------

    private var bluetoothGatt: BluetoothGatt? = null

    private var rxCharacteristic:
            BluetoothGattCharacteristic? = null

    private var txCharacteristic:
            BluetoothGattCharacteristic? = null

    private var scanCallback:
            android.bluetooth.le.ScanCallback? = null

    private var scanTimeoutRunnable: Runnable? = null

    @Volatile
    private var connected = false

    private var currentDevice:
            BluetoothDevice? = null

    private var negotiatedMtu = 23

    // ---------------------------------------------------------
    // NXAW receive state
    // ---------------------------------------------------------

    private val nxawLock = Any()

    private var nxawBuffer:
            ByteArrayOutputStream? = null

    private var nxawExpectedLength = 0

    private var nxawExpectedSequence = 0L

    private var nxawActive = false

    private var nxawFrameCount = 0

    // ---------------------------------------------------------
    // Vosk
    // ---------------------------------------------------------

    @Volatile
    private var voskRecognizer:
            NexusEyeVoskRecognizer? = null

    // ---------------------------------------------------------
    // Audio transfer
    // ---------------------------------------------------------

    @Volatile
    private var audioTransferCancelled = false

    // ---------------------------------------------------------
    // Basic properties
    // ---------------------------------------------------------

    fun hasBluetooth(): Boolean =
        bluetoothAdapter != null

    fun isBluetoothEnabled(): Boolean =
        bluetoothAdapter?.isEnabled == true

    fun hasRequiredPermissions(): Boolean {

        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.S
        ) {

            return ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        appContext,
                        Manifest.permission.BLUETOOTH_ADMIN
                    ) == PackageManager.PERMISSION_GRANTED
        }

        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
    }

    val isWearableAudioAvailable: Boolean
        get() =
            connected &&
                    txCharacteristic != null &&
                    rxCharacteristic != null

    // ---------------------------------------------------------
    // Scan
    // ---------------------------------------------------------

    fun startScan(
        durationMs: Long = SCAN_PERIOD_MS
    ) {

        val adapter = bluetoothAdapter

        if (adapter == null) {
            setError("Bluetooth adapter unavailable")
            return
        }

        if (!adapter.isEnabled) {
            setError("Bluetooth is disabled")
            return
        }

        if (!hasRequiredPermissions()) {
            setError("Bluetooth permissions are not granted")
            return
        }

        stopScan()

        _devices.value = emptyList()

        _connectionState.value =
            NexusEyeConnectionState.SCANNING

        val scanner =
            adapter.bluetoothLeScanner

        if (scanner == null) {

            setError(
                "BLE scanner unavailable"
            )

            _connectionState.value =
                NexusEyeConnectionState.ERROR

            return
        }

        val callback =
            object : android.bluetooth.le.ScanCallback() {

                override fun onScanResult(
                    callbackType: Int,
                    result:
                    android.bluetooth.le.ScanResult
                ) {

                    val device =
                        result.device

                    val name =
                        result.scanRecord?.deviceName
                            ?: try {
                                device.name
                            } catch (
                                _: SecurityException
                            ) {
                                null
                            }
                            ?: ""

                    val address =
                        try {
                            device.address
                        } catch (
                            _: SecurityException
                        ) {
                            return
                        }

                    if (
                        !name.equals(
                            DEVICE_NAME,
                            ignoreCase = true
                        )
                    ) {
                        return
                    }

                    val item =
                        NexusEyeBleDevice(
                            device = device,
                            name = name,
                            address = address,
                            rssi = result.rssi
                        )

                    val existing =
                        _devices.value.toMutableList()

                    val index =
                        existing.indexOfFirst {
                            it.address == address
                        }

                    if (index >= 0) {
                        existing[index] = item
                    } else {
                        existing.add(item)
                    }

                    _devices.value =
                        existing
                }

                override fun onScanFailed(
                    errorCode: Int
                ) {

                    Log.e(
                        TAG,
                        "BLE scan failed: $errorCode"
                    )

                    setError(
                        "BLE scan failed: $errorCode"
                    )

                    _connectionState.value =
                        NexusEyeConnectionState.ERROR
                }
            }

        scanCallback = callback

        try {

            scanner.startScan(
                callback
            )

            scanTimeoutRunnable =
                Runnable {

                    stopScan()

                    if (!connected) {

                        _connectionState.value =
                            NexusEyeConnectionState.DISCONNECTED
                    }
                }

            Handler(
                Looper.getMainLooper()
            ).postDelayed(
                scanTimeoutRunnable!!,
                durationMs
            )

        } catch (
            e: SecurityException
        ) {

            Log.e(
                TAG,
                "Unable to start BLE scan",
                e
            )

            setError(
                "Bluetooth scan permission denied"
            )

            _connectionState.value =
                NexusEyeConnectionState.ERROR
        }
    }

    fun stopScan() {

        val adapter =
            bluetoothAdapter

        val scanner =
            adapter?.bluetoothLeScanner

        val callback =
            scanCallback

        if (
            scanner != null &&
            callback != null &&
            hasRequiredPermissions()
        ) {

            try {

                scanner.stopScan(
                    callback
                )

            } catch (
                e: Exception
            ) {

                Log.w(
                    TAG,
                    "stopScan failed",
                    e
                )
            }
        }

        scanCallback = null

        scanTimeoutRunnable?.let {

            Handler(
                Looper.getMainLooper()
            ).removeCallbacks(it)
        }

        scanTimeoutRunnable = null

        if (
            _connectionState.value ==
            NexusEyeConnectionState.SCANNING
        ) {

            _connectionState.value =
                NexusEyeConnectionState.DISCONNECTED
        }
    }

    // ---------------------------------------------------------
    // Connect
    // ---------------------------------------------------------

    fun connect(
        device: BluetoothDevice
    ) {

        if (!hasRequiredPermissions()) {

            setError(
                "Bluetooth permissions are not granted"
            )

            return
        }

        stopScan()

        Log.i(
            TAG,
            "Connecting to ${safeAddress(device)}"
        )

        _connectionState.value =
            NexusEyeConnectionState.CONNECTING

        try {
            bluetoothGatt?.close()
        } catch (
            _: Exception
        ) {
        }

        bluetoothGatt = null
        rxCharacteristic = null
        txCharacteristic = null
        connected = false

        currentDevice = device

        try {

            bluetoothGatt =
                if (
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.M
                ) {

                    device.connectGatt(
                        appContext,
                        false,
                        gattCallback,
                        BluetoothDevice.TRANSPORT_LE
                    )

                } else {

                    device.connectGatt(
                        appContext,
                        false,
                        gattCallback
                    )
                }

        } catch (
            e: SecurityException
        ) {

            Log.e(
                TAG,
                "BLE connect permission denied",
                e
            )

            setError(
                "Bluetooth connect permission denied"
            )

            _connectionState.value =
                NexusEyeConnectionState.ERROR
        }
    }

    fun connect(
        device: NexusEyeBleDevice
    ) {
        connect(device.device)
    }

    fun reconnect() {

        val device =
            currentDevice

        if (device == null) {

            setError(
                "No previous Nexus Eye device"
            )

            return
        }

        connect(device)
    }

    fun disconnect() {

        Log.i(
            TAG,
            "Disconnect requested"
        )

        _connectionState.value =
            NexusEyeConnectionState.DISCONNECTING

        try {

            bluetoothGatt?.disconnect()

        } catch (
            e: Exception
        ) {

            Log.w(
                TAG,
                "Disconnect failed",
                e
            )

            finishDisconnected()
        }
    }

    fun close() {

        Log.i(
            TAG,
            "Closing BLE manager"
        )

        stopScan()

        connected = false

        try {
            bluetoothGatt?.close()
        } catch (
            _: Exception
        ) {
        }

        bluetoothGatt = null
        rxCharacteristic = null
        txCharacteristic = null
        currentDevice = null

        synchronized(nxawLock) {
            resetNxawState()
        }

        voskRecognizer?.shutdown()
        voskRecognizer = null

        _connectionState.value =
            NexusEyeConnectionState.DISCONNECTED
    }

    fun isConnected(): Boolean =
        connected

    // ---------------------------------------------------------
    // GATT CALLBACK
    // ---------------------------------------------------------

    private val gattCallback =
        object : BluetoothGattCallback() {

            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int
            ) {

                super.onConnectionStateChange(
                    gatt,
                    status,
                    newState
                )

                Log.i(
                    TAG,
                    "Connection state " +
                            "status=$status " +
                            "state=$newState"
                )

                if (
                    newState ==
                    BluetoothProfile.STATE_CONNECTED
                ) {

                    connected = true
                    bluetoothGatt = gatt

                    _connectionState.value =
                        NexusEyeConnectionState.CONNECTED

                    Log.i(
                        TAG,
                        "BLE connected"
                    )

                    try {

                        gatt.requestMtu(
                            517
                        )

                    } catch (
                        e: SecurityException
                    ) {

                        Log.e(
                            TAG,
                            "requestMtu permission error",
                            e
                        )

                        try {
                            gatt.discoverServices()
                        } catch (
                            _: Exception
                        ) {
                        }
                    }

                } else if (
                    newState ==
                    BluetoothProfile.STATE_DISCONNECTED
                ) {

                    finishDisconnected()

                    try {
                        gatt.close()
                    } catch (
                        _: Exception
                    ) {
                    }
                }
            }

            override fun onMtuChanged(
                gatt: BluetoothGatt,
                mtu: Int,
                status: Int
            ) {

                super.onMtuChanged(
                    gatt,
                    mtu,
                    status
                )

                negotiatedMtu = mtu

                Log.i(
                    TAG,
                    "MTU changed: " +
                            "mtu=$mtu status=$status"
                )

                try {

                    gatt.discoverServices()

                } catch (
                    e: SecurityException
                ) {

                    Log.e(
                        TAG,
                        "discoverServices permission error",
                        e
                    )
                }
            }

            override fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int
            ) {

                super.onServicesDiscovered(
                    gatt,
                    status
                )

                if (
                    status !=
                    BluetoothGatt.GATT_SUCCESS
                ) {

                    Log.e(
                        TAG,
                        "Service discovery failed: $status"
                    )

                    setError(
                        "BLE service discovery failed"
                    )

                    _connectionState.value =
                        NexusEyeConnectionState.ERROR

                    return
                }

                val service =
                    gatt.getService(
                        SERVICE_UUID_OBJECT
                    )

                if (service == null) {

                    Log.e(
                        TAG,
                        "NEXUS EYE service not found"
                    )

                    setError(
                        "NEXUS EYE BLE service not found"
                    )

                    _connectionState.value =
                        NexusEyeConnectionState.ERROR

                    return
                }

                rxCharacteristic =
                    service.getCharacteristic(
                        RX_UUID_OBJECT
                    )

                txCharacteristic =
                    service.getCharacteristic(
                        TX_UUID_OBJECT
                    )

                if (rxCharacteristic == null) {

                    setError(
                        "NEXUS EYE RX characteristic not found"
                    )

                    return
                }

                if (txCharacteristic == null) {

                    setError(
                        "NEXUS EYE TX characteristic not found"
                    )

                    return
                }

                enableNotifications(
                    gatt
                )

                _connectionState.value =
                    NexusEyeConnectionState.READY

                Log.i(
                    TAG,
                    "NEXUS EYE BLE READY"
                )

                onConnected?.invoke()

                Thread {

                    Log.i(
                        TAG,
                        "Starting background Vosk initialization..."
                    )

                    val ready =
                        ensureVoskInitialized()

                    if (ready) {

                        Log.i(
                            TAG,
                            "Vosk is READY for speech recognition."
                        )

                    } else {

                        Log.e(
                            TAG,
                            "Vosk failed to initialize."
                        )
                    }

                }.start()

                Handler(
                    Looper.getMainLooper()
                ).postDelayed(
                    {
                        sendHello()
                    },
                    300
                )
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int
            ) {

                super.onDescriptorWrite(
                    gatt,
                    descriptor,
                    status
                )

                Log.i(
                    TAG,
                    "CCCD write status=$status"
                )
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic:
                BluetoothGattCharacteristic
            ) {

                super.onCharacteristicChanged(
                    gatt,
                    characteristic
                )

                if (
                    characteristic.uuid !=
                    TX_UUID_OBJECT
                ) {
                    return
                }

                val data =
                    characteristic.value
                        ?: return

                Log.d(
                    TAG,
                    "BLE notification received: " +
                            "${data.size} bytes"
                )

                handleIncomingPacket(
                    data
                )
            }

            @Suppress("DEPRECATION")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic:
                BluetoothGattCharacteristic,
                value: ByteArray
            ) {

                super.onCharacteristicChanged(
                    gatt,
                    characteristic,
                    value
                )

                if (
                    characteristic.uuid !=
                    TX_UUID_OBJECT
                ) {
                    return
                }

                Log.d(
                    TAG,
                    "BLE notification received: " +
                            "${value.size} bytes"
                )

                handleIncomingPacket(
                    value
                )
            }
        }

    // ---------------------------------------------------------
    // Notifications
    // ---------------------------------------------------------

    private fun enableNotifications(
        gatt: BluetoothGatt
    ) {

        val characteristic =
            txCharacteristic
                ?: return

        try {

            val localResult =
                gatt.setCharacteristicNotification(
                    characteristic,
                    true
                )

            Log.i(
                TAG,
                "setCharacteristicNotification=" +
                        localResult
            )

            val descriptor =
                characteristic.getDescriptor(
                    CLIENT_CONFIG_UUID
                )

            if (descriptor == null) {

                setError(
                    "BLE notification descriptor missing"
                )

                return
            }

            descriptor.value =
                BluetoothGattDescriptor
                    .ENABLE_NOTIFICATION_VALUE

            gatt.writeDescriptor(
                descriptor
            )

        } catch (
            e: SecurityException
        ) {

            Log.e(
                TAG,
                "Notification permission error",
                e
            )

            setError(
                "Bluetooth notification permission denied"
            )
        }
    }

    // ---------------------------------------------------------
    // TEXT
    // ---------------------------------------------------------

    fun sendText(
        text: String
    ): Boolean {

        val characteristic =
            rxCharacteristic
                ?: run {

                    Log.w(
                        TAG,
                        "RX characteristic unavailable"
                    )

                    return false
                }

        val message =
            if (text.endsWith("\n")) {
                text
            } else {
                "$text\n"
            }

        characteristic.value =
            message.toByteArray(
                Charsets.UTF_8
            )

        return try {

            val result =
                bluetoothGatt
                    ?.writeCharacteristic(
                        characteristic
                    )
                    ?: false

            Log.d(
                TAG,
                "TX text: ${message.trim()} " +
                        "result=$result"
            )

            result

        } catch (
            e: SecurityException
        ) {

            Log.e(
                TAG,
                "Text send permission error",
                e
            )

            false
        }
    }

    fun sendMessage(
        message: String
    ): Boolean =
        sendText(message)

    fun sendCommand(
        command: String
    ): Boolean =
        sendText(command)

    fun sendHello(): Boolean =
        sendText("HELLO")

    fun sendPing(): Boolean =
        sendText("PING")

    fun sendStatus(): Boolean =
        sendText("STATUS")

    fun sendAnswer(
        answer: String
    ): Boolean =
        sendText(
            "ANSWER:$answer"
        )

    // ---------------------------------------------------------
    // Incoming packet routing
    // ---------------------------------------------------------

    private fun handleIncomingPacket(
        data: ByteArray
    ) {

        if (data.isEmpty()) {
            return
        }

        Log.d(
            TAG,
            "Incoming packet size=${data.size}"
        )

        if (
            data.size >= NXAW_HEADER_SIZE &&
            isNxawPacket(data)
        ) {

            Log.d(
                TAG,
                "Incoming packet identified as NXAW."
            )

            handleNxawPacket(
                data
            )

            return
        }

        val text =
            data.toString(
                Charsets.UTF_8
            ).trim()

        if (text.isEmpty()) {
            return
        }

        Log.i(
            TAG,
            "RX text: $text"
        )

        _lastReceivedMessage.value =
            text

        handleEsp32TextMessage(
            text
        )
    }

    private fun isNxawPacket(
        data: ByteArray
    ): Boolean {

        if (
            data.size <
            NXAW_HEADER_SIZE
        ) {
            return false
        }

        for (
        i in 0 until 4
        ) {

            if (
                data[i] !=
                NXAW_MAGIC[i]
            ) {
                return false
            }
        }

        return data[4] ==
                NXAW_VERSION
    }

    // ---------------------------------------------------------
    // NXAW
    // ---------------------------------------------------------

    private fun handleNxawPacket(
        data: ByteArray
    ) {

        if (
            data.size <
            NXAW_HEADER_SIZE
        ) {
            return
        }

        val frameType =
            data[5]

        val sequence =
            readUInt32BigEndian(
                data,
                6
            )

        val totalLength =
            readUInt32BigEndian(
                data,
                10
            )

        val payload =
            if (
                data.size >
                NXAW_HEADER_SIZE
            ) {

                data.copyOfRange(
                    NXAW_HEADER_SIZE,
                    data.size
                )

            } else {

                ByteArray(0)
            }

        Log.d(
            TAG,
            "NXAW frame: " +
                    "type=$frameType " +
                    "seq=$sequence " +
                    "total=$totalLength " +
                    "payload=${payload.size}"
        )

        when (frameType) {

            NXAW_START -> {

                Log.i(
                    TAG,
                    "NXAW START received."
                )

                beginNxawTransfer(
                    totalLength,
                    sequence,
                    payload
                )
            }

            NXAW_DATA -> {

                appendNxawTransfer(
                    sequence,
                    payload
                )
            }

            NXAW_END -> {

                Log.i(
                    TAG,
                    "NXAW END received."
                )

                finishNxawTransfer(
                    sequence
                )
            }

            else -> {

                Log.w(
                    TAG,
                    "Unknown NXAW frame type=$frameType"
                )
            }
        }
    }

    private fun beginNxawTransfer(
        totalLength: Long,
        sequence: Long,
        payload: ByteArray
    ) {

        synchronized(nxawLock) {

            resetNxawState()

            if (
                totalLength <= 0 ||
                totalLength > MAX_WAV_SIZE
            ) {

                Log.e(
                    TAG,
                    "Invalid WAV length=$totalLength"
                )

                return
            }

            nxawExpectedLength =
                totalLength.toInt()

            nxawExpectedSequence =
                sequence + 1

            nxawFrameCount = 1

            nxawBuffer =
                ByteArrayOutputStream(
                    nxawExpectedLength
                )

            nxawActive = true

            if (
                payload.isNotEmpty()
            ) {

                nxawBuffer?.write(
                    payload
                )
            }

            val currentSize =
                nxawBuffer?.size() ?: 0

            Log.i(
                TAG,
                "NXAW transfer started: " +
                        "expected=$nxawExpectedLength " +
                        "seq=$sequence " +
                        "firstPayload=${payload.size} " +
                        "buffered=$currentSize"
            )

            if (
                currentSize >
                nxawExpectedLength
            ) {

                Log.e(
                    TAG,
                    "NXAW START exceeds expected length"
                )

                resetNxawState()
            }
        }
    }

    private fun appendNxawTransfer(
        sequence: Long,
        payload: ByteArray
    ) {

        synchronized(nxawLock) {

            if (!nxawActive) {

                Log.w(
                    TAG,
                    "NXAW DATA without START. " +
                            "seq=$sequence"
                )

                return
            }

            if (
                sequence !=
                nxawExpectedSequence
            ) {

                Log.e(
                    TAG,
                    "NXAW sequence error: " +
                            "expected=$nxawExpectedSequence " +
                            "received=$sequence"
                )

                resetNxawState()

                return
            }

            nxawExpectedSequence++
            nxawFrameCount++

            if (
                payload.isNotEmpty()
            ) {

                nxawBuffer?.write(
                    payload
                )
            }

            val current =
                nxawBuffer?.size() ?: 0

            Log.d(
                TAG,
                "NXAW DATA: " +
                        "seq=$sequence " +
                        "payload=${payload.size} " +
                        "buffer=$current/$nxawExpectedLength"
            )

            if (
                current >
                nxawExpectedLength
            ) {

                Log.e(
                    TAG,
                    "NXAW buffer exceeded expected size"
                )

                resetNxawState()

                return
            }
        }
    }

    private fun finishNxawTransfer(
        sequence: Long
    ) {

        val wav: ByteArray

        synchronized(nxawLock) {

            if (!nxawActive) {

                Log.w(
                    TAG,
                    "NXAW END without active transfer."
                )

                return
            }

            if (
                sequence !=
                nxawExpectedSequence
            ) {

                Log.e(
                    TAG,
                    "NXAW END sequence error: " +
                            "expected=$nxawExpectedSequence " +
                            "received=$sequence"
                )

                resetNxawState()

                return
            }

            val result =
                nxawBuffer?.toByteArray()

            if (result == null) {

                Log.e(
                    TAG,
                    "NXAW WAV buffer missing."
                )

                resetNxawState()

                return
            }

            Log.i(
                TAG,
                "NXAW END: " +
                        "frames=$nxawFrameCount " +
                        "expectedBytes=$nxawExpectedLength " +
                        "actualBytes=${result.size}"
            )

            if (
                result.size !=
                nxawExpectedLength
            ) {

                Log.e(
                    TAG,
                    "NXAW length mismatch: " +
                            "expected=$nxawExpectedLength " +
                            "actual=${result.size}"
                )

                resetNxawState()

                return
            }

            wav = result

            Log.i(
                TAG,
                "NXAW transfer COMPLETE: " +
                        "${wav.size} byte WAV"
            )

            resetNxawState()
        }

        recognizeReceivedSpeech(
            wav
        )
    }

    private fun resetNxawState() {

        nxawBuffer?.reset()
        nxawBuffer = null

        nxawExpectedLength = 0
        nxawExpectedSequence = 0
        nxawActive = false
        nxawFrameCount = 0
    }

    // ---------------------------------------------------------
    // VOSK
    // ---------------------------------------------------------

    private fun ensureVoskInitialized():
            Boolean {

        val existing =
            voskRecognizer

        if (
            existing != null &&
            existing.isReady()
        ) {

            Log.i(
                TAG,
                "Vosk recognizer already READY."
            )

            return true
        }

        if (
            existing != null &&
            !existing.isReady()
        ) {

            Log.w(
                TAG,
                "Vosk recognizer exists but is NOT ready. Reinitializing."
            )

            voskRecognizer = null
        }

        return try {

            Log.i(
                TAG,
                "Creating NexusEyeVoskRecognizer..."
            )

            val recognizer =
                NexusEyeVoskRecognizer(
                    appContext
                )

            Log.i(
                TAG,
                "Calling Vosk initialize()..."
            )

            var initializationError:
                    String? = null

            recognizer.initialize(

                onSuccess = {

                    Log.i(
                        TAG,
                        "Vosk initialize callback: SUCCESS"
                    )
                },

                onError = { error ->

                    initializationError =
                        error

                    Log.e(
                        TAG,
                        "Vosk initialize callback: ERROR: $error"
                    )
                }
            )

            if (
                initializationError != null
            ) {

                Log.e(
                    TAG,
                    "Vosk initialization returned error."
                )

                recognizer.shutdown()

                return false
            }

            if (
                !recognizer.isReady()
            ) {

                Log.e(
                    TAG,
                    "Vosk initialize() returned, " +
                            "but recognizer is NOT ready."
                )

                recognizer.shutdown()

                return false
            }

            voskRecognizer =
                recognizer

            Log.i(
                TAG,
                "Vosk recognizer created and READY."
            )

            true

        } catch (
            e: Exception
        ) {

            Log.e(
                TAG,
                "Vosk initialization failed.",
                e
            )

            voskRecognizer = null

            false
        }
    }

    private fun recognizeReceivedSpeech(
        wav: ByteArray
    ) {

        Log.i(
            TAG,
            "recognizeReceivedSpeech(): " +
                    "WAV=${wav.size} bytes"
        )

        Thread {

            try {

                Log.i(
                    TAG,
                    "Speech recognition worker started."
                )

                if (
                    !ensureVoskInitialized()
                ) {

                    Log.e(
                        TAG,
                        "Cannot recognize speech: " +
                                "Vosk unavailable."
                    )

                    return@Thread
                }

                Log.i(
                    TAG,
                    "Vosk confirmed READY."
                )

                Log.i(
                    TAG,
                    "Sending ${wav.size} byte WAV to Vosk..."
                )

                voskRecognizer?.recognizeWav(

                    wavBytes = wav,

                    onResult = { recognizedText ->

                        val text =
                            recognizedText.trim()

                        if (
                            text.isEmpty()
                        ) {

                            Log.w(
                                TAG,
                                "Vosk returned an EMPTY recognition result."
                            )

                        } else {

                            Log.i(
                                TAG,
                                "Vosk RESULT: \"$text\""
                            )

                            Handler(
                                Looper.getMainLooper()
                            ).post {

                                onSpeechRecognized
                                    ?.invoke(text)

                                onTextReceived
                                    ?.invoke(text)
                            }

                            val sent =
                                sendText(
                                    "TEXT:$text"
                                )

                            Log.i(
                                TAG,
                                "TEXT response sent to ESP32: " +
                                        "success=$sent"
                            )
                        }
                    },

                    onError = { error ->

                        Log.e(
                            TAG,
                            "Vosk recognition ERROR: $error"
                        )
                    }
                )

            } catch (
                e: Exception
            ) {

                Log.e(
                    TAG,
                    "Speech recognition worker failed.",
                    e
                )
            }

        }.start()
    }

    // ---------------------------------------------------------
    // ESP32 TEXT
    // ---------------------------------------------------------

    private fun handleEsp32TextMessage(
        text: String
    ) {

        if (
            text.startsWith(
                "TEXT:",
                ignoreCase = false
            )
        ) {

            val recognizedText =
                text.substringAfter(
                    "TEXT:"
                ).trim()

            if (
                recognizedText.isNotEmpty()
            ) {

                onTextReceived
                    ?.invoke(
                        recognizedText
                    )
            }

            return
        }

        onTextReceived?.invoke(
            text
        )
    }

    // ---------------------------------------------------------
    // ANDROID -> ESP32 AUDIO
    // ---------------------------------------------------------

    fun cancelAudioTransfer() {

        audioTransferCancelled = true
    }

    fun sendAudioFile(
        audio: ByteArray
    ): Boolean {

        audioTransferCancelled = false

        if (audio.isEmpty()) {

            setError(
                "Audio data is empty"
            )

            return false
        }

        if (!connected) {

            setError(
                "NEXUS EYE is not connected"
            )

            return false
        }

        return sendAudioPacketized(
            audio
        )
    }

    fun sendAudioFile(
        file: File
    ): Boolean {

        return try {

            if (!file.exists()) {

                setError(
                    "Audio file does not exist"
                )

                return false
            }

            sendAudioFile(
                file.readBytes()
            )

        } catch (
            e: Exception
        ) {

            Log.e(
                TAG,
                "Unable to read audio file",
                e
            )

            setError(
                "Unable to read audio file"
            )

            false
        }
    }

    fun sendAudioFile(
        file: File,
        onComplete:
            (Boolean, String?) -> Unit
    ): Boolean {

        val started =
            sendAudioFile(
                file
            )

        if (!started) {

            onComplete(
                false,
                _errorMessage.value
            )

            return false
        }

        onComplete(
            true,
            null
        )

        return true
    }

    fun sendAudioPacket(
        packet: ByteArray
    ): Boolean {

        if (packet.isEmpty()) {
            return false
        }

        return writeBytes(
            packet
        )
    }

    fun sendCommandBytes(
        bytes: ByteArray
    ): Boolean {

        if (bytes.isEmpty()) {
            return false
        }

        return writeBytes(
            bytes
        )
    }

    private fun sendAudioPacketized(
        audio: ByteArray
    ): Boolean {

        val totalLength =
            audio.size

        var offset = 0
        var sequence = 0

        while (
            offset < totalLength
        ) {

            if (
                audioTransferCancelled
            ) {

                Log.w(
                    TAG,
                    "Audio transfer cancelled"
                )

                return false
            }

            val end =
                minOf(
                    offset +
                            AUDIO_CHUNK_SIZE,
                    totalLength
                )

            val chunk =
                audio.copyOfRange(
                    offset,
                    end
                )

            val frameType =
                when {

                    offset == 0 ->
                        1

                    end == totalLength ->
                        3

                    else ->
                        2
                }

            val packet =
                createNxauPacket(
                    frameType,
                    sequence,
                    totalLength,
                    chunk
                )

            if (
                !writeBytes(
                    packet
                )
            ) {
                return false
            }

            offset = end
            sequence++
        }

        return true
    }

    private fun createNxauPacket(
        frameType: Int,
        sequence: Int,
        totalLength: Int,
        payload: ByteArray
    ): ByteArray {

        val packet =
            ByteArray(
                14 +
                        payload.size
            )

        packet[0] =
            'N'.code.toByte()

        packet[1] =
            'X'.code.toByte()

        packet[2] =
            'A'.code.toByte()

        packet[3] =
            'U'.code.toByte()

        packet[4] =
            1

        packet[5] =
            frameType.toByte()

        writeUInt32BigEndian(
            packet,
            6,
            sequence.toLong()
        )

        writeUInt32BigEndian(
            packet,
            10,
            totalLength.toLong()
        )

        payload.copyInto(
            packet,
            14
        )

        return packet
    }

    private fun writeBytes(
        bytes: ByteArray
    ): Boolean {

        val characteristic =
            rxCharacteristic
                ?: return false

        if (!connected) {
            return false
        }

        characteristic.value =
            bytes

        return try {

            bluetoothGatt
                ?.writeCharacteristic(
                    characteristic
                )
                ?: false

        } catch (
            e: SecurityException
        ) {

            Log.e(
                TAG,
                "BLE write permission error",
                e
            )

            false
        }
    }

    // ---------------------------------------------------------
    // ERROR HANDLING
    // ---------------------------------------------------------

    fun clearError() {

        _errorMessage.value = null
    }

    private fun setError(
        message: String
    ) {

        Log.e(
            TAG,
            message
        )

        _errorMessage.value =
            message
    }

    // ---------------------------------------------------------
    // DISCONNECT
    // ---------------------------------------------------------

    private fun finishDisconnected() {

        connected = false

        rxCharacteristic = null
        txCharacteristic = null

        synchronized(nxawLock) {
            resetNxawState()
        }

        _connectionState.value =
            NexusEyeConnectionState.DISCONNECTED

        onDisconnected?.invoke()
    }

    // ---------------------------------------------------------
    // UTILITY
    // ---------------------------------------------------------

    private fun safeAddress(
        device: BluetoothDevice
    ): String {

        return try {

            device.address

        } catch (
            _: SecurityException
        ) {

            "unknown"
        }
    }

    private fun readUInt32BigEndian(
        data: ByteArray,
        offset: Int
    ): Long {

        if (
            offset + 4 >
            data.size
        ) {
            return 0L
        }

        return (
                ((data[offset].toLong() and 0xFF) shl 24) or
                        ((data[offset + 1].toLong() and 0xFF) shl 16) or
                        ((data[offset + 2].toLong() and 0xFF) shl 8) or
                        (data[offset + 3].toLong() and 0xFF)
                )
    }

    private fun writeUInt32BigEndian(
        data: ByteArray,
        offset: Int,
        value: Long
    ) {

        data[offset] =
            ((value shr 24) and 0xFF)
                .toByte()

        data[offset + 1] =
            ((value shr 16) and 0xFF)
                .toByte()

        data[offset + 2] =
            ((value shr 8) and 0xFF)
                .toByte()

        data[offset + 3] =
            (value and 0xFF)
                .toByte()
    }
}