package com.bluetoothhotspot.automation.manager

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import android.util.Log

class ScreenWakeManager(private val context: Context) {
    
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    private var wakeLock: PowerManager.WakeLock? = null
    
    companion object {
        private const val TAG = "ScreenWakeManager"
        private const val WAKE_LOCK_TIMEOUT = 15000L // 15 seconds (sufficient for automation)
    }
    
    /**
     * Wake the screen and temporarily disable keyguard for automation
     */
    fun wakeScreenForAutomation(): Boolean {
        return try {
            Log.d(TAG, "Attempting to wake screen for automation")
            
            // Check if screen is already on
            if (powerManager.isInteractive) {
                Log.d(TAG, "Screen is already on")
                return true
            }
            
            // Acquire wake lock to turn on screen
            wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or 
                PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "BluetoothHotspot:ScreenWake"
            ).apply {
                acquire(WAKE_LOCK_TIMEOUT)
            }
            
            Log.d(TAG, "Screen wake lock acquired")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to wake screen", e)
            false
        }
    }
    
    /**
     * Release wake lock
     */
    fun releaseWakeLock() {
        try {
            wakeLock?.let { lock ->
                if (lock.isHeld) {
                    lock.release()
                    Log.d(TAG, "Wake lock released")
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing wake lock", e)
        }
    }
    
    /**
     * Check if screen is currently on
     */
    fun isScreenOn(): Boolean {
        return powerManager.isInteractive
    }
    
    /**
     * Check if device is locked
     */
    fun isDeviceLocked(): Boolean {
        return keyguardManager.isKeyguardLocked
    }
}