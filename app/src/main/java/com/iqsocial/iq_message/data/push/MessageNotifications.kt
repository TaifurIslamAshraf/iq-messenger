package com.iqsocial.iq_message.data.push

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.iqsocial.iq_message.BubbleActivity
import com.iqsocial.iq_message.MainActivity
import com.iqsocial.iq_message.R
import com.iqsocial.iq_message.data.media.RemoteImage
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * Message notifications, in the shape a messenger's are.
 *
 * `MessagingStyle` rather than a title and a line of text. It is what gives the
 * sender's face and name, what lets several messages from one person read as a
 * conversation instead of stacking as separate alerts, and what the system uses for
 * its own conversation features. Building the same thing by hand would be a
 * worse-looking copy that none of those apply to.
 *
 * The lines are kept per conversation so that a second message joins the first
 * rather than replacing it, which is the whole reason the style exists.
 *
 * It is also published as a **conversation**: a long-lived shortcut per chat, named
 * on the notification. That is what moves it into the Conversations section at the
 * top of the shade and gives it the large face and the name - the look this was
 * measured against. Without a shortcut the same notification is an ordinary one,
 * which is where the difference came from.
 *
 * Writing the layout by hand instead would be a step backwards. The reference is the
 * system's own conversation template; a hand-drawn one cannot be put in that section,
 * cannot be marked Priority or silenced per chat, and looks different on every
 * manufacturer's shade.
 */
object MessageNotifications {

    private data class Line(
        val text: String,
        val at: Long,
        val fromMe: Boolean,
        val sender: String
    )

    private val threads = java.util.Collections.synchronizedMap(
        mutableMapOf<String, MutableList<Line>>()
    )

    /**
     * The other person's face, per conversation.
     *
     * Keyed on the chat rather than on the picture's address, because that is what
     * every caller has: a reply typed in the shade knows which thread it is in and
     * nothing about whose picture belongs to it.
     */
    private val faces = java.util.Collections.synchronizedMap(mutableMapOf<String, Bitmap>())

    /**
     * Chats currently open in a bubble.
     *
     * Here so that [clear] knows not to cancel their notification. Android ties a
     * bubble to the notification that offered it: cancel the notification and the
     * bubble goes with it, which would mean a bubble that vanishes the moment it is
     * tapped - the chat opens, the chat marks itself read, the notification is
     * cancelled, the window closes. Kept by [BubbleActivity]'s own lifecycle rather
     * than guessed at from what is on screen.
     */
    private val bubbled = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /** Called by [BubbleActivity] as it opens, before anything composes. */
    fun bubbleOpened(conversationId: String) {
        if (conversationId.isNotBlank()) bubbled.add(conversationId)
    }

    /**
     * Called when the bubble goes.
     *
     * The notification it was holding open is cleared for real now: the chat was on
     * screen, so it has been read, and leaving it would put a stale thread back in
     * the shade the moment the window closed.
     */
    fun bubbleClosed(context: Context, conversationId: String) {
        bubbled.remove(conversationId)
        clear(context, conversationId)
    }

    /**
     * Posts, or adds to, the notification for [conversationId].
     *
     * Suspending because the sender's picture has to be fetched before the
     * notification is built: posting without it and updating afterwards makes the
     * face appear a moment late, which looks like a glitch rather than loading.
     *
     * Waited for only so long. A picture that is slow to come is not worth holding
     * the message back for, and on the push path the time is not this app's to
     * spend: Firebase gives a woken process seconds, not minutes. Past
     * [FACE_WAIT_MS] it goes without; the fetch carries on, and the next message has
     * the face. A picture that fails outright is the same - no face, never no
     * notification.
     */
    suspend fun show(
        context: Context,
        conversationId: String,
        sender: String,
        body: String,
        avatarUrl: String = "",
        unread: Int = 0
    ) {
        val face = faces[conversationId] ?: avatarUrl.takeIf { it.isNotBlank() }
            ?.let { url ->
                runCatching { withTimeoutOrNull(FACE_WAIT_MS) { RemoteImage.load(url) } }
                    .getOrNull()
            }
            ?.let { image -> runCatching { image.asAndroidBitmap() }.getOrNull() }
            ?.also { faces[conversationId] = it }
        addLine(conversationId, Line(body, System.currentTimeMillis(), false, sender))
        post(context, conversationId, sender, face, unread)
    }

