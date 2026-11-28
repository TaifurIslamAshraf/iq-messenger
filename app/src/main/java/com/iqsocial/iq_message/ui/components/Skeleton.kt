package com.iqsocial.iq_message.ui.components

import com.iqsocial.iq_message.ui.theme.ThemeMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.iqsocial.iq_message.ui.theme.IqTheme

/**
 * The shape of what is coming, drawn while it is still coming.
 *
 * A spinner says "wait" and nothing else: it is the same circle whatever is being
 * waited for, it appears in the middle of an empty screen, and the real content
 * replaces it in a jump that moves everything. A skeleton says what is about to
 * arrive and where it will be - so the page is already laid out when the first row
 * lands, and nothing shifts underneath a finger that was already reaching for it.
 *
 * It is also honest about scale: eight grey rows mean a list is coming, and that is
 * true before anything has been counted.
 *
 * **Only where there is nothing to show.** A list with rows already in it - from
 * the local database, or a page loaded a moment ago - shows those rows and quietly
 * fetches behind them. A skeleton over content that already exists would be hiding
 * the answer to make the waiting look busier.
 */

/* -- the shimmer ---------------------------------------------------------- */

/**
 * A pale block with a light moving across it.
 *
 * The movement is what separates a placeholder from a bug. A row of flat grey
 * rectangles reads as content that failed to draw; the same rectangles with
 * something travelling over them read as work in progress, without a single word.
 *
 * One sweep every 1.3 seconds, which is slow enough not to pull the eye away from
 * whatever else is on the screen. Each block runs its own animation and they all
 * begin when the skeleton appears, so they travel together without having to share
 * any state.
 */
fun Modifier.shimmer(
    shape: Shape = RoundedCornerShape(50),
    /**
     * The resting colour and the light that crosses it.
     *
     * Given rather than fixed because the same effect has to work inside a crimson
     * bubble and on a black viewer, where a pale grey would be either invisible or
     * a hole punched in the picture.
     */
    base: Color = SkeletonGrey,
    glow: Color = SkeletonGlow
): Modifier =
    composed {
        val sweep = rememberInfiniteTransition(label = "skeleton")
        val travel by sweep.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing)),
            label = "sweep"
        )
        clip(shape).drawBehind {
            // The highlight is twice the block wide and starts fully off the left,
            // so a sweep begins and ends with the block at its resting grey rather
            // than with a bright edge parked on it.
            val band = size.width * 2f
            val from = -band + travel * (size.width + band)
            drawRect(
                Brush.linearGradient(
                    colors = listOf(base, glow, base),
                    start = Offset(from, 0f),
                    end = Offset(from + band, size.height)
                )
            )
        }
    }

/** A line of text that has not arrived. [part] is how much of the width it takes. */
@Composable
fun SkeletonLine(part: Float, height: Dp = 12.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth(part)
            .height(height)
            .shimmer()
    )
}

/** A block of a known size: an avatar, a thumbnail, a chip. */
@Composable
fun SkeletonBlock(
    width: Dp,
    height: Dp,
    shape: Shape = RoundedCornerShape(50),
    modifier: Modifier = Modifier
) {
    Box(modifier.size(width, height).shimmer(shape))
}

/* -- the lists ------------------------------------------------------------ */

/**
 * One row of a list of people: a round picture, a name, and a line under it.
 *
 * The same shape serves the chat list, the call list and every picker, because
 * those rows are the same shape. [trailing] is the time or the tick on the right,
 * which the pickers do not have.
 *
 * The widths of the two lines are stepped through per row rather than fixed. Real
 * names and messages are not all one length, and a column of identical grey bars
 * looks like a pattern rather than like a list.
 */
@Composable
fun PersonRowSkeleton(index: Int = 0, trailing: Boolean = false, lines: Int = 2) {
    val d = IqTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = d.gutter, vertical = d.gapMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SkeletonBlock(d.avatarLg, d.avatarLg, CircleShape)
        Spacer(Modifier.width(d.gapMd))
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(d.gapSm)
        ) {
            SkeletonLine(NAME_WIDTHS[index % NAME_WIDTHS.size], d.scaled(13))
            if (lines > 1) {
                SkeletonLine(LINE_WIDTHS[index % LINE_WIDTHS.size], d.scaled(11))
            }
            if (lines > 2) {
                SkeletonLine(LINE_WIDTHS[(index + 1) % LINE_WIDTHS.size], d.scaled(11))
            }
        }
        if (trailing) {
            Spacer(Modifier.width(d.gapMd))
            SkeletonBlock(d.scaled(34), d.scaled(11))
        }
    }
}

