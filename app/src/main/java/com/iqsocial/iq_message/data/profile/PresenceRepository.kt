package com.iqsocial.iq_message.data.profile

import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import com.iqsocial.iq_message.data.remote.ApiResult
import com.iqsocial.iq_message.data.remote.Presence
import com.iqsocial.iq_message.data.remote.PresenceApi
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Who is online, as far as this app has been told.
 *
 * In memory only and deliberately so. Presence is the one thing in the app that is
 * worthless the moment it is stale - a "last seen" from the last session is not old
 * information, it is wrong information - so nothing here survives the process, and a
 * screen that wants to know asks again when it opens.
 *
 * A snapshot map, so a screen reading [of] recomposes when the answer arrives
 * without having to hold the result itself.
 */
object PresenceRepository {

    private val known: SnapshotStateMap<String, Presence> = mutableStateMapOf()

    /** What is known about one account, or null when nothing has been said. */
    fun of(uid: String): Presence? = if (uid.isBlank()) null else known[uid]

    /**
     * Asks the server about these accounts and keeps what it says.
     *
     * Quiet about failure: there is no error state here and no screen shows one.
     * Presence that cannot be fetched leaves the dot grey and the line absent, which
     * is the same as what an account that hides it looks like - and that is the
     * right answer, because the two really are indistinguishable from here.
     */
    suspend fun refresh(token: String?, uids: List<String>) {
        if (token.isNullOrBlank()) return
        when (val result = PresenceApi.get(token, uids)) {
            is ApiResult.Success -> result.data.forEach { known[it.uid] = it }
            is ApiResult.Failure -> Unit
        }
    }

    /**
     * Keeps what the socket just said.
     *
     * Separate from [refresh] because it is the other half of the same job: the
     * fetch answers "where do things stand", this one keeps the answer true while
     * the screen is open. Written straight in - a presence event is never worth
     * arguing with, it is the server saying something changed a moment ago.
     */
    fun remember(updates: List<Presence>) {
        updates.forEach { if (it.uid.isNotBlank()) known[it.uid] = it }
    }

    /** Signing out: whose account this was is no longer anyone's business. */
    fun clear() {
        known.clear()
    }
}

/**
 * The line under a name: "Online now", when they were last seen, or nothing.
 *
 * Nothing is a real answer and the important one. The server returns no last seen
 * for anyone who hides their active status or has blocked you, so a stand-in like
 * "last seen recently" would be the app inventing the very fact the person asked it
 * not to give out - and it would read as true, because every other line here is.
 */
fun presenceLabel(presence: Presence?): String = when {
    presence == null -> ""
    presence.online -> "Online now"
    presence.lastSeenAt > 0L -> "Last seen " + lastSeenStamp(presence.lastSeenAt)
    else -> ""
}

/**
 * How long ago, in the shape a header wants.
 *
 * Minutes while that is still the useful unit, then hours, then the day - the same
 * narrowing the call list uses, for the same reason: "last seen 1,412 minutes ago"
 * is arithmetic rather than an answer.
 */
private fun lastSeenStamp(millis: Long): String {
    val gap = System.currentTimeMillis() - millis
    if (gap < 0L) return "just now"

    val minutes = gap / 60_000L
    if (minutes < 1L) return "just now"
    if (minutes < 60L) return "${minutes}m ago"

    val hours = minutes / 60L
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = millis }
    val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
    val sameDay = sameYear && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)

    if (sameDay) return if (hours < 1L) "${minutes}m ago" else "${hours}h ago"

    val daysApart = if (sameYear) {
        now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR)
    } else {
        Int.MAX_VALUE
    }
    val pattern = when {
        daysApart == 1 -> "'yesterday'"
        daysApart in 2..6 -> "EEEE"
        sameYear -> "d MMM"
        else -> "d MMM yyyy"
    }
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis))
}
