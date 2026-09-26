package com.thirdeye.app.voice

sealed class NexusEyeVoiceCommand {

    data object GoHome : NexusEyeVoiceCommand()

    data object GoBack : NexusEyeVoiceCommand()

    data object OpenVoice : NexusEyeVoiceCommand()

    data object OpenIntelligence : NexusEyeVoiceCommand()

    data object OpenVision : NexusEyeVoiceCommand()

    data object OpenCommunication : NexusEyeVoiceCommand()

    data object OpenSettings : NexusEyeVoiceCommand()

    data object StartListening : NexusEyeVoiceCommand()

    data object Stop : NexusEyeVoiceCommand()

    data object Repeat : NexusEyeVoiceCommand()

    data object IdentifyPerson : NexusEyeVoiceCommand()

    data object ReadText : NexusEyeVoiceCommand()

    data object Weather : NexusEyeVoiceCommand()

    data class Navigate(
        val destination: String
    ) : NexusEyeVoiceCommand()

    data class AskQuestion(
        val question: String
    ) : NexusEyeVoiceCommand()

    data object ConnectBluetooth : NexusEyeVoiceCommand()

    data object DisconnectBluetooth : NexusEyeVoiceCommand()

    data class SwitchLanguage(
        val languageName: String
    ) : NexusEyeVoiceCommand()

    data class Unknown(
        val originalText: String
    ) : NexusEyeVoiceCommand()
}