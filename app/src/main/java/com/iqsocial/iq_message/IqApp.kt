package com.iqsocial.iq_message

import android.net.Uri
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.iqsocial.iq_message.data.auth.SessionStore
import com.iqsocial.iq_message.data.call.CallAudio
import com.iqsocial.iq_message.data.call.CallCenter
import com.iqsocial.iq_message.data.call.CallEngine
import com.iqsocial.iq_message.data.call.CallService
import com.iqsocial.iq_message.data.call.CallWindow
import com.iqsocial.iq_message.data.call.RingingNotification
import com.iqsocial.iq_message.data.chat.ChatMessages
import com.iqsocial.iq_message.data.chat.Outbox
import com.iqsocial.iq_message.data.chat.OutboxWork
import com.iqsocial.iq_message.data.chat.ChatRepository
import com.iqsocial.iq_message.data.chat.KnownPeople
import com.iqsocial.iq_message.data.contacts.ContactsRepository
import com.iqsocial.iq_message.data.media.MediaCache
import com.iqsocial.iq_message.data.media.RemoteImage
import com.iqsocial.iq_message.data.media.UploadQueue
import com.iqsocial.iq_message.data.net.Reachability
import com.iqsocial.iq_message.data.media.VoiceWaveforms
import com.iqsocial.iq_message.data.overlay.ChatHeads
import com.iqsocial.iq_message.data.profile.MeRepository
import com.iqsocial.iq_message.data.profile.PeerPrivacy
import com.iqsocial.iq_message.data.profile.PresenceRepository
import com.iqsocial.iq_message.data.profile.SettingsRepository
import com.iqsocial.iq_message.data.privacy.ScreenshotAlerts
import com.iqsocial.iq_message.data.push.IqMessagingService
import com.iqsocial.iq_message.data.push.PushTokens
import com.iqsocial.iq_message.data.push.MessageNotifications
import com.iqsocial.iq_message.data.push.SocketAlerts
import com.iqsocial.iq_message.data.remote.CallStatus
import com.iqsocial.iq_message.data.remote.CallType
import com.iqsocial.iq_message.data.remote.ChatSocket
import com.iqsocial.iq_message.ui.components.CallPermissions
import com.iqsocial.iq_message.ui.components.ChatHeadsGate
import com.iqsocial.iq_message.ui.components.ContactsAccessGate
import com.iqsocial.iq_message.ui.components.FullScreenCallsGate
import com.iqsocial.iq_message.ui.components.HomeTab
import com.iqsocial.iq_message.ui.components.IqBottomBar
import com.iqsocial.iq_message.ui.components.NotificationsGate
import com.iqsocial.iq_message.ui.screens.AudioCallScreen
import com.iqsocial.iq_message.ui.screens.CallPipScreen
import com.iqsocial.iq_message.ui.screens.CallProblem
import com.iqsocial.iq_message.ui.screens.CallsScreen
import com.iqsocial.iq_message.ui.screens.ChatScreen
import com.iqsocial.iq_message.ui.screens.ChatsScreen
import com.iqsocial.iq_message.ui.screens.EditProfileScreen
import com.iqsocial.iq_message.data.remote.ProfileImage
import com.iqsocial.iq_message.ui.screens.ForgotPasswordScreen
import com.iqsocial.iq_message.ui.screens.ImageCropScreen
import com.iqsocial.iq_message.ui.screens.NotificationsScreen
import com.iqsocial.iq_message.ui.screens.PrivacyScreen
import com.iqsocial.iq_message.ui.screens.IncomingCallScreen
import com.iqsocial.iq_message.ui.screens.LoginScreen
import com.iqsocial.iq_message.ui.screens.OngoingCallBar
import com.iqsocial.iq_message.ui.screens.PeopleScreen
import com.iqsocial.iq_message.ui.screens.PeopleSearchScreen
import com.iqsocial.iq_message.ui.screens.PersonScreen
import com.iqsocial.iq_message.ui.screens.QrScreen
import com.iqsocial.iq_message.ui.screens.ReelsScreen
import com.iqsocial.iq_message.ui.screens.ChatSearchScreen
import com.iqsocial.iq_message.data.reels.ReelsFeed
import com.iqsocial.iq_message.ui.screens.ProfileScreen
import com.iqsocial.iq_message.ui.screens.RewardsScreen
import com.iqsocial.iq_message.ui.screens.SignupScreen
import com.iqsocial.iq_message.ui.screens.VideoCallScreen
import com.iqsocial.iq_message.ui.screens.WaitingCallBar
import com.iqsocial.iq_message.ui.screens.rememberAuthRepository
import com.iqsocial.iq_message.ui.theme.CallBlack
import kotlinx.coroutines.launch
/**
 * Destinations in the prototype.
 *
 * Navigation is a plain in-memory back stack rather than navigation-compose: the flow
 * is small and fixed, and this keeps the module free of extra dependencies.
 */
/**
 * How long a call screen takes to become the next one.
 *
 * Short on purpose. It is covering a change of state rather than a change of place,
 * and a leisurely fade over "Connected" reads as the app being slow to notice.
 */
private const val CALL_FADE_MS = 160

sealed interface Route {
    data object Login : Route
    data object Signup : Route
    data object ForgotPassword : Route
    data object Home : Route
    data object PeopleSearch : Route
    data class Chat(val conversationId: String) : Route

