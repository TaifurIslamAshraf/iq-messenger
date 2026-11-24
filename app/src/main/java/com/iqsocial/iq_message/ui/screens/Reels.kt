package com.iqsocial.iq_message.ui.screens

import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.iqsocial.iq_message.R
import com.iqsocial.iq_message.data.Person
import com.iqsocial.iq_message.data.auth.SessionStore
import com.iqsocial.iq_message.data.media.RemoteImage
import com.iqsocial.iq_message.data.reels.ReelSession
import com.iqsocial.iq_message.data.reels.ReelsFeed
import com.iqsocial.iq_message.data.remote.Reel
import com.iqsocial.iq_message.ui.components.Avatar
import com.iqsocial.iq_message.ui.components.shimmer
import com.iqsocial.iq_message.ui.icons.IqIcons
import com.iqsocial.iq_message.ui.theme.Ink
import com.iqsocial.iq_message.ui.theme.IqTheme
import com.iqsocial.iq_message.ui.theme.SystemBarIcons

/* ========================================================================= */
/*  The strip, under the search on the chat list                             */
/* ========================================================================= */

/**
 * A row of reels across the top of the chat list.
 *
 * Thumbnails only. Nothing plays here: a list that starts videos while somebody is
 * looking for a conversation is a list fighting the thing it is for, and every
 * autoplaying card is data spent on a glance. Tapping one opens the viewer at that
 * reel, and that is where anything moves.
 *
 * Not drawn at all once the feed is known to be empty or would not load. It is an
 * extra on the chat list; a heading over nothing, or an error over somebody's
 * conversations, would be worse than its absence.
 */
@Composable
fun ReelsStrip(onOpen: (Int) -> Unit) {
    val d = IqTheme.dimens
    val context = LocalContext.current
    val token = remember { SessionStore(context).token }
    LaunchedEffect(token) { ReelsFeed.refresh(token) }

    val reels = ReelsFeed.reels
    if (ReelsFeed.loaded && reels.isEmpty()) return

    Column(Modifier.fillMaxWidth().padding(bottom = d.gapSm)) {
        Text(
            "Reels",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Ink,
            modifier = Modifier.padding(horizontal = d.gutter, vertical = d.gapXs)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = d.gutter),
            horizontalArrangement = Arrangement.spacedBy(d.gapSm)
        ) {
            if (reels.isEmpty()) {
                // First load: the cards' own shape, so the row does not jump when
                // they arrive.
                items(CARDS_WHILE_LOADING) {
                    Box(
                        Modifier
                            .size(CARD_WIDTH, CARD_HEIGHT)
                            .shimmer(RoundedCornerShape(d.radiusMd))
                    )
                }
            } else {
                itemsIndexed(reels, key = { _, reel -> reel.id }) { index, reel ->
                    ReelCard(reel) { onOpen(index) }
                    // Three from the end of what is loaded, the next page is asked
                    // for - early enough that scrolling does not reach an edge.
                    if (index >= reels.size - 3) {
                        LaunchedEffect(reels.size) { ReelsFeed.loadMore(token) }
                    }
                }
            }
        }
    }
}

/**
 * One reel in the strip: a fixed upright card, with the picture inside it at its
 * own shape.
 *
 * The cards are all one size, so the row reads as a row. The picture inside fills
 * the card's width and takes whatever height its own ratio gives it, centred, with
 * the card's dark ground above and below where it is shorter - so a square reel
 * shows as a square inside the card rather than having its sides cut away. Cropping
 * every thumbnail to fill the card made anything not upright lose most of itself.
 *
 * An upright 9:16 reel - nearly all of them - fills the card exactly, as before.
 */
