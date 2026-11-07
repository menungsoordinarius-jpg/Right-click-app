package com.example.ui.overlay

import android.view.MotionEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.backend.BackendState
import com.example.backend.BackendStatus
import com.example.backend.BackendType
import com.example.backend.InputBackendManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot

// =========================================================================
// KONSTANTA DIMENSI & GAIN (Sesuai Desain HTML Virtual Touch Pad)
// =========================================================================
val PANEL_MAX_WIDTH = 340.dp
val PANEL_CORNER_RADIUS = 20.dp
val PANEL_PADDING = 15.dp

val CONTROL_BTN_HEIGHT = 80.dp
val CONTROL_BTN_RADIUS = 16.dp
val CONTROL_BTN_GAP = 15.dp

val TOUCHPAD_HEIGHT = 180.dp
val TOUCHPAD_RADIUS = 18.dp

val SCROLL_STRIP_WIDTH = 20.dp
val SCROLL_STRIP_RADIUS = 7.dp
val SCROLL_TICK_INTERVAL_DP = 16f
val SCROLL_STEP_PX = 24f
const val SCROLL_NATURAL = true

val DRAG_HANDLE_WIDTH = 120.dp
val DRAG_HANDLE_HEIGHT = 6.dp
val DRAG_HANDLE_TOUCH_HEIGHT = 32.dp
val DRAG_HANDLE_GAP = 10.dp

// Touchpad Gain Constants
const val BASE_GAIN = 0.8f
const val ACCEL = 2.5f
const val MAX_GAIN = 8.0f
const val HOLD_TIME_MS = 600L

// Konstanta Ketuk = Klik Kiri di Touchpad
const val TAP_MAX_DURATION_MS = 250L
const val TAP_MAX_DISTANCE_DP = 10f
const val DOUBLE_TAP_WINDOW_MS = 300L

// Warna dari HTML
val ColorBgDark = Color(0xEF14161C) // #14161C 94%
val ColorWhiteLayer = Color(0x0CFFFFFF) // 4.5% white overlay
val ColorBorderWhite = Color(0x18FFFFFF) // #ffffff18
val ColorActiveBorder = Color(0x7738E6A2) // #38e6a277
val ColorActiveGreen = Color(0xFF39EFA1) // #39efa1
val ColorStatusGreen = Color(0xFF36D995) // #36d995
val ColorDotInactive = Color(0xFF555B66) // #555b66
val ColorTextSystem = Color(0xFF8D929E) // #8d929e
val ColorTextTouchpad = Color(0xFFA4ACB9) // #a4acb9
val ColorTextSub = Color(0xFF666E7C) // #666e7c
val ColorHandle = Color(0x44FFFFFF) // #ffffff44

/**
 * Pilihan Mode Rekam
 */
enum class RecordMode(val displayName: String) {
    OFF("Mati"),
    SECURE("Sembunyikan dari rekaman"),
    TRANSPARENT("Transparan")
}

/**
 * Pilihan Perilaku Panel (Tembus vs Dinding)
 */
enum class PanelBehavior(val displayName: String) {
    TEMBUS("Tembus"),
    DINDING("Dinding")
}

/**
 * Komponen utama panel kontrol RightClickDash (Virtual Touch Pad).
 */