    /** A reply sent from the shade, shown the moment it is tapped. */
    fun addOwnLine(context: Context, conversationId: String, text: String) {
        val sender = threads[conversationId]?.lastOrNull { !it.fromMe }?.sender.orEmpty()
        addLine(conversationId, Line(text, System.currentTimeMillis(), true, sender))
        post(context, conversationId, sender, faces[conversationId], 0)
    }

    /** The reply did not go. Said in the thread rather than as a separate alert. */
    fun markFailed(context: Context, conversationId: String) {
        addLine(
            conversationId,
            Line("Not sent. Open the chat to try again.", System.currentTimeMillis(), true, "")
        )
        val sender = threads[conversationId]?.lastOrNull { !it.fromMe }?.sender.orEmpty()
        post(context, conversationId, sender, faces[conversationId], 0)
    }

    /**
     * Clears the thread once the conversation has actually been read.
     *
     * The shortcut stays on purpose. It is not part of the notification: it is what
     * puts the chat in the launcher's long-press menu and at the top of the share
     * sheet, and removing it the moment a message is read would take that away for
     * no reason.
     */
    fun clear(context: Context, conversationId: String) {
        // A chat being read inside its own bubble keeps its notification. Cancelling
        // it would take the bubble down mid-read, and there is nothing to gain: the
        // system hides a bubbled notification from the shade by itself while the
        // window is open. It is cleared for real when the bubble closes.
        if (bubbled.contains(conversationId)) return
        threads.remove(conversationId)
        faces.remove(conversationId)
        runCatching {
            NotificationManagerCompat.from(context).cancel(conversationId, ID)
        }
    }

    private fun addLine(conversationId: String, line: Line) {
        synchronized(threads) {
            val lines = threads.getOrPut(conversationId) { mutableListOf() }
            lines += line
            // A shade is not a transcript; past a handful the oldest are of no use.
            while (lines.size > MAX_LINES) lines.removeAt(0)
        }
    }

    private fun post(
        context: Context,
        conversationId: String,
        senderName: String,
        face: Bitmap?,
        unread: Int
    ) {
        if (!allowed(context)) return
        IqMessagingService.ensureChannels(context)

        val lines = synchronized(threads) { threads[conversationId]?.toList().orEmpty() }

        // A conversation is keyed on its id - the shortcut, the locus and the tag all
        // are - and the locus refuses an empty one outright. Nothing should arrive
        // without one, but a message that did still gets its notification.
        if (conversationId.isBlank()) {
            postPlain(context, conversationId, senderName, lines)
            return
        }

        val me = Person.Builder().setName("You").setKey("me").build()
        val them = Person.Builder()
            .setName(senderName.ifBlank { "New message" })
            .setKey(conversationId)
            .apply { face?.let { setIcon(IconCompat.createWithAdaptiveBitmap(it)) } }
            .build()

        val style = NotificationCompat.MessagingStyle(me)
        lines.forEach { line ->
            style.addMessage(line.text, line.at, if (line.fromMe) me else them)
        }
        if (lines.isEmpty()) style.addMessage("New message", System.currentTimeMillis(), them)

        // One to one, so the style carries no title of its own: the person's name is
        // the title, and a separate one would say the same thing twice.
        style.isGroupConversation = false

        publishShortcut(context, conversationId, them, face)

        val builder = NotificationCompat.Builder(context, IqMessagingService.CHANNEL_MESSAGES)
            // A silhouette, not the launcher icon. The system keeps only the alpha
            // and tints what is left, so anything with colour in it arrives as a
            // white blob badged on the face.
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openIntent(context, conversationId))
            // Like first, then Reply, which is the order the reference puts them in
            // and the order they are reached in: a like is the one-tap answer and
            // sits under the thumb, a reply opens a keyboard. Android draws them
            // left to right as they are added.
            //
            // Safe to order by looks because Reply is not found by its position:
            // it carries SEMANTIC_ACTION_REPLY, which is what the shade, Auto and a
            // watch all use to pick out the one that takes text.
            .addAction(likeAction(context, conversationId))
            .addAction(replyAction(context, conversationId))
            // Exactly those two. Left on, the system adds suggestion chips of its own
            // under them ("OK", "Thanks", a call or map button) - on by default on
            // Samsung - and the notification stops matching the reference, which
            // shows Like and Reply and nothing else.
            .setAllowSystemGeneratedContextualActions(false)
            // What makes it a conversation rather than a notice. Both are needed:
            // the shortcut says which chat, the locus lets the system tie it to the
            // same chat when it is opened.
            .setShortcutId(conversationId)
            .setLocusId(LocusIdCompat(conversationId))
            .addPerson(them)
            .apply { if (unread > 0) setNumber(unread) }

