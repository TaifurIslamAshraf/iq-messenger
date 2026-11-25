package com.iqsocial.iq_message.data.reels

import android.content.Context
import android.net.ConnectivityManager
import android.os.PowerManager
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager
import androidx.media3.exoplayer.source.preload.TargetPreloadStatusControl
import com.iqsocial.iq_message.data.remote.Reel
import java.io.File

/**
 * Where downloaded reels are kept on the phone.
 *
 * One instance for the life of the process, and that is not a style choice:
 * `SimpleCache` locks its folder, and a second one opened on the same folder throws.
 * So it is made once, here, and never released - the process ending is what closes
 * it.
 *
 * Bounded at 200 MB and trimmed oldest-first. Without a bound a cache of video is a
 * leak that happens to be on disk: every reel ever watched, kept for ever, in a
 * folder the person cannot see in any setting.
 */
@OptIn(UnstableApi::class)
object ReelCache {

    @Volatile
    private var cache: SimpleCache? = null

    fun of(context: Context): SimpleCache =
        cache ?: synchronized(this) {
            cache ?: SimpleCache(
                File(context.applicationContext.cacheDir, "reels"),
                LeastRecentlyUsedCacheEvictor(MAX_BYTES),
                StandaloneDatabaseProvider(context.applicationContext)
            ).also { cache = it }
        }

    private const val MAX_BYTES = 200L * 1024 * 1024
}

/**
 * One viewing of the reels: a player, and the next few reels loaded ahead of it.
 *
 * Opened when the viewer opens and released when it closes. Built on Media3's
 * `DefaultPreloadManager`, which exists for exactly this - a feed of short videos
 * swiped one at a time - and is told two things: which reel is playing, and how much
 * of each reel near it to have ready.
 *
 * Everything it downloads goes through [ReelCache] on the way, so a reel loaded
 * ahead and then played is played from disk, and one watched twice is downloaded
 * once.
 */
@OptIn(UnstableApi::class)
class ReelSession(context: Context) {

    private val app = context.applicationContext

    /** Read on the preload thread, written on the main one. */
    @Volatile
    private var current = 0

    @Volatile
    private var budget: List<Long> = emptyList()

    /**
     * How much of a reel to have ready, by how far ahead of the playing one it is.
     *
     * Only ahead: a reel already behind has been played, so most of it is on disk
     * already and swiping back to it is quick without help. Everything outside the
     * budget answers null, and the preload manager then *clears* that reel - drops
     * what it had loaded - which is what keeps memory flat however far somebody
     * swipes.
     */
    private val control =
        TargetPreloadStatusControl<Int, DefaultPreloadManager.PreloadStatus> { rank ->
            val ahead = rank - current
            if (ahead <= 0) null
            else budget.getOrNull(ahead - 1)
                ?.let { DefaultPreloadManager.PreloadStatus.specifiedRangeLoaded(it) }
        }

    private val builder = DefaultPreloadManager.Builder(app, control)
        .setMediaSourceFactory(DefaultMediaSourceFactory(cachedSource(app)))
        .setLoadControl(reelLoadControl())

    /** The one player every page shares. Built by the preload manager's builder,
     *  so the two use the same loaders and the player can take a preloaded source
     *  without loading it again. */
    val player: ExoPlayer = builder.buildExoPlayer().apply {
        // Reels loop until swiped away, which is what a reel is.
        repeatMode = Player.REPEAT_MODE_ONE
        // Takes audio focus, so a ringing call or a voice note pauses it instead of
        // playing over the top of it.
        setAudioAttributes(AudioAttributes.DEFAULT, true)
    }

    /**
     * The reel whose own first frame is on the surface - not merely the one asked
     * for.
     *
     * Every page shares this one player, and a player that is switched to a new
     * reel goes on showing the last frame of the old one until the new one has
     * decoded something. The new page was drawing exactly that: for a moment, the
     * reel just swiped away, sitting on the page of the next one. Pages keep their
     * video hidden until this names them, and show their own thumbnail meanwhile.
     */
    var shown by mutableStateOf<String?>(null)
        private set

    /**
     * Each reel's real shape - width over height, as the decoder reports it - by
     * reel id.
     *
     * The page sizes the video's frame from this, so the frame is this reel's
     * shape before its first picture is drawn into it. It used to be the player
     * view's own business, and the player view asked the shared player - which,
     * at the moment a new page took it, was still describing the *previous*
     * reel. A landscape reel followed by an upright one drew the upright one's
     * first frames into a landscape box, squashed, until the new size caught up.
     */
    val ratios = mutableStateMapOf<String, Float>()

    /** The reel [playAt] last asked for, waiting for its first frame. */
    private var pending: String? = null

    /**
     * Credits a drawn frame to the reel it actually came from.
     *
     * "A first frame was rendered" is not enough on its own, and trusting it was
     * the bug that survived the first fix. The page swiped to attaches a new
     * surface to the shared player, and a player given a new surface redraws the
     * frame it is holding onto it - the old reel's - and reports that as a first
     * frame too. Taken at its word, that marked the new page ready while the old
     * reel's picture was on it.
     *
     * The analytics version of the callback says which media the frame belongs to:
     * the timeline and window as the playback thread saw them when it drew. A frame
     * from anything but the reel just asked for is ignored, whatever it reports.
     * Delivered on the main thread, which is where Compose state wants writing.
     */
    init {
        player.addAnalyticsListener(object : AnalyticsListener {
            private val window = Timeline.Window()

            /** Which reel the playback thread was on when it reported this. */
            private fun reelOf(eventTime: AnalyticsListener.EventTime): String? {
                val timeline = eventTime.timeline
                if (timeline.isEmpty || eventTime.windowIndex >= timeline.windowCount) return null
                return timeline.getWindow(eventTime.windowIndex, window).mediaItem.mediaId
            }

            override fun onRenderedFirstFrame(
                eventTime: AnalyticsListener.EventTime,
                output: Any,
                renderTimeMs: Long
            ) {
                val drawn = reelOf(eventTime)
                if (drawn == pending) shown = drawn
            }

            // Reported before the first frame is, so by the time a page shows its
            // video the frame around it already has the video's own shape.
            override fun onVideoSizeChanged(
                eventTime: AnalyticsListener.EventTime,
                videoSize: VideoSize
            ) {
                val reel = reelOf(eventTime) ?: return
                if (videoSize.width <= 0 || videoSize.height <= 0) return
                ratios[reel] =
                    videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
            }
        })
    }

