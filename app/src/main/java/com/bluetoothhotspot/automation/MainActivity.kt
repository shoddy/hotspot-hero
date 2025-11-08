package com.bluetoothhotspot.automation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bluetoothhotspot.automation.adapter.ActivityLogAdapter
import com.bluetoothhotspot.automation.model.ActivityLogEntry
import com.bluetoothhotspot.automation.model.AutomationSettings
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import com.bluetoothhotspot.automation.service.BluetoothMonitorService
import com.bluetoothhotspot.automation.service.HotspotAccessibilityService
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.util.Log

/**
 * Main activity for the Bluetooth Hotspot Automation app
 * Provides UI for monitoring status and controlling automation
 */
class MainActivity : AppCompatActivity() {
    
    // UI Components
    private lateinit var automationToggle: SwitchMaterial
    private lateinit var targetDeviceSpinner: Spinner
    private lateinit var refreshDevicesButton: MaterialButton
    private lateinit var activityLogRecyclerView: RecyclerView
    private lateinit var emptyLogText: TextView
    private lateinit var clearLogButton: MaterialButton
    private lateinit var testEnableButton: MaterialButton
    
    // Data and Adapters
    private lateinit var activityLogAdapter: ActivityLogAdapter
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var deviceSpinnerAdapter: ArrayAdapter<String>
    private val pairedDeviceNames = mutableListOf<String>()
    
    // Settings
    private var automationSettings = AutomationSettings()
    
    // Broadcast receivers
    private var bluetoothStateReceiver: BroadcastReceiver? = null
    private var activityLogReceiver: BroadcastReceiver? = null
    
    // Permission handling
    private val requiredPermissions = mutableListOf<String>().apply {
        add(Manifest.permission.BLUETOOTH)
        add(Manifest.permission.BLUETOOTH_ADMIN)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        handlePermissionResults(permissions)
    }
    
