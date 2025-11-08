package com.bluetoothhotspot.automation.service

import android.Manifest
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.bluetoothhotspot.automation.MainActivity
import com.bluetoothhotspot.automation.manager.AppNotificationManager
import com.bluetoothhotspot.automation.manager.ConnectionStateManager
import com.bluetoothhotspot.automation.manager.ScreenWakeManager
import com.bluetoothhotspot.automation.model.ActivityLogEntry
import com.bluetoothhotspot.automation.model.DeviceConnectionState
import com.bluetoothhotspot.automation.util.NotificationHelper

/**
 * Background service to monitor Bluetooth device connections
 * Handles connection/disconnection events and triggers hotspot automation
 */
class BluetoothMonitorService : Service() {
    
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var notificationManager: AppNotificationManager
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var connectionStateManager: ConnectionStateManager
    private lateinit var screenWakeManager: ScreenWakeManager
    
    private var bluetoothReceiver: BluetoothBroadcastReceiver? = null
    private var targetDeviceName: String = ""
    private var isAutomationEnabled: Boolean = true
    private var isTargetDeviceConnected: Boolean = false
    
    // Debouncing for connection events
    private val debounceHandler = Handler(Looper.getMainLooper())
    private var disconnectionRunnable: Runnable? = null
    private val debounceDelayMs = 5000L // 5 seconds
    
    companion object {
        private const val TAG = "BluetoothMonitorService"
        private const val PREFS_NAME = "bluetooth_hotspot_automation"
        private const val PREF_AUTOMATION_ENABLED = "automation_enabled"
        private const val PREF_TARGET_DEVICE = "target_device"
        
        const val ACTION_START_MONITORING = "com.bluetoothhotspot.automation.START_MONITORING"
        const val ACTION_STOP_MONITORING = "com.bluetoothhotspot.automation.STOP_MONITORING"
        const val ACTION_UPDATE_TARGET_DEVICE = "com.bluetoothhotspot.automation.UPDATE_TARGET_DEVICE"
        const val ACTION_UPDATE_HOTSPOT_STATE = "com.bluetoothhotspot.automation.UPDATE_HOTSPOT_STATE"
        const val ACTION_UPDATE_AUTOMATION_STATE = "com.bluetoothhotspot.automation.UPDATE_AUTOMATION_STATE"
        const val EXTRA_TARGET_DEVICE = "target_device"
        const val EXTRA_HOTSPOT_ENABLED = "hotspot_enabled"
        const val EXTRA_AUTOMATION_ENABLED = "automation_enabled"
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "BluetoothMonitorService created")
        
