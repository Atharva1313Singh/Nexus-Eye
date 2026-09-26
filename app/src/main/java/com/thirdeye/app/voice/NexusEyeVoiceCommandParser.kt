package com.thirdeye.app.voice

import java.util.Locale

object NexusEyeVoiceCommandParser {

    fun parse(
        input: String
    ): NexusEyeVoiceCommand {

        val originalText = input.trim()

        if (originalText.isBlank()) {
            return NexusEyeVoiceCommand.Unknown("")
        }

        val text = normalize(originalText)

        when {
            isExactOrContains(
                text,
                listOf(
                    "go home",
                    "home screen",
                    "open home",
                    "main screen",
                    "वापस होम",
                    "होम स्क्रीन"
                )
            ) -> {
                return NexusEyeVoiceCommand.GoHome
            }

            isExactOrContains(
                text,
                listOf(
                    "go back",
                    "back",
                    "previous screen",
                    "वापस",
                    "पीछे जाओ"
                )
            ) -> {
                return NexusEyeVoiceCommand.GoBack
            }

            isExactOrContains(
                text,
                listOf(
                    "open voice",
                    "voice mode",
                    "voice assistant",
                    "open assistant",
                    "वॉइस खोलो",
                    "वॉइस मोड"
                )
            ) -> {
                return NexusEyeVoiceCommand.OpenVoice
            }

            isExactOrContains(
                text,
                listOf(
                    "open intelligence",
                    "intelligence",
                    "ask question",
                    "question mode",
                    "इंटेलिजेंस खोलो",
                    "सवाल पूछना है"
                )
            ) -> {
                val question =
                    extractAfterPrefix(
                        text,
                        listOf(
                            "ask question",
                            "ask",
                            "सवाल"
                        )
                    )

                return if (question.isNotBlank()) {
                    NexusEyeVoiceCommand.AskQuestion(question)
                } else {
                    NexusEyeVoiceCommand.OpenIntelligence
                }
            }

            isExactOrContains(
                text,
                listOf(
                    "open vision",
                    "vision",
                    "camera mode",
                    "open camera",
                    "विजन खोलो",
                    "कैमरा खोलो"
                )
            ) -> {
                return NexusEyeVoiceCommand.OpenVision
            }

            isExactOrContains(
                text,
                listOf(
                    "identify person",
                    "identify the person",
                    "who is ahead",
                    "who is in front",
                    "person identification",
                    "व्यक्ति पहचानो",
                    "कौन सामने है"
                )
            ) -> {
                return NexusEyeVoiceCommand.IdentifyPerson
            }

            isExactOrContains(
                text,
                listOf(
                    "read this",
                    "read text",
                    "read the text",
                    "ocr",
                    "text reading",
                    "यह पढ़ो",
                    "टेक्स्ट पढ़ो"
                )
            ) -> {
                return NexusEyeVoiceCommand.ReadText
            }

            isExactOrContains(
                text,
                listOf(
                    "open communication",
                    "communication",
                    "bluetooth communication",
                    "communication mode",
                    "कम्युनिकेशन खोलो"
                )
            ) -> {
                return NexusEyeVoiceCommand.OpenCommunication
            }

            isExactOrContains(
                text,
                listOf(
                    "open settings",
                    "settings",
                    "setting mode",
                    "सेटिंग खोलो",
                    "सेटिंग्स खोलो"
                )
            ) -> {
                return NexusEyeVoiceCommand.OpenSettings
            }

            isExactOrContains(
                text,
                listOf(
                    "what is the weather",
                    "what's the weather",
                    "weather",
                    "today weather",
                    "weather today",
                    "मौसम",
                    "आज का मौसम"
                )
            ) -> {
                return NexusEyeVoiceCommand.Weather
            }

            isExactOrContains(
                text,
                listOf(
                    "stop",
                    "stop everything",
                    "cancel",
                    "cancel this",
                    "रुको",
                    "बंद करो",
                    "रद्द करो"
                )
            ) -> {
                return NexusEyeVoiceCommand.Stop
            }

            isExactOrContains(
                text,
                listOf(
                    "repeat",
                    "say again",
                    "repeat that",
                    "फिर से बोलो",
                    "दोबारा बोलो"
                )
            ) -> {
                return NexusEyeVoiceCommand.Repeat
            }

            isExactOrContains(
                text,
                listOf(
                    "start listening",
                    "listen",
                    "listen to me",
                    "सुनो",
                    "सुनना शुरू करो"
                )
            ) -> {
                return NexusEyeVoiceCommand.StartListening
            }

            isExactOrContains(
                text,
                listOf(
                    "connect bluetooth",
                    "connect esp32",
                    "connect device",
                    "bluetooth connect",
                    "ब्लूटूथ कनेक्ट करो",
                    "ईएसपी32 कनेक्ट करो"
                )
            ) -> {
                return NexusEyeVoiceCommand.ConnectBluetooth
            }

            isExactOrContains(
                text,
                listOf(
                    "disconnect bluetooth",
                    "disconnect esp32",
                    "disconnect device",
                    "bluetooth disconnect",
                    "ब्लूटूथ डिस्कनेक्ट करो",
                    "ईएसपी32 डिस्कनेक्ट करो"
                )
            ) -> {
                return NexusEyeVoiceCommand.DisconnectBluetooth
            }

            looksLikeNavigationCommand(text) -> {
                val destination =
                    extractNavigationDestination(text)

                return if (destination.isNotBlank()) {
                    NexusEyeVoiceCommand.Navigate(destination)
                } else {
                    NexusEyeVoiceCommand.Unknown(originalText)
                }
            }

            looksLikeQuestion(text) -> {
                return NexusEyeVoiceCommand.AskQuestion(
                    question = originalText
                )
            }

            else -> {
                return NexusEyeVoiceCommand.Unknown(
                    originalText = originalText
                )
            }
        }
    }

