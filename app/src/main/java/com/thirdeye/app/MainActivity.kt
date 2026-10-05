package com.thirdeye.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.thirdeye.app.audio.NexusEyeTtsManager
import com.thirdeye.app.bluetooth.NexusEyeBleManager
import com.thirdeye.app.environment.NexusEyeHomeLocationManager
import com.thirdeye.app.intelligence.TaskRouter
import com.thirdeye.app.language.LanguageManager
import com.thirdeye.app.language.LanguageState
import com.thirdeye.app.language.NexusEyeLanguage
import com.thirdeye.app.language.NexusEyeLanguages
import com.thirdeye.app.navigation.NexusEyeNavigationManager
import com.thirdeye.app.navigation.NexusEyeNavigationSettings
import com.thirdeye.app.navigation.NexusEyeHomeNavigationTarget
import com.thirdeye.app.notifications.NexusEyeNotificationAccessManager
import com.thirdeye.app.security.NexusEyeApiCredentialStore
import com.thirdeye.app.setup.SetupRole
import com.thirdeye.app.ui.CommunicationScreen
import com.thirdeye.app.ui.GeminiApiKeySetupScreen
import com.thirdeye.app.ui.HomeScreen
import com.thirdeye.app.ui.IntelligenceScreen
import com.thirdeye.app.ui.NavigationScreen
import com.thirdeye.app.ui.NotificationAccessSetupScreen
import com.thirdeye.app.ui.OnlineNavigationApiSetupScreen
import com.thirdeye.app.ui.SettingsScreen
import com.thirdeye.app.ui.SetupRoleScreen
import com.thirdeye.app.ui.VisionScreen
import com.thirdeye.app.ui.VoiceScreen
import com.thirdeye.app.ui.WeatherScreen
import com.thirdeye.app.voice.NexusEyeAssistantManager
import kotlinx.coroutines.launch

