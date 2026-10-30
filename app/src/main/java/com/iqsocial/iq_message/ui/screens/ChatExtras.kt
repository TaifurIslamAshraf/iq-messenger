package com.iqsocial.iq_message.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.iqsocial.iq_message.data.Person
import com.iqsocial.iq_message.data.chat.ChatRepository
import com.iqsocial.iq_message.data.remote.ApiResult
import com.iqsocial.iq_message.data.remote.ChatMessage
import com.iqsocial.iq_message.data.remote.ChatPerson
import com.iqsocial.iq_message.data.remote.MessageInfo
import com.iqsocial.iq_message.data.remote.MessagesApi
import com.iqsocial.iq_message.data.remote.Reaction
import com.iqsocial.iq_message.ui.components.Avatar
import com.iqsocial.iq_message.ui.components.HairlineDivider
import com.iqsocial.iq_message.ui.components.SheetHandle
import com.iqsocial.iq_message.ui.icons.IqIcons
import com.iqsocial.iq_message.ui.theme.Crimson
import com.iqsocial.iq_message.ui.theme.CrimsonTint
import com.iqsocial.iq_message.ui.theme.FieldGrey
import com.iqsocial.iq_message.ui.theme.Ink
import com.iqsocial.iq_message.ui.theme.InkMuted
import com.iqsocial.iq_message.ui.theme.IqTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
/**
 * Picks the conversations to forward into.
 *
 * The server takes at most five, so the list stops offering more rather than
 * letting someone select six and meet a rejection.
 */
@Composable
fun ForwardPicker(
    token: String?,
    exclude: String,
    onDismiss: () -> Unit,
    onSend: (List<String>) -> Unit
) {
    val d = IqTheme.dimens
    var picked by remember { mutableStateOf(setOf<String>()) }
    val options = ChatRepository.conversations.filter { it.id != exclude }
    val atLimit = picked.size >= MAX_FORWARD

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(d.radiusXl))
                .background(Color.White)
                .padding(vertical = d.gapMd)
        ) {
            Text(
                "Forward to",
                style = MaterialTheme.typography.titleLarge,
                color = Ink,
                modifier = Modifier.padding(horizontal = d.gutter, vertical = d.gapSm)
            )
            Text(
                "Up to $MAX_FORWARD chats",
                style = MaterialTheme.typography.bodyMedium,
                color = InkMuted,
                modifier = Modifier.padding(horizontal = d.gutter)
            )
            Spacer(Modifier.height(d.gapSm))
            HairlineDivider()

            if (options.isEmpty()) {
                Text(
                    "No other chats to forward to.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted,
                    modifier = Modifier.padding(d.gutter)
                )
            }

            LazyColumn(Modifier.heightIn(max = d.screenHeight * 0.45f)) {
                items(options, key = { it.id }) { conversation ->
                    val name = conversation.peer?.fullname?.ifBlank { "Unknown" } ?: "Unknown"
                    val chosen = conversation.id in picked
                    Row(
                        Modifier
                            .fillMaxWidth()
                            // A sixth pick would be refused, so it is not offered.
                            .clickable(enabled = chosen || !atLimit) {
                                picked = if (chosen) picked - conversation.id
                                else picked + conversation.id
                            }
                            .padding(horizontal = d.gutter, vertical = d.gapMd),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(
                            person = Person(
                                id = conversation.id,
                                name = name,
                                handle = "",
                                photo = conversation.peer?.profilePicture.orEmpty()
                            ),
                            size = d.avatarMd
                        )
                        Spacer(Modifier.width(d.gapMd))
                        Text(
                            name,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (chosen || !atLimit) Ink else InkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (chosen) {
                            Icon(IqIcons.Check, null, Modifier.size(d.icon), tint = Crimson)
                        }
                    }
                }
            }

            HairlineDivider()
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = d.gutter, vertical = d.gapSm),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Cancel",
                    style = MaterialTheme.typography.titleMedium,
                    color = InkMuted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(d.radiusSm))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = d.gapMd, vertical = d.gapSm)
                )
                Text(
                    if (picked.isEmpty()) "Send" else "Send (${picked.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (picked.isEmpty()) InkMuted else Crimson,
                    modifier = Modifier
                        .clip(RoundedCornerShape(d.radiusSm))
                        .clickable(enabled = picked.isNotEmpty()) { onSend(picked.toList()) }
                        .padding(horizontal = d.gapMd, vertical = d.gapSm)
                )
            }
        }
    }
}

