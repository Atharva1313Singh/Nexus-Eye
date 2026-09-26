package com.thirdeye.app.device

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import java.util.Locale

class DeviceActionManager(
    context: Context
) {

    private val appContext = context.applicationContext

    data class Result(
        val handled: Boolean,
        val answer: String,
        val requiredPermissions: List<String> = emptyList()
    )

    private data class ParsedCommand(
        val type: Type,
        val contactQuery: String,
        val message: String?
    )

    private enum class Type {
        CALL,
        MESSAGE
    }

    fun tryHandle(
        query: String,
        speechLanguageId: String
    ): Result {
        val parsed = parseCommand(query) ?: return Result(
            handled = false,
            answer = ""
        )

        val permissions = mutableListOf<String>()

        if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
            permissions += Manifest.permission.READ_CONTACTS
        }

        if (
            parsed.type == Type.CALL &&
            !hasPermission(Manifest.permission.CALL_PHONE)
        ) {
            permissions += Manifest.permission.CALL_PHONE
        }

        if (permissions.isNotEmpty()) {
            return Result(
                handled = true,
                answer = permissionMessage(
                    parsed.type,
                    speechLanguageId
                ),
                requiredPermissions = permissions
            )
        }

        val contact = findContact(
            parsed.contactQuery
        )

        if (contact == null) {
            return Result(
                handled = true,
                answer = contactNotFoundMessage(
                    parsed.contactQuery,
                    speechLanguageId
                )
            )
        }

        return when (parsed.type) {
            Type.CALL -> callContact(
                contactName = contact.name,
                phoneNumber = contact.number,
                speechLanguageId = speechLanguageId
            )

            Type.MESSAGE -> messageContact(
                contactName = contact.name,
                phoneNumber = contact.number,
                message = parsed.message,
                speechLanguageId = speechLanguageId
            )
        }
    }

    private fun parseCommand(
        query: String
    ): ParsedCommand? {
        val clean = query.trim()
        if (clean.isBlank()) return null

        val lower = clean.lowercase(Locale.ROOT)

        val callPrefix = when {
            lower.startsWith("call ") -> "call"
            lower.startsWith("phone ") -> "phone"
            lower.startsWith("dial ") -> "dial"
            lower.startsWith("कॉल ") -> "call"
            lower.startsWith("फोन ") -> "phone"
            else -> null
        }

        if (callPrefix != null) {
            val contact = clean
                .substringAfter(' ')
                .trim()
                .removePrefix("the ")
                .removePrefix("my ")
                .trim()

            if (contact.isNotBlank()) {
                return ParsedCommand(
                    type = Type.CALL,
                    contactQuery = contact,
                    message = null
                )
            }
        }

        val messagePrefix = when {
            lower.startsWith("message ") -> true
            lower.startsWith("text ") -> true
            lower.startsWith("sms ") -> true
            lower.startsWith("send a message to ") -> true
            lower.startsWith("send message to ") -> true
            lower.startsWith("send sms to ") -> true
            lower.startsWith("मैसेज ") -> true
            lower.startsWith("संदेश ") -> true
            else -> false
        }

        if (messagePrefix) {
            val remainder = when {
                lower.startsWith("send a message to ") ->
                    clean.substring(20).trim()

                lower.startsWith("send message to ") ->
                    clean.substring(17).trim()

                lower.startsWith("send sms to ") ->
                    clean.substring(12).trim()

                else ->
                    clean.substringAfter(' ').trim()
            }

            if (remainder.isBlank()) return null

            val separators = listOf(
                " that says ",
                " saying ",
                ":",
                " - "
            )

            var contact = remainder
            var message: String? = null

            for (separator in separators) {
                val index = remainder.lowercase(Locale.ROOT)
                    .indexOf(separator)

                if (index >= 0) {
                    contact = remainder.substring(0, index).trim()
                    message = remainder
                        .substring(index + separator.length)
                        .trim()
                    break
                }
            }

            if (
                message == null &&
                lower.startsWith("message ")
            ) {
                val toIndex =
                    remainder.lowercase(Locale.ROOT)
                        .lastIndexOf(" to ")

                if (toIndex > 0) {
                    val possibleMessage =
                        remainder.substring(0, toIndex).trim()
                    val possibleContact =
                        remainder.substring(toIndex + 4).trim()

                    if (
                        possibleMessage.isNotBlank() &&
                        possibleContact.isNotBlank()
                    ) {
                        message = possibleMessage
                        contact = possibleContact
                    }
                }
            }

            if (contact.isBlank()) return null

            return ParsedCommand(
                type = Type.MESSAGE,
                contactQuery = contact
                    .removePrefix("the ")
                    .removePrefix("my ")
                    .trim(),
                message = message?.takeIf { it.isNotBlank() }
            )
        }

        return null
    }

    private data class Contact(
        val name: String,
        val number: String
    )

    private fun findContact(
        requestedName: String
    ): Contact? {
        val normalizedRequest = normalize(requestedName)
        if (normalizedRequest.isBlank()) return null

        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        var best: Contact? = null
        var bestScore = 0

        appContext.contentResolver.query(
            uri,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            )
            val numberIndex = cursor.getColumnIndex(
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )

            if (nameIndex < 0 || numberIndex < 0) return@use

            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIndex)?.trim().orEmpty()
                val number = cursor.getString(numberIndex)?.trim().orEmpty()

                if (name.isBlank() || number.isBlank()) continue

                val normalizedName = normalize(name)
                val score = contactScore(
                    normalizedRequest,
                    normalizedName
                )

                if (score > bestScore) {
                    bestScore = score
                    best = Contact(
                        name = name,
                        number = number
                    )
                }
            }
        }

        return best
    }

    private fun contactScore(
        requested: String,
        actual: String
    ): Int {
        if (requested == actual) return 100
        if (actual.startsWith(requested)) return 90
        if (actual.contains(requested)) return 80

        val requestedWords = requested
            .split(' ')
            .filter { it.length > 1 }

        if (requestedWords.isNotEmpty()) {
            val matched = requestedWords.count {
                actual.contains(it)
            }

            if (matched == requestedWords.size) {
                return 70
            }
        }

        return 0
    }

    private fun callContact(
        contactName: String,
        phoneNumber: String,
        speechLanguageId: String
    ): Result {
        return try {
            val intent = Intent(
                Intent.ACTION_CALL,
                Uri.parse(
                    "tel:${Uri.encode(phoneNumber)}"
                )
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            appContext.startActivity(intent)

            Result(
                handled = true,
                answer = if (speechLanguageId == "hi") {
                    "$contactName को कॉल कर रही हूँ।"
                } else {
                    "Calling $contactName."
                }
            )
        } catch (_: Exception) {
            Result(
                handled = true,
                answer = if (speechLanguageId == "hi") {
                    "$contactName को कॉल शुरू नहीं हो सका।"
                } else {
                    "I could not start the call to $contactName."
                }
            )
        }
    }

    private fun messageContact(
        contactName: String,
        phoneNumber: String,
        message: String?,
        speechLanguageId: String
    ): Result {
        return try {
            val intent = Intent(
                Intent.ACTION_SENDTO,
                Uri.parse(
                    "smsto:${Uri.encode(phoneNumber)}"
                )
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                if (!message.isNullOrBlank()) {
                    putExtra(
                        "sms_body",
                        message
                    )
                }
            }

            appContext.startActivity(intent)

            Result(
                handled = true,
                answer = if (speechLanguageId == "hi") {
                    if (message.isNullOrBlank()) {
                        "$contactName के लिए मैसेज खोल रही हूँ।"
                    } else {
                        "$contactName के लिए मैसेज तैयार है।"
                    }
                } else {
                    if (message.isNullOrBlank()) {
                        "Opening a message to $contactName."
                    } else {
                        "The message to $contactName is ready to send."
                    }
                }
            )
        } catch (_: Exception) {
            Result(
                handled = true,
                answer = if (speechLanguageId == "hi") {
                    "$contactName के लिए मैसेज नहीं खोल सकी।"
                } else {
                    "I could not open a message to $contactName."
                }
            )
        }
    }

    private fun permissionMessage(
        type: Type,
        speechLanguageId: String
    ): String {
        return if (speechLanguageId == "hi") {
            when (type) {
                Type.CALL ->
                    "कॉल करने के लिए कॉन्टैक्ट और फोन की अनुमति चाहिए।"

                Type.MESSAGE ->
                    "कॉन्टैक्ट ढूँढने के लिए कॉन्टैक्ट की अनुमति चाहिए।"
            }
        } else {
            when (type) {
                Type.CALL ->
                    "I need Contacts and Phone permission to make the call."

                Type.MESSAGE ->
                    "I need Contacts permission to find that contact."
            }
        }
    }

    private fun contactNotFoundMessage(
        requestedName: String,
        speechLanguageId: String
    ): String {
        return if (speechLanguageId == "hi") {
            "मुझे $requestedName नाम का कॉन्टैक्ट नहीं मिला।"
        } else {
            "I could not find a contact named $requestedName."
        }
    }

    private fun normalize(
        value: String
    ): String {
        return value
            .lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    private fun hasPermission(
        permission: String
    ): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }
}
