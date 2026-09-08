package com.iqsocial.iq_message.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iqsocial.iq_message.data.Conversation
import com.iqsocial.iq_message.data.Person
import com.iqsocial.iq_message.data.SampleData
import com.iqsocial.iq_message.ui.components.Avatar
import com.iqsocial.iq_message.ui.components.CircleIconButton
import com.iqsocial.iq_message.ui.components.HairlineDivider
import com.iqsocial.iq_message.ui.components.UnreadBadge
import com.iqsocial.iq_message.ui.icons.IqIcons
import com.iqsocial.iq_message.ui.theme.Crimson
import com.iqsocial.iq_message.ui.theme.FieldGrey
import com.iqsocial.iq_message.ui.theme.Hairline
import com.iqsocial.iq_message.ui.theme.Ink
import com.iqsocial.iq_message.ui.theme.InkFaint
import com.iqsocial.iq_message.ui.theme.InkMuted
import com.iqsocial.iq_message.ui.theme.IqTheme
import com.iqsocial.iq_message.ui.theme.SystemBarIcons

@Composable
fun ChatsScreen(
    onOpenChat: (String) -> Unit,
    onSimulateIncomingCall: () -> Unit
) {
    SystemBarIcons(darkIcons = true)
    val d = IqTheme.dimens
    var query by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }

    val results = remember(query) {
        if (query.isBlank()) SampleData.conversations
        else SampleData.conversations.filter {
            it.person.name.contains(query, ignoreCase = true) ||
                it.lastMessage.contains(query, ignoreCase = true)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
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
                        "Messages",
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
                                text = { Text("Simulate incoming call") },
                                onClick = {
                                    menuOpen = false
                                    onSimulateIncomingCall()
                                }
                            )
                        }
                    }
                }
            }

            item { StoriesRow() }

            item {
                SearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search conversations",
                    modifier = Modifier.padding(horizontal = d.gutter, vertical = d.gapMd)
                )
            }

            items(results, key = { it.id }) { conversation ->
                ConversationRow(conversation) { onOpenChat(conversation.id) }
                HairlineDivider(
                    Modifier.padding(start = d.avatarLg + d.gutter + d.gapMd, end = d.gutter)
                )
            }
        }

        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = d.gutter, bottom = d.gapLg)
                .size(d.fab)
                .clip(CircleShape)
                .background(Crimson)
                .clickable { onOpenChat(SampleData.conversations.first().id) },
            contentAlignment = Alignment.Center
        ) {
            Icon(IqIcons.ChatCompose, "New message", Modifier.size(d.iconLg), tint = Color.White)
        }
    }
}

@Composable
private fun StoriesRow() {
    val d = IqTheme.dimens
    LazyRow(
        contentPadding = PaddingValues(horizontal = d.gutter - d.gapSm),
        horizontalArrangement = Arrangement.spacedBy(d.gapXs)
    ) {
        item { StoryItem(SampleData.me, label = "Your Story", isOwn = true) }
        items(SampleData.stories, key = { it.id }) { person ->
            StoryItem(person, label = person.name.substringBefore(" "), isOwn = false)
        }
    }
}

@Composable
private fun StoryItem(person: Person, label: String, isOwn: Boolean) {
    val d = IqTheme.dimens
    val avatar = d.scaled(58)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .widthIn(min = avatar + d.gapLg)
            .padding(horizontal = d.gapXs, vertical = d.gapSm)
    ) {
        Box {
            Avatar(
                person = person,
                size = avatar,
                ringWidth = 2.dp,
                ringColor = Crimson,
                ringGap = d.scaled(3)
            )
            if (isOwn) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(d.scaled(22))
                        .clip(CircleShape)
                        .background(Color.White)
                        .padding(1.5.dp)
                        .clip(CircleShape)
                        .background(Crimson),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        IqIcons.Add, "Add to your story",
                        Modifier.size(d.scaled(13)), tint = Color.White
                    )
                }
            }
        }
        Spacer(Modifier.height(d.gapXs + 2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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

@Composable
private fun ConversationRow(conversation: Conversation, onClick: () -> Unit) {
    val d = IqTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = d.gutter, vertical = d.gapMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(person = conversation.person, size = d.avatarLg, showPresence = true)
        Spacer(Modifier.width(d.gapMd))
        Column(Modifier.weight(1f)) {
            Text(
                conversation.person.name,
                style = MaterialTheme.typography.titleLarge,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(d.gapXs / 2))
            Text(
                conversation.lastMessage,
                style = MaterialTheme.typography.bodyLarge,
                color = InkMuted,
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
                conversation.time,
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            if (conversation.unread > 0) {
                UnreadBadge(conversation.unread)
            } else {
                Spacer(Modifier.height(d.scaled(23)))
            }
        }
    }
}