@Composable
fun ControlPanel(
    backendState: BackendState,
    cursorX: Float,
    cursorY: Float,
    recordMode: RecordMode = RecordMode.OFF,
    panelBehavior: PanelBehavior = PanelBehavior.TEMBUS,
    inputMode: InputMode = InputMode.TOUCHPAD,
    heldButton: Int = 0,
    resetTouchpadTrigger: Int = 0,
    onSwitchBackend: (BackendType) -> Unit,
    onRequestPermission: () -> Unit,
    onRecheckBackend: () -> Unit,
    onSelectRecordMode: (RecordMode) -> Unit,
    onSelectPanelBehavior: (PanelBehavior) -> Unit,
    onSelectInputMode: (InputMode) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onTouchStart: () -> Unit = {},
    onTouchEnd: (isCancel: Boolean) -> Unit = {},
    onClickLeft: () -> Unit,
    onClickRight: () -> Unit,
    onLockLeft: () -> Unit,
    onLockRight: () -> Unit,
    onUnlock: () -> Unit,
    onRelativeMove: (dx: Float, dy: Float) -> Unit,
    onTapClick: () -> Unit,
    onScroll: (notches: Int) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (recordMode == RecordMode.TRANSPARENT) 0f else 1f)
            .shadow(
                elevation = 24.dp,
                shape = RoundedCornerShape(PANEL_CORNER_RADIUS),
                ambientColor = Color.Black,
                spotColor = Color.Black
            )
            .clip(RoundedCornerShape(PANEL_CORNER_RADIUS))
            .background(ColorBgDark)
            .background(ColorWhiteLayer)
            .border(1.dp, ColorBorderWhite, RoundedCornerShape(PANEL_CORNER_RADIUS))
            .padding(PANEL_PADDING)
            .testTag("virtual_touch_pad")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // 1. HEADER (Status dot + Teks + Kursor Koordinat Monospace + Menu dropdown)
            HeaderSection(
                backendState = backendState,
                cursorX = cursorX,
                cursorY = cursorY,
                recordMode = recordMode,
                panelBehavior = panelBehavior,
                inputMode = inputMode,
                onSwitchBackend = onSwitchBackend,
                onRequestPermission = onRequestPermission,
                onRecheckBackend = onRecheckBackend,
                onSelectRecordMode = onSelectRecordMode,
                onSelectPanelBehavior = onSelectPanelBehavior,
                onSelectInputMode = onSelectInputMode,
                onOpenSettings = onOpenSettings
            )

            Spacer(modifier = Modifier.height(15.dp))

            // 2. TOMBOL BESAR: CLICK LEFT & CLICK RIGHT
            ControlsSection(
                heldButton = heldButton,
                onTouchStart = onTouchStart,
                onTouchEnd = onTouchEnd,
                onClickLeft = onClickLeft,
                onClickRight = onClickRight,
                onLockLeft = onLockLeft,
                onLockRight = onLockRight,
                onUnlock = onUnlock
            )

            Spacer(modifier = Modifier.height(15.dp))

            // 3. TOUCHPAD RELATIF & 4. SCROLL STRIP
            TouchRowSection(
                isLocked = (heldButton != 0),
                resetTouchpadTrigger = resetTouchpadTrigger,
                onTouchStart = onTouchStart,
                onTouchEnd = onTouchEnd,
                onRelativeMove = onRelativeMove,
                onTapClick = onTapClick,
                onScroll = onScroll
            )
        }
    }
}

/**
 * Header: Titik status 7dp dengan glow + teks 10sp + koordinat kursor real-time (monospace 9sp).
 * Ketuk header untuk membuka menu pilihan backend, cek ulang, Mode Rekam, Perilaku Panel, dan Mode Input.
 */
