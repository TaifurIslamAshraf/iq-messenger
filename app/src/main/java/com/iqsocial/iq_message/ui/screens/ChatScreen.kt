package com.iqsocial.iq_message.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iqsocial.iq_message.data.Message
import com.iqsocial.iq_message.data.MessageKind
import com.iqsocial.iq_message.data.Person
import com.iqsocial.iq_message.data.SampleData
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
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun ChatScreen(
    conversationId: String,
    onBack: () -> Unit,
    onAudioCall: (String) -> Unit,
    onVideoCall: (String) -> Unit
) {
    SystemBarIcons(darkIcons = true)
    val d = IqTheme.dimens
    val conversation = remember(conversationId) { SampleData.conversation(conversationId) }
    val person = conversation.person

    val messages = remember(conversationId) {
        mutableStateListOf<Message>().apply { addAll(SampleData.thread) }
    }
    var draft by remember { mutableStateOf("") }
    var attachmentsOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(ChatCanvas)
    ) {
        ChatTopBar(
            person = person,
            onBack = onBack,
            onAudioCall = { onAudioCall(person.id) },
            onVideoCall = { onVideoCall(person.id) }
        )

        Box(Modifier.weight(1f)) {
            ChatWallpaper()
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    horizontal = d.gutterTight,
                    vertical = d.gapMd
                ),
                verticalArrangement = Arrangement.spacedBy(d.gapSm)
            ) {
                item { DayChip("Today") }
                itemsIndexed(messages, key = { _, m -> m.id }) { _, message ->
                    MessageRow(message)
                }
            }
        }

        TypingIndicator(name = person.name.substringBefore(" "))

        Composer(
            draft = draft,
            onDraftChange = { draft = it },
            onAttach = { attachmentsOpen = true },
            onSend = {
                if (draft.isNotBlank()) {
                    messages += Message(
                        id = "sent_${messages.size}",
                        text = draft.trim(),
                        time = "now",
                        fromMe = true
                    )
                    draft = ""
                }
            }
        )
    }

    if (attachmentsOpen) {
        AttachmentSheet(
            onDismiss = { attachmentsOpen = false },
            onSend = { count ->
                attachmentsOpen = false
                if (count > 0) {
                    messages += Message(
                        id = "media_${messages.size}",
                        text = if (count == 1) "📷 Photo" else "📷 $count photos",
                        time = "now",
                        fromMe = true
                    )
                    scope.launch { listState.animateScrollToItem(messages.lastIndex) }
                }
            }
        )
    }
}

/* -- top bar --------------------------------------------------------------- */

@Composable
private fun ChatTopBar(
    person: Person,
    onBack: () -> Unit,
    onAudioCall: () -> Unit,
    onVideoCall: () -> Unit
) {
    val d = IqTheme.dimens
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
            Avatar(person = person, size = d.avatarMd, showPresence = true)
            Spacer(Modifier.width(d.gapSm))
            // weight(1f) lets the name shrink first when the bar gets tight.
            Column(Modifier.weight(1f)) {
                Text(
                    person.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (person.online) "Online now" else "Last seen recently",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (person.online) OnlineGreen else InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
            CircleIconButton(
                icon = IqIcons.MoreVert,
                contentDescription = "More options",
                onClick = {},
                size = d.iconButtonSm
            )
        }
        HairlineDivider()
    }
}

/* -- message rendering ------------------------------------------------------ */

@Composable
private fun DayChip(label: String) {
    val d = IqTheme.dimens
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .clip(CircleShape)
                .background(Color.White)
                .border(1.dp, Hairline, CircleShape)
                .padding(horizontal = d.gapLg, vertical = d.gapSm)
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = InkMuted)
        }
    }
}

@Composable
private fun MessageRow(message: Message) {
    Box(
        Modifier.fillMaxWidth(),
        contentAlignment = if (message.fromMe) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(
            horizontalAlignment = if (message.fromMe) Alignment.End else Alignment.Start,
            // Bubbles never exceed 84% of the column, on any screen size.
            modifier = Modifier.fillMaxWidth(0.84f)
        ) {
            when (message.kind) {
                MessageKind.TEXT -> TextBubble(message)
                MessageKind.VOICE -> VoiceBubble(message)
            }
        }
    }
}

