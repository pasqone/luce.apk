# Project Specification: Luce – Minimalist Display & LED Torch App (Android)

## 1. App Identity & Overview
- **App Name:** Luce
- **Package Name / Application ID:** `com.luce.torch`
- **Goal:** Build an ultra-minimalist, ad-free, single-screen Android flashlight and table lamp app using **Native Android (Kotlin + Jetpack Compose)**. 
- **Core Concept:** The app serves primarily as a fullscreen color display light ("Display Torch" / "Table Lamp") with swipe-based hardware brightness control, a full UI lock mechanism, a clean 2-slider color picker with 4 persistent preset slots, and a double-tap toggle to switch to the rear camera hardware LED ("Hardware Torch") while blacking out the display.

## 2. Tech Stack & Architecture Constraints
- **Language:** Kotlin
- **UI Framework:** Jetpack Compose (Material 3)
- **Min SDK:** 26 (Android 8.0) | **Target/Compile SDK:** 34
- **Zero External Third-Party Libraries:** Use ONLY standard AndroidX libraries, Jetpack Compose (`androidx.compose.material:material-icons-extended` or custom Canvas/Vector drawing), and native Android system APIs (`CameraManager`, `WindowManager`, `SharedPreferences`).
- **Compact Single-File Architecture:** Implement the entire application logic and UI cleanly inside `MainActivity.kt` (plus standard Gradle/Manifest/Resource files) to avoid unnecessary boilerplate.
- **No Runtime Permissions Needed:** Calling `CameraManager.setTorchMode(cameraId, enabled)` does NOT require the `CAMERA` runtime permission on Android 6.0+. Do not add runtime permission prompts; only declare `<uses-feature android:name="android.hardware.camera.flash" android:required="false" />` in `AndroidManifest.xml`.

---

## 3. Core System Behavior
1. **Immersive Fullscreen:**
   - Hide both the status bar and navigation bar completely using `WindowInsetsControllerCompat` (`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`).
   - Draw edge-to-edge (`WindowCompat.setDecorFitsSystemWindows(window, false)`).
2. **Keep Screen Awake:**
   - Set `WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON` on the window so the display never dims or locks while **Luce** is open.
3. **Hardware Brightness Control:**
   - Control the physical screen brightness directly via `window.attributes.screenBrightness` (float range `0.01f` to `1.0f`).
   - **Lifecycle Restoration:** When the app goes to background (`onPause` / `onStop`), reset `screenBrightness` to `WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE` (`-1f`) and turn off the hardware camera LED if it is currently active. Restore the user's saved app brightness in `onResume`.

---

## 4. Modes & User Interactions

### Mode A: Display Torch Mode (Default)
- **Background:** Fullscreen solid color calculated from HSV (`Color.hsv(hue, saturation, 1.0f)`). Note: Value (`V`) is fixed at `1.0f` because brightness is controlled exclusively via the hardware backlight swipe gesture.
- **Vertical Swipe Gesture (Brightness):**
  - Swiping up/down anywhere on the screen dynamically adjusts `screenBrightness` between `0.01f` (1%) and `1.0f` (100%).
  - Map the vertical drag delta smoothly (e.g., dragging 70% of the screen height covers the full `0.01` to `1.0` range).
  - Show a subtle, minimalist percentage text (e.g., `75%`) briefly in the center while dragging, fading out automatically 800ms after release.
  - **Strictly disabled when Locked.**
- **Overlay Icons (Minimalist & Semi-Transparent):**
  - Position both icons neatly at the bottom corners (or top-right for Lock, bottom-center for Color) with subtle semi-transparent contrast backing so they remain legible on both pure white and dark/saturated backgrounds.
  - **Lock Icon:** Small icon button (`Icons.Default.Lock` / `Icons.Default.LockOpen`).
    - **Unlocked:** All gestures and icons are active.
    - **Locked:** Tapping the lock locks the entire interface. In locked state:
      1. Vertical swipe for brightness is **blocked**.
      2. Double-tap to switch to Camera LED is **blocked**.
      3. The Color Picker icon is **hidden** (and any open color dialog is closed).
      4. Only tapping the Lock Icon again unlocks the app.
  - **Color Picker Icon:** Small palette/tune icon button. Only visible when **Unlocked**. Tapping toggles the Color Picker Overlay.
- **Double-Tap Gesture:**
  - Double-tapping anywhere on the fullscreen area (when **Unlocked** and Color Picker is closed) switches to **Mode B (Hardware LED Mode)**.

### Mode B: Hardware LED Mode (Camera Torch)
- **Trigger:** Entered via Double-Tap from Mode A (when unlocked).
- **Hardware Action:** Turns ON the back camera flashlight via `CameraManager.setTorchMode(cameraId, true)`. (Gracefully handle devices without flash via try/catch).
- **Screen State:**
  - Fullscreen background turns pure OLED Black (`#000000`).
  - Set `window.attributes.screenBrightness = 0.01f` while in this mode to prevent display glow in dark environments.
  - **Hide all UI controls:** Both the Lock Icon and the Color Picker Icon are completely hidden.
