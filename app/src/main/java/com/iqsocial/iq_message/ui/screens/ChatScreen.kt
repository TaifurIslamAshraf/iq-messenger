package com.iqsocial.iq_message.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.iqsocial.iq_message.data.BubbleGroup
import com.iqsocial.iq_message.data.DeliveryStatus
import com.iqsocial.iq_message.data.Message
import com.iqsocial.iq_message.data.MessageKind
import com.iqsocial.iq_message.data.CallNote
import com.iqsocial.iq_message.data.Person
import com.iqsocial.iq_message.data.QuotedMessage
import com.iqsocial.iq_message.data.auth.SessionStore
import com.iqsocial.iq_message.data.chat.ChatRepository
import com.iqsocial.iq_message.data.chat.KnownPeople
import com.iqsocial.iq_message.data.chat.OpenChat
import com.iqsocial.iq_message.data.media.DeviceGallery
import com.iqsocial.iq_message.data.media.MediaCache
import com.iqsocial.iq_message.data.media.MediaPreparer
import com.iqsocial.iq_message.data.media.PhotoQuality
import com.iqsocial.iq_message.data.media.RemoteImage
import com.iqsocial.iq_message.data.media.Sounds
import com.iqsocial.iq_message.data.media.UploadQueue
import com.iqsocial.iq_message.data.media.VoicePlayer
import com.iqsocial.iq_message.data.media.VoiceRecorder
import com.iqsocial.iq_message.data.media.VoiceWaveforms
import com.iqsocial.iq_message.data.overlay.ChatHeads
import com.iqsocial.iq_message.data.overlay.ChatWindow
import com.iqsocial.iq_message.data.profile.MeRepository
import com.iqsocial.iq_message.data.profile.PresenceRepository
import com.iqsocial.iq_message.data.profile.presenceLabel
import com.iqsocial.iq_message.data.push.MessageNotifications
import com.iqsocial.iq_message.data.remote.ApiResult
import com.iqsocial.iq_message.data.remote.ChatConversation
import com.iqsocial.iq_message.data.remote.ChatMessage
import com.iqsocial.iq_message.data.remote.ChatSocket
import com.iqsocial.iq_message.data.remote.ConversationsApi
import com.iqsocial.iq_message.data.remote.LinkPreview
import com.iqsocial.iq_message.data.remote.LinkPreviewApi
import com.iqsocial.iq_message.data.remote.MediaApi
import com.iqsocial.iq_message.data.remote.MessageInfo
import com.iqsocial.iq_message.data.remote.MessagesApi
import com.iqsocial.iq_message.ui.components.Avatar
import com.iqsocial.iq_message.ui.components.BubbleShape
import com.iqsocial.iq_message.ui.components.ChatWallpaper
import com.iqsocial.iq_message.ui.components.CircleIconButton
import com.iqsocial.iq_message.ui.components.HairlineDivider
import com.iqsocial.iq_message.ui.components.TypingDots
import com.iqsocial.iq_message.ui.icons.IqIcons
import com.iqsocial.iq_message.ui.theme.ChatCanvas
import com.iqsocial.iq_message.ui.theme.Crimson
import com.iqsocial.iq_message.ui.theme.CrimsonTint
import com.iqsocial.iq_message.ui.theme.FieldGrey
import com.iqsocial.iq_message.ui.theme.Hairline
import com.iqsocial.iq_message.ui.theme.Ink
import com.iqsocial.iq_message.ui.theme.InkFaint
import com.iqsocial.iq_message.ui.theme.InkMuted
import com.iqsocial.iq_message.ui.theme.IqTheme
import com.iqsocial.iq_message.ui.theme.OnlineGreen
import com.iqsocial.iq_message.ui.theme.SystemBarIcons
import com.iqsocial.iq_message.ui.theme.messageBody
import java.util.UUID
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    conversationId: String,
    onBack: () -> Unit,
    onAudioCall: (String) -> Unit,
    onVideoCall: (String) -> Unit
) {
    SystemBarIcons(darkIcons = true)
    val d = IqTheme.dimens
    val context = LocalContext.current
    val session = remember { SessionStore(context) }
    val token = session.token
    val myUid = remember { session.load()?.uid.orEmpty() }

    var remote by remember(conversationId) { mutableStateOf<ChatConversation?>(null) }
    val thread = remember(conversationId) { mutableStateListOf<ChatMessage>() }
    var threadError by remember(conversationId) { mutableStateOf<String?>(null) }

    /**
     * The peer, with anything the server left out filled in from what is known.
     *
     * A conversation opened from search comes back, now and then, with the peer's
     * name empty - and the header drew "Unknown" for somebody whose name had been
     * on screen a tap earlier. The search put the person in [KnownPeople] as it
     * opened the chat, so there is an answer for exactly that gap.
     */
    val peer = remember(remote) { KnownPeople.complete(conversationId, remote?.peer) }

    /**
     * Whether they are there, from the server rather than from nowhere.
     *
     * `Person.online` defaults to false and nothing but the profile screen ever set
     * it, so the dot on this header was dark for everyone, always, and the line
     * under the name read "Last seen recently" whether or not they had ever been
     * seen at all.
     *
     * Asked again each time the chat opens, because a cached presence is not stale
     * information so much as false information.
     */
    val presence = PresenceRepository.of(peer?.uid.orEmpty())
    LaunchedEffect(peer?.uid, token) {
        val uid = peer?.uid?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        PresenceRepository.refresh(token, listOf(uid))
        // And keep it true while the chat is open, rather than showing where they
        // were at the moment it opened.
        ChatSocket.watchPresence(listOf(uid))
    }

    // The header's Person comes from the conversation's peer.
    val person = remember(peer, presence?.online) {
        peer?.let {
            Person(
                id = it.uid,
                name = it.fullname.ifBlank { "Unknown" },
                handle = "",
                online = presence?.online == true,
                photo = it.profilePicture
            )
        } ?: Person(id = conversationId, name = "...", handle = "")
    }

    // Upload progress must not remap history and format every message's timestamp.
    // Only server changes invalidate these bubbles; uploads are merged separately.
    val sentMessages by remember(conversationId) {
        derivedStateOf {
            val name = remote?.peer?.fullname.orEmpty()
            val delivered = remote?.peerDeliveredSeq ?: 0
            val read = remote?.peerReadSeq ?: 0
            thread.map { it.toBubble(myUid, name, delivered, read) }
        }
    }
    val messages by remember(conversationId) {
        derivedStateOf {
            sentMessages + UploadQueue.forConversation(conversationId).map { it.toBubble() }
        }
    }
    // Work out separators once, shared by run grouping and the displayed rows.
    val shown by remember(conversationId) {
        derivedStateOf {
            val list = messages
            val stamps = list.indices.map { separatorBefore(list, it) }
            val groups = runsIn(list, stamps)
            list.indices.reversed().map { i ->
                ThreadRow(list[i].copy(group = groups[i]), stamps[i])
            }
        }
    }

    var draft by remember { mutableStateOf("") }
    var attachmentsOpen by remember { mutableStateOf(false) }
    // The message under long press, where on screen it sits so the reaction
    // tray can be put against it, and the one being edited.
    var acting by remember(conversationId) { mutableStateOf<ChatMessage?>(null) }
    var actingAt by remember(conversationId) { mutableStateOf(Rect.Zero) }
    // The overflow behind "More", and the full emoji sheet behind "+".
    var moreFor by remember(conversationId) { mutableStateOf<ChatMessage?>(null) }
    var reactionPickerFor by remember(conversationId) { mutableStateOf<ChatMessage?>(null) }
    // The message whose reactions are being read, which is also where they come off.
    var reactionsFor by remember(conversationId) { mutableStateOf<ChatMessage?>(null) }
    // Briefly marked after a quote was followed back to it, so the eye can find it.
    var highlighted by remember(conversationId) { mutableStateOf<String?>(null) }
    /**
     * The one message showing its time, if any.
     *
     * One at a time rather than a set: a thread with half its stamps open is a thread
     * you have to read around, and tapping a second message almost always means you
     * have finished with the first.
     */
    var timeShownFor by remember(conversationId) { mutableStateOf<String?>(null) }
    var editing by remember(conversationId) { mutableStateOf<ChatMessage?>(null) }
    var forwarding by remember(conversationId) { mutableStateOf<ChatMessage?>(null) }
    var replyingTo by remember(conversationId) { mutableStateOf<ChatMessage?>(null) }
    var emojiOpen by remember(conversationId) { mutableStateOf(false) }
    var trayOpen by remember(conversationId) { mutableStateOf(false) }
    val recorder = remember { VoiceRecorder(context) }
    val clipboard = LocalClipboardManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current

    /**
     * Whether the keyboard is actually up.
     *
     * Asked of the window rather than inferred from what this screen last did.
     * `keyboard?.hide()` is a request, not a fact: the system can bring the keyboard
     * back - a field regaining focus, the window coming forward, a hardware keyboard
     * going away - without this screen being told, and then the panel and the
     * keyboard are both on screen, each taking the same space.
     *
     * Read as a boolean through `derivedStateOf` so the height animating up and down
     * does not recompose the thread on every frame of it.
     */
    val imeInsets = WindowInsets.ime
    val density = LocalDensity.current
    val keyboardUp by remember(imeInsets, density) {
        derivedStateOf { imeInsets.getBottom(density) > 0 }
    }
    var recordingMs by remember { mutableStateOf(0L) }
    var recording by remember { mutableStateOf(false) }
    var info by remember(conversationId) { mutableStateOf<MessageInfo?>(null) }
    var searchOpen by remember(conversationId) { mutableStateOf(false) }
    var sharedOpen by remember(conversationId) { mutableStateOf(false) }
    var peerOpen by remember(conversationId) { mutableStateOf(false) }
    // The picture being looked at full screen, if any.
    var viewing by remember(conversationId) { mutableStateOf<Message?>(null) }

    /**
     * Which messages were already here when the thread opened.
     *
     * Everything else arrives, and arriving is what is worth animating. Without
     * this the whole history plays its entrance at once the moment a chat is
     * opened - thirty bubbles sliding and fading together, which reads as the
     * screen struggling rather than as anything arriving.
     *
     * Filled once, from the first non-empty thread. Not cleared afterwards: a
     * message that scrolls out of the window and back is not new, and treating it
     * as new is the same mistake in a different place.
     */
    val alreadyHere = remember(conversationId) { mutableSetOf<String>() }
    var settled by remember(conversationId) { mutableStateOf(false) }
    LaunchedEffect(conversationId, thread.size) {
        if (!settled && thread.isNotEmpty()) {
            thread.forEach { alreadyHere += it.id }
            settled = true
        }
    }
    var preview by remember(conversationId) { mutableStateOf<LinkPreview?>(null) }
    var previewDismissed by remember(conversationId) { mutableStateOf<String?>(null) }
    /** The link the current card was asked about, as written in the box. */
    var previewOf by remember(conversationId) { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState(cacheWindow = ThreadCacheWindow)
    val scope = rememberCoroutineScope()

    LaunchedEffect(conversationId, token) {
        if (token.isNullOrBlank()) return@LaunchedEffect
        when (val c = ConversationsApi.get(token, conversationId)) {
            is ApiResult.Success -> {
                remote = c.data
                // A good answer is worth keeping for the next time this chat is
                // opened, when the answer may be the thin one.
                c.data.peer?.let { KnownPeople.remember(conversationId, it) }
            }
            is ApiResult.Failure -> threadError = c.message
        }
        when (val page = MessagesApi.history(token, conversationId)) {
            is ApiResult.Success -> {
                // History arrives newest first; the list reads oldest at the top.
                thread.clear()
                thread.addAll(page.data.items.reversed())
            }
            is ApiResult.Failure -> threadError = page.message
        }
        // Report how far this device has read, so the peer's ticks turn blue.
        thread.lastOrNull()?.seq?.takeIf { it > 0 }?.let {
            ConversationsApi.markRead(token, conversationId, it)
        }

        // Catch up on changes to messages already in the page. Edits, deletions
        // and reactions leave `seq` alone, so history cannot surface them and
        // this is the only route that does.
        val since = ChatRepository.syncedAt(conversationId)
        if (since > 0L) {
            var cursor: String? = null
            do {
                val changed = MessagesApi.changes(token, conversationId, since, cursor)
                if (changed !is ApiResult.Success) break
                changed.data.items.forEach { updated ->
                    val at = thread.indexOfFirst { it.id == updated.id }
                    if (at >= 0) thread[at] = updated
                }
                cursor = changed.data.nextCursor
                if (cursor == null && changed.data.syncedAt > 0L) {
                    ChatRepository.rememberSync(conversationId, changed.data.syncedAt)
                }
            } while (cursor != null)
        } else {
            // First visit this launch: the page just read is the baseline.
            ChatRepository.rememberSync(conversationId, System.currentTimeMillis())
        }
    }

    // Live traffic for this conversation. Anything for another conversation is
    // the chat list's business, not this screen's.
    // Reconnecting means the socket was down, and anything sent in that window
    // never arrived as an event. `after_seq` is the only way to find it.
    LaunchedEffect(conversationId, ChatSocket.connected) {
        if (!ChatSocket.connected) return@LaunchedEffect
        val auth = token ?: return@LaunchedEffect
        val lastSeq = thread.lastOrNull { it.seq > 0 }?.seq ?: return@LaunchedEffect
        when (val page = MessagesApi.since(auth, conversationId, lastSeq)) {
            is ApiResult.Success -> {
                val known = thread.mapTo(HashSet()) { it.id }
                val missed = page.data.items.filterNot { it.id in known }
                if (missed.isNotEmpty()) {
                    thread.addAll(missed)
                    ChatRepository.invalidate()
                    missed.maxOfOrNull { it.seq }?.takeIf { it > 0 }?.let {
                        ConversationsApi.markRead(auth, conversationId, it)
                    }
                }
            }
            is ApiResult.Failure -> Unit   // a failed catch-up is not worth a banner
        }
    }

    LaunchedEffect(conversationId) {
        ChatSocket.events.collect { event ->
            when (event) {
                is ChatSocket.Event.New -> {
                    val m = event.message
                    if (m.conversationId != conversationId) {
                        // Something arrived elsewhere; the list has to re-read.
                        ChatRepository.invalidate()
                        return@collect
                    }
                    // A message this device sent comes back over the socket too;
                    // reconcile it rather than showing it twice.
                    val mine = thread.indexOfFirst {
                        it.clientId.isNotBlank() && it.clientId == m.clientId
                    }
                    if (mine >= 0) thread[mine] = m
                    else if (thread.none { it.id == m.id }) thread += m

                    ChatRepository.invalidate()
                    if (m.senderUid != myUid && m.seq > 0) {
                        // The socket is preferred, but it is exactly when it is
                        // down that a receipt would otherwise be lost - so the
                        // HTTP route covers that case rather than being skipped.
                        val viaSocket = ChatSocket.reportDelivered(conversationId, m.seq)
                        token?.let { auth ->
                            if (!viaSocket) ConversationsApi.markDelivered(auth, conversationId, m.seq)
                            ConversationsApi.markRead(auth, conversationId, m.seq)
                        }
                    }
                }

                is ChatSocket.Event.Updated -> {
                    val m = event.message
                    val at = thread.indexOfFirst { it.id == m.id }
                    if (at >= 0) thread[at] = m
                }

                is ChatSocket.Event.ReceiptChanged -> {
                    if (event.receipt.conversationId == conversationId) {
                        remote = remote?.copy(
                            peerDeliveredSeq = event.receipt.deliveredSeq,
                            peerReadSeq = event.receipt.readSeq
                        )
                    }
                }
            }
        }
    }

    // An upload that finished while this screen was open: the queue has dropped the
    // placeholder, so the real message has to take its place here. If the screen was
    // closed instead, nothing is needed - reopening reads the history.
    LaunchedEffect(conversationId) {
        UploadQueue.sent.collect { message ->
            if (message.conversationId != conversationId) return@collect
            if (thread.none { it.id == message.id }) thread += message
        }
    }

    // The compose box gets its own preview; a sent link previews itself and
    // arrives as `message:updated`, which this screen already handles.
    /**
     * The card over the composer, for the link being typed.
     *
     * Everything here is keyed on the link **as written**, not on the one the server
     * answers with. Those differ whenever it canonicalises - a trailing slash, a
     * redirect followed, tracking parameters dropped - and comparing against its
     * answer meant two things quietly misbehaved: the preview was fetched again on
     * every keystroke after it had already arrived, and dismissing one never stuck,
     * because the url remembered was not the url next tested.
     */
    LaunchedEffect(draft, token) {
        val link = LinkPreviewApi.firstLink(draft)
        if (token.isNullOrBlank() || link == null || link == previewDismissed) {
            preview = null
            previewOf = null
            return@LaunchedEffect
        }
        if (previewOf == link) return@LaunchedEffect
        delay(500)                       // don't chase every keystroke
        val card = LinkPreviewApi.preview(token, link)
        // Remembered even when nothing came back, so a link the server has no
        // preview for is asked about once rather than on every letter after it.
        previewOf = link
        preview = card
    }

    /**
     * Follows the conversation down, without yanking it.
     *
     * Reversed, the newest message is index 0, so opening the thread needs no scroll
     * at all - the list is already there. That alone removes the jump the old
     * `animateScrollToItem(lastIndex)` made on every change.
     *
     * What is left is the case of a message arriving while you are reading further
     * up, and there the list is left alone unless the message is yours.
     */
    LaunchedEffect(messages.lastOrNull()?.id) {
        val last = messages.lastOrNull() ?: return@LaunchedEffect
        val atBottom = listState.firstVisibleItemIndex <= 1
        if (atBottom || last.fromMe) listState.animateScrollToItem(0)
    }

    /**
     * Follows a quote back to the message it answers.
     *
     * Scrolls to it and marks it for a moment. The mark matters more than the
     * scroll: landing somewhere in the middle of a thread with no sign of which
     * bubble you were sent to leaves you reading backwards to work it out.
     *
     * Only what is loaded can be jumped to. A quote of something further back than
     * the history in hand does nothing rather than guessing, which is better than
     * scrolling somewhere arbitrary and marking the wrong message.
     */
    fun jumpTo(messageId: String) {
        val index = messages.indexOfFirst { it.id == messageId }
        if (index < 0) return
        scope.launch {
            // The list runs the other way round.
            listState.animateScrollToItem(messages.lastIndex - index)
            highlighted = messageId
            delay(HIGHLIGHT_MS)
            // Only clear it if nothing else has been jumped to since.
            if (highlighted == messageId) highlighted = null
        }
    }

    /**
     * Puts the keyboard away for good, not just out of sight.
     *
     * Clearing focus as well as hiding is what makes it stick. A field that still
     * holds the cursor is a field the system may raise the keyboard for again, and
     * then the panel below is under a keyboard nobody asked back. Nothing is lost by
     * it: emoji append to the end of the draft rather than at a cursor.
     */
    fun dismissKeyboard() {
        keyboard?.hide()
        focus.clearFocus(force = true)
    }

    /**
     * The keyboard and a panel are never both on screen.
     *
     * Enforced here, from the keyboard's side, because that is the side this screen
     * does not control. Whatever raised it - a tap on the field, the window coming
     * back, the system restoring focus - the panel goes.
     */
    LaunchedEffect(keyboardUp) {
        if (keyboardUp) {
            emojiOpen = false
            trayOpen = false
        }
    }

    /**
     * Clears this chat's notification whenever the chat comes into view.
     *
     * Reading the chat is what the notification was asking for, so it goes. On
     * every ON_START rather than once: the screen stays composed while the app is in
     * the background, and whatever arrived meanwhile is read the moment the reader
     * comes back to it.
     *
     * Cleared, never held back. A message in the chat on screen still gets its
     * notification as it arrives - nothing in the push layer asks which chat is open
     * (see `SeenMessages`) - and that one goes the next time the chat is opened.
     */
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(conversationId, lifecycle) {
        // Added to a lifecycle that is already started, an observer is handed
        // ON_START straight away - which is the chat opening in front of the reader.
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                MessageNotifications.clear(context, conversationId)
                // Said out loud, because the socket listener cannot see the
                // composition and needs to know not to float a head over the very
                // chat being read.
                OpenChat.opened(conversationId)
                // And the one already floating for this chat goes, which the
                // listener cannot do for it: opening from the chat list reaches
                // here without ever touching the head, so it would have stayed up
                // over the conversation it was offering to open. A no-op when there
                // is none.
                //
                // Except when this *is* the head's own panel. The head opened it
                // and the head is what closes it again, so taking the head away
                // here was the panel pulling the floor out from under itself: the
                // last head going takes the row down, and the row coming down
                // closes the panel. It opened and vanished in the same frame.
                if (ChatWindow.showing != conversationId) {
                    ChatHeads.dismiss(conversationId)
                }
            }
            if (event == Lifecycle.Event.ON_STOP) OpenChat.closed(conversationId)
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            OpenChat.closed(conversationId)
        }
    }

    fun showInfo(message: ChatMessage) {
        val authToken = token ?: return
        acting = null
        scope.launch {
            when (val r = MessagesApi.info(authToken, message.id)) {
                is ApiResult.Success -> info = r.data
                is ApiResult.Failure -> threadError = r.message
            }
        }
    }

    fun forwardTo(message: ChatMessage, targets: List<String>) {
        val authToken = token ?: return
        forwarding = null
        if (targets.isEmpty()) return
        scope.launch {
            val r = MessagesApi.forward(
                authToken, message.id, targets, java.util.UUID.randomUUID().toString()
            )
            if (r is ApiResult.Failure) threadError = r.message else ChatRepository.invalidate()
        }
    }

    fun react(message: ChatMessage, emoji: String) {
        val authToken = token ?: return
        acting = null
        scope.launch {
            // One reaction per member: sending the one already there removes it.
            val mine = message.reactions.firstOrNull { it.uid == myUid }
            val r = if (mine?.emoji == emoji) {
                MessagesApi.removeReaction(authToken, message.id)
            } else {
                MessagesApi.react(authToken, message.id, emoji)
            }
            if (r is ApiResult.Failure) {
                threadError = r.message
            } else {
                // Only once it is actually on the message. Sounding on the tap would
                // be the app congratulating itself for the press.
                if (mine?.emoji != emoji) Sounds.reaction(context)
                refreshThread(authToken, conversationId, thread)
            }
        }
    }

    fun deleteMessage(message: ChatMessage, forEveryone: Boolean) {
        val authToken = token ?: return
        acting = null
        scope.launch {
            val r = if (forEveryone) {
                MessagesApi.deleteForEveryone(authToken, conversationId, listOf(message.id))
            } else {
                MessagesApi.deleteForMe(authToken, conversationId, listOf(message.id))
            }
            when (r) {
                is ApiResult.Failure -> threadError = r.message
                else -> {
                    if (forEveryone) refreshThread(authToken, conversationId, thread)
                    else thread.removeAll { it.id == message.id }
                    ChatRepository.invalidate()
                }
            }
        }
    }

    fun saveEdit(message: ChatMessage, text: String) {
        val authToken = token ?: return
        scope.launch {
            when (val r = MessagesApi.edit(authToken, message.id, text)) {
                is ApiResult.Success -> {
                    val at = thread.indexOfFirst { it.id == message.id }
                    if (at >= 0) thread[at] = r.data
                    ChatRepository.invalidate()
                }
                is ApiResult.Failure -> threadError = r.message
            }
        }
    }

    /**
     * Hands the file to [UploadQueue] and returns.
     *
     * The upload itself deliberately does **not** run in this screen's scope. Tied
     * to the composable it would be cancelled the moment you left the conversation,
     * so backing out of a thread would quietly throw away a photo you had already
     * watched start.
     *
     * The picture appears in the thread straight away, drawn off the file on the
     * device, with its progress over it. That reverses what this did before, which
     * was to show nothing until the server had the bytes - on the grounds that a
     * bubble before then claims something untrue. The progress is what makes it
     * honest: the bubble says "this is going", not "this is sent".
     */
    fun sendMedia(uri: android.net.Uri) {
        val authToken = token ?: return
        threadError = null
        scope.launch {
            // Measured here rather than in the queue so the bubble is the right
            // shape from its first frame; the queue would only learn it after the
            // file had been read, which is a visible jump.
            val shape = MediaPreparer.shapeOf(context, uri)
            UploadQueue.enqueue(
                context = context.applicationContext,
                token = authToken,
                conversationId = conversationId,
                uri = uri,
                kind = shape.kind,
                aspect = shape.aspect,
                replyToId = replyingTo?.id,
                // Read at the moment of sending, and carried on the queued item from
                // there - flipping the switch afterwards must not change what is
                // already on its way.
                hd = PhotoQuality.hd
            )
            replyingTo = null
            trayOpen = false
        }
    }

    /**
     * Where the camera app writes the shot.
     *
     * Kept across the launch because the result only says whether a picture was
     * taken - the file is the one we named going in.
     */
    var shot by remember(conversationId) { mutableStateOf<android.net.Uri?>(null) }

    val camera = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { taken ->
        val target = shot
        shot = null
        if (taken && target != null) sendMedia(target)
    }

    /**
     * Opens whatever camera app is installed, rather than building one.
     *
     * No CAMERA permission is declared, and that is the reason this works without
     * asking for anything: `ACTION_IMAGE_CAPTURE` is handled by another app, which
     * has its own permission. Declaring it here would add a prompt for a camera this
     * app never opens.
     */
    fun openCamera() {
        val folder = java.io.File(context.cacheDir, "camera")
        if (!folder.exists() && !folder.mkdirs()) {
            threadError = "Could not open the camera"
            return
        }
        val file = java.io.File(folder, "shot_${System.currentTimeMillis()}.jpg")
        val uri = runCatching {
            androidx.core.content.FileProvider.getUriForFile(
                context,
                context.packageName + ".files",
                file
            )
        }.getOrNull()
        if (uri == null) {
            threadError = "Could not open the camera"
            return
        }
        shot = uri
        emojiOpen = false
        trayOpen = false
        runCatching { camera.launch(uri) }.onFailure {
            shot = null
            threadError = "No camera app to open"
        }
    }

    // Android's photo picker: no storage permission, and the user chooses what the
    // app may see rather than granting it the whole gallery.
    val mediaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { picked -> picked?.let { sendMedia(it) } }

    /**
     * Hands the recording to [UploadQueue], exactly as a picked photo is handed over.
     *
     * It used to upload inline, which made a voice note the one thing in the thread
     * that showed nothing until it had arrived - and that died if you left the
     * conversation while it was going. That mattered more here than for a photo: a
     * picked photo is still in the gallery afterwards, a recording is not.
     */
    fun sendVoice(result: VoiceRecorder.Recording) {
        val authToken = token ?: return
        threadError = null
        UploadQueue.enqueueVoice(
            context = context.applicationContext,
            token = authToken,
            conversationId = conversationId,
            file = result.file,
            durationMs = result.durationMs,
            waveform = result.waveform,
            replyToId = replyingTo?.id
        )
        replyingTo = null
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) threadError = "Microphone permission is needed to record"
    }

    fun startRecording() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (recorder.start()) {
            recording = true
            recordingMs = 0L
        } else {
            threadError = "Could not start recording"
        }
    }

    fun finishRecording(send: Boolean) {
        if (!recording) return
        recording = false
        if (!send) {
            recorder.cancel()
            return
        }
        val result = recorder.stop()
        if (result == null) {
            threadError = "Hold to record, release to send"
            return
        }
        sendVoice(result)
    }

    // Only the visible timer. The waveform is the recorder's own business now -
    // tying it to this loop meant a recording could arrive the right length and
    // completely flat, with nothing to say why.
    LaunchedEffect(recording) {
        while (recording) {
            delay(100)
            recordingMs += 100
        }
    }

    fun sendLive(text: String) {
        val authToken = token
        if (authToken.isNullOrBlank()) return
        val clientId = UUID.randomUUID().toString()
        val replyId = replyingTo?.id
        replyingTo = null
        // Shown at once, reconciled by client_id when the server answers.
        thread += ChatMessage.pending(conversationId, clientId, myUid, text)
        scope.launch {
            when (
                val r = MessagesApi.send(
                    authToken, conversationId, clientId, text, replyToId = replyId
                )
            ) {
                is ApiResult.Success -> {
                    val at = thread.indexOfFirst { it.clientId == clientId }
                    if (at >= 0) thread[at] = r.data else thread += r.data
                    // The chat list must now show a conversation that may be new.
                    ChatRepository.invalidate()
                    Sounds.sent(context)
                }
                is ApiResult.Failure -> {
                    val at = thread.indexOfFirst { it.clientId == clientId }
                    if (at >= 0) thread[at] = thread[at].copy(pending = false, failed = true)
                    threadError = r.message
                }
            }
        }
    }

    // The long-press overlay reaches over the composer and the panels as well,
    // so the screen goes in a box and the overlay is the last thing in it.
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(ChatCanvas)
                // The inset belongs to the whole screen, not to the composer. The window
                // does not shrink under edge-to-edge, so padding only the composer would
                // push it up the screen while the list above kept its full height.
                .imePadding()
        ) {
            ChatTopBar(
                person = person,
                onBack = onBack,
                onAudioCall = { onAudioCall(person.id) },
                onVideoCall = { onVideoCall(person.id) },
                onSearch = { searchOpen = true },
                onShared = { sharedOpen = true },
                onOpenPeer = { peerOpen = true }
            )

            Box(Modifier.weight(1f)) {
                ChatWallpaper()
                if (messages.isEmpty()) EmptyThread(person.name, threadError)
                ProvideThreadMedia(conversationId, token) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            horizontal = d.gutterTight,
                            vertical = d.gapMd
                        ),
                        // Built from the bottom up, which is how a conversation is read
                        // and how every other messenger lays one out.
                        //
                        // It is not only about starting at the newest message. In a
                        // normal list the anchor is the top, so anything above the
                        // viewport that changes height - a photo settling, a receipt
                        // appearing, a reaction arriving - shoves everything below it
                        // and the thread lurches under the thumb. Reversed, the anchor
                        // is the bottom: those changes move the far end, which nobody is
                        // looking at.
                        reverseLayout = true,
                        // Runs are tight and the gaps between them are not, so the
                        // spacing belongs to the row rather than to the list.
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        items(
                            shown,
                            key = { it.message.id },
                            // Reuse is by shape, so whether there is a stamp is part of
                            // it. Without that the list hands a slot that was built with
                            // a stamp to a row that has none - the tree changes under the
                            // reuse, the item is measured again at a different height,
                            // and the thread jumps as it scrolls in.
                            contentType = {
                                it.message.mediaKind.orEmpty() +
                                    it.message.kind.name +
                                    (it.separator != null)
                            }
                        ) { row ->
                            val message = row.message
                            // New since this screen opened - which is to say sent
                            // just now, or just arrived.
                            val arriving = settled && message.id !in alreadyHere
                            // One child, not two. A lazy item is meant to be a single
                            // composable; two siblings loose in it are measured and
                            // placed by the list's own policy, which stacks them along
                            // the main axis and - with reverseLayout set, as it is here -
                            // walks them from the far end. Wrapping them settles the
                            // order here instead of relying on that.
                            Column(
                                // Two different jobs, and the list does one of them
                                // far better than hand-written state could: when a
                                // message lands, every bubble above it has to move
                                // up, and `animateItem` animates that placement from
                                // inside the list's own measure pass.
                                //
                                // The fade is asked for only when something is
                                // actually arriving. `null` is not "no animation
                                // configured" - it is this item declining the fade,
                                // which is what keeps a scrolled-back-to bubble from
                                // blinking as it is composed again.
                                Modifier.animateItem(
                                    fadeInSpec = if (arriving) tween(ENTER_MS) else null,
                                    placementSpec = spring(
                                        stiffness = Spring.StiffnessMediumLow,
                                        visibilityThreshold = IntOffset.VisibilityThreshold
                                    ),
                                    fadeOutSpec = null
                                )
                            ) {
                                // The data is reversed, the thread is not: the stamp
                                // still belongs above the message it introduces.
                                row.separator?.let { TimeSeparator(it) }
                                MessageRow(
                                    message = message,
                                    peer = person,
                                    arriving = arriving,
                                    highlighted = message.id == highlighted,
                                    showTime = message.id == timeShownFor,
                                    onTap = {
                                        timeShownFor =
                                            if (timeShownFor == message.id) null
                                            else message.id
                                    },
                                    onJumpToQuoted = { id -> jumpTo(id) },
                                    onCallBack = { video ->
                                        if (video) onVideoCall(person.id)
                                        else onAudioCall(person.id)
                                    },
                                    onLongPress = { at ->
                                        // The bubble model has no server fields, so the
                                        // real message is found by the id it was mapped
                                        // from.
                                        acting = thread.firstOrNull { it.id == message.id }
                                        actingAt = at
                                        // The overlay's bar wants the bottom of the
                                        // screen, so whatever is already there gets out
                                        // of the way.
                                        emojiOpen = false
                                        trayOpen = false
                                        keyboard?.hide()
                                    },
                                    onReply = {
                                        replyingTo = thread.firstOrNull { it.id == message.id }
                                    },
                                    onReactionClick = {
                                        reactionsFor = thread.firstOrNull { it.id == message.id }
                                    },
                                    onOpenMedia = { viewing = message }
                                )
                            }
                        }
                    }
                }
            }

            // Everything stacked between the thread and the composer, in the order it
            // should be read: what you are answering, what you are about to link, then
            // anything that went wrong.
            replyingTo?.let { target ->
                ReplyingTo(
                    message = target,
                    mine = target.senderUid == myUid,
                    peerName = person.name
                ) { replyingTo = null }
            }

            preview?.let { card ->
                LinkPreviewCard(card) {
                    // The typed link, not the server's: that is what the next pass
                    // will compare against.
                    previewDismissed = previewOf
                    preview = null
                }
            }

            // Sending, editing and uploading all fail here, and without this they fail
            // silently whenever the thread has any messages in it.
            threadError?.let { message ->
                ThreadError(message) { threadError = null }
            }


            Composer(
                draft = draft,
                emojiOpen = emojiOpen,
                onToggleEmoji = {
                    trayOpen = false
                    emojiOpen = !emojiOpen
                    if (emojiOpen) dismissKeyboard()
                },
                onDraftChange = { draft = it },
                onAttach = {
                    emojiOpen = false
                    trayOpen = !trayOpen
                    if (trayOpen) dismissKeyboard()
                },
                // The other direction is covered by the effect above rather than
                // here: this only fires on a change of focus, and the keyboard can
                // come up without one.
                onFieldFocused = {
                    emojiOpen = false
                    trayOpen = false
                },
                onCamera = { openCamera() },
                trayOpen = trayOpen,
                recording = recording,
                recordingMs = recordingMs,
                onHoldStart = { startRecording() },
                onHoldEnd = { send -> finishRecording(send) },
                onSend = {
                    emojiOpen = false
                    trayOpen = false
                    val text = draft.trim()
                    if (text.isNotEmpty()) {
                        val edited = editing
                        if (edited != null) {
                            saveEdit(edited, text)
                            editing = null
                        } else {
                            sendLive(text)
                        }
                        draft = ""
                        preview = null
                        previewOf = null
                        previewDismissed = null
                    }
                }
            )

            // Below the composer, where the keyboard would be — siblings of the
            // thread, not overlays on top of it, so opening one shortens the thread
            // rather than hiding the conversation.
            if (emojiOpen) {
                EmojiPanel(
                    onPick = { draft += it },
                    onBackspace = { draft = draft.dropLastEmoji() }
                )
            }

            if (trayOpen) {
                MediaTray(
                    onPick = { uri ->
                        trayOpen = false
                        sendMedia(uri)
                    },
                    onOpenCamera = { openCamera() },
                    onDismiss = { trayOpen = false },
                    onOpenSystemPicker = {
                        trayOpen = false
                        mediaPicker.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageAndVideo
                            )
                        )
                    }
                )
            }

            // Room for the gesture bar, and it has to be last.
            //
            // It used to be the final line of the composer, which is the same place
            // only while the composer is the bottom of the screen. Open a panel and
            // the panel goes below the composer - so that spacer stopped being the
            // gap under everything and became a gap between the composer and the
            // sheet, which is the extra space that appeared when either picker
            // opened.
            //
            // Nothing is lost with the keyboard up: an inner `windowInsetsPadding`
            // subtracts what an outer one has already consumed, and the `imePadding`
            // on this column consumes more at the bottom than the gesture bar asks
            // for, so this comes out as nothing rather than stacking on top.
            Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
        }

        acting?.let { message ->
            // A call is an event rather than something anybody said: there is
            // nothing in it to answer and nothing to put a face on.
            val answerable = !message.isDeleted && message.call == null
            MessageActionOverlay(
                anchor = actingAt,
                mine = message.senderUid == myUid,
                canReact = answerable,
                canReply = answerable,
                // Nothing to put on the clipboard unless the message has words.
                canCopy = message.text.isNotBlank() && !message.isDeleted,
                myReaction = message.reactions.firstOrNull { it.uid == myUid }?.emoji,
                onReact = { react(message, it) },
                onPickOtherReaction = {
                    reactionPickerFor = message
                    acting = null
                },
                onReply = {
                    replyingTo = message
                    acting = null
                },
                onCopy = {
                    clipboard.setText(AnnotatedString(message.text))
                    acting = null
                },
                onDelete = { deleteMessage(message, false) },
                onMore = {
                    moreFor = message
                    acting = null
                },
                onDismiss = { acting = null }
            )
        }
    }

    moreFor?.let { message ->
        MessageActions(
            message = message,
            mine = message.senderUid == myUid,
            onEdit = {
                editing = message
                draft = message.text
                moreFor = null
            },
            onDelete = { forEveryone ->
                moreFor = null
                deleteMessage(message, forEveryone)
            },
            onForward = {
                forwarding = message
                moreFor = null
            },
            onInfo = {
                moreFor = null
                showInfo(message)
            },
            onDismiss = { moreFor = null }
        )
    }

    reactionsFor?.let { message ->
        val profile = MeRepository.profile
        ReactionSheet(
            reactions = message.reactions,
            myUid = myUid,
            peer = person,
            // Your own row needs a face and a name too, and the profile is the only
            // place this screen has them.
            me = Person(
                id = myUid,
                name = profile?.fullname.orEmpty().ifBlank { "You" },
                handle = "",
                photo = profile?.profilePicture.orEmpty()
            ),
            onRemove = {
                reactionsFor = null
                // Sending the reaction already there is how the server removes it,
                // which react() already knows.
                message.reactions.firstOrNull { it.uid == myUid }
                    ?.let { react(message, it.emoji) }
            },
            onDismiss = { reactionsFor = null }
        )
    }

    reactionPickerFor?.let { message ->
        ReactionPicker(
            onPick = {
                reactionPickerFor = null
                react(message, it)
            },
            onDismiss = { reactionPickerFor = null }
        )
    }

    forwarding?.let { message ->
        ForwardPicker(
            token = token,
            exclude = conversationId,
            onDismiss = { forwarding = null },
            onSend = { targets -> forwardTo(message, targets) }
        )
    }

    info?.let { detail ->
        MessageInfoSheet(detail) { info = null }
    }

    if (searchOpen) {
        InConversationSearch(
            token = token,
            conversationId = conversationId,
            onDismiss = { searchOpen = false }
        )
    }

    if (sharedOpen) {
        SharedItems(
            token = token,
            conversationId = conversationId,
            // The thread already knows how to find a message and mark it; sharing
            // that is better than a second way of looking at the same picture.
            onOpen = { id -> jumpTo(id) },
            onDismiss = { sharedOpen = false }
        )
    }

    viewing?.let { shot ->
        MediaViewer(
            messageId = shot.id,
            video = shot.mediaKind == "video",
            // What the bubble already has, so the screen is never blank while the
            // full file is fetched.
            placeholder = MediaCache.peek(shot.id) ?: MediaCache.peekTiny(shot.id),
            aspect = shot.mediaAspect,
            onDismiss = { viewing = null }
        )
    }

    if (peerOpen) {
        PeerProfileSheet(
            person = person,
            peer = peer,
            conversationId = conversationId,
            muted = remote?.muted == true,
            token = token,
            onAudioCall = { onAudioCall(person.id) },
            onVideoCall = { onVideoCall(person.id) },
            onSearch = { searchOpen = true },
            onShared = { sharedOpen = true },
            onDismiss = { peerOpen = false }
        )
    }

    if (attachmentsOpen) {
        AttachmentSheet(
            onDismiss = { attachmentsOpen = false },
            onSend = { attachmentsOpen = false }
        )
    }
}

