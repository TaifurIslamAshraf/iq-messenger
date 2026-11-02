package com.iqsocial.iq_message.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Where the IQ Social backend lives.
 *
 * The live host serves the user API under `/v1/api` (the same prefix
 * `iq-backend-web/.env` calls MAIN_API_URL). Note the deployed route names differ
 * from the local repo's router: it is `/login` and `/signup` here, not
 * `/api/auth/login` and `/api/auth/register`.
 *
 * `/v1/api/auth/... ` on the same host is a separate ADMIN API keyed by email — not
 * for this app.
 */
object ApiConfig {
    const val BASE_URL = "https://connect.iqamasocial.com/v1/api"
    const val TIMEOUT_MS = 20_000
}

/**
 * A string field, with JSON null read as nothing there.
 *
 * Used everywhere instead of `optString`, which does not do this. Android's
 * `optString` stringifies whatever it finds, and what it finds for a null field is
 * the `JSONObject.NULL` sentinel - whose `toString` is the four characters `null`.
 * So a field the server sends as null comes back as the **word** "null", which is
 * not empty, is not blank, and passes every check written to catch a missing value.
 *
 * It showed up as messages that said "null". A photo or a call has no text, the
 * server sends `"text": null`, and `text.isNotBlank()` was true - so the bubble and
 * the chat list drew the word instead of "Photo". The same four characters were
 * being handed to the image loader as a profile picture, and to anything else that
 * reads a string out of an API that is allowed to leave one out.
 */
internal fun JSONObject.string(name: String, fallback: String = ""): String =
    if (isNull(name)) fallback else optString(name, fallback)

/** The same for an element of an array, which carries the same trap. */
internal fun JSONArray.string(index: Int, fallback: String = ""): String =
    if (isNull(index)) fallback else optString(index, fallback)

sealed interface ApiResult<out T> {
    /** [message] is the envelope's `msg`; some endpoints put the outcome there. */
    data class Success<T>(val data: T, val message: String? = null) : ApiResult<T>

    /** [notImplemented] marks a route the server does not expose yet (404). */
    data class Failure(
        val message: String,
        val notImplemented: Boolean = false,
        /**
         * The request never reached the server, so nothing has been refused.
         *
         * The distinction matters to anything that retries. A server saying no tends
         * to say no again, and work that keeps being refused has to be given up on
         * eventually; a phone in a tunnel will say yes the moment it is out of it,
         * and giving up on that would lose a message for being written in the wrong
         * place.
         */
        val offline: Boolean = false
    ) : ApiResult<Nothing>
}

/**
 * Minimal JSON-over-HTTP client built on [HttpURLConnection].
 *
 * Deliberately dependency-free: the app ships no Retrofit/OkHttp, and the auth
 * surface is three endpoints, so the platform client is enough.
 *
 * The backend answers failures with HTTP 200 and `{"status":"error","msg":...}`
 * (see utils/response.js), so the envelope's `status` decides the outcome, not the
 * status code.
 */
object ApiClient {

    /*
     * The messenger host is the default: it is where the app's API surface lives
     * from here on. The account API is now the exception and every call to it says
     * so, by passing [ApiConfig.BASE_URL] explicitly — see [AuthApi].
     */

    suspend fun post(
        path: String,
        body: JSONObject,
        token: String? = null,
        baseUrl: String = MessengerConfig.BASE_URL
    ): ApiResult<JSONObject> = request("POST", path, body, token, baseUrl)

    suspend fun get(
        path: String,
        token: String? = null,
        baseUrl: String = MessengerConfig.BASE_URL
    ): ApiResult<JSONObject> = request("GET", path, null, token, baseUrl)

    suspend fun put(
        path: String,
        body: JSONObject,
        token: String? = null,
        baseUrl: String = MessengerConfig.BASE_URL
    ): ApiResult<JSONObject> = request("PUT", path, body, token, baseUrl)

    suspend fun patch(
        path: String,
        body: JSONObject,
        token: String? = null,
        baseUrl: String = MessengerConfig.BASE_URL
    ): ApiResult<JSONObject> = request("PATCH", path, body, token, baseUrl)

