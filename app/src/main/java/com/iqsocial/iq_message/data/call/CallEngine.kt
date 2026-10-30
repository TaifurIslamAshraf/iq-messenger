package com.iqsocial.iq_message.data.call

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.iqsocial.iq_message.data.remote.CallConnection
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.participant.ConnectionQuality
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The media half of a call: one LiveKit room, and the tracks in it.
 *
 * Deliberately knows nothing about ringing, declining or history. Those are the call
 * API's business and change for their own reasons; this connects to a room, turns a
 * microphone and a camera on and off, and says what it can see. Keeping the two
 * apart is what lets the ringing screen exist before there is a room to join, and
 * the room outlive the screen that opened it.
 */
object CallEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var room: Room? = null
    private var events: Job? = null

    /** True once this device is actually in the room, not merely dialling. */
    var connected by mutableStateOf(false)
        private set

    /**
     * True while the SDK is putting a dropped connection back together.
     *
     * The call is not over in this state and must not be treated as if it were: a
     * lift, a tunnel, or a handover from wi-fi to mobile data all land here for a few
     * seconds and then recover by themselves. `Disconnected` used to be the only
     * event watched, and it tore the call down for every one of them.
     */
    var reconnecting by mutableStateOf(false)
        private set

    /** The worst connection in the room, which is what decides whether it is usable. */
    var poorConnection by mutableStateOf(false)
        private set

    var micOn by mutableStateOf(false)
        private set

    var cameraOn by mutableStateOf(false)
        private set

    /**
     * Whether the call is on the loudspeaker.
     *
     * Derived, not held. It was its own `mutableStateOf` while the route was a
     * boolean, and it stopped being the truth the moment the route grew a third
     * value: a call sent to a headset left this reading false, which the
     * picture-in-picture button and the video screen both drew as "earpiece". Two
     * answers to one question, and the stale one was the one most of the app asked.
     *
     * [CallAudio.route] is the only place that knows, because it is the only place
     * that sets it - and reading its state here keeps every caller observing.
     */
    val speakerOn: Boolean get() = CallAudio.route == CallAudio.Route.SPEAKER

    /** Your own camera, for the small preview. Null until it is on. */
    var localVideo by mutableStateOf<VideoTrack?>(null)
        private set

    /**
     * The other side's camera, for the big picture. Null on an audio call.
     *
     * The first of [remoteFeeds], kept because a one-to-one call and the floating
     * window both only ever want one.
     */
    val remoteVideo: VideoTrack? get() = remoteFeeds.firstOrNull()?.video

    /**
     * Everyone else in the room, each with whatever they are sending.
     *
     * This used to be a single track - `firstOrNull` over every remote
     * publication - which was right while a call was two people and became wrong
     * the moment people could be added to one: a call with three in it showed one
     * of them, chosen arbitrarily, and the others were in the room unseen.
     *
     * A participant with their camera off is still here, with a null [video]. They
     * have to be: a grid that drops them has somebody disappear when they cover
     * their lens, and the person who muted their camera is still in the call.
     */
    var remoteFeeds by mutableStateOf<List<RemoteFeed>>(emptyList())
        private set

    /**
     * True while this end is leaving the room on purpose.
     *
     * The SDK reports a hang-up and a collapse with the same event, so without this
     * there is no way to tell them apart - and they need opposite answers.
     */
    private var leaving = false

    /**
     * The room went down and did not come back.
     *
     * Set by [CallCenter], which is the only part that can do anything about it: the
     * server has to be told, or the call goes on running on the other phone with
     * nobody in it from this end.
     *
     * Why this exists at all is written in the file's history. `Disconnected` once
     * tore the call down every time, which broke every lift and tunnel; the fix
     * watched [reconnecting] instead and left `Disconnected` doing nothing but
     * tidying up here - so a call that really had collapsed became a screen reading
     * "Connecting" for ever, over a call the other end still thought was live.
     * Neither extreme is right: the SDK gives up only after its own retries, and
     * when it does, the call is over and somebody has to say so.
     */
    var onConnectionLost: (() -> Unit)? = null

    /** Who is talking right now, by LiveKit identity - which is the account uid. */
    var speaking by mutableStateOf<Set<String>>(emptySet())
        private set

    /**
     * Joins the room and publishes.
     *
     * The camera starts on only for a video call. A microphone that cannot be opened
     * - permission refused, another app holding it - leaves the call connected and
     * muted rather than failing it: being in a call you cannot speak on is recoverable,
     * and being dropped from one is not.
     */
    fun connect(context: Context, connection: CallConnection, video: Boolean) {
        if (room != null) return
        leaving = false
        val created = LiveKit.create(appContext = context.applicationContext)
        room = created

        events = scope.launch {
            created.events.collect { event ->
                when (event) {
                    is RoomEvent.TrackSubscribed,
                    is RoomEvent.TrackUnsubscribed,
                    is RoomEvent.TrackPublished,
                    is RoomEvent.TrackUnpublished,
                    is RoomEvent.ParticipantConnected,
                    is RoomEvent.ParticipantDisconnected,
                    is RoomEvent.TrackMuted,
                    is RoomEvent.TrackUnmuted -> refresh(created)

                    is RoomEvent.ActiveSpeakersChanged ->
                        speaking = event.speakers.mapNotNull { it.identity?.value }.toSet()

                    // Dropped, and being put back together. Not an ending: the
                    // room keeps its tracks and the call keeps its timer, and
                    // tearing down here is what made a call die every time a
                    // phone passed through a tunnel.
                    is RoomEvent.Reconnecting -> reconnecting = true

                    is RoomEvent.Reconnected -> {
                        reconnecting = false
                        refresh(created)
                    }

                    // Said rather than acted on. A bad connection is the person's
                    // to do something about - move, or switch the camera off -
                    // and an app that decided for them would be dropping calls
                    // that were still usable.
                    is RoomEvent.ConnectionQualityChanged ->
                        poorConnection = event.quality == ConnectionQuality.POOR

                    // Ours or not. A hang-up only has to be tidied up; a
                    // collapse is a call that has just died, and the server and the
                    // person both have to hear about it.
                    //
                    // The handler is taken first because `clear` cancels this very
                    // collector, and read through a local so a disconnect arriving
                    // while it is being changed cannot lose it.
                    is RoomEvent.Disconnected -> {
                        val dropped = !leaving
                        val tell = onConnectionLost
                        clear()
                        if (dropped) tell?.invoke()
                    }
                    else -> Unit
                }
            }
        }

        scope.launch {
            runCatching { created.connect(connection.url, connection.token) }
                .onFailure {
                    clear()
                    return@launch
                }
            connected = true
            // Audio first. A call with no picture is a call; a call with no sound is
            // not, so the microphone is never left behind a camera that may fail.
            runCatching { created.localParticipant.setMicrophoneEnabled(true) }
                .onSuccess { micOn = true }
            if (video) {
                runCatching { created.localParticipant.setCameraEnabled(true) }
                    .onSuccess { cameraOn = true }
            }
            // The earpiece for a voice call, the loudspeaker for a video one: a
            // video call is held away from the face. A headset overrides both.
            // A video call is held away from the face, a voice call against it -
            // and a headset beats both, because wearing one has already answered
            // the question.
            CallAudio.begin(
                context,
                when {
                    CallAudio.headsetAttached(context) -> CallAudio.Route.BLUETOOTH
                    video -> CallAudio.Route.SPEAKER
                    else -> CallAudio.Route.EARPIECE
                }
            )
            refresh(created)
        }
    }

    fun toggleMic() {
        val r = room ?: return
        val want = !micOn
        scope.launch {
            runCatching { r.localParticipant.setMicrophoneEnabled(want) }
                .onSuccess { micOn = want }
        }
    }

    fun toggleCamera() {
        val r = room ?: return
        val want = !cameraOn
        scope.launch {
            runCatching { r.localParticipant.setCameraEnabled(want) }
                .onSuccess {
                    cameraOn = want
                    refresh(r)
                }
        }
    }

    /** Front to back and back again. */
    fun flipCamera() {
        val r = room ?: return
        scope.launch {
            runCatching {
                r.localParticipant.getTrackPublication(io.livekit.android.room.track.Track.Source.CAMERA)
                    ?.track
                    ?.let { it as? io.livekit.android.room.track.LocalVideoTrack }
                    ?.switchCamera()
            }
        }
    }

    /**
     * Earpiece or loudspeaker - and neither, when a headset is attached.
     *
     * [CallAudio] decides what that actually means: forcing the loudspeaker here
     * would cut off a Bluetooth headset, which is the one case where the person has
     * already said where they want the call.
     */
    /**
     * Earpiece, speaker, headset, round again - skipping the headset when none is
     * there, so the button is never a no-op.
     *
     * One button rather than a sheet of radio buttons: there are at most three
     * places a call can come out of, and cycling is how every phone app has offered
     * them.
     */
    fun cycleRoute() {
        val context = appContext ?: return
        val next = when (CallAudio.route) {
            CallAudio.Route.EARPIECE -> CallAudio.Route.SPEAKER
            CallAudio.Route.SPEAKER ->
                if (CallAudio.headsetAvailable) CallAudio.Route.BLUETOOTH
                else CallAudio.Route.EARPIECE
            CallAudio.Route.BLUETOOTH -> CallAudio.Route.EARPIECE
        }
        CallAudio.apply(context, next)
    }

    fun setSpeaker(on: Boolean) {
        appContext?.let {
            CallAudio.apply(it, if (on) CallAudio.Route.SPEAKER else CallAudio.Route.EARPIECE)
        }
    }

    private var appContext: Context? = null

    /**
     * Hands a renderer the room's drawing context.
     *
     * Required before it will show anything: the decoder draws into the room's EGL
     * context, and a renderer that does not share it stays black. Harmless before a
     * room exists - the view simply waits for the next one.
     */
    fun initRenderer(view: io.livekit.android.renderer.TextureViewRenderer) {
        runCatching { room?.initVideoRenderer(view) }
    }

    /** Leaves the room and lets go of everything it was holding. */
    fun disconnect() {
        // Said before the room is touched, so the `Disconnected` this causes is
        // read as a hang-up rather than as the line going dead.
        leaving = true
        runCatching { room?.disconnect() }
        runCatching { room?.release() }
        clear()
    }

    private fun clear() {
        // Back to whatever the phone was doing before the call took the stream.
        CallAudio.end()
        events?.cancel()
        events = null
        room = null
        connected = false
        reconnecting = false
        poorConnection = false
        micOn = false
        cameraOn = false
        localVideo = null
        remoteFeeds = emptyList()
        speaking = emptySet()
    }

    /**
     * Re-reads the tracks after anything that could have changed them.
     *
     * Rebuilt from the room rather than tracked event by event: the events say *what*
     * happened and the room says *what is true*, and only the second one cannot drift.
     */
    private fun refresh(r: Room) {
        localVideo = r.localParticipant.videoTrackPublications
            .firstOrNull { it.second is VideoTrack }
            ?.second as? VideoTrack

        remoteFeeds = r.remoteParticipants.values.map { p: Participant ->
            RemoteFeed(
                // LiveKit's identity is the account uid, which is what ties a feed
                // to the person the call object already describes.
                uid = p.identity?.value.orEmpty(),
                name = p.name.orEmpty(),
                video = p.videoTrackPublications
                    .mapNotNull { it.second as? VideoTrack }
                    .firstOrNull()
            )
        }
    }

    /** One other person in the room, and what they are sending. */
    @androidx.compose.runtime.Immutable
    data class RemoteFeed(
        val uid: String,
        val name: String,
        /** Null while their camera is off, which is not the same as them being gone. */
        val video: VideoTrack?
    )

    /** Called once at start-up, so routing can be set without a context to hand. */
    fun init(context: Context) {
        appContext = context.applicationContext
        CallAudio.init(context)
    }
}
