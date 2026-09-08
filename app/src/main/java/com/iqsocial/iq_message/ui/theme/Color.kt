package com.iqsocial.iq_message.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/* ---- Brand ------------------------------------------------------------- */

val Crimson = Color(0xFFE8114C)
val CrimsonDeep = Color(0xFFC9003F)
val CrimsonBright = Color(0xFFFF2D63)
val CrimsonTint = Color(0xFFFDE6EC)
val CrimsonTintStrong = Color(0xFFFBD2DE)

val Sunset = Color(0xFFF4784A)
val SunsetLight = Color(0xFFF9A273)

/* ---- Neutrals ---------------------------------------------------------- */

val Ink = Color(0xFF121214)
val InkSoft = Color(0xFF2A2B30)
val InkMuted = Color(0xFF6C6E76)
val InkFaint = Color(0xFF9B9DA5)
val Hairline = Color(0xFFEAEAEE)
val FieldGrey = Color(0xFFF0F0F3)
val CanvasGrey = Color(0xFFF7F7F9)
val ChatCanvas = Color(0xFFF6F6F7)

/* ---- Status ------------------------------------------------------------ */

val OnlineGreen = Color(0xFF17C24A)
val AcceptGreen = Color(0xFF16C647)
val ProtectedGreen = Color(0xFF0FA958)

/* ---- Dark call surfaces ------------------------------------------------ */

val CallBlack = Color(0xFF0A0A0B)
val CallPanel = Color(0xFF1B1C1F)
val CallPanelSoft = Color(0xFF2A2B2F)

/* ---- Gradients --------------------------------------------------------- */

/** Flat brand wash used on the onboarding hero. */
val BrandVertical = Brush.verticalGradient(listOf(Crimson, CrimsonDeep))

/** Crimson -> sunset sweep used on the OTP and profile headers. */
val BrandSunset = Brush.linearGradient(
    colors = listOf(CrimsonDeep, Crimson, Sunset, SunsetLight),
    start = androidx.compose.ui.geometry.Offset(0f, 600f),
    end = androidx.compose.ui.geometry.Offset(900f, 0f)
)

/** Background of the onboarding sheets and the notch-label backdrop. */
val SheetSurface = Color(0xFFFBFBFC)
