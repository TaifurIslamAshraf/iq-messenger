package com.iqsocial.iq_message.ui.screens

import com.iqsocial.iq_message.ui.theme.Paper
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.iqsocial.iq_message.data.Person
import com.iqsocial.iq_message.data.auth.SessionStore
import com.iqsocial.iq_message.data.chat.ChatRepository
import com.iqsocial.iq_message.data.chat.KnownPeople
import com.iqsocial.iq_message.data.remote.ApiResult
import com.iqsocial.iq_message.data.remote.ChatPerson
import com.iqsocial.iq_message.data.remote.ConversationsApi
import com.iqsocial.iq_message.data.remote.PeopleApi
import com.iqsocial.iq_message.ui.components.Avatar
import com.iqsocial.iq_message.ui.components.ListSkeleton
import com.iqsocial.iq_message.ui.components.rememberPeopleResults
import com.iqsocial.iq_message.ui.components.shown
import com.iqsocial.iq_message.ui.components.shimmer
import com.iqsocial.iq_message.ui.components.CircleIconButton
import com.iqsocial.iq_message.ui.components.HairlineDivider
import com.iqsocial.iq_message.ui.icons.IqIcons
import com.iqsocial.iq_message.ui.theme.Crimson
import com.iqsocial.iq_message.ui.theme.CrimsonTint
import com.iqsocial.iq_message.ui.theme.Ink
import com.iqsocial.iq_message.ui.theme.InkMuted
import com.iqsocial.iq_message.ui.theme.IqTheme
import com.iqsocial.iq_message.ui.theme.SystemBarIcons
import kotlinx.coroutines.launch

/**
 * Pick someone from your people and open a conversation with them.
 *
 * Your people are the one contact list - the phone book's people on IQ and whoever
 * was added by QR code - and search looks only inside it, never at everyone on IQ
 * Social. Below the two-character minimum it shows the whole list.
 */
@Composable
fun PeopleSearchScreen(
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit
) {
    SystemBarIcons(darkIcons = true)
    val d = IqTheme.dimens
    val context = LocalContext.current
    val session = remember { SessionStore(context) }
    val scope = rememberCoroutineScope()
    val token = session.token

    var query by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var opening by remember { mutableStateOf<String?>(null) }

    val trimmed = query.trim()
    val results = rememberPeopleResults(token, query)
    val isSearch = results.isSearch
    val searching = results.loading

    fun open(person: ChatPerson) {
        if (token.isNullOrBlank() || opening != null) return
        opening = person.uid
        error = null
        scope.launch {
            when (val r = ConversationsApi.open(token, person.uid)) {
                is ApiResult.Success -> {
                    // Everything about this person is in hand right now and the
                    // route carries an id and nothing else. Kept here so the chat
                    // screen has a name to draw if the server's own answer for a
                    // conversation this new comes back without one.
                    KnownPeople.remember(r.data.id, person)
                    // The list must pick up a conversation that did not exist a
                    // moment ago.
                    ChatRepository.invalidate()
                    opening = null
                    onOpenConversation(r.data.id)
                }
                is ApiResult.Failure -> {
                    opening = null
                    error = r.message
                }
            }
        }
    }

    val people = results.people.map { it.shown() }
    val shownError = error ?: results.error

    Column(
        Modifier
            .fillMaxSize()
            .background(Paper)
            // A search screen is mostly used with the keyboard up; without this the
            // results below the field would sit under it.
            .imePadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = d.gapXs, end = d.gutter, top = d.gapSm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleIconButton(
                icon = IqIcons.ArrowBack,
                contentDescription = "Back",
                onClick = onBack
            )
            Spacer(Modifier.width(d.gapXs))
            Text(
                "New message",
                style = MaterialTheme.typography.headlineSmall,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        SearchField(
            value = query,
            onValueChange = { query = it.take(PeopleApi.MAX_QUERY) },
            placeholder = "Search your people",
            modifier = Modifier.padding(horizontal = d.gutter, vertical = d.gapMd)
        )

        if (shownError != null) {
            Text(
                shownError,
                style = MaterialTheme.typography.bodyMedium,
                color = Crimson,
                modifier = Modifier.padding(horizontal = d.gutter, vertical = d.gapXs)
            )
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = d.gapXl)
        ) {
            item {
                SectionLabel(
                    when {
                        isSearch -> "Results"
                        people.isNotEmpty() -> "Your people"
                        else -> ""
                    }
                )
            }

            items(people, key = { it.uid }) { person ->
                PersonRow(
                    person = person,
                    busy = opening == person.uid,
                    onClick = { open(person) }
                )
                HairlineDivider(
                    Modifier.padding(start = d.avatarLg + d.gutter + d.gapMd, end = d.gutter)
                )
            }

            if (people.isEmpty()) {
                item {
                    SearchHint(
                        searching = searching,
                        isSearch = isSearch,
                        typed = trimmed
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    if (text.isBlank()) return
    val d = IqTheme.dimens
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = InkMuted,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = d.gutter, top = d.gapSm, bottom = d.gapXs)
    )
}

@Composable
private fun PersonRow(person: ChatPerson, busy: Boolean, onClick: () -> Unit) {
    val d = IqTheme.dimens
    val avatarPerson = remember(person) {
        Person(
            id = person.uid,
            name = person.fullname.ifBlank { "Unknown" },
            handle = "",
            photo = person.profilePicture
        )
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !busy, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = d.gutter, vertical = d.gapMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(person = avatarPerson, size = d.avatarLg)
        Spacer(Modifier.width(d.gapMd))
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                avatarPerson.name,
                style = MaterialTheme.typography.titleLarge,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (person.isVerified) {
                Spacer(Modifier.width(d.gapXs))
                Icon(IqIcons.Verified, "Verified", Modifier.size(d.iconSm), tint = Crimson)
            }
        }
        Spacer(Modifier.width(d.gapSm))
        if (busy) {
            // Where the chevron was, and the same width, so the row does not
            // change shape while the chat it points at is being opened.
            Box(Modifier.size(d.icon, d.scaled(10)).shimmer())
        } else {
            Icon(IqIcons.ChevronRight, null, Modifier.size(d.icon), tint = InkMuted)
        }
    }
}

/**
 * Explains an empty list: nobody on it matches, or there is nobody on it yet - and
 * how people get there, since search can't reach anyone else.
 */
@Composable
private fun SearchHint(
    searching: Boolean,
    isSearch: Boolean,
    typed: String
) {
    val d = IqTheme.dimens
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = d.gutter, vertical = d.gapXxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(d.gapSm)
    ) {
        if (searching) {
            // Results are rows, so what stands in for them is rows. This is the
            // one placeholder on the screen that is also the answer's shape.
            ListSkeleton(rows = 5, lines = 1)
            return@Column
        }

        Box(
            Modifier
                .size(d.avatarXl)
                .clip(CircleShape)
                .background(CrimsonTint),
            contentAlignment = Alignment.Center
        ) {
            Icon(IqIcons.Search, null, Modifier.size(d.iconXl), tint = Crimson)
        }
        Spacer(Modifier.height(d.gapXs))
        Text(
            if (isSearch) "Nobody found" else "No people yet",
            style = MaterialTheme.typography.titleLarge,
            color = Ink
        )
        Text(
            if (isSearch) {
                "Nobody on your list matches \"$typed\". Search looks only at your people: " +
                    "your contacts on IQ Social and anyone you added by QR code."
            } else {
                "Sync your contacts, or scan someone's QR code, to add people to talk to."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = InkMuted,
            textAlign = TextAlign.Center
        )
    }
}
