package com.iqsocial.iq_message.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.ContentScale
import com.iqsocial.iq_message.data.media.RemoteImage
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iqsocial.iq_message.data.BubbleGroup
import com.iqsocial.iq_message.data.Person
import com.iqsocial.iq_message.ui.theme.Crimson
import com.iqsocial.iq_message.ui.theme.Hairline
import com.iqsocial.iq_message.ui.theme.Ink
import com.iqsocial.iq_message.ui.theme.IqTheme
import com.iqsocial.iq_message.ui.theme.OnlineGreen

/* -- avatars -------------------------------------------------------------- */

/**
 * Portrait photos are not shipped with the project, so every avatar is generated:
 * a stable two-stop gradient keyed off the person's seed, with their initials on top.
 * Swap this for an image loader once real assets exist — nothing else has to change.
 */
private val avatarPalette = listOf(
    Color(0xFF9C8B7A) to Color(0xFF5E5044),   // warm sand
    Color(0xFF7FA8A0) to Color(0xFF44706B),   // muted teal
    Color(0xFF8E9BB3) to Color(0xFF4E5A72),   // slate
    Color(0xFFAE8C9E) to Color(0xFF6B4C5C),   // plum
    Color(0xFF9EA98B) to Color(0xFF5C6650),   // olive
    Color(0xFFC29279) to Color(0xFF7A5140),   // terracotta
    Color(0xFF8891B5) to Color(0xFF4B5375),   // indigo
    Color(0xFFB78F97) to Color(0xFF74505A)    // rose
)

/** A curated, desaturated set — a full hue sweep read as garish next to the brand red. */
fun avatarBrush(seed: Int): Brush {
    val i = ((seed % avatarPalette.size) + avatarPalette.size) % avatarPalette.size
    val (top, bottom) = avatarPalette[i]
    return Brush.linearGradient(listOf(top, bottom))
}

/**
 * Someone's face, or a stand-in for it.
 *
 * The gradient and initials appear while the photo loads, or if it is absent.
 * Once it is ready, skip the covered text layout on each avatar in the thread.
 */
@Composable
fun Avatar(
    person: Person,
    size: Dp,
    modifier: Modifier = Modifier,
    ringWidth: Dp = 0.dp,
    ringColor: Color = Crimson,
    ringGap: Dp = 2.dp,
    showPresence: Boolean = false,
    presenceBorder: Color = Color.White
) {
    val pad = if (ringWidth > 0.dp) ringWidth + ringGap else 0.dp
    val outer = size + pad * 2
    Box(modifier = modifier.size(outer), contentAlignment = Alignment.Center) {
        if (ringWidth > 0.dp) {
            Box(
                Modifier
                    .size(outer)
                    .border(ringWidth, ringColor, CircleShape)
            )
        }
        // Starting from the cache means an avatar already seen does not fade in
        // again every time it scrolls back into view.
        var photo by remember(person.photo) { mutableStateOf(RemoteImage.peek(person.photo)) }
        LaunchedEffect(person.photo) {
            if (photo == null && person.photo.isNotBlank()) {
                photo = RemoteImage.load(person.photo)
            }
        }

        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(remember(person.seed) { avatarBrush(person.seed) }),
            contentAlignment = Alignment.Center
        ) {
            val image = photo
            if (image == null) {
                Text(
                    text = remember(person.name) { person.initials },
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (size.value * 0.34f).sp
                )
            } else {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    // Cropped, not fitted: a portrait letterboxed inside a circle
                    // would show the gradient down two sides.
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(size)
                )
            }
        }
        if (showPresence && person.online) {
            val dot = (size.value * 0.27f).coerceIn(11f, 22f).dp
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = pad, bottom = pad)
                    .size(dot)
                    .clip(CircleShape)
                    .background(presenceBorder)
                    .padding(dot * 0.16f)
                    .clip(CircleShape)
                    .background(OnlineGreen)
            )
        }
    }
}

/* -- buttons -------------------------------------------------------------- */

/**
 * Circular icon button. The visible circle can be smaller than the touch area, so
 * dense toolbars still meet the 48dp minimum without looking chunky.
 */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = IqTheme.dimens.iconButton,
    iconSize: Dp = IqTheme.dimens.icon,
    background: Color = Color.Transparent,
    tint: Color = Ink,
    borderColor: Color? = null,
    borderWidth: Dp = 1.dp
) {
    val touch = maxOf(size, 48.dp)
    Box(
        modifier = modifier
            .size(touch)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(background)
                .then(
                    if (borderColor != null) Modifier.border(borderWidth, borderColor, CircleShape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription, Modifier.size(iconSize), tint = tint)
        }
    }
}

