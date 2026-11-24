package com.iqsocial.iq_message.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import com.iqsocial.iq_message.data.call.CameraLook
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.iqsocial.iq_message.data.Person
import com.iqsocial.iq_message.data.profile.SettingsRepository
import com.iqsocial.iq_message.data.call.CallCenter
import com.iqsocial.iq_message.data.call.CallAudio
import com.iqsocial.iq_message.data.call.CallEngine
import com.iqsocial.iq_message.data.profile.MeRepository
import com.iqsocial.iq_message.ui.components.CallVideo
import com.iqsocial.iq_message.ui.components.HumpedBarShape
import com.iqsocial.iq_message.ui.components.KeepScreenOn
import com.iqsocial.iq_message.ui.components.avatarBrush
import com.iqsocial.iq_message.ui.components.rememberCameraToggle
import com.iqsocial.iq_message.ui.icons.IqIcons
import com.iqsocial.iq_message.ui.theme.CallBlack
import com.iqsocial.iq_message.ui.theme.Crimson
import com.iqsocial.iq_message.ui.theme.InkFaint
import com.iqsocial.iq_message.ui.components.SecureScreen
import com.iqsocial.iq_message.ui.components.peerAllowsScreenshot
import com.iqsocial.iq_message.ui.components.watchForScreenshotGesture
import com.iqsocial.iq_message.ui.theme.IqTheme
import com.iqsocial.iq_message.ui.theme.SystemBarIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
@Composable
fun VideoCallScreen(
    onMinimize: () -> Unit,
    onEnd: () -> Unit
) {
    SystemBarIcons(darkIcons = false)
    val d = IqTheme.dimens
    val call = CallCenter.call
    val person = remember(call?.peer) { callerOf(call) }

    // A call is as private as the chat it came from, and the same rule decides it:
    // the person on the other end says whether they may be screenshotted, and this
    // phone obeys. This account's own setting is not read here - it is enforced on
    // their phone, where this account is the peer. Theirs is read off the server,
    // since nothing in a call's own payload carries it.
    val peerAllows = peerAllowsScreenshot(call?.peer?.uid)
    SecureScreen(!peerAllows)
    val profile = MeRepository.profile
    val toggleCamera = rememberCameraToggle()

    /** The two sliders, which are only up while somebody is using them. */
    var looks by remember { mutableStateOf(false) }
    val lookContext = LocalContext.current
    LaunchedEffect(Unit) { CameraLook.load(lookContext) }
    val me = remember(profile) {
        Person(
            id = profile?.uid.orEmpty().ifBlank { "me" },
            name = profile?.fullname.orEmpty().ifBlank { "You" },
            handle = "",
            photo = profile?.profilePicture.orEmpty()
        )
    }

    // From when it was answered, not from when this screen opened - see the audio
    // screen for why those differ.
    val standing = standingOf(
        call,
        joined = CallEngine.connected,
        reconnecting = CallEngine.reconnecting,
        poor = CallEngine.poorConnection
    )
    var seconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(call?.answeredAt) {
        val from = call?.answeredAt ?: 0L
        while (true) {
            seconds =
                if (from > 0L) ((System.currentTimeMillis() - from) / 1000).toInt() else 0
            delay(1000)
        }
    }

    // A video call is watched without being touched, so the idle timeout has no
    // idea anyone is there and dims the screen mid-conversation.
    KeepScreenOn()

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(CallBlack)
            // Only while the call is one the system is blocking; see
            // [watchForScreenshotGesture].
            .watchForScreenshotGesture(active = !peerAllows)
    ) {
        /*
         * Everything except the picture can be put away.
         *
         * A video call is watched, and what is being watched is the person on the
         * other end - not the row of buttons over them. A tap anywhere on the
         * picture slides the header and the bar off their own edges; a tap brings
         * them back. Nothing is lost by hiding them, which is what makes it safe to
         * do on a tap rather than behind a control of its own.
         *
         * The self-view stays either way. It is not a control, it is the other half
         * of what there is to look at, and an app that hid your own face when you
         * asked for a clear view would have hidden the wrong thing.
         */
        var chrome by remember { mutableStateOf(true) }

        // Their camera, full screen. Until a frame arrives - they have not joined,
        // or have their camera off - the seeded gradient stands in, which is the same
        // thing their avatar is drawn on and so reads as them rather than as a fault.
        Box(
            Modifier
                .fillMaxSize()
                .background(avatarBrush(person.seed))
                // The tap that puts the controls away. It sits on the picture
                // rather than on the whole screen, so a tap meant for a button is
                // taken by the button and never reaches here.
                .pointerInput(Unit) { detectTapGestures { chrome = !chrome } }
        ) {
            val feeds = CallEngine.remoteFeeds
            if (feeds.size > 1) {
                // More than one other person, so there is no "their camera" to fill
                // the screen with. Only one was ever drawn before - whichever the
                // room listed first - and the rest were in the call unseen.
                CallGrid(
                    feeds = feeds,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(d.gapXs)
                )
            } else {
                CallVideo(
                    track = CallEngine.remoteVideo,
                    modifier = Modifier.fillMaxSize()
                )
            }
            // The scrim goes over whichever of the two is showing, so the controls
            // and the name stay readable against a bright picture.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.55f),
                            0.35f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.45f)
                        )
                    )
            )
        }

        // Your own camera, which is now nobody's child: it is placed over the
        // call rather than inside the header, so it can be put down anywhere and
        // can stay while everything else is away.
        // Read out here: inside the lambdas below, the layout scopes are nearer
        // receivers and these are no longer reachable without naming one.
        val roomFor = selfWidthIn(maxWidth)
        SelfView(
            me = me,
            chrome = chrome,
            onTap = { chrome = !chrome },
            wide = maxWidth,
            tall = maxHeight
        )

        AnimatedVisibility(
            visible = chrome,
            // Off its own edge rather than fading on the spot: a header that
            // slides up says where it went, and so says how to get it back.
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = d.gutterTight,
                            // Kept clear of where the self-view starts, so a long
                            // name never runs underneath it.
                            end = d.gutterTight + roomFor + d.gapSm,
                            top = d.gapMd
                        )
                ) {
                    Box(
                        Modifier
                            .size(d.iconButtonLg)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.16f))
                            .clickable(onClick = onMinimize),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            IqIcons.ChevronDown, "Minimize",
                            Modifier.size(d.iconLg), tint = Color.White
                        )
                    }
                    Spacer(Modifier.width(d.gapSm))
                    // The camera look lives up here, not in the control bar
                    // below: it is set once near the start of a call and then
                    // left alone, while the bar is for what gets reached for
                    // mid-sentence - mute, speaker, hang up. Filled in while
                    // something is on, so the screen says whether the picture
                    // being sent is the plain one without the panel being opened.
                    val lit = CameraLook.brightness > 0f || CameraLook.smooth > 0f
                    Box(
                        Modifier
                            .size(d.iconButtonLg)
                            .clip(CircleShape)
                            .background(
                                if (lit) Color.White else Color.White.copy(alpha = 0.16f)
                            )
                            .clickable { looks = !looks },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            IqIcons.Sparkle, "Camera look",
                            Modifier.size(d.iconLg),
                            tint = if (lit) Color.Black else Color.White
                        )
                    }
                    Spacer(Modifier.width(d.gapMd))
                    Column(Modifier.weight(1f)) {
                        Text(
                            person.name,
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        // The duration once there is one, and what the call is
                        // doing until then. A header reading 0:00 over a phone that
                        // is still ringing is the screen answering a question it has
                        // not been asked and not answering the one it has.
                        Text(
                            if (seconds > 0) formatDuration(seconds) else standing.label,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Normal,
                            maxLines = 1
                        )
                        // Shown only when the call actually is one.
                        //
                        // This line was unconditional, and it was the app's most
                        // serious untruth: no call this build places is encrypted
                        // end to end - `/v1/keys` is never called, this install has
                        // no key pair, and nothing is handed to LiveKit's encryption
                        // - so every call carried a shield telling the person it was
                        // private while the media went through the server in the
                        // clear. Privacy is the one claim an app must not make ahead
                        // of itself.
                        //
                        // `e2ee` is the server's own word for it, parsed since the
                        // call models were written and never once read. It is false
                        // until the key exchange exists, which is exactly when this
                        // should start appearing by itself.
                        if (call?.e2ee == true) {
                            Spacer(Modifier.height(d.gapXs))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    IqIcons.Shield, null,
                                    Modifier.size(d.iconXs),
                                    tint = Color.White.copy(alpha = 0.75f)
                                )
                                Spacer(Modifier.width(d.gapXs))
                                Text(
                                    "End-to-end encrypted",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.78f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                if (looks) {
                    CameraLookPanel(
                        onDone = { looks = false },
                        modifier = Modifier
                            .padding(horizontal = d.gutterTight)
                            .padding(top = d.gapMd)
                            .fillMaxWidth()
                            .widthIn(max = d.contentMaxWidth)
                    )
                }
            }
        }

        /*
             * The end-call button is taller than the bar and shares a centreline
             * with the small buttons, so the pill has to bulge up around it.
             * `bulge` is the strip above the flat edge that the hump lives in.
             */
        val ctrl = d.callControl
        val endBtn = d.callEnd
        val padTop = d.scaled(10)
        val bulge = d.scaled(11)
        val pillShape = HumpedBarShape(
            humpWidth = d.scaled(92),
            humpHeight = bulge
        )

        AnimatedVisibility(
            visible = chrome,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        ) {
            Row(
                Modifier
                    .padding(start = d.gapMd, end = d.gapMd, bottom = d.gapXl)
                    .fillMaxWidth()
                    .widthIn(max = d.contentMaxWidth)
                    .clip(pillShape)
                    .background(Color.Black.copy(alpha = 0.62f), pillShape)
                    .padding(
                        start = d.gapSm, end = d.gapSm,
                        top = bulge + padTop, bottom = padTop
                    ),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Top
            ) {
                VideoControl(
                    icon = if (!CallEngine.micOn) IqIcons.MicOff else IqIcons.Mic,
                    label = "Mute",
                    enabled = CallEngine.micOn,
                    onClick = { CallEngine.toggleMic() },
                    modifier = Modifier.weight(1f)
                )
                VideoControl(
                    icon = CallAudio.route.icon,
                    label = "Speaker",
                    enabled = CallAudio.route != CallAudio.Route.EARPIECE,
                    onClick = { CallEngine.cycleRoute() },
                    modifier = Modifier.weight(1f)
                )
                // Height matches the small buttons so it does not stretch the bar;
                // requiredSize lets the larger circle overflow into the hump.
                Box(
                    Modifier
                        .weight(1.3f)
                        .height(ctrl),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .requiredSize(endBtn)
                            .clip(CircleShape)
                            .background(Crimson)
                            .clickable(onClick = onEnd),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            IqIcons.CallEnd, "End call",
                            Modifier
                                .size(d.iconLg)
                                .rotate(133f),
                            tint = Color.White
                        )
                    }
                }
                // Outline when the camera is off, the way Mute carries MicOff.
                // `enabled` only dims the icon, so a greyed camera over an
                // unchanged icon was the one state that read as "you cannot press
                // this" - which was exactly what it was not.
                VideoControl(
                    icon = if (CallEngine.cameraOn) IqIcons.Videocam
                    else IqIcons.VideocamOutline,
                    label = "Video",
                    enabled = CallEngine.cameraOn,
                    onClick = toggleCamera,
                    modifier = Modifier.weight(1f)
                )
                VideoControl(
                    icon = IqIcons.FlipCamera,
                    label = "Flip",
                    onClick = { CallEngine.flipCamera() },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** A share of the width, with a floor and a ceiling, so it suits any screen. */
private fun selfWidthIn(space: Dp): Dp = (space * 0.27f).coerceIn(88.dp, 150.dp)

/**
 * Your own camera, placed wherever it is put down.
 *
 * It used to sit in the header, which decided two things for the person holding
 * the phone: that it belonged in the top right, and that it went away whenever the
 * header did. Both are wrong on a call. It covers part of the picture by its
 * nature, so which part has to be theirs to choose, and the face you are checking
 * is the last thing to hide when the controls go.
 *
 * Dragged by a finger, and kept inside the screen while it moves rather than
 * caught at the end: a corner that is allowed off the edge and then pulled back
 * reads as a mistake being corrected. The settle is there for the other case -
 * the screen itself changing size, on rotation or in a split window - where the
 * place it was put down may no longer be on the screen at all.
 *
 * It grows very slightly while held. A drag with no answer from the thing being
 * dragged feels like dragging the screen.
 */
@Composable
private fun BoxScope.SelfView(
    me: Person,
    chrome: Boolean,
    onTap: () -> Unit,
    wide: Dp,
    tall: Dp
) {
    val d = IqTheme.dimens
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val bars = WindowInsets.systemBars

    val sizeDp = selfWidthIn(wide)
    val across = with(density) { wide.toPx() }
    val down = with(density) { tall.toPx() }
    val mine = with(density) { sizeDp.toPx() }
    val high = mine / RATIO
    val edge = with(density) { d.gutterTight.toPx() }
    // Clear of the notch at the top and of the gesture bar at the bottom: a
    // self-view under either is one that cannot be picked up again.
    val sky = bars.getTop(density) + with(density) { d.gapMd.toPx() }
    val floor = bars.getBottom(density) + edge

    fun inside(at: Offset) = Offset(
        at.x.coerceIn(edge, (across - mine - edge).coerceAtLeast(edge)),
        at.y.coerceIn(sky, (down - high - floor).coerceAtLeast(sky))
    )

    // Where it starts: the top right, which is where it always was.
    val spot = remember { Animatable(Offset(across - mine - edge, sky), Offset.VectorConverter) }
    var held by remember { mutableStateOf(false) }
    val grab by animateFloatAsState(if (held) 1.06f else 1f, label = "selfGrab")

    LaunchedEffect(across, down) {
        spot.animateTo(inside(spot.value), spring(stiffness = Spring.StiffnessMediumLow))
    }

    Box(
        Modifier
            .align(Alignment.TopStart)
            .offset { IntOffset(spot.value.x.roundToInt(), spot.value.y.roundToInt()) }
            .graphicsLayer { scaleX = grab; scaleY = grab }
            .width(sizeDp)
            .aspectRatio(RATIO)
            .clip(RoundedCornerShape(d.radiusLg))
            .background(avatarBrush(me.seed))
            .pointerInput(across, down) {
                detectDragGestures(
                    onDragStart = { held = true },
                    onDragEnd = {
                        held = false
                        scope.launch {
                            spot.animateTo(
                                inside(spot.value),
                                spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)
                            )
                        }
                    },
                    onDragCancel = { held = false }
                ) { change, drag ->
                    // Taken here, so the tap that hides the controls does not also
                    // fire at the end of every drag.
                    change.consume()
                    scope.launch { spot.snapTo(inside(spot.value + drag)) }
                }
            }
            .pointerInput(Unit) { detectTapGestures { onTap() } }
    ) {
        // Mirrored, because this is the one picture that behaves like a mirror:
        // you move left and the reflection moves left.
        CallVideo(
            track = CallEngine.localVideo,
            modifier = Modifier.fillMaxSize(),
            mirror = true
        )
        // The flip button is a control, so it goes with the other controls.
        AnimatedVisibility(
            visible = chrome,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = d.gapMd)
        ) {
            Box(
                Modifier
                    .size(d.scaled(38))
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable { CallEngine.flipCamera() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    IqIcons.FlipCamera, "Switch camera",
                    Modifier.size(d.iconSm), tint = Color.White
                )
            }
        }
    }
}

