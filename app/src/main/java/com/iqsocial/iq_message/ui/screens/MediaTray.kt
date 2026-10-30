package com.iqsocial.iq_message.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import com.iqsocial.iq_message.data.media.DeviceGallery
import com.iqsocial.iq_message.data.media.PhotoQuality
import com.iqsocial.iq_message.ui.components.HairlineDivider
import com.iqsocial.iq_message.ui.icons.IqIcons
import com.iqsocial.iq_message.ui.theme.Crimson
import com.iqsocial.iq_message.ui.theme.CrimsonTint
import com.iqsocial.iq_message.ui.theme.FieldGrey
import com.iqsocial.iq_message.ui.theme.Hairline
import com.iqsocial.iq_message.ui.theme.Ink
import com.iqsocial.iq_message.ui.theme.InkMuted
import com.iqsocial.iq_message.ui.theme.IqTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
/**
 * The photo tray, in the keyboard's place.
 *
 * The system picker is still there behind "All photos" and is the right tool for
 * digging through everything; this exists because sending the shot you just took
 * should not mean leaving the conversation. It shows the most recent roll inline,
 * and a tap sends.
 *
 * Reading the roll needs a permission the system picker does not, so the tray asks
 * for it in place rather than at launch — and if it is refused, it still offers the
 * system picker, which needs nothing.
 *
 * It opens at the keyboard's height and **drags up to fill most of the screen**. The
 * short state is right for sending the shot you just took, which is most of what this
 * is for; browsing further back needs room, and making someone leave for the system
 * picker to get it would defeat the point of having the tray at all. Two stops rather
 * than free resize - a sheet that holds any height leaves the person deciding what
 * height is correct, which is not their problem.
 */
