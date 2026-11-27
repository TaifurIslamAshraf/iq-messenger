package com.iqsocial.iq_message.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/* ---- Brand ------------------------------------------------------------- */

val Crimson = Color(0xFFE8114C)
val CrimsonDeep = Color(0xFFC9003F)
val CrimsonBright = Color(0xFFFF2D63)
val CrimsonTint: Color get() = if (ThemeMode.dark) Color(0xFF3B1422) else Color(0xFFFDE6EC)
val CrimsonTintStrong: Color
    get() = if (ThemeMode.dark) Color(0xFF52192D) else Color(0xFFFBD2DE)

val Sunset = Color(0xFFF4784A)
val SunsetLight = Color(0xFFF9A273)

/* ---- Neutrals ---------------------------------------------------------- */

// Each of these is read through [ThemeMode], so every composable that draws with
// one is redrawn when dark mode is switched - and the hundreds of places that
// already say `Ink` or `CanvasGrey` follow without being touched.

val Ink: Color get() = if (ThemeMode.dark) Color(0xFFF1F1F4) else Color(0xFF121214)
val InkSoft: Color get() = if (ThemeMode.dark) Color(0xFFD9DAE0) else Color(0xFF2A2B30)
val InkMuted: Color get() = if (ThemeMode.dark) Color(0xFFA0A2AA) else Color(0xFF6C6E76)
val InkFaint: Color get() = if (ThemeMode.dark) Color(0xFF76787F) else Color(0xFF9B9DA5)
val Hairline: Color get() = if (ThemeMode.dark) Color(0xFF2C2D33) else Color(0xFFEAEAEE)
val FieldGrey: Color get() = if (ThemeMode.dark) Color(0xFF24252A) else Color(0xFFF0F0F3)
val CanvasGrey: Color get() = if (ThemeMode.dark) Color(0xFF0E0E10) else Color(0xFFF7F7F9)
val ChatCanvas: Color get() = if (ThemeMode.dark) Color(0xFF0B0B0D) else Color(0xFFF6F6F7)

/**
 * Pages, cards, sheets and dialogs: white in light mode, near-black in dark.
 *
 * What `Color.White` used to be written as wherever it meant "the surface" rather
 * than "white" - white text on crimson and the call screens' buttons are still
 * plain white.
 */
val Paper: Color get() = if (ThemeMode.dark) Color(0xFF161619) else Color.White

/** The slightly-off-white panels inside a page. */
val PaperSoft: Color get() = if (ThemeMode.dark) Color(0xFF1C1D21) else Color(0xFFFBFAFA)

/** A dark round button with a white icon, which must stay dark on a dark page too. */
val InkButton: Color get() = if (ThemeMode.dark) Color(0xFF3A3B42) else Color(0xFF121214)

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
val SheetSurface: Color get() = if (ThemeMode.dark) Color(0xFF18191C) else Color(0xFFFBFBFC)
