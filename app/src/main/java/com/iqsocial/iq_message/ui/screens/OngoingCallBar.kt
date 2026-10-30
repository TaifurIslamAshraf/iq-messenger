package com.iqsocial.iq_message.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.iqsocial.iq_message.data.call.CallEngine
import com.iqsocial.iq_message.data.remote.CallType
import com.iqsocial.iq_message.data.remote.ChatCall
import com.iqsocial.iq_message.ui.icons.IqIcons
import com.iqsocial.iq_message.ui.theme.Crimson
import com.iqsocial.iq_message.ui.theme.IqTheme
import kotlinx.coroutines.delay

/**
 * A call that is still running, while the rest of the app is being used.
 *
 * The missing half of minimising. Backing out of a call screen used to shrink the
 * whole app into a floating window - which keeps the call visible and takes the app
 * away, so during a call there was no way to read a message, find a photo or do
 * anything else. The window is right for leaving the app; it is the wrong answer for
 * staying in it.
 *
 * So minimising now returns to whatever was underneath, and this strip is what keeps
 * the call from being lost: it says a call is on, how long it has been on, and
 * tapping it goes back. The floating window is still there for leaving the app
 * entirely, which is where it belongs.
 *
 * Deliberately a strip and not a chip in a corner: it has to be legible at a glance
 * and impossible to miss, because the one thing worse than losing the call is
 * forgetting it is running. It takes its own space above the screen rather than
 * lying over it - a red bar across a title or a search field hid the screen to
 * announce the call, which is a poor trade for a call that is only running.
 */
@Composable
fun OngoingCallBar(call: ChatCall, onReturn: () -> Unit) {
    val d = IqTheme.dimens
    val who = call.peer?.fullname?.takeIf { it.isNotBlank() } ?: "In call"

    // Counted from when it was answered, as the call screen counts it, so the two
    // never disagree by the seconds it spent ringing.
    var seconds by remember(call.id) { mutableIntStateOf(0) }
    LaunchedEffect(call.answeredAt) {
        val from = call.answeredAt
        while (true) {
            seconds = if (from > 0L) ((System.currentTimeMillis() - from) / 1000).toInt() else 0
            delay(1000)
        }
    }

    // Edge to edge, square-cornered, and up into the status bar.
    //
    // It was a rounded pill inset by a margin, which is the right shape for
    // something floating over a screen - and it is not floating over anything any
    // more. A band across the top of the window is what it is now, so it is drawn
    // as one: no margin, no corner radius, and the colour running up behind the
    // status bar rather than stopping under it and leaving a pale stripe above.
    //
    // The inset is applied after the background on purpose. That is the whole
    // trick: the crimson takes the full height including the status bar, and the
    // padding it adds only pushes the row's contents clear of the clock.
    Row(
        Modifier
            .fillMaxWidth()
            .background(Crimson)
            .clickable(onClick = onReturn)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = d.gapMd, vertical = d.gapSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(d.iconButtonSm)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (call.type == CallType.VIDEO) IqIcons.Videocam else IqIcons.Phone,
                null,
                Modifier.size(d.iconSm),
                tint = Color.White
            )
        }
        Spacer(Modifier.width(d.gapMd))
        Text(
            who,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(Modifier.width(d.gapSm))
        Text(
            // The duration once there is one; what it is doing until then, so a call
            // still connecting does not sit under a timer reading 0:00.
            if (seconds > 0) formatDuration(seconds)
            else standingOf(
                call,
                joined = CallEngine.connected,
                reconnecting = CallEngine.reconnecting
            ).label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.85f),
            maxLines = 1
        )
        Spacer(Modifier.weight(1f))
        Text(
            "Tap to return",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.75f),
            maxLines = 1
        )
    }
}
