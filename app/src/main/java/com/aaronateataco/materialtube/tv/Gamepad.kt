package com.aaronateataco.materialtube.tv

import android.view.KeyEvent
import android.view.MotionEvent

/**
 * One controller in the W3C "standard" layout, built from Android's controller events. [onChange]
 * receives the 17 button values and 4 axes whenever something moves, ready for the page's virtual
 * gamepad (tv/gamepad.js).
 */
class Gamepad(private val onChange: (buttons: FloatArray, axes: FloatArray) -> Unit) {
    private val buttons = FloatArray(17)
    private val axes = FloatArray(4)

    fun onKey(event: KeyEvent): Boolean {
        val index = buttonIndex(event.keyCode) ?: return false
        if (event.repeatCount > 0) return true // the page repeats held buttons itself
        set(index, if (event.action == KeyEvent.ACTION_DOWN) 1f else 0f)
        return true
    }

    fun onMotion(event: MotionEvent): Boolean {
        fun axis(a: Int) = event.getAxisValue(a).let { if (kotlin.math.abs(it) < 0.12f) 0f else it }
        val next = floatArrayOf(axis(MotionEvent.AXIS_X), axis(MotionEvent.AXIS_Y), axis(MotionEvent.AXIS_Z), axis(MotionEvent.AXIS_RZ))
        var changed = !next.contentEquals(axes)
        next.copyInto(axes)
        // Triggers report on different axes depending on the controller.
        val lt = maxOf(axis(MotionEvent.AXIS_LTRIGGER), axis(MotionEvent.AXIS_BRAKE))
        val rt = maxOf(axis(MotionEvent.AXIS_RTRIGGER), axis(MotionEvent.AXIS_GAS))
        changed = update(6, lt) or update(7, rt) or changed
        // Many Bluetooth pads send the D-pad as a hat rather than as keys.
        val hx = axis(MotionEvent.AXIS_HAT_X)
        val hy = axis(MotionEvent.AXIS_HAT_Y)
        changed = update(12, if (hy < -0.5f) 1f else 0f) or update(13, if (hy > 0.5f) 1f else 0f) or
            update(14, if (hx < -0.5f) 1f else 0f) or update(15, if (hx > 0.5f) 1f else 0f) or changed
        if (changed) onChange(buttons.copyOf(), axes.copyOf())
        return true
    }

    private fun update(index: Int, value: Float): Boolean {
        if (buttons[index] == value) return false
        buttons[index] = value
        return true
    }

    private fun set(index: Int, value: Float) {
        if (update(index, value)) onChange(buttons.copyOf(), axes.copyOf())
    }

    companion object {
        /** Android key code to standard gamepad button index. */
        fun buttonIndex(keyCode: Int): Int? = when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> 0
            KeyEvent.KEYCODE_BUTTON_B -> 1
            KeyEvent.KEYCODE_BUTTON_X -> 2
            KeyEvent.KEYCODE_BUTTON_Y -> 3
            KeyEvent.KEYCODE_BUTTON_L1 -> 4
            KeyEvent.KEYCODE_BUTTON_R1 -> 5
            KeyEvent.KEYCODE_BUTTON_L2 -> 6
            KeyEvent.KEYCODE_BUTTON_R2 -> 7
            KeyEvent.KEYCODE_BUTTON_SELECT -> 8
            KeyEvent.KEYCODE_BUTTON_START -> 9
            KeyEvent.KEYCODE_BUTTON_THUMBL -> 10
            KeyEvent.KEYCODE_BUTTON_THUMBR -> 11
            KeyEvent.KEYCODE_DPAD_UP -> 12
            KeyEvent.KEYCODE_DPAD_DOWN -> 13
            KeyEvent.KEYCODE_DPAD_LEFT -> 14
            KeyEvent.KEYCODE_DPAD_RIGHT -> 15
            KeyEvent.KEYCODE_BUTTON_MODE -> 16
            else -> null
        }
    }
}
