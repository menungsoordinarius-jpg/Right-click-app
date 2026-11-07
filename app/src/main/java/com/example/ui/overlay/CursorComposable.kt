package com.example.ui.overlay

import android.view.MotionEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

// Ukuran jendela kursor dan offset ujung kursor (hotspot)
val CURSOR_WINDOW_SIZE = 44.dp
val CURSOR_TIP_OFFSET = 14.dp

/**
 * Panah kursor virtual sesuai desain HTML:
 * Path: M22 10.2069L3 3L10.2069 22L13.4828 13.4828L22 10.2069Z
 * Ujung panah di titik (CURSOR_TIP_OFFSET, CURSOR_TIP_OFFSET).
 *
 * Umpan Balik:
 * - Cincin biru saat klik kiri tertahan (BUTTON_PRIMARY).
 * - Cincin merah saat klik kanan tertahan (BUTTON_SECONDARY).
 */
@Composable
fun CursorCrosshair(
    modifier: Modifier = Modifier,
    heldButton: Int = 0
) {
    val ringPulse = remember { Animatable(1f) }

    LaunchedEffect(heldButton) {
        if (heldButton != 0) {
            ringPulse.snapTo(0.85f)
            ringPulse.animateTo(
                targetValue = 1.25f,
                animationSpec = infiniteRepeatable(
                    animation = tween(450, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
        } else {
            ringPulse.snapTo(1f)
        }
    }

    Box(
        modifier = modifier.size(CURSOR_WINDOW_SIZE)
    ) {
        Canvas(modifier = Modifier.size(CURSOR_WINDOW_SIZE)) {
            val tipX = CURSOR_TIP_OFFSET.toPx()
            val tipY = CURSOR_TIP_OFFSET.toPx()

            // 1. Gambar cincin indikator saat tombol tertahan
            if (heldButton != 0) {
                val ringColor = if (heldButton == MotionEvent.BUTTON_PRIMARY) {
                    Color(0xFF2979FF) // Biru saat tombol kiri tertahan
                } else {
                    Color(0xFFFF1744) // Merah saat tombol kanan tertahan
                }

                val currentPulse = ringPulse.value
                val baseRadius = 13.dp.toPx() * currentPulse

                // Glow cincin terluar
                drawCircle(
                    color = ringColor.copy(alpha = 0.25f),
                    radius = baseRadius + 3.dp.toPx(),
                    center = Offset(tipX, tipY),
                    style = Stroke(width = 4.dp.toPx())
                )

                // Garis cincin utama
                drawCircle(
                    color = ringColor.copy(alpha = 0.90f),
                    radius = baseRadius,
                    center = Offset(tipX, tipY),
                    style = Stroke(width = 2.dp.toPx())
                )

                // Titik pusat tip
                drawCircle(
                    color = ringColor,
                    radius = 2.5.dp.toPx(),
                    center = Offset(tipX, tipY)
                )
            }

            // 2. Gambar panah kursor
            val scale = 24.dp.toPx() / 24f
            val path = Path().apply {
                // Offset path agar titik (3, 3) pas berada di (tipX, tipY)
                val offsetX = tipX - (3f * scale)
                val offsetY = tipY - (3f * scale)

                moveTo(22f * scale + offsetX, 10.2069f * scale + offsetY)
                lineTo(3f * scale + offsetX, 3f * scale + offsetY)
                lineTo(10.2069f * scale + offsetX, 22f * scale + offsetY)
                lineTo(13.4828f * scale + offsetX, 13.4828f * scale + offsetY)
                close()
            }

            // Glow hijau (#39efa1) di belakang kursor
            val glowColor = Color(0xFF39EFA1)
            drawPath(
                path = path,
                color = glowColor.copy(alpha = 0.25f),
                style = Stroke(width = 6.dp.toPx())
            )
            drawPath(
                path = path,
                color = glowColor.copy(alpha = 0.45f),
                style = Stroke(width = 3.dp.toPx())
            )
            drawPath(
                path = path,
                color = glowColor.copy(alpha = 0.70f),
                style = Stroke(width = 1.5.dp.toPx())
            )

            // Isi panah: putih
            drawPath(
                path = path,
                color = Color.White
            )

            // Garis tepi panah: hitam
            drawPath(
                path = path,
                color = Color.Black,
                style = Stroke(
                    width = 0.8.dp.toPx(),
                    miter = 4f
                )
            )
        }
    }
}
