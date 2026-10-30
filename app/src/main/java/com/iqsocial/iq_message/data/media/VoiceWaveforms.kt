package com.iqsocial.iq_message.data.media

import android.content.Context
import android.content.SharedPreferences

/**
 * The shape of voice messages you recorded, kept against the id the server gave them.
 *
 * **Why keep what we just sent.** The sender measured the recording; reading that
 * shape back off a round trip is a worse source of truth than the measurement
 * itself, and it makes your own message depend on the server echoing a field it is
 * allowed to leave null. Anything stored here wins over nothing coming back.
 *
 * It also separates two failures that look identical in the bubble. If your own
 * voice messages draw a real waveform and the ones you receive do not, the capture
 * is fine and the field is being dropped in transit. If neither draws one, the
 * capture is the problem.
 *
 * Only your own, and only what this device recorded - there is nothing to say about
 * anybody else's.
 */
object VoiceWaveforms {

    private val bars = mutableMapOf<String, List<Int>>()

    @Volatile
    private var prefs: SharedPreferences? = null

    /** Loads what was kept from earlier runs; call once at start-up. */
    fun init(context: Context) {
        if (prefs != null) return
        val store = context.applicationContext
            .getSharedPreferences("voice_waveforms", Context.MODE_PRIVATE)
        prefs = store
        synchronized(bars) {
            store.all.forEach { (id, value) ->
                val text = value as? String ?: return@forEach
                bars[id] = text.split(',').mapNotNull(String::toIntOrNull)
            }
            trim()
        }
    }

    fun of(messageId: String): List<Int> =
        if (messageId.isBlank()) emptyList()
        else synchronized(bars) { bars[messageId].orEmpty() }

    fun remember(messageId: String, waveform: List<Int>) {
        if (messageId.isBlank() || waveform.isEmpty()) return
        synchronized(bars) {
            bars[messageId] = waveform
            prefs?.edit()?.putString(messageId, waveform.joinToString(","))?.apply()
            trim()
        }
    }

    /**
     * Drops the oldest once there are too many.
     *
     * Insertion order is the only ordering there is here, which is close enough to
     * oldest-first for a store whose entries are a few hundred bytes each.
     */
    private fun trim() {
        if (bars.size <= MAX_KEPT) return
        val extra = bars.size - MAX_KEPT
        val doomed = bars.keys.take(extra)
        doomed.forEach { bars -= it }
        prefs?.edit()?.apply { doomed.forEach(::remove) }?.apply()
    }

    private const val MAX_KEPT = 300
}
