package com.iqsocial.iq_message.ui.screens

import com.iqsocial.iq_message.ui.theme.Paper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.iqsocial.iq_message.data.auth.SessionStore
import com.iqsocial.iq_message.data.profile.MeRepository
import com.iqsocial.iq_message.data.remote.ApiResult
import com.iqsocial.iq_message.data.remote.ChatMessage
import com.iqsocial.iq_message.data.remote.MessagesApi
import com.iqsocial.iq_message.ui.components.CircleIconButton
import com.iqsocial.iq_message.ui.components.HairlineDivider
import com.iqsocial.iq_message.ui.components.ListSkeleton
import com.iqsocial.iq_message.ui.icons.IqIcons
import com.iqsocial.iq_message.ui.theme.Crimson
import com.iqsocial.iq_message.ui.theme.Hairline
import com.iqsocial.iq_message.ui.theme.Ink
import com.iqsocial.iq_message.ui.theme.InkFaint
import com.iqsocial.iq_message.ui.theme.InkMuted
import com.iqsocial.iq_message.ui.theme.IqTheme
import com.iqsocial.iq_message.ui.theme.SystemBarIcons
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Searching one conversation, on a screen of its own.
 *
 * Opened from the chat's menu with the cursor already in the field. Every match is
 * a row: who said it, the words with the match picked out, and when. Picking one
 * goes back to the chat and takes the thread to that message - loading as much of
 * the history as it takes, since a match can be from long before what the chat had
 * loaded - and marks it there for a moment.
 *
 * `GET /v1/conversations/{id}/messages/search`, newest first, a page at a time:
 * further matches load as the end of the list comes into view.
 */
@Composable
fun ThreadSearchScreen(
    conversationId: String,
    /** The other person's name, for the field's hint and their messages' label. */
    peerName: String,
    onBack: () -> Unit,
    onPick: (ChatMessage) -> Unit
) {
    SystemBarIcons(darkIcons = true)
    val d = IqTheme.dimens
    val context = LocalContext.current
    val token = remember { SessionStore(context).token }
    val myUid = MeRepository.profile?.uid.orEmpty()
    val them = peerName.substringBefore(' ').ifBlank { peerName.ifBlank { "Them" } }

    var query by rememberSaveable { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var next by remember { mutableStateOf<Int?>(null) }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf("") }
    var loadingMore by remember { mutableStateOf(false) }

    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }

    LaunchedEffect(query, token) {
        val q = query.trim()
        if (token.isNullOrBlank() || q.isEmpty()) {
            hits = emptyList()
            next = null
            searching = false
            searched = ""
            return@LaunchedEffect
        }
        // Let the typing settle: a request per keystroke is a request for every
        // half-word on the way to the one being looked for.
        delay(300)
        searching = true
        when (val r = MessagesApi.searchIn(token, conversationId, q)) {
            is ApiResult.Success -> {
                hits = r.data.items.filter { it.id.isNotBlank() }.distinctBy { it.id }
                next = r.data.nextSeq
            }
            is ApiResult.Failure -> {
                hits = emptyList()
                next = null
            }
        }
        searched = q
        searching = false
    }

    // The next page of matches, once the last one is in view.
    suspend fun loadMore() {
        val auth = token ?: return
        val before = next ?: return
        val q = searched.ifBlank { return }
        if (loadingMore) return
        loadingMore = true
        when (val r = MessagesApi.searchIn(auth, conversationId, q, beforeSeq = before)) {
            is ApiResult.Success -> {
                val seen = hits.mapTo(HashSet()) { it.id }
                hits = hits + r.data.items.filter { it.id.isNotBlank() && seen.add(it.id) }
                // The same cursor back again would ask for ever.
                next = r.data.nextSeq?.takeIf { it != before }
            }
            is ApiResult.Failure -> Unit
        }
        loadingMore = false
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
                placeholder = if (peerName.isBlank()) "Search in chat" else "Search in chat with $them",
                focusRequester = focus,
                modifier = Modifier.weight(1f)
            )
        }
        HairlineDivider()

        val typed = query.isNotBlank()
        LazyColumn(Modifier.fillMaxSize()) {
            when {
                !typed -> item {
                    Text(
                        "Search what was said in this chat",
                        style = MaterialTheme.typography.bodyLarge,
                        color = InkMuted,
                        modifier = Modifier.padding(horizontal = d.gutter, vertical = d.gapXl)
                    )
                }

                searching && hits.isEmpty() -> item { ListSkeleton(rows = 5, lines = 2) }

                // Said only once the answer is for what is in the box now.
                hits.isEmpty() && searched == query.trim() -> item {
                    Text(
                        "No messages match “${query.trim()}”",
                        style = MaterialTheme.typography.bodyLarge,
                        color = InkMuted,
                        modifier = Modifier.padding(horizontal = d.gutter, vertical = d.gapXl)
                    )
                }
            }

            if (typed && hits.isNotEmpty()) {
                item {
                    Text(
                        if (hits.size == 1 && next == null) "1 message" else "${hits.size}${if (next != null) "+" else ""} messages",
                        style = MaterialTheme.typography.labelLarge,
                        color = InkFaint,
                        modifier = Modifier.padding(horizontal = d.gutter, vertical = d.gapSm)
                    )
                }
                items(hits, key = { it.id }) { message ->
                    ThreadHitRow(
                        message = message,
                        query = searched,
                        author = if (message.senderUid == myUid) "You" else them,
                        onClick = {
                            keyboard?.hide()
                            onPick(message)
                        }
                    )
                    HairlineDivider(Modifier.padding(start = d.gutter, end = d.gutter))
                }
                if (next != null) {
                    item {
                        LaunchedEffect(hits.size) { loadMore() }
                        ListSkeleton(rows = 2, lines = 2)
                    }
                }
            }
        }
    }
}

/**
 * One match: who said it and when on the first line, the words below with the
 * match in bold crimson - the part that answers the search, found at a glance.
 */
@Composable
private fun ThreadHitRow(
    message: ChatMessage,
    query: String,
    author: String,
    onClick: () -> Unit
) {
    val d = IqTheme.dimens
    val text = message.text.ifBlank { message.type.replaceFirstChar { it.uppercase() } }
    val marked = remember(text, query) {
        buildAnnotatedString {
            val at = if (query.isBlank()) -1 else text.indexOf(query, ignoreCase = true)
            if (at < 0) {
                append(text)
            } else {
                // A long message is shown from a little before the match, so the
                // match is on the visible lines rather than cut off after them.
                val from = if (at > LEAD_IN) text.lastIndexOf(' ', at - LEAD_IN).coerceAtLeast(0) else 0
                if (from > 0) append("…")
                append(text.substring(from, at).trimStart())
                withStyle(SpanStyle(color = Crimson, fontWeight = FontWeight.Bold)) {
                    append(text.substring(at, at + query.length))
                }
                append(text.substring(at + query.length))
            }
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = d.gutter, vertical = d.gapMd)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                author,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(d.gapSm))
            Text(
                whenSaid(message.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = InkFaint,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(d.gapXs))
        Text(
            marked,
            style = MaterialTheme.typography.bodyMedium,
            color = InkMuted,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** "12 Nov 2026, 4:05 PM" - a search reaches back further than a day chip would. */
private fun whenSaid(millis: Long): String =
    if (millis <= 0L) ""
    else SimpleDateFormat("d MMM yyyy, h:mm a", Locale.US).format(Date(millis))

/** Characters of a long message shown before its match. */
private const val LEAD_IN = 40
