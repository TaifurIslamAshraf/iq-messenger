package com.iqsocial.iq_message.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import com.iqsocial.iq_message.ui.icons.IqIcons
import com.iqsocial.iq_message.ui.theme.Crimson
import com.iqsocial.iq_message.ui.theme.CrimsonTint
import com.iqsocial.iq_message.ui.theme.Ink
import com.iqsocial.iq_message.ui.theme.InkMuted
import com.iqsocial.iq_message.ui.theme.IqTheme

/**
 * Why a call did not go through.
 *
 * A dialog rather than a line on the call screen, because by the time this is known
 * there is usually no call screen left: a start that the server refused never
 * opened one. It has to be able to appear over whatever the person is looking at
 * instead.
 *
 * The server's own words. They are written for a reader - "This device is already
 * registered to another account", "You are blocked" - and replacing them with
 * "Something went wrong" would be throwing away the only part anyone can act on.
 */
@Composable
fun CallProblem(reason: String, onDismiss: () -> Unit) {
    val d = IqTheme.dimens
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(d.radiusXl))
                .background(Color.White)
                .padding(d.gapLg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(d.avatarLg)
                    .clip(CircleShape)
                    .background(CrimsonTint),
                contentAlignment = Alignment.Center
            ) {
                Icon(IqIcons.CallEnd, null, Modifier.size(d.icon), tint = Crimson)
            }

            Spacer(Modifier.height(d.gapMd))
            Text(
                "Call not connected",
                style = MaterialTheme.typography.titleLarge,
                color = Ink,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(d.gapXs))
            Text(
                reason,
                style = MaterialTheme.typography.bodyMedium,
                color = InkMuted,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(d.gapLg))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(d.buttonHeight)
                    .clip(RoundedCornerShape(d.radiusMd))
                    .background(Crimson)
                    .clickable(onClick = onDismiss),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "OK",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}
