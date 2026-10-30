package com.iqsocial.iq_message.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.iqsocial.iq_message.data.Person
import com.iqsocial.iq_message.data.remote.CallType
import com.iqsocial.iq_message.data.remote.ChatCall
import com.iqsocial.iq_message.ui.components.Avatar
import com.iqsocial.iq_message.ui.icons.IqIcons
import com.iqsocial.iq_message.ui.theme.Crimson
import com.iqsocial.iq_message.ui.theme.IqTheme
import com.iqsocial.iq_message.ui.theme.OnlineGreen

/**
 * Somebody else calling while a call is already up.
 *
 * A bar over the call rather than a screen in front of it, because the call in
 * progress is still happening and must stay visible and hangable-up. Taking the
 * whole screen for a call that may never be answered would hide the one that is.
 *
 * Two ways out and no third: take it, which ends the call in progress, or turn it
 * away. There is no hold - LiveKit has no notion of a call parked and resumed, and
 * offering it would be a button that silently dropped the first call anyway.
 *
 * Doing nothing is also an answer: it stops ringing by itself when its time is up,
 * and the caller is told nobody picked up.
 */
@Composable
fun WaitingCallBar(
    call: ChatCall,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    val d = IqTheme.dimens
    val who = call.peer?.fullname?.takeIf { it.isNotBlank() } ?: "Unknown"

    Row(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(d.gapSm)
            .clip(RoundedCornerShape(d.radiusLg))
            .background(Color.Black.copy(alpha = 0.82f))
            .padding(horizontal = d.gapMd, vertical = d.gapSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(
            person = Person(
                id = call.peer?.uid.orEmpty(),
                name = who,
                handle = "",
                photo = call.peer?.profilePicture.orEmpty()
            ),
            size = d.avatarMd
        )
        Spacer(Modifier.width(d.gapMd))
        Column(Modifier.weight(1f)) {
            Text(
                who,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (call.type == CallType.VIDEO) "Incoming video call"
                else "Incoming call",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1
            )
        }

        Spacer(Modifier.width(d.gapSm))
        Row(horizontalArrangement = Arrangement.spacedBy(d.gapSm)) {
            // Declining first, on the side the thumb reaches without crossing the
            // other: the wrong button here drops a call that is in progress.
            RoundAction(IqIcons.CallEnd, "Decline", Crimson, onDecline)
            RoundAction(IqIcons.Phone, "End and accept", OnlineGreen, onAccept)
        }
    }
}

@Composable
private fun RoundAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    colour: Color,
    onClick: () -> Unit
) {
    val d = IqTheme.dimens
    Box(
        Modifier
            .size(d.iconButton)
            .clip(CircleShape)
            .background(colour)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, Modifier.size(d.iconSm), tint = Color.White)
    }
}