/**
 * Maps a server message onto the bubble model the mockup's composables speak, so
 * the whole visual layer is reused rather than rebuilt.
 *
 * Types the bubbles cannot draw yet - media, location, contact cards - degrade to
 * a label rather than an empty bubble.
 */
private fun ChatMessage.toBubble(
    myUid: String,
    peerName: String = "",
    deliveredSeq: Int = 0,
    readSeq: Int = 0
): Message = Message(
    id = id.ifBlank { clientId },
    text = when {
        isDeleted -> "This message was deleted"
        text.isNotBlank() -> text
        // A call message has no text of its own - what it is, is the call - so
        // without this the bubble was empty. Every call ever placed left one.
        call != null -> call.label
        else -> when (type) {
            "image" -> "Photo"
            "video" -> "Video"
            "voice" -> "Voice message"
            "audio" -> "Audio"
            "document" -> "Document"
            "location" -> "Location"
            "contact" -> "Contact"
            // Named even without the call object, which a push can leave out.
            "voice_call" -> "Voice call"
            "video_call" -> "Video call"
            else -> ""
        }
    },
    time = when {
        pending -> "sending"
        failed -> "not sent"
        else -> messageClock(createdAt)
    },
    fromMe = senderUid == myUid,
    kind = when {
        call != null -> MessageKind.CALL
        type == "voice" -> MessageKind.VOICE
        else -> MessageKind.TEXT
    },
    call = call?.let { CallNote(video = it.video, missed = it.missed, seconds = it.duration) },
    quoted = replyTo?.let {
        val them = peerName.substringBefore(" ").ifBlank { "them" }
        QuotedMessage(
            // Messenger phrases this as a sentence above the bubble, so the label
            // is built here where both names are known.
            author = when {
                senderUid == myUid && it.senderUid == myUid -> "You replied to yourself"
                senderUid == myUid -> "You replied to $them"
                it.senderUid == myUid -> "$them replied to you"
                else -> "$them replied to themselves"
            },
            text = it.text.ifBlank { it.type.replaceFirstChar { c -> c.uppercase() } },
            id = it.id,
            mediaKind = it.type.takeIf { k -> k != "text" }.orEmpty()
        )
    },
    reaction = reactions.firstOrNull()?.emoji,
    reactionCount = reactions.size,
    status = if (senderUid != myUid) null else when {
        pending -> DeliveryStatus.SENDING
        failed -> DeliveryStatus.FAILED
        // The server reports how far the peer has reached, so a message counts as
        // read once their position is at or past its own seq.
        seq in 1..readSeq -> DeliveryStatus.SEEN
        seq in 1..deliveredSeq -> DeliveryStatus.DELIVERED
        else -> DeliveryStatus.SENT
    },
    voiceLength = media?.durationMs?.let { ms ->
        val total = ms / 1000
        "%d:%02d".format(total / 60, total % 60)
    } ?: "0:00",
    // What the server returned, or what this device measured when it recorded it.
    waveform = media?.waveform.orEmpty().ifEmpty { VoiceWaveforms.of(id) },
    link = linkPreview,
    createdAt = createdAt,
    mediaKind = media?.kind.takeIf { !isDeleted },
    mediaAspect = media?.let { m ->
        if (m.width > 0 && m.height > 0) m.width.toFloat() / m.height else 1f
    } ?: 1f
)

