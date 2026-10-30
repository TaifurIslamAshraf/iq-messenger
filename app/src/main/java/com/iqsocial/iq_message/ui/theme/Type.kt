package com.iqsocial.iq_message.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The mockups use a geometric grotesque with tight tracking on display sizes.
 * The system default is kept (no bundled font files) and the scale is tuned to match.
 *
 * Sizes are multiplied by the window-derived font scale so headlines shrink on small
 * phones instead of wrapping mid-word.
 */
private val Sans = FontFamily.Default

/**
 * What a message is read at: [Typography.bodyLarge] and a third again.
 *
 * Its own style rather than a bigger `bodyLarge`, because the two are not the same
 * job. `bodyLarge` is the app's ordinary running text - list rows, field contents,
 * banners - and everything there is laid out around its size. A message is the thing
 * the screen exists for, and it is read at arm's length in a hurry.
 *
 * Derived rather than written out, so it keeps the window-derived scale that stops
 * headlines wrapping mid-word on a small phone.
 */
val Typography.messageBody: TextStyle
    get() = bodyLarge.copy(
        fontSize = bodyLarge.fontSize * MESSAGE_SCALE,
        lineHeight = bodyLarge.lineHeight * MESSAGE_SCALE
    )

// A third again was too much; fifteen per cent off that lands here.
private const val MESSAGE_SCALE = 1.1f

/**
 * The emoji in the reaction tray.
 *
 * Bigger than the label style they were borrowing, because they are not labels -
 * they are the targets, and the only thing in that row carrying meaning. At
 * `titleLarge` they were the size of a word in a list.
 *
 * Only the glyph grows. The circles around them stay as they are: seven of them
 * across a tray already come to 280dp, and widening those would push the row off the
 * edge of a narrow phone - where a reaction that cannot be reached is worse than a
 * small one.
 *
 * The line height goes up with the size so a tall emoji is not clipped at its top.
 */
val Typography.reactionTray: TextStyle
    get() = titleLarge.copy(
        fontSize = titleLarge.fontSize * REACTION_SCALE,
        lineHeight = titleLarge.lineHeight * REACTION_SCALE
    )

private const val REACTION_SCALE = 1.3f

fun iqTypography(s: Float = 1f): Typography = Typography(
    displaySmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Bold,
        fontSize = (38 * s).sp, lineHeight = (44 * s).sp, letterSpacing = (-1).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Bold,
        fontSize = (29 * s).sp, lineHeight = (35 * s).sp, letterSpacing = (-0.7).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Bold,
        fontSize = (24 * s).sp, lineHeight = (30 * s).sp, letterSpacing = (-0.5).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Bold,
        fontSize = (20 * s).sp, lineHeight = (26 * s).sp, letterSpacing = (-0.3).sp
    ),
    titleLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = (17.5f * s).sp, lineHeight = (23 * s).sp, letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = (15.5f * s).sp, lineHeight = (21 * s).sp, letterSpacing = (-0.1).sp
    ),
    titleSmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium,
        fontSize = (14 * s).sp, lineHeight = (19 * s).sp
    ),
    bodyLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = (15 * s).sp, lineHeight = (21 * s).sp, letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = (13.5f * s).sp, lineHeight = (19 * s).sp, letterSpacing = 0.sp
    ),
    bodySmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = (12 * s).sp, lineHeight = (16 * s).sp
    ),
    labelLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = (14.5f * s).sp, lineHeight = (19 * s).sp
    ),
    labelMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium,
        fontSize = (12 * s).sp, lineHeight = (15 * s).sp
    ),
    labelSmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium,
        fontSize = (10.5f * s).sp, lineHeight = (13 * s).sp
    )
)
