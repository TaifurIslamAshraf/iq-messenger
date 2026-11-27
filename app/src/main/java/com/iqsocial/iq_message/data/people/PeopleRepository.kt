package com.iqsocial.iq_message.data.people

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.iqsocial.iq_message.data.chat.ChatRepository
import com.iqsocial.iq_message.data.chat.KnownPeople
import com.iqsocial.iq_message.data.contacts.ContactsRepository
import com.iqsocial.iq_message.data.profile.ProfileCache
import com.iqsocial.iq_message.data.remote.ApiResult
import com.iqsocial.iq_message.data.remote.ChatPerson
import com.iqsocial.iq_message.data.remote.PeopleApi
import com.iqsocial.iq_message.data.remote.QrApi
import com.iqsocial.iq_message.data.remote.QrScan
import com.iqsocial.iq_message.data.remote.SavedPerson
import com.iqsocial.iq_message.data.remote.mapObjects
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/**
 * The account's people (`GET /v1/people`), held once for every screen that offers someone.
 *
 * The People tab, "New message", the rewards special-person picker and "Add to call"
 * all show this same list, so it is read here once per app start rather than by each
 * of them. Searching goes to the server ([PeopleApi.list] with a query) and is not
 * kept here.
 *
 * The list only changes through this account's own actions - a contact sync that
 * found someone, a QR code it scanned - and both update it straight away, so there is
 * nothing to poll for.
 *
 * A copy is kept on disk ([ProfileCache], wiped at sign-out with the rest of it) so
 * the pickers have people to offer before the network answers, or without one.
 */
object PeopleRepository {

    var people by mutableStateOf<List<SavedPerson>>(emptyList())
        private set

    /** True once a list is in hand, from the server or from the copy on disk. */
    var loaded by mutableStateOf(false)
        private set

    /** True while the list is being fetched. */
    var loading by mutableStateOf(false)
        private set

    /** The last fetch failed and nothing was in hand to show instead. */
    var failed by mutableStateOf(false)
        private set

    private val lock = Mutex()
    private var fetchedThisRun = false

    /** Fetches the list unless this run already has. Cheap to call from every screen. */
    suspend fun ensureLoaded(token: String) {
        if (!fetchedThisRun) refresh(token)
    }

    /** Fetches the whole list again. A failure keeps what was there. */
    suspend fun refresh(token: String) = lock.withLock {
        if (!loaded) readCopy()
        loading = true
        try {
            val all = fetchAll(token)
            if (all == null) {
                failed = !loaded
                return@withLock
            }
            people = all
            loaded = true
            failed = false
            fetchedThisRun = true
            writeCopy(all)
        } finally {
            loading = false
        }
    }

    /**
     * Someone just added by QR code, shown at once. The server has already put them
     * on the list; this saves the round trip to find that out.
     */
    fun add(person: ChatPerson, now: Long = System.currentTimeMillis()) {
        val existing = people.firstOrNull { it.uid == person.uid }
        people = if (existing == null) {
            listOf(SavedPerson(person, listOf(SavedPerson.SOURCE_QR), null, now)) + people
        } else if (SavedPerson.SOURCE_QR in existing.sources) {
            people
        } else {
            people.map { if (it.uid == person.uid) it.copy(sources = it.sources + SavedPerson.SOURCE_QR) else it }
        }
    }

    /**
     * Adds whoever owns a scanned or typed QR [code] (`POST /v1/qr/scan`): the server
     * puts them on this list and opens a chat with them in the one answer.
     *
     * Both halves are kept here, for every screen that scans: the person goes on the
     * list at once and is remembered for the chat, so it has a name before it fetches
     * anything, and the chat list is told a conversation may be new.
     */
    suspend fun addByCode(token: String, code: String): ApiResult<QrScan> {
        val result = QrApi.scan(token, code.trim())
        if (result is ApiResult.Success) {
            KnownPeople.remember(result.data.conversation.id, result.data.person)
            add(result.data.person)
            ChatRepository.invalidate()
        }
        return result
    }

    /** What this phone calls [uid] when they are on the list ([shownName]); null when they are not. */
    fun listedName(uid: String?): String? {
        if (uid.isNullOrBlank()) return null
        return people.firstOrNull { it.uid == uid }?.shownName
    }

    /**
     * The name to draw for [peer] outside the people screens: theirs, else what the
     * list calls them, else "Unknown".
     *
     * An account can have no name at all, and the server then sends an empty one.
     * Someone found by their number is on the list under that number, so a chat
     * with them says the number rather than "Unknown".
     */
    fun nameOf(peer: ChatPerson?): String =
        peer?.fullname?.takeIf { it.isNotBlank() } ?: listedName(peer?.uid) ?: "Unknown"

    /** The list belongs to whoever is signed in, so it goes when they do. The disk copy goes with [ProfileCache]. */
    fun clear() {
        people = emptyList()
        loaded = false
        loading = false
        failed = false
        fetchedThisRun = false
    }

    /** Every page, or null when one fails: half a list would be shown as the whole of it. */
    private suspend fun fetchAll(token: String): List<SavedPerson>? {
        val all = mutableListOf<SavedPerson>()
        var cursor: String? = null
        repeat(MAX_PAGES) {
            when (val page = PeopleApi.list(token, cursor = cursor, limit = PAGE_SIZE)) {
                is ApiResult.Failure -> return null
                is ApiResult.Success -> {
                    all += page.data.items
                    cursor = page.data.nextCursor ?: return all.distinctBy { it.uid }
                }
            }
        }
        return all.distinctBy { it.uid }
    }

    private suspend fun readCopy() {
        val json = ProfileCache.read(CACHE_KEY) ?: return
        val copy = runCatching { json.optJSONArray("items").mapObjects(SavedPerson::from) }.getOrNull() ?: return
        people = copy
        loaded = true
    }

    private suspend fun writeCopy(list: List<SavedPerson>) {
        ProfileCache.write(CACHE_KEY, JSONObject().put("items", JSONArray(list.map { it.toJson() })))
    }

    private const val PAGE_SIZE = 100

    /** 3,000 people. Past that the list stops growing on this phone; search still finds the rest. */
    private const val MAX_PAGES = 30

    private const val CACHE_KEY = "people_list"
}

/**
 * What this phone calls them, the same on every screen that lists people: the name
 * in the phone book, else their IQ name, else the number they were found by.
 */
val SavedPerson.shownName: String
    get() = ContactsRepository.nameFor(phone) ?: person.fullname.ifBlank { phone ?: "Unknown" }
