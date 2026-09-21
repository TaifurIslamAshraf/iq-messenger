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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iqsocial.iq_message.data.Person
import com.iqsocial.iq_message.data.SampleData
import com.iqsocial.iq_message.data.contacts.ContactsRepository
import com.iqsocial.iq_message.ui.components.rememberContactsRequest
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
 *
 * Lists the contacts who are **on IQ Social** — the address book filtered down to
 * the numbers `/v1/contacts/sync` matched to an account. People you cannot message
 * do not belong on a messaging app's People tab.
 *
 * Falls back to the sample set when contacts were skipped or the device has none,
 * so the tab is never empty in a demo.
 */
@Composable
fun PeopleScreen(onOpenChat: (String) -> Unit) {
    SystemBarIcons(darkIcons = true)
    val d = IqTheme.dimens
    var query by remember { mutableStateOf("") }

    val deviceContacts = ContactsRepository.contacts
    val matches = ContactsRepository.matches
    val requestContacts = rememberContactsRequest()

    // Only the contacts the server matched to an IQ account. This tab is for people
    // you can actually message, so an address book full of numbers that are not on
    // IQ Social is noise rather than content.
    val onIq = remember(deviceContacts, matches) {
        deviceContacts.filter { ContactsRepository.matchFor(it) != null }
    }
    val source = if (deviceContacts.isNotEmpty()) onIq else SampleData.contacts

    // Distinguishes "nobody matched" from "we never got to ask", which need
    // different things said about them.
    val syncSucceeded = ContactsRepository.lastSync != null

    val grouped = remember(query, source) {
        source
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

        if (deviceContacts.isEmpty()) {
            item {
                SyncContactsRow(onClick = requestContacts)
            }
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

        // The address book was read but nobody in it is on IQ Social. Without this
        // the tab would just look broken.
        if (deviceContacts.isNotEmpty() && onIq.isEmpty()) {
            item {
                NoMatchesState(
                    synced = syncSucceeded,
                    contactCount = deviceContacts.size,
                    onRetry = requestContacts
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

/**
 * Shown when the address book was read but produced no one to talk to.
 *
 * [synced] separates the two reasons that look identical on screen: the server
 * answered and matched nobody, or it was never asked. Only the second is worth
 * offering a retry for.
 */
@Composable
private fun NoMatchesState(synced: Boolean, contactCount: Int, onRetry: () -> Unit) {
    val d = IqTheme.dimens
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = d.gutter, vertical = d.gapXl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(d.avatarXl)
                .clip(CircleShape)
                .background(CrimsonTint),
            contentAlignment = Alignment.Center
        ) {
            Icon(IqIcons.People, null, Modifier.size(d.iconXl), tint = Crimson)
        }

        Spacer(Modifier.height(d.gapLg))
        Text(
            if (synced) "No contacts on IQ Social yet" else "Couldn't check your contacts",
            style = MaterialTheme.typography.titleLarge,
            color = Ink
        )

        Spacer(Modifier.height(d.gapSm))
        Text(
            if (synced) {
                "None of your $contactCount contacts have an account yet. " +
                    "They'll show up here when they join."
            } else {
                "Your contacts are on this phone, but we couldn't reach the server " +
                    "to see who is on IQ Social."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = InkMuted,
            textAlign = TextAlign.Center
        )

        if (!synced) {
            Spacer(Modifier.height(d.gapLg))
            Text(
                "Try again",
                style = MaterialTheme.typography.titleMedium,
                color = Crimson,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(d.radiusMd))
                    .clickable(onClick = onRetry)
                    .padding(horizontal = d.gapLg, vertical = d.gapSm)
            )
        }
    }
}

/**
 * Shown only while the address book is unread: skipping the popup should not be a
 * decision the user can never revisit.
 */
@Composable
private fun SyncContactsRow(onClick: () -> Unit) {
    val d = IqTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = d.gutter)
            .clip(RoundedCornerShape(d.radiusMd))
            .background(CrimsonTint)
            .clickable(onClick = onClick)
            .padding(horizontal = d.gapMd, vertical = d.gapMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(IqIcons.PersonAdd, null, Modifier.size(d.icon), tint = Crimson)
        Spacer(Modifier.width(d.gapMd))
        Column(Modifier.weight(1f)) {
            Text(
                "Sync your contacts",
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Find the people you already know",
                style = MaterialTheme.typography.bodyMedium,
                color = InkMuted
            )
        }
        Icon(IqIcons.ChevronRight, null, Modifier.size(d.icon), tint = Crimson)
    }
}

@Composable
private fun ContactRow(person: Person, onClick: () -> Unit) {
    val d = IqTheme.dimens
    // No "On IQ Social" badge: the list is now only people who are, so saying it on
    // every row would be noise. The number identifies them instead.
    val subtitle = if (person.online) "Online now" else person.handle
    val subtitleColor = if (person.online) OnlineGreen else InkMuted
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
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = subtitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(d.gapSm))
        Icon(IqIcons.ChevronRight, null, Modifier.size(d.icon), tint = InkMuted)
    }
}
