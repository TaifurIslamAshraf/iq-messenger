package com.iqsocial.iq_message.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.iqsocial.iq_message.data.media.ProfilePhoto
import com.iqsocial.iq_message.data.remote.ProfileImage
import com.iqsocial.iq_message.ui.components.CircleIconButton
import com.iqsocial.iq_message.ui.icons.IqIcons
import com.iqsocial.iq_message.ui.theme.CallBlack
import com.iqsocial.iq_message.ui.theme.Crimson
import com.iqsocial.iq_message.ui.theme.InkFaint
import com.iqsocial.iq_message.ui.theme.IqTheme
import com.iqsocial.iq_message.ui.theme.SystemBarIcons
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Choosing what part of a picture becomes the profile photo or the cover.
 *
 * Every screen that draws one of these crops it: an avatar is a circle and a cover
 * is a wide band, so a tall photo sent whole is a tall photo with most of it cut
 * off by whoever draws it. Doing the cutting here is the difference between the
 * reader deciding what is in frame and finding out afterwards that their head is
 * not in it.
 *
 * The shape comes from the picture's own kind - 1:1 for a face, 16:9 for a banner,
 * both on [ProfileImage] - so there is nothing to choose but the part, which is
 * what the frame is for.
 *
 * **How it works.** The frame is a window the size of the final shape and the
 * picture moves behind it: drag to pan, pinch to zoom. The picture is laid out at
 * its own pixel size and scaled from there, so what is inside the frame is exactly
 * the rectangle of source pixels that gets cut - there is no second coordinate
 * system to keep in step, and the crop is read back off the same two numbers the
 * drawing used.
 *
 * It can never be moved off its own edges: zoom starts at the scale that just
 * covers the frame and panning is clamped to the picture, so there is no way to
 * frame a corner of nothing and upload a photo with an empty wedge in it.
 */
@Composable
fun ImageCropScreen(
    uri: Uri,
    kind: ProfileImage,
    onCancel: () -> Unit,
    onDone: (Bitmap) -> Unit
) {
    SystemBarIcons(darkIcons = false)
    val d = IqTheme.dimens
    val context = LocalContext.current
    val density = LocalDensity.current

    var source by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(uri) { mutableStateOf(false) }
    LaunchedEffect(uri) {
        // Read bigger than either picture needs: a crop takes a part of this, and
        // the part still has to be large enough to upload at full size.
        val loaded = ProfilePhoto.decode(context, uri, SOURCE_EDGE)
        if (loaded == null) failed = true else source = loaded
    }

    var zoom by remember(uri) { mutableFloatStateOf(1f) }
    var offset by remember(uri) { mutableStateOf(Offset.Zero) }

    /**
     * The frame as it was actually measured.
     *
     * Taken from the layout rather than computed twice: the button that cuts the
     * picture is in a different part of the screen from the box that sizes the
     * frame, and two calculations of the same rectangle are two things that can
     * disagree - which here would mean a crop that is not what was shown.
     */
    var frame by remember { mutableStateOf(IntSize.Zero) }

    Column(
        Modifier
            .fillMaxSize()
            .background(CallBlack)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = d.gapXs, end = d.gutter, top = d.gapSm, bottom = d.gapSm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleIconButton(
                icon = IqIcons.Close,
                contentDescription = "Cancel",
                onClick = onCancel,
                background = Color.White.copy(alpha = 0.16f),
                tint = Color.White
            )
            Spacer(Modifier.width(d.gapXs))
            Text(
                if (kind == ProfileImage.PICTURE) "Crop photo" else "Crop cover",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
        }

        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val picture = source
            when {
                failed -> Text(
                    "That picture could not be opened.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = InkFaint,
                    textAlign = TextAlign.Center
                )

                picture == null -> CircularProgressIndicator(color = Crimson)

                else -> {
                    // As wide as the screen allows and as tall as the shape says -
                    // unless that is taller than there is room for, and then the
                    // height leads instead.
                    val roomW = with(density) { (maxWidth - d.gutter * 2).toPx() }
                    val roomH = with(density) { (maxHeight - d.gapLg * 2).toPx() }
                    val frameW = minOf(roomW, roomH * kind.aspect)
                    val frameH = frameW / kind.aspect

                    // The scale at which the picture just covers the frame. Zoom is
                    // a multiplier on top of that, so 1 is "as small as allowed"
                    // and the picture can never be smaller than the hole it fills.
                    val base = max(frameW / picture.width, frameH / picture.height)
                    val scale = base * zoom
                    val maxX = ((picture.width * scale - frameW) / 2f).coerceAtLeast(0f)
                    val maxY = ((picture.height * scale - frameH) / 2f).coerceAtLeast(0f)
                    val held = Offset(
                        offset.x.coerceIn(-maxX, maxX),
                        offset.y.coerceIn(-maxY, maxY)
                    )

                    Box(
                        Modifier
                            .size(
                                with(density) { frameW.toDp() },
                                with(density) { frameH.toDp() }
                            )
                            .onSizeChanged { frame = it }
                            .clipToBounds()
                            .pointerInput(picture, frameW, frameH) {
                                detectTransformGestures { _, pan, pinch, _ ->
                                    val wanted = (zoom * pinch).coerceIn(1f, MAX_ZOOM)
                                    // The limits move with the zoom, so they are
                                    // worked out for the zoom being applied, not
                                    // the one before it: zooming out with the
                                    // picture pushed to one side would otherwise
                                    // leave it there, past its own edge.
                                    val afterW = picture.width * base * wanted
                                    val afterH = picture.height * base * wanted
                                    val limitX = ((afterW - frameW) / 2f).coerceAtLeast(0f)
                                    val limitY = ((afterH - frameH) / 2f).coerceAtLeast(0f)
                                    zoom = wanted
                                    offset = Offset(
                                        (offset.x + pan.x).coerceIn(-limitX, limitX),
                                        (offset.y + pan.y).coerceIn(-limitY, limitY)
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = picture.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier
                                // Laid out at its own pixel size and scaled from
                                // there: that is what makes the drawing and the
                                // crop the same arithmetic.
                                //
                                // **required**, not plain `size`. A size modifier
                                // is still bounded by what the parent offers, and
                                // the parent here is the frame - so the picture was
                                // being laid out at the frame's size and then fitted
                                // inside it, which is why it sat small in the middle
                                // with the background showing around it. Required
                                // size ignores the parent's maximum, which is the
                                // whole point: this child is meant to overflow and
                                // be clipped.
                                .requiredSize(
                                    with(density) { picture.width.toDp() },
                                    with(density) { picture.height.toDp() }
                                )
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = held.x
                                    translationY = held.y
                                }
                        )

                        // The guide: a circle for the picture that will be shown as
                        // a circle, a plain edge for the one that will be shown as
                        // it is cut.
                        Canvas(Modifier.fillMaxSize()) {
                            val line = with(density) { 2.dp.toPx() }
                            if (kind == ProfileImage.PICTURE) {
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.9f),
                                    radius = size.minDimension / 2f - line,
                                    style = Stroke(line)
                                )
                            } else {
                                drawRect(
                                    color = Color.White.copy(alpha = 0.9f),
                                    style = Stroke(line)
                                )
                            }
                        }
                    }
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = d.gutterTight)
                .padding(bottom = d.gapMd)
                .navigationBarsPadding()
        ) {
            Text(
                if (kind == ProfileImage.PICTURE) {
                    "Drag to move, pinch to zoom. Your photo is shown as a circle."
                } else {
                    "Drag to move, pinch to zoom."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = InkFaint,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(d.gapMd))
            Row(horizontalArrangement = Arrangement.spacedBy(d.gapMd)) {
                DarkButton(
                    text = "Cancel",
                    filled = false,
                    modifier = Modifier.weight(1f),
                    onClick = onCancel
                )
                DarkButton(
                    text = "Use photo",
                    filled = true,
                    enabled = source != null && frame != IntSize.Zero,
                    modifier = Modifier.weight(1f)
                ) {
                    val picture = source ?: return@DarkButton
                    cut(picture, frame, zoom, offset)?.let(onDone)
                }
            }
        }
    }
}

