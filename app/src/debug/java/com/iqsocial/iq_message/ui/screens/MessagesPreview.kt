package com.iqsocial.iq_message.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.iqsocial.iq_message.data.remote.ChatConversation
import com.iqsocial.iq_message.data.remote.ChatPerson
import com.iqsocial.iq_message.data.remote.LastMessage
import com.iqsocial.iq_message.ui.components.HomeTab
import com.iqsocial.iq_message.ui.components.HairlineDivider
import com.iqsocial.iq_message.ui.components.IqBottomBar
import com.iqsocial.iq_message.ui.theme.IQMessageTheme
import com.iqsocial.iq_message.ui.theme.IqTheme

/** Sample content is confined to debug previews. No account or network is used. */
@Preview(name = "Messages", widthDp = 411, heightDp = 850, showBackground = true)
@Preview(name = "Messages compact", widthDp = 320, heightDp = 740, showBackground = true)
@Preview(name = "Messages large text", widthDp = 411, heightDp = 950, fontScale = 1.3f)
@Composable
private fun MessagesPreview() {
    IQMessageTheme {
        val d = IqTheme.dimens
        val conversations = remember { previewConversations() }
        Column(Modifier.fillMaxSize().background(Color.White)) {
            Box(Modifier.weight(1f)) {
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        MessagesHeader(false, {}, Modifier.padding(top = d.gapSm)) {
                            MessagesMenuButton {}
                        }
                        Spacer(Modifier.height(d.gapMd))
                    }
                    items(conversations) { conversation ->
                        ConversationRow(conversation, {}, inboxStyle = true)
                        HairlineDivider(
                            Modifier.padding(start = d.avatarLg + d.gutter + d.gapMd, end = d.gutter)
                        )
                    }
                    item { Spacer(Modifier.height(d.fab + d.gapXl)) }
                }
                NewMessageButton(
                    {},
                    Modifier.align(Alignment.BottomEnd).padding(end = d.gutter, bottom = d.gapLg)
                )
            }
            IqBottomBar(HomeTab.Chats, {}, unread = 2)
        }
    }
}

private fun previewConversations(): List<ChatConversation> {
    val now = System.currentTimeMillis()
    return listOf(
        "Ayesha Rahman" to "See you at the cafe!",
        "Rafi Ahmed" to "The new design looks great",
        "Nusrat Jahan" to "You: Thanks, talk soon!",
        "Sakib Hasan" to "Sent you the photos",
        "Tania Islam" to "Are we still on for tomorrow?",
        "Arif Khan" to "You: Sounds like a plan",
        "Mehedi Hasan" to "Catch up this weekend?"
    ).mapIndexed { index, (name, message) ->
        val id = "preview-$index"
        val time = now - index * 3_600_000L
        ChatConversation(
            id = id, type = "private",
            peer = ChatPerson(id, name, "", if (index == 0) 1 else 0, ""),
            lastMessage = LastMessage(id, 1, id, "text", message, 0, time),
            lastMessageAt = time,
            unreadCount = when (index) { 0 -> 2; 1 -> 1; else -> 0 },
            peerDeliveredSeq = 0, peerReadSeq = 0, markedUnread = false,
            pinned = index == 0, archived = false, muted = index == 5,
            mutedUntil = 0, createdAt = time
        )
    }
}
