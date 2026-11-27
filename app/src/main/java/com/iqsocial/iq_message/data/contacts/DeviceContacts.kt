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
import com.iqsocial.iq_message.data.auth.SessionStore
import com.iqsocial.iq_message.data.remote.ApiResult
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
 *
 * Who of them is on IQ is not kept here: the server adds them to the account's
 * people list, and [PeopleRepository] is where every screen reads that. What stays
 * here is the phone's own names, so the People tab can call someone what this phone
 * calls them.
 */
object ContactsRepository {

    var contacts by mutableStateOf<List<Person>>(emptyList())
        private set

    /** True once a read has completed, even if it found nothing. */
    var loaded by mutableStateOf(false)
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

    /** The phone's names by [numberKey], rebuilt whenever [contacts] is. */
    private var namesByNumber: Map<String, String> = emptyMap()

    /**
     * Reads the address book and, when signed in, sends the server the numbers it
     * has not had yet. Returns true when the server found someone on IQ among them,
     * which is when the people list has grown.
     *
     * The server only ever adds - a number deleted from the phone stays, by the
     * owner's decision - so a number sent once never needs sending again, and an
     * ordinary launch uploads nothing unless the phone book gained a number.
     * [everything] sends the whole book anyway: a pull-to-refresh, which is also how
     * someone already in the book who joined IQ since is found.
     *
     * A failed sync is deliberately not fatal: the contacts have been read, and the
     * People tab is useful without them.
     */
    suspend fun refresh(context: Context, token: String?, everything: Boolean = false): Boolean {
        syncing = true
        try {
            contacts = DeviceContacts.read(context)
            namesByNumber = contacts
                .filter { it.name.isNotBlank() }
                .associateBy({ numberKey(it.handle) }, { it.name })
            loaded = true
            if (token.isNullOrBlank() || contacts.isEmpty()) return false

            val session = SessionStore(context)
            val sent = session.sentContactNumbers
            val toSend = contacts.map { it.handle }
                .filter { everything || digitsOf(it) !in sent }
                .distinct()
            if (toSend.isEmpty()) return false

            val result = MessengerApi.syncContacts(
                phones = toSend,
                region = DeviceContacts.region(context),
                token = token
            )
            if (result !is ApiResult.Success) return false
            lastSync = result.data
            session.sentContactNumbers = sent + toSend.map(::digitsOf)
            return result.data.matches.isNotEmpty()
        } finally {
            // In a finally, because the early returns above are normal paths out and
            // a flag left true would leave the spinner turning for good.
            syncing = false
        }
    }

    /** What this phone's address book calls the owner of [phone], if it has them. */
    fun nameFor(phone: String?): String? = phone?.let { namesByNumber[numberKey(it)] }

    /**
     * Numbers come back from the server in E.164 but are stored on the device in
     * whatever form somebody typed, so the two are compared by their last nine
     * digits — enough to identify a subscriber without relying on a country code
     * that may not be there.
     */
    private fun numberKey(raw: String): String = raw.filter(Char::isDigit).takeLast(9)

    /** How a sent number is remembered: every digit, so two numbers are never taken for one. */
    private fun digitsOf(raw: String): String = raw.filter(Char::isDigit)

    /** The address book belongs to whoever is signed in, so it goes when they do. */
    fun clear() {
        contacts = emptyList()
        namesByNumber = emptyMap()
        loaded = false
        lastSync = null
        syncing = false
    }
}
