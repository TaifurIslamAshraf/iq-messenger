package com.iqsocial.iq_message.data.media

import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.iqsocial.iq_message.data.remote.MessengerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Plays voice messages, one at a time.
 *
 * Only one can be playing: starting another stops the first, which is what a thread
 * of them needs and what every messenger does.
 *
 * The bytes are fetched to the cache first rather than streamed, because the media
 * route answers a **302** to a signed link and the token must not follow it —
 * `MediaPlayer` given the original URL would carry the header across.
 */
object VoicePlayer {

    /** The message currently playing, so its bubble can show a stop control. */
    var playingId by mutableStateOf<String?>(null)
        private set

    private var player: MediaPlayer? = null

    suspend fun toggle(cacheDir: File, token: String?, messageId: String) {
        if (playingId == messageId) {
            stop()
            return
        }
        stop()
        if (token.isNullOrBlank()) return

        val file = cache(cacheDir, token, messageId) ?: return
        withContext(Dispatchers.Main) {
            runCatching {
                val created = MediaPlayer()
                created.setDataSource(file.absolutePath)
                // Deliberately NOT inside an `apply` on the player: an unqualified
                // stop() in there binds to MediaPlayer.stop(), which halts the audio
                // but never clears playingId - so the bubble kept its stop icon
                // forever once a message finished.
                created.setOnCompletionListener { stop() }
                created.prepare()
                created.start()
                player = created
                playingId = messageId
            }.onFailure { stop() }
        }
    }

    fun stop() {
        player?.let { p ->
            runCatching { p.stop() }
            runCatching { p.release() }
        }
        player = null
        playingId = null
    }

    /**
     * Downloads once and keeps it: replaying should not fetch again.
     *
     * Named after the message, not by `createTempFile`, which hands back a *new*
     * random file every call - the existence check below could never pass, so every
     * play re-downloaded and left another copy behind.
     */
    private suspend fun cache(cacheDir: File, token: String, messageId: String): File? =
        withContext(Dispatchers.IO) {
            val safe = messageId.filter { it.isLetterOrDigit() || it == '_' || it == '-' }
            val target = File(cacheDir, "voice_$safe.m4a")
            if (target.exists() && target.length() > 0) return@withContext target

            runCatching {
                val first = (URL("${MessengerConfig.BASE_URL}/v1/messages/$messageId/media")
                    .openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false        // the token stops here
                    connectTimeout = 20_000
                    readTimeout = 60_000
                    setRequestProperty("Authorization", "Bearer $token")
                }
                val location = try {
                    when (first.responseCode) {
                        in 300..399 -> first.getHeaderField("Location")
                        in 200..299 -> {
                            first.inputStream.use { input ->
                                target.outputStream().use { out -> input.copyTo(out) }
                            }
                            return@runCatching target
                        }
                        else -> null
                    }
                } finally {
                    first.disconnect()
                }
                if (location.isNullOrBlank()) return@runCatching null

                val second = (URL(location).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20_000
                    readTimeout = 60_000
                }
                try {
                    if (second.responseCode in 200..299) {
                        second.inputStream.use { input ->
                            target.outputStream().use { out -> input.copyTo(out) }
                        }
                        target
                    } else {
                        null
                    }
                } finally {
                    second.disconnect()
                }
            }.getOrNull()
                // A half-written file would be cached as if it were whole.
                ?: run { target.delete(); null }
        }
}
