package com.thirdeye.app.navigation

import android.content.Context
import com.thirdeye.app.environment.NexusEyeHomeLocation
import com.thirdeye.app.environment.NexusEyeHomeLocationManager

data class NexusEyeHomeNavigationTarget(
    val latitude: Double,
    val longitude: Double,
    val address: String
)

sealed class NexusEyeHomeCommandResult {

    data class Ready(
        val target: NexusEyeHomeNavigationTarget
    ) : NexusEyeHomeCommandResult()

    data object HomeNotSaved : NexusEyeHomeCommandResult()

    data class NotAHomeCommand(
        val originalText: String
    ) : NexusEyeHomeCommandResult()
}

class NexusEyeHomeCommandHandler(
    context: Context
) {

    private val homeLocationManager =
        NexusEyeHomeLocationManager(
            context.applicationContext
        )

    fun isHomeCommand(
        text: String
    ): Boolean {

        val normalized =
            normalize(
                text
            )

        if (normalized.isBlank()) {
            return false
        }

        return HOME_COMMANDS.any { command ->

            normalized == command ||
                    normalized.contains(
                        command
                    )
        }
    }

    fun handle(
        text: String
    ): NexusEyeHomeCommandResult {

        if (
            !isHomeCommand(
                text
            )
        ) {

            return NexusEyeHomeCommandResult
                .NotAHomeCommand(
                    originalText = text
                )
        }

        val savedHome =
            homeLocationManager
                .getSavedHomeLocation()

        if (savedHome == null) {

            return NexusEyeHomeCommandResult
                .HomeNotSaved
        }

        return NexusEyeHomeCommandResult
            .Ready(
                target =
                    NexusEyeHomeNavigationTarget(
                        latitude =
                            savedHome.latitude,

                        longitude =
                            savedHome.longitude,

                        address =
                            savedHome.address
                    )
            )
    }

    fun getSavedHome(): NexusEyeHomeNavigationTarget? {

        val savedHome =
            homeLocationManager
                .getSavedHomeLocation()
                ?: return null

        return savedHome.toNavigationTarget()
    }

    fun hasSavedHome(): Boolean {

        return homeLocationManager
            .hasSavedHomeLocation()
    }

    fun clearSavedHome() {

        homeLocationManager
            .clearHomeLocation()
    }

    private fun normalize(
        text: String
    ): String {

        return text
            .trim()
            .lowercase()
            .replace(
                Regex("\\s+"),
                " "
            )
    }

    private fun NexusEyeHomeLocation
            .toNavigationTarget():
            NexusEyeHomeNavigationTarget {

        return NexusEyeHomeNavigationTarget(
            latitude =
                latitude,

            longitude =
                longitude,

            address =
                address
        )
    }

    companion object {

        private val HOME_COMMANDS =
            setOf(

                "go home",

                "take me home",

                "navigate home",

                "navigate to home",

                "navigation to home",

                "route to home",

                "go to home",

                "return home",

                "take me to home",

                "home"
            )
    }
}