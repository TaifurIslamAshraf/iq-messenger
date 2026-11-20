package com.iqsocial.iq_message.data.media

import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.iqsocial.iq_message.data.remote.MessengerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
 *
 * Fetching first is also why there are three states below rather than one. A note
 * that is not on this phone yet does not begin at the first tap; it begins when it
 * has arrived, and on a slow connection those are seconds apart. With nothing said
 * in between, a pressed play button looks like a button that was never pressed.
 */
object VoicePlayer {

    /** The message currently playing, so its bubble can show a stop control. */
    var playingId by mutableStateOf<String?>(null)
        private set

    /**
     * The message whose audio is on its way down.
     *
     * Read by the bubble to put a spinner where the play button was. Held here and
     * not in the bubble for the same reason [playingId] is: the row is recomposed,
     * recycled and scrolled away while the fetch is still running, and a flag that
     * lived in the row would go with it.
     */
    var loadingId by mutableStateOf<String?>(null)
        private set

    /**
     * The message whose last tap did not end in sound.
     *
     * A fetch that fails is otherwise completely silent — the spinner stops and
     * nothing plays, which from the outside is the same as a tap that never
     * registered. Cleared by the next tap, so the mark answers one attempt rather
     * than standing as a permanent verdict on the message.
     */
    var failedId by mutableStateOf<String?>(null)
        private set

    private var player: MediaPlayer? = null

    /**
     * The fetch runs here, in the player's own scope, not the caller's.
     *
     * The bubble that asks for it is a row in a `LazyColumn`, and its
     * `rememberCoroutineScope` is cancelled the moment that row leaves the viewport:
     * a download started and then scrolled past died halfway, with nothing to say so.
     * What has to outlive the row is owned by what outlives the row.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var fetch: Job? = null

    /**
     * One control, so one tap means whichever of its three things applies: stop what
     * is playing, abandon what is being fetched, or start this one.
     *
     * The middle case is deliberately a cancel and not a second attempt. Two taps on
     * a slow note used to start two downloads of the same message into the same path,
     * each writing over the other.
     */
    fun toggle(cacheDir: File, token: String?, messageId: String) {
        if (playingId == messageId || loadingId == messageId) {
            stop()
            return
        }
        stop()
        // No token is a reason it cannot play, so it is shown as one rather than
        // swallowed — this is where a tap lands after the session has gone.
        if (token.isNullOrBlank()) {
            failedId = messageId
            return
        }

        failedId = null
        loadingId = messageId
        fetch = scope.launch {
            try {
                val file = cache(cacheDir, token, messageId)
                if (file == null || !start(file, messageId)) failedId = messageId
            } finally {
                // Only while it is still ours: by the time a cancelled fetch reaches
                // here, the id may already belong to the note tapped instead.
                if (loadingId == messageId) loadingId = null
            }
        }
    }

    fun stop() {
        fetch?.cancel()
        fetch = null
        loadingId = null
        release()
    }

    /**
     * Starts the file, and says whether there is sound.
     *
     * Separate from [stop] because this runs *inside* the fetch, and calling `stop()`
     * from there would cancel the very job that called it.
     */
    private fun start(file: File, messageId: String): Boolean = runCatching {
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
    }.isSuccess.also { if (!it) release() }

    /** Tears down the player alone, leaving the queue of work to its owner. */
    private fun release() {
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

            val attempt = runCatching {
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
            }

            val got = attempt.getOrNull()
            // A half-written file would be cached as if it were whole.
            if (got == null) target.delete()
            // `runCatching` catches cancellation as well, and a cancelled fetch
            // reported as a plain failure would mark the note unplayable for having
            // been abandoned. The file is dealt with above; this only has to go on.
            (attempt.exceptionOrNull() as? CancellationException)?.let { throw it }
            got
        }
}
