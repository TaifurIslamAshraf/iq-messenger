package com.iqsocial.iq_message.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.iqsocial.iq_message.ui.icons.IqIcons
import com.iqsocial.iq_message.ui.theme.Crimson
import com.iqsocial.iq_message.ui.theme.Hairline
import com.iqsocial.iq_message.ui.theme.Ink
import com.iqsocial.iq_message.ui.theme.InkFaint
import com.iqsocial.iq_message.ui.theme.InkMuted
import com.iqsocial.iq_message.ui.theme.IqTheme
import com.iqsocial.iq_message.ui.theme.Paper

/**
 * Adding someone by their QR code, two ways: the camera, or the code typed in.
 *
 * Typing is there for the same reason as on the QR screen: the code is ten
 * characters, short enough to read out over a call or paste from a message, and
 * the server takes it with any case, spaces or dashes.
 *
 * [onCode] gets what was scanned or typed. The dialog stays open while [busy], and
 * shows [note] - an expired or unknown code, say - so another try is one tap away.
 * The caller closes it once the code worked.
 */
@Composable
fun QrCodeEntryDialog(
    busy: Boolean,
    note: String?,
    onCode: (String) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Add by QR code"
) {
    val d = IqTheme.dimens
    var typed by remember { mutableStateOf("") }
    var scanning by remember { mutableStateOf(false) }

    fun send() {
        if (typed.isNotBlank() && !busy) onCode(typed.trim())
    }

    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .clip(RoundedCornerShape(d.radiusXl))
                .background(Paper)
                .padding(d.gutter)
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Ink)
            Spacer(Modifier.height(d.gapLg))

            Row(
                Modifier
                    .fillMaxWidth()
                    .height(d.buttonHeight)
                    .clip(RoundedCornerShape(d.radiusMd))
                    .background(if (busy) InkFaint else Crimson)
                    .clickable(enabled = !busy) { scanning = true },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(IqIcons.Camera, null, Modifier.size(d.icon), tint = Color.White)
                Spacer(Modifier.width(d.gapSm))
                Text(
                    "Open the camera",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }

            Spacer(Modifier.height(d.gapLg))
            Text(
                "Or type the code they read out to you.",
                style = MaterialTheme.typography.bodyMedium,
                color = InkMuted
            )
            Spacer(Modifier.height(d.gapSm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(d.radiusMd))
                        .border(1.dp, Hairline, RoundedCornerShape(d.radiusMd))
                        .padding(horizontal = d.gapMd, vertical = d.gapMd)
                ) {
                    if (typed.isEmpty()) {
                        Text("K7M2-Q9XA-4T", style = MaterialTheme.typography.bodyLarge, color = InkFaint)
                    }
                    BasicTextField(
                        value = typed,
                        onValueChange = { typed = it.take(MAX_CODE) },
                        enabled = !busy,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
                        cursorBrush = SolidColor(Crimson),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { send() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.width(d.gapSm))
                val canSend = typed.isNotBlank() && !busy
                Box(
                    Modifier
                        .height(d.buttonHeight)
                        .clip(RoundedCornerShape(d.radiusMd))
                        .background(if (canSend) Crimson else Hairline)
                        .clickable(enabled = canSend) { send() }
                        .padding(horizontal = d.gapLg),
                    contentAlignment = Alignment.Center
                ) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(d.iconSm), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text(
                            "Add",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (canSend) Color.White else InkMuted
                        )
                    }
                }
            }

            note?.let {
                Spacer(Modifier.height(d.gapMd))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = Crimson)
            }
        }
    }

    // After the dialog, so its window opens on top of it.
    if (scanning) {
        QrScanDialog(
            onFound = { code ->
                scanning = false
                onCode(code)
            },
            onDismiss = { scanning = false }
        )
    }
}

/** Codes are ten characters; this leaves room for dashes and spaces pasted with them. */
private const val MAX_CODE = 32
