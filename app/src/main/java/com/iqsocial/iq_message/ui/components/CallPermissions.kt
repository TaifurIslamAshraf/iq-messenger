package com.iqsocial.iq_message.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.iqsocial.iq_message.data.call.CallCenter
import com.iqsocial.iq_message.data.call.CallEngine
import com.iqsocial.iq_message.data.remote.CallType

/**
 * Asks for what a call actually needs, at the moment it needs it.
 *
 * Not at launch, and not all at once: the microphone is asked for when a call starts,
 * the camera only when that call has a picture, and Bluetooth only to find a headset.
 * Someone who never makes a video call is never asked about their camera.
 *
 * **Refusing is handled rather than prevented.** A call with no microphone permission
 * still connects and is simply silent from this end - which is recoverable by granting
 * it - where refusing to connect at all would not be. The same for the camera: a video
 * call without one is a voice call, and that is a better answer than an error.
 */
@Composable
fun CallPermissions() {
    val context = LocalContext.current
    val call = CallCenter.call
    val video = call?.type == CallType.VIDEO

    val ask = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        // The engine joined the room without waiting. Now that the answer is in,
        // whatever was refused at connect time can be turned on.
        if (granted[Manifest.permission.RECORD_AUDIO] == true && !CallEngine.micOn) {
            CallEngine.toggleMic()
        }
        if (video && granted[Manifest.permission.CAMERA] == true && !CallEngine.cameraOn) {
            CallEngine.toggleCamera()
        }
    }

    val wanted = remember(video) { needed(video) }

    LaunchedEffect(call?.id, video) {
        if (call == null) return@LaunchedEffect
        val missing = wanted.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) ask.launch(missing.toTypedArray())
    }
}

/**
 * The camera button, with the permission asked for at the tap.
 *
 * [CallPermissions] asks for the camera when the call is a video call, and that was
 * the whole of it - so a voice call that grew a picture never asked. The camera was
 * then turned on by `setCameraEnabled`, inside a `runCatching` that swallowed the
 * SecurityException: the button did nothing, said nothing, and stayed looking off,
 * on both ends. The one who pressed it had no picture, and the one who already had
 * their camera on could not be joined.
 *
 * Asked here instead, at the moment the camera is actually wanted. That also keeps
 * the promise this file opens with - nothing is asked for before it is needed -
 * which widening the call-start request would have broken for every voice call.
 *
 * Turning the camera back off needs no permission: it is already granted, or the
 * camera would not be on.
 */
@Composable
fun rememberCameraToggle(): () -> Unit {
    val context = LocalContext.current
    val ask = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Only on the way on. A refusal leaves the call as it was, which is a voice
        // call - and that is a better answer than an error.
        if (granted && !CallEngine.cameraOn) CallEngine.toggleCamera()
    }
    // Both of what it closes over outlive recomposition - the launcher is
    // remembered and the context is the activity's - so the lambda can be too.
    return remember(ask) {
        {
            val held = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
            if (held) CallEngine.toggleCamera() else ask.launch(Manifest.permission.CAMERA)
        }
    }
}

/**
 * What a call of this kind needs.
 *
 * Bluetooth is in the list from Android 12, because without it a headset is not even
 * visible to the audio router - the call would quietly use the phone's own microphone
 * while somebody is wearing one.
 */
private fun needed(video: Boolean): List<String> = buildList {
    add(Manifest.permission.RECORD_AUDIO)
    if (video) add(Manifest.permission.CAMERA)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(Manifest.permission.BLUETOOTH_CONNECT)
    }
}

/** Whether a microphone is to hand at all, for deciding what to say rather than to block. */
fun micGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
