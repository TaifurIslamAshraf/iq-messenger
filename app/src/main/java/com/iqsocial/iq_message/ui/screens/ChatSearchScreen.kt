package com.iqsocial.iq_message.ui.screens

import com.iqsocial.iq_message.ui.theme.Paper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.iqsocial.iq_message.data.auth.SessionStore
import com.iqsocial.iq_message.data.remote.ApiResult
import com.iqsocial.iq_message.data.remote.ChatConversation
import com.iqsocial.iq_message.data.remote.ConversationsApi
import com.iqsocial.iq_message.data.remote.MessageHit
import com.iqsocial.iq_message.data.remote.MessagesApi
import com.iqsocial.iq_message.ui.components.CircleIconButton
import com.iqsocial.iq_message.ui.components.HairlineDivider
import com.iqsocial.iq_message.ui.components.ListSkeleton
import com.iqsocial.iq_message.ui.icons.IqIcons
import com.iqsocial.iq_message.ui.theme.Hairline
import com.iqsocial.iq_message.ui.theme.InkMuted
import com.iqsocial.iq_message.ui.theme.IqTheme
import com.iqsocial.iq_message.ui.theme.SystemBarIcons
import kotlinx.coroutines.delay

/**
 * Searching conversations, on a screen of its own.
 *
 * Opened from the search icon on the chat list, with the cursor already in the
 * field and the keyboard up: whoever pressed search has said what they want to do
 * next, and a second tap into the box before typing is a tap they should not owe.
 *
 * The same two searches the list used to run under its own box: people by name,
 * through `GET /v1/conversations/search`, and what was said, through
 * `GET /v1/messages/search`. Both are the server's - the spec matches a word
 * prefix of a name, which a filter over the page already loaded could not
 * reproduce.
 */
@Composable
fun ChatSearchScreen(
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit
) {
    SystemBarIcons(darkIcons = true)
    val d = IqTheme.dimens
    val context = LocalContext.current
    val token = remember { SessionStore(context).token }

    var query by rememberSaveable { mutableStateOf("") }
    var chats by remember { mutableStateOf<List<ChatConversation>>(emptyList()) }
    var messages by remember { mutableStateOf<List<MessageHit>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf("") }

    // Into the field as the screen opens. Asked for after the first frame, once
    // the field exists to take focus; the keyboard is shown as well as focus
    // given, because some keyboards wait for a tap before they open on their own.
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }

    LaunchedEffect(query, token) {
        val q = query.trim()
        if (token.isNullOrBlank() || q.isEmpty()) {
            chats = emptyList()
            messages = emptyList()
            searching = false
            searched = ""
            return@LaunchedEffect
        }
        // Let the typing settle before asking: a request per keystroke is a
        // request for every half-word on the way to the name.
        delay(300)
        searching = true
        // Each result once. The lists below key their rows by id, and a lazy
        // list handed one key twice throws rather than skipping the repeat - so
        // a server that answered with the same conversation twice would have
        // crashed the search instead of showing it.
        chats = when (val r = ConversationsApi.search(token, q)) {
            is ApiResult.Success -> r.data.distinctBy { it.id }
            is ApiResult.Failure -> emptyList()
        }
        // Searching a messenger means searching what was said, not only who said
        // it, so message text is searched alongside the names.
        // A hit without its message or its conversation has nothing to show and
        // nowhere to go, so it is left out rather than keyed on a hash.
        messages = when (val r = MessagesApi.search(token, q)) {
            is ApiResult.Success -> r.data.items
                .filter { it.message != null && it.conversation != null }
                .distinctBy { it.message?.id }
            is ApiResult.Failure -> emptyList()
        }
        searched = q
        searching = false
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .statusBarsPadding()
            .imePadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = d.gapXs, end = d.gutter, top = d.gapXs, bottom = d.gapSm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleIconButton(
                icon = IqIcons.ArrowBack,
                contentDescription = "Back",
                onClick = {
                    keyboard?.hide()
                    onBack()
                },
                borderColor = Hairline
            )
            Spacer(Modifier.width(d.gapSm))
            SearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search conversations",
                focusRequester = focus,
                modifier = Modifier.weight(1f)
            )
        }
        HairlineDivider()

        val typed = query.isNotBlank()
        LazyColumn(Modifier.fillMaxSize()) {
            when {
                // Nothing typed yet: a line saying what this finds, rather than a
                // blank page under a keyboard.
                !typed -> item {
                    Text(
                        "Search people and messages",
                        style = MaterialTheme.typography.bodyLarge,
                        color = InkMuted,
                        modifier = Modifier.padding(horizontal = d.gutter, vertical = d.gapXl)
                    )
                }

                // Asked and not yet answered, with nothing from an earlier answer
                // to show meanwhile: the rows the answer will arrive as.
                searching && chats.isEmpty() && messages.isEmpty() ->
                    item { ListSkeleton(rows = 5, trailing = true) }

                // Answered, and empty. Only said once the answer is for what is in
                // the box now - not while a newer query is still settling.
                chats.isEmpty() && messages.isEmpty() && searched == query.trim() ->
                    item { EmptyChats(searching = true) }
            }

            if (typed && chats.isNotEmpty()) {
                item { SearchHeading("Chats") }
                items(chats, key = { "c" + it.id }) { conversation ->
                    ConversationRow(
                        conversation = conversation,
                        onClick = {
                            keyboard?.hide()
                            onOpenChat(conversation.id)
                        }
                    )
                    HairlineDivider(
                        Modifier.padding(start = d.avatarLg + d.gutter + d.gapMd, end = d.gutter)
                    )
                }
            }

            if (typed && messages.isNotEmpty()) {
                item { SearchHeading("Messages") }
                items(messages, key = { "m" + it.message?.id }) { hit ->
                    MessageHitRow(hit, query.trim()) {
                        hit.conversation?.id?.let {
                            keyboard?.hide()
                            onOpenChat(it)
                        }
                    }
                    HairlineDivider(
                        Modifier.padding(start = d.avatarLg + d.gutter + d.gapMd, end = d.gutter)
                    )
                }
            }
        }
    }
}
