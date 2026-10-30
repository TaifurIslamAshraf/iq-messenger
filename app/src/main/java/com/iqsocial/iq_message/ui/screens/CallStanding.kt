package com.iqsocial.iq_message.ui.screens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.iqsocial.iq_message.data.call.CallAudio
import com.iqsocial.iq_message.ui.icons.IqIcons
import com.iqsocial.iq_message.data.remote.CallEnd
import com.iqsocial.iq_message.data.remote.CallStatus
import com.iqsocial.iq_message.data.remote.ChatCall

/**
 * What to say about a call, and whether it is actually up.
 *
 * [connected] is the dot: green only when there is a room with somebody in it, not
 * merely a call that exists. The two were the same thing in the UI before - the
 * caller's screen said "Connected" beside a green dot from the moment it opened, so
 * a phone that was still ringing, one that had been declined and one that was
 * carrying a conversation all read identically.
 */
@Immutable
data class CallStanding(val label: String, val connected: Boolean)

/**
 * What the current audio route looks like, and what it is called.
 *
 * Here rather than in each screen because three of them ask - the voice screen, the
 * video screen and the floating window's button - and an icon that disagrees with
 * where the sound is actually going is worse than no icon at all.
 *
 * The headset splits in two. The route is one decision covering wired, USB and
 * Bluetooth, but a Bluetooth rune over a cabled headset names a device that is not
 * attached, so the picture follows what is really there.
 */
val CallAudio.Route.icon: ImageVector
    get() = when (this) {
        CallAudio.Route.EARPIECE -> IqIcons.Phone
        CallAudio.Route.SPEAKER -> IqIcons.Speaker
        CallAudio.Route.BLUETOOTH ->
            if (CallAudio.headsetIsWireless) IqIcons.Bluetooth else IqIcons.Headset
    }

val CallAudio.Route.label: String
    get() = when (this) {
        CallAudio.Route.EARPIECE -> "Phone"
        CallAudio.Route.SPEAKER -> "Speaker"
        CallAudio.Route.BLUETOOTH ->
            if (CallAudio.headsetIsWireless) "Bluetooth" else "Headset"
    }

/**
 * Reads the call's standing out of what the server says.
 *
 * Every field this uses was already being parsed and none of it was being shown.
 *
 * **On "is it ringing on their phone":** `ringing` is as close as this API gets, and
 * it is worth being exact about what it means. It says the server has the call in
 * its ringing phase and has sent for the other devices - not that a particular
 * handset has received it, drawn anything, or made a sound. There is no
 * per-device acknowledgement in the spec to show, and claiming one would be the
 * screen inventing a fact.
 *
 * What *is* definite arrives at the end, in `end_reason`: `busy` means they were on
 * another call, `declined` means somebody pressed decline, and `missed` means it
 * rang its full time with no answer. Those three are worth telling apart, because
 * they are the difference between "try again now" and "do not".
 *
 * [joined] is this device's own view of the room, which the call object cannot
 * carry: the server can call a direct call `ongoing` from the moment it is answered,
 * while this end is still negotiating. Saying "Connected" then is a promise the
 * audio has not kept yet.
 */
fun standingOf(
    call: ChatCall?,
    joined: Boolean,
    reconnecting: Boolean = false,
    poor: Boolean = false
): CallStanding = when {
    call == null -> CallStanding("Call ended", connected = false)

    call.status == CallStatus.ENDED -> CallStanding(
        when (call.endReason) {
            CallEnd.DECLINED -> "Declined"
            CallEnd.BUSY -> "On another call"
            CallEnd.MISSED -> "No answer"
            CallEnd.CANCELLED -> "Cancelled"
            CallEnd.HANGUP, null -> "Call ended"
        },
        connected = false
    )

    // Still ringing. Which end you are on changes what that means, so it changes
    // what it says.
    //
    // "Calling" for the end that placed it, not "Ringing" - and that is accuracy
    // rather than wording. `ringing` is the server's own phase: it has the call and
    // has sent for the other devices. Whether a particular handset received it, drew
    // anything or made a sound is not in the API, so "Ringing" would be this screen
    // claiming something it has not been told.
    call.status == CallStatus.RINGING -> CallStanding(
        if (call.isIncoming) "Incoming call" else "Calling",
        connected = false
    )

    // Answered, but this device is not in the room yet.
    !joined -> CallStanding("Connecting", connected = false)

    // The connection dropped and is being rebuilt. Said, because the alternative is
    // a screen that reads "Connected" over silence - and the person hangs up and
    // calls back, which is the one thing that makes it worse.
    reconnecting -> CallStanding("Reconnecting", connected = false)

    // Up, but barely. Not acted on: whether a bad line is worth staying on is the
    // person's call, and an app that dropped it for them would be ending calls that
    // were still being used.
    poor -> CallStanding("Weak connection", connected = true)

    else -> CallStanding("Connected", connected = true)
}