    private fun normalize(
        input: String
    ): String {

        return input
            .lowercase(Locale.ROOT)
            .trim()
            .replace(
                Regex("\\s+"),
                " "
            )
            .removeSuffix(".")
            .removeSuffix("?")
    }

    private fun isExactOrContains(
        text: String,
        phrases: List<String>
    ): Boolean {

        return phrases.any { phrase ->

            val normalizedPhrase =
                normalize(phrase)

            text == normalizedPhrase ||
                    text.contains(
                        normalizedPhrase
                    )
        }
    }

    private fun extractAfterPrefix(
        text: String,
        prefixes: List<String>
    ): String {

        for (prefix in prefixes) {

            val normalizedPrefix =
                normalize(prefix)

            if (text.startsWith(normalizedPrefix)) {

                return text
                    .removePrefix(normalizedPrefix)
                    .trim()
                    .removePrefix(" ")
            }
        }

        return ""
    }

    private fun looksLikeNavigationCommand(
        text: String
    ): Boolean {

        val prefixes =
            listOf(
                "navigate to",
                "take me to",
                "go to",
                "route to",
                "directions to",
                "navigation to",
                "मुझे ले चलो",
                "यहाँ ले चलो"
            )

        return prefixes.any { prefix ->
            text.startsWith(
                normalize(prefix)
            )
        }
    }

    private fun extractNavigationDestination(
        text: String
    ): String {

        val prefixes =
            listOf(
                "navigate to",
                "take me to",
                "go to",
                "route to",
                "directions to",
                "navigation to",
                "मुझे ले चलो",
                "यहाँ ले चलो"
            )

        for (prefix in prefixes) {

            val normalizedPrefix =
                normalize(prefix)

            if (text.startsWith(normalizedPrefix)) {

                return text
                    .removePrefix(normalizedPrefix)
                    .trim()
            }
        }

        return ""
    }

    private fun looksLikeQuestion(
        text: String
    ): Boolean {

        val questionPrefixes =
            listOf(
                "what ",
                "what is ",
                "who ",
                "who is ",
                "where ",
                "where is ",
                "when ",
                "when is ",
                "why ",
                "why is ",
                "how ",
                "how is ",
                "can you ",
                "क्या ",
                "कौन ",
                "कहाँ ",
                "कब ",
                "क्यों ",
                "कैसे "
            )

        return questionPrefixes.any { prefix ->
            text.startsWith(prefix)
        }
    }
}