@Composable
fun HeaderSection(
    backendState: BackendState,
    cursorX: Float,
    cursorY: Float,
    recordMode: RecordMode,
    panelBehavior: PanelBehavior,
    inputMode: InputMode = InputMode.TOUCHPAD,
    onSwitchBackend: (BackendType) -> Unit,
    onRequestPermission: () -> Unit,
    onRecheckBackend: () -> Unit,
    onSelectRecordMode: (RecordMode) -> Unit,
    onSelectPanelBehavior: (PanelBehavior) -> Unit,
    onSelectInputMode: (InputMode) -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val dotColor = when (backendState.status) {
        BackendStatus.READY -> ColorStatusGreen
        BackendStatus.CONNECTING, BackendStatus.PERMISSION_REQUIRED -> Color(0xFFFFD54F) // Kuning
        else -> Color(0xFFFF5252) // Merah
    }

    val statusText = when {
        backendState.status == BackendStatus.READY -> {
            if (backendState.type == BackendType.SHIZUKU) "READY (shizuku is connect)"
            else "READY (root is connect)"
        }
        backendState.status == BackendStatus.NOT_RUNNING -> "SHIZUKU BELUM BERJALAN"
        backendState.status == BackendStatus.PERMISSION_REQUIRED -> "IZIN SHIZUKU BELUM DIBERIKAN"
        backendState.status == BackendStatus.CONNECTING -> "MENGHUBUNGKAN..."
        else -> "ERROR"
    }

    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { menuExpanded = true }
                .padding(vertical = 2.dp, horizontal = 2.dp)
                .testTag("header_status")
        ) {
            // Baris 1: Titik status dan teks status
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Titik 7dp dengan glow
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .drawBehind {
                            drawCircle(
                                color = dotColor.copy(alpha = 0.5f),
                                radius = 6.dp.toPx()
                            )
                            drawCircle(
                                color = dotColor,
                                radius = 3.5.dp.toPx()
                            )
                        }
                )

                Spacer(modifier = Modifier.width(7.dp))

                Text(
                    text = statusText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = ColorTextSystem,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Baris 2: Posisi kursor real-time (font monospace 9sp, warna #8d929e, lebar tetap 110dp)
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = "X: ${cursorX.toInt()}  Y: ${cursorY.toInt()}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Normal,
                    color = ColorTextSystem,
                    modifier = Modifier
                        .width(110.dp)
                        .testTag("cursor_position_text")
                )
            }
        }

        // Dropdown Menu pemilihan backend, cek ulang, Mode Rekam, dan Perilaku Panel
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            modifier = Modifier
                .background(Color(0xFF1E212B))
                .border(1.dp, ColorBorderWhite, RoundedCornerShape(8.dp))
        ) {
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (backendState.type == BackendType.SHIZUKU) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = ColorActiveGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Backend: Shizuku", color = Color.White, fontSize = 12.sp)
                    }
                },
                onClick = {
                    menuExpanded = false
                    onSwitchBackend(BackendType.SHIZUKU)
                }
            )

            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (backendState.type == BackendType.ROOT) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = ColorActiveGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Backend: Root", color = Color.White, fontSize = 12.sp)
                    }
                },
                onClick = {
                    menuExpanded = false
                    onSwitchBackend(BackendType.ROOT)
                }
            )

            if (backendState.status == BackendStatus.PERMISSION_REQUIRED) {
                DropdownMenuItem(
                    text = {
                        Text("Minta Izin Shizuku", color = ColorActiveGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    },
                    onClick = {
                        menuExpanded = false
                        onRequestPermission()
                    }
                )
            }

            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = ColorTextSystem, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cek Ulang Status", color = Color.White, fontSize = 12.sp)
                    }
                },
                onClick = {
                    menuExpanded = false
                    onRecheckBackend()
                }
            )

            // Subheader: PERILAKU PANEL (Tembus vs Dinding)
            DropdownMenuItem(
                text = {
                    Text("PERILAKU PANEL", color = ColorTextSub, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                },
                onClick = {},
                enabled = false
            )

            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (panelBehavior == PanelBehavior.TEMBUS) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = ColorActiveGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Perilaku: Tembus (Default)", color = Color.White, fontSize = 12.sp)
                    }
                },
                onClick = {
                    menuExpanded = false
                    onSelectPanelBehavior(PanelBehavior.TEMBUS)
                }
            )

            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (panelBehavior == PanelBehavior.DINDING) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = ColorActiveGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Perilaku: Dinding", color = Color.White, fontSize = 12.sp)
                    }
                },
                onClick = {
                    menuExpanded = false
                    onSelectPanelBehavior(PanelBehavior.DINDING)
                }
            )

            // Subheader: MODE REKAM
            DropdownMenuItem(
                text = {
                    Text("MODE REKAM", color = ColorTextSub, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                },
                onClick = {},
                enabled = false
            )

            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (recordMode == RecordMode.OFF) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = ColorActiveGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Mati (Normal)", color = Color.White, fontSize = 12.sp)
                    }
                },
                onClick = {
                    menuExpanded = false
                    onSelectRecordMode(RecordMode.OFF)
                }
            )

            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (recordMode == RecordMode.SECURE) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = ColorActiveGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Sembunyikan dari rekaman", color = Color.White, fontSize = 12.sp)
                    }
                },
                onClick = {
                    menuExpanded = false
                    onSelectRecordMode(RecordMode.SECURE)
                }
            )

            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (recordMode == RecordMode.TRANSPARENT) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = ColorActiveGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Transparan (Hanya Kursor)", color = Color.White, fontSize = 12.sp)
                    }
                },
                onClick = {
                    menuExpanded = false
                    onSelectRecordMode(RecordMode.TRANSPARENT)
                }
            )

            // Subheader: MODE INPUT
            DropdownMenuItem(
                text = {
                    Text("MODE INPUT", color = ColorTextSub, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                },
                onClick = {},
                enabled = false
            )

            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (inputMode == InputMode.TOUCHSCREEN) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = ColorActiveGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Mode: Layar Sentuh (Bawaan)", color = Color.White, fontSize = 12.sp)
                    }
                },
                onClick = {
                    menuExpanded = false
                    onSelectInputMode(InputMode.TOUCHSCREEN)
                }
            )

            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (inputMode == InputMode.TOUCHPAD) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = ColorActiveGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Mode: Touchpad Virtual", color = Color.White, fontSize = 12.sp)
                    }
                },
                onClick = {
                    menuExpanded = false
                    onSelectInputMode(InputMode.TOUCHPAD)
                }
            )

            // Pengaturan Gerakan & Sensitivitas
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = ColorActiveGreen, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pengaturan Gerakan & Kursor...", color = ColorActiveGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                },
                onClick = {
                    menuExpanded = false
                    onOpenSettings()
                }
            )
        }
    }
}

