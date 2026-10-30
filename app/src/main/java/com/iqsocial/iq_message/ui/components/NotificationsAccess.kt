package com.iqsocial.iq_message.ui.components

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.iqsocial.iq_message.data.auth.SessionStore
import com.iqsocial.iq_message.data.push.IqMessagingService
import com.iqsocial.iq_message.ui.icons.IqIcons
import com.iqsocial.iq_message.ui.theme.Crimson
import com.iqsocial.iq_message.ui.theme.CrimsonTint
import com.iqsocial.iq_message.ui.theme.Ink
import com.iqsocial.iq_message.ui.theme.InkMuted
import com.iqsocial.iq_message.ui.theme.IqTheme
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Gets notifications turned on, by whatever route this device actually has.
 *
 * There is no single way to ask, and which one applies is decided by the Android
 * version and by what the person has already said:
 *
 * | Device state | What happens |
 * |---|---|
 * | Below Android 13, switched on | nothing; there is no permission to hold |
 * | Below Android 13, switched off | no prompt exists - Settings is the only route |
 * | Android 13+, never asked | the system prompt, with no screen of our own first |
 * | Android 13+, refused once | the system will show it again, so ask again |
 * | Android 13+, refused twice | the system silently drops further requests, so a screen offering Settings instead |
 *
 * The last row is the one worth building for. After two refusals `launch` returns
 * "denied" without putting anything on screen, so an app that keeps calling it looks
 * to the user like one that has given up asking - and leaves them no way back if they
 * change their mind.
 *
 * Nothing here is required. Refused, the socket still delivers while the app is open;
 * what is lost is being told while it is not.
 */
@Composable
fun NotificationsGate() {
    val context = LocalContext.current
    val session = remember { SessionStore(context) }
    var showSettings by remember { mutableStateOf(false) }

    val ask = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        session.notificationsPrompted = true
        // Refused. Whether that is the end of it is the system's to say: if it would
        // still show the prompt another time, leave it be and ask again next
        // sign-in; if it has given up, Settings is the only way left.
        if (!granted && !context.activity().canAskAgain()) showSettings = true
    }

    LaunchedEffect(Unit) {
        IqMessagingService.ensureChannels(context)

        // Already on, by permission or because this Android never needed one.
        if (allowed(context)) return@LaunchedEffect
        // One ask per sign-in. The flag goes with the session, so the next person to
        // sign in on this phone is asked in their own right.
        if (session.notificationsPrompted) return@LaunchedEffect

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // Nothing to request: notifications are off because they were switched
            // off, and only Settings can switch them back on.
            session.notificationsPrompted = true
            showSettings = true
            return@LaunchedEffect
        }

        // Asked without first working out whether the system would answer. It is the
        // one that knows, and it tells us in the result: a refusal that appeared and
        // one that was dropped look the same here but differ in what can be done
        // next, which is what the callback sorts out.
        ask.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    if (showSettings) {
        NotificationsSettingsDialog(
            onOpen = {
                showSettings = false
                context.openNotificationSettings()
            },
            onSkip = {
                session.notificationsPrompted = true
                showSettings = false
            }
        )
    }
}

/** Permission where there is one, and the master switch everywhere. */
private fun allowed(context: Context): Boolean {
    val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    // Granted is not the same as on: the switch in Settings overrides it, and on
    // older versions it is the only thing there is.
    return permitted && NotificationManagerCompat.from(context).areNotificationsEnabled()
}

/**
 * Whether the system would put the prompt up again.
 *
 * `shouldShowRequestPermissionRationale` is how Android answers "have they refused
 * enough times that I have stopped asking?" - true after the first refusal, false
 * once it has given up. Read only after a refusal, since before the first ask it is
 * also false and means the opposite.
 */
private fun Activity?.canAskAgain(): Boolean {
    val activity = this ?: return false
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
    return ActivityCompat.shouldShowRequestPermissionRationale(
        activity,
        Manifest.permission.POST_NOTIFICATIONS
    )
}

private fun Context.activity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/**
 * Opens this app's notification settings, or its details page if that is all the
 * device offers.
 *
 * The notification screen is the right place and exists from Android 8, but not
 * every manufacturer's build honours the intent, so the details page is the fallback
 * rather than nothing happening.
 */
private fun Context.openNotificationSettings() {
    val screens = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            add(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            )
        }
        add(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", packageName, null))
        )
    }
    for (intent in screens) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
            return
        } catch (_: ActivityNotFoundException) {
            // Try the next one.
        }
    }
}

/**
 * Shown only when there is no prompt left to show.
 *
 * Deliberately not shown before the first ask: the system prompt says the same thing
 * in fewer words, and a screen explaining that a screen is coming is one screen too
 * many. This exists because by this point the system has stopped asking, and without
 * it there would be no way back.
 */
@Composable
private fun NotificationsSettingsDialog(onOpen: () -> Unit, onSkip: () -> Unit) {
    val d = IqTheme.dimens
    Dialog(
        onDismissRequest = onSkip,
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
                    "Not now",
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
                Icon(IqIcons.Bell, null, Modifier.size(d.iconXl), tint = Crimson)
            }

            Spacer(Modifier.height(d.gapLg))
            Text(
                "Know when someone writes",
                style = MaterialTheme.typography.headlineSmall,
                color = Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(d.gapSm))
            Text(
                "Notifications are switched off for IQ Message, and this phone will " +
                    "not ask again. You can turn them back on in Settings.",
                style = MaterialTheme.typography.bodyLarge,
                color = InkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(d.gapXl))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(d.buttonHeight)
                    .clip(RoundedCornerShape(d.radiusMd))
                    .background(Crimson)
                    .clickable(onClick = onOpen),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Open settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}
