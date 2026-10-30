package com.iqsocial.iq_message.data.call

import com.iqsocial.iq_message.data.CallDirection
import com.iqsocial.iq_message.data.CallKind
import com.iqsocial.iq_message.data.CallRecord
import com.iqsocial.iq_message.data.Person
import com.iqsocial.iq_message.data.remote.CallType
import com.iqsocial.iq_message.data.remote.ChatCall
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * A finished call, as a row in the calls tab.
 *
 * Everything here comes off the call the server already sends; nothing is inferred
 * that it does not say.
 */
fun ChatCall.toRecord(): CallRecord = CallRecord(
    id = id,
    person = Person(
        id = peer?.uid.orEmpty(),
        name = peer?.fullname?.takeIf { it.isNotBlank() } ?: "Unknown",
        handle = "",
        photo = peer?.profilePicture.orEmpty()
    ),
    kind = if (type == CallType.VIDEO) CallKind.VIDEO else CallKind.AUDIO,
    direction = when {
        // The server's own word for it, rather than this side working it out from
        // timestamps and getting a different answer.
        missed -> CallDirection.MISSED
        isIncoming -> CallDirection.INCOMING
        else -> CallDirection.OUTGOING
    },
    time = callStamp(if (createdAt > 0) createdAt else answeredAt),
    durationSeconds = durationSeconds()
)

/**
 * How long the call ran.
 *
 * From **answered** to ended, not from started to ended: the time it spent ringing
 * is not time anybody was on a call, and counting it would make every missed call
 * look like a thirty-second conversation.
 *
 * Zero when it was never answered, and zero while it is still going - a call with no
 * end yet has no length to report, and subtracting from an unset `ended_at` would
 * give the time since 1970.
 */
private fun ChatCall.durationSeconds(): Int {
    if (answeredAt <= 0L || endedAt <= answeredAt) return 0
    return ((endedAt - answeredAt) / 1000).toInt()
}

/** `2:05`, or `1:02:05` once it runs past an hour. */
fun formatCallLength(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, secs)
    else "%d:%02d".format(minutes, secs)
}

/**
 * When it was, in the shape a list of calls wants.
 *
 * The clock alone for today, because the day is understood; the weekday within the
 * last week, which reads faster than a date; the date beyond that. A full timestamp
 * on every row would be the same eight characters repeated down the screen with the
 * useful part buried in them.
 */
private fun callStamp(millis: Long): String {
    if (millis <= 0L) return ""
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = millis }
    val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
    val sameDay = sameYear && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    val daysApart = if (sameYear) {
        now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR)
    } else {
        Int.MAX_VALUE
    }

    val pattern = when {
        sameDay -> "h:mm a"
        daysApart == 1 -> "'Yesterday'"
        daysApart in 2..6 -> "EEEE"
        sameYear -> "d MMM"
        else -> "d MMM yyyy"
    }
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis))
}
