package com.thirdeye.app.voice

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi

/**
 * Android-mediated setup for making Nexus-Eye the user's digital assistant.
 *
 * Android deliberately does not allow an application to silently grant the
 * assistant role or accessibility access. These helpers only open the
 * official system-controlled flows.
 */
object NexusEyeAssistantManager {

    fun isAssistant(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val roleManager = context.getSystemService(RoleManager::class.java)
            ?: return false
        return roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)
    }

    fun canRequestAssistant(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val roleManager = context.getSystemService(RoleManager::class.java)
            ?: return false
        return roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT) &&
                roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT).not()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun createAssistantRoleRequest(context: Context): Intent? {
        val roleManager = context.getSystemService(RoleManager::class.java)
            ?: return null
        if (!roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT) ||
            roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)
        ) {
            return null
        }
        return roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
    }

    fun openAccessibilitySettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    fun openAssistantSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
        } else {
            Intent(Settings.ACTION_SETTINGS)
        }
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun isAccessibilityEnabled(context: Context): Boolean =
        com.thirdeye.app.device.NexusEyeActionWorkflow.isAccessibilityServiceEnabled(context)
}
