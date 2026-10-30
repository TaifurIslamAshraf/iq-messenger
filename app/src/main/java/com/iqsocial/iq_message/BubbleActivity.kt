package com.iqsocial.iq_message

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.iqsocial.iq_message.data.call.CallCenter
import com.iqsocial.iq_message.data.push.MessageNotifications
import com.iqsocial.iq_message.data.remote.CallType
import com.iqsocial.iq_message.ui.screens.ChatScreen
import com.iqsocial.iq_message.ui.theme.IQMessageTheme

/**
 * One conversation, in the floating window a bubble opens.
 *
 * Its own activity rather than [MainActivity] in a different mode, because the
 * system embeds this one in a window it owns and the manifest has to say so:
 * `allowEmbedded` and `resizeableActivity`, and no fixed orientation. MainActivity is
 * portrait-locked and cannot be either of those, and a notification whose bubble
 * activity does not qualify does not fail - it quietly arrives as an ordinary
 * notification, which is the kind of near-miss that goes unnoticed for weeks.
 *
 * There is no navigation in here on purpose. A bubble is a window onto one chat; the
 * back gesture closes it rather than walking up a stack that a bubble has no room to
 * show. Anything that needs the whole screen - a call - hands over to the app.
 *
 * [ChatScreen] needs nothing but the id: it fetches the conversation and joins the
 * socket exactly as it does in the app.
 */
class BubbleActivity : ComponentActivity() {

    private var conversationId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        conversationId = conversationOf(intent)
        if (conversationId.isBlank()) {
            // Nothing to show. Finishing is better than an empty window that the
            // reader has to work out how to get rid of - but it is indistinguishable
            // from a bubble that opened and drew nothing, so it says which it was.
            Log.w(TAG, "Bubble opened with no conversation in " + intent)
            finish()
            return
        }

        // Said before composition, and this is why: ChatScreen clears the chat's
        // notification as soon as it opens, and cancelling a bubbled notification
        // takes the bubble down with it. Knowing the chat is in a bubble is what
        // turns that clear into a no-op - see MessageNotifications.clear.
        MessageNotifications.bubbleOpened(conversationId)

        // No enableEdgeToEdge here, unlike MainActivity. This window is a panel the
        // system draws and positions; there are no system bars behind it to draw
        // under, and the insets come back empty either way.
        setContent {
            IQMessageTheme {
                ChatScreen(
                    conversationId = conversationId,
                    onBack = { finish() },
                    onAudioCall = { handOverForCall(it, CallType.AUDIO) },
                    onVideoCall = { handOverForCall(it, CallType.VIDEO) }
                )
            }
        }
    }

    /**
     * The bubble was tapped again while already open.
     *
     * A bubble keeps one task per conversation, so this is the same chat arriving
     * twice rather than a different one - but the id is re-read rather than assumed,
     * because a wrong assumption here would show one person's thread under another's
     * face.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val next = conversationOf(intent)
        if (next.isNotBlank() && next != conversationId) {
            MessageNotifications.bubbleClosed(applicationContext, conversationId)
            // Cleared before recreating, not after. `recreate` tears this instance
            // down, and onDestroy closes whatever conversationId holds - so leaving
            // the new id here would have onDestroy cancel the notification of the
            // chat just opened, which is the one thing that takes a bubble down.
            // onCreate reads the new intent and opens it again from scratch.
            conversationId = ""
            recreate()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Not on a recreation: the instance is going, but the bubble is not, and
        // cancelling the notification would close the window this is rebuilding.
        if (conversationId.isNotBlank() && !isChangingConfigurations) {
            // The bubble is gone, so the notification it was holding open can go
            // too - the chat has been read by now.
            MessageNotifications.bubbleClosed(applicationContext, conversationId)
        }
    }

    /**
     * Starts the call and gets out of the way.
     *
     * A call does not belong in a bubble: it wants the whole screen for a picture, a
     * speaker and a set of controls, and a bubble is a panel the size of a message
     * thread. The call is started here, exactly as the app starts one, and the app is
     * brought forward - its own effect sees a live call and opens the right screen
     * for it, so there is no second route to keep in step.
     */
    private fun handOverForCall(peerId: String, type: CallType) {
        CallCenter.start(applicationContext, peerId, type)
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        )
    }

    /**
     * The conversation this window is for, from either half of the intent.
     *
     * The extra is the convenient half and the URI is the durable one, and that
     * difference is the whole reason this reads both. A bubble is a document task:
     * the system keeps the task alive across the process being killed and relaunches
     * it from the intent that started it - and what identifies that intent, and so
     * what is certain to survive, is the component, the action and the **data URI**.
     * Extras are not part of a task's identity and are not promised back.
     *
     * Reading only the extra meant that whenever they did not come back the id was
     * blank, and a blank id finishes immediately - so the bubble opened onto
     * nothing and shut again, now and then, for no reason the reader could see.
     */
    private fun conversationOf(intent: Intent?): String {
        intent ?: return ""
        intent.getStringExtra(EXTRA_CONVERSATION)
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        // `iqmessage://chat/<id>`, written by `intentFor` for exactly this reason.
        return intent.data?.lastPathSegment.orEmpty()
    }

    companion object {

        private const val TAG = "BubbleActivity"

        const val EXTRA_CONVERSATION = "bubble_conversation"

        /**
         * The intent a bubble is opened with.
         *
         * The data URI carries the conversation as well as the extra, and it is not
         * redundant: the system tells one document task from another by the intent,
         * and extras are not part of that comparison. Without it every chat's bubble
         * would look like the same document and they would share one window.
         */
        fun intentFor(context: android.content.Context, conversationId: String): Intent =
            Intent(context, BubbleActivity::class.java).apply {
                data = Uri.parse("iqmessage://chat/$conversationId")
                putExtra(EXTRA_CONVERSATION, conversationId)
            }
    }
}
