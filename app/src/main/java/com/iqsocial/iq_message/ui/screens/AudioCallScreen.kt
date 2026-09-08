package com.iqsocial.iq_message.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iqsocial.iq_message.data.Person
import com.iqsocial.iq_message.data.SampleData
import com.iqsocial.iq_message.ui.components.Avatar
import com.iqsocial.iq_message.ui.components.EdgeCutShape
import com.iqsocial.iq_message.ui.components.CircleIconButton
import com.iqsocial.iq_message.ui.icons.IqIcons
import com.iqsocial.iq_message.ui.theme.CallBlack
import com.iqsocial.iq_message.ui.theme.CallPanel
import com.iqsocial.iq_message.ui.theme.Crimson
import com.iqsocial.iq_message.ui.theme.CrimsonBright
import com.iqsocial.iq_message.ui.theme.IqTheme
import com.iqsocial.iq_message.ui.theme.OnlineGreen
import com.iqsocial.iq_message.ui.theme.SystemBarIcons
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AudioCallScreen(
    personId: String,
    onMinimize: () -> Unit,
    onEnd: () -> Unit
) {
    SystemBarIcons(darkIcons = false)
    val d = IqTheme.dimens
    val person = remember(personId) { SampleData.person(personId) }
    var seconds by remember { mutableIntStateOf(138) }
    var muted by remember { mutableStateOf(true) }
    var speakerOn by remember { mutableStateOf(true) }

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
        // crimson glow bleeding in from the top-right, as in the mockup
        Canvas(Modifier.fillMaxSize()) {
            val centre = Offset(size.width * 1.02f, size.height * 0.12f)
            val r = size.width * 0.72f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Crimson.copy(alpha = 0.55f), Color.Transparent),
                    center = centre, radius = r
                ),
                radius = r, center = centre
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = d.gapMd, vertical = d.gapSm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleIconButton(
                    icon = IqIcons.ChevronDown,
                    contentDescription = "Minimize",
                    onClick = onMinimize,
                    size = d.iconButtonLg,
                    iconSize = d.iconLg,
                    background = Color.White.copy(alpha = 0.10f),
                    tint = Color.White
                )
                Spacer(Modifier.width(d.gapSm))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Audio Call",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        maxLines = 1
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            IqIcons.Shield, null,
                            Modifier.size(d.iconXs), tint = Color.White.copy(alpha = 0.6f)
                        )
                        Spacer(Modifier.width(d.gapXs))
                        Text(
                            "Encrypted",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.68f),
                            maxLines = 1
                        )
                    }
                }
                CircleIconButton(
                    icon = IqIcons.MoreVert,
                    contentDescription = "More options",
                    onClick = {},
                    tint = Color.White
                )
            }

            Spacer(Modifier.weight(0.5f))

            /* -- caller ---------------------------------------------------- */
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = d.gutter)
            ) {
                // Side-by-side only when there is genuinely room for it; otherwise
                // the avatar and name stack, which is what saves narrow screens.
                val sideBySide = maxWidth >= 340.dp
                val ring = (if (sideBySide) maxWidth * 0.48f else maxWidth * 0.60f)
                    .coerceAtMost(240.dp)
                val avatar = ring * 0.76f

                if (sideBySide) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(contentAlignment = Alignment.Center) {
                            ConnectionArc(Modifier.size(ring))
                            Avatar(person = person, size = avatar)
                        }
                        Spacer(Modifier.width(d.gapMd))
                        CallerText(person, seconds, Modifier.weight(1f))
                    }
                } else {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            ConnectionArc(Modifier.size(ring))
                            Avatar(person = person, size = avatar)
                        }
                        Spacer(Modifier.height(d.gapLg))
                        CallerText(
                            person, seconds,
                            Modifier.fillMaxWidth(),
                            centered = true
                        )
                    }
                }
            }

            Spacer(Modifier.height(d.gapXl))
            SponsoredCard(Modifier.padding(horizontal = d.gutter))

            Spacer(Modifier.weight(1f))

            /*
             * The end-call button straddles the panel's top edge, so the panel is
             * scooped out beneath it rather than simply sitting behind it.
             */
            val endBtn = d.callEnd
            val notchRadius = endBtn / 2 + d.scaled(6)
            val endOverhang = endBtn / 2 - d.scaled(4)   // button centre sits 4dp inside
            val panelShape = EdgeCutShape(
                corner = d.radiusXl,
                circleRadius = notchRadius,
                circleCenterY = d.scaled(4),
                union = false
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = d.gutter, end = d.gutter, bottom = d.gapXl)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = d.contentMaxWidth)
                        .padding(top = endOverhang)
                        .clip(panelShape)
                        .background(CallPanel.copy(alpha = 0.92f), panelShape)
                        .border(1.dp, Color.White.copy(alpha = 0.08f), panelShape)
                        .padding(top = notchRadius + d.gapMd, bottom = d.gapMd),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = d.gapMd),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        AudioControl(
                            icon = if (muted) IqIcons.MicOff else IqIcons.Mic,
                            label = "Mute",
                            active = false,
                            onClick = { muted = !muted },
                            modifier = Modifier.weight(1f)
                        )
                        AudioControl(
                            icon = IqIcons.Speaker,
                            label = "Speaker",
                            active = speakerOn,
                            onClick = { speakerOn = !speakerOn },
                            modifier = Modifier.weight(1f)
                        )
                        AudioControl(
                            icon = IqIcons.PersonAdd,
                            label = "Add Call",
                            active = false,
                            onClick = {},
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(d.gapMd))
                    Text(
                        "End Call",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier
                            .clip(RoundedCornerShape(d.radiusMd))
                            .clickable(onClick = onEnd)
                            .padding(horizontal = d.gapLg, vertical = d.gapXs)
                    )
                }

                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .size(endBtn)
                        .clip(CircleShape)
                        .background(Crimson)
                        .clickable(onClick = onEnd),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        IqIcons.CallEnd, "End call",
                        Modifier
                            .size(d.iconXl)
                            .rotate(133f),
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun CallerText(
    person: Person,
    seconds: Int,
    modifier: Modifier = Modifier,
    centered: Boolean = false
) {
    val d = IqTheme.dimens
    val align = if (centered) Alignment.CenterHorizontally else Alignment.Start
    Column(modifier, horizontalAlignment = align) {
        Text(
            person.name,
            style = MaterialTheme.typography.displaySmall,
            color = Color.White,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(d.gapSm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(d.scaled(11))
                    .clip(CircleShape)
                    .background(OnlineGreen)
            )
            Spacer(Modifier.width(d.gapSm))
            Text(
                "Connected",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.75f),
                fontWeight = FontWeight.Normal,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(d.gapSm))
        Text(
            formatDuration(seconds),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            maxLines = 1
        )
    }
}

