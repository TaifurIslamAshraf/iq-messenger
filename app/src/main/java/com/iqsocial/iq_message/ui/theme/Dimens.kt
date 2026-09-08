package com.iqsocial.iq_message.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Every size in the UI derives from here instead of being a literal.
 *
 * The mockups were drawn at 411dp wide, so that is the reference: on a 360dp phone
 * everything shrinks ~12%, on a tablet it grows a little and then stops. Text scales
 * on a gentler curve than boxes do (see [fontScale]) because type that tracks layout
 * width one-for-one gets unreadable at the small end.
 */
@Immutable
data class IqDimens(
    val scale: Float,
    val fontScale: Float,
    val isCompact: Boolean,
    val isWide: Boolean,

    // page rhythm
    val gutter: Dp,
    val gutterTight: Dp,
    val gapXs: Dp,
    val gapSm: Dp,
    val gapMd: Dp,
    val gapLg: Dp,
    val gapXl: Dp,
    val gapXxl: Dp,

    // corners
    val radiusSm: Dp,
    val radiusMd: Dp,
    val radiusLg: Dp,
    val radiusXl: Dp,
    val sheetRadius: Dp,

    // glyphs & hit targets
    val iconXs: Dp,
    val iconSm: Dp,
    val icon: Dp,
    val iconLg: Dp,
    val iconXl: Dp,
    val touchTarget: Dp,
    val iconButton: Dp,
    val iconButtonSm: Dp,
    val iconButtonLg: Dp,

    // inputs
    val fieldHeight: Dp,
    val fieldHeightLg: Dp,
    val buttonHeight: Dp,

    // avatars
    val avatarSm: Dp,
    val avatarMd: Dp,
    val avatarLg: Dp,
    val avatarXl: Dp,
    val avatarHero: Dp,

    // one-offs that recur
    val fab: Dp,
    val callControl: Dp,
    val callControlLg: Dp,
    val callAnswer: Dp,
    val callEnd: Dp,
    val navIcon: Dp,

    /** Usable window height, for panels sized as a share of the screen. */
    val screenHeight: Dp,

    /** Caps line length so wide screens do not stretch a phone layout across a tablet. */
    val contentMaxWidth: Dp
) {
    /** Scales an arbitrary one-off value on the same curve as the tokens. */
    fun scaled(value: Int): Dp = (value * scale).dp
}

private const val REFERENCE_WIDTH = 411f

fun buildIqDimens(widthDp: Float, heightDp: Float): IqDimens {
    val raw = widthDp / REFERENCE_WIDTH
    // Short screens (landscape, small phones) get a touch more breathing room.
    val heightFactor = if (heightDp < 700f) 0.94f else 1f
    val scale = (raw * heightFactor).coerceIn(0.80f, 1.16f)
    val fontScale = raw.coerceIn(0.88f, 1.08f)

    fun d(value: Float): Dp = (value * scale).dp

    return IqDimens(
        scale = scale,
        fontScale = fontScale,
        isCompact = widthDp < 360f,
        isWide = widthDp >= 600f,

        gutter = d(20f),
        gutterTight = d(14f),
        gapXs = d(4f),
        gapSm = d(8f),
        gapMd = d(12f),
        gapLg = d(18f),
        gapXl = d(26f),
        gapXxl = d(38f),

        radiusSm = d(10f),
        radiusMd = d(14f),
        radiusLg = d(20f),
        radiusXl = d(26f),
        sheetRadius = d(30f),

        iconXs = d(15f),
        iconSm = d(18f),
        icon = d(22f),
        iconLg = d(26f),
        iconXl = d(32f),
        touchTarget = d(48f).coerceAtLeast(48.dp),
        iconButton = d(44f),
        iconButtonSm = d(38f),
        iconButtonLg = d(52f),

        fieldHeight = d(52f),
        fieldHeightLg = d(66f),
        buttonHeight = d(56f),

        avatarSm = d(38f),
        avatarMd = d(46f),
        avatarLg = d(54f),
        avatarXl = d(86f),
        avatarHero = d(168f),

        fab = d(58f),
        // Measured off the mockups at their 411dp reference width: video-bar
        // buttons 42dp, audio-panel buttons 55dp, end-call 70dp, answer 84dp.
        callControl = d(42f),
        callControlLg = d(55f),
        callAnswer = d(84f),
        callEnd = d(70f),
        navIcon = d(24f),

        screenHeight = heightDp.dp,
        contentMaxWidth = if (widthDp >= 600f) 600.dp else Dp.Unspecified
    )
}

val LocalIqDimens = staticCompositionLocalOf { buildIqDimens(REFERENCE_WIDTH, 900f) }

/** Entry point for screens: `val d = IqTheme.dimens`. */
object IqTheme {
    val dimens: IqDimens
        @androidx.compose.runtime.Composable
        @androidx.compose.runtime.ReadOnlyComposable
        get() = LocalIqDimens.current
}
