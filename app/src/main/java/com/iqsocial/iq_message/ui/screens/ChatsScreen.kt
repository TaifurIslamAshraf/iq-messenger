package com.iqsocial.iq_message.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.iqsocial.iq_message.data.Person
import com.iqsocial.iq_message.data.auth.SessionStore
import com.iqsocial.iq_message.data.chat.ChatRepository
import com.iqsocial.iq_message.data.profile.PresenceRepository
import com.iqsocial.iq_message.data.remote.ChatSocket
import com.iqsocial.iq_message.data.remote.ApiResult
import com.iqsocial.iq_message.data.remote.ChatConversation
import com.iqsocial.iq_message.data.remote.ConversationsApi
import com.iqsocial.iq_message.data.remote.LastMessage
import com.iqsocial.iq_message.data.remote.MessageHit
import com.iqsocial.iq_message.data.remote.MessagesApi
import com.iqsocial.iq_message.ui.components.Avatar
import com.iqsocial.iq_message.ui.components.CircleIconButton
import com.iqsocial.iq_message.ui.components.HairlineDivider
import com.iqsocial.iq_message.ui.components.UnreadBadge
import com.iqsocial.iq_message.ui.icons.IqIcons
import com.iqsocial.iq_message.ui.theme.Crimson
import com.iqsocial.iq_message.ui.theme.CrimsonTint
import com.iqsocial.iq_message.ui.theme.FieldGrey
import com.iqsocial.iq_message.ui.theme.Hairline
import com.iqsocial.iq_message.ui.theme.Ink
import com.iqsocial.iq_message.ui.theme.InkFaint
import com.iqsocial.iq_message.ui.theme.InkMuted
import com.iqsocial.iq_message.ui.theme.IqTheme
import com.iqsocial.iq_message.ui.theme.SystemBarIcons
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(
    onOpenChat: (String) -> Unit,
    onNewMessage: () -> Unit
) {
    SystemBarIcons(darkIcons = true)
    val d = IqTheme.dimens
    var query by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(false) }
    // The conversation whose action sheet is open, and the mute submenu.
    var acting by remember { mutableStateOf<ChatConversation?>(null) }
    var muting by remember { mutableStateOf<ChatConversation?>(null) }
    val scope = rememberCoroutineScope()
    val archived = ChatRepository.folder == ConversationsApi.Folder.ARCHIVED

    val context = LocalContext.current
    val session = remember { SessionStore(context) }
    val token = session.token

    // `loaded` is a key, not just a flag read elsewhere: that is what makes
    // `invalidate()` mean anything from here. A message arriving in the background
    // marks the list stale, this effect runs again, and `load` fetches - without
    // it the flag flipped and nothing ever asked.
    LaunchedEffect(token, refresh, ChatRepository.loaded) {
        // Stored first, server second. The list that was kept is on screen before
        // the request is made, so opening the app with no connection shows the
        // conversations rather than a spinner over nothing.
        ChatRepository.open(context, token)
        ChatRepository.refreshUnread(token)
    }

    // Search is the server's: the spec matches a word prefix of the peer's name,
    // which a filter over the loaded page could not reproduce.
    var searchHits by remember { mutableStateOf<List<ChatConversation>?>(null) }
    var messageHits by remember { mutableStateOf<List<MessageHit>>(emptyList()) }
    LaunchedEffect(query, token) {
        if (token.isNullOrBlank() || query.isBlank()) {
            searchHits = null
            messageHits = emptyList()
            return@LaunchedEffect
        }
        delay(300)                       // let typing settle before asking
        val q = query.trim()
        searchHits = when (val r = ConversationsApi.search(token!!, q)) {
            is ApiResult.Success -> r.data
            is ApiResult.Failure -> emptyList()
        }
        // Searching a messenger means searching what was said, not only who said
        // it, so message text is searched alongside the names.
        messageHits = when (val r = MessagesApi.search(token, q)) {
            is ApiResult.Success -> r.data.items
            is ApiResult.Failure -> emptyList()
        }
    }
    /**
     * Who in this list is online.
     *
     * Watched over the socket first and asked for as a snapshot second, and in
     * that order for the reason written at the same pair in the chat screen: the
     * subscription cannot block and the request can.
     *
     * Keyed on the list itself, so a page of conversations arriving - or a new one
     * appearing at the top - brings its people with it. The subscription replaces
     * rather than adds up, because a list that has scrolled a long way would
     * otherwise be watching every person it has ever shown.
     */
    val rowsForPresence = searchHits ?: ChatRepository.conversations
    val peerUids = remember(rowsForPresence) {
        rowsForPresence.mapNotNull { it.peer?.uid?.takeIf(String::isNotBlank) }.distinct()
    }
    LaunchedEffect(peerUids, token) {
        if (peerUids.isEmpty() || token.isNullOrBlank()) return@LaunchedEffect
        ChatSocket.watchPresence(peerUids)
        PresenceRepository.refresh(token, peerUids)
    }

    val rows = searchHits ?: ChatRepository.conversations

    val listState = rememberLazyListState()

    /**
     * The next page of conversations, as the end of the list comes into view.
     *
     * [ChatRepository.loadMore] and the cursor it walks have been here all along;
     * nothing called it. An account with more chats than one page simply had the
     * rest missing, with no sign that there was a rest - the list just ended.
     *
     * Carries the row count rather than a plain "near the end", for the reason the
     * thread does: a page that lands while somebody is sitting at the bottom has to
     * be able to ask for the one after it, and a boolean that was already true would
     * be dropped as unchanged.
     *
     * Not while searching. Those rows are the server's answer to a query, and paging
     * the inbox underneath them would append conversations nobody asked about.
     */
    LaunchedEffect(token, searchHits == null) {
        if (searchHits != null) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            if (last >= info.totalItemsCount - MORE_WHEN_WITHIN) rows.size else -1
        }
            .distinctUntilChanged()
            .filter { it >= 0 }
            .collect { if (ChatRepository.canLoadMore) ChatRepository.loadMore(token) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Dragging the list down asks the server again. It was already possible -
        // behind "Refresh" in the overflow menu - which is not where anyone looks
        // for it: a list of messages is the screen people reach for by pulling the
        // moment they suspect it is stale.
        PullToRefreshBox(
            isRefreshing = ChatRepository.loading,
            onRefresh = { refresh = !refresh },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = d.fab + d.gapXl)
            ) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(start = d.gutter, end = d.gapXs, top = d.gapSm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(d.iconButton)
                            .clip(CircleShape)
                            .border(1.5.dp, Hairline, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(IqIcons.PersonOutline, "Your profile", Modifier.size(d.icon), tint = Ink)
                    }
                    Spacer(Modifier.width(d.gapMd))
                    Text(
                        if (archived) "Archived" else "Messages",
                        style = MaterialTheme.typography.headlineLarge,
                        color = Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    CircleIconButton(
                        icon = IqIcons.CameraOutline,
                        contentDescription = "Camera",
                        onClick = {},
                        iconSize = d.iconLg
                    )
                    Box {
                        CircleIconButton(
                            icon = IqIcons.MoreVert,
                            contentDescription = "More options",
                            onClick = { menuOpen = true }
                        )
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(if (archived) "Back to inbox" else "Archived") },
                                onClick = {
                                    menuOpen = false
                                    scope.launch {
                                        ChatRepository.showFolder(
                                            token,
                                            if (archived) ConversationsApi.Folder.INBOX
                                            else ConversationsApi.Folder.ARCHIVED
                                        )
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Refresh") },
                                onClick = {
                                    menuOpen = false
                                    refresh = !refresh
                                }
                            )
                        }
                    }
                }
            }

            item {
                SearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search conversations",
                    modifier = Modifier.padding(horizontal = d.gutter, vertical = d.gapMd)
                )
            }

            val searching = query.isNotBlank()
            if (searching && rows.isNotEmpty()) {
                item { SearchHeading("Chats") }
            }
            if (rows.isEmpty() && messageHits.isEmpty() && ChatRepository.loaded) {
                item { EmptyChats(searching = searching) }
            }
            items(rows, key = { it.id }) { conversation ->
                ConversationRow(
                    conversation = conversation,
                    onClick = { onOpenChat(conversation.id) },
                    onLongPress = { acting = conversation }
                )
                HairlineDivider(
                    Modifier.padding(start = d.avatarLg + d.gutter + d.gapMd, end = d.gutter)
                )
            }

            if (searching && messageHits.isNotEmpty()) {
                item { SearchHeading("Messages") }
                items(messageHits, key = { it.message?.id ?: it.hashCode().toString() }) { hit ->
                    MessageHitRow(hit, query.trim()) {
                        hit.conversation?.id?.let(onOpenChat)
                    }
                    HairlineDivider(
                        Modifier.padding(start = d.avatarLg + d.gutter + d.gapMd, end = d.gutter)
                    )
                }
            }
        }
        }

        acting?.let { conversation ->
            ConversationActions(
                conversation = conversation,
                onDismiss = { acting = null },
                onPin = {
                    acting = null
                    scope.launch { ChatRepository.setPinned(token, conversation.id, !conversation.pinned) }
                },
                onMute = {
                    acting = null
                    if (conversation.muted) {
                        scope.launch { ChatRepository.setMuted(token, conversation.id, null) }
                    } else {
                        muting = conversation
                    }
                },
                onArchive = {
                    acting = null
                    scope.launch { ChatRepository.setArchived(token, conversation.id, !conversation.archived) }
                },
                onToggleUnread = {
                    acting = null
                    scope.launch { ChatRepository.setUnread(token, conversation.id, !conversation.showsUnread) }
                },
                onDelete = {
                    acting = null
                    scope.launch { ChatRepository.delete(token, conversation.id) }
                }
            )
        }

        muting?.let { conversation ->
            MuteDurations(
                onDismiss = { muting = null },
                onPick = { duration ->
                    muting = null
                    scope.launch { ChatRepository.setMuted(token, conversation.id, duration) }
                }
            )
        }

        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = d.gutter, bottom = d.gapLg)
                .size(d.fab)
                .clip(CircleShape)
                .background(Crimson)
                .clickable(onClick = onNewMessage),
            contentAlignment = Alignment.Center
        ) {
            Icon(IqIcons.ChatCompose, "New message", Modifier.size(d.iconLg), tint = Color.White)
        }
    }
}

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val d = IqTheme.dimens
    Row(
        modifier
            .fillMaxWidth()
            .height(d.fieldHeight)
            .clip(RoundedCornerShape(d.radiusMd))
            .background(FieldGrey)
            .padding(horizontal = d.gapLg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(IqIcons.Search, null, Modifier.size(d.icon), tint = InkMuted)
        Spacer(Modifier.width(d.gapMd))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = InkFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
                cursorBrush = SolidColor(Crimson),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * A conversation row driven by the API model.
 *
 * Kept apart from the sample [ConversationRow] rather than made generic: this one
 * has to say things the sample model has no room for - pinned, muted, a deleted
 * last message, and a preview for the seven non-text message types.
 */
/**
 * Long-press actions on a conversation.
 *
 * Every label reads the conversation's current state rather than naming a fixed
 * action, so the sheet says "Unpin" on a pinned chat instead of offering to pin it
 * again. Muting opens a second step because the server wants a duration, not a
 * boolean.
 */
@Composable
private fun ConversationActions(
    conversation: ChatConversation,
    onDismiss: () -> Unit,
    onPin: () -> Unit,
    onMute: () -> Unit,
    onArchive: () -> Unit,
    onToggleUnread: () -> Unit,
    onDelete: () -> Unit
) {
    val d = IqTheme.dimens
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(d.radiusXl))
                .background(Color.White)
                .padding(vertical = d.gapMd)
        ) {
            Text(
                conversation.peer?.fullname?.ifBlank { "Unknown" } ?: "Unknown",
                style = MaterialTheme.typography.titleLarge,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = d.gutter, vertical = d.gapSm)
            )
            HairlineDivider()
            SheetRow(
                if (conversation.pinned) IqIcons.Close else IqIcons.Pin,
                if (conversation.pinned) "Unpin" else "Pin to top",
                onPin
            )
            SheetRow(
                if (conversation.muted) IqIcons.Bell else IqIcons.BellOff,
                if (conversation.muted) "Unmute" else "Mute notifications",
                onMute
            )
            SheetRow(
                IqIcons.Storage,
                if (conversation.archived) "Move to inbox" else "Archive",
                onArchive
            )
            SheetRow(
                IqIcons.Check,
                if (conversation.showsUnread) "Mark as read" else "Mark as unread",
                onToggleUnread
            )
            SheetRow(IqIcons.Close, "Delete chat", onDelete)
        }
    }
}

