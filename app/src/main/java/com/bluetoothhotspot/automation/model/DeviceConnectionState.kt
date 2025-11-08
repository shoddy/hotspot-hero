package com.bluetoothhotspot.automation.model

/**
 * Data model for device connection state
 */
data class DeviceConnectionState(
    val deviceName: String,
    val isConnected: Boolean,
    val connectionTime: Long,
    val hotspotEnabled: Boolean
)