@Composable
fun MediaTray(
    onPick: (Uri) -> Unit,
    onOpenSystemPicker: () -> Unit,
    onOpenCamera: () -> Unit,
    onDismiss: () -> Unit
) {
    val d = IqTheme.dimens
    val context = LocalContext.current
    var granted by remember { mutableStateOf(DeviceGallery.isGranted(context)) }
    var items by remember { mutableStateOf<List<DeviceGallery.Item>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    val ask = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        granted = result.values.any { it }
    }

    LaunchedEffect(granted) {
        if (!granted) {
            loaded = true
            return@LaunchedEffect
        }
        items = DeviceGallery.recent(context)
        loaded = true
    }

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val short = with(density) { (d.screenHeight * SHORT).toPx() }
    val tall = with(density) { (d.screenHeight * TALL).toPx() }

    // Height is plain state rather than an Animatable: a drag has to land on the same
    // frame as the finger, and Animatable can only snap from a coroutine.
    var heightPx by remember(short) { mutableFloatStateOf(short) }
    var settling by remember { mutableStateOf<Job?>(null) }

    fun drag(delta: Float) {
        settling?.cancel()                     // the finger wins over an animation
        heightPx = TraySheet.heightAfterDrag(heightPx, delta, short, tall)
    }

    /** Lands on a stop, taking the throw into account rather than position alone. */
    suspend fun animateToStop(velocity: Float) {
        val target = TraySheet.stopFor(heightPx, velocity, short, tall)
        animate(
            initialValue = heightPx,
            targetValue = target,
            // Screen quantities point down, heights up, so the throw inverts.
            initialVelocity = -velocity,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ) { value, _ -> heightPx = value }
        // Closed is a stop like any other, so the sheet is taken off screen once it
        // has finished travelling there rather than vanishing under the finger.
        if (target <= 0f) onDismiss()
    }

    /**
     * Starts a settle that a new drag can interrupt.
     *
     * It has to be tracked and cancellable: without that, grabbing the sheet again
     * mid-animation leaves the animation and the finger both writing the height, and
     * the sheet fights the hand.
     */
    fun settle(velocity: Float) {
        settling?.cancel()
        settling = scope.launch { animateToStop(velocity) }
    }

    /**
     * Lets the grid hand its scroll to the sheet.
     *
     * Swiping up inside the photos grows the sheet before the grid starts scrolling,
     * and swiping down once the grid is back at its top shrinks it again - so the
     * whole surface answers the gesture, not just the handle. Without this the handle
     * would be the only way up, which is a small target and not where a thumb already
     * is.
     */
    val fromGrid = remember(short, tall) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val taken = TraySheet.preScrollTaken(heightPx, available.y, short, tall)
                if (taken == 0f) return Offset.Zero
                drag(taken)
                return Offset(0f, taken)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // Only what the grid could not use, which means it is at its top.
                val taken = TraySheet.postScrollTaken(heightPx, available.y, short, tall)
                if (taken == 0f) return Offset.Zero
                drag(taken)
                return Offset(0f, taken)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                // Mid-travel the throw belongs to the sheet; the grid must not fling
                // too, or it scrolls away under a sheet that is still moving.
                if (!TraySheet.ownsFling(heightPx, short, tall)) return Velocity.Zero
                settle(available.y)
                // Consumed even though the settle runs on its own: the grid must not
                // get this throw as well.
                return available
            }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White)
            .height(with(density) { heightPx.toDp() })
    ) {
        HairlineDivider()
        // The handle and the header drag the sheet directly; the grid does it through
        // the nested-scroll connection instead, so its own scrolling still works.
        Box(
            Modifier
                .fillMaxWidth()
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { drag(it) },
                    onDragStopped = { velocity -> settle(velocity) }
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .padding(vertical = d.gapSm)
                    .width(d.scaled(36))
                    .height(d.scaled(4))
                    .clip(CircleShape)
                    .background(FieldGrey)
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { drag(it) },
                    onDragStopped = { velocity -> settle(velocity) }
                )
                .padding(horizontal = d.gutter, vertical = d.gapXs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Recents",
                style = MaterialTheme.typography.titleSmall,
                color = InkMuted,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            // The camera sits with the roll rather than in the composer's icon row:
            // taking a photo and choosing one are the same errand, and this is the
            // surface that errand already opened.
            Text(
                "Camera",
                style = MaterialTheme.typography.titleSmall,
                color = Crimson,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(d.radiusSm))
                    .clickable(onClick = onOpenCamera)
                    .padding(horizontal = d.gapSm, vertical = d.gapXs)
            )
            Text(
                "All photos",
                style = MaterialTheme.typography.titleSmall,
                color = Crimson,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(d.radiusSm))
                    .clickable(onClick = onOpenSystemPicker)
                    .padding(horizontal = d.gapSm, vertical = d.gapXs)
            )
            Spacer(Modifier.width(d.gapXs))
            HdSwitch()
        }

        when {
            !granted -> TrayMessage(
                title = "Show your recent photos here",
                body = "Allow access and your latest shots appear in the chat, " +
                    "or pick one the usual way.",
                action = "Allow access",
                onAction = { ask.launch(DeviceGallery.permissions) }
            )

            loaded && items.isEmpty() -> TrayMessage(
                title = "No photos yet",
                body = "Anything you shoot will show up here.",
                action = "Browse files",
                onAction = onOpenSystemPicker
            )

            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = d.scaled(96)),
                modifier = Modifier
                    .fillMaxWidth()
                    .nestedScroll(fromGrid),
                contentPadding = PaddingValues(horizontal = d.gapXs, vertical = d.gapXs),
                horizontalArrangement = Arrangement.spacedBy(d.scaled(2)),
                verticalArrangement = Arrangement.spacedBy(d.scaled(2))
            ) {
                items(items, key = { it.uri.toString() }) { item ->
                    GalleryTile(item) { onPick(item.uri) }
                }
            }
        }
    }
}

