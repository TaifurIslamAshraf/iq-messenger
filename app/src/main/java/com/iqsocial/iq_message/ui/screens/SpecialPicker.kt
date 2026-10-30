package com.iqsocial.iq_message.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import com.iqsocial.iq_message.data.Person
import com.iqsocial.iq_message.data.remote.ApiResult
import com.iqsocial.iq_message.data.remote.ChatPerson
import com.iqsocial.iq_message.data.remote.PeopleApi
import com.iqsocial.iq_message.ui.components.Avatar
import com.iqsocial.iq_message.ui.components.SheetHandle
import com.iqsocial.iq_message.ui.icons.IqIcons
import com.iqsocial.iq_message.ui.theme.Crimson
import com.iqsocial.iq_message.ui.theme.Ink
import com.iqsocial.iq_message.ui.theme.InkMuted
import com.iqsocial.iq_message.ui.theme.IqTheme
import kotlinx.coroutines.delay

/**
 * Choosing the one person the special reward slot is for.
 *
 * Single pick, and it commits on the tap rather than collecting a selection and
 * waiting for a confirm button: there is only one to choose, so a second step would
 * be a button that only ever has one thing to do.
 *
 * Its own sheet rather than [AddToCallSheet] with a flag. That one adds several
 * people to a call in progress and says so throughout; sharing it would mean a
 * handful of conditionals through its middle to change what it is for, which is how
 * one screen quietly becomes two badly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpecialPicker(
    token: String?,
    /** Who it already is, so they are marked rather than offered as a change. */
    already: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    /** What the sheet is for, which is the only thing that differs between uses. */
    title: String = "Special person"
) {
    val d = IqTheme.dimens
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var people by remember { mutableStateOf<List<ChatPerson>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    val searching = query.trim().length >= PeopleApi.MIN_QUERY

    LaunchedEffect(query, token) {
        if (token.isNullOrBlank()) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        // A pause before searching, so typing a name is one request and not one a
        // letter - the same wait the call sheet uses.
        if (searching) delay(SEARCH_PAUSE_MS)
        people = if (searching) {
            PeopleApi.search(token, query.trim())
                .let { if (it is ApiResult.Success) it.data.items else emptyList() }
        } else {
            PeopleApi.suggestions(token)
                .let { if (it is ApiResult.Success) it.data.items else emptyList() }
        }
        loading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        dragHandle = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = d.gapMd),
                contentAlignment = Alignment.Center
            ) { SheetHandle() }
        }
    ) {
        Column(Modifier.fillMaxHeight(0.9f)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                color = Ink,
                modifier = Modifier.padding(horizontal = d.gutter, vertical = d.gapMd)
            )
            SearchField(
                value = query,
                onValueChange = { query = it.take(PeopleApi.MAX_QUERY) },
                placeholder = "Search people",
                modifier = Modifier.padding(horizontal = d.gutter)
            )

            if (loading && people.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(d.gapXxl),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(color = Crimson) }
            }

            LazyColumn(contentPadding = PaddingValues(vertical = d.gapSm)) {
                items(people, key = { it.uid }) { person ->
                    val current = person.uid == already
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !current) {
                                onPick(person.uid)
                            }
                            .padding(horizontal = d.gutter, vertical = d.gapSm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(
                            person = Person(
                                id = person.uid,
                                name = person.fullname.ifBlank { "Unknown" },
                                handle = "",
                                photo = person.profilePicture
                            ),
                            size = d.avatarMd
                        )
                        Spacer(Modifier.width(d.gapSm))
                        Text(
                            person.fullname.ifBlank { "Unknown" },
                            style = MaterialTheme.typography.titleMedium,
                            color = Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (current) {
                            Icon(
                                IqIcons.Check,
                                "Already chosen",
                                Modifier.size(d.icon),
                                tint = Crimson
                            )
                        }
                    }
                }

                if (!loading && people.isEmpty()) {
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(d.gapXxl),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (searching) "Nobody by that name" else "Nobody to suggest yet",
                                style = MaterialTheme.typography.bodyLarge,
                                color = InkMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val SEARCH_PAUSE_MS = 300L
