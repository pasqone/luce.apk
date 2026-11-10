# Feature Update Specification: Luce – Text Modes (LED Banner & Word Slideshow)

## 1. Overview & Integration
Extend the existing **Luce** (`com.luce.torch`) app with two fullscreen text display modes:
1. **Banner Mode (LED Matrix Ticker):** Continuous horizontal scrolling marquee with an authentic glowing LED dot-matrix look on an OLED black background.
2. **Slideshow Mode (Word-by-Word Flasher):** Displays text **one word per screen** sequentially with an adjustable delay, rendered in a clean, bold, modern Sans-Serif typeface with a toggleable **Invert** option (Colored Text on Black vs. High-Contrast Text on Colored Background).

Keep all existing features (Hardware Brightness vertical swipe, Color Picker with 2 sliders + 4 presets, Lock mechanism, and Double-Tap for Hardware Camera LED) intact and integrated.

---

## 2. Screen Orientation Rules
- **Display Torch Mode (Default) & Camera LED Mode:** Portrait (or standard unspecified orientation).
- **Banner Mode & Slideshow Mode (Active):** Force landscape orientation using `ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE` so the display is always horizontal, but automatically flips 180° depending on which way the user holds the phone horizontally.
- When exiting Text Modes back to the pure Display Torch Mode, restore normal orientation (`SCREEN_ORIENTATION_UNSPECIFIED`).

---

## 3. UI Entry Point & Text Configuration Overlay
- **Text Mode Icon:** Add a minimalist **Text Icon (`T` / `Icons.Default.TextFields`)** next to the Color Picker Icon on the main screen.
  - Like the Color Picker Icon, it is **only visible when Unlocked** (`is_locked == false`) and hidden in Hardware Camera LED Mode.
- **Text Settings Overlay Card:** Tapping the Text Icon opens a semi-transparent dark configuration sheet/card (similar in style to the Color Picker overlay) containing:
  1. **Mode Selector (Segmented Button / Tabs):**
     - `Off` (Pure Display Lamp)
     - `Banner` (LED Matrix Scrolling)
     - `Slideshow` (1 Word per Screen)
  2. **Text Input Row (shown when `Banner` or `Slideshow` is selected):**
     - Single-line/Multi-line `OutlinedTextField` showing the active text for the currently selected mode.
     - **`Clear` Button:** A distinct clear icon/button (`X` / `Clear`) right next to or inside the text field that empties the current mode's text with a single tap.
  3. **Mode-Specific Controls:**
     - **If `Banner` is active:**
       - **Speed Slider:** Horizontal slider controlling scroll speed (from slow/readable to fast ticker).
     - **If `Slideshow` is active:**
       - **Word Delay Slider:** Horizontal slider controlling how long each word stays on screen (`0.2s` to `5.0s`, displaying the current value like `1.2s`).
       - **Invert Toggle (Switch / Chip):** Toggles between normal (Colored Text on OLED Black) and inverted (Black/White Contrast Text on Colored Background).

---

## 4. Visual & Functional Specifications per Mode

### Mode 1: Banner Mode (LED Matrix Marquee)
- **Background:** Always pure OLED Black (`#000000`).
- **Text Color:** Uses the currently active user color from the Color Picker (`Color.hsv(hue, saturation, 1.0f)`).
- **LED Matrix & Glow Effect:**
  - Render the scrolling text very large (filling ~70–85% of the landscape screen height).
  - **Soft LED Glow:** Draw a blurred/softened glow pass behind the text in the active color (using Android `Paint` with `BlurMaskFilter` via `drawIntoCanvas` / `nativeCanvas`) so the letters emit a realistic neon/LED bloom.
  - **Subtle Dot-Matrix Grid:** Overlay a fine dot-matrix pattern across the screen:
    - On the black background, draw a barely visible dark grey dot grid (`#0D0D0D` or `alpha = 0.05f`) so the matrix structure is subtly perceptible.
    - Over the glowing text area, overlay a perforated grid mask (thin black grid lines or circular dot cutouts) so the letters and their surrounding glow look like genuine individual LED diodes.
- **Scrolling Animation:** Smooth, continuous 60fps+ right-to-left loop.

### Mode 2: Slideshow Mode (1 Word per Screen)
- **Word Splitting Logic:**
  - Split the input text by whitespace (`text.trim().split(Regex("\\s+"))`).
  - Cycle through the resulting words sequentially (`1 word per screen`) in an infinite loop at the interval set by the **Word Delay Slider**.
- **Typography:**
  - Do **NOT** use a pixel/matrix font here.
  - Use a clean, geometric, modern **Sans-Serif** font (`FontFamily.SansSerif`, `FontWeight.Bold` or `FontWeight.ExtraBold`) with tight letter spacing so it is maximally legible from a distance.
  - **Auto-Sizing:** Dynamically scale the font size so each individual word fills the landscape screen width/height as large as possible without clipping or wrapping into multiple lines.
- **Color & Invert Logic:**
  - **Standard (`isInverted = false`):**
    - Background: Pure OLED Black (`#000000`).
    - Word Color: Active Color Picker color (`Color.hsv(hue, saturation, 1.0f)`).
  - **Inverted (`isInverted = true`):**
    - Background: Active Color Picker color (`Color.hsv(hue, saturation, 1.0f)`).
    - Word Color: High-contrast solid color (Pure Black `#000000` for bright/desaturated backgrounds, or Pure White `#FFFFFF` for deep/dark saturated colors like pure red/blue—calculate automatically via luminance so contrast is always optimal).

---

## 5. Interaction with Existing Controls
- **Color Picker:** Can be opened at any time while Banner or Slideshow is running to live-adjust the text/background color or pick one of the 4 color presets.
- **Vertical Swipe (Hardware Brightness):** Works identically in Banner and Slideshow modes (adjusting `window.attributes.screenBrightness` from `1%` to `100%`) when unlocked.
- **Lock Icon:** Locking (`is_locked = true`) hides both the Color Picker Icon and the Text Icon, closes any open overlay sheets, and blocks brightness swipes and double-taps while the Banner or Slideshow continues running undisturbed.
- **Double-Tap (Camera LED Mode):** Double-tapping the screen (when unlocked and overlays are closed) still switches to the blacked-out Hardware Camera LED Mode; double-tapping again returns to the active Banner/Slideshow mode.

---

## 6. Persistence (`SharedPreferences`)
Extend `SharedPreferences` to save and restore the following additional values immediately on change:
- `text_mode` (Int/String: `OFF`, `BANNER`, `SLIDESHOW`, default `OFF`)
- `banner_text` (String, default `"LUCE"`)
- `banner_speed` (Float, default `0.5f`)
- `slideshow_text` (String, default `"HALLO WELT"`)
- `slideshow_delay_ms` (Long/Float, default `1000` ms / `1.0s`)
- `slideshow_inverted` (Boolean, default `false`)