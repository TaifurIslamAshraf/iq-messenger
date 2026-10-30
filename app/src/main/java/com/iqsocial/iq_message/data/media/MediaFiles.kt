package com.iqsocial.iq_message.data.media

import com.iqsocial.iq_message.data.remote.MessengerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * A message's media, brought down to a file that can be played.
 *
 * Fetched rather than streamed, and that is forced by the route: `/media` answers a
 * **302** to a signed link, and the bearer token must not follow it. A player handed
 * the original URL would carry the header across to storage, which rejects a request
 * signed one way and authorised another - so the redirect is walked here, by hand,
 * with the token dropped at the first hop.
 *
 * One copy of that walk, shared. It was written once for voice and would have been
 * written again for video, and the part worth getting right - stopping the token at
 * the redirect, and never leaving a half-written file behind as though it were
 * whole - is the part that quietly diverges when it exists twice.
 */
object MediaFiles {

    /**
     * The file for [messageId], downloading it if this is the first ask.
     *
     * Null when it could not be had. The partial file is removed on the way out:
     * a truncated video that stays on disk is one that never plays again, because
     * every later call finds it and believes it.
     */
    suspend fun cached(
        cacheDir: File,
        token: String,
        messageId: String,
        suffix: String
    ): File? = withContext(Dispatchers.IO) {
        val safe = messageId.filter { it.isLetterOrDigit() || it == '_' || it == '-' }
        if (safe.isEmpty() || token.isBlank()) return@withContext null

        val target = File(cacheDir, "$suffix$safe")
        if (target.exists() && target.length() > 0) return@withContext target

        runCatching { fetch(token, messageId, target) }.getOrNull()
            ?: run { target.delete(); null }
    }

    private fun fetch(token: String, messageId: String, target: File): File? {
        val first = (URL("${MessengerConfig.BASE_URL}/v1/messages/$messageId/media")
            .openConnection() as HttpURLConnection).apply {
            // The token stops here. Everything after this hop is already signed.
            instanceFollowRedirects = false
            connectTimeout = CONNECT_MS
            readTimeout = READ_MS
            setRequestProperty("Authorization", "Bearer $token")
        }
        val location = try {
            when (first.responseCode) {
                in 300..399 -> first.getHeaderField("Location")
                in 200..299 -> {
                    first.inputStream.use { input ->
                        target.outputStream().use { out -> input.copyTo(out) }
                    }
                    return target
                }

                else -> null
            }
        } finally {
            first.disconnect()
        }
        if (location.isNullOrBlank()) return null

        val second = (URL(location).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_MS
            readTimeout = READ_MS
        }
        return try {
            if (second.responseCode in 200..299) {
                second.inputStream.use { input ->
                    target.outputStream().use { out -> input.copyTo(out) }
                }
                target
            } else {
                null
            }
        } finally {
            second.disconnect()
        }
    }

    private const val CONNECT_MS = 20_000
    private const val READ_MS = 60_000
}
