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
import com.iqsocial.iq_message.ui.components.HomeTab
import com.iqsocial.iq_message.ui.components.IqBottomBar
import com.iqsocial.iq_message.ui.screens.AudioCallScreen
import com.iqsocial.iq_message.ui.screens.CallsScreen
import com.iqsocial.iq_message.ui.screens.ChatScreen
import com.iqsocial.iq_message.ui.screens.ChatsScreen
import com.iqsocial.iq_message.ui.screens.IncomingCallScreen
import com.iqsocial.iq_message.ui.screens.OtpScreen
import com.iqsocial.iq_message.ui.screens.PeopleScreen
import com.iqsocial.iq_message.ui.screens.PhoneScreen
import com.iqsocial.iq_message.ui.screens.ProfileScreen
import com.iqsocial.iq_message.ui.screens.VideoCallScreen

/**
 * Destinations in the prototype.
 *
 * Navigation is a plain in-memory back stack rather than navigation-compose: the flow
 * is small and fixed, and this keeps the module free of extra dependencies.
 */
sealed interface Route {
    data object Phone : Route
    data object Otp : Route
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
    val nav = remember { Navigator(Route.Phone) }
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
            Route.Phone -> PhoneScreen(
                onBack = { },
                onContinue = { nav.push(Route.Otp) }
            )

            Route.Otp -> OtpScreen(
                onBack = { nav.pop() },
                onVerified = { nav.replaceAll(Route.Home) }
            )

            Route.Home -> HomeShell(
                tab = tab,
                onTabChange = { tab = it },
                onOpenChat = { nav.push(Route.Chat(it)) },
                onAudioCall = { nav.push(Route.AudioCall(it)) },
                onVideoCall = { nav.push(Route.VideoCall(it)) },
                onIncomingCall = { nav.push(Route.IncomingCall(SampleData.ayesha.id)) },
                onLogOut = { nav.replaceAll(Route.Phone) }
            )

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
