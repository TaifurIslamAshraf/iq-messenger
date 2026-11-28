package com.iqsocial.iq_message.ui.screens

import com.iqsocial.iq_message.ui.theme.PaperSoft
import com.iqsocial.iq_message.ui.theme.Paper
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.iqsocial.iq_message.data.Country
import com.iqsocial.iq_message.data.Person
import com.iqsocial.iq_message.data.auth.SessionStore
import com.iqsocial.iq_message.data.media.RemoteImage
import com.iqsocial.iq_message.data.profile.MeRepository
import com.iqsocial.iq_message.data.profile.SettingsRepository
import com.iqsocial.iq_message.data.remote.MessengerProfile
import com.iqsocial.iq_message.data.remote.NotificationPreview
import com.iqsocial.iq_message.data.remote.ProfileImage
import com.iqsocial.iq_message.ui.components.Avatar
import com.iqsocial.iq_message.ui.components.CircleIconButton
import com.iqsocial.iq_message.ui.components.CountryPickerSheet
import com.iqsocial.iq_message.ui.components.HairlineDivider
import com.iqsocial.iq_message.ui.icons.IqIcons
import com.iqsocial.iq_message.ui.theme.CanvasGrey
import com.iqsocial.iq_message.ui.theme.Crimson
import com.iqsocial.iq_message.ui.theme.CrimsonTint
import com.iqsocial.iq_message.ui.theme.FieldGrey
import com.iqsocial.iq_message.ui.theme.Hairline
import com.iqsocial.iq_message.ui.theme.Ink
import com.iqsocial.iq_message.ui.theme.InkFaint
import com.iqsocial.iq_message.ui.theme.InkMuted
import com.iqsocial.iq_message.ui.theme.IqTheme
import com.iqsocial.iq_message.ui.theme.SystemBarIcons
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Editing the account's own profile.
 *
 * A screen rather than a sheet, and that is the whole shape of this file: settings
 * that change something are pushed onto the navigation stack like any other screen,
 * so they get the back gesture, the whole height for a form with a keyboard under
 * it, and a title that says where you are. A bottom sheet gives none of those - it
 * is for a choice that takes a second, and none of these do.
 *
 * Six things, and they do not all go to the same place. The bio, gender, birthday,
 * country and location are one `PATCH /v1/me/profile`; the name is a `PUT` of its
 * own, because it is rationed at one change every sixty days and the refusal that
 * enforces that has to be shown rather than swallowed. Both are sent here, in that
 * order, and only what was actually edited is sent at all - the endpoints take
 * "only what changes", and writing back untouched values would undo an edit made on
 * the main IQ Social app since this screen was opened.
 *
 * Nothing is applied optimistically. Every one of these endpoints answers with the
 * profile as it now stands, which is worth more than this side's assumption that
 * the write took: a name the server trimmed, or a bio it shortened, arrives back
 * and is what the screen shows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    /** A picture has been chosen; it is cropped before anything is uploaded. */
    onCrop: (Uri, ProfileImage) -> Unit = { _, _ -> }
) {
    SystemBarIcons(darkIcons = true)
    val d = IqTheme.dimens
    val context = androidx.compose.ui.platform.LocalContext.current
    val session = remember { SessionStore(context) }
    val token = session.token
    val scope = rememberCoroutineScope()
    val profile = MeRepository.profile

    // Opened cold - from a notification, or after the process was rebuilt - this
    // screen can be the first thing to want the profile.
    LaunchedEffect(Unit) {
        if (!MeRepository.loaded) MeRepository.load(token)
    }

    var name by remember(profile) { mutableStateOf(profile?.fullname.orEmpty()) }
    var bio by remember(profile) { mutableStateOf(profile?.bio.orEmpty()) }
    var gender by remember(profile) { mutableStateOf(profile?.gender.orEmpty()) }
    var birthday by remember(profile) { mutableStateOf(profile?.birthday ?: 0L) }
    var country by remember(profile) { mutableStateOf(profile?.country.orEmpty()) }
    var place by remember(profile) { mutableStateOf(profile?.location.orEmpty()) }

    var countries by remember { mutableStateOf(false) }
    var calendar by remember { mutableStateOf(false) }

    /** This screen's own complaint, as opposed to the server's in [MeRepository]. */
    var note by remember { mutableStateOf<String?>(null) }

    /**
     * Which picture the photo picker was opened for.
     *
     * The launcher hands back only a `Uri`, and by then the press that started it
     * is long over - so which of the two pictures it was for has to be kept here.
     */
    var choosingFor by remember { mutableStateOf<ProfileImage?>(null) }
    val photos = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { picked: Uri? ->
        val kind = choosingFor
        choosingFor = null
        // To the crop screen first: see [ImageCropScreen]. The upload itself runs
        // in the repository's own scope, so leaving this screen cannot cancel it.
        if (picked != null && kind != null) onCrop(picked, kind)
    }

    fun pick(kind: ProfileImage) {
        choosingFor = kind
        photos.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    val canRename = profile?.nameCanChange != false
    val edited = profile != null && (
        name.trim() != profile.fullname ||
            bio != profile.bio ||
            gender != profile.gender ||
            birthday != profile.birthday ||
            country != profile.country ||
            place != profile.location
        )

    fun save() {
        val person = profile ?: return
        val wanted = name.trim()
        if (wanted.isEmpty()) {
            note = "A name cannot be empty."
            return
        }
        note = null
        MeRepository.forgetError()
        scope.launch {
            var ok = true
            // The name first, because it is the one that can be refused for a
            // reason that is not a failure - and if it is, the rest should still
            // save rather than being abandoned halfway.
            if (wanted != person.fullname) {
                when (val renamed = MeRepository.rename(token, wanted)) {
                    is MeRepository.Renamed.Done -> Unit
                    is MeRepository.Renamed.TooSoon -> {
                        val days = renamed.cooldown.daysRemaining
                        note = if (days > 0) {
                            "Your name can change again in $days day" +
                                (if (days == 1) "." else "s.")
                        } else {
                            "Your name cannot change yet."
                        }
                        ok = false
                    }

                    is MeRepository.Renamed.Failed -> ok = false
                }
            }

            val rest = bio != person.bio ||
                gender != person.gender ||
                birthday != person.birthday ||
                country != person.country ||
                place != person.location
            if (rest) {
                val saved = MeRepository.updateProfile(
                    token = token,
                    // Each of these is null when untouched, which is how the
                    // endpoint is told to leave it alone. An empty string is the
                    // opposite instruction - clear it - and the two are different
                    // things the server treats differently.
                    bio = bio.takeIf { it != person.bio },
                    gender = gender.takeIf { it != person.gender && it.isNotBlank() },
                    birthday = birthday.takeIf { it != person.birthday && it > 0 },
                    country = country.takeIf { it != person.country && it.isNotBlank() },
                    location = place.takeIf { it != person.location }
                )
                ok = saved && ok
            }
            if (ok) onBack()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(CanvasGrey)
            .imePadding()
    ) {
        ScreenHeader(title = "Edit profile", onBack = onBack)

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = d.gutterTight)
                .padding(top = d.gapMd, bottom = d.gapLg)
        ) {
            PhotoCard(
                profile = profile,
                onChange = ::pick,
                onRemove = { kind ->
                    scope.launch { MeRepository.removeImage(token, kind) }
                }
            )

            Spacer(Modifier.height(d.gapLg))
            Field(
                label = "Name",
                value = name,
                onValueChange = { name = it.take(NAME_MAX) },
                placeholder = "Your name",
                enabled = canRename,
                note = if (canRename) {
                    "A name can change once every 60 days."
                } else {
                    val day = profile?.nameNextChangeAt ?: 0L
                    if (day > 0) "You can change your name again on ${onDay(day)}."
                    else "Your name was changed recently."
                }
            )

            Spacer(Modifier.height(d.gapMd))
            Field(
                label = "Bio",
                value = bio,
                onValueChange = { bio = it.take(BIO_MAX) },
                placeholder = "Say something about yourself",
                singleLine = false,
                note = "${bio.length} / $BIO_MAX"
            )

            Spacer(Modifier.height(d.gapMd))
            FieldLabel("Gender")
            Spacer(Modifier.height(d.gapXs))
            Row(horizontalArrangement = Arrangement.spacedBy(d.gapSm)) {
                GENDERS.forEach { option ->
                    ChoiceChip(
                        text = option,
                        chosen = gender.equals(option, ignoreCase = true),
                        // Pressed again it goes back to unset locally - though the
                        // server has no way to clear one, so an unset gender is
                        // simply not sent rather than sent as empty.
                        onClick = { gender = if (gender == option) "" else option }
                    )
                }
            }

            Spacer(Modifier.height(d.gapMd))
            TapField(
                label = "Birthday",
                value = if (birthday > 0) onDay(birthday) else "",
                placeholder = "Not set",
                onClick = { calendar = true }
            )

            Spacer(Modifier.height(d.gapMd))
            TapField(
                label = "Country",
                value = country,
                placeholder = "Not set",
                onClick = { countries = true }
            )

            Spacer(Modifier.height(d.gapMd))
            Field(
                label = "Location",
                value = place,
                onValueChange = { place = it.take(LOCATION_MAX) },
                placeholder = "A city, or anywhere you like"
            )

            (note ?: MeRepository.error)?.let { why ->
                Spacer(Modifier.height(d.gapMd))
                Text(why, style = MaterialTheme.typography.bodyMedium, color = Crimson)
            }
        }

        // Its own strip at the foot of the screen rather than the end of the scroll:
        // a form whose save button is below the fold is a form people think they
        // have finished.
        Column(
            Modifier
                .fillMaxWidth()
                .background(Paper)
                .padding(horizontal = d.gutterTight, vertical = d.gapMd)
                .navigationBarsPadding()
        ) {
            WideButton(
                text = "Save changes",
                loading = MeRepository.saving,
                enabled = edited,
                onClick = ::save
            )
        }
    }

    if (countries) {
        CountryPickerSheet(
            selected = remember(country) {
                Country.ALL.firstOrNull { it.name.equals(country, true) } ?: Country.BANGLADESH
            },
            onDismiss = { countries = false },
            onSelect = {
                country = it.name
                countries = false
            }
        )
    }

    if (calendar) {
        // The picker works in UTC and the server stores epoch ms, so the two agree
        // without a conversion - which matters for a birthday more than for most
        // dates: run through a local timezone and a date near midnight becomes the
        // day before for anybody west of Greenwich.
        val picked = rememberDatePickerState(
            initialSelectedDateMillis = birthday.takeIf { it > 0 }
        )
        DatePickerDialog(
            onDismissRequest = { calendar = false },
            confirmButton = {
                TextButton(onClick = {
                    picked.selectedDateMillis?.let { birthday = it }
                    calendar = false
                }) { Text("Set", color = Crimson) }
            },
            dismissButton = {
                TextButton(onClick = { calendar = false }) {
                    Text("Cancel", color = InkMuted)
                }
            }
        ) {
            DatePicker(state = picked, title = null)
        }
    }
}

