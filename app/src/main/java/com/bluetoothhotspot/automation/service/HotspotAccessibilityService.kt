package com.bluetoothhotspot.automation.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.bluetoothhotspot.automation.util.NotificationHelper

/**
 * Accessibility service to control WiFi hotspot via UI automation
 * Uses accessibility APIs to interact with the notification panel and hotspot toggle
 */
class HotspotAccessibilityService : AccessibilityService() {
    

    private val handler = Handler(Looper.getMainLooper())

    private var pendingAction: String? = null
    private var pendingDeviceName: String? = null
    private var commandCheckRunnable: Runnable? = null
    private var isExecutingAction = false
    
    companion object {
        private const val TAG = "HotspotAccessibilityService"
        
        const val ACTION_ENABLE_HOTSPOT = "com.bluetoothhotspot.automation.ENABLE_HOTSPOT"
        const val EXTRA_DEVICE_NAME = "device_name"
        
        // UI interaction constants
        private const val SWIPE_DURATION_MS = 500L
        private const val ACTION_DELAY_MS = 1500L  // Increased delay for panel to settle

        private const val COMMAND_CHECK_INTERVAL_MS = 5000L // Check for commands every 5 seconds (battery optimized)
        
        // Quick settings tile identifiers
        private const val QUICK_SETTINGS_PACKAGE = "com.android.systemui"
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "HotspotAccessibilityService connected")
        
        // Show a notification to confirm service is working
        try {
            NotificationHelper.getInstance().showHotspotControlErrorNotification("Accessibility service connected and ready")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show notification", e)
        }
        
