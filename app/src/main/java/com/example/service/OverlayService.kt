package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.DisplayMetrics
import android.util.Log
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.R
import com.example.backend.BackendType
import com.example.backend.InputBackendManager
import com.example.ui.overlay.CURSOR_TIP_OFFSET
import com.example.ui.overlay.CURSOR_WINDOW_SIZE
import com.example.ui.overlay.ControlPanel
import com.example.ui.overlay.CursorCrosshair
import com.example.ui.overlay.InputMode
import com.example.ui.overlay.PanelBehavior
import com.example.ui.overlay.RecordMode
import com.example.ui.overlay.SeparateDragHandle
import com.example.ui.overlay.TouchSettings
import com.example.ui.theme.RightClickDashTheme
import kotlin.math.hypot

class OverlayService : Service() {

    companion object {
        private const val TAG = "RCD"
        const val CHANNEL_ID = "right_click_dash_overlay_channel"
        const val NOTIFICATION_ID = 9001

        const val ACTION_START = "com.example.action.START"
        const val ACTION_STOP = "com.example.action.STOP"
        const val ACTION_SHOW = "com.example.action.SHOW"
        const val ACTION_HIDE = "com.example.action.HIDE"
        const val ACTION_TOGGLE_VISIBILITY = "com.example.action.TOGGLE_VISIBILITY"
        const val ACTION_TOGGLE_INPUT_MODE = "com.example.action.TOGGLE_INPUT_MODE"

        private const val PREFS_NAME = "right_click_dash_prefs"
        private const val KEY_RECORD_MODE = "KEY_RECORD_MODE"
        private const val KEY_PANEL_BEHAVIOR = "KEY_PANEL_BEHAVIOR"

        @Volatile
        var isRunning: Boolean = false
            private set
    }

    private lateinit var windowManager: WindowManager
    private lateinit var backendManager: InputBackendManager
    private val lifecycleOwner = ServiceLifecycleOwner()
    private val mainHandler = Handler(Looper.getMainLooper())

    // 1. Cursor Overlay
    private var cursorView: ComposeView? = null
    private var cursorParams: WindowManager.LayoutParams? = null

    // 2. Touchpad Virtual Overlays (Hanya ada di Mode Touchpad)
    private var panelView: ComposeView? = null
    private var panelParams: WindowManager.LayoutParams? = null

    private var handleView: ComposeView? = null
    private var handleParams: WindowManager.LayoutParams? = null

    // 3. Mode Layar Sentuh: Fullscreen Capture Window (Hanya ada di Mode Layar Sentuh)
    private var fullscreenCaptureView: View? = null
    private var fullscreenCaptureParams: WindowManager.LayoutParams? = null
    private var isCaptureFlagNotTouchable = false

    // Screen Dimensions
    private var screenWidth = 1080
    private var screenHeight = 2400

    // Coordinates & UI States
    private val cursorXState = mutableFloatStateOf(540f)
    private val cursorYState = mutableFloatStateOf(1200f)

    private val isHiddenState = mutableStateOf(false)
    private val heldButtonState = mutableIntStateOf(0)
    private val recordModeState = mutableStateOf(RecordMode.OFF)

    // Default "Perilaku panel" = Dinding
    private val panelBehaviorState = mutableStateOf(PanelBehavior.DINDING)
    private val resetTouchpadTriggerState = mutableIntStateOf(0)

    // Mode Input & Gesture Settings (Default: Layar Sentuh)
    private val touchSettingsState = mutableStateOf(TouchSettings())

    // State untuk Mode TEMBUS pada Panel Touchpad (FLAG_NOT_TOUCHABLE)
    private var activeTouchCount = 0
    private var isFlagNotTouchableSet = false

    private val restoreTouchableRunnable = Runnable {
        removeFlagNotTouchable()
    }

    // State Machine untuk Mode Layar Sentuh
    private var touchDownTime = 0L
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var lastTouchTime = 0L
    private var hasMovedBeyondSlop = false
    private var isRightDragTriggered = false
    private var lastTapTime = 0L
    private var smoothTouchSpeed = 0f

    // Dua Jari (Ketuk = Klik Kanan, Geser = Scroll)
    private var isTwoFingerTouch = false
    private var twoFingerDownTime = 0L
    private var twoFingerTotalDy = 0f
    private var twoFingerLastY0 = 0f
    private var twoFingerLastY1 = 0f
    private var twoFingerScrollAcc = 0f

    // Long press runnable untuk Mode Layar Sentuh
    private val longPressRunnable = Runnable {
        if (!hasMovedBeyondSlop && heldButtonState.intValue == 0) {
            val isWithinDoubleTap = (touchDownTime - lastTapTime) <= touchSettingsState.value.doubleTapMs
            vibrateBrief()
            if (isWithinDoubleTap) {
                Log.i(TAG, "Mode Layar Sentuh: diam sampai LONG_PRESS setelah double tap -> pressDown KANAN")
                heldButtonState.intValue = InputBackendManager.BUTTON_RIGHT
                backendManager.pressDown(cursorXState.floatValue, cursorYState.floatValue, InputBackendManager.BUTTON_RIGHT)
            } else {
                Log.i(TAG, "Mode Layar Sentuh: tetap diam sampai LONG_PRESS -> pressDown KIRI")
                heldButtonState.intValue = InputBackendManager.BUTTON_LEFT
                backendManager.pressDown(cursorXState.floatValue, cursorYState.floatValue, InputBackendManager.BUTTON_LEFT)
            }
        }
    }

    // Diagnostik: Jumlah updateViewLayout per detik pada jendela kursor
    private var cursorUpdateCount = 0
    private var lastCursorFpsLogTime = 0L