@Composable
private fun GalleryTile(item: DeviceGallery.Item, onClick: () -> Unit) {
    val d = IqTheme.dimens
    val context = LocalContext.current
    var thumb by remember(item.uri) { mutableStateOf(DeviceGallery.peek(item.uri)) }

    LaunchedEffect(item.uri) {
        if (thumb == null) thumb = DeviceGallery.thumbnail(context, item.uri)
    }

    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(d.radiusSm))
            .background(FieldGrey)
            .clickable(onClick = onClick)
    ) {
        thumb?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (item.isVideo) {
            // Duration marks a video apart from a photo at a glance.
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(d.gapXs)
                    .clip(RoundedCornerShape(d.radiusSm))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = d.gapXs, vertical = 1.dp()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    IqIcons.PlayArrow,
                    null,
                    Modifier.size(d.iconXs),
                    tint = Color.White
                )
                Spacer(Modifier.width(2.dp()))
                Text(
                    clock(item.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun TrayMessage(title: String, body: String, action: String, onAction: () -> Unit) {
    val d = IqTheme.dimens
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = d.gutter, vertical = d.gapXl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(d.avatarLg)
                .clip(CircleShape)
                .background(CrimsonTint),
            contentAlignment = Alignment.Center
        ) {
            Icon(IqIcons.Gallery, null, Modifier.size(d.icon), tint = Crimson)
        }
        Spacer(Modifier.height(d.gapMd))
        Text(title, style = MaterialTheme.typography.titleMedium, color = Ink)
        Spacer(Modifier.height(d.gapXs))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = InkMuted,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(d.gapMd))
        Text(
            action,
            style = MaterialTheme.typography.titleSmall,
            color = Crimson,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(d.radiusSm))
                .background(CrimsonTint)
                .clickable(onClick = onAction)
                .padding(horizontal = d.gapLg, vertical = d.gapSm)
        )
    }
}

/**
 * The two stops, as a share of the screen.
 *
 * [SHORT] is about a keyboard, so opening the tray does not move the conversation
 * further than typing already would. [TALL] leaves the header and the composer on
 * screen - covering them would make this a different screen rather than a tray.
 */
private const val SHORT = 0.40f
private const val TALL = 0.74f

internal fun clock(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}

/** Small literal sizes inside the tile, kept out of the dimens scale on purpose. */
@Composable
private fun Int.dp() = androidx.compose.ui.unit.Dp(this.toFloat())


/**
 * The HD choice, where the photos are.
 *
 * On, a picture goes as it was taken. Off, it is brought inside
 * [PhotoQuality.MAX_EDGE] first - which is most of a phone photo's weight for a
 * fraction of its pixels, and the right default for a conversation.
 *
 * Here rather than in the app's settings because it is a decision about *these*
 * photos, taken while looking at them. A quality setting several screens away is one
 * nobody finds and nobody changes.
 *
 * Its own small control rather than a Material `Switch`: that one is sized for a
 * settings row and would be the tallest thing in this strip by half again, pushing
 * the roll down to make room for it.
 */
@Composable
private fun HdSwitch() {
    val d = IqTheme.dimens
    val context = LocalContext.current
    LaunchedEffect(Unit) { PhotoQuality.load(context) }

    val on = PhotoQuality.hd
    val track = d.scaled(34)
    val knob = d.scaled(14)
    // Animated so the knob is seen to move. A control this small that simply
    // redraws reads as a label that changed colour rather than a switch that was
    // thrown.
    val shift by animateDpAsState(
        if (on) track - knob - d.scaled(3) else d.scaled(3),
        label = "hd knob"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(d.radiusSm))
            .clickable { PhotoQuality.set(context, !on) }
            .padding(horizontal = d.gapXs, vertical = d.gapXs)
    ) {
        Text(
            "HD",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (on) Crimson else InkMuted
        )
        Spacer(Modifier.width(d.gapXs))
        Box(
            Modifier
                .width(track)
                .height(d.scaled(20))
                .clip(CircleShape)
                .background(if (on) Crimson else Hairline)
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = shift)
                    .size(knob)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}
