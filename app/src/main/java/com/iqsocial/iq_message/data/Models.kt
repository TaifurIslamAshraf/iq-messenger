package com.iqsocial.iq_message.data

import androidx.compose.runtime.Immutable
import com.iqsocial.iq_message.data.remote.LinkPreview

/*
 * The UI's rendering models.
 *
 * These are what the screens draw: [Person] feeds the generated avatars, [Message]
 * the chat bubbles. The demo content that used to sit alongside them is gone - every
 * screen now reads from the API - so what remains is shape, not data.
 */

@Immutable
data class Person(
    val id: String,
    val name: String,
    val handle: String,
    val online: Boolean = false,
    /**
     * The account's `profile_picture` - a full public URL, or empty.
     *
     * Empty is the normal case, not an error: most accounts have never set one, and
     * the generated gradient below is the real answer for them rather than a
     * placeholder waiting to be replaced.
     */
    val photo: String = "",
    /** Seeds the generated avatar gradient so a person keeps one identity everywhere. */
    val seed: Int = id.hashCode()
) {
    val initials: String
        get() = name.trim().split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
}

enum class MessageKind { TEXT, VOICE, CALL }

/**
 * What a call message in the thread has to say for itself.
 *
 * The bubble's own small copy of the server's record, carrying only what is drawn.
 * It is here rather than reaching into the remote model from the bubble so that the
 * drawing layer keeps speaking its own language, as it does for every other kind.
 */
@Immutable
data class CallNote(
    val video: Boolean,
    val missed: Boolean,
    /** Seconds talked; zero when it was never answered. */
    val seconds: Int
)

@Immutable
data class QuotedMessage(
    val author: String,
    val text: String,
    /** The message being answered, so the quote can be followed back to it. */
    val id: String = "",
    /** `image`, `voice`, ... or empty for plain text. */
    val mediaKind: String = ""
)

/**
 * How far one of your own messages has got.
 *
 * Read from the conversation's `peer_delivered_seq` and `peer_read_seq` against the
 * message's own `seq` - the server reports positions, not per-message flags, so a
 * message is delivered once the peer has reached at least that far.
 */
/**
 * A message's place in a run from the same sender.
 *
 * Consecutive messages are stacked the way Messenger stacks them: the corners that
 * face a neighbour flatten, only the last of a run carries the tail, and only the
 * last shows the avatar and the time.
 */
enum class BubbleGroup { SINGLE, FIRST, MIDDLE, LAST }

enum class DeliveryStatus { SENDING, FAILED, SENT, DELIVERED, SEEN }

@Immutable
data class Message(
    val id: String,
    val text: String = "",
    val time: String,
    val fromMe: Boolean,
    val kind: MessageKind = MessageKind.TEXT,
    val quoted: QuotedMessage? = null,
    val reaction: String? = null,
    val reactionCount: Int = 0,
    val voiceLength: String = "0:00",
    /** Null on someone else's message: only your own carry a delivery state. */
    val status: DeliveryStatus? = null,
    /** Set on a file message, so the bubble can fetch and draw it. */
    val mediaKind: String? = null,
    /** Set on a `voice_call` or `video_call` message, and the whole of its content. */
    val call: CallNote? = null,
    /** Aspect ratio from the server's width/height, to size the bubble before it loads. */
    val mediaAspect: Float = 1f,
    /** Voice only: 0-100 per bar, as it was recorded. */
    val waveform: List<Int> = emptyList(),
    /** Where this sits in a run of messages from the same person. */
    val group: BubbleGroup = BubbleGroup.SINGLE,
    /** Unix ms, for the separators between runs. */
    val createdAt: Long = 0L,
    /**
     * The file on this device, for a picture still going up.
     *
     * The bubble is drawn from this rather than from the server, because there is
     * nothing on the server yet - that is the whole point of showing it.
     */
    val localUri: String = "",
    /** 0..1 while uploading, or -1 when there is no upload to report. */
    val uploadProgress: Float = -1f,
    val uploadFailed: Boolean = false,
    /**
     * The card under a link, once the server has fetched the page.
     *
     * Absent for a moment after sending and then filled in - the server does the
     * fetching and says so over the socket, so this arrives later than the message
     * it belongs to.
     */
    val link: LinkPreview? = null
)

enum class CallKind { AUDIO, VIDEO }
enum class CallDirection { INCOMING, OUTGOING, MISSED }

@Immutable
data class CallRecord(
    val id: String,
    val person: Person,
    val kind: CallKind,
    val direction: CallDirection,
    val time: String,
    /**
     * How long it ran, or zero when it never did.
     *
     * Zero is the whole difference between a call that happened and one that only
     * rang: a missed call and a one-second call both read as "nothing" without it,
     * and the row has no other way to tell them apart.
     */
    val durationSeconds: Int = 0
) {
    /** Somebody picked up. */
    val taken: Boolean get() = durationSeconds > 0
}
