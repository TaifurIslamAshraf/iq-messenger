package com.iqsocial.iq_message.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.iqsocial.iq_message.data.Person
import com.iqsocial.iq_message.data.call.CallCenter
import com.iqsocial.iq_message.data.call.CallEngine
import com.iqsocial.iq_message.ui.components.Avatar
import com.iqsocial.iq_message.ui.components.CallVideo
import com.iqsocial.iq_message.ui.theme.IqTheme
import kotlinx.coroutines.delay

/**
 * The call, in the floating window.
 *
 * The face, the name and the duration - and deliberately no buttons.
 *
 * The mute, hang-up and speaker buttons are on this window too, but they are not
 * drawn here and cannot be: **a picture-in-picture window does not pass touch to its
 * content.** A tap anywhere in it means "make this big again", so a button composed
 * inside would be a picture of a button - pressing it would open the call rather
 * than mute it. They are `RemoteAction`s instead, which the system draws on the
 * window itself and which do work; see `callPipParams` in MainActivity.
 *
 * So what is left for this to do is say who, and for how long. A call with a
 * picture shows the picture, because that is the call; everything else shows the
 * face, because a black rectangle says nothing - and under the face, the clock,
 * which on a voice call is the only thing in the window that moves.
 */
@Composable
fun CallPipScreen() {
    val d = IqTheme.dimens
    val call = CallCenter.call
    /**
     * Whatever picture the call has - theirs first, then this phone's own camera.
     *
     * Not `type == VIDEO`, which was the test here and disagreed with the window it
     * was being drawn into. The shape comes from `CallWindow.video`, which counts
     * any live picture rather than the server's word, so a voice call with a camera
     * on minimises into a 9:16 frame - and this put a round face in the middle of
     * it. Asking the same question as the shape does keeps the two together.
     */
    val picture = CallEngine.remoteVideo
        ?: CallEngine.localVideo?.takeIf { CallEngine.cameraOn }

    /**
     * How long the call has been up, counted from when it was **answered**.
     *
     * The same reckoning as the two full screens and the bar, and for the same
     * reason: the window can be opened half way through a call it never saw start,
     * so counting from when this appeared would restart the clock on every minimise.
     */
    var seconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(call?.answeredAt) {
        val from = call?.answeredAt ?: 0L
        while (true) {
            seconds =
                if (from > 0L) ((System.currentTimeMillis() - from) / 1000).toInt() else 0
            delay(1000)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (picture != null) {
            CallVideo(track = picture, modifier = Modifier.fillMaxSize())
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(d.gapSm)
            ) {
                // Large for the window it is in. The system leaves room under the
                // content for its own row of buttons, so this takes the rest rather
                // than sitting as a small disc in a lot of black.
                Avatar(
                    person = Person(
                        id = call?.peer?.uid.orEmpty(),
                        name = call?.peer?.fullname.orEmpty().ifBlank { "Call" },
                        handle = "",
                        photo = call?.peer?.profilePicture.orEmpty()
                    ),
                    size = d.avatarXl
                )
                Spacer(Modifier.height(d.gapXs))
                Text(
                    call?.peer?.fullname.orEmpty().ifBlank { "Call" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                // What the call is doing, which is the question a glance at a
                // thumbnail is asking. It said "In call" - a fallback for a missing
                // name that read as a state, and read it wrongly: a call still
                // ringing is not one you are in.
                //
                // The clock once it is counting, and what the call is waiting for
                // before that. This line used to go away once connected, on the
                // reasoning that a window this small should not repeat what the face
                // and the name already give - but the duration is the one thing
                // neither of them carries, and on a voice call it is the only moving
                // part there is. So the slot stays, and holds whichever of the two
                // is worth reading.
                val standing = standingOf(
                    call,
                    joined = CallEngine.connected,
                    reconnecting = CallEngine.reconnecting
                )
                Spacer(Modifier.height(d.gapXs))
                Text(
                    if (seconds > 0) formatDuration(seconds) else standing.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
