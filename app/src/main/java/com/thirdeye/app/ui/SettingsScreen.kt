package com.thirdeye.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.thirdeye.app.audio.NexusEyeTtsManager
import com.thirdeye.app.bluetooth.NexusEyeEsp32Settings
import com.thirdeye.app.bluetooth.NexusEyeEsp32SettingsManager
import com.thirdeye.app.environment.NexusEyeHomeLocation
import com.thirdeye.app.environment.NexusEyeHomeLocationManager
import com.thirdeye.app.language.AppTextKey
import com.thirdeye.app.language.LanguageState
import com.thirdeye.app.language.NexusEyeLanguage
import com.thirdeye.app.language.NexusEyeLanguages
import com.thirdeye.app.language.nexusText
import com.thirdeye.app.maintenance.NexusEyeMaintenanceManager
import com.thirdeye.app.navigation.NexusEyeNavigationSettings
import com.thirdeye.app.offline.NexusEyeOfflineDataManager
import com.thirdeye.app.security.NexusEyeApiCredentialStore
import com.thirdeye.app.setup.SetupRole
import com.thirdeye.app.vision.NexusEyeVisionSettings
import com.thirdeye.app.vision.NexusEyeVisionSettingsManager
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    languageState: LanguageState,
    role: SetupRole?,
    onSaveLanguages: (
        NexusEyeLanguage,
        NexusEyeLanguage
    ) -> Unit,
    onResetSetup: () -> Unit,
    onBack: () -> Unit
) {

    val context = LocalContext.current
    val appContext = context.applicationContext

    val ttsManager = remember {
        NexusEyeTtsManager(appContext)
    }

    val homeLocationManager = remember {
        NexusEyeHomeLocationManager(appContext)
    }

    val locationClient = remember {
        LocationServices.getFusedLocationProviderClient(
            appContext
        )
    }

    val scope = rememberCoroutineScope()

    val esp32SettingsManager = remember {
        NexusEyeEsp32SettingsManager(appContext)
    }

    val initialEsp32Settings = remember {
        esp32SettingsManager.getSettings()
    }

    val visionSettingsManager = remember {
        NexusEyeVisionSettingsManager(appContext)
    }

    val initialVisionSettings = remember {
        visionSettingsManager.getSettings()
    }

    val navigationSettings = remember {
        NexusEyeNavigationSettings(appContext)
    }

    val apiCredentialStore = remember {
        NexusEyeApiCredentialStore(appContext)
    }

    val initialApiCredentialState = remember {
        apiCredentialStore.getState()
    }

    val offlineDataManager = remember {
        NexusEyeOfflineDataManager(appContext)
    }

    val maintenanceManager = remember {
        NexusEyeMaintenanceManager(appContext)
    }

    val initialOfflineDataStats = remember {
        offlineDataManager.getStats()
    }

    var strideMeters by remember {
        mutableStateOf(navigationSettings.getStrideMeters())
    }

    var selectedAppLanguage by remember(
        languageState.appLanguage.id
    ) {
        mutableStateOf(languageState.appLanguage)
    }

    var selectedSpeechLanguage by remember(
        languageState.speechLanguage.id
    ) {
        mutableStateOf(languageState.speechLanguage)
    }

    var speechRate by remember {
        mutableStateOf(
            ttsManager.getSpeechRate()
        )
    }

    var savedHome by remember {
        mutableStateOf(
            homeLocationManager.getSavedHomeLocation()
        )
    }

    var statusMessage by remember {
        mutableStateOf("")
    }

    var preferredEsp32DeviceName by remember {
        mutableStateOf(initialEsp32Settings.preferredDeviceName)
    }

    var esp32AutoReconnect by remember {
        mutableStateOf(initialEsp32Settings.autoReconnect)
    }

    var useWearableAudioWhenConnected by remember {
        mutableStateOf(initialEsp32Settings.useWearableAudioWhenConnected)
    }

    var esp32VoiceTriggerEnabled by remember {
        mutableStateOf(initialEsp32Settings.voiceTriggerEnabled)
    }

    var savePhotosToLibrary by remember {
        mutableStateOf(initialVisionSettings.savePhotosToLibrary)
    }

    var saveCameraCaptures by remember {
        mutableStateOf(initialVisionSettings.saveCameraCaptures)
    }

    var saveGalleryImports by remember {
        mutableStateOf(initialVisionSettings.saveGalleryImports)
    }

    var personRecognitionEnabled by remember {
        mutableStateOf(initialVisionSettings.personRecognitionEnabled)
    }

    var showPhotoMetadata by remember {
        mutableStateOf(initialVisionSettings.showPhotoMetadata)
    }

    var autoIdentifyAfterCapture by remember {
        mutableStateOf(initialVisionSettings.autoIdentifyAfterCapture)
    }

    var geminiApiKeyInput by remember {
        mutableStateOf("")
    }

    var geminiApiKeyConfigured by remember {
        mutableStateOf(initialApiCredentialState.geminiApiKeyConfigured)
    }

    var offlinePackagedEntryCount by remember {
        mutableStateOf(initialOfflineDataStats.packagedEntryCount)
    }

    var offlineCustomEntryCount by remember {
        mutableStateOf(initialOfflineDataStats.customEntryCount)
    }

    var offlineDataVersion by remember {
        mutableStateOf(initialOfflineDataStats.dataVersion)
    }

    var showClearOfflineCustomDataDialog by remember {
        mutableStateOf(false)
    }

    var showClearApiKeyDialog by remember {
        mutableStateOf(false)
    }

    var showMaintenanceResetDialog by remember {
        mutableStateOf(false)
    }

    var showResetDialog by remember {
        mutableStateOf(false)
    }

    var showClearHomeDialog by remember {
        mutableStateOf(false)
    }

    var showHomeConfirmationDialog by remember {
        mutableStateOf(false)
    }

    var detectedHome by remember {
        mutableStateOf<NexusEyeHomeLocation?>(null)
    }

    var homeLocationRequestPending by remember {
        mutableStateOf(false)
    }

    val locationPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->

            val fineGranted =
                permissions[
                    Manifest.permission.ACCESS_FINE_LOCATION
                ] == true

            val coarseGranted =
                permissions[
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ] == true

            if (fineGranted || coarseGranted) {
                homeLocationRequestPending = true
            } else {
                statusMessage =
                    "Location permission is required to change the Home location."

                speakConfirmation(
                    ttsManager = ttsManager,
                    language = selectedSpeechLanguage,
                    text =
                        "Location permission is required to change the Home location."
                )
            }
        }

    DisposableEffect(ttsManager) {

        onDispose {
            try {
                ttsManager.shutdown()
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(homeLocationRequestPending) {

        if (!homeLocationRequestPending) {
            return@LaunchedEffect
        }

        homeLocationRequestPending = false

        statusMessage =
            "Getting your current location..."

        try {
            val location =
                obtainCurrentLocation(
                    context = appContext,
                    locationClient = locationClient
                )

            if (location == null) {
                statusMessage =
                    "Could not determine your current location."

                speakConfirmation(
                    ttsManager = ttsManager,
                    language = selectedSpeechLanguage,
                    text =
                        "I could not determine your current location."
                )

                return@LaunchedEffect
            }

            val address =
                homeLocationManager.resolveAddress(
                    latitude = location.latitude,
                    longitude = location.longitude
                )

            detectedHome =
                NexusEyeHomeLocation(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    address = address
                )

            showHomeConfirmationDialog = true

            statusMessage =
                "Current location found. Please confirm it as Home."

            speakConfirmation(
                ttsManager = ttsManager,
                language = selectedSpeechLanguage,
                text =
                    "I detected this as your Home location: $address. Please confirm whether this is your correct Home location."
            )

        } catch (exception: Exception) {

            statusMessage =
                exception.message
                    ?: "Could not determine the current location."

            speakConfirmation(
                ttsManager = ttsManager,
                language = selectedSpeechLanguage,
                text =
                    "I could not determine your current location."
            )
        }
    }

    fun requestChangeHome() {

        val fineGranted =
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted =
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        if (fineGranted || coarseGranted) {
            homeLocationRequestPending = true
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    fun confirmDetectedHome() {

        val home = detectedHome
            ?: return

        homeLocationManager.saveHomeLocation(
            home
        )

        savedHome = home
        detectedHome = null
        showHomeConfirmationDialog = false

        statusMessage =
            "Home location saved."

        speakConfirmation(
            ttsManager = ttsManager,
            language = selectedSpeechLanguage,
            text =
                "Home location saved successfully."
        )
    }

    fun clearHome() {

        homeLocationManager.clearHomeLocation()
        savedHome = null
        showClearHomeDialog = false

        statusMessage =
            "Saved Home location removed."

        speakConfirmation(
            ttsManager = ttsManager,
            language = selectedSpeechLanguage,
            text =
                "Saved Home location removed."
        )
    }

    fun saveEsp32Settings() {

        val cleanDeviceName =
            preferredEsp32DeviceName.trim()

        if (cleanDeviceName.isBlank()) {
            statusMessage =
                "Please enter the preferred ESP32 device name."

            preferredEsp32DeviceName =
                NexusEyeEsp32SettingsManager.DEFAULT_DEVICE_NAME

            speakConfirmation(
                ttsManager = ttsManager,
                language = selectedSpeechLanguage,
                text =
                    "Please enter a valid preferred ESP32 device name."
            )

            return
        }

        val settings =
            NexusEyeEsp32Settings(
                preferredDeviceName = cleanDeviceName,
                autoReconnect = esp32AutoReconnect,
                useWearableAudioWhenConnected = useWearableAudioWhenConnected,
                voiceTriggerEnabled = esp32VoiceTriggerEnabled
            )

        esp32SettingsManager.saveSettings(settings)
        preferredEsp32DeviceName = cleanDeviceName

        statusMessage =
            "ESP32 settings saved."

        speakConfirmation(
            ttsManager = ttsManager,
            language = selectedSpeechLanguage,
            text =
                "ESP32 settings saved. The preferred device name is $cleanDeviceName."
        )
    }

    fun resetEsp32Settings() {

        esp32SettingsManager.resetToDefaults()

        val defaults =
            esp32SettingsManager.getSettings()

        preferredEsp32DeviceName =
            defaults.preferredDeviceName

        esp32AutoReconnect =
            defaults.autoReconnect

        useWearableAudioWhenConnected =
            defaults.useWearableAudioWhenConnected

        esp32VoiceTriggerEnabled =
            defaults.voiceTriggerEnabled

        statusMessage =
            "ESP32 settings reset."

        speakConfirmation(
            ttsManager = ttsManager,
            language = selectedSpeechLanguage,
            text =
                "ESP32 settings have been reset to their defaults."
        )
    }

    fun saveNavigationSettings() {

        navigationSettings.setStrideMeters(strideMeters)

        statusMessage =
            "Navigation settings saved."

        speakConfirmation(
            ttsManager = ttsManager,
            language = selectedSpeechLanguage,
            text =
                "Navigation settings saved. Your stride length is ${formatStrideMeters(strideMeters)} meters."
        )
    }

    fun resetNavigationSettings() {

        navigationSettings.resetToDefaults()
        strideMeters = NexusEyeNavigationSettings.DEFAULT_STRIDE_METERS

        statusMessage =
            "Navigation settings reset."

        speakConfirmation(
            ttsManager = ttsManager,
            language = selectedSpeechLanguage,
            text =
                "Navigation settings have been reset. Your stride length is ${formatStrideMeters(strideMeters)} meters."
        )
    }

    fun saveVisionSettings() {

        val settings =
            NexusEyeVisionSettings(
                savePhotosToLibrary = savePhotosToLibrary,
                saveCameraCaptures = saveCameraCaptures,
                saveGalleryImports = saveGalleryImports,
                personRecognitionEnabled = personRecognitionEnabled,
                showPhotoMetadata = showPhotoMetadata,
                autoIdentifyAfterCapture = autoIdentifyAfterCapture
            )

        visionSettingsManager.saveSettings(settings)

        statusMessage =
            "Photo and Vision settings saved."

        speakConfirmation(
            ttsManager = ttsManager,
            language = selectedSpeechLanguage,
            text =
                "Photo and Vision settings saved successfully."
        )
    }

    fun resetVisionSettings() {

        visionSettingsManager.resetToDefaults()

        val defaults =
            visionSettingsManager.getSettings()

        savePhotosToLibrary =
            defaults.savePhotosToLibrary

        saveCameraCaptures =
            defaults.saveCameraCaptures

        saveGalleryImports =
            defaults.saveGalleryImports

        personRecognitionEnabled =
            defaults.personRecognitionEnabled

        showPhotoMetadata =
            defaults.showPhotoMetadata

        autoIdentifyAfterCapture =
            defaults.autoIdentifyAfterCapture

        statusMessage =
            "Photo and Vision settings reset."

        speakConfirmation(
            ttsManager = ttsManager,
            language = selectedSpeechLanguage,
            text =
                "Photo and Vision settings have been reset to their defaults."
        )
    }

    fun saveApiSettings() {

        val cleanKey =
            geminiApiKeyInput.trim()

        if (cleanKey.isNotBlank()) {
            apiCredentialStore.saveGeminiApiKey(cleanKey)
            geminiApiKeyInput = ""
            geminiApiKeyConfigured = true

            statusMessage =
                "Gemini API key saved securely."

            speakConfirmation(
                ttsManager = ttsManager,
                language = selectedSpeechLanguage,
                text =
                    "Gemini API key saved securely in protected device storage."
            )

        } else {

            geminiApiKeyConfigured =
                apiCredentialStore.hasGeminiApiKey()

            statusMessage =
                if (geminiApiKeyConfigured) {
                    "Protected Gemini API key is already configured."
                } else {
                    "No Gemini API key was entered."
                }

            speakConfirmation(
                ttsManager = ttsManager,
                language = selectedSpeechLanguage,
                text =
                    if (geminiApiKeyConfigured) {
                        "The protected Gemini API key is already configured."
                    } else {
                        "No Gemini API key was entered."
                    }
            )
        }
    }

    fun clearApiKey() {

        apiCredentialStore.clearGeminiApiKey()
        geminiApiKeyInput = ""
        geminiApiKeyConfigured = false
        showClearApiKeyDialog = false

        statusMessage =
            "Protected Gemini API key removed."

        speakConfirmation(
            ttsManager = ttsManager,
            language = selectedSpeechLanguage,
            text =
                "The protected Gemini API key has been removed from this device."
        )
    }

    fun resetUserSettings() {

        maintenanceManager.resetUserSettings()

        selectedAppLanguage =
            NexusEyeLanguages.English

        selectedSpeechLanguage =
            NexusEyeLanguages.English

        speechRate =
            ttsManager.getSpeechRate()

        strideMeters =
            navigationSettings.getStrideMeters()

        val esp32Defaults =
            esp32SettingsManager.getSettings()

        preferredEsp32DeviceName =
            esp32Defaults.preferredDeviceName

        esp32AutoReconnect =
            esp32Defaults.autoReconnect

        useWearableAudioWhenConnected =
            esp32Defaults.useWearableAudioWhenConnected

        esp32VoiceTriggerEnabled =
            esp32Defaults.voiceTriggerEnabled

        val visionDefaults =
            visionSettingsManager.getSettings()

        savePhotosToLibrary =
            visionDefaults.savePhotosToLibrary

        saveCameraCaptures =
            visionDefaults.saveCameraCaptures

        saveGalleryImports =
            visionDefaults.saveGalleryImports

        personRecognitionEnabled =
            visionDefaults.personRecognitionEnabled

        showPhotoMetadata =
            visionDefaults.showPhotoMetadata

        autoIdentifyAfterCapture =
            visionDefaults.autoIdentifyAfterCapture

        val offlineStats =
            offlineDataManager.getStats()

        offlinePackagedEntryCount =
            offlineStats.packagedEntryCount

        offlineCustomEntryCount =
            offlineStats.customEntryCount

        offlineDataVersion =
            offlineStats.dataVersion

        geminiApiKeyConfigured =
            apiCredentialStore.hasGeminiApiKey()

        showMaintenanceResetDialog =
            false

        statusMessage =
            "Application settings have been reset to defaults."

        speakConfirmation(
            ttsManager = ttsManager,
            language = NexusEyeLanguages.English,
            text =
                "Application settings have been reset to defaults. Protected API credentials and your saved Home location were kept."
        )
    }

    fun refreshOfflineDataStats() {

        val stats =
            offlineDataManager.getStats()

        offlinePackagedEntryCount =
            stats.packagedEntryCount

        offlineCustomEntryCount =
            stats.customEntryCount

        offlineDataVersion =
            stats.dataVersion

        statusMessage =
            "Offline data status refreshed."

        speakConfirmation(
            ttsManager = ttsManager,
            language = selectedSpeechLanguage,
            text =
                "Offline data contains ${stats.totalEntryCount} local entries. Packaged entries: ${stats.packagedEntryCount}. Custom entries: ${stats.customEntryCount}."
        )
    }

    fun clearOfflineCustomData() {

        val removedCount =
            offlineDataManager.clearCustomEntries()

        showClearOfflineCustomDataDialog =
            false

        val stats =
            offlineDataManager.getStats()

        offlinePackagedEntryCount =
            stats.packagedEntryCount

        offlineCustomEntryCount =
            stats.customEntryCount

        offlineDataVersion =
            stats.dataVersion

        statusMessage =
            if (removedCount > 0) {
                "Removed $removedCount custom offline entries."
            } else {
                "No custom offline entries were stored."
            }

        speakConfirmation(
            ttsManager = ttsManager,
            language = selectedSpeechLanguage,
            text =
                if (removedCount > 0) {
                    "$removedCount custom offline entries were removed. The packaged offline data remains available."
                } else {
                    "There were no custom offline entries to remove."
                }
        )
    }

    Scaffold { innerPadding ->

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(
                        rememberScrollState()
                    )
                    .padding(innerPadding)
                    .padding(24.dp),
            verticalArrangement =
                Arrangement.spacedBy(16.dp)
        ) {

            Text(
                text =
                    nexusText(
                        AppTextKey.SETTINGS_TITLE
                    ),
                style =
                    MaterialTheme.typography.headlineLarge,
                modifier =
                    Modifier.semantics {
                        contentDescription =
                            "NEXUS EYE settings"
                    }
            )

            Text(
                text =
                    when (role) {
                        SetupRole.BLIND_USER ->
                            nexusText(
                                AppTextKey.BLIND_USER_MODE
                            )

                        SetupRole.HELPER ->
                            nexusText(
                                AppTextKey.HELPER_MODE
                            )

                        null -> ""
                    },
                style =
                    MaterialTheme.typography.titleMedium
            )

            Text(
                text =
                    nexusText(
                        AppTextKey.CURRENT_APP_LANGUAGE
                    ),
                style =
                    MaterialTheme.typography.titleMedium
            )

            LanguageSelector(
                label = "Application language",
                selectedLanguage =
                    selectedAppLanguage,
                onSelected = {
                    selectedAppLanguage = it
                }
            )

            Text(
                text =
                    nexusText(
                        AppTextKey.CURRENT_SPEECH_LANGUAGE
                    ),
                style =
                    MaterialTheme.typography.titleMedium
            )

            LanguageSelector(
                label =
                    "Speech and communication language",
                selectedLanguage =
                    selectedSpeechLanguage,
                onSelected = {
                    selectedSpeechLanguage = it
                }
            )

            Button(
                onClick = {

                    onSaveLanguages(
                        selectedAppLanguage,
                        selectedSpeechLanguage
                    )

                    statusMessage =
                        "Language settings saved."

                    speakConfirmation(
                        ttsManager = ttsManager,
                        language = selectedSpeechLanguage,
                        text =
                            "Language settings saved. Speech language is ${selectedSpeechLanguage.displayName}."
                    )
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription =
                                "Save language settings"
                        }
            ) {
                Text(
                    text =
                        nexusText(
                            AppTextKey.SAVE
                        )
                )
            }

            Text(
                text = "Speech speed",
                style =
                    MaterialTheme.typography.titleMedium,
                modifier =
                    Modifier.semantics {
                        contentDescription =
                            "Speech speed setting"
                    }
            )

            Text(
                text =
                    "${formatSpeechRate(speechRate)}x",
                style =
                    MaterialTheme.typography.bodyLarge,
                modifier =
                    Modifier.semantics {
                        contentDescription =
                            "Current speech speed"
                        stateDescription =
                            "${formatSpeechRate(speechRate)} times normal speed"
                    }
            )

            Slider(
                value = speechRate,
                onValueChange = { newValue ->

                    val rounded =
                        roundSpeechRate(
                            newValue
                        )

                    speechRate = rounded

                    try {
                        ttsManager.setSpeechRate(
                            rounded
                        )
                    } catch (_: Exception) {
                    }
                },
                valueRange =
                    SPEECH_RATE_MIN..SPEECH_RATE_MAX,
                steps =
                    SPEECH_RATE_STEPS,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription =
                                "Speech speed slider"
                            stateDescription =
                                "${formatSpeechRate(speechRate)} times normal speed"
                        }
            )

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription =
                                "Speech speed range from 0.50 times to 1.50 times normal speed"
                        },
                horizontalArrangement =
                    Arrangement.spacedBy(12.dp)
            ) {

                Text(
                    text = "0.50x",
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = "Normal",
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = "1.50x",
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedButton(
                onClick = {

                    val defaultRate =
                        try {
                            ttsManager.resetSpeechRate()
                            ttsManager.getSpeechRate()
                        } catch (_: Exception) {
                            DEFAULT_SPEECH_RATE
                        }

                    speechRate = defaultRate
                    statusMessage =
                        "Speech speed reset."

                    speakConfirmation(
                        ttsManager = ttsManager,
                        language = selectedSpeechLanguage,
                        text =
                            "Speech speed has been reset to normal."
                    )
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription =
                                "Reset speech speed to normal"
                        }
            ) {
                Text(
                    text =
                        "RESET SPEECH SPEED"
                )
            }

            OutlinedButton(
                onClick = {

                    statusMessage =
                        "Testing the current voice settings..."

                    val spokenText =
                        "This is a voice settings test. The current speech speed is ${formatSpeechRate(speechRate)} times normal speed."

                    speakConfirmation(
                        ttsManager = ttsManager,
                        language = selectedSpeechLanguage,
                        text = spokenText
                    )
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription =
                                "Test current voice settings"
                        }
            ) {
                Text(
                    text =
                        "TEST CURRENT VOICE"
                )
            }

            Text(
                text = "Navigation Settings",
                style =
                    MaterialTheme.typography.titleMedium,
                modifier =
                    Modifier.semantics {
                        contentDescription =
                            "Navigation settings"
                    }
            )

            Text(
                text =
                    "Adjust the estimated stride length used for spoken step guidance. NEXUS EYE continues to use offline-first BRouter navigation; this setting does not enable or disable online routing.",
                style =
                    MaterialTheme.typography.bodyMedium,
                modifier =
                    Modifier.semantics {
                        contentDescription =
                            "Navigation settings information"
                    }
            )

            Text(
                text =
                    "Stride length: ${formatStrideMeters(strideMeters)} m",
                style =
                    MaterialTheme.typography.bodyLarge,
                modifier =
                    Modifier.semantics {
                        contentDescription =
                            "Current navigation stride length"
                        stateDescription =
                            "${formatStrideMeters(strideMeters)} meters per step"
                    }
            )

            Slider(
                value = strideMeters.toFloat(),
                onValueChange = { value ->
                    strideMeters = roundStrideMeters(value.toDouble())
                },
                valueRange =
                    NexusEyeNavigationSettings.MIN_STRIDE_METERS.toFloat()..
                            NexusEyeNavigationSettings.MAX_STRIDE_METERS.toFloat(),
                steps = 16,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription =
                                "Navigation stride length slider"
                            stateDescription =
                                "${formatStrideMeters(strideMeters)} meters per step"
                        }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "0.30 m",
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "0.70 m default",
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "2.00 m",
                    modifier = Modifier.weight(1f)
                )
            }

            Button(
                onClick = {
                    saveNavigationSettings()
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription =
                                "Save navigation settings"
                        }
            ) {
                Text(
                    text = "SAVE NAVIGATION SETTINGS"
                )
            }

            OutlinedButton(
                onClick = {
                    resetNavigationSettings()
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription =
                                "Reset navigation settings to defaults"
                        }
            ) {
                Text(
                    text = "RESET NAVIGATION SETTINGS"
                )
            }

            Text(
                text = "Photo and Vision Settings",
                style =
                    MaterialTheme.typography.titleMedium,
                modifier =
                    Modifier.semantics {
                        contentDescription =
                            "Photo and Vision settings"
                    }
            )

            Text(
                text =
                    "These preferences control how the Android Vision system should store photos and use person-recognition features. The actual camera, gallery, face-recognition, and future OV7670 pipelines remain in the Vision subsystem.",
                style =
                    MaterialTheme.typography.bodyMedium,
                modifier =
                    Modifier.semantics {
                        contentDescription =
                            "Photo and Vision settings information"
                    }
            )

            VisionSettingSwitch(
                title = "Keep photos in the central Vision library",
                description =
                    "Allow captured or imported images to be retained in the central NEXUS EYE Vision photo library.",
                checked = savePhotosToLibrary,
                onCheckedChange = {
                    savePhotosToLibrary = it
                },
                accessibilityDescription =
                    "Keep photos in central Vision library"
            )

            VisionSettingSwitch(
                title = "Save phone camera captures",
                description =
                    "Allow photos captured with the phone camera to be saved into the Vision library.",
                checked = saveCameraCaptures,
                onCheckedChange = {
                    saveCameraCaptures = it
                },
                accessibilityDescription =
                    "Save phone camera captures"
            )

            VisionSettingSwitch(
                title = "Save gallery imports",
                description =
                    "Allow images selected from the phone gallery to be copied into the Vision library.",
                checked = saveGalleryImports,
                onCheckedChange = {
                    saveGalleryImports = it
                },
                accessibilityDescription =
                    "Save gallery imports"
            )

            VisionSettingSwitch(
                title = "Person recognition",
                description =
                    "Allow the Vision system to use saved face profiles when identifying people.",
                checked = personRecognitionEnabled,
                onCheckedChange = {
                    personRecognitionEnabled = it
                },
                accessibilityDescription =
                    "Person recognition"
            )

            VisionSettingSwitch(
                title = "Show photo metadata",
                description =
                    "Allow the Vision interface to display available photo metadata such as dimensions and camera information.",
                checked = showPhotoMetadata,
                onCheckedChange = {
                    showPhotoMetadata = it
                },
                accessibilityDescription =
                    "Show photo metadata"
            )

            VisionSettingSwitch(
                title = "Automatically identify after capture or import",
                description =
                    "Allow the Vision workflow to start person recognition automatically after a photo is stored when known profiles are available.",
                checked = autoIdentifyAfterCapture,
                onCheckedChange = {
                    autoIdentifyAfterCapture = it
                },
                accessibilityDescription =
                    "Automatically identify after capture or import"
            )

            Button(
                onClick = {
                    saveVisionSettings()
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription =
                                "Save Photo and Vision settings"
                        }
            ) {
                Text(
                    text = "SAVE PHOTO / VISION SETTINGS"
                )
            }

            OutlinedButton(
                onClick = {
                    resetVisionSettings()
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription =
                                "Reset Photo and Vision settings to defaults"
                        }
            ) {
                Text(
                    text = "RESET PHOTO / VISION SETTINGS"
                )
            }

            Text(
                text = "API Settings & Protected Credentials",
                style =
                    MaterialTheme.typography.titleMedium,
                modifier =
                    Modifier.semantics {
                        contentDescription =
                            "API settings and protected credentials"
                    }
            )

            Text(
                text =
                    "NEXUS EYE can keep a Gemini API key in protected Android Keystore-backed storage. The key is never displayed after it has been saved.",
                style =
                    MaterialTheme.typography.bodyMedium,
                modifier =
                    Modifier.semantics {
                        contentDescription =
                            "API settings information. Gemini API keys are stored in protected Android Keystore-backed storage and are not displayed after saving."
                    }
            )

            Text(
                text =
                    if (geminiApiKeyConfigured) {
                        "Gemini API key: Configured"
                    } else {
                        "Gemini API key: Not configured"
                    },
                style =
                    MaterialTheme.typography.bodyLarge,
                modifier =
                    Modifier.semantics {
                        contentDescription =
                            if (geminiApiKeyConfigured) {
                                "Gemini API key is configured"
                            } else {
                                "Gemini API key is not configured"
                            }
                        stateDescription =
                            if (geminiApiKeyConfigured) {
                                "Configured"
                            } else {
                                "Not configured"
                            }
                    }
            )

            OutlinedTextField(
                value = geminiApiKeyInput,
                onValueChange = {
                    geminiApiKeyInput = it
                },
                label = {
                    Text(
                        text = "Gemini API key"
                    )
                },
                placeholder = {
                    Text(
                        text = "Enter a new API key"
                    )
                },
                singleLine = true,
                visualTransformation =
                    PasswordVisualTransformation(),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription =
                                "Gemini API key entry. The key is hidden while typing."
                        }
            )

            Text(
                text =
                    "Leave the field empty to keep the currently stored key. Entering a new key replaces the protected credential.",
                style =
                    MaterialTheme.typography.bodySmall
            )

            Button(
                onClick = {
                    saveApiSettings()
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription =
                                "Save Gemini API key securely"
                        }
            ) {
                Text(
                    text = "SAVE API KEY SECURELY"
                )
            }

            OutlinedButton(
                onClick = {
                    statusMessage =
                        if (apiCredentialStore.hasGeminiApiKey()) {
                            "Protected Gemini API key is present on this device."
                        } else {
                            "No protected Gemini API key is stored."
                        }

                    speakConfirmation(
                        ttsManager = ttsManager,
                        language = selectedSpeechLanguage,
                        text =
                            if (apiCredentialStore.hasGeminiApiKey()) {
                                "A protected Gemini API key is stored on this device."
                            } else {
                                "No protected Gemini API key is stored on this device."
                            }
                    )
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription =
                                "Check whether a protected Gemini API key is stored"
                        }
            ) {
                Text(
                    text = "CHECK API KEY STATUS"
                )
            }

            OutlinedButton(
                onClick = {
                    showClearApiKeyDialog = true
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription =
                                "Remove the protected Gemini API key"
                        }
            ) {
                Text(
                    text = "REMOVE PROTECTED API KEY"
                )
            }

            Text(
                text =
                    "Security note: the actual online service must read the credential from the protected store when it performs an API request. Do not place the key in Kotlin source code, XML resources, or build constants.",
                style =
                    MaterialTheme.typography.bodySmall
            )

            Text(
                text = "Offline Data Management",
                style =
                    MaterialTheme.typography.titleMedium,
                modifier =
                    Modifier.semantics {
                        contentDescription =
                            "Offline data management settings"
                    }
            )

            Text(
                text =
                    "NEXUS EYE keeps the packaged offline knowledge data on the device. Online services are not represented as offline answers. Custom offline entries are stored locally and can be removed here.",
                style =
                    MaterialTheme.typography.bodyMedium,
                modifier =
                    Modifier.semantics {
                        contentDescription =
                            "Offline data information. Packaged knowledge stays on the device. Custom entries are local."
                    }
            )

            Text(
                text =
                    "Offline data version: $offlineDataVersion",
                style =
                    MaterialTheme.typography.bodyLarge
            )

            Text(
                text =
                    "Packaged local entries: $offlinePackagedEntryCount",
                style =
                    MaterialTheme.typography.bodyLarge
            )

            Text(
                text =
                    "Custom local entries: $offlineCustomEntryCount",
                style =
                    MaterialTheme.typography.bodyLarge
            )

            Text(
                text =
                    "Total local entries: ${offlinePackagedEntryCount + offlineCustomEntryCount}",
                style =
                    MaterialTheme.typography.bodyLarge,
                modifier =
                    Modifier.semantics {
                        contentDescription =
                            "Total offline local entries: ${offlinePackagedEntryCount + offlineCustomEntryCount}"
                    }
            )

            Button(
                onClick = {
                    refreshOfflineDataStats()
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription =
                                "Refresh offline data status"
                        }
            ) {
                Text(
                    text =
                        "REFRESH OFFLINE DATA STATUS"
                )
            }

            OutlinedButton(
                onClick = {
                    showClearOfflineCustomDataDialog = true
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription =
                                "Clear custom offline data"
                        }
            ) {
                Text(
                    text =
                        "CLEAR CUSTOM OFFLINE DATA"
                )
            }

            Text(
                text =
                    "The packaged offline knowledge is read from the app's local asset. Clearing custom data does not remove the packaged entries.",
                style =
                    MaterialTheme.typography.bodySmall
            )

            Text(
                text = "ESP32 Settings",
                style =
                    MaterialTheme.typography.titleMedium,
                modifier =
                    Modifier.semantics {
                        contentDescription =
                            "ESP32 wearable settings"
                    }
            )

            Text(
                text =
                    "These settings are stored on the Android device. Actual ESP32 BLE connection, packet transport, camera transport, and wearable audio behavior are handled by the Bluetooth and hardware integration layers.",
                style =
                    MaterialTheme.typography.bodyMedium,
                modifier =
                    Modifier.semantics {
                        contentDescription =
                            "ESP32 settings information. These are Android side preferences. Actual hardware integration is handled separately."
                    }
            )

            OutlinedTextField(
                value = preferredEsp32DeviceName,
                onValueChange = {
                    preferredEsp32DeviceName = it
                },
                label = {
                    Text(
                        text = "Preferred ESP32 device name"
                    )
                },
                singleLine = true,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription =
                                "Preferred ESP32 device name"
                        }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Automatic reconnect",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Allow the app to prefer reconnecting to the saved ESP32 device when the Bluetooth layer supports it.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Switch(
                    checked = esp32AutoReconnect,
                    onCheckedChange = {
                        esp32AutoReconnect = it
                    },
                    modifier = Modifier.semantics {
                        contentDescription = "Automatic ESP32 reconnect"
                        stateDescription =
                            if (esp32AutoReconnect) "Enabled" else "Disabled"
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Use wearable audio when connected",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Prefer the future ESP32 audio path instead of unnecessary phone playback when the wearable is connected.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Switch(
                    checked = useWearableAudioWhenConnected,
                    onCheckedChange = {
                        useWearableAudioWhenConnected = it
                    },
                    modifier = Modifier.semantics {
                        contentDescription = "Use wearable audio when connected"
                        stateDescription =
                            if (useWearableAudioWhenConnected) "Enabled" else "Disabled"
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Wearable voice trigger",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Keep support for the ESP32 voice-trigger command enabled when the wearable integration is available.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Switch(
                    checked = esp32VoiceTriggerEnabled,
                    onCheckedChange = {
                        esp32VoiceTriggerEnabled = it
                    },
                    modifier = Modifier.semantics {
                        contentDescription = "ESP32 wearable voice trigger"
                        stateDescription =
                            if (esp32VoiceTriggerEnabled) "Enabled" else "Disabled"
                    }
                )
            }

            Button(
                onClick = {
                    saveEsp32Settings()
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription =
                                "Save ESP32 settings"
                        }
            ) {
                Text(
                    text = "SAVE ESP32 SETTINGS"
                )
            }

            OutlinedButton(
                onClick = {
                    resetEsp32Settings()
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription =
                                "Reset ESP32 settings to defaults"
                        }
            ) {
                Text(
                    text = "RESET ESP32 SETTINGS"
                )
            }

            Text(
                text = "Home Location",
                style =
                    MaterialTheme.typography.titleMedium,
                modifier =
                    Modifier.semantics {
                        contentDescription =
                            "Home location settings"
                    }
            )

            if (savedHome != null) {

                Text(
                    text =
                        "Saved Home: ${savedHome!!.address}",
                    style =
                        MaterialTheme.typography.bodyLarge,
                    modifier =
                        Modifier.semantics {
                            contentDescription =
                                "Saved Home location: ${savedHome!!.address}"
                        }
                )

                Button(
                    onClick = {
                        requestChangeHome()
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .semantics {
                                contentDescription =
                                    "Change saved Home location"
                            }
                ) {
                    Text(
                        text =
                            "CHANGE HOME LOCATION"
                    )
                }

                OutlinedButton(
                    onClick = {
                        showClearHomeDialog = true
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .semantics {
                                contentDescription =
                                    "Remove saved Home location"
                            }
                ) {
                    Text(
                        text =
                            "REMOVE SAVED HOME"
                    )
                }

            } else {

                Text(
                    text =
                        "No Home location is saved.",
                    style =
                        MaterialTheme.typography.bodyLarge,
                    modifier =
                        Modifier.semantics {
                            contentDescription =
                                "No Home location is currently saved"
                        }
                )

                Button(
                    onClick = {
                        requestChangeHome()
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .semantics {
                                contentDescription =
                                    "Set current location as Home"
                            }
                ) {
                    Text(
                        text =
                            "SET HOME LOCATION"
                    )
                }
            }

            if (statusMessage.isNotBlank()) {

                Text(
                    text = statusMessage,
                    style =
                        MaterialTheme.typography.bodyMedium,
                    modifier =
                        Modifier.semantics {
                            contentDescription =
                                statusMessage
                        }
                )
            }

            Text(
                text =
                    "Reset & Maintenance",
                style =
                    MaterialTheme.typography.headlineSmall,
                modifier =
                    Modifier.semantics {
                        contentDescription =
                            "Reset and maintenance settings"
                    }
            )

            Text(
                text =
                    "Reset ordinary NEXUS EYE settings to their defaults. Your protected Gemini API key, saved Home location, setup role, and Vision photo library are not removed by this reset.",
                style =
                    MaterialTheme.typography.bodyLarge,
                modifier =
                    Modifier.semantics {
                        contentDescription =
                            "Reset information. Protected API credentials, Home location, setup role, and Vision photos are preserved."
                    }
            )

            OutlinedButton(
                onClick = {
                    showMaintenanceResetDialog = true
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription =
                                "Reset ordinary application settings to defaults"
                        }
            ) {
                Text(
                    text =
                        "RESET APPLICATION SETTINGS"
                )
            }

            OutlinedButton(
                onClick = {
                    showResetDialog = true
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription =
                                "Reset application setup"
                        }
            ) {
                Text(
                    text =
                        nexusText(
                            AppTextKey.RESET_SETUP
                        )
                )
            }

            OutlinedButton(
                onClick = onBack,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription =
                                "Return to previous screen"
                        }
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

    if (showHomeConfirmationDialog) {

        val home = detectedHome

        if (home != null) {

            AlertDialog(
                onDismissRequest = {
                    showHomeConfirmationDialog = false
                    detectedHome = null
                    statusMessage =
                        "Home location was not changed."
                },
                title = {
                    Text(
                        text =
                            "Confirm Home Location"
                    )
                },
                text = {
                    Text(
                        text =
                            "I detected this as your Home location:\n\n${home.address}\n\nPlease confirm whether this is your correct Home location."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            confirmDetectedHome()
                        },
                        modifier =
                            Modifier.semantics {
                                contentDescription =
                                    "Confirm and save this Home location"
                            }
                    ) {
                        Text(
                            text =
                                "YES, SAVE HOME"
                        )
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            showHomeConfirmationDialog = false
                            detectedHome = null
                            statusMessage =
                                "Home location was not changed."
                        },
                        modifier =
                            Modifier.semantics {
                                contentDescription =
                                    "Do not save this Home location"
                            }
                    ) {
                        Text(
                            text =
                                "NO"
                        )
                    }
                }
            )
        }
    }

    if (showClearHomeDialog) {

        AlertDialog(
            onDismissRequest = {
                showClearHomeDialog = false
            },
            title = {
                Text(
                    text =
                        "Remove Saved Home"
                )
            },
            text = {
                Text(
                    text =
                        "Remove the saved Home location? Voice commands such as Go Home will no longer have a saved Home until you set one again."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        clearHome()
                    },
                    modifier =
                        Modifier.semantics {
                            contentDescription =
                                "Confirm removal of saved Home location"
                        }
                ) {
                    Text(
                        text =
                            "REMOVE"
                    )
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showClearHomeDialog = false
                    },
                    modifier =
                        Modifier.semantics {
                            contentDescription =
                                "Cancel removal of saved Home location"
                        }
                ) {
                    Text(
                        text =
                            nexusText(
                                AppTextKey.CANCEL
                            )
                    )
                }
            }
        )
    }

    if (showClearOfflineCustomDataDialog) {

        AlertDialog(
            onDismissRequest = {
                showClearOfflineCustomDataDialog = false
            },
            title = {
                Text(
                    text =
                        "Clear Custom Offline Data?"
                )
            },
            text = {
                Text(
                    text =
                        "This removes only custom offline entries stored on this device. The packaged offline knowledge remains unchanged."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        clearOfflineCustomData()
                    },
                    modifier =
                        Modifier.semantics {
                            contentDescription =
                                "Confirm clear custom offline data"
                        }
                ) {
                    Text(
                        text =
                            "CLEAR CUSTOM DATA"
                    )
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showClearOfflineCustomDataDialog = false
                    },
                    modifier =
                        Modifier.semantics {
                            contentDescription =
                                "Cancel clear custom offline data"
                        }
                ) {
                    Text(
                        text =
                            nexusText(
                                AppTextKey.CANCEL
                            )
                    )
                }
            }
        )
    }

    if (showClearApiKeyDialog) {

        AlertDialog(
            onDismissRequest = {
                showClearApiKeyDialog = false
            },
            title = {
                Text(
                    text = "Remove protected API key?"
                )
            },
            text = {
                Text(
                    text = "This removes the stored Gemini API key from protected device storage. You can enter it again later."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        clearApiKey()
                    },
                    modifier =
                        Modifier.semantics {
                            contentDescription =
                                "Confirm removal of protected API key"
                        }
                ) {
                    Text(
                        text = "REMOVE"
                    )
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showClearApiKeyDialog = false
                    },
                    modifier =
                        Modifier.semantics {
                            contentDescription =
                                "Cancel removal of protected API key"
                        }
                ) {
                    Text(
                        text = "CANCEL"
                    )
                }
            }
        )
    }

    if (showMaintenanceResetDialog) {

        AlertDialog(
            onDismissRequest = {
                showMaintenanceResetDialog = false
            },
            title = {
                Text(
                    text =
                        "Reset Application Settings?"
                )
            },
            text = {
                Text(
                    text =
                        "This restores language, speech speed, ESP32 preferences, Photo/Vision preferences, navigation stride, and custom offline entries. Your protected Gemini API key, saved Home location, setup role, and Vision photos will remain unchanged."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        resetUserSettings()
                    },
                    modifier =
                        Modifier.semantics {
                            contentDescription =
                                "Confirm reset application settings"
                        }
                ) {
                    Text(
                        text =
                            "RESET SETTINGS"
                    )
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showMaintenanceResetDialog = false
                    },
                    modifier =
                        Modifier.semantics {
                            contentDescription =
                                "Cancel reset application settings"
                        }
                ) {
                    Text(
                        text =
                            nexusText(
                                AppTextKey.CANCEL
                            )
                    )
                }
            }
        )
    }

    if (showResetDialog) {

        AlertDialog(
            onDismissRequest = {
                showResetDialog = false
            },
            title = {
                Text(
                    text =
                        nexusText(
                            AppTextKey.RESET_SETUP
                        )
                )
            },
            text = {
                Text(
                    text =
                        nexusText(
                            AppTextKey.RESET_SETUP_CONFIRMATION
                        )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetDialog = false
                        onResetSetup()

                        speakConfirmation(
                            ttsManager = ttsManager,
                            language = selectedSpeechLanguage,
                            text =
                                "Application setup has been reset."
                        )
                    },
                    modifier =
                        Modifier.semantics {
                            contentDescription =
                                "Confirm reset of application setup"
                        }
                ) {
                    Text(
                        text =
                            nexusText(
                                AppTextKey.RESET
                            )
                    )
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showResetDialog = false
                    },
                    modifier =
                        Modifier.semantics {
                            contentDescription =
                                "Cancel application reset"
                        }
                ) {
                    Text(
                        text =
                            nexusText(
                                AppTextKey.CANCEL
                            )
                    )
                }
            }
        )
    }

    DisposableEffect(maintenanceManager) {
        onDispose {
            maintenanceManager.close()
        }
    }
}

