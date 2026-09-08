package com.iqsocial.iq_message.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import com.iqsocial.iq_message.ui.components.CircleIconButton
import com.iqsocial.iq_message.ui.components.SheetHandle
import com.iqsocial.iq_message.ui.components.drawHeroBokeh
import com.iqsocial.iq_message.ui.components.sheetShape
import com.iqsocial.iq_message.ui.icons.IqIcons
import com.iqsocial.iq_message.ui.theme.BrandVertical
import com.iqsocial.iq_message.ui.theme.Crimson
import com.iqsocial.iq_message.ui.theme.Hairline
import com.iqsocial.iq_message.ui.theme.Ink
import com.iqsocial.iq_message.ui.theme.SheetSurface
import com.iqsocial.iq_message.ui.theme.InkFaint
import com.iqsocial.iq_message.ui.theme.InkMuted
import com.iqsocial.iq_message.ui.theme.IqTheme
import com.iqsocial.iq_message.ui.theme.SystemBarIcons

@Composable
fun PhoneScreen(
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    SystemBarIcons(darkIcons = false)
    val d = IqTheme.dimens
    var number by remember { mutableStateOf("") }

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
            // The mockup gives the hero a little over half the screen before the
            // sheet starts, so it is sized as a share of the window rather than
            // just wrapping its content.
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = d.screenHeight * 0.57f)
                    .background(BrandVertical)
            ) {
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
                    StepPill("1 of 2")
                }

                Spacer(Modifier.height(d.gapXxl))
                BrandLockup(color = Color.White, markSize = d.scaled(32))

                Spacer(Modifier.height(d.gapLg))
                // In the mockup the artwork sits beside the headline and bleeds off
                // the right screen edge, rather than stacking underneath it.
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val artW = maxWidth * 0.60f
                    SpeechBubblesArt(
                        Modifier
                            .align(Alignment.CenterEnd)
                            .offset(x = d.gutter)
                            .width(artW)
                            .height(artW * 0.707f)
                    )
                    Column(Modifier.fillMaxWidth(0.58f)) {
                        Text(
                            "Let's get you\nconnected",
                            style = MaterialTheme.typography.headlineLarge,
                            color = Color.White
                        )
                        Spacer(Modifier.height(d.gapMd))
                        Text(
                            "Secure messaging starts\nwith your number.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.92f)
                        )
                    }
                }
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
                CenteredColumn(topPadding = d.gapMd) {
                    SheetHandle(Modifier.align(Alignment.CenterHorizontally))
                    Spacer(Modifier.height(d.gapXl))

                    Text(
                        "Continue with phone",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Ink
                    )
                    Spacer(Modifier.height(d.gapSm))
                    Text(
                        "We'll text you a one-time verification code.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = InkMuted
                    )
                    Spacer(Modifier.height(d.gapMd))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(IqIcons.Shield, null, Modifier.size(d.iconSm), tint = Ink)
                        Spacer(Modifier.width(d.gapSm))
                        Text(
                            "Private & secure",
                            style = MaterialTheme.typography.titleSmall,
                            color = Ink
                        )
                    }

                    Spacer(Modifier.height(d.gapLg))
                    PhoneField(
                        value = number,
                        onValueChange = { input -> number = input.filter(Char::isDigit).take(11) }
                    )

                    Spacer(Modifier.height(d.gapMd))
                    Text(
                        "Use email instead",
                        style = MaterialTheme.typography.titleSmall,
                        color = Crimson,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .align(Alignment.End)
                            .clip(RoundedCornerShape(d.radiusSm))
                            .clickable(onClick = onContinue)
                            .padding(horizontal = d.gapSm, vertical = d.gapSm)
                    )

                    Spacer(Modifier.height(d.gapMd))
                    PrimaryButton(text = "Continue", onClick = onContinue)

                    Spacer(Modifier.height(d.gapLg))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(IqIcons.Lock, null, Modifier.size(d.iconXs), tint = Ink)
                        Spacer(Modifier.width(d.gapSm))
                        Text(
                            "Your number is never shown publicly.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkMuted
                        )
                    }

                    Spacer(Modifier.height(d.gapMd))
                    Text(
                        text = buildAnnotatedString {
                            append("By continuing, you agree to our ")
                            withStyle(SpanStyle(color = Ink, fontWeight = FontWeight.Bold)) {
                                append("Terms")
                            }
                            append(" & ")
                            withStyle(SpanStyle(color = Ink, fontWeight = FontWeight.Bold)) {
                                append("Privacy Policy.")
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(d.gapMd))
                    Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
                }
            }
        }
    }
}

