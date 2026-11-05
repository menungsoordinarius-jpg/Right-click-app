package com.example.ui.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.AdsClick
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.backend.BackendState
import com.example.backend.BackendStatus
import com.example.backend.BackendType
import com.example.ui.theme.AccentPurple
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.ui.theme.StatusYellow
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

@Composable
fun ControlPanel(
    backendState: BackendState,
    cursorX: Float,
    cursorY: Float,
    holdDurationMs: Long,
    isCollapsed: Boolean,
    isTapToPlaceActive: Boolean,
    onHoldDurationChange: (Long) -> Unit,
    onSwitchBackend: (BackendType) -> Unit,
    onRequestPermission: () -> Unit,
    onClickLeft: () -> Unit,
    onClickRight: () -> Unit,
    onHoldRight: () -> Unit,
    onRelativeMove: (dx: Float, dy: Float) -> Unit,
    onCenterCursor: () -> Unit,
    onToggleTapToPlace: () -> Unit,
    onToggleCollapsed: () -> Unit,
    onCloseService: () -> Unit,
    onDragPanel: (dx: Float, dy: Float) -> Unit,
    onRecheckBackend: () -> Unit = {}
) {
    if (isCollapsed) {
        CollapsedBubble(
            backendState = backendState,
            onClick = onToggleCollapsed,
            onLongClick = onClickRight,
            onDrag = onDragPanel
        )
    } else {
        ExpandedPanel(
            backendState = backendState,
            cursorX = cursorX,
            cursorY = cursorY,
            holdDurationMs = holdDurationMs,
            isTapToPlaceActive = isTapToPlaceActive,
            onHoldDurationChange = onHoldDurationChange,
            onSwitchBackend = onSwitchBackend,
            onRequestPermission = onRequestPermission,
            onClickLeft = onClickLeft,
            onClickRight = onClickRight,
            onHoldRight = onHoldRight,
            onRelativeMove = onRelativeMove,
            onCenterCursor = onCenterCursor,
            onToggleTapToPlace = onToggleTapToPlace,
            onCollapse = onToggleCollapsed,
            onClose = onCloseService,
            onDrag = onDragPanel,
            onRecheckBackend = onRecheckBackend
        )
    }
}

@Composable
fun CollapsedBubble(
    backendState: BackendState,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDrag: (dx: Float, dy: Float) -> Unit
) {
    val statusColor = when (backendState.status) {
        BackendStatus.READY -> StatusGreen
        BackendStatus.CONNECTING, BackendStatus.PERMISSION_REQUIRED -> StatusYellow
        else -> StatusRed
    }

    Box(
        modifier = Modifier
            .size(60.dp)
            .shadow(12.dp, CircleShape)
            .clip(CircleShape)
            .background(DarkSurface)
            .border(2.dp, NeonCyan.copy(alpha = 0.8f), CircleShape)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() }
                )
            }
            .testTag("bubble_toggle"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Mouse,
            contentDescription = "Buka Panel",
            tint = NeonCyan,
            modifier = Modifier.size(28.dp)
        )

        // Status indicator dot
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(10.dp)
                .background(statusColor, CircleShape)
                .border(1.5.dp, DarkSurface, CircleShape)
        )
    }
}

