package com.iqsocial.iq_message.data.remote

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Starting, answering and ending calls.
 *
 * None of this carries audio or video. Three of these routes hand back a
 * [CallConnection] - a LiveKit room and a token for it - and the media goes there.
 * This API is the part that decides *who is in a call and what state it is in*; the
 * engine is a separate thing entirely.
 *
 * **`client_id` is what makes starting a call safe to retry.** The server answers
 * 201 the first time and 200 for the same id again, and never rings twice. A call
 * placed on a bad connection is therefore retried rather than guessed about.
 */
object CallsApi {

    /**
     * Starts a one-to-one call and connects you straight away.
     *
     * The other person's phone rings over both the socket and a push. Connect to
     * LiveKit immediately rather than waiting for them to answer: the call is then
     * live the moment they do, instead of starting to negotiate at that point.
     *
     * A call to somebody already on another call comes back **ended** with
     * `end_reason = busy`, which is an answer rather than a failure.
     */
    suspend fun start(
        token: String,
        to: String,
        type: CallType,
        clientId: String = UUID.randomUUID().toString()
    ): ApiResult<CallSession> =
        ApiClient.post(
            "/v1/calls",
            JSONObject()
                .put("to", to)
                .put("type", type.value)
                .put("client_id", clientId),
            token,
            MessengerConfig.BASE_URL
        ).mapTo(CallSession::started)

    /** The same, for two to seven people. Everyone rings at once. */
    suspend fun startGroup(
        token: String,
        to: List<String>,
        type: CallType,
        clientId: String = UUID.randomUUID().toString()
    ): ApiResult<CallSession> =
        ApiClient.post(
            "/v1/calls/group",
            JSONObject()
                .put("to", JSONArray(to))
                .put("type", type.value)
                .put("client_id", clientId),
            token,
            MessengerConfig.BASE_URL
        ).mapTo(CallSession::started)

    /**
     * Answers a call that is ringing for you.
     *
     * Safe to repeat - a second accept returns a fresh token - which matters because
     * the ringing screen can be reached from a push, a socket event and a tap on a
     * notification, and more than one of them can win.
     *
     * [existing] is the call already in hand: the reply carries only what changed, so
     * without it the fixed half (who, what type, which direction) would be lost.
     */
    suspend fun accept(
        token: String,
        callId: String,
        existing: ChatCall? = null,
        deviceId: String? = null
    ): ApiResult<CallSession> =
        ApiClient.post(
            "/v1/calls/${Uri.encode(callId)}/accept",
            JSONObject().apply { deviceId?.let { put("device_id", it) } },
            token,
            MessengerConfig.BASE_URL
        ).mapTo { CallSession.accepted(it, existing) }

    /**
     * Turns it down. Only while it rings.
     *
     * **409 once it has been answered**, which is the point: a ringing screen left
     * open on another device cannot hang up a call you are already in.
     */
    suspend fun decline(token: String, callId: String): ApiResult<ChatCall> =
        ApiClient.post(
            "/v1/calls/${Uri.encode(callId)}/decline",
            JSONObject(),
            token,
            MessengerConfig.BASE_URL
        ).mapTo(ChatCall::stateFrom)

    /**
     * Leaves it, in any state.
     *
     * While it rings this cancels; once answered it hangs up. In a group call you
     * leave and the others carry on. Safe to repeat, so the end of a call never has
     * to be guarded against being called twice.
     */
    suspend fun end(token: String, callId: String): ApiResult<ChatCall> =
        ApiClient.post(
            "/v1/calls/${Uri.encode(callId)}/end",
            JSONObject(),
            token,
            MessengerConfig.BASE_URL
        ).mapTo(ChatCall::stateFrom)

    /** Rings more people into a call you are in; a one-to-one call becomes a group. */
    suspend fun invite(token: String, callId: String, to: List<String>): ApiResult<ChatCall> =
        ApiClient.post(
            "/v1/calls/${Uri.encode(callId)}/invite",
            JSONObject().put("to", JSONArray(to)),
            token,
            MessengerConfig.BASE_URL
        ).mapTo(ChatCall::stateFrom)

    /** One call as it stands: opened from a missed-call notice, or to rejoin a group. */
    suspend fun one(token: String, callId: String): ApiResult<ChatCall> =
        ApiClient.get(
            "/v1/calls/${Uri.encode(callId)}",
            token,
            MessengerConfig.BASE_URL
        ).mapTo(ChatCall::from)

    /** What `GET /v1/calls` may be narrowed to. */
    enum class Filter(val value: String) { ALL("all"), MISSED("missed") }

    /** The history, newest first. */
    suspend fun history(
        token: String,
        filter: Filter = Filter.ALL,
        cursor: String? = null,
        limit: Int = 30
    ): ApiResult<CallPage> {
        val query = buildString {
            append("/v1/calls?filter=").append(filter.value)
            append("&limit=").append(limit.coerceIn(1, 100))
            cursor?.takeIf { it.isNotBlank() }?.let { append("&cursor=").append(Uri.encode(it)) }
        }
        return ApiClient.get(query, token, MessengerConfig.BASE_URL).mapTo(CallPage::from)
    }

    /** Removes calls from **your** history; everyone else keeps theirs. */
    suspend fun delete(token: String, callIds: List<String>): ApiResult<Unit> =
        ApiClient.post(
            "/v1/calls/delete",
            JSONObject().put("call_ids", JSONArray(callIds.take(100))),
            token,
            MessengerConfig.BASE_URL
        ).mapTo { }
}