/* -- decoration ----------------------------------------------------------- */

/** Rounded-top sheet used by the onboarding screens. */
@Composable
fun sheetShape(): RoundedCornerShape {
    val r = IqTheme.dimens.sheetRadius
    return RoundedCornerShape(topStart = r, topEnd = r)
}

@Composable
fun SheetHandle(modifier: Modifier = Modifier, color: Color = Hairline) {
    val d = IqTheme.dimens
    Box(
        modifier
            .width(d.scaled(44))
            .height(d.scaled(5))
            .clip(CircleShape)
            .background(color)
    )
}

/**
 * The soft wave that closes off the crimson profile header.
 * Drawn rather than shipped as an asset so it scales to any header height.
 */
fun DrawScope.drawHeaderWave(color: Color, alpha: Float = 1f) {
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(0f, h * 0.60f)
        cubicTo(w * 0.22f, h * 0.42f, w * 0.34f, h * 0.96f, w * 0.60f, h * 0.80f)
        cubicTo(w * 0.80f, h * 0.68f, w * 0.90f, h * 0.50f, w, h * 0.56f)
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    drawPath(path, color, alpha = alpha)
}

/** Translucent bokeh circles behind the onboarding hero. */
fun DrawScope.drawHeroBokeh(tint: Color = Color.White) {
    val w = size.width
    val h = size.height
    drawCircle(tint, radius = w * 0.46f, center = Offset(w * 0.92f, h * 0.34f), alpha = 0.13f)
    drawCircle(tint, radius = w * 0.40f, center = Offset(w * 0.52f, h * 0.86f), alpha = 0.09f)
    drawCircle(tint, radius = w * 0.16f, center = Offset(w * 0.06f, h * 0.20f), alpha = 0.10f)
}

/* -- chat bubbles --------------------------------------------------------- */

/** Message bubble with a tail on the sender's side, matching the mockup silhouette. */
/**
 * A message bubble: a rounded rectangle, and nothing else.
 *
 * No tail. Messenger dropped the nub years ago, and it is what made these read as
 * speech balloons rather than as a thread - the avatar beside a run already says
 * who is speaking, so the tail was doing no work.
 *
 * Within a run the corners that face a neighbour tighten, which is what turns a
 * burst into one block instead of three separate balloons.
 */
data class BubbleShape(
    private val fromMe: Boolean,
    private val radius: Dp = 20.dp,
    private val group: BubbleGroup = BubbleGroup.SINGLE
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val r = with(density) { radius.toPx() }
        // Never rounder than the bubble is tall, or a one-line message turns oval.
        val full = CornerRadius(r.coerceAtMost(size.height / 2f), r.coerceAtMost(size.height / 2f))
        val tight = CornerRadius(r * 0.25f, r * 0.25f)

        val joinsAbove = group == BubbleGroup.MIDDLE || group == BubbleGroup.LAST
        val joinsBelow = group == BubbleGroup.FIRST || group == BubbleGroup.MIDDLE

        // Only the edge the run stacks along tightens; the far side stays round.
        val topInner = if (joinsAbove) tight else full
        val bottomInner = if (joinsBelow) tight else full

        val rect = RoundRect(
            rect = Rect(0f, 0f, size.width, size.height),
            topLeft = if (fromMe) full else topInner,
            topRight = if (fromMe) topInner else full,
            bottomRight = if (fromMe) bottomInner else full,
            bottomLeft = if (fromMe) full else bottomInner
        )
        return Outline.Rounded(rect)
    }
}

/* -- call control containers ---------------------------------------------- */

/**
 * A rounded container whose top edge is interrupted by a circle, so the end-call
 * button reads as part of the bar rather than sitting on top of it.
 *
 * The mockups use both variants: the video pill *bulges up* around the button
 * ([union] = true), while the audio panel is *scooped out* beneath it, with the
 * button straddling the edge ([union] = false).
 *
 * @param flatTop distance from the shape's top to the flat part of the edge; the
 *        bulge lives in that strip, so it stays inside the layout bounds.
 * @param circleCenterY circle centre, measured from the shape's top.
 */
class EdgeCutShape(
    private val corner: Dp,
    private val circleRadius: Dp,
    private val circleCenterY: Dp,
    private val union: Boolean,
    private val flatTop: Dp = 0.dp
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val r = with(density) { corner.toPx() }
        val cr = with(density) { circleRadius.toPx() }
        val cy = with(density) { circleCenterY.toPx() }
        val top = with(density) { flatTop.toPx() }
        val cx = size.width / 2f

        val body = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(0f, top, size.width, size.height),
                    cornerRadius = CornerRadius(r, r)
                )
            )
        }
        val circle = Path().apply {
            addOval(Rect(cx - cr, cy - cr, cx + cr, cy + cr))
        }
        val merged = Path().apply {
            op(body, circle, if (union) PathOperation.Union else PathOperation.Difference)
        }
        return Outline.Generic(merged)
    }
}

