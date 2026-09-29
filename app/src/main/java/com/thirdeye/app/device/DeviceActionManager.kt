package com.thirdeye.app.device

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.Uri
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
        OPEN_MAPS
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
                        Uri.parse("geo:0,0?q=" + Uri.encode(parsed.value))
                    ),
                    "Opening Maps for ${parsed.value}."
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

        val normalized =
            normalize(query)

        if (normalized.isBlank()) {
            return null
        }

        /*
         * --------------------------------------------------------
         * APP / PHONE CONTROL COMMANDS
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

        if (settingsCommands.any { normalized == it }) {
            return ParsedCommand(
                type = CommandType.OPEN_SETTINGS
            )
        }

        val cameraCommands =
            listOf(
                "open camera",
                "launch camera",
                "camera kholo",
                "कैमरा खोलो"
            )

        if (cameraCommands.any { normalized == it }) {
            return ParsedCommand(
                type = CommandType.OPEN_CAMERA
            )
        }

        val mapsPrefixes =
            listOf(
                "open maps for ",
                "open map for ",
                "show maps for ",
                "maps for ",
                "मैप खोलो "
            )

        for (prefix in mapsPrefixes) {
            if (normalized.startsWith(prefix)) {
                val destination = normalized.removePrefix(prefix).trim()
                if (destination.isNotBlank()) {
                    return ParsedCommand(
                        type = CommandType.OPEN_MAPS,
                        value = destination
                    )
                }
            }
        }

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

        for (prefix in searchPrefixes) {
            if (normalized.startsWith(prefix)) {
                val search = normalized.removePrefix(prefix).trim()
                if (search.isNotBlank()) {
                    return ParsedCommand(
                        type = CommandType.WEB_SEARCH,
                        value = search
                    )
                }
            }
        }

        val openAppPrefixes =
            listOf(
                "open ",
                "launch ",
                "start ",
                "open app ",
                "launch app ",
                "ऐप खोलो ",
                "खोलो "
            )

        for (prefix in openAppPrefixes) {
            if (normalized.startsWith(prefix)) {
                val appName = normalized.removePrefix(prefix).trim()
                if (appName.isNotBlank()) {
                    return ParsedCommand(
                        type = CommandType.OPEN_APP,
                        value = appName
                    )
                }
            }
        }

        /*
         * --------------------------------------------------------
         * CALL COMMANDS
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
                normalized.startsWith(
                    prefix
                )
            ) {

                val contact =
                    normalized
                        .removePrefix(prefix)
                        .trim()

                if (contact.isNotBlank()) {

                    return ParsedCommand(
                        type =
                            CommandType.CALL,
                        contactQuery =
                            contact,
                        message = ""
                    )
                }
            }
        }

        /*
         * --------------------------------------------------------
         * WHATSAPP / MESSAGE COMMANDS
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

        for (
        prefix in messagePrefixes
        ) {

            if (
                normalized.startsWith(
                    prefix
                )
            ) {

                val remaining =
                    normalized
                        .removePrefix(prefix)
                        .trim()

                if (remaining.isBlank()) {
                    continue
                }

                /*
                 * Examples:
                 *
                 * message Mom that says hi
                 * message Mom saying hi
                 * message Mom: hi
                 * message Mom - hi
                 */

                val separators =
                    listOf(
                        " that says ",
                        " saying ",
                        ":",
                        " - "
                    )

                for (
                separator in separators
                ) {

                    val separatorIndex =
                        remaining.indexOf(
                            separator
                        )

                    if (
                        separatorIndex > 0
                    ) {

                        val contact =
                            remaining
                                .substring(
                                    0,
                                    separatorIndex
                                )
                                .trim()

                        val message =
                            remaining
                                .substring(
                                    separatorIndex +
                                            separator.length
                                )
                                .trim()

                        if (
                            contact.isNotBlank() &&
                            message.isNotBlank()
                        ) {

                            return ParsedCommand(
                                type =
                                    CommandType.MESSAGE,
                                contactQuery =
                                    contact,
                                message =
                                    message
                            )
                        }
                    }
                }

                /*
                 * Supports:
                 *
                 * message hi to Mom
                 *
                 * and:
                 *
                 * whatsapp hi to Mom
                 */

                val toIndex =
                    remaining.lastIndexOf(
                        " to "
                    )

                if (
                    toIndex > 0 &&
                    toIndex < remaining.length - 4
                ) {

                    val message =
                        remaining
                            .substring(
                                0,
                                toIndex
                            )
                            .trim()

                    val contact =
                        remaining
                            .substring(
                                toIndex + 4
                            )
                            .trim()

                    if (
                        contact.isNotBlank() &&
                        message.isNotBlank()
                    ) {

                        return ParsedCommand(
                            type =
                                CommandType.MESSAGE,
                            contactQuery =
                                contact,
                            message =
                                message
                        )
                    }
                }

                /*
                 * If there is only a contact and no message,
                 * open the contact's WhatsApp conversation.
                 */

                return ParsedCommand(
                    type =
                        CommandType.MESSAGE,
                    contactQuery =
                        remaining,
                    message = ""
                )
            }
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

        /*
         * Start the action workflow before opening the dialer.
         * This gives the accessibility system a chance to track
         * the active phone action.
         */

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

            appContext.startActivity(
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
            phoneNumber
                .filter {
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

        /*
         * WhatsApp's wa.me format requires an international
         * phone number without +, spaces, brackets, or dashes.
         *
         * We use the number exactly as stored after removing
         * punctuation. This avoids guessing a country code.
         */

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

        /*
         * Check that NEXUS EYE Accessibility Service is enabled
         * before starting the automatic workflow.
         */

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

        /*
         * Start the cancellable action workflow.
         */

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

            try {

                appContext.startActivity(
                    whatsappIntent
                )

            } catch (
                whatsappException: Exception
            ) {

                /*
                 * If the dedicated WhatsApp package cannot
                 * handle the intent, try the normal ACTION_VIEW
                 * handler.
                 */

                val fallbackIntent =
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(
                            whatsappUrl
                        )
                    ).apply {

                        flags =
                            Intent.FLAG_ACTIVITY_NEW_TASK
                    }

                appContext.startActivity(
                    fallbackIntent
                )
            }

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
     * APP / WEB / SYSTEM CONTROL
     * ============================================================
     */

    private fun openApplication(
        requestedName: String
    ): Result {

        val normalizedName =
            normalize(requestedName)

        val knownPackages =
            mapOf(
                "whatsapp" to "com.whatsapp",
                "youtube" to "com.google.android.youtube",
                "chrome" to "com.android.chrome",
                "google chrome" to "com.android.chrome",
                "gmail" to "com.google.android.gm",
                "google maps" to "com.google.android.apps.maps",
                "maps" to "com.google.android.apps.maps",
                "instagram" to "com.instagram.android",
                "facebook" to "com.facebook.katana",
                "telegram" to "org.telegram.messenger",
                "spotify" to "com.spotify.music",
                "phone" to "com.google.android.dialer",
                "dialer" to "com.google.android.dialer",
                "camera" to "com.android.camera"
            )

        val packageName =
            knownPackages[normalizedName]

        if (packageName != null) {
            val packageIntent =
                appContext.packageManager
                    .getLaunchIntentForPackage(packageName)

            if (packageIntent != null) {
                packageIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                return try {
                    appContext.startActivity(packageIntent)
                    Result(
                        handled = true,
                        answer = "Opening $requestedName."
                    )
                } catch (_: ActivityNotFoundException) {
                    Result(
                        handled = true,
                        answer = "I could not open $requestedName because it is not available on this phone."
                    )
                }
            }
        }

        val launcherIntent =
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

        val matches =
            appContext.packageManager
                .queryIntentActivities(launcherIntent, 0)

        val match =
            matches.firstOrNull { info ->
                val label =
                    info.loadLabel(appContext.packageManager)
                        ?.toString()
                        ?.let(::normalize)
                        ?: return@firstOrNull false

                label == normalizedName ||
                        label.contains(normalizedName) ||
                        normalizedName.contains(label)
            }

        if (match != null) {
            val launchIntent =
                appContext.packageManager
                    .getLaunchIntentForPackage(
                        match.activityInfo.packageName
                    )

            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                return try {
                    appContext.startActivity(launchIntent)
                    Result(
                        handled = true,
                        answer = "Opening $requestedName."
                    )
                } catch (_: Exception) {
                    Result(
                        handled = true,
                        answer = "I could not open $requestedName."
                    )
                }
            }
        }

        return Result(
            handled = true,
            answer = "I could not find an installed app named $requestedName."
        )
    }

    private fun webSearch(
        query: String
    ): Result {

        val cleanQuery = query.trim()

        if (cleanQuery.isBlank()) {
            return Result(
                handled = true,
                answer = "Please tell me what you want me to search for."
            )
        }

        val url =
            "https://www.google.com/search?q=" +
                    Uri.encode(cleanQuery)

        return openSystemIntent(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(url)
            ),
            "Searching for $cleanQuery."
        )
    }

    private fun openSystemIntent(
        intent: Intent,
        successMessage: String
    ): Result {

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            appContext.startActivity(intent)
            Result(
                handled = true,
                answer = successMessage
            )
        } catch (_: Exception) {
            Result(
                handled = true,
                answer = "I could not open that on this phone."
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
            .lowercase(Locale.ROOT)
            .replace(
                Regex("\\s+"),
                " "
            )
    }
}