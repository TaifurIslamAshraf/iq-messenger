package com.iqsocial.iq_message.data.call

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.iqsocial.iq_message.data.auth.SessionStore
import com.iqsocial.iq_message.data.remote.ApiResult
import com.iqsocial.iq_message.data.remote.CallSession
import com.iqsocial.iq_message.data.remote.CallStatus
import com.iqsocial.iq_message.data.remote.CallType
import com.iqsocial.iq_message.data.remote.CallsApi
import com.iqsocial.iq_message.data.remote.ChatCall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The one call this phone is in, and everything that can change it.
 *
 * A single object rather than screen state, because a call is not a screen. It
 * survives the one that started it, is answered from a notification as readily as
 * from the app, and has to be ended from whichever of those the person reaches for.
 * It also arrives unbidden: a push can wake the app into a ringing call with no
 * screen open at all.
 *
 * Everything that happens to a call arrives **twice** - once on the socket and once
 * as a push - so every entry point here is written to be repeated without effect.
 */
object CallCenter {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** The live call, or null. Screens are drawn from this. */
    var call by mutableStateOf<ChatCall?>(null)
        private set

    /** Set while a start or an accept is in flight, so buttons can say so. */
    var busy by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    private var ringTimer: Job? = null

    /* -- starting ----------------------------------------------------------- */

    /**
     * Places a call and connects immediately.
     *
     * The room is joined before the other person answers, on purpose: the connection
     * is then already up when they do, instead of a silent few seconds while it is
     * negotiated.
     */
    fun start(context: Context, peerId: String, type: CallType) {
        if (call?.isLive == true || busy) return
        val token = SessionStore(context).token ?: return
        busy = true
        error = null
        scope.launch {
            when (val r = CallsApi.start(token, peerId, type)) {
                is ApiResult.Success -> adopt(context, r.data, type)
                is ApiResult.Failure -> {
                    error = r.message
                    busy = false
                }
            }
        }
    }

    /* -- being called -------------------------------------------------------- */

    /**
     * A call is ringing for us, from the socket or a push.
     *
     * Ignored when it is the call we are already showing, which is what makes the
     * two deliveries harmless. A second, different call while one is live is also
     * ignored: the server has already told the caller we are busy.
     */
    /**
     * @param context optional because the socket has none to give. The one kept at
     *   start-up stands in, so a call arriving over the socket rings exactly as one
     *   arriving by push does.
     */
    fun ringing(incoming: ChatCall, context: Context? = null) {
        if (incoming.id == call?.id || incoming.id == waiting?.id) return

        // Already on one, so this one waits rather than being thrown away.
        //
        // It used to be declined on the spot, which at least told the caller
        // something - but it decided for the person holding the phone, who may well
        // want to take it. Held here instead, shown over the call in progress, and
        // declined only if they say so or it rings out by itself.
        if (call?.isLive == true) {
            waiting = incoming
            armWaitingTimeout(incoming)
            return
        }
        call = incoming

        // The notification still goes up either way - it is how the call is reached
        // from the lock screen and the shade, and on Android 14 it is what takes
        // over a locked screen. What changes is which of the two makes the sound.
        //
        // Both would be two ringtones over each other, started a moment apart.
        (context ?: ringingContext)?.let { host ->
            RingingNotification.show(host, incoming, silent = CallWindow.appInFront)
            if (CallWindow.appInFront) Ringtone.start(host)
        }
        armRingTimeout(incoming)
    }

    /**
     * Answers it.
     *
     * [CallsApi.accept] is safe to repeat and so is this, which matters: the same
     * tap can arrive from the ringing screen and from a notification action.
     */
    fun accept(context: Context) {
        val current = call ?: return
        // First, before anything that can fail or take a moment: a ringtone still
        // going while the call connects is the loudest kind of bug.
        Ringtone.stop()
        if (busy) return
        val token = SessionStore(context).token ?: return
        busy = true
        scope.launch {
            when (val r = CallsApi.accept(token, current.id, current)) {
                is ApiResult.Success -> adopt(context, r.data, current.type)
                is ApiResult.Failure -> {
                    error = r.message
                    busy = false
                }
            }
        }
    }

