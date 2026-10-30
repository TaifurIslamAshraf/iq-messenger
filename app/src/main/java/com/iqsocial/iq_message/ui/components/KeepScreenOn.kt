package com.iqsocial.iq_message.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Stops the screen timing out while this is on screen.
 *
 * The window's own flag rather than a wake lock, and the difference matters: a flag
 * is released when the window goes, including when the process is killed, where a
 * wake lock has to be given back by hand and is left held by every path that forgets
 * to. Nothing to leak.
 *
 * Cleared on dispose, so leaving the screen hands the normal timeout back
 * immediately rather than at the end of the call.
 */
@Composable
fun KeepScreenOn() {
    val context = LocalContext.current
    DisposableEffect(context) {
        val window = context.activity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

/**
 * The activity behind a composable's context.
 *
 * `LocalContext.current` is almost never the activity itself - it is a wrapper
 * around it - so casting straight to Activity returns null and whatever depended on
 * it quietly does nothing.
 */
private fun Context.activity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