@Composable
private fun VisionSettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accessibilityDescription: String
) {

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics {
                contentDescription = accessibilityDescription
                stateDescription =
                    if (checked) "Enabled" else "Disabled"
            }
        )
    }
}

@Composable
private fun LanguageSelector(
    label: String,
    selectedLanguage: NexusEyeLanguage,
    onSelected: (
        NexusEyeLanguage
    ) -> Unit
) {

    var expanded by remember {
        mutableStateOf(false)
    }

    Column(
        modifier =
            Modifier.fillMaxWidth()
    ) {

        OutlinedButton(
            onClick = {
                expanded = true
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription =
                            "$label. Current selection: ${selectedLanguage.displayName}"
                    }
        ) {
            Text(
                text =
                    selectedLanguage.displayName
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
            }
        ) {

            NexusEyeLanguages
                .supportedLanguages
                .forEach { language ->

                    DropdownMenuItem(
                        text = {
                            Text(
                                text =
                                    language.displayName
                            )
                        },
                        onClick = {
                            onSelected(
                                language
                            )
                            expanded = false
                        },
                        modifier =
                            Modifier.semantics {
                                contentDescription =
                                    "Select ${language.displayName}"
                            }
                    )
                }
        }
    }
}

private suspend fun obtainCurrentLocation(
    context: Context,
    locationClient: FusedLocationProviderClient
): Location? {

    if (!hasLocationPermission(context)) {
        return null
    }

    val lastKnownLocation =
        suspendCoroutine<Location?> { continuation ->

            try {
                locationClient
                    .lastLocation
                    .addOnSuccessListener { location ->
                        continuation.resume(
                            location
                        )
                    }
                    .addOnFailureListener {
                        continuation.resume(
                            null
                        )
                    }
            } catch (_: SecurityException) {
                continuation.resume(
                    null
                )
            } catch (_: Exception) {
                continuation.resume(
                    null
                )
            }
        }

    if (lastKnownLocation != null) {
        return lastKnownLocation
    }

    return suspendCoroutine { continuation ->

        val cancellationTokenSource =
            CancellationTokenSource()

        try {
            locationClient
                .getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    cancellationTokenSource.token
                )
                .addOnSuccessListener { location ->
                    continuation.resume(
                        location
                    )
                }
                .addOnFailureListener {
                    continuation.resume(
                        null
                    )
                }
        } catch (_: SecurityException) {
            continuation.resume(
                null
            )
        } catch (_: Exception) {
            continuation.resume(
                null
            )
        }
    }
}