/** Bubble timestamps are the clock only; the date is carried by the day chip. */
private fun messageClock(millis: Long): String =
    if (millis <= 0L) ""
    else java.text.SimpleDateFormat("h:mm a", java.util.Locale.US)
        .format(java.util.Date(millis))

/** A real conversation with nothing in it - the state right after opening one. */
@Composable
private fun EmptyThread(name: String, error: String?) {
    val d = IqTheme.dimens
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = d.gutter)
        ) {
            Text(
                error ?: "No messages yet",
                style = MaterialTheme.typography.titleLarge,
                color = Ink,
                textAlign = TextAlign.Center
            )
            if (error == null) {
                Spacer(Modifier.height(d.gapSm))
                Text(
                    "Say hello to " + name.substringBefore(" ") + ".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Re-reads the thread after a change that the server owns.
 *
 * Reactions and delete-for-everyone rewrite a message in place rather than adding
 * one, so there is nothing to append - the page has to be read again.
 */
private suspend fun refreshThread(
    token: String,
    conversationId: String,
    thread: MutableList<ChatMessage>
) {
    val page = MessagesApi.history(token, conversationId)
    if (page is ApiResult.Success) {
        thread.clear()
        thread.addAll(page.data.items.reversed())
    }
}

/** How long the server lets you edit and unsend, straight from the spec. */
private const val EDIT_WINDOW_MS = 15 * 60 * 1000L
private const val UNSEND_WINDOW_MS = 48 * 60 * 60 * 1000L


/**
 * The rest of what you can do to one message - what would not fit on the bar.
 *
 * Reply, Copy and Delete are on the bar because they are most of what anyone does;
 * this holds the rarer half, and only the parts the server would actually accept.
 * The windows are its: editing closes after 15 minutes, unsending after 48 hours,
 * and both are yours alone. Offering either outside its window would only produce
 * a refusal.
 */
@Composable
private fun MessageActions(
    message: ChatMessage,
    mine: Boolean,
    onEdit: () -> Unit,
    onDelete: (Boolean) -> Unit,
    onForward: () -> Unit,
    onInfo: () -> Unit,
    onDismiss: () -> Unit
) {
    val d = IqTheme.dimens
    val age = System.currentTimeMillis() - message.createdAt
    val canEdit = mine && message.type == "text" && !message.isDeleted && age < EDIT_WINDOW_MS
    val canUnsend = mine && !message.isDeleted && age < UNSEND_WINDOW_MS

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(d.radiusXl))
                .background(Color.White)
                .padding(vertical = d.gapMd)
        ) {
            if (!message.isDeleted) ActionRow(IqIcons.ArrowForward, "Forward", onForward)
            // Only your own message has receipts worth reading.
            if (mine && !message.isDeleted) ActionRow(IqIcons.Check, "Message info", onInfo)
            if (canEdit) ActionRow(IqIcons.Edit, "Edit message", onEdit)
            if (canUnsend) ActionRow(IqIcons.Trash, "Unsend for everyone") { onDelete(true) }
            ActionRow(IqIcons.Trash, "Delete for me") { onDelete(false) }
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    val d = IqTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = d.gutter, vertical = d.gapMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(d.icon), tint = Ink)
        Spacer(Modifier.width(d.gapMd))
        Text(label, style = MaterialTheme.typography.titleMedium, color = Ink)
    }
}