/**
 * A pill whose top edge swells into a smooth, wide hump so an oversized button can
 * sit on the same centreline as the small ones.
 *
 * Measured off the video-call mockup the hump is a shallow S-curved arc about 92dp
 * across and 11dp tall — much wider and gentler than the button it clears, which is
 * why this is a hand-built path rather than a union with the button's circle.
 */
class HumpedBarShape(
    private val humpWidth: Dp,
    private val humpHeight: Dp,
    private val corner: Dp = 999.dp
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val hw = with(density) { humpWidth.toPx() } / 2f
        val top = with(density) { humpHeight.toPx() }
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val r = with(density) { corner.toPx() }
            .coerceAtMost(minOf(w, (h - top).coerceAtLeast(0f)) / 2f)

        val path = Path().apply {
            moveTo(r, top)
            lineTo(cx - hw, top)
            // S-curve up, flat over the crown, S-curve back down
            cubicTo(cx - hw * 0.45f, top, cx - hw * 0.62f, 0f, cx, 0f)
            cubicTo(cx + hw * 0.62f, 0f, cx + hw * 0.45f, top, cx + hw, top)
            lineTo(w - r, top)
            arcTo(Rect(w - 2 * r, top, w, top + 2 * r), -90f, 90f, false)
            lineTo(w, h - r)
            arcTo(Rect(w - 2 * r, h - 2 * r, w, h), 0f, 90f, false)
            lineTo(r, h)
            arcTo(Rect(0f, h - 2 * r, 2 * r, h), 90f, 90f, false)
            lineTo(0f, top + r)
            arcTo(Rect(0f, top, 2 * r, top + 2 * r), 180f, 90f, false)
            close()
        }
        return Outline.Generic(path)
    }
}

/** Faint paper-plane / heart / bubble doodles behind the conversation. */
@Composable
fun ChatWallpaper(modifier: Modifier = Modifier, tint: Color = Color(0xFF9AA0A6)) {
    Canvas(modifier.fillMaxSize()) {
        val step = size.width / 2.6f
        val alpha = 0.05f
        var row = 0
        var y = step * 0.25f
        while (y < size.height + step) {
            var x = if (row % 2 == 0) step * 0.25f else step * 0.75f
            while (x < size.width + step) {
                val s = step / 155f
                when ((row + (x / step).toInt()) % 3) {
                    0 -> {
                        val p = Path().apply {
                            moveTo(x, y + 26f * s)
                            lineTo(x + 34f * s, y)
                            lineTo(x + 12f * s, y + 34f * s)
                            lineTo(x + 8f * s, y + 20f * s)
                            close()
                        }
                        drawPath(p, tint, alpha = alpha, style = Stroke(2.2f))
                    }
                    1 -> drawRoundRect(
                        color = tint, alpha = alpha,
                        topLeft = Offset(x, y),
                        size = Size(38f * s, 28f * s),
                        cornerRadius = CornerRadius(11f * s, 11f * s),
                        style = Stroke(2.2f)
                    )
                    else -> {
                        val p = Path().apply {
                            moveTo(x + 16f * s, y + 30f * s)
                            cubicTo(x - 6f * s, y + 14f * s, x + 4f * s, y - 2f * s, x + 16f * s, y + 10f * s)
                            cubicTo(x + 28f * s, y - 2f * s, x + 38f * s, y + 14f * s, x + 16f * s, y + 30f * s)
                            close()
                        }
                        drawPath(p, tint, alpha = alpha, style = Stroke(2.2f))
                    }
                }
                x += step
            }
            y += step
            row++
        }
    }
}

/** The animated "… is typing" ellipsis. */
@Composable
fun TypingDots(color: Color = Crimson, dotSize: Dp = IqTheme.dimens.scaled(7)) {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(
        horizontalArrangement = Arrangement.spacedBy(dotSize * 0.7f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(520, delayMillis = index * 170, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot$index"
            )
            Box(
                Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(color.copy(alpha = alpha))
            )
        }
    }
}

/* -- misc ----------------------------------------------------------------- */

@Composable
fun UnreadBadge(count: Int, modifier: Modifier = Modifier) {
    val d = IqTheme.dimens
    Box(
        modifier
            .size(d.scaled(23))
            .clip(CircleShape)
            .background(Crimson),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = count.toString(),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun HairlineDivider(
    modifier: Modifier = Modifier,
    color: Color = Hairline
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color)
    )
}
