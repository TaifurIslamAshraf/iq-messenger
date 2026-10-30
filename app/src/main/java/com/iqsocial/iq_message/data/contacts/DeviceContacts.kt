package com.iqsocial.iq_message.data.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.telephony.TelephonyManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.iqsocial.iq_message.data.Person
import com.iqsocial.iq_message.data.remote.ApiResult
import com.iqsocial.iq_message.data.remote.ContactMatch
import com.iqsocial.iq_message.data.remote.ContactSyncResult
import com.iqsocial.iq_message.data.remote.MessengerApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the phone's address book into the same [Person] model the rest of the app
 * uses, so the People tab renders device contacts and sample data identically.
 */
object DeviceContacts {

    fun isGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * One row per contact, not per number: somebody saved with a mobile and a work
     * line is one person in a messenger, so the first number wins and the rest are
     * skipped. Ordering is left to the provider's own name collation.
     *
     * Returns empty rather than throwing if the permission is missing or revoked
     * while the query runs — the caller treats "no contacts" and "not allowed" the
     * same way.
     */
    suspend fun read(context: Context): List<Person> = withContext(Dispatchers.IO) {
        if (!isGranted(context)) return@withContext emptyList()

        val projection = arrayOf(
            Phone.CONTACT_ID,
            Phone.DISPLAY_NAME_PRIMARY,
            Phone.NUMBER
        )

        val byContact = LinkedHashMap<String, Person>()
        runCatching {
            context.contentResolver.query(
                Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${Phone.DISPLAY_NAME_PRIMARY} COLLATE NOCASE ASC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(Phone.CONTACT_ID)
                val nameColumn = cursor.getColumnIndexOrThrow(Phone.DISPLAY_NAME_PRIMARY)
                val numberColumn = cursor.getColumnIndexOrThrow(Phone.NUMBER)

                while (cursor.moveToNext()) {
                    val contactId = cursor.getString(idColumn) ?: continue
                    if (byContact.containsKey(contactId)) continue

                    val number = tidyNumber(cursor.getString(numberColumn).orEmpty())
                    if (number.isEmpty()) continue

                    // A contact saved without a name still has to show something, and
                    // its number is the only thing left to call it.
                    val name = cursor.getString(nameColumn)?.trim().orEmpty()
                        .ifEmpty { number.trimStart('+') }

                    byContact[contactId] = Person(
                        id = "contact:$contactId",
                        name = name,
                        handle = number
                    )
                }
            }
        }
        byContact.values.toList()
    }

    /**
     * The SIM's country, so the server can read numbers saved without a country
     * code. Falls back to the network, then the device locale; null lets the server
     * default to the country of the user's own number.
     */
    fun region(context: Context): String? {
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val iso = telephony?.simCountryIso?.takeIf { it.isNotBlank() }
            ?: telephony?.networkCountryIso?.takeIf { it.isNotBlank() }
            ?: context.resources.configuration.locales[0].country
        return iso?.takeIf { it.length == 2 }?.uppercase()
    }

    /** Address books are full of odd spacing; the digits are what matter. */
    private fun tidyNumber(raw: String): String {
        val cleaned = raw.map { if (it.isWhitespace()) ' ' else it }
            .joinToString("")
            .trim()
            .replace(Regex(" +"), " ")
        return if (cleaned.any(Char::isDigit)) cleaned else ""
    }
}

/**
 * The loaded address book, held for the life of the process.
 *
 * Contacts are read once after sign-in and shared from here rather than re-queried
 * per screen — the provider call is slow enough to be worth doing once, and every
 * screen should see the same list.
 */
object ContactsRepository {

    var contacts by mutableStateOf<List<Person>>(emptyList())
        private set

    /** True once a read has completed, even if it found nothing. */
    var loaded by mutableStateOf(false)
        private set

    /** Contacts who turned out to be on IQ Social, keyed by [numberKey]. */
    var matches by mutableStateOf<Map<String, ContactMatch>>(emptyMap())
        private set

    /** Counts from the last successful sync, for anything that wants to show them. */
    var lastSync by mutableStateOf<ContactSyncResult?>(null)
        private set

    /**
     * True while the address book is being read and synced.
     *
     * Only here so a pull-to-refresh has something to hold its spinner open on.
     * Without it the gesture would spring back the instant it was let go, whatever
     * the sync was doing, and the one thing that gesture promises is that something
     * is happening.
     */
    var syncing by mutableStateOf(false)
        private set

    /**
     * Reads the address book and, when signed in, syncs it so the server can say
     * which of those numbers already have accounts.
     *
     * A failed sync is deliberately not fatal: the contacts have been read, and the
     * People tab is useful without yet knowing who is on IQ Social.
     */
    suspend fun refresh(context: Context, token: String?) {
        syncing = true
        try {
            contacts = DeviceContacts.read(context)
            loaded = true
            if (token.isNullOrBlank() || contacts.isEmpty()) return

            val result = MessengerApi.syncContacts(
                phones = contacts.map { it.handle },
                region = DeviceContacts.region(context),
                token = token
            )
            if (result is ApiResult.Success) {
                lastSync = result.data
                matches = result.data.matches.associateBy { numberKey(it.phone) }
            }
        } finally {
            // In a finally, because the early return above is a normal path out and
            // a flag left true would leave the spinner turning for good.
            syncing = false
        }
    }

    fun matchFor(person: Person): ContactMatch? = matches[numberKey(person.handle)]

    /**
     * Numbers come back from the server in E.164 but are stored on the device in
     * whatever form somebody typed, so the two are compared by their last nine
     * digits — enough to identify a subscriber without relying on a country code
     * that may not be there.
     */
    private fun numberKey(raw: String): String = raw.filter(Char::isDigit).takeLast(9)

    /** The address book belongs to whoever is signed in, so it goes when they do. */
    fun clear() {
        contacts = emptyList()
        loaded = false
        matches = emptyMap()
        lastSync = null
        syncing = false
    }
}
