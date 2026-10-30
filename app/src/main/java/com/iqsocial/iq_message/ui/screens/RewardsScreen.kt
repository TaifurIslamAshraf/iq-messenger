package com.iqsocial.iq_message.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import com.iqsocial.iq_message.data.Person
import com.iqsocial.iq_message.data.auth.SessionStore
import com.iqsocial.iq_message.data.remote.ApiResult
import com.iqsocial.iq_message.data.remote.AppliesFrom
import com.iqsocial.iq_message.data.remote.ChatPerson
import com.iqsocial.iq_message.data.remote.RewardSlot
import com.iqsocial.iq_message.data.remote.RewardsApi
import com.iqsocial.iq_message.data.remote.RewardsToday
import com.iqsocial.iq_message.data.remote.SlotStatus
import com.iqsocial.iq_message.data.remote.SpecialChange
import com.iqsocial.iq_message.data.remote.SpecialSlot
import com.iqsocial.iq_message.ui.components.Avatar
import com.iqsocial.iq_message.ui.icons.IqIcons
import com.iqsocial.iq_message.ui.theme.CanvasGrey
import com.iqsocial.iq_message.ui.theme.Crimson
import com.iqsocial.iq_message.ui.theme.CrimsonTint
import com.iqsocial.iq_message.ui.theme.Hairline
import com.iqsocial.iq_message.ui.theme.Ink
import com.iqsocial.iq_message.ui.theme.InkFaint
import com.iqsocial.iq_message.ui.theme.InkMuted
import com.iqsocial.iq_message.ui.theme.IqTheme
import com.iqsocial.iq_message.ui.theme.OnlineGreen
import com.iqsocial.iq_message.ui.theme.SystemBarIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Today's rewards: points, who counts, and how far each has got.
 *
 * The idea the API encodes: a day gives a handful of **slots**, each one a person
 * you can earn points by talking to, plus one **special** slot whose person you
 * choose. Talk fills a slot; a full slot pays once; everything resets at
 * `resets_at`.
 *
 * Three calls back it: `GET /v1/rewards/today` for all of the above, and
 * `PUT`/`DELETE /v1/rewards/special` to pick or clear the chosen person.
 */
