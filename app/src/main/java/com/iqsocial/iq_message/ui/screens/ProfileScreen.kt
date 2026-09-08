package com.iqsocial.iq_message.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iqsocial.iq_message.data.SampleData
import com.iqsocial.iq_message.ui.components.Avatar
import com.iqsocial.iq_message.ui.components.CircleIconButton
import com.iqsocial.iq_message.ui.components.HairlineDivider
import com.iqsocial.iq_message.ui.components.drawHeaderWave
import com.iqsocial.iq_message.ui.icons.IqIcons
import com.iqsocial.iq_message.ui.theme.BrandSunset
import com.iqsocial.iq_message.ui.theme.CanvasGrey
import com.iqsocial.iq_message.ui.theme.Crimson
import com.iqsocial.iq_message.ui.theme.CrimsonTint
import com.iqsocial.iq_message.ui.theme.Hairline
import com.iqsocial.iq_message.ui.theme.Ink
import com.iqsocial.iq_message.ui.theme.InkFaint
import com.iqsocial.iq_message.ui.theme.InkMuted
import com.iqsocial.iq_message.ui.theme.IqTheme
import com.iqsocial.iq_message.ui.theme.ProtectedGreen
import com.iqsocial.iq_message.ui.theme.SystemBarIcons

@Composable
fun ProfileScreen(onBack: () -> Unit, onLogOut: () -> Unit) {
    SystemBarIcons(darkIcons = false)
    val d = IqTheme.dimens
    val me = SampleData.me
    var notificationsOn by remember { mutableStateOf(true) }
    val headerHeight = d.scaled(248)

    Column(
        Modifier
            .fillMaxSize()
            .background(CanvasGrey)
            .verticalScroll(rememberScrollState())
    ) {
        Box {
            /* -- gradient header with the wave cut-out ---------------------- */
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(headerHeight)
                    .background(BrandSunset)
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawHeaderWave(Color.White, alpha = 0.16f)
                    translate(0f, size.height * 0.10f) {
                        drawHeaderWave(CanvasGrey)
                    }
                }
            }

            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = d.gapMd, vertical = d.gapSm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircleIconButton(
                        icon = IqIcons.ArrowBack,
                        contentDescription = "Back",
                        onClick = onBack,
                        background = Color.White.copy(alpha = 0.22f),
                        tint = Color.White
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Profile",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        maxLines = 1
                    )
                    Spacer(Modifier.weight(1f))
                    CircleIconButton(
                        icon = IqIcons.QrCode,
                        contentDescription = "Your QR code",
                        onClick = {},
                        background = Color.White.copy(alpha = 0.22f),
                        tint = Color.White
                    )
                    CircleIconButton(
                        icon = IqIcons.MoreVert,
                        contentDescription = "More options",
                        onClick = {},
                        background = Color.White.copy(alpha = 0.22f),
                        tint = Color.White
                    )
                }

                Spacer(Modifier.height(d.gapLg))
                IdentityCard(me)
            }
        }

        Spacer(Modifier.height(d.gapLg))

        /* -- settings ------------------------------------------------------ */
        PageWidth {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Settings",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Ink,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                CircleIconButton(
                    icon = IqIcons.Search,
                    contentDescription = "Search settings",
                    onClick = {},
                    size = d.iconButtonLg,
                    background = Color.White,
                    borderColor = Hairline
                )
            }

            Spacer(Modifier.height(d.gapMd))

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(d.radiusXl))
                    .background(Color(0xFFFBFAFA))
                    .border(1.dp, Hairline, RoundedCornerShape(d.radiusXl))
            ) {
                SettingRow(
                    icon = IqIcons.PersonOutline,
                    iconTint = CrimsonTint,
                    title = "Account",
                    subtitle = "Profile & verification"
                ) { Icon(IqIcons.ChevronRight, null, Modifier.size(d.icon), tint = InkFaint) }

                SettingDivider()
                SettingRow(
                    icon = IqIcons.ShieldOutline,
                    iconTint = Color(0xFFE3F5E9),
                    title = "Privacy",
                    subtitle = "Security and visibility"
                ) {
                    Text(
                        "Protected",
                        style = MaterialTheme.typography.titleMedium,
                        color = ProtectedGreen,
                        maxLines = 1
                    )
                    Spacer(Modifier.width(d.gapSm))
                    Icon(IqIcons.ChevronRight, null, Modifier.size(d.icon), tint = InkFaint)
                }

                SettingDivider()
                SettingRow(
                    icon = IqIcons.Bell,
                    iconTint = CrimsonTint,
                    title = "Notifications",
                    subtitle = "Messages, calls & updates"
                ) {
                    Switch(
                        checked = notificationsOn,
                        onCheckedChange = { notificationsOn = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Crimson,
                            checkedBorderColor = Crimson,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = InkFaint,
                            uncheckedBorderColor = InkFaint
                        )
                    )
                }

                SettingDivider()
                SettingRow(
                    icon = IqIcons.Storage,
                    iconTint = CrimsonTint,
                    title = "Data & Storage",
                    subtitle = "3.2 GB used"
                ) {
                    StorageRing(progress = 0.32f)
                    Spacer(Modifier.width(d.gapSm))
                    Icon(IqIcons.ChevronRight, null, Modifier.size(d.icon), tint = InkFaint)
                }

                SettingDivider()
                SettingRow(
                    icon = IqIcons.HelpCircle,
                    iconTint = CrimsonTint,
                    title = "Help & Support",
                    subtitle = "Get assistance"
                ) { Icon(IqIcons.ChevronRight, null, Modifier.size(d.icon), tint = InkFaint) }
            }

            Spacer(Modifier.height(d.gapLg))

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(d.radiusMd))
                    .clickable(onClick = onLogOut)
                    .padding(vertical = d.gapMd),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(IqIcons.Logout, null, Modifier.size(d.icon), tint = Crimson)
                Spacer(Modifier.width(d.gapSm))
                Text(
                    "Log out",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Normal,
                    color = Crimson
                )
            }

            Spacer(Modifier.height(d.gapXl))
        }
    }
}

