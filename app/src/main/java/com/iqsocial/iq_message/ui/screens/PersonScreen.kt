package com.iqsocial.iq_message.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.iqsocial.iq_message.data.Person
import com.iqsocial.iq_message.data.auth.SessionStore
import com.iqsocial.iq_message.data.media.RemoteImage
import com.iqsocial.iq_message.data.remote.ApiResult
import com.iqsocial.iq_message.data.remote.PeopleApi
import com.iqsocial.iq_message.data.remote.PublicProfile
import com.iqsocial.iq_message.ui.components.Avatar
import com.iqsocial.iq_message.ui.components.CircleIconButton
import com.iqsocial.iq_message.ui.components.HairlineDivider
import com.iqsocial.iq_message.ui.components.SecureScreen
import com.iqsocial.iq_message.ui.components.peerAllowsScreenshot
import com.iqsocial.iq_message.ui.icons.IqIcons
import com.iqsocial.iq_message.ui.theme.BrandSunset
import com.iqsocial.iq_message.ui.theme.CanvasGrey
import com.iqsocial.iq_message.ui.theme.Crimson
import com.iqsocial.iq_message.ui.theme.Hairline
import com.iqsocial.iq_message.ui.theme.Ink
import com.iqsocial.iq_message.ui.theme.InkFaint
import com.iqsocial.iq_message.ui.theme.InkMuted
import com.iqsocial.iq_message.ui.theme.IqTheme
import com.iqsocial.iq_message.ui.theme.SystemBarIcons

/**
 * Somebody else's profile, from the chat they are in.
 *
 * A screen rather than the sheet it used to be. Pressing a face is a request to
 * see who this is, and half a screen of it under the conversation answers that
 * about as well as a business card held at arm's length - there is a cover photo,
 * a bio and three numbers to show, and they want the room.
 *
 * `GET /v1/people/{uid}` is the whole of it, and what it leaves out is as
 * deliberate as what it returns: no phone number, no birthday, no privacy choices.
 * The screen therefore has nothing of theirs to leak, whatever it draws.
 *
 * The loaded profile also stands in for what the chat already knew: a name and a
 * picture arrive with the conversation, so the screen is drawn from those the
 * moment it opens and fills in when the request lands. A spinner over a name
 * somebody has just tapped would be a worse answer than the name.
 */
@Composable
fun PersonScreen(
    uid: String,
    /** What the chat already knows, so the screen is never blank to begin with. */
    knownName: String = "",
    knownPhoto: String = "",
    onBack: () -> Unit,
    onAudioCall: (String) -> Unit = {},
    onVideoCall: (String) -> Unit = {}
) {
    SystemBarIcons(darkIcons = false)
    val d = IqTheme.dimens
    val context = androidx.compose.ui.platform.LocalContext.current
    val token = remember { SessionStore(context).token }

    var profile by remember(uid) { mutableStateOf<PublicProfile?>(null) }
    var failed by remember(uid) { mutableStateOf<String?>(null) }

    LaunchedEffect(uid, token) {
        val auth = token ?: return@LaunchedEffect
        if (uid.isBlank()) return@LaunchedEffect
        when (val result = PeopleApi.profile(auth, uid)) {
            is ApiResult.Success -> profile = result.data
            is ApiResult.Failure -> failed = result.message
        }
    }

    // Their setting, not this account's: the same rule as the chat and the call.
    SecureScreen(!peerAllowsScreenshot(uid))

    val name = profile?.fullname?.takeIf { it.isNotBlank() }
        ?: knownName.takeIf { it.isNotBlank() }
        ?: "Profile"
    val photo = profile?.profilePicture?.takeIf { it.isNotBlank() } ?: knownPhoto
    val face = remember(uid, name, photo) {
        Person(id = uid.ifBlank { name }, name = name, handle = "", photo = photo)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(CanvasGrey)
            .verticalScroll(rememberScrollState())
    ) {
        Box {
            Cover(url = profile?.coverPicture.orEmpty())

            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = d.gapSm, vertical = d.gapSm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleIconButton(
                    icon = IqIcons.ArrowBack,
                    contentDescription = "Back",
                    onClick = onBack,
                    background = Color.Black.copy(alpha = 0.28f),
                    tint = Color.White
                )
            }
        }

        // Lifted over the cover's lower edge, as a profile picture sits on a
        // banner everywhere else.
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = d.gutterTight)
                .offsetUpBy(d.avatarXl / 2),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Avatar(
                person = face,
                size = d.avatarXl,
                ringWidth = d.scaled(4),
                ringColor = Color.White,
                ringGap = 0.dp
            )
            Spacer(Modifier.height(d.gapSm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Ink,
                    maxLines = 2,
                    textAlign = TextAlign.Center
                )
                if (profile?.isVerified == true) {
                    Spacer(Modifier.width(d.gapXs))
                    Icon(IqIcons.Verified, "Verified", Modifier.size(d.icon), tint = Crimson)
                }
            }

            profile?.bio?.takeIf { it.isNotBlank() }?.let { bio ->
                Spacer(Modifier.height(d.gapSm))
                Text(
                    bio,
                    style = MaterialTheme.typography.bodyLarge,
                    color = InkMuted,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(d.gapLg))
            Counts(profile)

            Spacer(Modifier.height(d.gapLg))
            Row(horizontalArrangement = Arrangement.spacedBy(d.gapMd)) {
                Action(IqIcons.Phone, "Audio") { onAudioCall(uid) }
                Action(IqIcons.Videocam, "Video") { onVideoCall(uid) }
            }

            when {
                failed != null && profile == null -> {
                    Spacer(Modifier.height(d.gapLg))
                    Text(
                        // 404 here is not an error to explain away: it is an
                        // unknown id, a banned account, or somebody who has
                        // blocked you, and the server deliberately does not say
                        // which. Neither does this.
                        "This profile is not available.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkFaint
                    )
                }

                profile == null -> {
                    Spacer(Modifier.height(d.gapLg))
                    CircularProgressIndicator(
                        color = Crimson,
                        modifier = Modifier.size(d.icon)
                    )
                }
            }

            Spacer(Modifier.height(d.gapXxl))
        }
    }
}

