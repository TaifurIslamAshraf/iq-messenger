package com.iqsocial.iq_message.data.media

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateListOf
import com.iqsocial.iq_message.data.chat.ChatRepository
import com.iqsocial.iq_message.data.remote.ApiResult
import com.iqsocial.iq_message.data.remote.ChatMessage
import com.iqsocial.iq_message.data.remote.MediaApi
import com.iqsocial.iq_message.data.remote.MessagesApi
import com.iqsocial.iq_message.data.media.VoiceWaveforms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID

/**
 * Files on their way to the server.
 *
 * **This is why it is an object and not part of the chat screen.** An upload that
 * lives in a composable's scope dies when you leave the conversation, which turns
 * backing out of a thread into a way to lose a photo you already watched start. The
 * scope here belongs to the process, so walking away is just walking away.
 *
 * The queue is also what makes the photo appear at once. The bubble is drawn from
 * the entry below, off the file on the device, before a single byte has been sent -
 * the thing you are waiting for should be the thing you are looking at.
 *
 * That is a change of position, taken deliberately: the thread used to show nothing
 * until the server had the bytes, on the grounds that a bubble before that claims
 * something untrue. What makes it honest now is the progress drawn over it - the
 * bubble says "this is going", not "this is sent", and [Outgoing.failed] says when
 * it did not.
 */
object UploadQueue {