/**
 * Dua tombol besar: CLICK LEFT dan CLICK RIGHT.
 * Hanya merespons sentuhan JARI (PointerType.Touch), mengabaikan event mouse yang disuntikkan.
 */
@Composable
fun ControlsSection(
    heldButton: Int,
    onTouchStart: () -> Unit = {},
    onTouchEnd: (isCancel: Boolean) -> Unit = {},
    onClickLeft: () -> Unit,
    onClickRight: () -> Unit,
    onLockLeft: () -> Unit,
    onLockRight: () -> Unit,
    onUnlock: () -> Unit
) {
    val isLeftLocked = heldButton == MotionEvent.BUTTON_PRIMARY
    val isRightLocked = heldButton == MotionEvent.BUTTON_SECONDARY

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CONTROL_BTN_GAP)
    ) {
        // Tombol CLICK LEFT
        ControlButton(
            modifier = Modifier.weight(1f),
            label = "CLICK LEFT",
            isLocked = isLeftLocked,
            onTouchStart = onTouchStart,
            onTouchEnd = onTouchEnd,
            onQuickClick = onClickLeft,
            onLock = onLockLeft,
            onUnlock = onUnlock
        )

        // Tombol CLICK RIGHT
        ControlButton(
            modifier = Modifier.weight(1f),
            label = "CLICK RIGHT",
            isLocked = isRightLocked,
            onTouchStart = onTouchStart,
            onTouchEnd = onTouchEnd,
            onQuickClick = onClickRight,
            onLock = onLockRight,
            onUnlock = onUnlock
        )
    }
}

