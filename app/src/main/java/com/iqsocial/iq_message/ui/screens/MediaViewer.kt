package com.iqsocial.iq_message.ui.screens

import android.media.MediaPlayer
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.iqsocial.iq_message.data.auth.SessionStore
import com.iqsocial.iq_message.data.media.MediaCache
import com.iqsocial.iq_message.data.media.MediaFiles
import com.iqsocial.iq_message.ui.icons.IqIcons
import com.iqsocial.iq_message.ui.theme.IqTheme
import java.io.File
import kotlin.math.abs
/**
 * A photo or a video, filling the screen.
 *
 * The bubble shows a preview sized for a conversation - half the column wide, and
 * fetched as a thumbnail so a thread of photos is not a thread of originals. That is
 * right for reading past it and wrong for looking at it, and there was no way to do
 * the second: tapping a picture did nothing at all.
 *
 * Black, and over everything. A picture is judged against what surrounds it, and the
 * surroundings here should be nothing - not the thread it came from, and not the
 * app's own white.
 */
@Composable
fun MediaViewer(
    messageId: String,
    video: Boolean,
    /** What the bubble is already showing, so the screen is never empty first. */
    placeholder: ImageBitmap?,
    aspect: Float,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        // The dialog's own width would leave the picture in a box in the middle of a
        // dimmed screen. This is the screen.
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (video) {
                VideoStage(messageId, aspect)
            } else {
                PhotoStage(messageId, placeholder, onDismiss)
            }

            // Last, so it stays above the picture however far that is zoomed.
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(IqTheme.dimens.gapMd)
                    .size(IqTheme.dimens.iconButton)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.14f))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    IqIcons.Close,
                    "Close",
                    Modifier.size(IqTheme.dimens.icon),
                    tint = Color.White
                )
            }
        }
    }
}

/**
 * The photo, zoomable.
 *
 * Two gestures, because people reach for both: a pinch, and a double tap for those
 * who do not want to use two fingers. Double tap toggles rather than steps through
 * magnifications - one tap in and one tap back out is the whole interaction, and a
 * ladder of zoom levels means tapping repeatedly to escape.
 *
 * Panning is bounded to the picture. Without it a zoomed photo can be flung off the
 * screen and left there, with nothing to say which direction it went.
 */
@Composable
private fun PhotoStage(
    messageId: String,
    placeholder: ImageBitmap?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val token = remember { SessionStore(context).token }
    var full by remember(messageId) { mutableStateOf(MediaCache.peek(messageId)) }

    // The full file, not the thumbnail the bubble settled for.
    LaunchedEffect(messageId, token) {
        full = MediaCache.load(token, messageId, thumbnailOnly = false) ?: full
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var frame by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

    fun clamp() {
        // How far the picture may slide is half of what sticking out past the frame
        // leaves over - beyond that an edge comes inside the screen.
        val slackX = (frame.width * (scale - 1f) / 2f).coerceAtLeast(0f)
        val slackY = (frame.height * (scale - 1f) / 2f).coerceAtLeast(0f)
        offsetX = offsetX.coerceIn(-slackX, slackX)
        offsetY = offsetY.coerceIn(-slackY, slackY)
    }

    val shown = full ?: placeholder

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { frame = it }
            .pointerInput(messageId) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                    if (scale > 1f) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                    clamp()
                }
            }
            .pointerInput(messageId) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            scale = DOUBLE_TAP_ZOOM
                        }
                    },
                    // A tap on a picture at rest closes it, the way a full-screen
                    // photo closes everywhere. Only at rest: tapping while zoomed in
                    // is how a picture is examined, not how it is dismissed.
                    onTap = { if (abs(scale - 1f) < 0.01f) onDismiss() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (shown != null) {
            Image(
                bitmap = shown,
                contentDescription = "Photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    }
            )
        }
        if (full == null) {
            CircularProgressIndicator(color = Color.White.copy(alpha = 0.8f))
        }
    }
}

/**
 * The video, played from a file.
 *
 * Downloaded before it plays rather than streamed, and that is not a shortcut: the
 * media route answers a redirect to a signed link, and a player handed the original
 * URL would take the bearer token along to storage, which refuses it. [MediaFiles]
 * walks that redirect with the token dropped at the first hop.
 *
 * `MediaPlayer` on a `SurfaceView` rather than `VideoView`, because `VideoView`
 * insists on sizing itself to the video and cannot be told to fit a box; the aspect
 * ratio is known from the message, so the box is sized here and the surface fills
 * it.
 */
@Composable
private fun VideoStage(messageId: String, aspect: Float) {
    val context = LocalContext.current
    val token = remember { SessionStore(context).token }
    var file by remember(messageId) { mutableStateOf<File?>(null) }
    var failed by remember(messageId) { mutableStateOf(false) }
    var playing by remember(messageId) { mutableStateOf(true) }
    var player by remember(messageId) { mutableStateOf<MediaPlayer?>(null) }

    LaunchedEffect(messageId, token) {
        val auth = token
        if (auth.isNullOrBlank()) {
            failed = true
            return@LaunchedEffect
        }
        val got = MediaFiles.cached(context.cacheDir, auth, messageId, "video_")
        if (got == null) failed = true else file = got
    }

    DisposableEffect(messageId) {
        onDispose {
            // Released by hand. A MediaPlayer left behind holds a decoder, and
            // there are only so many of those on a phone - the next video to be
            // opened is the one that silently fails.
            runCatching { player?.release() }
            player = null
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(aspect.coerceIn(0.4f, 2.5f)),
        contentAlignment = Alignment.Center
    ) {
        val ready = file
        if (ready != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    SurfaceView(ctx).also { view ->
                        view.holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) {
                                runCatching {
                                    MediaPlayer().apply {
                                        setDataSource(ready.absolutePath)
                                        setDisplay(holder)
                                        isLooping = true
                                        prepare()
                                        start()
                                        player = this
                                    }
                                }
                            }

                            override fun surfaceChanged(
                                holder: SurfaceHolder,
                                format: Int,
                                width: Int,
                                height: Int
                            ) = Unit

                            override fun surfaceDestroyed(holder: SurfaceHolder) {
                                runCatching { player?.release() }
                                player = null
                            }
                        })
                    }
                }
            )

            // The whole picture is the play/pause target. A small button would be
            // one more thing drawn over a video that is being watched.
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        val p = player ?: return@clickable
                        runCatching {
                            if (p.isPlaying) {
                                p.pause()
                                playing = false
                            } else {
                                p.start()
                                playing = true
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (!playing) {
                    Box(
                        Modifier
                            .size(IqTheme.dimens.scaled(64))
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            IqIcons.PlayArrow,
                            "Play",
                            Modifier.size(IqTheme.dimens.iconXl),
                            tint = Color.White
                        )
                    }
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (failed) {
                    Text(
                        "Could not play that video.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                } else {
                    CircularProgressIndicator(color = Color.White.copy(alpha = 0.8f))
                    Spacer(Modifier.height(IqTheme.dimens.gapMd))
                    Text(
                        "Loading video",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

/** Far enough in to read small print in a photographed document. */
private const val MAX_ZOOM = 5f

/** One tap in, one tap back out. */
private const val DOUBLE_TAP_ZOOM = 2.5f
