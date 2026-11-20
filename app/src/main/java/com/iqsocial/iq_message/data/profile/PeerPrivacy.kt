package com.iqsocial.iq_message.data.profile

import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import com.iqsocial.iq_message.data.remote.ApiResult
import com.iqsocial.iq_message.data.remote.SettingsApi

/**
 * What other people have asked of this phone: today, whether it may screenshot them.
 *
 * The setting belongs to the person being looked at and the button is pressed on the
 * phone doing the looking, so this is the one privacy control an app enforces on
 * somebody else's behalf. Until now there was no way to know what they had asked
 * for - `Person` does not carry the field and never did, so `peer.allowScreenshot`
 * was the model's default of `true` for everybody and the lock could never close.
 * `GET /v1/settings/users/{uid}` is the answer to exactly that question, and it
 * shares that one setting and nothing else of theirs.
 *
 * **Unknown counts as blocked.** The window is locked from the moment a chat opens
 * until the server says it need not be - the other way round, the thread is drawn
 * and screenshotable for as long as the request takes, which is the whole of the
 * window somebody waiting for it would use. A failed request is different from a
 * slow one: with no answer coming, the lock is lifted. An offline phone would
 * otherwise be a phone that can never screenshot anything, and somebody determined
 * enough to pull the network to beat this can hold up a second camera anyway.
 *
 * **Asked afresh every time a screen opens, and never read from a cache.** It is
 * somebody else's setting and they can change it at any moment - turning it off is
 * exactly the kind of thing somebody does *because* of what is about to be said -
 * so an answer kept even for a minute is a minute in which this phone can be
 * wrong. It used to be kept for exactly that long, and the effect was a setting
 * that did not take until the app had been closed and opened again.
 *
 * What is kept is only what has been answered, for the screens drawing right now:
 * nothing goes to disk, and a request already on its way is not sent twice.
 */
object PeerPrivacy {

    private const val TAG = "PeerPrivacy"

    /** What each person allows. Absent means nobody has asked yet. */
    private val answers = mutableStateMapOf<String, Boolean>()

    /** Who is being asked about right now, so a screen does not ask twice. */
    private val inFlight = mutableSetOf<String>()

    /**
     * Whether this phone may be screenshotted while it is showing [uid].
     *
     * Read from Compose: the map is a snapshot state, so a screen that asks this
     * draws again by itself when the answer lands.
     *
     * No uid at all - a conversation whose peer has not loaded yet, a group - is
     * allowed. There is nobody to have asked otherwise.
     */
    fun allows(uid: String?): Boolean {
        if (uid.isNullOrBlank()) return true
        return answers[uid] ?: false
    }

    /**
     * Asks, unless the answer is recent or already being fetched.
     *
     * Called from the main thread (a `LaunchedEffect`), which is what makes the
     * three collections safe without a lock: every read and write of them happens
     * there, and only the HTTP call itself is suspended.
     */
    suspend fun refresh(token: String?, uid: String?) {
        val who = uid?.takeIf { it.isNotBlank() } ?: run {
            Log.i(TAG, "asked about nobody - the screen has no peer yet")
            return
        }
        Log.i(TAG, "asking whether $who allows screenshots")
        if (token.isNullOrBlank()) {
            // No token, no question - and no reason to hold a chat locked for an
            // answer that is never going to be asked for.
            answers[who] = true
            return
        }
        if (!inFlight.add(who)) return
        // The old answer goes before the new one is asked for, so the window is
        // locked while the question is in the air. The other way round - keep the
        // last answer until a new one arrives - is a screen that stays open to
        // screenshots for as long as the request takes, in the one case where the
        // person has just this moment asked that it not be.
        answers.remove(who)
        try {
            when (val result = SettingsApi.allowsScreenshot(token, who)) {
                is ApiResult.Success -> {
                    // Said out loud, because this one decision is invisible from
                    // the outside: a window that is not locked and a window that is
                    // locked for somebody who allows screenshots look the same
                    // until somebody presses the buttons.
                    Log.i(TAG, "$who allows screenshots: ${result.data}")
                    answers[who] = result.data
                }

                is ApiResult.Failure -> {
                    // 404 is one of these, and the spec is explicit about what it
                    // means: banned, unknown, or someone who blocked you - the same
                    // answer people search gives. None of those is "they asked not
                    // to be screenshotted", so none of them should leave the window
                    // locked.
                    Log.w(TAG, "No screenshot setting for $who: ${result.message}")
                    if (!answers.containsKey(who)) answers[who] = true
                }
            }
        } finally {
            inFlight.remove(who)
        }
    }

    /** Signing out: the next account's chats must not inherit these answers. */
    fun forget() {
        answers.clear()
        inFlight.clear()
    }
}