@Composable
fun ControlButton(
    modifier: Modifier = Modifier,
    label: String,
    isLocked: Boolean,
    onTouchStart: () -> Unit = {},
    onTouchEnd: (isCancel: Boolean) -> Unit = {},
    onQuickClick: () -> Unit,
    onLock: () -> Unit,
    onUnlock: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var isHolding by remember { mutableStateOf(false) }
    var holdJob by remember { mutableStateOf<Job?>(null) }
    val scaleAnim = remember { Animatable(1f) }

    val holdRingAlpha = remember { Animatable(0.2f) }
    val holdRingScale = remember { Animatable(0.95f) }

    LaunchedEffect(isHolding) {
        if (isHolding) {
            holdRingAlpha.snapTo(0.2f)
            holdRingScale.snapTo(0.95f)
            launch {
                holdRingAlpha.animateTo(1f, tween(600, easing = LinearEasing))
            }
            launch {
                holdRingScale.animateTo(1f, tween(600, easing = LinearEasing))
            }
        }
    }

    val bgBrush = if (isLocked) {
        Brush.linearGradient(
            colors = listOf(Color(0x1936D995), Color(0x05FFFFFF)),
            start = Offset(0f, 0f),
            end = Offset(400f, 400f)
        )
    } else {
        Brush.linearGradient(
            colors = listOf(Color(0x0DFFFFFF), Color(0x03FFFFFF)),
            start = Offset(0f, 0f),
            end = Offset(400f, 400f)
        )
    }

    val borderColor = if (isLocked) ColorActiveBorder else ColorBorderWhite

    Box(
        modifier = modifier
            .height(CONTROL_BTN_HEIGHT)
            .scale(scaleAnim.value)
            .clip(RoundedCornerShape(CONTROL_BTN_RADIUS))
            .background(bgBrush)
            .border(1.dp, borderColor, RoundedCornerShape(CONTROL_BTN_RADIUS))
            .pointerInput(isLocked) {
                awaitPointerEventScope {
                    while (true) {
                        val downEvent = awaitPointerEvent()
                        val downChange = downEvent.changes.firstOrNull() ?: continue

                        // ABAIKAN EVENT MOUSE: Hanya tanggapi sentuhan JARI
                        if (downChange.type != PointerType.Touch) {
                            continue
                        }

                        if (downChange.pressed) {
                            downChange.consume()
                            onTouchStart()

                            if (isLocked) {
                                while (true) {
                                    val moveOrUp = awaitPointerEvent()
                                    val change = moveOrUp.changes.firstOrNull() ?: break
                                    if (change.type != PointerType.Touch) continue
                                    if (!change.pressed) {
                                        change.consume()
                                        onTouchEnd(false)
                                        onUnlock()
                                        break
                                    }
                                }
                            } else {
                                var reachedHold = false
                                isHolding = true
                                holdJob?.cancel()
                                holdJob = coroutineScope.launch {
                                    delay(HOLD_TIME_MS)
                                    reachedHold = true
                                    isHolding = false
                                    onLock()
                                }

                                while (true) {
                                    val moveOrUp = awaitPointerEvent()
                                    val change = moveOrUp.changes.firstOrNull() ?: break
                                    if (change.type != PointerType.Touch) continue
                                    if (!change.pressed) {
                                        change.consume()
                                        onTouchEnd(false)
                                        holdJob?.cancel()
                                        isHolding = false
                                        if (!reachedHold) {
                                            coroutineScope.launch {
                                                scaleAnim.animateTo(0.96f, tween(90))
                                                scaleAnim.animateTo(1f, tween(90))
                                            }
                                            onQuickClick()
                                        }
                                        break
                                    }
                                }
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (isHolding) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(3.dp)
                    .scale(holdRingScale.value)
                    .border(
                        2.dp,
                        ColorActiveGreen.copy(alpha = holdRingAlpha.value * 0.45f),
                        RoundedCornerShape(14.dp)
                    )
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .drawBehind {
                        if (isLocked) {
                            drawCircle(
                                color = ColorActiveGreen.copy(alpha = 0.4f),
                                radius = 8.dp.toPx()
                            )
                            drawCircle(
                                color = ColorActiveGreen,
                                radius = 4.dp.toPx()
                            )
                        } else {
                            drawCircle(
                                color = ColorDotInactive,
                                radius = 4.dp.toPx()
                            )
                        }
                    }
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = Color.White
            )
        }
    }
}

/**
 * Baris Touchpad & Scroll Strip:
 * Touchpad kiri (fleksibel) dan Scroll Strip kanan (20dp).
 */
@Composable
fun TouchRowSection(
    isLocked: Boolean,
    resetTouchpadTrigger: Int = 0,
    onTouchStart: () -> Unit = {},
    onTouchEnd: (isCancel: Boolean) -> Unit = {},
    onRelativeMove: (dx: Float, dy: Float) -> Unit,
    onTapClick: () -> Unit,
    onScroll: (notches: Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Touchpad Area
        TouchpadArea(
            modifier = Modifier
                .weight(1f)
                .height(TOUCHPAD_HEIGHT),
            isLocked = isLocked,
            resetTouchpadTrigger = resetTouchpadTrigger,
            onTouchStart = onTouchStart,
            onTouchEnd = onTouchEnd,
            onRelativeMove = onRelativeMove,
            onTapClick = onTapClick
        )

        // Scroll Strip
        ScrollStrip(
            modifier = Modifier
                .width(SCROLL_STRIP_WIDTH)
                .height(TOUCHPAD_HEIGHT),
            onTouchStart = onTouchStart,
            onTouchEnd = onTouchEnd,
            onScroll = onScroll
        )
    }
}

/**
 * Touchpad Relatif:
 * - Hanya menanggapi sentuhan JARI (PointerType.Touch), mengabaikan event mouse suntikan.
 * - Mendukung Mode TEMBUS dengan memanggil onTouchStart saat jari menyentuh dan onTouchEnd saat terangkat.
 * - Mereset status jika ACTION_CANCEL terdeteksi atau resetTouchpadTrigger berubah.
 * - Ketuk < 250ms & total geseran < 10dp memanggil onTapClick() (Klik Kiri).
 * - Dua ketukan dalam 300ms menjadi dua klik.
 * - Mengabaikan klik setelah gerakan menggeser.
 * - Mengabaikan ketukan saat mode seret (isLocked).
 */
@Composable
fun TouchpadArea(
    modifier: Modifier = Modifier,
    isLocked: Boolean = false,
    resetTouchpadTrigger: Int = 0,
    onTouchStart: () -> Unit = {},
    onTouchEnd: (isCancel: Boolean) -> Unit = {},
    onRelativeMove: (dx: Float, dy: Float) -> Unit,
    onTapClick: () -> Unit
) {
    val density = LocalDensity.current
    var isTouching by remember { mutableStateOf(false) }

    // Reset state saat ada sinyal reset / ACTION_CANCEL
    LaunchedEffect(resetTouchpadTrigger) {
        if (resetTouchpadTrigger > 0) {
            isTouching = false
        }
    }

    val bgColor = if (isTouching) Color(0x59000000) else Color(0x40000000)
    val borderColor = if (isTouching) Color(0x5539EFA1) else ColorBorderWhite

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(TOUCHPAD_RADIUS))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(TOUCHPAD_RADIUS))
            .pointerInput(isLocked, resetTouchpadTrigger) {
                awaitPointerEventScope {
                    var touchDownTime = 0L
                    var lastX = 0f
                    var lastY = 0f
                    var lastTime = 0L
                    var smoothSpeed = 0f
                    var totalDisplacement = 0f
                    var isDragMovement = false

                    val maxDistPx = with(density) { TAP_MAX_DISTANCE_DP.dp.toPx() }

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue

                        // 1. ABAIKAN EVENT MOUSE: Hanya tanggapi sentuhan JARI
                        if (change.type != PointerType.Touch) {
                            continue
                        }

                        if (change.pressed) {
                            change.consume()
                            if (!isTouching) {
                                isTouching = true
                                onTouchStart()
                                touchDownTime = change.uptimeMillis
                                lastX = change.position.x
                                lastY = change.position.y
                                lastTime = change.uptimeMillis
                                smoothSpeed = 0f
                                totalDisplacement = 0f
                                isDragMovement = false
                            } else {
                                val dx = change.position.x - lastX
                                val dy = change.position.y - lastY
                                val dt = (change.uptimeMillis - lastTime).coerceAtLeast(4L)
                                lastX = change.position.x
                                lastY = change.position.y
                                lastTime = change.uptimeMillis

                                val stepDist = hypot(dx, dy)
                                totalDisplacement += stepDist
                                if (totalDisplacement > maxDistPx || (change.uptimeMillis - touchDownTime) >= TAP_MAX_DURATION_MS) {
                                    isDragMovement = true
                                }

                                val rawSpeed = stepDist / dt.toFloat()
                                smoothSpeed = smoothSpeed * 0.6f + rawSpeed * 0.4f
                                val gain = (BASE_GAIN + smoothSpeed * ACCEL).coerceAtMost(MAX_GAIN)

                                onRelativeMove(dx * gain, dy * gain)
                            }
                        } else {
                            if (isTouching) {
                                isTouching = false
                                onTouchEnd(false)
                                val touchDuration = change.uptimeMillis - touchDownTime
                                // Syarat KETUK = KLIK KIRI:
                                // durasi < 250 ms, total geseran < 10dp, bukan menggeser, dan tidak sedang terkunci
                                if (!isDragMovement &&
                                    touchDuration < TAP_MAX_DURATION_MS &&
                                    totalDisplacement < maxDistPx &&
                                    !isLocked
                                ) {
                                    onTapClick()
                                }
                            }
                        }
                    }
                }
            }
            .testTag("touchpad_area"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "TOUCH PAD",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = ColorTextTouchpad
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "UNTUK MENGGERAKKAN KURSOR",
                fontSize = 9.sp,
                fontWeight = FontWeight.Normal,
                color = ColorTextSub
            )
        }
    }
}

