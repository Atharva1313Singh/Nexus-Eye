package com.thirdeye.app.language

object AppStrings {

    private val english = mapOf(

        AppTextKey.APP_NAME to "NEXUS EYE",
        AppTextKey.WELCOME to "Welcome to NEXUS EYE",
        AppTextKey.INITIAL_SETUP to "Initial Setup",
        AppTextKey.SELECT_ROLE to "Who will use NEXUS EYE?",
        AppTextKey.BLIND_USER to "Blind User",
        AppTextKey.HELPER to "Helper",
        AppTextKey.CONTINUE to "Continue",
        AppTextKey.LANGUAGE_SETUP to "Language Setup",
        AppTextKey.APP_LANGUAGE to "App Language",
        AppTextKey.SPEECH_LANGUAGE to "Blind User Speech Language",
        AppTextKey.APP_LANGUAGE_DESCRIPTION to
                "This language controls the Android app interface.",
        AppTextKey.SPEECH_LANGUAGE_DESCRIPTION to
                "This language is used for speech recognition and future spoken responses through the wearable.",
        AppTextKey.SAVE_AND_CONTINUE to "Save and Continue",
        AppTextKey.HOME to "Home",
        AppTextKey.SETTINGS to "Settings",
        AppTextKey.COMMUNICATION to "Communication",
        AppTextKey.ESP32_STATUS to "ESP32 Status",
        AppTextKey.NOT_CONNECTED to "Not connected",
        AppTextKey.STAGE_ONE_READY to
                "NEXUS EYE uses ESP32 for simple fast tasks and Android for advanced processing.",
        AppTextKey.SETTINGS_TITLE to "Settings",
        AppTextKey.CURRENT_APP_LANGUAGE to "Current App Language",
        AppTextKey.CURRENT_SPEECH_LANGUAGE to
                "Current Blind User Speech Language",
        AppTextKey.SAVE to "Save",
        AppTextKey.BACK to "Back",
        AppTextKey.RESET_SETUP to "Reset Setup",
        AppTextKey.RESET_SETUP_CONFIRMATION to
                "This will clear the current setup and return NEXUS EYE to first-launch setup.",
        AppTextKey.CANCEL to "Cancel",
        AppTextKey.RESET to "Reset",
        AppTextKey.BLIND_USER_MODE to "Blind User Mode",
        AppTextKey.HELPER_MODE to "Helper Mode",

        AppTextKey.BLE_COMMUNICATION to "ESP32 Communication",
        AppTextKey.BLUETOOTH_UNAVAILABLE to
                "Bluetooth is not available on this phone.",
        AppTextKey.BLUETOOTH_DISABLED to
                "Bluetooth is turned off. Please turn it on.",
        AppTextKey.BLUETOOTH_PERMISSION_REQUIRED to
                "Bluetooth permission is required.",
        AppTextKey.SCAN_FOR_DEVICES to "Scan for BLE Devices",
        AppTextKey.STOP_SCAN to "Stop Scan",
        AppTextKey.AVAILABLE_DEVICES to "Available BLE Devices",
        AppTextKey.NO_DEVICES_FOUND to
                "No BLE devices found yet.",
        AppTextKey.CONNECT to "Connect",
        AppTextKey.DISCONNECT to "Disconnect",
        AppTextKey.CONNECTION to "Connection",
        AppTextKey.DISCONNECTED to "Disconnected",
        AppTextKey.SCANNING to "Scanning",
        AppTextKey.CONNECTING to "Connecting",
        AppTextKey.CONNECTED to "Connected",
        AppTextKey.READY to "Ready",
        AppTextKey.DISCONNECTING to "Disconnecting",
        AppTextKey.ERROR to "Error",
        AppTextKey.SEND_PING to "Send PING",
        AppTextKey.SEND_STATUS to "Send STATUS",
        AppTextKey.LAST_RECEIVED to "Last Received Message",
        AppTextKey.NO_MESSAGE_RECEIVED to
                "No message received yet.",
        AppTextKey.CONNECTION_ERROR to "BLE Error",

        AppTextKey.VOICE_SYSTEM to "Voice System",
        AppTextKey.START_LISTENING to "Start Listening",
        AppTextKey.STOP_LISTENING to "Stop Listening",
        AppTextKey.PARTIAL_RESULT to "Live Speech",
        AppTextKey.RECOGNIZED_TEXT to "Recognized Text",
        AppTextKey.AUDIO_OUTPUT_POLICY to
                "Assistant responses are reserved for the NEXUS EYE wearable speaker. The phone speaker is not used for assistant responses.",

        AppTextKey.INTELLIGENCE to "NEXUS EYE Assistant",
        AppTextKey.PROCESSING to "Processing...",
        AppTextKey.ANSWER to "Answer",
        AppTextKey.RESPONSE_SOURCE to "Response Source",
        AppTextKey.TYPE_QUESTION to "Type or dictate a question",
        AppTextKey.ASK to "Ask",
        AppTextKey.OFFLINE_DATABASE to "Offline Database",
        AppTextKey.CALCULATOR to "Calculator",
        AppTextKey.DEVICE to "Device",
        AppTextKey.ONLINE to "Online",
        AppTextKey.UNKNOWN to "Unknown"
    )

