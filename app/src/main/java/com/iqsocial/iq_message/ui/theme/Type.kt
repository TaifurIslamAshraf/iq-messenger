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
