# Screenshots and Images

This directory contains screenshots and images for the README.

## Files needed:
- `hero-icon.png` - App icon for README header
- `main-interface.png` - Screenshot of main app interface
- `automation-demo.png` - Screenshot showing activity log with automation events

## Screenshot Guidelines:
- Remove or blur any personal information (device names, etc.)
- Use clean, representative examples
- Show the app in action when possible
- Keep images reasonably sized (max 800px wide)

## Taking Screenshots:
```bash
# Take screenshot via ADB
adb shell screencap -p /sdcard/screenshot.png
adb pull /sdcard/screenshot.png .
```