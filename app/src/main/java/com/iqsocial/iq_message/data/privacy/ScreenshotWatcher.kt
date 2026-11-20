package com.iqsocial.iq_message.data.privacy

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Noticing a screenshot on the phones that will not say so themselves.
 *
 * `ScreenCaptureCallback` is the proper way and it arrived in Android 14; before
 * that there is no signal at all, and even on 14 a maker with its own screenshot
 * implementation can take one without the platform telling anybody. Both cases
 * leave `notify_on_screenshot` doing nothing for the people it is meant to protect.
 *
 * So this watches the other end of the same event: a screenshot becomes a file, the
 * file lands in the gallery, and the gallery tells anyone who asked. It is not a
 * substitute for the callback - it needs the picture permission, it sees the file
 * rather than the act, and a phone that saves screenshots somewhere unusual will
 * slip past it - which is why the two run together and whichever notices first
 * wins; see [ScreenshotWitness.captured] for how the second one is ignored.
 *
 * **Only while a conversation is on screen.** Registered with the activity's
 * foreground, so a screenshot of somebody else's app is not this app's business and
 * is never looked at. And even then it reports nothing unless [ScreenshotWitness]
 * is holding somebody: the file is not read, opened or uploaded - all that leaves
 * this phone is "a screenshot happened while I was looking at this person".
 */
object ScreenshotWatcher {

    private const val TAG = "ScreenshotWatcher"

    /** How recent a file has to be to count as the screenshot just taken. */
    private const val FRESH_SECONDS = 12L

    private var observer: ContentObserver? = null

    /** Ids already reported, so one file is not counted twice as it is written. */
    private val seen = mutableSetOf<Long>()

    /** Whether the gallery can be read at all; without it there is nothing to watch. */
    fun allowed(context: Context): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun start(context: Context) {
        if (observer != null) return
        if (!allowed(context)) {
            Log.i(TAG, "not watching: no permission to see the gallery")
            return
        }
        val app = context.applicationContext
        val watcher = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                runCatching { lookForOne(app) }
                    .onFailure { Log.w(TAG, "Could not check the gallery", it) }
            }
        }
        runCatching {
            app.contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                watcher
            )
            observer = watcher
            Log.i(TAG, "watching the gallery for screenshots")
        }.onFailure { Log.w(TAG, "Could not watch the gallery", it) }
    }

    fun stop(context: Context) {
        val watcher = observer ?: return
        observer = null
        runCatching { context.applicationContext.contentResolver.unregisterContentObserver(watcher) }
    }

    /**
     * The newest picture, if it is a screenshot and it is new enough to be *this*
     * screenshot.
     *
     * Both halves matter. Without "is a screenshot" every photo saved by every app
     * would be reported; without "new enough" the first change event after the app
     * starts would report whatever happened to be at the top of the gallery.
     */
    private fun lookForOne(context: Context) {
        val subject = ScreenshotWitness.showing() ?: return
        val columns = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED
        )
        val since = System.currentTimeMillis() / 1000 - FRESH_SECONDS
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            columns,
            "${MediaStore.Images.Media.DATE_ADDED} >= ?",
            arrayOf(since.toString()),
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { rows ->
            if (!rows.moveToFirst()) return
            val id = rows.getLong(rows.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
            val name = rows.getString(
                rows.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            ).orEmpty()
            if (!name.contains("screenshot", ignoreCase = true)) return
            if (!seen.add(id)) return
            // A file is written in pieces and the gallery announces it more than
            // once; the id set above is what turns that into one report.
            if (seen.size > 32) seen.iterator().let { if (it.hasNext()) { it.next(); it.remove() } }
            Log.i(TAG, "a screenshot appeared in the gallery: $name")
            ScreenshotWitness.captured(context, from = "gallery")
        }
        // Mentioned so the two detectors can be told apart in a log.
        if (subject.isBlank()) Log.i(TAG, "nobody on screen; nothing reported")
    }
}
