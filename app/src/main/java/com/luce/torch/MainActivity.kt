package com.luce.torch

import android.content.Context
import android.content.SharedPreferences
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.res.ResourcesCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

data class ColorPreset(val hue: Float, val saturation: Float, val brightness: Float)

enum class TextMode {
    OFF,
    BANNER,
    SLIDESHOW
}

class MainActivity : ComponentActivity() {

    private lateinit var prefs: SharedPreferences
    private var isTorchActive = false
    private var lastDisplayBrightness = 0.8f
    private var isModeBState = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Immersive fullscreen edge-to-edge into display cutout / notch
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        hideSystemBars()

        // Keep screen awake
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        prefs = getSharedPreferences("luce_prefs", Context.MODE_PRIVATE)
        lastDisplayBrightness = prefs.getFloat("current_brightness", 0.8f).coerceIn(0.01f, 1.0f)
        applyScreenBrightness(lastDisplayBrightness)

        setContent {
            LuceApp(
                prefs = prefs,
                onBrightnessChange = { b ->
                    lastDisplayBrightness = b
                    if (!isModeBState) {
                        applyScreenBrightness(b)
                    }
                },
                onModeBChange = { inModeB ->
                    isModeBState = inModeB
                    if (inModeB) {
                        applyScreenBrightness(0.01f)
                        setTorch(true)
                    } else {
                        setTorch(false)
                        applyScreenBrightness(lastDisplayBrightness)
                    }
                },
                onSetTorch = { enabled ->
                    setTorch(enabled)
                },
                onApplyBrightness = { b ->
                    applyScreenBrightness(b)
                }
            )
        }
    }

    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun applyScreenBrightness(brightness: Float) {
        val lp = window.attributes
        lp.screenBrightness = brightness.coerceIn(0.001f, 1.0f)
        window.attributes = lp
    }

    private fun resetScreenBrightnessToDefault() {
        val lp = window.attributes
        lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = lp
    }

    private fun setTorch(enabled: Boolean) {
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
        try {
            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraManager.setTorchMode(id, enabled)
                    isTorchActive = enabled
                    return
                }
            }
            // Fallback: any camera with flash
            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                if (characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true) {
                    cameraManager.setTorchMode(id, enabled)
                    isTorchActive = enabled
                    return
                }
            }
        } catch (_: Exception) {
            isTorchActive = false
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        if (isModeBState) {
            applyScreenBrightness(0.01f)
            setTorch(true)
        } else {
            applyScreenBrightness(lastDisplayBrightness)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    override fun onPause() {
        super.onPause()
        if (isTorchActive) {
            setTorch(false)
        }
        resetScreenBrightnessToDefault()
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LuceApp(
    prefs: SharedPreferences,
    onBrightnessChange: (Float) -> Unit,
    onModeBChange: (Boolean) -> Unit,
    onSetTorch: (Boolean) -> Unit,
    onApplyBrightness: (Float) -> Unit
) {
    // Mode A vs Mode B (always starts in Mode A per SPEC)
    var isModeB by remember { mutableStateOf(false) }

    // Persistent state: Color & Brightness
    var hue by remember { mutableFloatStateOf(prefs.getFloat("current_hue", 35f)) }
    var saturation by remember { mutableFloatStateOf(prefs.getFloat("current_saturation", 0.0f)) }
    var brightness by remember { mutableFloatStateOf(prefs.getFloat("current_brightness", 0.8f).coerceIn(0.01f, 1.0f)) }
    var isLocked by remember { mutableStateOf(prefs.getBoolean("is_locked", false)) }

    // Persistent state: Text Modes
    val savedTextModeName = prefs.getString("text_mode", TextMode.OFF.name) ?: TextMode.OFF.name
    var textMode by remember {
        mutableStateOf(
            try {
                TextMode.valueOf(savedTextModeName)
            } catch (_: Exception) {
                TextMode.OFF
            }
        )
    }
    var bannerText by remember { mutableStateOf(prefs.getString("banner_text", "LUCE") ?: "LUCE") }
    var bannerSpeed by remember { mutableFloatStateOf(prefs.getFloat("banner_speed", 0.5f)) }
    var slideshowText by remember { mutableStateOf(prefs.getString("slideshow_text", "HALLO WELT") ?: "HALLO WELT") }
    var slideshowDelayMs by remember { mutableLongStateOf(prefs.getLong("slideshow_delay_ms", 1000L)) }
    var slideshowInverted by remember { mutableStateOf(prefs.getBoolean("slideshow_inverted", false)) }
    var textFlipped by remember { mutableStateOf(prefs.getBoolean("text_flipped", false)) }

    // SOS State
    var isSosActive by remember { mutableStateOf(false) }
    var isSosFlashOn by remember { mutableStateOf(false) }

    // Overlay visibilities
    var showColorPicker by remember { mutableStateOf(false) }
    var showTextSettings by remember { mutableStateOf(false) }
    var showInfoMenu by remember { mutableStateOf(false) }

    // Drag brightness indicator
    var isDraggingBrightness by remember { mutableStateOf(false) }
    var hideBrightnessJob by remember { mutableStateOf<Job?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // 4 Presets
    val presets = remember {
        mutableStateListOf(
            ColorPreset(
                prefs.getFloat("preset_0_hue", 0f),
                prefs.getFloat("preset_0_sat", 0.0f),
                prefs.getFloat("preset_0_bri", 0.8f)
            ),
            ColorPreset(
                prefs.getFloat("preset_1_hue", 35f),
                prefs.getFloat("preset_1_sat", 0.45f),
                prefs.getFloat("preset_1_bri", 0.8f)
            ),
            ColorPreset(
                prefs.getFloat("preset_2_hue", 22f),
                prefs.getFloat("preset_2_sat", 0.85f),
                prefs.getFloat("preset_2_bri", 0.8f)
            ),
            ColorPreset(
                prefs.getFloat("preset_3_hue", 0f),
                prefs.getFloat("preset_3_sat", 1.0f),
                prefs.getFloat("preset_3_bri", 0.8f)
            )
        )
    }

    // Brief visual confirmation for preset save (stores preset index or -1)
    var savedPresetIndex by remember { mutableIntStateOf(-1) }
    val haptic = LocalHapticFeedback.current

    val screenHeightPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenHeightDp.dp.toPx()
    }

    // Standard Morse SOS sequence (... --- ...)
    val sosSequence = remember {
        listOf(
            // S: ...
            true to 200L, false to 200L,
            true to 200L, false to 200L,
            true to 200L, false to 600L,
            // O: ---
            true to 600L, false to 200L,
            true to 600L, false to 200L,
            true to 600L, false to 600L,
            // S: ...
            true to 200L, false to 200L,
            true to 200L, false to 200L,
            true to 200L, false to 1400L
        )
    }

    LaunchedEffect(isSosActive) {
        if (!isSosActive) {
            isSosFlashOn = false
            onSetTorch(isModeB)
            onApplyBrightness(if (isModeB) 0.01f else brightness)
            return@LaunchedEffect
        }
        onApplyBrightness(1.0f)
        try {
            while (isActive) {
                for ((flashOn, duration) in sosSequence) {
                    isSosFlashOn = flashOn
                    onSetTorch(flashOn)
                    delay(duration)
                }
            }
        } finally {
            isSosFlashOn = false
            onSetTorch(isModeB)
            onApplyBrightness(if (isModeB) 0.01f else brightness)
        }
    }

    fun savePreset(index: Int) {
        presets[index] = ColorPreset(hue, saturation, brightness)
        prefs.edit()
            .putFloat("preset_${index}_hue", hue)
            .putFloat("preset_${index}_sat", saturation)
            .putFloat("preset_${index}_bri", brightness)
            .apply()
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        savedPresetIndex = index
        coroutineScope.launch {
            delay(900)
            if (savedPresetIndex == index) {
                savedPresetIndex = -1
            }
        }
    }

    fun applyPreset(index: Int) {
        val p = presets[index]
        hue = p.hue
        saturation = p.saturation
        brightness = p.brightness
        onBrightnessChange(p.brightness)
        prefs.edit()
            .putFloat("current_hue", hue)
            .putFloat("current_saturation", saturation)
            .putFloat("current_brightness", p.brightness)
            .apply()
    }

    fun toggleModeB() {
        if (isSosActive) {
            isSosActive = false
        }
        val next = !isModeB
        isModeB = next
        if (next) {
            showColorPicker = false
            showTextSettings = false
            showInfoMenu = false
        }
        onModeBChange(next)
    }

    fun toggleSos() {
        val next = !isSosActive
        isSosActive = next
        if (next) {
            showColorPicker = false
            showTextSettings = false
            showInfoMenu = false
        }
    }

    // Below 30%, smoothly dim the pixel value down to 0.08f at 1% for ultra-dim night light
    val colorValue = if (brightness < 0.30f) {
        val t = ((brightness - 0.01f) / (0.30f - 0.01f)).coerceIn(0.0f, 1.0f)
        0.08f + 0.92f * t
    } else {
        1.0f
    }

    val activeDisplayColor = Color.hsv(hue, saturation, colorValue)
    val fullBrightColor = Color.hsv(hue, saturation, 1.0f)

    val backgroundColor = if (isSosActive) {
        if (isSosFlashOn) Color(0xFF0055FF) else Color.Black
    } else if (isModeB) {
        Color.Black
    } else if (textMode == TextMode.BANNER) {
        Color.Black
    } else if (textMode == TextMode.SLIDESHOW) {
        if (slideshowInverted) activeDisplayColor else Color.Black
    } else {
        activeDisplayColor
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .pointerInput(isLocked, isModeB, isSosActive, showColorPicker, showTextSettings, showInfoMenu) {
                detectTapGestures(
                    onTap = {
                        if (showColorPicker) showColorPicker = false
                        if (showTextSettings) showTextSettings = false
                        if (showInfoMenu) showInfoMenu = false
                    },
                    onDoubleTap = {
                        if (isSosActive) {
                            isSosActive = false
                        } else if (isModeB) {
                            toggleModeB()
                        } else if (!showColorPicker && !showTextSettings && !showInfoMenu) {
                            toggleModeB()
                        }
                    }
                )
            }
            .pointerInput(isLocked, isModeB, isSosActive) {
                if (isLocked || isModeB || isSosActive) return@pointerInput
                detectVerticalDragGestures(
                    onDragStart = {
                        isDraggingBrightness = true
                        hideBrightnessJob?.cancel()
                    },
                    onDragEnd = {
                        hideBrightnessJob?.cancel()
                        hideBrightnessJob = coroutineScope.launch {
                            delay(800)
                            isDraggingBrightness = false
                        }
                    },
                    onDragCancel = {
                        isDraggingBrightness = false
                    },
                    onVerticalDrag = { _, dragAmount ->
                        val sensitivity = 0.70f * screenHeightPx
                        val precisionScale = if (brightness < 0.25f) {
                            0.30f + (brightness / 0.25f) * 0.70f
                        } else {
                            1.0f
                        }
                        val delta = (-dragAmount / sensitivity) * precisionScale
                        val newBrightness = (brightness + delta).coerceIn(0.01f, 1.0f)
                        brightness = newBrightness
                        onBrightnessChange(newBrightness)
                        prefs.edit().putFloat("current_brightness", newBrightness).apply()
                        isDraggingBrightness = true
                    }
                )
            }
    ) {
        if (isModeB && !isSosActive) {
            // Mode B: Hardware LED active, screen black, 30% gray torch graphic
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                HardwareTorchCenterGraphic()
            }
        } else if (!isModeB && !isSosActive) {
            // Mode A: Display Light / Text Modes (horizontal landscape orientation across portrait screen)
            if (textMode != TextMode.OFF) {
                val configuration = LocalConfiguration.current
                val screenW = configuration.screenWidthDp.dp
                val screenH = configuration.screenHeightDp.dp
                val longSide = maxOf(screenW, screenH)
                val shortSide = minOf(screenW, screenH)
                val rotationAngle = if (textFlipped) 270f else 90f

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = longSide, height = shortSide)
                            .graphicsLayer {
                                rotationZ = rotationAngle
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        when (textMode) {
                            TextMode.BANNER -> {
                                BannerLedMarquee(
                                    text = bannerText,
                                    speed = bannerSpeed,
                                    color = fullBrightColor
                                )
                            }
                            TextMode.SLIDESHOW -> {
                                SlideshowWordDisplay(
                                    text = slideshowText,
                                    delayMs = slideshowDelayMs,
                                    isInverted = slideshowInverted,
                                    color = fullBrightColor
                                )
                            }
                            TextMode.OFF -> { }
                        }
                    }
                }
            }

            // Brightness percentage indicator while dragging (lifted above finger area)
            AnimatedVisibility(
                visible = isDraggingBrightness,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-90).dp)
            ) {
                val pct = (brightness * 100).roundToInt()
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "$pct%",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Bottom controls: Action Row & Overlays
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                // Info & Guide Overlay Sheet
                AnimatedVisibility(
                    visible = showInfoMenu && !isLocked,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    InfoGuideCard(
                        onDismiss = { showInfoMenu = false }
                    )
                }

                // Color Picker Overlay Sheet
                AnimatedVisibility(
                    visible = showColorPicker && !isLocked,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    ColorPickerCard(
                        hue = hue,
                        saturation = saturation,
                        brightness = brightness,
                        presets = presets,
                        savedPresetIndex = savedPresetIndex,
                        onHueChange = { newHue ->
                            hue = newHue
                            prefs.edit().putFloat("current_hue", newHue).apply()
                        },
                        onSaturationChange = { newSat ->
                            saturation = newSat
                            prefs.edit().putFloat("current_saturation", newSat).apply()
                        },
                        onBrightnessChange = { newBrightness ->
                            brightness = newBrightness
                            onBrightnessChange(newBrightness)
                            prefs.edit().putFloat("current_brightness", newBrightness).apply()
                        },
                        onSelectPreset = { idx -> applyPreset(idx) },
                        onSavePreset = { idx -> savePreset(idx) }
                    )
                }

                // Text Settings Overlay Sheet
                AnimatedVisibility(
                    visible = showTextSettings && !isLocked,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    TextSettingsCard(
                        textMode = textMode,
                        onTextModeChange = { newMode ->
                            textMode = newMode
                            prefs.edit().putString("text_mode", newMode.name).apply()
                        },
                        bannerText = bannerText,
                        onBannerTextChange = { newText ->
                            bannerText = newText
                            prefs.edit().putString("banner_text", newText).apply()
                        },
                        bannerSpeed = bannerSpeed,
                        onBannerSpeedChange = { newSpeed ->
                            bannerSpeed = newSpeed
                            prefs.edit().putFloat("banner_speed", newSpeed).apply()
                        },
                        slideshowText = slideshowText,
                        onSlideshowTextChange = { newText ->
                            slideshowText = newText
                            prefs.edit().putString("slideshow_text", newText).apply()
                        },
                        slideshowDelayMs = slideshowDelayMs,
                        onSlideshowDelayMsChange = { newDelay ->
                            slideshowDelayMs = newDelay
                            prefs.edit().putLong("slideshow_delay_ms", newDelay).apply()
                        },
                        slideshowInverted = slideshowInverted,
                        onSlideshowInvertedChange = { newInverted ->
                            slideshowInverted = newInverted
                            prefs.edit().putBoolean("slideshow_inverted", newInverted).apply()
                        },
                        textFlipped = textFlipped,
                        onTextFlippedChange = { newFlipped ->
                            textFlipped = newFlipped
                            prefs.edit().putBoolean("text_flipped", newFlipped).apply()
                        },
                        onDismiss = { showTextSettings = false }
                    )
                }

                // Bottom Action Row: Color Picker & Text Mode buttons (hidden when locked)
                if (!isLocked) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 36.dp, top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Color Picker Toggle Button
                        IconButton(
                            onClick = {
                                val next = !showColorPicker
                                showColorPicker = next
                                if (next) {
                                    showInfoMenu = false
                                    showTextSettings = false
                                }
                            },
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(
                                    if (showColorPicker) Color.White.copy(alpha = 0.28f)
                                    else Color.Black.copy(alpha = 0.35f)
                                )
                                .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = "Color Picker",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        // Text Mode Toggle Button (T)
                        IconButton(
                            onClick = {
                                val next = !showTextSettings
                                showTextSettings = next
                                if (next) {
                                    showColorPicker = false
                                    showInfoMenu = false
                                }
                            },
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(
                                    if (showTextSettings || textMode != TextMode.OFF) Color.White.copy(alpha = 0.28f)
                                    else Color.Black.copy(alpha = 0.35f)
                                )
                                .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.TextFields,
                                contentDescription = "Text Modes",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }
        }

        // Info Icon (Bottom-Start / unten links - only when unlocked and in Mode A)
        if (!isLocked && !isModeB && !isSosActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 36.dp, start = 20.dp)
            ) {
                IconButton(
                    onClick = {
                        val next = !showInfoMenu
                        showInfoMenu = next
                        if (next) {
                            showColorPicker = false
                            showTextSettings = false
                        }
                    },
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f))
                        .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info & Instructions",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // SOS Button (Top-Start / oben links)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 44.dp, start = 20.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSosActive) Color(0xFFD32F2F) else Color.Black.copy(alpha = 0.35f)
                    )
                    .border(
                        1.dp,
                        if (isSosActive) Color.Red else Color.White.copy(alpha = 0.25f),
                        CircleShape
                    )
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        toggleSos()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "SOS",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Lock Icon (Bottom-End / unten rechts)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 36.dp, end = 20.dp)
        ) {
            IconButton(
                onClick = {
                    val nextLock = !isLocked
                    isLocked = nextLock
                    prefs.edit().putBoolean("is_locked", nextLock).apply()
                    if (nextLock) {
                        showColorPicker = false
                        showTextSettings = false
                        showInfoMenu = false
                    }
                },
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
            ) {
                Icon(
                    imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = if (isLocked) "Unlock" else "Lock",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * Authentic LED Dot-Matrix Marquee with Neon Halo Glow and OLED Black background.
 */
@Composable
fun BannerLedMarquee(
    text: String,
    speed: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ledTypeface = remember(context) {
        try {
            ResourcesCompat.getFont(context, R.font.dotmatrix)
                ?: android.graphics.Typeface.MONOSPACE
        } catch (_: Exception) {
            android.graphics.Typeface.MONOSPACE
        }
    }

    val displayText = remember(text) {
        val t = text.trim()
        if (t.isEmpty()) "LUCE" else t
    }
    val loopText = remember(displayText) { "$displayText   •   " }

    val androidColor = remember(color) {
        android.graphics.Color.argb(
            255,
            (color.red * 255).roundToInt(),
            (color.green * 255).roundToInt(),
            (color.blue * 255).roundToInt()
        )
    }

    var scrollOffset by remember { mutableFloatStateOf(0f) }
    var measuredTextWidth by remember { mutableFloatStateOf(1000f) }

    val density = LocalDensity.current
    val speedPxPerSec = remember(speed, density) {
        with(density) { (90.dp + (450.dp * speed)).toPx() }
    }

    LaunchedEffect(loopText, speedPxPerSec, measuredTextWidth) {
        var lastNano = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (lastNano != 0L) {
                    val dt = (now - lastNano) / 1_000_000_000f
                    val w = measuredTextWidth.coerceAtLeast(10f)
                    scrollOffset = (scrollOffset + speedPxPerSec * dt) % w
                }
                lastNano = now
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        // Size text to fill ~60% of the short axis (width of phone)
        val targetTextSize = h * 0.60f

        // Pass 1: Neon bloom halo around the dots
        val glowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = androidColor
            typeface = ledTypeface
            textSize = targetTextSize
            style = android.graphics.Paint.Style.FILL_AND_STROKE
            strokeWidth = 6.dp.toPx()
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
            alpha = 140
            setShadowLayer(20.dp.toPx(), 0f, 0f, androidColor)
        }

        // Pass 2: Crisp bright core illuminated LED diode dots
        val corePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = androidColor
            typeface = ledTypeface
            textSize = targetTextSize
            style = android.graphics.Paint.Style.FILL
            alpha = 255
            setShadowLayer(4.dp.toPx(), 0f, 0f, androidColor)
        }

        val textWidth = corePaint.measureText(loopText).coerceAtLeast(1f)
        if (measuredTextWidth != textWidth) {
            measuredTextWidth = textWidth
        }

        val metrics = corePaint.fontMetrics
        // Center vertically on screen
        val baselineY = (h - (metrics.ascent + metrics.descent)) / 2f

        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas

            var drawX = -scrollOffset
            while (drawX > 0) {
                drawX -= textWidth
            }
            while (drawX < w) {
                if (drawX + textWidth > 0) {
                    // Glow pass
                    native.drawText(loopText, drawX, baselineY, glowPaint)
                    // Core illuminated pass
                    native.drawText(loopText, drawX, baselineY, corePaint)
                }
                drawX += textWidth
            }
        }
    }
}

