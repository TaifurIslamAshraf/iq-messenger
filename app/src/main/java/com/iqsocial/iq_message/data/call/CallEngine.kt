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
import kotlinx.coroutines.delay
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

    /**
     * Everybody else has gone, and the server never said so.
     *
     * The other half of the same failure, seen from the other phone. When a call
     * drops because one side's connection died, that side knows: the SDK gives up,
     * [onConnectionLost] fires, and it tells the server. But it tells the server over
     * the connection it has just lost - so the message does not arrive, and the
     * phone that is still fine hears nothing at all. Its room simply empties, and it
     * sat there running a call with nobody in it until somebody pressed the red
     * button.
     *
     * Set by [CallCenter], which is the only part that can end a call properly.
     */
    var onEveryoneLeft: (() -> Unit)? = null

    /**
     * Whether anybody else has ever been in this room.
     *
     * Without it an empty room would end every outgoing call the instant it started:
     * a call that is still ringing is a room of one, and that is not the same thing
     * as a room that has emptied.
     */
    private var metAnyone = false
    private var emptyRoom: Job? = null

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

        // The call's audio session first, and before anything records or plays.
        //
        // This used to be set after joining, which left two things wrong: the
        // microphone was opened in whatever mode the phone happened to be in, so
        // the platform's echo canceller was configured for the wrong one, and the
        // ringback the caller hears played on a stream that was not yet the call's.
        // Both are decided when the audio session starts, not later.
        CallAudio.begin(
            context,
            when {
                CallAudio.headsetAttached(context) -> CallAudio.Route.BLUETOOTH
                video -> CallAudio.Route.SPEAKER
                else -> CallAudio.Route.EARPIECE
            }
        )

        // The microphone, opened now and published when there is a room to publish
        // to. Opening it costs the time the audio stack takes to come up - the
        // record session, the echo canceller, the routing - and doing that after
        // the join meant the first word or two of every call went nowhere. This is
        // what `prewarm` is for: it starts the recording before anything is
        // published, so the stack is already running when the call connects.
        openMic(created)

        // The camera is opened here, beside the room rather than after it.
        //
        // It used to wait for `connect` to come back - signalling, ICE, the whole
        // join - and only then open the lens, so a video call showed a blank
        // rectangle for a second or two before you appeared in it. Nothing about
        // opening a camera needs a connection: the capturer belongs to this
        // process, and every other calling app shows you yourself while the call
        // is still ringing. Publishing is what needs the room, and that still
        // waits; this only means the picture is ready the moment there is
        // somewhere to send it.
        if (video) openCamera(created)

        events = scope.launch {
            created.events.collect { event ->
                when (event) {
                    is RoomEvent.TrackSubscribed,
                    is RoomEvent.TrackUnsubscribed,
                    is RoomEvent.TrackPublished,
                    is RoomEvent.TrackUnpublished,
                    is RoomEvent.TrackMuted,
                    is RoomEvent.TrackUnmuted -> refresh(created)

                    // The room's population changed, which is the one kind of
                    // refresh that can also mean the call is over.
                    is RoomEvent.ParticipantConnected,
                    is RoomEvent.ParticipantDisconnected -> {
                        refresh(created)
                        watchForEmpty(created)
                    }

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
            val mic = warmMic
            val micUp = mic != null && runCatching {
                created.localParticipant.publishAudioTrack(mic)
            }.getOrDefault(false)
            if (!micUp && mic != null) {
                // Let the recording go before the slow path opens its own, for the
                // same reason as the camera: two of them is one too many.
                warmMic = null
                runCatching { mic.stopPrewarm() }
                runCatching { mic.stop() }
            }
            if (micUp) {
                micOn = true
            } else {
                runCatching { created.localParticipant.setMicrophoneEnabled(true) }
                    .onSuccess { micOn = true }
            }
            if (video) {
                val warm = warmCamera
                val published = warm != null && runCatching {
                    created.localParticipant.publishVideoTrack(warm)
                }.getOrDefault(false)
                // Would not go up, so the lens is let go before anything else
                // tries to open it: a second camera track while this one still
                // holds the camera is a second track that cannot start.
                if (!published && warm != null) {
                    warmCamera = null
                    localVideo = null
                    runCatching { warm.stopCapture() }
                    runCatching { warm.stop() }
                    runCatching { warm.dispose() }
                }
                // The slow path makes a track of its own. It also covers the
                // camera being refused at the moment the call began and granted a
                // second later.
                if (published || enableCamera(created)) cameraOn = true
            }
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
            val done = if (want) {
                enableCamera(r)
            } else {
                runCatching { r.localParticipant.setCameraEnabled(false) }.isSuccess
            }
            if (done) {
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
        // A microphone opened for a call that never connected belongs to nobody,
        // the same as the camera below - and a record session left running is a
        // phone that thinks it is still being listened to.
        warmMic?.let { track ->
            warmMic = null
            if (room?.localParticipant?.audioTrackPublications.isNullOrEmpty()) {
                runCatching { track.stopPrewarm() }
                runCatching { track.stop() }
            }
        }
        // A camera opened for a call that never connected belongs to nobody: the
        // room disposes what it published, and this was never published. Left
        // alone it would hold the lens open after the call was gone.
        warmCamera?.let { track ->
            warmCamera = null
            if (room?.localParticipant?.videoTrackPublications.isNullOrEmpty()) {
                runCatching { track.stopCapture() }
                runCatching { track.stop() }
                runCatching { track.dispose() }
            }
        }
        // Back to whatever the phone was doing before the call took the stream.
        CallAudio.end()
        events?.cancel()
        events = null
        emptyRoom?.cancel()
        emptyRoom = null
        metAnyone = false
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
    /**
     * Ends the call when the last other person has gone and stayed gone.
     *
     * Not the moment the room empties. A phone that drops off a tunnel is removed
     * from the room by the server and rejoins a second or two later, and ending on
     * the first empty reading would turn every lift into a dropped call - the same
     * mistake `Disconnected` used to make from this end. So an empty room is
     * *suspected* for [EMPTY_ROOM_MS] and anybody arriving in that time calls it
     * off.
     *
     * Only after somebody has been here: see [metAnyone].
     */
    private fun watchForEmpty(r: Room) {
        if (r.remoteParticipants.isNotEmpty()) {
            metAnyone = true
            emptyRoom?.cancel()
            emptyRoom = null
            return
        }
        if (!metAnyone || emptyRoom != null) return
        emptyRoom = scope.launch {
            delay(EMPTY_ROOM_MS)
            emptyRoom = null
            // Asked again rather than trusted: the room is the truth, and between
            // the two readings somebody may have come back.
            if (room !== r || r.remoteParticipants.isNotEmpty()) return@launch
            onEveryoneLeft?.invoke()
        }
    }

    /**
     * The look of this camera, hung on whatever track it is publishing.
     *
     * One filter for the life of the process rather than one per call: it holds
     * shader programs and framebuffers, which belong to the capturer's GL context,
     * and it builds them again by itself whenever a new context hands it a frame.
     */
    private val cameraFilter = CameraFilter()

    /**
     * Turns the camera on, with the filter already inside the track.
     *
     * The track is made here rather than left to `setCameraEnabled`, because a
     * filter cannot safely be added to a camera track that already exists. The
     * SDK's way in is `restartTrack(videoProcessor = ...)`, and in 2.29.0 that
     * kills the self-view: a track built without a processor feeds local
     * renderers raw frames through a `CaptureDispatchObserver`, renderers
     * registered that way never reach the `sinks` list that `restartTrack`
     * migrates, and the field holding the observer is not reassigned afterwards -
     * so the old renderers stay bound to a disposed capturer and every later one
     * joins them. The other phone still saw the picture; this one showed an empty
     * box where the self-view should be.
     *
     * Making the track with the processor is the path the SDK documents, and it
     * has no dispatch observer at all: local renderers hang off the track, so the
     * self-view shows exactly the frames being sent - filter included, which is
     * the only way to judge the sliders while moving them.
     *
     * `setCameraEnabled(true)` still finishes the job either way. For the track
     * published here it only unmutes it and starts the capture; if publishing
     * failed - no camera permission, a camera already held - it makes its own
     * unfiltered track, and the call keeps its picture rather than losing it to a
     * filter nobody asked about.
     */
    /** The microphone that was opened before the room was ready. */
    private var warmMic: io.livekit.android.room.track.LocalAudioTrack? = null

    /**
     * Starts recording before there is anywhere to send it.
     *
     * `prewarm` is the SDK's own word for this and does exactly what the name says:
     * it brings up the record session with the platform's echo cancellation and
     * noise suppression configured, without publishing anything. The track is then
     * published the moment the room is joined, and the first thing said into the
     * phone is already being captured rather than waiting for a microphone that has
     * not opened yet.
     *
     * Refused - no microphone permission, another app holding it - is not a
     * failure: the call connects muted and the ordinary path tries again once there
     * is a room, by which time the person may have granted it.
     */
    private fun openMic(r: Room) {
        if (warmMic != null) return
        runCatching {
            val track = r.localParticipant.createAudioTrack()
            track.prewarm()
            track.start()
            warmMic = track
        }.onFailure {
            android.util.Log.w("CallEngine", "Could not open the microphone early", it)
        }
    }

    /** The camera that was opened before the room was ready, waiting to be published. */
    private var warmCamera: io.livekit.android.room.track.LocalVideoTrack? = null

    /**
     * Opens the lens now and publishes it later.
     *
     * Made with the filter already in it, for the reason written over
     * [enableCamera]: a filter cannot safely be added to a camera track that
     * already exists.
     *
     * [localVideo] is set here, so the self-view has something to draw from the
     * first frame rather than from the first publication. Refused - no camera
     * permission yet, another app holding the lens - is not a failure: the call
     * goes on without a picture and the ordinary path tries again once there is a
     * room, by which time the person may well have granted it.
     */
    private fun openCamera(r: Room) {
        if (warmCamera != null) return
        runCatching {
            val track = r.localParticipant.createVideoTrack(videoProcessor = cameraFilter)
            track.start()
            track.startCapture()
            warmCamera = track
            localVideo = track
            cameraOn = true
        }.onFailure {
            android.util.Log.w("CallEngine", "Could not open the camera early", it)
        }
    }

    private suspend fun enableCamera(r: Room): Boolean {
        val me = r.localParticipant
        if (me.getTrackPublication(io.livekit.android.room.track.Track.Source.CAMERA) == null) {
            runCatching {
                val track = me.createVideoTrack(videoProcessor = cameraFilter)
                track.start()
                track.startCapture()
                me.publishVideoTrack(track)
            }.onFailure {
                android.util.Log.w("CallEngine", "Could not publish the filtered camera", it)
            }
        }
        return runCatching { me.setCameraEnabled(true) }.getOrDefault(false)
    }

    private fun refresh(r: Room) {
        // Falls back to the warm track, which is the same camera before it has been
        // published. Without that, any room event arriving in the gap between
        // opening the camera and publishing it would blank the self-view.
        localVideo = r.localParticipant.videoTrackPublications
            .firstOrNull { it.second is VideoTrack }
            ?.second as? VideoTrack
            ?: warmCamera

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

    /**
     * How long an empty room is given to fill again before the call is called over.
     *
     * Long enough for a rejoin after a connection the server dropped, short enough
     * that nobody sits watching a call that has already ended.
     */
    private const val EMPTY_ROOM_MS = 6_000L
}
