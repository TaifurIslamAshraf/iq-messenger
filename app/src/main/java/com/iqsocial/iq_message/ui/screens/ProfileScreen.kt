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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iqsocial.iq_message.data.Person
import com.iqsocial.iq_message.data.SampleData
import com.iqsocial.iq_message.data.auth.SessionStore
import com.iqsocial.iq_message.data.profile.MeRepository
import com.iqsocial.iq_message.data.remote.MessengerProfile
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

    // The real profile, which only /v1/me can supply. Fetched once per launch and
    // cached, so switching tabs does not re-request it.
    val context = LocalContext.current
    val session = remember { SessionStore(context) }
    val profile = MeRepository.profile
    LaunchedEffect(Unit) {
        if (!MeRepository.loaded) MeRepository.load(session.token)
    }

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
                IdentityCard(me, profile)
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
                    // Read-only: /v1/me serves these but has no write verb, so the
                    // row reports them rather than pretending to change them.
                    // One line, sharing the row with the "Protected" chip, so the
                    // common case of both settings matching is collapsed rather than
                    // spelled out and then truncated.
                    subtitle = profile?.let {
                        if (it.whoCanMessage == it.whoCanCall) {
                            "Messages & calls: ${readable(it.whoCanMessage)}"
                        } else {
                            "Msg: ${readable(it.whoCanMessage)} · " +
                                "Calls: ${readable(it.whoCanCall)}"
                        }
                    } ?: "Security and visibility"
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

/**
 * Counts in the style the mockup uses: `9`, `1.2K`, `12.6K`, `3.4M`.
 *
 * Done with integer maths rather than `String.format`, which would render the
 * decimal separator in the device's locale and turn `12.6K` into `12,6K`.
 */
private fun compactCount(value: Int): String = when {
    value < 1_000 -> value.toString()
    value < 1_000_000 -> abbreviate(value, 1_000, "K")
    else -> abbreviate(value, 1_000_000, "M")
}

private fun abbreviate(value: Int, unit: Int, suffix: String): String {
    val whole = value / unit
    val tenth = (value % unit) * 10 / unit
    return if (tenth == 0) "$whole$suffix" else "$whole.$tenth$suffix"
}

/** Turns an API enum such as `my_contacts` into something readable. */
private fun readable(value: String): String =
    value.replace('_', ' ').trim().ifEmpty { "—" }

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
private fun IdentityCard(sample: Person, profile: MessengerProfile?) {
    val d = IqTheme.dimens
    val editSize = d.iconButtonLg

    // Real profile where there is one, mockup data until then. Building a Person
    // from it keeps the avatar's generated gradient and initials tied to the real
    // identity rather than to "Rafi Hasan".
    val me = remember(profile, sample) {
        profile?.let {
            Person(
                id = it.uid.ifBlank { sample.id },
                name = it.fullname.ifBlank { sample.name },
                handle = it.phoneNumber.ifBlank { sample.handle },
                online = it.isOnline
            )
        } ?: sample
    }
    // A real account with an empty bio must not inherit the mockup's line — that
    // would show the user a bio they never wrote. The sample copy belongs to sample
    // mode only; a loaded profile with no bio invites one instead.
    val bio = when {
        profile == null -> "Creating smarter connections."
        profile.bio.isNotBlank() -> profile.bio
        else -> null
    }
    // The tick was hardcoded. With a real profile it has to follow `verified`;
    // without one the mockup's look stands, since the whole card is sample data.
    val verified = profile?.isVerified ?: true
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
                        // Respects the user's own "show active status" setting.
                        showPresence = profile?.showActiveStatus ?: true
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
                            if (verified) {
                                Spacer(Modifier.width(d.gapXs))
                                Icon(
                                    IqIcons.Verified, "Verified",
                                    Modifier.size(d.iconSm), tint = Crimson
                                )
                            }
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
                            bio ?: "Add a bio",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (bio != null) Ink else InkFaint,
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
                // Real counts once /v1/me has answered; the mockup's numbers until
                // then, so the card never shows a row of zeroes while loading.
                StatCell(
                    profile?.let { compactCount(it.totalPost) } ?: "128",
                    "Posts", Modifier.weight(1f)
                )
                StatDivider()
                StatCell(
                    profile?.let { compactCount(it.followers) } ?: "12.6K",
                    "Followers", Modifier.weight(1f)
                )
                StatDivider()
                StatCell(
                    profile?.let { compactCount(it.following) } ?: "584",
                    "Following", Modifier.weight(1f)
                )
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
