package com.example.backend

import android.content.Context
import androidx.annotation.Keep
import com.example.IInputService
import kotlin.system.exitProcess

/**
 * Shizuku UserService running in elevated shell process.
 * Instantiated automatically by Shizuku via reflection.
 */
@Keep
class ShizukuUserService : IInputService.Stub {

    @Keep
    constructor() : super()

    @Keep
    constructor(@Suppress("UNUSED_PARAMETER") context: Context) : super()

    private val delegate: InputServiceImpl = InputServiceImpl.getInstance()

    override fun click(x: Float, y: Float, button: Int) {
        delegate.click(x, y, button)
    }

    override fun hold(x: Float, y: Float, button: Int, durationMs: Long) {
        delegate.hold(x, y, button, durationMs)
    }

    override fun move(x: Float, y: Float) {
        delegate.move(x, y)
    }

    override fun pressDown(x: Float, y: Float, button: Int) {
        delegate.pressDown(x, y, button)
    }

    override fun moveHeld(x: Float, y: Float, button: Int) {
        delegate.moveHeld(x, y, button)
    }

    override fun release(x: Float, y: Float, button: Int) {
        delegate.release(x, y, button)
    }

    override fun scroll(x: Float, y: Float, notches: Int) {
        delegate.scroll(x, y, notches)
    }

    /**
     * Called when Shizuku UserService is destroyed
     */
    @Keep
    fun destroy() {
        exitProcess(0)
    }
}