private const val MAX_FORWARD = 5

/** Delivery and read times per member, from `/v1/messages/{id}/info`. */
@Composable
fun MessageInfoSheet(detail: MessageInfo, onDismiss: () -> Unit) {
    val d = IqTheme.dimens
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(d.radiusXl))
                .background(Color.White)
                .padding(d.gutter)
        ) {
            Text("Message info", style = MaterialTheme.typography.titleLarge, color = Ink)
            Spacer(Modifier.height(d.gapSm))
            detail.message?.text?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(d.gapMd))
            }

            if (detail.recipients.isEmpty()) {
                Text(
                    "No one has received this yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted
                )
            }

            detail.recipients.forEach { r ->
                Column(Modifier.padding(vertical = d.gapSm)) {
                    InfoLine("Delivered", r.deliveredAt)
                    Spacer(Modifier.height(d.gapXs))
                    InfoLine("Read", r.readAt)
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, at: Long) {
    val d = IqTheme.dimens
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            IqIcons.DoneAll,
            null,
            Modifier.size(d.iconSm),
            // Grey until it has actually happened - the tick should not imply more
            // than the server reported.
            tint = if (at > 0) Crimson else InkMuted
        )
        Spacer(Modifier.width(d.gapSm))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Ink)
        Spacer(Modifier.weight(1f))
        Text(
            if (at > 0) fullStamp(at) else "-",
            style = MaterialTheme.typography.bodyMedium,
            color = InkMuted
        )
    }
}

private fun fullStamp(millis: Long): String =
    SimpleDateFormat("d MMM, h:mm a", Locale.US).format(Date(millis))

/** Searches inside one conversation, newest first. */
@Composable
fun InConversationSearch(token: String?, conversationId: String, onDismiss: () -> Unit) {
    val d = IqTheme.dimens
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }

    LaunchedEffect(query, token) {
        if (token.isNullOrBlank() || query.isBlank()) {
            hits = emptyList()
            searched = false
            return@LaunchedEffect
        }
        delay(300)
        hits = when (val r = MessagesApi.searchIn(token, conversationId, query.trim())) {
            is ApiResult.Success -> r.data.items
            is ApiResult.Failure -> emptyList()
        }
        searched = true
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(d.radiusXl))
                .background(Color.White)
                .padding(vertical = d.gapMd)
        ) {
            SearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search in this chat",
                modifier = Modifier.padding(horizontal = d.gutter, vertical = d.gapSm)
            )
            HairlineDivider()
            if (searched && hits.isEmpty()) {
                Text(
                    "Nothing found.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted,
                    modifier = Modifier.padding(d.gutter)
                )
            }
            LazyColumn(Modifier.heightIn(max = d.screenHeight * 0.45f)) {
                items(hits, key = { it.id }) { m ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = d.gutter, vertical = d.gapMd)
                    ) {
                        Text(
                            m.text.ifBlank { m.type },
                            style = MaterialTheme.typography.bodyLarge,
                            color = Ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            fullStamp(m.createdAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = InkMuted
                        )
                    }
                    HairlineDivider()
                }
            }
        }
    }
}