/** A failure the user should see, dismissible because it is not fatal. */
@Composable
private fun ThreadError(message: String, onDismiss: () -> Unit) {
    val d = IqTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .background(Crimson.copy(alpha = 0.10f))
            .padding(horizontal = d.gutter, vertical = d.gapSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(IqIcons.HelpCircle, null, Modifier.size(d.iconSm), tint = Crimson)
        Spacer(Modifier.width(d.gapSm))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = Crimson,
            modifier = Modifier.weight(1f)
        )
        Icon(
            IqIcons.Close,
            "Dismiss",
            Modifier
                .size(d.icon)
                .clip(CircleShape)
                .clickable(onClick = onDismiss),
            tint = Crimson
        )
    }
}


/**
 * The preview of a link in the compose box.
 *
 * Text only: the image would have to come through this server's proxy to keep the
 * reader's address off the linked site, and a card this small does not need one.
 */
@Composable
private fun LinkPreviewCard(preview: LinkPreview, onDismiss: () -> Unit) {
    val d = IqTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = d.gutter, vertical = d.gapSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The picture where the accent bar used to be. It says more about the link
        // than a coloured rule does, and it is the same image the sent bubble will
        // show - so the compose box is a preview of the message, not of the API.
        if (preview.imageUrl != null) {
            LinkImage(preview.imageUrl, d.scaled(40), RoundedCornerShape(d.radiusSm))
        } else {
            Box(
                Modifier
                    .width(d.scaled(3))
                    .height(d.scaled(40))
                    .clip(RoundedCornerShape(d.radiusSm))
                    .background(Crimson)
            )
        }
        Spacer(Modifier.width(d.gapMd))
        Column(Modifier.weight(1f)) {
            Text(
                preview.title.ifBlank { preview.url },
                style = MaterialTheme.typography.titleSmall,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                preview.siteName.ifBlank { preview.description },
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            IqIcons.Close,
            "Remove preview",
            Modifier
                .size(d.icon)
                .clip(CircleShape)
                .clickable(onClick = onDismiss),
            tint = InkMuted
        )
    }
}

/**
 * A preview's image, fetched **through the server**.
 *
 * `image_url` already points at this server's proxy and is signed, so it is used
 * exactly as given and carries the bearer token. Going to the original address
 * instead would hand the linked site the reader's IP - which is the thing the proxy
 * exists to prevent.
 */
@Composable
private fun LinkImage(url: String, size: Dp, shape: Shape) {
    val context = LocalContext.current
    val token = remember { SessionStore(context).token }
    var bitmap by remember(url) {
        mutableStateOf(RemoteImage.peek(url, RemoteImage.PREVIEW_PX))
    }

    LaunchedEffect(url, token) {
        if (bitmap == null) {
            bitmap = RemoteImage.load(url, token, RemoteImage.PREVIEW_PX)
        }
    }

    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(CrimsonTint),
        contentAlignment = Alignment.Center
    ) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * The card under a link in a sent message.
 *
 * Inside the bubble rather than beside it, because it belongs to that message and a
 * card of its own would read as a second one. Tinted against the bubble it sits in -
 * white on crimson, grey on white - so it reads as part of it either way.
 */