private fun hasLocationPermission(
    context: Context
): Boolean {

    val fineGranted =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    val coarseGranted =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    return fineGranted || coarseGranted
}

private fun speakConfirmation(
    ttsManager: NexusEyeTtsManager,
    language: NexusEyeLanguage,
    text: String
) {

    try {
        if (
            ttsManager.setLanguage(
                language
            )
        ) {
            ttsManager.speakOnPhoneFallback(
                text = text
            )
        }
    } catch (_: Exception) {
    }
}

private fun roundSpeechRate(
    value: Float
): Float {

    val rounded =
        (
                value * 20f
                ).roundToInt() / 20f

    return rounded.coerceIn(
        SPEECH_RATE_MIN,
        SPEECH_RATE_MAX
    )
}

private fun formatStrideMeters(value: Double): String {
    return String.format(
        Locale.US,
        "%.2f",
        value
    )
}

private fun roundStrideMeters(value: Double): Double {
    val rounded =
        kotlin.math.round(
            value * 100.0
        ) / 100.0

    return rounded.coerceIn(
        NexusEyeNavigationSettings.MIN_STRIDE_METERS,
        NexusEyeNavigationSettings.MAX_STRIDE_METERS
    )
}

private fun formatSpeechRate(
    value: Float
): String {

    return String.format(
        Locale.US,
        "%.2f",
        value
    )
}

private const val SPEECH_RATE_MIN =
    0.50f

private const val SPEECH_RATE_MAX =
    1.50f

private const val DEFAULT_SPEECH_RATE =
    0.88f

private const val SPEECH_RATE_STEPS =
    19
