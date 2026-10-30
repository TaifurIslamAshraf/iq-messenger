package com.iqsocial.iq_message.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import com.iqsocial.iq_message.data.overlay.ChatHeads
import com.iqsocial.iq_message.ui.icons.IqIcons
import com.iqsocial.iq_message.ui.theme.Crimson
import com.iqsocial.iq_message.ui.theme.CrimsonTint
import com.iqsocial.iq_message.ui.theme.Ink
import com.iqsocial.iq_message.ui.theme.InkMuted
import com.iqsocial.iq_message.ui.theme.IqTheme

/**
 * Asks to draw over other apps, which is what floating chat heads are.
 *
 * Like the full-screen call permission, there is no runtime prompt for it - the
 * only route is a Settings page - so this explains what it is for before sending
 * anyone there. "Display over other apps" is a frightening-sounding switch, and an
 * app that opens it without saying why deserves to be refused.
 *
 * Asked once per sign-in, and refusing costs nothing: messages still arrive as
 * notifications, which is how they arrive now.
 */
@Composable
fun ChatHeadsGate() {
    val context = LocalContext.current
    val session = remember { SessionStore(context) }
    var ask by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (ChatHeads.allowed(context)) return@LaunchedEffect
        if (session.chatHeadsPrompted) return@LaunchedEffect
        ask = true
    }

    if (!ask) return

    val d = IqTheme.dimens
    fun done() {
        session.chatHeadsPrompted = true
        ask = false
    }

    Dialog(
        onDismissRequest = { done() },
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
                        .clickable { done() }
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
                Icon(IqIcons.ChatFilled, null, Modifier.size(d.iconXl), tint = Crimson)
            }

            Spacer(Modifier.height(d.gapLg))
            Text(
                "Keep chats within reach",
                style = MaterialTheme.typography.headlineSmall,
                color = Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(d.gapSm))
            Text(
                "New messages can float as small circles over whatever you are " +
                    "doing, several at once. Tap one to open that chat, drag them " +
                    "aside, or leave this off and they stay as notifications.",
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
                        done()
                        context.openOverlaySettings()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Allow",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}

private fun Context.openOverlaySettings() {
    val direct = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.fromParts("package", packageName, null)
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { startActivity(direct) }.isSuccess) return

    // Some builds answer the action only without the package, landing on the list
    // rather than this app's row. Better than a button that appears to do nothing.
    runCatching {
        startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