@Composable
private fun LinkInBubble(preview: LinkPreview, fromMe: Boolean) {
    val d = IqTheme.dimens
    val panel = if (fromMe) Color.White.copy(alpha = 0.16f) else Color.White
    val title = if (fromMe) Color.White else Ink
    val subtitle = if (fromMe) Color.White.copy(alpha = 0.75f) else InkMuted
    val uriHandler = LocalUriHandler.current

    Column(
        Modifier
            .padding(top = d.gapXs)
            .clip(RoundedCornerShape(d.radiusMd))
            .background(panel)
            .clickable { runCatching { uriHandler.openUri(preview.url) } }
    ) {
        preview.imageUrl?.let {
            LinkImage(
                it,
                d.scaled(132),
                RoundedCornerShape(topStart = d.radiusMd, topEnd = d.radiusMd)
            )
        }
        Column(Modifier.padding(horizontal = d.gapSm, vertical = d.gapXs)) {
            Text(
                preview.siteName.ifBlank { hostOf(preview.url) }.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                preview.title.ifBlank { preview.url },
                style = MaterialTheme.typography.titleSmall,
                color = title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (preview.description.isNotBlank()) {
                Text(
                    preview.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = subtitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** The site, for when the server could not name it. */
private fun hostOf(url: String): String =
    runCatching { java.net.URI(url).host.orEmpty().removePrefix("www.") }.getOrDefault("")

/**
 * What you are replying to, above the composer.
 *
 * Mirrors the quote that will appear inside the sent bubble, so the message is
 * composed against the same thing it will be attached to.
 */
@Composable
private fun ReplyingTo(
    message: ChatMessage,
    mine: Boolean,
    peerName: String,
    onCancel: () -> Unit
) {
    val d = IqTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = d.gutter, vertical = d.gapSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(d.scaled(3))
                .height(d.scaled(38))
                .clip(RoundedCornerShape(d.radiusSm))
                .background(Crimson)
        )
        Spacer(Modifier.width(d.gapMd))
        // Swiping a photo to reply should show the photo here, not the word
        // "Photo" - otherwise, having picked one out of several, you cannot tell
        // which one you are answering.
        val kind = message.media?.kind
        if (kind == "image" || kind == "video") {
            MediaThumb(message.id, d.scaled(38), video = kind == "video")
            Spacer(Modifier.width(d.gapMd))
        }
        Column(Modifier.weight(1f)) {
            Text(
                if (mine) "Replying to yourself" else "Replying to ${peerName.substringBefore(" ")}",
                style = MaterialTheme.typography.titleSmall,
                color = Crimson,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                message.text.ifBlank { message.type.replaceFirstChar { c -> c.uppercase() } },
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            IqIcons.Close,
            "Cancel reply",
            Modifier
                .size(d.icon)
                .clip(CircleShape)
                .clickable(onClick = onCancel),
            tint = InkMuted
        )
    }
}

/**
 * Drops one emoji, not one char.
 *
 * Most emoji are surrogate pairs and many carry a variation selector or a skin
 * tone, so deleting a single `Char` would leave half a glyph behind.
 */
private fun String.dropLastEmoji(): String {
    if (isEmpty()) return this
    var end = length
    // Trailing modifiers belong to the glyph before them.
    while (end > 0) {
        val cp = codePointBefore(end)
        val width = Character.charCount(cp)
        end -= width
        val isModifier = cp == 0xFE0F || cp == 0x200D || cp in 0x1F3FB..0x1F3FF
        if (!isModifier) {
            // A zero-width joiner before this one means the sequence continues.
            if (end > 0 && codePointBefore(end) == 0x200D) continue
            break
        }
    }
    return substring(0, end)
}

/** How far the finger must travel left before a release cancels instead of sends. */
private const val CANCEL_SLIDE_PX = 120f

/** Replaces the text field while the mic is held. */
@Composable
private fun RowScope.RecordingField(elapsedMs: Long) {
    val d = IqTheme.dimens
    Row(
        Modifier
            .weight(1f)
            .heightIn(min = d.fieldHeight)
            .clip(CircleShape)
            .background(CrimsonTint)
            .padding(horizontal = d.gapLg, vertical = d.gapSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(d.gapSm)
                .clip(CircleShape)
                .background(Crimson)
        )
        Spacer(Modifier.width(d.gapSm))
        Text(
            elapsed(elapsedMs),
            style = MaterialTheme.typography.bodyLarge,
            color = Ink
        )
        Spacer(Modifier.weight(1f))
        Text(
            "Slide left to cancel",
            style = MaterialTheme.typography.bodyMedium,
            color = InkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun elapsed(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}

/* -- top bar --------------------------------------------------------------- */

@Composable
private fun ChatTopBar(
    person: Person,
    onBack: () -> Unit,
    onAudioCall: () -> Unit,
    onVideoCall: () -> Unit,
    onSearch: () -> Unit = {},
    onShared: () -> Unit = {},
    onOpenPeer: () -> Unit = {}
) {
    val d = IqTheme.dimens
    var menuOpen by remember { mutableStateOf(false) }
    // Read here rather than passed in: it is a snapshot map, so this recomposes
    // when the answer lands without the screen above having to thread it down.
    val presence = PresenceRepository.of(person.id)
    Column(Modifier.background(Color.White)) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = d.gapXs, end = d.gapXs, top = d.gapXs, bottom = d.gapSm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleIconButton(
                icon = IqIcons.ArrowBack,
                contentDescription = "Back",
                onClick = onBack,
                borderColor = Hairline
            )
            // The face and the name are one target, as they are in Messenger -
            // tapping either opens the person. They were not a target at all, which
            // is the first place anyone taps to see who they are talking to.
            //
            // weight(1f) is on the row rather than the name now, so the name still
            // shrinks before the call buttons do.
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(d.radiusMd))
                    .clickable(onClick = onOpenPeer)
                    .padding(end = d.gapXs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(person = person, size = d.avatarMd, showPresence = true)
                Spacer(Modifier.width(d.gapSm))
                Column(Modifier.weight(1f)) {
                    Text(
                        person.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // Nothing at all when there is nothing to say, rather than a
                    // comforting guess. Someone who hides their active status comes
                    // back from the server indistinguishable from someone who has
                    // never opened the app, and inventing a line for them would be
                    // undoing the setting they chose.
                    val line = presenceLabel(presence)
                    if (line.isNotEmpty()) {
                        Text(
                            line,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (person.online) OnlineGreen else InkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            CircleIconButton(
                icon = IqIcons.Phone,
                contentDescription = "Voice call",
                onClick = onAudioCall,
                size = d.iconButton,
                background = CrimsonTint,
                tint = Crimson
            )
            CircleIconButton(
                icon = IqIcons.Videocam,
                contentDescription = "Video call",
                onClick = onVideoCall,
                size = d.iconButton,
                background = CrimsonTint,
                tint = Crimson
            )
            Box {
                CircleIconButton(
                    icon = IqIcons.MoreVert,
                    contentDescription = "More options",
                    onClick = { menuOpen = true },
                    size = d.iconButtonSm
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Search in chat") },
                        onClick = {
                            menuOpen = false
                            onSearch()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Shared media") },
                        onClick = {
                            menuOpen = false
                            onShared()
                        }
                    )
                }
            }
        }
        HairlineDivider()
    }
}

/* -- message rendering ------------------------------------------------------ */

/**
 * The "You replied to Samim" line and the quoted message, above the bubble.
 *
 * Messenger puts this outside the reply rather than inside it, which keeps the
 * reply itself readable and makes the quote skimmable down the thread. The quote is
 * a muted bubble of its own, not a coloured strip.
 */
@Composable
private fun ReplyContext(quoted: QuotedMessage, fromMe: Boolean, onJump: () -> Unit) {
    val d = IqTheme.dimens
    Column(
        horizontalAlignment = if (fromMe) Alignment.End else Alignment.Start,
        modifier = Modifier
            .padding(bottom = d.gapXs)
            // The quote is a pointer, so it should behave like one: following it is
            // the only thing anybody wants from it.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onJump
            )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                IqIcons.ArrowBack,
                null,
                Modifier.size(d.iconXs),
                tint = InkFaint
            )
            Spacer(Modifier.width(d.gapXs))
            Text(
                quoted.author,
                style = MaterialTheme.typography.bodySmall,
                color = InkFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(d.gapXs))
        Row(
            Modifier
                .clip(RoundedCornerShape(d.scaled(15)))
                .background(FieldGrey),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // A picture answered by name only - "Photo" - tells you nothing about
            // which photo, which is the whole question a quote exists to answer.
            if (quoted.mediaKind == "image" || quoted.mediaKind == "video") {
                MediaThumb(quoted.id, d.scaled(38), video = quoted.mediaKind == "video")
            }
            Text(
                quoted.text,
                style = MaterialTheme.typography.bodyMedium,
                color = InkMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = d.gapMd, vertical = d.gapSm)
            )
        }
    }
}

/**
 * A message's picture, small.
 *
 * Shared by everything in the thread that needs to show *which* photo without
 * showing the photo: the quote above a reply, the bar over the composer, and the
 * list of what has been shared. All of them reach it through [MediaCache], which
 * asks for the server's thumbnail before the file itself - so these cost a few score
 * kilobytes each rather than the original.
 *
 * Falls back to the gallery mark while there is nothing to draw, which is also what
 * stays if the picture cannot be fetched.
 */
@Composable
internal fun MediaThumb(
    messageId: String,
    size: Dp,
    video: Boolean = false,
    /**
     * How the thumbnail is laid out, when the caller wants something other than a
     * square of [size] - a grid cell, say, which takes its width from the column.
     * [size] still decides what is fetched and decoded either way.
     */
    modifier: Modifier? = null
) {
    val bitmap by rememberThreadPreview(messageId, video = video)

    Box(
        (modifier ?: Modifier.size(size))
            .clip(RoundedCornerShape(IqTheme.dimens.radiusSm))
            .background(CrimsonTint),
        contentAlignment = Alignment.Center
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                IqIcons.Gallery,
                null,
                Modifier.size(IqTheme.dimens.iconSm),
                tint = Crimson
            )
        }
    }
}

/** More than a handful and it is a sentence written in pictures, not a reaction. */
private const val MAX_BARE_EMOJI = 3

/**
 * How many emoji the string is, or 0 if it is anything else.
 *
 * Walked by code point because almost every emoji is a surrogate pair, and the
 * joiners, skin tones and variation selectors between them are part of a glyph
 * rather than glyphs of their own.
 */
private fun String.emojiOnlyCount(): Int {
    val text = trim()
    if (text.isEmpty()) return 0
    var i = 0
    var count = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        i += Character.charCount(cp)
        when {
            cp == 0x200D || cp == 0xFE0F || cp == 0xFE0E -> Unit      // joiner, selector
            cp in 0x1F3FB..0x1F3FF -> Unit                            // skin tone
            Character.isWhitespace(cp) -> Unit
            isEmoji(cp) -> count++
            else -> return 0                                          // a letter: not bare
        }
    }
    return count
}

private fun isEmoji(cp: Int): Boolean =
    cp in 0x1F300..0x1FAFF ||      // pictographs, faces, symbols
        cp in 0x1F000..0x1F2FF ||  // tiles, enclosed letters
        cp in 0x2600..0x27BF ||    // misc symbols and dingbats
        cp in 0x2B00..0x2BFF ||
        cp in 0x2190..0x21FF ||
        cp == 0x203C || cp == 0x2049

/**
 * A reaction, hanging off the bubble's lower edge.
 *
 * It overlaps rather than sitting under: a chip on its own line would read as
 * another message, and the overlap is what ties it to the one it belongs to. The
 * ring in the chat's own background colour is what keeps it legible against a
 * bubble of any colour.
 */
@Composable
private fun ReactionChip(
    emoji: String,
    count: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val d = IqTheme.dimens
    Row(
        modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .background(ChatCanvas)
            .padding(2.dp)
            .clip(CircleShape)
            .background(Color.White)
            .padding(horizontal = d.gapXs, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, style = MaterialTheme.typography.bodySmall)
        if (count > 1) {
            Spacer(Modifier.width(2.dp))
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = InkMuted
            )
        }
    }
}

/** One line of the thread: the bubble, and the stamp that goes above it if any. */
@Immutable
private data class ThreadRow(val message: Message, val separator: String?)

/**
 * Draws at its own size while measuring as nothing.
 *
 * The reply arrow hangs outside the bubble it belongs to, so it has no business
 * sizing the box that holds them both: a disc of 38dp is wider than a bubble holding
 * one letter, and left to count it would widen that bubble's container and shift the
 * bubble inside it. Reported as a point, it cannot.
 *
 * The point is the centre rather than the top left, which is what makes the two
 * alignments symmetrical - the disc straddles whichever edge it was aligned to, and
 * one offset of half its width clears it either way.
 */
private fun Modifier.weightless(): Modifier = layout { measurable, _ ->
    val placeable = measurable.measure(Constraints())
    layout(0, 0) { placeable.place(-placeable.width / 2, -placeable.height / 2) }
}

/**
 * Where a bubble ended up, kept out of snapshot state on purpose.
 *
 * A plain field, so writing it from `onGloballyPositioned` costs nothing and a
 * scrolled frame does no work at all. Making it observable would invalidate every
 * visible row sixty times a second - and worse, writing to snapshot state from
 * inside the layout phase, which is where that callback runs, forces a second
 * layout pass before the frame can be drawn. Two versions of this class did exactly
 * that, and it was the stutter that survived every other fix: a row's position is
 * unknown until it has been positioned once, so the extra pass landed on the first
 * frame of every row scrolling into view.
 *
 * Nobody needs telling when it changes. The one caller asks from a long press,
 * which is to say long after whichever frame this was last written on.
 */
private class RowMarks {

    var bubble: LayoutCoordinates? = null

    /** The bubble in window coordinates, for anchoring the long-press tray. */
    fun bubbleInRoot(): Rect {
        val b = bubble ?: return Rect.Zero
        return if (b.isAttached) b.boundsInRoot() else Rect.Zero
    }
}

/**
 * A file on its way up, as a bubble.
 *
 * Always `fromMe` and always last: you cannot be uploading someone else's photo, and
 * it will land at the end of the thread when it arrives.
 */
private fun UploadQueue.Outgoing.toBubble(): Message = Message(
    id = clientId,
    text = "",
    time = if (failed) "not sent" else "sending",
    fromMe = true,
    // A recording already carries its own shape and length, so the bubble is the
    // finished one from the first frame rather than a placeholder that is replaced.
    kind = if (kind == "voice") MessageKind.VOICE else MessageKind.TEXT,
    waveform = waveform,
    voiceLength = clock(durationMs),
    mediaKind = kind,
    mediaAspect = aspect,
    localUri = uri.toString(),
    uploadProgress = progress,
    uploadFailed = failed,
    status = if (failed) DeliveryStatus.FAILED else DeliveryStatus.SENDING,
    createdAt = startedAt,
    group = BubbleGroup.SINGLE
)

/**
 * How much of the thread is built before it is needed, and kept after it is not.
 *
 * The default window is one item ahead in the direction of travel, thrown away the
 * moment it leaves the screen. For a list of plain rows that is enough. A message is
 * not a plain row: it brings a gesture detector, a bubble shape, text to lay out and,
 * for a photo or a recording, an effect that goes looking for bytes. Composing all of
 * that in the one frame between "not needed" and "on screen" is work that has to land
 * somewhere, and it lands on that frame.
 *
 * Half a screen either way, so a message is built while there is still half a screen
 * to scroll before anyone sees it - and a message scrolled just off the top is still
 * there when the thumb comes back for it, which is the case that was asked about.
 *
 * The cost is composing rows that may never be looked at. Bounded here: the window is
 * a fraction of the viewport rather than a count, so it does not grow with the
 * thread, and the fetches those rows start are already behind a limit of three at a
 * time. Raise it if the scroll still catches on a fast fling; lower it if a slow
 * phone starts fetching pictures nobody asked to see.
 *
 * Handed to `rememberLazyListState`, not to `LazyColumn`. Foundation later moved it
 * to a parameter on the composable and deprecated this route, but that parameter does
 * not exist on 1.10.4, which the BOM pins. If the BOM moves far enough forward this
 * will start warning; the fix then is to pass it to the list instead, not to drop it.
 *
 * The opt-in is on this property because it is read at class initialisation, outside
 * any composable - the two that carry one of their own cannot cover it.
 */
@OptIn(ExperimentalFoundationApi::class)
private val ThreadCacheWindow =
    LazyLayoutCacheWindow(aheadFraction = 0.5f, behindFraction = 0.5f)

/**
 * How long a landing message takes to fade in.
 *
 * Short. This is the acknowledgement that a message was sent, and an
 * acknowledgement that takes its time stops reading as one.
 */
private const val ENTER_MS = 180

/** How far a landing bubble rises, in pixels. A nudge, not a flight. */
private const val ENTER_RISE_PX = 28f

/** Long enough to find the marked message, short enough not to linger. */
private const val HIGHLIGHT_MS = 1600L

/**
 * Where each message sits in a run.
 *
 * Six things have to hold for two messages to stack into one block, and each breaks
 * it for its own reason:
 *
 * - **Same sender.** Obvious, and the one everybody implements.
 * - **Same type.** A photo and a sentence are different shapes; stacking them would
 *   flatten the corners between a bubble and a picture, which reads as a mistake
 *   rather than as a group.
 * - **Both are words.** Pictures never join a run, even to each other: sent one
 *   after another they used to close up into a single slab with one avatar and one
 *   tick under the lot, and then there is no telling how many there are or which one
 *   the tick is about.
 * - **Neither is a reply.** A reply carries a header naming what it answers; buried
 *   mid-block that header looks like it belongs to the message above.
 * - **Nothing came between them.** The stamp is the divider, and the only one time
 *   gets: before it is one block, after it is another.
 *
 * **Time divides only through the stamp**, which is the part that was wrong. There
 * used to be a second rule here - five minutes - held apart from the twenty the stamp
 * needs. Messages six minutes apart therefore broke into separate blocks with nothing
 * on screen to say why, and a reader has no way to tell that from a bug. A run should
 * only ever be broken by something they can see.
 */
internal fun runsIn(bubbles: List<Message>, stamps: List<String?>): List<BubbleGroup> {
    fun joined(at: Int): Boolean {
        val b = bubbles.getOrNull(at) ?: return false
        val a = bubbles.getOrNull(at - 1) ?: return false
        if (stamps.getOrNull(at) != null) return false
        return a.fromMe == b.fromMe &&
            a.mediaKind == null && b.mediaKind == null &&
            a.kind == MessageKind.TEXT && b.kind == MessageKind.TEXT &&
            b.quoted == null
    }

    return bubbles.indices.map { i ->
        val startsRun = !joined(i)
        val endsRun = !joined(i + 1)
        when {
            startsRun && endsRun -> BubbleGroup.SINGLE
            startsRun -> BubbleGroup.FIRST
            endsRun -> BubbleGroup.LAST
            else -> BubbleGroup.MIDDLE
        }
    }
}


/**
 * The stamp between runs: `8:15 PM`, `SUN AT 8:33 PM`, `3 OCT AT 9:02 AM`.
 *
 * Returned only where there is a real pause, so a burst of messages carries one
 * stamp rather than one per bubble.
 */
private fun separatorBefore(messages: List<Message>, index: Int): String? {
    val current = messages.getOrNull(index) ?: return null
    if (current.createdAt <= 0L) return null
    val previous = messages.getOrNull(index - 1)
    if (previous != null && current.createdAt - previous.createdAt < SEPARATOR_GAP_MS) return null

    val now = java.util.Calendar.getInstance()
    val then = java.util.Calendar.getInstance().apply { timeInMillis = current.createdAt }
    val clock = java.text.SimpleDateFormat("h:mm a", java.util.Locale.US).format(then.time)

    fun sameDay(a: java.util.Calendar, b: java.util.Calendar) =
        a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR) &&
            a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR)

    val weekAgo = (now.clone() as java.util.Calendar)
        .apply { add(java.util.Calendar.DAY_OF_YEAR, -6) }

    return when {
        sameDay(now, then) -> clock
        then.after(weekAgo) ->
            java.text.SimpleDateFormat("EEE", java.util.Locale.US)
                .format(then.time).uppercase() + " AT " + clock
        else ->
            java.text.SimpleDateFormat("d MMM", java.util.Locale.US)
                .format(then.time).uppercase() + " AT " + clock
    }
}

/** Messages closer together than this belong to the same stretch of talking. */
private const val SEPARATOR_GAP_MS = 20 * 60 * 1000L

@Composable
private fun TimeSeparator(label: String) {
    val d = IqTheme.dimens
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = d.gapLg, bottom = d.gapSm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = InkFaint,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Delivery, shown the way Messenger shows it: a small mark under the last of your
 * own messages rather than a tick inside the bubble.
 *
 * Seen is the peer's own face, which is why it reads instantly - the others are a
 * ring that fills as the message gets further.
 */
@Composable
private fun DeliveryMark(status: DeliveryStatus, peer: Person) {
    val d = IqTheme.dimens
    val size = d.scaled(14)
    Box(
        Modifier
            .padding(top = d.gapXs, end = d.gapXs)
            .size(size),
        contentAlignment = Alignment.Center
    ) {
        when (status) {
            DeliveryStatus.SEEN -> Avatar(person = peer, size = size)

            DeliveryStatus.DELIVERED -> Box(
                Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(InkFaint),
                contentAlignment = Alignment.Center
            ) {
                Icon(IqIcons.Check, "Delivered", Modifier.size(size * 0.7f), tint = Color.White)
            }

            DeliveryStatus.SENT -> Box(
                Modifier
                    .size(size)
                    .clip(CircleShape)
                    .border(1.dp, InkFaint, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(IqIcons.Check, "Sent", Modifier.size(size * 0.6f), tint = InkFaint)
            }

            DeliveryStatus.SENDING -> Box(
                Modifier
                    .size(size)
                    .clip(CircleShape)
                    .border(1.dp, InkFaint, CircleShape)
            )

            DeliveryStatus.FAILED -> Icon(
                IqIcons.HelpCircle, "Not sent", Modifier.size(size), tint = Crimson
            )
        }
    }
}

/** How far the bubble must travel before releasing it fires a reply. */
private val SWIPE_TRIGGER = 56.dp

/** Past the trigger the bubble keeps moving, but grudgingly. */
private val SWIPE_LIMIT = 84.dp

/**
 * A message, swipeable to reply.
 *
 * The drag runs **away from the bubble's own edge** - right on a message that sits
 * on the left, left on your own - so the gesture always pulls the bubble towards
 * the middle rather than off the screen it is already hugging.
 *
 * Past the trigger the bubble resists rather than stopping dead, which is what
 * tells a thumb it has gone far enough; the reply fires on release, so a drag can
 * still be taken back by sliding home.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun MessageRow(
    message: Message,
    peer: Person,
    highlighted: Boolean = false,
    showTime: Boolean = false,
    onTap: () -> Unit = {},
    onLongPress: (Rect) -> Unit = {},
    onReply: () -> Unit = {},
    onReactionClick: () -> Unit = {},
    onJumpToQuoted: (String) -> Unit = {},
    /** A tap on the picture itself, which opens it full screen. */
    onOpenMedia: () -> Unit = {},
    /** True for a message that has just landed, which plays a short entrance. */
    arriving: Boolean = false,
    /** Ring them again, with the kind of call the message is about. */
    onCallBack: (Boolean) -> Unit = {}
) {
    val d = IqTheme.dimens
    // The swipe answers a message, and a call is not one.
    val canReply = message.kind != MessageKind.CALL
    val endsRun = message.group == BubbleGroup.SINGLE || message.group == BubbleGroup.LAST
    val startsRun = message.group == BubbleGroup.SINGLE || message.group == BubbleGroup.FIRST
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val trigger = with(density) { SWIPE_TRIGGER.toPx() }
    val limit = with(density) { SWIPE_LIMIT.toPx() }
    // Positive is right; a message of your own travels the other way.
    val direction = if (message.fromMe) -1f else 1f

    val offset = remember(message.id) { Animatable(0f) }
    var armed by remember(message.id) { mutableStateOf(false) }
    /**
     * Where the row and the bubble are, held **outside** snapshot state.
     *
     * These were two `mutableStateOf(Rect)` fed from `boundsInRoot()`. Root
     * coordinates change on every scrolled frame, so every visible row was writing
     * new state sixty times a second and invalidating itself - which is most of why
     * the thread stuttered. Nothing here needs to recompose when a position
     * changes: the long press reads it once, and the icon reads it at placement.
     */
    val marks = remember(message.id) { RowMarks() }

    /**
     * The entrance: up a little, and up to full size.
     *
     * The list's own `animateItem` fades it in and moves everything above it out of
     * the way; this is the part that makes it look like it came from the composer
     * rather than simply appearing. Small on purpose - a bubble that flies in from
     * the bottom of the screen is a thing being watched, and a sent message should
     * be a thing already done.
     *
     * Driven off a flag flipped on the first frame rather than an `Animatable` run
     * by hand: there is no sequence to control, and a value animated by the system
     * costs nothing once it has settled.
     */
    var landed by remember(message.id) { mutableStateOf(!arriving) }
    LaunchedEffect(message.id) { landed = true }
    val entrance by animateFloatAsState(
        targetValue = if (landed) 1f else 0f,
        animationSpec = tween(ENTER_MS),
        label = "message entrance"
    )
    // Deliberately a function, not a value. Read at composition it would recompose
    // the whole row on every frame of a drag; the two places that want it both read
    // it inside a lambda that runs at draw time instead.
    fun progress(): Float = (kotlin.math.abs(offset.value) / trigger).coerceIn(0f, 1f)

    Box(
        Modifier
            .fillMaxWidth()
            // Nothing at all once it has landed, which is every row but the one that
            // just arrived - and `graphicsLayer` with an unchanging value is not
            // something the row pays for again.
            .graphicsLayer {
                if (entrance < 1f) {
                    alpha = entrance
                    translationY = (1f - entrance) * ENTER_RISE_PX
                    val s = 0.94f + 0.06f * entrance
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(if (message.fromMe) 1f else 0f, 1f)
                }
            }
            // Tight inside a run, open between runs: the gap is what separates one
            // person's turn from the next.
            .padding(top = if (startsRun) d.gapSm else d.scaled(2))
            // The mark sits behind the whole row rather than on the bubble: it has
            // to be findable at a glance, and a tint inside a crimson bubble is not.
            .background(if (highlighted) CrimsonTint else Color.Transparent)
            // No ripple: a tap on a bubble does nothing, so flashing the whole row
            // promises an action that is not there. Only the long press acts, and
            // the tray it opens is its own feedback.
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                // A tap shows when it was sent. Still no ripple: the answer appears
                // under the bubble, which is feedback enough, and a flash across the
                // whole row would suggest the row itself did something.
                onClick = onTap,
                // Read once, when it is asked for, rather than kept up to date.
                onLongClick = { onLongPress(marks.bubbleInRoot()) }
            )
            // Keyed on it as well as the id, so the gesture is rebuilt rather
            // than left installed if the answer ever changes under it.
            .pointerInput(message.id, canReply) {
                // Nothing to swipe towards. Returning here means no detector is
                // installed at all, so a call card does not drag a little and then
                // refuse - it simply is not a thing that moves.
                if (!canReply) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val fired = armed
                        armed = false
                        scope.launch { offset.animateTo(0f, spring()) }
                        if (fired) {
                            Sounds.swipeDone(context)
                            onReply()
                        }
                    },
                    onDragCancel = {
                        armed = false
                        scope.launch { offset.animateTo(0f, spring()) }
                    }
                ) { change, dragAmount ->
                    // Only the direction that means "reply" moves the bubble.
                    val wanted = dragAmount * direction
                    if (wanted <= 0f && offset.value == 0f) return@detectHorizontalDragGestures
                    change.consume()

                    val raw = offset.value + dragAmount
                    val travelled = kotlin.math.abs(raw)
                    // Beyond the trigger the drag is damped, so the bubble slows
                    // instead of following the finger off the edge.
                    val eased = if (travelled <= trigger) travelled
                    else trigger + (travelled - trigger) * 0.35f
                    val clamped = eased.coerceAtMost(limit) * direction

                    scope.launch { offset.snapTo(clamped) }

                    val nowArmed = kotlin.math.abs(clamped) >= trigger * 0.95f
                    if (nowArmed && !armed) {
                        // One piece of feedback at the moment it arms, in two senses
                        // at once - as Messenger does. Only on the way in: crossing
                        // back out is the gesture being taken back, and announcing
                        // that would make a wobble sound like a decision.
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        Sounds.swipeArmed(context)
                    }
                    armed = nowArmed
                }
            },
        contentAlignment = if (message.fromMe) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .fillMaxWidth()
                // Everything that travels on a swipe is inside this node, the reply
                // icon included, so one offset moves the lot and the gap between
                // them cannot drift.
                .offset { IntOffset(offset.value.toInt(), 0) },
            horizontalArrangement = if (message.fromMe) Arrangement.End else Arrangement.Start
        ) {
            if (!message.fromMe) {
                // The avatar marks the end of someone's turn; inside a run the slot
                // is held open so the bubbles stay on one line.
                if (endsRun) {
                    Avatar(person = peer, size = d.scaled(28))
                } else {
                    Spacer(Modifier.width(d.scaled(28)))
                }
                Spacer(Modifier.width(d.gapSm))
            }

            Column(
                horizontalAlignment = if (message.fromMe) Alignment.End else Alignment.Start,
                // Bubbles never exceed 84% of the column, on any screen size.
                modifier = Modifier.fillMaxWidth(0.84f)
            ) {
                message.quoted?.let { quote ->
                    ReplyContext(quote, message.fromMe) {
                        if (quote.id.isNotBlank()) onJumpToQuoted(quote.id)
                    }
                }
                Box(
                    // Kept only to anchor the long-press tray, which asks for it
                    // once, at the moment of the press.
                    Modifier.onGloballyPositioned { marks.bubble = it }
                ) {
                    // The arrow rides in the gutter the bubble is vacating, a fixed
                    // margin off the bubble's own edge.
                    //
                    // Inside the bubble's box rather than beside it, which is what
                    // makes both of those true without measuring anything: centred
                    // on the bubble because the box *is* the bubble, and clear of it
                    // by a constant because the box's edge is the origin. It lands
                    // outside its parent, which Compose draws without complaint -
                    // nothing on this row clips.
                    //
                    // Two earlier versions measured the bubble and fed the answer
                    // back as an offset. Both then had to ask for a second layout
                    // pass once the answer was in, and that pass - on the first
                    // frame of every row that appeared - was the last of the
                    // scroll's stutter. Now there is nothing to ask for: the
                    // position is a constant, so the first frame already has it.
                    Box(
                        Modifier
                            // Centred on the bubble's near edge, then pushed clear
                            // of it: half the disc to get off the edge, the margin
                            // to leave a gap.
                            .align(
                                if (message.fromMe) Alignment.CenterEnd
                                else Alignment.CenterStart
                            )
                            .offset(
                                x = if (message.fromMe) d.iconButtonSm / 2 + d.gapMd
                                else -(d.iconButtonSm / 2 + d.gapMd)
                            )
                            .weightless()
                            .size(d.iconButtonSm)
                            .graphicsLayer {
                                // Read at draw time, so a drag costs neither a
                                // recomposition nor a re-layout. Invisible until one
                                // starts, which is what lets it rest off the edge of
                                // the screen.
                                val p = progress()
                                alpha = p
                                scaleX = 0.6f + 0.4f * p
                                scaleY = 0.6f + 0.4f * p
                            }
                            .clip(CircleShape)
                            // Solid from the first millimetre of the drag, the way
                            // Messenger draws it: the filled disc is the affordance
                            // and the white glyph is what carries against it. An
                            // outline would read as disabled. It turns crimson once
                            // the drag is far enough to fire on release - the
                            // gesture completes on lift, so this is the only thing
                            // telling a thumb it has gone far enough.
                            .drawBehind {
                                drawCircle(if (progress() >= 1f) Crimson else InkMuted)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // The curved reply hook, not a plain back arrow: this answers
                        // the message, it does not navigate away from it.
                        Icon(IqIcons.Reply, null, Modifier.size(d.icon), tint = Color.White)
                    }
                    Column {
                        when {
                            // A video is a picture with a play mark on it. The
                            // frame it opens on is what the server stores as its
                            // thumbnail, so the bubble shows the same thing before
                            // and after it is sent.
                            message.mediaKind == "image" ||
                                message.mediaKind == "video" ->
                                    ImageBubble(message, onOpenMedia)
                            message.kind == MessageKind.CALL ->
                                CallBubble(message) { video -> onCallBack(video) }
                            message.kind == MessageKind.VOICE -> VoiceBubble(message)
                            else -> TextBubble(message)
                        }
                        // Room for the chip to hang into, so it overlaps the bubble
                        // without being clipped by the row below.
                        if (message.reaction != null) Spacer(Modifier.height(d.gapMd))
                    }
                    message.reaction?.let { emoji ->
                        ReactionChip(
                            emoji = emoji,
                            count = message.reactionCount,
                            modifier = Modifier.align(
                                if (message.fromMe) Alignment.BottomStart else Alignment.BottomEnd
                            ),
                            onClick = onReactionClick
                        )
                    }
                }
                // Asked for, so shown on this message alone - not on the run. The
                // run's stamp is the separator above it, which answers a different
                // question: when the talking started, rather than when this line was
                // sent.
                if (showTime && message.time.isNotBlank()) {
                    Text(
                        message.time,
                        style = MaterialTheme.typography.labelSmall,
                        color = InkFaint,
                        modifier = Modifier.padding(top = d.gapXs, bottom = 1.dp)
                    )
                }

                // Only the newest of your own run is marked, as Messenger marks it:
                // the state of the last message is the state of the conversation.
                if (endsRun) {
                    message.status?.let { DeliveryMark(it, peer) }
                }
            }
        }
    }
}