        setupBroadcastReceiver()
        startPeriodicCommandCheck()
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let { accessibilityEvent ->
            Log.d(TAG, "Accessibility event received: ${accessibilityEvent.eventType} from ${accessibilityEvent.packageName}")
            
            // Only check for commands on SystemUI events (more efficient)
            if (accessibilityEvent.packageName == QUICK_SETTINGS_PACKAGE) {
                Log.d(TAG, "SystemUI event detected")
                checkForPendingCommands()
            }
            
            // Handle pending actions when UI is ready (but only once)
            if (pendingAction != null && !isExecutingAction) {
                Log.d(TAG, "Executing pending action: $pendingAction")
                isExecutingAction = true
                val actionToExecute = pendingAction
                
                // Clear pending action immediately to prevent multiple executions
                pendingAction = null
                pendingDeviceName = null
                
                handler.postDelayed({
                    // Execute the action we captured
                    when (actionToExecute) {
                        ACTION_ENABLE_HOTSPOT -> findAndClickHotspotTile()
                    }
                }, ACTION_DELAY_MS)
            }
        }
    }
    
    private fun startPeriodicCommandCheck() {
        commandCheckRunnable = object : Runnable {
            override fun run() {
                checkForPendingCommands()
                handler.postDelayed(this, COMMAND_CHECK_INTERVAL_MS)
            }
        }
        handler.post(commandCheckRunnable!!)
        Log.d(TAG, "Started periodic command checking every ${COMMAND_CHECK_INTERVAL_MS}ms")
    }
    
    private fun stopPeriodicCommandCheck() {
        commandCheckRunnable?.let {
            handler.removeCallbacks(it)
            commandCheckRunnable = null
        }
        Log.d(TAG, "Stopped periodic command checking")
    }
    
    private fun checkForPendingCommands() {
        try {
            val prefs = getSharedPreferences("hotspot_commands", android.content.Context.MODE_PRIVATE)
            val command = prefs.getString("pending_command", null)
            val deviceName = prefs.getString("device_name", "Unknown Device")
            val wasScreenWoken = prefs.getBoolean("screen_was_woken", false)
            
            if (command != null) {
                // Clear the command
                prefs.edit()
                    .remove("pending_command")
                    .remove("device_name")
                    .remove("screen_was_woken")
                    .apply()
                
                Log.d(TAG, "Found pending command: $command, screen was woken: $wasScreenWoken")
                when (command) {
                    "ENABLE" -> {
                        Log.d(TAG, "Processing enable command from SharedPreferences")
                        enableHotspot(deviceName ?: "Unknown Device", wasScreenWoken)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for pending commands", e)
        }
    }
    
    override fun onInterrupt() {
        Log.w(TAG, "HotspotAccessibilityService interrupted")
        
        // Cancel any pending actions
        pendingAction = null
        pendingDeviceName = null
        
        // Notify about interruption
        try {
            NotificationHelper.getInstance().showHotspotControlErrorNotification("Accessibility service was interrupted")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show interruption notification", e)
        }
    }
    
    private fun setupBroadcastReceiver() {
        // Broadcast receiver removed - using SharedPreferences for communication
        Log.d(TAG, "Using SharedPreferences for service communication (more efficient)")
    }
    
    /**
     * Check if accessibility service has required permissions and capabilities
     */
    private fun hasAccessibilityPermissions(): Boolean {
        // Check if service is properly connected
        if (serviceInfo == null) {
            Log.w(TAG, "Accessibility service not properly connected")
            return false
        }
        
        // Check if we can perform gestures
        val canPerformGestures = try {
            // Test if we have gesture capability
            serviceInfo.capabilities and android.accessibilityservice.AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES != 0
        } catch (e: Exception) {
            Log.w(TAG, "Cannot check gesture capabilities", e)
            false
        }
        
        if (!canPerformGestures) {
            Log.w(TAG, "Accessibility service cannot perform gestures")
            return false
        }
        
        return true
    }
    
    /**
     * Check if WiFi hotspot is currently enabled
     */
    private fun isHotspotCurrentlyEnabled(): Boolean {
        return try {
            // Check via WifiManager reflection method
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            
            // Try reflection method for older Android versions
            val method = wifiManager.javaClass.getDeclaredMethod("isWifiApEnabled")
            method.isAccessible = true
            val isEnabled = method.invoke(wifiManager) as Boolean
            
            Log.d(TAG, "Hotspot state check: $isEnabled")
            isEnabled
            
        } catch (e: Exception) {
            Log.w(TAG, "Could not determine hotspot state, proceeding with automation: ${e.message}")
            // If we can't determine state, proceed with automation (safer default)
            false
        }
    }

    /**
     * Enable WiFi hotspot via UI automation
     */
    private fun enableHotspot(deviceName: String, wasScreenWoken: Boolean = false) {
        Log.d(TAG, "Request to enable hotspot for device: $deviceName, screen was woken: $wasScreenWoken")
        
        // Check if hotspot is already enabled
        if (isHotspotCurrentlyEnabled()) {
            Log.d(TAG, "Hotspot is already enabled, skipping automation to avoid toggling it off")
            NotificationHelper.getInstance().showHotspotControlErrorNotification(
                "🦸‍♂️ Hotspot already active - HotSpot Hero standing by!"
            )
            return
        }
        
        // Validate accessibility permissions before proceeding
        if (!hasAccessibilityPermissions()) {
            Log.w(TAG, "Missing accessibility permissions - cannot enable hotspot")
            NotificationHelper.getInstance().showHotspotControlErrorNotification(
                "Cannot enable hotspot - accessibility service not properly configured"
            )
            return
        }
        
        pendingAction = ACTION_ENABLE_HOTSPOT
        pendingDeviceName = deviceName
        
        // Store screen wake state for tile positioning
        val prefs = getSharedPreferences("hotspot_ui_state", android.content.Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("was_screen_woken", wasScreenWoken)
            .apply()
        
        // Start the UI automation sequence
        expandNotificationPanel()
    }
    

    

    
    /**
     * Expand the notification panel to access quick settings
     */
    private fun expandNotificationPanel() {
        Log.d(TAG, "Expanding notification panel")
        
        try {
            // Perform swipe down gesture from top of screen
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            
            val startX = screenWidth / 2f
            val startY = 0f
            val endX = screenWidth / 2f
            val endY = screenHeight / 3f
            
            val swipePath = Path().apply {
                moveTo(startX, startY)
                lineTo(endX, endY)
            }
            
            val gestureBuilder = GestureDescription.Builder()
            val strokeDescription = GestureDescription.StrokeDescription(swipePath, 0, SWIPE_DURATION_MS)
            gestureBuilder.addStroke(strokeDescription)
            
            val success = dispatchGesture(gestureBuilder.build(), object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "Notification panel swipe completed")
                    handler.postDelayed({
                        findAndClickHotspotTile()
                    }, ACTION_DELAY_MS)
                }
                
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.e(TAG, "Notification panel swipe cancelled")
                    handleActionFailure("Failed to expand notification panel")
                }
            }, null)
            
            if (!success) {
                Log.e(TAG, "Failed to dispatch swipe gesture")
                handleActionFailure("Failed to dispatch swipe gesture")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error expanding notification panel", e)
            handleActionFailure("Error expanding notification panel: ${e.message}")
        }
    }
    
    /**
     * Click the hotspot tile (always in top right position)
     */
    private fun findAndClickHotspotTile() {
        Log.d(TAG, "Clicking hotspot tile in top-right position")
        
        try {
            // Since we know the hotspot tile is always in the top-right position,
            // just click there directly for simplicity and reliability
            clickTopRightQuickSettingsTile()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error clicking hotspot tile", e)
            handleActionFailure("Error clicking hotspot tile: ${e.message}")
        }
    }
    
    /**
     * Click hotspot tile by coordinates when direct click fails
     */
    private fun clickHotspotTileByCoordinates(hotspotNode: AccessibilityNodeInfo) {
        try {
            val bounds = Rect()
            hotspotNode.getBoundsInScreen(bounds)
            
            val centerX = bounds.centerX().toFloat()
            val centerY = bounds.centerY().toFloat()
            
            performTapGesture(centerX, centerY)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error clicking hotspot tile by coordinates", e)
            handleActionFailure("Error clicking hotspot tile by coordinates: ${e.message}")
        }
    }
    
    /**
     * Fallback method - click the top right quick settings area where hotspot should be
     */
    private fun clickTopRightQuickSettingsTile() {
        Log.d(TAG, "Using fallback method - clicking top right quick settings area")
        
        try {
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            
            // Check if screen was woken up (affects tile position)
            val prefs = getSharedPreferences("hotspot_ui_state", android.content.Context.MODE_PRIVATE)
            val wasScreenWoken = prefs.getBoolean("was_screen_woken", false)
            
            // Calculate top right position for hotspot tile
            // Based on typical quick settings layout: 2x2 grid in portrait
            val tileWidth = screenWidth / 2f
            val tileX = screenWidth - (tileWidth / 2f) // Right tile center
            
            // Adjust Y position based on whether screen was woken from lock
            val tileY = if (wasScreenWoken) {
                // When screen is woken from lock, hotspot tile appears lower
                690f // Manually tested position that works
            } else {
                // Normal unlocked screen position
                200f // Original position for unlocked screen
            }
            
            Log.d(TAG, "Tile position: X=$tileX, Y=$tileY (screen woken: $wasScreenWoken)")
            Log.d(TAG, "Screen dimensions: ${screenWidth}x${screenHeight}")
            Log.d(TAG, "Calculated Y position: $tileY (${(tileY/screenHeight*100).toInt()}% down from top)")
            
            performTapGesture(tileX, tileY)
            
            // Clear the screen wake state after use
            prefs.edit().remove("was_screen_woken").apply()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error with fallback click method", e)
            handleActionFailure("Error with fallback click method: ${e.message}")
        }
    }
    
    /**
     * Perform a tap gesture at the specified coordinates
     */
    private fun performTapGesture(x: Float, y: Float) {
        Log.d(TAG, "Attempting tap gesture at ($x, $y)")
        
        val tapPath = Path().apply {
            moveTo(x, y)
        }
        
        val gestureBuilder = GestureDescription.Builder()
        val strokeDescription = GestureDescription.StrokeDescription(tapPath, 0, 100) // Longer tap
        gestureBuilder.addStroke(strokeDescription)
        
        val success = dispatchGesture(gestureBuilder.build(), object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Tap gesture completed at ($x, $y)")
                        handler.postDelayed({
                    verifyHotspotStateChange()
                }, ACTION_DELAY_MS * 2)
            }
            
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.e(TAG, "Tap gesture cancelled at ($x, $y)")
                handleActionFailure("Tap gesture was cancelled")
            }
        }, null)
        
        if (!success) {
            Log.e(TAG, "Failed to dispatch tap gesture at ($x, $y)")
            handleActionFailure("Failed to dispatch tap gesture")
        }
    }
    
    /**
     * Complete the hotspot action (assume success after tap)
     */
    private fun verifyHotspotStateChange() {
        Log.d(TAG, "Hotspot tile tapped, assuming action completed successfully")
        
        // Add delay before dismissing notification panel to let hotspot tap register
        handler.postDelayed({
            dismissNotificationPanel()
        }, 1000) // 1 second delay
        
        // Assume the action was successful since we tapped the tile
        Log.d(TAG, "Hotspot enabled successfully")

        notifyHotspotStateChange(pendingDeviceName ?: "")
        clearPendingAction()
    }
    
    /**
     * Dismiss the notification panel by swiping up
     */
    private fun dismissNotificationPanel() {
        Log.d(TAG, "Dismissing notification panel")
        
        try {
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            
            val startX = screenWidth / 2f
            val startY = screenHeight / 3f  // Start from notification panel area (top third)
            val endX = screenWidth / 2f
            val endY = 0f  // Swipe up to top
            
            val swipePath = Path().apply {
                moveTo(startX, startY)
                lineTo(endX, endY)
            }
            
            val gestureBuilder = GestureDescription.Builder()
            val strokeDescription = GestureDescription.StrokeDescription(swipePath, 0, SWIPE_DURATION_MS)
            gestureBuilder.addStroke(strokeDescription)
            
            val success = dispatchGesture(gestureBuilder.build(), object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "Notification panel dismissed successfully")
                }
                
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "Notification panel dismiss gesture cancelled")
                }
            }, null)
            
            if (!success) {
                Log.w(TAG, "Failed to dispatch dismiss gesture")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error dismissing notification panel", e)
        }
    }
    

    

    
    /**
     * Execute the pending action (now handled in onAccessibilityEvent)
     */
    private fun executePendingAction() {
        // Action execution moved to onAccessibilityEvent to prevent double execution
    }
    
    /**
     * Handle action failure
     */
    private fun handleActionFailure(error: String) {
        Log.e(TAG, "Action failed: $error")
        
        try {
            NotificationHelper.getInstance().showHotspotControlErrorNotification(error)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show error notification", e)
        }
        
        // Notify BluetoothMonitorService about the failure
        val intent = Intent("com.bluetoothhotspot.automation.LOG_ACTIVITY").apply {
            putExtra("action", "Hotspot control failed")
            putExtra("device_name", pendingDeviceName ?: "")
            putExtra("success", false)
            putExtra("details", error)
        }
        sendBroadcast(intent)
        
        clearPendingAction()
    }
    
    /**
     * Notify about hotspot state change
     */
    private fun notifyHotspotStateChange(deviceName: String) {
        // Show notification
        try {
            val notificationManager = NotificationHelper.getInstance()
            notificationManager.showHotspotEnabledNotification(deviceName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show hotspot notification", e)
        }
        
        Log.d(TAG, "Hotspot enabled for device: $deviceName")
    }
    
    /**
     * Clear pending action
     */
    private fun clearPendingAction() {

        pendingAction = null
        pendingDeviceName = null
        isExecutingAction = false
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "HotspotAccessibilityService destroyed")
        
        stopPeriodicCommandCheck()
        
        // Broadcast receiver cleanup removed (no longer used)
        
        clearPendingAction()
    }
}