package com.example.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun SettingsDialog(
    initialSettings: TouchSettings,
    onDismissRequest: () -> Unit,
    onSaveSettings: (TouchSettings) -> Unit
) {
    var inputMode by remember { mutableStateOf(initialSettings.inputMode) }
    var longPressMs by remember { mutableFloatStateOf(initialSettings.longPressMs.toFloat()) }
    var doubleTapMs by remember { mutableFloatStateOf(initialSettings.doubleTapMs.toFloat()) }
    var slopDp by remember { mutableFloatStateOf(initialSettings.slopDp) }
    var twoFingerTap by remember { mutableStateOf(initialSettings.twoFingerTapRightClick) }
    var twoFingerNatural by remember { mutableStateOf(initialSettings.twoFingerScrollNatural) }
    var baseGain by remember { mutableFloatStateOf(initialSettings.baseGain) }
    var accel by remember { mutableFloatStateOf(initialSettings.accel) }
    var maxGain by remember { mutableFloatStateOf(initialSettings.maxGain) }

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, ColorBorderWhite, RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF14161C)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pengaturan Input & Gerakan",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = ColorTextSystem
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. Pemilihan Mode Input
                    Column {
                        Text(
                            text = "MODE INPUT",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorActiveGreen,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1E212B), RoundedCornerShape(10.dp))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val isTouchscreen = inputMode == InputMode.TOUCHSCREEN
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isTouchscreen) ColorActiveGreen else Color.Transparent)
                                    .clickable { inputMode = InputMode.TOUCHSCREEN }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.TouchApp,
                                        contentDescription = null,
                                        tint = if (isTouchscreen) Color.Black else ColorTextTouchpad,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Layar Sentuh (Bawaan)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isTouchscreen) Color.Black else ColorTextTouchpad
                                    )
                                }
                            }

                            val isTouchpad = inputMode == InputMode.TOUCHPAD
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isTouchpad) ColorActiveGreen else Color.Transparent)
                                    .clickable { inputMode = InputMode.TOUCHPAD }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Mouse,
                                        contentDescription = null,
                                        tint = if (isTouchpad) Color.Black else ColorTextTouchpad,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Touchpad Virtual",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isTouchpad) Color.Black else ColorTextTouchpad
                                    )
                                }
                            }
                        }
                    }

                    // 2. Pengaturan Gerakan Layar Sentuh
                    Column {
                        Text(
                            text = "GERAKAN LAYAR SENTUH",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorActiveGreen,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        // Long Press Slider
                        Text(
                            text = "Durasi Tahan (Long Press): ${longPressMs.toInt()} ms",
                            fontSize = 12.sp,
                            color = Color.White
                        )
                        Slider(
                            value = longPressMs,
                            onValueChange = { longPressMs = it },
                            valueRange = 200f..700f,
                            steps = 9,
                            colors = SliderDefaults.colors(
                                thumbColor = ColorActiveGreen,
                                activeTrackColor = ColorActiveGreen
                            )
                        )

                        // Double Tap Window Slider
                        Text(
                            text = "Jeda Ketuk Dua Kali (Double Tap): ${doubleTapMs.toInt()} ms",
                            fontSize = 12.sp,
                            color = Color.White
                        )
                        Slider(
                            value = doubleTapMs,
                            onValueChange = { doubleTapMs = it },
                            valueRange = 150f..600f,
                            steps = 8,
                            colors = SliderDefaults.colors(
                                thumbColor = ColorActiveGreen,
                                activeTrackColor = ColorActiveGreen
                            )
                        )

                        // Slop Distance Slider
                        Text(
                            text = "Toleransi Geser (Slop): ${slopDp.toInt()} dp",
                            fontSize = 12.sp,
                            color = Color.White
                        )
                        Slider(
                            value = slopDp,
                            onValueChange = { slopDp = it },
                            valueRange = 4f..25f,
                            steps = 20,
                            colors = SliderDefaults.colors(
                                thumbColor = ColorActiveGreen,
                                activeTrackColor = ColorActiveGreen
                            )
                        )

                        // Dua Jari Ketuk
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Ketuk 2 Jari = Klik Kanan",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                                Text(
                                    text = "Dua jari menyentuh dan lepas bersamaan",
                                    fontSize = 10.sp,
                                    color = ColorTextSub
                                )
                            }
                            Switch(
                                checked = twoFingerTap,
                                onCheckedChange = { twoFingerTap = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = ColorActiveGreen,
                                    checkedTrackColor = ColorActiveBorder
                                )
                            )
                        }

                        // Dua Jari Scroll Natural
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Scroll 2 Jari Arah Natural",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                                Text(
                                    text = "Seret ke atas menggeser konten ke atas",
                                    fontSize = 10.sp,
                                    color = ColorTextSub
                                )
                            }
                            Switch(
                                checked = twoFingerNatural,
                                onCheckedChange = { twoFingerNatural = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = ColorActiveGreen,
                                    checkedTrackColor = ColorActiveBorder
                                )
                            )
                        }
                    }

                    // 3. Sensitivitas & Akselerasi Kursor
                    Column {
                        Text(
                            text = "SENSITIVITAS & AKSELERASI POINTER",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorActiveGreen,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Kecepatan Dasar (Base Gain): ${"%.1f".format(baseGain)}x",
                            fontSize = 12.sp,
                            color = Color.White
                        )
                        Slider(
                            value = baseGain,
                            onValueChange = { baseGain = it },
                            valueRange = 0.5f..2.5f,
                            steps = 19,
                            colors = SliderDefaults.colors(
                                thumbColor = ColorActiveGreen,
                                activeTrackColor = ColorActiveGreen
                            )
                        )

                        Text(
                            text = "Akselerasi Kursor: ${"%.1f".format(accel)}x",
                            fontSize = 12.sp,
                            color = Color.White
                        )
                        Slider(
                            value = accel,
                            onValueChange = { accel = it },
                            valueRange = 1.0f..6.0f,
                            steps = 24,
                            colors = SliderDefaults.colors(
                                thumbColor = ColorActiveGreen,
                                activeTrackColor = ColorActiveGreen
                            )
                        )

                        Text(
                            text = "Batas Kecepatan Maksimal: ${maxGain.toInt()}x",
                            fontSize = 12.sp,
                            color = Color.White
                        )
                        Slider(
                            value = maxGain,
                            onValueChange = { maxGain = it },
                            valueRange = 2f..15f,
                            steps = 12,
                            colors = SliderDefaults.colors(
                                thumbColor = ColorActiveGreen,
                                activeTrackColor = ColorActiveGreen
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            inputMode = InputMode.TOUCHSCREEN
                            longPressMs = 350f
                            doubleTapMs = 300f
                            slopDp = 10f
                            twoFingerTap = true
                            twoFingerNatural = true
                            baseGain = 1.0f
                            accel = 3.0f
                            maxGain = 8.0f
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            val updated = TouchSettings(
                                inputMode = inputMode,
                                longPressMs = longPressMs.toLong(),
                                doubleTapMs = doubleTapMs.toLong(),
                                tapMaxMs = 250L,
                                slopDp = slopDp,
                                twoFingerTapRightClick = twoFingerTap,
                                twoFingerScrollNatural = twoFingerNatural,
                                baseGain = baseGain,
                                accel = accel,
                                maxGain = maxGain
                            )
                            onSaveSettings(updated)
                            onDismissRequest()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ColorActiveGreen,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Simpan", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