/**
 * A photo in the thread.
 *
 * The bubble is sized from the server's width and height before anything is
 * fetched, so the list does not jump when the image arrives. A caption, when there
 * is one, sits under the picture in the same bubble.
 */
@Composable
private fun ImageBubble(message: Message, onOpen: () -> Unit = {}) {
    val d = IqTheme.dimens
    val isVideo = message.mediaKind == "video"
    val bitmap by rememberThreadPreview(message.id, message.localUri, isVideo)
    // Shown while the full one is fetched, so a photo scrolling back into view is
    // never an empty rectangle.
    val stand = remember(message.id, message.localUri) {
        if (message.localUri.isNotBlank()) null else MediaCache.peekTiny(message.id)
    }

    val shape = RoundedCornerShape(d.scaled(15))
    Column(
        Modifier
            // Half the column a bubble of words gets. A photo reads at a glance and
            // does not need the width a sentence does; at full width a thread of
            // them is a column of pictures with the conversation squeezed between.
            .fillMaxWidth(0.5f)
            .clip(shape)
            .background(if (message.fromMe) Crimson else Color.White)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(message.mediaAspect.coerceIn(0.6f, 1.8f))
                .background(CrimsonTint)
                // The picture opens full screen. Only while it is a picture: during
                // the upload the bubble is showing the local file behind a progress
                // ring, and there is nothing on the server to open yet.
                .then(
                    if (message.uploadProgress < 0f) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onOpen
                        )
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            val image = bitmap ?: stand
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = if (isVideo) "Video" else "Photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // The placeholder keeps the bubble's shape while the bytes arrive.
                Icon(
                    if (isVideo) IqIcons.Videocam else IqIcons.Gallery,
                    null,
                    Modifier.size(d.iconXl),
                    tint = Crimson
                )
            }
            when {
                message.uploadProgress >= 0f ->
                    UploadProgress(message.uploadProgress, message.uploadFailed)

                // Only once it is sent. During the upload the ring is the thing to
                // look at, and a play mark under it would promise something that
                // cannot happen yet.
                isVideo -> VideoMark(message.voiceLength)
            }
        }
        if (message.text.isNotBlank()) {
            Text(
                message.text,
                style = MaterialTheme.typography.messageBody,
                color = if (message.fromMe) Color.White else Ink,
                modifier = Modifier.padding(horizontal = d.gapMd, vertical = d.gapSm)
            )
        }
    }
}