/** Width over height, the shape a phone camera gives held upright. */
private const val RATIO = 0.67f

/**
 * Brightness and smoothing, over the call.
 *
 * Sliders rather than a preset or two: what the room's light needs is not a
 * setting somebody can be given, and the picture is right there behind the panel
 * while it is being moved - which is the only honest way to choose either of
 * these. Both start at zero, where the filter costs nothing at all.
 *
 * What is changed here changes what the other phone receives, because the filter
 * sits between the camera and the track rather than between the track and the
 * screen. There is deliberately no "preview only" mode: a picture that flattered
 * one side and not the other would be a surprise, not a feature.
 */
@Composable
private fun CameraLookPanel(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val d = IqTheme.dimens
    val context = LocalContext.current
    Column(
        modifier
            .clip(RoundedCornerShape(d.radiusXl))
            .background(Color.Black.copy(alpha = 0.62f))
            .padding(horizontal = d.gapLg, vertical = d.gapMd)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Camera look",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            Text(
                "Done",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(d.radiusSm))
                    .clickable(onClick = onDone)
                    .padding(horizontal = d.gapSm, vertical = d.gapXs)
            )
        }
        LookSlider(
            label = "Brightness",
            value = CameraLook.brightness,
            onChange = { CameraLook.setBrightness(it, context) }
        )
        LookSlider(
            label = "Smooth",
            value = CameraLook.smooth,
            onChange = { CameraLook.setSmooth(it, context) }
        )
    }
}

@Composable
private fun LookSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    val d = IqTheme.dimens
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.width(d.scaled(86))
        )
        Slider(
            value = value,
            onValueChange = onChange,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Crimson,
                inactiveTrackColor = Color.White.copy(alpha = 0.25f)
            ),
            modifier = Modifier.weight(1f)
        )
        Text(
            "${(value * 100).toInt()}",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.width(d.scaled(28))
        )
    }
}

@Composable
private fun VideoControl(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val d = IqTheme.dimens
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(d.callControl)
                .clip(CircleShape)
                .background(Color.White)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon, label,
                Modifier.size(d.iconSm),
                tint = if (enabled) Color.Black else InkFaint
            )
        }
        Spacer(Modifier.height(d.gapSm))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Normal,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

internal fun formatDuration(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%02d:%02d".format(m, s)
}
