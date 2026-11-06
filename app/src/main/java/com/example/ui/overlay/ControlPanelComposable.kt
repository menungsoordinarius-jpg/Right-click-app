package com.example.ui.overlay

import android.view.MotionEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
val DRAG_HANDLE_TOP_MARGIN = 18.dp
val DRAG_HANDLE_TOUCH_HEIGHT = 32.dp

// Touchpad Gain Constants
const val BASE_GAIN = 0.8f
const val ACCEL = 2.5f
const val MAX_GAIN = 8.0f
const val HOLD_TIME_MS = 600L

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
 * Komponen utama panel kontrol RightClickDash (Virtual Touch Pad).
 */
@Composable
fun ControlPanel(
    backendState: BackendState,
    cursorX: Float,
    cursorY: Float,
    heldButton: Int = 0,
    onSwitchBackend: (BackendType) -> Unit,
    onRequestPermission: () -> Unit,
    onRecheckBackend: () -> Unit,
    onClickLeft: () -> Unit,
    onClickRight: () -> Unit,
    onLockLeft: () -> Unit,
    onLockRight: () -> Unit,
    onUnlock: () -> Unit,
    onRelativeMove: (dx: Float, dy: Float) -> Unit,
    onScroll: (notches: Int) -> Unit,
    onDragPanel: (dx: Float, dy: Float) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
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
            // 1. HEADER (Status dot + Teks + Menu dropdown)
            HeaderSection(
                backendState = backendState,
                onSwitchBackend = onSwitchBackend,
                onRequestPermission = onRequestPermission,
                onRecheckBackend = onRecheckBackend
            )

            Spacer(modifier = Modifier.height(15.dp))

            // 2. TOMBOL BESAR: CLICK LEFT & CLICK RIGHT
            ControlsSection(
                heldButton = heldButton,
                onClickLeft = onClickLeft,
                onClickRight = onClickRight,
                onLockLeft = onLockLeft,
                onLockRight = onLockRight,
                onUnlock = onUnlock
            )

            Spacer(modifier = Modifier.height(15.dp))

            // 3. TOUCHPAD RELATIF & 4. SCROLL STRIP
            TouchRowSection(
                onRelativeMove = onRelativeMove,
                onScroll = onScroll
            )

            // 5. DRAG HANDLE BAWAH (untuk menggeser seluruh panel)
            DragHandleBottom(
                onDragPanel = onDragPanel
            )
        }
    }
}

/**
 * Header: Titik status 7dp dengan glow + teks 10sp.
 * Ketuk header untuk membuka menu pilihan backend & cek ulang.
 */
@Composable
fun HeaderSection(
    backendState: BackendState,
    onSwitchBackend: (BackendType) -> Unit,
    onRequestPermission: () -> Unit,
    onRecheckBackend: () -> Unit
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
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { menuExpanded = true }
                .padding(vertical = 4.dp, horizontal = 2.dp)
                .testTag("header_status"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Titik 7dp dengan glow
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .drawBehind {
                        // Glow shadow
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

        // Dropdown Menu pemilihan backend & cek ulang
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
        }
    }
}

/**
 * Dua tombol besar: CLICK LEFT dan CLICK RIGHT.
 * Ketuk cepat = klik biasa + scale 0.96 (180ms).
 * Tahan 600ms = Kunci tombol (active border & green indicator). Ketuk lagi untuk melepas.
 */