/* -- shared onboarding pieces ---------------------------------------------- */

/**
 * Page column: gutters on phones, capped and centred on tablets so a phone layout
 * never stretches across a wide screen.
 */
@Composable
fun CenteredColumn(
    modifier: Modifier = Modifier,
    topPadding: Dp = 0.dp,
    applyStatusBars: Boolean = false,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val d = IqTheme.dimens
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier
                .fillMaxWidth()
                .widthIn(max = d.contentMaxWidth)
                .then(
                    if (applyStatusBars) Modifier.windowInsetsPadding(WindowInsets.statusBars)
                    else Modifier
                )
                .padding(horizontal = d.gutter)
                .padding(top = topPadding),
            horizontalAlignment = horizontalAlignment,
            content = content
        )
    }
}

@Composable
fun StepPill(text: String) {
    val d = IqTheme.dimens
    Box(
        Modifier
            .border(1.5.dp, Color.White.copy(alpha = 0.55f), CircleShape)
            .background(Color.White.copy(alpha = 0.12f), CircleShape)
            .padding(horizontal = d.gapLg, vertical = d.gapSm)
    ) {
        Text(text, color = Color.White, style = MaterialTheme.typography.titleSmall)
    }
}

/** The two-bubble mark plus the "IQ Social" wordmark. */
@Composable
fun BrandLockup(
    color: Color,
    markSize: Dp,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit.Unspecified
) {
    val d = IqTheme.dimens
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(markSize)) {
            val s = size.minDimension
            val stroke = Stroke(s * 0.085f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawCircle(color, radius = s * 0.29f, center = Offset(s * 0.32f, s * 0.42f), style = stroke)
            drawCircle(color, radius = s * 0.29f, center = Offset(s * 0.66f, s * 0.50f), style = stroke)
            drawLine(
                color, Offset(s * 0.14f, s * 0.62f), Offset(s * 0.10f, s * 0.86f),
                s * 0.085f, cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            drawLine(
                color, Offset(s * 0.62f, s * 0.78f), Offset(s * 0.72f, s * 0.92f),
                s * 0.085f, cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
        Spacer(Modifier.width(d.gapSm))
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("IQ ") }
                withStyle(SpanStyle(fontWeight = FontWeight.Normal)) { append("Social") }
            },
            color = color,
            style = MaterialTheme.typography.headlineSmall,
            fontSize = fontSize,
            maxLines = 1
        )
    }
}

/** Large outlined speech-bubble illustration on the phone-entry hero. */
@Composable
private fun SpeechBubblesArt(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val stroke = Stroke(
            width = w * 0.024f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        drawPath(bubblePath(w, 0.472f, 0.263f, 0.270f, mirror = false), Color.White, style = stroke)
        drawPath(bubblePath(w, 0.694f, 0.423f, 0.259f, mirror = true), Color.White, style = stroke)
    }
}

/*
 * Speech-bubble outline: one closed path so the beak flows out of the body with no
 * seam. Angles are parametric degrees measured off the mockup — the body arc leaves
 * at 134.5 deg, rejoins at 115.2 deg, and the tip sits at 130 deg, 1.37 radii out.
 */
private const val BUBBLE_ARC_A = 134.5f
private const val BUBBLE_ARC_B = 115.2f
private const val BUBBLE_TIP = 130.0f
private const val BUBBLE_TIP_LEN = 1.37f
private const val BUBBLE_SWEEP = 340.7f

