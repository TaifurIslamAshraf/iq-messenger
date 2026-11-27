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
import com.iqsocial.iq_message.data.contacts.ContactsRepository
import com.iqsocial.iq_message.data.people.PeopleRepository
import com.iqsocial.iq_message.data.remote.PeopleApi
import com.iqsocial.iq_message.data.remote.SavedPerson
import com.iqsocial.iq_message.ui.components.ListSkeleton
import com.iqsocial.iq_message.ui.components.rememberPeopleResults
import com.iqsocial.iq_message.data.people.shownName
import com.iqsocial.iq_message.data.remote.ApiResult
import com.iqsocial.iq_message.data.remote.ConversationsApi
import com.iqsocial.iq_message.ui.components.Avatar
import com.iqsocial.iq_message.ui.components.CircleIconButton
import com.iqsocial.iq_message.ui.components.HairlineDivider
import com.iqsocial.iq_message.ui.components.rememberContactsRequest
import com.iqsocial.iq_message.ui.icons.IqIcons
import com.iqsocial.iq_message.ui.theme.Crimson
import com.iqsocial.iq_message.ui.theme.CrimsonTint
import com.iqsocial.iq_message.ui.theme.Ink
import com.iqsocial.iq_message.ui.theme.InkMuted
import com.iqsocial.iq_message.ui.theme.IqTheme
import com.iqsocial.iq_message.ui.theme.OnlineGreen
import com.iqsocial.iq_message.ui.theme.SystemBarIcons
import kotlinx.coroutines.launch
/**
 * Not one of the supplied mockups — built in the same visual language so the
 * People tab in the bottom bar leads somewhere real.
 *
 * The account's people (`GET /v1/people`): everyone in the phone book who is on IQ
 * Social, and anyone added by QR code. It is the same list "New message", the
 * rewards picker and "Add to call" offer, and the search box searches only it.
 * Accepting someone's message request does not put them here.
 *
 * Someone saved in this phone's address book is shown under the name saved there,
 * as a phone would; anyone else under their IQ Social name.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleScreen(onOpenChat: (String) -> Unit, onFindPeople: () -> Unit = {}) {
    SystemBarIcons(darkIcons = true)
    val d = IqTheme.dimens
    var query by remember { mutableStateOf("") }

    val context = LocalContext.current
    val session = remember { SessionStore(context) }
    val scope = rememberCoroutineScope()
    val token = session.token
    val requestContacts = rememberContactsRequest()
    val deviceContacts = ContactsRepository.contacts

    val results = rememberPeopleResults(token, query)
    val rows = remember(results.people, deviceContacts) { results.people.map(::rowOf) }
    val grouped = remember(rows) {
        // A contact saved as a number alone has an empty name, and `first()` on an
        // empty string throws - one such entry took the whole tab down once.
        // Nameless people, shown by their number, file under '#' at the end, as a
        // phone's own contacts do.
        rows.groupBy { it.name.trim().firstOrNull()?.takeIf(Char::isLetter)?.uppercaseChar() ?: '#' }
            .toSortedMap(compareBy<Char> { it == '#' }.thenBy { it })
    }

    // Sends the whole phone book again - the only way to find someone already in it
    // who has joined IQ since - and fetches the list again. An ordinary launch sends
    // only numbers that are new.
    var pull by remember { mutableIntStateOf(0) }
    LaunchedEffect(pull) {
        if (pull > 0) {
            ContactsRepository.refresh(context, token, everything = true)
            token?.let { PeopleRepository.refresh(it) }
        }
    }

    PullToRefreshBox(
        isRefreshing = ContactsRepository.syncing || PeopleRepository.loading,
        onRefresh = { pull++ },
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
                    "People",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                CircleIconButton(
                    icon = IqIcons.PersonAdd,
                    contentDescription = "New message",
                    onClick = onFindPeople,
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
                onValueChange = { query = it.take(PeopleApi.MAX_QUERY) },
                placeholder = "Search your people",
                modifier = Modifier.padding(horizontal = d.gutter, vertical = d.gapMd)
            )
        }

        if (deviceContacts.isEmpty() && !results.isSearch) {
            item {
                SyncContactsRow(onClick = requestContacts)
            }
        }

        if (!results.isSearch) {
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
        }

        if (rows.isEmpty()) {
            item {
                if (results.loading) {
                    ListSkeleton(rows = 5, lines = 1)
                } else {
                    EmptyPeople(
                        isSearch = results.isSearch,
                        failed = results.error != null,
                        onRetry = { pull++ }
                    )
                }
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
                        val auth = token ?: return@ContactRow
                        scope.launch {
                            val r = ConversationsApi.open(auth, person.id)
                            if (r is ApiResult.Success) onOpenChat(r.data.id)
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
}

/** A row of the list: the name this phone gives them, and how they got here. */
private fun rowOf(saved: SavedPerson): Person {
    return Person(
        id = saved.uid,
        name = saved.shownName,
        handle = saved.phone ?: "Added by QR code",
        photo = saved.person.profilePicture
    )
}

/**
 * Shown when there is nobody to list: nobody matches the search, nobody is on the
 * list yet, or the list could not be fetched - only the last is worth a retry.
 */
@Composable
private fun EmptyPeople(isSearch: Boolean, failed: Boolean, onRetry: () -> Unit) {
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
            when {
                failed -> "Couldn't load your people"
                isSearch -> "Nobody found"
                else -> "No people yet"
            },
            style = MaterialTheme.typography.titleLarge,
            color = Ink
        )

        Spacer(Modifier.height(d.gapSm))
        Text(
            when {
                failed -> "Check your connection and try again."
                isSearch -> "Nobody on your list matches that. Search looks only at your people."
                else -> "Your contacts on IQ Social show up here, and anyone you add by QR code."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = InkMuted,
            textAlign = TextAlign.Center
        )

        if (failed) {
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
