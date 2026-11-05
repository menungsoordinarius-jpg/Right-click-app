package com.example.ui.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.ui.theme.NeonCyan

/**
 * High-visibility precision crosshair cursor for mouse event injection.
 * Rendered inside a transparent, non-touchable overlay window.
 */
@Composable
fun CursorCrosshair(
    modifier: Modifier = Modifier,
    cursorColor: Color = NeonCyan
) {
    Box(
        modifier = modifier.size(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(48.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerRadius = 14.dp.toPx()
            val innerGap = 5.dp.toPx()
            val armLength = 18.dp.toPx()

            // Outer subtle circle
            drawCircle(
                color = cursorColor.copy(alpha = 0.35f),
                radius = outerRadius,
                center = center,
                style = Stroke(width = 3.dp.toPx())
            )
            drawCircle(
                color = cursorColor,
                radius = outerRadius,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )

            // Crosshair lines
            val strokeWidth = 2.dp.toPx()

            // Left arm
            drawLine(
                color = cursorColor,
                start = Offset(center.x - armLength, center.y),
                end = Offset(center.x - innerGap, center.y),
                strokeWidth = strokeWidth
            )
            // Right arm
            drawLine(
                color = cursorColor,
                start = Offset(center.x + innerGap, center.y),
                end = Offset(center.x + armLength, center.y),
                strokeWidth = strokeWidth
            )
            // Top arm
            drawLine(
                color = cursorColor,
                start = Offset(center.x, center.y - armLength),
                end = Offset(center.x, center.y - innerGap),
                strokeWidth = strokeWidth
            )
            // Bottom arm
            drawLine(
                color = cursorColor,
                start = Offset(center.x, center.y + innerGap),
                end = Offset(center.x, center.y + armLength),
                strokeWidth = strokeWidth
            )

            // Center target dot
            drawCircle(
                color = Color.White,
                radius = 2.5.dp.toPx(),
                center = center
            )
            drawCircle(
                color = cursorColor,
                radius = 1.5.dp.toPx(),
                center = center
            )
        }
    }
}