/**
 * Media, documents and links shared in a conversation.
 *
 * A bottom sheet, because it is a list that can be long and is read by scrolling -
 * a dialog floats a fixed box in the middle of the screen and gives the list a
 * fraction of it. The sheet comes up from the edge the thumb is already at and can
 * be dragged to full height.
 *
 * Three things here were quietly broken, and all three looked the same from the
 * outside - a sheet that did nothing:
 *
 * - **A failed request read as an empty conversation.** The error was mapped to an
 *   empty list, so a server that was refusing and a chat with nothing in it were
 *   drawn identically, as "Nothing shared yet".
 * - **Loading read as empty too**, because nothing was drawn between opening the
 *   sheet and the reply arriving.
 * - **The rows did nothing when tapped.** A list of things you cannot open is a
 *   list that does not work, whatever it says on it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedItems(
    token: String?,
    conversationId: String,
    onOpen: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val d = IqTheme.dimens
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var kind by remember(conversationId) { mutableStateOf(MessagesApi.Shared.MEDIA) }
    var items by remember(conversationId) { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var loading by remember(conversationId) { mutableStateOf(true) }
    var failure by remember(conversationId) { mutableStateOf<String?>(null) }
    // Bumped by "Try again". A counter rather than re-setting `kind`, because two
    // writes to the same state in one frame coalesce to the last one - setting it
    // away and back lands on the value it started at, and the effect never re-runs.
    var attempt by remember(conversationId) { mutableIntStateOf(0) }

    // Keyed on the conversation as well: the sheet is remembered per chat, and
    // without it a sheet reopened in another chat would show the first one's list.
    LaunchedEffect(kind, token, conversationId, attempt) {
        loading = true
        failure = null
        if (token.isNullOrBlank()) {
            failure = "Not signed in"
            loading = false
            return@LaunchedEffect
        }
        when (val r = MessagesApi.shared(token, conversationId, kind)) {
            is ApiResult.Success -> items = r.data.items
            // Said, not swallowed. An empty list here is the server's answer to a
            // question it refused to answer, and drawing it as "nothing shared"
            // blames the conversation for the network.
            is ApiResult.Failure -> {
                items = emptyList()
                failure = r.message
            }
        }
        loading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        dragHandle = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = d.gapMd),
                contentAlignment = Alignment.Center
            ) { SheetHandle() }
        }
    ) {
        Column(Modifier.fillMaxHeight(0.9f)) {
            Text(
                "Shared",
                style = MaterialTheme.typography.headlineMedium,
                color = Ink,
                modifier = Modifier.padding(horizontal = d.gutter, vertical = d.gapMd)
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = d.gutter)
                    .padding(bottom = d.gapSm),
                horizontalArrangement = Arrangement.spacedBy(d.gapSm)
            ) {
                MessagesApi.Shared.entries.forEach { option ->
                    val on = option == kind
                    Text(
                        option.value.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleSmall,
                        color = if (on) Crimson else InkMuted,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier
                            .clip(RoundedCornerShape(d.radiusSm))
                            .background(if (on) CrimsonTint else Color.Transparent)
                            .clickable { kind = option }
                            .padding(horizontal = d.gapMd, vertical = d.gapXs)
                    )
                }
            }
            HairlineDivider()

            when {
                loading -> Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(d.gutter),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(d.icon),
                        color = Crimson,
                        strokeWidth = d.scaled(2)
                    )
                }

                failure != null -> Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(d.gutter),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        failure.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Crimson,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(d.gapSm))
                    Text(
                        "Try again",
                        style = MaterialTheme.typography.titleSmall,
                        color = Crimson,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(d.radiusSm))
                            .clickable { attempt++ }
                            .padding(horizontal = d.gapMd, vertical = d.gapXs)
                    )
                }

                items.isEmpty() -> Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(d.gutter),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Nothing shared yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkMuted,
                        textAlign = TextAlign.Center
                    )
                }

                // Photos and videos are shown, not listed.
                //
                // A row each gave every picture a thumbnail the size of an avatar
                // and two lines of text beside it - a file name nobody chose and a
                // date - so finding a photo meant reading about photos. A grid puts
                // nine on screen where the list put four, and the thing being looked
                // for is the thing being drawn.
                //
                // Documents and links keep their rows: there is nothing to show for
                // them, and a grid of identical paper icons would be worse than the
                // names.
                kind == MessagesApi.Shared.MEDIA -> LazyVerticalGrid(
                    columns = GridCells.Fixed(MEDIA_COLUMNS),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(d.gapXs),
                    horizontalArrangement = Arrangement.spacedBy(d.gapXs),
                    verticalArrangement = Arrangement.spacedBy(d.gapXs)
                ) {
                    items(items, key = { it.id }) { m ->
                        val isVideo = (m.media?.kind ?: m.type) == "video"
                        Box(
                            Modifier
                                // Square cells, whatever shape the picture is. A grid
                                // of varying heights is a grid with holes in it, and
                                // the preview is for recognising the photo rather
                                // than judging its framing.
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(d.radiusSm))
                                .background(CrimsonTint)
                                .clickable {
                                    onDismiss()
                                    onOpen(m.id)
                                }
                        ) {
                            MediaThumb(
                                m.id,
                                size = GRID_CELL,
                                video = isVideo,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (isVideo) {
                                // A still frame from a video looks exactly like a
                                // photo, so the mark is the only thing that says
                                // which it is - and the length says how much of one.
                                Box(
                                    Modifier
                                        .align(Alignment.Center)
                                        .size(d.scaled(28))
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.45f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        IqIcons.PlayArrow,
                                        "Video",
                                        Modifier.size(d.iconSm),
                                        tint = Color.White
                                    )
                                }
                                clockOf(m)?.let { length ->
                                    Text(
                                        length,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White,
                                        maxLines = 1,
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
                    }
                }

                else -> LazyColumn(Modifier.fillMaxWidth()) {
                    items(items, key = { it.id }) { m ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                // Tapping goes to the message where it was sent,
                                // which is the thing a list of shared items is for:
                                // finding what somebody sent and the talk around it.
                                .clickable {
                                    onDismiss()
                                    onOpen(m.id)
                                }
                                .heightIn(min = 56.dp)
                                .padding(horizontal = d.gutter, vertical = d.gapSm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // A photo shows itself. The mark is for the kinds that
                            // have no picture to show - a voice note, a document -
                            // where it says what the thing is; on an image it only
                            // said that an image was there, which the row already
                            // said in words.
                            val shape = m.media?.kind
                            if (shape == "image" || shape == "video") {
                                MediaThumb(m.id, d.avatarMd, video = shape == "video")
                            } else {
                                Box(
                                    Modifier
                                        .size(d.avatarMd)
                                        .clip(CircleShape)
                                        .background(CrimsonTint),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        sharedIcon(m),
                                        null,
                                        Modifier.size(d.iconSm),
                                        tint = Crimson
                                    )
                                }
                            }
                            Spacer(Modifier.width(d.gapMd))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    sharedLabel(m),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    fullStamp(m.createdAt),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InkMuted
                                )
                            }
                        }
                        HairlineDivider()
                    }
                }
            }
        }
    }
}

/** A video's length, when the server sent one. */
private fun clockOf(m: ChatMessage): String? {
    val ms = m.media?.durationMs ?: 0L
    if (ms <= 0L) return null
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}

