package com.iqsocial.iq_message.data.call

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Where the call is being shown, as opposed to what the call is.
 *
 * [CallCenter] owns the call and [CallEngine] owns the media; neither has any
 * business knowing whether the window it is drawn in is full screen or a thumbnail
 * in the corner. This is that, and only that.
 *
 * It exists because the two halves cannot see each other. The activity is told by
 * Android when it enters or leaves a picture-in-picture window and has no idea which
 * screen is composed inside it; the composition knows which screen it is showing and
 * cannot enter picture-in-picture, which is an activity's call to make. Each writes
 * the half it knows.
 */
object CallWindow {

    /**
     * True while the activity is a floating window rather than the whole screen.
     *
     * Read by the call screens, which draw almost nothing in that state: a
     * picture-in-picture window is a few centimetres across, and controls sized for
     * a thumb in it are a row of targets too small to hit and too small to read.
     */
    var inPip by mutableStateOf(false)
        internal set

    /**
     * True while a call screen is the one on screen.
     *
     * The activity asks this before shrinking itself. Without it, leaving the app
     * from the chat list would put the chat list in a floating window.
     */
    var callOnScreen by mutableStateOf(false)
        internal set

    /** Whether that call has a picture, which decides the window's shape. */
    var video by mutableStateOf(false)
        internal set

    /**
     * True while the app is in front of the person.
     *
     * Read by [CallCenter] to decide which of the two ringers makes the sound. With
     * the app away, the notification's channel rings and the app is not even
     * running to be asked; with the app in front, Android has no reason to alert for
     * a notification from the app already on screen, so the ringing has to be done
     * here or it is not done at all.
     */
    var appInFront by mutableStateOf(false)
        internal set

    /** Called by the composition as the call screens come and go. */
    fun showing(call: Boolean, video: Boolean = false) {
        callOnScreen = call
        this.video = video
    }
}