    /**
     * The settings pages that change something.
     *
     * Screens rather than sheets over the profile tab: each one edits the account,
     * and the back gesture, the title and the full height are worth more than the
     * half-screen a popup leaves. They carry nothing, because what they edit is
     * held by [MeRepository] and would go stale in a route.
     */
    /** This account's QR code, and reading somebody else's. */
    data object QrCode : Route

    /** Searching conversations and messages, opened from the chat list's icon. */
    data object ChatSearch : Route

    /** The reels viewer, opened at the reel tapped in the strip. */
    data class Reels(val start: Int) : Route

    data object EditProfile : Route
    data object Privacy : Route
    data object Notifications : Route

    /**
     * Cropping a picture that has just been chosen, before it goes anywhere.
     *
     * The only settings route that carries anything, and it carries the least it
     * can: the picked `Uri` as text and which of the two pictures it is for. The
     * picture itself is not in the route - a bitmap in a navigation stack is a
     * bitmap kept alive by the stack.
     */
    data class CropPicture(val uri: String, val cover: Boolean) : Route

    /**
     * Somebody else's profile.
     *
     * Carries the name and picture the screen that opened it already had, so the
     * profile is drawn the instant it opens rather than after a request. They are
     * a starting point, not the answer: `GET /v1/people/{uid}` replaces them.
     */
    data class PersonProfile(
        val uid: String,
        val name: String = "",
        val photo: String = ""
    ) : Route
    /**
     * The call screens carry nothing.
     *
     * There is one call at a time and [CallCenter] holds it, so a route parameter
     * would be a second copy of the same fact - and the one that goes stale, because
     * a call changes under the screen showing it.
     */
    data object IncomingCall : Route
    data object VideoCall : Route
    data object AudioCall : Route
}

/**
 * Whether this route is one of the call screens.
 *
 * Asked by the two things that have to agree about it: what the move between screens
 * looks like, and what colour is behind it while it happens.
 */
private val Route.isCall: Boolean
    get() = this is Route.IncomingCall || this is Route.VideoCall || this is Route.AudioCall

private class Navigator(initial: Route) {
    val stack: SnapshotStateList<Route> = mutableStateListOf(initial)
    val current: Route get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    /**
     * Which way the last move went.
     *
     * The stack cannot be asked this afterwards - by the time the animation is being
     * chosen the move has happened, and two routes say nothing about which of them
     * was on top a moment ago. So each move records its own direction.
     *
     * Set before the stack changes, so that it is already true when the change wakes
     * the composition that reads it.
     */
    var goingBack by mutableStateOf(false)
        private set

    fun push(route: Route) {
        goingBack = false
        stack.add(route)
    }

    fun pop() {
        if (!canGoBack) return
        goingBack = true
        stack.removeAt(stack.lastIndex)
    }

    /**
     * Swaps what is on top, rather than stacking on it.
     *
     * For a screen that becomes another screen - a ringing call being answered -
     * where pushing would leave the one it replaced underneath, and the back gesture
     * would walk into a call that is no longer ringing.
     */
    fun replace(route: Route) {
        goingBack = false
        if (stack.isEmpty()) stack.add(route) else stack[stack.lastIndex] = route
    }

    /** Used when signing in: the onboarding screens must not stay on the stack. */
    fun replaceAll(route: Route) {
        goingBack = false
        stack.clear()
        stack.add(route)
    }
}

