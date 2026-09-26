package com.thirdeye.app

import android.Manifest
import android.content.pm.PackageManager
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

        var pendingNavigationDestination by
        remember {

            mutableStateOf<String?>(
                null
            )
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

    SETTINGS
}