@Composable
fun RewardsScreen(
    onOpenChat: (String) -> Unit = {},
    /**
     * Starts a call, which is the only thing that fills a slot.
     *
     * Talking is what the server counts - `talk counts once a one-to-one call ends`
     * - so a screen about empty slots that could only open a chat would be pointing
     * at the wrong door.
     */
    onCall: (String) -> Unit = {}
) {
    SystemBarIcons(darkIcons = true)
    val d = IqTheme.dimens
    val context = LocalContext.current
    val token = remember { SessionStore(context).token }
    val scope = rememberCoroutineScope()

    var today by remember { mutableStateOf<RewardsToday?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failure by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var picking by remember { mutableStateOf(false) }
    var calling by remember { mutableStateOf(false) }
    var changing by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(token, attempt) {
        loading = true
        failure = null
        if (token.isNullOrBlank()) {
            loading = false
            return@LaunchedEffect
        }
        when (val r = RewardsApi.today(token)) {
            is ApiResult.Success -> today = r.data
            is ApiResult.Failure -> failure = r.message
        }
        loading = false
    }

    /**
     * The call happening right now, counted here rather than waited for.
     *
     * The server adds a call to `talk_ms` only once it **ends**, and says so. While
     * one is running the figures would sit still for the length of the call - the
     * one time someone is most likely to be watching them - so the live call's
     * elapsed time is added on this side.
     *
     * Measured against `server_now`, never this phone's clock: `since` is the
     * server's time, and the two can be minutes apart. A timer that opens at "4:12"
     * because the handset runs fast is worse than no timer.
     */
    var liveMs by remember { mutableLongStateOf(0L) }
    val state = today
    LaunchedEffect(state?.live?.callId, state?.serverNow) {
        val live = state?.live
        if (live == null || live.since <= 0L) {
            liveMs = 0L
            return@LaunchedEffect
        }
        val drift = System.currentTimeMillis() - state.serverNow
        while (true) {
            liveMs = (System.currentTimeMillis() - drift - live.since).coerceAtLeast(0L)
            delay(1000)
        }
    }

    fun reload() {
        attempt++
    }

    /**
     * What the server made of a pick or a clear.
     *
     * `applies_from` is the part worth repeating back. The server takes the change
     * either way, but puts it off to tomorrow when today's special slot already has
     * talk against it - so a tap that looked like it did nothing actually did
     * something, just not yet, and saying so is the difference between the two.
     */
    fun applySpecial(result: ApiResult<SpecialChange>, picked: Boolean) {
        changing = false
        when (result) {
            is ApiResult.Success -> {
                note = if (result.data.appliesFrom == AppliesFrom.TOMORROW) {
                    if (picked) {
                        "Today's special slot is already under way, so this starts tomorrow."
                    } else {
                        "Today's special slot is already under way, so this clears tomorrow."
                    }
                } else {
                    null
                }
                reload()
            }
            is ApiResult.Failure -> note = result.message
        }
    }

    PullToRefreshBox(
        isRefreshing = loading && today != null,
        onRefresh = { reload() },
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
                        .padding(start = d.gutter, end = d.gutter, top = d.gapSm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Rewards",
                        style = MaterialTheme.typography.headlineLarge,
                        color = Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (state != null) {
                item { Summary(state, liveMs) }

                item {
                    SpecialCard(
                        special = state.special,
                        liveMs = liveMs,
                        livePeer = state.live?.peerUid,
                        busy = changing,
                        onPick = { picking = true },
                        onClear = {
                            val auth = token ?: return@SpecialCard
                            changing = true
                            scope.launch {
                                applySpecial(RewardsApi.clearSpecial(auth), picked = false)
                            }
                        }
                    )
                }

                if (state.slots.isNotEmpty()) {
                    item {
                        SectionLabel(
                            "Today's people",
                            "${state.slotsUsed} of ${state.slotsTotal} slots used"
                        )
                    }
                    items(state.slots, key = { it.person?.uid.orEmpty() + it.kind.wire }) { slot ->
                        SlotRow(
                            slot = slot,
                            liveMs = liveMs,
                            livePeer = state.live?.peerUid,
                            onClick = { slot.person?.uid?.let(onOpenChat) }
                        )
                    }
                }

                if (state.normalFree > 0) {
                    item { MoreSlots(state.normalFree) { calling = true } }
                }
            }

            note?.let { why ->
                item { Note(why) { note = null } }
            }

            if (loading && today == null) {
                item { Centred { CircularProgressIndicator(color = Crimson) } }
            }

            failure?.let { why ->
                item {
                    Centred {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                why,
                                style = MaterialTheme.typography.bodyLarge,
                                color = InkMuted,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(d.gapMd))
                            Text(
                                "Try again",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Crimson,
                                modifier = Modifier.clickable { reload() }
                            )
                        }
                    }
                }
            }
        }
    }

    if (calling) {
        // The same sheet, for the other reason somebody is chosen here. `already`
        // is left out: anyone can be called, including the special person.
        SpecialPicker(
            token = token,
            already = null,
            title = "Call someone",
            onPick = { uid ->
                calling = false
                onCall(uid)
            },
            onDismiss = { calling = false }
        )
    }

    if (picking) {
        SpecialPicker(
            token = token,
            already = state?.special?.person?.uid,
            onPick = { uid ->
                picking = false
                val auth = token ?: return@SpecialPicker
                changing = true
                scope.launch { applySpecial(RewardsApi.setSpecial(auth, uid), picked = true) }
            },
            onDismiss = { picking = false }
        )
    }
}

/* -- the pieces ----------------------------------------------------------- */

@Composable
private fun Summary(state: RewardsToday, liveMs: Long) {
    val d = IqTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = d.gutter, vertical = d.gapMd),
        horizontalArrangement = Arrangement.spacedBy(d.gapSm)
    ) {
        Figure(
            label = "Points",
            value = state.balance.toString(),
            tint = Crimson,
            modifier = Modifier.weight(1f)
        )
        Figure(
            label = "Talked today",
            // The live call is added here for the same reason it is added to a
            // slot: the server counts a call only once it has ended.
            value = spanOf(state.usedMs + liveMs),
            tint = Ink,
            modifier = Modifier.weight(1f)
        )
    }
    if (state.resetsAt > 0L) {
        Text(
            "Resets at " + clockOf(state.resetsAt),
            style = MaterialTheme.typography.bodyMedium,
            color = InkFaint,
            modifier = Modifier.padding(horizontal = d.gutter)
        )
    }
}