private fun bubblePath(
    unit: Float,
    cxF: Float,
    cyF: Float,
    rF: Float,
    mirror: Boolean
): Path {
    val cx = cxF * unit
    val cy = cyF * unit
    val r = rF * unit

    fun ang(t: Float) = if (mirror) 180f - t else t
    fun pt(t: Float, k: Float = 1f): Offset {
        val a = Math.toRadians(t.toDouble())
        return Offset(cx + k * r * cos(a).toFloat(), cy + k * r * sin(a).toFloat())
    }
    fun lerp(u: Offset, v: Offset, f: Float) =
        Offset(u.x + (v.x - u.x) * f, u.y + (v.y - u.y) * f)

    val a = ang(BUBBLE_ARC_A)
    val b = ang(BUBBLE_ARC_B)
    val tipAngle = ang(BUBBLE_TIP)
    val sweep = if (mirror) -BUBBLE_SWEEP else BUBBLE_SWEEP

    val pA = pt(a)
    val pB = pt(b)
    val tip = pt(tipAngle, BUBBLE_TIP_LEN)
    // The beak's long edge runs from the rejoin point out to the tip; the short
    // edge snaps back to the body. Near-straight, gently bowed.
    val c1 = lerp(pB, tip, 0.42f)
    val c2 = lerp(pB, tip, 0.78f)
    val c3 = lerp(tip, pA, 0.30f)
    val c4 = lerp(tip, pA, 0.68f)

    return Path().apply {
        moveTo(pA.x, pA.y)
        arcTo(Rect(cx - r, cy - r, cx + r, cy + r), a, sweep, false)
        cubicTo(c1.x, c1.y, c2.x, c2.y, tip.x, tip.y)
        cubicTo(c3.x, c3.y, c4.x, c4.y, pA.x, pA.y)
        close()
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showArrow: Boolean = true
) {
    val d = IqTheme.dimens
    Box(
        modifier
            .fillMaxWidth()
            .height(d.buttonHeight)
            .clip(RoundedCornerShape(d.radiusMd))
            .background(Crimson)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // Label centred, arrow pinned to the trailing edge. A Row of two competing
        // weights split the space 50/50 and clipped "Verify & Continue" to "Verify &".
        Text(
            text,
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = d.gapXxl)
        )
        if (showArrow) {
            Icon(
                IqIcons.ArrowForward, null,
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = d.gapXl)
                    .size(d.iconLg),
                tint = Color.White
            )
        }
    }
}

@Composable
private fun PhoneField(value: String, onValueChange: (String) -> Unit) {
    val d = IqTheme.dimens
    val labelHalf = d.scaled(8)
    // Outer box reserves room above the field so the notched label has somewhere to
    // sit; drawing it as a later sibling guarantees it paints over the border.
    Box(Modifier.fillMaxWidth()) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = labelHalf)
            .height(d.fieldHeightLg)
            .border(1.dp, Hairline, RoundedCornerShape(d.radiusMd))
            .background(Color.White, RoundedCornerShape(d.radiusMd))
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = d.gapMd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BangladeshFlag()
            Spacer(Modifier.width(d.gapSm))
            Text(
                "+880",
                style = MaterialTheme.typography.titleLarge,
                color = Ink,
                maxLines = 1
            )
            Icon(IqIcons.ChevronDown, null, Modifier.size(d.iconSm), tint = InkMuted)
            Spacer(Modifier.width(d.gapSm))
            Box(
                Modifier
                    .width(1.dp)
                    .height(d.scaled(30))
                    .background(Hairline)
            )
            Spacer(Modifier.width(d.gapMd))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        "1XX XXX XXXX",
                        style = MaterialTheme.typography.titleLarge,
                        color = InkFaint,
                        maxLines = 1
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = MaterialTheme.typography.titleLarge.copy(color = Ink),
                    cursorBrush = SolidColor(Crimson),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
        // Notch label: sibling of the field, so it paints after the border, and its
        // backdrop is the sheet colour because that is what shows above the field.
        Text(
            "Phone number",
            style = MaterialTheme.typography.bodySmall,
            color = InkMuted,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = d.gapMd)
                .background(SheetSurface)
                .padding(horizontal = d.gapXs)
        )
    }
}

@Composable
private fun BangladeshFlag() {
    val d = IqTheme.dimens
    Box(
        Modifier
            .size(width = d.scaled(30), height = d.scaled(21))
            .clip(RoundedCornerShape(d.scaled(3)))
            .background(Color(0xFF006A4E)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(d.scaled(12))
                .clip(CircleShape)
                .background(Color(0xFFF42A41))
        )
    }
}