        // An addition to the notification, never a condition of it. Attached only if
        // it could be built, because the alternative is what this already cost once:
        // the metadata was built inline in the chain above, where anything it threw
        // was thrown by `post` itself - and a message notification stopped appearing
        // at all, for a feature that is meant to be optional.
        val bubble = bubbleFor(context, conversationId, face)
        bubble?.let { builder.setBubbleMetadata(it) }

        // Built inside the same guard as the posting, for the same reason. A message
        // that someone sent is worth more than any one decoration on the notice of
        // it, so nothing added here can take the whole thing down.
        //
        // Tagged with the conversation, as the server tags its own: a newer message
        // in the same chat joins this notification rather than opening another.
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(conversationId, ID, builder.build())
        }.recoverCatching { refused ->
            // The bubble is the one part the system checks hardest and the one the
            // reader needs least, so the conversation goes again without it before
            // anything else is given up. It is not hypothetical: an immutable bubble
            // intent once had Android 14 refuse every message notification whole.
            if (bubble == null) throw refused
            Log.w(TAG, "Bubble refused; posting the conversation without it", refused)
            builder.setBubbleMetadata(null)
            NotificationManagerCompat.from(context)
                .notify(conversationId, ID, builder.build())
        }.onFailure { refused ->
            // And when the whole thing is refused - a face too large to hand over,
            // a manufacturer's shade that will not take the style - the message is
            // still said, plainly. Only the decoration is lost.
            //
            // Logged, because the fallback hides it otherwise: the reader gets a
            // notification either way, and a phone that refuses the conversation
            // every time would look exactly like one that was never asked.
            Log.w(TAG, "Conversation notification refused; posted plainly", refused)
            postPlain(context, conversationId, senderName, lines)
        }

        // Last, not first: the plain one stays up until there is a better one to
        // replace it with, so a failure above leaves the reader with something
        // rather than nothing.
        dropSystemDrawn(context)
    }

    /**
     * The message as a plain notification: the floor under [post].
     *
     * No style, no face, no shortcut and no bubble - everything that might have been
     * why the conversation could not be posted. What is left is who sent it, what
     * they said and a tap that opens the chat, which is all a notification has to
     * do. Same channel, tag and id as the conversation, so the next message that
     * posts properly replaces it rather than standing beside it.
     */
    private fun postPlain(
        context: Context,
        conversationId: String,
        senderName: String,
        lines: List<Line>
    ) {
        runCatching {
            val notification = NotificationCompat.Builder(
                context,
                IqMessagingService.CHANNEL_MESSAGES
            )
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(ContextCompat.getColor(context, R.color.notification_accent))
                .setContentTitle(senderName.ifBlank { context.getString(R.string.app_name) })
                // Their words under their name: a reply sent from the shade is the
                // reader's own, and would read as the other person's here.
                .setContentText(lines.lastOrNull { !it.fromMe }?.text ?: "New message")
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(openIntent(context, conversationId))
                .build()
            NotificationManagerCompat.from(context).notify(conversationId, ID, notification)
        }
    }

    /**
     * Clears a plain notification Firebase drew for a message, if there is one.
     *
     * There should not be: `IqMessagingService.handleIntent` takes message pushes
     * back before Firebase can draw them. This is the net under that. It leans on
     * how the SDK works inside, and if an update ever changed that, the first sign
     * would be the same message on screen twice - the plain one Firebase made, and
     * this one. Taking the plain one down keeps that a non-event.
     *
     * Narrow on purpose: only the messages channel, and only ids that are not this
     * app's own, so a conversation notification or a missed call is never caught by
     * it.
     */
    private fun dropSystemDrawn(context: Context) {
        // `Notification.channelId` arrived with channels in O, and it is what keeps
        // this narrow. Without it there is no way to tell a message notification
        // from any other, so on anything older nothing is touched - these are also
        // the versions where the shade has no conversation section to be missing
        // from, which is what this is in aid of.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.activeNotifications
                .filter { it.notification.channelId == IqMessagingService.CHANNEL_MESSAGES }
                .filter { it.id != ID && it.id != MISSED_CALL_ID }
                .forEach { manager.cancel(it.tag, it.id) }
        }
    }

    /**
     * A call nobody picked up.
     *
     * Plain, not `MessagingStyle`: a missed call is a fact, not a conversation, and
     * dressing it as one would put it in the Conversations section among chats that
     * can actually be replied to. It lands on its own channel so it can be quietened
     * without quietening messages, and it carries no actions because there is nothing
     * to do from here - this app cannot place a call yet.
     */
    suspend fun showMissedCall(
        context: Context,
        callId: String,
        who: String,
        what: String,
        avatarUrl: String
    ) {
        if (!allowed(context)) return
        IqMessagingService.ensureChannels(context)

        val face = avatarUrl.takeIf { it.isNotBlank() }
            ?.let { RemoteImage.load(it) }
            ?.let { image -> runCatching { image.asAndroidBitmap() }.getOrNull() }

        val notification = NotificationCompat.Builder(
            context,
            IqMessagingService.CHANNEL_MISSED_CALLS
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setContentTitle(who)
            .setContentText(what)
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setAutoCancel(true)
            .apply { face?.let { setLargeIcon(it) } }
            .build()

        // Tagged by the call, so the same one arriving twice does not stack.
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(callId.ifBlank { "call" }, MISSED_CALL_ID, notification)
        }
    }

    /**
     * What lets this chat float as a bubble.
     *
     * The bubble is offered, never forced. Android decides whether to show one, and
     * by default only for conversations the reader has promoted with the bubble
     * button on the notification itself - after which that chat bubbles on its own
     * for good. So the first message from someone new is a notification with a
     * bubble button on it, not a bubble; there is no API to skip that step, and an
     * app that could would be a popup nobody asked for.
     *
     * Which means the notification has to be worth reading on its own, as it already
     * is: everything here is an addition to it, nothing is moved into the bubble.
     *
     * `autoExpand` and `suppressNotification` are deliberately not set. They are for
     * a bubble the reader just asked for by name - tapping "new chat" - where the
     * window appearing is the answer to the tap. An arriving message is not that, and
     * a thread that throws itself open over whatever is on screen is the behaviour
     * people turn bubbles off over.
     *
     * The face is the icon when one has been fetched. It is what makes a bubble
     * recognisable at a glance, which is the entire point of the shape - a row of
     * identical app icons says only that there are three of something.
     *
     * Null rather than thrown when it cannot be offered, and null below Android 11,
     * where a bubble is either absent or a developer option rather than a feature.
     * Nothing is gained by putting metadata on a notification that cannot use it, and
     * leaving the notification byte for byte what it was on those versions means this
     * cannot have changed how it looks there.
     */
    private fun bubbleFor(
        context: Context,
        conversationId: String,
        face: Bitmap?
    ): NotificationCompat.BubbleMetadata? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return runCatching {
            // Mutable, and it has to be. The system fills in the launch flags that
            // open the chat in the bubble's own window, and from Android 12 a
            // notification whose bubble intent is immutable is refused outright -
            // not the bubble, the whole notification, which is how every message
            // came to arrive as a plain one. Safe because the intent is explicit:
            // it names BubbleActivity, so nothing filled in can send it elsewhere.
            val pending = PendingIntent.getActivity(
                context,
                ("bubble" + conversationId).hashCode(),
                BubbleActivity.intentFor(context, conversationId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            // The launcher icon as the fallback, not the status bar one. They are not
            // interchangeable: the small icon is a white silhouette because the
            // system keeps only its alpha, while a bubble's icon is drawn as it is -
            // so the silhouette would be a bubble with nothing visible in it.
            val icon = face?.let { IconCompat.createWithAdaptiveBitmap(it) }
                ?: IconCompat.createWithResource(context, R.mipmap.ic_launcher)
            NotificationCompat.BubbleMetadata.Builder(pending, icon)
                // A resource rather than a number, so it is a dp the screen's
                // density applies to rather than raw pixels that mean different
                // things on different phones.
                .setDesiredHeightResId(R.dimen.bubble_height)
                .build()
        }.getOrNull()
    }

    /**
     * Publishes the chat as a long-lived shortcut.
     *
     * Required, not decorative: Android only treats a notification as a conversation
     * when its `shortcutId` names a shortcut that exists, is long-lived and carries a
     * person. Name one that does not and the notification silently falls back to the
     * ordinary layout, which is exactly how a near-miss like this goes unnoticed.
     *
     * Dynamic shortcuts are capped per app, so the oldest chats drop off as newer
     * ones arrive - which is the right order to lose them in.
     */
    private fun publishShortcut(
        context: Context,
        conversationId: String,
        them: Person,
        face: Bitmap?
    ) {
        runCatching {
            val open = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra(IqMessagingService.EXTRA_EVENT, "message:notification")
                putExtra(
                    IqMessagingService.EXTRA_PAYLOAD,
                    JSONObject()
                        .put("message", JSONObject().put("conversation_id", conversationId))
                        .toString()
                )
            }
            val shortcut = ShortcutInfoCompat.Builder(context, conversationId)
                .setShortLabel(them.name ?: "Chat")
                .setLongLived(true)
                .setIntent(open)
                .setPerson(them)
                .setLocusId(LocusIdCompat(conversationId))
                .setCategories(setOf(SHORTCUT_CATEGORY))
                .apply { face?.let { setIcon(IconCompat.createWithAdaptiveBitmap(it)) } }
                .build()
            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
        }
    }

    /**
     * The field you can type into in the shade.
     *
     * The intent has to be **mutable**: the system writes the typed text into it,
     * and an immutable one would arrive empty with nothing to say why.
     */
    private fun replyAction(context: Context, conversationId: String): NotificationCompat.Action {
        val input = RemoteInput.Builder(NotificationActions.KEY_REPLY)
            .setLabel("Reply")
            .build()

        val intent = Intent(context, NotificationActions::class.java).apply {
            action = NotificationActions.ACTION_REPLY
            putExtra(NotificationActions.EXTRA_CONVERSATION, conversationId)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            ("reply" + conversationId).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        return NotificationCompat.Action.Builder(0, "Reply", pending)
            .addRemoteInput(input)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            // No suggested-reply chips under the buttons, for the same reason the
            // notification turns off the system's contextual actions: the reference
            // has Like and Reply and nothing more.
            .setAllowGeneratedReplies(false)
            .setShowsUserInterface(false)
            .build()
    }

    private fun likeAction(context: Context, conversationId: String): NotificationCompat.Action {
        val intent = Intent(context, NotificationActions::class.java).apply {
            action = NotificationActions.ACTION_LIKE
            putExtra(NotificationActions.EXTRA_CONVERSATION, conversationId)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            ("like" + conversationId).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(0, "Like", pending)
            .setShowsUserInterface(false)
            .build()
    }

    /**
     * The intent that opens the app on one conversation.
     *
     * Shared, because the floating panel hands over to it: a camera, a picker and a
     * permission dialog all answer to an activity, and a window is not one - so the
     * panel takes the reader to the app rather than offering a button that cannot
     * work. One shape for "open this chat", wherever the asking came from.
     */
    internal fun openIntentFor(context: Context, conversationId: String): Intent =
        Intent(context, MainActivity::class.java).apply {
            // NEW_TASK for the same reason the call service needs it: this is
            // built from an application context, and an activity started from one
            // without it is refused.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            // The same extras Android itself puts there for a notification push, so
            // the activity reads one shape whichever way it was opened.
            putExtra(IqMessagingService.EXTRA_EVENT, "message:notification")
            putExtra(
                IqMessagingService.EXTRA_PAYLOAD,
                JSONObject()
                    .put("message", JSONObject().put("conversation_id", conversationId))
                    .toString()
            )
        }

    private fun openIntent(context: Context, conversationId: String): PendingIntent {
        val open = openIntentFor(context, conversationId)
        return PendingIntent.getActivity(
            context,
            conversationId.hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Posting without the permission throws on Android 13 and above. */
    private fun allowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private const val ID = 1

    /** Its own id as well as its own channel, so neither cancels the other. */
    private const val MISSED_CALL_ID = 2
    private const val MAX_LINES = 6

    /** How long a notification waits for the sender's face before going without. */
    private const val FACE_WAIT_MS = 3_000L

    private const val TAG = "MessageNotifications"

    /** What marks a shortcut as a chat; the launcher and the shade both read it. */
    private const val SHORTCUT_CATEGORY =
        "android.shortcut.conversation"
}