/**
 * A list that is still on its way.
 *
 * [rows] is deliberately more than fills a screen: a skeleton that stops halfway
 * down says the list is short, and then the real list arrives longer and the page
 * jumps - which is the thing a skeleton is there to prevent.
 */
@Composable
fun ListSkeleton(
    rows: Int = 9,
    trailing: Boolean = false,
    lines: Int = 2,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth()) {
        repeat(rows) { PersonRowSkeleton(index = it, trailing = trailing, lines = lines) }
    }
}

/**
 * A conversation on its way: bubbles, alternating sides, of uneven length.
 *
 * Drawn from the bottom like the thread itself, so the newest placeholder sits
 * where the newest message will be. The widths vary because messages do, and a
 * column of identical blocks reads as a loading pattern rather than as a chat.
 */
@Composable
fun ThreadSkeleton(rows: Int = 7, modifier: Modifier = Modifier) {
    val d = IqTheme.dimens
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = d.gutterTight, vertical = d.gapMd),
        verticalArrangement = Arrangement.spacedBy(d.gapSm)
    ) {
        repeat(rows) { index ->
            val mine = index % 3 == 1
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(BUBBLE_WIDTHS[index % BUBBLE_WIDTHS.size])
                        .height(d.scaled(BUBBLE_HEIGHTS[index % BUBBLE_HEIGHTS.size]))
                        .shimmer(RoundedCornerShape(d.radiusLg))
                )
            }
        }
    }
}

private val BUBBLE_WIDTHS = listOf(0.52f, 0.44f, 0.66f, 0.38f, 0.58f, 0.3f, 0.48f)
private val BUBBLE_HEIGHTS = listOf(38, 30, 54, 30, 44, 30, 38)

/** The same thing for a grid of pictures: shared media, the gallery. */
@Composable
fun GridSkeleton(rows: Int = 4, columns: Int = 3, modifier: Modifier = Modifier) {
    val d = IqTheme.dimens
    Column(
        modifier
            .fillMaxWidth()
            .padding(d.gapXs),
        verticalArrangement = Arrangement.spacedBy(d.gapXs)
    ) {
        repeat(rows) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(d.gapXs)
            ) {
                repeat(columns) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(d.scaled(110))
                            .shimmer(RoundedCornerShape(d.radiusMd))
                    )
                }
            }
        }
    }
}

/* -- the greys ------------------------------------------------------------ */

/**
 * Between [com.iqsocial.iq_message.ui.theme.Hairline] and
 * [com.iqsocial.iq_message.ui.theme.FieldGrey], where a placeholder belongs: dark
 * enough to read as a block on a white card, light enough that nine of them are
 * not a wall.
 */
val SkeletonGrey: Color
    get() = if (ThemeMode.dark) Color(0xFF26272C) else Color(0xFFE3E3E8)
val SkeletonGlow: Color
    get() = if (ThemeMode.dark) Color(0xFF34353B) else Color(0xFFF2F2F5)

/**
 * A head of a profile that has not arrived: the picture, the name, a line under it.
 *
 * Centred, because that is how [com.iqsocial.iq_message.ui.screens.PersonScreen]
 * draws the real one, and a placeholder that sits somewhere else is a placeholder
 * that moves everything when it is replaced.
 */
@Composable
fun ProfileSkeleton(modifier: Modifier = Modifier) {
    val d = IqTheme.dimens
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(d.gapMd)
    ) {
        SkeletonBlock(d.avatarXl, d.avatarXl, CircleShape)
        SkeletonLine(0.42f, d.scaled(16))
        SkeletonLine(0.28f, d.scaled(12))
    }
}

private val NAME_WIDTHS = listOf(0.52f, 0.38f, 0.61f, 0.45f, 0.56f, 0.34f)
private val LINE_WIDTHS = listOf(0.78f, 0.64f, 0.85f, 0.56f, 0.72f, 0.48f)
