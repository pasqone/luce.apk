package com.luce.torch

import android.content.Context
import android.content.SharedPreferences
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class ColorPreset(val hue: Float, val saturation: Float)

class MainActivity : ComponentActivity() {

    private lateinit var prefs: SharedPreferences
    private var isTorchActive = false
    private var lastDisplayBrightness = 0.8f
    private var isModeBState = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Immersive fullscreen edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)
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
        lp.screenBrightness = brightness.coerceIn(0.01f, 1.0f)
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

    // Persistent state
    var hue by remember { mutableFloatStateOf(prefs.getFloat("current_hue", 35f)) }
    var saturation by remember { mutableFloatStateOf(prefs.getFloat("current_saturation", 0.0f)) }
    var brightness by remember { mutableFloatStateOf(prefs.getFloat("current_brightness", 0.8f).coerceIn(0.01f, 1.0f)) }
    var isLocked by remember { mutableStateOf(prefs.getBoolean("is_locked", false)) }

    // SOS State
    var isSosActive by remember { mutableStateOf(false) }
    var isSosFlashOn by remember { mutableStateOf(false) }

    // Color picker visibility
    var showColorPicker by remember { mutableStateOf(false) }

    // Drag brightness indicator
    var isDraggingBrightness by remember { mutableStateOf(false) }
    var hideBrightnessJob by remember { mutableStateOf<Job?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // 4 Presets
    val presets = remember {
        mutableStateListOf(
            ColorPreset(
                prefs.getFloat("preset_0_hue", 0f),
                prefs.getFloat("preset_0_sat", 0.0f)
            ),
            ColorPreset(
                prefs.getFloat("preset_1_hue", 35f),
                prefs.getFloat("preset_1_sat", 0.45f)
            ),
            ColorPreset(
                prefs.getFloat("preset_2_hue", 22f),
                prefs.getFloat("preset_2_sat", 0.85f)
            ),
            ColorPreset(
                prefs.getFloat("preset_3_hue", 0f),
                prefs.getFloat("preset_3_sat", 1.0f)
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
        presets[index] = ColorPreset(hue, saturation)
        prefs.edit()
            .putFloat("preset_${index}_hue", hue)
            .putFloat("preset_${index}_sat", saturation)
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
        prefs.edit()
            .putFloat("current_hue", hue)
            .putFloat("current_saturation", saturation)
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
        }
        onModeBChange(next)
    }

    fun toggleSos() {
        val next = !isSosActive
        isSosActive = next
        if (next) {
            showColorPicker = false
        }
    }

    val backgroundColor = if (isSosActive) {
        if (isSosFlashOn) Color(0xFF0055FF) else Color.Black
    } else if (isModeB) {
        Color.Black
    } else {
        Color.hsv(hue, saturation, 1.0f)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .pointerInput(isLocked, isModeB, isSosActive, showColorPicker) {
                detectTapGestures(
                    onTap = {
                        if (showColorPicker) {
                            showColorPicker = false
                        }
                    },
                    onDoubleTap = {
                        if (isSosActive) {
                            isSosActive = false
                        } else if (isModeB) {
                            toggleModeB()
                        } else if (!showColorPicker) {
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
                        // Upwards drag is negative in y-axis -> increases brightness
                        val sensitivity = 0.70f * screenHeightPx
                        val delta = -dragAmount / sensitivity
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
            // Mode A: Display Light Mode

            // Center brightness percentage indicator while dragging
            AnimatedVisibility(
                visible = isDraggingBrightness,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
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

            // Bottom controls: Color Picker Button & Overlay
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
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

                // Color Picker Toggle Button (Bottom-Center, hidden when locked)
                if (!isLocked) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 36.dp, top = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = { showColorPicker = !showColorPicker },
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.35f))
                                .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = "Color Picker",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
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

        // Lock Icon (Bottom-Start / unten links)
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 36.dp, start = 20.dp)
        ) {
            IconButton(
                onClick = {
                    val nextLock = !isLocked
                    isLocked = nextLock
                    prefs.edit().putBoolean("is_locked", nextLock).apply()
                    if (nextLock) {
                        showColorPicker = false
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
