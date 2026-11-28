package com.iqsocial.iq_message.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Light or dark, as chosen in Settings.
 *
 * This phone's choice, not the account's: it is kept in local preferences and is
 * not sent anywhere, so it applies to whoever signs in here, the way a phone's own
 * display setting would.
 *
 * Snapshot state, because the palette in Color.kt reads it: switching it redraws
 * everything drawn with a theme colour, with no restart.
 */
object ThemeMode {

    var dark by mutableStateOf(false)
        private set

    private var read = false

    /** Read once, before the first frame, so the app never opens in the wrong one. */
    fun init(context: Context) {
        if (read) return
        read = true
        dark = prefs(context).getBoolean(KEY_DARK, false)
    }

    fun setDark(context: Context, on: Boolean) {
        dark = on
        prefs(context).edit().putBoolean(KEY_DARK, on).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private const val PREFS = "iq_display"
    private const val KEY_DARK = "dark_mode"
}
