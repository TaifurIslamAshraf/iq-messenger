package com.iqsocial.iq_message.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.iqsocial.iq_message.data.people.PeopleRepository
import com.iqsocial.iq_message.data.people.shownName
import com.iqsocial.iq_message.data.remote.ApiResult
import com.iqsocial.iq_message.data.remote.ChatPerson
import com.iqsocial.iq_message.data.remote.PeopleApi
import com.iqsocial.iq_message.data.remote.SavedPerson
import kotlinx.coroutines.delay

/** What a people screen shows for what has been typed: the whole list, or what the search found. */
@Immutable
data class PeopleResults(
    val people: List<SavedPerson>,
    /** Nothing to show yet because an answer is still on its way. */
    val loading: Boolean,
    /** Showing a search rather than the list. */
    val isSearch: Boolean,
    /** The list could not be fetched, or the search failed. */
    val error: String?
)

/**
 * The account's people for [query]: the list itself while fewer than two characters
 * are typed, and the server's search of that same list after a short pause.
 *
 * Shared by every screen that offers someone, so they all find people the same way
 * and from the same place.
 */
@Composable
fun rememberPeopleResults(token: String?, query: String): PeopleResults {
    val trimmed = query.trim()
    val isSearch = trimmed.length >= PeopleApi.MIN_QUERY
    var found by remember { mutableStateOf<List<SavedPerson>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(token) {
        if (!token.isNullOrBlank()) PeopleRepository.ensureLoaded(token)
    }

    LaunchedEffect(trimmed, token) {
        if (token.isNullOrBlank() || !isSearch) {
            found = emptyList()
            searching = false
            searchError = null
            return@LaunchedEffect
        }
        searching = true
        // So typing a name is one request rather than one a letter.
        delay(SEARCH_PAUSE_MS)
        when (val result = PeopleApi.list(token, query = trimmed, limit = SEARCH_LIMIT)) {
            is ApiResult.Success -> {
                found = result.data.items
                searchError = null
            }
            is ApiResult.Failure -> {
                found = emptyList()
                searchError = result.message
            }
        }
        searching = false
    }

    return if (isSearch) {
        PeopleResults(found, loading = searching, isSearch = true, error = searchError)
    } else {
        PeopleResults(
            people = PeopleRepository.people,
            // Until a list is in hand or the fetch has failed: the first frame comes
            // before the fetch has even started, and "nobody yet" would flash past.
            loading = !PeopleRepository.loaded && !PeopleRepository.failed,
            isSearch = false,
            error = if (PeopleRepository.failed) "Couldn't load your people" else null
        )
    }
}

/** [SavedPerson.person] under [shownName], for the screens that work with a [ChatPerson]. */
fun SavedPerson.shown(): ChatPerson = person.copy(fullname = shownName)

private const val SEARCH_PAUSE_MS = 300L
private const val SEARCH_LIMIT = 50