/** Three across: big enough to recognise a face, small enough to scan a month of them. */
private const val MEDIA_COLUMNS = 3

/** What to fetch for a cell. Generous, because a cell is a third of the screen. */
private val GRID_CELL = 160.dp

private fun sharedIcon(m: ChatMessage) = when (m.media?.kind ?: m.type) {
    "image" -> IqIcons.Gallery
    "video" -> IqIcons.Videocam
    "voice", "audio" -> IqIcons.Waveform
    "document" -> IqIcons.DocumentOutline
    else -> IqIcons.AttachFile
}

private fun sharedLabel(m: ChatMessage): String =
    m.media?.fileName?.takeIf { it.isNotBlank() }
        ?: m.text.takeIf { it.isNotBlank() }
        ?: (m.media?.kind ?: m.type).replaceFirstChar { it.uppercase() }

/**
 * Who reacted, and the way to take yours back.
 *
 * A reaction chip is a few millimetres wide and already carries the emoji and the
 * count, so tapping it cannot usefully mean "react" - the tray on long press does
 * that. What it is missing is *who*, which is the only question a chip raises and
 * cannot answer. So the tap opens this.
 *
 * Removing is the same gesture as reading: your own row says "Tap to remove" and
 * does it. There is no separate button, because a list with one actionable row does
 * not need a second control explaining which row that is.
 */