/**
 * Sequential Word-by-Word Slideshow with dynamic auto-sizing and contrast invert.
 */
@Composable
fun SlideshowWordDisplay(
    text: String,
    delayMs: Long,
    isInverted: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    val words = remember(text) {
        text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.ifEmpty { listOf("LUCE") }
    }

    var currentWordIndex by remember(words) { mutableIntStateOf(0) }

    LaunchedEffect(words, delayMs) {
        currentWordIndex = 0
        while (isActive) {
            delay(delayMs)
            currentWordIndex = (currentWordIndex + 1) % words.size
        }
    }

    val currentWord = words.getOrElse(currentWordIndex % words.size) { "LUCE" }

    // Relative luminance calculation for high-contrast inverted text
    val lum = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
    val wordColor = if (isInverted) {
        if (lum > 0.45f) Color.Black else Color.White
    } else {
        color
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val maxW = maxWidth
        val maxH = maxHeight
        val density = LocalDensity.current

        val optimalFontSize = remember(currentWord, maxW, maxH) {
            val targetW = with(density) { (maxW * 0.88f).toPx() }
            val targetH = with(density) { (maxH * 0.72f).toPx() }

            val paint = android.graphics.Paint().apply {
                typeface = android.graphics.Typeface.create(
                    android.graphics.Typeface.SANS_SERIF,
                    android.graphics.Typeface.BOLD
                )
                textSize = 100f
            }
            val measuredW = paint.measureText(currentWord).coerceAtLeast(1f)
            val metrics = paint.fontMetrics
            val measuredH = (metrics.descent - metrics.ascent).coerceAtLeast(1f)

            val scaleX = targetW / measuredW
            val scaleY = targetH / measuredH
            val bestPx = 100f * minOf(scaleX, scaleY)
            with(density) { bestPx.toSp() }
        }

        Text(
            text = currentWord,
            fontSize = optimalFontSize,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.SansSerif,
            color = wordColor,
            textAlign = TextAlign.Center,
            letterSpacing = (-0.5).sp,
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * Text Settings Overlay Sheet for configuring Banner Mode and Word Slideshow Mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextSettingsCard(
    textMode: TextMode,
    onTextModeChange: (TextMode) -> Unit,
    bannerText: String,
    onBannerTextChange: (String) -> Unit,
    bannerSpeed: Float,
    onBannerSpeedChange: (Float) -> Unit,
    slideshowText: String,
    onSlideshowTextChange: (String) -> Unit,
    slideshowDelayMs: Long,
    onSlideshowDelayMsChange: (Long) -> Unit,
    slideshowInverted: Boolean,
    onSlideshowInvertedChange: (Boolean) -> Unit,
    textFlipped: Boolean,
    onTextFlippedChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xE6181818))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
            .pointerInput(Unit) {
                detectTapGestures { }
            }
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Text Modes",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color(0xFFAAAAAA),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Mode Selector Segmented Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF242424))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    TextMode.OFF to "Off",
                    TextMode.BANNER to "Banner",
                    TextMode.SLIDESHOW to "Slideshow"
                ).forEach { (mode, label) ->
                    val isSelected = textMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color.White else Color.Transparent)
                            .clickable { onTextModeChange(mode) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.Black else Color(0xFFAAAAAA),
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            // Mode Content
            when (textMode) {
                TextMode.BANNER -> {
                    // Text Input with Clear button
                    OutlinedTextField(
                        value = bannerText,
                        onValueChange = onBannerTextChange,
                        label = { Text("Banner Text", color = Color(0xFFAAAAAA)) },
                        placeholder = { Text("Enter text...", color = Color(0xFF666666)) },
                        singleLine = true,
                        trailingIcon = {
                            if (bannerText.isNotEmpty()) {
                                IconButton(onClick = { onBannerTextChange("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        tint = Color(0xFFAAAAAA)
                                    )
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                            cursorColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Speed Slider
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Scroll Speed",
                                color = Color(0xFFDDDDDD),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = when {
                                    bannerSpeed < 0.35f -> "Slow"
                                    bannerSpeed > 0.65f -> "Fast"
                                    else -> "Normal"
                                },
                                color = Color(0xFFAAAAAA),
                                fontSize = 13.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            value = bannerSpeed,
                            onValueChange = onBannerSpeedChange,
                            valueRange = 0.1f..1.0f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                TextMode.SLIDESHOW -> {
                    // Text Input with Clear button
                    OutlinedTextField(
                        value = slideshowText,
                        onValueChange = onSlideshowTextChange,
                        label = { Text("Slideshow Text", color = Color(0xFFAAAAAA)) },
                        placeholder = { Text("Enter words...", color = Color(0xFF666666)) },
                        trailingIcon = {
                            if (slideshowText.isNotEmpty()) {
                                IconButton(onClick = { onSlideshowTextChange("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        tint = Color(0xFFAAAAAA)
                                    )
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                            cursorColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Word Delay Slider
                    val delaySec = slideshowDelayMs / 1000f
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Word Delay",
                                color = Color(0xFFDDDDDD),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = String.format(Locale.US, "%.1fs", delaySec),
                                color = Color(0xFFAAAAAA),
                                fontSize = 13.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            value = delaySec,
                            onValueChange = { newSec ->
                                onSlideshowDelayMsChange((newSec * 1000f).roundToLong().coerceIn(200L, 5000L))
                            },
                            valueRange = 0.2f..5.0f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Invert Toggle Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Invert Colors",
                                color = Color(0xFFDDDDDD),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (slideshowInverted) "Contrast text on colored background" else "Colored text on black background",
                                color = Color(0xFFAAAAAA),
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = slideshowInverted,
                            onCheckedChange = onSlideshowInvertedChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF4CAF50),
                                uncheckedThumbColor = Color(0xFFAAAAAA),
                                uncheckedTrackColor = Color(0xFF333333)
                            )
                        )
                    }
                }

                TextMode.OFF -> {
                    Text(
                        text = "Standard display lamp active. Select Banner or Slideshow to show glowing text.",
                        color = Color(0xFFAAAAAA),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            // Flip Direction 180° toggle (available for both Banner and Slideshow)
            if (textMode != TextMode.OFF) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Flip 180° Direction",
                            color = Color(0xFFDDDDDD),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (textFlipped) "Phone turned: Camera on Right" else "Phone turned: Camera on Left",
                            color = Color(0xFFAAAAAA),
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = textFlipped,
                        onCheckedChange = onTextFlippedChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF4CAF50),
                            uncheckedThumbColor = Color(0xFFAAAAAA),
                            uncheckedTrackColor = Color(0xFF333333)
                        )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerCard(
    hue: Float,
    saturation: Float,
    brightness: Float,
    presets: List<ColorPreset>,
    savedPresetIndex: Int,
    onHueChange: (Float) -> Unit,
    onSaturationChange: (Float) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onSelectPreset: (Int) -> Unit,
    onSavePreset: (Int) -> Unit
) {
    val rainbowBrush = remember {
        Brush.horizontalGradient(
            listOf(
                Color.Red,
                Color.Yellow,
                Color.Green,
                Color.Cyan,
                Color.Blue,
                Color.Magenta,
                Color.Red
            )
        )
    }

    val saturationBrush = remember(hue) {
        Brush.horizontalGradient(
            listOf(
                Color.White,
                Color.hsv(hue, 1.0f, 1.0f)
            )
        )
    }

    val brightnessBrush = remember(hue, saturation) {
        Brush.horizontalGradient(
            listOf(
                Color.Black,
                Color.hsv(hue, saturation, 1.0f)
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xE6181818))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
            // Consume clicks inside overlay card to prevent dismiss
            .pointerInput(Unit) {
                detectTapGestures { }
            }
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Slider 1: Hue (0f..360f)
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Hue",
                        color = Color(0xFFDDDDDD),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${hue.roundToInt()}°",
                        color = Color(0xFFAAAAAA),
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Slider(
                    value = hue,
                    onValueChange = onHueChange,
                    valueRange = 0f..360f,
                    modifier = Modifier.fillMaxWidth(),
                    track = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(rainbowBrush)
                        )
                    },
                    thumb = {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(2.dp, Color(0xFF222222), CircleShape)
                        )
                    }
                )
            }

            // Slider 2: Saturation (0f..1f)
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Saturation",
                        color = Color(0xFFDDDDDD),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${(saturation * 100).roundToInt()}%",
                        color = Color(0xFFAAAAAA),
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Slider(
                    value = saturation,
                    onValueChange = onSaturationChange,
                    valueRange = 0f..1.0f,
                    modifier = Modifier.fillMaxWidth(),
                    track = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(saturationBrush)
                        )
                    },
                    thumb = {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(2.dp, Color(0xFF222222), CircleShape)
                        )
                    }
                )
            }

            // Slider 3: Brightness (0.01f..1.0f)
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Brightness",
                        color = Color(0xFFDDDDDD),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${(brightness * 100).roundToInt()}%",
                        color = Color(0xFFAAAAAA),
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Slider(
                    value = brightness,
                    onValueChange = onBrightnessChange,
                    valueRange = 0.01f..1.0f,
                    modifier = Modifier.fillMaxWidth(),
                    track = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(brightnessBrush)
                        )
                    },
                    thumb = {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(2.dp, Color(0xFF222222), CircleShape)
                        )
                    }
                )
            }

            // 4 Presets Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                presets.forEachIndexed { index, preset ->
                    val presetColor = Color.hsv(preset.hue, preset.saturation, 1.0f)
                    val isSaved = (savedPresetIndex == index)

                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(presetColor)
                            .border(2.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                            .combinedClickable(
                                onClick = { onSelectPreset(index) },
                                onLongClick = { onSavePreset(index) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSaved) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Saved",
                                tint = if (preset.saturation < 0.3f) Color.Black else Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InfoGuideCard(
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xE6181818))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
            .pointerInput(Unit) {
                detectTapGestures { }
            }
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 440.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Luce",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "v2.0",
                        color = Color(0xFFAAAAAA),
                        fontSize = 13.sp
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color(0xFFAAAAAA),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Quick Guide Section
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "QUICK GUIDE",
                    color = Color(0xFFCCCCCC),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                GuideItem(icon = "↕", title = "Brightness Gesture", desc = "Drag vertically anywhere to adjust light level (1%–100%). Fine control below 25%.")
                GuideItem(icon = "👆", title = "Double-Tap Switch", desc = "Switch between Display Lamp (Mode A) and Camera LED Torch (Mode B).")
                GuideItem(icon = "🎨", title = "Color Picker", desc = "Tap the palette button to adjust hue, saturation, and brightness.")
                GuideItem(icon = "🔤", title = "LED Banner & Slideshow", desc = "Tap the 'T' button for continuous LED dot-matrix marquee or word flasher.")
                GuideItem(icon = "💾", title = "Preset Slots", desc = "Tap a slot to load color & brightness. Long-press to save current settings.")
                GuideItem(icon = "🔒", title = "Screen Lock", desc = "Tap bottom-right to lock touch gestures for undisturbed night use.")
                GuideItem(icon = "🆘", title = "Morse SOS", desc = "Tap top-left to flash international Morse code (... --- ...) via screen and LED.")
            }

            // Divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.12f))
            )

            // About Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "ABOUT",
                    color = Color(0xFFCCCCCC),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Creator", color = Color(0xFFAAAAAA), fontSize = 13.sp)
                    Text(text = "pasq.org", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "License", color = Color(0xFFAAAAAA), fontSize = 13.sp)
                    Text(text = "GNU GPL v3.0", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Privacy", color = Color(0xFFAAAAAA), fontSize = 13.sp)
                    Text(text = "100% Offline • No Tracking", color = Color(0xFF81C784), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun GuideItem(icon: String, title: String, desc: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = icon,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 1.dp)
        )
        Column {
            Text(
                text = title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = desc,
                color = Color(0xFFAAAAAA),
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
fun HardwareTorchCenterGraphic() {
    Canvas(modifier = Modifier.size(240.dp)) {
        val gray = Color(0xFF4D4D4D)
        val w = size.width
        val h = size.height

        // Beam / Rays shining upward
        // Center ray
        drawLine(
            color = gray,
            start = Offset(w * 0.5f, h * 0.30f),
            end = Offset(w * 0.5f, h * 0.05f),
            strokeWidth = 5.dp.toPx(),
            cap = StrokeCap.Round
        )
        // Inner left diagonal ray
        drawLine(
            color = gray,
            start = Offset(w * 0.44f, h * 0.32f),
            end = Offset(w * 0.26f, h * 0.12f),
            strokeWidth = 5.dp.toPx(),
            cap = StrokeCap.Round
        )
        // Inner right diagonal ray
        drawLine(
            color = gray,
            start = Offset(w * 0.56f, h * 0.32f),
            end = Offset(w * 0.74f, h * 0.12f),
            strokeWidth = 5.dp.toPx(),
            cap = StrokeCap.Round
        )
        // Outer left ray
        drawLine(
            color = gray,
            start = Offset(w * 0.38f, h * 0.35f),
            end = Offset(w * 0.14f, h * 0.24f),
            strokeWidth = 5.dp.toPx(),
            cap = StrokeCap.Round
        )
        // Outer right ray
        drawLine(
            color = gray,
            start = Offset(w * 0.62f, h * 0.35f),
            end = Offset(w * 0.86f, h * 0.24f),
            strokeWidth = 5.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Flashlight Head / Bezel (trapezoid pointing up)
        val headPath = Path().apply {
            moveTo(w * 0.35f, h * 0.39f)
            lineTo(w * 0.65f, h * 0.39f)
            lineTo(w * 0.59f, h * 0.52f)
            lineTo(w * 0.41f, h * 0.52f)
            close()
        }
        drawPath(path = headPath, color = gray)

        // Flashlight Handle / Body
        drawRoundRect(
            color = gray,
            topLeft = Offset(w * 0.42f, h * 0.54f),
            size = Size(w * 0.16f, h * 0.32f),
            cornerRadius = CornerRadius(8.dp.toPx())
        )

        // Flashlight Base / Tailcap
        drawRoundRect(
            color = gray,
            topLeft = Offset(w * 0.40f, h * 0.85f),
            size = Size(w * 0.20f, h * 0.04f),
            cornerRadius = CornerRadius(4.dp.toPx())
        )
    }
}
