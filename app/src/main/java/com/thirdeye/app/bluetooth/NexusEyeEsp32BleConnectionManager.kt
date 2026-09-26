package com.thirdeye.app.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.BluetoothLeScanner
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NexusEyeEsp32BleConnectionState {
    IDLE,
    BLUETOOTH_UNAVAILABLE,
    BLUETOOTH_DISABLED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    SERVICES_DISCOVERED,
    DISCONNECTED,
    ERROR
}

data class NexusEyeEsp32BleDevice(
    val address: String,
    val name: String
)

data class NexusEyeEsp32BleSnapshot(
    val state: NexusEyeEsp32BleConnectionState,
    val message: String,
    val selectedDevice: NexusEyeEsp32BleDevice?,
    val discoveredDevices: List<NexusEyeEsp32BleDevice>,
    val discoveredServices: List<String>,
    val discoveredCharacteristics: List<String>
)

class NexusEyeEsp32BleConnectionManager(
    context: Context
) {

    private val appContext =
        context.applicationContext

    private val bluetoothManager =
        appContext.getSystemService(
            BluetoothManager::class.java
        )

    private val bluetoothAdapter: BluetoothAdapter? =
        bluetoothManager?.adapter

    private val mainHandler =
        Handler(
            Looper.getMainLooper()
        )

    private val _snapshot =
        MutableStateFlow(
            NexusEyeEsp32BleSnapshot(
                state =
                    NexusEyeEsp32BleConnectionState.IDLE,
                message =
                    "ESP32-S3 BLE is ready.",
                selectedDevice =
                    null,
                discoveredDevices =
                    emptyList(),
                discoveredServices =
                    emptyList(),
                discoveredCharacteristics =
                    emptyList()
            )
        )

    val snapshot: StateFlow<NexusEyeEsp32BleSnapshot> =
        _snapshot.asStateFlow()

    private var scanner: BluetoothLeScanner? =
        null

    private var bluetoothGatt: BluetoothGatt? =
        null

    private var selectedBluetoothDevice: BluetoothDevice? =
        null

    private var scanRunning =
        false

    private var desiredConnection =
        false

    private var reconnectAttempt =
        0

    private val scanStopRunnable =
        Runnable {
            stopScan(
                speakStatus = false
            )
        }

    private val reconnectRunnable =
        Runnable {
            if (
                desiredConnection
            ) {
                connectSelectedDevice()
            }
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
                for (
                result in results
                ) {
                    handleScanResult(
                        result
                    )
                }
            }

            override fun onScanFailed(
                errorCode: Int
            ) {
                scanRunning =
                    false

                scanner =
                    null

                updateSnapshot(
                    state =
                        NexusEyeEsp32BleConnectionState.ERROR,
                    message =
                        "ESP32-S3 BLE scan failed. Error code $errorCode."
                )
            }
        }

    private val gattCallback =
        object : BluetoothGattCallback() {

            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int
            ) {
                if (
                    status !=
                    BluetoothGatt.GATT_SUCCESS
                ) {
                    handleGattFailure(
                        gatt = gatt,
                        message =
                            "ESP32-S3 BLE connection failed. GATT status $status."
                    )
                    return
                }

                when (
                    newState
                ) {
                    android.bluetooth.BluetoothProfile.STATE_CONNECTED -> {
                        reconnectAttempt =
                            0

                        val device =
                            safeDeviceInfo(
                                gatt.device
                            )

                        _snapshot.value =
                            _snapshot.value.copy(
                                state =
                                    NexusEyeEsp32BleConnectionState.CONNECTED,
                                message =
                                    "ESP32-S3 connected: ${device.name}.",
                                selectedDevice =
                                    device
                            )

                        tryDiscoverServices(
                            gatt
                        )
                    }

                    android.bluetooth.BluetoothProfile.STATE_DISCONNECTED -> {
                        closeGatt(
                            gatt
                        )

                        updateSnapshot(
                            state =
                                NexusEyeEsp32BleConnectionState.DISCONNECTED,
                            message =
                                "ESP32-S3 disconnected."
                        )

                        if (
                            desiredConnection &&
                            isAutoReconnectEnabled()
                        ) {
                            scheduleReconnect()
                        }
                    }
                }
            }

            override fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int
            ) {
                if (
                    status !=
                    BluetoothGatt.GATT_SUCCESS
                ) {
                    handleGattFailure(
                        gatt = gatt,
                        message =
                            "ESP32-S3 service discovery failed. GATT status $status."
                    )
                    return
                }

                val services =
                    mutableListOf<String>()

                val characteristics =
                    mutableListOf<String>()

                for (
                service in gatt.services.orEmpty()
                ) {
                    services +=
                        service.uuid.toString()

                    for (
                    characteristic in
                    service.characteristics.orEmpty()
                    ) {
                        characteristics +=
                            characteristic.uuid.toString()
                    }
                }

                updateSnapshot(
                    state =
                        NexusEyeEsp32BleConnectionState.SERVICES_DISCOVERED,
                    message =
                        "ESP32-S3 services discovered.",
                    discoveredServices =
                        services.distinct(),
                    discoveredCharacteristics =
                        characteristics.distinct()
                )
            }

            @Suppress("DEPRECATION")
            override fun onMtuChanged(
                gatt: BluetoothGatt,
                mtu: Int,
                status: Int
            ) {
                if (
                    status ==
                    BluetoothGatt.GATT_SUCCESS
                ) {
                    updateSnapshot(
                        message =
                            "ESP32-S3 MTU negotiated at $mtu bytes."
                    )
                }
            }
        }

    fun startScan(
        scanDurationMillis: Long = DEFAULT_SCAN_DURATION_MILLIS
    ): Boolean {

        stopScan(
            speakStatus = false
        )

        if (
            bluetoothAdapter == null
        ) {
            updateSnapshot(
                state =
                    NexusEyeEsp32BleConnectionState.BLUETOOTH_UNAVAILABLE,
                message =
                    "Bluetooth is not available on this device."
            )
            return false
        }

        if (
            !bluetoothAdapter.isEnabled
        ) {
            updateSnapshot(
                state =
                    NexusEyeEsp32BleConnectionState.BLUETOOTH_DISABLED,
                message =
                    "Bluetooth is turned off."
            )
            return false
        }

        if (
            !hasScanPermission()
        ) {
            updateSnapshot(
                state =
                    NexusEyeEsp32BleConnectionState.ERROR,
                message =
                    "Bluetooth scan permission is not granted."
            )
            return false
        }

        scanner =
            bluetoothAdapter.bluetoothLeScanner

        val activeScanner =
            scanner

        if (
            activeScanner == null
        ) {
            updateSnapshot(
                state =
                    NexusEyeEsp32BleConnectionState.ERROR,
                message =
                    "The Bluetooth LE scanner is unavailable."
            )
            return false
        }

        updateSnapshot(
            state =
                NexusEyeEsp32BleConnectionState.SCANNING,
            message =
                "Scanning for ESP32-S3 wearable."
        )

        scanRunning =
            true

        try {
            activeScanner.startScan(
                scanCallback
            )
        } catch (
            exception: SecurityException
        ) {
            scanRunning =
                false

            scanner =
                null

            updateSnapshot(
                state =
                    NexusEyeEsp32BleConnectionState.ERROR,
                message =
                    "Bluetooth scan permission was denied."
            )

            return false
        } catch (
            exception: Exception
        ) {
            scanRunning =
                false

            scanner =
                null

            updateSnapshot(
                state =
                    NexusEyeEsp32BleConnectionState.ERROR,
                message =
                    exception.message
                        ?: "Bluetooth scan could not start."
            )

            return false
        }

        mainHandler.postDelayed(
            scanStopRunnable,
            scanDurationMillis.coerceIn(
                MIN_SCAN_DURATION_MILLIS,
                MAX_SCAN_DURATION_MILLIS
            )
        )

        return true
    }

    fun stopScan(
        speakStatus: Boolean = true
    ) {

        mainHandler.removeCallbacks(
            scanStopRunnable
        )

        if (
            scanRunning
        ) {
            try {
                scanner?.stopScan(
                    scanCallback
                )
            } catch (
                _: Exception
            ) {
            }
        }

        scanRunning =
            false

        scanner =
            null

        if (
            speakStatus &&
            _snapshot.value.state ==
            NexusEyeEsp32BleConnectionState.SCANNING
        ) {
            updateSnapshot(
                state =
                    NexusEyeEsp32BleConnectionState.IDLE,
                message =
                    "ESP32-S3 BLE scan stopped."
            )
        }
    }

    fun connectToDevice(
        device: NexusEyeEsp32BleDevice
    ): Boolean {

        if (
            bluetoothAdapter == null
        ) {
            updateSnapshot(
                state =
                    NexusEyeEsp32BleConnectionState.BLUETOOTH_UNAVAILABLE,
                message =
                    "Bluetooth is not available on this device."
            )
            return false
        }

        if (
            !bluetoothAdapter.isEnabled
        ) {
            updateSnapshot(
                state =
                    NexusEyeEsp32BleConnectionState.BLUETOOTH_DISABLED,
                message =
                    "Bluetooth is turned off."
            )
            return false
        }

        if (
            !hasConnectPermission()
        ) {
            updateSnapshot(
                state =
                    NexusEyeEsp32BleConnectionState.ERROR,
                message =
                    "Bluetooth connection permission is not granted."
            )
            return false
        }

        val remoteDevice =
            try {
                bluetoothAdapter.getRemoteDevice(
                    device.address
                )
            } catch (
                exception: Exception
            ) {
                updateSnapshot(
                    state =
                        NexusEyeEsp32BleConnectionState.ERROR,
                    message =
                        "The selected ESP32-S3 device address is invalid."
                )
                return false
            }

        desiredConnection =
            true

        reconnectAttempt =
            0

        selectedBluetoothDevice =
            remoteDevice

        stopScan(
            speakStatus = false
        )

        closeCurrentGatt()

        updateSnapshot(
            state =
                NexusEyeEsp32BleConnectionState.CONNECTING,
            message =
                "Connecting to ${device.name}."
        )

        return connectGatt(
            remoteDevice
        )
    }

    fun connectToPreferredDevice(): Boolean {

        val preferredName =
            NexusEyeEsp32SettingsManager(
                appContext
            )
                .getSettings()
                .preferredDeviceName
                .trim()

        if (
            preferredName.isBlank()
        ) {
            updateSnapshot(
                state =
                    NexusEyeEsp32BleConnectionState.ERROR,
                message =
                    "No preferred ESP32-S3 device name is configured."
            )
            return false
        }

        val matchingDevice =
            _snapshot.value.discoveredDevices
                .firstOrNull {
                    it.name.equals(
                        preferredName,
                        ignoreCase = true
                    )
                }

        if (
            matchingDevice != null
        ) {
            return connectToDevice(
                matchingDevice
            )
        }

        updateSnapshot(
            state =
                NexusEyeEsp32BleConnectionState.ERROR,
            message =
                "The preferred ESP32-S3 device was not found. Start a BLE scan first."
        )

        return false
    }

    fun disconnect() {

        desiredConnection =
            false

        mainHandler.removeCallbacks(
            reconnectRunnable
        )

        try {
            bluetoothGatt?.disconnect()
        } catch (
            _: Exception
        ) {
        }

        closeCurrentGatt()

        updateSnapshot(
            state =
                NexusEyeEsp32BleConnectionState.DISCONNECTED,
            message =
                "ESP32-S3 disconnected."
        )
    }

    fun shutdown() {

        desiredConnection =
            false

        mainHandler.removeCallbacks(
            reconnectRunnable
        )

        stopScan(
            speakStatus = false
        )

        closeCurrentGatt()
    }

    fun isConnected(): Boolean {
        return _snapshot.value.state ==
                NexusEyeEsp32BleConnectionState.CONNECTED ||
                _snapshot.value.state ==
                NexusEyeEsp32BleConnectionState.SERVICES_DISCOVERED
    }

    fun getGattServices(): List<BluetoothGattService> {

        if (
            !hasConnectPermission()
        ) {
            return emptyList()
        }

        return try {
            bluetoothGatt
                ?.services
                .orEmpty()
        } catch (
            _: Exception
        ) {
            emptyList()
        }
    }

    fun findCharacteristic(
        uuidText: String
    ): BluetoothGattCharacteristic? {

        val expectedUuid =
            try {
                java.util.UUID.fromString(
                    uuidText.trim()
                )
            } catch (
                _: Exception
            ) {
                return null
            }

        for (
        service in
        getGattServices()
        ) {
            for (
            characteristic in
            service.characteristics.orEmpty()
            ) {
                if (
                    characteristic.uuid ==
                    expectedUuid
                ) {
                    return characteristic
                }
            }
        }

        return null
    }

    private fun connectSelectedDevice(): Boolean {

        val device =
            selectedBluetoothDevice
                ?: return false

        updateSnapshot(
            state =
                NexusEyeEsp32BleConnectionState.CONNECTING,
            message =
                "Reconnecting to ESP32-S3."
        )

        return connectGatt(
            device
        )
    }

    private fun connectGatt(
        device: BluetoothDevice
    ): Boolean {

        if (
            !hasConnectPermission()
        ) {
            updateSnapshot(
                state =
                    NexusEyeEsp32BleConnectionState.ERROR,
                message =
                    "Bluetooth connection permission is not granted."
            )
            return false
        }

        return try {
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
                    @Suppress("DEPRECATION")
                    device.connectGatt(
                        appContext,
                        false,
                        gattCallback
                    )
                }

            bluetoothGatt != null
        } catch (
            exception: SecurityException
        ) {
            updateSnapshot(
                state =
                    NexusEyeEsp32BleConnectionState.ERROR,
                message =
                    "Bluetooth connection permission was denied."
            )
            false
        } catch (
            exception: Exception
        ) {
            updateSnapshot(
                state =
                    NexusEyeEsp32BleConnectionState.ERROR,
                message =
                    exception.message
                        ?: "ESP32-S3 connection could not start."
            )
            false
        }
    }

    private fun tryDiscoverServices(
        gatt: BluetoothGatt
    ) {

        if (
            !hasConnectPermission()
        ) {
            updateSnapshot(
                state =
                    NexusEyeEsp32BleConnectionState.ERROR,
                message =
                    "Bluetooth connection permission is not granted."
            )
            return
        }

        try {
            gatt.requestConnectionPriority(
                BluetoothGatt.CONNECTION_PRIORITY_HIGH
            )
        } catch (
            _: Exception
        ) {
        }

        try {
            gatt.discoverServices()
        } catch (
            exception: Exception
        ) {
            handleGattFailure(
                gatt = gatt,
                message =
                    exception.message
                        ?: "ESP32-S3 service discovery could not start."
            )
        }
    }

    private fun handleGattFailure(
        gatt: BluetoothGatt,
        message: String
    ) {

        closeGatt(
            gatt
        )

        updateSnapshot(
            state =
                NexusEyeEsp32BleConnectionState.ERROR,
            message =
                message
        )

        if (
            desiredConnection &&
            isAutoReconnectEnabled()
        ) {
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {

        mainHandler.removeCallbacks(
            reconnectRunnable
        )

        reconnectAttempt =
            (
                    reconnectAttempt + 1
                    )
                .coerceAtMost(
                    MAX_RECONNECT_ATTEMPTS
                )

        val delay =
            reconnectAttempt *
                    RECONNECT_BASE_DELAY_MILLIS

        mainHandler.postDelayed(
            reconnectRunnable,
            delay
        )
    }

    private fun handleScanResult(
        result: ScanResult
    ) {

        val device =
            result.device

        val name =
            try {
                result.scanRecord
                    ?.deviceName
                    ?.trim()
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?: device.name
                        ?.trim()
                        ?.takeIf {
                            it.isNotBlank()
                        }
                    ?: "Unnamed BLE device"
            } catch (
                _: SecurityException
            ) {
                "Unnamed BLE device"
            } catch (
                _: Exception
            ) {
                "Unnamed BLE device"
            }

        val item =
            NexusEyeEsp32BleDevice(
                address =
                    device.address,
                name =
                    name
            )

        val currentDevices =
            _snapshot.value.discoveredDevices

        if (
            currentDevices.none {
                it.address.equals(
                    item.address,
                    ignoreCase = true
                )
            }
        ) {
            val updated =
                (
                        currentDevices +
                                item
                        )
                    .sortedBy {
                        it.name.lowercase()
                    }

            _snapshot.value =
                _snapshot.value.copy(
                    discoveredDevices =
                        updated
                )
        }

        val preferredName =
            try {
                NexusEyeEsp32SettingsManager(
                    appContext
                )
                    .getSettings()
                    .preferredDeviceName
                    .trim()
            } catch (
                _: Exception
            ) {
                ""
            }

        if (
            preferredName.isNotBlank() &&
            name.equals(
                preferredName,
                ignoreCase = true
            ) &&
            selectedBluetoothDevice == null &&
            desiredConnection
        ) {
            selectedBluetoothDevice =
                device

            stopScan(
                speakStatus = false
            )

            connectToDevice(
                item
            )
        }
    }

    private fun safeDeviceInfo(
        device: BluetoothDevice
    ): NexusEyeEsp32BleDevice {

        val name =
            try {
                device.name
                    ?.trim()
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?: "ESP32-S3"
            } catch (
                _: Exception
            ) {
                "ESP32-S3"
            }

        return NexusEyeEsp32BleDevice(
            address =
                device.address,
            name =
                name
        )
    }

    private fun updateSnapshot(
        state:
        NexusEyeEsp32BleConnectionState? = null,
        message: String? = null,
        discoveredServices: List<String>? = null,
        discoveredCharacteristics: List<String>? = null
    ) {

        val current =
            _snapshot.value

        _snapshot.value =
            current.copy(
                state =
                    state
                        ?: current.state,
                message =
                    message
                        ?: current.message,
                discoveredServices =
                    discoveredServices
                        ?: current.discoveredServices,
                discoveredCharacteristics =
                    discoveredCharacteristics
                        ?: current.discoveredCharacteristics
            )
    }

    private fun closeCurrentGatt() {

        val gatt =
            bluetoothGatt
                ?: return

        closeGatt(
            gatt
        )

        bluetoothGatt =
            null
    }

    private fun closeGatt(
        gatt: BluetoothGatt
    ) {

        try {
            gatt.disconnect()
        } catch (
            _: Exception
        ) {
        }

        try {
            gatt.close()
        } catch (
            _: Exception
        ) {
        }

        if (
            bluetoothGatt ===
            gatt
        ) {
            bluetoothGatt =
                null
        }
    }

    private fun hasScanPermission(): Boolean {

        return if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.S
        ) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.BLUETOOTH_SCAN
            ) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasConnectPermission(): Boolean {

        return if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.S
        ) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.BLUETOOTH_CONNECT
            ) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun isAutoReconnectEnabled(): Boolean {

        return try {
            NexusEyeEsp32SettingsManager(
                appContext
            )
                .getSettings()
                .autoReconnect
        } catch (
            _: Exception
        ) {
            false
        }
    }

    companion object {

        private const val DEFAULT_SCAN_DURATION_MILLIS =
            10_000L

        private const val MIN_SCAN_DURATION_MILLIS =
            2_000L

        private const val MAX_SCAN_DURATION_MILLIS =
            30_000L

        private const val RECONNECT_BASE_DELAY_MILLIS =
            1_500L

        private const val MAX_RECONNECT_ATTEMPTS =
            5
    }
}