    // State untuk Choreographer VSync Throttling (Kelancaran kursor overlay)
    private var isCursorFrameScheduled = false
    private val cursorFrameCallback = Choreographer.FrameCallback {
        isCursorFrameScheduled = false
        val cursorDensity = resources.displayMetrics.density
        val tipOffsetPx = CURSOR_TIP_OFFSET.value * cursorDensity
        cursorParams?.let { params ->
            val targetX = (cursorXState.floatValue - tipOffsetPx).toInt()
            val targetY = (cursorYState.floatValue - tipOffsetPx).toInt()
            if (params.x != targetX || params.y != targetY) {
                params.x = targetX
                params.y = targetY
                try {
                    if (cursorView != null && cursorView?.isAttachedToWindow == true) {
                        windowManager.updateViewLayout(cursorView, params)
                        cursorUpdateCount++
                    }
                } catch (_: Throwable) {}
            }
        }

        // Catat jumlah updateViewLayout per detik pada jendela kursor ke log "RCD"
        val now = SystemClock.uptimeMillis()
        if (now - lastCursorFpsLogTime >= 1000L) {
            if (lastCursorFpsLogTime != 0L) {
                val elapsed = (now - lastCursorFpsLogTime).coerceAtLeast(1L)
                val rate = cursorUpdateCount * 1000f / elapsed
                Log.d(TAG, "Jendela kursor: %.1f updateViewLayout / detik".format(rate))
            }
            cursorUpdateCount = 0
            lastCursorFpsLogTime = now
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        lifecycleOwner.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        backendManager = InputBackendManager.getInstance(applicationContext)

        readScreenDimensions()
        cursorXState.floatValue = screenWidth / 2f
        cursorYState.floatValue = screenHeight / 2f

        // Muat Pengaturan Input, Mode Rekam, dan Perilaku Panel
        val loadedSettings = TouchSettings.load(this)
        touchSettingsState.value = loadedSettings

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedModeName = prefs.getString(KEY_RECORD_MODE, RecordMode.OFF.name)
        val initialRecordMode = try {
            RecordMode.valueOf(savedModeName ?: RecordMode.OFF.name)
        } catch (_: Exception) {
            RecordMode.OFF
        }
        recordModeState.value = initialRecordMode

        // Default "Perilaku panel" = DINDING
        val savedBehaviorName = prefs.getString(KEY_PANEL_BEHAVIOR, PanelBehavior.DINDING.name)
        val initialBehavior = try {
            PanelBehavior.valueOf(savedBehaviorName ?: PanelBehavior.DINDING.name)
        } catch (_: Exception) {
            PanelBehavior.DINDING
        }
        panelBehaviorState.value = initialBehavior

        Log.i(TAG, "OverlayService onCreate: Mode Input=${loadedSettings.inputMode.name}, Perilaku Panel=${initialBehavior.name}")
        val backendState = backendManager.backendState.value
        Log.i(TAG, "Backend aktif saat inisialisasi: ${backendState.type.name} (${backendState.status.name})")

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(isHidden = false))

        initCursorOverlay()

        // Pasang jendela sesuai mode aktif
        if (loadedSettings.inputMode == InputMode.TOUCHSCREEN) {
            initFullscreenCaptureOverlay()
            backendManager.resetDiagnostics()
        } else {
            initControlPanelOverlay()
            initHandleOverlay()
            backendManager.resetDiagnostics()
        }

        applyRecordMode(initialRecordMode, saveToPrefs = false)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Log.i(TAG, "Menerima aksi ACTION_STOP -> menghentikan OverlayService")
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_VISIBILITY -> {
                if (isHiddenState.value) {
                    showOverlayViews()
                } else {
                    hideOverlayViews()
                }
            }
            ACTION_SHOW -> {
                showOverlayViews()
            }
            ACTION_HIDE -> {
                hideOverlayViews()
            }
            ACTION_TOGGLE_INPUT_MODE -> {
                val current = touchSettingsState.value.inputMode
                val target = if (current == InputMode.TOUCHSCREEN) InputMode.TOUCHPAD else InputMode.TOUCHSCREEN
                Log.i(TAG, "Notifikasi: Mengganti mode input ke ${target.name}")
                applyInputMode(target, centerCursor = true, saveToPrefs = true)
            }
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.i(TAG, "onConfigurationChanged dipanggil (orientasi layar berubah)")
        readScreenDimensions()

        // Clamp ulang posisi kursor ke batas layar yang baru
        val clampedCursorX = cursorXState.floatValue.coerceIn(0f, screenWidth.toFloat())
        val clampedCursorY = cursorYState.floatValue.coerceIn(0f, screenHeight.toFloat())
        cursorXState.floatValue = clampedCursorX
        cursorYState.floatValue = clampedCursorY

        scheduleCursorWindowUpdate()
        clampAndUpdateOverlays()
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

    private fun vibrateBrief() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val vibrator = getSystemService(Vibrator::class.java)
                vibrator?.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(35)
            }
        } catch (t: Throwable) {
            Log.d(TAG, "Vibrate note: ${t.message}")
        }
    }

    private fun logWindowFlagChange(windowName: String, oldFlags: Int, newFlags: Int) {
        if (oldFlags != newFlags) {
            Log.i(TAG, "Perubahan flag jendela [$windowName]: lama=0x${Integer.toHexString(oldFlags)} -> baru=0x${Integer.toHexString(newFlags)}")
        }
    }

    private fun initCursorOverlay() {
        if (cursorView != null) return
        val cursorDensity = resources.displayMetrics.density
        val cursorSizePx = (CURSOR_WINDOW_SIZE.value * cursorDensity).toInt()
        val tipOffsetPx = CURSOR_TIP_OFFSET.value * cursorDensity

        cursorParams = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            format = PixelFormat.TRANSLUCENT
            width = cursorSizePx
            height = cursorSizePx
            gravity = Gravity.TOP or Gravity.START
            x = (cursorXState.floatValue - tipOffsetPx).toInt()
            y = (cursorYState.floatValue - tipOffsetPx).toInt()
        }

        cursorView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                RightClickDashTheme {
                    CursorCrosshair(
                        heldButton = heldButtonState.intValue
                    )
                }
            }
        }

        try {
            windowManager.addView(cursorView, cursorParams)
            logWindowFlagChange("cursorView", 0, cursorParams?.flags ?: 0)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed adding cursor overlay: ${t.message}")
        }
    }

    private fun initControlPanelOverlay() {
        if (panelView != null) return
        val density = resources.displayMetrics.density
        val panelW = minOf((screenWidth * 0.9f).toInt(), (340 * density).toInt())

        panelParams = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            if (recordModeState.value == RecordMode.SECURE) {
                flags = flags or WindowManager.LayoutParams.FLAG_SECURE
            }
            format = PixelFormat.TRANSLUCENT
            width = panelW
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.START
            x = ((screenWidth - panelW) / 2).coerceAtLeast(10)
            y = (screenHeight * 0.25f).toInt()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                    blurBehindRadius = (25 * density).toInt()
                } catch (_: Throwable) {}
            }
        }

        panelView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)

            // Event bertipe mouse (hasil suntikan) tidak boleh diteruskan ke touchpad!
            setOnTouchListener { _, event ->
                if (event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE) {
                    return@setOnTouchListener true
                }
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> onPanelTouchStart()
                    MotionEvent.ACTION_UP -> onPanelTouchEnd(isCancel = false)
                    MotionEvent.ACTION_CANCEL -> {
                        Log.w(TAG, "ACTION_CANCEL terdeteksi pada [panelView]!")
                        onPanelTouchEnd(isCancel = true)
                    }
                }
                false
            }

            setContent {
                RightClickDashTheme {
                    val backendState by backendManager.backendState.collectAsState()

                    ControlPanel(
                        backendState = backendState,
                        cursorX = cursorXState.floatValue,
                        cursorY = cursorYState.floatValue,
                        recordMode = recordModeState.value,
                        panelBehavior = panelBehaviorState.value,
                        inputMode = touchSettingsState.value.inputMode,
                        heldButton = heldButtonState.intValue,
                        resetTouchpadTrigger = resetTouchpadTriggerState.intValue,
                        onSwitchBackend = { backendManager.switchBackend(it) },
                        onRequestPermission = {
                            if (backendState.type == BackendType.SHIZUKU) {
                                backendManager.requestShizukuPermission()
                            } else {
                                backendManager.reconnect()
                            }
                        },
                        onRecheckBackend = { backendManager.recheckStatus() },
                        onSelectRecordMode = { applyRecordMode(it) },
                        onSelectPanelBehavior = { applyPanelBehavior(it) },
                        onSelectInputMode = { applyInputMode(it, centerCursor = true, saveToPrefs = true) },
                        onOpenSettings = { openSettingsInActivity() },
                        onTouchStart = { onPanelTouchStart() },
                        onTouchEnd = { isCancel -> onPanelTouchEnd(isCancel) },
                        onClickLeft = {
                            val ok = backendManager.click(cursorXState.floatValue, cursorYState.floatValue, InputBackendManager.BUTTON_LEFT)
                            if (!ok) showToast("Gagal: backend belum siap")
                        },
                        onClickRight = {
                            val ok = backendManager.click(cursorXState.floatValue, cursorYState.floatValue, InputBackendManager.BUTTON_RIGHT)
                            if (!ok) showToast("Gagal: backend belum siap")
                        },
                        onLockLeft = { lockButton(InputBackendManager.BUTTON_LEFT) },
                        onLockRight = { lockButton(InputBackendManager.BUTTON_RIGHT) },
                        onUnlock = { unlockButton() },
                        onRelativeMove = { dx, dy ->
                            updateCursorPosition(cursorXState.floatValue + dx, cursorYState.floatValue + dy)
                        },
                        onTapClick = {
                            Log.i(TAG, "Ketukan touchpad -> kirim klik kiri di (${cursorXState.floatValue}, ${cursorYState.floatValue})")
                            val ok = backendManager.click(cursorXState.floatValue, cursorYState.floatValue, InputBackendManager.BUTTON_LEFT)
                            if (!ok) showToast("Gagal: backend belum siap")
                        },
                        onScroll = { notches ->
                            backendManager.scroll(cursorXState.floatValue, cursorYState.floatValue, notches)
                        }
                    )
                }
            }
        }

        try {
            windowManager.addView(panelView, panelParams)
            logWindowFlagChange("panelView", 0, panelParams?.flags ?: 0)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed adding panel overlay: ${t.message}")
        }
    }

    private fun initHandleOverlay() {
        if (handleView != null) return
        val density = resources.displayMetrics.density
        val handleW = (140 * density).toInt()
        val handleH = (32 * density).toInt()
        val gapPx = (10 * density).toInt()

        val panelW = panelParams?.width ?: minOf((screenWidth * 0.9f).toInt(), (340 * density).toInt())
        val panelH = if (panelView != null && panelView!!.height > 0) panelView!!.height else (310 * density).toInt()
        val panelX = panelParams?.x ?: ((screenWidth - panelW) / 2).coerceAtLeast(10)
        val panelY = panelParams?.y ?: (screenHeight * 0.25f).toInt()

        handleParams = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            if (recordModeState.value == RecordMode.SECURE) {
                flags = flags or WindowManager.LayoutParams.FLAG_SECURE
            }
            format = PixelFormat.TRANSLUCENT
            width = handleW
            height = handleH
            gravity = Gravity.TOP or Gravity.START
            x = panelX + (panelW - handleW) / 2
            y = panelY + panelH + gapPx
        }

        handleView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                RightClickDashTheme {
                    SeparateDragHandle(
                        recordMode = recordModeState.value,
                        onDragStart = {},
                        onDragEnd = {},
                        onDrag = { dx, dy -> dragControlPanel(dx, dy) }
                    )
                }
            }
        }

        try {
            windowManager.addView(handleView, handleParams)
            logWindowFlagChange("handleView", 0, handleParams?.flags ?: 0)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed adding handle overlay: ${t.message}")
        }
    }

    private fun removeControlPanelOverlay() {
        panelView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Throwable) {}
        }
        panelView = null
        panelParams = null
    }

    private fun removeHandleOverlay() {
        handleView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Throwable) {}
        }
        handleView = null
        handleParams = null
    }

    private fun initFullscreenCaptureOverlay() {
        if (fullscreenCaptureView != null) return
        fullscreenCaptureParams = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            format = PixelFormat.TRANSLUCENT
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            gravity = Gravity.TOP or Gravity.START
        }

        val capture = View(this)
        capture.setOnTouchListener { _, event ->
            if (event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE) {
                return@setOnTouchListener true
            }
            handleFullscreenTouchEvent(event)
        }

        fullscreenCaptureView = capture
        try {
            windowManager.addView(fullscreenCaptureView, fullscreenCaptureParams)
            isCaptureFlagNotTouchable = false
            logWindowFlagChange("fullscreenCaptureView", 0, fullscreenCaptureParams?.flags ?: 0)
            Log.i(TAG, "Fullscreen capture overlay berhasil dipasang.")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed adding fullscreen capture overlay: ${t.message}")
        }
    }

    private fun removeFullscreenCaptureOverlay() {
        fullscreenCaptureView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Throwable) {}
        }
        fullscreenCaptureView = null
        fullscreenCaptureParams = null
        isCaptureFlagNotTouchable = false
    }

    private fun setCaptureFlagNotTouchable(notTouchable: Boolean) {
        val params = fullscreenCaptureParams ?: return
        val view = fullscreenCaptureView ?: return
        if (isCaptureFlagNotTouchable == notTouchable) return

        val oldFlags = params.flags
        params.flags = if (notTouchable) {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }

        if (params.flags != oldFlags && view.isAttachedToWindow) {
            try {
                windowManager.updateViewLayout(view, params)
                isCaptureFlagNotTouchable = notTouchable
                logWindowFlagChange("fullscreenCaptureView", oldFlags, params.flags)
            } catch (t: Throwable) {
                Log.e(TAG, "Gagal updateViewLayout captureView FLAG_NOT_TOUCHABLE: ${t.message}")
            }
        }
    }

    private fun handleFullscreenTouchEvent(event: MotionEvent): Boolean {
        if (touchSettingsState.value.inputMode != InputMode.TOUCHSCREEN || isHiddenState.value) {
            return false
        }

        val density = resources.displayMetrics.density
        val settings = touchSettingsState.value

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // TEMBUS: saat ACTION_DOWN pertama, pasang FLAG_NOT_TOUCHABLE sebelum menyuntik event apa pun!
                setCaptureFlagNotTouchable(true)

                touchDownTime = event.eventTime
                touchDownX = event.x
                touchDownY = event.y
                lastTouchX = event.x
                lastTouchY = event.y
                lastTouchTime = event.eventTime
                hasMovedBeyondSlop = false
                isRightDragTriggered = false
                smoothTouchSpeed = 0f
                isTwoFingerTouch = false
                twoFingerScrollAcc = 0f

                mainHandler.removeCallbacks(longPressRunnable)
                mainHandler.postDelayed(longPressRunnable, settings.longPressMs)
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    mainHandler.removeCallbacks(longPressRunnable)
                    isTwoFingerTouch = true
                    twoFingerDownTime = event.eventTime
                    twoFingerTotalDy = 0f
                    twoFingerLastY0 = event.getY(0)
                    twoFingerLastY1 = event.getY(1)
                    twoFingerScrollAcc = 0f
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1 && !isTwoFingerTouch) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    val distFromDownDp = hypot(event.x - touchDownX, event.y - touchDownY) / density

                    if (distFromDownDp > settings.slopDp) {
                        if (!hasMovedBeyondSlop) {
                            hasMovedBeyondSlop = true
                            mainHandler.removeCallbacks(longPressRunnable)

                            val isWithinDoubleTap = (touchDownTime - lastTapTime) <= settings.doubleTapMs
                            if (isWithinDoubleTap && heldButtonState.intValue == 0 && !isRightDragTriggered) {
                                Log.i(TAG, "Mode Layar Sentuh: bergeser > SLOP dalam double tap window -> pressDown KANAN")
                                isRightDragTriggered = true
                                heldButtonState.intValue = InputBackendManager.BUTTON_RIGHT
                                backendManager.pressDown(cursorXState.floatValue, cursorYState.floatValue, InputBackendManager.BUTTON_RIGHT)
                            }
                        }

                        // Pergerakan Pointer Relatif dengan kurva kecepatan (gain)
                        val dt = (event.eventTime - lastTouchTime).coerceAtLeast(4L)
                        val stepDistDp = hypot(dx, dy) / density
                        val rawSpeed = stepDistDp / dt.toFloat()
                        smoothTouchSpeed = smoothTouchSpeed * 0.6f + rawSpeed * 0.4f

                        val gain = if (smoothTouchSpeed <= 0.5f) {
                            settings.baseGain
                        } else {
                            (settings.baseGain + (smoothTouchSpeed - 0.5f) * settings.accel).coerceAtMost(settings.maxGain)
                        }

                        val deltaCursorX = dx * gain
                        val deltaCursorY = dy * gain
                        updateCursorPosition(cursorXState.floatValue + deltaCursorX, cursorYState.floatValue + deltaCursorY)
                    }

                    lastTouchX = event.x
                    lastTouchY = event.y
                    lastTouchTime = event.eventTime
                } else if (event.pointerCount >= 2) {
                    mainHandler.removeCallbacks(longPressRunnable)
                    val y0 = event.getY(0)
                    val y1 = event.getY(1)
                    val avgDy = ((y0 - twoFingerLastY0) + (y1 - twoFingerLastY1)) / 2f
                    twoFingerLastY0 = y0
                    twoFingerLastY1 = y1
                    twoFingerTotalDy += kotlin.math.abs(avgDy)

                    val stepPx = 24f * density
                    twoFingerScrollAcc += if (settings.twoFingerScrollNatural) -avgDy else avgDy
                    val notches = (twoFingerScrollAcc / stepPx).toInt()
                    if (notches != 0) {
                        twoFingerScrollAcc -= notches * stepPx
                        backendManager.scroll(cursorXState.floatValue, cursorYState.floatValue, notches)
                    }
                }
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount == 2 && isTwoFingerTouch) {
                    val duration = event.eventTime - twoFingerDownTime
                    val slopPx = settings.slopDp * density
                    if (duration < settings.tapMaxMs && twoFingerTotalDy < slopPx * 1.5f) {
                        if (settings.twoFingerTapRightClick) {
                            Log.i(TAG, "Mode Layar Sentuh: Ketuk dua jari bersamaan -> KLIK KANAN")
                            backendManager.click(cursorXState.floatValue, cursorYState.floatValue, InputBackendManager.BUTTON_RIGHT)
                        }
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                mainHandler.removeCallbacks(longPressRunnable)
                val upTime = event.eventTime

                if (heldButtonState.intValue != 0) {
                    val btn = heldButtonState.intValue
                    Log.i(TAG, "Mode Layar Sentuh: Jari lepas -> release button=$btn")
                    backendManager.release(cursorXState.floatValue, cursorYState.floatValue, btn)
                    heldButtonState.intValue = 0
                    lastTapTime = 0L
                } else if (!isTwoFingerTouch) {
                    val duration = upTime - touchDownTime
                    if (!hasMovedBeyondSlop && duration < settings.tapMaxMs) {
                        val isWithinDoubleTap = (touchDownTime - lastTapTime) <= settings.doubleTapMs
                        if (isWithinDoubleTap) {
                            Log.i(TAG, "Mode Layar Sentuh: Double click tap kedua -> KLIK KIRI kedua")
                            backendManager.click(cursorXState.floatValue, cursorYState.floatValue, InputBackendManager.BUTTON_LEFT)
                            lastTapTime = 0L
                        } else {
                            Log.i(TAG, "Mode Layar Sentuh: Single tap -> KLIK KIRI")
                            backendManager.click(cursorXState.floatValue, cursorYState.floatValue, InputBackendManager.BUTTON_LEFT)
                            lastTapTime = upTime
                        }
                    } else {
                        lastTapTime = 0L
                    }
                } else {
                    lastTapTime = 0L
                }

                isTwoFingerTouch = false
                // Pulihkan touchable SEGERA tanpa jeda tambahan
                setCaptureFlagNotTouchable(false)
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                Log.w(TAG, "ACTION_CANCEL terdeteksi pada [fullscreenCaptureView]!")
                mainHandler.removeCallbacks(longPressRunnable)
                if (heldButtonState.intValue != 0) {
                    backendManager.release(cursorXState.floatValue, cursorYState.floatValue, heldButtonState.intValue)
                    heldButtonState.intValue = 0
                }
                lastTapTime = 0L
                isTwoFingerTouch = false
                setCaptureFlagNotTouchable(false)
                return true
            }
        }
        return false
    }

    private fun openSettingsInActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("OPEN_SETTINGS_DIALOG", true)
        }
        startActivity(intent)
    }

    /**
     * Berpindah Mode Input:
     * - Saat tersembunyi: hanya mengubah pilihan di SharedPreferences.
     * - Saat tampil: lepaskan tombol tertahan, hapus jendela mode lama, pasang jendela mode baru tanpa restart service.
     */
    private fun applyInputMode(mode: InputMode, centerCursor: Boolean = false, saveToPrefs: Boolean = true) {
        val currentSettings = touchSettingsState.value
        val previousMode = currentSettings.inputMode

        val updated = currentSettings.copy(inputMode = mode)
        touchSettingsState.value = updated
        if (saveToPrefs) {
            TouchSettings.save(this, updated)
        }

        Log.i(TAG, "Pilihan Mode Input: ${mode.name} (${mode.displayName})")
        val backendState = backendManager.backendState.value
        Log.i(TAG, "Backend aktif: ${backendState.type.name} (${backendState.status.name})")

        // Jika sedang tersembunyi: hanya ubah pilihan, jangan pasang jendela
        if (isHiddenState.value) {
            updateNotification(isHidden = true)
            showToast("Mode: ${mode.displayName} (disimpan)")
            return
        }

        // Sedang tampil: lepaskan tombol tertahan, hapus jendela mode lama, pasang jendela mode baru
        unlockButton()

        if (previousMode == InputMode.TOUCHPAD) {
            removeControlPanelOverlay()
            removeHandleOverlay()
        } else {
            removeFullscreenCaptureOverlay()
        }

        if (mode == InputMode.TOUCHSCREEN) {
            initFullscreenCaptureOverlay()
            if (centerCursor) {
                cursorXState.floatValue = screenWidth / 2f
                cursorYState.floatValue = screenHeight / 2f
                scheduleCursorWindowUpdate()
            }
            backendManager.resetDiagnostics()
        } else {
            initControlPanelOverlay()
            initHandleOverlay()
            backendManager.resetDiagnostics()
        }

        updateNotification(isHidden = false)
        showToast("Mode: ${mode.displayName}")
    }

    private fun scheduleCursorWindowUpdate() {
        if (!isCursorFrameScheduled) {
            isCursorFrameScheduled = true
            Choreographer.getInstance().postFrameCallback(cursorFrameCallback)
        }
    }

    private fun clampOutsideRect(
        x: Float,
        y: Float,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float
    ): Pair<Float, Float> {
        if (x < left || x > right || y < top || y > bottom) {
            return Pair(x, y)
        }

        val dLeft = x - left
        val dRight = right - x
        val dTop = y - top
        val dBottom = bottom - y

        val minD = minOf(dLeft, dRight, dTop, dBottom)
        return when (minD) {
            dLeft -> Pair(left, y)
            dRight -> Pair(right, y)
            dTop -> Pair(x, top)
            else -> Pair(x, bottom)
        }
    }

    private fun applyDindingMode(targetX: Float, targetY: Float): Pair<Float, Float> {
        val density = resources.displayMetrics.density
        val pParams = panelParams ?: return Pair(targetX, targetY)
        val panelW = pParams.width.toFloat()
        val panelH = if (panelView != null && panelView!!.height > 0) panelView!!.height.toFloat() else 310f * density

        val pLeft = pParams.x.toFloat()
        val pTop = pParams.y.toFloat()
        val pRight = pLeft + panelW
        val pBottom = pTop + panelH

        val (afterPanelX, afterPanelY) = clampOutsideRect(targetX, targetY, pLeft, pTop, pRight, pBottom)

        val hParams = handleParams
        if (hParams != null) {
            val handleW = hParams.width.toFloat()
            val handleH = hParams.height.toFloat()
            val hLeft = hParams.x.toFloat()
            val hTop = hParams.y.toFloat()
            val hRight = hLeft + handleW
            val hBottom = hTop + handleH

            val (afterHandleX, afterHandleY) = clampOutsideRect(afterPanelX, afterPanelY, hLeft, hTop, hRight, hBottom)
            if (afterHandleX in pLeft..pRight && afterHandleY in pTop..pBottom) {
                return clampOutsideRect(afterHandleX, afterHandleY, pLeft, pTop, pRight, pBottom)
            }
            return Pair(afterHandleX, afterHandleY)
        }

        return Pair(afterPanelX, afterPanelY)
    }

    private fun updateCursorPosition(newX: Float, newY: Float) {
        var clampedX = newX.coerceIn(0f, screenWidth.toFloat())
        var clampedY = newY.coerceIn(0f, screenHeight.toFloat())

        // Mode DINDING pada Touchpad
        if (touchSettingsState.value.inputMode == InputMode.TOUCHPAD && panelBehaviorState.value == PanelBehavior.DINDING) {
            val (walledX, walledY) = applyDindingMode(clampedX, clampedY)
            clampedX = walledX.coerceIn(0f, screenWidth.toFloat())
            clampedY = walledY.coerceIn(0f, screenHeight.toFloat())
        }

        cursorXState.floatValue = clampedX
        cursorYState.floatValue = clampedY

        // Kelancaran: Posisi kursor diperbarui paling banyak sekali per frame (Choreographer)
        scheduleCursorWindowUpdate()

        val held = heldButtonState.intValue
        if (held != 0) {
            backendManager.moveHeld(clampedX, clampedY, held)
        } else {
            backendManager.move(clampedX, clampedY)
        }
    }

    private fun onPanelTouchStart() {
        if (panelBehaviorState.value != PanelBehavior.TEMBUS) return
        activeTouchCount++
        mainHandler.removeCallbacks(restoreTouchableRunnable)

        if (!isFlagNotTouchableSet) {
            isFlagNotTouchableSet = true
            applyFlagNotTouchable(true)
        }
    }

    private fun onPanelTouchEnd(isCancel: Boolean) {
        if (panelBehaviorState.value != PanelBehavior.TEMBUS) return
        activeTouchCount = (activeTouchCount - 1).coerceAtLeast(0)

        if (isCancel) {
            Log.w(TAG, "ACTION_CANCEL terdeteksi pada gesture panel! Memulihkan flag segera dan mereset state touchpad.")
            mainHandler.removeCallbacks(restoreTouchableRunnable)
            removeFlagNotTouchable()
            resetTouchpadTriggerState.intValue++
            activeTouchCount = 0
            return
        }

        if (activeTouchCount == 0) {
            mainHandler.removeCallbacks(restoreTouchableRunnable)
            mainHandler.postDelayed(restoreTouchableRunnable, 80L)
        }
    }

    private fun applyFlagNotTouchable(notTouchable: Boolean) {
        panelParams?.let { pParams ->
            val oldFlags = pParams.flags
            pParams.flags = if (notTouchable) {
                pParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            } else {
                pParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            }
            if (pParams.flags != oldFlags && panelView != null && panelView?.isAttachedToWindow == true) {
                try {
                    windowManager.updateViewLayout(panelView, pParams)
                    logWindowFlagChange("panelView", oldFlags, pParams.flags)
                } catch (t: Throwable) {
                    Log.e(TAG, "Gagal updateViewLayout panelView FLAG_NOT_TOUCHABLE=$notTouchable: ${t.message}")
                }
            }
        }

        handleParams?.let { hParams ->
            val oldFlags = hParams.flags
            hParams.flags = if (notTouchable) {
                hParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            } else {
                hParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            }
            if (hParams.flags != oldFlags && handleView != null && handleView?.isAttachedToWindow == true) {
                try {
                    windowManager.updateViewLayout(handleView, hParams)
                    logWindowFlagChange("handleView", oldFlags, hParams.flags)
                } catch (t: Throwable) {
                    Log.e(TAG, "Gagal updateViewLayout handleView FLAG_NOT_TOUCHABLE=$notTouchable: ${t.message}")
                }
            }
        }
    }

    private fun removeFlagNotTouchable() {
        if (isFlagNotTouchableSet) {
            isFlagNotTouchableSet = false
            applyFlagNotTouchable(false)
        }
    }

    private fun applyPanelBehavior(behavior: PanelBehavior) {
        panelBehaviorState.value = behavior
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PANEL_BEHAVIOR, behavior.name).apply()
        Log.i(TAG, "Perilaku panel diubah ke: ${behavior.name} (${behavior.displayName})")
        showToast("Perilaku panel: ${behavior.displayName}")

        if (behavior != PanelBehavior.TEMBUS) {
            mainHandler.removeCallbacks(restoreTouchableRunnable)
            removeFlagNotTouchable()
        }
    }

    private fun applyRecordMode(mode: RecordMode, saveToPrefs: Boolean = true) {
        recordModeState.value = mode
        if (saveToPrefs) {
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_RECORD_MODE, mode.name).apply()
        }

        Log.i(TAG, "Menerapkan Mode Rekam: ${mode.name} (${mode.displayName})")
        val isSecure = (mode == RecordMode.SECURE)

        panelParams?.let { pParams ->
            val oldFlags = pParams.flags
            pParams.flags = if (isSecure) {
                pParams.flags or WindowManager.LayoutParams.FLAG_SECURE
            } else {
                pParams.flags and WindowManager.LayoutParams.FLAG_SECURE.inv()
            }
            if (panelView != null && panelView?.isAttachedToWindow == true && pParams.flags != oldFlags) {
                try {
                    windowManager.updateViewLayout(panelView, pParams)
                    logWindowFlagChange("panelView", oldFlags, pParams.flags)
                } catch (_: Throwable) {}
            }
        }

        handleParams?.let { hParams ->
            val oldFlags = hParams.flags
            hParams.flags = if (isSecure) {
                hParams.flags or WindowManager.LayoutParams.FLAG_SECURE
            } else {
                hParams.flags and WindowManager.LayoutParams.FLAG_SECURE.inv()
            }
            if (handleView != null && handleView?.isAttachedToWindow == true && hParams.flags != oldFlags) {
                try {
                    windowManager.updateViewLayout(handleView, hParams)
                    logWindowFlagChange("handleView", oldFlags, hParams.flags)
                } catch (_: Throwable) {}
            }
        }

        cursorParams?.let { cParams ->
            val oldFlags = cParams.flags
            if ((cParams.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0) {
                cParams.flags = cParams.flags and WindowManager.LayoutParams.FLAG_SECURE.inv()
                if (cursorView != null && cursorView?.isAttachedToWindow == true && cParams.flags != oldFlags) {
                    try {
                        windowManager.updateViewLayout(cursorView, cParams)
                        logWindowFlagChange("cursorView", oldFlags, cParams.flags)
                    } catch (_: Throwable) {}
                }
            }
        }

        updateNotification(isHiddenState.value)
    }

    private fun lockButton(button: Int) {
        val current = heldButtonState.intValue
        if (current != 0 && current != button) {
            backendManager.release(cursorXState.floatValue, cursorYState.floatValue, current)
        }
        val success = backendManager.pressDown(cursorXState.floatValue, cursorYState.floatValue, button)
        if (success) {
            heldButtonState.intValue = button
            val name = if (button == InputBackendManager.BUTTON_LEFT) "LEFT" else "RIGHT"
            showToast("CLICK $name terkunci (aktif)")
        } else {
            heldButtonState.intValue = 0
            showToast("Gagal mengunci: backend belum siap")
        }
    }

    private fun unlockButton() {
        val current = heldButtonState.intValue
        if (current != 0) {
            backendManager.release(cursorXState.floatValue, cursorYState.floatValue, current)
            heldButtonState.intValue = 0
            showToast("Tombol mouse dilepas")
        }
    }

    /**
     * Mengatur posisi panel dan handle HANYA saat digeser atau saat rotasi layar.
     * Tidak dipanggil berulang di setiap event sentuhan biasa untuk mencegah getaran pada touchpad.
     */
    private fun setPanelAndHandlePosition(newX: Int, newY: Int) {
        val density = resources.displayMetrics.density
        val gapPx = (10 * density).toInt()
        val handleW = (140 * density).toInt()
        val handleH = (32 * density).toInt()

        val panelW = if (panelView != null && panelView!!.width > 0) panelView!!.width
                     else panelParams?.width ?: minOf((screenWidth * 0.9f).toInt(), (340 * density).toInt())
        val panelH = if (panelView != null && panelView!!.height > 0) panelView!!.height
                     else (310 * density).toInt()

        val totalHeight = panelH + gapPx + handleH
        val maxX = (screenWidth - panelW).coerceAtLeast(0)
        val maxY = (screenHeight - totalHeight).coerceAtLeast(0)

        val clampedX = newX.coerceIn(0, maxX)
        val clampedY = newY.coerceIn(0, maxY)

        panelParams?.let { pParams ->
            if (pParams.x != clampedX || pParams.y != clampedY) {
                pParams.x = clampedX
                pParams.y = clampedY
                try {
                    if (panelView != null && panelView?.isAttachedToWindow == true) {
                        windowManager.updateViewLayout(panelView, pParams)
                    }
                } catch (_: Throwable) {}
            }
        }

        handleParams?.let { hParams ->
            val targetHX = clampedX + (panelW - handleW) / 2
            val targetHY = clampedY + panelH + gapPx
            if (hParams.x != targetHX || hParams.y != targetHY) {
                hParams.x = targetHX
                hParams.y = targetHY
                try {
                    if (handleView != null && handleView?.isAttachedToWindow == true) {
                        windowManager.updateViewLayout(handleView, hParams)
                    }
                } catch (_: Throwable) {}
            }
        }
    }

    private fun clampAndUpdateOverlays() {
        val panelW = if (panelView != null && panelView!!.width > 0) panelView!!.width
                     else minOf((screenWidth * 0.9f).toInt(), (340 * resources.displayMetrics.density).toInt())

        panelParams?.let { pParams ->
            pParams.width = panelW
            setPanelAndHandlePosition(pParams.x, pParams.y)
        }
    }

    private fun dragControlPanel(dx: Float, dy: Float) {
        panelParams?.let { pParams ->
            setPanelAndHandlePosition(pParams.x + dx.toInt(), pParams.y + dy.toInt())
        }
    }

    private fun hideOverlayViews() {
        unlockButton()
        mainHandler.removeCallbacks(restoreTouchableRunnable)
        removeFlagNotTouchable()
        setCaptureFlagNotTouchable(false)

        isHiddenState.value = true

        // Sembunyikan semua jendela
        cursorView?.visibility = View.GONE
        panelView?.visibility = View.GONE
        handleView?.visibility = View.GONE
        fullscreenCaptureView?.visibility = View.GONE

        updateNotification(isHidden = true)
        Log.i(TAG, "Overlay disembunyikan")
        showToast("Overlay disembunyikan. Gunakan notifikasi untuk menampilkan.")
    }

    private fun showOverlayViews() {
        readScreenDimensions()
        isHiddenState.value = false

        initCursorOverlay()
        cursorView?.visibility = View.VISIBLE

        val currentMode = touchSettingsState.value.inputMode
        if (currentMode == InputMode.TOUCHSCREEN) {
            removeControlPanelOverlay()
            removeHandleOverlay()
            initFullscreenCaptureOverlay()
            fullscreenCaptureView?.visibility = View.VISIBLE
        } else {
            removeFullscreenCaptureOverlay()
            initControlPanelOverlay()
            initHandleOverlay()
            panelView?.visibility = View.VISIBLE
            handleView?.visibility = View.VISIBLE
            clampAndUpdateOverlays()
        }

        updateNotification(isHidden = false)
        Log.i(TAG, "Overlay ditampilkan kembali (Mode: ${currentMode.displayName})")
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

    /**
     * Notifikasi foreground memakai MAKSIMAL 3 AKSI:
     * 1. [Sembunyikan/Tampilkan]: satu tombol, label berganti sesuai status.
     * 2. [Mode: Sentuh/Touchpad]: berganti mode input, label menunjukkan mode saat ini.
     * 3. [Stop].
     */
    private fun buildNotification(isHidden: Boolean): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Aksi 1: [Sembunyikan/Tampilkan]
        val toggleVisibilityIntent = Intent(this, OverlayService::class.java).apply {
            action = ACTION_TOGGLE_VISIBILITY
        }
        val toggleVisibilityPending = PendingIntent.getService(
            this, 1, toggleVisibilityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val visibilityActionTitle = if (isHidden) "Tampilkan" else "Sembunyikan"
        val visibilityActionIcon = if (isHidden) android.R.drawable.ic_menu_view else android.R.drawable.ic_menu_close_clear_cancel
        val visibilityAction = NotificationCompat.Action.Builder(
            visibilityActionIcon,
            visibilityActionTitle,
            toggleVisibilityPending
        ).build()

        // Aksi 2: [Mode: Sentuh/Touchpad] (label menunjukkan mode saat ini)
        val currentInputMode = touchSettingsState.value.inputMode
        val inputModeToggleIntent = Intent(this, OverlayService::class.java).apply {
            action = ACTION_TOGGLE_INPUT_MODE
        }
        val inputModePending = PendingIntent.getService(
            this, 2, inputModeToggleIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val modeActionTitle = if (currentInputMode == InputMode.TOUCHSCREEN) "Mode: Sentuh" else "Mode: Touchpad"
        val modeAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_compass,
            modeActionTitle,
            inputModePending
        ).build()

        // Aksi 3: [Stop]
        val stopIntent = Intent(this, OverlayService::class.java).apply {
            action = ACTION_STOP
        }
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
            .setContentTitle("RightClickDash [${currentInputMode.displayName}]")
            .setContentText(
                if (isHidden) "Overlay tersembunyi"
                else "Kursor di X: ${cursorXState.floatValue.toInt()}, Y: ${cursorYState.floatValue.toInt()}"
            )
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .addAction(visibilityAction)
            .addAction(modeAction)
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
        unlockButton()
        mainHandler.removeCallbacks(restoreTouchableRunnable)
        mainHandler.removeCallbacks(longPressRunnable)
        removeFlagNotTouchable()
        setCaptureFlagNotTouchable(false)

        cursorView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Throwable) {}
        }
        cursorView = null

        removeControlPanelOverlay()
        removeHandleOverlay()
        removeFullscreenCaptureOverlay()

        lifecycleOwner.onDestroy()
        Log.i(TAG, "OverlayService onDestroy selesai")
        super.onDestroy()
    }
}
