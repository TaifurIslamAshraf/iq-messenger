package com.iqsocial.iq_message.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.iqsocial.iq_message.data.call.CallEngine
import io.livekit.android.renderer.TextureViewRenderer
import io.livekit.android.room.track.VideoTrack
// LiveKit ships WebRTC repackaged under its own name, so this is not org.webrtc:
// the platform classes and the SDK's would otherwise clash in one app.
import livekit.org.webrtc.RendererCommon

/**
 * One video track, drawn.
 *
 * `TextureViewRenderer` from the SDK itself rather than the separate Compose
 * components artifact: this is the only thing that would have been used out of it,
 * and the renderer is the piece that artifact wraps anyway.
 *
 * Two things here are easy to leave out and silent when missing:
 *
 * - **The renderer is initialised against the room**, because it needs the room's
 *   EGL context - the same one the decoder draws into. Without it the view stays
 *   black and nothing is logged.
 * - **The track is attached to the renderer and detached again.** A renderer that is
 *   never attached shows nothing; one that is never detached goes on decoding frames
 *   into a view that has gone.
 */
@Composable
fun CallVideo(
    track: VideoTrack?,
    modifier: Modifier = Modifier,
    /**
     * Fill or fit.
     *
     * Filling, because a call is looked at rather than framed: fitting a portrait
     * picture into a phone leaves two black bars where the person should be.
     */
    fill: Boolean = true,
    /** Your own camera is drawn mirrored, as a mirror would. */
    mirror: Boolean = false
) {
    var view by remember { mutableStateOf<TextureViewRenderer?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextureViewRenderer(context).also { renderer ->
                CallEngine.initRenderer(renderer)
                renderer.setScalingType(
                    if (fill) RendererCommon.ScalingType.SCALE_ASPECT_FILL
                    else RendererCommon.ScalingType.SCALE_ASPECT_FIT
                )
                renderer.setEnableHardwareScaler(true)
                view = renderer
            }
        },
        update = { it.setMirror(mirror) },
        onRelease = { runCatching { it.release() } }
    )

    DisposableEffect(track, view) {
        val renderer = view
        if (track != null && renderer != null) {
            runCatching { track.addRenderer(renderer) }
        }
        onDispose {
            if (track != null && renderer != null) {
                runCatching { track.removeRenderer(renderer) }
            }
        }
    }
}
