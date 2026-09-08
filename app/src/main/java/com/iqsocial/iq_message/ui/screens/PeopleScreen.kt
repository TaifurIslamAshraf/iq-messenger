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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iqsocial.iq_message.data.Person
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
 * People tab in the bottom bar leads somewhere real.
 */
@Composable
fun PeopleScreen(onOpenChat: (String) -> Unit) {
    SystemBarIcons(darkIcons = true)
    val d = IqTheme.dimens
    var query by remember { mutableStateOf("") }

    val grouped = remember(query) {
        SampleData.contacts
            .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
            .groupBy { it.name.first().uppercaseChar() }
            .toSortedMap()
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
                    "People",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                CircleIconButton(
                    icon = IqIcons.PersonAdd,
                    contentDescription = "Add contact",
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
                placeholder = "Search people",
                modifier = Modifier.padding(horizontal = d.gutter, vertical = d.gapMd)
            )
        }

        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { }
                    .heightIn(min = 64.dp)
                    .padding(horizontal = d.gutter, vertical = d.gapMd),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(d.avatarLg)
                        .clip(CircleShape)
                        .background(CrimsonTint),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(IqIcons.People, null, Modifier.size(d.iconLg), tint = Crimson)
                }
                Spacer(Modifier.width(d.gapMd))
                Text(
                    "New group",
                    style = MaterialTheme.typography.titleLarge,
                    color = Ink
                )
            }
        }

        grouped.forEach { (letter, people) ->
            item(key = "header_$letter") {
                Text(
                    letter.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = InkMuted,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(start = d.gutter, top = d.gapMd, bottom = d.gapXs)
                )
            }
            people.forEach { person ->
                item(key = person.id) {
                    ContactRow(person) {
                        val conversation = SampleData.conversations
                            .firstOrNull { it.person.id == person.id }
                        onOpenChat(conversation?.id ?: SampleData.conversations.first().id)
                    }
                    HairlineDivider(
                        Modifier.padding(start = d.avatarLg + d.gutter + d.gapMd, end = d.gutter)
                    )
                }
            }
        }
    }
}

@Composable
private fun ContactRow(person: Person, onClick: () -> Unit) {
    val d = IqTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = d.gutter, vertical = d.gapMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(person = person, size = d.avatarLg, showPresence = true)
        Spacer(Modifier.width(d.gapMd))
        Column(Modifier.weight(1f)) {
            Text(
                person.name,
                style = MaterialTheme.typography.titleLarge,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(d.gapXs / 2))
            Text(
                if (person.online) "Online now" else person.handle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (person.online) OnlineGreen else InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(d.gapSm))
        Icon(IqIcons.ChevronRight, null, Modifier.size(d.icon), tint = InkMuted)
    }
}