/**
 * Strip SCROLL di sebelah kanan touchpad:
 * Garis-garis tick tiap 16dp bergerak mengikuti jari, panah ▲ ▼, teks SCROLL diputar -90 derajat.
 * Hanya merespons sentuhan JARI (PointerType.Touch).
 */
@Composable
fun ScrollStrip(
    modifier: Modifier = Modifier,
    onTouchStart: () -> Unit = {},
    onTouchEnd: (isCancel: Boolean) -> Unit = {},
    onScroll: (notches: Int) -> Unit
) {
    val density = LocalDensity.current
    var isTouching by remember { mutableStateOf(false) }
    var tickOffset by remember { mutableFloatStateOf(0f) }

    val bgColor = if (isTouching) Color(0x59000000) else Color(0x40000000)
    val borderColor = if (isTouching) Color(0x5539EFA1) else ColorBorderWhite
    val contentColor = if (isTouching) ColorActiveGreen else ColorTextSub
    val arrowColor = if (isTouching) ColorActiveGreen else ColorTextTouchpad

    val tickIntervalPx = with(density) { SCROLL_TICK_INTERVAL_DP.dp.toPx() }
    val scrollStepPx = with(density) { SCROLL_STEP_PX.dp.toPx() }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(SCROLL_STRIP_RADIUS))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(SCROLL_STRIP_RADIUS))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    var lastY = 0f
                    var scrollAcc = 0f

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue

                        // ABAIKAN EVENT MOUSE: Hanya tanggapi sentuhan JARI
                        if (change.type != PointerType.Touch) {
                            continue
                        }

                        if (change.pressed) {
                            change.consume()
                            if (!isTouching) {
                                isTouching = true
                                onTouchStart()
                                lastY = change.position.y
                                scrollAcc = 0f
                            } else {
                                val dy = change.position.y - lastY
                                lastY = change.position.y

                                tickOffset = (tickOffset + dy) % tickIntervalPx

                                scrollAcc += if (SCROLL_NATURAL) -dy else dy
                                val notches = (scrollAcc / scrollStepPx).toInt()
                                if (notches != 0) {
                                    scrollAcc -= notches * scrollStepPx
                                    onScroll(notches)
                                }
                            }
                        } else {
                            if (isTouching) {
                                isTouching = false
                                onTouchEnd(false)
                            }
                        }
                    }
                }
            }
            .drawBehind {
                val tickColor = Color(0x22FFFFFF)
                val strokeW = 1.5.dp.toPx()
                var y = tickOffset
                if (y > 0) y -= tickIntervalPx
                while (y < size.height) {
                    if (y >= 0) {
                        drawLine(
                            color = tickColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = strokeW
                        )
                    }
                    y += tickIntervalPx
                }
            }
            .testTag("scroll_strip")
    ) {
        Text(
            text = "▲",
            fontSize = 9.sp,
            color = arrowColor,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
        )

        Text(
            text = "SCROLL",
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            color = contentColor,
            softWrap = false,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.Center)
                .wrapContentSize(unbounded = true)
                .rotate(-90f)
        )

        Text(
            text = "▼",
            fontSize = 9.sp,
            color = arrowColor,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp)
        )
    }
}

