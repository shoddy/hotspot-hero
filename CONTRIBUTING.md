# Contributing to HotSpot Hero

Thanks for your interest in contributing! This guide will help you get started.

## ⚠️ Project Status & Maintenance

**HotSpot Hero is a "proof of concept" project** that was built to solve a specific personal automation need. While it works great for its intended purpose, please understand:

### 🔧 Maintenance Level
- **Not actively maintained**: This is a completed project, not an ongoing development effort
- **Best effort support**: I'll try to help with issues when I can, but responses may be slow
- **Community-driven**: The project benefits most from community contributions and forks
- **Working solution**: The current version is stable and functional for its target use case

### 🤝 How You Can Help
- **Fork and improve**: Feel free to create your own version with enhancements
- **Share solutions**: If you fix something, please share it with others via issues or PRs
- **Help others**: Answer questions from other users when you can
- **Document discoveries**: Share device compatibility findings

### 💡 Realistic Expectations
- **Simple fixes**: I'm more likely to merge simple bug fixes or compatibility improvements
- **Major features**: Large new features are better suited for community forks
- **Device support**: Adding support for new devices is welcomed but may require community testing
- **Response time**: Please be patient - this is a side project

**Bottom line**: HotSpot Hero works as intended, but it's more of a "finished experiment" than an actively developed app. The community is encouraged to build upon it! 🚀

## 🎯 Ways to Contribute

### 🐛 Bug Reports
- Use the issue tracker to report bugs
- Include device model, Android version, and steps to reproduce
- Screenshots or screen recordings are very helpful

### 💡 Feature Requests
- Describe the problem you're trying to solve
- Explain how the feature would work
- Consider if it fits the app's simple, focused scope

### 🔧 Code Contributions
- Fork the repository
- Create a feature branch
- Make your changes
- Test thoroughly on your device
- Submit a pull request

## 🏗️ Development Setup

### Prerequisites
- Android Studio
- Android SDK (API 26+)
- ADB for testing
- Physical Android device (emulator has limitations with accessibility services)

### Building
```bash
git clone https://github.com/your-username/hotspot-hero.git
cd hotspot-hero
./gradlew assembleDebug
```

### Testing
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 📱 Device Compatibility

### Adding Support for New Devices
The main compatibility issue is **tap coordinates** for the hotspot tile. Different devices have different screen layouts.

**Key file:** `app/src/main/java/com/bluetoothhotspot/automation/service/HotspotAccessibilityService.kt`

**Look for:**
```kotlin
val tileY = if (wasScreenWoken) {
    690f // Wake screen position
} else {
    200f // Normal position
}
```

### Testing New Coordinates
1. Use the "Test Enable" button in the app
2. Watch where it taps on your device
3. Adjust coordinates as needed
4. Test both locked and unlocked scenarios

### Screen Density Considerations
Consider using density-independent calculations:
```kotlin
val density = resources.displayMetrics.density
val tileY = (200 * density).toFloat() // Convert dp to pixels
```

---

**Thanks for helping make HotSpot Hero better for everyone! 🦸‍♂️**