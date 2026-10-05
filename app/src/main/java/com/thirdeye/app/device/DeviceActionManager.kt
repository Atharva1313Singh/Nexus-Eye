package com.thirdeye.app.device

import android.Manifest
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

class DeviceActionManager(
    context: Context
) {

    private val appContext =
        context.applicationContext

    data class Result(
        val handled: Boolean,
        val answer: String,
        val requiredPermissions: List<String> = emptyList()
    )

    private data class ParsedCommand(
        val type: CommandType,
        val contactQuery: String = "",
        val message: String = "",
        val value: String = ""
    )

    private enum class CommandType {
        CALL,
        MESSAGE,
        OPEN_APP,
        WEB_SEARCH,
        OPEN_SETTINGS,
        OPEN_CAMERA,
        OPEN_MAPS,
        ACCESSIBILITY_BACK,
        ACCESSIBILITY_HOME,
        ACCESSIBILITY_RECENTS,
        ACCESSIBILITY_NOTIFICATIONS,
        ACCESSIBILITY_QUICK_SETTINGS,
        ACCESSIBILITY_CLICK,
        ACCESSIBILITY_TYPE,
        ACCESSIBILITY_SCROLL,
        ACCESSIBILITY_READ_SCREEN,
        ACCESSIBILITY_SEARCH_MESSAGES
    }

    /*
     * ============================================================
     * MAIN ENTRY POINT
     * ============================================================
     */

    fun tryHandle(
        query: String
    ): Result {

        val parsed =
            parseCommand(query)
                ?: return Result(
                    handled = false,
                    answer = ""
                )

        when (parsed.type) {

            CommandType.OPEN_APP ->
                return openApplication(parsed.value)

            CommandType.WEB_SEARCH ->
                return webSearch(parsed.value)

            CommandType.OPEN_SETTINGS ->
                return openSystemIntent(
                    Intent(
                        android.provider.Settings.ACTION_SETTINGS
                    ),
                    "Opening phone settings."
                )

            CommandType.OPEN_CAMERA ->
                return openSystemIntent(
                    Intent(
                        "android.media.action.IMAGE_CAPTURE"
                    ),
                    "Opening the camera."
                )

            CommandType.OPEN_MAPS ->
                return openSystemIntent(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(
                            "geo:0,0?q=" +
                                    Uri.encode(parsed.value)
                        )
                    ),
                    "Opening Maps for ${parsed.value}."
                )

            CommandType.ACCESSIBILITY_BACK ->
                return accessibilityResult(
                    NexusEyeActionWorkflow.accessibilityBack(),
                    "Going back."
                )

            CommandType.ACCESSIBILITY_HOME ->
                return accessibilityResult(
                    NexusEyeActionWorkflow.accessibilityHome(),
                    "Going to the home screen."
                )

            CommandType.ACCESSIBILITY_RECENTS ->
                return accessibilityResult(
                    NexusEyeActionWorkflow.accessibilityRecents(),
                    "Opening recent apps."
                )

            CommandType.ACCESSIBILITY_NOTIFICATIONS ->
                return accessibilityResult(
                    NexusEyeActionWorkflow.accessibilityNotifications(),
                    "Opening notifications."
                )

            CommandType.ACCESSIBILITY_QUICK_SETTINGS ->
                return accessibilityResult(
                    NexusEyeActionWorkflow.accessibilityQuickSettings(),
                    "Opening quick settings."
                )

            CommandType.ACCESSIBILITY_CLICK ->
                return accessibilityResult(
                    NexusEyeActionWorkflow.accessibilityClick(parsed.value),
                    "Activating ${parsed.value}."
                )

            CommandType.ACCESSIBILITY_TYPE ->
                return accessibilityResult(
                    NexusEyeActionWorkflow.accessibilityType(parsed.value),
                    "Typing your text."
                )

            CommandType.ACCESSIBILITY_SCROLL ->
                return accessibilityResult(
                    NexusEyeActionWorkflow.accessibilityScroll(parsed.value),
                    "Scrolling ${parsed.value}."
                )

            CommandType.ACCESSIBILITY_READ_SCREEN -> {
                val text = NexusEyeActionWorkflow.accessibilityReadScreen()
                return if (text.isBlank()) {
                    Result(true, "I cannot read the current screen. Please make sure Accessibility access is enabled.")
                } else {
                    Result(true, text)
                }
            }

            CommandType.ACCESSIBILITY_SEARCH_MESSAGES ->
                return accessibilityResult(
                    NexusEyeActionWorkflow.accessibilitySearchMessages(parsed.value),
                    "Searching messages for ${parsed.value}."
                )

            else -> Unit
        }

        /*
         * Contacts permission is required for both calls and
         * WhatsApp messages because NEXUS EYE needs to find the
         * requested contact.
         */

        if (
            !hasPermission(
                Manifest.permission.READ_CONTACTS
            )
        ) {

            return Result(
                handled = true,
                answer =
                    "I need Contacts permission to find ${parsed.contactQuery}.",
                requiredPermissions =
                    listOf(
                        Manifest.permission.READ_CONTACTS
                    )
            )
        }

        /*
         * Calling requires CALL_PHONE.
         */

        if (
            parsed.type ==
            CommandType.CALL &&
            !hasPermission(
                Manifest.permission.CALL_PHONE
            )
        ) {

            return Result(
                handled = true,
                answer =
                    "I need Phone permission to call ${parsed.contactQuery}.",
                requiredPermissions =
                    listOf(
                        Manifest.permission.CALL_PHONE
                    )
            )
        }

        val contact =
            findContact(
                parsed.contactQuery
            )

        if (contact == null) {

            return Result(
                handled = true,
                answer =
                    "I could not find ${parsed.contactQuery} in your contacts."
            )
        }

        return when (
            parsed.type
        ) {

            CommandType.CALL ->
                callContact(
                    contactName = contact.first,
                    phoneNumber = contact.second
                )

            CommandType.MESSAGE ->
                messageContact(
                    contactName = contact.first,
                    phoneNumber = contact.second,
                    message = parsed.message
                )

            else ->
                Result(
                    handled = false,
                    answer = ""
                )
        }
    }

    /*
     * ============================================================
     * COMMAND PARSING
     * ============================================================
     */

    private fun parseCommand(
        query: String
    ): ParsedCommand? {

        val rawQuery = query.trim()
        val normalized =
            normalize(rawQuery)

        if (normalized.isBlank()) {
            return null
        }

        /*
         * --------------------------------------------------------
         * SYSTEM CONTROL
         * --------------------------------------------------------
         */

        val settingsCommands =
            listOf(
                "open settings",
                "open phone settings",
                "phone settings",
                "सेटिंग खोलो",
                "सेटिंग्स खोलो"
            )

        if (
            settingsCommands.any {
                normalized == it
            }
        ) {

            return ParsedCommand(
                type =
                    CommandType.OPEN_SETTINGS
            )
        }

        val cameraCommands =
            listOf(
                "open camera",
                "launch camera",
                "camera kholo",
                "कैमरा खोलो"
            )

        if (
            cameraCommands.any {
                normalized == it
            }
        ) {

            return ParsedCommand(
                type =
                    CommandType.OPEN_CAMERA
            )
        }

        /*
         * --------------------------------------------------------
         * MAPS
         * --------------------------------------------------------
         */

        val mapsPrefixes =
            listOf(
                "open maps for ",
                "open map for ",
                "show maps for ",
                "maps for ",
                "मैप खोलो "
            )

        for (
        prefix in mapsPrefixes
        ) {

            if (
                normalized.startsWith(prefix)
            ) {

                val destination =
                    normalized
                        .removePrefix(prefix)
                        .trim()

                if (
                    destination.isNotBlank()
                ) {

                    return ParsedCommand(
                        type =
                            CommandType.OPEN_MAPS,
                        value =
                            destination
                    )
                }
            }
        }

        /*
         * --------------------------------------------------------
         * GENERIC ACCESSIBILITY / VOICE CONTROL
         * --------------------------------------------------------
         * These commands work through the user-enabled accessibility
         * service. They provide a general control layer for apps whose
         * UI is exposed through AccessibilityNodeInfo, instead of relying
         * on app-specific automation for every package.
         */

        when (normalized) {
            "go back", "back", "पीछे जाओ", "वापस जाओ" ->
                return ParsedCommand(CommandType.ACCESSIBILITY_BACK)
            "go home", "home screen", "open home", "होम खोलो" ->
                return ParsedCommand(CommandType.ACCESSIBILITY_HOME)
            "recent apps", "show recent apps", "हाल के ऐप" ->
                return ParsedCommand(CommandType.ACCESSIBILITY_RECENTS)
            "open notifications", "show notifications", "notifications खोलो" ->
                return ParsedCommand(CommandType.ACCESSIBILITY_NOTIFICATIONS)
            "open quick settings", "quick settings", "त्वरित सेटिंग" ->
                return ParsedCommand(CommandType.ACCESSIBILITY_QUICK_SETTINGS)
            "read screen", "read this screen", "what is on the screen", "स्क्रीन पढ़ो", "स्क्रीन पर क्या है" ->
                return ParsedCommand(CommandType.ACCESSIBILITY_READ_SCREEN)
        }

        val clickPrefixes = listOf("click ", "tap ", "press ", "क्लिक ", "दबाओ ")
        clickPrefixes.firstOrNull { normalized.startsWith(it) }?.let { prefix ->
            val target = normalized.removePrefix(prefix).trim()
            if (target.isNotBlank()) {
                return ParsedCommand(CommandType.ACCESSIBILITY_CLICK, value = target)
            }
        }

        val typePrefixes = listOf("type ", "enter text ", "write ", "टाइप ", "लिखो ")
        typePrefixes.firstOrNull { normalized.startsWith(it) }?.let { prefix ->
            val text = rawQuery.substring(prefix.length).trim()
            if (text.isNotBlank()) {
                return ParsedCommand(CommandType.ACCESSIBILITY_TYPE, value = text)
            }
        }

        val scrollPrefixes = listOf("scroll ", "स्क्रॉल ")
        scrollPrefixes.firstOrNull { normalized.startsWith(it) }?.let { prefix ->
            val direction = normalized.removePrefix(prefix).trim().ifBlank { "down" }
            return ParsedCommand(CommandType.ACCESSIBILITY_SCROLL, value = direction)
        }

        val messageSearchPrefixes = listOf(
            "search messages for ",
            "search messages ",
            "find messages for ",
            "find messages "
        )
        messageSearchPrefixes.firstOrNull { normalized.startsWith(it) }?.let { prefix ->
            val search = normalized.removePrefix(prefix).trim()
            if (search.isNotBlank()) {
                return ParsedCommand(CommandType.ACCESSIBILITY_SEARCH_MESSAGES, value = search)
            }
        }

        /*
         * --------------------------------------------------------
         * WEB SEARCH
         * --------------------------------------------------------
         */

        val searchPrefixes =
            listOf(
                "search for ",
                "search ",
                "google ",
                "look up ",
                "find online ",
                "सर्च करो ",
                "खोजो "
            )

        for (
        prefix in searchPrefixes
        ) {

            if (
                normalized.startsWith(prefix)
            ) {

                val search =
                    normalized
                        .removePrefix(prefix)
                        .trim()

                if (
                    search.isNotBlank()
                ) {

                    return ParsedCommand(
                        type =
                            CommandType.WEB_SEARCH,
                        value =
                            search
                    )
                }
            }
        }

        /*
         * --------------------------------------------------------
         * OPEN APPLICATION
         * --------------------------------------------------------
         */

        val openAppPrefixes =
            listOf(
                "open app ",
                "launch app ",
                "open ",
                "launch ",
                "start ",
                "ऐप खोलो ",
                "खोलो "
            )

        for (
        prefix in openAppPrefixes
        ) {

            if (
                normalized.startsWith(prefix)
            ) {

                val appName =
                    normalized
                        .removePrefix(prefix)
                        .trim()

                if (
                    appName.isNotBlank()
                ) {

                    return ParsedCommand(
                        type =
                            CommandType.OPEN_APP,
                        value =
                            appName
                    )
                }
            }
        }

        /*
         * Some speech recognizers drop the imperative word (for example,
         * "open") and return only the app name. If the recognized text is
         * exactly a known app alias, treat it as an app-open command too.
         * This keeps voice app launching reliable without changing the
         * existing command grammar.
         */
        val knownAppAliases = setOf(
            "whatsapp",
            "youtube",
            "chrome",
            "google chrome",
            "gmail",
            "google maps",
            "maps",
            "instagram",
            "facebook",
            "telegram",
            "spotify",
            "fc mobile",
            "fcmobile",
            "ea sports fc mobile",
            "ea sports fc football mobile",
            "fifa mobile",
            "phone",
            "dialer",
            "camera"
        )

        if (knownAppAliases.contains(normalized)) {
            return ParsedCommand(
                type = CommandType.OPEN_APP,
                value = normalized
            )
        }

        /*
         * --------------------------------------------------------
         * CALL
         * --------------------------------------------------------
         */

        val callPrefixes =
            listOf(
                "call ",
                "phone ",
                "dial ",
                "कॉल ",
                "फोन "
            )

        for (
        prefix in callPrefixes
        ) {

            if (
                normalized.startsWith(prefix)
            ) {

                val contact =
                    normalized
                        .removePrefix(prefix)
                        .trim()

                if (
                    contact.isNotBlank()
                ) {

                    return ParsedCommand(
                        type =
                            CommandType.CALL,
                        contactQuery =
                            contact
                    )
                }
            }
        }

        /*
         * --------------------------------------------------------
         * WHATSAPP / MESSAGE
         * --------------------------------------------------------
         */

        val messagePrefixes =
            listOf(
                "whatsapp ",
                "send whatsapp to ",
                "message ",
                "text ",
                "send a message to ",
                "send message to ",
                "मैसेज ",
                "संदेश "
            )

        for (prefix in messagePrefixes) {
            if (!normalized.startsWith(prefix)) continue

            val remainingRaw = rawQuery.substring(prefix.length).trim()
            val remainingNormalized = normalize(remainingRaw)
            if (remainingRaw.isBlank()) continue

            // Preserve the user's original message text. The previous
            // implementation normalized the entire command first, which
            // silently lower-cased messages and removed punctuation.
            val separatorPatterns = listOf(
                " that says ",
                " saying ",
                ":",
                " - ",
                " that say "
            )

            val lowerRaw = remainingRaw.lowercase(Locale.ROOT)
            val separator = separatorPatterns
                .map { it to lowerRaw.indexOf(it.lowercase(Locale.ROOT)) }
                .filter { it.second > 0 }
                .minByOrNull { it.second }

            if (separator != null) {
                val index = separator.second
                val contact = remainingRaw.substring(0, index).trim()
                val message = remainingRaw.substring(index + separator.first.length).trim()
                if (contact.isNotBlank() && message.isNotBlank()) {
                    return ParsedCommand(
                        type = CommandType.MESSAGE,
                        contactQuery = contact,
                        message = message
                    )
                }
            }

            val toIndex = remainingNormalized.lastIndexOf(" to ")
            if (toIndex > 0 && toIndex < remainingNormalized.length - 4) {
                val message = remainingRaw.substring(0, toIndex).trim()
                val contact = remainingRaw.substring(toIndex + 4).trim()
                if (contact.isNotBlank() && message.isNotBlank()) {
                    return ParsedCommand(
                        type = CommandType.MESSAGE,
                        contactQuery = contact,
                        message = message
                    )
                }
            }

            return ParsedCommand(
                type = CommandType.MESSAGE,
                contactQuery = remainingRaw,
                message = ""
            )
        }

        return null
    }

    /*
     * ============================================================
     * CONTACT LOOKUP
     * ============================================================
     */

    private fun findContact(
        contactQuery: String
    ): Pair<String, String>? {

        val normalizedQuery =
            normalize(
                contactQuery
            )

        if (
            normalizedQuery.isBlank()
        ) {
            return null
        }

        val projection =
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )

        val cursor =
            appContext.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME +
                        " ASC"
            )
                ?: return null

        cursor.use {

            var bestName:
                    String? = null

            var bestNumber:
                    String? = null

            var bestScore =
                0

            while (
                it.moveToNext()
            ) {

                val name =
                    it.getString(0)
                        ?: continue

                val number =
                    it.getString(1)
                        ?: continue

                val normalizedName =
                    normalize(name)

                if (
                    normalizedName.isBlank()
                ) {
                    continue
                }

                val score =
                    contactMatchScore(
                        query =
                            normalizedQuery,
                        name =
                            normalizedName
                    )

                if (
                    score > bestScore
                ) {

                    bestScore =
                        score

                    bestName =
                        name

                    bestNumber =
                        number
                }
            }

            if (
                bestName != null &&
                bestNumber != null
            ) {

                return Pair(
                    bestName,
                    bestNumber
                )
            }
        }

        return null
    }

    private fun contactMatchScore(
        query: String,
        name: String
    ): Int {

        if (
            query == name
        ) {
            return 1000
        }

        if (
            name.startsWith(query)
        ) {
            return 900
        }

        if (
            name.contains(query)
        ) {
            return 800
        }

        val queryWords =
            query
                .split(
                    Regex("\\s+")
                )
                .filter {
                    it.isNotBlank()
                }

        if (
            queryWords.isEmpty()
        ) {
            return 0
        }

        var matched =
            0

        for (
        word in queryWords
        ) {

            if (
                name.contains(word)
            ) {
                matched++
            }
        }

        return if (
            matched > 0
        ) {
            500 + matched * 50
        } else {
            0
        }
    }

    /*
     * ============================================================
     * CALL
     * ============================================================
     */

    private fun callContact(
        contactName: String,
        phoneNumber: String
    ): Result {

        if (
            !hasPermission(
                Manifest.permission.CALL_PHONE
            )
        ) {

            return Result(
                handled = true,
                answer =
                    "I need Phone permission to call $contactName.",
                requiredPermissions =
                    listOf(
                        Manifest.permission.CALL_PHONE
                    )
            )
        }

        val cleanNumber =
            phoneNumber.trim()

        if (
            cleanNumber.isBlank()
        ) {

            return Result(
                handled = true,
                answer =
                    "I could not find a phone number for $contactName."
            )
        }

        NexusEyeActionWorkflow.beginCall(
            contactName =
                contactName,
            phoneNumber =
                cleanNumber
        )

        return try {

            val intent =
                Intent(
                    Intent.ACTION_CALL
                ).apply {

                    data =
                        Uri.parse(
                            "tel:${Uri.encode(cleanNumber)}"
                        )

                    flags =
                        Intent.FLAG_ACTIVITY_NEW_TASK
                }

            launchActivityForVoiceCommand(
                intent
            )

            Result(
                handled = true,
                answer =
                    "Calling $contactName."
            )

        } catch (
            exception: Exception
        ) {

            NexusEyeActionWorkflow.cancel()

            Result(
                handled = true,
                answer =
                    "I could not start the call to $contactName."
            )
        }
    }

    /*
     * ============================================================
     * WHATSAPP
     * ============================================================
     */

    private fun messageContact(
        contactName: String,
        phoneNumber: String,
        message: String
    ): Result {

        val cleanNumber =
            phoneNumber.filter {
                it.isDigit()
            }

        if (
            cleanNumber.isBlank()
        ) {

            return Result(
                handled = true,
                answer =
                    "I could not find a phone number for $contactName."
            )
        }

        val encodedMessage =
            if (
                message.isNotBlank()
            ) {

                URLEncoder.encode(
                    message,
                    StandardCharsets.UTF_8.toString()
                )

            } else {
                ""
            }

        val whatsappUrl =
            if (
                encodedMessage.isNotBlank()
            ) {

                "https://wa.me/$cleanNumber?text=$encodedMessage"

            } else {

                "https://wa.me/$cleanNumber"
            }

        if (
            !NexusEyeActionWorkflow.isAccessibilityServiceEnabled(
                appContext
            )
        ) {

            return Result(
                handled = true,
                answer =
                    "NEXUS EYE accessibility control is not enabled. Please enable it in Accessibility settings before I can automatically send WhatsApp messages."
            )
        }

        NexusEyeActionWorkflow.beginWhatsAppMessage(
            contactName =
                contactName,
            phoneNumber =
                cleanNumber,
            message =
                message
        )

        return try {

            val whatsappIntent =
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(
                        whatsappUrl
                    )
                ).apply {

                    setPackage(
                        "com.whatsapp"
                    )

                    flags =
                        Intent.FLAG_ACTIVITY_NEW_TASK
                }

            launchActivityForVoiceCommand(
                whatsappIntent
            )

            Result(
                handled = true,
                answer =
                    if (
                        message.isBlank()
                    ) {
                        "Opening WhatsApp for $contactName."
                    } else {
                        "Sending the WhatsApp message to $contactName."
                    }
            )

        } catch (
            exception: Exception
        ) {

            NexusEyeActionWorkflow.cancel()

            Result(
                handled = true,
                answer =
                    "I could not open WhatsApp for $contactName."
            )
        }
    }

    /*
     * ============================================================
     * APPLICATION LAUNCH
     * ============================================================
     */

    private fun openApplication(
        requestedName: String
    ): Result {

        val normalizedName =
            normalize(
                requestedName
            )

        /*
         * Explicit application package mappings.
         *
         * FC Mobile is deliberately included so that:
         *
         * "open FC Mobile"
         *
         * does not depend on launcher-label matching.
         */

        val knownPackages =
            mapOf(

                "whatsapp" to
                        "com.whatsapp",

                "youtube" to
                        "com.google.android.youtube",

                "chrome" to
                        "com.android.chrome",

                "google chrome" to
                        "com.android.chrome",

                "gmail" to
                        "com.google.android.gm",

                "google maps" to
                        "com.google.android.apps.maps",

                "maps" to
                        "com.google.android.apps.maps",

                "instagram" to
                        "com.instagram.android",

                "facebook" to
                        "com.facebook.katana",

                "telegram" to
                        "org.telegram.messenger",

                "spotify" to
                        "com.spotify.music",

                /*
                 * EA SPORTS FC Mobile
                 */

                "fc mobile" to
                        "com.ea.gp.fifamobile",

                "fcmobile" to
                        "com.ea.gp.fifamobile",

                "fcmobile" to
                        "com.ea.gp.fifamobile",

                "ea sports fc mobile" to
                        "com.ea.gp.fifamobile",

                "ea sports fc football mobile" to
                        "com.ea.gp.fifamobile",

                "fifa mobile" to
                        "com.ea.gp.fifamobile",

                "phone" to
                        "com.google.android.dialer",

                "dialer" to
                        "com.google.android.dialer",

                "camera" to
                        "com.android.camera"
            )

        /*
         * --------------------------------------------------------
         * 1. KNOWN PACKAGE
         * --------------------------------------------------------
         */

        val packageName =
            knownPackages[
                normalizedName
            ]

        if (
            packageName != null
        ) {

            val packageIntent =
                appContext
                    .packageManager
                    .getLaunchIntentForPackage(
                        packageName
                    )

            if (
                packageIntent == null
            ) {

                return Result(
                    handled = true,
                    answer =
                        "$requestedName is not installed on this phone."
                )
            }

            packageIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            )

            return try {

                launchActivityForVoiceCommand(
                    packageIntent
                )

                Result(
                    handled = true,
                    answer =
                        "Opening $requestedName."
                )

            } catch (
                exception: ActivityNotFoundException
            ) {

                Result(
                    handled = true,
                    answer =
                        "I could not open $requestedName."
                )

            } catch (
                exception: SecurityException
            ) {

                Result(
                    handled = true,
                    answer =
                        "Android blocked opening $requestedName from the background."
                )

            } catch (
                exception: Exception
            ) {

                Result(
                    handled = true,
                    answer =
                        "I could not open $requestedName."
                )
            }
        }

        /*
         * --------------------------------------------------------
         * 2. SEARCH INSTALLED LAUNCHER APPS
         * --------------------------------------------------------
         */

        val launcherIntent =
            Intent(
                Intent.ACTION_MAIN
            ).apply {

                addCategory(
                    Intent.CATEGORY_LAUNCHER
                )
            }

        val matches =
            appContext
                .packageManager
                .queryIntentActivities(
                    launcherIntent,
                    0
                )

        val match =
            matches.firstOrNull {
                    info ->

                val label =
                    info
                        .loadLabel(
                            appContext.packageManager
                        )
                        ?.toString()
                        ?.let(
                            ::normalize
                        )
                        ?: return@firstOrNull false

                label == normalizedName ||
                        label.contains(
                            normalizedName
                        ) ||
                        normalizedName.contains(
                            label
                        )
            }

        if (
            match != null
        ) {

            val launchIntent =
                appContext
                    .packageManager
                    .getLaunchIntentForPackage(
                        match.activityInfo.packageName
                    )

            if (
                launchIntent != null
            ) {

                launchIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                )

                return try {

                    launchActivityForVoiceCommand(
                        launchIntent
                    )

                    Result(
                        handled = true,
                        answer =
                            "Opening $requestedName."
                    )

                } catch (
                    exception: Exception
                ) {

                    Result(
                        handled = true,
                        answer =
                            "I could not open $requestedName."
                    )
                }
            }
        }

        return Result(
            handled = true,
            answer =
                "I could not find an installed app named $requestedName."
        )
    }

    /*
     * ============================================================
     * BACKGROUND ACTIVITY LAUNCH
     * ============================================================
     *
     * Android 14/15 introduced stricter BAL rules.
     *
     * We therefore:
     *
     * 1. Try the normal activity launch first.
     * 2. On Android 14+, create a PendingIntent with explicit
     *    creator-side BAL permission.
     * 3. Send it with explicit sender-side BAL permission.
     *
     * IMPORTANT:
     *
     * If the device/OEM still returns BAL_BLOCK, Android has decided
     * that this background service is not an allowed activity launcher.
     * Kotlin code cannot override that system decision.
     *
     * In that case NEXUS EYE must be converted to a system-approved
     * interaction path such as VoiceInteractionService or another
     * user-authorized system role.
     */

    private fun launchActivityForVoiceCommand(
        intent: Intent
    ) {

        /*
         * Make sure the target intent is explicitly launchable
         * from an application context.
         */

        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
        )

        /*
         * --------------------------------------------------------
         * API < 34
         * --------------------------------------------------------
         */

        if (
            Build.VERSION.SDK_INT < 34
        ) {

            appContext.startActivity(
                intent
            )

            return
        }

        /*
         * --------------------------------------------------------
         * API 34+
         * --------------------------------------------------------
         *
         * The voice service is a background caller. Android 14/15 can
         * still reject a PendingIntent with BAL_BLOCK even when the
         * PendingIntent creator and sender both opt in.
         *
         * NEXUS EYE already has a user-enabled AccessibilityService,
         * which is the existing system-mediated UI automation path in
         * this app. Use it first so an app-open command does not depend
         * on the foreground-service PendingIntent BAL decision.
         */

        Log.d(
            "NexusEyeDeviceAction",
            "Trying external activity launch through AccessibilityService first"
        )

        if (
            NexusEyeActionWorkflow.launchExternalActivity(intent)
        ) {

            Log.d(
                "NexusEyeDeviceAction",
                "External activity launch accepted through enabled AccessibilityService"
            )

            return
        }

        Log.w(
            "NexusEyeDeviceAction",
            "AccessibilityService launch unavailable; falling back to PendingIntent BAL path"
        )

        val requestCode =
            (
                    System.identityHashCode(
                        intent
                    )
                            and
                            0x7fffffff
                    )

        /*
         * Android 15 requires the PendingIntent creator to
         * explicitly opt into BAL.
         */

        val creatorOptions =
            ActivityOptions.makeBasic().apply {

                pendingIntentCreatorBackgroundActivityStartMode =
                    ActivityOptions
                        .MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            }

        val pendingIntent =
            PendingIntent.getActivity(
                appContext,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE,
                creatorOptions.toBundle()
            )

        /*
         * The sender also explicitly opts into BAL.
         */

        val senderOptions =
            ActivityOptions.makeBasic().apply {

                pendingIntentBackgroundActivityStartMode =
                    ActivityOptions
                        .MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            }

        try {

            pendingIntent.send(
                appContext,
                0,
                null,
                null,
                null,
                null,
                senderOptions.toBundle()
            )

        } catch (securityException: SecurityException) {
            /*
             * A foreground service is still a background caller for Android's
             * activity-launch rules. If the system rejects the PendingIntent,
             * use the accessibility service only when the user has already
             * enabled it. Otherwise rethrow so the caller reports a truthful
             * failure instead of saying the app opened when it did not.
             */
            if (!NexusEyeActionWorkflow.launchExternalActivity(intent)) {
                throw securityException
            }
        } finally {

            pendingIntent.cancel()
        }
    }

    /*
     * ============================================================
     * WEB SEARCH
     * ============================================================
     */

    private fun webSearch(
        query: String
    ): Result {

        val cleanQuery =
            query.trim()

        if (
            cleanQuery.isBlank()
        ) {

            return Result(
                handled = true,
                answer =
                    "Please tell me what you want me to search for."
            )
        }

        val url =
            "https://www.google.com/search?q=" +
                    Uri.encode(
                        cleanQuery
                    )

        return openSystemIntent(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(url)
            ),
            "Searching for $cleanQuery."
        )
    }

    /*
     * ============================================================
     * SYSTEM INTENT
     * ============================================================
     */

    private fun openSystemIntent(
        intent: Intent,
        successMessage: String
    ): Result {

        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
        )

        return try {

            launchActivityForVoiceCommand(
                intent
            )

            Result(
                handled = true,
                answer =
                    successMessage
            )

        } catch (
            exception: Exception
        ) {

            Result(
                handled = true,
                answer =
                    "I could not open that on this phone."
            )
        }
    }

    private fun accessibilityResult(
        success: Boolean,
        successMessage: String
    ): Result {
        return if (success) {
            Result(true, successMessage)
        } else {
            Result(
                handled = true,
                answer =
                    "I cannot control that part of the screen. Please make sure Nexus-Eye Accessibility access is enabled and the current app exposes its controls to Android."
            )
        }
    }

    /*
     * ============================================================
     * PERMISSION HELPER
     * ============================================================
     */

    private fun hasPermission(
        permission: String
    ): Boolean {

        return ContextCompat.checkSelfPermission(
            appContext,
            permission
        ) ==
                PackageManager.PERMISSION_GRANTED
    }

    /*
     * ============================================================
     * NORMALIZATION
     * ============================================================
     */

    private fun normalize(
        value: String
    ): String {

        return value
            .trim()
            .lowercase(
                Locale.ROOT
            )
            .replace(
                Regex("\\s+"),
                " "
            )
    }
}