/**
 * Who may message, which is the account's setting rather than the messenger's. Who
 * may call is not here: it is chosen per person, in each chat's Permissions.
 *
 * Written the moment a choice is pressed rather than behind a save button: there
 * is one value per question and the server answers with what it became, so a
 * "Save" underneath would be asking people to confirm a choice they have already
 * made.
 *
 * The note at the foot is the server's own: it stores and returns
 * `who_can_message` but the messenger does not enforce it yet. Saying so is better
 * than a setting that looks like it is doing something it is not.
 */
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    SystemBarIcons(darkIcons = true)
    val d = IqTheme.dimens
    val context = androidx.compose.ui.platform.LocalContext.current
    val session = remember { SessionStore(context) }
    val scope = rememberCoroutineScope()
    val profile = MeRepository.profile

    LaunchedEffect(Unit) {
        if (!MeRepository.loaded) MeRepository.load(session.token)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(CanvasGrey)
    ) {
        ScreenHeader(title = "Privacy", onBack = onBack)

        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = d.gutterTight)
                .padding(top = d.gapMd, bottom = d.gapXl)
                .navigationBarsPadding()
        ) {
            Text(
                "The same setting as the IQ Social app - it is one account. Who can " +
                    "call you is chosen per person, in each chat's Permissions.",
                style = MaterialTheme.typography.bodyMedium,
                color = InkMuted
            )

            Spacer(Modifier.height(d.gapLg))
            FieldLabel("Who can message you")
            Spacer(Modifier.height(d.gapXs))
            Card {
                AUDIENCES.forEachIndexed { index, (wire, label) ->
                    if (index > 0) HairlineDivider()
                    AudienceRow(
                        label = label,
                        chosen = profile?.whoCanMessage == wire,
                        enabled = !MeRepository.saving
                    ) {
                        scope.launch {
                            MeRepository.updatePrivacy(session.token, whoCanMessage = wire)
                        }
                    }
                }
            }

            Spacer(Modifier.height(d.gapSm))
            Text(
                "Saved on your account. Messages are not filtered by the messenger yet.",
                style = MaterialTheme.typography.bodySmall,
                color = InkFaint
            )

            MeRepository.error?.let { why ->
                Spacer(Modifier.height(d.gapMd))
                Text(why, style = MaterialTheme.typography.bodyMedium, color = Crimson)
            }
        }
    }
}

