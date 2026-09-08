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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iqsocial.iq_message.data.CallDirection
import com.iqsocial.iq_message.data.CallKind
import com.iqsocial.iq_message.data.CallRecord
import com.iqsocial.iq_message.data.SampleData
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
@Composable
fun CallsScreen(
    onAudioCall: (String) -> Unit,
    onVideoCall: (String) -> Unit
) {
    SystemBarIcons(darkIcons = true)
    val d = IqTheme.dimens
    var query by remember { mutableStateOf("") }

    val results = remember(query) {
        if (query.isBlank()) SampleData.calls
        else SampleData.calls.filter { it.person.name.contains(query, ignoreCase = true) }
    }

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
            Text(
                "Recent",
                style = MaterialTheme.typography.titleMedium,
                color = InkMuted,
                modifier = Modifier.padding(
                    start = d.gutter, top = d.gapSm, bottom = d.gapXs
                )
            )
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
                    text = if (missed) "Missed · ${record.time}" else record.time,
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
