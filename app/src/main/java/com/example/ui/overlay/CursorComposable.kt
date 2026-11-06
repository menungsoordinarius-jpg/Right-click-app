package com.example.ui.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Panah kursor virtual sesuai desain HTML:
 * Path: M22 10.2069L3 3L10.2069 22L13.4828 13.4828L22 10.2069Z
 * Ukuran: 28dp, ujung panah di titik (3.5dp, 3.5dp), glow #39efa1.
 */
@Composable
fun CursorCrosshair(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(28.dp)
    ) {
        Canvas(modifier = Modifier.size(28.dp)) {
            val scale = size.width / 24f
            val path = Path().apply {
                moveTo(22f * scale, 10.2069f * scale)
                lineTo(3f * scale, 3f * scale)
                lineTo(10.2069f * scale, 22f * scale)
                lineTo(13.4828f * scale, 13.4828f * scale)
                close()
            }

            // Glow hijau (#39efa1) berlapis di belakang kursor
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
