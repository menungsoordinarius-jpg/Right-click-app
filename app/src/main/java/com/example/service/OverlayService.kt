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
import com.example.ui.overlay.FloatingBubble
import com.example.ui.overlay.InputMode
import com.example.ui.overlay.PanelBehavior
import com.example.ui.overlay.RecordMode
import com.example.ui.overlay.SeparateDragHandle
import com.example.ui.overlay.SettingsDialog
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
        const val ACTION_TOGGLE_RECORD_MODE = "com.example.action.TOGGLE_RECORD_MODE"
        const val ACTION_TOGGLE_INPUT_MODE = "com.example.action.TOGGLE_INPUT_MODE"

        private const val PREFS_NAME = "right_click_dash_prefs"
        private const val KEY_RECORD_MODE = "KEY_RECORD_MODE"
        private const val KEY_LAST_ACTIVE_RECORD_MODE = "KEY_LAST_ACTIVE_RECORD_MODE"
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

    // 2. Touchpad Virtual Overlays
    private var panelView: ComposeView? = null
    private var panelParams: WindowManager.LayoutParams? = null

    private var handleView: ComposeView? = null
    private var handleParams: WindowManager.LayoutParams? = null

    // 3. Mode Layar Sentuh: Fullscreen Capture Window
    private var fullscreenCaptureView: View? = null
    private var fullscreenCaptureParams: WindowManager.LayoutParams? = null
    private var isCaptureFlagNotTouchable = false

    // 4. Floating Bubble Overlay (selalu interaktif di atas penangkap)
    private var bubbleView: ComposeView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    // 5. Settings Dialog Overlay
    private var settingsDialogView: ComposeView? = null
    private var settingsDialogParams: WindowManager.LayoutParams? = null
    private val isSettingsDialogOpen = mutableStateOf(false)

    // Screen Dimensions
    private var screenWidth = 1080
    private var screenHeight = 2400

    // Coordinates & UI States
    private val cursorXState = mutableFloatStateOf(540f)
    private val cursorYState = mutableFloatStateOf(1200f)

    private val isHiddenState = mutableStateOf(false)
    private val heldButtonState = mutableIntStateOf(0)
    private val recordModeState = mutableStateOf(RecordMode.OFF)
    private val panelBehaviorState = mutableStateOf(PanelBehavior.TEMBUS)
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
                // Jari menyentuh lagi dalam DOUBLE_TAP_MS: diam sampai LONG_PRESS -> pressDown(KANAN)
                Log.i(TAG, "Mode Layar Sentuh: diam sampai LONG_PRESS setelah double tap -> pressDown KANAN")
                heldButtonState.intValue = InputBackendManager.BUTTON_RIGHT
                backendManager.pressDown(cursorXState.floatValue, cursorYState.floatValue, InputBackendManager.BUTTON_RIGHT)
            } else {
                // Jari 1 menyentuh: tetap diam sampai LONG_PRESS -> pressDown(KIRI)
                Log.i(TAG, "Mode Layar Sentuh: tetap diam sampai LONG_PRESS -> pressDown KIRI")
                heldButtonState.intValue = InputBackendManager.BUTTON_LEFT
                backendManager.pressDown(cursorXState.floatValue, cursorYState.floatValue, InputBackendManager.BUTTON_LEFT)
            }
        }
    }

    // State untuk Choreographer VSync Throttling (Kelancaran kursor overlay)
    private var isCursorFrameScheduled = false
    private val cursorFrameCallback = Choreographer.FrameCallback {
        isCursorFrameScheduled = false
        val cursorDensity = resources.displayMetrics.density
        val tipOffsetPx = CURSOR_TIP_OFFSET.value * cursorDensity
        cursorParams?.let { params ->
            params.x = (cursorXState.floatValue - tipOffsetPx).toInt()
            params.y = (cursorYState.floatValue - tipOffsetPx).toInt()
            try {
                if (cursorView != null && cursorView?.isAttachedToWindow == true) {
                    windowManager.updateViewLayout(cursorView, params)
                }
            } catch (_: Throwable) {}
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

        val savedBehaviorName = prefs.getString(KEY_PANEL_BEHAVIOR, PanelBehavior.TEMBUS.name)
        val initialBehavior = try {
            PanelBehavior.valueOf(savedBehaviorName ?: PanelBehavior.TEMBUS.name)
        } catch (_: Exception) {
            PanelBehavior.TEMBUS
        }
        panelBehaviorState.value = initialBehavior

        Log.i(TAG, "OverlayService onCreate: Mode Input=${loadedSettings.inputMode.name}, Mode Rekam=${initialRecordMode.name}")

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(isHidden = false))

        initCursorOverlay()
        initControlPanelOverlay()
        initHandleOverlay()
        initFullscreenCaptureOverlay()
        initBubbleOverlay()
        initSettingsDialogOverlay()

        applyRecordMode(initialRecordMode, saveToPrefs = false)
        applyInputMode(loadedSettings.inputMode, centerCursor = true, saveToPrefs = false)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Log.i(TAG, "Menerima aksi ACTION_STOP -> menghentikan OverlayService")
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SHOW -> {
                Log.i(TAG, "Menerima aksi ACTION_SHOW")
                showOverlayViews()
            }
            ACTION_HIDE -> {
                Log.i(TAG, "Menerima aksi ACTION_HIDE")
                hideOverlayViews()
            }
            ACTION_TOGGLE_RECORD_MODE -> {
                toggleRecordModeFromNotification()
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
        clampBubblePosition()
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
        Log.d(TAG, "readScreenDimensions: screenWidth=$screenWidth, screenHeight=$screenHeight")
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

    private fun initCursorOverlay() {
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
        } catch (t: Throwable) {
            Log.e(TAG, "Failed adding cursor overlay: ${t.message}")
        }
    }

    private fun initControlPanelOverlay() {
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
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                clampAndUpdateOverlays()
            }

            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> onPanelTouchStart()
                    MotionEvent.ACTION_UP -> onPanelTouchEnd(isCancel = false)
                    MotionEvent.ACTION_CANCEL -> {
                        Log.w(TAG, "ACTION_CANCEL terdeteksi pada panelView!")
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
                        onOpenSettings = { isSettingsDialogOpen.value = true },
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
        } catch (t: Throwable) {
            Log.e(TAG, "Failed adding panel overlay: ${t.message}")
        }
    }

    private fun initHandleOverlay() {
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
        } catch (t: Throwable) {
            Log.e(TAG, "Failed adding handle overlay: ${t.message}")
        }
    }

    /**
     * Inisialisasi Jendela Penangkap Layar Penuh untuk Mode Layar Sentuh.
     */
    private fun initFullscreenCaptureOverlay() {
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
            handleFullscreenTouchEvent(event)
        }

        fullscreenCaptureView = capture
        try {
            windowManager.addView(fullscreenCaptureView, fullscreenCaptureParams)
            Log.i(TAG, "Fullscreen capture overlay berhasil ditambahkan.")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed adding fullscreen capture overlay: ${t.message}")
        }
    }

    /**
     * Memasang atau melepas FLAG_NOT_TOUCHABLE pada fullscreenCaptureView untuk Mode TEMBUS.
     */
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
                val action = if (notTouchable) "dipasang pada" else "dicabut dari"
                Log.i(TAG, "Perubahan flag: FLAG_NOT_TOUCHABLE $action fullscreenCaptureView")
            } catch (t: Throwable) {
                Log.e(TAG, "Gagal updateViewLayout captureView FLAG_NOT_TOUCHABLE: ${t.message}")
            }
        }
    }

    /**
     * State Machine Penanganan Event Sentuhan pada Mode Layar Sentuh.
     */
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
                                // Jari menyentuh lagi dalam DOUBLE_TAP_MS: bergeser > SLOP -> pressDown(KANAN)
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
                Log.w(TAG, "ACTION_CANCEL terdeteksi pada fullscreenCaptureView!")
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

    /**
     * Inisialisasi Floating Bubble kecil (selalu bisa disentuh di atas penangkap).
     */
    private fun initBubbleOverlay() {
        val density = resources.displayMetrics.density
        val sizePx = (52 * density).toInt()

        bubbleParams = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            if (recordModeState.value == RecordMode.SECURE) {
                flags = flags or WindowManager.LayoutParams.FLAG_SECURE
            }
            format = PixelFormat.TRANSLUCENT
            width = sizePx
            height = sizePx
            gravity = Gravity.TOP or Gravity.START
            x = (screenWidth - sizePx - (16 * density).toInt()).coerceAtLeast(10)
            y = (screenHeight * 0.20f).toInt()
        }

        bubbleView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                RightClickDashTheme {
                    val backendState by backendManager.backendState.collectAsState()

                    FloatingBubble(
                        backendState = backendState,
                        inputMode = touchSettingsState.value.inputMode,
                        recordMode = recordModeState.value,
                        onToggleInputMode = { targetMode ->
                            applyInputMode(targetMode, centerCursor = true, saveToPrefs = true)
                        },
                        onOpenSettings = {
                            isSettingsDialogOpen.value = true
                        },
                        onRecheckBackend = {
                            backendManager.recheckStatus()
                        },
                        onHideOverlay = {
                            hideOverlayViews()
                        },
                        onDrag = { dx, dy ->
                            dragBubble(dx, dy)
                        }
                    )
                }
            }
        }

        try {
            windowManager.addView(bubbleView, bubbleParams)
            Log.i(TAG, "Floating bubble overlay berhasil ditambahkan.")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed adding floating bubble: ${t.message}")
        }
    }

    private fun dragBubble(dx: Float, dy: Float) {
        val bParams = bubbleParams ?: return
        val density = resources.displayMetrics.density
        val sizePx = (52 * density).toInt()
        val maxX = (screenWidth - sizePx).coerceAtLeast(0)
        val maxY = (screenHeight - sizePx).coerceAtLeast(0)

        bParams.x = (bParams.x + dx.toInt()).coerceIn(0, maxX)
        bParams.y = (bParams.y + dy.toInt()).coerceIn(0, maxY)

        try {
            if (bubbleView != null && bubbleView?.isAttachedToWindow == true) {
                windowManager.updateViewLayout(bubbleView, bParams)
            }
        } catch (_: Throwable) {}
    }

    private fun clampBubblePosition() {
        val bParams = bubbleParams ?: return
        val density = resources.displayMetrics.density
        val sizePx = (52 * density).toInt()
        val maxX = (screenWidth - sizePx).coerceAtLeast(0)
        val maxY = (screenHeight - sizePx).coerceAtLeast(0)

        bParams.x = bParams.x.coerceIn(0, maxX)
        bParams.y = bParams.y.coerceIn(0, maxY)

        try {
            if (bubbleView != null && bubbleView?.isAttachedToWindow == true) {
                windowManager.updateViewLayout(bubbleView, bParams)
            }
        } catch (_: Throwable) {}
    }

    /**
     * Inisialisasi Overlay Dialog Pengaturan.
     */
    private fun initSettingsDialogOverlay() {
        settingsDialogParams = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            format = PixelFormat.TRANSLUCENT
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            gravity = Gravity.CENTER
        }

        settingsDialogView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            visibility = View.GONE
            setContent {
                RightClickDashTheme {
                    if (isSettingsDialogOpen.value) {
                        SettingsDialog(
                            initialSettings = touchSettingsState.value,
                            onDismissRequest = {
                                isSettingsDialogOpen.value = false
                                settingsDialogView?.visibility = View.GONE
                            },
                            onSaveSettings = { updated ->
                                touchSettingsState.value = updated
                                TouchSettings.save(applicationContext, updated)
                                applyInputMode(updated.inputMode, centerCursor = false, saveToPrefs = false)
                                showToast("Pengaturan disimpan")
                            }
                        )
                    }
                }
            }
        }

        try {
            windowManager.addView(settingsDialogView, settingsDialogParams)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed adding settings dialog overlay: ${t.message}")
        }
    }

    /**
     * Memperbarui mode input aktif (Layar Sentuh vs Touchpad).
     */
    private fun applyInputMode(mode: InputMode, centerCursor: Boolean = false, saveToPrefs: Boolean = true) {
        val currentSettings = touchSettingsState.value
        if (currentSettings.inputMode != mode) {
            // Tombol tertahan WAJIB dilepas saat mode dialihkan
            unlockButton()
        }

        val updated = currentSettings.copy(inputMode = mode)
        touchSettingsState.value = updated
        if (saveToPrefs) {
            TouchSettings.save(this, updated)
        }

        Log.i(TAG, "Menerapkan Mode Input: ${mode.name} (${mode.displayName})")

        // Saat mode Layar Sentuh diaktifkan, kursor muncul di TENGAH layar
        if (mode == InputMode.TOUCHSCREEN && centerCursor) {
            cursorXState.floatValue = screenWidth / 2f
            cursorYState.floatValue = screenHeight / 2f
            scheduleCursorWindowUpdate()
        }

        if (!isHiddenState.value) {
            if (mode == InputMode.TOUCHSCREEN) {
                // Sembunyikan panel & handle lama
                panelView?.visibility = View.GONE
                handleView?.visibility = View.GONE
                // Tampilkan penangkap layar penuh & bubble
                fullscreenCaptureView?.visibility = View.VISIBLE
                bubbleView?.visibility = View.VISIBLE
            } else {
                // Tampilkan panel & handle touchpad lama
                fullscreenCaptureView?.visibility = View.GONE
                panelView?.visibility = View.VISIBLE
                handleView?.visibility = View.VISIBLE
                bubbleView?.visibility = View.VISIBLE
            }
        }

        updateNotification(isHiddenState.value)
        showToast("Mode Input: ${mode.displayName}")
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

        // Kirim event gerak ke backend thread (akan di-coalesce di InputServiceImpl)
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
        var panelChanged = false
        var handleChanged = false

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
                    panelChanged = true
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
                    handleChanged = true
                } catch (t: Throwable) {
                    Log.e(TAG, "Gagal updateViewLayout handleView FLAG_NOT_TOUCHABLE=$notTouchable: ${t.message}")
                }
            }
        }

        if (panelChanged || handleChanged) {
            val actionName = if (notTouchable) "dipasang pada" else "dicabut dari"
            Log.i(TAG, "Perubahan flag: FLAG_NOT_TOUCHABLE $actionName jendela panel dan handle")
        }
    }

    private fun removeFlagNotTouchable() {
        if (isFlagNotTouchableSet) {
            isFlagNotTouchableSet = false
            applyFlagNotTouchable(false)
            Log.i(TAG, "Perubahan flag: FLAG_NOT_TOUCHABLE berhasil dipulihkan (interaktif kembali)")
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
            if (mode != RecordMode.OFF) {
                prefs.edit().putString(KEY_LAST_ACTIVE_RECORD_MODE, mode.name).apply()
            }
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
                } catch (_: Throwable) {}
            }
        }

        bubbleParams?.let { bParams ->
            val oldFlags = bParams.flags
            bParams.flags = if (isSecure) {
                bParams.flags or WindowManager.LayoutParams.FLAG_SECURE
            } else {
                bParams.flags and WindowManager.LayoutParams.FLAG_SECURE.inv()
            }
            if (bubbleView != null && bubbleView?.isAttachedToWindow == true && bParams.flags != oldFlags) {
                try {
                    windowManager.updateViewLayout(bubbleView, bParams)
                } catch (_: Throwable) {}
            }
        }

        // Kursor overlay TIDAK diberi FLAG_SECURE
        cursorParams?.let { cParams ->
            if ((cParams.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0) {
                cParams.flags = cParams.flags and WindowManager.LayoutParams.FLAG_SECURE.inv()
                if (cursorView != null && cursorView?.isAttachedToWindow == true) {
                    try {
                        windowManager.updateViewLayout(cursorView, cParams)
                    } catch (_: Throwable) {}
                }
            }
        }

        updateNotification(isHiddenState.value)
        showToast("Mode Rekam: ${mode.displayName}")
    }

    private fun toggleRecordModeFromNotification() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (recordModeState.value == RecordMode.OFF) {
            val lastActiveName = prefs.getString(KEY_LAST_ACTIVE_RECORD_MODE, RecordMode.SECURE.name)
            val targetMode = try {
                RecordMode.valueOf(lastActiveName ?: RecordMode.SECURE.name)
            } catch (_: Exception) {
                RecordMode.SECURE
            }
            Log.i(TAG, "Notifikasi: Mengaktifkan Mode Rekam -> ${targetMode.name}")
            applyRecordMode(targetMode)
        } else {
            Log.i(TAG, "Notifikasi: Menonaktifkan Mode Rekam -> OFF")
            applyRecordMode(RecordMode.OFF)
        }
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
            pParams.x = clampedX
            pParams.y = clampedY
            try {
                if (panelView != null && panelView?.isAttachedToWindow == true) {
                    windowManager.updateViewLayout(panelView, pParams)
                }
            } catch (_: Throwable) {}
        }

        handleParams?.let { hParams ->
            hParams.x = clampedX + (panelW - handleW) / 2
            hParams.y = clampedY + panelH + gapPx
            try {
                if (handleView != null && handleView?.isAttachedToWindow == true) {
                    windowManager.updateViewLayout(handleView, hParams)
                }
            } catch (_: Throwable) {}
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
        cursorView?.visibility = View.GONE
        panelView?.visibility = View.GONE
        handleView?.visibility = View.GONE
        fullscreenCaptureView?.visibility = View.GONE
        bubbleView?.visibility = View.GONE
        settingsDialogView?.visibility = View.GONE

        updateNotification(isHidden = true)
        Log.i(TAG, "Overlay disembunyikan")
        showToast("Overlay disembunyikan. Gunakan notifikasi untuk menampilkan.")
    }

    private fun showOverlayViews() {
        readScreenDimensions()
        clampAndUpdateOverlays()
        clampBubblePosition()

        isHiddenState.value = false
        cursorView?.visibility = View.VISIBLE

        if (touchSettingsState.value.inputMode == InputMode.TOUCHSCREEN) {
            panelView?.visibility = View.GONE
            handleView?.visibility = View.GONE
            fullscreenCaptureView?.visibility = View.VISIBLE
            bubbleView?.visibility = View.VISIBLE
        } else {
            fullscreenCaptureView?.visibility = View.GONE
            panelView?.visibility = View.VISIBLE
            handleView?.visibility = View.VISIBLE
            bubbleView?.visibility = View.VISIBLE
        }

        updateNotification(isHidden = false)
        Log.i(TAG, "Overlay ditampilkan kembali")
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

        // Action ganti Mode Input
        val currentInputMode = touchSettingsState.value.inputMode
        val inputModeToggleIntent = Intent(this, OverlayService::class.java).apply { action = ACTION_TOGGLE_INPUT_MODE }
        val inputModePending = PendingIntent.getService(
            this, 5, inputModeToggleIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val inputModeActionTitle = if (currentInputMode == InputMode.TOUCHSCREEN) "Ke Touchpad" else "Ke Layar Sentuh"
        val inputModeAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_compass,
            inputModeActionTitle,
            inputModePending
        ).build()

        val currentMode = recordModeState.value
        val recordToggleIntent = Intent(this, OverlayService::class.java).apply { action = ACTION_TOGGLE_RECORD_MODE }
        val recordPending = PendingIntent.getService(
            this, 4, recordToggleIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val recordActionTitle = if (currentMode == RecordMode.OFF) {
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastMode = prefs.getString(KEY_LAST_ACTIVE_RECORD_MODE, RecordMode.SECURE.name)
            val label = if (lastMode == RecordMode.TRANSPARENT.name) "Transparan" else "Sembunyikan"
            "Rekam: $label"
        } else {
            "Rekam: OFF"
        }
        val recordAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_camera,
            recordActionTitle,
            recordPending
        ).build()

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

        val modeBadge = if (currentMode != RecordMode.OFF) " [${currentMode.displayName}]" else ""

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RightClickDash [${currentInputMode.displayName}]$modeBadge")
            .setContentText(
                if (isHidden) "Overlay tersembunyi"
                else "Kursor di X: ${cursorXState.floatValue.toInt()}, Y: ${cursorYState.floatValue.toInt()}"
            )
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .addAction(toggleAction)
            .addAction(inputModeAction)
            .addAction(recordAction)
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

        panelView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Throwable) {}
        }
        panelView = null

        handleView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Throwable) {}
        }
        handleView = null

        fullscreenCaptureView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Throwable) {}
        }
        fullscreenCaptureView = null

        bubbleView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Throwable) {}
        }
        bubbleView = null

        settingsDialogView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Throwable) {}
        }
        settingsDialogView = null

        lifecycleOwner.onDestroy()
        Log.i(TAG, "OverlayService onDestroy selesai")
        super.onDestroy()
    }
}
