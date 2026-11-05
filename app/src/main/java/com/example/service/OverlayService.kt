package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.R
import com.example.backend.BackendType
import com.example.backend.InputBackendManager
import com.example.ui.overlay.ControlPanel
import com.example.ui.overlay.CursorCrosshair
import com.example.ui.theme.RightClickDashTheme

class OverlayService : Service() {

    companion object {
        private const val TAG = "RightClickDash-Overlay"
        const val CHANNEL_ID = "right_click_dash_overlay_channel"
        const val NOTIFICATION_ID = 9001

        const val ACTION_START = "com.example.action.START"
        const val ACTION_STOP = "com.example.action.STOP"
        const val ACTION_SHOW = "com.example.action.SHOW"
        const val ACTION_HIDE = "com.example.action.HIDE"

        @Volatile
        var isRunning: Boolean = false
            private set
    }

    private lateinit var windowManager: WindowManager
    private lateinit var backendManager: InputBackendManager
    private val lifecycleOwner = ServiceLifecycleOwner()

    // Overlay Views
    private var cursorView: ComposeView? = null
    private var cursorParams: WindowManager.LayoutParams? = null

    private var panelView: ComposeView? = null
    private var panelParams: WindowManager.LayoutParams? = null

    private var tapCatcherLayout: FrameLayout? = null

    // Coordinates & UI States
    private var screenWidth = 1080
    private var screenHeight = 2400

    private val cursorXState = mutableFloatStateOf(540f)
    private val cursorYState = mutableFloatStateOf(1200f)

    private val isCollapsedState = mutableStateOf(false)
    private val isHiddenState = mutableStateOf(false)
    private val isTapModeState = mutableStateOf(false)
    private val holdDurationState = mutableLongStateOf(800L)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        lifecycleOwner.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        backendManager = InputBackendManager.getInstance(applicationContext)

        holdDurationState.longValue = backendManager.getHoldDuration()

