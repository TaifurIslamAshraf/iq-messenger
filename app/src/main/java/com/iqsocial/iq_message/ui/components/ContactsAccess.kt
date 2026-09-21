package com.iqsocial.iq_message.ui.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.iqsocial.iq_message.data.auth.SessionStore
import com.iqsocial.iq_message.data.contacts.ContactsRepository
import com.iqsocial.iq_message.data.contacts.DeviceContacts
import com.iqsocial.iq_message.ui.icons.IqIcons
import com.iqsocial.iq_message.ui.theme.Crimson
import com.iqsocial.iq_message.ui.theme.CrimsonTint
import com.iqsocial.iq_message.ui.theme.Ink
import com.iqsocial.iq_message.ui.theme.InkMuted
import com.iqsocial.iq_message.ui.theme.IqTheme
import kotlinx.coroutines.launch

/**
 * Asks for the address book once the user is signed in.
 *
 * Three cases, decided without bothering the user where possible: already granted
 * loads silently, already asked stays quiet, and only a fresh sign-in sees the
 * popup. The flag lives with the session, so signing out and back in asks again.
 */
@Composable
fun ContactsAccessGate() {
    val context = LocalContext.current
    val session = remember { SessionStore(context) }
    var showPopup by remember { mutableStateOf(false) }

    val requestPermission = rememberContactsRequest {
        session.contactsPrompted = true
        showPopup = false
    }

    LaunchedEffect(Unit) {
        when {
            // Already done this launch. Coming back to Home from a chat should not
            // re-read the address book, let alone upload it again.
            ContactsRepository.loaded -> Unit
            DeviceContacts.isGranted(context) ->
                ContactsRepository.refresh(context, session.token)
            !session.contactsPrompted -> showPopup = true
        }
    }

    if (showPopup) {
        ContactsAccessDialog(
            onAllow = requestPermission,
            onSkip = {
                session.contactsPrompted = true
                showPopup = false
            }
        )
    }
}

/**
 * Returns a function that shows the system permission prompt and, if it is
 * granted, loads the address book. Shared so the popup and the People tab ask in
 * exactly the same way.
 */
@Composable
fun rememberContactsRequest(onResult: (Boolean) -> Unit = {}): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = remember { SessionStore(context) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) scope.launch { ContactsRepository.refresh(context, session.token) }
        onResult(granted)
    }
    return { launcher.launch(Manifest.permission.READ_CONTACTS) }
}

/**
 * The permission popup.
 *
 * Skip sits in the top-right corner rather than under the primary button: it is
 * the way out of a request the user never asked for, so it should be the first
 * thing their thumb finds, not something to read past.
 */
@Composable
fun ContactsAccessDialog(
    onAllow: () -> Unit,
    onSkip: () -> Unit
) {
    val d = IqTheme.dimens

    Dialog(
        onDismissRequest = onSkip,
        // Tapping outside is too easy to do by accident for a one-time ask; Skip
        // and the back button are the deliberate ways out.
        properties = DialogProperties(dismissOnClickOutside = false)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(d.radiusXl))
                .background(Color.White)
                .padding(start = d.gapLg, end = d.gapLg, bottom = d.gapLg)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = d.gapSm),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    "Skip",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = InkMuted,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onSkip)
                        .padding(horizontal = d.gapMd, vertical = d.gapSm)
                )
            }

            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(d.avatarXl)
                    .clip(CircleShape)
                    .background(CrimsonTint),
                contentAlignment = Alignment.Center
            ) {
                Icon(IqIcons.People, null, Modifier.size(d.iconXl), tint = Crimson)
            }

            Spacer(Modifier.height(d.gapLg))
            Text(
                "Find your friends",
                style = MaterialTheme.typography.headlineSmall,
                color = Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(d.gapSm))
            Text(
                "See which of your contacts are already on IQ Social, so you can " +
                    "message the people you already know.",
                style = MaterialTheme.typography.bodyLarge,
                color = InkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(d.gapLg))
            // This line has to stay true: the sync request carries `phones` and
            // nothing else, so names really do stay on the device.
            ReassuranceRow(IqIcons.Lock, "Only numbers are sent, never names")
            Spacer(Modifier.height(d.gapSm))
            ReassuranceRow(IqIcons.Check, "You can turn this off any time in Settings")

            Spacer(Modifier.height(d.gapXl))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(d.buttonHeight)
                    .clip(RoundedCornerShape(d.radiusMd))
                    .background(Crimson)
                    .clickable(onClick = onAllow),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Allow access",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun ReassuranceRow(icon: ImageVector, text: String) {
    val d = IqTheme.dimens
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(d.iconSm), tint = Crimson)
        Spacer(Modifier.width(d.gapSm))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = InkMuted
        )
    }
}
