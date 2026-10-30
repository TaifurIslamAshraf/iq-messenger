package com.iqsocial.iq_message.data.remote

import androidx.compose.runtime.Immutable
import org.json.JSONObject

/**
 * How far a slot has got.
 *
 * `in_progress` covers two things the server treats alike: talk that has been
 * counted, and a call with that person happening right now. Worth knowing when
 * reading a slot that shows no talk yet still says it is under way.
 */
enum class SlotStatus(val wire: String) {
    NOT_STARTED("not_started"),
    IN_PROGRESS("in_progress"),
    COMPLETED("completed");

    companion object {
        fun from(value: String?): SlotStatus =
            entries.firstOrNull { it.wire.equals(value, ignoreCase = true) } ?: NOT_STARTED
    }
}

/** A normal slot, or the one special slot, which pays differently. */
enum class SlotKind(val wire: String) {
    NORMAL("normal"),
    SPECIAL("special");

    companion object {
        fun from(value: String?): SlotKind =
            if (value.equals(SPECIAL.wire, ignoreCase = true)) SPECIAL else NORMAL
    }
}

/** When a change to the special person starts counting. */
enum class AppliesFrom(val wire: String) {
    TODAY("today"),
    TOMORROW("tomorrow");

    companion object {
        fun from(value: String?): AppliesFrom =
            if (value.equals(TOMORROW.wire, ignoreCase = true)) TOMORROW else TODAY
    }
}

/**
 * One person's slot for today.
 *
 * [talkMs] counts only calls that have **ended**; a call in progress is in
 * [RewardsToday.live] instead, and it is the app's job to add the two together while
 * it is running. The server says as much, and it is why a slot can read
 * `in_progress` with `talk_ms` of zero.
 */
@Immutable
data class RewardSlot(
    val kind: SlotKind,
    /** Left out of push payloads to keep them under FCM's size limit, so nullable. */
    val person: ChatPerson?,
    val status: SlotStatus,
    val talkMs: Long,
    val targetMs: Long,
    val points: Int,
    val completedAt: Long
) {
    companion object {
        fun from(json: JSONObject): RewardSlot = RewardSlot(
            kind = SlotKind.from(json.optString("kind")),
            person = json.optJSONObject("person")?.let(ChatPerson::from),
            status = SlotStatus.from(json.optString("status")),
            talkMs = json.optLong("talk_ms"),
            targetMs = json.optLong("target_ms"),
            points = json.optInt("points"),
            completedAt = json.optLong("completed_at")
        )
    }
}

/**
 * The special slot, which is a normal slot plus the right to choose who is in it.
 *
 * [canChangeToday] is the one field with teeth: once today's special slot has talk
 * against it the choice is locked, and a new pick lands tomorrow instead. The server
 * enforces that either way - this is so the screen can say so before the tap rather
 * than after.
 */
@Immutable
data class SpecialSlot(
    /** Null until somebody is picked. */
    val person: ChatPerson?,
    val status: SlotStatus,
    val talkMs: Long,
    val targetMs: Long,
    val points: Int,
    val completedAt: Long,
    val canChangeToday: Boolean
) {
    companion object {
        fun from(json: JSONObject): SpecialSlot = SpecialSlot(
            person = json.optJSONObject("person")?.let(ChatPerson::from),
            status = SlotStatus.from(json.optString("status")),
            talkMs = json.optLong("talk_ms"),
            targetMs = json.optLong("target_ms"),
            points = json.optInt("points"),
            completedAt = json.optLong("completed_at"),
            canChangeToday = json.optBoolean("can_change_today", true)
        )
    }
}

/**
 * The one-to-one call happening right now, for the screen to tick.
 *
 * [since] is server time, not this phone's, and [RewardsToday.serverNow] is what it
 * should be measured against - the two clocks can differ by minutes, and a timer
 * that starts at "4 minutes ago" because the phone is fast is worse than none.
 */
@Immutable
data class LiveTalk(
    val callId: String,
    val peerUid: String,
    val since: Long
) {
    companion object {
        fun from(json: JSONObject): LiveTalk = LiveTalk(
            callId = json.optString("call_id"),
            peerUid = json.optString("peer_uid"),
            since = json.optLong("since")
        )
    }
}

/** Everything `/v1/rewards/today` has to say. */
@Immutable
data class RewardsToday(
    val day: String,
    val serverNow: Long,
    val dayStartsAt: Long,
    val resetsAt: Long,
    val balance: Int,
    val usedMs: Long,
    val live: LiveTalk?,
    val slotsUsed: Int,
    val slotsTotal: Int,
    /** Above zero means there is room to earn another slot today. */
    val normalFree: Int,
    val slots: List<RewardSlot>,
    val special: SpecialSlot
) {
    companion object {
        fun from(json: JSONObject): RewardsToday {
            val slots = json.optJSONObject("slots") ?: JSONObject()
            return RewardsToday(
                day = json.optString("day"),
                serverNow = json.optLong("server_now"),
                dayStartsAt = json.optLong("day_starts_at"),
                resetsAt = json.optLong("resets_at"),
                balance = json.optJSONObject("points")?.optInt("balance") ?: 0,
                usedMs = json.optJSONObject("talk")?.optLong("used_ms") ?: 0L,
                live = json.optJSONObject("live")?.let(LiveTalk::from),
                slotsUsed = slots.optInt("used"),
                slotsTotal = slots.optInt("total"),
                normalFree = slots.optInt("normal_free"),
                slots = slots.optJSONArray("items").mapObjects(RewardSlot::from),
                special = SpecialSlot.from(json.optJSONObject("special") ?: JSONObject())
            )
        }
    }
}

/** What picking or clearing the special person answers with. */
@Immutable
data class SpecialChange(
    val appliesFrom: AppliesFrom,
    /** Who it now is, or null after clearing. */
    val person: ChatPerson?
) {
    companion object {
        fun from(json: JSONObject): SpecialChange = SpecialChange(
            appliesFrom = AppliesFrom.from(json.optString("applies_from")),
            person = json.optJSONObject("person")?.let(ChatPerson::from)
        )
    }
}

/**
 * `/v1/rewards`.
 *
 * Three calls and one idea: talking to people fills slots, and full slots pay
 * points. Everything is per-day and resets at `resets_at`.
 */
object RewardsApi {

    suspend fun today(token: String): ApiResult<RewardsToday> =
        ApiClient.get("/v1/rewards/today", token, MessengerConfig.BASE_URL)
            .mapTo(RewardsToday::from)

    /**
     * Picks the special person.
     *
     * **404 is an answer, not a fault.** The server returns it for an account that is
     * gone, banned, or that blocks you - all three being things the person choosing
     * is not entitled to be told apart, so the screen says the pick did not go
     * through and leaves it there.
     */
    suspend fun setSpecial(token: String, uid: String): ApiResult<SpecialChange> =
        ApiClient.put(
            path = "/v1/rewards/special",
            body = JSONObject().put("uid", uid),
            token = token,
            baseUrl = MessengerConfig.BASE_URL
        ).mapTo(SpecialChange::from)

    suspend fun clearSpecial(token: String): ApiResult<SpecialChange> =
        ApiClient.delete("/v1/rewards/special", token, MessengerConfig.BASE_URL)
            .mapTo(SpecialChange::from)
}
