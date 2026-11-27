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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.iqsocial.iq_message.data.people.PeopleRepository
import com.iqsocial.iq_message.data.remote.ApiResult
import com.iqsocial.iq_message.ui.components.CircleIconButton
import com.iqsocial.iq_message.ui.components.QrCodeEntryDialog
import kotlinx.coroutines.launch
import com.iqsocial.iq_message.data.Person
import com.iqsocial.iq_message.data.remote.PeopleApi
import com.iqsocial.iq_message.ui.components.Avatar
import com.iqsocial.iq_message.ui.components.ListSkeleton
import com.iqsocial.iq_message.ui.components.rememberPeopleResults
import com.iqsocial.iq_message.ui.components.shown
import com.iqsocial.iq_message.ui.components.SheetHandle
import com.iqsocial.iq_message.ui.icons.IqIcons
import com.iqsocial.iq_message.ui.theme.Crimson
import com.iqsocial.iq_message.ui.theme.Ink
import com.iqsocial.iq_message.ui.theme.InkMuted
import com.iqsocial.iq_message.ui.theme.IqTheme

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
    title: String = "Special person",
    /**
     * Offers a QR scan at the top: someone not on the list yet is added by their
     * code and picked in the same go. Only for the special person - the call picker
     * calls people already here.
     */
    scanToPick: Boolean = false
) {
    val d = IqTheme.dimens
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    // Only the account's own people: the server refuses anyone else as the special
    // person, so offering them would only end in an error.
    val results = rememberPeopleResults(token, query)
    val people = results.people.map { it.shown() }
    val loading = results.loading
    val scope = rememberCoroutineScope()
    var entering by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var codeNote by remember { mutableStateOf<String?>(null) }

    // The code goes to the server, which puts its owner on this account's people
    // list; then they are picked like anyone already on it.
    fun pickByCode(code: String) {
        val auth = token ?: return
        if (adding) return
        adding = true
        codeNote = null
        scope.launch {
            when (val r = PeopleRepository.addByCode(auth, code)) {
                is ApiResult.Success -> {
                    entering = false
                    onPick(r.data.person.uid)
                }
                is ApiResult.Failure -> codeNote = r.message
            }
            adding = false
        }
    }

    if (entering) {
        QrCodeEntryDialog(
            busy = adding,
            note = codeNote,
            onCode = ::pickByCode,
            onDismiss = { entering = false }
        )
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
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = d.gutter,
                        end = if (scanToPick) d.gapXs else d.gutter,
                        top = d.gapMd,
                        bottom = d.gapMd
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Ink,
                    modifier = Modifier.weight(1f)
                )
                if (scanToPick) {
                    CircleIconButton(
                        icon = IqIcons.QrCode,
                        contentDescription = "Add by QR code",
                        onClick = {
                            codeNote = null
                            entering = true
                        },
                        tint = Crimson
                    )
                }
            }
            if (scanToPick) {
                Text(
                    "Whoever you pick stays your special person for 2 days. Tap the QR icon to add someone new by their code.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted,
                    modifier = Modifier.padding(start = d.gutter, end = d.gutter, bottom = d.gapMd)
                )
            }
            SearchField(
                value = query,
                onValueChange = { query = it.take(PeopleApi.MAX_QUERY) },
                placeholder = "Search your people",
                modifier = Modifier.padding(horizontal = d.gutter)
            )

            if (loading && people.isEmpty()) {
                ListSkeleton(rows = 4, lines = 1)
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
                                when {
                                    results.error != null -> results.error
                                    results.isSearch -> "Nobody on your list matches that"
                                    else -> "Your people list is empty. Sync your contacts or add someone by QR code."
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = InkMuted,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