@Composable
fun ControlsSection(
    heldButton: Int,
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
            onQuickClick = onClickLeft,
            onLock = onLockLeft,
            onUnlock = onUnlock
        )

        // Tombol CLICK RIGHT
        ControlButton(
            modifier = Modifier.weight(1f),
            label = "CLICK RIGHT",
            isLocked = isRightLocked,
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
    onQuickClick: () -> Unit,
    onLock: () -> Unit,
    onUnlock: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var isHolding by remember { mutableStateOf(false) }
    var holdJob by remember { mutableStateOf<Job?>(null) }
    val scaleAnim = remember { Animatable(1f) }

    // Efek animasi ring holding saat tombol ditekan selama 600ms
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
                        if (downChange.pressed) {
                            downChange.consume()

                            if (isLocked) {
                                // Sudah terkunci -> ketuk lagi untuk melepas
                                while (true) {
                                    val moveOrUp = awaitPointerEvent()
                                    val change = moveOrUp.changes.firstOrNull() ?: break
                                    if (!change.pressed) {
                                        change.consume()
                                        onUnlock()
                                        break
                                    }
                                }
                            } else {
                                // Belum terkunci -> mulai timer tahan 600ms
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
                                    if (!change.pressed) {
                                        change.consume()
                                        holdJob?.cancel()
                                        isHolding = false
                                        if (!reachedHold) {
                                            // Ketuk cepat: animasi skala 0.96 (180ms) + panggil klik
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
        // Hold ring glow indicator saat proses menahan 600ms
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
            // Indikator titik 8dp
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .drawBehind {
                        if (isLocked) {
                            // Glow hijau menyala
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

            // Label teks 11sp bold letterSpacing 1.5sp
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
    onRelativeMove: (dx: Float, dy: Float) -> Unit,
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
            onRelativeMove = onRelativeMove
        )

        // Scroll Strip
        ScrollStrip(
            modifier = Modifier
                .width(SCROLL_STRIP_WIDTH)
                .height(TOUCHPAD_HEIGHT),
            onScroll = onScroll
        )
    }
}

/**
 * Touchpad Relatif:
 * Menghitung akselerasi persis seperti rumus JavaScript di HTML:
 * smoothSpeed = smoothSpeed * 0.6 + (hypot(dx, dy) / dt) * 0.4
 * gain = min(BASE_GAIN + smoothSpeed * ACCEL, MAX_GAIN)
 */
@Composable
fun TouchpadArea(
    modifier: Modifier = Modifier,
    onRelativeMove: (dx: Float, dy: Float) -> Unit
) {
    var isTouching by remember { mutableStateOf(false) }

    val bgColor = if (isTouching) Color(0x59000000) else Color(0x40000000)
    val borderColor = if (isTouching) Color(0x5539EFA1) else ColorBorderWhite

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(TOUCHPAD_RADIUS))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(TOUCHPAD_RADIUS))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    var lastX = 0f
                    var lastY = 0f
                    var lastTime = 0L
                    var smoothSpeed = 0f

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue

                        if (change.pressed) {
                            change.consume()
                            if (!isTouching) {
                                isTouching = true
                                lastX = change.position.x
                                lastY = change.position.y
                                lastTime = change.uptimeMillis
                                smoothSpeed = 0f
                            } else {
                                val dx = change.position.x - lastX
                                val dy = change.position.y - lastY
                                val dt = (change.uptimeMillis - lastTime).coerceAtLeast(4L)
                                lastX = change.position.x
                                lastY = change.position.y
                                lastTime = change.uptimeMillis

                                val rawSpeed = hypot(dx, dy) / dt.toFloat()
                                smoothSpeed = smoothSpeed * 0.6f + rawSpeed * 0.4f
                                val gain = (BASE_GAIN + smoothSpeed * ACCEL).coerceAtMost(MAX_GAIN)

                                onRelativeMove(dx * gain, dy * gain)
                            }
                        } else {
                            isTouching = false
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
 * Satu notch tiap 24dp geseran, mode natural.
 */
@Composable
fun ScrollStrip(
    modifier: Modifier = Modifier,
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

                        if (change.pressed) {
                            change.consume()
                            if (!isTouching) {
                                isTouching = true
                                lastY = change.position.y
                                scrollAcc = 0f
                            } else {
                                val dy = change.position.y - lastY
                                lastY = change.position.y

                                // Update garis-garis tick
                                tickOffset = (tickOffset + dy) % tickIntervalPx

                                // Akumulasi scroll (natural mode)
                                scrollAcc += if (SCROLL_NATURAL) -dy else dy
                                val notches = (scrollAcc / scrollStepPx).toInt()
                                if (notches != 0) {
                                    scrollAcc -= notches * scrollStepPx
                                    onScroll(notches)
                                }
                            }
                        } else {
                            isTouching = false
                        }
                    }
                }
            }
            .drawBehind {
                // Gambar garis-garis tick tiap 16dp
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
        // Panah Atas ▲ (10dp dari atas)
        Text(
            text = "▲",
            fontSize = 9.sp,
            color = arrowColor,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
        )

        // Teks SCROLL diputar -90 derajat di tengah
        Text(
            text = "SCROLL",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            color = contentColor,
            modifier = Modifier
                .align(Alignment.Center)
                .rotate(-90f)
        )

        // Panah Bawah ▼ (10dp dari bawah)
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
 * Handle geser di bawah (120dp x 6dp, radius 10dp, margin atas 18dp).
 * Area sentuh vertikal diperbesar (32dp) agar nyaman digeser.
 */
@Composable
fun DragHandleBottom(
    onDragPanel: (dx: Float, dy: Float) -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = DRAG_HANDLE_TOP_MARGIN)
            .height(DRAG_HANDLE_TOUCH_HEIGHT)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDragPanel(dragAmount.x, dragAmount.y)
                    }
                )
            }
            .testTag("drag_handle_area"),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(DRAG_HANDLE_WIDTH)
                .height(DRAG_HANDLE_HEIGHT)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isDragging) ColorActiveGreen else ColorHandle)
        )
    }
}