/** The three windows the server accepts, and nothing else. */
@Composable
private fun MuteDurations(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val d = IqTheme.dimens
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(d.radiusXl))
                .background(Color.White)
                .padding(vertical = d.gapMd)
        ) {
            Text(
                "Mute for",
                style = MaterialTheme.typography.titleLarge,
                color = Ink,
                modifier = Modifier.padding(horizontal = d.gutter, vertical = d.gapSm)
            )
            HairlineDivider()
            SheetRow(IqIcons.Clock, "8 hours") { onPick("8h") }
            SheetRow(IqIcons.Clock, "1 week") { onPick("1w") }
            SheetRow(IqIcons.BellOff, "Until I turn it back on") { onPick("always") }
        }
    }
}

@Composable
private fun SheetRow(icon: ImageVector, label: String, onClick: () -> Unit) {
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

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ConversationRow(
    conversation: ChatConversation,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {}
) {
    val d = IqTheme.dimens
    val peer = conversation.peer
    // Read here rather than passed in, so the dot lights up by itself when the
    // answer arrives: the repository is a snapshot map and this row is reading it.
    val presence = PresenceRepository.of(peer?.uid.orEmpty())
    val person = remember(peer, presence?.online) {
        Person(
            id = peer?.uid ?: conversation.id,
            name = peer?.fullname?.ifBlank { "Unknown" } ?: "Unknown",
            handle = "",
            online = presence?.online == true,
            photo = peer?.profilePicture.orEmpty()
        )
    }

    /**
     * Whether this row still has something in it nobody has read.
     *
     * The server's own reading of it, counted or marked by hand - see
     * [ChatConversation.showsUnread] - so a conversation marked unread on another
     * device reads as unread here too.
     */
    val unseen = conversation.showsUnread

    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .heightIn(min = 64.dp)
            .padding(horizontal = d.gutter, vertical = d.gapMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The dot only ever appears for somebody the server has said is online.
        // Nothing is inferred: an account that hides its active status and one
        // that is simply offline come back identical, and inventing a difference
        // between them is the one thing that setting exists to prevent.
        Avatar(person = person, size = d.avatarLg, showPresence = true)
        Spacer(Modifier.width(d.gapMd))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    person.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = Ink,
                    // Heavier while something is waiting. The badge says how many,
                    // and the weight is what makes the row findable without
                    // reading it - which is how a list of twenty conversations is
                    // actually used.
                    fontWeight = if (unseen) FontWeight.Bold else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (peer?.isVerified == true) {
                    Spacer(Modifier.width(d.gapXs))
                    Icon(IqIcons.Verified, "Verified", Modifier.size(d.iconSm), tint = Crimson)
                }
            }
            Spacer(Modifier.height(d.gapXs / 2))
            Text(
                conversation.lastMessage.preview(),
                style = MaterialTheme.typography.bodyLarge,
                // Darker as well as heavier: grey is what the line is while it is
                // read, and a bold grey line still reads as spent.
                color = if (unseen) Ink else InkMuted,
                fontWeight = if (unseen) FontWeight.SemiBold else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(d.gapSm))
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(d.gapSm)
        ) {
            Text(
                chatTimestamp(conversation.lastMessageAt),
                style = MaterialTheme.typography.bodySmall,
                // The time takes the accent rather than the weight: it is the one
                // part of the row that says *when* the waiting message arrived.
                color = if (unseen) Crimson else InkMuted,
                fontWeight = if (unseen) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (conversation.pinned) {
                    Icon(IqIcons.Pin, "Pinned", Modifier.size(d.iconSm), tint = InkMuted)
                    Spacer(Modifier.width(d.gapXs))
                }
                if (conversation.muted) {
                    Icon(IqIcons.BellOff, "Muted", Modifier.size(d.iconSm), tint = InkMuted)
                    Spacer(Modifier.width(d.gapXs))
                }
                when {
                    conversation.unreadCount > 0 -> UnreadBadge(conversation.unreadCount)
                    // A conversation can be unread with no count behind it.
                    conversation.markedUnread -> UnreadDot()
                    else -> Spacer(Modifier.height(d.scaled(23)))
                }
            }
        }
    }
}

