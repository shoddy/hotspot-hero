package com.bluetoothhotspot.automation.model

/**
 * Data model for automation settings
 */
data class AutomationSettings(
    val isEnabled: Boolean = true,
    val targetBluetoothDevice: String = "",
    val debounceDelaySeconds: Int = 5,
    val showNotifications: Boolean = true
)