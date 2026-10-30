package com.iqsocial.iq_message

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.iqsocial.iq_message.data.auth.SessionStore
import com.iqsocial.iq_message.data.call.CallAudio
import com.iqsocial.iq_message.data.call.CallCenter
import com.iqsocial.iq_message.data.call.CallEngine
import com.iqsocial.iq_message.data.call.CallService
import com.iqsocial.iq_message.data.call.CallWindow
import com.iqsocial.iq_message.data.call.RingingNotification
import com.iqsocial.iq_message.data.chat.ChatRepository
import com.iqsocial.iq_message.data.chat.KnownPeople
import com.iqsocial.iq_message.data.contacts.ContactsRepository
import com.iqsocial.iq_message.data.media.MediaCache
import com.iqsocial.iq_message.data.media.VoiceWaveforms
import com.iqsocial.iq_message.data.overlay.ChatHeads
import com.iqsocial.iq_message.data.profile.MeRepository
import com.iqsocial.iq_message.data.profile.PresenceRepository
import com.iqsocial.iq_message.data.profile.SettingsRepository
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
import com.iqsocial.iq_message.ui.screens.ForgotPasswordScreen
import com.iqsocial.iq_message.ui.screens.IncomingCallScreen
import com.iqsocial.iq_message.ui.screens.LoginScreen
import com.iqsocial.iq_message.ui.screens.OngoingCallBar
import com.iqsocial.iq_message.ui.screens.PeopleScreen
import com.iqsocial.iq_message.ui.screens.PeopleSearchScreen
import com.iqsocial.iq_message.ui.screens.ProfileScreen
import com.iqsocial.iq_message.ui.screens.RewardsScreen
import com.iqsocial.iq_message.ui.screens.SignupScreen
import com.iqsocial.iq_message.ui.screens.VideoCallScreen
import com.iqsocial.iq_message.ui.screens.WaitingCallBar
import com.iqsocial.iq_message.ui.screens.rememberAuthRepository
import com.iqsocial.iq_message.ui.theme.CallBlack
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
    onConversationOpened: () -> Unit = {}
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
    val session = remember { SessionStore(context) }
    // Photos survive eviction from memory here, so scrolling back to one does not
    // fetch it again.
    LaunchedEffect(Unit) {
        MediaCache.cacheDir = context.applicationContext.cacheDir
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

        when {
            wanted == null -> if (onCall) {
                // Out of the thumbnail first. The screen under it is the chat list,
                // and leaving that in a floating window is the app looking broken.
                if (CallWindow.inPip) context.activity()?.leaveCallPip()
                nav.pop()
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
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, session.token) {
        fun send() {
            if (session.token.isNullOrBlank()) return
            IqMessagingService.ensureChannels(context)
            PushTokens.register(context)
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

    // Asked for after sign-in rather than at launch, where it would be the first
    // thing a stranger sees. Nothing depends on the answer.
    if (auth.isSignedIn()) NotificationsGate()
    // After it, not beside it: notifications come first, and two permission
    // dialogs at once is a wall rather than a question.
    if (auth.isSignedIn()) FullScreenCallsGate()
    if (auth.isSignedIn()) ChatHeadsGate()


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
    DisposableEffect(Unit) {
        onDispose { CallWindow.showing(false) }
    }

    /**
     * Keeps the floating window's buttons honest.
     *
     * The actions are a snapshot taken when they were built, so muting from the
     * window would otherwise leave a "Mute" button over an already-muted
     * microphone. Reading both flags here is what makes this re-run when either
     * changes - including when the change came from the window's own buttons.
     */
    LaunchedEffect(CallWindow.inPip, CallEngine.micOn, CallAudio.route) {
        if (CallWindow.inPip) context.activity()?.updateCallPipParams()
    }

    /**
     * Back, during a call, shrinks rather than leaves.
     *
     * Popping was the old behaviour and it is the wrong shape for a call: the call
     * carried on, correctly, but with nothing on screen to say so and no way back to
     * it except the notification. Shrinking keeps it in the corner, which is what
     * the other messengers do and what was asked for.
     *
     * The route is deliberately **not** popped. The window still holds this
     * composition, so tapping it to expand finds the call screen where it was left -
     * where a pop would expand onto whatever was underneath.
     *
     * If the phone cannot do it - Android 7, or a device without the feature -
     * `enterCallPip` says so and the old behaviour stands rather than nothing
     * happening at all.
     */
    BackHandler(enabled = nav.canGoBack) {
        // Back out of a call the same way the minimise button does: into the app,
        // with the call still running behind the bar.
        if (minimisable && live != null) setAside = live.id
        nav.pop()
    }

    /**
     * Whether the return-to-call strip is up.
     *
     * Asked once because two things need the answer: whether to draw the strip, and
     * whether the column it sits in should take the status bar for it.
     */
    val barUp = live != null && !minimisable && !CallWindow.inPip &&
        live.status != CallStatus.RINGING

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
                            ChatRepository.clear()
                            KnownPeople.clear()
                            ChatHeads.clear()
                            ChatSocket.disconnect()
                            SocketAlerts.stop()
                            nav.replaceAll(Route.Login)
                        }
                    )

                    // Signing in is the moment the address book becomes useful, so the
                    // ask happens here rather than behind the People tab.
                    ContactsAccessGate()
                }

                Route.PeopleSearch -> PeopleSearchScreen(
                    onBack = { nav.pop() },
                    onOpenConversation = { conversationId ->
                        nav.pop()
                        nav.push(Route.Chat(conversationId))
                    }
                )

                is Route.Chat -> ChatScreen(
                    conversationId = route.conversationId,
                    onBack = { nav.pop() },
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
                // shrunk - it is a different, much smaller thing. Checked here rather
                // than inside each screen so neither has to know the difference.
                Route.VideoCall -> if (CallWindow.inPip) CallPipScreen() else VideoCallScreen(
                    onMinimize = { minimise(nav, { setAside = it }, live?.id) },
                    onEnd = { CallCenter.end(context) }
                )

                // A voice call with a camera on - either end's - is a video call, and
                // is drawn as one. The route does not change: what kind of call it is is
                // the server's word, and this is about what there is to show.
                Route.AudioCall -> when {
                    CallWindow.inPip -> CallPipScreen()
                    hasPicture -> VideoCallScreen(
                        onMinimize = { minimise(nav, { setAside = it }, live?.id) },
                        onEnd = { CallCenter.end(context) }
                    )
                    else -> AudioCallScreen(
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
     */
    CallCenter.error?.let { why ->
        CallProblem(why) { CallCenter.dismissError() }
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
}

/**
 * What the minimise button does: the same as back during a call.
 *
 * Kept together so the button and the gesture cannot drift apart - they are one
 * action with two ways of asking for it.
 */
/**
 * Steps out of the call screen and back into the app.
 *
 * It used to shrink the whole app into a floating window instead, which keeps the
 * call in view and takes everything else away - so during a call there was no way to
 * read a message or look anything up. The window is the right answer for *leaving*
 * the app, which `onUserLeaveHint` still handles; it is the wrong one for staying in
 * it.
 *
 * The call is not left behind: [OngoingCallBar] sits above whatever is underneath
 * and comes straight back here.
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
                    onNewMessage = onNewMessage
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
                    onLogOut = onLogOut
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
