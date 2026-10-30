package com.iqsocial.iq_message.data.call

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

/**
 * Where a call's sound goes in and out.
 *
 * **This is not a detail.** Setting `isSpeakerphoneOn` - which is what the obvious
 * reading of "turn the speaker on" produces - forces the loudspeaker whatever else is
 * attached, so a Bluetooth headset goes deaf and dumb the moment a call starts. The
 * microphone is worse: a headset's mic needs a separate SCO link brought up by hand
 * on older versions, and without it the call picks up the phone's own microphone from
 * wherever the phone happens to be.
 *
 * So routing is asked for by **what it is for**, not by which switch to flip, and
 * what is actually attached decides.
 */
object CallAudio {

    /**
     * What the call should come out of.
     *
     * [BLUETOOTH] is a choice and not just a fact about what is attached. A headset
     * used to win unconditionally, which is right the moment a call starts and wrong
     * for the rest of it: someone who puts the phone to their ear mid-call, or hands
     * it to the person next to them, has said something the router was not listening
     * to. Now it only wins by default.
     */
    enum class Route { EARPIECE, SPEAKER, BLUETOOTH }

    private var manager: AudioManager? = null
    private var previousMode = AudioManager.MODE_NORMAL
    private var appContext: Context? = null

    /** What was last asked for, so a device appearing mid-call can be obeyed. */
    private var wanted: Route = Route.EARPIECE

    /** True while something worn or plugged in is available, for the UI to offer it. */
    var headsetAvailable by mutableStateOf(false)
        private set

    /**
     * Whether that headset is wireless.
     *
     * Only so the button can name the right thing. [Route.BLUETOOTH] covers wired,
     * USB and Bluetooth alike - they are one routing decision - but they are not one
     * picture, and a Bluetooth rune over a cabled headset is the icon naming a
     * device that is not there.
     */
    var headsetIsWireless by mutableStateOf(false)
        private set