    fun decline(context: Context) {
        val current = call ?: return
        Ringtone.stop()
        val token = SessionStore(context).token ?: return
        finish(context)
        scope.launch { CallsApi.decline(token, current.id) }
    }

    /* -- ending -------------------------------------------------------------- */

    /**
     * Hangs up, or cancels while it is still ringing.
     *
     * The call is let go **here** rather than when the server answers. Hanging up is
     * the one thing that must never appear to fail: a screen that stays on a dead
     * call because the network did not reply is worse than a server that catches up
     * a moment later - and `end` is safe to repeat, so it will.
     */
    fun end(context: Context) {
        val current = call ?: return
        val token = SessionStore(context).token
        finish(context)
        if (token != null) scope.launch { CallsApi.end(token, current.id) }
    }

    /**
     * The server says the call changed - answered, somebody left, or it is over.
     *
     * Only the changed half arrives, so it is merged over what we hold rather than
     * replacing it; otherwise answering a call would lose who was on it.
     */
    /**
     * Adds people to the call already in progress.
     *
     * The API for this was written and never called, and the button for it was on
     * screen with an empty handler - so "Add Call" did nothing at all.
     *
     * The reply is the call as it now stands, which is what makes the new people
     * appear: the participant list comes back with them on it, rather than this side
     * guessing who joined.
     */
    fun invite(context: Context, uids: List<String>) {
        val live = call ?: return
        if (uids.isEmpty()) return
        val token = SessionStore(context).token ?: return
        scope.launch {
            when (val r = CallsApi.invite(token, live.id, uids)) {
                is ApiResult.Success -> updated(r.data)
                is ApiResult.Failure -> error = r.message
            }
        }
    }

    /**
     * Asks the server what the call is doing now.
     *
     * For when a push went missing - which the push spec says will happen - and this
     * side is holding a call that may be long over, or missing one that is running.
     */
    fun resync(context: Context) {
        val live = call ?: return
        val token = SessionStore(context).token ?: return
        scope.launch {
            val r = CallsApi.one(token, live.id)
            if (r is ApiResult.Success) updated(r.data)
        }
    }

    /**
     * A second call, ringing while the first is still up.
     *
     * Separate from [call] on purpose: the call in progress must not be disturbed by
     * one that may never be answered, and the two have to be told apart to end the
     * right one.
     */
    var waiting by mutableStateOf<ChatCall?>(null)
        private set

    private var waitingTimer: Job? = null

    /** Takes the waiting call and hangs up the one in progress. */
    fun endAndAccept(context: Context) {
        val next = waiting ?: return
        clearWaiting()
        end(context)
        // The call object is already in hand from the push, so this goes straight to
        // accepting rather than asking for it again.
        call = next
        accept(context)
    }

    /** Turns the waiting call away, leaving the one in progress alone. */
    fun declineWaiting(context: Context) {
        val next = waiting ?: return
        clearWaiting()
        val token = SessionStore(context).token ?: return
        scope.launch { CallsApi.decline(token, next.id) }
    }

    private fun clearWaiting() {
        waitingTimer?.cancel()
        waitingTimer = null
        waiting = null
    }

    /** A waiting call stops waiting when its own ringing time is up. */
    private fun armWaitingTimeout(target: ChatCall) {
        waitingTimer?.cancel()
        val until = target.ringExpiresAt
        if (until <= 0L) return
        waitingTimer = scope.launch {
            val wait = until - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            if (waiting?.id == target.id) clearWaiting()
        }
    }

