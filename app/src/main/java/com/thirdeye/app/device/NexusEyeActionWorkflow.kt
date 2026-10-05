package com.thirdeye.app.device

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Handler
import android.os.Looper
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

object NexusEyeActionWorkflow {

    private const val WHATSAPP_PACKAGE = "com.whatsapp"

    private const val DIALER_PACKAGE_GOOGLE =
        "com.google.android.dialer"

    private const val DIALER_PACKAGE_ANDROID =
        "com.android.dialer"

    private const val DIALER_PACKAGE_SAMSUNG =
        "com.samsung.android.dialer"

    private const val ACTION_NONE = 0
    private const val ACTION_WHATSAPP = 1
    private const val ACTION_CALL = 2

    private val lock =
        Any()

    private val active =
        AtomicBoolean(false)

    private val handler =
        Handler(Looper.getMainLooper())

    private var actionType =
        ACTION_NONE

    private var contactName =
        ""

    private var phoneNumber =
        ""

    private var message =
        ""

    private var accessibilityService:
            AccessibilityService? = null

    private var whatsappSendAttempted =
        false

    private var callEndAttempted =
        false

    private var workflowGeneration =
        0L

    /*
     * ============================================================
     * ACCESSIBILITY SERVICE CONNECTION
     * ============================================================
     */

    fun attachAccessibilityService(
        service: AccessibilityService
    ) {
        synchronized(lock) {
            accessibilityService = service
        }

        scheduleWhatsAppProcessing()
    }

    /**
     * Attempts to launch an external activity through the already user-enabled
     * accessibility service. This is only a fallback for Android background
     * activity-launch restrictions; it does not enable or request accessibility.
     */
    fun launchExternalActivity(
        intent: android.content.Intent
    ): Boolean {
        val service = synchronized(lock) { accessibilityService }

        if (service == null) {
            android.util.Log.w(
                "NexusEyeActionWorkflow",
                "External launch FAILED: AccessibilityService is not connected"
            )
            return false
        }

        return try {
            val launchIntent = android.content.Intent(intent).apply {
                addFlags(
                    android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                            android.content.Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                )
            }

            android.util.Log.d(
                "NexusEyeActionWorkflow",
                "Launching external activity through AccessibilityService: " +
                        "package=${launchIntent.`package`}, " +
                        "component=${launchIntent.component}, " +
                        "action=${launchIntent.action}"
            )

            service.startActivity(launchIntent)

            android.util.Log.d(
                "NexusEyeActionWorkflow",
                "AccessibilityService startActivity() completed"
            )

            true
        } catch (throwable: Throwable) {
            android.util.Log.e(
                "NexusEyeActionWorkflow",
                "AccessibilityService external launch FAILED",
                throwable
            )
            false
        }
    }