@Composable
private fun Figure(label: String, value: String, tint: Color, modifier: Modifier = Modifier) {
    val d = IqTheme.dimens
    Column(
        modifier
            .clip(RoundedCornerShape(d.radiusMd))
            .background(CanvasGrey)
            .padding(d.gapMd)
    ) {
        Text(
            value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = tint,
            maxLines = 1
        )
        Text(label, style = MaterialTheme.typography.bodyMedium, color = InkMuted, maxLines = 1)
    }
}

@Composable
private fun SectionLabel(title: String, trailing: String) {
    val d = IqTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = d.gutter, end = d.gutter, top = d.gapLg, bottom = d.gapXs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Ink,
            modifier = Modifier.weight(1f)
        )
        Text(trailing, style = MaterialTheme.typography.bodyMedium, color = InkMuted, maxLines = 1)
    }
}

/**
 * The special slot, and the only card with buttons.
 *
 * [SpecialSlot.canChangeToday] is said out loud rather than used to disable the
 * button. The pick still works when it is false - it lands tomorrow instead - and a
 * dead button with no explanation is worse than a live one that says what it will do.
 */
@Composable
private fun SpecialCard(
    special: SpecialSlot,
    liveMs: Long,
    livePeer: String?,
    busy: Boolean,
    onPick: () -> Unit,
    onClear: () -> Unit
) {
    val d = IqTheme.dimens
    val who = special.person
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = d.gutter, vertical = d.gapSm)
            .clip(RoundedCornerShape(d.radiusMd))
            .background(CrimsonTint)
            .padding(d.gapMd)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(IqIcons.Star, null, Modifier.size(d.iconSm), tint = Crimson)
            Spacer(Modifier.width(d.gapSm))
            Text(
                "Special person",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Crimson,
                modifier = Modifier.weight(1f)
            )
            if (special.points > 0) {
                Text(
                    "+${special.points}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Crimson
                )
            }
        }

        Spacer(Modifier.height(d.gapMd))

        if (who == null) {
            Text(
                "Pick someone to earn more for talking to them.",
                style = MaterialTheme.typography.bodyLarge,
                color = InkMuted
            )
        } else {
            PersonLine(
                person = who,
                slotStatus = special.status,
                talkMs = special.talkMs + if (livePeer == who.uid) liveMs else 0L,
                targetMs = special.targetMs
            )
        }

        if (!special.canChangeToday) {
            Spacer(Modifier.height(d.gapSm))
            Text(
                "Today's slot is under way, so a change starts tomorrow.",
                style = MaterialTheme.typography.bodyMedium,
                color = InkMuted
            )
        }

        Spacer(Modifier.height(d.gapMd))
        Row(horizontalArrangement = Arrangement.spacedBy(d.gapSm)) {
            Action(
                label = if (who == null) "Pick someone" else "Change",
                filled = true,
                enabled = !busy,
                onClick = onPick,
                modifier = Modifier.weight(1f)
            )
            if (who != null) {
                Action(
                    label = "Clear",
                    filled = false,
                    enabled = !busy,
                    onClick = onClear,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun Action(
    label: String,
    filled: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val d = IqTheme.dimens
    Row(
        modifier
            .height(d.buttonHeight)
            .clip(RoundedCornerShape(d.radiusMd))
            .background(if (filled) Crimson else Color.White)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (!enabled) InkFaint else if (filled) Color.White else Crimson,
            maxLines = 1
        )
    }
}

@Composable
private fun SlotRow(slot: RewardSlot, liveMs: Long, livePeer: String?, onClick: () -> Unit) {
    val d = IqTheme.dimens
    val who = slot.person ?: return
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = d.gutter, vertical = d.gapSm)
    ) {
        PersonLine(
            person = who,
            slotStatus = slot.status,
            talkMs = slot.talkMs + if (livePeer == who.uid) liveMs else 0L,
            targetMs = slot.targetMs,
            points = slot.points
        )
    }
}