/**
 * Jendela overlay terpisah untuk handle geser:
 * Garis tipis 120dp x 6dp, warna #ffffff44, jadi #39efa1 saat ditekan/digeser, tanpa latar kartu.
 * Hanya merespons sentuhan JARI (PointerType.Touch).
 */
@Composable
fun SeparateDragHandle(
    recordMode: RecordMode = RecordMode.OFF,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDrag: (dx: Float, dy: Float) -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(width = 140.dp, height = DRAG_HANDLE_TOUCH_HEIGHT)
            .alpha(if (recordMode == RecordMode.TRANSPARENT) 0f else 1f)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val downEvent = awaitPointerEvent()
                        val downChange = downEvent.changes.firstOrNull() ?: continue

                        // ABAIKAN EVENT MOUSE: Hanya tanggapi sentuhan JARI
                        if (downChange.type != PointerType.Touch) {
                            continue
                        }

                        if (downChange.pressed) {
                            downChange.consume()
                            isPressed = true
                            onDragStart()
                            var lastPos = downChange.position

                            while (true) {
                                val moveEvent = awaitPointerEvent()
                                val change = moveEvent.changes.firstOrNull() ?: break
                                if (change.type != PointerType.Touch) continue

                                if (change.pressed) {
                                    change.consume()
                                    val dx = change.position.x - lastPos.x
                                    val dy = change.position.y - lastPos.y
                                    lastPos = change.position
                                    if (dx != 0f || dy != 0f) {
                                        onDrag(dx, dy)
                                    }
                                } else {
                                    isPressed = false
                                    onDragEnd()
                                    break
                                }
                            }
                        }
                    }
                }
            }
            .testTag("separate_drag_handle"),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(DRAG_HANDLE_WIDTH)
                .height(DRAG_HANDLE_HEIGHT)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isPressed) ColorActiveGreen else ColorHandle)
        )
    }
}