    private val hindi = mapOf(

        AppTextKey.APP_NAME to "NEXUS EYE",
        AppTextKey.WELCOME to "NEXUS EYE में आपका स्वागत है",
        AppTextKey.INITIAL_SETUP to "प्रारंभिक सेटअप",
        AppTextKey.SELECT_ROLE to "NEXUS EYE का उपयोग कौन करेगा?",
        AppTextKey.BLIND_USER to "दृष्टिबाधित उपयोगकर्ता",
        AppTextKey.HELPER to "सहायक",
        AppTextKey.CONTINUE to "जारी रखें",
        AppTextKey.LANGUAGE_SETUP to "भाषा सेटअप",
        AppTextKey.APP_LANGUAGE to "ऐप भाषा",
        AppTextKey.SPEECH_LANGUAGE to
                "दृष्टिबाधित उपयोगकर्ता की बोलने की भाषा",
        AppTextKey.APP_LANGUAGE_DESCRIPTION to
                "यह भाषा Android ऐप के इंटरफेस को नियंत्रित करती है।",
        AppTextKey.SPEECH_LANGUAGE_DESCRIPTION to
                "इस भाषा का उपयोग speech recognition और आगे चलकर wearable से बोले जाने वाले जवाबों के लिए होगा।",
        AppTextKey.SAVE_AND_CONTINUE to "सहेजें और जारी रखें",
        AppTextKey.HOME to "होम",
        AppTextKey.SETTINGS to "सेटिंग्स",
        AppTextKey.COMMUNICATION to "कम्युनिकेशन",
        AppTextKey.ESP32_STATUS to "ESP32 स्थिति",
        AppTextKey.NOT_CONNECTED to "कनेक्ट नहीं है",
        AppTextKey.STAGE_ONE_READY to
                "NEXUS EYE simple fast tasks के लिए ESP32 और advanced processing के लिए Android का उपयोग करता है।",
        AppTextKey.SETTINGS_TITLE to "सेटिंग्स",
        AppTextKey.CURRENT_APP_LANGUAGE to "वर्तमान ऐप भाषा",
        AppTextKey.CURRENT_SPEECH_LANGUAGE to
                "वर्तमान दृष्टिबाधित उपयोगकर्ता की बोलने की भाषा",
        AppTextKey.SAVE to "सहेजें",
        AppTextKey.BACK to "वापस",
        AppTextKey.RESET_SETUP to "सेटअप रीसेट करें",
        AppTextKey.RESET_SETUP_CONFIRMATION to
                "इससे वर्तमान सेटअप साफ हो जाएगा और NEXUS EYE पहली बार वाले सेटअप पर वापस आ जाएगा।",
        AppTextKey.CANCEL to "रद्द करें",
        AppTextKey.RESET to "रीसेट",
        AppTextKey.BLIND_USER_MODE to "दृष्टिबाधित उपयोगकर्ता मोड",
        AppTextKey.HELPER_MODE to "सहायक मोड",

        AppTextKey.BLE_COMMUNICATION to "ESP32 कम्युनिकेशन",
        AppTextKey.BLUETOOTH_UNAVAILABLE to
                "इस फोन में Bluetooth उपलब्ध नहीं है।",
        AppTextKey.BLUETOOTH_DISABLED to
                "Bluetooth बंद है। कृपया इसे चालू करें।",
        AppTextKey.BLUETOOTH_PERMISSION_REQUIRED to
                "Bluetooth की अनुमति आवश्यक है।",
        AppTextKey.SCAN_FOR_DEVICES to "BLE डिवाइस खोजें",
        AppTextKey.STOP_SCAN to "स्कैन रोकें",
        AppTextKey.AVAILABLE_DEVICES to "उपलब्ध BLE डिवाइस",
        AppTextKey.NO_DEVICES_FOUND to
                "अभी कोई BLE डिवाइस नहीं मिला।",
        AppTextKey.CONNECT to "कनेक्ट करें",
        AppTextKey.DISCONNECT to "डिस्कनेक्ट करें",
        AppTextKey.CONNECTION to "कनेक्शन",
        AppTextKey.DISCONNECTED to "डिस्कनेक्टेड",
        AppTextKey.SCANNING to "स्कैन हो रहा है",
        AppTextKey.CONNECTING to "कनेक्ट हो रहा है",
        AppTextKey.CONNECTED to "कनेक्टेड",
        AppTextKey.READY to "तैयार",
        AppTextKey.DISCONNECTING to "डिस्कनेक्ट हो रहा है",
        AppTextKey.ERROR to "त्रुटि",
        AppTextKey.SEND_PING to "PING भेजें",
        AppTextKey.SEND_STATUS to "STATUS भेजें",
        AppTextKey.LAST_RECEIVED to "अंतिम प्राप्त संदेश",
        AppTextKey.NO_MESSAGE_RECEIVED to
                "अभी कोई संदेश प्राप्त नहीं हुआ।",
        AppTextKey.CONNECTION_ERROR to "BLE त्रुटि",

        AppTextKey.VOICE_SYSTEM to "वॉइस सिस्टम",
        AppTextKey.START_LISTENING to "सुनना शुरू करें",
        AppTextKey.STOP_LISTENING to "सुनना रोकें",
        AppTextKey.PARTIAL_RESULT to "लाइव आवाज़",
        AppTextKey.RECOGNIZED_TEXT to "पहचाना गया टेक्स्ट",
        AppTextKey.AUDIO_OUTPUT_POLICY to
                "Assistant के जवाब NEXUS EYE wearable speaker के लिए निर्धारित हैं। फोन speaker का उपयोग assistant के जवाबों के लिए नहीं किया जाता।",

        AppTextKey.INTELLIGENCE to "NEXUS EYE सहायक",
        AppTextKey.PROCESSING to "प्रोसेसिंग हो रही है...",
        AppTextKey.ANSWER to "उत्तर",
        AppTextKey.RESPONSE_SOURCE to "उत्तर का स्रोत",
        AppTextKey.TYPE_QUESTION to "प्रश्न लिखें या बोलें",
        AppTextKey.ASK to "पूछें",
        AppTextKey.OFFLINE_DATABASE to "ऑफलाइन डेटाबेस",
        AppTextKey.CALCULATOR to "कैलकुलेटर",
        AppTextKey.DEVICE to "डिवाइस",
        AppTextKey.ONLINE to "ऑनलाइन",
        AppTextKey.UNKNOWN to "अज्ञात"
    )

    fun getText(
        languageId: String,
        key: AppTextKey
    ): String {

        val selectedMap =
            when (languageId) {
                NexusEyeLanguages.Hindi.id ->
                    hindi

                else ->
                    english
            }

        return selectedMap[key]
            ?: english[key]
            ?: key.name
    }
}