    /** Where the call is actually coming out, which the UI shows rather than guesses. */
    var route by mutableStateOf(Route.EARPIECE)
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
        manager = appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    /** True while something better than the phone's own speakers is plugged in. */
    fun headsetAttached(context: Context): Boolean {
        val audio = manager ?: return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val worn = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.isHeadset(context) }
        headsetIsWireless = worn.any {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
        }
        return worn.isNotEmpty()
    }

    /**
     * Takes the audio stream for a call and routes it.
     *
     * `MODE_IN_COMMUNICATION` is what tells the system this is a call: it turns on
     * echo cancellation and noise suppression, and it is the mode Bluetooth hands-free
     * links are brought up under. Without it a headset connects for media and the
     * microphone is never offered at all.
     *
     * Focus is taken first and devices are watched from here on, because both last
     * exactly as long as the call does.
     */
    fun begin(context: Context, route: Route) {
        val audio = manager ?: return
        previousMode = audio.mode
        takeFocus()
        runCatching { audio.mode = AudioManager.MODE_IN_COMMUNICATION }
        watchDevices(context)
        apply(context, route)
    }

    /**
     * Chooses the device.
     *
     * A headset wins when nothing else was asked for, which is not the same as always
     * winning: [Route.EARPIECE] and [Route.SPEAKER] are now honoured even with one
     * connected, because a person reaching for the speaker button while wearing a
     * headset means it.
     */
    fun apply(context: Context, route: Route) {
        val audio = manager ?: return
        wanted = route

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // One call covers earpiece, speaker, wired and Bluetooth alike, and the
            // microphone follows the output it belongs to.
            val devices = audio.availableCommunicationDevices
            val headset = devices.firstOrNull { it.isHeadset(context) }
            val target = when (route) {
                Route.BLUETOOTH -> headset
                Route.SPEAKER ->
                    devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                Route.EARPIECE ->
                    devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
            } ?: headset
            target?.let { runCatching { audio.setCommunicationDevice(it) } }
            this.route = if (target != null && target.isHeadset(context)) {
                Route.BLUETOOTH
            } else {
                route
            }
            return
        }

        // Before Android 12 the hands-free link is brought up by hand, and only then
        // is the headset's microphone in the call at all.
        @Suppress("DEPRECATION")
        runCatching {
            val useHeadset = route == Route.BLUETOOTH &&
                headsetAttached(context) &&
                audio.isBluetoothScoAvailableOffCall
            if (useHeadset) {
                audio.startBluetoothSco()
                audio.isBluetoothScoOn = true
                audio.isSpeakerphoneOn = false
                this.route = Route.BLUETOOTH
            } else {
                audio.stopBluetoothSco()
                audio.isBluetoothScoOn = false
                audio.isSpeakerphoneOn = route == Route.SPEAKER
                this.route = route
            }
        }
    }

    /** Hands the stream back to whatever the phone was doing before. */
    fun end() {
        val audio = manager ?: return
        stopWatchingDevices()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audio.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                audio.stopBluetoothSco()
                @Suppress("DEPRECATION")
                audio.isBluetoothScoOn = false
                @Suppress("DEPRECATION")
                audio.isSpeakerphoneOn = false
            }
            audio.mode = previousMode
        }
        dropFocus()
        headsetAvailable = false
        headsetIsWireless = false
        route = Route.EARPIECE
    }

    /* -- focus ------------------------------------------------------------- */

    private var focus: AudioFocusRequest? = null

    /**
     * Stops everything else making noise.
     *
     * Without this the call is simply one more sound: music keeps playing underneath
     * it, a video in another app keeps talking over it, and neither is told the call
     * ended. `TRANSIENT_EXCLUSIVE` is the one that says "stop, I will hand it back" -
     * as opposed to ducking, which lowers the music instead of stopping it, and is
     * wrong for speech.
     */
    private fun takeFocus() {
        val audio = manager ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val request = AudioFocusRequest.Builder(
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                ).setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                ).build()
                focus = request
                audio.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                audio.requestAudioFocus(
                    null,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                )
            }
        }
    }

    private fun dropFocus() {
        val audio = manager ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focus?.let { audio.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audio.abandonAudioFocus(null)
            }
        }
        focus = null
    }

    /* -- devices coming and going ------------------------------------------ */

    private var deviceWatch: AudioDeviceCallback? = null

    /**
     * Follows headsets plugged in and pulled out during the call.
     *
     * The route used to be decided once, when the call started. Plug a headset in
     * half way through and the call stayed on the phone's own speaker; pull one out
     * and the call kept talking to a device that was no longer there. Neither is
     * recoverable by the person, because the button on screen only chooses between
     * earpiece and speaker.
     *
     * Pulling one out falls back to [wanted], the last thing actually asked for,
     * rather than to a fixed default - so a call put on speaker before the headset
     * arrived goes back to speaker rather than to the earpiece.
     */
    private fun watchDevices(context: Context) {
        val audio = manager ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        stopWatchingDevices()
        headsetAvailable = headsetAttached(context)

        val watch = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) {
                val app = appContext ?: return
                headsetAvailable = headsetAttached(app)
                // Something was just put on or plugged in: that is a choice, and the
                // call follows it.
                if (added?.any { it.isHeadset(app) } == true) apply(app, Route.BLUETOOTH)
            }

            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) {
                val app = appContext ?: return
                headsetAvailable = headsetAttached(app)
                if (!headsetAvailable && wanted == Route.BLUETOOTH) {
                    // Gone. Back to the phone itself, loud rather than at the ear:
                    // a headset pulled out is usually a phone being put down.
                    apply(app, Route.SPEAKER)
                }
            }
        }
        runCatching { audio.registerAudioDeviceCallback(watch, null) }
        deviceWatch = watch
    }

    private fun stopWatchingDevices() {
        val audio = manager ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        deviceWatch?.let { runCatching { audio.unregisterAudioDeviceCallback(it) } }
        deviceWatch = null
    }

    /**
     * Whether this is something worn or plugged in.
     *
     * Bluetooth needs `BLUETOOTH_CONNECT` from Android 12 to be read at all, and
     * without it the device list comes back without the headset rather than with an
     * error - so a call would route to the phone with nothing to say why. Checked
     * here so that case is at least visible in one place.
     */
    private fun AudioDeviceInfo.isHeadset(context: Context): Boolean = when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET -> bluetoothAllowed(context)

        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_USB_HEADSET -> true

        else -> false
    }

    fun bluetoothAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
}
