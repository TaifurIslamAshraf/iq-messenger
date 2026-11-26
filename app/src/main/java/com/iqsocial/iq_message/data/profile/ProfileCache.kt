package com.iqsocial.iq_message.data.profile

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Profiles as they last arrived, kept on this phone so they open without a network.
 *
 * Without it a profile was a request every time: offline, the account's own tab
 * showed no name and no counts, and somebody else's profile showed nothing but a
 * spinner and an error. Now the last copy is drawn at once and the network, when
 * there is one, only brings it up to date.
 *
 * The JSON exactly as the server sent it, parsed back with the same code that parsed
 * it the first time, so the stored copy and a fresh one cannot drift apart. Pictures
 * are not kept here: [com.iqsocial.iq_message.data.media.RemoteImage] already keeps
 * them on disk.
 *
 * In `filesDir`, not `cacheDir`: the system empties a cache folder whenever it is
 * short of space, and that is exactly the moment somebody is likely to be offline.
 */
object ProfileCache {

    @Volatile
    private var dir: File? = null

    /** Called at start-up; harmless to call again. */
    fun init(context: Context) {
        if (dir == null) dir = File(context.applicationContext.filesDir, "profiles")
    }

    const val ME = "me"

    fun personKey(uid: String) = "person_$uid"

    private fun fileFor(key: String): File? {
        val folder = dir ?: return null
        if (!folder.exists() && !folder.mkdirs()) return null
        val safe = key.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File(folder, "$safe.json")
    }

    suspend fun read(key: String): JSONObject? = withContext(Dispatchers.IO) {
        runCatching {
            fileFor(key)?.takeIf { it.isFile }?.readText()?.let(::JSONObject)
        }.getOrNull()
    }

    /**
     * Written to a temporary file and moved into place, so a process killed halfway
     * through leaves the old copy rather than half of a new one.
     */
    suspend fun write(key: String, json: JSONObject) {
        withContext(Dispatchers.IO) {
            runCatching {
                val target = fileFor(key) ?: return@runCatching
                val temp = File(target.parentFile, target.name + ".tmp")
                temp.writeText(json.toString())
                if (!temp.renameTo(target)) {
                    target.delete()
                    temp.renameTo(target)
                }
                prune(target.parentFile)
            }
        }
    }

    /**
     * Keeps the folder from growing for ever: every profile ever opened is a file.
     * Oldest first, down to half, so this runs rarely. The account's own is never
     * one of them.
     */
    private fun prune(folder: File?) {
        val files = folder?.listFiles { f -> f.name.startsWith("person_") } ?: return
        if (files.size <= MAX_PEOPLE) return
        files.sortedBy { it.lastModified() }
            .take(files.size - MAX_PEOPLE / 2)
            .forEach { runCatching { it.delete() } }
    }

    /** Signing out: these were one account's view of people, not the next one's. */
    fun wipe() {
        dir?.let { runCatching { it.deleteRecursively() } }
    }

    private const val MAX_PEOPLE = 300
}
