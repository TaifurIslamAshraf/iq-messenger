package com.iqsocial.iq_message.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.iqsocial.iq_message.data.remote.CallType
import com.iqsocial.iq_message.data.remote.ChatCall
import com.iqsocial.iq_message.data.call.CallCenter
import com.iqsocial.iq_message.data.Person
import com.iqsocial.iq_message.data.chat.ChatRepository
import com.iqsocial.iq_message.ui.components.Avatar
import com.iqsocial.iq_message.ui.components.CircleIconButton
import com.iqsocial.iq_message.ui.components.avatarBrush
import com.iqsocial.iq_message.ui.icons.IqIcons
import com.iqsocial.iq_message.ui.theme.AcceptGreen
import com.iqsocial.iq_message.ui.theme.CallBlack
import com.iqsocial.iq_message.ui.theme.Crimson
import com.iqsocial.iq_message.ui.theme.IqTheme
import com.iqsocial.iq_message.ui.theme.SystemBarIcons

@Composable
fun IncomingCallScreen(
    onDecline: () -> Unit,
    onAccept: () -> Unit
) {
    SystemBarIcons(darkIcons = false)
    val d = IqTheme.dimens
    val call = CallCenter.call
    val person = remember(call?.peer) { callerOf(call) }
    /**
     * Whether this is a video call, which this screen used to assume.
     *
     * It said "Incoming Video Call..." over a camera icon and offered a camera
     * button to answer with, for every call - so a voice call looked like a video
     * one right up to the moment it was answered, and the button promised a camera
     * that was never going to turn on.
     *
     * `type` was there to be read the whole time: the ringing notification, the
     * waiting bar, the service and the audio routing all branch on it already. This
     * screen was the only place that did not, which made it the only place that
     * could be wrong.
     *
     * Defaults to voice when there is no call, which is the quieter mistake of the
     * two - and by then the screen is on its way out anyway.
     */
    val video = call?.type == CallType.VIDEO
    val transition = rememberInfiniteTransition(label = "ring")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "pulse"
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(CallBlack)
    ) {
        // Stand-in for the caller's blurred camera feed.
        Box(
            Modifier
                .fillMaxSize()
                .background(avatarBrush(person.seed))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.78f),
                            Color.Black.copy(alpha = 0.62f),
                            Color.Black.copy(alpha = 0.86f)
                        )
                    )
                )
        )

        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = d.gutterTight, end = d.gapXs, top = d.gapSm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BrandLockup(color = Color.White.copy(alpha = 0.9f), markSize = d.scaled(20))
                Spacer(Modifier.weight(1f))
                CircleIconButton(
                    icon = IqIcons.MoreVert,
                    contentDescription = "More options",
                    onClick = {},
                    tint = Color.White
                )
            }

            Spacer(Modifier.weight(0.9f))

            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = d.gutter)
            ) {
                // Avatar is a share of the available width, never a fixed dp.
                val avatar = (maxWidth * 0.52f).coerceAtMost(240.dp)
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(avatar * 1.55f)) {
                            val base = size.minDimension * 0.335f
                            listOf(pulse, (pulse + 0.5f) % 1f).forEach { p ->
                                drawCircle(
                                    color = Color.White,
                                    radius = base + p * size.minDimension * 0.16f,
                                    alpha = (1f - p) * 0.28f,
                                    style = Stroke(1.6f)
                                )
                            }
                        }
                        Avatar(
                            person = person,
                            size = avatar,
                            ringWidth = 2.dp,
                            ringColor = Color.White.copy(alpha = 0.85f),
                            ringGap = d.gapXs
                        )
                    }

                    Spacer(Modifier.height(d.gapXl))
                    Text(
                        person.name,
                        style = MaterialTheme.typography.displaySmall,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(d.gapMd))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (video) IqIcons.Videocam else IqIcons.Phone, null,
                            Modifier.size(d.icon), tint = Color.White.copy(alpha = 0.92f)
                        )
                        Spacer(Modifier.width(d.gapSm))
                        Text(
                            if (video) "Incoming Video Call..." else "Incoming Voice Call...",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White.copy(alpha = 0.92f),
                            maxLines = 1
                        )
                    }
                    // Only when it is. The same guard as on the two call screens -
                    // and this is the worst place to claim it falsely, because it is
                    // read while deciding whether to pick up.
                    if (call?.e2ee == true) {
                        Spacer(Modifier.height(d.gapMd))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                IqIcons.Shield, null,
                                Modifier.size(d.iconXs), tint = Color.White.copy(alpha = 0.55f)
                            )
                            Spacer(Modifier.width(d.gapSm))
                            Text(
                                "Encrypted call",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White.copy(alpha = 0.62f),
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1.1f))

            // SpaceEvenly + weights, so the two buttons can never overflow.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = d.gutter, end = d.gutter, bottom = d.gapXxl),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CallAction(
                    icon = IqIcons.CallEnd,
                    label = "Decline",
                    background = Crimson,
                    onClick = onDecline,
                    rotateIcon = true,
                    modifier = Modifier.weight(1f)
                )
                CallAction(
                    icon = if (video) IqIcons.Videocam else IqIcons.Phone,
                    label = "Accept",
                    background = AcceptGreen,
                    onClick = onAccept,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun CallAction(
    icon: ImageVector,
    label: String,
    background: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    rotateIcon: Boolean = false
) {
    val d = IqTheme.dimens
    val button: Dp = d.callAnswer
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            // soft halo around the button, as in the mockup
            Canvas(Modifier.size(button * 1.45f)) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(background.copy(alpha = 0.45f), Color.Transparent),
                        radius = size.minDimension / 2
                    ),
                    radius = size.minDimension / 2
                )
            }
            Box(
                Modifier
                    .size(button)
                    .clip(CircleShape)
                    .background(background)
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier
                        .size(d.iconXl)
                        .then(if (rotateIcon) Modifier.rotate(133f) else Modifier),
                    tint = Color.White
                )
            }
        }
        Spacer(Modifier.height(d.gapSm))
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            maxLines = 1
        )
    }
}

/**
 * The person on the other end, as the call itself reports them.
 *
 * Read from the call rather than looked up: a call can arrive from a push with no
 * conversation loaded at all, and the server sends who is calling precisely so the
 * ringing screen never has to guess.
 */
internal fun callerOf(call: ChatCall?): Person {
    val peer = call?.peer
    return Person(
        id = peer?.uid.orEmpty(),
        name = peer?.fullname?.ifBlank { "Unknown" } ?: "Unknown",
        handle = "",
        photo = peer?.profilePicture.orEmpty()
    )
}
