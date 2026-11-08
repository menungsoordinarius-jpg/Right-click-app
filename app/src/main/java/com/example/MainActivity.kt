package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdsClick
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.backend.BackendState
import com.example.backend.BackendStatus
import com.example.backend.BackendType
import com.example.backend.InputBackendManager
import com.example.service.OverlayService
import com.example.ui.overlay.InputMode
import com.example.ui.overlay.SettingsDialog
import com.example.ui.overlay.TouchSettings
import com.example.ui.theme.AccentPurple
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.RightClickDashTheme
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.ui.theme.StatusYellow
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            RightClickDashTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val backendManager = remember { InputBackendManager.getInstance(context) }
    val backendState by backendManager.backendState.collectAsState()

    var hasOverlayPermission by remember { mutableStateOf(checkOverlayPermission(context)) }
    var hasNotificationPermission by remember { mutableStateOf(checkNotificationPermission(context)) }
    var isServiceRunning by remember { mutableStateOf(OverlayService.isRunning) }
    var touchSettings by remember { mutableStateOf(TouchSettings.load(context)) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    // Re-check permissions on resume
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = checkOverlayPermission(context)
                hasNotificationPermission = checkNotificationPermission(context)
                isServiceRunning = OverlayService.isRunning
                backendManager.recheckStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val overlayLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasOverlayPermission = checkOverlayPermission(context)
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(NeonCyan.copy(alpha = 0.2f))
                                .border(1.dp, NeonCyan, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mouse,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "RightClickDash",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBg,
                    titleContentColor = TextPrimary
                )
            )
        },
        containerColor = DarkBg
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Hero card banner
            item {
                HeroBanner(isServiceRunning = isServiceRunning)
            }

            // Onboarding Setup Steps
            item {
                Text(
                    text = "LANGKAH PERSIAPAN",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextTertiary,
                    letterSpacing = 1.sp
                )
            }

            // Step 1: Overlay Permission
            item {
                StepCard(
                    stepNumber = "1",
                    title = "Izin Tampilan di Atas Aplikasi Lain",
                    description = "Diperlukan untuk memunculkan kursor crosshair dan panel kontrol mengambang.",
                    isCompleted = hasOverlayPermission,
                    buttonText = if (hasOverlayPermission) "Izin Diberikan" else "Beri Izin Overlay",
                    onClick = {
                        if (!hasOverlayPermission) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            overlayLauncher.launch(intent)
                        }
                    },
                    testTag = "step_overlay_btn"
                )
            }

            // Step 2: Notification Permission
            item {
                StepCard(
                    stepNumber = "2",
                    title = "Izin Notifikasi",
                    description = "Menjaga foreground service tetap aktif dan menyediakan tombol kontrol di bilah status.",
                    isCompleted = hasNotificationPermission,
                    buttonText = if (hasNotificationPermission) "Izin Diberikan" else "Beri Izin Notifikasi",
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            Toast.makeText(context, "Izin notifikasi sudah aktif", Toast.LENGTH_SHORT).show()
                        }
                    },
                    testTag = "step_notification_btn"
                )
            }

            // Step 3: Backend Selection & Status
            item {
                BackendCard(
                    backendState = backendState,
                    onSwitchBackend = { backendManager.switchBackend(it) },
                    onRequestPermission = {
                        if (backendState.type == BackendType.SHIZUKU) {
                            backendManager.requestShizukuPermission()
                        } else {
                            backendManager.reconnect()
                        }
                    },
                    onRecheck = {
                        backendManager.recheckStatus()
                    }
                )
            }

            // Mode Input: Layar Sentuh / Touchpad
            item {
                InputModeCard(
                    currentMode = touchSettings.inputMode,
                    onSelectMode = { mode ->
                        val updated = touchSettings.copy(inputMode = mode)
                        touchSettings = updated
                        TouchSettings.save(context, updated)
                        Toast.makeText(context, "Mode input: ${mode.displayName}", Toast.LENGTH_SHORT).show()
                    },
                    onOpenSettings = {
                        showSettingsDialog = true
                    }
                )
            }

            // Step 4: Primary Action Button (Start / Stop Overlay)
            item {
                PrimaryActionCard(
                    hasOverlayPermission = hasOverlayPermission,
                    isServiceRunning = isServiceRunning,
                    backendState = backendState,
                    onStart = {
                        if (!hasOverlayPermission) {
                            Toast.makeText(context, "Berikan izin overlay terlebih dahulu!", Toast.LENGTH_SHORT).show()
                            return@PrimaryActionCard
                        }
                        val intent = Intent(context, OverlayService::class.java).apply {
                            action = OverlayService.ACTION_START
                        }
                        ContextCompat.startForegroundService(context, intent)
                        isServiceRunning = true
                        Toast.makeText(context, "Overlay RightClickDash dimulai!", Toast.LENGTH_SHORT).show()
                    },
                    onStop = {
                        val intent = Intent(context, OverlayService::class.java).apply {
                            action = OverlayService.ACTION_STOP
                        }
                        context.startService(intent)
                        isServiceRunning = false
                        Toast.makeText(context, "Overlay dihentikan", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // Interactive Click Testing Box
            item {
                MouseTestCard()
            }

            // Shizuku Quick Guide
            item {
                ShizukuGuideCard()
            }

            // Technical Notes & Limitations
            item {
                TechnicalNotesCard()
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        if (showSettingsDialog) {
            SettingsDialog(
                initialSettings = touchSettings,
                onDismissRequest = { showSettingsDialog = false },
                onSaveSettings = { updated ->
                    touchSettings = updated
                    TouchSettings.save(context, updated)
                    Toast.makeText(context, "Pengaturan gerakan disimpan", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
fun HeroBanner(isServiceRunning: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkCardBorder))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(if (isServiceRunning) StatusGreen else TextTertiary, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isServiceRunning) "OVERLAY SEDANG BERJALAN" else "OVERLAY TIDAK AKTIF",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isServiceRunning) StatusGreen else TextTertiary,
                        letterSpacing = 0.5.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Injeksi Event Mouse Native",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Klik kiri, klik kanan, dan tahan via Shizuku & Root tanpa mouse fisik.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
fun StepCard(
    stepNumber: String,
    title: String,
    description: String,
    isCompleted: Boolean,
    buttonText: String,
    onClick: () -> Unit,
    testTag: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkCardBorder))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (isCompleted) StatusGreen else DarkSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCompleted) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Selesai",
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Text(
                            text = stepNumber,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(10.dp))
            if (isCompleted) {
                OutlinedButton(
                    onClick = onClick,
                    modifier = Modifier.testTag(testTag),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusGreen)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(buttonText, fontSize = 12.sp)
                }
            } else {
                Button(
                    onClick = onClick,
                    modifier = Modifier.testTag(testTag),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = Color.Black
                    )
                ) {
                    Text(buttonText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun BackendCard(
    backendState: BackendState,
    onSwitchBackend: (BackendType) -> Unit,
    onRequestPermission: () -> Unit,
    onRecheck: () -> Unit
) {
    val statusColor = when (backendState.status) {
        BackendStatus.READY -> StatusGreen
        BackendStatus.CONNECTING, BackendStatus.PERMISSION_REQUIRED -> StatusYellow
        else -> StatusRed
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkCardBorder))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Langkah 3: Pilih Backend Injeksi",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "Pilih Shizuku (tanpa root via ADB) atau Root (SuperSU/Magisk/KernelSU).",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Segmented switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBg, RoundedCornerShape(10.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val isShizuku = backendState.type == BackendType.SHIZUKU
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isShizuku) NeonCyan else Color.Transparent)
                        .clickable { onSwitchBackend(BackendType.SHIZUKU) }
                        .padding(vertical = 8.dp)
                        .testTag("backend_tab_shizuku"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Mode Shizuku",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (isShizuku) Color.Black else TextSecondary
                    )
                }

                val isRoot = backendState.type == BackendType.ROOT
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isRoot) NeonCyan else Color.Transparent)
                        .clickable { onSwitchBackend(BackendType.ROOT) }
                        .padding(vertical = 8.dp)
                        .testTag("backend_tab_root"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Mode Root",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (isRoot) Color.Black else TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Status message pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurfaceVariant, RoundedCornerShape(8.dp))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(statusColor, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = backendState.message,
                    fontSize = 12.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (backendState.status == BackendStatus.PERMISSION_REQUIRED) {
                        Button(
                            onClick = onRequestPermission,
                            modifier = Modifier.height(32.dp).testTag("backend_action_btn"),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonCyan,
                                contentColor = Color.Black
                            )
                        ) {
                            Text(
                                text = "Minta Izin",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onRecheck,
                        modifier = Modifier.height(32.dp).testTag("backend_recheck_btn"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Cek Ulang",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Cek Ulang",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InputModeCard(
    currentMode: InputMode,
    onSelectMode: (InputMode) -> Unit,
    onOpenSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkCardBorder))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.TouchApp,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Mode Input Kursor",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Pilih Layar Sentuh untuk memakai jari langsung di layar, atau Touchpad untuk panel melayang virtual.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Segmented Switch Mode Input
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBg, RoundedCornerShape(10.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val isTouchscreen = currentMode == InputMode.TOUCHSCREEN
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isTouchscreen) NeonCyan else Color.Transparent)
                        .clickable { onSelectMode(InputMode.TOUCHSCREEN) }
                        .padding(vertical = 8.dp)
                        .testTag("mode_tab_touchscreen"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = null,
                            tint = if (isTouchscreen) Color.Black else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Layar Sentuh (eksperimen)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (isTouchscreen) Color.Black else TextSecondary
                        )
                    }
                }

                val isTouchpad = currentMode == InputMode.TOUCHPAD
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isTouchpad) NeonCyan else Color.Transparent)
                        .clickable { onSelectMode(InputMode.TOUCHPAD) }
                        .padding(vertical = 8.dp)
                        .testTag("mode_tab_touchpad"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Mouse,
                            contentDescription = null,
                            tint = if (isTouchpad) Color.Black else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Touchpad Virtual (Bawaan)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (isTouchpad) Color.Black else TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tombol buka pengaturan gerakan & sensistivitas
            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .testTag("open_gesture_settings_btn"),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Konfigurasi Gerakan & Sensitivitas...",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun PrimaryActionCard(
    hasOverlayPermission: Boolean,
    isServiceRunning: Boolean,
    backendState: BackendState,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isServiceRunning) DarkSurfaceVariant else DarkSurface
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (isServiceRunning) StatusGreen else NeonCyan
            )
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Langkah 4: Jalankan Kursor",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isServiceRunning)
                    "Kursor dan panel mengambang aktif di layar. Anda dapat beralih ke aplikasi lain untuk melakukan klik."
                else
                    "Tekan tombol di bawah untuk menampilkan kursor crosshair dan panel kontrol melayang.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (isServiceRunning) {
                Button(
                    onClick = onStop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("stop_overlay_btn"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StatusRed,
                        contentColor = Color.White
                    )
                ) {
                    Icon(imageVector = Icons.Default.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Hentikan Overlay",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            } else {
                Button(
                    onClick = onStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("start_overlay_btn"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Mulai Overlay",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MouseTestCard() {
    var lastEventLog by remember { mutableStateOf("Belum ada event terdeteksi. Arahkan kursor ke sini!") }
    var eventCount by remember { mutableStateOf(0) }
    var lastButtonState by remember { mutableStateOf(0) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkCardBorder))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AdsClick,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Area Uji Coba Klik",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Posisikan kursor crosshair di kotak ini lalu tekan Klik Kiri atau Klik Kanan pada panel untuk memverifikasi deteksi mouse.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Test surface target
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkBg)
                    .border(1.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .pointerInteropFilter { motionEvent ->
                        val action = motionEvent.actionMasked
                        val buttonState = motionEvent.buttonState
                        val isMouse = motionEvent.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE

                        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_BUTTON_PRESS) {
                            eventCount++
                            lastButtonState = buttonState
                            val btnName = when {
                                (buttonState and MotionEvent.BUTTON_SECONDARY) != 0 -> "KLIK KANAN (BUTTON_SECONDARY)"
                                (buttonState and MotionEvent.BUTTON_PRIMARY) != 0 -> "KLIK KIRI (BUTTON_PRIMARY)"
                                else -> "ACTION_DOWN (Button: $buttonState)"
                            }
                            lastEventLog = "[$eventCount] $btnName | MouseTool: $isMouse"
                        }
                        true
                    }
                    .testTag("mouse_test_surface"),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "TARGET UJI KLIK",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = lastEventLog,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (lastButtonState != 0) StatusGreen else TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun ShizukuGuideCard() {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkCardBorder))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.HelpOutline,
                    contentDescription = null,
                    tint = ElectricBlue,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Panduan Shizuku Tanpa PC (Wireless Debugging)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = TextSecondary
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GuideStepItem("1", "Aktifkan Opsi Pengembang: Buka Pengaturan HP > Tentang Ponsel > Ketuk 'Nomor Bentukan' sebanyak 7 kali.")
                    GuideStepItem("2", "Nyalakan Debugging Nirkabel: Buka Pengaturan > Sistem > Opsi Pengembang > Aktifkan 'Debugging Nirkabel' saat terhubung ke Wi-Fi.")
                    GuideStepItem("3", "Pairing Shizuku: Buka aplikasi Shizuku, pilih 'Pairing', masukkan kode 6 angka dari notifikasi Debugging Nirkabel.")
                    GuideStepItem("4", "Mulai Layanan: Ketuk tombol 'Start' di Shizuku hingga status menjadi 'Shizuku is running'.")
                    GuideStepItem("5", "Buka RightClickDash: Kembali ke aplikasi ini dan ketuk 'Minta Izin Shizuku'.")
                }
            }
        }
    }
}

@Composable
fun GuideStepItem(num: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .background(ElectricBlue.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(num, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ElectricBlue)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, fontSize = 12.sp, color = TextSecondary, lineHeight = 16.sp)
    }
}

@Composable
fun TechnicalNotesCard() {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkCardBorder))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Catatan Teknis & Batasan",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = TextSecondary
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "• Event Disuntikkan: MotionEvent dengan SOURCE_MOUSE, TOOL_TYPE_MOUSE, BUTTON_PRIMARY (kiri) & BUTTON_SECONDARY (kanan).",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = "• Batasan Keamanan: Beberapa aplikasi dengan proteksi input (seperti aplikasi perbankan ber-FLAG_SECURE atau keyboard password sistem) dapat mengabaikan event sintetis dari luar demi keamanan.",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = "• Cara Build: Proyek ini menggunakan Gradle Kotlin DSL standar. Jalankan 'gradle assembleDebug' atau push ke GitHub Actions dengan setup-java temurin-17 untuk mengompilasi APK.",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

private fun checkOverlayPermission(context: Context): Boolean {
    return Settings.canDrawOverlays(context)
}

private fun checkNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}