/**
 * How much of a message its notification is allowed to say.
 *
 * Worth a screen rather than a list of three labels, because this is not about
 * what the phone draws on its lock screen: whatever is hidden is never sent, so it
 * cannot be read off this device by anyone, including anything else installed on
 * it. That is the reason to choose the narrower option, and it needs the room to
 * be said.
 */
@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    SystemBarIcons(darkIcons = true)
    val d = IqTheme.dimens
    val context = androidx.compose.ui.platform.LocalContext.current
    val session = remember { SessionStore(context) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (!SettingsRepository.loaded) SettingsRepository.load(session.token)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(CanvasGrey)
    ) {
        ScreenHeader(title = "Notifications", onBack = onBack)

        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = d.gutterTight)
                .padding(top = d.gapMd, bottom = d.gapXl)
                .navigationBarsPadding()
        ) {
            Text(
                "Whatever is hidden is never sent to your phones. It applies to " +
                    "every device on your account, from the next message on. Calls " +
                    "always say who is calling.",
                style = MaterialTheme.typography.bodyMedium,
                color = InkMuted
            )

            Spacer(Modifier.height(d.gapLg))
            Card {
                NotificationPreview.entries.forEachIndexed { index, option ->
                    if (index > 0) HairlineDivider()
                    val chosen = option == SettingsRepository.notificationPreview
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !SettingsRepository.saving && !chosen) {
                                scope.launch {
                                    SettingsRepository.setNotificationPreview(
                                        session.token,
                                        option
                                    )
                                }
                            }
                            .padding(horizontal = d.gapMd, vertical = d.gapMd),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Tick(chosen)
                        Spacer(Modifier.width(d.gapMd))
                        Column(Modifier.weight(1f)) {
                            Text(
                                option.label,
                                style = MaterialTheme.typography.titleMedium,
                                color = Ink
                            )
                            Text(
                                option.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = InkMuted
                            )
                        }
                    }
                }
            }

            SettingsRepository.error?.let { why ->
                Spacer(Modifier.height(d.gapMd))
                Text(why, style = MaterialTheme.typography.bodyMedium, color = Crimson)
            }
        }
    }
}

