package com.thirdeye.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
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
import com.thirdeye.app.security.NexusEyeApiCredentialStore
import com.thirdeye.app.setup.SetupRole
import com.thirdeye.app.ui.CommunicationScreen
import com.thirdeye.app.ui.HomeScreen
import com.thirdeye.app.ui.IntelligenceScreen
import com.thirdeye.app.ui.NavigationScreen
import com.thirdeye.app.ui.NotificationAccessSetupScreen
import com.thirdeye.app.ui.SettingsScreen
import com.thirdeye.app.ui.SetupRoleScreen
import com.thirdeye.app.ui.VisionScreen
import com.thirdeye.app.ui.VoiceScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil

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
                false
            )
        }

        val apiCredentialStore =
            remember {

                NexusEyeApiCredentialStore(
                    this@MainActivity
                )
            }

        var apiKeySetupComplete by
        remember {

            mutableStateOf(
                apiCredentialStore
                    .hasGeminiApiKey()
            )
        }

        var mapApiKeySetupComplete by
        remember {

            mutableStateOf(
                apiCredentialStore
                    .hasOnlineNavigationApiKey()
            )
        }

        var pendingNavigationDestination by
        remember {

            mutableStateOf<String?>(
                null
            )
        }

        /*
         * Setup timing.
         *
         * These values are measured from the real time the user spends
         * completing each setup stage. They are not fake download times.
         */
        var setupStartedAt by
        remember {

            mutableStateOf(
                System.currentTimeMillis()
            )
        }

        var completedSetupDurations by
        remember {

            mutableStateOf(
                emptyList<Long>()
            )
        }

        var setupTimerNow by
        remember {

            mutableStateOf(
                System.currentTimeMillis()
            )
        }

        val setupGateComplete =
            setupComplete &&
                    notificationAccessComplete &&
                    apiKeySetupComplete &&
                    mapApiKeySetupComplete

        /*
         * Keep the setup timer alive while the setup gate is active.
         */
        LaunchedEffect(
            setupGateComplete
        ) {

            if (!setupGateComplete) {

                while (true) {

                    setupTimerNow =
                        System.currentTimeMillis()

                    delay(1000L)
                }
            }
        }

        /*
         * If the user already has all required setup state,
         * the initialization gate is immediately bypassed.
         *
         * Notification access is checked from the actual Android
         * notification listener state when the setup screen is entered.
         */
        LaunchedEffect(
            setupComplete,
            apiKeySetupComplete,
            mapApiKeySetupComplete
        ) {

            if (
                setupComplete &&
                apiKeySetupComplete &&
                mapApiKeySetupComplete
            ) {

                notificationAccessComplete =
                    isNotificationAccessGranted()
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

        fun recordSetupStepCompleted() {

            val now =
                System.currentTimeMillis()

            val duration =
                (
                        now -
                                setupStartedAt
                        )
                    .coerceAtLeast(
                        1000L
                    )

            completedSetupDurations =
                completedSetupDurations +
                        duration

            setupStartedAt =
                now

            setupTimerNow =
                now
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

        val completedSteps =
            listOf(
                setupComplete,
                notificationAccessComplete,
                apiKeySetupComplete,
                mapApiKeySetupComplete
            )
                .count {
                    it
                }

        val setupProgress =
            completedSteps / 4f

        val estimatedRemainingMillis =
            calculateEstimatedRemainingMillis(
                completedSetupDurations =
                    completedSetupDurations,
                remainingSteps =
                    4 - completedSteps
            )

        Surface(
            modifier =
                Modifier.fillMaxSize(),

            color =
                MaterialTheme
                    .colorScheme
                    .background
        ) {

            when {

                !setupGateComplete -> {

                    InitializationSetupGateScreen(

                        progress =
                            setupProgress,

                        completedSteps =
                            completedSteps,

                        totalSteps =
                            4,

                        estimatedRemainingMillis =
                            estimatedRemainingMillis,

                        setupComplete =
                            setupComplete,

                        notificationAccessComplete =
                            notificationAccessComplete,

                        apiKeySetupComplete =
                            apiKeySetupComplete,

                        mapApiKeySetupComplete =
                            mapApiKeySetupComplete,

                        onContinueSetup = {

                            currentScreen =
                                AppScreen.HOME
                        }
                    )

                    /*
                     * The actual setup screen is layered below the
                     * gate state through the stage-specific branches
                     * below. The gate itself prevents Home from being
                     * reachable until all stages are complete.
                     */
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

                                    recordSetupStepCompleted()

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

                                    recordSetupStepCompleted()

                                    notificationAccessComplete =
                                        true

                                    currentScreen =
                                        AppScreen.HOME
                                }
                            )
                        }

                        !apiKeySetupComplete -> {

                            ApiKeySetupScreen(

                                onApiKeySaved = {
                                        apiKey ->

                                    try {

                                        apiCredentialStore
                                            .saveGeminiApiKey(
                                                apiKey
                                            )

                                        recordSetupStepCompleted()

                                        apiKeySetupComplete =
                                            true

                                        currentScreen =
                                            AppScreen.HOME

                                    } catch (_: Exception) {

                                        /*
                                         * The API-key screen remains
                                         * active when secure storage fails.
                                         */
                                    }
                                }
                            )
                        }

                        !mapApiKeySetupComplete -> {

                            OnlineNavigationApiKeySetupScreen(

                                onApiKeySaved = {
                                        apiKey ->

                                    try {

                                        apiCredentialStore
                                            .saveOnlineNavigationApiKey(
                                                apiKey
                                            )

                                        recordSetupStepCompleted()

                                        mapApiKeySetupComplete =
                                            true

                                        currentScreen =
                                            AppScreen.HOME

                                    } catch (_: Exception) {

                                        /*
                                         * The map/navigation API-key screen remains
                                         * active when secure storage fails.
                                         */
                                    }
                                }
                            )
                        }
                    }
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

                                        requestNavigation(
                                            home.address
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

                                    apiKeySetupComplete =
                                        apiCredentialStore
                                            .hasGeminiApiKey()

                                    mapApiKeySetupComplete =
                                        apiCredentialStore
                                            .hasOnlineNavigationApiKey()

                                    completedSetupDurations =
                                        emptyList()

                                    setupStartedAt =
                                        System.currentTimeMillis()

                                    setupTimerNow =
                                        setupStartedAt

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

    private fun startNavigation(
        destination: String
    ) {

        navigationManager
            .navigateTo(
                destinationText =
                    destination,

                strideMeters =
                    navigationSettings
                        .getStrideMeters()
            )
    }

    private fun isNotificationAccessGranted():
            Boolean {

        return try {

            val enabledListeners =
                android.provider.Settings
                    .Secure
                    .getString(
                        contentResolver,
                        "enabled_notification_listeners"
                    )
                    ?: return false

            enabledListeners
                .contains(
                    packageName
                )

        } catch (_: Exception) {

            false
        }
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

/*
 * ------------------------------------------------------------------------
 * FIRST-RUN INITIALIZATION / SETUP GATE
 * ------------------------------------------------------------------------
 */

@Composable
private fun InitializationSetupGateScreen(
    progress: Float,
    completedSteps: Int,
    totalSteps: Int,
    estimatedRemainingMillis: Long?,
    setupComplete: Boolean,
    notificationAccessComplete: Boolean,
    apiKeySetupComplete: Boolean,
    mapApiKeySetupComplete: Boolean,
    onContinueSetup: () -> Unit
) {

    /*
     * This screen deliberately does not contain a close button.
     *
     * Android itself can still suspend or destroy an Activity according
     * to normal operating-system lifecycle rules. The app does not
     * provide a setup "skip" or "close" action.
     */

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),

        verticalArrangement =
            Arrangement.Center
    ) {

        Text(
            text =
                "NEXUS EYE",

            style =
                MaterialTheme
                    .typography
                    .headlineLarge
        )

        Spacer(
            modifier =
                Modifier.height(12.dp)
        )

        Text(
            text =
                "Preparing your app",

            style =
                MaterialTheme
                    .typography
                    .headlineMedium
        )

        Spacer(
            modifier =
                Modifier.height(12.dp)
        )

        Text(
            text =
                "Please do not close the app while setup is in progress."
        )

        Spacer(
            modifier =
                Modifier.height(24.dp)
        )

        LinearProgressIndicator(
            progress = {
                progress
                    .coerceIn(
                        0f,
                        1f
                    )
            },

            modifier =
                Modifier.fillMaxWidth()
        )

        Spacer(
            modifier =
                Modifier.height(12.dp)
        )

        Text(
            text =
                "$completedSteps of $totalSteps setup steps completed"
        )

        Spacer(
            modifier =
                Modifier.height(20.dp)
        )

        SetupStepStatus(
            title =
                "Role configuration",

            complete =
                setupComplete
        )

        Spacer(
            modifier =
                Modifier.height(8.dp)
        )

        SetupStepStatus(
            title =
                "Notification access",

            complete =
                notificationAccessComplete
        )

        Spacer(
            modifier =
                Modifier.height(8.dp)
        )

        SetupStepStatus(
            title =
                "Google / Gemini API configuration",

            complete =
                apiKeySetupComplete
        )

        Spacer(
            modifier =
                Modifier.height(8.dp)
        )

        SetupStepStatus(
            title =
                "Map / online navigation API configuration",

            complete =
                mapApiKeySetupComplete
        )

        Spacer(
            modifier =
                Modifier.height(24.dp)
        )

        Text(
            text =
                if (
                    estimatedRemainingMillis !=
                    null
                ) {

                    "Estimated time remaining: ${
                        formatEstimatedTime(
                            estimatedRemainingMillis
                        )
                    }"

                } else {

                    "Estimated time remaining: Calculating..."
                },

            style =
                MaterialTheme
                    .typography
                    .bodyLarge
        )

        Spacer(
            modifier =
                Modifier.height(8.dp)
        )

        Text(
            text =
                when {

                    !setupComplete ->
                        "Complete the role setup to continue."

                    !notificationAccessComplete ->
                        "Grant notification access to continue."

                    !apiKeySetupComplete ->
                        "Enter and securely save your Gemini API key."

                    !mapApiKeySetupComplete ->
                        "Enter and securely save your map / online navigation API key."

                    else ->
                        "Setup complete."
                }
        )
    }
}

@Composable
private fun SetupStepStatus(
    title: String,
    complete: Boolean
) {

    Text(
        text =
            if (complete) {
                "✓ $title"
            } else {
                "○ $title"
            },

        style =
            MaterialTheme
                .typography
                .bodyLarge
    )
}

private fun calculateEstimatedRemainingMillis(
    completedSetupDurations: List<Long>,
    remainingSteps: Int
): Long? {

    if (
        remainingSteps <= 0
    ) {

        return 0L
    }

    if (
        completedSetupDurations.isEmpty()
    ) {

        return null
    }

    val averageDuration =
        completedSetupDurations
            .average()
            .toLong()
            .coerceAtLeast(
                1000L
            )

    return averageDuration *
            remainingSteps
}

private fun formatEstimatedTime(
    millis: Long
): String {

    val totalSeconds =
        ceil(
            millis
                .coerceAtLeast(
                    0L
                ) /
                    1000.0
        )
            .toLong()

    if (
        totalSeconds < 60
    ) {

        return "$totalSeconds seconds"
    }

    val minutes =
        totalSeconds / 60

    val seconds =
        totalSeconds % 60

    return if (
        seconds == 0L
    ) {

        "$minutes min"

    } else {

        "$minutes min $seconds sec"
    }
}

/*
 * ------------------------------------------------------------------------
 * API KEY SETUP
 * ------------------------------------------------------------------------
 */

@Composable
private fun ApiKeySetupScreen(
    onApiKeySaved: (String) -> Unit
) {

    var apiKey by
    remember {

        mutableStateOf("")
    }

    var errorMessage by
    remember {

        mutableStateOf("")
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),

        verticalArrangement =
            Arrangement.spacedBy(
                16.dp
            )
    ) {

        Text(
            text =
                "NEXUS EYE setup",

            style =
                MaterialTheme
                    .typography
                    .headlineLarge
        )

        Text(
            text =
                "Enter your Gemini API key to finish the initial setup. The key will be stored using protected Android Keystore-backed storage."
        )

        OutlinedTextField(
            value =
                apiKey,

            onValueChange = {
                    value ->

                apiKey =
                    value

                errorMessage =
                    ""
            },

            label = {
                Text(
                    "Gemini API key"
                )
            },

            singleLine = true,

            visualTransformation =
                PasswordVisualTransformation(),

            modifier =
                Modifier.fillMaxWidth()
        )

        if (
            errorMessage.isNotBlank()
        ) {

            Text(
                text =
                    errorMessage,

                color =
                    MaterialTheme
                        .colorScheme
                        .error
            )
        }

        Button(
            onClick = {

                val cleanKey =
                    apiKey.trim()

                if (
                    cleanKey.isBlank()
                ) {

                    errorMessage =
                        "Please enter your Gemini API key."

                } else {

                    try {

                        onApiKeySaved(
                            cleanKey
                        )

                    } catch (_: Exception) {

                        errorMessage =
                            "The API key could not be saved. Please try again."
                    }
                }
            },

            modifier =
                Modifier.fillMaxWidth()
        ) {

            Text(
                "SAVE API KEY AND CONTINUE"
            )
        }
    }
}

