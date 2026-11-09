# Luce

A minimalist, distraction-free, privacy-first ambient display light and LED torch for Android. Built natively with Kotlin and Jetpack Compose.

![Android](https://img.shields.io/badge/Android-API%2026%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0.0-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![No Tracking](https://img.shields.io/badge/Privacy-100%25%20Offline-success)

---

## Features

### 💡 Mode A: Ambient Display Lamp
- **Fullscreen Color Glow:** Pure edge-to-edge illumination without bars or navigation clutter.
- **Vertical Swipe Brightness:** Swipe up or down anywhere on the screen to smoothly adjust hardware screen brightness (0.01 to 1.0) with an unobtrusive percentage readout.
- **Color & Brightness Controls:**
  - Smooth **Hue** slider ($0^\circ$–$360^\circ$).
  - **Saturation** slider ($0\%$–$100\%$) for everything from crisp white to deep vivid colors.
  - Linked **Brightness** slider synchronized with the swipe gesture.
- **4 Customizable Presets:** Quickly switch between favorite colors and light levels. Tap to load color & brightness; long-press with haptic vibration to save current color and brightness.
- **UI Lock:** Dedicated lock button (bottom-right) prevents accidental gestures while holding the phone or using it as a nightlight.
- **Info & Instructions:** Quick guide button (bottom-left) explains all gestures, author information (pasq.org), and license.

### 🔦 Mode B: Hardware LED Torch
- **Instant Double-Tap:** Double-tap anywhere on the display to toggle the rear camera LED flashlight—even when the screen is locked.
- **OLED Power Saving:** Screen drops to true OLED black (`#000000`) and minimal brightness, displaying an elegant flashlight graphic.

### 🆘 Emergency Morse SOS
- **Synchronized Beacon:** Dedicated SOS button (top-left) triggers the international Morse code pattern for SOS (`... --- ...`).
- **Dual Flash:** Flashes both the rear camera LED and the screen in high-intensity safety blue simultaneously.
- **Instant Cancel:** Tap the SOS button or double-tap the screen to instantly stop and restore previous settings.

---

## Gestures & Controls

| Action | Function |
|---|---|
| **Vertical Drag** | Adjust display brightness smoothly |
| **Double Tap** | Toggle between Display Lamp (Mode A) and LED Torch (Mode B) / Stop SOS |
| **Tap Screen** | Dismiss color palette or info overlay |
| **Top-Left Button (`SOS`)** | Toggle Morse code emergency beacon |
| **Bottom-Left Button (`Info`)** | Open quick guide & about dialog (gestures, pasq.org, GPL-3.0) |
| **Bottom-Center Button (`Palette`)** | Open / close Hue, Saturation & Brightness picker |
| **Bottom-Right Button (`Lock`)** | Lock / unlock screen gestures |
| **Preset Circle (Tap)** | Apply saved color & brightness preset |
| **Preset Circle (Long Press)** | Save current color & brightness into slot (with haptic feedback) |

---

## Privacy & Permissions

Luce is designed with uncompromising privacy in mind:

- **No Internet Access:** The `android.permission.INTERNET` permission is **not** included. The app cannot transmit or receive any network data.
- **Zero Trackers & Ads:** No third-party SDKs, no analytics, no telemetry.
- **Minimal Permissions:** Only requests camera flash hardware feature (`android.hardware.camera.flash`) to drive the LED light.

---

## Building from Source

### Prerequisites
- JDK 17 or JDK 21
- Android SDK Platform 34 & Build-Tools 34.0.0
- Linux / macOS / Windows

### Build Instructions
Clone the repository and build using the Gradle wrapper:

```bash
git clone git@github-luce:pasqone/luce.apk.git
cd luce.apk

# Build debug APK
./gradlew assembleDebug
```

The compiled APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## Architecture

- **Language:** 100% Kotlin
- **UI Framework:** Jetpack Compose (Material 3)
- **Minimum SDK:** Android 8.0 (API level 26)
- **Target / Compile SDK:** Android 14 (API level 34)
- **State Persistence:** Android `SharedPreferences` for brightness, hue, saturation, presets, and lock state.

---

## License

This project is licensed under the **GNU General Public License v3.0** (GPL-3.0).  
See the [LICENSE](LICENSE) file for the full license text.
