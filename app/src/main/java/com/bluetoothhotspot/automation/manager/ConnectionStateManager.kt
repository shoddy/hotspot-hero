package com.bluetoothhotspot.automation.manager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.bluetoothhotspot.automation.model.DeviceConnectionState

/**
 * Manages connection state persistence and tracking
 */
class ConnectionStateManager(private val context: Context) {
    
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    companion object {
        private const val TAG = "ConnectionStateManager"
        private const val PREFS_NAME = "connection_state"
        private const val PREF_DEVICE_NAME = "device_name"
        private const val PREF_IS_CONNECTED = "is_connected"
        private const val PREF_CONNECTION_TIME = "connection_time"
        private const val PREF_HOTSPOT_ENABLED = "hotspot_enabled"
        private const val PREF_LAST_DISCONNECTION_TIME = "last_disconnection_time"
        private const val PREF_DEBOUNCE_ACTIVE = "debounce_active"
    }
    
    /**
     * Save current connection state to persistent storage
     */
    fun saveConnectionState(state: DeviceConnectionState) {
        sharedPreferences.edit().apply {
            putString(PREF_DEVICE_NAME, state.deviceName)
            putBoolean(PREF_IS_CONNECTED, state.isConnected)
            putLong(PREF_CONNECTION_TIME, state.connectionTime)
            putBoolean(PREF_HOTSPOT_ENABLED, state.hotspotEnabled)
            apply()
        }
        
        Log.d(TAG, "Connection state saved: $state")
    }
    
    /**
     * Load connection state from persistent storage
     */
    fun loadConnectionState(): DeviceConnectionState? {
        val deviceName = sharedPreferences.getString(PREF_DEVICE_NAME, null)
        
        return if (deviceName != null) {
            val state = DeviceConnectionState(
                deviceName = deviceName,
                isConnected = sharedPreferences.getBoolean(PREF_IS_CONNECTED, false),
                connectionTime = sharedPreferences.getLong(PREF_CONNECTION_TIME, 0),
                hotspotEnabled = sharedPreferences.getBoolean(PREF_HOTSPOT_ENABLED, false)
            )
            Log.d(TAG, "Connection state loaded: $state")
            state
        } else {
            Log.d(TAG, "No saved connection state found")
            null
        }
    }
    
    /**
     * Clear all connection state data
     */
    fun clearConnectionState() {
        sharedPreferences.edit().clear().apply()
        Log.d(TAG, "Connection state cleared")
    }
    
    /**
     * Save disconnection timestamp for debouncing
     */
    fun saveDisconnectionTime(timestamp: Long) {
        sharedPreferences.edit().apply {
            putLong(PREF_LAST_DISCONNECTION_TIME, timestamp)
            putBoolean(PREF_DEBOUNCE_ACTIVE, true)
            apply()
        }
        Log.d(TAG, "Disconnection time saved: $timestamp")
    }
    
    /**
     * Get last disconnection timestamp
     */
    fun getLastDisconnectionTime(): Long {
        return sharedPreferences.getLong(PREF_LAST_DISCONNECTION_TIME, 0)
    }
    
    /**
     * Check if debounce is currently active
     */
    fun isDebounceActive(): Boolean {
        return sharedPreferences.getBoolean(PREF_DEBOUNCE_ACTIVE, false)
    }
    
    /**
     * Clear debounce state (called when debounce period completes or is cancelled)
     */
    fun clearDebounceState() {
        sharedPreferences.edit().apply {
            putBoolean(PREF_DEBOUNCE_ACTIVE, false)
            remove(PREF_LAST_DISCONNECTION_TIME)
            apply()
        }
        Log.d(TAG, "Debounce state cleared")
    }
    
    /**
     * Update hotspot state in connection data
     */
    fun updateHotspotState(enabled: Boolean) {
        sharedPreferences.edit().apply {
            putBoolean(PREF_HOTSPOT_ENABLED, enabled)
            apply()
        }
        Log.d(TAG, "Hotspot state updated: $enabled")
    }
    
    /**
     * Get time since last disconnection in milliseconds
     */
    fun getTimeSinceLastDisconnection(): Long {
        val lastDisconnectionTime = getLastDisconnectionTime()
        return if (lastDisconnectionTime > 0) {
            System.currentTimeMillis() - lastDisconnectionTime
        } else {
            Long.MAX_VALUE
        }
    }
    
    /**
     * Check if enough time has passed since disconnection for debounce to complete
     */
    fun shouldCompleteDebounce(debounceDelayMs: Long): Boolean {
        return getTimeSinceLastDisconnection() >= debounceDelayMs
    }
}