/** A filled dot for "marked unread", which carries no number. */
@Composable
private fun UnreadDot() {
    val d = IqTheme.dimens
    Box(
        Modifier
            .size(d.scaled(12))
            .clip(CircleShape)
            .background(Crimson)
    )
}

/** Section label separating chats from message hits while searching. */
@Composable
private fun SearchHeading(text: String) {
    val d = IqTheme.dimens
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = InkMuted,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = d.gutter, top = d.gapMd, bottom = d.gapXs)
    )
}

/**
 * A message that matched, under the conversation it lives in.
 *
 * The matching run is emphasised in place, so a hit reads as the sentence it came
 * from rather than a bare line of text.
 */
@Composable
private fun MessageHitRow(hit: MessageHit, query: String, onClick: () -> Unit) {
    val d = IqTheme.dimens
    val peer = hit.conversation?.peer
    val name = peer?.fullname?.ifBlank { "Unknown" } ?: "Unknown"
    val person = remember(peer) {
        Person(
            id = peer?.uid ?: name,
            name = name,
            handle = "",
            photo = peer?.profilePicture.orEmpty()
        )
    }
    val text = hit.message?.text.orEmpty()
    val highlighted = remember(text, query) {
        val at = text.indexOf(query, ignoreCase = true)
        buildAnnotatedString {
            if (at < 0) {
                append(text)
            } else {
                append(text.substring(0, at))
                withStyle(SpanStyle(color = Crimson, fontWeight = FontWeight.SemiBold)) {
                    append(text.substring(at, at + query.length))
                }
                append(text.substring(at + query.length))
            }
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = d.gutter, vertical = d.gapMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(person = person, size = d.avatarLg)
        Spacer(Modifier.width(d.gapMd))
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.titleLarge,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(d.gapXs / 2))
            Text(
                highlighted,
                style = MaterialTheme.typography.bodyLarge,
                color = InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(d.gapSm))
        Text(
            chatTimestamp(hit.message?.createdAt ?: 0L),
            style = MaterialTheme.typography.bodySmall,
            color = InkMuted,
            maxLines = 1
        )
    }
}

