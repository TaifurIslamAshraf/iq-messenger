package com.iqsocial.iq_message.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iqsocial.iq_message.data.CallDirection
import com.iqsocial.iq_message.data.CallKind
import com.iqsocial.iq_message.data.CallRecord
import com.iqsocial.iq_message.data.auth.SessionStore
import com.iqsocial.iq_message.data.call.formatCallLength
import com.iqsocial.iq_message.data.call.toRecord
import com.iqsocial.iq_message.data.remote.ApiResult
import com.iqsocial.iq_message.data.remote.CallsApi
import com.iqsocial.iq_message.ui.components.Avatar
import com.iqsocial.iq_message.ui.components.CircleIconButton
import com.iqsocial.iq_message.ui.components.HairlineDivider
import com.iqsocial.iq_message.ui.icons.IqIcons
import com.iqsocial.iq_message.ui.theme.Crimson
import com.iqsocial.iq_message.ui.theme.CrimsonTint
import com.iqsocial.iq_message.ui.theme.Ink
import com.iqsocial.iq_message.ui.theme.InkMuted
import com.iqsocial.iq_message.ui.theme.IqTheme
import com.iqsocial.iq_message.ui.theme.OnlineGreen
import com.iqsocial.iq_message.ui.theme.SystemBarIcons
/**
 * Not one of the supplied mockups — built in the same visual language so the
 * Calls tab in the bottom bar leads somewhere real.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallsScreen(
    onAudioCall: (String) -> Unit,
    onVideoCall: (String) -> Unit
) {
    SystemBarIcons(darkIcons = true)
    val d = IqTheme.dimens
    var query by remember { mutableStateOf("") }

    val context = LocalContext.current
    val token = remember { SessionStore(context).token }
    var calls by remember { mutableStateOf<List<CallRecord>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var failure by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var onlyMissed by remember { mutableStateOf(false) }
    var cursor by remember { mutableStateOf<String?>(null) }
    var loadingMore by remember { mutableStateOf(false) }

    // `GET /v1/calls` has existed all along; this tab was still drawing the mockup's
    // empty list, with a comment saying no call API existed.
    LaunchedEffect(token, attempt, onlyMissed) {
        loading = true
        failure = null
        cursor = null
        if (token.isNullOrBlank()) {
            loading = false
            return@LaunchedEffect
        }
        val filter =
            if (onlyMissed) CallsApi.Filter.MISSED else CallsApi.Filter.ALL
        when (val r = CallsApi.history(token, filter)) {
            is ApiResult.Success -> {
                calls = r.data.items.map { it.toRecord() }
                cursor = r.data.nextCursor
            }
            is ApiResult.Failure -> failure = r.message
        }
        loading = false
    }

    /**
     * The next page, asked for when the end of this one comes into view.
     *
     * Only the first thirty calls were ever fetched; `next_cursor` came back on
     * every reply and was thrown away, so history stopped at whatever that first
     * page held.
     */
    suspend fun loadMore() {
        val next = cursor ?: return
        if (token.isNullOrBlank() || loadingMore) return
        loadingMore = true
        val filter = if (onlyMissed) CallsApi.Filter.MISSED else CallsApi.Filter.ALL
        when (val r = CallsApi.history(token, filter, cursor = next)) {
            is ApiResult.Success -> {
                // Ids must stay unique or the list's keys collide, and a server
                // repeating a page is cheaper to guard against than to debug.
                val known = calls.mapTo(HashSet()) { it.id }
                calls = calls + r.data.items.map { it.toRecord() }.filterNot { it.id in known }
                cursor = r.data.nextCursor
            }
            is ApiResult.Failure -> failure = r.message
        }
        loadingMore = false
    }

    // The server has no search over calls, so this filters the page in hand - which
    // is what the box can honestly do, rather than looking like it searches history.
    val results = remember(query, calls) {
        val needle = query.trim()
        if (needle.isBlank()) calls
        else calls.filter { it.person.name.contains(needle, ignoreCase = true) }
    }

    PullToRefreshBox(
        // Only once there is a list to pull: the first load has a spinner of its
        // own in the middle of the screen, and two at once reads as two things
        // happening.
        isRefreshing = loading && calls.isNotEmpty(),
        onRefresh = { attempt++ },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .background(Color.White),
            contentPadding = PaddingValues(bottom = d.gapXl)
        ) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = d.gutter, end = d.gapXs, top = d.gapSm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Calls",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                CircleIconButton(
                    icon = IqIcons.PersonAdd,
                    contentDescription = "New call",
                    onClick = {},
                    iconSize = d.iconLg
                )
                CircleIconButton(
                    icon = IqIcons.MoreVert,
                    contentDescription = "More options",
                    onClick = {}
                )
            }
        }

        item {
            SearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search calls",
                modifier = Modifier.padding(horizontal = d.gutter, vertical = d.gapMd)
            )
        }

        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = d.gutter, end = d.gutter, top = d.gapSm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (onlyMissed) "Missed" else "Recent",
                    style = MaterialTheme.typography.titleMedium,
                    color = InkMuted,
                    modifier = Modifier.weight(1f)
                )
                // The server filters this, so it covers the whole history rather
                // than the page in hand - which is why it is a request and not a
                // predicate over `calls`.
                Text(
                    if (onlyMissed) "Show all" else "Missed only",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Crimson,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onlyMissed = !onlyMissed }
                        .padding(horizontal = d.gapSm, vertical = d.gapXs)
                )
            }
        }

        when {
            loading && calls.isEmpty() -> item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = d.gapXxl),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(d.icon),
                        color = Crimson,
                        strokeWidth = d.scaled(2)
                    )
                }
            }

            failure != null -> item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = d.gutter, vertical = d.gapXxl),
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
                            .clip(CircleShape)
                            .clickable { attempt++ }
                            .padding(horizontal = d.gapMd, vertical = d.gapXs)
                    )
                }
            }

            results.isEmpty() -> item { NoCalls(searching = query.isNotBlank()) }
        }

        items(results, key = { it.id }) { record ->
            CallRow(
                record = record,
                onClick = {
                    if (record.kind == CallKind.VIDEO) onVideoCall(record.person.id)
                    else onAudioCall(record.person.id)
                }
            )
            HairlineDivider(
                Modifier.padding(start = d.avatarLg + d.gutter + d.gapMd, end = d.gutter)
            )
        }

        if (cursor != null && query.isBlank()) {
            item {
                // Reaching this row is the request: it is only composed when the
                // list has been scrolled to the end of what is loaded.
                LaunchedEffect(cursor) { loadMore() }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = d.gapLg),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(d.iconSm),
                        color = Crimson,
                        strokeWidth = d.scaled(2)
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun NoCalls(searching: Boolean) {
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
            Icon(IqIcons.PhoneOutline, null, Modifier.size(d.iconXl), tint = Crimson)
        }
        Spacer(Modifier.height(d.gapLg))
        Text(
            if (searching) "No matches" else "No calls yet",
            style = MaterialTheme.typography.titleLarge,
            color = Ink
        )
        Spacer(Modifier.height(d.gapSm))
        Text(
            if (searching) "No one by that name in your recent calls."
            else "Calls you make from a conversation will be listed here.",
            style = MaterialTheme.typography.bodyMedium,
            color = InkMuted,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CallRow(record: CallRecord, onClick: () -> Unit) {
    val d = IqTheme.dimens
    val missed = record.direction == CallDirection.MISSED
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = d.gutter, vertical = d.gapMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(person = record.person, size = d.avatarLg, showPresence = true)
        Spacer(Modifier.width(d.gapMd))
        Column(Modifier.weight(1f)) {
            Text(
                record.person.name,
                style = MaterialTheme.typography.titleLarge,
                color = if (missed) Crimson else Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(d.gapXs / 2))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (record.direction) {
                        CallDirection.INCOMING -> IqIcons.CallReceived
                        CallDirection.OUTGOING -> IqIcons.CallMade
                        CallDirection.MISSED -> IqIcons.CallMissed
                    },
                    contentDescription = record.direction.name.lowercase(),
                    modifier = Modifier.size(d.iconXs),
                    tint = if (missed) Crimson else OnlineGreen
                )
                Spacer(Modifier.width(d.gapXs))
                Text(
                    // Three different things, and they were one line before: a call
                    // that was missed, one that was answered and ran for a while,
                    // and one that rang out without either side calling it missed.
                    text = when {
                        missed -> "Missed \u00b7 ${record.time}"
                        record.taken ->
                            "${record.time} \u00b7 ${formatCallLength(record.durationSeconds)}"
                        else -> "Not answered \u00b7 ${record.time}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(d.gapSm))
        Box(
            Modifier
                .size(d.iconButton)
                .clip(CircleShape)
                .background(CrimsonTint)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (record.kind == CallKind.VIDEO) IqIcons.Videocam else IqIcons.Phone,
                contentDescription = if (record.kind == CallKind.VIDEO) "Video call" else "Voice call",
                modifier = Modifier.size(d.icon),
                tint = Crimson
            )
        }
    }
}