/* ---------------------------------------------------------------- pieces */

/** Back, and where you are. The same strip on all three screens. */
@Composable
private fun ScreenHeader(title: String, onBack: () -> Unit) {
    val d = IqTheme.dimens
    Column(Modifier.fillMaxWidth().background(Paper)) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = d.gapXs, end = d.gutter, top = d.gapSm, bottom = d.gapSm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleIconButton(
                icon = IqIcons.ArrowBack,
                contentDescription = "Back",
                onClick = onBack
            )
            Spacer(Modifier.width(d.gapXs))
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = Ink,
                maxLines = 1
            )
        }
        HairlineDivider()
    }
}

/** The white rounded block the rows sit in, as on the settings page. */
@Composable
private fun Card(content: @Composable () -> Unit) {
    val d = IqTheme.dimens
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(d.radiusXl))
            .background(PaperSoft)
            .border(1.dp, Hairline, RoundedCornerShape(d.radiusXl))
    ) { content() }
}

/**
 * The two pictures, with what can be done to them.
 *
 * In this screen rather than behind a menu on the profile page, because removing
 * one is an edit like any other here - and because a two-item popup to choose
 * between "change" and "remove" is exactly the kind of thing this screen exists
 * instead of.
 */
@Composable
private fun PhotoCard(
    profile: MessengerProfile?,
    onChange: (ProfileImage) -> Unit,
    onRemove: (ProfileImage) -> Unit
) {
    val d = IqTheme.dimens
    val lifting = MeRepository.imageProgress
    val me = remember(profile) {
        Person(
            id = profile?.uid.orEmpty().ifBlank { "me" },
            name = profile?.fullname.orEmpty().ifBlank { "Your profile" },
            handle = profile?.phoneNumber.orEmpty(),
            photo = profile?.profilePicture.orEmpty()
        )
    }
    Card {
        Row(
            Modifier.fillMaxWidth().padding(d.gapMd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(person = me, size = d.avatarLg)
            Spacer(Modifier.width(d.gapMd))
            PhotoActions(
                title = "Profile photo",
                has = profile?.profilePicture.orEmpty().isNotBlank(),
                busy = lifting,
                onChange = { onChange(ProfileImage.PICTURE) },
                onRemove = { onRemove(ProfileImage.PICTURE) }
            )
        }

        HairlineDivider()

        Row(
            Modifier.fillMaxWidth().padding(d.gapMd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val cover = profile?.coverPicture.orEmpty()
            Box(
                Modifier
                    .size(width = d.scaled(84), height = d.avatarLg)
                    .clip(RoundedCornerShape(d.radiusMd))
                    .background(FieldGrey),
                contentAlignment = Alignment.Center
            ) {
                if (cover.isBlank()) {
                    Icon(IqIcons.Gallery, null, Modifier.size(d.icon), tint = InkFaint)
                } else {
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
                    }
                }
            }
            Spacer(Modifier.width(d.gapMd))
            PhotoActions(
                title = "Cover photo",
                has = cover.isNotBlank(),
                busy = lifting,
                onChange = { onChange(ProfileImage.COVER) },
                onRemove = { onRemove(ProfileImage.COVER) }
            )
        }
    }
}

@Composable
private fun PhotoActions(
    title: String,
    has: Boolean,
    busy: Float?,
    onChange: () -> Unit,
    onRemove: () -> Unit
) {
    val d = IqTheme.dimens
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Ink)
        Spacer(Modifier.height(d.gapXs))
        if (busy != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    progress = { busy },
                    modifier = Modifier.size(d.iconSm),
                    color = Crimson,
                    strokeWidth = d.scaled(2)
                )
                Spacer(Modifier.width(d.gapSm))
                Text(
                    "Uploading ${(busy * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(d.gapMd)) {
                TextAction("Change", Crimson, onChange)
                if (has) TextAction("Remove", InkMuted, onRemove)
            }
        }
    }
}