/**
 * What marks a video apart from a photo: a play disc, and how long it runs.
 *
 * The length sits in the corner rather than beside the disc, because the disc is
 * what the eye goes to and the number is what it asks for second.
 */
@Composable
private fun VideoMark(length: String) {
    val d = IqTheme.dimens
    Box(
        Modifier
            .size(d.scaled(44))
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(IqIcons.PlayArrow, null, Modifier.size(d.icon), tint = Color.White)
    }
    if (length.isNotBlank() && length != "0:00") {
        Box(Modifier.fillMaxSize()) {
            Text(
                length,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(d.gapXs)
                    .clip(RoundedCornerShape(d.radiusSm))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = d.gapXs)
            )
        }
    }
}

/** How far a recording has got, in the circle the play button will take over. */
@Composable
private fun VoiceUploadMark(progress: Float, failed: Boolean, color: Color) {
    val d = IqTheme.dimens
    val shown by animateFloatAsState(progress, label = "voice upload")
    if (failed) {
        Icon(IqIcons.Close, "Not sent", Modifier.size(d.iconSm), tint = color)
    } else {
        CircularProgressIndicator(
            progress = { shown },
            modifier = Modifier.size(d.scaled(22)),
            color = color,
            trackColor = color.copy(alpha = 0.3f),
            strokeWidth = d.scaled(2)
        )
    }
}

/**
 * How far a photo has got, over the photo.
 *
 * In the middle of the picture, not under it: the picture is what is being waited
 * for, and a bar somewhere else makes you look in two places. The disc behind it is
 * what keeps a white ring readable over a photo of any colour.
 */
@Composable
private fun UploadProgress(progress: Float, failed: Boolean) {
    val d = IqTheme.dimens
    // Eased so the ring does not jump between percents on a fast connection.
    val shown by animateFloatAsState(progress, label = "upload")
    Box(
        Modifier
            .size(d.scaled(54))
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center
    ) {
        if (failed) {
            Icon(IqIcons.Close, "Upload failed", Modifier.size(d.icon), tint = Color.White)
        } else {
            CircularProgressIndicator(
                progress = { shown },
                modifier = Modifier.size(d.scaled(34)),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.3f),
                strokeWidth = d.scaled(3)
            )
        }
    }
}

/**
 * The message, with its links made to open.
 *
 * `LinkAnnotation` rather than a tap handler of our own: the text layout knows where
 * each link actually is after wrapping, which a gesture on the whole bubble cannot,
 * and it is what gives long-press-to-copy-link and accessibility for free.
 *
 * Underlined, not just coloured. On your own bubble the only colour available is a
 * tint of white, and a tint of white on crimson is the kind of difference that
 * disappears outdoors; the underline is what carries on both backgrounds.
 */
@Composable
private fun linkedText(text: String, fromMe: Boolean): AnnotatedString {
    val colour = if (fromMe) Color.White else Crimson
    // One remember, and no early return before it: a conditional remember changes
    // the shape of the composition between one text and the next, which is the kind
    // of thing that works until the day it does not.
    return remember(text, colour) {
        val spans = LinkPreviewApi.linksIn(text)
        if (spans.isEmpty()) return@remember AnnotatedString(text)

        val styles = TextLinkStyles(
            style = SpanStyle(color = colour, textDecoration = TextDecoration.Underline)
        )
        buildAnnotatedString {
            var at = 0
            spans.forEach { span ->
                if (span.from > at) append(text.substring(at, span.from))
                withLink(LinkAnnotation.Url(span.url, styles)) {
                    append(text.substring(span.from, span.to))
                }
                at = span.to
            }
            if (at < text.length) append(text.substring(at))
        }
    }
}

/**
 * A call that happened, drawn as a card rather than a sentence.
 *
 * A call is not something anybody said, so it is not a speech bubble: it is a record
 * of an event, with the two things worth knowing about it - what kind and how long -
 * and the one thing worth doing about it. Messenger draws it this way and the shape
 * is the reason it reads at a glance.
 *
 * Neutral on both sides, unlike every other bubble here. A call belongs to the two
 * people equally; colouring it by who dialled would be saying it was theirs.
 */
@Composable
private fun CallBubble(message: Message, onCallBack: (Boolean) -> Unit) {
    val d = IqTheme.dimens
    val note = message.call ?: return
    val shape = BubbleShape(
        fromMe = message.fromMe,
        radius = d.scaled(20),
        group = message.group
    )
    Column(
        Modifier
            .widthIn(max = d.contentMaxWidth)
            .wrapContentWidth(if (message.fromMe) Alignment.End else Alignment.Start)
            .clip(shape)
            .background(FieldGrey)
            .padding(d.gapMd)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(d.avatarSm)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    // Direction, not just "a call": whether it was placed or taken
                    // is half of what a line in a history is for.
                    when {
                        note.missed -> IqIcons.CallMissed
                        message.fromMe -> IqIcons.CallMade
                        else -> IqIcons.CallReceived
                    },
                    null,
                    Modifier.size(d.iconSm),
                    tint = if (note.missed) Crimson else Ink
                )
            }
            Spacer(Modifier.width(d.gapSm))
            Column {
                Text(
                    callTitle(note),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink,
                    maxLines = 1
                )
                val detail = callDetail(note)
                if (detail.isNotEmpty()) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkMuted,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(Modifier.height(d.gapMd))
        Row(
            Modifier
                .fillMaxWidth()
                .height(d.buttonHeight)
                .clip(RoundedCornerShape(d.radiusMd))
                .background(Color.White)
                .clickable { onCallBack(note.video) },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Call back",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Ink,
                maxLines = 1
            )
        }
    }
}

private fun callTitle(note: CallNote): String {
    val kind = if (note.video) "Video call" else "Audio call"
    return if (note.missed) "Missed " + kind.lowercase() else kind
}

