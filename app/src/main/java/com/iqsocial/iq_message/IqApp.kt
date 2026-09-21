package com.iqsocial.iq_message

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.iqsocial.iq_message.data.SampleData
import com.iqsocial.iq_message.data.contacts.ContactsRepository
import com.iqsocial.iq_message.data.profile.MeRepository
import com.iqsocial.iq_message.ui.components.ContactsAccessGate
import com.iqsocial.iq_message.ui.components.HomeTab
import com.iqsocial.iq_message.ui.components.IqBottomBar
import com.iqsocial.iq_message.ui.screens.AudioCallScreen
import com.iqsocial.iq_message.ui.screens.CallsScreen
import com.iqsocial.iq_message.ui.screens.ChatScreen
import com.iqsocial.iq_message.ui.screens.ChatsScreen
import com.iqsocial.iq_message.ui.screens.IncomingCallScreen
import com.iqsocial.iq_message.ui.screens.ForgotPasswordScreen
import com.iqsocial.iq_message.ui.screens.LoginScreen
import com.iqsocial.iq_message.ui.screens.SignupScreen
import com.iqsocial.iq_message.ui.screens.rememberAuthRepository
import com.iqsocial.iq_message.ui.screens.PeopleScreen
import com.iqsocial.iq_message.ui.screens.ProfileScreen
import com.iqsocial.iq_message.ui.screens.VideoCallScreen

/**
 * Destinations in the prototype.
 *
 * Navigation is a plain in-memory back stack rather than navigation-compose: the flow
 * is small and fixed, and this keeps the module free of extra dependencies.
 */
sealed interface Route {
    data object Login : Route
    data object Signup : Route
    data object ForgotPassword : Route
    data object Home : Route
    data class Chat(val conversationId: String) : Route
    data class IncomingCall(val personId: String) : Route
    data class VideoCall(val personId: String) : Route
    data class AudioCall(val personId: String) : Route
}

private class Navigator(initial: Route) {
    val stack: SnapshotStateList<Route> = mutableStateListOf(initial)
    val current: Route get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    fun push(route: Route) {
        stack.add(route)
    }

    fun pop() {
        if (canGoBack) stack.removeAt(stack.lastIndex)
    }

    /** Used when signing in: the onboarding screens must not stay on the stack. */
    fun replaceAll(route: Route) {
        stack.clear()
        stack.add(route)
    }
}

@Composable
fun IqApp() {
    // A stored token skips straight past the auth screens on relaunch.
    val auth = rememberAuthRepository()
    val nav = remember {
        Navigator(if (auth.isSignedIn()) Route.Home else Route.Login)
    }
    var tab by rememberSaveable { mutableStateOf(HomeTab.Chats) }

    BackHandler(enabled = nav.canGoBack) { nav.pop() }

    AnimatedContent(
        targetState = nav.current,
        transitionSpec = {
            (slideInHorizontally { it / 6 } + fadeIn()) togetherWith
                (slideOutHorizontally { -it / 12 } + fadeOut())
        },
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
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
                    onAudioCall = { nav.push(Route.AudioCall(it)) },
                    onVideoCall = { nav.push(Route.VideoCall(it)) },
                    onIncomingCall = { nav.push(Route.IncomingCall(SampleData.ayesha.id)) },
                    onLogOut = {
                        auth.logout()
                        ContactsRepository.clear()
                        MeRepository.clear()
                        nav.replaceAll(Route.Login)
                    }
                )

                // Signing in is the moment the address book becomes useful, so the
                // ask happens here rather than behind the People tab.
                ContactsAccessGate()
            }

            is Route.Chat -> ChatScreen(
                conversationId = route.conversationId,
                onBack = { nav.pop() },
                onAudioCall = { nav.push(Route.AudioCall(it)) },
                onVideoCall = { nav.push(Route.VideoCall(it)) }
            )

            is Route.IncomingCall -> IncomingCallScreen(
                personId = route.personId,
                onDecline = { nav.pop() },
                onAccept = {
                    nav.pop()
                    nav.push(Route.VideoCall(route.personId))
                }
            )

            is Route.VideoCall -> VideoCallScreen(
                personId = route.personId,
                onMinimize = { nav.pop() },
                onEnd = { nav.pop() }
            )

            is Route.AudioCall -> AudioCallScreen(
                personId = route.personId,
                onMinimize = { nav.pop() },
                onEnd = { nav.pop() }
            )
        }
    }
}

@Composable
private fun HomeShell(
    tab: HomeTab,
    onTabChange: (HomeTab) -> Unit,
    onOpenChat: (String) -> Unit,
    onAudioCall: (String) -> Unit,
    onVideoCall: (String) -> Unit,
    onIncomingCall: () -> Unit,
    onLogOut: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                HomeTab.Chats -> ChatsScreen(
                    onOpenChat = onOpenChat,
                    onSimulateIncomingCall = onIncomingCall
                )

                HomeTab.Calls -> CallsScreen(
                    onAudioCall = onAudioCall,
                    onVideoCall = onVideoCall
                )

                HomeTab.People -> PeopleScreen(onOpenChat = onOpenChat)

                HomeTab.Settings -> ProfileScreen(
                    onBack = { onTabChange(HomeTab.Chats) },
                    onLogOut = onLogOut
                )
            }
        }
        IqBottomBar(selected = tab, onSelect = onTabChange)
    }
}
