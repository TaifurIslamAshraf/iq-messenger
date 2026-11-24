package com.iqsocial.iq_message.data.media

import android.content.Context
import android.net.Uri
import com.iqsocial.iq_message.R

/**
 * The app's own two sounds: the ring, and the one a notification makes.
 *
 * Shipped in `res/raw` rather than taken from the phone's settings. For the
 * notification that is the ordinary thing to do - a messaging app is recognised by
 * its sound, and a reader hearing it knows which app spoke before looking. For the
 * ringtone it is a real choice, and it is made the way the rest of the call screen
 * is made: this is an app people are meant to recognise when it rings, not a dialler
 * standing in for the system one. Whoever prefers their own can still set it per
 * channel in Android's own notification settings, which is where that choice lives.
 *
 * Both are handed out as `android.resource://` URIs, because that is the one form
 * both a `NotificationChannel` and a `MediaPlayer` take.
 */
object Tones {

    /** The ring heard while somebody is calling. Loops; about twenty seconds long. */
    fun ringtone(context: Context): Uri = raw(context, R.raw.iq_ringtone)

    /** The short sound a message, a missed call or an alert arrives with. */
    fun notification(context: Context): Uri = raw(context, R.raw.iq_notify)

    /**
     * The ringback: what the caller hears once the other phone is ringing.
     *
     * Not the same file as [ringtone] and not meant to be. One is heard by the
     * person being called and has to carry across a room; this one is held against
     * an ear while waiting, and is written as a loop so it can simply repeat.
     */
    fun ringback(context: Context): Uri = raw(context, R.raw.iq_ringback)

    /**
     * The beep before that, while the call is on its way and nothing is ringing yet.
     *
     * A different sound rather than silence or the ringback, because the difference
     * is the only thing telling the caller whether the call has reached the other
     * end. See [com.iqsocial.iq_message.data.call.Ringback].
     */
    fun connecting(context: Context): Uri = raw(context, R.raw.iq_connecting)

    /**
     * `android.resource://<package>/raw/<name>`, by name and not by number.
     *
     * The numeric form is the one everybody writes, and it is a trap for anything
     * stored: a notification channel keeps the URI it was created with for the life
     * of the install, while resource ids are assigned at build time and move
     * whenever resources are added or removed. A channel left holding last build's
     * number is a channel that plays nothing at all, silently.
     *
     * The name is written into the apk and does not move, so the id is looked up
     * here only to ask what it is called - which keeps the compiler checking that
     * the file exists.
     */
    private fun raw(context: Context, id: Int): Uri {
        val res = context.resources
        return Uri.parse(
            "android.resource://" + context.packageName +
                "/" + res.getResourceTypeName(id) +
                "/" + res.getResourceEntryName(id)
        )
    }
}