/**
 * How long it lasted, spelled out.
 *
 * Words rather than a clock face. "4 mins 5 secs" is read once; "4:05" under a
 * heading that is not a time is read twice, the first time as something else.
 *
 * Nothing at all for a call that never connected - the title has already said it
 * was missed, and "0 secs" under it would be arithmetic about an event that did
 * not happen.
 */
private fun callDetail(note: CallNote): String {
    if (note.missed || note.seconds <= 0) return ""
    val minutes = note.seconds / 60
    val seconds = note.seconds % 60
    val mins = if (minutes == 1) "1 min" else "$minutes mins"
    val secs = if (seconds == 1) "1 sec" else "$seconds secs"
    return when {
        minutes == 0 -> secs
        seconds == 0 -> mins
        else -> "$mins $secs"
    }
}

@Composable
private fun TextBubble(message: Message) {
    val d = IqTheme.dimens
    val shape = BubbleShape(
        fromMe = message.fromMe,
        radius = d.scaled(20),
        group = message.group
    )
    val bg = if (message.fromMe) Crimson else FieldGrey
    val fg = if (message.fromMe) Color.White else Ink

    // A message that is nothing but emoji is sent to be looked at, so it is drawn
    // large and bare - a bubble around three characters is mostly bubble.
    val emojiCount = message.text.emojiOnlyCount()
    if (emojiCount in 1..MAX_BARE_EMOJI) {
        Box(
            Modifier
                .wrapContentWidth(if (message.fromMe) Alignment.End else Alignment.Start)
                .padding(vertical = d.gapXs)
        ) {
            Text(
                message.text,
                style = MaterialTheme.typography.displaySmall,
                color = Ink
            )
        }
        return
    }

    // Nothing but the words. The time lives in the separator above the run and the
    // delivery state under the last bubble, so a burst of five messages is five
    // lines of text rather than five lines each wearing a clock.
    Box(
        Modifier
            .wrapContentWidth(if (message.fromMe) Alignment.End else Alignment.Start)
            .clip(shape)
            .background(bg)
            .padding(horizontal = d.gapLg, vertical = d.gapMd)
    ) {
        val body = linkedText(message.text, message.fromMe)
        val link = message.link
        if (link == null) {
            Text(body, style = MaterialTheme.typography.messageBody, color = fg)
        } else {
            // The words stay above the card: the message is what was written, and
            // the preview is what the server found out about it afterwards.
            Column(Modifier.widthIn(max = d.scaled(240))) {
                Text(body, style = MaterialTheme.typography.messageBody, color = fg)
                LinkInBubble(link, message.fromMe)
            }
        }
    }
}

@Composable
private fun VoiceBubble(message: Message) {
    val d = IqTheme.dimens
    val context = LocalContext.current
    val token = remember { SessionStore(context).token }
    val scope = rememberCoroutineScope()
    val playing = VoicePlayer.playingId == message.id

    val shape = BubbleShape(
        fromMe = message.fromMe,
        radius = d.scaled(20),
        group = message.group
    )
    val bg = if (message.fromMe) Crimson else FieldGrey
    val fg = if (message.fromMe) Color.White else Ink

    Box(
        Modifier
            .wrapContentWidth(if (message.fromMe) Alignment.End else Alignment.Start)
            .clip(shape)
            .background(bg)
            .padding(horizontal = d.gapMd, vertical = d.gapSm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val sending = message.uploadProgress >= 0f
            Box(
                Modifier
                    .size(d.scaled(40))
                    .clip(CircleShape)
                    .background(if (message.fromMe) Color.White else Ink)
                    // Nothing to play until it has been sent.
                    .clickable(enabled = !sending) {
                        scope.launch {
                            VoicePlayer.toggle(context.cacheDir, token, message.id)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                val mark = if (message.fromMe) Crimson else Color.White
                when {
                    // In the play button's own place, because that is the control
                    // being waited for: the ring fills, and when it is gone the
                    // button underneath is the thing you wanted.
                    sending -> VoiceUploadMark(message.uploadProgress, message.uploadFailed, mark)
                    else -> Icon(
                        if (playing) IqIcons.Close else IqIcons.PlayArrow,
                        if (playing) "Stop" else "Play",
                        Modifier.size(d.iconSm),
                        tint = mark
                    )
                }
            }
            Spacer(Modifier.width(d.gapMd))
            VoiceWave(
                bars = message.waveform,
                active = playing,
                color = if (message.fromMe) Color.White else Ink,
                modifier = Modifier
                    .width(d.scaled(132))
                    .height(d.scaled(28))
            )
            Spacer(Modifier.width(d.gapMd))
            Text(message.voiceLength, style = MaterialTheme.typography.bodyMedium, color = fg)
        }
    }
}

/**
 * The recorded shape, not a decorative one.
 *
 * Falls back to a flat row of stubs when a message arrives without a waveform, so
 * the bubble keeps its size either way.
 */
@Composable
private fun VoiceWave(
    bars: List<Int>,
    active: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    val d = IqTheme.dimens
    // An absent waveform is drawn as one quiet line rather than as a row of stubs.
    // Stubs look like a measurement - a very flat one - and this is the absence of
    // one; older messages, and anything recorded where the device reported no
    // amplitude, land here.
    val missing = bars.isEmpty() || bars.all { it <= 0 }
    val shown = remember(bars) { if (missing) emptyList() else peaksOf(bars, WAVE_BARS) }
    val barWidth = d.scaled(3)
    val gap = d.scaled(2)
    val tint = color.copy(
        alpha = when {
            missing -> 0.3f
            active -> 1f
            else -> 0.55f
        }
    )

    Canvas(modifier) {
        val w = barWidth.toPx()
        val slot = w + gap.toPx()
        val midY = size.height / 2f

        if (shown.isEmpty()) {
            // One quiet rule across the middle: there is no shape to draw.
            drawLine(
                color = tint,
                start = Offset(w / 2f, midY),
                end = Offset(size.width - w / 2f, midY),
                strokeWidth = w / 2f,
                cap = StrokeCap.Round
            )
            return@Canvas
        }

        // Never draw past the width given, however many bars arrived.
        val fits = ((size.width + gap.toPx()) / slot).toInt().coerceAtLeast(1)
        val count = minOf(shown.size, fits)
        for (i in 0 until count) {
            // A floor so a pause is still a bar rather than a dot: a round cap on a
            // line shorter than it is wide draws a circle, which is what made the
            // quiet stretches read as dots.
            val level = shown[i].coerceIn(MIN_BAR, 100)
            val half = (size.height * level / 100f / 2f).coerceAtLeast(w / 2f)
            val x = w / 2f + i * slot
            drawLine(
                color = tint,
                start = Offset(x, midY - half),
                end = Offset(x, midY + half),
                strokeWidth = w,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * How many bars fit the bubble: 26 at 3dp with 2dp between them is 128dp of the
 * 132dp the wave is given.
 */
private const val WAVE_BARS = 26

/**
 * The shortest a bar is drawn, as a percentage.
 *
 * Tall enough to still be a capsule at 3dp wide. Below that the round cap closes
 * into a circle and a quiet stretch turns into a row of dots.
 */
private const val MIN_BAR = 12

/**
 * Reduces a waveform to [count] bars, keeping the **loudest** of each group.
 *
 * Averaging is what a smaller version of a waveform usually gets, and it is wrong
 * here: the peaks are the syllables. Average them away and a sentence flattens into
 * the dotted line this was drawing. A message recorded before the bar count changed
 * still arrives with forty-eight, so this runs on the way in rather than being left
 * to the recorder.
 */
private fun peaksOf(bars: List<Int>, count: Int): List<Int> {
    if (bars.isEmpty()) return List(count) { 0 }
    if (bars.size <= count) return bars
    val step = bars.size.toFloat() / count
    return (0 until count).map { i ->
        val from = (i * step).toInt()
        val to = ((i + 1) * step).toInt().coerceAtMost(bars.size)
        if (from >= to) bars[from.coerceAtMost(bars.lastIndex)] else bars.subList(from, to).max()
    }
}

/** Static voice-note waveform; bar heights come from a smooth deterministic curve. */
@Composable
private fun Waveform(modifier: Modifier = Modifier, color: Color = Ink) {
    Canvas(modifier) {
        // Bar count follows the available width so bars keep a constant density.
        val bars = (size.width / 9f).toInt().coerceIn(12, 46)
        val gap = size.width / bars
        val barWidth = (gap * 0.34f).coerceAtLeast(1.5f)
        for (i in 0 until bars) {
            val t = i.toFloat()
            val amp = abs(sin(t * 0.7f) * 0.55f + sin(t * 0.23f) * 0.45f)
            val h = (size.height * (0.16f + amp * 0.84f)).coerceAtMost(size.height)
            val x = gap * i + gap / 2
            drawLine(
                color = color.copy(alpha = 0.85f),
                start = Offset(x, size.height / 2 - h / 2),
                end = Offset(x, size.height / 2 + h / 2),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

/* -- typing + composer ------------------------------------------------------ */

@Composable
private fun Composer(
    draft: String,
    onDraftChange: (String) -> Unit,
    onAttach: () -> Unit,
    onSend: () -> Unit,
    emojiOpen: Boolean = false,
    onToggleEmoji: () -> Unit = {},
    trayOpen: Boolean = false,
    recording: Boolean = false,
    recordingMs: Long = 0L,
    onHoldStart: () -> Unit = {},
    onHoldEnd: (Boolean) -> Unit = {},
    onFieldFocused: () -> Unit = {},
    onCamera: () -> Unit = {}
) {
    val d = IqTheme.dimens

    /**
     * The left-hand icons fold away while you are writing.
     *
     * A message bar is mostly the message. Once the field has focus the field is
     * what the width should go to, and two buttons for things you are not doing are
     * two buttons' worth of it spent badly.
     *
     * [expanded] is what stops that being a trap. Hiding them with no way back would
     * mean dismissing the keyboard to attach a photo, so the chevron brings them
     * straight back; it resets when focus goes, because the next time you come to
     * the bar you are starting again rather than carrying on.
     */
    var focused by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val collapsed = focused && !expanded

    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White)
    ) {
        HairlineDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = d.gapXs, vertical = d.gapSm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (collapsed) {
                CircleIconButton(
                    icon = IqIcons.ChevronRight,
                    contentDescription = "More",
                    onClick = { expanded = true },
                    iconSize = d.iconLg,
                    tint = Crimson
                )
            } else {
                // Also in the tray, but a shot you want to take now should not need
                // the roll opened first to reach it.
                CircleIconButton(
                    icon = IqIcons.Camera,
                    contentDescription = "Take a photo",
                    onClick = onCamera,
                    iconSize = d.iconLg,
                    tint = Ink
                )
                CircleIconButton(
                    icon = IqIcons.AttachFile,
                    contentDescription = if (trayOpen) "Close photos" else "Photos",
                    onClick = onAttach,
                    iconSize = d.iconLg,
                    tint = if (trayOpen) Crimson else Ink
                )
            }
            if (recording) {
                RecordingField(recordingMs)
                Spacer(Modifier.width(d.gapSm))
            } else
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = d.fieldHeight)
                    .clip(CircleShape)
                    .background(FieldGrey)
                    .padding(horizontal = d.gapLg, vertical = d.gapSm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (draft.isEmpty()) {
                        Text(
                            "Write a message...",
                            style = MaterialTheme.typography.messageBody,
                            color = InkFaint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = draft,
                        onValueChange = onDraftChange,
                        textStyle = MaterialTheme.typography.messageBody.copy(color = Ink),
                        cursorBrush = SolidColor(Crimson),
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { state ->
                                focused = state.isFocused
                                if (state.isFocused) onFieldFocused() else expanded = false
                            }
                    )
                }
                Spacer(Modifier.width(d.gapSm))
                Icon(
                    IqIcons.Emoji,
                    if (emojiOpen) "Close emoji" else "Emoji",
                    Modifier
                        .size(d.icon)
                        .clip(CircleShape)
                        .clickable(onClick = onToggleEmoji),
                    tint = if (emojiOpen) Crimson else InkMuted
                )
            }
            Spacer(Modifier.width(d.gapSm))
            val hasText = draft.isNotBlank()
            Box(
                Modifier
                    .size(d.fieldHeight)
                    .widthIn(min = 48.dp)
                    .clip(CircleShape)
                    .background(if (recording) Ink else Crimson)
                    .then(
                        if (hasText) {
                            Modifier.clickable(onClick = onSend)
                        } else {
                            // Hold to record, release to send, slide away to cancel -
                            // the gesture people already know from every messenger.
                            Modifier.pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        awaitFirstDown(requireUnconsumed = false)
                                        onHoldStart()
                                        var cancelled = false
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val pointer = event.changes.firstOrNull() ?: break
                                            if (pointer.position.x < -CANCEL_SLIDE_PX) {
                                                cancelled = true
                                            }
                                            if (!pointer.pressed) {
                                                pointer.consume()
                                                break
                                            }
                                        }
                                        onHoldEnd(!cancelled)
                                    }
                                }
                            }
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (hasText) IqIcons.Send else IqIcons.Mic,
                    contentDescription = if (hasText) "Send" else "Hold to record",
                    modifier = Modifier.size(d.icon),
                    tint = Color.White
                )
            }
        }
    }
}