    @Immutable
    data class Outgoing(
        val clientId: String,
        val conversationId: String,
        val uri: Uri,
        val kind: String,
        /** Shape of the picture, so the bubble is the right size from the start. */
        val aspect: Float,
        val sentBytes: Long = 0L,
        val totalBytes: Long = 0L,
        val failed: Boolean = false,
        val error: String = "",
        /**
         * Whether this photo goes up as it was taken.
         *
         * Carried on the item rather than read when the upload runs: the choice was
         * made when it was picked, and a toggle flipped while something is still in
         * the queue must not change what that one sends.
         */
        val hd: Boolean = true,
        /** Voice only: what the bubble needs to draw itself before it has been sent. */
        val durationMs: Long = 0L,
        val waveform: List<Int> = emptyList(),
        /**
         * When it was picked.
         *
         * Fixed at that moment rather than read when the bubble is built: the bubble
         * is built from derived state, and a clock read in there would make the
         * result different every time it was evaluated.
         */
        val startedAt: Long = System.currentTimeMillis()
    ) {
        /** 0..1, or 0 until the size is known. */
        val progress: Float
            get() = if (totalBytes <= 0L) 0f
            else (sentBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
    }

    /** Survives any one screen, so uploads are not tied to what is on top. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val outgoing = mutableStateListOf<Outgoing>()

    /**
     * Messages the server accepted, for whichever screen is listening.
     *
     * A flow rather than a callback because the screen that started the upload may
     * be gone by the time it lands. Nothing is lost if nobody is listening: the
     * thread reads its history when it next opens.
     */
    private val _sent = MutableSharedFlow<ChatMessage>(extraBufferCapacity = 16)
    val sent = _sent.asSharedFlow()

    fun forConversation(conversationId: String): List<Outgoing> =
        outgoing.filter { it.conversationId == conversationId }

    fun retry(context: Context, token: String, clientId: String) {
        val at = outgoing.indexOfFirst { it.clientId == clientId }
        if (at < 0) return
        val item = outgoing[at]
        outgoing[at] = item.copy(failed = false, error = "", sentBytes = 0L)
        scope.launch { run(context, token, item.clientId) }
    }

    fun discard(clientId: String) {
        outgoing.removeAll { it.clientId == clientId }
    }

    /**
     * Starts an upload and returns at once.
     *
     * [aspect] is passed in because the caller has already measured the picture to
     * build the request; measuring it twice would mean reading the file twice.
     */
    fun enqueue(
        context: Context,
        token: String,
        conversationId: String,
        uri: Uri,
        kind: String,
        aspect: Float,
        replyToId: String? = null,
        /** False shrinks a photo before it goes; see [MediaPreparer.prepare]. */
        hd: Boolean = true
    ): String {
        val clientId = UUID.randomUUID().toString()
        outgoing += Outgoing(
            clientId = clientId,
            conversationId = conversationId,
            uri = uri,
            kind = kind,
            aspect = aspect,
            hd = hd
        )
        replies[clientId] = replyToId
        scope.launch { run(context, token, clientId) }
        return clientId
    }

    /**
     * The same, for a recording that is already measured.
     *
     * A voice note goes through here for the reasons a photo does - it shows at once
     * and it outlives the screen - and for one more: it is the only copy. A picked
     * photo is still in the gallery if the upload is cancelled by walking away, but a
     * recording exists in the cache and nowhere else.
     */
    fun enqueueVoice(
        context: Context,
        token: String,
        conversationId: String,
        file: File,
        durationMs: Long,
        waveform: List<Int>,
        replyToId: String? = null
    ): String {
        val clientId = UUID.randomUUID().toString()
        outgoing += Outgoing(
            clientId = clientId,
            conversationId = conversationId,
            uri = Uri.fromFile(file),
            kind = "voice",
            aspect = 1f,
            durationMs = durationMs,
            waveform = waveform
        )
        replies[clientId] = replyToId
        scope.launch { run(context, token, clientId) }
        return clientId
    }

    private val replies = mutableMapOf<String, String?>()

    private suspend fun run(context: Context, token: String, clientId: String) {
        fun current() = outgoing.firstOrNull { it.clientId == clientId }
        fun update(block: (Outgoing) -> Outgoing) {
            val at = outgoing.indexOfFirst { it.clientId == clientId }
            if (at >= 0) outgoing[at] = block(outgoing[at])
        }
        fun fail(message: String) = update { it.copy(failed = true, error = message) }

        // A recording that cannot be sent is worth keeping on disk: the entry stays
        // in the queue, marked failed, and retry still has something to send.

        val item = current() ?: return

        val prepared = if (item.kind == "voice") {
            val file = item.uri.path?.let(::File)
            if (file == null || !file.exists()) {
                fail("The recording is gone")
                return
            }
            MediaPreparer.voiceUpload(file, item.durationMs, item.waveform)
        } else {
            MediaPreparer.prepare(context, item.uri, hd = item.hd).getOrElse {
                fail(it.message ?: "Could not read that file")
                return
            }
        }
        update { it.copy(totalBytes = prepared.size) }

        val created = when (val r = MediaApi.createUpload(token, prepared.body)) {
            is ApiResult.Success -> r.data
            is ApiResult.Failure -> {
                fail(r.message)
                return
            }
        }

        val put = MediaApi.putBytes(
            target = created.upload,
            size = prepared.size,
            // putBytes already reports only on whole percents, so this does not
            // rewrite the list on every buffer.
            onProgress = { done -> update { it.copy(sentBytes = done) } }
        ) {
            // The shrunk bytes when there are some, the file itself otherwise.
            // These are the very bytes the declaration was measured from, and the
            // server presigned for that exact length - re-reading the original here
            // would send a different number of them and be refused.
            prepared.payload?.let(::ByteArrayInputStream)
                ?: MediaPreparer.open(context, prepared.uri)
        }
        if (put is ApiResult.Failure) {
            fail(put.message)
            return
        }

        // Both have to be in storage before completing: the server answers 409 for
        // a declared thumbnail that never arrived.
        val thumbTarget = created.thumbnailUpload
        val thumbBytes = prepared.thumbnail
        if (thumbTarget != null && thumbBytes != null) {
            val sent = MediaApi.putBytes(thumbTarget, thumbBytes.size.toLong()) {
                thumbBytes.inputStream()
            }
            if (sent is ApiResult.Failure) {
                fail(sent.message)
                return
            }
        }

        if (MediaApi.complete(token, created.mediaId) is ApiResult.Failure) {
            fail("The upload did not finish")
            return
        }

        when (
            val r = MessagesApi.send(
                token = token,
                conversationId = item.conversationId,
                clientId = clientId,
                text = "",
                replyToId = replies[clientId],
                mediaId = created.mediaId
            )
        ) {
            is ApiResult.Success -> {
                if (item.kind == "voice") {
                    // We measured this one; keep the measurement rather than
                    // depending on the server echoing it back.
                    VoiceWaveforms.remember(r.data.id, item.waveform)
                    // The cache copy has done its job.
                    runCatching { item.uri.path?.let { File(it).delete() } }
                }
                // Removed only once the real message exists, so the thread never
                // shows neither the placeholder nor the message.
                Sounds.sent(context)
                _sent.emit(r.data)
                outgoing.removeAll { it.clientId == clientId }
                replies -= clientId
                ChatRepository.invalidate()
            }

            is ApiResult.Failure -> fail(r.message)
        }
    }
}