    fun updated(state: ChatCall) {
        // The second call can end before it is answered - cancelled, or picked up on
        // another device - and nothing else is watching it.
        waiting?.let { held ->
            if (state.id == held.id && state.status == CallStatus.ENDED) clearWaiting()
        }
        val current = call ?: return
        if (state.id != current.id) return
        val merged = current.merge(state)
        call = merged
        // Answered, so the waiting tone has done its job. Said here because this is
        // where the status actually changes - stopping it only in `finish` would
        // leave it ringing underneath a connected call.
        if (merged.status != CallStatus.RINGING) Ringback.stop()
        if (merged.status == CallStatus.ENDED) finish()
    }

    /* -- the machinery ------------------------------------------------------- */

    private fun adopt(context: Context, session: CallSession, type: CallType) {
        call = session.call
        busy = false
        RingingNotification.hide(context)
        ringTimer?.cancel()
        CallEngine.connect(context, session.connection, video = type == CallType.VIDEO)
        // A voice call goes to the ear, so the screen gets out of the way.
        CallScreenLock.begin(context, video = type == CallType.VIDEO)
        CallService.start(context)
        // An outgoing call still rings; a ring that nobody answers has to stop.
        if (session.call.status == CallStatus.RINGING) {
            armRingTimeout(session.call)
            // Only for the end that placed it. The other phone has a ringtone of
            // its own, and two at once is one too many.
            if (!session.call.isIncoming) Ringback.start()
        }
    }

    /**
     * Stops ringing when the server said it would, whatever else arrives.
     *
     * The push spec is explicit that a `call:ended` can be late or lost, and that a
     * ringing push FCM cannot deliver within forty-five seconds is dropped - so a
     * phone that was offline must not start ringing for a call that is long over.
     * `ring_expires_at` is the only thing that can be trusted here.
     */
    private fun armRingTimeout(target: ChatCall) {
        ringTimer?.cancel()
        val until = target.ringExpiresAt
        if (until <= 0L) return
        ringTimer = scope.launch {
            val wait = until - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            if (call?.id == target.id && call?.status == CallStatus.RINGING) finish()
        }
    }

    private fun finish(context: Context? = null) {
        Ringback.stop()
        Ringtone.stop()
        clearWaiting()
        ringTimer?.cancel()
        ringTimer = null
        call = null
        busy = false
        CallEngine.disconnect()
        // Before anything else can go wrong: a proximity lock left held is a phone
        // whose screen never comes back on.
        CallScreenLock.release()
        CallService.stop()
        context?.let { RingingNotification.hide(it) } ?: ringingContext?.let {
            RingingNotification.hide(it)
        }
    }

    /**
     * Kept so the ringing can be taken down from places that have no context.
     *
     * `call:ended` arrives on the socket, which has none, and a notification left
     * ringing for a call the caller has already given up on is the worst of the
     * failures here.
     */
    @Volatile
    private var ringingContext: Context? = null

    fun init(context: Context) {
        ringingContext = context.applicationContext
        CallEngine.onConnectionLost = { connectionLost() }
    }

    /**
     * The room collapsed under a live call.
     *
     * Ended rather than waited on: the SDK has already spent its own reconnection
     * attempts by the time it says this, so there is nothing left to wait for. What
     * there is to do is the half that was being skipped - **tell the server**, so
     * the call stops on the other phone too, and **say so**, so this is not a call
     * that silently stopped working.
     *
     * Observed after answering on the lock screen and then unlocking, where the room
     * goes down with the call still up at the far end. Why it goes down is not
     * decided here; this is about not losing the call quietly when it does.
     */
    private fun connectionLost() {
        if (call == null) return
        error = "The connection was lost."
        // Through `end`, which is what reports to the server. Only `finish` without
        // a context, where the report cannot be made - the local side still has to
        // come down either way.
        ringingContext?.let { end(it) } ?: finish()
    }

    /** Signing out ends whatever was going on. */
    fun clear() {
        finish()
        error = null
    }

    fun dismissError() {
        error = null
    }
}