/** Rotating progress arc plus tick marks around the caller's avatar. */
@Composable
private fun ConnectionArc(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "arc")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "sweep"
    )
    Canvas(modifier) {
        val strokeW = size.minDimension * 0.017f
        val inset = strokeW * 2
        val diameter = size.minDimension - inset * 2
        val radius = diameter / 2
        val centre = Offset(size.width / 2, size.height / 2)

        drawArc(
            color = Crimson,
            startAngle = angle - 150f,
            sweepAngle = 190f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = androidx.compose.ui.geometry.Size(diameter, diameter),
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )
        val headRad = Math.toRadians((angle + 40f).toDouble())
        drawCircle(
            color = CrimsonBright,
            radius = strokeW * 2f,
            center = Offset(
                centre.x + radius * cos(headRad).toFloat(),
                centre.y + radius * sin(headRad).toFloat()
            )
        )
        repeat(18) { i ->
            val a = Math.toRadians((angle + 150f + i * 3.6f).toDouble())
            val inner = radius - size.minDimension * 0.075f
            drawLine(
                color = CrimsonBright.copy(alpha = 0.85f),
                start = Offset(
                    centre.x + inner * cos(a).toFloat(),
                    centre.y + inner * sin(a).toFloat()
                ),
                end = Offset(
                    centre.x + radius * cos(a).toFloat(),
                    centre.y + radius * sin(a).toFloat()
                ),
                strokeWidth = strokeW * 0.7f,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun SponsoredCard(modifier: Modifier = Modifier) {
    val d = IqTheme.dimens
    Row(
        modifier
            .fillMaxWidth()
            .widthIn(max = d.contentMaxWidth)
            .clip(RoundedCornerShape(d.radiusLg))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(d.radiusLg))
            .padding(d.gapMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(d.scaled(74))
                .clip(RoundedCornerShape(d.radiusMd))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF3A0A22), Color(0xFF8E1246), Color(0xFF2A0716))
                    )
                )
        )
        Spacer(Modifier.width(d.gapMd))
        Column(Modifier.weight(1f)) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(d.scaled(6)))
                    .background(Color.White.copy(alpha = 0.16f))
                    .padding(horizontal = d.gapSm, vertical = d.gapXs / 2)
            ) {
                Text(
                    "Ad",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
            Spacer(Modifier.height(d.gapXs))
            Text(
                "Explore more",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "Recommended",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1
            )
        }
        Spacer(Modifier.width(d.gapSm))
        Box(
            Modifier
                .clip(CircleShape)
                .background(Crimson)
                .clickable { }
                .padding(horizontal = d.gapLg, vertical = d.gapSm + 2.dp)
        ) {
            Text(
                "View",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AudioControl(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val d = IqTheme.dimens
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(d.callControlLg)
                .clip(CircleShape)
                .background(
                    if (active) Crimson.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.07f)
                )
                .border(
                    1.dp,
                    if (active) Crimson.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.14f),
                    CircleShape
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon, label,
                Modifier.size(d.iconLg),
                tint = if (active) CrimsonBright else Color.White
            )
        }
        Spacer(Modifier.height(d.gapSm))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
