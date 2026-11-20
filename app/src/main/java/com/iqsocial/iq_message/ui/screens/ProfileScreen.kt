package com.iqsocial.iq_message.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.iqsocial.iq_message.data.Person
import com.iqsocial.iq_message.data.auth.SessionStore
import com.iqsocial.iq_message.data.profile.MeRepository
import com.iqsocial.iq_message.data.profile.SettingsRepository
import com.iqsocial.iq_message.data.media.RemoteImage
import com.iqsocial.iq_message.data.remote.MessengerProfile
import com.iqsocial.iq_message.data.remote.NotificationPreview
import com.iqsocial.iq_message.data.remote.ProfileImage
import kotlinx.coroutines.launch
import com.iqsocial.iq_message.ui.components.Avatar
import com.iqsocial.iq_message.ui.components.CircleIconButton
import com.iqsocial.iq_message.ui.components.HairlineDivider
import com.iqsocial.iq_message.ui.components.drawHeaderWave
import com.iqsocial.iq_message.ui.components.canTakeOverScreen
import com.iqsocial.iq_message.ui.components.openFullScreenSettings
import com.iqsocial.iq_message.ui.components.openPhoneCallSettings
import com.iqsocial.iq_message.ui.components.phoneBlocksLockScreen
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
fun ProfileScreen(
    onBack: () -> Unit,
    onLogOut: () -> Unit,
    /**
     * The three settings that change something, each its own screen.
     *
     * Pushed by whoever owns the navigation stack rather than opened here as a
     * sheet or a dialog: a settings page that edits an account belongs on the
     * stack, with the back gesture, a title and the whole height for a form - all
     * of which a popup gives up.
     */
    onEditProfile: () -> Unit = {},
    /** This account's QR code, which is how somebody adds it without a number. */
    onQrCode: () -> Unit = {},
    /** A picture has been chosen; it is cropped before anything is uploaded. */
    onCrop: (Uri, ProfileImage) -> Unit = { _, _ -> },
    onPrivacy: () -> Unit = {},
    onNotifications: () -> Unit = {}
) {
    SystemBarIcons(darkIcons = false)
    val d = IqTheme.dimens
    val headerHeight = d.scaled(248)
    val scope = rememberCoroutineScope()

    // The real profile, which only /v1/me can supply. Fetched once per launch and
    // cached, so switching tabs does not re-request it.
    val context = LocalContext.current
    val session = remember { SessionStore(context) }
    val profile = MeRepository.profile
    LaunchedEffect(Unit) {
        if (!MeRepository.loaded) MeRepository.load(session.token)
    }

    // The account's settings, read the same way and for the same reason: the screen
    // shows both, and a tab change should not re-request either.
    LaunchedEffect(Unit) {
        if (!SettingsRepository.loaded) SettingsRepository.load(session.token)
    }

    /**
     * Android's own photo picker: no storage permission, and the reader chooses
     * which single picture this app may see rather than granting it the gallery.
     *
     * Which of the two pictures it is for is held beside it, because the launcher
     * hands back only a `Uri` - by the time it answers, the sheet that started it
     * has closed and taken its kind with it.
     */
    var choosingFor by remember { mutableStateOf<ProfileImage?>(null) }
    val photos = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { picked: Uri? ->
        val kind = choosingFor
        choosingFor = null
        // To the crop screen, not to the upload. Every place that draws one of
        // these cuts it to shape anyway, and the reader should be the one who
        // decides which part survives that.
        if (picked != null && kind != null) onCrop(picked, kind)
    }

    /**
     * Straight to Android's picker, with nothing in between.
     *
     * There was a two-item sheet here first - "choose" or "remove" - which is a
     * popup asking which of two things a press on a camera button meant. The
     * camera button means change it; removing one is an edit, and lives with the
     * other edits in [EditProfileScreen].
     */
    fun choose(kind: ProfileImage) {
        choosingFor = kind
        photos.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
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
                // The account's own banner, under the wave rather than instead of
                // it: the wave is what joins the header to the page below, and a
                // photo with a straight edge across the screen would be a different
                // screen. Dimmed, because the white title and the two buttons sit
                // on top of it and a bright picture takes them with it.
                val cover = profile?.coverPicture.orEmpty()
                if (cover.isNotBlank()) {
                    var banner by remember(cover) {
                        mutableStateOf(RemoteImage.peek(cover, RemoteImage.PREVIEW_PX))
                    }
                    LaunchedEffect(cover) {
                        if (banner == null) {
                            banner = RemoteImage.load(cover, targetPx = RemoteImage.PREVIEW_PX)
                        }
                    }
                    banner?.let {
                        Image(
                            bitmap = it,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.22f))
                        )
                    }
                }
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
                        icon = IqIcons.Camera,
                        contentDescription = "Change your cover photo",
                        onClick = { choose(ProfileImage.COVER) },
                        background = Color.White.copy(alpha = 0.22f),
                        tint = Color.White
                    )
                    CircleIconButton(
                        icon = IqIcons.QrCode,
                        contentDescription = "Your QR code",
                        onClick = onQrCode,
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
                IdentityCard(
                    profile = profile,
                    onEdit = onEditProfile,
                    onPicture = { choose(ProfileImage.PICTURE) }
                )
            }
        }

        // Why the last write failed, where the write was started from.
        //
        // The pictures are changed from this page - the two camera buttons - and
        // until this line existed a refused upload did exactly nothing visible:
        // the spinner finished, the picture stayed as it was, and the reason sat
        // in a field nothing drew.
        MeRepository.error?.let { why ->
            Spacer(Modifier.height(d.gapMd))
            PageWidth {
                Text(
                    why,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Crimson
                )
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
                    subtitle = profile?.let {
                        listOfNotNull(
                            it.gender.takeIf(String::isNotBlank),
                            it.location.takeIf(String::isNotBlank)
                                ?: it.country.takeIf(String::isNotBlank)
                        ).joinToString(" \u00b7 ").ifBlank { "Name, bio, birthday and more" }
                    } ?: "Profile & verification",
                    onClick = onEditProfile
                ) { Icon(IqIcons.ChevronRight, null, Modifier.size(d.icon), tint = InkFaint) }

                SettingDivider()
                SettingRow(
                    icon = IqIcons.ShieldOutline,
                    iconTint = Color(0xFFE3F5E9),
                    title = "Privacy",
                    // Writable now: `/v1/me/privacy` takes both of these, so the row
                    // opens them rather than only reporting them.
                    // One line, sharing the row with the "Protected" chip, so the
                    // common case of both settings matching is collapsed rather than
                    // spelled out and then truncated.
                    onClick = onPrivacy,
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
                // What a notification is allowed to say, which `/v1/settings` owns.
                //
                // This was a switch wired to a `remember` - it moved, and nothing
                // happened. The setting behind it is not a boolean anyway: it has
                // three values, and a switch cannot show the middle one.
                SettingRow(
                    icon = IqIcons.Bell,
                    iconTint = CrimsonTint,
                    title = "Notifications",
                    subtitle = SettingsRepository.notificationPreview.summary,
                    onClick = onNotifications
                ) {
                    if (SettingsRepository.saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(d.icon),
                            color = Crimson,
                            strokeWidth = d.scaled(2)
                        )
                    } else {
                        Text(
                            SettingsRepository.notificationPreview.label,
                            style = MaterialTheme.typography.titleMedium,
                            color = InkMuted,
                            maxLines = 1
                        )
                        Spacer(Modifier.width(d.gapSm))
                        Icon(IqIcons.ChevronRight, null, Modifier.size(d.icon), tint = InkFaint)
                    }
                }

                SettingDivider()
                // A switch, and this one is wired to something.
                //
                // The setting behind it really is a boolean, unlike the three-valued
                // one above, and the server applies it the moment it answers: whoever
                // is watching this account's presence is sent an event. So what the
                // switch shows is what the people on the other end are seeing.
                SettingRow(
                    icon = if (SettingsRepository.showActiveStatus) IqIcons.Eye
                    else IqIcons.EyeOff,
                    iconTint = CrimsonTint,
                    title = "Active status",
                    subtitle = if (SettingsRepository.showActiveStatus) {
                        "Others can see when you're online"
                    } else {
                        "Your online and last seen are hidden"
                    }
                ) {
                    Switch(
                        checked = SettingsRepository.showActiveStatus,
                        // Held still while a write is in flight, so it cannot be
                        // flipped twice into a race whose loser is whatever the
                        // second reply happens to be.
                        enabled = !SettingsRepository.saving,
                        onCheckedChange = { want ->
                            scope.launch {
                                SettingsRepository.setActiveStatus(session.token, want)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Crimson,
                            checkedBorderColor = Crimson
                        )
                    )
                }

                SettingDivider()
                // The permission that decides whether a call can take the screen -
                // and, on Android 15, whether the running call's notification is
                // allowed to be drawn as a call at all.
                //
                // A row rather than only the prompt at sign-in, because that prompt
                // asks once. Declined, it never asks again, and from then on calls
                // arrive as a banner a locked phone does not show and the ongoing
                // notification quietly falls back to the plain template - with
                // nothing anywhere to say which switch would undo it. A setting
                // somebody can turn down has to be a setting they can find.
                if (!context.canTakeOverScreen()) {
                    SettingRow(
                        icon = IqIcons.Phone,
                        iconTint = CrimsonTint,
                        title = "Full screen calls",
                        subtitle = "Calls arrive as a banner, not a ringing screen",
                        onClick = { context.openFullScreenSettings() }
                    ) {
                        Text(
                            "Allow",
                            style = MaterialTheme.typography.titleMedium,
                            color = Crimson,
                            maxLines = 1
                        )
                        Spacer(Modifier.width(d.gapSm))
                        Icon(IqIcons.ChevronRight, null, Modifier.size(d.icon), tint = InkFaint)
                    }

                    SettingDivider()
                }

                // The same thing again, for the phones that keep their own switch
                // for it. Separate from the row above because they are separate
                // permissions in separate places: granting Android's does nothing
                // about Xiaomi's, and somebody who has done the first and still
                // misses calls needs to be told there is a second.
                if (context.phoneBlocksLockScreen()) {
                    SettingRow(
                        icon = IqIcons.Lock,
                        iconTint = CrimsonTint,
                        title = "Lock screen & pop-ups",
                        subtitle = "This phone keeps its own switch for ringing calls",
                        onClick = { context.openPhoneCallSettings() }
                    ) {
                        Text(
                            "Allow",
                            style = MaterialTheme.typography.titleMedium,
                            color = Crimson,
                            maxLines = 1
                        )
                        Spacer(Modifier.width(d.gapSm))
                        Icon(IqIcons.ChevronRight, null, Modifier.size(d.icon), tint = InkFaint)
                    }

                    SettingDivider()
                }
                // Whether other people may screenshot this account.
                //
                // The switch is about them, not about this phone: the setting
                // travels with the account, and the app showing this person's chat,
                // call or profile is the one that locks its window. This phone does
                // the same for whoever it is looking at.
                SettingRow(
                    icon = if (SettingsRepository.allowScreenshot) IqIcons.Camera
                    else IqIcons.Lock,
                    iconTint = CrimsonTint,
                    title = "Screenshots",
                    subtitle = if (SettingsRepository.allowScreenshot) {
                        "Others can screenshot your chats, calls and profile"
                    } else {
                        "Others are blocked from screenshotting you"
                    }
                ) {
                    Switch(
                        checked = SettingsRepository.allowScreenshot,
                        enabled = !SettingsRepository.saving,
                        onCheckedChange = { want ->
                            scope.launch {
                                SettingsRepository.setAllowScreenshot(session.token, want)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Crimson,
                            checkedBorderColor = Crimson
                        )
                    )
                }

                SettingDivider()
                SettingRow(
                    icon = IqIcons.Bell,
                    iconTint = CrimsonTint,
                    title = "Screenshot alerts",
                    subtitle = if (SettingsRepository.notifyOnScreenshot) {
                        "You're told when someone screenshots you"
                    } else {
                        "Screenshots of you pass without a word"
                    }
                ) {
                    Switch(
                        checked = SettingsRepository.notifyOnScreenshot,
                        enabled = !SettingsRepository.saving,
                        onCheckedChange = { want ->
                            scope.launch {
                                SettingsRepository.setNotifyOnScreenshot(session.token, want)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Crimson,
                            checkedBorderColor = Crimson
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

/** What the row says underneath "Notifications". */
internal val NotificationPreview.summary: String
    get() = when (this) {
        NotificationPreview.NAME_AND_TEXT -> "Sender and message shown"
        NotificationPreview.NAME_ONLY -> "Sender shown, message hidden"
        NotificationPreview.NOTHING -> "Sender and message hidden"
    }

/** What the row says on the right, and what the picker lists. */
internal val NotificationPreview.label: String
    get() = when (this) {
        NotificationPreview.NAME_AND_TEXT -> "Name and text"
        NotificationPreview.NAME_ONLY -> "Name only"
        NotificationPreview.NOTHING -> "Nothing"
    }

internal val NotificationPreview.detail: String
    get() = when (this) {
        NotificationPreview.NAME_AND_TEXT ->
            "Who wrote, and the start of what they said."
        NotificationPreview.NAME_ONLY ->
            "Who wrote. What they said is not sent to this phone at all."
        NotificationPreview.NOTHING ->
            "Only that a message arrived. Neither name nor text leaves the server."
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
private fun IdentityCard(
    profile: MessengerProfile?,
    onEdit: () -> Unit,
    onPicture: () -> Unit
) {
    val d = IqTheme.dimens
    val editSize = d.iconButtonLg

    // Building a Person from the profile keeps the avatar's generated gradient
    // and initials tied to the real identity.
    val me = remember(profile) {
        Person(
            id = profile?.uid.orEmpty().ifBlank { "me" },
            name = profile?.fullname.orEmpty().ifBlank { "Your profile" },
            handle = profile?.phoneNumber.orEmpty(),
            online = profile?.isOnline == true,
            photo = profile?.profilePicture.orEmpty()
        )
    }
    val bio = profile?.bio?.takeIf { it.isNotBlank() }
    val verified = profile?.isVerified == true
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
                    Box {
                        Avatar(
                            person = me,
                            size = d.avatarXl,
                            ringWidth = d.scaled(4),
                            ringColor = Color.White,
                            ringGap = 0.dp,
                            // Respects the user's own "show active status" setting.
                            showPresence = profile?.showActiveStatus == true
                        )
                        // Over the picture rather than beside it, which is where
                        // every app that has ever let somebody change one puts it -
                        // the thing to press is the thing being changed.
                        val lifting = MeRepository.imageProgress
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .size(d.iconButton)
                                .clip(CircleShape)
                                .background(Crimson)
                                .border(d.scaled(2), Color.White, CircleShape)
                                .clickable(enabled = lifting == null, onClick = onPicture),
                            contentAlignment = Alignment.Center
                        ) {
                            if (lifting != null) {
                                CircularProgressIndicator(
                                    progress = { lifting },
                                    modifier = Modifier.size(d.iconSm),
                                    color = Color.White,
                                    strokeWidth = d.scaled(2)
                                )
                            } else {
                                Icon(
                                    IqIcons.Camera,
                                    "Change your profile photo",
                                    Modifier.size(d.iconSm),
                                    tint = Color.White
                                )
                            }
                        }
                    }
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
                        .clickable(onClick = onEdit),
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
                // A dash until /v1/me answers: a placeholder number would be a lie.
                StatCell(profile?.let { compactCount(it.totalPost) } ?: "-",
                    "Posts", Modifier.weight(1f))
                StatDivider()
                StatCell(profile?.let { compactCount(it.followers) } ?: "-",
                    "Followers", Modifier.weight(1f))
                StatDivider()
                StatCell(profile?.let { compactCount(it.following) } ?: "-",
                    "Following", Modifier.weight(1f))
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
    // Every row was already `clickable` with an empty lambda, so each one rippled
    // and did nothing. Rows that lead somewhere pass this; the rest stay inert and
    // now say so by not rippling.
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit
) {
    val d = IqTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
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