@Composable
private fun TextAction(text: String, tint: Color, onClick: () -> Unit) {
    val d = IqTheme.dimens
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = tint,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(d.radiusSm))
            .clickable(onClick = onClick)
            .padding(horizontal = d.gapXs, vertical = d.gapXs)
    )
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = InkMuted,
        fontWeight = FontWeight.Medium
    )
}

/**
 * A labelled box with text in it.
 *
 * Its own rather than the auth screens' notched field: this one has to hold a bio,
 * which is several lines, and to carry a line of help under it - a character count,
 * or why the name is not editable today.
 */
@Composable
private fun Field(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    note: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    val d = IqTheme.dimens
    Column(Modifier.fillMaxWidth()) {
        FieldLabel(label)
        Spacer(Modifier.height(d.gapXs))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(d.radiusMd))
                .background(if (enabled) Paper else FieldGrey)
                .border(1.dp, Hairline, RoundedCornerShape(d.radiusMd))
                .padding(horizontal = d.gapMd, vertical = d.gapSm)
                .heightIn(min = d.scaled(32))
        ) {
            if (value.isEmpty()) {
                Text(
                    placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = InkFaint,
                    maxLines = if (singleLine) 1 else 2
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = singleLine,
                maxLines = if (singleLine) 1 else BIO_LINES,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = if (enabled) Ink else InkMuted
                ),
                cursorBrush = SolidColor(Crimson),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.fillMaxWidth()
            )
        }
        note?.let {
            Spacer(Modifier.height(d.gapXs))
            Text(it, style = MaterialTheme.typography.bodySmall, color = InkFaint)
        }
    }
}

