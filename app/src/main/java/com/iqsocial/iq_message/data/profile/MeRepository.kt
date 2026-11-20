package com.iqsocial.iq_message.data.profile

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.iqsocial.iq_message.data.media.ProfilePhoto
import com.iqsocial.iq_message.data.remote.ApiResult
import com.iqsocial.iq_message.data.remote.MediaApi
import com.iqsocial.iq_message.data.remote.MessengerApi
import com.iqsocial.iq_message.data.remote.MessengerProfile
import com.iqsocial.iq_message.data.remote.NameCooldown
import com.iqsocial.iq_message.data.remote.ProfileApi
import com.iqsocial.iq_message.data.remote.ProfileFields
import com.iqsocial.iq_message.data.remote.ProfileImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream

/**
 * The signed-in user's messenger profile: what it says, and changing it.
 *
 * `/v1/me` is the only place this data exists — the account API has no `/me`, and
 * login returns just name, phone and picture. Bio, verification, badge, chat
 * status and privacy settings all come from here.
 *
 * Every write goes through this object rather than through [ProfileApi] directly,
 * for one reason: each endpoint answers with the profile as it now stands, and that
 * answer is worth more than the request that caused it. Saving a bio and then
 * believing the bio was saved is a guess; taking what the server sends back is not,
 * and it is also how a name the server trimmed, or a picture it re-hosted at a URL
 * of its own, reaches the screen.
 */
object MeRepository {

    var profile by mutableStateOf<MessengerProfile?>(null)
        private set

    /** True once a fetch has finished, successfully or not. */
    var loaded by mutableStateOf(false)
        private set

    /** Why the last fetch or write failed, for screens that want to say so. */
    var error by mutableStateOf<String?>(null)
        private set

    /** True while a write is in flight, so the control that started it can wait. */
    var saving by mutableStateOf(false)
        private set

    /**
     * How far a picture has got, 0..1, or null when none is going up.
     *
     * Its own state rather than part of [saving]: a bio saves in a moment and a
     * cover photo does not, and the difference between "working" and "two thirds
     * of the way through" is the difference between a spinner people trust and one
     * they press again.
     */
    var imageProgress by mutableStateOf<Float?>(null)
        private set

    suspend fun load(token: String?) {
        if (token.isNullOrBlank()) {
            loaded = true
            return
        }
        when (val result = MessengerApi.me(token)) {
            is ApiResult.Success -> {
                profile = result.data
                error = null
            }
            is ApiResult.Failure -> error = result.message
        }
        loaded = true
    }

    /**
     * Bio, gender, birthday, country or location - whichever were given.
     *
     * Nulls are not "clear this", they are "leave this alone": the endpoint takes
     * only what changes, and a field clears by being sent as an empty string. Both
     * meanings are needed and `null` can only carry one of them, so clearing is
     * `""` here as it is on the wire.
     */
    suspend fun updateProfile(
        token: String?,
        bio: String? = null,
        gender: String? = null,
        birthday: Long? = null,
        country: String? = null,
        location: String? = null
    ): Boolean = write(token) {
        ProfileApi.updateProfile(it, bio, gender, birthday, country, location)
    }

    /**
     * Changes the name, or says how long until it can be.
     *
     * Three outcomes, and the middle one is why this does not simply return a
     * boolean: a refusal inside the sixty days is not a failure to be reported as
     * one, it is an answer with a date in it.
     */
    sealed interface Renamed {
        data object Done : Renamed
        data class TooSoon(val cooldown: NameCooldown) : Renamed
        data class Failed(val message: String) : Renamed
    }

    suspend fun rename(token: String?, fullname: String): Renamed {
        val key = token?.takeIf { it.isNotBlank() } ?: return Renamed.Failed("Not signed in")
        saving = true
        error = null
        return try {
            when (val result = ProfileApi.rename(key, fullname)) {
                is ApiResult.Success -> {
                    apply(result.data)
                    Renamed.Done
                }

                is ApiResult.Failure -> {
                    val waiting = NameCooldown.of(result)
                    if (waiting != null) {
                        // Not an error on the screen: the name simply cannot change
                        // yet, and the screen says so in its own words.
                        Renamed.TooSoon(waiting)
                    } else {
                        error = result.message
                        Renamed.Failed(result.message)
                    }
                }
            }
        } finally {
            saving = false
        }
    }

    /** Who may message, who may call. */
    suspend fun updatePrivacy(
        token: String?,
        whoCanMessage: String? = null,
        whoCanCall: String? = null
    ): Boolean = write(token) {
        ProfileApi.updatePrivacy(it, whoCanMessage, whoCanCall)
    }

    /**
     * Makes [uri] the account's picture or cover.
     *
     * Three steps and they are not interchangeable: the bytes are prepared first,
     * because the link that follows is signed for exactly their number; then they
     * are PUT with exactly the headers that came back; then the server is told to
     * publish them. Nothing on the profile changes until the last of the three.
     */
    /**
     * The same, started from a screen that may not be there when it finishes.
     *
     * A picture takes seconds to prepare and seconds more to upload, and the
     * profile screen is a tab: start it in the screen's own scope and switching to
     * Chats halfway through cancels it, leaving a link spent, nothing uploaded and
     * no sign that anything happened. This scope outlives the screen, and the
     * progress the screen draws is read from here anyway.
     */
    fun uploadImage(context: Context, token: String?, kind: ProfileImage, uri: Uri) {
        work.launch { setImage(token, kind) { ProfilePhoto.prepare(context, uri, kind) } }
    }