/**
 * One person, what they are worth, and how far along they are.
 *
 * The bar is talk against target. A completed slot shows it full and says so rather
 * than showing a bar at 140%: the target is what pays, and talk beyond it is not
 * worth drawing as progress towards anything.
 */
@Composable
private fun PersonLine(
    person: ChatPerson,
    slotStatus: SlotStatus,
    talkMs: Long,
    targetMs: Long,
    points: Int = 0
) {
    val d = IqTheme.dimens
    val done = slotStatus == SlotStatus.COMPLETED
    val fraction = when {
        done -> 1f
        targetMs <= 0L -> 0f
        else -> (talkMs.toFloat() / targetMs.toFloat()).coerceIn(0f, 1f)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
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
        Column(Modifier.weight(1f)) {
            Text(
                person.fullname.ifBlank { "Unknown" },
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(d.gapXs))
            Text(
                when {
                    done -> "Done"
                    targetMs > 0L -> spanOf(talkMs) + " of " + spanOf(targetMs)
                    else -> spanOf(talkMs)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (done) OnlineGreen else InkMuted,
                maxLines = 1
            )
            Spacer(Modifier.height(d.gapXs))
            Bar(fraction = fraction, done = done)
        }
        if (points > 0) {
            Spacer(Modifier.width(d.gapSm))
            Text(
                "+$points",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (done) OnlineGreen else InkFaint,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun Bar(fraction: Float, done: Boolean) {
    val d = IqTheme.dimens
    Box(
        Modifier
            .fillMaxWidth()
            .height(d.scaled(6))
            .clip(CircleShape)
            .background(Hairline)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(d.scaled(6))
                .clip(CircleShape)
                .background(if (done) OnlineGreen else Crimson)
        )
    }
}

/**
 * The empty slots, and the one thing that fills them.
 *
 * This was a line of text with a plus beside it and nothing behind it - it looked
 * like a button because the API's own note calls it one ("Show \"Add more slots\"
 * while above 0"), and it did nothing when pressed.
 *
 * There is no endpoint that adds a slot, and there is not meant to be: a free slot
 * is filled by talking to somebody new, and that is what this now opens. The label
 * says so too, with the count underneath rather than as the whole sentence.
 */
@Composable
private fun MoreSlots(free: Int, onAdd: () -> Unit) {
    val d = IqTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = d.gutter, vertical = d.gapMd)
            .clip(RoundedCornerShape(d.radiusMd))
            .background(CanvasGrey)
            .clickable(onClick = onAdd)
            .padding(d.gapMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(IqIcons.Add, null, Modifier.size(d.iconSm), tint = Crimson)
        Spacer(Modifier.width(d.gapSm))
        Column(Modifier.weight(1f)) {
            Text(
                "Add more slots",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Ink,
                maxLines = 1
            )
            Text(
                if (free == 1) "One left to fill today" else "$free left to fill today",
                style = MaterialTheme.typography.bodyMedium,
                color = InkMuted,
                maxLines = 1
            )
        }
        Icon(IqIcons.ChevronRight, null, Modifier.size(d.icon), tint = InkFaint)
    }
}

@Composable
private fun Note(text: String, onDismiss: () -> Unit) {
    val d = IqTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = d.gutter, vertical = d.gapSm)
            .clip(RoundedCornerShape(d.radiusMd))
            .background(CrimsonTint)
            .clickable(onClick = onDismiss)
            .padding(d.gapMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Crimson)
    }
}

@Composable
private fun Centred(content: @Composable () -> Unit) {
    val d = IqTheme.dimens
    Box(
        Modifier
            .fillMaxWidth()
            .padding(d.gapXxl),
        contentAlignment = Alignment.Center
    ) { content() }
}

/* -- formatting ----------------------------------------------------------- */

/** Talk time, as a person reads it: minutes until there are hours. */
private fun spanOf(millis: Long): String {
    val total = (millis / 1000L).coerceAtLeast(0L)
    val hours = total / 3600L
    val minutes = (total % 3600L) / 60L
    val seconds = total % 60L
    return when {
        hours > 0L -> "${hours}h ${minutes}m"
        minutes > 0L -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

private fun clockOf(millis: Long): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(millis))
