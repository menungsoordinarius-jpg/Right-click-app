package com.example.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.backend.BackendState
import com.example.backend.BackendStatus
import com.example.backend.BackendType

val BUBBLE_SIZE = 52.dp

/**
 * Bubble mengambang kecil yang selalu interaktif di atas jendela penangkap:
 * - Menyalakan/mematikan Mode Layar Sentuh vs Touchpad.
 * - Membuka menu & dialog pengaturan.
 * - Dapat digeser (drag).
 */
@Composable
fun FloatingBubble(
    backendState: BackendState,
    inputMode: InputMode,
    recordMode: RecordMode = RecordMode.OFF,
    onToggleInputMode: (InputMode) -> Unit,
    onOpenSettings: () -> Unit,
    onRecheckBackend: () -> Unit,
    onHideOverlay: () -> Unit,
    onDrag: (dx: Float, dy: Float) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val dotColor = when (backendState.status) {
        BackendStatus.READY -> ColorStatusGreen
        BackendStatus.CONNECTING, BackendStatus.PERMISSION_REQUIRED -> Color(0xFFFFD54F)
        else -> Color(0xFFFF5252)
    }

    Box(
        modifier = Modifier
            .size(BUBBLE_SIZE)
            .alpha(if (recordMode == RecordMode.TRANSPARENT) 0f else 1f)
            .shadow(12.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(CircleShape)
            .background(ColorBgDark)
            .background(ColorWhiteLayer)
            .border(1.5.dp, if (inputMode == InputMode.TOUCHSCREEN) ColorActiveBorder else ColorBorderWhite, CircleShape)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val downEvent = awaitPointerEvent()
                        val downChange = downEvent.changes.firstOrNull() ?: continue
                        if (downChange.type != PointerType.Touch) continue

                        if (downChange.pressed) {
                            downChange.consume()
                            var lastPos = downChange.position
                            var totalMoved = 0f
                            val downTime = downChange.uptimeMillis

                            while (true) {
                                val moveEvent = awaitPointerEvent()
                                val change = moveEvent.changes.firstOrNull() ?: break
                                if (change.type != PointerType.Touch) continue

                                if (change.pressed) {
                                    change.consume()
                                    val dx = change.position.x - lastPos.x
                                    val dy = change.position.y - lastPos.y
                                    lastPos = change.position
                                    totalMoved += kotlin.math.hypot(dx, dy)
                                    if (dx != 0f || dy != 0f) {
                                        onDrag(dx, dy)
                                    }
                                } else {
                                    val duration = change.uptimeMillis - downTime
                                    if (totalMoved < 15f && duration < 350L) {
                                        menuExpanded = true
                                    }
                                    break
                                }
                            }
                        }
                    }
                }
            }
            .testTag("floating_bubble"),
        contentAlignment = Alignment.Center
    ) {
        // Icon Mode Utama
        if (inputMode == InputMode.TOUCHSCREEN) {
            Icon(
                imageVector = Icons.Default.TouchApp,
                contentDescription = "Mode Layar Sentuh",
                tint = ColorActiveGreen,
                modifier = Modifier.size(26.dp)
            )
        } else {
            Icon(
                imageVector = Icons.Default.Mouse,
                contentDescription = "Mode Touchpad",
                tint = ColorTextTouchpad,
                modifier = Modifier.size(24.dp)
            )
        }

        // Status Dot Backend di pojok kanan atas
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(7.dp)
                .drawBehind {
                    drawCircle(color = dotColor.copy(alpha = 0.4f), radius = 5.dp.toPx())
                    drawCircle(color = dotColor, radius = 3.dp.toPx())
                }
        )

        // Dropdown Menu Bubble
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
                        if (inputMode == InputMode.TOUCHSCREEN) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = ColorActiveGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Mode: Layar Sentuh", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                },
                onClick = {
                    menuExpanded = false
                    onToggleInputMode(InputMode.TOUCHSCREEN)
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
                    onToggleInputMode(InputMode.TOUCHPAD)
                }
            )

            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = ColorActiveGreen, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pengaturan Gerakan & Kursor", color = Color.White, fontSize = 12.sp)
                    }
                },
                onClick = {
                    menuExpanded = false
                    onOpenSettings()
                }
            )

            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = ColorTextSystem, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cek Ulang Backend", color = Color.White, fontSize = 12.sp)
                    }
                },
                onClick = {
                    menuExpanded = false
                    onRecheckBackend()
                }
            )

            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sembunyikan Overlay", color = Color(0xFFFF5252), fontSize = 12.sp)
                    }
                },
                onClick = {
                    menuExpanded = false
                    onHideOverlay()
                }
            )
        }
    }
}