    /**
     * The same, for a picture the reader has just cropped.
     *
     * The crop screen hands over a bitmap rather than a file: what was chosen is
     * part of the original and exists nowhere on disk, and writing it out to a
     * temporary file only to read it back would be a round trip for nothing.
     */
    fun uploadImage(token: String?, kind: ProfileImage, bitmap: Bitmap) {
        work.launch { setImage(token, kind) { ProfilePhoto.prepare(bitmap, kind) } }
    }

    /** Lives as long as the process, for work a screen must not be able to cancel. */
    private val work = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private suspend fun setImage(
        token: String?,
        kind: ProfileImage,
        prepare: suspend () -> ProfilePhoto.Ready?
    ): Boolean {
        val key = token?.takeIf { it.isNotBlank() } ?: return false
        saving = true
        error = null
        imageProgress = 0f
        try {
            val ready = prepare()
            if (ready == null) {
                error = "That picture could not be used. Try another one."
                return false
            }

            // Said at every step, because this is a three-request errand that runs
            // after the screen that started it may be gone: when it fails there is
            // nothing on screen to read, and "the picture did not change" is the
            // same symptom for a refused link, a refused upload and a refused
            // publish. The log is the only place that can tell them apart.
            Log.i(TAG, "${kind.wire}: asking for a link for ${ready.size} bytes")

            val slot = when (
                val asked = ProfileApi.imageSlot(key, kind, ready.mimeType, ready.size)
            ) {
                is ApiResult.Success -> asked.data
                is ApiResult.Failure -> {
                    Log.w(TAG, "${kind.wire}: no upload link - ${asked.message}")
                    error = asked.message
                    return false
                }
            }
            // Without its query string, which carries the signature.
            Log.i(TAG, "${kind.wire}: uploading to ${slot.upload.url.substringBefore('?')}")

            val sent = MediaApi.putBytes(
                target = slot.upload,
                size = ready.size,
                onProgress = { done -> imageProgress = done.toFloat() / ready.size }
            ) { ByteArrayInputStream(ready.bytes) }
            if (sent is ApiResult.Failure) {
                Log.w(TAG, "${kind.wire}: upload refused - ${sent.message}")
                error = sent.message
                return false
            }
            Log.i(TAG, "${kind.wire}: bytes are up, publishing")

            // Storage has the bytes; the server has not looked yet. A 409 means it
            // looked too early, which is worth one more try rather than a failure -
            // the upload is done and the only thing missing is the server noticing.
            repeat(COMPLETE_TRIES) { attempt ->
                when (val done = ProfileApi.completeImage(key, kind, slot.key)) {
                    is ApiResult.Success -> {
                        Log.i(TAG, "${kind.wire}: published")
                        apply(done.data)
                        return true
                    }

                    is ApiResult.Failure -> {
                        Log.w(TAG, "${kind.wire}: publish refused - ${done.message}")
                        if (attempt == COMPLETE_TRIES - 1) {
                            error = done.message
                            return false
                        }
                        delay(COMPLETE_RETRY_MS)
                    }
                }
            }
            return false
        } finally {
            imageProgress = null
            saving = false
        }
    }

    /** Takes the picture or the cover away. */
    suspend fun removeImage(token: String?, kind: ProfileImage): Boolean =
        write(token) { ProfileApi.removeImage(it, kind) }

    fun clear() {
        profile = null
        loaded = false
        error = null
        saving = false
        imageProgress = null
    }

    /** Clears the last complaint, so a sheet does not open wearing an old one. */
    fun forgetError() {
        error = null
    }

    /**
     * The shape every write but the name and the pictures has.
     *
     * Not optimistic, for the reason in the class comment: what the server answers
     * with is the profile, and this side's assumption is not.
     */
    private suspend inline fun write(
        token: String?,
        call: (String) -> ApiResult<ProfileFields>
    ): Boolean {
        val key = token?.takeIf { it.isNotBlank() } ?: return false
        saving = true
        error = null
        return try {
            when (val result = call(key)) {
                is ApiResult.Success -> {
                    apply(result.data)
                    true
                }

                is ApiResult.Failure -> {
                    error = result.message
                    false
                }
            }
        } finally {
            saving = false
        }
    }

    /**
     * Folds a reply into what is on screen.
     *
     * A write answers with the `Profile` half only, so this keeps the account's own
     * facts - uid, phone number, follower counts - and takes everything the reply
     * actually speaks for.
     */
    private fun apply(fields: ProfileFields) {
        profile = profile?.with(fields)
    }

    private const val TAG = "MeRepository"
    private const val COMPLETE_TRIES = 2
    private const val COMPLETE_RETRY_MS = 900L
}