    suspend fun delete(
        path: String,
        token: String? = null,
        baseUrl: String = MessengerConfig.BASE_URL
    ): ApiResult<JSONObject> = request("DELETE", path, null, token, baseUrl)

    private suspend fun request(
        method: String,
        path: String,
        body: JSONObject?,
        token: String?,
        baseUrl: String
    ): ApiResult<JSONObject> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
                // PATCH is awkward: the JDK's HttpURLConnection rejects it outright.
                // The usual workaround — POST plus X-HTTP-Method-Override — was
                // tried against this server and 404s ("Route not found: POST
                // /v1/messages/{id}"), so the method is forced through instead.
                // Android's implementation accepts PATCH; the reflection is there
                // for the stricter JDK one that unit tests would run against.
                try {
                    requestMethod = method
                } catch (_: java.net.ProtocolException) {
                    forceMethod(this, method)
                }
                connectTimeout = ApiConfig.TIMEOUT_MS
                readTimeout = ApiConfig.TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                token?.let { setRequestProperty("Authorization", "Bearer $it") }
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
            }

            body?.let {
                conn.outputStream.use { out -> out.write(it.toString().toByteArray(Charsets.UTF_8)) }
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()

            if (text.isBlank()) {
                return@withContext ApiResult.Failure(
                    message = "Server returned an empty response ($code)",
                    notImplemented = code == 404
                )
            }

            val json = try {
                JSONObject(text)
            } catch (_: Exception) {
                return@withContext ApiResult.Failure(
                    message = if (code == 404) "That endpoint does not exist yet"
                    else "Unexpected response from server",
                    notImplemented = code == 404
                )
            }

            // Three envelope styles across the two hosts: {"status":"success",...}
            // from the user API, {"status":true,"message":...} from the admin one, and
            // {"success":true,"responseObject":...} from the messenger server. Every
            // spelling of the flag, the payload and the message key is accepted.
            val succeeded = when (val flag = json.opt("status") ?: json.opt("success")) {
                is Boolean -> flag
                is String -> flag.equals("success", true) || flag.equals("ok", true)
                else -> false
            }

            if (succeeded) {
                val result = json.optJSONObject("result")
                    ?: json.optJSONObject("data")
                    ?: json.optJSONObject("responseObject")
                    ?: JSONObject()
                val note = json.string("msg").takeIf { it.isNotBlank() }
                    ?: json.string("message").takeIf { it.isNotBlank() }
                ApiResult.Success(result, note)
            } else {
                val msg = json.string("msg").takeIf { it.isNotBlank() }
                    ?: json.string("message").takeIf { it.isNotBlank() }
                    ?: "Something went wrong"
                // Zod-style validation replies carry the useful detail in `errors`.
                val detail = json.optJSONArray("errors")?.let { arr ->
                    (0 until arr.length())
                        .mapNotNull { arr.optJSONObject(it)?.string("message") }
                        .filter { it.isNotBlank() }
                        .joinToString("\n")
                        .takeIf { it.isNotBlank() }
                }
                val missing = code == 404 || msg.equals("Route not found", ignoreCase = true)
                ApiResult.Failure(detail ?: msg, notImplemented = missing)
            }
        } catch (e: java.net.SocketTimeoutException) {
            // Marked as offline rather than refused: a timeout is the server not
            // answering, which is the same to a caller as not being reachable.
            ApiResult.Failure("The server took too long to respond", offline = true)
        } catch (e: java.io.IOException) {
            ApiResult.Failure("Can't reach the server. Check your connection.", offline = true)
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Something went wrong")
        } finally {
            conn?.disconnect()
        }
    }
}

/**
 * Sets a request method the connection would otherwise refuse.
 *
 * Only reached when [HttpURLConnection.setRequestMethod] throws, which it does on
 * the JDK for PATCH. Failing quietly is correct here: the request then goes as
 * whatever method was already set, and the server reports the mismatch.
 */
private fun forceMethod(conn: HttpURLConnection, method: String) {
    runCatching {
        val field = HttpURLConnection::class.java.getDeclaredField("method")
        field.isAccessible = true
        field.set(conn, method)
    }
}
