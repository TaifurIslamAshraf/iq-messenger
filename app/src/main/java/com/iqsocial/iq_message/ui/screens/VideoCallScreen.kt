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
import com.iqsocial.iq_message.data.SampleData
import com.iqsocial.iq_message.ui.components.HumpedBarShape
import com.iqsocial.iq_message.ui.components.avatarBrush
import com.iqsocial.iq_message.ui.icons.IqIcons
import com.iqsocial.iq_message.ui.theme.CallBlack
import com.iqsocial.iq_message.ui.theme.Crimson
import com.iqsocial.iq_message.ui.theme.InkFaint
import com.iqsocial.iq_message.ui.theme.IqTheme
import com.iqsocial.iq_message.ui.theme.SystemBarIcons
import kotlinx.coroutines.delay

@Composable
fun VideoCallScreen(
    personId: String,
    onMinimize: () -> Unit,
    onEnd: () -> Unit
) {
    SystemBarIcons(darkIcons = false)
    val d = IqTheme.dimens
    val person = remember(personId) { SampleData.person(personId) }
    val me = SampleData.me

    var seconds by remember { mutableIntStateOf(18) }
    var muted by remember { mutableStateOf(true) }
    var speakerOn by remember { mutableStateOf(true) }
    var videoOn by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            seconds++
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(CallBlack)
    ) {
        // Remote feed stand-in.
        Box(
            Modifier
                .fillMaxSize()
                .background(avatarBrush(person.seed))
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.55f),
                        0.35f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.45f)
                    )
                )
        )

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
                        Text(
                            formatDuration(seconds),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Normal,
                            maxLines = 1
                        )
                        Spacer(Modifier.height(d.gapXs))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                IqIcons.Shield, null,
                                Modifier.size(d.iconXs), tint = Color.White.copy(alpha = 0.75f)
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
                    Spacer(Modifier.width(d.gapSm))
                    Box(
                        Modifier
                            .width(selfWidth)
                            .aspectRatio(0.67f)
                            .clip(RoundedCornerShape(d.radiusLg))
                            .background(avatarBrush(me.seed))
                    ) {
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = d.gapMd)
                                .size(d.scaled(38))
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                IqIcons.CameraOutline, "Switch view",
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
                    icon = if (muted) IqIcons.MicOff else IqIcons.Mic,
                    label = "Mute",
                    enabled = !muted,
                    onClick = { muted = !muted },
                    modifier = Modifier.weight(1f)
                )
                VideoControl(
                    icon = IqIcons.Speaker,
                    label = "Speaker",
                    enabled = speakerOn,
                    onClick = { speakerOn = !speakerOn },
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
                VideoControl(
                    icon = IqIcons.Videocam,
                    label = "Video",
                    enabled = videoOn,
                    onClick = { videoOn = !videoOn },
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
