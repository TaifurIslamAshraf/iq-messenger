package com.iqsocial.iq_message.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The emoji this person actually reaches for, most recent first.
 *
 * A "Recent" row is the only part of an emoji sheet most people use, so it is kept
 * across launches rather than rebuilt per session. Stored as one delimited string
 * because it is a short list and a set would lose the ordering that makes it
 * useful.
 */
object EmojiRecents {

    private const val PREFS = "iq_emoji"
    private const val KEY = "recent"
    private const val LIMIT = 24
    private const val SEP = "\u0001"

    var recent by mutableStateOf<List<String>>(emptyList())
        private set

    fun load(context: Context) {
        if (recent.isNotEmpty()) return
        recent = prefs(context).getString(KEY, "").orEmpty()
            .split(SEP)
            .filter { it.isNotBlank() }
    }

    /** Re-picking one moves it to the front rather than adding it twice. */
    fun remember(context: Context, emoji: String) {
        val next = (listOf(emoji) + recent.filterNot { it == emoji }).take(LIMIT)
        recent = next
        prefs(context).edit().putString(KEY, next.joinToString(SEP)).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
