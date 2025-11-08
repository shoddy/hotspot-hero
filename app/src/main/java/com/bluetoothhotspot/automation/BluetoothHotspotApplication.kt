package com.bluetoothhotspot.automation

import android.app.Application
import com.bluetoothhotspot.automation.util.NotificationHelper

/**
 * Application class for Bluetooth Hotspot Automation
 * Handles app-wide initialization
 */
class BluetoothHotspotApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize notification system
        NotificationHelper.initialize(this)
    }
}