package com.iqsocial.iq_message.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import com.iqsocial.iq_message.data.Country
import com.iqsocial.iq_message.ui.components.CountryChip
import com.iqsocial.iq_message.ui.components.CountryPickerSheet
import com.iqsocial.iq_message.data.auth.AuthRepository
import com.iqsocial.iq_message.data.auth.SessionStore
import com.iqsocial.iq_message.ui.components.CircleIconButton
import com.iqsocial.iq_message.ui.components.SheetHandle
import com.iqsocial.iq_message.ui.components.drawHeroBokeh
import com.iqsocial.iq_message.ui.components.sheetShape
import com.iqsocial.iq_message.ui.icons.IqIcons
import com.iqsocial.iq_message.ui.theme.BrandVertical
import com.iqsocial.iq_message.ui.theme.Crimson
import com.iqsocial.iq_message.ui.theme.Hairline
import com.iqsocial.iq_message.ui.theme.Ink
import com.iqsocial.iq_message.ui.theme.InkFaint
import com.iqsocial.iq_message.ui.theme.InkMuted
import com.iqsocial.iq_message.ui.theme.IqTheme
import com.iqsocial.iq_message.ui.theme.SheetSurface
import com.iqsocial.iq_message.ui.theme.SystemBarIcons

@Composable
fun rememberAuthRepository(): AuthRepository {
    val context = LocalContext.current
    return remember { AuthRepository(SessionStore(context)) }
}

/**
 * Crimson hero over a rounded white sheet — the same shape as the onboarding
 * screens, so login, signup and password reset sit in one family.
 */
@Composable
fun AuthScaffold(
    title: String,
    subtitle: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    sheet: @Composable ColumnScope.() -> Unit
) {
    SystemBarIcons(darkIcons = false)
    val d = IqTheme.dimens

    Box(
        modifier
            .fillMaxSize()
            .background(SheetSurface)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = d.screenHeight * 0.30f)
                    .background(BrandVertical)
            ) {
                Canvas(Modifier.matchParentSize()) { drawHeroBokeh() }
                CenteredColumn(applyStatusBars = true) {
                    Spacer(Modifier.height(d.gapMd))
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onBack != null) {
                            CircleIconButton(
                                icon = IqIcons.ArrowBack,
                                contentDescription = "Back",
                                onClick = onBack,
                                background = Color.White.copy(alpha = 0.20f),
                                tint = Color.White
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        trailing?.invoke()
                    }

                    Spacer(Modifier.height(d.gapLg))
                    BrandLockup(color = Color.White, markSize = d.scaled(28))
                    Spacer(Modifier.height(d.gapLg))
                    Text(title, style = MaterialTheme.typography.headlineLarge, color = Color.White)
                    Spacer(Modifier.height(d.gapSm))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.92f)
                    )
                    Spacer(Modifier.height(d.gapXl + d.sheetRadius))
                }
            }

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
                    sheet()
                    Spacer(Modifier.height(d.gapXl))
                    Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
                }
            }
        }
    }
}

/**
 * Outlined field whose border is drawn with a **gap** for the label.
 *
 * The label is not painted on top of the border with a masking chip — that only
 * works when the field, the label's chip and whatever is behind them are all the
 * same colour, and it leaves a visible rectangle the moment they are not. Here the
 * outline is a path that simply stops either side of the label, so there is nothing
 * to mask and nothing to match.
 *
 * The label's own height is measured, so the notch is exactly as wide as the text
 * and the whole component reserves room for the half that sits above the border —
 * nothing is ever drawn outside the composable's bounds.
 */
@Composable
private fun NotchedField(
    label: String,
    focused: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val d = IqTheme.dimens
    // Animated so the change reads as a transition rather than a flicker.
    val outline by animateColorAsState(
        targetValue = if (focused) Crimson else Hairline,
        label = "fieldOutline"
    )
    val outlineWidth by animateDpAsState(
        targetValue = if (focused) 2.dp else 1.dp,
        label = "fieldOutlineWidth"
    )
    val labelColor by animateColorAsState(
        targetValue = if (focused) Crimson else InkMuted,
        label = "fieldLabel"
    )
    val density = LocalDensity.current
    val labelStyle = MaterialTheme.typography.bodySmall
    val measurer = rememberTextMeasurer()

    val measured = remember(label, labelStyle, density) {
        measurer.measure(AnnotatedString(label), labelStyle)
    }
    val labelWidth = with(density) { measured.size.width.toDp() }
    val labelHeight = with(density) { measured.size.height.toDp() }

    val corner = d.radiusMd
    val breathe = d.gapXs        // clear space either side of the text in the notch
    // The notch has to open after the corner arc ends, or drawNotchedOutline clamps
    // its start and the border runs under the first few dp of the label. Deriving the
    // inset from the corner keeps that impossible, and the content uses the same
    // value so the label lines up with the flag and the typed text.
    val inset = corner + d.gapSm

    Box(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = labelHeight / 2)
                .heightIn(min = d.fieldHeightLg)
                .background(SheetSurface, RoundedCornerShape(corner))
                .drawBehind {
                    drawNotchedOutline(
                        gapStart = (inset - breathe).toPx(),
                        gapEnd = (inset + labelWidth + breathe).toPx(),
                        corner = corner.toPx(),
                        stroke = outlineWidth.toPx(),
                        color = outline
                    )
                }
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = d.fieldHeightLg)
                    .padding(horizontal = inset),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }

        // Vertically centred on the border because the field starts labelHeight/2 down.
        Text(
            label,
            style = labelStyle,
            color = labelColor,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = inset)
        )
    }
}