    private val accessibilitySettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkAccessibilityServiceStatus()
    }
    
    // Broadcast receiver removed - status displays removed
    
    companion object {
        private const val PREFS_NAME = "bluetooth_hotspot_automation"
        private const val PREF_AUTOMATION_ENABLED = "automation_enabled"
        private const val PREF_TARGET_DEVICE = "target_device"
        private const val PREF_SHOW_NOTIFICATIONS = "show_notifications"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initializeViews()
        initializeSharedPreferences()
        setupActivityLog()
        setupAutomationToggle()
        setupTargetDeviceSpinner()
        setupClearLogButton()
        setupTestButtons()
        loadSettings()
        updateUI()
        checkAndRequestPermissions()
        
        // Broadcast receivers removed
    }
    
    private fun initializeViews() {
        automationToggle = findViewById(R.id.automationToggle)
        targetDeviceSpinner = findViewById(R.id.targetDeviceSpinner)
        refreshDevicesButton = findViewById(R.id.refreshDevicesButton)
        activityLogRecyclerView = findViewById(R.id.activityLogRecyclerView)
        emptyLogText = findViewById(R.id.emptyLogText)
        clearLogButton = findViewById(R.id.clearLogButton)
        testEnableButton = findViewById(R.id.testEnableButton)
    }
    
    private fun initializeSharedPreferences() {
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    private fun setupActivityLog() {
        activityLogAdapter = ActivityLogAdapter()
        activityLogRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = activityLogAdapter
        }
        updateEmptyLogVisibility()
    }
    
    private fun setupAutomationToggle() {
        automationToggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Validate all requirements before enabling automation
                if (!hasAllRequiredPermissionsAndServices()) {
                    // Prevent enabling automation without proper permissions
                    automationToggle.isChecked = false
                    showActivityLog(
                        "Automation blocked",
                        "",
                        false,
                        "Cannot enable automation - missing permissions or accessibility service"
                    )
                    
                    // Show dialog to guide user
                    AlertDialog.Builder(this)
                        .setTitle("Requirements Not Met")
                        .setMessage(
                            "Automation requires:\n" +
                            "• Bluetooth permissions\n" +
                            "• Location permission\n" +
                            "• Accessibility service enabled\n\n" +
                            "Please grant all permissions and enable the accessibility service."
                        )
                        .setPositiveButton("Check Permissions") { _, _ ->
                            checkAndRequestPermissions()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                    return@setOnCheckedChangeListener
                }
            }
            
            automationSettings = automationSettings.copy(isEnabled = isChecked)
            saveSettings()
            
            // Status display updates removed
            
            // Log the automation state change
            showActivityLog(
                if (isChecked) "Automation enabled" else "Automation disabled",
                "",
                true,
                "User toggled automation ${if (isChecked) "on" else "off"}"
            )
            
            // Update the monitoring service's automation state
            updateServiceAutomationState(isChecked)
            
            // Start or stop monitoring service based on automation state
            if (isChecked && automationSettings.targetBluetoothDevice.isNotEmpty()) {
                startBluetoothMonitoring()
            } else if (!isChecked) {
                stopBluetoothMonitoring()
            }
        }
    }
    
    private fun setupTargetDeviceSpinner() {
        // Initialize spinner adapter
        deviceSpinnerAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            pairedDeviceNames
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        targetDeviceSpinner.adapter = deviceSpinnerAdapter
        
        // Set up spinner selection listener
        targetDeviceSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position > 0 && position < pairedDeviceNames.size) { // Skip "Select device" option
                    val deviceName = pairedDeviceNames[position]
                    automationSettings = automationSettings.copy(targetBluetoothDevice = deviceName)
                    saveSettings()
                    
                    showActivityLog(
                        "Target device selected",
                        deviceName,
                        true,
                        "User selected target device from paired devices"
                    )
                    
                    // Update monitoring service with new target device
                    if (automationSettings.isEnabled && deviceName.isNotEmpty()) {
                        updateBluetoothMonitoringTarget(deviceName)
                    }
                }
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        // Set up refresh button
        refreshDevicesButton.setOnClickListener {
            loadPairedDevices()
            showActivityLog(
                "Device list refreshed",
                "",
                true,
                "Reloaded paired Bluetooth devices"
            )
        }
        
        // Load paired devices initially
        loadPairedDevices()
    }
    
    private fun loadPairedDevices() {
        pairedDeviceNames.clear()
        pairedDeviceNames.add(getString(R.string.select_device))
        
        if (!hasBluetoothPermissions()) {
            pairedDeviceNames.add("Bluetooth permissions required")
            deviceSpinnerAdapter.notifyDataSetChanged()
            
            // Show a more helpful message and offer to request permissions
            showActivityLog(
                "Bluetooth permissions missing",
                "",
                false,
                "Cannot load paired devices without Bluetooth permissions. Please grant permissions."
            )
            return
        }
        
        try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter?.isEnabled == true) {
                val pairedDevices = bluetoothAdapter.bondedDevices
                if (pairedDevices.isNotEmpty()) {
                    for (device in pairedDevices) {
                        device.name?.let { deviceName ->
                            pairedDeviceNames.add(deviceName)
                        }
                    }
                } else {
                    pairedDeviceNames.add(getString(R.string.no_paired_devices))
                }
            } else {
                pairedDeviceNames.add("Bluetooth is disabled")
            }
        } catch (e: SecurityException) {
            Log.e("MainActivity", "Security exception loading paired devices", e)
            pairedDeviceNames.add("Permission denied")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error loading paired devices", e)
            pairedDeviceNames.add("Error loading devices")
        }
        
        deviceSpinnerAdapter.notifyDataSetChanged()
        
        // Try to select the currently configured device
        val currentDevice = automationSettings.targetBluetoothDevice
        if (currentDevice.isNotEmpty()) {
            val index = pairedDeviceNames.indexOf(currentDevice)
            if (index > 0) {
                targetDeviceSpinner.setSelection(index)
            }
        }
    }
    
    private fun setupClearLogButton() {
        clearLogButton.setOnClickListener {
            activityLogAdapter.clearLog()
            updateEmptyLogVisibility()
            showActivityLog("Log cleared", "", true, "User cleared activity log")
        }
    }
    
    private fun setupTestButtons() {
        testEnableButton.setOnClickListener {
            testHotspotEnable()
        }
    }
    
    /**
     * Test hotspot enable functionality
     */
    private fun testHotspotEnable() {
        val testDeviceName = "Test Device"
        
        showActivityLog(
            "Test Enable Hotspot",
            testDeviceName,
            true,
            "Manual test triggered by user"
        )
        
        // Send enable command to accessibility service
        try {
            val prefs = getSharedPreferences("hotspot_commands", android.content.Context.MODE_PRIVATE)
            prefs.edit()
                .putString("pending_command", "ENABLE")
                .putString("device_name", testDeviceName)
                .apply()
            
            showActivityLog(
                "Enable command sent",
                testDeviceName,
                true,
                "Accessibility service will enable hotspot"
            )
        } catch (e: Exception) {
            showActivityLog(
                "Command failed",
                testDeviceName,
                false,
                "Error: ${e.message}"
            )
        }
    }

    
    private fun loadSettings() {
        val isEnabled = sharedPreferences.getBoolean(PREF_AUTOMATION_ENABLED, false)
        val targetDevice = sharedPreferences.getString(PREF_TARGET_DEVICE, "") ?: ""
        val showNotifications = sharedPreferences.getBoolean(PREF_SHOW_NOTIFICATIONS, true)
        
        automationSettings = AutomationSettings(
            isEnabled = isEnabled,
            targetBluetoothDevice = targetDevice,
            showNotifications = showNotifications
        )
    }
    
    private fun saveSettings() {
        sharedPreferences.edit().apply {
            putBoolean(PREF_AUTOMATION_ENABLED, automationSettings.isEnabled)
            putString(PREF_TARGET_DEVICE, automationSettings.targetBluetoothDevice)
            putBoolean(PREF_SHOW_NOTIFICATIONS, automationSettings.showNotifications)
            apply()
        }
    }
    
    private fun updateUI() {
        automationToggle.isChecked = automationSettings.isEnabled
        
        // Select the current device in spinner if it exists
        val currentDevice = automationSettings.targetBluetoothDevice
        if (currentDevice.isNotEmpty()) {
            val index = pairedDeviceNames.indexOf(currentDevice)
            if (index > 0) {
                targetDeviceSpinner.setSelection(index)
            }
        }
        

    }
    

    
    // Status display methods removed - focusing on core automation functionality
    
    /**
     * Update automation status (called from services)
     */
    fun updateAutomationStatus(enabled: Boolean) {
        automationToggle.isChecked = enabled
        automationSettings = automationSettings.copy(isEnabled = enabled)
        saveSettings()
    }
    
    /**
     * Add entry to activity log and update display
     */
    fun showActivityLog(action: String, deviceName: String, success: Boolean, details: String) {
        val entry = ActivityLogEntry(
            timestamp = System.currentTimeMillis(),
            action = action,
            deviceName = deviceName,
            success = success,
            details = details
        )
        
        activityLogAdapter.addLogEntry(entry)
        updateEmptyLogVisibility()
        
        // Scroll to top to show newest entry
        activityLogRecyclerView.scrollToPosition(0)
    }
    
    private fun updateEmptyLogVisibility() {
        if (activityLogAdapter.isEmpty()) {
            emptyLogText.visibility = View.VISIBLE
            activityLogRecyclerView.visibility = View.GONE
        } else {
            emptyLogText.visibility = View.GONE
            activityLogRecyclerView.visibility = View.VISIBLE
        }
    }
    
    /**
     * Get current automation settings
     */
    fun getAutomationSettings(): AutomationSettings {
        return automationSettings
    }
    
    // Permission handling methods
    
    private fun checkAndRequestPermissions() {
        val missingPermissions = requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isNotEmpty()) {
            showPermissionExplanationDialog(missingPermissions)
        } else {
            checkAccessibilityServiceStatus()
        }
    }
    
    private fun showPermissionExplanationDialog(missingPermissions: List<String>) {
        val permissionNames = missingPermissions.map { permission ->
            when (permission) {
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN -> "Bluetooth"
                Manifest.permission.ACCESS_FINE_LOCATION -> "Location"
                Manifest.permission.POST_NOTIFICATIONS -> "Notifications"
                else -> permission
            }
        }.distinct()
        
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage(
                "This app needs the following permissions to work properly:\n\n" +
                "• ${permissionNames.joinToString("\n• ")}\n\n" +
                "Bluetooth and Location permissions are required to detect when your car connects. " +
                "Notification permission allows the app to inform you when hotspot is toggled."
            )
            .setPositiveButton("Grant Permissions") { _, _ ->
                requestPermissions(missingPermissions)
            }
            .setNegativeButton("Cancel") { _, _ ->
                handlePermissionDenial()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun requestPermissions(permissions: List<String>) {
        permissionLauncher.launch(permissions.toTypedArray())
    }
    
    private fun handlePermissionResults(permissions: Map<String, Boolean>) {
        val deniedPermissions = permissions.filter { !it.value }.keys
        
        if (deniedPermissions.isEmpty()) {
            showActivityLog(
                "Permissions granted",
                "",
                true,
                "All required permissions have been granted"
            )
            checkAccessibilityServiceStatus()
        } else {
            val criticalPermissions = deniedPermissions.filter { permission ->
                permission == Manifest.permission.BLUETOOTH ||
                permission == Manifest.permission.BLUETOOTH_ADMIN ||
                permission == Manifest.permission.BLUETOOTH_CONNECT ||
                permission == Manifest.permission.BLUETOOTH_SCAN ||
                permission == Manifest.permission.ACCESS_FINE_LOCATION
            }
            
            if (criticalPermissions.isNotEmpty()) {
                showCriticalPermissionDeniedDialog(criticalPermissions)
            } else {
                showActivityLog(
                    "Some permissions denied",
                    "",
                    false,
                    "Non-critical permissions were denied: ${deniedPermissions.joinToString(", ")}"
                )
                checkAccessibilityServiceStatus()
            }
        }
    }
    
    private fun showCriticalPermissionDeniedDialog(deniedPermissions: List<String>) {
        AlertDialog.Builder(this)
            .setTitle("Critical Permissions Denied")
            .setMessage(
                "The app cannot function without Bluetooth and Location permissions. " +
                "Please grant these permissions in the app settings to use the automation features."
            )
            .setPositiveButton("Open Settings") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("Continue Anyway") { _, _ ->
                handlePermissionDenial()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun handlePermissionDenial() {
        showActivityLog(
            "Permissions denied",
            "",
            false,
            "App functionality will be limited without required permissions"
        )
        
        // Disable automation toggle if critical permissions are missing
        val hasCriticalPermissions = hasBluetoothPermissions() && hasLocationPermission()
        if (!hasCriticalPermissions) {
            automationToggle.isEnabled = false
            automationToggle.isChecked = false
            updateAutomationStatus(false)
        }
    }
    
    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }
    
    private fun checkAccessibilityServiceStatus() {
        if (!isAccessibilityServiceEnabled()) {
            showAccessibilityServiceDialog()
        } else {
            showActivityLog(
                "Accessibility service enabled",
                "",
                true,
                "Hotspot control functionality is available"
            )
            // Update UI since all requirements are now met
            val hasAllRequirements = hasAllRequiredPermissionsAndServices()
            automationToggle.isEnabled = hasAllRequirements
        }
    }
    
    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityEnabled = try {
            Settings.Secure.getInt(
                contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (e: Settings.SettingNotFoundException) {
            0
        }
        
        if (accessibilityEnabled == 1) {
            val services = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            val serviceName = "${packageName}/.service.HotspotAccessibilityService"
            Log.d("MainActivity", "Checking accessibility service - Enabled services: '$services', Looking for: '$serviceName'")
            val isEnabled = services?.contains(serviceName) == true
            Log.d("MainActivity", "Accessibility service enabled: $isEnabled")
            return isEnabled
        }
        
        return false
    }
    
    private fun showAccessibilityServiceDialog() {
        AlertDialog.Builder(this)
            .setTitle("Accessibility Service Required")
            .setMessage(
                "To automatically control your WiFi hotspot, this app needs accessibility service permission. " +
                "This allows the app to interact with the notification panel to toggle the hotspot.\n\n" +
                "Please enable 'Bluetooth Hotspot Automation' in the accessibility settings."
            )
            .setPositiveButton("Open Settings") { _, _ ->
                openAccessibilitySettings()
            }
            .setNegativeButton("Skip") { _, _ ->
                showActivityLog(
                    "Accessibility service not enabled",
                    "",
                    false,
                    "Hotspot control will not work without accessibility service"
                )
            }
            .setCancelable(true)
            .show()
    }
    
    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        accessibilitySettingsLauncher.launch(intent)
    }
    
    private fun hasBluetoothPermissions(): Boolean {
        val bluetoothPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.BLUETOOTH
        ) == PackageManager.PERMISSION_GRANTED
        
        val bluetoothAdminPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.BLUETOOTH_ADMIN
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
                               bluetoothConnectPermission && bluetoothScanPermission
        
        if (!hasAllPermissions) {
            showActivityLog(
                "Bluetooth permissions missing",
                "",
                false,
                "Some Bluetooth permissions missing. App may have limited functionality."
            )
        }
        
        return hasAllPermissions
    }
    
    private fun hasLocationPermission(): Boolean {
        val hasPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        if (!hasPermission) {
            showActivityLog(
                "Location permission missing",
                "",
                false,
                "Location permission required for Bluetooth device detection"
            )
        }
        
        return hasPermission
    }
    
    /**
     * Check if all required permissions and services are available for automation
     */
    private fun hasAllRequiredPermissionsAndServices(): Boolean {
        val hasBluetoothPerms = hasBluetoothPermissions()
        val hasLocationPerm = hasLocationPermission()
        val hasAccessibilityService = isAccessibilityServiceEnabled()
        
        Log.d("MainActivity", "Permission check - Bluetooth: $hasBluetoothPerms, Location: $hasLocationPerm, Accessibility: $hasAccessibilityService")
        
        return hasBluetoothPerms && hasLocationPerm && hasAccessibilityService
    }
    
    override fun onResume() {
        super.onResume()
        
        // Broadcast receiver registration removed
        
        // Comprehensive permission and service validation
        val hasAllRequirements = hasAllRequiredPermissionsAndServices()
        
        // Enable/disable automation toggle based on requirements
        automationToggle.isEnabled = hasAllRequirements
        
        if (!hasAllRequirements) {
            // Force disable automation if requirements not met
            if (automationToggle.isChecked) {
                automationToggle.isChecked = false
                updateAutomationStatus(false)
                showActivityLog(
                    "Automation disabled",
                    "",
                    false,
                    "Missing required permissions or accessibility service"
                )
            }
        } else {
            // Restore automation state if all requirements are met
            if (!automationToggle.isChecked && automationSettings.isEnabled) {
                automationToggle.isChecked = true
            }
        }
        
        // Only start monitoring if all requirements are met
        if (hasAllRequirements && automationSettings.isEnabled && automationSettings.targetBluetoothDevice.isNotEmpty()) {
            startBluetoothMonitoring()
        }
        
        // Check accessibility service status if not enabled
        if (!isAccessibilityServiceEnabled()) {
            checkAccessibilityServiceStatus()
        }
    }
    
    override fun onPause() {
        super.onPause()
        
        // Broadcast receiver unregistration removed
    }
    
    /**
     * Get the notification manager instance
     */
    fun getNotificationManager(): com.bluetoothhotspot.automation.manager.AppNotificationManager {
        return com.bluetoothhotspot.automation.util.NotificationHelper.getInstance()
    }
    
    /**
     * Start Bluetooth monitoring service
     */
    private fun startBluetoothMonitoring() {
        try {
            val intent = Intent(this, BluetoothMonitorService::class.java).apply {
                action = BluetoothMonitorService.ACTION_START_MONITORING
                putExtra(BluetoothMonitorService.EXTRA_TARGET_DEVICE, automationSettings.targetBluetoothDevice)
            }
            startService(intent)
            
            showActivityLog(
                "Monitoring started",
                automationSettings.targetBluetoothDevice,
                true,
                "Bluetooth monitoring service started"
            )
        } catch (e: Exception) {
            showActivityLog(
                "Failed to start monitoring",
                automationSettings.targetBluetoothDevice,
                false,
                "Error: ${e.message}"
            )
        }
    }
    
    /**
     * Stop Bluetooth monitoring service
     */
    private fun stopBluetoothMonitoring() {
        try {
            val intent = Intent(this, BluetoothMonitorService::class.java).apply {
                action = BluetoothMonitorService.ACTION_STOP_MONITORING
            }
            startService(intent)
            
            showActivityLog(
                "Monitoring stopped",
                "",
                true,
                "Bluetooth monitoring service stopped"
            )
        } catch (e: Exception) {
            showActivityLog(
                "Failed to stop monitoring",
                "",
                false,
                "Error: ${e.message}"
            )
        }
    }
    
    /**
     * Update automation state in the monitoring service
     */
    private fun updateServiceAutomationState(enabled: Boolean) {
        try {
            val intent = Intent(this, BluetoothMonitorService::class.java).apply {
                action = BluetoothMonitorService.ACTION_UPDATE_AUTOMATION_STATE
                putExtra(BluetoothMonitorService.EXTRA_AUTOMATION_ENABLED, enabled)
            }
            startService(intent)
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to update service automation state", e)
        }
    }
    

    
    // Broadcast receivers removed - status displays removed
    
    /**
     * Update target device for monitoring service
     */
    private fun updateBluetoothMonitoringTarget(deviceName: String) {
        val intent = Intent(this, BluetoothMonitorService::class.java).apply {
            action = BluetoothMonitorService.ACTION_UPDATE_TARGET_DEVICE
            putExtra(BluetoothMonitorService.EXTRA_TARGET_DEVICE, deviceName)
        }
        startService(intent)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Broadcast receivers removed
    }
}