@Composable
private fun EmptyChats(searching: Boolean) {
    val d = IqTheme.dimens
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = d.gutter, vertical = d.gapXxl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(d.avatarXl)
                .clip(CircleShape)
                .background(CrimsonTint),
            contentAlignment = Alignment.Center
        ) {
            Icon(IqIcons.ChatCompose, null, Modifier.size(d.iconXl), tint = Crimson)
        }
        Spacer(Modifier.height(d.gapLg))
        Text(
            if (searching) "No conversations found" else "No conversations yet",
            style = MaterialTheme.typography.titleLarge,
            color = Ink
        )
        Spacer(Modifier.height(d.gapSm))
        Text(
            if (searching) {
                "No one you have chatted with matches that name."
            } else {
                "Start one from People, and it will appear here."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = InkMuted,
            textAlign = TextAlign.Center
        )
    }
}

/** What the list shows for a message that is not plain text. */
private fun LastMessage?.preview(): String = when {
    this == null -> ""
    isDeleted -> "This message was deleted"
    text.isNotBlank() -> text
    else -> when (type) {
        "image" -> "Photo"
        "video" -> "Video"
        "voice" -> "Voice message"
        "audio" -> "Audio"
        "voice_call" -> "Voice call"
        "video_call" -> "Video call"
        "document" -> "Document"
        "location" -> "Location"
        "contact" -> "Contact"
        else -> ""
    }
}

/**
 * Chat-list timestamps: the time today, "Yesterday", the weekday within a week,
 * then a date. Built on [Calendar] because `java.time` needs API 26 and this app
 * ships to 24 without desugaring.
 */
internal fun chatTimestamp(millis: Long): String {
    if (millis <= 0L) return ""
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = millis }

    fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    val weekAgo = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -6) }

    val pattern = when {
        sameDay(now, then) -> "h:mm a"
        sameDay(yesterday, then) -> return "Yesterday"
        then.after(weekAgo) -> "EEE"
        else -> "dd/MM/yy"
    }
    return java.text.SimpleDateFormat(pattern, Locale.US).format(then.time)
}

/**
 * How many rows short of the end the next page is asked for.
 *
 * Conversation rows are tall, so a handful is already most of a screen - far enough
 * ahead that the page is usually there before the list runs out.
 */
private const val MORE_WHEN_WITHIN = 4
