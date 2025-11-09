# HotSpot Hero Releases 🦸‍♂️

## Current Release

### Latest - Current Working Version
**File:** `hotspot-hero-latest.apk`  
**Size:** ~13 MB  
**Date:** November 2025

**New in this version:**
- ✅ Fixed accessibility service detection (automation toggle now works correctly)
- ✅ Activity log now updates in real-time with automation events
- ✅ Smart hotspot detection - skips automation if hotspot already enabled
- ✅ Better error handling and user feedback
- ✅ Improved notification messages
- ✅ Background Wi-Fi hotspot state tracking

**Tested On:**
- Google Pixel 6a (Android 13+)
- Successfully handles rapid Bluetooth reconnections (e.g., fuel stops)

**Installation:**
```bash
adb install hotspot-hero-latest.apk
```

**SHA256 Checksum:**
```
906a39512b14283837ad52cf809b0ac066a4d2b2c7759db4d977e4278a33dcee  hotspot-hero-latest.apk
```

### v1.0.0 - Initial Release
**File:** `hotspot-hero-v1.0.0.apk`  
**Size:** ~10.7 MB  
**Date:** October 2024

*Note: This is the original release. Use `hotspot-hero-latest.apk` for the most recent version with bug fixes.*

**Features:**
- ✅ Automatic hotspot activation when car Bluetooth connects
- ✅ Screen wake functionality for locked devices
- ✅ Battery optimized background service
- ✅ Clean, hero-themed UI
- ✅ Activity logging and monitoring
- ✅ Adaptive tile positioning (Y=690 for wake, Y=200 for unlocked)

**Tested On:**
- Google Pixel 6a (Android 13+)
- May work on similar Android devices

**Installation:**
```bash
adb install hotspot-hero-v1.0.0.apk
```

## Version History

### Latest (November 2025)
- Fixed automation toggle not staying enabled
- Restored real-time activity log updates
- Added duplicate automation prevention when hotspot already enabled
- Improved error handling and notifications
- Background hotspot state tracking via system broadcasts

### v1.0.0 (October 2024)
- Initial public release
- Basic car automation functionality
- Screen wake support
- Battery optimizations
- Hero-themed branding

## Installation Requirements

- **ADB (Android Debug Bridge)** installed on computer
- **Developer Options** enabled on phone
- **USB Debugging** enabled
- **Android 8.0+** (API level 26+)

## Security Note

This APK is **unsigned** (development build). Your device may show security warnings during installation. This is normal for sideloaded apps.

**SHA256 Checksum:**
```
3050070701ab215cf93d304da14d5650f5eef1a197a6c48bc37a77c0757fed87  hotspot-hero-v1.0.0.apk
```

---

**Need help?** Check the main [README](../README.md) for detailed installation and usage instructions.