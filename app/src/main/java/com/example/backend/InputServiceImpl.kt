package com.example.backend

import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import androidx.annotation.Keep
import com.example.IInputService
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.Method

/**
 * Singleton implementation of IInputService that injects genuine mouse events
 * into Android InputManager using HiddenApiBypass and reflection.
 * Hosted by both Shizuku UserService and libsu RootService.
 */
@Keep
class InputServiceImpl : IInputService.Stub() {

    companion object {
        private const val TAG = "RCD"
        private const val INJECT_INPUT_EVENT_MODE_ASYNC = 0

        @Volatile
        private var instance: InputServiceImpl? = null

        fun getInstance(): InputServiceImpl {
            return instance ?: synchronized(this) {
                instance ?: InputServiceImpl().also { instance = it }
            }
        }
    }

    private val handlerThread: HandlerThread = HandlerThread("RightClickDash-InputThread").apply {
        start()
    }
    private val handler: Handler = Handler(handlerThread.looper)

    private var inputManagerInstance: Any? = null
    private var injectInputEventMethod: Method? = null
    private var setDisplayIdMethod: Method? = null

    init {
        initHiddenApi()
    }

    private fun initHiddenApi() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                HiddenApiBypass.addHiddenApiExemptions("")
            }

            val inputManagerClass = Class.forName("android.hardware.input.InputManager")
            val getInstanceMethod = inputManagerClass.getDeclaredMethod("getInstance")
            getInstanceMethod.isAccessible = true
            inputManagerInstance = getInstanceMethod.invoke(null)

            val eventClass = Class.forName("android.view.InputEvent")
            injectInputEventMethod = inputManagerClass.getDeclaredMethod(
                "injectInputEvent",
                eventClass,
                Int::class.javaPrimitiveType
            )
            injectInputEventMethod?.isAccessible = true

            try {
                setDisplayIdMethod = MotionEvent::class.java.getDeclaredMethod(
                    "setDisplayId",
                    Int::class.javaPrimitiveType
                )
                setDisplayIdMethod?.isAccessible = true
            } catch (t: Throwable) {
                Log.w(TAG, "setDisplayId reflection fallback: ${t.message}")
            }

            Log.i(TAG, "Hidden API initialized successfully on PID ${android.os.Process.myPid()}")
        } catch (e: Throwable) {
            Log.e(TAG, "Error initializing hidden API: ${e.message}", e)
        }
    }

    override fun click(x: Float, y: Float, button: Int) {
        handler.post {
            executeClick(x, y, button)
        }
    }

    override fun hold(x: Float, y: Float, button: Int, durationMs: Long) {
        handler.post {
            executeHold(x, y, button, durationMs.coerceIn(100L, 10000L))
        }
    }

    override fun move(x: Float, y: Float) {
        handler.post {
            executeMove(x, y)
        }
    }

    private fun executeClick(x: Float, y: Float, button: Int) {
        val now = SystemClock.uptimeMillis()

        // 1. ACTION_HOVER_MOVE to point
        injectEvent(createMouseEvent(MotionEvent.ACTION_HOVER_MOVE, x, y, 0, 0, now, now))
        SystemClock.sleep(12)

        // 2. ACTION_DOWN (buttonState = tombol)
        val downTime = SystemClock.uptimeMillis()
        injectEvent(createMouseEvent(MotionEvent.ACTION_DOWN, x, y, button, 0, downTime, downTime))
        SystemClock.sleep(12)

        // 3. ACTION_BUTTON_PRESS
        val pressTime = SystemClock.uptimeMillis()
        injectEvent(createMouseEvent(MotionEvent.ACTION_BUTTON_PRESS, x, y, button, button, downTime, pressTime))
        SystemClock.sleep(35)

        // 4. ACTION_BUTTON_RELEASE
        val releaseTime = SystemClock.uptimeMillis()
        injectEvent(createMouseEvent(MotionEvent.ACTION_BUTTON_RELEASE, x, y, 0, button, downTime, releaseTime))
        SystemClock.sleep(12)

        // 5. ACTION_UP (buttonState = 0)
        val upTime = SystemClock.uptimeMillis()
        injectEvent(createMouseEvent(MotionEvent.ACTION_UP, x, y, 0, 0, downTime, upTime))
    }

    private fun executeHold(x: Float, y: Float, button: Int, durationMs: Long) {
        val now = SystemClock.uptimeMillis()

        // 1. ACTION_HOVER_MOVE to point
        injectEvent(createMouseEvent(MotionEvent.ACTION_HOVER_MOVE, x, y, 0, 0, now, now))
        SystemClock.sleep(12)

        // 2. ACTION_DOWN (buttonState = tombol)
        val downTime = SystemClock.uptimeMillis()
        injectEvent(createMouseEvent(MotionEvent.ACTION_DOWN, x, y, button, 0, downTime, downTime))
        SystemClock.sleep(12)

        // 3. ACTION_BUTTON_PRESS
        val pressTime = SystemClock.uptimeMillis()
        injectEvent(createMouseEvent(MotionEvent.ACTION_BUTTON_PRESS, x, y, button, button, downTime, pressTime))

        // Jeda sebesar durasi, kirim ACTION_MOVE berkala tiap ~50 ms
        val startTime = SystemClock.uptimeMillis()
        val targetEndTime = startTime + durationMs
        while (SystemClock.uptimeMillis() < targetEndTime) {
            SystemClock.sleep(50)
            val moveTime = SystemClock.uptimeMillis()
            injectEvent(createMouseEvent(MotionEvent.ACTION_MOVE, x, y, button, 0, downTime, moveTime))
        }

        // 4. ACTION_BUTTON_RELEASE
        val releaseTime = SystemClock.uptimeMillis()
        injectEvent(createMouseEvent(MotionEvent.ACTION_BUTTON_RELEASE, x, y, 0, button, downTime, releaseTime))
        SystemClock.sleep(12)

        // 5. ACTION_UP (buttonState = 0)
        val upTime = SystemClock.uptimeMillis()
        injectEvent(createMouseEvent(MotionEvent.ACTION_UP, x, y, 0, 0, downTime, upTime))
    }

    private fun executeMove(x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        injectEvent(createMouseEvent(MotionEvent.ACTION_HOVER_MOVE, x, y, 0, 0, now, now))
    }

    private fun createMouseEvent(
        action: Int,
        x: Float,
        y: Float,
        buttonState: Int,
        actionButton: Int,
        downTime: Long,
        eventTime: Long
    ): MotionEvent {
        val properties = arrayOf(
            MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_MOUSE
            }
        )

        val coords = arrayOf(
            MotionEvent.PointerCoords().apply {
                this.x = x
                this.y = y
                this.pressure = if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_HOVER_MOVE) 0f else 1f
                this.size = 1f
            }
        )

        val event = MotionEvent.obtain(
            downTime,
            eventTime,
            action,
            1,
            properties,
            coords,
            0, // metaState
            buttonState,
            1f, // xPrecision
            1f, // yPrecision
            0, // deviceId
            0, // edgeFlags
            InputDevice.SOURCE_MOUSE,
            0 // flags
        )

        if (actionButton != 0) {
            try {
                val method = MotionEvent::class.java.getMethod("setActionButton", Int::class.javaPrimitiveType)
                method.invoke(event, actionButton)
            } catch (_: Throwable) {
                try {
                    HiddenApiBypass.invoke(MotionEvent::class.java, event, "setActionButton", actionButton)
                } catch (_: Throwable) {}
            }
        }

        // Atur displayId lewat reflection (setDisplayId) ke display default (0)
        try {
            if (setDisplayIdMethod != null) {
                setDisplayIdMethod?.invoke(event, 0)
            } else {
                HiddenApiBypass.invoke(MotionEvent::class.java, event, "setDisplayId", 0)
            }
        } catch (_: Throwable) {
            // Some platforms ignore setDisplayId or it's implicitly 0
        }

        return event
    }

    private fun injectEvent(event: MotionEvent) {
        try {
            if (inputManagerInstance == null || injectInputEventMethod == null) {
                initHiddenApi()
            }

            if (inputManagerInstance != null && injectInputEventMethod != null) {
                injectInputEventMethod?.invoke(inputManagerInstance, event, INJECT_INPUT_EVENT_MODE_ASYNC)
            } else {
                // Secondary fallback via HiddenApiBypass
                val inputManagerClass = Class.forName("android.hardware.input.InputManager")
                val im = HiddenApiBypass.invoke(inputManagerClass, null, "getInstance")
                HiddenApiBypass.invoke(
                    inputManagerClass,
                    im,
                    "injectInputEvent",
                    event,
                    INJECT_INPUT_EVENT_MODE_ASYNC
                )
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed injecting MotionEvent (${event.action}): ${t.message}")
        } finally {
            event.recycle()
        }
    }
}