/** The same box, but it opens something instead of taking typing. */
@Composable
private fun TapField(
    label: String,
    value: String,
    placeholder: String,
    onClick: () -> Unit
) {
    val d = IqTheme.dimens
    Column(Modifier.fillMaxWidth()) {
        FieldLabel(label)
        Spacer(Modifier.height(d.gapXs))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(d.radiusMd))
                .background(Paper)
                .border(1.dp, Hairline, RoundedCornerShape(d.radiusMd))
                .clickable(onClick = onClick)
                .padding(horizontal = d.gapMd, vertical = d.gapMd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                value.ifBlank { placeholder },
                style = MaterialTheme.typography.bodyLarge,
                color = if (value.isBlank()) InkFaint else Ink,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Icon(IqIcons.ChevronDown, null, Modifier.size(d.icon), tint = InkMuted)
        }
    }
}

@Composable
private fun ChoiceChip(text: String, chosen: Boolean, onClick: () -> Unit) {
    val d = IqTheme.dimens
    Row(
        Modifier
            .clip(RoundedCornerShape(d.radiusMd))
            .background(if (chosen) CrimsonTint else Paper)
            .border(
                1.dp,
                if (chosen) Crimson else Hairline,
                RoundedCornerShape(d.radiusMd)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = d.gapLg, vertical = d.gapSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (chosen) {
            Icon(IqIcons.Check, null, Modifier.size(d.iconSm), tint = Crimson)
            Spacer(Modifier.width(d.gapXs))
        }
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = if (chosen) Crimson else Ink
        )
    }
}

@Composable
private fun AudienceRow(
    label: String,
    chosen: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val d = IqTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && !chosen, onClick = onClick)
            .padding(horizontal = d.gapMd, vertical = d.gapMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Tick(chosen)
        Spacer(Modifier.width(d.gapMd))
        Text(label, style = MaterialTheme.typography.titleMedium, color = Ink)
    }
}

/** The filled circle that says which one of a set is in force. */
@Composable
private fun Tick(chosen: Boolean) {
    val d = IqTheme.dimens
    Box(
        Modifier
            .size(d.icon)
            .clip(CircleShape)
            .background(if (chosen) Crimson else Color.Transparent)
            .border(if (chosen) 0.dp else 2.dp, InkFaint, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (chosen) Icon(IqIcons.Check, null, Modifier.size(d.iconSm), tint = Color.White)
    }
}

@Composable
private fun WideButton(
    text: String,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val d = IqTheme.dimens
    val active = enabled && !loading
    Box(
        Modifier
            .fillMaxWidth()
            .height(d.buttonHeight)
            .clip(RoundedCornerShape(d.radiusMd))
            .background(if (active) Crimson else Crimson.copy(alpha = 0.45f))
            .clickable(enabled = active, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(d.icon)
            )
        } else {
            Text(
                text,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * A date the way it is stored: in UTC.
 *
 * The server keeps a birthday as epoch milliseconds and the picker hands them back
 * in UTC, so reading them back in the phone's timezone would show the day before
 * for anybody west of Greenwich - and then saving it would move it again.
 */
private fun onDay(epochMs: Long): String =
    SimpleDateFormat("d MMM yyyy", Locale.getDefault())
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(java.util.Date(epochMs))

/** The two the server takes. Nothing else is accepted, so nothing else is offered. */
private val GENDERS = listOf("Male", "Female")

/** The five audiences, in the order the account API lists them. */
private val AUDIENCES = listOf(
    "everyone" to "Everyone",
    "following" to "People you follow",
    "followers" to "People who follow you",
    "mutual" to "People you both follow",
    "no_one" to "No one"
)

private const val NAME_MAX = 60
private const val BIO_MAX = 500
private const val BIO_LINES = 5
private const val LOCATION_MAX = 120