/**
 * The banner: theirs, or the app's own gradient when they have none.
 *
 * 16:9, the shape a cover is cropped to when it is chosen - so what was framed in
 * the crop screen is exactly what is drawn here.
 */
@Composable
private fun Cover(url: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(BrandSunset)
    ) {
        if (url.isBlank()) return@Box
        var banner by remember(url) {
            mutableStateOf(RemoteImage.peek(url, RemoteImage.PREVIEW_PX))
        }
        LaunchedEffect(url) {
            if (banner == null) banner = RemoteImage.load(url, targetPx = RemoteImage.PREVIEW_PX)
        }
        banner?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** Posts, followers, following - or dashes until the server has said. */
@Composable
private fun Counts(profile: PublicProfile?) {
    val d = IqTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(d.radiusXl))
            .background(Color.White)
            .border(1.dp, Hairline, RoundedCornerShape(d.radiusXl))
            .padding(vertical = d.gapMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Count(profile?.totalPost, "Posts", Modifier.weight(1f))
        Divider()
        Count(profile?.followers, "Followers", Modifier.weight(1f))
        Divider()
        Count(profile?.following, "Following", Modifier.weight(1f))
    }
}

@Composable
private fun Count(value: Int?, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            // A dash while it is unknown. A zero would be a claim.
            value?.let(::compact) ?: "-",
            style = MaterialTheme.typography.headlineSmall,
            color = Ink,
            fontWeight = FontWeight.Bold
        )
        Text(label, style = MaterialTheme.typography.bodySmall, color = InkMuted)
    }
}

@Composable
private fun Divider() {
    val d = IqTheme.dimens
    Box(
        Modifier
            .height(d.scaled(28))
            .width(1.dp)
            .background(Hairline)
    )
}

@Composable
private fun Action(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val d = IqTheme.dimens
    Column(
        Modifier
            .clip(RoundedCornerShape(d.radiusLg))
            .clickable(onClick = onClick)
            .padding(horizontal = d.gapLg, vertical = d.gapSm),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(d.iconButtonLg)
                .clip(CircleShape)
                .background(Color.White)
                .border(1.dp, Hairline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(d.icon), tint = Crimson)
        }
        Spacer(Modifier.height(d.gapXs))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = InkMuted)
    }
}

/** `9`, `1.2K`, `12.6K`, `3.4M` - integer maths, so the separator is not localised. */
private fun compact(value: Int): String = when {
    value < 1_000 -> value.toString()
    value < 1_000_000 -> short(value, 1_000, "K")
    else -> short(value, 1_000_000, "M")
}

private fun short(value: Int, unit: Int, suffix: String): String {
    val whole = value / unit
    val tenth = (value % unit) * 10 / unit
    return if (tenth == 0) "$whole$suffix" else "$whole.$tenth$suffix"
}

/**
 * Pulls content up over what is above it.
 *
 * A layout modifier rather than a negative padding, which Compose will not take:
 * the height is reported as it would be without the shift, so the column below
 * closes the gap instead of leaving a hole where the avatar used to be.
 */
private fun Modifier.offsetUpBy(by: androidx.compose.ui.unit.Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val shift = by.roundToPx()
    layout(placeable.width, (placeable.height - shift).coerceAtLeast(0)) {
        placeable.place(0, -shift)
    }
}
