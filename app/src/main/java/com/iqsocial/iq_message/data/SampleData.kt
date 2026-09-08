package com.iqsocial.iq_message.data

import androidx.compose.runtime.Immutable

/* -- models --------------------------------------------------------------- */

@Immutable
data class Person(
    val id: String,
    val name: String,
    val handle: String,
    val online: Boolean = false,
    /** Seeds the generated avatar gradient so a person keeps one identity everywhere. */
    val seed: Int = id.hashCode()
) {
    val initials: String
        get() = name.trim().split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
}

@Immutable
data class Conversation(
    val id: String,
    val person: Person,
    val lastMessage: String,
    val time: String,
    val unread: Int = 0
)

enum class MessageKind { TEXT, VOICE }

@Immutable
data class QuotedMessage(val author: String, val text: String)

@Immutable
data class Message(
    val id: String,
    val text: String = "",
    val time: String,
    val fromMe: Boolean,
    val kind: MessageKind = MessageKind.TEXT,
    val quoted: QuotedMessage? = null,
    val reaction: String? = null,
    val reactionCount: Int = 0,
    val voiceLength: String = "0:00",
    val delivered: Boolean = true
)

enum class CallKind { AUDIO, VIDEO }
enum class CallDirection { INCOMING, OUTGOING, MISSED }

@Immutable
data class CallRecord(
    val id: String,
    val person: Person,
    val kind: CallKind,
    val direction: CallDirection,
    val time: String
)

/* -- demo content --------------------------------------------------------- */

object SampleData {

    val me = Person("me", "Rafi Hasan", "@rafihasan", online = true, seed = 21)

    val ayesha = Person("p_ayesha", "Ayesha Rahman", "@ayesha.r", online = true, seed = 3)
    val tanvir = Person("p_tanvir", "Tanvir Ahmed", "@tanvir", online = true, seed = 8)
    val nusrat = Person("p_nusrat", "Nusrat Jahan", "@nusratj", online = true, seed = 14)
    val rafi = Person("p_rafi", "Rafi Hasan", "@rafihasan", online = true, seed = 21)
    val maya = Person("p_maya", "Maya Akter", "@maya", online = true, seed = 27)
    val siam = Person("p_siam", "Siam Khan", "@siamk", online = true, seed = 33)
    val farhan = Person("p_farhan", "Farhan Kabir", "@farhank", seed = 39)
    val zara = Person("p_zara", "Zara Islam", "@zara", online = true, seed = 45)
    val imran = Person("p_imran", "Imran Hossain", "@imran", seed = 51)

    val stories = listOf(ayesha, tanvir, nusrat, rafi, maya, siam)

    val conversations = listOf(
        Conversation("c1", ayesha, "See you at 7 PM tonight!", "9:41 AM", 2),
        Conversation("c2", tanvir, "Thanks for sending the documents.", "9:10 AM", 1),
        Conversation("c3", nusrat, "Can we reschedule our meeting?", "8:45 AM", 3),
        Conversation("c4", rafi, "I'll call you in a few minutes.", "Yesterday"),
        Conversation("c5", maya, "That sounds great!", "Yesterday", 1),
        Conversation("c6", siam, "Check out this new cafe!", "Mon"),
        Conversation("c7", farhan, "Sent you the invoice 📄", "Mon"),
        Conversation("c8", zara, "Happy birthday! 🎉", "Sun"),
        Conversation("c9", imran, "Let's catch up next week.", "Sat")
    )

    fun conversation(id: String): Conversation =
        conversations.firstOrNull { it.id == id } ?: conversations.first()

    fun person(id: String): Person =
        (conversations.map { it.person } + me).firstOrNull { it.id == id } ?: ayesha

    /** The thread shown in the conversation mockup. */
    val thread = listOf(
        Message("m1", "Hey! Are we still meeting today?", "6:42 PM", fromMe = false),
        Message("m2", "Yes, absolutely!", "6:42 PM", fromMe = true),
        Message("m3", "How about 7 PM?", "6:43 PM", fromMe = true),
        Message("m4", "Perfect. I'll be there 😊", "6:43 PM", fromMe = false),
        Message(
            "m5", "Great—see you soon!", "6:44 PM", fromMe = true,
            quoted = QuotedMessage("Ayesha", "How about 7 PM?")
        ),
        Message(
            "m6", time = "6:45 PM", fromMe = false, kind = MessageKind.VOICE,
            voiceLength = "0:18", reaction = "❤️", reactionCount = 1
        ),
        Message("m7", "I'll send you the location.", "6:45 PM", fromMe = true)
    )

    val calls = listOf(
        CallRecord("cl1", ayesha, CallKind.VIDEO, CallDirection.INCOMING, "Today, 9:12 AM"),
        CallRecord("cl2", tanvir, CallKind.AUDIO, CallDirection.OUTGOING, "Today, 8:30 AM"),
        CallRecord("cl3", nusrat, CallKind.AUDIO, CallDirection.MISSED, "Yesterday, 7:48 PM"),
        CallRecord("cl4", maya, CallKind.VIDEO, CallDirection.OUTGOING, "Yesterday, 3:20 PM"),
        CallRecord("cl5", siam, CallKind.AUDIO, CallDirection.INCOMING, "Mon, 11:02 AM"),
        CallRecord("cl6", zara, CallKind.VIDEO, CallDirection.MISSED, "Mon, 9:15 AM"),
        CallRecord("cl7", farhan, CallKind.AUDIO, CallDirection.OUTGOING, "Sun, 6:41 PM")
    )

    val contacts = listOf(
        ayesha, maya, farhan, imran, nusrat, rafi, siam, tanvir, zara
    ).sortedBy { it.name }
}
