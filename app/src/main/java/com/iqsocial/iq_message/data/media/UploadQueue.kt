package com.iqsocial.iq_message.data.media

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateListOf
import com.iqsocial.iq_message.data.auth.SessionStore
import com.iqsocial.iq_message.data.chat.ChatRepository
import com.iqsocial.iq_message.data.chat.Outbox
import com.iqsocial.iq_message.data.local.ChatStore
import com.iqsocial.iq_message.data.local.UploadRow
import com.iqsocial.iq_message.data.remote.ApiResult
import com.iqsocial.iq_message.data.remote.MediaApi
import com.iqsocial.iq_message.data.media.VoiceWaveforms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
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
        /**
         * Held up by there being no network, as opposed to refused.
         *
         * Worth its own state rather than being shown as a failure. An upload that
         * never left the phone has not gone wrong, and offering a retry button for
         * it would ask somebody to press a thing that cannot work yet - it will be
         * picked up on its own when there is a connection again.
         */
        val waiting: Boolean = false,
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
     * Held for every change to [outgoing], and the reason is a crash rather than
     * tidiness.
     *
     * The list is written from two directions at once: the composition adds an entry
     * when something is picked, and an upload coroutine on a background thread
     * rewrites it several times a second as the bytes go up. Each of those writes
     * was a pair - find the row, then replace it:
     *
     *     val at = outgoing.indexOfFirst { it.clientId == clientId }
     *     if (at >= 0) outgoing[at] = block(outgoing[at])
     *
     * and a pair is not one action. Anything that *removes* a row in between - a
     * send finishing, a failed one dismissed, another upload completing - moves
     * every index after it. The write then lands on the wrong row, or, when the row
     * was the last one, on no row at all:
     *
     *     IndexOutOfBoundsException: index 2, size 2
     *
     * thrown inside a coroutine with nobody to catch it, which is the whole app.
     * Rare, and rarest on a fast phone: the slower the upload the more progress
     * writes there are to collide with, which is exactly the shape of "sometimes,
     * on some phones, when sending a photo".
     *
     * A `SnapshotStateList` is safe to write from two threads; what it cannot do is
     * make two of those writes into one. That is what this does.
     */
    private val lock = Any()

    /** Finds the row and replaces it, with nothing able to move it in between. */
    private fun change(clientId: String, block: (Outgoing) -> Outgoing) {
        synchronized(lock) {
            val at = outgoing.indexOfFirst { it.clientId == clientId }
            if (at >= 0) outgoing[at] = block(outgoing[at])
        }
    }

    private fun addOutgoing(item: Outgoing) {
        synchronized(lock) { outgoing += item }
    }

    private fun dropOutgoing(clientId: String) {
        synchronized(lock) { outgoing.removeAll { it.clientId == clientId } }
    }

    // There was a flow of accepted messages here, for whichever screen was
    // listening. Nothing needs it now: the message goes to the store, and the store
    // is what every thread reads - so a screen hears about it the same way it hears
    // about a message somebody else sent, and a screen that is closed misses
    // nothing.

    fun forConversation(conversationId: String): List<Outgoing> =
        outgoing.filter { it.conversationId == conversationId }

    fun retry(context: Context, token: String, clientId: String) {
        if (outgoing.none { it.clientId == clientId }) return
        change(clientId) { it.copy(failed = false, error = "", sentBytes = 0L) }
        scope.launch { run(context, token, clientId) }
    }

    /**
     * Gives up on one, for good.
     *
     * The stored row goes with it, and so do the kept bytes. Taking it off the list
     * alone would not be giving up: the row is what [resume] works from, so the
     * upload would come back by itself the next time the app started.
     */
    fun discard(context: Context, clientId: String) {
        dropOutgoing(clientId)
        replies -= clientId
        val app = context.applicationContext
        scope.launch {
            val dao = ChatStore.of(app).chats()
            dao.upload(clientId)?.let { row ->
                runCatching { File(row.path).delete() }
                row.thumbPath.takeIf { it.isNotBlank() }
                    ?.let { runCatching { File(it).delete() } }
            }
            dao.dropUpload(clientId)
        }
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
        addOutgoing(Outgoing(
            clientId = clientId,
            conversationId = conversationId,
            uri = uri,
            kind = kind,
            aspect = aspect,
            hd = hd
        ))
        replies[clientId] = replyToId
        scope.launch {
            if (hold(context, clientId, conversationId, uri, kind, aspect, hd, replyToId)) {
                run(context, token, clientId)
            }
        }
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
        addOutgoing(Outgoing(
            clientId = clientId,
            conversationId = conversationId,
            uri = Uri.fromFile(file),
            kind = "voice",
            aspect = 1f,
            durationMs = durationMs,
            waveform = waveform
        ))
        replies[clientId] = replyToId
        scope.launch {
            // Measured from the clip itself when the microphone would not say how
            // loud it was. Several phones return nothing from `maxAmplitude` - this
            // one among them - and a voice note from one of them arrived with no
            // shape at all, drawn as the flat line meant for messages that never
            // had one. Here rather than in the recorder because it is a decode: tens
            // of milliseconds, off the main thread, and the release gesture should
            // not wait for it.
            val shape = waveform.ifEmpty { VoiceShape.measure(file) }
            if (shape.isNotEmpty() && waveform.isEmpty()) {
                // The bubble is already on screen and already flat; this is the
                // moment it stops being.
                change(clientId) { it.copy(waveform = shape) }
            }
            // The recording is already a file of ours, so there is nothing to copy -
            // it is held where it was made.
            if (hold(
                    context, clientId, conversationId, Uri.fromFile(file), "voice",
                    aspect = 1f, hd = true, replyToId = replyToId,
                    durationMs = durationMs, waveform = shape, copy = false
                )
            ) {
                run(context, token, clientId)
            }
        }
        return clientId
    }

    private val replies = mutableMapOf<String, String?>()

    /**
     * Takes hold of the bytes, before anything is prepared.
     *
     * This is the first thing that happens to a picked file, and it is the whole
     * point: until the bytes are somewhere of ours with a row pointing at them, an
     * upload exists only in this process. Preparing a photo takes a second or two,
     * and the app being killed in that second used to lose it outright - which is
     * exactly the second it is most likely to be killed in, because preparing a
     * large photo is the most memory the app ever asks for.
     *
     * The row goes in with a blank declaration, which is what marks it as not yet
     * prepared. [run] finishes the job, here or after a restart.
     */
    private suspend fun hold(
        context: Context,
        clientId: String,
        conversationId: String,
        uri: Uri,
        kind: String,
        aspect: Float,
        hd: Boolean,
        replyToId: String?,
        durationMs: Long = 0L,
        waveform: List<Int> = emptyList(),
        copy: Boolean = true
    ): Boolean {
        val app = context.applicationContext
        val dir = File(app.cacheDir, "uploads").apply { mkdirs() }
        val held = runCatching {
            if (!copy) File(uri.path!!)
            else File(dir, clientId + suffixFor(app, uri)).also { out ->
                MediaPreparer.open(app, uri)!!.use { input ->
                    out.outputStream().use(input::copyTo)
                }
            }
        }.getOrNull()

        if (held == null || !held.exists()) {
            change(clientId) { it.copy(failed = true, error = "Could not read that file") }
            return false
        }

        ChatStore.of(app).chats().putUpload(
            UploadRow(
                clientId = clientId,
                conversationId = conversationId,
                path = held.absolutePath,
                thumbPath = "",
                // Blank: nothing has been measured yet.
                body = "",
                size = 0L,
                kind = kind,
                aspect = aspect,
                hd = hd,
                durationMs = durationMs,
                waveform = JSONArray(waveform).toString(),
                replyToId = replyToId.orEmpty(),
                createdAt = System.currentTimeMillis()
            )
        )
        return true
    }

    /**
     * The extension the copy is given, taken from what the source says it is.
     *
     * Not decoration, and not cosmetic: it is the only thing that still knows what
     * the file is by the time it is prepared. [MediaPreparer.prepare] runs against
     * *this* copy rather than the picked file - the picked file's permission dies
     * with the process, which is why there is a copy at all - and it asks the system
     * what type it is. For a `file://` path the system has nothing to go on but the
     * name, so a copy called `.src` came back as "application/octet-stream", which
     * is not a picture of anything: every photo chosen from the gallery was then
     * declared, uploaded and delivered as a **document**.
     *
     * Read here because here is the last moment it can be: the `content://` grant
     * that answers this question is gone a moment later.
     */
    private fun suffixFor(context: Context, uri: Uri): String {
        val mime = context.contentResolver.getType(uri).orEmpty()
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime).orEmpty()
        // Something the system has no name for is a document, and `.src` says so as
        // well as anything else would.
        return if (extension.isBlank()) ".src" else ".$extension"
    }

    /**
     * Picks up uploads that were interrupted.
     *
     * Called when the app starts and whenever the socket reconnects. Before this
     * there was nothing to pick up: the queue lived in memory, so a photo chosen
     * with no signal was gone the moment the app was killed - which, being offline,
     * is exactly when it was most likely to be.
     */
    fun resume(context: Context, token: String) {
        scope.launch { resumeNow(context, token) }
    }

    /**
     * The same, waited for, in the caller's own scope.
     *
     * For the background worker: it is stopped as soon as it returns, so handing the
     * uploads to a scope of ours and walking away would have the system kill them
     * halfway through a photo.
     */
    suspend fun resumeNow(context: Context, token: String) {
        val app = context.applicationContext
        for (row in ChatStore.of(app).chats().uploads()) {
            if (outgoing.any { it.clientId == row.clientId }) continue
            if (!File(row.path).exists()) {
                // The cache was cleared under us. Nothing can be done for it,
                // and keeping the row would mean trying again for ever.
                ChatStore.of(app).chats().dropUpload(row.clientId)
                continue
            }
            // The bubble comes back first, so a thread reopened after a restart
            // shows what is still going rather than a gap where it was.
            addOutgoing(row.toOutgoing())
            replies[row.clientId] = row.replyToId.takeIf { it.isNotBlank() }
            run(app, token, row.clientId)
        }
    }

    /**
     * Drops the placeholder once the message itself exists.
     *
     * Called by the outbox when the send lands. The two cannot be done in one place
     * any more: the upload hands the message to the queue and stops being involved,
     * and the queue may not get it away for hours.
     */
    fun handedOver(clientId: String) {
        dropOutgoing(clientId)
        replies -= clientId
    }

    /**
     * Signing out: nothing here is the next account's to send.
     *
     * The rows go with the rest of the store, but the bytes do not - they are files
     * in this app's cache, and a photo somebody chose to send stays on disk under
     * its own name until something deletes it. The in-memory list goes too, or the
     * next person to sign in would find a stranger's photo half-uploaded in a
     * conversation they cannot open.
     */
    fun clear(context: Context) {
        synchronized(lock) { outgoing.clear() }
        replies.clear()
        val app = context.applicationContext
        scope.launch {
            runCatching { File(app.cacheDir, "uploads").deleteRecursively() }
        }
    }

    private suspend fun run(context: Context, token: String, clientId: String) {
        fun update(block: (Outgoing) -> Outgoing) = change(clientId, block)

        /**
         * Marks the entry, telling apart "refused" from "not tried yet".
         *
         * An upload with no network has not failed, and showing it in red with a
         * retry button would be the app blaming somebody for being in a lift. It
         * waits instead, and the next reconnect picks it up.
         */
        fun stop(message: String, offline: Boolean) {
            if (offline) update { it.copy(waiting = true, error = "") }
            else update { it.copy(failed = true, waiting = false, error = message) }
        }

        val dao = ChatStore.of(context).chats()
        update { it.copy(waiting = false, failed = false, error = "") }

        // Everything from here is driven by the row, which [hold] wrote before any
        // of this started. A blank declaration on it means the bytes are held but
        // not yet measured - the state a restart can land in.
        val stored = dao.upload(clientId) ?: return
        val row = if (stored.body.isNotBlank()) stored else {
            val source = File(stored.path)
            if (!source.exists()) {
                // Held once and gone since: the cache was cleared, or a recording
                // was deleted. Nothing to send and nothing to retry.
                dao.dropUpload(clientId)
                stop("That file is no longer there", offline = false)
                return
            }
            val prepared = if (stored.kind == "voice") {
                MediaPreparer.voiceUpload(source, stored.durationMs, stored.waveform.toBars())
            } else {
                MediaPreparer.prepare(context, Uri.fromFile(source), hd = stored.hd)
                    .getOrElse {
                        stop(it.message ?: "Could not read that file", offline = false)
                        return
                    }
            }
            val ready = keep(context, stored, prepared) ?: run {
                stop("No room to keep that file", offline = false)
                return
            }
            dao.putUpload(ready)
            // The raw copy has been measured and rewritten; only a recording, which
            // was never copied, keeps the same file.
            if (ready.path != stored.path) runCatching { source.delete() }
            ready
        }

        update { it.copy(totalBytes = row.size) }
        val file = File(row.path)

        val created = when (val r = MediaApi.createUpload(token, JSONObject(row.body))) {
            is ApiResult.Success -> r.data
            is ApiResult.Failure -> {
                stop(r.message, r.offline)
                return
            }
        }

        val put = MediaApi.putBytes(
            target = created.upload,
            size = row.size,
            // putBytes already reports only on whole percents, so this does not
            // rewrite the list on every buffer.
            onProgress = { done -> update { it.copy(sentBytes = done) } }
        ) {
            // The kept bytes, which are the ones the declaration was measured from.
            // The server presigned for that exact length, so reading the original
            // again here would send a different number of them and be refused.
            file.inputStream()
        }
        if (put is ApiResult.Failure) {
            stop(put.message, put.offline)
            return
        }

        // Both have to be in storage before completing: the server answers 409 for
        // a declared thumbnail that never arrived.
        val thumbTarget = created.thumbnailUpload
        val thumb = row.thumbPath.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.exists() }
        if (thumbTarget != null && thumb != null) {
            val sent = MediaApi.putBytes(thumbTarget, thumb.length()) { thumb.inputStream() }
            if (sent is ApiResult.Failure) {
                stop(sent.message, sent.offline)
                return
            }
        }

        when (val done = MediaApi.complete(token, created.mediaId)) {
            is ApiResult.Failure -> {
                stop(done.message, done.offline)
                return
            }
            is ApiResult.Success -> Unit
        }

        // The server has the bytes, so the message is handed to the outbox and this
        // stops being an upload. From here it is a message like any other: it goes
        // when the network allows, and it survives the app being killed because the
        // store is holding it rather than this list.
        if (row.kind == "voice") {
            // We measured this one; keep the measurement rather than depending on
            // the server echoing it back. Filed under the client id, which is what
            // the message will carry until the server answers.
            VoiceWaveforms.remember(clientId, row.waveform.toBars())
        }
        Outbox.send(
            context = context,
            conversationId = row.conversationId,
            clientId = clientId,
            senderUid = SessionStore(context).load()?.uid.orEmpty(),
            text = "",
            replyToId = row.replyToId.takeIf { it.isNotBlank() },
            mediaId = created.mediaId
        )
        dao.dropUpload(clientId)
        // The kept copy has done its job; the bytes are on the server now.
        runCatching { file.delete() }
        runCatching { thumb?.delete() }
        Sounds.sent(context)
        ChatRepository.invalidate()
        // The placeholder stays until the send lands, so the thread never shows a
        // bubble with nothing in it. The outbox retires it through [handedOver].
        update { it.copy(sentBytes = row.size, waiting = false) }
    }

    /**
     * Writes the prepared bytes into this app's cache and describes them as a row.
     *
     * The declaration is kept beside them because it was measured from these exact
     * bytes. Null if the write failed, which on a full disk it can.
     */
    private fun keep(
        context: Context,
        row: UploadRow,
        prepared: MediaPreparer.Prepared
    ): UploadRow? {
        val clientId = row.clientId
        val dir = File(context.cacheDir, "uploads").apply { mkdirs() }
        return runCatching {
            val payload = File(dir, "$clientId.bin")
            val bytes = prepared.payload
            if (bytes != null) payload.writeBytes(bytes)
            else MediaPreparer.open(context, prepared.uri)!!.use { input ->
                payload.outputStream().use(input::copyTo)
            }

            val thumb = prepared.thumbnail?.let {
                File(dir, "$clientId.thumb").apply { writeBytes(it) }
            }

            row.copy(
                path = payload.absolutePath,
                thumbPath = thumb?.absolutePath.orEmpty(),
                body = prepared.body.toString(),
                size = prepared.size
            )
        }.getOrNull()
    }

    private fun UploadRow.toOutgoing(): Outgoing = Outgoing(
        clientId = clientId,
        conversationId = conversationId,
        // The held bytes either way: the raw copy before it is measured, the
        // prepared one after. Both draw the same picture in the bubble.
        uri = Uri.fromFile(File(path)),
        kind = kind,
        aspect = aspect,
        hd = hd,
        totalBytes = size,
        durationMs = durationMs,
        waveform = waveform.toBars(),
        startedAt = createdAt
    )

    private fun String.toBars(): List<Int> =
        runCatching {
            val arr = JSONArray(this)
            (0 until arr.length()).map { arr.optInt(it) }
        }.getOrElse { emptyList() }
}