        readScreenDimensions()
        cursorXState.floatValue = screenWidth / 2f
        cursorYState.floatValue = screenHeight / 2f

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(isHidden = false))

        initCursorOverlay()
        initControlPanelOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SHOW -> {
                showOverlayViews()
            }
            ACTION_HIDE -> {
                hideOverlayViews()
            }
        }
        return START_STICKY
    }

    private fun readScreenDimensions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager.currentWindowMetrics
            val bounds = windowMetrics.bounds
            screenWidth = bounds.width()
            screenHeight = bounds.height()
        } else {
            @Suppress("DEPRECATION")
            val display = windowManager.defaultDisplay
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            display.getRealMetrics(metrics)
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
        }
    }

    private fun initCursorOverlay() {
        val cursorDensity = resources.displayMetrics.density
        val cursorHalfSizePx = (24 * cursorDensity).toInt()

        cursorParams = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            format = PixelFormat.TRANSLUCENT
            width = (48 * cursorDensity).toInt()
            height = (48 * cursorDensity).toInt()
            gravity = Gravity.TOP or Gravity.START
            x = (cursorXState.floatValue - cursorHalfSizePx).toInt()
            y = (cursorYState.floatValue - cursorHalfSizePx).toInt()
        }

        cursorView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                RightClickDashTheme {
                    CursorCrosshair()
                }
            }
        }

        try {
            windowManager.addView(cursorView, cursorParams)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed adding cursor overlay: ${t.message}")
        }
    }

    private fun initControlPanelOverlay() {
        panelParams = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            format = PixelFormat.TRANSLUCENT
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.START
            x = (screenWidth - 300 * resources.displayMetrics.density).toInt().coerceAtLeast(20)
            y = (screenHeight * 0.25f).toInt()
        }

        panelView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                RightClickDashTheme {
                    val backendState by backendManager.backendState.collectAsState()

                    ControlPanel(
                        backendState = backendState,
                        cursorX = cursorXState.floatValue,
                        cursorY = cursorYState.floatValue,
                        holdDurationMs = holdDurationState.longValue,
                        isCollapsed = isCollapsedState.value,
                        isTapToPlaceActive = isTapModeState.value,
                        onHoldDurationChange = { newDuration ->
                            holdDurationState.longValue = newDuration
                            backendManager.setHoldDuration(newDuration)
                        },
                        onSwitchBackend = { targetType ->
                            backendManager.switchBackend(targetType)
                        },
                        onRequestPermission = {
                            if (backendState.type == BackendType.SHIZUKU) {
                                backendManager.requestShizukuPermission()
                            } else {
                                backendManager.reconnect()
                            }
                        },
                        onClickLeft = {
                            val success = backendManager.click(
                                cursorXState.floatValue,
                                cursorYState.floatValue,
                                InputBackendManager.BUTTON_LEFT
                            )
                            if (!success) showToast("Gagal: backend belum siap")
                        },
                        onClickRight = {
                            val success = backendManager.click(
                                cursorXState.floatValue,
                                cursorYState.floatValue,
                                InputBackendManager.BUTTON_RIGHT
                            )
                            if (!success) showToast("Gagal: backend belum siap")
                        },
                        onHoldRight = {
                            val success = backendManager.hold(
                                cursorXState.floatValue,
                                cursorYState.floatValue,
                                InputBackendManager.BUTTON_RIGHT,
                                holdDurationState.longValue
                            )
                            if (!success) showToast("Gagal: backend belum siap")
                        },
                        onRelativeMove = { dx, dy ->
                            updateCursorPosition(
                                cursorXState.floatValue + dx,
                                cursorYState.floatValue + dy
                            )
                        },
                        onCenterCursor = {
                            updateCursorPosition(screenWidth / 2f, screenHeight / 2f)
                        },
                        onToggleTapToPlace = {
                            toggleTapToPlace()
                        },
                        onToggleCollapsed = {
                            isCollapsedState.value = !isCollapsedState.value
                        },
                        onCloseService = {
                            stopSelf()
                        },
                        onDragPanel = { dx, dy ->
                            dragControlPanel(dx, dy)
                        }
                    )
                }
            }
        }

        try {
            windowManager.addView(panelView, panelParams)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed adding panel overlay: ${t.message}")
        }
    }

    private fun updateCursorPosition(newX: Float, newY: Float) {
        val clampedX = newX.coerceIn(0f, screenWidth.toFloat())
        val clampedY = newY.coerceIn(0f, screenHeight.toFloat())

        cursorXState.floatValue = clampedX
        cursorYState.floatValue = clampedY

        val cursorDensity = resources.displayMetrics.density
        val cursorHalfSizePx = (24 * cursorDensity).toInt()

        cursorParams?.let { params ->
            params.x = (clampedX - cursorHalfSizePx).toInt()
            params.y = (clampedY - cursorHalfSizePx).toInt()
            try {
                if (cursorView != null && cursorView?.isAttachedToWindow == true) {
                    windowManager.updateViewLayout(cursorView, params)
                }
            } catch (_: Throwable) {}
        }

        // Notify input move in background
        backendManager.move(clampedX, clampedY)
    }

    private fun dragControlPanel(dx: Float, dy: Float) {
        panelParams?.let { params ->
            params.x = (params.x + dx.toInt()).coerceIn(0, screenWidth - 100)
            params.y = (params.y + dy.toInt()).coerceIn(0, screenHeight - 100)
            try {
                if (panelView != null && panelView?.isAttachedToWindow == true) {
                    windowManager.updateViewLayout(panelView, params)
                }
            } catch (_: Throwable) {}
        }
    }

    private fun toggleTapToPlace() {
        if (isTapModeState.value) {
            disableTapToPlace()
        } else {
            enableTapToPlace()
        }
    }

    private fun enableTapToPlace() {
        if (tapCatcherLayout != null) return
        isTapModeState.value = true

        val params = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            format = PixelFormat.TRANSLUCENT
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            gravity = Gravity.TOP or Gravity.START
        }

        tapCatcherLayout = FrameLayout(this).apply {
            setBackgroundColor(0x3300E5FF.toInt())
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    updateCursorPosition(event.rawX, event.rawY)
                    disableTapToPlace()
                    true
                } else {
                    false
                }
            }
        }

        try {
            windowManager.addView(tapCatcherLayout, params)
            showToast("Ketuk di mana saja di layar untuk menempatkan kursor")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed adding tap catcher: ${t.message}")
            disableTapToPlace()
        }
    }

    private fun disableTapToPlace() {
        isTapModeState.value = false
        tapCatcherLayout?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Throwable) {}
        }
        tapCatcherLayout = null
    }

    private fun hideOverlayViews() {
        isHiddenState.value = true
        cursorView?.visibility = View.GONE
        panelView?.visibility = View.GONE
        disableTapToPlace()
        updateNotification(isHidden = true)
        showToast("Overlay disembunyikan. Gunakan notifikasi untuk menampilkan.")
    }

    private fun showOverlayViews() {
        isHiddenState.value = false
        cursorView?.visibility = View.VISIBLE
        panelView?.visibility = View.VISIBLE
        updateNotification(isHidden = false)
        showToast("Overlay ditampilkan kembali")
    }

    private fun showToast(msg: String) {
        Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "RightClickDash Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Kontrol overlay kursor & klik mouse"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(isHidden: Boolean): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val toggleAction = if (isHidden) {
            val showIntent = Intent(this, OverlayService::class.java).apply { action = ACTION_SHOW }
            val showPending = PendingIntent.getService(
                this, 1, showIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_menu_view,
                "Tampilkan",
                showPending
            ).build()
        } else {
            val hideIntent = Intent(this, OverlayService::class.java).apply { action = ACTION_HIDE }
            val hidePending = PendingIntent.getService(
                this, 2, hideIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Sembunyikan",
                hidePending
            ).build()
        }

        val stopIntent = Intent(this, OverlayService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this, 3, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_delete,
            "Stop",
            stopPending
        ).build()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RightClickDash Aktif")
            .setContentText(
                if (isHidden) "Overlay tersembunyi"
                else "Kursor di X: ${cursorXState.floatValue.toInt()}, Y: ${cursorYState.floatValue.toInt()}"
            )
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .addAction(toggleAction)
            .addAction(stopAction)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(isHidden: Boolean) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification(isHidden))
    }

    override fun onDestroy() {
        isRunning = false
        disableTapToPlace()

        cursorView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Throwable) {}
        }
        cursorView = null

        panelView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Throwable) {}
        }
        panelView = null

        lifecycleOwner.onDestroy()
        super.onDestroy()
    }
}