    /** Generic voice-driven accessibility primitives. These operate only
     * through the user-enabled AccessibilityService and never attempt to
     * obtain or bypass the service permission. */
    fun accessibilityBack(): Boolean =
        synchronized(lock) { accessibilityService?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) == true }

    fun accessibilityHome(): Boolean =
        synchronized(lock) { accessibilityService?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) == true }

    fun accessibilityRecents(): Boolean =
        synchronized(lock) { accessibilityService?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS) == true }

    fun accessibilityNotifications(): Boolean =
        synchronized(lock) { accessibilityService?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS) == true }

    fun accessibilityQuickSettings(): Boolean =
        synchronized(lock) { accessibilityService?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS) == true }

    fun accessibilityClick(target: String): Boolean {
        val root = synchronized(lock) { accessibilityService?.rootInActiveWindow } ?: return false
        val normalizedTarget = target.trim().lowercase(Locale.ROOT)
        if (normalizedTarget.isBlank()) return false
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        collectNodes(root, nodes)
        val node = nodes.firstOrNull { n ->
            val text = n.text?.toString()?.trim()?.lowercase(Locale.ROOT).orEmpty()
            val description = n.contentDescription?.toString()?.trim()?.lowercase(Locale.ROOT).orEmpty()
            text == normalizedTarget || description == normalizedTarget ||
                    text.contains(normalizedTarget) || description.contains(normalizedTarget)
        }
        return performClick(node)
    }

    fun accessibilityType(text: String): Boolean {
        val service = synchronized(lock) { accessibilityService } ?: return false
        val root = service.rootInActiveWindow ?: return false
        var focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused == null) {
            val nodes = mutableListOf<AccessibilityNodeInfo>()
            collectNodes(root, nodes)
            focused = nodes.firstOrNull { it.isFocused && it.isEditable }
        }
        val node = focused ?: return false
        val arguments = android.os.Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    fun accessibilityScroll(direction: String): Boolean {
        val root = synchronized(lock) { accessibilityService?.rootInActiveWindow } ?: return false
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        collectNodes(root, nodes)
        val action = if (direction.equals("up", true) || direction.equals("back", true)) {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        }
        val scrollable = nodes.firstOrNull { it.isScrollable } ?: root
        return scrollable.performAction(action)
    }

    fun accessibilityReadScreen(maxCharacters: Int = 3500): String {
        val root = synchronized(lock) { accessibilityService?.rootInActiveWindow } ?: return ""
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        collectNodes(root, nodes)
        val parts = nodes.asSequence()
            .mapNotNull { node ->
                val value = node.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
                    ?: node.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }
                value
            }
            .distinct()
            .take(250)
            .toList()
        return parts.joinToString(". ").take(maxCharacters)
    }

    fun accessibilitySearchMessages(query: String): Boolean {
        if (!accessibilityClick("search") &&
            !accessibilityClick("search messages") &&
            !accessibilityClick("search chats")
        ) {
            return false
        }
        handler.postDelayed({ accessibilityType(query) }, 300L)
        return true
    }

    fun detachAccessibilityService(
        service: AccessibilityService
    ) {
        synchronized(lock) {

            if (
                accessibilityService === service
            ) {
                accessibilityService = null
            }
        }
    }

    fun onAccessibilityInterrupted() {
        /*
         * Do not automatically cancel here.
         *
         * Android may interrupt/restart an accessibility service
         * for reasons unrelated to the user's voice command.
         */
    }

    /*
     * ============================================================
     * STATUS
     * ============================================================
     */

    fun isActive(): Boolean {
        return active.get()
    }

    /*
     * ============================================================
     * START WHATSAPP WORKFLOW
     * ============================================================
     */

    fun beginWhatsAppMessage(
        contactName: String,
        phoneNumber: String,
        message: String
    ) {

        synchronized(lock) {

            workflowGeneration += 1L

            actionType =
                ACTION_WHATSAPP

            this.contactName =
                contactName

            this.phoneNumber =
                phoneNumber

            this.message =
                message

            whatsappSendAttempted =
                false

            callEndAttempted =
                false

            active.set(true)
        }

        scheduleWhatsAppProcessing()
    }

    /*
     * ============================================================
     * START CALL WORKFLOW
     * ============================================================
     */

    fun beginCall(
        contactName: String,
        phoneNumber: String
    ) {

        synchronized(lock) {

            workflowGeneration += 1L

            actionType =
                ACTION_CALL

            this.contactName =
                contactName

            this.phoneNumber =
                phoneNumber

            this.message =
                ""

            whatsappSendAttempted =
                false

            callEndAttempted =
                false

            active.set(true)
        }
    }

    /*
     * ============================================================
     * CANCEL
     * ============================================================
     *
     * VoiceScreen can call this when the user says:
     *
     * Stop
     * Cancel
     * Don't send
     * Don't call
     * Ruko
     * Rok do
     * Radd karo
     *
     * ============================================================
     */

    fun cancel(
        reason: String = "User cancelled the action."
    ) {

        synchronized(lock) {

            workflowGeneration += 1L

            active.set(false)

            actionType =
                ACTION_NONE

            contactName =
                ""

            phoneNumber =
                ""

            message =
                ""

            whatsappSendAttempted =
                false

            callEndAttempted =
                false
        }
    }

    /*
     * ============================================================
     * WHATSAPP ACCESSIBILITY PROCESSING
     * ============================================================
     */

    fun handleWhatsAppAccessibilityEvent(
        rootNode: AccessibilityNodeInfo?
    ) {

        if (!active.get()) {
            return
        }

        synchronized(lock) {

            if (
                actionType !=
                ACTION_WHATSAPP
            ) {
                return
            }

            if (
                whatsappSendAttempted
            ) {
                return
            }
        }

        if (rootNode == null) {
            return
        }

        /*
         * WhatsApp can take a short amount of time to finish
         * drawing the conversation screen.
         *
         * Therefore we first look for a Send button.
         */

        val sendNode =
            findWhatsAppSendButton(
                rootNode
            )

        if (sendNode == null) {
            return
        }

        synchronized(lock) {

            if (!active.get()) {
                return
            }

            if (
                actionType !=
                ACTION_WHATSAPP
            ) {
                return
            }

            if (
                whatsappSendAttempted
            ) {
                return
            }

            /*
             * Mark this immediately so multiple accessibility
             * events cannot press Send more than once.
             */
            whatsappSendAttempted =
                true
        }

        val clicked =
            performClick(
                sendNode
            )

        if (clicked) {

            synchronized(lock) {
                active.set(false)
                actionType =
                    ACTION_NONE
            }

        } else {

            synchronized(lock) {
                whatsappSendAttempted =
                    false
            }
        }
    }

    /*
     * ============================================================
     * FIND WHATSAPP SEND BUTTON
     * ============================================================
     */

    private fun findWhatsAppSendButton(
        rootNode: AccessibilityNodeInfo
    ): AccessibilityNodeInfo? {

        val candidates =
            mutableListOf<AccessibilityNodeInfo>()

        collectNodes(
            node = rootNode,
            result = candidates
        )

        /*
         * First try accessibility text/content descriptions.
         */

        for (node in candidates) {

            val text =
                node.text
                    ?.toString()
                    ?.trim()
                    ?.lowercase(Locale.ROOT)
                    .orEmpty()

            val description =
                node.contentDescription
                    ?.toString()
                    ?.trim()
                    ?.lowercase(Locale.ROOT)
                    .orEmpty()

            if (
                isSendLabel(text) ||
                isSendLabel(description)
            ) {
                return node
            }
        }

        /*
         * Then check common WhatsApp resource IDs.
         *
         * WhatsApp versions can change these IDs, so the
         * accessibility label search above remains the primary
         * method.
         */

        for (node in candidates) {

            val viewId =
                node.viewIdResourceName
                    ?.lowercase(Locale.ROOT)
                    .orEmpty()

            if (
                viewId.contains("send") ||
                viewId.contains("voice_message")
            ) {

                if (
                    node.isClickable
                ) {
                    return node
                }
            }
        }

        return null
    }

    private fun isSendLabel(
        value: String
    ): Boolean {

        if (value.isBlank()) {
            return false
        }

        return value == "send" ||
                value == "send message" ||
                value == "भेजें" ||
                value == "भेजो"
    }

    /*
     * ============================================================
     * DIALER ACCESSIBILITY PROCESSING
     * ============================================================
     */

    fun handleDialerAccessibilityEvent(
        rootNode: AccessibilityNodeInfo?
    ) {

        if (!active.get()) {
            return
        }

        synchronized(lock) {

            if (
                actionType !=
                ACTION_CALL
            ) {
                return
            }

            /*
             * The normal call action is already started by
             * DeviceActionManager.
             *
             * This method is intentionally conservative:
             * it does not press arbitrary dialer controls.
             *
             * If cancellation is requested later, the call can
             * be ended through cancelCall().
             */
        }

        if (rootNode == null) {
            return
        }
    }

    /*
     * ============================================================
     * CANCEL ACTIVE CALL
     * ============================================================
     */

    fun cancelCall() {

        synchronized(lock) {

            if (
                actionType !=
                ACTION_CALL
            ) {
                active.set(false)
                return
            }

            if (
                callEndAttempted
            ) {
                return
            }

            callEndAttempted =
                true
        }

        val service =
            synchronized(lock) {
                accessibilityService
            }

        if (service == null) {
            cancel()
            return
        }

        val root =
            service.rootInActiveWindow

        if (root == null) {
            cancel()
            return
        }

        val endNode =
            findCallEndButton(
                root
            )

        if (endNode != null) {

            performClick(
                endNode
            )
        }

        cancel()
    }

    private fun findCallEndButton(
        rootNode: AccessibilityNodeInfo
    ): AccessibilityNodeInfo? {

        val nodes =
            mutableListOf<AccessibilityNodeInfo>()

        collectNodes(
            node = rootNode,
            result = nodes
        )

        for (node in nodes) {

            val text =
                node.text
                    ?.toString()
                    ?.trim()
                    ?.lowercase(Locale.ROOT)
                    .orEmpty()

            val description =
                node.contentDescription
                    ?.toString()
                    ?.trim()
                    ?.lowercase(Locale.ROOT)
                    .orEmpty()

            val id =
                node.viewIdResourceName
                    ?.lowercase(Locale.ROOT)
                    .orEmpty()

            if (
                text == "end call" ||
                text == "hang up" ||
                text == "disconnect" ||
                description == "end call" ||
                description == "hang up" ||
                description == "disconnect" ||
                id.contains("end_call") ||
                id.contains("hangup")
            ) {
                return node
            }
        }

        return null
    }

    /*
     * ============================================================
     * SCHEDULE WHATSAPP PROCESSING
     * ============================================================
     */

    private fun scheduleWhatsAppProcessing() {

        val generation =
            synchronized(lock) {
                workflowGeneration
            }

        handler.postDelayed(
            {

                synchronized(lock) {

                    if (
                        !active.get()
                    ) {
                        return@postDelayed
                    }

                    if (
                        actionType !=
                        ACTION_WHATSAPP
                    ) {
                        return@postDelayed
                    }

                    if (
                        generation !=
                        workflowGeneration
                    ) {
                        return@postDelayed
                    }
                }

                val service =
                    synchronized(lock) {
                        accessibilityService
                    }

                val root =
                    service?.rootInActiveWindow

                if (root != null) {

                    handleWhatsAppAccessibilityEvent(
                        root
                    )
                }

                /*
                 * Continue checking while the workflow remains
                 * active. This is needed because WhatsApp can
                 * render the conversation screen asynchronously.
                 */

                if (isActive()) {
                    scheduleWhatsAppProcessing()
                }

            },
            400L
        )
    }

    /*
     * ============================================================
     * ACCESSIBILITY SERVICE ENABLE CHECK
     * ============================================================
     */

    fun isAccessibilityServiceEnabled(
        context: Context
    ): Boolean {

        val expectedComponent =
            ComponentName(
                context,
                com.thirdeye.app.accessibility.NexusEyeAccessibilityService::class.java
            )

        val expectedName =
            expectedComponent.flattenToString()

        val enabledServices =
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
                ?: return false

        return enabledServices
            .split(':')
            .any {
                it.equals(
                    expectedName,
                    ignoreCase = true
                )
            }
    }

    /*
     * ============================================================
     * ACCESSIBILITY TREE HELPERS
     * ============================================================
     */

    private fun collectNodes(
        node: AccessibilityNodeInfo?,
        result: MutableList<AccessibilityNodeInfo>
    ) {

        if (node == null) {
            return
        }

        result += node

        for (
        index in
        0 until node.childCount
        ) {

            collectNodes(
                node.getChild(index),
                result
            )
        }
    }

    private fun performClick(
        node: AccessibilityNodeInfo?
    ): Boolean {

        if (node == null) {
            return false
        }

        if (node.isClickable) {

            return node.performAction(
                AccessibilityNodeInfo.ACTION_CLICK
            )
        }

        var parent =
            node.parent

        while (
            parent != null
        ) {

            if (
                parent.isClickable
            ) {

                return parent.performAction(
                    AccessibilityNodeInfo.ACTION_CLICK
                )
            }

            parent =
                parent.parent
        }

        return false
    }

    /*
     * ============================================================
     * DEBUG / STATE HELPERS
     * ============================================================
     */

    fun currentContactName(): String {
        synchronized(lock) {
            return contactName
        }
    }

    fun currentPhoneNumber(): String {
        synchronized(lock) {
            return phoneNumber
        }
    }

    fun currentMessage(): String {
        synchronized(lock) {
            return message
        }
    }

    fun currentActionType(): String {

        synchronized(lock) {

            return when (actionType) {

                ACTION_WHATSAPP ->
                    "WHATSAPP"

                ACTION_CALL ->
                    "CALL"

                else ->
                    "NONE"
            }
        }
    }
}