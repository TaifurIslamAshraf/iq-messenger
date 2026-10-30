package com.iqsocial.iq_message.ui.screens

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.iqsocial.iq_message.data.auth.SessionStore
import com.iqsocial.iq_message.data.media.DeviceGallery
import com.iqsocial.iq_message.data.media.MediaCache
import com.iqsocial.iq_message.data.media.RetainedMediaCache

private data class PreviewKey(val messageId: String, val localUri: String, val video: Boolean)

private val LocalThreadMedia =
    staticCompositionLocalOf<RetainedMediaCache<PreviewKey, ImageBitmap>?> { null }

@Composable
internal fun ProvideThreadMedia(conversationId: String, token: String?, content: @Composable () -> Unit) {
    val media = rememberMediaCache(conversationId, token)
    CompositionLocalProvider(LocalThreadMedia provides media, content = content)
}

@Composable
private fun rememberMediaCache(conversationId: String, token: String?): RetainedMediaCache<PreviewKey, ImageBitmap> {
    val context = LocalContext.current.applicationContext
    val owner = rememberCoroutineScope()
    val media = remember(conversationId, token, context) {
        RetainedMediaCache<PreviewKey, ImageBitmap>(
            owner = owner,
            maxBytes = (Runtime.getRuntime().maxMemory() / 8)
                .coerceIn(8L * 1024 * 1024, 32L * 1024 * 1024),
            sizeOf = { it.width.toLong() * it.height * 4 },
            peek = { key ->
                if (key.localUri.isNotBlank()) DeviceGallery.peekPreview(Uri.parse(key.localUri))
                else MediaCache.peek(key.messageId)
            },
            fetch = { key ->
                if (key.localUri.isNotBlank()) DeviceGallery.preview(context, Uri.parse(key.localUri))
                else MediaCache.load(token, key.messageId, thumbnailOnly = key.video)
            }
        )
    }
    DisposableEffect(media) { onDispose { media.close() } }
    return media
}

@Composable
internal fun rememberThreadPreview(
    messageId: String,
    localUri: String = "",
    video: Boolean = false
): State<ImageBitmap?> {
    val provided = LocalThreadMedia.current
    // Shared-media sheets also use MediaThumb without an enclosing chat provider.
    val media = if (provided != null) provided else {
        val context = LocalContext.current
        val token = remember(context) { SessionStore(context).token }
        rememberMediaCache("", token)
    }
    val key = remember(messageId, localUri, video) { PreviewKey(messageId, localUri, video) }
    val state = remember(media, key) { media.state(key) }
    LaunchedEffect(media, key) { media.load(key) }
    return state
}