/**
 * The part of [picture] that was inside the frame, as a new bitmap.
 *
 * The same three numbers the screen drew with - the frame it was measured at, the
 * zoom and the pan - run backwards. Nothing is remembered between the two: if the
 * drawing was right then this is right, and there is no third copy of the maths to
 * drift out of step with either.
 */
private fun cut(picture: Bitmap, frame: IntSize, zoom: Float, offset: Offset): Bitmap? {
    val frameW = frame.width.toFloat()
    val frameH = frame.height.toFloat()
    if (frameW <= 0f || frameH <= 0f) return null
    val scale = max(frameW / picture.width, frameH / picture.height) * zoom
    val visibleW = frameW / scale
    val visibleH = frameH / scale
    val left = (picture.width - visibleW) / 2f - offset.x / scale
    val top = (picture.height - visibleH) / 2f - offset.y / scale
    val x = left.roundToInt().coerceIn(0, (picture.width - 1).coerceAtLeast(0))
    val y = top.roundToInt().coerceIn(0, (picture.height - 1).coerceAtLeast(0))
    val w = visibleW.roundToInt().coerceIn(1, picture.width - x)
    val h = visibleH.roundToInt().coerceIn(1, picture.height - y)
    return runCatching { Bitmap.createBitmap(picture, x, y, w, h) }.getOrNull()
}

@Composable
private fun DarkButton(
    text: String,
    filled: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val d = IqTheme.dimens
    Box(
        modifier
            .height(d.buttonHeight)
            .clip(RoundedCornerShape(d.radiusMd))
            .background(
                when {
                    !enabled -> Crimson.copy(alpha = 0.35f)
                    filled -> Crimson
                    else -> Color.White.copy(alpha = 0.14f)
                }
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** How far in a picture may be zoomed before it is only pixels. */
private const val MAX_ZOOM = 6f

/**
 * How big the picture is read at before cropping.
 *
 * Larger than either kind's own limit on purpose: a crop takes a part of it, and
 * that part still has to be big enough to upload at the size the server is told.
 */
private const val SOURCE_EDGE = 2048