@Composable
fun IqApp(
    /** A conversation a notification asked for, or null. */
    openConversation: String? = null,
    /** Called once it has been opened, so the same tap is not acted on twice. */
    onConversationOpened: () -> Unit = {},
    /** Set when the running call's notification was tapped. */
    openCall: Boolean = false,
    /** Called once the call screen is up, for the same reason. */
    onCallOpened: () -> Unit = {}
) {
    // A stored token skips straight past the auth screens on relaunch.
    val auth = rememberAuthRepository()
    val nav = remember {
        Navigator(if (auth.isSignedIn()) Route.Home else Route.Login)
    }
    var tab by rememberSaveable { mutableStateOf(HomeTab.Chats) }

    // Realtime is a property of being signed in, not of any one screen, so it is
    // opened here and torn down on log-out.
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = remember { SessionStore(context) }
    // Photos survive eviction from memory here, so scrolling back to one does not
    // fetch it again.
    LaunchedEffect(Unit) {
        MediaCache.cacheDir = context.applicationContext.cacheDir
        // Faces and link pictures, kept the same way, so a cold start with no
        // connection draws them instead of initials.
        RemoteImage.cacheDir = context.applicationContext.cacheDir
        VoiceWaveforms.init(context)
        CallEngine.init(context)
        CallCenter.init(context)
        CallService.ensureChannel(context)
        RingingNotification.ensureChannel(context)
    }

    /**
     * A ringing call takes the screen, wherever you were.
     *
     * Driven from [CallCenter] rather than from a navigation call, because a call can
     * arrive with nothing open - woken by a push - and has to put itself on screen.
     * It leaves the same way: when the call ends the route is popped, whichever side
     * ended it.
     */
    /**
     * A call the reader has deliberately stepped away from.
     *
     * Without it the routing effect below is a trap: it sends you to the call screen
     * whenever there is a call and you are not on it, so leaving by any route put
     * you straight back. Held by id, so the next call still arrives in front.
     */
    var setAside by remember { mutableStateOf<String?>(null) }

    val live = CallCenter.call
    // Asked for while the call is already connecting, not before it. Waiting on a
    // dialog first would put a silence at the front of every call.
    if (live != null) CallPermissions()
    LaunchedEffect(live?.id, live?.status) {
        val onCall = nav.current is Route.IncomingCall ||
            nav.current is Route.AudioCall ||
            nav.current is Route.VideoCall

        /**
         * Which call screen this call should be on, from what it is doing.
         *
         * Worked out rather than decided once when the call arrived, because it
         * changes underneath: a ringing call that is answered stops being a ringing
         * call. The effect used to stop at "already on a call screen" and do nothing
         * more - so answering left the ringing screen up, with its Accept and
         * Decline buttons, over a call that was already connected.
         */
        val wanted: Route? = when {
            live == null -> null
            live.isIncoming && live.status == CallStatus.RINGING -> Route.IncomingCall
            live.type == CallType.VIDEO -> Route.VideoCall
            else -> Route.AudioCall
        }

        // A ringing call cannot be answered in a thumbnail: a floating window passes
        // no touch to what is drawn in it, so Accept there is a picture of a button.
        // One that arrives while the window is up takes the screen back first, and
        // the routing below then puts it on it.
        if (wanted == Route.IncomingCall && CallWindow.inPip) {
            context.activity()?.leaveCallPip()
        }

        when {
            wanted == null -> {
                // Out of the thumbnail first, and whatever is in it - not only when
                // a call screen was up. A call can be put aside and then followed
                // out of the app, which leaves a chat in the window; when the call
                // ends there is then nothing left to float but the app itself.
                if (CallWindow.inPip) context.activity()?.leaveCallPip()
                if (onCall) nav.pop()
            }

            nav.current == wanted -> Unit

            // Put aside on purpose, so it stays put aside. The bar at the top is
            // what keeps it from being lost.
            live != null && live.id == setAside && !onCall -> Unit

            // Already on a call screen, so this is one becoming another: swapped,
            // not stacked. Pushing would leave the ringing screen underneath, and
            // one back gesture during the call would land on it.
            onCall -> nav.replace(wanted)

            else -> nav.push(wanted)
        }
    }
    /**
     * Anything this phone decided while it could not reach the server.
     *
     * Tried when the app starts and again whenever the socket comes back, because
     * those are the two moments the answer to "can we reach it now" changes. A
     * message interrupted mid-send is put back in the queue first - the send died
     * with the process, and nothing else is ever going to finish it.
     */
    LaunchedEffect(session.token, ChatSocket.connected) {
        val auth = session.token ?: return@LaunchedEffect
        if (auth.isBlank()) return@LaunchedEffect
        ChatMessages.recoverInterrupted(context)
        Outbox.drain(context)
        // Files are their own queue, because what has to be kept for them is bytes
        // rather than a decision - but they are picked up at the same two moments.
        UploadQueue.resume(context, auth)
    }

    /**
     * The network itself, asked directly.
     *
     * Beside the effect above rather than instead of it, because the two answer
     * different questions and each catches what the other misses: the socket coming
     * back means the server is reachable, and this means the phone is. A handover
     * from Wi-Fi to mobile is noticed here at once and by the socket twenty seconds
     * later; a server that went down and came up is noticed by the socket and not
     * here at all.
     *
     * Registered against the application, so it keeps watching while no screen is
     * looking - which is most of when it matters.
     */
    DisposableEffect(Unit) {
        Reachability.start(context) {
            val auth = SessionStore(context).token
            if (!auth.isNullOrBlank()) {
                Outbox.drain(context)
                UploadQueue.resume(context, auth)
            }
        }
        onDispose { }
    }

    LaunchedEffect(session.token) {
        session.token?.let(ChatSocket::connect)
        // Started with the socket because it listens to the socket, and torn down
        // with it on sign-out. It announces every message while the app is running,
        // side by side with the push - whichever of the two arrives first is the
        // one that shows it.
        session.token?.let { SocketAlerts.start(context) }
    }

    /**
     * Sends the FCM token to `POST /v1/devices` **every time the app is opened**.
     *
     * On the lifecycle rather than in a `LaunchedEffect`, which was the mistake in
     * the first pass: a `LaunchedEffect` runs once per composition, so it covered a
     * cold start and a sign-in but not the ordinary case of leaving the app and
     * coming back. The composition survives that; `ON_START` does not.
     *
     * That gap matters because Firebase can reissue a token while the app is away -
     * a restore to a new device, data cleared, a reinstall - and `onNewToken` only
     * fires if there is an app running to hear it. Asking Firebase afresh on every
     * foreground is what closes it.
     *
     * The call is deliberately unconditional. The server documents it as safe to
     * repeat, and remembering what was last sent would mean trusting this app's
     * memory over Firebase's answer - which is the thing that goes stale.
     */
    /**
     * The account's own settings, at launch rather than when Settings is opened.
     *
     * One of them is `allow_screenshot`, which decides whether this phone locks its
     * own window - and until this effect existed, nothing fetched the settings
     * until the profile tab was visited. On a fresh launch the repository held
     * null, null reads as the permissive default, and a reader who had turned
     * screenshots off found them working again every time the app was restarted.
     */
    LaunchedEffect(session.token) {
        if (!session.token.isNullOrBlank()) SettingsRepository.load(session.token)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, session.token) {
        fun send() {
            if (session.token.isNullOrBlank()) return
            IqMessagingService.ensureChannels(context)
            PushTokens.register(context)
            // And anything that happened while this phone was not listening. A
            // push is not a delivery guarantee - the phone can be off, out of
            // signal, or have notifications turned off at the system level - so
            // the list is read when the app comes forward and anything newer
            // than the last alert shown is shown now.
            scope.launch { runCatching { ScreenshotAlerts.catchUp(context) } }
        }

        // Signing in happens *after* ON_START, so waiting for the next one would
        // leave a freshly signed-in phone unregistered until it had been left and
        // come back to. The duplicate this can cause on a cold start is harmless -
        // the route is documented as safe to repeat.
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            CallWindow.appInFront = true
            send()
        }

        val observer = LifecycleEventObserver { _, event ->
            // Which ringer owns the sound for an incoming call depends on this.
            if (event == Lifecycle.Event.ON_START) CallWindow.appInFront = true
            if (event == Lifecycle.Event.ON_STOP) CallWindow.appInFront = false
            if (event == Lifecycle.Event.ON_START) {
                send()
                // Coming back is the moment to find out whether the call this side
                // still thinks is running actually is. The push spec says a
                // `call:ended` can be late or lost, and a phone that was asleep
                // through one would otherwise hold a call that ended long ago -
                // ringing, or showing controls for nothing.
                if (CallCenter.call != null) CallCenter.resync(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    /**
     * Whether this screen is actually in front of somebody.
     *
     * The prompts below are dialogs, and a dialog has to attach to the activity's
     * window. Launched behind the lock screen - which is what tapping a notification
     * on a locked phone does - the activity exists but has no window to give, and
     * putting one up there throws `BadTokenException` and takes the app down with
     * it. That was seen once, on the first launch after an install.
     *
     * Waiting is the right answer regardless of the crash: a permission prompt shown
     * behind a locked screen is one nobody is there to read, and it would be marked
     * as asked.
     */
    val lifecycle by lifecycleOwner.lifecycle.currentStateAsState()
    val inFront = lifecycle.isAtLeast(Lifecycle.State.RESUMED)

    // Asked for after sign-in rather than at launch, where it would be the first
    // thing a stranger sees. Nothing depends on the answer.
    if (auth.isSignedIn() && inFront) NotificationsGate()
    // After it, not beside it: notifications come first, and two permission
    // dialogs at once is a wall rather than a question.
    if (auth.isSignedIn() && inFront) FullScreenCallsGate()
    if (auth.isSignedIn() && inFront) ChatHeadsGate()


    /**
     * Opens what a notification was about.
     *
     * Only while signed in: a tap that arrives on the login screen would otherwise
     * push a thread nobody can load behind it. The conversation is cleared whether
     * or not it was opened, so a stale id does not sit waiting to fire at sign-in.
     */
    LaunchedEffect(openConversation) {
        val id = openConversation ?: return@LaunchedEffect
        if (auth.isSignedIn() && nav.current != Route.Chat(id)) nav.push(Route.Chat(id))
        onConversationOpened()
    }

    /**
     * The running call's notification was tapped: show the call.
     *
     * Keyed on the call as well as on the tap, because the two can arrive in either
     * order. A notification tapped while the app was not running starts it, and the
     * call this phone is in may not be known yet when the first composition runs -
     * so the request waits here until there is a call to put on screen, rather than
     * being spent on nothing.
     *
     * Clearing [setAside] is what makes it stick: without it the call is still
     * marked as one the person stepped away from, and the routing effect would read
     * the screen as unwanted and take it away again.
     */
    LaunchedEffect(openCall, live?.id, live?.type) {
        if (!openCall) return@LaunchedEffect
        val call = live ?: return@LaunchedEffect
        setAside = null
        val wanted = if (call.type == CallType.VIDEO) Route.VideoCall else Route.AudioCall
        if (nav.current != wanted) nav.push(wanted)
        onCallOpened()
    }

    /**
     * Which route is up, for the half of the window logic the activity owns.
     *
     * `onUserLeaveHint` fires for every way out of the app and the activity has no
     * idea what is composed in it, so without this, pressing home from the chat list
     * would put the chat list in a floating window.
     */
    val minimisable = nav.current is Route.AudioCall || nav.current is Route.VideoCall
    // A picture from either end, whichever route the call started on.
    val hasPicture = CallEngine.cameraOn || CallEngine.remoteFeeds.any { it.video != null }
    LaunchedEffect(minimisable, nav.current, hasPicture) {
        // The floating window's shape follows this too: a call that grew a picture
        // should not keep the square window a voice call was given.
        CallWindow.showing(minimisable, nav.current is Route.VideoCall || hasPicture)
    }
    /**
     * The other half of what the activity needs: that there is a call at all.
     *
     * Kept apart from the route above because the two disagree exactly when it
     * matters - a call put aside is running with a chat in front of it. A call still
     * ringing to be answered is left out: that one belongs on a full screen or in a
     * notification, and a thumbnail of it offers no way to answer.
     */
    LaunchedEffect(live?.id, live?.status, live?.isIncoming) {
        CallWindow.inCall(
            live != null && !(live.isIncoming && live.status == CallStatus.RINGING)
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            CallWindow.showing(false)
            CallWindow.inCall(false)
        }
    }

    /**
     * Keeps the floating window's buttons honest, and arms it while there is a call.
     *
     * The actions are a snapshot taken when they were built, so muting from the
     * window would otherwise leave a "Mute" button over an already-muted
     * microphone. Reading every flag here is what makes this re-run when any of them
     * changes - including when the change came from the window's own buttons.
     *
     * No longer only while the window is up, because this is also what puts it up:
     * from Android 12 a window armed in advance is shrunk by the system as part of
     * the gesture out of the app, rather than appearing in the corner after it. That
     * has to be set while the call is still on screen - and unset the moment it
     * ends, which is why this runs with no call too and sends `false`.
     */
    LaunchedEffect(
        CallWindow.inPip,
        CallWindow.callOnScreen,
        CallWindow.callRunning,
        CallWindow.video,
        CallEngine.micOn,
        CallAudio.route
    ) {
        context.activity()?.updateCallPipParams(
            autoEnter = CallWindow.callOnScreen || CallWindow.callRunning
        )
    }

    /**
     * Back, during a call, never leaves the call behind.
     *
     * Two cases, and they are the same promise answered twice. On the call screen it
     * floats the call and steps out of that screen. Anywhere else in the app, with
     * the call running behind the strip at the top, it floats the call rather than
     * letting back carry the app out of sight - which is the home button's behaviour,
     * reached by the other way out.
     *
     * Both halves of the first, and the second is the one that is easy to leave out.
     * Shrinking
     * alone keeps the call in the corner but leaves the call screen standing in the
     * app behind it, so coming back - by the window, by the app's own icon - lands
     * on a call screen nobody asked to see again. The step back is what makes the
     * window a way of *putting the call aside*: behind it the app is where it was
     * before the call, and [OngoingCallBar] is how the call is picked up again.
     *
     * So the pop happens whether or not the window went up. On a phone that cannot
     * float one - Android 7, or a device without the feature - this is exactly what
     * the minimise button does, which is the right thing to degrade to.
     *
     * The window itself is asked for **first**. The system takes the shrinking
     * activity as it finds it, and finding it already showing the screen underneath
     * would animate that into the corner instead of the call.
     */
    // Enabled during a call even with nothing to go back to. A call answered from a
    // notification on a cold start can be the only thing on the stack, and left to
    // the system that back would finish the activity - which is the one exit that
    // never reaches `onUserLeaveHint` either, so the call would go with no window
    // and no screen.
    BackHandler(enabled = nav.canGoBack || CallWindow.callRunning) {
        if (minimisable && live != null) {
            context.activity()?.enterCallPip()
            // Put aside by name, so the routing effect reads this as a call the
            // person stepped away from rather than one it has yet to show.
            setAside = live?.id
            nav.pop()
            return@BackHandler
        }

        // Back out of the app, with a call still running behind the strip at the
        // top. Not the call screen - that is the case above - but the one after it:
        // the call was put aside, the person carried on reading, and now they are
        // leaving. Which is the home button's case exactly, and the home button
        // floats the call, so this does too rather than letting the call go off
        // screen with nothing to bring it back.
        //
        // Only when there is nowhere left to go back to. Back inside the app is
        // still back inside the app: during a call a chat still returns to the
        // list, or the gesture would stop being navigation for as long as somebody
        // was talking.
        if (!nav.canGoBack && CallWindow.callRunning) {
            // Failing that - Android 7, or a phone with no picture-in-picture - the
            // app still goes to the background, because that is what back meant.
            // What must not happen is nothing at all: a gesture that does nothing
            // reads as an app that has frozen mid-call.
            if (context.activity()?.enterCallPip() != true) {
                context.activity()?.moveTaskToBack(true)
            }
            return@BackHandler
        }

        nav.pop()
    }

    /**
     * Whether the return-to-call strip is up.
     *
     * Asked once because two things need the answer: whether to draw the strip, and
     * whether the column it sits in should take the status bar for it.
     *
     * A call that is still ringing counts, as long as it is one being *placed*. The
     * test used to be "not ringing", which quietly meant that stepping out of an
     * outgoing call before it was answered left nothing at all on screen: the call
     * was dialling, the other phone was ringing, and this one showed no sign of it.
     * The strip already knows what to say in that state - it reads "Calling" until
     * there is a duration to show instead.
     *
     * What stays out is an *incoming* call still ringing. That one is not a call in
     * progress to be returned to, it is a question waiting for an answer, and it has
     * a screen and a notification of its own to ask it with.
     */
    val barUp = live != null && !minimisable && !CallWindow.inPip &&
        !(live.isIncoming && live.status == CallStatus.RINGING)

    /**
     * The strip above the screen, in space of its own.
     *
     * It used to be drawn after the screen and over it - this composable has no
     * container, so its children stack - which put a red bar across whatever the
     * screen keeps at the top: a title, a search field, the row of tabs. The call
     * was visible and the screen under it was not.
     *
     * A column instead, so the strip pushes the screen down by exactly its own
     * height. The dialog and the waiting-call bar stay outside it, because those
     * are meant to be over everything.
     */
    Column(
        Modifier
            .fillMaxSize()
            // Before the inset below, so it covers the status bar too. Nothing was
            // painting that strip once the column moved out from under it, and the
            // bar is a rounded pill with a margin - so the window's own background
            // showed through around it.
            .background(Color.White)
    ) {
        live?.let { running ->
            if (barUp) {
                OngoingCallBar(running) {
                    setAside = null
                    nav.push(
                        if (running.type == CallType.VIDEO) Route.VideoCall
                        else Route.AudioCall
                    )
                }
            }
        }

        AnimatedContent(
            targetState = nav.current,
            transitionSpec = {
                // A call does not slide.
                //
                // The horizontal pair below is a navigation gesture - one screen
                // pushing the last one aside - and the call screens are not that.
                // Answering does not go anywhere: it is the same call, on the same
                // phone, a moment later. Slid, the two black screens moved past each
                // other at different speeds and left a band of the container
                // uncovered between them, which is the jump that showed.
                //
                // Crossed over instead, both screens filling the same space the
                // whole way, so there is nothing to see between them.
                if (initialState.isCall || targetState.isCall) {
                    fadeIn(tween(CALL_FADE_MS)) togetherWith fadeOut(tween(CALL_FADE_MS))
                } else if (nav.goingBack) {
                    // Going back, run exactly backwards.
                    //
                    // Both directions used to play the forward move, so leaving a
                    // screen looked identical to entering one: the screen being
                    // returned to slid in from the right, as though it were new,
                    // while the one being left went the way it had come. That is
                    // what reads as reversed - the picture says "further in" while
                    // the gesture meant "back out".
                    //
                    // The offsets are the forward pair with their roles swapped, so
                    // a push and the pop that undoes it are the same movement run
                    // each way: what came from the right leaves to the right, and
                    // what stepped aside steps back.
                    (slideInHorizontally { -it / 12 } + fadeIn()) togetherWith
                        (slideOutHorizontally { it / 6 } + fadeOut())
                } else {
                    (slideInHorizontally { it / 6 } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it / 12 } + fadeOut())
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                // Consumed rather than padded, and only while the strip is up: the
                // strip has already taken the status bar for itself, so a screen
                // that added its own inset here would leave a status-bar-high gap
                // under the band. With no strip nothing is consumed and every
                // screen draws edge to edge exactly as before.
                .then(
                    if (barUp) Modifier.consumeWindowInsets(WindowInsets.statusBars)
                    else Modifier
                )
                // What shows through while one screen is replacing another, and the
                // other half of why the call screens flashed: they are black on a
                // container painted white, so every frame of the change let white
                // through the parts neither screen had covered yet.
                .background(if (nav.current.isCall) CallBlack else Color.White),
            label = "route"
        ) { route ->
            /**
             * In a thumbnail, what is drawn is the call, not the call screen shrunk:
             * a different, much smaller thing. Asked here rather than inside each
             * call screen so that neither has to know the difference.
             *
             * `isCall` and not simply [CallWindow.inPip], because the route and the
             * window no longer always agree - a call can be put aside and then
             * followed out of the app, which floats a window with a *chat* in it.
             * That case is handled at the end of this file instead, over the screen
             * rather than in place of it; see there for why the difference matters.
             */
            if (CallWindow.inPip && route.isCall) {
                CallPipScreen()
                return@AnimatedContent
            }

            when (route) {
                Route.Login -> LoginScreen(
                    onLoggedIn = { nav.replaceAll(Route.Home) },
                    onSignUp = { nav.push(Route.Signup) },
                    onForgotPassword = { nav.push(Route.ForgotPassword) }
                )

                Route.Signup -> SignupScreen(
                    onRegistered = { nav.replaceAll(Route.Home) },
                    onLogin = { nav.pop() },
                    onBack = { nav.pop() }
                )

                Route.ForgotPassword -> ForgotPasswordScreen(
                    onBack = { nav.pop() },
                    onDone = { nav.replaceAll(Route.Login) }
                )

                Route.Home -> {
                    HomeShell(
                        tab = tab,
                        onTabChange = { tab = it },
                        onOpenChat = { nav.push(Route.Chat(it)) },
                        // Placing a call is not navigation: the call puts its own screen
                        // up once the server has one, through the effect above.
                        onAudioCall = { CallCenter.start(context, it, CallType.AUDIO) },
                        onVideoCall = { CallCenter.start(context, it, CallType.VIDEO) },
                        onNewMessage = { nav.push(Route.PeopleSearch) },
                        onOpenReel = { nav.push(Route.Reels(it)) },
                        onSearch = { nav.push(Route.ChatSearch) },
                        onEditProfile = { nav.push(Route.EditProfile) },
                        onQrCode = { nav.push(Route.QrCode) },
                        onCrop = { picked, kind ->
                            nav.push(
                                Route.CropPicture(
                                    picked.toString(),
                                    kind == ProfileImage.COVER
                                )
                            )
                        },
                        onPrivacy = { nav.push(Route.Privacy) },
                        onNotifications = { nav.push(Route.Notifications) },
                        onLogOut = {
                            // Before the session goes: unregistering needs the token it
                            // is about to discard, and without it this account keeps
                            // ringing a phone that has signed out of it.
                            session.token?.let { PushTokens.unregister(it) }
                            CallCenter.clear()
                            auth.logout()
                            ContactsRepository.clear()
                            MeRepository.clear()
                            // Settings belong to the account, not the install, so the
                            // next person to sign in on this phone must not inherit
                            // them - least of all a notification preview, which decides
                            // how much of their messages reaches the lock screen.
                            SettingsRepository.clear()
                            // Who was online while this account was signed in is
                            // that account's business and nobody else's.
                            PresenceRepository.clear()
                            // And what the people this account talked to allow it
                            // to screenshot. A privacy answer belongs to the pair
                            // of accounts it was asked about, not to the phone.
                            PeerPrivacy.forget()
                            ChatRepository.clear()
                            // And the copy on disk. Everything above empties memory;
                            // this account's messages would otherwise still be here
                            // for whoever signs in next.
                            scope.launch { ChatMessages.wipe(context) }
                            // The system is still holding a request to empty a queue
                            // that is about to be emptied by other means, for an
                            // account that will not be signed in when it runs.
                            OutboxWork.cancel(context)
                            // And the three places an account's content lives
                            // outside that database: pictures decoded to disk,
                            // waveforms this phone measured, and the bytes of
                            // anything still on its way up. Emptying the tables and
                            // leaving these would be tidying the room and locking
                            // the contents in a drawer.
                            MediaCache.clear()
                            RemoteImage.clear()
                            VoiceWaveforms.clear()
                            UploadQueue.clear(context)
                            KnownPeople.clear()
                            ChatHeads.clear()
                            ReelsFeed.clear()
                            ChatSocket.disconnect()
                            SocketAlerts.stop()
                            nav.replaceAll(Route.Login)
                        }
                    )

                    // Signing in is the moment the address book becomes useful, so the
                    // ask happens here rather than behind the People tab. Behind
                    // `inFront` like the other three: it is a dialog, and the chat
                    // list is what a call ringing on a locked phone composes on its
                    // way to the call screen - where a dialog has no window to open
                    // in and crashes rather than appearing.
                    if (inFront) ContactsAccessGate()
                }

                Route.QrCode -> QrScreen(
                    onBack = { nav.pop() },
                    onOpenChat = { conversationId ->
                        // Away first: the code screen has done its errand, and
                        // coming back to it from the chat would be coming back to
                        // a code nobody needs to see again.
                        nav.pop()
                        nav.push(Route.Chat(conversationId))
                    }
                )

                Route.EditProfile -> EditProfileScreen(
                    onBack = { nav.pop() },
                    onCrop = { picked, kind ->
                        nav.push(
                            Route.CropPicture(
                                picked.toString(),
                                kind == ProfileImage.COVER
                            )
                        )
                    }
                )

                is Route.CropPicture -> {
                    val kind =
                        if (route.cover) ProfileImage.COVER else ProfileImage.PICTURE
                    ImageCropScreen(
                        uri = Uri.parse(route.uri),
                        kind = kind,
                        onCancel = { nav.pop() },
                        onDone = { cropped ->
                            // Away first, then the upload: it carries on in the
                            // repository's own scope, and the screen it was started
                            // from has nothing left to say about it.
                            nav.pop()
                            MeRepository.uploadImage(session.token, kind, cropped)
                        }
                    )
                }

                Route.Privacy -> PrivacyScreen(onBack = { nav.pop() })

                Route.Notifications -> NotificationsScreen(onBack = { nav.pop() })

                Route.PeopleSearch -> PeopleSearchScreen(
                    onBack = { nav.pop() },
                    onOpenConversation = { conversationId ->
                        nav.pop()
                        nav.push(Route.Chat(conversationId))
                    }
                )

                Route.ChatSearch -> ChatSearchScreen(
                    onBack = { nav.pop() },
                    onOpenChat = { conversationId ->
                        // The search has done its errand. Leaving it underneath
                        // would make back from the chat land on a box of results
                        // for something already found.
                        nav.pop()
                        nav.push(Route.Chat(conversationId))
                    }
                )

                is Route.Reels -> ReelsScreen(
                    start = route.start,
                    onBack = { nav.pop() },
                    onOpenPerson = { uid, name, photo ->
                        nav.push(Route.PersonProfile(uid, name, photo))
                    }
                )

                is Route.PersonProfile -> PersonScreen(
                    uid = route.uid,
                    knownName = route.name,
                    knownPhoto = route.photo,
                    onBack = { nav.pop() },
                    onAudioCall = { CallCenter.start(context, it, CallType.AUDIO) },
                    onVideoCall = { CallCenter.start(context, it, CallType.VIDEO) }
                )

                is Route.Chat -> ChatScreen(
                    conversationId = route.conversationId,
                    onBack = { nav.pop() },
                    onOpenProfile = { uid ->
                        val known = KnownPeople.of(route.conversationId)
                        nav.push(
                            Route.PersonProfile(
                                uid = uid,
                                name = known?.fullname.orEmpty(),
                                photo = known?.profilePicture.orEmpty()
                            )
                        )
                    },
                    onAudioCall = { CallCenter.start(context, it, CallType.AUDIO) },
                    onVideoCall = { CallCenter.start(context, it, CallType.VIDEO) }
                )

                // All three end themselves through CallCenter; the effect above takes
                // the screen away once there is no call left.
                Route.IncomingCall -> IncomingCallScreen(
                    onDecline = { CallCenter.decline(context) },
                    onAccept = { CallCenter.accept(context) }
                )

                // The window is a thumbnail, so what goes in it is not the call screen
                // shrunk - it is a different, much smaller thing. That choice is made
                // above, for every route, so neither screen here has to know about it.
                Route.VideoCall -> VideoCallScreen(
                    onMinimize = { minimise(nav, { setAside = it }, live?.id) },
                    onEnd = { CallCenter.end(context) }
                )

                // A voice call with a camera on - either end's - is a video call, and
                // is drawn as one. The route does not change: what kind of call it is is
                // the server's word, and this is about what there is to show.
                Route.AudioCall -> if (hasPicture) {
                    VideoCallScreen(
                        onMinimize = { minimise(nav, { setAside = it }, live?.id) },
                        onEnd = { CallCenter.end(context) }
                    )
                } else {
                    AudioCallScreen(
                        onMinimize = { minimise(nav, { setAside = it }, live?.id) },
                        onEnd = { CallCenter.end(context) }
                    )
                }
            }
        }
    }

    /**
     * Why a call did not happen.
     *
     * `CallCenter` has recorded this from the day it was written - a refused start,
     * a failed accept, an invitation the server would not take - and nothing ever
     * read it. So a call that could not be placed looked exactly like one nobody
     * answered: the screen opened, the screen closed, and the reason was held in a
     * field no one asked for.
     *
     * Over everything, because it is the answer to something just attempted.
     *
     * Behind `inFront` with the permission prompts, and for the harder reason. This
     * is a `Dialog`, and a dialog needs the activity's window to hang from. The one
     * moment a call is most likely to fail is the one where there is no window to
     * hang it from: a ringing call on a locked phone, which starts the activity
     * behind the keyguard. Putting a dialog up there throws `BadTokenException` -
     * "token null is not valid; is your activity running?" - and takes the app down,
     * with the call and the ringing notification still on the lock screen.
     *
     * Nothing is lost by waiting. The reason is held in [CallCenter.error] until
     * something reads it, so it is shown the moment there is somebody to show it to.
     */
    if (inFront) {
        CallCenter.error?.let { why ->
            CallProblem(why) { CallCenter.dismissError() }
        }
    }

    // A second call, over the one in progress.
    //
    // Last, and that is what puts it on top: this composable has no container of its
    // own, so its children are stacked in the order they are written. Outside
    // `AnimatedContent` as well, so the bar does not slide in and out with the
    // screen underneath it.
    CallCenter.waiting?.let { held ->
        if (minimisable && !CallWindow.inPip) {
            WaitingCallBar(
                call = held,
                onAccept = { CallCenter.endAndAccept(context) },
                onDecline = { CallCenter.declineWaiting(context) }
            )
        }
    }

    /**
     * A call put aside, in a window, with the app carrying on behind it.
     *
     * The ordinary case rather than the odd one: back during a call steps out of the
     * call screen as it floats the window, so by the time there is a window the
     * route underneath is a chat or the list. The same holds for a call minimised by
     * the button and then followed out of the app.
     *
     * The window belongs to the call, so the call is what fills it - but the screen
     * it shrank from is a chat, and this is drawn **over** that screen rather than
     * in place of it. In place of it would take the chat out of the composition, and
     * everything a screen holds in `remember` - the half-written message most of
     * all - goes with it. A draft lost for pressing home during a call is not a
     * trade worth making for a few centimetres of window nobody can see behind.
     *
     * Last, with the other overlays, which is what puts it on top: this composable
     * has no container of its own, so its children stack in the order written.
     */
    if (CallWindow.inPip && !nav.current.isCall) CallPipScreen()
}

/**
 * What the minimise button does: steps out of the call screen and into the app.
 *
 * The same step back as the gesture, without the floating window. The button is
 * pressed to reach the app - a message to read, something to look up - and the app
 * is right there, so a window over it would only cover what was being reached for.
 * Back means leaving, and leaving is when the call needs something to travel in.
 *
 * The call is not left behind either way: [OngoingCallBar] sits above whatever is
 * underneath and comes straight back here.
 */
private fun minimise(nav: Navigator, setAside: (String?) -> Unit, callId: String?) {
    setAside(callId)
    nav.pop()
}

/**
 * The activity behind a composable's context.
 *
 * `LocalContext.current` is almost never the activity itself - it is a wrapper
 * around it, and more than one in some setups - so casting it straight to Activity
 * returns null and whatever depended on it quietly does nothing. Unwrapping is the
 * only reliable way to ask.
 */
private fun Context.activity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
private fun HomeShell(
    tab: HomeTab,
    onTabChange: (HomeTab) -> Unit,
    onOpenChat: (String) -> Unit,
    onAudioCall: (String) -> Unit,
    onVideoCall: (String) -> Unit,
    onNewMessage: () -> Unit,
    onOpenReel: (Int) -> Unit,
    onSearch: () -> Unit,
    onEditProfile: () -> Unit,
    onQrCode: () -> Unit,
    onCrop: (Uri, ProfileImage) -> Unit,
    onPrivacy: () -> Unit,
    onNotifications: () -> Unit,
    onLogOut: () -> Unit
) {
    // Same reason as the chat screen: the window keeps its full height when the
    // keyboard opens, so the shell insets itself and the tab bar rides above it
    // rather than sitting behind the keys.
    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                HomeTab.Chats -> ChatsScreen(
                    onOpenChat = onOpenChat,
                    onNewMessage = onNewMessage,
                    onOpenReel = onOpenReel,
                    onSearch = onSearch
                )

                HomeTab.Calls -> CallsScreen(
                    onAudioCall = onAudioCall,
                    onVideoCall = onVideoCall
                )

                HomeTab.People -> PeopleScreen(
                    onOpenChat = onOpenChat,
                    onFindPeople = onNewMessage
                )

                HomeTab.Rewards -> RewardsScreen(
                    onOpenChat = onOpenChat,
                    onCall = onAudioCall
                )

                HomeTab.Settings -> ProfileScreen(
                    onBack = { onTabChange(HomeTab.Chats) },
                    onLogOut = onLogOut,
                    onEditProfile = onEditProfile,
                    onQrCode = onQrCode,
                    onCrop = onCrop,
                    onPrivacy = onPrivacy,
                    onNotifications = onNotifications
                )
            }
        }
        IqBottomBar(
            selected = tab,
            onSelect = onTabChange,
            unread = ChatRepository.unread.conversations
        )
    }
}