@Composable
private fun ReelCard(reel: Reel, onClick: () -> Unit) {
    val d = IqTheme.dimens
    val thumb by rememberThumbnail(reel.thumbnailUrl)
    Box(
        Modifier
            .size(CARD_WIDTH, CARD_HEIGHT)
            .clip(RoundedCornerShape(d.radiusMd))
            .background(Ink)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        thumb?.let {
            // Width first, height from the picture's own ratio - and softened, so
            // the card reads as "a reel, tap to watch" rather than as a still
            // photograph. The edges stay square under the blur: the card already
            // rounds them, and an unbounded blur would fade the picture into a halo
            // inside its own frame.
            //
            // The blur exists from Android 12. Below that the picture stays sharp,
            // and the play mark still says what the card is.
            Image(
                it,
                null,
                Modifier
                    .fillMaxSize()
                    .blur(CARD_BLUR, BlurredEdgeTreatment.Rectangle),
                contentScale = ContentScale.FillWidth
            )
        }
        // What a tap does, in the middle where a thumb lands.
        Box(
            Modifier
                .size(PLAY_MARK)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                IqIcons.PlayArrow,
                "Play",
                Modifier.size(PLAY_MARK * 0.55f),
                tint = Color.White
            )
        }
        // A wash at the bottom, so the name reads over a bright frame.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.7f)
                    )
                )
        )
        reel.person?.let { who ->
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(d.gapSm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(
                    person = Person(
                        id = who.uid,
                        name = who.fullname,
                        handle = "",
                        photo = who.profilePicture
                    ),
                    size = d.scaled(22)
                )
                Spacer(Modifier.width(d.gapXs))
                Text(
                    who.fullname.substringBefore(' ').ifBlank { who.fullname },
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/* ========================================================================= */
/*  The viewer                                                               */
/* ========================================================================= */

/**
 * The reels, full screen, one at a time, swiped upward.
 *
 * Opened at the reel that was tapped. One player plays whichever page has settled;
 * the pages either side show their thumbnails, so a swipe never reveals a black
 * page while the next video starts. The reels ahead are already loading by then -
 * see [ReelSession] for how far ahead, and why that depends on the network.
 */
@OptIn(UnstableApi::class)
@Composable
fun ReelsScreen(
    start: Int,
    onBack: () -> Unit,
    onOpenPerson: (uid: String, name: String, photo: String) -> Unit
) {
    SystemBarIcons(darkIcons = false)
    val d = IqTheme.dimens
    val context = LocalContext.current
    val token = remember { SessionStore(context).token }

    val reels = ReelsFeed.reels
    if (reels.isEmpty()) {
        // Nothing to show - a process restarted onto this screen with the feed
        // gone. Back to where the feed is loaded, rather than a black page.
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val session = remember { ReelSession(context) }
    DisposableEffect(session) { onDispose { session.release() } }

    val pager = rememberPagerState(initialPage = start.coerceIn(0, reels.lastIndex)) {
        reels.size
    }
    var paused by remember { mutableStateOf(false) }

    // Every reel known to the preload manager, including each page as it arrives.
    LaunchedEffect(reels) { session.sync(reels) }

    // The settled page is the one that plays. Not the current page: that changes
    // the moment a swipe passes halfway, and starting a video for a page that is
    // then let go of and springs back would start and stop it for nothing.
    LaunchedEffect(pager.settledPage) {
        paused = false
        session.playAt(pager.settledPage, reels)
        if (pager.settledPage >= reels.size - 4) ReelsFeed.loadMore(token)
    }

    // Quiet when the app is not on screen, and back where it was when it returns.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val watch = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> session.pause()
                Lifecycle.Event.ON_RESUME -> if (!paused) session.resume()
                else -> Unit
            }
        }
        lifecycle.addObserver(watch)
        onDispose { lifecycle.removeObserver(watch) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        VerticalPager(
            state = pager,
            key = { reels[it].id },
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val playing = page == pager.settledPage
            ReelPage(
                reel = reels[page],
                player = if (playing) session.player else null,
                videoReady = playing && session.shown == reels[page].id,
                paused = playing && paused,
                onTap = {
                    paused = !paused
                    if (paused) session.pause() else session.resume()
                },
                onOpenPerson = onOpenPerson
            )
        }

        Box(
            Modifier
                .statusBarsPadding()
                .padding(d.gapSm)
                .size(d.iconButtonLg)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.3f))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(IqIcons.ArrowBack, "Back", Modifier.size(d.icon), tint = Color.White)
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun ReelPage(
    reel: Reel,
    player: Player?,
    /** This reel's own first frame has been drawn; until then, its thumbnail. */
    videoReady: Boolean,
    paused: Boolean,
    onTap: () -> Unit,
    onOpenPerson: (uid: String, name: String, photo: String) -> Unit
) {
    val d = IqTheme.dimens
    val thumb by rememberThumbnail(reel.thumbnailUrl)
    Box(
        Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap
            )
    ) {
        // Under the video, and what shows until its first frame does. Framed the
        // same way the video is - the screen's width, its own height, centred - so
        // the first frame lands exactly on top of it instead of jumping in size.
        thumb?.let {
            Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillWidth)
        }
        if (player != null) {
            AndroidView(
                factory = { ctx ->
                    LayoutInflater.from(ctx).inflate(R.layout.reel_player, null) as PlayerView
                },
                // Attached at once, so the surface is ready, but not seen until
                // the frame on it belongs to this reel - see ReelSession.shown.
                // On the view itself rather than a Compose layer: a TextureView
                // honours its own alpha without a second layer around it.
                update = { view ->
                    view.player = player
                    view.alpha = if (videoReady) 1f else 0f
                },
                // Let go of the player as the page leaves, so the next page can
                // take it without two views drawing one video.
                onRelease = { view -> view.player = null },
                modifier = Modifier.fillMaxSize()
            )
        }

        AnimatedVisibility(
            visible = paused,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                Modifier
                    .size(d.scaled(72))
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(IqIcons.PlayArrow, "Play", Modifier.size(d.scaled(36)), tint = Color.White)
            }
        }

        // How it has been received: down the right side, centred top to bottom,
        // where a thumb holding the phone rests. Numbers and not buttons - liking
        // and commenting are not in the API yet, and a heart that does nothing
        // when pressed is worse than one that is not offered.
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = d.gapSm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(d.gapLg)
        ) {
            Counter(IqIcons.Favorite, reel.likes, if (reel.liked) Color(0xFFFF2D63) else Color.White)
            Counter(IqIcons.ChatFilled, reel.comments, Color.White)
            Counter(IqIcons.EyeFilled, reel.views, Color.White)
        }

        // Who, and what they said, along the bottom, on a black wash that rises
        // from the bottom edge to just above the name.
        //
        // The wash belongs to the words rather than to the screen: it is drawn
        // behind this column, so it is exactly as tall as what it has to carry - a
        // name alone, or a name and three lines of caption - and never darkens
        // more of the video than the words need. Drawn before the paddings, so it
        // runs to the screen's edges and under the gesture bar, with the extra top
        // padding as the stretch where it fades in.
        //
        // The text keeps clear of the lane the counters run down, so a long
        // caption never slides under them; the wash does not need to.
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(
                    // Clear at the top of the rise, already well into black by
                    // the time it reaches the name, and nearly solid at the
                    // bottom edge - so the name sits on dark whatever the video
                    // has behind it, subtitles included.
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.3f to Color.Black.copy(alpha = 0.6f),
                        0.6f to Color.Black.copy(alpha = 0.8f),
                        1f to Color.Black.copy(alpha = 0.9f)
                    )
                )
                .navigationBarsPadding()
                .padding(
                    start = d.gutter,
                    end = COUNTER_SIZE + d.gapSm * 2,
                    top = WASH_RISE,
                    bottom = d.gapXl
                )
        ) {
            Column {
                reel.person?.let { who ->
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(d.radiusMd))
                            .clickable {
                                onOpenPerson(who.uid, who.fullname, who.profilePicture)
                            }
                            .padding(vertical = d.gapXs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(
                            person = Person(
                                id = who.uid,
                                name = who.fullname,
                                handle = "",
                                photo = who.profilePicture
                            ),
                            size = AVATAR_SIZE
                        )
                        Spacer(Modifier.width(d.gapSm))
                        Text(
                            who.fullname,
                            style = MaterialTheme.typography.titleMedium.copy(shadow = TextShade),
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (reel.text.isNotBlank()) {
                    Spacer(Modifier.height(d.gapXs))
                    Text(
                        reel.text,
                        style = MaterialTheme.typography.bodyMedium.copy(shadow = TextShade),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * One count on the right of a reel: its icon and its number, both shadowed.
 *
 * The shadow is what keeps white legible over a video that is white itself - a
 * sky, a wall, a shirt. Light on purpose: it should read as the icon standing off
 * the picture, not as a dark smudge around it.
 *
 * An icon has no shadow of its own to switch on, so it gets a copy of itself
 * underneath, dark, a little lower, and blurred. The blur exists from Android 12;
 * below that the copy is drawn sharp, which is still a shadow and still does the
 * job.
 */
@Composable
private fun Counter(icon: ImageVector, count: Int, tint: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Icon(
                icon,
                null,
                Modifier
                    .size(COUNTER_SIZE)
                    .offset(y = SHADOW_DROP)
                    .blur(SHADOW_BLUR),
                tint = Color.Black.copy(alpha = 0.35f)
            )
            Icon(icon, null, Modifier.size(COUNTER_SIZE), tint = tint)
        }
        Text(
            compact(count),
            style = MaterialTheme.typography.labelMedium.copy(shadow = TextShade),
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

/** The same light shadow under every word drawn over a reel. */
private val TextShade = Shadow(
    color = Color.Black.copy(alpha = 0.45f),
    offset = Offset(0f, 2f),
    blurRadius = 6f
)

/**
 * 950, 1.2K, 3.4M - the way every counter on every reel has always read.
 *
 * Never below zero. The feed has been seen sending a like count of -6, which can
 * only be a fault in how the server keeps the tally; a minus sign under a heart
 * says nothing anyone can use, so it is shown as none rather than as a number.
 */
private fun compact(count: Int): String {
    val n = count.coerceAtLeast(0)
    return when {
        n >= 1_000_000 -> "%.1fM".format(n / 1_000_000f).replace(".0M", "M")
        n >= 1_000 -> "%.1fK".format(n / 1_000f).replace(".0K", "K")
        else -> n.toString()
    }
}

/**
 * A reel's thumbnail, keyed by its address.
 *
 * `produceState` restarts its loader when the key changes but keeps the *value*
 * - it is remembered without a key. A page or card reused for a different reel
 * therefore went on holding the previous reel's picture, and the loader, seeing a
 * picture already there, never fetched the new one. Remembered against the URL,
 * a new reel starts from its own picture or from nothing, never from somebody
 * else's.
 */
@Composable
private fun rememberThumbnail(url: String): State<ImageBitmap?> {
    val picture = remember(url) { mutableStateOf(RemoteImage.peek(url, RemoteImage.PREVIEW_PX)) }
    LaunchedEffect(url) {
        if (picture.value == null && url.isNotBlank()) {
            picture.value = RemoteImage.load(url, targetPx = RemoteImage.PREVIEW_PX)
        }
    }
    return picture
}

/** Every card's width: with [CARD_HEIGHT], an upright 9:16 card. */
private val CARD_WIDTH = androidx.compose.ui.unit.Dp(96f)

/** Every card's height. */
private val CARD_HEIGHT = androidx.compose.ui.unit.Dp(170f)

/** How soft the pictures in the strip are: about half way to unreadable. */
private val CARD_BLUR = androidx.compose.ui.unit.Dp(6f)

/** The circle and arrow over each card in the strip. */
private val PLAY_MARK = androidx.compose.ui.unit.Dp(36f)

/** Where the wash under a reel's name starts to fade in, above the name. */
private val WASH_RISE = androidx.compose.ui.unit.Dp(48f)

/** The like, comment and view icons on the viewer. */
private val COUNTER_SIZE = androidx.compose.ui.unit.Dp(40f)

/** The poster's picture beside their name on the viewer. */
private val AVATAR_SIZE = androidx.compose.ui.unit.Dp(35f)

/** How far below the icon its shadow falls, and how soft it is. */
private val SHADOW_DROP = androidx.compose.ui.unit.Dp(1.5f)
private val SHADOW_BLUR = androidx.compose.ui.unit.Dp(3f)
private const val CARDS_WHILE_LOADING = 5
