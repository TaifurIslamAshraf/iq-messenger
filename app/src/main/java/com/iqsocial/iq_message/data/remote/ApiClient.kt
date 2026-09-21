package com.iqsocial.iq_message.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

sealed interface ApiResult<out T> {
    /** [message] is the envelope's `msg`; some endpoints put the outcome there. */
    data class Success<T>(val data: T, val message: String? = null) : ApiResult<T>

    /** [notImplemented] marks a route the server does not expose yet (404). */
    data class Failure(
        val message: String,
        val notImplemented: Boolean = false
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
                requestMethod = method
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
                val note = json.optString("msg").takeIf { it.isNotBlank() }
                    ?: json.optString("message").takeIf { it.isNotBlank() }
                ApiResult.Success(result, note)
            } else {
                val msg = json.optString("msg").takeIf { it.isNotBlank() }
                    ?: json.optString("message").takeIf { it.isNotBlank() }
                    ?: "Something went wrong"
                // Zod-style validation replies carry the useful detail in `errors`.
                val detail = json.optJSONArray("errors")?.let { arr ->
                    (0 until arr.length())
                        .mapNotNull { arr.optJSONObject(it)?.optString("message") }
                        .filter { it.isNotBlank() }
                        .joinToString("\n")
                        .takeIf { it.isNotBlank() }
                }
                val missing = code == 404 || msg.equals("Route not found", ignoreCase = true)
                ApiResult.Failure(detail ?: msg, notImplemented = missing)
            }
        } catch (e: java.net.SocketTimeoutException) {
            ApiResult.Failure("The server took too long to respond")
        } catch (e: java.io.IOException) {
            ApiResult.Failure("Can't reach the server. Check your connection.")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Something went wrong")
        } finally {
            conn?.disconnect()
        }
    }
}
