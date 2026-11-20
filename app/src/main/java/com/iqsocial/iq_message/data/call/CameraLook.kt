package com.iqsocial.iq_message.data.call

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue

/**
 * How the camera should look on a video call: brighter, softer, or neither.
 *
 * Two numbers, both `0f` by default, which is the untouched picture - and that
 * matters for more than taste: at zero the filter passes frames through without
 * touching the GPU at all, so somebody who never opens the panel pays nothing for
 * it in battery or heat.
 *
 * Kept on the phone rather than the account. It is a property of this camera in
 * this room's light, not of the person - and a setting that followed somebody to a
 * different phone would be wrong as often as right.
 *
 * Read from the capture thread on every frame and written from the call screen, so
 * the values are plain volatile floats; Compose reads the same numbers through
 * [brightness] and [smooth], which are snapshot state so a slider redraws itself.
 */
object CameraLook {

    private const val PREFS = "iq_camera_look"
    private const val KEY_BRIGHTNESS = "brightness"
    private const val KEY_SMOOTH = "smooth"

    /** 0 is the camera's own exposure; 1 lifts the picture as far as it goes. */
    var brightness by mutableFloatStateOf(0f)
        private set

    /** 0 is every pore; 1 is as soft as this is willing to be. */
    var smooth by mutableFloatStateOf(0f)
        private set

    /**
     * The same two numbers, read without the snapshot machinery.
     *
     * The filter reads these thirty times a second on the capture thread, which is
     * not a thread that should be taking part in Compose's snapshot system.
     */
    @Volatile
    var brightnessNow: Float = 0f
        private set

    @Volatile
    var smoothNow: Float = 0f
        private set

    /** True when there is nothing to do, which is the fast path through the filter. */
    val off: Boolean get() = brightnessNow <= 0.01f && smoothNow <= 0.01f

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        setBrightness(prefs.getFloat(KEY_BRIGHTNESS, 0f), context)
        setSmooth(prefs.getFloat(KEY_SMOOTH, 0f), context)
    }

    fun setBrightness(value: Float, context: Context? = null) {
        val held = value.coerceIn(0f, 1f)
        brightness = held
        brightnessNow = held
        context?.save(KEY_BRIGHTNESS, held)
    }

    fun setSmooth(value: Float, context: Context? = null) {
        val held = value.coerceIn(0f, 1f)
        smooth = held
        smoothNow = held
        context?.save(KEY_SMOOTH, held)
    }

    private fun Context.save(key: String, value: Float) {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putFloat(key, value).apply()
    }
}