@Composable
private fun OnlineNavigationApiKeySetupScreen(
    onApiKeySaved: (String) -> Unit
) {

    var apiKey by
    remember {
        mutableStateOf("")
    }

    var errorMessage by
    remember {
        mutableStateOf("")
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),

        verticalArrangement =
            Arrangement.spacedBy(16.dp)
    ) {

        Text(
            text =
                "NEXUS EYE map setup",

            style =
                MaterialTheme
                    .typography
                    .headlineLarge
        )

        Text(
            text =
                "Enter your OpenRouteService API key to enable online map routing when offline BRouter routing cannot calculate a route. The key will be stored using protected Android Keystore-backed storage."
        )

        OutlinedTextField(
            value =
                apiKey,

            onValueChange = {
                    value ->

                apiKey =
                    value

                errorMessage =
                    ""
            },

            label = {
                Text(
                    "OpenRouteService API key"
                )
            },

            singleLine = true,

            visualTransformation =
                PasswordVisualTransformation(),

            modifier =
                Modifier.fillMaxWidth()
        )

        if (
            errorMessage.isNotBlank()
        ) {

            Text(
                text =
                    errorMessage,

                color =
                    MaterialTheme
                        .colorScheme
                        .error
            )
        }

        Button(
            onClick = {

                val cleanKey =
                    apiKey.trim()

                if (
                    cleanKey.isBlank()
                ) {

                    errorMessage =
                        "Please enter your OpenRouteService API key."

                } else {

                    try {

                        onApiKeySaved(
                            cleanKey
                        )

                    } catch (_: Exception) {

                        errorMessage =
                            "The map API key could not be saved. Please try again."
                    }
                }
            },

            modifier =
                Modifier.fillMaxWidth()
        ) {

            Text(
                "SAVE MAP API KEY AND FINISH SETUP"
            )
        }
    }
}

/*
 * ------------------------------------------------------------------------
 * APP SCREENS
 * ------------------------------------------------------------------------
 */

private enum class AppScreen {

    HOME,

    NAVIGATION,

    VISION,

    COMMUNICATION,

    VOICE,

    INTELLIGENCE,

    SETTINGS
}