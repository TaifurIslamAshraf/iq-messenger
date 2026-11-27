package com.iqsocial.iq_message.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.iqsocial.iq_message.data.remote.PeopleApi
import com.iqsocial.iq_message.ui.components.Avatar
import com.iqsocial.iq_message.ui.components.ListSkeleton
import com.iqsocial.iq_message.ui.components.rememberPeopleResults
import com.iqsocial.iq_message.ui.components.shown
import com.iqsocial.iq_message.ui.components.HairlineDivider
import com.iqsocial.iq_message.ui.components.SheetHandle
import com.iqsocial.iq_message.ui.icons.IqIcons
import com.iqsocial.iq_message.ui.theme.Crimson
import com.iqsocial.iq_message.ui.theme.CrimsonTint
import com.iqsocial.iq_message.ui.theme.Ink
import com.iqsocial.iq_message.ui.theme.InkFaint
import com.iqsocial.iq_message.ui.theme.InkMuted
import com.iqsocial.iq_message.ui.theme.IqTheme

/**
 * Who to add to the call in progress.
 *
 * The server has taken invitations all along - `POST /v1/calls/{id}/invite`, up to
 * seven at a time - and "Add Call" on the call screen had an empty handler, so the
 * button was there and did nothing. This is the missing half.
 *
 * Several at once rather than one: the API takes a list, a call being turned into a
 * group usually gains more than one person, and inviting them one at a time would
 * send a separate ring to each.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToCallSheet(
    token: String?,
    /** Who is already on the call, so they are not offered again. */
    exclude: Set<String>,
    onAdd: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val d = IqTheme.dimens
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf<Set<String>>(emptySet()) }
    // The account's own people, as everywhere else that offers someone.
    val results = rememberPeopleResults(token, query)
    val people = results.people.map { it.shown() }.filterNot { it.uid in exclude }
    val loading = results.loading
    val searching = results.isSearch

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
                    .padding(horizontal = d.gutter, vertical = d.gapMd),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Add to call",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Ink,
                    modifier = Modifier.weight(1f)
                )
                if (chosen.isNotEmpty()) {
                    Text(
                        "Add ${chosen.size}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Crimson,
                        modifier = Modifier
                            .clip(RoundedCornerShape(d.radiusMd))
                            .background(CrimsonTint)
                            .clickable {
                                onAdd(chosen.toList())
                                onDismiss()
                            }
                            .padding(horizontal = d.gapMd, vertical = d.gapXs)
                    )
                }
            }

            SearchField(
                value = query,
                onValueChange = { query = it.take(PeopleApi.MAX_QUERY) },
                placeholder = "Search your people",
                modifier = Modifier.padding(horizontal = d.gutter)
            )
            Spacer(Modifier.height(d.gapSm))
            HairlineDivider()

            when {
                // A sheet is short, so the skeleton is short: enough rows to
                // fill what can be seen and no more.
                loading -> ListSkeleton(rows = 4, lines = 1)

                people.isEmpty() -> Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(d.gutter),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (searching) "Nobody on your list matches that."
                        else "Your people list is empty. Sync your contacts or add someone by QR code.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkMuted,
                        textAlign = TextAlign.Center
                    )
                }

                else -> LazyColumn(Modifier.fillMaxWidth()) {
                    items(people, key = { it.uid }) { person ->
                        val picked = person.uid in chosen
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    chosen = if (picked) chosen - person.uid
                                    else chosen + person.uid
                                }
                                .heightIn(min = 64.dp)
                                .padding(horizontal = d.gutter, vertical = d.gapSm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Avatar(
                                person = Person(
                                    id = person.uid,
                                    name = person.fullname,
                                    handle = "",
                                    photo = person.profilePicture
                                ),
                                size = d.avatarMd
                            )
                            Spacer(Modifier.width(d.gapMd))
                            Text(
                                person.fullname.ifBlank { "Unknown" },
                                style = MaterialTheme.typography.titleMedium,
                                color = Ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                Modifier
                                    .size(d.icon)
                                    .clip(CircleShape)
                                    .background(if (picked) Crimson else Color.Transparent),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (picked) IqIcons.Check else IqIcons.PersonAdd,
                                    null,
                                    Modifier.size(d.iconSm),
                                    tint = if (picked) Color.White else InkFaint
                                )
                            }
                        }
                        HairlineDivider()
                    }
                }
            }
        }
    }
}
