package com.iqsocial.iq_message.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iqsocial.iq_message.data.Person
import com.iqsocial.iq_message.data.call.CallCenter
import com.iqsocial.iq_message.data.call.CallAudio
import com.iqsocial.iq_message.data.call.CallEngine
import com.iqsocial.iq_message.data.profile.MeRepository
import com.iqsocial.iq_message.ui.components.CallVideo
import com.iqsocial.iq_message.ui.components.HumpedBarShape
import com.iqsocial.iq_message.ui.components.KeepScreenOn
import com.iqsocial.iq_message.ui.components.avatarBrush
import com.iqsocial.iq_message.ui.components.rememberCameraToggle
import com.iqsocial.iq_message.ui.icons.IqIcons
import com.iqsocial.iq_message.ui.theme.CallBlack
import com.iqsocial.iq_message.ui.theme.Crimson
import com.iqsocial.iq_message.ui.theme.InkFaint
import com.iqsocial.iq_message.ui.theme.IqTheme
import com.iqsocial.iq_message.ui.theme.SystemBarIcons
import kotlinx.coroutines.delay
@Composable
fun VideoCallScreen(
    onMinimize: () -> Unit,
    onEnd: () -> Unit
) {
    SystemBarIcons(darkIcons = false)
    val d = IqTheme.dimens
    val call = CallCenter.call
    val person = remember(call?.peer) { callerOf(call) }
    val profile = MeRepository.profile
    val toggleCamera = rememberCameraToggle()
    val me = remember(profile) {
        Person(
            id = profile?.uid.orEmpty().ifBlank { "me" },
            name = profile?.fullname.orEmpty().ifBlank { "You" },
            handle = "",
            photo = profile?.profilePicture.orEmpty()
        )
    }

    // From when it was answered, not from when this screen opened - see the audio
    // screen for why those differ.
    val standing = standingOf(
        call,
        joined = CallEngine.connected,
        reconnecting = CallEngine.reconnecting,
        poor = CallEngine.poorConnection
    )
    var seconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(call?.answeredAt) {
        val from = call?.answeredAt ?: 0L
        while (true) {
            seconds =
                if (from > 0L) ((System.currentTimeMillis() - from) / 1000).toInt() else 0
            delay(1000)
        }
    }

    // A video call is watched without being touched, so the idle timeout has no
    // idea anyone is there and dims the screen mid-conversation.
    KeepScreenOn()

    Box(
        Modifier
            .fillMaxSize()
            .background(CallBlack)
    ) {
        // Their camera, full screen. Until a frame arrives - they have not joined,
        // or have their camera off - the seeded gradient stands in, which is the same
        // thing their avatar is drawn on and so reads as them rather than as a fault.
        Box(Modifier.fillMaxSize().background(avatarBrush(person.seed))) {
            val feeds = CallEngine.remoteFeeds
            if (feeds.size > 1) {
                // More than one other person, so there is no "their camera" to fill
                // the screen with. Only one was ever drawn before - whichever the
                // room listed first - and the rest were in the call unseen.
                CallGrid(
                    feeds = feeds,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(d.gapXs)
                )
            } else {
                CallVideo(
                    track = CallEngine.remoteVideo,
                    modifier = Modifier.fillMaxSize()
                )
            }
            // The scrim goes over whichever of the two is showing, so the controls
            // and the name stay readable against a bright picture.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.55f),
                            0.35f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.45f)
                        )
                    )
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                // Self-view is a share of the width so it never squeezes the name.
                val selfWidth = (maxWidth * 0.27f).coerceIn(88.dp, 150.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = d.gutterTight, end = d.gutterTight, top = d.gapMd)
                ) {
                    Box(
                        Modifier
                            .size(d.iconButtonLg)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.16f))
                            .clickable(onClick = onMinimize),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            IqIcons.ChevronDown, "Minimize",
                            Modifier.size(d.iconLg), tint = Color.White
                        )
                    }
                    Spacer(Modifier.width(d.gapMd))
                    Column(Modifier.weight(1f)) {
                        Text(
                            person.name,
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        // The duration once there is one, and what the call is
                        // doing until then. A header reading 0:00 over a phone that
                        // is still ringing is the screen answering a question it has
                        // not been asked and not answering the one it has.
                        Text(
                            if (seconds > 0) formatDuration(seconds) else standing.label,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Normal,
                            maxLines = 1
                        )
                        // Shown only when the call actually is one.
                        //
                        // This line was unconditional, and it was the app's most
                        // serious untruth: no call this build places is encrypted
                        // end to end - `/v1/keys` is never called, this install has
                        // no key pair, and nothing is handed to LiveKit's encryption
                        // - so every call carried a shield telling the person it was
                        // private while the media went through the server in the
                        // clear. Privacy is the one claim an app must not make ahead
                        // of itself.
                        //
                        // `e2ee` is the server's own word for it, parsed since the
                        // call models were written and never once read. It is false
                        // until the key exchange exists, which is exactly when this
                        // should start appearing by itself.
                        if (call?.e2ee == true) {
                            Spacer(Modifier.height(d.gapXs))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    IqIcons.Shield, null,
                                    Modifier.size(d.iconXs),
                                    tint = Color.White.copy(alpha = 0.75f)
                                )
                                Spacer(Modifier.width(d.gapXs))
                                Text(
                                    "End-to-end encrypted",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.78f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(d.gapSm))
                    Box(
                        Modifier
                            .width(selfWidth)
                            .aspectRatio(0.67f)
                            .clip(RoundedCornerShape(d.radiusLg))
                            .background(avatarBrush(me.seed))
                    ) {
                        // Mirrored, because this is the one picture that behaves like
                        // a mirror: you move left and the reflection moves left.
                        CallVideo(
                            track = CallEngine.localVideo,
                            modifier = Modifier.fillMaxSize(),
                            mirror = true
                        )
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = d.gapMd)
                                .size(d.scaled(38))
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.45f))
                                .clickable { CallEngine.flipCamera() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                IqIcons.FlipCamera, "Switch camera",
                                Modifier.size(d.iconSm), tint = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            /*
             * The end-call button is taller than the bar and shares a centreline
             * with the small buttons, so the pill has to bulge up around it.
             * `bulge` is the strip above the flat edge that the hump lives in.
             */
            val ctrl = d.callControl
            val endBtn = d.callEnd
            val padTop = d.scaled(10)
            val bulge = d.scaled(11)
            val pillShape = HumpedBarShape(
                humpWidth = d.scaled(92),
                humpHeight = bulge
            )

            Row(
                Modifier
                    .padding(start = d.gapMd, end = d.gapMd, bottom = d.gapXl)
                    .fillMaxWidth()
                    .widthIn(max = d.contentMaxWidth)
                    .clip(pillShape)
                    .background(Color.Black.copy(alpha = 0.62f), pillShape)
                    .padding(
                        start = d.gapSm, end = d.gapSm,
                        top = bulge + padTop, bottom = padTop
                    ),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Top
            ) {
                VideoControl(
                    icon = if (!CallEngine.micOn) IqIcons.MicOff else IqIcons.Mic,
                    label = "Mute",
                    enabled = CallEngine.micOn,
                    onClick = { CallEngine.toggleMic() },
                    modifier = Modifier.weight(1f)
                )
                VideoControl(
                    icon = CallAudio.route.icon,
                    label = "Speaker",
                    enabled = CallAudio.route != CallAudio.Route.EARPIECE,
                    onClick = { CallEngine.cycleRoute() },
                    modifier = Modifier.weight(1f)
                )
                // Height matches the small buttons so it does not stretch the bar;
                // requiredSize lets the larger circle overflow into the hump.
                Box(
                    Modifier
                        .weight(1.3f)
                        .height(ctrl),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .requiredSize(endBtn)
                            .clip(CircleShape)
                            .background(Crimson)
                            .clickable(onClick = onEnd),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            IqIcons.CallEnd, "End call",
                            Modifier
                                .size(d.iconLg)
                                .rotate(133f),
                            tint = Color.White
                        )
                    }
                }
                // Outline when the camera is off, the way Mute carries MicOff.
                // `enabled` only dims the icon, so a greyed camera over an
                // unchanged icon was the one state that read as "you cannot press
                // this" - which was exactly what it was not.
                VideoControl(
                    icon = if (CallEngine.cameraOn) IqIcons.Videocam
                    else IqIcons.VideocamOutline,
                    label = "Video",
                    enabled = CallEngine.cameraOn,
                    onClick = toggleCamera,
                    modifier = Modifier.weight(1f)
                )
                VideoControl(
                    icon = IqIcons.FlipCamera,
                    label = "Flip",
                    onClick = {},
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun VideoControl(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val d = IqTheme.dimens
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(d.callControl)
                .clip(CircleShape)
                .background(Color.White)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon, label,
                Modifier.size(d.iconSm),
                tint = if (enabled) Color.Black else InkFaint
            )
        }
        Spacer(Modifier.height(d.gapSm))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Normal,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

internal fun formatDuration(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%02d:%02d".format(m, s)
}