class MainActivity :
    ComponentActivity() {

    private lateinit var bleManager:
            NexusEyeBleManager

    private lateinit var taskRouter:
            TaskRouter

    private lateinit var ttsManager:
            NexusEyeTtsManager

    private lateinit var languageManager:
            LanguageManager

    private lateinit var navigationManager:
            NexusEyeNavigationManager

    private lateinit var navigationSettings:
            NexusEyeNavigationSettings

    private lateinit var homeLocationManager:
            NexusEyeHomeLocationManager

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        bleManager =
            NexusEyeBleManager(
                this
            )

        NexusEyeRuntime.registerBleManager(
            bleManager
        )

        taskRouter =
            TaskRouter(
                this
            )

        ttsManager =
            NexusEyeTtsManager(
                this
            )

        languageManager =
            LanguageManager(
                this
            )

        navigationManager =
            NexusEyeNavigationManager(
                this
            )

        navigationSettings =
            NexusEyeNavigationSettings(
                this
            )

        homeLocationManager =
            NexusEyeHomeLocationManager(
                this
            )

        setContent {

            NexusEyeRoot()
        }
    }

    @Composable
    private fun NexusEyeRoot() {

        val languageState by
        languageManager
            .languageState
            .collectAsState(
                initial =
                    LanguageState(
                        appLanguage =
                            NexusEyeLanguages.English,
                        speechLanguage =
                            NexusEyeLanguages.English
                    )
            )

        val scope =
            rememberCoroutineScope()

        val notificationAccessManager =
            remember {
                NexusEyeNotificationAccessManager(
                    this@MainActivity
                )
            }

        val apiCredentialStore =
            remember {
                NexusEyeApiCredentialStore(
                    this@MainActivity
                )
            }

        var currentScreen by
        remember {

            mutableStateOf(
                AppScreen.HOME
            )
        }

        var role by
        remember {

            mutableStateOf(
                loadSavedRole()
            )
        }

        var setupComplete by
        remember {

            mutableStateOf(
                isRoleConfigured()
            )
        }

        var notificationAccessComplete by
        remember {

            mutableStateOf(
                notificationAccessManager
                    .isAccessGranted()
            )
        }

        var apiSetupComplete by
        remember {

            mutableStateOf(
                apiCredentialStore
                    .hasGeminiApiKey()
            )
        }

        var onlineNavigationApiSetupComplete by
        remember {
            mutableStateOf(
                apiCredentialStore.hasOnlineNavigationApiKey()
            )
        }

        var pendingNavigationDestination by
        remember {

            mutableStateOf<String?>(
                null
            )
        }

        var assistantPrompted by remember { mutableStateOf(false) }
        var assistantSetupAttempted by remember { mutableStateOf(false) }

        val assistantRoleLauncher =
            rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) {
                // The role decision is made by Android. Whether accepted or
                // declined, continue with the normal app setup flow.
                assistantSetupAttempted = true
            }

        val voicePermissionsLauncher =
            rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                if (permissions[Manifest.permission.RECORD_AUDIO] == true ||
                    ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    startWakeWordService()
                }

                if (role == SetupRole.BLIND_USER &&
                    !NexusEyeAssistantManager.isAccessibilityEnabled(this@MainActivity)
                ) {
                    try {
                        NexusEyeAssistantManager.openAccessibilitySettings(this@MainActivity)
                    } catch (_: Exception) {
                    }
                }
            }

        val wakeWordMicrophonePermissionLauncher =
            rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted) {
                    startWakeWordService()
                }
            }

        LaunchedEffect(setupComplete, role) {
            if (setupComplete &&
                role == SetupRole.BLIND_USER &&
                !assistantPrompted
            ) {
                assistantPrompted = true

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val roleIntent =
                        NexusEyeAssistantManager.createAssistantRoleRequest(
                            this@MainActivity
                        )
                    if (roleIntent != null) {
                        try {
                            assistantRoleLauncher.launch(roleIntent)
                        } catch (_: Exception) {
                            // The user can still select Nexus-Eye manually in
                            // Android's Assistant settings.
                            assistantSetupAttempted = true
                        }
                    } else {
                        assistantSetupAttempted = true
                        if (!NexusEyeAssistantManager.isAccessibilityEnabled(this@MainActivity)) {
                            try {
                                NexusEyeAssistantManager.openAccessibilitySettings(this@MainActivity)
                            } catch (_: Exception) {
                            }
                        }
                    }
                } else {
                    assistantSetupAttempted = true
                    if (!NexusEyeAssistantManager.isAccessibilityEnabled(this@MainActivity)) {
                        try {
                            NexusEyeAssistantManager.openAccessibilitySettings(this@MainActivity)
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        }

        LaunchedEffect(setupComplete, role, assistantSetupAttempted) {
            if (setupComplete && role == SetupRole.BLIND_USER && assistantSetupAttempted) {
                val permissions = buildList {
                    add(Manifest.permission.RECORD_AUDIO)
                    add(Manifest.permission.READ_CONTACTS)
                    add(Manifest.permission.CALL_PHONE)
                    add(Manifest.permission.ACCESS_FINE_LOCATION)
                    add(Manifest.permission.ACCESS_COARSE_LOCATION)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        add(Manifest.permission.BLUETOOTH_SCAN)
                        add(Manifest.permission.BLUETOOTH_CONNECT)
                        add(Manifest.permission.BLUETOOTH_ADVERTISE)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }.distinct().filter { permission ->
                    ContextCompat.checkSelfPermission(
                        this@MainActivity, permission
                    ) != PackageManager.PERMISSION_GRANTED
                }

                if (permissions.isNotEmpty()) {
                    voicePermissionsLauncher.launch(permissions.toTypedArray())
                }
            }
        }

        LaunchedEffect(
            setupComplete,
            notificationAccessComplete,
            role
        ) {
            if (setupComplete && notificationAccessComplete && role != SetupRole.BLIND_USER) {
                val microphoneGranted =
                    ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED

                if (microphoneGranted) {
                    startWakeWordService()
                } else {
                    wakeWordMicrophonePermissionLauncher.launch(
                        Manifest.permission.RECORD_AUDIO
                    )
                }
            }
        }

        val locationPermissionLauncher =
            rememberLauncherForActivityResult(
                contract =
                    ActivityResultContracts
                        .RequestMultiplePermissions()
            ) { permissions ->

                val fineGranted =
                    permissions[
                        Manifest.permission
                            .ACCESS_FINE_LOCATION
                    ] == true

                val coarseGranted =
                    permissions[
                        Manifest.permission
                            .ACCESS_COARSE_LOCATION
                    ] == true

                val destination =
                    pendingNavigationDestination

                pendingNavigationDestination =
                    null

                if (
                    destination != null &&
                    (
                            fineGranted ||
                                    coarseGranted
                            )
                ) {

                    startNavigation(
                        destination
                    )
                }
            }

        LaunchedEffect(
            languageState.speechLanguage
        ) {

            navigationManager
                .setSpeechLanguage(
                    languageState
                        .speechLanguage
                )
        }

        DisposableEffect(
            navigationManager
        ) {

            onDispose {

                navigationManager
                    .shutdown()
            }
        }

        fun requestNavigation(
            destination: String
        ) {

            val fineGranted =
                ContextCompat
                    .checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission
                            .ACCESS_FINE_LOCATION
                    ) ==
                        PackageManager
                            .PERMISSION_GRANTED

            val coarseGranted =
                ContextCompat
                    .checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission
                            .ACCESS_COARSE_LOCATION
                    ) ==
                        PackageManager
                            .PERMISSION_GRANTED

            if (
                fineGranted ||
                coarseGranted
            ) {

                startNavigation(
                    destination
                )

            } else {

                pendingNavigationDestination =
                    destination

                locationPermissionLauncher
                    .launch(
                        arrayOf(
                            Manifest.permission
                                .ACCESS_FINE_LOCATION,

                            Manifest.permission
                                .ACCESS_COARSE_LOCATION
                        )
                    )
            }
        }

        fun startHomeNavigation(
            home: NexusEyeHomeNavigationTarget
        ) {

            navigationManager
                .navigateToCoordinates(
                    latitude =
                        home.latitude,
                    longitude =
                        home.longitude,
                    displayName =
                        home.address,
                    strideMeters =
                        navigationSettings
                            .getStrideMeters()
                )
        }

        Surface(
            modifier =
                Modifier.fillMaxSize(),

            color =
                MaterialTheme
                    .colorScheme
                    .background
        ) {

            when {

                !setupComplete -> {

                    SetupRoleScreen(

                        onRoleSelected = {
                                selectedRole ->

                            role =
                                selectedRole

                            saveRole(
                                selectedRole
                            )

                            setupComplete =
                                true

                            currentScreen =
                                AppScreen.HOME
                        }
                    )
                }

                !notificationAccessComplete -> {

                    NotificationAccessSetupScreen(

                        onAccessGranted = {

                            notificationAccessComplete =
                                true

                            currentScreen =
                                AppScreen.HOME
                        }
                    )
                }

                !apiSetupComplete -> {

                    GeminiApiKeySetupScreen(
                        context =
                            this@MainActivity,
                        onBack = {

                            apiSetupComplete =
                                apiCredentialStore
                                    .hasGeminiApiKey()
                        }
                    )
                }

                !onlineNavigationApiSetupComplete -> {

                    OnlineNavigationApiSetupScreen(
                        onComplete = {
                            onlineNavigationApiSetupComplete =
                                apiCredentialStore.hasOnlineNavigationApiKey()
                        }
                    )
                }

                else -> {

                    when (
                        currentScreen
                    ) {

                        AppScreen.HOME -> {

                            HomeScreen(

                                onCommunicationClick = {

                                    currentScreen =
                                        AppScreen
                                            .COMMUNICATION
                                },

                                onVoiceClick = {

                                    currentScreen =
                                        AppScreen
                                            .VOICE
                                },

                                onIntelligenceClick = {

                                    currentScreen =
                                        AppScreen
                                            .INTELLIGENCE
                                },

                                onVisionClick = {

                                    currentScreen =
                                        AppScreen
                                            .VISION
                                },

                                onNavigationClick = {

                                    currentScreen =
                                        AppScreen
                                            .NAVIGATION
                                },

                                onWeatherClick = {

                                    currentScreen =
                                        AppScreen
                                            .WEATHER
                                },

                                onSettingsClick = {

                                    currentScreen =
                                        AppScreen
                                            .SETTINGS
                                }
                            )
                        }

                        AppScreen.NAVIGATION -> {

                            val savedHome =
                                homeLocationManager
                                    .getSavedHomeLocation()

                            NavigationScreen(

                                savedHomeAddress =
                                    savedHome?.address,

                                navigationManager =
                                    navigationManager,

                                onStartNavigation = {
                                        destination ->

                                    requestNavigation(
                                        destination
                                    )
                                },

                                onGoHome = {

                                    val home =
                                        homeLocationManager
                                            .getSavedHomeLocation()

                                    if (
                                        home == null
                                    ) {

                                        false

                                    } else {

                                        startHomeNavigation(
                                            NexusEyeHomeNavigationTarget(
                                                latitude =
                                                    home.latitude,
                                                longitude =
                                                    home.longitude,
                                                address =
                                                    home.address
                                            )
                                        )

                                        true
                                    }
                                },

                                onBack = {

                                    currentScreen =
                                        AppScreen.HOME
                                }
                            )
                        }

                        AppScreen.VISION -> {

                            VisionScreen(

                                context =
                                    this@MainActivity,

                                onBack = {

                                    currentScreen =
                                        AppScreen.HOME
                                }
                            )
                        }

                        AppScreen.COMMUNICATION -> {

                            CommunicationScreen(

                                bleManager =
                                    bleManager,

                                taskRouter =
                                    taskRouter,

                                speechLanguage =
                                    languageState
                                        .speechLanguage,

                                onBack = {

                                    currentScreen =
                                        AppScreen.HOME
                                }
                            )
                        }

                        AppScreen.VOICE -> {

                            VoiceScreen(

                                context =
                                    this@MainActivity,

                                bleManager =
                                    bleManager,

                                taskRouter =
                                    taskRouter,

                                speechLanguage =
                                    languageState
                                        .speechLanguage,

                                onNavigateHome = {
                                        home ->

                                    startHomeNavigation(
                                        home
                                    )
                                },

                                onBack = {

                                    currentScreen =
                                        AppScreen.HOME
                                }
                            )
                        }

                        AppScreen.INTELLIGENCE -> {

                            IntelligenceScreen(

                                context =
                                    this@MainActivity,

                                taskRouter =
                                    taskRouter,

                                bleManager =
                                    bleManager,

                                speechLanguage =
                                    languageState
                                        .speechLanguage,

                                onBack = {

                                    currentScreen =
                                        AppScreen.HOME
                                }
                            )
                        }

                        AppScreen.WEATHER -> {

                            WeatherScreen(

                                context =
                                    this@MainActivity,

                                bleManager =
                                    bleManager,

                                ttsManager =
                                    ttsManager,

                                speechLanguage =
                                    languageState
                                        .speechLanguage,

                                onBack = {

                                    currentScreen =
                                        AppScreen.HOME
                                }
                            )
                        }

                        AppScreen.SETTINGS -> {

                            SettingsScreen(

                                languageState =
                                    languageState,

                                role =
                                    role,

                                onSaveLanguages = {
                                        appLanguage:
                                        NexusEyeLanguage,
                                        speechLanguage:
                                        NexusEyeLanguage ->

                                    scope.launch {

                                        languageManager
                                            .saveLanguages(
                                                appLanguageId =
                                                    appLanguage.id,

                                                speechLanguageId =
                                                    speechLanguage.id
                                            )
                                    }
                                },

                                onResetSetup = {

                                    resetRole()

                                    role =
                                        null

                                    setupComplete =
                                        false

                                    notificationAccessComplete =
                                        false

                                    currentScreen =
                                        AppScreen.HOME
                                },

                                onBack = {

                                    currentScreen =
                                        AppScreen.HOME
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun startWakeWordService() {
        // When Nexus-Eye is the system assistant, VoiceInteractionService owns
        // hotword microphone capture. Do not start a second microphone FGS.
        if (NexusEyeAssistantManager.isAssistant(this)) {
            return
        }

        try {
            val intent =
                android.content.Intent(
                    this,
                    com.thirdeye.app.voice.NexusEyeWakeWordService::class.java
                )

            if (android.os.Build.VERSION.SDK_INT >=
                android.os.Build.VERSION_CODES.O
            ) {
                androidx.core.content.ContextCompat.startForegroundService(
                    this,
                    intent
                )
            } else {
                startService(intent)
            }
        } catch (_: Exception) {
        }
    }

    private fun startNavigation(
        destination: String
    ) {

        navigationManager
            .navigateTo(
                destinationText =
                    destination,

                strideMeters =
                    navigationSettings.getStrideMeters()
            )
    }

    private fun isRoleConfigured():
            Boolean {

        return getSharedPreferences(
            SETUP_PREFS,
            MODE_PRIVATE
        )
            .getBoolean(
                ROLE_CONFIGURED,
                false
            )
    }

    private fun saveRole(
        role: SetupRole
    ) {

        getSharedPreferences(
            SETUP_PREFS,
            MODE_PRIVATE
        )
            .edit()
            .putBoolean(
                ROLE_CONFIGURED,
                true
            )
            .putString(
                ROLE_KEY,
                role.name
            )
            .apply()
    }

    private fun loadSavedRole():
            SetupRole? {

        val savedRole =
            getSharedPreferences(
                SETUP_PREFS,
                MODE_PRIVATE
            )
                .getString(
                    ROLE_KEY,
                    null
                )
                ?: return null

        return try {

            SetupRole.valueOf(
                savedRole
            )

        } catch (_: Exception) {

            null
        }
    }

    private fun resetRole() {

        getSharedPreferences(
            SETUP_PREFS,
            MODE_PRIVATE
        )
            .edit()
            .clear()
            .apply()
    }

    override fun onDestroy() {

        try {

            navigationManager
                .shutdown()

        } catch (_: Exception) {
        }

        try {

            bleManager
                .disconnect()

        } catch (_: Exception) {
        }

        NexusEyeRuntime.unregisterBleManager(
            bleManager
        )

        try {

            ttsManager
                .shutdown()

        } catch (_: Exception) {
        }

        super.onDestroy()
    }

    companion object {

        private const val SETUP_PREFS =
            "nexus_eye_setup"

        private const val ROLE_CONFIGURED =
            "role_configured"

        private const val ROLE_KEY =
            "role"
    }
}

private enum class AppScreen {

    HOME,

    NAVIGATION,

    VISION,

    COMMUNICATION,

    VOICE,

    INTELLIGENCE,

    WEATHER,

    SETTINGS
}
