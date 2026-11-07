package com.example.ui.overlay

import android.content.Context
import android.content.SharedPreferences

enum class InputMode(val displayName: String) {
    TOUCHSCREEN("Layar Sentuh"),
    TOUCHPAD("Touchpad")
}

data class TouchSettings(
    val inputMode: InputMode = InputMode.TOUCHSCREEN,
    val longPressMs: Long = 350L,
    val doubleTapMs: Long = 300L,
    val tapMaxMs: Long = 250L,
    val slopDp: Float = 10f,
    val twoFingerTapRightClick: Boolean = true,
    val twoFingerScrollNatural: Boolean = true,
    val baseGain: Float = 1.0f,
    val accel: Float = 3.0f,
    val maxGain: Float = 8.0f
) {
    companion object {
        const val PREFS_NAME = "right_click_dash_prefs"
        const val KEY_INPUT_MODE = "pref_input_mode"
        const val KEY_LONG_PRESS_MS = "pref_long_press_ms"
        const val KEY_DOUBLE_TAP_MS = "pref_double_tap_ms"
        const val KEY_TAP_MAX_MS = "pref_tap_max_ms"
        const val KEY_SLOP_DP = "pref_slop_dp"
        const val KEY_TWO_FINGER_TAP = "pref_two_finger_tap"
        const val KEY_SCROLL_NATURAL = "pref_scroll_natural"
        const val KEY_BASE_GAIN = "pref_base_gain"
        const val KEY_ACCEL = "pref_accel"
        const val KEY_MAX_GAIN = "pref_max_gain"

        fun load(context: Context): TouchSettings {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val modeStr = prefs.getString(KEY_INPUT_MODE, InputMode.TOUCHSCREEN.name)
            val mode = try {
                InputMode.valueOf(modeStr ?: InputMode.TOUCHSCREEN.name)
            } catch (_: Exception) {
                InputMode.TOUCHSCREEN
            }

            return TouchSettings(
                inputMode = mode,
                longPressMs = prefs.getLong(KEY_LONG_PRESS_MS, 350L),
                doubleTapMs = prefs.getLong(KEY_DOUBLE_TAP_MS, 300L),
                tapMaxMs = prefs.getLong(KEY_TAP_MAX_MS, 250L),
                slopDp = prefs.getFloat(KEY_SLOP_DP, 10f),
                twoFingerTapRightClick = prefs.getBoolean(KEY_TWO_FINGER_TAP, true),
                twoFingerScrollNatural = prefs.getBoolean(KEY_SCROLL_NATURAL, true),
                baseGain = prefs.getFloat(KEY_BASE_GAIN, 1.0f),
                accel = prefs.getFloat(KEY_ACCEL, 3.0f),
                maxGain = prefs.getFloat(KEY_MAX_GAIN, 8.0f)
            )
        }

        fun save(context: Context, settings: TouchSettings) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_INPUT_MODE, settings.inputMode.name)
                .putLong(KEY_LONG_PRESS_MS, settings.longPressMs)
                .putLong(KEY_DOUBLE_TAP_MS, settings.doubleTapMs)
                .putLong(KEY_TAP_MAX_MS, settings.tapMaxMs)
                .putFloat(KEY_SLOP_DP, settings.slopDp)
                .putBoolean(KEY_TWO_FINGER_TAP, settings.twoFingerTapRightClick)
                .putBoolean(KEY_SCROLL_NATURAL, settings.twoFingerScrollNatural)
                .putFloat(KEY_BASE_GAIN, settings.baseGain)
                .putFloat(KEY_ACCEL, settings.accel)
                .putFloat(KEY_MAX_GAIN, settings.maxGain)
                .apply()
        }
    }
}