- **Center Graphic:**
  - Display a custom Vector/Canvas graphic in the exact center of the screen rendered in **30% Grey (`Color(0xFF4D4D4D)`)**.
  - **Graphic Design:** A clean, minimalist flashlight icon oriented **vertically pointing upwards**, with stylized light rays/beam shining upward from the top of the flashlight head (symbolizing the rear LED shining forward). Draw this cleanly using Jetpack Compose `Canvas` or a custom `ImageVector` path.
- **Exit Mode B:**
  - Double-tapping anywhere on the black screen turns OFF the hardware LED (`setTorchMode(cameraId, false)`), restores the previous `screenBrightness`, and returns immediately to **Mode A (Display Torch Mode)**.

---

## 5. Color Picker UI & Presets
When the Color Picker Icon is tapped in Mode A (Unlocked), show a clean, compact semi-transparent dark overlay card at the bottom of the screen (dismissible by tapping outside on the main screen or tapping the icon again):

1. **Slider 1: Hue (Farbspektrum `0f..360f`)**
   - Horizontal slider with a custom rainbow spectrum gradient bar (`0°` Red through Yellow, Green, Cyan, Blue, Magenta to `360°` Red).
2. **Slider 2: Saturation (Sättigung `0f..1f`)**
   - Horizontal slider with a custom gradient bar from pure White (`0f`) on the left to the currently selected pure Hue (`1f`) on the right.
   - *(Do NOT include a 3rd brightness/value slider! Brightness is strictly controlled via vertical screen swipe).*
3. **4 Preset Color Slots:**
   - Below the two sliders, display an evenly spaced horizontal row of **4 circular or rounded-square color swatches** with a subtle border.
   - **Single Tap on a slot:** Immediately loads that slot's saved `(hue, saturation)` as the active screen color.
   - **Long Press on a slot:** Saves the currently active `(hue, saturation)` into that slot (trigger `HapticFeedbackType.LongPress` and a brief visual confirmation).
   - **Default Preset Values (on first install):**
     - Slot 1: Pure White (`Hue = 0f, Sat = 0.0f`)
     - Slot 2: Warm White / Candle (`Hue = 35f, Sat = 0.45f`)
     - Slot 3: Deep Amber / Night Light (`Hue = 22f, Sat = 0.85f`)
     - Slot 4: Night-Vision Red (`Hue = 0f, Sat = 1.0f`)

---

## 6. Persistence (`SharedPreferences`)
Persist the following states immediately whenever they change, and load them automatically on app startup:
- `current_hue` (Float, default `35f`)
- `current_saturation` (Float, default `0.0f`)
- `current_brightness` (Float, default `0.8f`)
- `is_locked` (Boolean, default `false`)
- `preset_0_hue`, `preset_0_sat` ... through `preset_3_hue`, `preset_3_sat` (Floats)
*(Note: Always start the app in Mode A / Display Torch Mode, even if it was closed while Mode B was active).*

---

## 7. Environment Check, App Icon & Build Deliverables
1. **Step 1 – Host Environment & SDK Verification (Linux / Fedora):**
   - Check if a compatible JDK (Java 17 or 21) is installed (`java -version`).
   - Check if the Android SDK / `sdkmanager` is already installed on the system (inspect `$ANDROID_HOME`, `$ANDROID_SDK_ROOT`, `~/Android/Sdk`, `/opt/android-sdk`).
   - If the Android SDK or required build-tools/platform (`platforms;android-34`, `build-tools;34.0.0`) are missing, automatically download/configure the official Android Command-Line Tools locally, accept licenses (`yes | sdkmanager --licenses`), and generate a valid `local.properties` pointing `sdk.dir` to the SDK path.
   - Check if `gradle` is installed or generate/bootstrap the Gradle Wrapper (`gradlew`, `gradle-wrapper.jar`, `gradle-wrapper.properties`) so the project builds standalone.
2. **Step 2 – Custom Minimalist App Icon:**
   - Configure an adaptive launcher icon (`ic_launcher`) with a pure dark/black background (`#121212`) and a minimalist warm-glowing vector light/beam symbol in the foreground.
3. **Step 3 – Project Generation & APK Build:**
   - Generate the complete Gradle project structure (`settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`, `gradle.properties`, `AndroidManifest.xml`, vector icon resources, and `MainActivity.kt`).
   - Run `./gradlew assembleDebug`, fix any compiler or dependency issues autonomously, and output the exact path to the finished `app-debug.apk` (and copy it to `./Luce.apk` in the project root for easy access).