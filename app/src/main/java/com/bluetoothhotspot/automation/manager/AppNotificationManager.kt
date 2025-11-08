package com.bluetoothhotspot.automation.manager

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bluetoothhotspot.automation.MainActivity
import com.bluetoothhotspot.automation.R

/**
 * Manages all notifications for the Bluetooth Hotspot Automation app
 */
class AppNotificationManager(private val context: Context) {
    
    private val notificationManager = NotificationManagerCompat.from(context)
    
    companion object {
        // Notification channels
        private const val CHANNEL_SERVICE = "service_channel"
        private const val CHANNEL_AUTOMATION = "automation_channel"
        private const val CHANNEL_ERROR = "error_channel"
        
        // Notification IDs
        const val NOTIFICATION_ID_SERVICE = 1001
        const val NOTIFICATION_ID_HOTSPOT_ENABLED = 1002
        const val NOTIFICATION_ID_HOTSPOT_DISABLED = 1003
        const val NOTIFICATION_ID_ERROR = 1004
        
        // Request codes for PendingIntents
        private const val REQUEST_CODE_MAIN_ACTIVITY = 100
    }
    
    init {
        createNotificationChannels()
    }
    
    /**
     * Create notification channels for different types of notifications
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Service channel - for persistent service notification
            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE,
                "Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent notification while the automation service is running"
                setShowBadge(false)
            }
            
            // Automation channel - for hotspot events
            val automationChannel = NotificationChannel(
                CHANNEL_AUTOMATION,
                "Automation Events",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications when hotspot is automatically enabled or disabled"
                setShowBadge(true)
            }
            
            // Error channel - for error notifications
            val errorChannel = NotificationChannel(
                CHANNEL_ERROR,
                "Errors",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Important error notifications that require attention"
                setShowBadge(true)
            }
            
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(automationChannel)
            manager.createNotificationChannel(errorChannel)
        }
    }
    
    /**
     * Create persistent notification for the background service
     */
    fun createServiceNotification(): Notification {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_MAIN_ACTIVITY,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setContentTitle(context.getString(R.string.service_running))
            .setContentText("Monitoring Bluetooth connections for automation")
            .setSmallIcon(R.drawable.ic_bluetooth_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
    
    /**
     * Show notification when hotspot is enabled
     */
    fun showHotspotEnabledNotification(deviceName: String) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_MAIN_ACTIVITY,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_AUTOMATION)
            .setContentTitle(context.getString(R.string.hotspot_enabled_notification))
            .setContentText("Connected to $deviceName")
            .setSmallIcon(R.drawable.ic_hotspot_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID_HOTSPOT_ENABLED, notification)
    }
    
    /**
     * Show notification when hotspot is disabled
     */
    fun showHotspotDisabledNotification(deviceName: String) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_MAIN_ACTIVITY,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_AUTOMATION)
            .setContentTitle(context.getString(R.string.hotspot_disabled_notification))
            .setContentText("Disconnected from $deviceName")
            .setSmallIcon(R.drawable.ic_hotspot_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID_HOTSPOT_DISABLED, notification)
    }
    
    /**
     * Show error notification
     */
    fun showErrorNotification(title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_MAIN_ACTIVITY,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ERROR)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_error_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .build()
        
        notificationManager.notify(NOTIFICATION_ID_ERROR, notification)
    }
    
    /**
     * Show permission error notification
     */
    fun showPermissionErrorNotification() {
        showErrorNotification(
            "Permissions Required",
            "Bluetooth Hotspot Automation needs Bluetooth and Location permissions to work properly. Tap to open settings."
        )
    }
    
    /**
     * Show accessibility service error notification
     */
    fun showAccessibilityServiceErrorNotification() {
        showErrorNotification(
            "Accessibility Service Required",
            "Enable the accessibility service to allow automatic hotspot control. Tap to open settings."
        )
    }
    
    /**
     * Show Bluetooth connection error notification
     */
    fun showBluetoothErrorNotification(error: String) {
        showErrorNotification(
            "Bluetooth Error",
            "Failed to monitor Bluetooth connections: $error"
        )
    }
    
    /**
     * Show hotspot control error notification
     */
    fun showHotspotControlErrorNotification(error: String) {
        showErrorNotification(
            "Hotspot Control Error",
            "Failed to control hotspot: $error"
        )
    }
    
    /**
     * Cancel a specific notification
     */
    fun cancelNotification(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }
    
    /**
     * Cancel all automation event notifications (but keep service notification)
     */
    fun cancelAutomationNotifications() {
        notificationManager.cancel(NOTIFICATION_ID_HOTSPOT_ENABLED)
        notificationManager.cancel(NOTIFICATION_ID_HOTSPOT_DISABLED)
        notificationManager.cancel(NOTIFICATION_ID_ERROR)
    }
    
    /**
     * Check if notifications are enabled for this app
     */
    fun areNotificationsEnabled(): Boolean {
        return notificationManager.areNotificationsEnabled()
    }
    
    /**
     * Check if a specific notification channel is enabled
     */
    fun isChannelEnabled(channelId: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = manager.getNotificationChannel(channelId)
            return channel?.importance != NotificationManager.IMPORTANCE_NONE
        }
        return areNotificationsEnabled()
    }
}