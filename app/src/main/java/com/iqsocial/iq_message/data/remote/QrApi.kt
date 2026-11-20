package com.iqsocial.iq_message.data.remote

import androidx.compose.runtime.Immutable
import org.json.JSONObject

/**
 * The ten characters that stand for this account, and when they stop doing so.
 *
 * The server is explicit that the QR image carries **this and nothing else** - no
 * URL, no JSON wrapper - so that a code pasted as text and a code scanned from a
 * screen are the same thing to it.
 */
@Immutable
data class QrCode(
    val code: String,
    /** Epoch ms. A code lasts a day, and is replaced when under an hour is left. */
    val expiresAt: Long
) {
    /**
     * The code in threes, which is how a human reads ten characters aloud.
     *
     * Only for showing. What is scanned and what is sent back is [code] itself -
     * the server ignores case, spaces and dashes on the way in, which is what
     * makes a prettier spelling safe here.
     */
    val spaced: String
        get() = code.chunked(4).joinToString("-")
}

/** What a scan found: whose code it was, and the conversation now open with them. */
@Immutable
data class QrScan(
    val person: ChatPerson,
    val conversation: ChatConversation
)

/**
 * `/v1/qr`: showing a code, replacing it, and reading somebody else's.
 *
 * Three calls and one idea - a code is a short-lived stand-in for an account, so
 * that two people who have never exchanged numbers can still start a conversation
 * by pointing a phone at a screen.
 */
object QrApi {

    /**
     * This account's current code.
     *
     * The same code comes back each time until under an hour of it is left, so a
     * screen showing one does not have to refresh while somebody is scanning it.
     */
    suspend fun mine(token: String): ApiResult<QrCode> =
        ApiClient.get("/v1/qr", token, MessengerConfig.BASE_URL).mapTo(::codeOf)

    /**
     * Throws the current code away and answers with a new one.
     *
     * The old one stops working immediately - which is the point: it is what
     * somebody does after showing their code to a room.
     */
    suspend fun reset(token: String): ApiResult<QrCode> =
        ApiClient.post("/v1/qr/reset", JSONObject(), token, MessengerConfig.BASE_URL)
            .mapTo(::codeOf)

    /**
     * Reads somebody's code: adds them to this account's people, opens the chat.
     *
     * Sent as scanned or pasted; case, spaces and dashes do not matter to the
     * server, so nothing is cleaned up here either - a client that "tidied" a code
     * would be the one place a valid code could be broken on its way out.
     *
     * Scanning the same code twice changes nothing, so a repeat is not an error.
     * 404 covers unknown, expired, replaced, banned and blocked alike, and always
     * with the same message, which is deliberate: the answer must not say which.
     */
    suspend fun scan(token: String, code: String): ApiResult<QrScan> =
        ApiClient.post(
            "/v1/qr/scan",
            JSONObject().put("code", code),
            token,
            MessengerConfig.BASE_URL
        ).mapTo { body ->
            QrScan(
                person = ChatPerson.from(body.optJSONObject("person") ?: JSONObject()),
                conversation = ChatConversation.from(
                    body.optJSONObject("conversation") ?: JSONObject()
                )
            )
        }

    private fun codeOf(json: JSONObject) = QrCode(
        code = json.string("code"),
        expiresAt = json.optLong("expires_at")
    )
}
