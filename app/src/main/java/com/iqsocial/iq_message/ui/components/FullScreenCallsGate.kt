package com.iqsocial.iq_message.ui.components

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.iqsocial.iq_message.data.auth.SessionStore
import com.iqsocial.iq_message.ui.icons.IqIcons
import com.iqsocial.iq_message.ui.theme.Crimson
import com.iqsocial.iq_message.ui.theme.CrimsonTint
import com.iqsocial.iq_message.ui.theme.Ink
import com.iqsocial.iq_message.ui.theme.InkMuted
import com.iqsocial.iq_message.ui.theme.IqTheme

/**
 * Asks for the one permission that lets a call take a locked screen.
 *
 * `USE_FULL_SCREEN_INTENT` was declared in the manifest and never checked, which was
 * enough until Android 14. From there the system stopped granting it on install to
 * anything it does not consider a calling or alarm app, and a full-screen intent
 * without it is quietly demoted to an ordinary heads-up notification. Nothing fails
 * and nothing is logged - a call simply arrives as a banner that a locked phone
 * never shows, which is exactly "it does not ring on the lock screen like other
 * apps".
 *
 * There is no runtime prompt for it. The only route is a Settings page, which the
 * system provides an intent for, so this explains why before sending anyone there -
 * a permissions screen opened with no reason given is one people back out of.
 *
 * Asked once per sign-in, like the notification prompt beside it. Calls still work
 * without it; they arrive as a notification rather than taking the screen.
 */
@Composable
fun FullScreenCallsGate() {
    val context = LocalContext.current
    val session = remember { SessionStore(context) }
    var ask by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Nothing to ask for below Android 14: the permission is granted with the
        // install and a full-screen intent works as it always did.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return@LaunchedEffect
        if (context.canTakeOverScreen()) return@LaunchedEffect
        if (session.fullScreenCallsPrompted) return@LaunchedEffect
        ask = true
    }

    if (!ask) return

    val d = IqTheme.dimens
    Dialog(
        onDismissRequest = {
            session.fullScreenCallsPrompted = true
            ask = false
        },
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
                        .clickable {
                            session.fullScreenCallsPrompted = true
                            ask = false
                        }
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
                Icon(IqIcons.Phone, null, Modifier.size(d.iconXl), tint = Crimson)
            }

            Spacer(Modifier.height(d.gapLg))
            Text(
                "Let calls reach a locked screen",
                style = MaterialTheme.typography.headlineSmall,
                color = Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(d.gapSm))
            Text(
                "Android asks separately before an app may take over a locked " +
                    "screen. Without it a call still arrives, but only as a " +
                    "notification - which a locked phone does not show.",
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
                    .clickable {
                        session.fullScreenCallsPrompted = true
                        ask = false
                        context.openFullScreenSettings()
                    },
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

/** Whether a ringing call may put itself over the lock screen. */
fun Context.canTakeOverScreen(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
    val manager = getSystemService(NotificationManager::class.java) ?: return false
    return runCatching { manager.canUseFullScreenIntent() }.getOrDefault(false)
}

/**
 * The settings page for it, falling back to this app's own settings.
 *
 * The dedicated intent exists from Android 14 and is the one that lands on the right
 * switch. Some builds do not answer it - a phone's own notification settings are
 * often a manufacturer's rather than Android's - and landing on the app's page is
 * better than a dialog that appears to do nothing.
 */
private fun Context.openFullScreenSettings() {
    val direct = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
        .setData(Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { startActivity(direct) }.isSuccess) return

    runCatching {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