        initializeService()
    }
    
    private fun initializeService() {
        try {
            // Initialize notification manager
            notificationManager = NotificationHelper.getInstance()
            
            // Initialize shared preferences
            sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            
            // Initialize connection state manager
            connectionStateManager = ConnectionStateManager(this)
            
            // Initialize screen wake manager
            screenWakeManager = ScreenWakeManager(this)
            
            // Initialize Bluetooth adapter
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                ?: throw IllegalStateException("Bluetooth not supported on this device")
            
            // Load settings and restore connection state
            loadSettings()
            restoreConnectionState()
            
            Log.d(TAG, "BluetoothMonitorService initialized successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize BluetoothMonitorService", e)
            notificationManager.showBluetoothErrorNotification("Service initialization failed: ${e.message}")
            stopSelf()
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_START_MONITORING -> startMonitoring()
            ACTION_STOP_MONITORING -> stopMonitoring()
            ACTION_UPDATE_TARGET_DEVICE -> {
                val newTargetDevice = intent.getStringExtra(EXTRA_TARGET_DEVICE) ?: ""
                updateTargetDevice(newTargetDevice)
            }
            ACTION_UPDATE_HOTSPOT_STATE -> {
                val hotspotEnabled = intent.getBooleanExtra(EXTRA_HOTSPOT_ENABLED, false)
                updateHotspotState(hotspotEnabled)
            }
            ACTION_UPDATE_AUTOMATION_STATE -> {
                val automationEnabled = intent.getBooleanExtra(EXTRA_AUTOMATION_ENABLED, true)
                updateAutomationState(automationEnabled)
            }
            else -> startMonitoring() // Default action
        }
        
        return START_STICKY
    }
    
    private fun loadSettings() {
        isAutomationEnabled = sharedPreferences.getBoolean(PREF_AUTOMATION_ENABLED, true)
        targetDeviceName = sharedPreferences.getString(PREF_TARGET_DEVICE, "") ?: ""
        
        Log.d(TAG, "Settings loaded - Automation: $isAutomationEnabled, Target: $targetDeviceName")
    }
    
    private fun restoreConnectionState() {
        val savedState = connectionStateManager.loadConnectionState()
        if (savedState != null && savedState.deviceName == targetDeviceName) {
            isTargetDeviceConnected = savedState.isConnected
            
            // Check if we were in the middle of a debounce period
            if (connectionStateManager.isDebounceActive()) {
                val timeSinceDisconnection = connectionStateManager.getTimeSinceLastDisconnection()
                val remainingDebounceTime = debounceDelayMs - timeSinceDisconnection
                
                if (remainingDebounceTime > 0) {
                    Log.d(TAG, "Resuming debounce period, ${remainingDebounceTime}ms remaining")
                    scheduleDisconnectionAction(remainingDebounceTime)
                } else {
                    Log.d(TAG, "Debounce period expired while service was stopped")
                    connectionStateManager.clearDebounceState()
                    if (isTargetDeviceConnected) {
                        // Complete the disconnection that was pending
                        processDisconnection()
                    }
                }
            }
            
            Log.d(TAG, "Connection state restored: connected=$isTargetDeviceConnected")
            
            // Verify the restored state by checking actual connection status
            // This prevents acting on stale connection data
            debounceHandler.postDelayed({
                checkCurrentConnectionState()
            }, 1000) // Give a moment for Bluetooth to be ready
        }
    }
    
    private fun saveCurrentConnectionState() {
        val state = DeviceConnectionState(
            deviceName = targetDeviceName,
            isConnected = isTargetDeviceConnected,
            connectionTime = System.currentTimeMillis(),
            hotspotEnabled = false // This will be updated by the accessibility service
        )
        connectionStateManager.saveConnectionState(state)
    }
    
    private fun hasRequiredPermissions(): Boolean {
        val bluetoothPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.BLUETOOTH
        ) == PackageManager.PERMISSION_GRANTED
        
        val bluetoothAdminPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.BLUETOOTH_ADMIN
        ) == PackageManager.PERMISSION_GRANTED
        
        val locationPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        val bluetoothConnectPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        
        val bluetoothScanPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        
        val hasAllPermissions = bluetoothPermission && bluetoothAdminPermission && 
                               locationPermission && bluetoothConnectPermission && bluetoothScanPermission
        
        if (!hasAllPermissions) {
            Log.w(TAG, "Missing required permissions - Bluetooth: $bluetoothPermission, " +
                      "Admin: $bluetoothAdminPermission, Location: $locationPermission, " +
                      "Connect: $bluetoothConnectPermission, Scan: $bluetoothScanPermission")
        }
        
        return hasAllPermissions
    }
    
    private fun startMonitoring() {
        if (!hasRequiredPermissions()) {
            Log.w(TAG, "Missing required permissions for Bluetooth monitoring")
            notificationManager.showPermissionErrorNotification()
            return
        }
        
        if (!bluetoothAdapter.isEnabled) {
            Log.w(TAG, "Bluetooth is not enabled")
            notificationManager.showBluetoothErrorNotification("Bluetooth is not enabled")
            return
        }
        
        if (targetDeviceName.isEmpty()) {
            Log.w(TAG, "No target device configured")
            return
        }
        
        // Start foreground service with notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                AppNotificationManager.NOTIFICATION_ID_SERVICE,
                notificationManager.createServiceNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(
                AppNotificationManager.NOTIFICATION_ID_SERVICE,
                notificationManager.createServiceNotification()
            )
        }
        
        // Register Bluetooth broadcast receiver
        registerBluetoothReceiver()
        
        // Check current connection state
        checkCurrentConnectionState()
        
        Log.d(TAG, "Bluetooth monitoring started for device: $targetDeviceName")
        logActivity("Monitoring started", targetDeviceName, true, "Bluetooth monitoring service started")
    }
    
    private fun stopMonitoring() {
        // Unregister receiver
        unregisterBluetoothReceiver()
        
        // Cancel any pending debounce operations
        disconnectionRunnable?.let { debounceHandler.removeCallbacks(it) }
        
        // Stop foreground service
        stopForeground(true)
        
        Log.d(TAG, "Bluetooth monitoring stopped")
        logActivity("Monitoring stopped", targetDeviceName, true, "Bluetooth monitoring service stopped")
        
        stopSelf()
    }
    
    private fun updateTargetDevice(newTargetDevice: String) {
        val oldTarget = targetDeviceName
        targetDeviceName = newTargetDevice
        
        // Save to preferences
        sharedPreferences.edit()
            .putString(PREF_TARGET_DEVICE, targetDeviceName)
            .apply()
        
        Log.d(TAG, "Target device updated from '$oldTarget' to '$targetDeviceName'")
        logActivity("Target device updated", targetDeviceName, true, "Changed from '$oldTarget' to '$targetDeviceName'")
        
        // Restart monitoring with new target
        if (bluetoothReceiver != null) {
            unregisterBluetoothReceiver()
            registerBluetoothReceiver()
            checkCurrentConnectionState()
        }
    }
    
    private fun registerBluetoothReceiver() {
        if (bluetoothReceiver == null) {
            bluetoothReceiver = BluetoothBroadcastReceiver()
            
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            }
            
            registerReceiver(bluetoothReceiver, filter)
            Log.d(TAG, "Bluetooth broadcast receiver registered")
        }
    }
    
    private fun unregisterBluetoothReceiver() {
        bluetoothReceiver?.let {
            try {
                unregisterReceiver(it)
                bluetoothReceiver = null
                Log.d(TAG, "Bluetooth broadcast receiver unregistered")
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Receiver was not registered", e)
            }
        }
    }
    
    private fun checkCurrentConnectionState() {
        if (!hasRequiredPermissions()) return
        
        try {
            // Check if target device is currently connected by looking at bonded devices
            // and checking their connection state via BluetoothManager
            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
            val connectedDevices = bluetoothManager.getConnectedDevices(android.bluetooth.BluetoothProfile.A2DP)
            
            val isConnected = connectedDevices.any { device ->
                device.name == targetDeviceName
            }
            
            Log.d(TAG, "Initial connection check: $targetDeviceName connected = $isConnected")
            updateConnectionState(isConnected, "Initial state check")
            
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception checking connection state", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking connection state", e)
            // Fallback: assume disconnected on startup
            updateConnectionState(false, "Error during initial check - assuming disconnected")
        }
    }
    
    private fun updateConnectionState(connected: Boolean, reason: String) {
        if (isTargetDeviceConnected == connected) {
            return // No state change
        }
        
        val previousState = isTargetDeviceConnected
        isTargetDeviceConnected = connected
        
        Log.d(TAG, "Connection state changed: $previousState -> $connected ($reason)")
        
        // Send broadcast to MainActivity to update UI (explicit broadcast)
        val intent = Intent("com.bluetoothhotspot.automation.BLUETOOTH_STATE_CHANGED").apply {
            putExtra("device_name", targetDeviceName)
            putExtra("connected", connected)
            setPackage(packageName) // Make it explicit
        }
        sendBroadcast(intent)
        Log.d(TAG, "Sent BLUETOOTH_STATE_CHANGED broadcast: device=$targetDeviceName, connected=$connected")
        
        // Save the new connection state
        saveCurrentConnectionState()
        
        if (connected) {
            handleDeviceConnected()
        } else {
            handleDeviceDisconnected()
        }
        
        // Update MainActivity if it's running
        updateMainActivity()
    }
    
    private fun handleDeviceConnected() {
        // Cancel any pending disconnection and clear debounce state
        disconnectionRunnable?.let { 
            debounceHandler.removeCallbacks(it)
            disconnectionRunnable = null
            connectionStateManager.clearDebounceState()
            Log.d(TAG, "Cancelled pending disconnection due to reconnection")
        }
        
        if (!isAutomationEnabled) {
            Log.d(TAG, "Device connected but automation is disabled")
            logActivity("Device connected", targetDeviceName, true, "Automation disabled - no action taken")
            return
        }
        
        if (targetDeviceName.isEmpty()) {
            Log.d(TAG, "Device connected but no target device configured")
            logActivity("Device connected", "Unknown", true, "No target device configured - no action taken")
            return
        }
        
        Log.d(TAG, "Target device connected: $targetDeviceName")
        
        // Check if screen needs to be woken
        if (!screenWakeManager.isScreenOn()) {
            Log.d(TAG, "Screen is off, attempting to wake for automation")
            val wakeSuccess = screenWakeManager.wakeScreenForAutomation()
            
            if (wakeSuccess) {
                logActivity("Screen woken", targetDeviceName, true, "Screen woken for hotspot automation")
                
                // Add delay to let screen fully wake up before automation
                debounceHandler.postDelayed({
                    triggerHotspotControl(true, wasScreenWoken = true)
                    // Release wake lock after automation
                    debounceHandler.postDelayed({
                        screenWakeManager.releaseWakeLock()
                    }, 5000) // 5 seconds after automation
                }, 2000) // 2 second delay for screen to wake
            } else {
                logActivity("Screen wake failed", targetDeviceName, false, "Could not wake screen for automation")
            }
        } else {
            // Screen is already on, proceed normally
            logActivity("Device connected", targetDeviceName, true, "Bluetooth connection established")
            triggerHotspotControl(true, wasScreenWoken = false)
        }
    }
    
    private fun handleDeviceDisconnected() {
        Log.d(TAG, "Target device disconnected: $targetDeviceName - starting debounce timer")
        
        // Save disconnection timestamp for persistence
        connectionStateManager.saveDisconnectionTime(System.currentTimeMillis())
        
        // Cancel any existing disconnection runnable
        disconnectionRunnable?.let { debounceHandler.removeCallbacks(it) }
        
        // Schedule disconnection action
        scheduleDisconnectionAction(debounceDelayMs)
        
        logActivity("Device disconnected", targetDeviceName, true, "Starting ${debounceDelayMs/1000}s debounce timer")
    }
    
    private fun scheduleDisconnectionAction(delayMs: Long) {
        // Create new debounced disconnection handler
        disconnectionRunnable = Runnable {
            processDisconnection()
        }
        
        debounceHandler.postDelayed(disconnectionRunnable!!, delayMs)
        Log.d(TAG, "Scheduled disconnection action in ${delayMs}ms")
    }
    
    private fun processDisconnection() {
        if (!isAutomationEnabled) {
            Log.d(TAG, "Device disconnected but automation is disabled")
            logActivity("Device disconnected", targetDeviceName, true, "Automation disabled - no action taken")
            connectionStateManager.clearDebounceState()
            return
        }
        
        Log.d(TAG, "Debounce period completed - processing disconnection")
        logActivity("Device disconnected", targetDeviceName, true, "Bluetooth connection lost after debounce period")
        
        // Clear debounce state
        connectionStateManager.clearDebounceState()
        
        // Trigger hotspot disable via accessibility service
        triggerHotspotControl(false)
    }
    
    private fun triggerHotspotControl(enable: Boolean, wasScreenWoken: Boolean = false) {
        try {
            // Use SharedPreferences to communicate with HotspotAccessibilityService
            val prefs = getSharedPreferences("hotspot_commands", Context.MODE_PRIVATE)
            val command = if (enable) "ENABLE" else "DISABLE"
            
            prefs.edit()
                .putString("pending_command", command)
                .putString("device_name", targetDeviceName)
                .putBoolean("screen_was_woken", wasScreenWoken)
                .apply()
            
            val action = if (enable) "enable" else "disable"
            val wakeInfo = if (wasScreenWoken) " (screen woken)" else ""
            Log.d(TAG, "Triggered hotspot $action request for device: $targetDeviceName$wakeInfo")
            logActivity("Hotspot $action requested", targetDeviceName, true, "Command sent to accessibility service$wakeInfo")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger hotspot control", e)
            logActivity("Hotspot control failed", targetDeviceName, false, "Error: ${e.message}")
        }
    }
    
    private fun updateMainActivity() {
        // Send broadcast to update MainActivity if it's running
        val intent = Intent("com.bluetoothhotspot.automation.BLUETOOTH_STATE_CHANGED").apply {
            putExtra("device_name", targetDeviceName)
            putExtra("connected", isTargetDeviceConnected)
        }
        sendBroadcast(intent)
    }
    
    private fun logActivity(action: String, deviceName: String, success: Boolean, details: String) {
        // Send broadcast to MainActivity for activity log
        val intent = Intent("com.bluetoothhotspot.automation.LOG_ACTIVITY").apply {
            putExtra("action", action)
            putExtra("device_name", deviceName)
            putExtra("success", success)
            putExtra("details", details)
        }
        sendBroadcast(intent)
    }
    
    /**
     * Update hotspot state (called by accessibility service)
     */
    private fun updateHotspotState(enabled: Boolean) {
        connectionStateManager.updateHotspotState(enabled)
        
        // Send broadcast to update MainActivity
        val intent = Intent("com.bluetoothhotspot.automation.HOTSPOT_STATE_CHANGED").apply {
            putExtra("hotspot_enabled", enabled)
        }
        sendBroadcast(intent)
        
        Log.d(TAG, "Hotspot state updated: $enabled")
        logActivity(
            if (enabled) "Hotspot enabled" else "Hotspot disabled",
            targetDeviceName,
            true,
            "Hotspot state changed by accessibility service"
        )
    }
    
    /**
     * Update automation state (called by MainActivity)
     */
    private fun updateAutomationState(enabled: Boolean) {
        isAutomationEnabled = enabled
        
        // Save to preferences
        sharedPreferences.edit()
            .putBoolean(PREF_AUTOMATION_ENABLED, enabled)
            .apply()
        
        Log.d(TAG, "Automation state updated: $enabled")
        logActivity(
            if (enabled) "Automation enabled" else "Automation disabled",
            "",
            true,
            "Automation toggled by user"
        )
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "BluetoothMonitorService destroyed")
        
        unregisterBluetoothReceiver()
        disconnectionRunnable?.let { debounceHandler.removeCallbacks(it) }
        
        // Release wake lock if held
        screenWakeManager.releaseWakeLock()
        
        // Save final connection state before service stops
        saveCurrentConnectionState()
        
        logActivity("Service stopped", targetDeviceName, true, "Bluetooth monitoring service destroyed")
    }
    
    /**
     * Broadcast receiver for Bluetooth connection events
     */
    private inner class BluetoothBroadcastReceiver : BroadcastReceiver() {
        
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!hasRequiredPermissions()) return
            
            when (intent?.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val device: BluetoothDevice? = when {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        }
                        else -> {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                    }
                    
                    device?.let { bluetoothDevice ->
                        try {
                            val deviceName = bluetoothDevice.name
                            Log.d(TAG, "Device connected: $deviceName")
                            
                            if (deviceName == targetDeviceName) {
                                updateConnectionState(true, "ACL_CONNECTED broadcast")
                            }
                        } catch (e: SecurityException) {
                            Log.e(TAG, "Security exception getting device name", e)
                        }
                        Unit
                    }
                }
                
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device: BluetoothDevice? = when {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        }
                        else -> {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                    }
                    
                    device?.let { bluetoothDevice ->
                        try {
                            val deviceName = bluetoothDevice.name
                            Log.d(TAG, "Device disconnected: $deviceName")
                            
                            if (deviceName == targetDeviceName) {
                                updateConnectionState(false, "ACL_DISCONNECTED broadcast")
                            }
                        } catch (e: SecurityException) {
                            Log.e(TAG, "Security exception getting device name", e)
                        }
                        Unit
                    }
                }
                
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    Log.d(TAG, "Bluetooth adapter state changed: $state")
                    
                    when (state) {
                        BluetoothAdapter.STATE_OFF -> {
                            updateConnectionState(false, "Bluetooth turned off")
                            logActivity("Bluetooth disabled", "", false, "Bluetooth adapter turned off")
                        }
                        BluetoothAdapter.STATE_ON -> {
                            logActivity("Bluetooth enabled", "", true, "Bluetooth adapter turned on")
                            checkCurrentConnectionState()
                        }
                    }
                }
            }
        }
    }
}