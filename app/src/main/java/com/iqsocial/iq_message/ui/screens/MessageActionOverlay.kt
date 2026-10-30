package com.iqsocial.iq_message.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.iqsocial.iq_message.ui.components.HairlineDivider
import com.iqsocial.iq_message.ui.icons.IqIcons
import com.iqsocial.iq_message.ui.theme.Crimson
import com.iqsocial.iq_message.ui.theme.CrimsonTint
import com.iqsocial.iq_message.ui.theme.Ink
import com.iqsocial.iq_message.ui.theme.InkMuted
import com.iqsocial.iq_message.ui.theme.IqTheme
import com.iqsocial.iq_message.ui.theme.reactionTray
/** The six Messenger offers, in its order. */
val QUICK_REACTIONS = listOf("❤️", "😆", "😮", "😢", "😡", "👍")

/**
 * What a long press puts on screen: a reaction tray against the message itself, and
 * the actions along the bottom.
 *
 * Split in two on purpose. A reaction is aimed at *that* message, so the tray sits
 * on it — a centred dialog, which is what this was, makes you look away from the
 * thing you are reacting to. The actions are aimed at the app, so they go where the
 * app's other actions go, in reach of a thumb.
 *
 * The thread behind is not dimmed. Dimming would say the conversation is
 * unavailable, when in fact it is the subject of what is on top of it.
 */
@Composable
fun MessageActionOverlay(
    anchor: Rect,
    mine: Boolean,
    canReact: Boolean,
    /**
     * Separate from [canReact], which used to gate this too.
     *
     * They are different questions - a message can be worth a face without being
     * worth answering - and a call is the case that shows it: there is nothing to
     * answer, and anything that turned reactions back on would have quietly brought
     * Reply with it.
     */
    canReply: Boolean,
    canCopy: Boolean,
    myReaction: String?,
    onReact: (String) -> Unit,
    onPickOtherReaction: () -> Unit,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onMore: () -> Unit,
    onDismiss: () -> Unit
) {
    val d = IqTheme.dimens
    val density = LocalDensity.current

    // Known exactly, because the tray's contents are fixed below: one row of
    // buttons inside its own padding.
    val trayHeight = d.scaled(40) + d.gapXs * 2
    // The floor the tray must clear: the status bar plus the header's own height,
    // since the overlay spans the window and the header is drawn over it.
    val statusBar = WindowInsets.statusBars.getTop(density)
    val floor = statusBar + with(density) { d.scaled(56).toPx() }
    val trayY = with(density) {
        val above = anchor.top - (trayHeight + d.gapSm).toPx()
        // Above the bubble, unless the bubble sits high enough that the tray would
        // land behind the header - then it goes under the bubble instead.
        if (above > floor) above else anchor.bottom + d.gapSm.toPx()
    }

    // The bar follows the keyboard for the same reason the composer does: the
    // window does not shrink, so without this it would be behind the keys.
    Box(
        Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        // A tap anywhere else closes. No ripple: this is a dismiss target, not a
        // button, and it covers the whole screen.
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        )

        // An unsent message has nothing left to react to or answer, so the tray
        // is not drawn at all rather than drawn and refused.
        if (canReact) Row(
            Modifier
                // Over the message's own side, so it reads as belonging to that
                // bubble rather than to the screen.
                .align(if (mine) Alignment.TopEnd else Alignment.TopStart)
                .offset { IntOffset(0, trayY.toInt()) }
                .padding(horizontal = d.gutter)
                .shadow(d.scaled(8), CircleShape)
                .clip(CircleShape)
                .background(Color.White)
                .padding(horizontal = d.gapSm, vertical = d.gapXs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QUICK_REACTIONS.forEach { emoji ->
                val chosen = emoji == myReaction
                Box(
                    Modifier
                        .size(d.scaled(40))
                        .clip(CircleShape)
                        // Your own reaction is marked, because tapping it again
                        // takes it off and nothing else would say so.
                        .background(if (chosen) CrimsonTint else Color.Transparent)
                        .clickable { onReact(emoji) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(emoji, style = MaterialTheme.typography.reactionTray)
                }
            }
            Box(
                Modifier
                    .size(d.scaled(40))
                    .clip(CircleShape)
                    .background(CrimsonTint)
                    .clickable(onClick = onPickOtherReaction),
                contentAlignment = Alignment.Center
            ) {
                Icon(IqIcons.Add, "Other reactions", Modifier.size(d.iconSm), tint = Crimson)
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.White)
        ) {
            HairlineDivider()
            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(vertical = d.gapMd),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                if (canReply) ActionButton(IqIcons.Reply, "Reply", Ink, onReply)
                // Nothing to put on the clipboard from a photo or a voice note, so
                // the slot is dropped rather than shown dead.
                if (canCopy) ActionButton(IqIcons.Copy, "Copy", Ink, onCopy)
                ActionButton(IqIcons.Trash, "Delete", Crimson, onDelete)
                ActionButton(IqIcons.MoreVert, "More", Ink, onMore)
            }
        }
    }
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    val d = IqTheme.dimens
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(d.radiusMd))
            .clickable(onClick = onClick)
            .padding(horizontal = d.gapLg, vertical = d.gapSm)
    ) {
        Icon(icon, null, Modifier.size(d.icon), tint = tint)
        Spacer(Modifier.height(d.gapXs))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (tint == Crimson) Crimson else InkMuted
        )
    }
}

/**
 * The whole emoji sheet, for a reaction the tray does not carry.
 *
 * The same sheet the composer uses, so the set and the recents are the same wherever
 * an emoji is chosen. It arrives as a sheet pinned to the bottom rather than in the
 * keyboard's place, because by this point the overlay has already taken focus off
 * the field and there is nothing for it to sit under.
 */
@Composable
fun ReactionPicker(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(Modifier.fillMaxSize()) {
            // Tapping the thread above closes, the way the tray does.
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
            EmojiPanel(onPick = onPick)
        }
    }
}