@Composable
fun ExpandedPanel(
    backendState: BackendState,
    cursorX: Float,
    cursorY: Float,
    holdDurationMs: Long,
    isTapToPlaceActive: Boolean,
    onHoldDurationChange: (Long) -> Unit,
    onSwitchBackend: (BackendType) -> Unit,
    onRequestPermission: () -> Unit,
    onClickLeft: () -> Unit,
    onClickRight: () -> Unit,
    onHoldRight: () -> Unit,
    onRelativeMove: (dx: Float, dy: Float) -> Unit,
    onCenterCursor: () -> Unit,
    onToggleTapToPlace: () -> Unit,
    onCollapse: () -> Unit,
    onClose: () -> Unit,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onRecheckBackend: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .width(285.dp)
            .shadow(16.dp, RoundedCornerShape(20.dp))
            .border(1.dp, DarkCardBorder, RoundedCornerShape(20.dp))
            .testTag("control_panel_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Bar (Draggable)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkSurfaceVariant.copy(alpha = 0.6f))
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        }
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Geser Panel",
                    tint = TextTertiary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "RightClickDash",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier.size(28.dp).testTag("collapse_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.UnfoldLess,
                        contentDescription = "Ciutkan",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(28.dp).testTag("close_service_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup",
                        tint = StatusRed,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Backend Selector & Status
            BackendSelectorCard(
                backendState = backendState,
                onSwitchBackend = onSwitchBackend,
                onRequestPermission = onRequestPermission,
                onRecheckBackend = onRecheckBackend
            )

            // Coordinate Info & Quick Action
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurfaceVariant, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "POSISI KURSOR",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextTertiary,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "X: ${cursorX.toInt()}  Y: ${cursorY.toInt()}",
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onCenterCursor,
                        modifier = Modifier
                            .size(32.dp)
                            .background(DarkCardBorder, CircleShape)
                            .testTag("center_cursor_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "Tengah Layar",
                            tint = NeonCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onToggleTapToPlace,
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                if (isTapToPlaceActive) NeonCyan else DarkCardBorder,
                                CircleShape
                            )
                            .testTag("tap_mode_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = "Mode Tap",
                            tint = if (isTapToPlaceActive) Color.Black else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Primary Mouse Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Klik Kiri
                Button(
                    onClick = onClickLeft,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("btn_click_left"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DarkSurfaceVariant,
                        contentColor = TextPrimary
                    )
                ) {
                    Text(
                        text = "Klik Kiri",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }

                // Klik Kanan (Primary focus)
                Button(
                    onClick = onClickRight,
                    modifier = Modifier
                        .weight(1.3f)
                        .height(44.dp)
                        .testTag("btn_click_right"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.AdsClick,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Klik Kanan",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            // Tombol Klik Kanan Tahan
            Button(
                onClick = onHoldRight,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .testTag("btn_hold_right"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentPurple,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Default.RadioButtonChecked,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Klik Kanan Tahan (${holdDurationMs} ms)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            // Slider Durasi Tahan (300 ms - 3000 ms, default 800 ms)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurfaceVariant, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Durasi Tahan",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                    Text(
                        text = "${holdDurationMs} ms",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan
                    )
                }

                Slider(
                    value = holdDurationMs.toFloat(),
                    onValueChange = { onHoldDurationChange(it.toLong()) },
                    valueRange = 300f..3000f,
                    steps = 26,
                    colors = SliderDefaults.colors(
                        thumbColor = NeonCyan,
                        activeTrackColor = NeonCyan,
                        inactiveTrackColor = DarkCardBorder
                    ),
                    modifier = Modifier.height(28.dp).testTag("hold_slider")
                )
            }

            // Mini Touchpad (Relative Drag Area) & Micro Nudge
            TouchpadSection(
                onRelativeMove = onRelativeMove
            )
        }
    }
}

@Composable
fun BackendSelectorCard(
    backendState: BackendState,
    onSwitchBackend: (BackendType) -> Unit,
    onRequestPermission: () -> Unit,
    onRecheckBackend: () -> Unit = {}
) {
    val statusColor = when (backendState.status) {
        BackendStatus.READY -> StatusGreen
        BackendStatus.CONNECTING, BackendStatus.PERMISSION_REQUIRED -> StatusYellow
        else -> StatusRed
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurfaceVariant, RoundedCornerShape(12.dp))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Shizuku tab
            val isShizuku = backendState.type == BackendType.SHIZUKU
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isShizuku) NeonCyan.copy(alpha = 0.2f) else DarkBg)
                    .border(
                        1.dp,
                        if (isShizuku) NeonCyan else Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { onSwitchBackend(BackendType.SHIZUKU) }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Shizuku",
                    fontSize = 12.sp,
                    fontWeight = if (isShizuku) FontWeight.Bold else FontWeight.Normal,
                    color = if (isShizuku) NeonCyan else TextSecondary
                )
            }

            // Root tab
            val isRoot = backendState.type == BackendType.ROOT
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isRoot) NeonCyan.copy(alpha = 0.2f) else DarkBg)
                    .border(
                        1.dp,
                        if (isRoot) NeonCyan else Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { onSwitchBackend(BackendType.ROOT) }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Root",
                    fontSize = 12.sp,
                    fontWeight = if (isRoot) FontWeight.Bold else FontWeight.Normal,
                    color = if (isRoot) NeonCyan else TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Status row with recheck and action
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .padding(vertical = 2.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(statusColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = backendState.message,
                fontSize = 11.sp,
                color = if (backendState.status == BackendStatus.READY) TextSecondary else statusColor,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )

            if (backendState.status == BackendStatus.PERMISSION_REQUIRED) {
                Text(
                    text = "Minta Izin",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onRequestPermission() }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }

            IconButton(
                onClick = onRecheckBackend,
                modifier = Modifier.size(24.dp).testTag("panel_recheck_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Cek Ulang",
                    tint = TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun TouchpadSection(
    onRelativeMove: (dx: Float, dy: Float) -> Unit
) {
    var touchIndicator by remember { mutableStateOf<Offset?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurfaceVariant, RoundedCornerShape(12.dp))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TOUCHPAD RELATIF",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = TextTertiary,
                letterSpacing = 0.5.sp
            )
            Text(
                text = "Geser untuk arahkan",
                fontSize = 9.sp,
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Touchpad surface
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DarkBg)
                .border(1.dp, DarkCardBorder, RoundedCornerShape(8.dp))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            touchIndicator = offset
                        },
                        onDragEnd = {
                            touchIndicator = null
                        },
                        onDragCancel = {
                            touchIndicator = null
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            touchIndicator = change.position
                            // Scale factor 1.8x for responsive cursor displacement
                            onRelativeMove(dragAmount.x * 1.8f, dragAmount.y * 1.8f)
                        }
                    )
                }
                .testTag("relative_touchpad"),
            contentAlignment = Alignment.Center
        ) {
            // Subtle cross guide
            Text(
                text = "+",
                fontSize = 20.sp,
                color = TextTertiary.copy(alpha = 0.3f),
                fontFamily = FontFamily.Monospace
            )

            // Touch point feedback ring
            touchIndicator?.let { pos ->
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(NeonCyan.copy(alpha = 0.25f), CircleShape)
                        .border(1.dp, NeonCyan, CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Fine Micro Nudge buttons (5px each tap)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { onRelativeMove(-5f, 0f) },
                modifier = Modifier.size(28.dp).background(DarkBg, CircleShape)
            ) {
                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Kiri", tint = TextSecondary, modifier = Modifier.size(16.dp))
            }
            IconButton(
                onClick = { onRelativeMove(0f, -5f) },
                modifier = Modifier.size(28.dp).background(DarkBg, CircleShape)
            ) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Atas", tint = TextSecondary, modifier = Modifier.size(16.dp))
            }
            IconButton(
                onClick = { onRelativeMove(0f, 5f) },
                modifier = Modifier.size(28.dp).background(DarkBg, CircleShape)
            ) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Bawah", tint = TextSecondary, modifier = Modifier.size(16.dp))
            }
            IconButton(
                onClick = { onRelativeMove(5f, 0f) },
                modifier = Modifier.size(28.dp).background(DarkBg, CircleShape)
            ) {
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Kanan", tint = TextSecondary, modifier = Modifier.size(16.dp))
            }
        }
    }
}