/** Page container: gutters, capped width on tablets. */
@Composable
private fun PageWidth(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val d = IqTheme.dimens
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .fillMaxWidth()
                .widthIn(max = d.contentMaxWidth)
                .padding(horizontal = d.gutterTight),
            content = content
        )
    }
}

@Composable
private fun IdentityCard(me: com.iqsocial.iq_message.data.Person) {
    val d = IqTheme.dimens
    val editSize = d.iconButtonLg
    PageWidth {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(d.radiusXl))
                .background(Color(0xFFFBFAFA))
                .border(1.dp, Hairline, RoundedCornerShape(d.radiusXl))
        ) {
            Box(Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(d.gapLg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(
                        person = me,
                        size = d.avatarXl,
                        ringWidth = d.scaled(4),
                        ringColor = Color.White,
                        ringGap = 0.dp,
                        showPresence = true
                    )
                    Spacer(Modifier.width(d.gapMd))
                    Column(
                        Modifier
                            .weight(1f)
                            // Just enough clearance for the floating edit button;
                            // reserving its full width forced the bio onto two lines.
                            .padding(end = d.gapXl)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                me.name,
                                style = MaterialTheme.typography.headlineSmall,
                                color = Ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(Modifier.width(d.gapXs))
                            Icon(
                                IqIcons.Verified, "Verified",
                                Modifier.size(d.iconSm), tint = Crimson
                            )
                        }
                        Text(
                            me.handle,
                            style = MaterialTheme.typography.bodyLarge,
                            color = InkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(d.gapXs))
                        Text(
                            "Creating smarter connections.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Floats over the card so it costs the text column no width.
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(d.gapMd)
                        .size(editSize)
                        .clip(CircleShape)
                        .background(Ink)
                        .clickable { },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(IqIcons.Edit, "Edit profile", Modifier.size(d.iconSm), tint = Color.White)
                }
            }

            HairlineDivider()

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = d.gapLg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatCell("128", "Posts", Modifier.weight(1f))
                StatDivider()
                StatCell("12.6K", "Followers", Modifier.weight(1f))
                StatDivider()
                StatCell("584", "Following", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatCell(value: String, label: String, modifier: Modifier = Modifier) {
    val d = IqTheme.dimens
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            color = Ink,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Spacer(Modifier.height(d.gapXs / 2))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = InkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StatDivider() {
    val d = IqTheme.dimens
    Box(
        Modifier
            .width(1.dp)
            .height(d.scaled(36))
            .background(Hairline)
    )
}

@Composable
private fun SettingDivider() {
    val d = IqTheme.dimens
    HairlineDivider(
        Modifier.padding(start = d.scaled(50) + d.gapLg + d.gapMd, end = d.gapLg)
    )
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit
) {
    val d = IqTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { }
            .heightIn(min = 58.dp)
            .padding(horizontal = d.gapLg, vertical = d.gapSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(d.scaled(50))
                .clip(CircleShape)
                .background(iconTint),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(d.icon), tint = Ink)
        }
        Spacer(Modifier.width(d.gapMd))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(d.gapSm))
        trailing()
    }
}

@Composable
private fun StorageRing(progress: Float) {
    val d = IqTheme.dimens
    Canvas(Modifier.size(d.scaled(28))) {
        val stroke = size.minDimension * 0.12f
        val inset = stroke / 2
        val diameter = size.minDimension - stroke
        drawArc(
            color = Crimson.copy(alpha = 0.20f),
            startAngle = 0f, sweepAngle = 360f, useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(diameter, diameter),
            style = Stroke(stroke)
        )
        drawArc(
            color = Crimson,
            startAngle = -90f, sweepAngle = 360f * progress, useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(diameter, diameter),
            style = Stroke(stroke, cap = StrokeCap.Round)
        )
    }
}
