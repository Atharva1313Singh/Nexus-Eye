package com.thirdeye.app

import com.thirdeye.app.bluetooth.NexusEyeBleManager

/**
 * Small process-level bridge for components that can outlive MainActivity,
 * such as the Hey Nexus foreground service.
 *
 * MainActivity owns the BLE manager and registers it here while that manager
 * is alive. The wake-word service can then reuse the same active connection
 * instead of creating a second BLE manager.
 */
object NexusEyeRuntime {

    @Volatile
    private var activeBleManager: NexusEyeBleManager? = null

    fun registerBleManager(
        manager: NexusEyeBleManager
    ) {
        activeBleManager = manager
    }

    fun unregisterBleManager(
        manager: NexusEyeBleManager
    ) {
        if (activeBleManager === manager) {
            activeBleManager = null
        }
    }

    fun getBleManager(): NexusEyeBleManager? {
        return activeBleManager
    }
}