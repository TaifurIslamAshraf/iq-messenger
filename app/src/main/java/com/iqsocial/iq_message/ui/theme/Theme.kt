package com.iqsocial.iq_message.ui.theme

import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val IqDarkColorScheme = darkColorScheme(
    primary = Crimson,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3B1422),
    onPrimaryContainer = Color(0xFFFFB3C6),
    secondary = Sunset,
    onSecondary = Color.White,
    tertiary = OnlineGreen,
    onTertiary = Color.White,
    background = Color(0xFF0E0E10),
    onBackground = Color(0xFFF1F1F4),
    surface = Color(0xFF161619),
    onSurface = Color(0xFFF1F1F4),
    surfaceVariant = Color(0xFF24252A),
    onSurfaceVariant = Color(0xFFA0A2AA),
    surfaceContainerLowest = Color(0xFF0E0E10),
    surfaceContainerLow = Color(0xFF161619),
    surfaceContainer = Color(0xFF1C1D21),
    surfaceContainerHigh = Color(0xFF222328),
    surfaceContainerHighest = Color(0xFF2A2B30),
    outline = Color(0xFF2C2D33),
    outlineVariant = Color(0xFF2C2D33),
    error = Crimson,
    onError = Color.White,
    scrim = Color(0x99000000)
)

private val IqColorScheme = lightColorScheme(
    primary = Crimson,
    onPrimary = Color.White,
    primaryContainer = CrimsonTint,
    onPrimaryContainer = CrimsonDeep,
    secondary = Sunset,
    onSecondary = Color.White,
    tertiary = OnlineGreen,
    onTertiary = Color.White,
    background = CanvasGrey,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = FieldGrey,
    onSurfaceVariant = InkMuted,
    outline = Hairline,
    outlineVariant = Hairline,
    error = Crimson,
    onError = Color.White,
    scrim = Color(0x99000000)
)

/**
 * The product is a single fixed light identity — the dark screens (calls) paint
 * themselves, so the scheme never flips with the system setting.
 *
 * Wraps the app in [BoxWithConstraints] so the real window size can drive both the
 * dimension tokens and the type scale.
 */
@Composable
fun IQMessageTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    BoxWithConstraints {
        IQMessageTheme(maxWidth.value, maxHeight.value, content)
    }
}

/**
 * The same theme for a window that already knows how big it is.
 *
 * [BoxWithConstraints] measures by subcomposing, and a subcomposition is the one
 * thing the floating panel could not survive: composing the chat inside one, in a
 * window handed to the window manager, tore the slot table apart on its first
 * measure -
 *
 *     NullPointerException: RememberObserverHolder.getWrapped()
 *       at RememberEventDispatcher.dispatchRememberList
 *
 * - while the very same content composed without it was fine. A window created by
 * this app knows its own size without asking the layout, so it says so and there is
 * no subcomposition to go wrong.
 */
@Composable
fun IQMessageTheme(widthDp: Float, heightDp: Float, content: @Composable () -> Unit) {
    // Before anything is drawn, so a dark-mode phone never flashes a white frame.
    val appContext = LocalContext.current.applicationContext
    remember(appContext) { ThemeMode.init(appContext) }
    val dimens = remember(widthDp, heightDp) { buildIqDimens(widthDp, heightDp) }
    val typography = remember(dimens.fontScale) { iqTypography(dimens.fontScale) }

    CompositionLocalProvider(LocalIqDimens provides dimens) {
        MaterialTheme(
            colorScheme = if (ThemeMode.dark) IqDarkColorScheme else IqColorScheme,
            typography = typography,
            content = content
        )
    }
}

/**
 * Drives status-bar icon colour per screen: dark icons over light surfaces,
 * light icons over the crimson headers and the black call screens.
 */
/**
 * False inside a screen that is composed but not on show, so its [SystemBarIcons]
 * leaves the bars to the screen that is.
 */
val LocalBarsOwner = compositionLocalOf { true }

@Composable
fun SystemBarIcons(darkIcons: Boolean) {
    val view = LocalView.current
    if (LocalInspectionMode.current) return
    // A screen kept alive behind another - a home tab not being looked at - must
    // not recolour the bars under the one that is.
    if (!LocalBarsOwner.current) return
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            // Dark icons are asked for over a light page; in dark mode that page
            // is dark, and the icons have to be light to be seen.
            val light = darkIcons && !ThemeMode.dark
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }
}
