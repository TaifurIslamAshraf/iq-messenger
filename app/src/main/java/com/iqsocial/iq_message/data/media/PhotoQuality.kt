package com.iqsocial.iq_message.data.media

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Whether photos go up as they were taken, or shrunk first.
 *
 * Off by default, and that is the useful default rather than the cautious one: a
 * phone photo is several megabytes, a conversation is mostly glances at pictures,
 * and sending the original is paid for twice - once by the sender's data and again
 * by every recipient's. HD is for the times it matters, which the person is the only
 * one who knows.
 *
 * Kept on disk because it is a preference, not a state: somebody who turns HD on to
 * send a document photo expects it still on when they send the next one, and
 * somebody who leaves it off should never have to think about it again.
 *
 * Mirrored into Compose state rather than read from preferences at each use. The
 * switch has to redraw the instant it is touched, and `SharedPreferences` is not
 * something a composition can observe.
 */
object PhotoQuality {

    /**
     * The longest edge a shrunk photo may have.
     *
     * Square, so it holds whichever way the picture is turned: a portrait photo
     * comes out 600 tall and a landscape one 600 wide, and neither is cropped. A
     * picture already smaller than this is left alone - scaling it up to reach the
     * limit would add bytes and no detail.
     */
    const val MAX_EDGE = 600

    var hd by mutableStateOf(false)
        private set

    private var loaded = false

    /** Reads the stored preference once per process. */
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        hd = prefs(context).getBoolean(KEY, false)
    }

    fun set(context: Context, on: Boolean) {
        hd = on
        loaded = true
        prefs(context).edit().putBoolean(KEY, on).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("iq_media", Context.MODE_PRIVATE)

    private const val KEY = "hd_photos"
}