@Composable
private fun TextBubble(message: Message) {
    val d = IqTheme.dimens
    val tail = d.scaled(9)
    val shape = BubbleShape(fromMe = message.fromMe, radius = d.scaled(15), tail = tail)
    val bg = if (message.fromMe) Crimson else Color.White
    val fg = if (message.fromMe) Color.White else Ink
    val meta = if (message.fromMe) Color.White.copy(alpha = 0.85f) else InkFaint

    Column(
        Modifier
            .wrapContentWidth(if (message.fromMe) Alignment.End else Alignment.Start)
            .clip(shape)
            .background(bg)
            .then(if (message.fromMe) Modifier else Modifier.border(1.dp, Hairline, shape))
            .padding(
                start = if (message.fromMe) d.gapMd else d.gapMd + tail,
                end = if (message.fromMe) d.gapMd + tail else d.gapMd,
                top = d.gapMd,
                bottom = d.gapSm
            )
    ) {
        message.quoted?.let { quoted ->
            Row(
                Modifier
                    .clip(RoundedCornerShape(d.radiusSm))
                    .background(Color.White.copy(alpha = 0.18f))
                    .height(IntrinsicSize.Min)
                    .padding(end = d.gapMd)
            ) {
                Box(
                    Modifier
                        .width(d.scaled(4))
                        .fillMaxHeight()
                        .background(Color.White)
                )
                Column(
                    Modifier.padding(
                        start = d.gapSm, top = d.gapSm, bottom = d.gapSm
                    )
                ) {
                    Text(
                        quoted.author,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(d.gapXs / 2))
                    Text(
                        quoted.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(d.gapSm))
        }

        Text(message.text, style = MaterialTheme.typography.bodyLarge, color = fg)
        Spacer(Modifier.height(d.gapXs))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(if (message.fromMe) Alignment.End else Alignment.Start)
        ) {
            Text(message.time, style = MaterialTheme.typography.bodySmall, color = meta)
            if (message.fromMe && message.delivered) {
                Spacer(Modifier.width(d.gapXs))
                Icon(IqIcons.DoneAll, null, Modifier.size(d.iconXs), tint = Color.White)
            }
        }
    }
}

@Composable
private fun VoiceBubble(message: Message) {
    val d = IqTheme.dimens
    val tail = d.scaled(9)
    val shape = BubbleShape(fromMe = false, radius = d.scaled(15), tail = tail)
    Column {
        Column(
            Modifier
                .wrapContentWidth(Alignment.Start)
                .clip(shape)
                .background(Color.White)
                .border(1.dp, Hairline, shape)
                .padding(
                    start = d.gapMd + tail, end = d.gapMd,
                    top = d.gapMd, bottom = d.gapSm
                )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(d.scaled(44))
                        .clip(CircleShape)
                        .background(Ink),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(IqIcons.PlayArrow, "Play", Modifier.size(d.iconSm), tint = Color.White)
                }
                Spacer(Modifier.width(d.gapMd))
                // Waveform takes the leftover width instead of a fixed 180dp.
                Waveform(
                    Modifier
                        .width(d.scaled(136))
                        .height(d.scaled(26))
                )
                Spacer(Modifier.width(d.gapMd))
                Text(
                    message.voiceLength,
                    style = MaterialTheme.typography.titleSmall,
                    color = Ink,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
            Spacer(Modifier.height(d.gapXs))
            Text(message.time, style = MaterialTheme.typography.bodySmall, color = InkFaint)
        }
        if (message.reaction != null) {
            Row(
                Modifier
                    .padding(start = d.gapLg, top = d.gapXs)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.dp, Hairline, CircleShape)
                    .padding(horizontal = d.gapSm, vertical = d.gapXs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(IqIcons.Favorite, null, Modifier.size(d.iconXs), tint = Crimson)
                Spacer(Modifier.width(d.gapXs))
                Text(
                    message.reactionCount.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink
                )
            }
        }
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
private fun TypingIndicator(name: String) {
    val d = IqTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = d.gutter, vertical = d.gapXs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$name is typing...",
            style = MaterialTheme.typography.bodyMedium,
            color = Crimson,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(Modifier.width(d.gapMd))
        TypingDots()
    }
}

@Composable
private fun Composer(
    draft: String,
    onDraftChange: (String) -> Unit,
    onAttach: () -> Unit,
    onSend: () -> Unit
) {
    val d = IqTheme.dimens
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White)
            .imePadding()
    ) {
        HairlineDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = d.gapXs, vertical = d.gapSm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleIconButton(
                icon = IqIcons.AttachFile,
                contentDescription = "Attach",
                onClick = onAttach,
                iconSize = d.iconLg
            )
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
                            style = MaterialTheme.typography.bodyLarge,
                            color = InkFaint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = draft,
                        onValueChange = onDraftChange,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
                        cursorBrush = SolidColor(Crimson),
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.width(d.gapSm))
                Icon(IqIcons.Emoji, "Emoji", Modifier.size(d.icon), tint = InkMuted)
                Spacer(Modifier.width(d.gapMd))
                Icon(IqIcons.CameraOutline, "Camera", Modifier.size(d.icon), tint = InkMuted)
            }
            Spacer(Modifier.width(d.gapSm))
            Box(
                Modifier
                    .size(d.fieldHeight)
                    .widthIn(min = 48.dp)
                    .clip(CircleShape)
                    .background(Crimson)
                    .clickable(onClick = onSend),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (draft.isBlank()) IqIcons.Mic else IqIcons.Send,
                    contentDescription = if (draft.isBlank()) "Record voice message" else "Send",
                    modifier = Modifier.size(d.icon),
                    tint = Color.White
                )
            }
        }
        Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
    }
}
