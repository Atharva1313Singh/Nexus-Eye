package com.thirdeye.app.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thirdeye.app.bluetooth.NexusEyeBleDevice
import com.thirdeye.app.bluetooth.NexusEyeBleManager
import com.thirdeye.app.bluetooth.NexusEyeBleProtocol
import com.thirdeye.app.bluetooth.NexusEyeConnectionState
import com.thirdeye.app.intelligence.TaskRouter
import com.thirdeye.app.language.NexusEyeLanguage
import com.thirdeye.app.language.AppTextKey
import com.thirdeye.app.language.nexusText
import kotlinx.coroutines.launch

@Composable
fun CommunicationScreen(
    bleManager: NexusEyeBleManager,
    taskRouter: TaskRouter,
    speechLanguage: NexusEyeLanguage,
    onBack: () -> Unit
) {
    val devices by bleManager.devices.collectAsState()

    val connectionState by bleManager.connectionState
        .collectAsState()

    val lastMessage by bleManager.lastReceivedMessage
        .collectAsState()

    val errorMessage by bleManager.errorMessage
        .collectAsState()

    val scope = rememberCoroutineScope()

    var permissionRequestFinished by remember {
        mutableStateOf(false)
    }

    var processingQuestion by remember {
        mutableStateOf(false)
    }

    var processedMessage by remember {
        mutableStateOf("")
    }

    var processingStatus by remember {
        mutableStateOf("")
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            permissionRequestFinished = true
        }

    val bluetoothEnableLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
        }

    fun requiredPermissions(): Array<String> {

        return if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.S
        ) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    LaunchedEffect(
        permissionRequestFinished
    ) {

        if (permissionRequestFinished) {

            permissionRequestFinished = false

            if (
                bleManager.hasRequiredPermissions()
            ) {

                if (
                    !bleManager.isBluetoothEnabled()
                ) {

                    bluetoothEnableLauncher.launch(
                        Intent(
                            BluetoothAdapter.ACTION_REQUEST_ENABLE
                        )
                    )
                }
            }
        }
    }

    LaunchedEffect(
        lastMessage
    ) {

        val message =
            lastMessage?.trim().orEmpty()

        if (
            message.isBlank()
        ) {
            return@LaunchedEffect
        }

        if (
            message == processedMessage
        ) {
            return@LaunchedEffect
        }

        if (
            message.startsWith(
                "QUESTION:",
                ignoreCase = true
            )
        ) {

            val question =
                message
                    .substringAfter(":")
                    .trim()

            if (
                question.isBlank()
            ) {
                processedMessage = message
                return@LaunchedEffect
            }

            processedMessage = message

            processingQuestion = true

            processingStatus =
                if (
                    speechLanguage.id == "hi"
                ) {
                    "ESP32 से प्रश्न प्राप्त हुआ। उत्तर तैयार किया जा रहा है।"
                } else {
                    "Question received from ESP32. Preparing the answer."
                }

            scope.launch {

                try {

                    val result =
                        taskRouter.process(
                            query = question,
                            speechLanguageId =
                                speechLanguage.id
                        )

                    val answer =
                        result.answer
                            .replace(
                                "\n",
                                " "
                            )
                            .trim()

                    val command =
                        NexusEyeBleProtocol.command(
                            name =
                                NexusEyeBleProtocol
                                    .Commands
                                    .ANSWER,
                            value = answer
                        )

                    bleManager.sendCommand(
                        command
                    )

                    processingStatus =
                        if (
                            speechLanguage.id == "hi"
                        ) {
                            "उत्तर ESP32 को भेज दिया गया।"
                        } else {
                            "Answer sent to ESP32."
                        }

                } catch (
                    _: Exception
                ) {

                    processingStatus =
                        if (
                            speechLanguage.id == "hi"
                        ) {
                            "प्रश्न प्रोसेस नहीं किया जा सका।"
                        } else {
                            "The ESP32 question could not be processed."
                        }

                } finally {

                    processingQuestion = false
                }
            }
        }
    }

    fun prepareBluetooth(
        onReady: () -> Unit
    ) {

        if (
            !bleManager.hasBluetooth()
        ) {
            return
        }

        if (
            !bleManager.hasRequiredPermissions()
        ) {

            permissionLauncher.launch(
                requiredPermissions()
            )

            return
        }

        if (
            !bleManager.isBluetoothEnabled()
        ) {

            bluetoothEnableLauncher.launch(
                Intent(
                    BluetoothAdapter.ACTION_REQUEST_ENABLE
                )
            )

            return
        }

        onReady()
    }

    Scaffold { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement =
                Arrangement.spacedBy(14.dp)
        ) {

            Text(
                text =
                    nexusText(
                        AppTextKey.BLE_COMMUNICATION
                    ),
                style =
                    MaterialTheme.typography.headlineLarge
            )

            Text(
                text =
                    buildConnectionText(
                        connectionState
                    ),
                style =
                    MaterialTheme.typography.titleMedium
            )

            if (
                processingStatus.isNotBlank()
            ) {

                Card(
                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    Text(
                        text =
                            processingStatus,
                        modifier =
                            Modifier.padding(16.dp)
                    )
                }
            }

            if (
                processingQuestion
            ) {

                Card(
                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    Text(
                        text =
                            if (
                                speechLanguage.id == "hi"
                            ) {
                                "ESP32 से प्रश्न प्राप्त हुआ।"
                            } else {
                                "Question received from ESP32."
                            },
                        modifier =
                            Modifier.padding(16.dp)
                    )
                }
            }

            when {

                !bleManager.hasBluetooth() -> {

                    Card(
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {

                        Text(
                            text =
                                nexusText(
                                    AppTextKey.BLUETOOTH_UNAVAILABLE
                                ),
                            modifier =
                                Modifier.padding(16.dp)
                        )
                    }
                }

                else -> {

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(10.dp)
                    ) {

                        Button(
                            onClick = {

                                prepareBluetooth {
                                    bleManager.startScan()
                                }
                            },
                            enabled =
                                connectionState !=
                                        NexusEyeConnectionState.SCANNING &&
                                        connectionState !=
                                        NexusEyeConnectionState.CONNECTING,
                            modifier =
                                Modifier.weight(1f)
                        ) {

                            Text(
                                text =
                                    nexusText(
                                        AppTextKey.SCAN_FOR_DEVICES
                                    )
                            )
                        }

                        OutlinedButton(
                            onClick = {

                                bleManager.stopScan()
                            },
                            enabled =
                                connectionState ==
                                        NexusEyeConnectionState.SCANNING,
                            modifier =
                                Modifier.weight(1f)
                        ) {

                            Text(
                                text =
                                    nexusText(
                                        AppTextKey.STOP_SCAN
                                    )
                            )
                        }
                    }

                    if (
                        connectionState ==
                        NexusEyeConnectionState.READY
                    ) {

                        Row(
                            modifier =
                                Modifier.fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement.spacedBy(10.dp)
                        ) {

                            Button(
                                onClick = {

                                    bleManager.sendCommand(
                                        NexusEyeBleProtocol
                                            .Commands
                                            .PING
                                    )
                                },
                                modifier =
                                    Modifier.weight(1f)
                            ) {

                                Text(
                                    text =
                                        nexusText(
                                            AppTextKey.SEND_PING
                                        )
                                )
                            }

                            Button(
                                onClick = {

                                    bleManager.sendCommand(
                                        NexusEyeBleProtocol
                                            .Commands
                                            .STATUS
                                    )
                                },
                                modifier =
                                    Modifier.weight(1f)
                            ) {

                                Text(
                                    text =
                                        nexusText(
                                            AppTextKey.SEND_STATUS
                                        )
                                )
                            }
                        }

                        Button(
                            onClick = {

                                bleManager.disconnect()
                            },
                            modifier =
                                Modifier.fillMaxWidth()
                        ) {

                            Text(
                                text =
                                    nexusText(
                                        AppTextKey.DISCONNECT
                                    )
                            )
                        }
                    }

                    Text(
                        text =
                            nexusText(
                                AppTextKey.AVAILABLE_DEVICES
                            ),
                        style =
                            MaterialTheme.typography.titleMedium
                    )

                    if (
                        devices.isEmpty()
                    ) {

                        Text(
                            text =
                                nexusText(
                                    AppTextKey.NO_DEVICES_FOUND
                                ),
                            style =
                                MaterialTheme.typography.bodyLarge
                        )

                    } else {

                        LazyColumn(
                            modifier =
                                Modifier.weight(1f),
                            verticalArrangement =
                                Arrangement.spacedBy(10.dp)
                        ) {

                            items(
                                items = devices,
                                key = {
                                    it.address
                                }
                            ) { device ->

                                BleDeviceCard(
                                    device = device,
                                    connectionState =
                                        connectionState,
                                    onConnect = {

                                        prepareBluetooth {

                                            bleManager.connect(
                                                device
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }

                    if (
                        !lastMessage.isNullOrEmpty()
                    ) {

                        Card(
                            modifier =
                                Modifier.fillMaxWidth()
                        ) {

                            Column(
                                modifier =
                                    Modifier.padding(16.dp)
                            ) {

                                Text(
                                    text =
                                        nexusText(
                                            AppTextKey.LAST_RECEIVED
                                        ),
                                    style =
                                        MaterialTheme
                                            .typography
                                            .titleMedium
                                )

                                Spacer(
                                    modifier =
                                        Modifier.height(6.dp)
                                )

                                Text(
                                    text =
                                        lastMessage.orEmpty(),
                                    style =
                                        MaterialTheme
                                            .typography
                                            .bodyLarge
                                )
                            }
                        }
                    }

                    if (
                        errorMessage != null
                    ) {

                        Card(
                            modifier =
                                Modifier.fillMaxWidth()
                        ) {

                            Text(
                                text =
                                    "${nexusText(
                                        AppTextKey.CONNECTION_ERROR
                                    )}: $errorMessage",
                                modifier =
                                    Modifier.padding(16.dp),
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .error
                            )
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = onBack,
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    text =
                        nexusText(
                            AppTextKey.BACK
                        )
                )
            }
        }
    }
}

@Composable
private fun BleDeviceCard(
    device: NexusEyeBleDevice,
    connectionState: NexusEyeConnectionState,
    onConnect: () -> Unit
) {

    Card(
        modifier =
            Modifier.fillMaxWidth()
    ) {

        Column(
            modifier =
                Modifier.padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(6.dp)
        ) {

            Text(
                text =
                    device.name,
                style =
                    MaterialTheme
                        .typography
                        .titleMedium
            )

            Text(
                text =
                    device.address,
                style =
                    MaterialTheme
                        .typography
                        .bodyMedium
            )

            Text(
                text =
                    "RSSI: ${device.rssi} dBm",
                style =
                    MaterialTheme
                        .typography
                        .bodyMedium
            )

            Button(
                onClick =
                    onConnect,
                enabled =
                    connectionState !=
                            NexusEyeConnectionState.CONNECTING,
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    text =
                        nexusText(
                            AppTextKey.CONNECT
                        )
                )
            }
        }
    }
}

@Composable
private fun buildConnectionText(
    state: NexusEyeConnectionState
): String {

    return when (state) {

        NexusEyeConnectionState.DISCONNECTED ->
            nexusText(
                AppTextKey.DISCONNECTED
            )

        NexusEyeConnectionState.SCANNING ->
            nexusText(
                AppTextKey.SCANNING
            )

        NexusEyeConnectionState.CONNECTING ->
            nexusText(
                AppTextKey.CONNECTING
            )

        NexusEyeConnectionState.CONNECTED ->
            nexusText(
                AppTextKey.CONNECTED
            )

        NexusEyeConnectionState.READY ->
            nexusText(
                AppTextKey.READY
            )

        NexusEyeConnectionState.DISCONNECTING ->
            nexusText(
                AppTextKey.DISCONNECTING
            )

        NexusEyeConnectionState.ERROR ->
            nexusText(
                AppTextKey.ERROR
            )
    }
}