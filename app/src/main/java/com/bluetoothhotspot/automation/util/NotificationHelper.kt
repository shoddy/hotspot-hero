package com.bluetoothhotspot.automation.util

import android.content.Context
import com.bluetoothhotspot.automation.manager.AppNotificationManager

/**
 * Utility class to provide easy access to notification functionality
 */
object NotificationHelper {
    
    private var notificationManager: AppNotificationManager? = null
    
    /**
     * Initialize the notification manager with application context
     */
    fun initialize(context: Context) {
        notificationManager = AppNotificationManager(context.applicationContext)
    }
    
    /**
     * Get the notification manager instance
     */
    fun getInstance(): AppNotificationManager {
        return notificationManager ?: throw IllegalStateException(
            "NotificationHelper must be initialized before use"
        )
    }
    
    /**
     * Check if notifications are properly initialized
     */
    fun isInitialized(): Boolean {
        return notificationManager != null
    }
}