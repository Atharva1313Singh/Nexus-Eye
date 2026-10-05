package com.thirdeye.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
import com.thirdeye.app.device.NexusEyeActionWorkflow

class NexusEyeAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()

        Log.d(
            "NexusEyeAccessibility",
            "Nexus-Eye AccessibilityService CONNECTED"
        )

        NexusEyeActionWorkflow.attachAccessibilityService(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!NexusEyeActionWorkflow.isActive()) return

        val packageName = event.packageName?.toString() ?: return

        when (packageName) {
            "com.whatsapp" -> {
                NexusEyeActionWorkflow.handleWhatsAppAccessibilityEvent(
                    rootNode = rootInActiveWindow
                )
            }

            "com.google.android.dialer",
            "com.android.dialer",
            "com.samsung.android.dialer" -> {
                NexusEyeActionWorkflow.handleDialerAccessibilityEvent(
                    rootNode = rootInActiveWindow
                )
            }
        }
    }

    override fun onInterrupt() {
        Log.w(
            "NexusEyeAccessibility",
            "Nexus-Eye AccessibilityService INTERRUPTED"
        )
        NexusEyeActionWorkflow.onAccessibilityInterrupted()
    }

    override fun onDestroy() {
        Log.d(
            "NexusEyeAccessibility",
            "Nexus-Eye AccessibilityService DISCONNECTED"
        )
        NexusEyeActionWorkflow.detachAccessibilityService(this)
        super.onDestroy()
    }

    companion object {

        fun findNodeByText(
            rootNode: AccessibilityNodeInfo?,
            text: String
        ): AccessibilityNodeInfo? {
            if (rootNode == null) return null

            val matches =
                rootNode.findAccessibilityNodeInfosByText(text)

            return matches.firstOrNull()
        }

        fun findClickableParent(
            node: AccessibilityNodeInfo?
        ): AccessibilityNodeInfo? {
            var current = node

            while (current != null) {
                if (current.isClickable) {
                    return current
                }

                current = current.parent
            }

            return null
        }

        fun performClick(
            node: AccessibilityNodeInfo?
        ): Boolean {
            if (node == null) return false

            if (node.isClickable) {
                return node.performAction(
                    AccessibilityNodeInfo.ACTION_CLICK
                )
            }

            val clickableParent =
                findClickableParent(node)

            return clickableParent?.performAction(
                AccessibilityNodeInfo.ACTION_CLICK
            ) == true
        }
    }
}