@Composable
fun ReactionSheet(
    reactions: List<Reaction>,
    myUid: String,
    peer: Person,
    me: Person,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    val d = IqTheme.dimens
    // Tabs are built from what is actually there rather than from the six offered,
    // so an emoji nobody used never appears.
    val byEmoji = remember(reactions) {
        reactions.groupBy { it.emoji }.toList().sortedByDescending { it.second.size }
    }
    var filter by remember(reactions) { mutableStateOf<String?>(null) }
    val shown = remember(reactions, filter) {
        if (filter == null) reactions else reactions.filter { it.emoji == filter }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(Modifier.fillMaxWidth()) {
            // The thread stays visible above, and a tap there closes. No ripple:
            // this is a dismiss target, not a button.
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = d.radiusXl, topEnd = d.radiusXl))
                    .background(Color.White)
                    .padding(vertical = d.gapMd)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = d.gutter),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Reactions",
                        style = MaterialTheme.typography.titleLarge,
                        color = Ink,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier
                            .size(d.iconButtonSm)
                            .clip(CircleShape)
                            .background(FieldGrey)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(IqIcons.Close, "Close", Modifier.size(d.iconSm), tint = InkMuted)
                    }
                }

                Spacer(Modifier.height(d.gapSm))

                LazyColumn(Modifier.heightIn(max = d.screenHeight * 0.4f)) {
                    items(shown, key = { it.uid + it.emoji }) { reaction ->
                        val mine = reaction.uid == myUid
                        ReactorRow(
                            person = if (mine) me else peer,
                            emoji = reaction.emoji,
                            mine = mine,
                            onClick = { if (mine) onRemove() }
                        )
                    }
                }

                // Only worth showing once there is a choice to make.
                if (byEmoji.size > 1) {
                    Spacer(Modifier.height(d.gapSm))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = d.gutter),
                        horizontalArrangement = Arrangement.spacedBy(d.gapSm)
                    ) {
                        FilterPill("ALL", filter == null) { filter = null }
                        byEmoji.forEach { (emoji, list) ->
                            FilterPill(
                                "$emoji  ${list.size}",
                                filter == emoji
                            ) { filter = emoji }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReactorRow(person: Person, emoji: String, mine: Boolean, onClick: () -> Unit) {
    val d = IqTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = mine, onClick = onClick)
            .padding(horizontal = d.gutter, vertical = d.gapSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(person = person, size = d.avatarMd, showPresence = true)
        Spacer(Modifier.width(d.gapMd))
        Column(Modifier.weight(1f)) {
            Text(
                person.name,
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // Said only on your own row, because it is the only one that can.
            if (mine) {
                Text(
                    "Tap to remove",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted
                )
            }
        }
        Spacer(Modifier.width(d.gapSm))
        Text(emoji, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val d = IqTheme.dimens
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) Crimson else InkMuted,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) CrimsonTint else FieldGrey)
            .clickable(onClick = onClick)
            .padding(horizontal = d.gapMd, vertical = d.gapXs)
    )
}


