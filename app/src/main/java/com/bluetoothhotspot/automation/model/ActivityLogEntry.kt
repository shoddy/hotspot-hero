package com.bluetoothhotspot.automation.model

/**
 * Data model for activity log entries
 */
data class ActivityLogEntry(
    val timestamp: Long,
    val action: String,
    val deviceName: String,
    val success: Boolean,
    val details: String
) {
    fun getFormattedTimestamp(): String {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }
}