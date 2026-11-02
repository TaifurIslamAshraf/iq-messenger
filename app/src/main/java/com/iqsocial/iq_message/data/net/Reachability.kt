package com.iqsocial.iq_message.data.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Whether this phone can reach anything, and a shout when it can again.
 *
 * The outbox used to find out through the chat socket: the socket reconnects by
 * itself, and something watched it come back. That worked, but it answered a
 * different question. A socket being down is not the same as a network being down -
 * the server's websocket can be out while HTTP is perfectly fine - and a socket
 * notices a Wi-Fi-to-mobile handover only when its ping times out, twenty-odd
 * seconds after everything else on the phone has moved over. Queued messages waited
 * all of that out for no reason.
 *
 * So the question is asked of the thing that knows the answer.
 *
 * **Validated, not merely connected.** [ConnectivityManager.NetworkCallback.onAvailable]
 * fires for a network that has an address but has not yet been shown to reach
 * anything - a captive portal at a cafe, a mobile connection mid-attach. Draining a
 * queue into one of those produces a round of failures and nothing else, so what is
 * waited for is the capability that says traffic has actually got somewhere.
 */
object Reachability {

    private const val TAG = "Reachability"

    /**
     * Whether something can be reached right now.
     *
     * Starts true, which is the useful lie: the honest answer before the system has
     * said anything is "nobody has asked yet", and starting false would have the app
     * announce itself offline for the first moments of every launch.
     */
    var online by mutableStateOf(true)
        private set

    private var watching = false
    private var whenBack: (() -> Unit)? = null

    /**
     * Starts watching, and says what to do when the network returns.
     *
     * Safe to call again: the action is replaced, the system is not asked twice. The
     * callback is registered against the application context on purpose - it has to
     * outlive any screen, because the whole point is to notice a change that happens
     * while nobody is looking at one.
     */
    fun start(context: Context, onReturn: () -> Unit) {
        whenBack = onReturn
        if (watching) return

        val app = context.applicationContext
        val manager = app.getSystemService(ConnectivityManager::class.java)
        if (manager == null) {
            Log.w(TAG, "No ConnectivityManager; falling back to the socket for this")
            return
        }

        // What is true before any callback arrives. Without it the first answer only
        // comes when something changes, and a phone that is simply online stays at
        // whatever this was initialised to.
        settle(manager.usable(), announce = false)

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                settle(caps.usable(), announce = true)
            }

            override fun onLost(network: Network) {
                settle(false, announce = true)
            }

            override fun onUnavailable() {
                settle(false, announce = true)
            }
        }

        runCatching { manager.registerDefaultNetworkCallback(callback) }
            .onSuccess { watching = true }
            // Said out loud rather than swallowed. Without this the app still works -
            // the socket coming back drains the queue as it did before - but it works
            // slowly, and silently losing the fast path is how a fast path quietly
            // stops existing.
            .onFailure { Log.w(TAG, "Could not watch the network; the socket is the fallback", it) }
    }

    /**
     * Records the new answer, and shouts only on the edge that matters.
     *
     * Capabilities change for all sorts of reasons - a signal strength, a metered
     * flag - and most arrive while already online. Firing on each would drain the
     * queue over and over for no news.
     */
    private fun settle(usable: Boolean, announce: Boolean) {
        val wasOffline = !online
        online = usable
        if (announce && usable && wasOffline) {
            Log.i(TAG, "Network is back; picking up whatever was waiting")
            runCatching { whenBack?.invoke() }
                .onFailure { Log.w(TAG, "Something went wrong picking it up", it) }
        }
    }

    private fun ConnectivityManager.usable(): Boolean =
        activeNetwork?.let { getNetworkCapabilities(it) }.usable()

    private fun NetworkCapabilities?.usable(): Boolean =
        this != null &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