/**
 * The other person, from the chat they are in.
 *
 * Opened by tapping their face or their name in the bar, which is where everyone
 * looks for it and where nothing happened before.
 *
 * **What it can show is what the conversation knows.** The messenger API has no
 * endpoint for somebody else's profile - `/v1/me` is the caller's own, and a people
 * search gives back the same five fields the conversation already carries. So this
 * is a name, a face, a tick and what can be done about the chat, not a profile page
 * with a bio on it. Better that than an empty page waiting on a route that does not
 * exist.
 *
 * The actions are the ones already in the bar's overflow menu, brought to where they
 * are looked for: in Messenger they live behind the person, not behind a menu.
 */
@Composable
fun PeerProfileSheet(
    person: Person,
    peer: ChatPerson?,
    conversationId: String,
    muted: Boolean,
    token: String?,
    onAudioCall: () -> Unit,
    onVideoCall: () -> Unit,
    onSearch: () -> Unit,
    onShared: () -> Unit,
    onDismiss: () -> Unit
) {
    val d = IqTheme.dimens
    val scope = rememberCoroutineScope()
    // Held here and not read back from the conversation: the mute call returns
    // nothing to re-read, and waiting for the list to be fetched again would leave
    // the switch showing the old answer for as long as that took.
    var quiet by remember(conversationId) { mutableStateOf(muted) }
    var busy by remember(conversationId) { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(d.radiusXl))
                .background(Color.White)
                .padding(vertical = d.gapLg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Avatar(person = person, size = d.avatarXl)

            Spacer(Modifier.height(d.gapMd))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    person.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = d.gapSm)
                )
                if (peer?.isVerified == true) {
                    Icon(
                        IqIcons.Verified,
                        contentDescription = "Verified",
                        modifier = Modifier.size(d.iconSm),
                        tint = Crimson
                    )
                }
            }

            peer?.badgeKey?.takeIf { it.isNotBlank() }?.let { badge ->
                Spacer(Modifier.height(d.gapXs))
                Text(
                    badge.replace('_', ' ').trim(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(d.gapLg))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                QuickAction(IqIcons.Phone, "Audio") {
                    onDismiss()
                    onAudioCall()
                }
                QuickAction(IqIcons.Videocam, "Video") {
                    onDismiss()
                    onVideoCall()
                }
                QuickAction(
                    icon = if (quiet) IqIcons.BellOff else IqIcons.Bell,
                    label = if (quiet) "Muted" else "Mute",
                    enabled = !busy
                ) {
                    val next = !quiet
                    busy = true
                    scope.launch {
                        // Null is the unmute: the repository reads it as "no
                        // duration" and calls the other route.
                        val failed = ChatRepository.setMuted(
                            token,
                            conversationId,
                            if (next) "forever" else null
                        )
                        if (failed == null) {
                            quiet = next
                            // The chat list draws a bell on a muted row, so it is
                            // out of date now.
                            ChatRepository.invalidate()
                        }
                        busy = false
                    }
                }
            }

            Spacer(Modifier.height(d.gapLg))
            HairlineDivider()
            PeerAction(IqIcons.Search, "Search in chat") {
                onDismiss()
                onSearch()
            }
            HairlineDivider()
            PeerAction(IqIcons.Gallery, "Shared media") {
                onDismiss()
                onShared()
            }
        }
    }
}

/** One of the round buttons under the name. */
@Composable
private fun QuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val d = IqTheme.dimens
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(d.radiusMd))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = d.gapMd, vertical = d.gapSm)
    ) {
        Box(
            Modifier
                .size(d.iconButton)
                .clip(CircleShape)
                .background(CrimsonTint),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(d.icon), tint = Crimson)
        }
        Spacer(Modifier.height(d.gapXs))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = InkMuted)
    }
}

/** One of the rows under the divider. */
@Composable
private fun PeerAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val d = IqTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = d.gutter, vertical = d.gapMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(d.icon), tint = InkMuted)
        Spacer(Modifier.width(d.gapMd))
        Text(label, style = MaterialTheme.typography.titleMedium, color = Ink)
    }
}