    private val preload: DefaultPreloadManager = builder.build()

    /** One MediaItem per reel, kept: the preload manager finds its sources by item. */
    private val items = HashMap<String, MediaItem>()

    /**
     * Tells the preload manager about every reel it does not know yet, ranked by
     * its place in the feed. Called again when a new page arrives.
     */
    fun sync(reels: List<Reel>) {
        var added = false
        reels.forEachIndexed { index, reel ->
            if (items.containsKey(reel.id)) return@forEachIndexed
            val item = MediaItem.Builder()
                .setMediaId(reel.id)
                .setUri(reel.videoUrl)
                .apply {
                    // Said outright for Cloudflare's manifests, so the player does not
                    // have to guess from the URL; anything else is left to it.
                    if (reel.videoUrl.contains(".m3u8")) setMimeType(MimeTypes.APPLICATION_M3U8)
                }
                .build()
            items[reel.id] = item
            preload.add(item, index)
            added = true
        }
        if (added) preload.invalidate()
    }

    /**
     * Plays the reel at [index] and moves the preloading along with it.
     *
     * The budget is read here, at every swipe, rather than once: the person may
     * walk out of Wi-Fi, or turn Data Saver on, halfway down the feed.
     */
    fun playAt(index: Int, reels: List<Reel>) {
        val reel = reels.getOrNull(index) ?: return
        val item = items[reel.id] ?: return
        current = index
        budget = budgetFor(app)
        // Before the switch, so not one frame of the old reel can be credited to
        // the new one.
        pending = reel.id
        shown = null
        preload.setCurrentPlayingIndex(index)

        // The preloaded source when there is one - that is the whole point - and the
        // plain item when there is not, which still plays, only from cold.
        val source = preload.getMediaSource(item)
        if (source != null) player.setMediaSource(source) else player.setMediaItem(item)
        player.prepare()
        player.playWhenReady = true

        preload.invalidate()
    }

    fun pause() {
        player.playWhenReady = false
    }

    fun resume() {
        player.playWhenReady = true
    }

    fun release() {
        player.release()
        preload.release()
    }

    private companion object {

        /**
         * The download path: through the cache, to the network.
         *
         * Errors in the cache fall through to the network rather than failing the
         * reel - a full or damaged cache should cost a download, not a video.
         */
        fun cachedSource(context: Context): CacheDataSource.Factory =
            CacheDataSource.Factory()
                .setCache(ReelCache.of(context))
                .setUpstreamDataSourceFactory(
                    DefaultDataSource.Factory(
                        context,
                        DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
                    )
                )
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        /**
         * How far ahead to load, decided by what the phone is on.
         *
         * - **Wi-Fi** (or any unmetered network): the next reel to 10 seconds, the
         *   one after to 4, the third to 2. The next one is the one most likely to
         *   be swiped to, so it gets the most; the third is barely more than its
         *   first frame. About half the data of loading all three to 10 seconds,
         *   for the same instant start on the reel that is actually opened next.
         * - **Mobile data, or battery saver on**: only the next one, to 5 seconds.
         * - **Data Saver on**: nothing ahead at all. The person has said what they
         *   want from their data allowance, and a reel that starts a moment late is
         *   the honest price of that.
         */
        fun budgetFor(context: Context): List<Long> {
            val network = context.getSystemService(ConnectivityManager::class.java)
                ?: return SPARING
            if (network.restrictBackgroundStatus ==
                ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
            ) return emptyList()

            val saving = context.getSystemService(PowerManager::class.java)
                ?.isPowerSaveMode == true
            return if (network.isActiveNetworkMetered || saving) SPARING else GENEROUS
        }

        val GENEROUS = listOf(10_000L, 4_000L, 2_000L)

        /**
         * Two ahead on mobile data, not one.
         *
         * With one, a second swipe in quick succession landed on a reel nothing
         * had touched - manifest, playlist, first segment, all from cold - and on
         * a fast 4.5G connection that was still well over a second of nothing.
         * The second reel gets less than the first because it is less likely to
         * be reached, and a few seconds is all it needs to start at once.
         */
        val SPARING = listOf(5_000L, 3_000L)

        /**
         * When playback may start, and how far ahead the playing reel buffers.
         *
         * Media3's defaults are for long-form video: it waits for 2.5 seconds of
         * picture before starting, and buffers up to 50 seconds ahead. For a reel
         * the first is the whole problem - a reel that was not preloaded sat
         * blank while two and a half seconds downloaded, even on a connection
         * that could deliver the first half-second almost at once. Half a second
         * is enough to start on; the reel keeps loading while it plays.
         *
         * The ceiling comes down too. A reel is seconds long and loops, and
         * fifty seconds buffered ahead of something swiped away after five is
         * data downloaded for nothing.
         */
        fun reelLoadControl(): DefaultLoadControl =
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs = */ 10_000,
                    /* maxBufferMs = */ 20_000,
                    /* bufferForPlaybackMs = */ 500,
                    /* bufferForPlaybackAfterRebufferMs = */ 1_000
                )
                .build()
    }
}