/** Rounded-rectangle outline with a horizontal gap in the top edge for the label. */
private fun DrawScope.drawNotchedOutline(
    gapStart: Float,
    gapEnd: Float,
    corner: Float,
    stroke: Float,
    color: Color
) {
    val w = size.width
    val h = size.height
    val r = corner.coerceAtMost(minOf(w, h) / 2f)
    val from = gapStart.coerceIn(r, w - r)
    val to = gapEnd.coerceIn(from, w - r)

    val path = Path().apply {
        // start just right of the notch, run clockwise all the way back to its left edge
        moveTo(to, 0f)
        lineTo(w - r, 0f)
        arcTo(Rect(w - 2 * r, 0f, w, 2 * r), -90f, 90f, false)
        lineTo(w, h - r)
        arcTo(Rect(w - 2 * r, h - 2 * r, w, h), 0f, 90f, false)
        lineTo(r, h)
        arcTo(Rect(0f, h - 2 * r, 2 * r, h), 90f, 90f, false)
        lineTo(0f, r)
        arcTo(Rect(0f, 0f, 2 * r, 2 * r), 180f, 90f, false)
        lineTo(from, 0f)
    }
    drawPath(path, color, style = Stroke(width = stroke))
}

@Composable
fun AuthField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    enabled: Boolean = true
) {
    val d = IqTheme.dimens
    var revealed by remember { mutableStateOf(false) }
    val hidden = isPassword && !revealed
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()

    NotchedField(label = label, focused = focused, modifier = modifier) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    placeholder,
                    style = fieldTextStyle(),
                    color = InkFaint,
                    maxLines = 1
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = fieldTextStyle().copy(color = Ink),
                cursorBrush = SolidColor(Crimson),
                interactionSource = interactions,
                visualTransformation =
                    if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (isPassword) {
            Spacer(Modifier.width(d.gapSm))
            Icon(
                imageVector = if (revealed) IqIcons.EyeOff else IqIcons.Eye,
                contentDescription = if (revealed) "Hide password" else "Show password",
                tint = InkMuted,
                modifier = Modifier
                    .size(d.icon)
                    .clip(CircleShape)
                    .clickable { revealed = !revealed }
            )
        }
    }
}

/** One size for every value typed into an auth field, matched to the mockup. */
@Composable
private fun fieldTextStyle() = MaterialTheme.typography.bodyLarge.copy(
    fontWeight = FontWeight.Medium
)

/**
 * Phone entry with a country selector.
 *
 * [value] is the national number as typed; call [Country.composeForApi] on submit
 * to get the E.164 string the server expects (`+8801792011775`). Keeping the two
 * apart lets the field show a friendly national number while the API always gets
 * the fully-qualified form.
 */
@Composable
fun PhoneNumberField(
    country: Country,
    onCountryChange: (Country) -> Unit,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Phone number",
    enabled: Boolean = true
) {
    val d = IqTheme.dimens
    var pickerOpen by remember { mutableStateOf(false) }
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()

    NotchedField(label = label, focused = focused, modifier = modifier) {
        CountryChip(
            country = country,
            onClick = { if (enabled) pickerOpen = true },
            enabled = enabled
        )
        Spacer(Modifier.width(d.gapSm))
        Box(
            Modifier
                .width(1.dp)
                .height(d.scaled(28))
                .background(Hairline)
        )
        Spacer(Modifier.width(d.gapMd))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    if (country.iso == "BD") "1XXX XXXXXX" else "Phone number",
                    style = fieldTextStyle(),
                    color = InkFaint,
                    maxLines = 1
                )
            }
            BasicTextField(
                value = value,
                onValueChange = { onValueChange(it.filter(Char::isDigit).take(15)) },
                enabled = enabled,
                singleLine = true,
                textStyle = fieldTextStyle().copy(color = Ink),
                cursorBrush = SolidColor(Crimson),
                interactionSource = interactions,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (pickerOpen) {
        CountryPickerSheet(
            selected = country,
            onDismiss = { pickerOpen = false },
            onSelect = {
                onCountryChange(it)
                pickerOpen = false
            }
        )
    }
}


/** Primary action with an inline spinner, so the button is the only busy signal. */
@Composable
fun AuthButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    enabled: Boolean = true
) {
    val d = IqTheme.dimens
    val active = enabled && !loading
    Box(
        modifier
            .fillMaxWidth()
            .height(d.buttonHeight)
            .clip(RoundedCornerShape(d.radiusMd))
            .background(if (active) Crimson else Crimson.copy(alpha = 0.55f))
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
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = d.gapLg),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(d.gapMd))
                Icon(IqIcons.ArrowForward, null, Modifier.size(d.icon), tint = Color.White)
            }
        }
    }
}

/** Inline error strip under a form. */
@Composable
fun AuthError(message: String?, modifier: Modifier = Modifier) {
    val d = IqTheme.dimens
    if (message.isNullOrBlank()) return
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(d.radiusSm))
            .background(Crimson.copy(alpha = 0.08f))
            .padding(horizontal = d.gapMd, vertical = d.gapSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(IqIcons.HelpCircle, null, Modifier.size(d.iconSm), tint = Crimson)
        Spacer(Modifier.width(d.gapSm))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = Crimson)
    }
}

/** "Already have an account? Log in" style footer. */
@Composable
fun AuthSwitchRow(
    prompt: String,
    action: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val d = IqTheme.dimens
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(prompt, style = MaterialTheme.typography.bodyLarge, color = InkMuted)
        Spacer(Modifier.width(d.gapXs))
        Text(
            action,
            style = MaterialTheme.typography.titleMedium,
            color = Crimson,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(d.radiusSm))
                .clickable(onClick = onClick)
                .padding(horizontal = d.gapSm, vertical = d.gapXs)
        )
    }
}
