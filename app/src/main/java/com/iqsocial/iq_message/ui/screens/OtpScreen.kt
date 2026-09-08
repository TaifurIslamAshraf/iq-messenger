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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.iqsocial.iq_message.ui.components.CircleIconButton
import com.iqsocial.iq_message.ui.components.SheetHandle
import com.iqsocial.iq_message.ui.components.drawHeroBokeh
import com.iqsocial.iq_message.ui.components.sheetShape
import com.iqsocial.iq_message.ui.icons.IqIcons
import com.iqsocial.iq_message.ui.theme.BrandSunset
import com.iqsocial.iq_message.ui.theme.Crimson
import com.iqsocial.iq_message.ui.theme.CrimsonTint
import com.iqsocial.iq_message.ui.theme.Hairline
import com.iqsocial.iq_message.ui.theme.Ink
import com.iqsocial.iq_message.ui.theme.SheetSurface
import com.iqsocial.iq_message.ui.theme.InkMuted
import com.iqsocial.iq_message.ui.theme.IqTheme
import com.iqsocial.iq_message.ui.theme.SystemBarIcons
import kotlinx.coroutines.delay

private const val OTP_LENGTH = 6
private const val OTP_SECONDS = 102 // 01:42, as in the mockup

@Composable
fun OtpScreen(
    onBack: () -> Unit,
    onVerified: () -> Unit
) {
    SystemBarIcons(darkIcons = false)
    val d = IqTheme.dimens

    var code by remember { mutableStateOf("842") }
    var secondsLeft by remember { mutableIntStateOf(OTP_SECONDS) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft--
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(SheetSurface)
    ) {

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            /* -- hero ------------------------------------------------------ */
            Box(Modifier.fillMaxWidth().background(BrandSunset)) {
            Canvas(Modifier.matchParentSize()) { drawHeroBokeh() }
            CenteredColumn(applyStatusBars = true) {
                Spacer(Modifier.height(d.gapMd))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircleIconButton(
                        icon = IqIcons.ArrowBack,
                        contentDescription = "Back",
                        onClick = onBack,
                        background = Color.White.copy(alpha = 0.20f),
                        tint = Color.White
                    )
                    Spacer(Modifier.weight(1f))
                    BrandLockup(color = Color.White, markSize = d.scaled(26))
                    Spacer(Modifier.weight(1f))
                    StepPill("2 of 2")
                }

                Spacer(Modifier.height(d.gapXxl))
                Text(
                    "Verify your number",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White
                )
                Spacer(Modifier.height(d.gapSm))
                Text(
                    "A secure code was sent to your phone.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.94f)
                )
                Spacer(Modifier.height(d.gapXl + d.sheetRadius))
            }
            }

            /* -- sheet ----------------------------------------------------- */
            // Pulled up over the hero so the sheet's rounded corners read
            // against the gradient instead of against their own colour.
            Column(
                Modifier
                    .fillMaxWidth()
                    .offset(y = -d.sheetRadius)
                    .clip(sheetShape())
                    .background(SheetSurface)
                    .imePadding()
            ) {
                CenteredColumn(
                    topPadding = d.gapMd,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SheetHandle()
                    Spacer(Modifier.height(d.gapXl))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            Modifier
                                .size(d.scaled(52))
                                .clip(CircleShape)
                                .background(CrimsonTint),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(IqIcons.ShieldCheck, null, Modifier.size(d.iconLg), tint = Crimson)
                        }
                        Spacer(Modifier.width(d.gapMd))
                        Text(
                            "Enter Verification Code",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Ink,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(d.gapLg))
                    Text(
                        "We sent a 6-digit code to",
                        style = MaterialTheme.typography.bodyLarge,
                        color = InkMuted
                    )
                    Spacer(Modifier.height(d.gapXs))
                    Text(
                        "+880 17••• ••678",
                        style = MaterialTheme.typography.titleLarge,
                        color = Ink,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(d.gapSm))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(d.radiusSm))
                            .clickable(onClick = onBack)
                            .padding(horizontal = d.gapSm, vertical = d.gapSm)
                    ) {
                        Icon(IqIcons.Edit, null, Modifier.size(d.iconSm), tint = Crimson)
                        Spacer(Modifier.width(d.gapSm))
                        Text(
                            "Change number",
                            style = MaterialTheme.typography.titleMedium,
                            color = Crimson,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(Modifier.height(d.gapMd))

                    /* -- code card --------------------------------------------- */
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .border(1.dp, Hairline, RoundedCornerShape(d.radiusLg))
                            .background(Color(0xFFFAFAFB), RoundedCornerShape(d.radiusLg))
                            .padding(horizontal = d.gapMd, vertical = d.gapLg)
                    ) {
                        Box {
                            // The real (invisible) input; the cells below render its value.
                            BasicTextField(
                                value = code,
                                onValueChange = { code = it.filter(Char::isDigit).take(OTP_LENGTH) },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.NumberPassword
                                ),
                                modifier = Modifier
                                    .matchParentSize()
                                    .focusRequester(focusRequester)
                                    .alpha(0f)
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(d.gapSm)
                            ) {
                                repeat(OTP_LENGTH) { index ->
                                    OtpCell(
                                        digit = code.getOrNull(index)?.toString(),
                                        focused = index == code.length.coerceAtMost(OTP_LENGTH - 1),
                                        onClick = { focusRequester.requestFocus() },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(d.gapLg))
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(IqIcons.Clock, null, Modifier.size(d.iconSm), tint = Ink)
                            Spacer(Modifier.width(d.gapSm))
                            Text(
                                text = buildAnnotatedString {
                                    append("Expires in ")
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Ink)) {
                                        append(formatMmSs(secondsLeft))
                                    }
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = InkMuted,
                                maxLines = 1
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "Resend Code",
                                style = MaterialTheme.typography.titleMedium,
                                color = Crimson,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(d.radiusSm))
                                    .clickable { secondsLeft = OTP_SECONDS }
                                    .padding(horizontal = d.gapXs, vertical = d.gapXs)
                            )
                        }

                        Spacer(Modifier.height(d.gapMd))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(d.radiusSm))
                                .background(CrimsonTint.copy(alpha = 0.55f))
                                .padding(horizontal = d.gapMd, vertical = d.gapMd),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(IqIcons.Shield, null, Modifier.size(d.iconSm), tint = Ink)
                            Spacer(Modifier.width(d.gapMd))
                            Text(
                                "The code is valid for 2 minutes.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = InkMuted
                            )
                        }
                    }

                    Spacer(Modifier.height(d.gapLg))
                    Text(
                        text = buildAnnotatedString {
                            append("Didn't receive the code? ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Ink)) {
                                append("Get help")
                            }
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = InkMuted,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(d.gapXl))
                    PrimaryButton(text = "Verify & Continue", onClick = onVerified)

                    Spacer(Modifier.height(d.gapLg))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(IqIcons.Lock, null, Modifier.size(d.iconXs), tint = Ink)
                        Spacer(Modifier.width(d.gapSm))
                        Text(
                            "Secure verification",
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkMuted
                        )
                    }
                    Spacer(Modifier.height(d.gapMd))
                    Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
                }
            }
        }
    }
}

@Composable
private fun OtpCell(
    digit: String?,
    focused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val d = IqTheme.dimens
    Box(
        modifier
            // Square-ish cells that follow the column width instead of a fixed height,
            // so six of them still fit comfortably on a 360dp screen.
            .aspectRatio(0.78f)
            .clip(RoundedCornerShape(d.radiusSm))
            .background(Color.White)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Crimson else Hairline,
                shape = RoundedCornerShape(d.radiusSm)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (digit != null) {
            Text(
                digit,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Normal,
                color = Ink
            )
        } else if (focused) {
            Box(
                Modifier
                    .width(2.dp)
                    .height(d.scaled(26))
                    .clip(CircleShape)
                    .background(Crimson)
            )
        }
    }
}

private fun formatMmSs(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%02d:%02d".format(m, s)
}
