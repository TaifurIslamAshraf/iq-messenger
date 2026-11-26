package com.iqsocial.iq_message.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iqsocial.iq_message.ui.icons.IqIcons
import com.iqsocial.iq_message.ui.theme.CanvasGrey
import com.iqsocial.iq_message.ui.theme.Crimson
import com.iqsocial.iq_message.ui.theme.Hairline
import com.iqsocial.iq_message.ui.theme.Ink
import com.iqsocial.iq_message.ui.theme.IqTheme

/** Preserve the original inbox controls and their order; only polish their styling. */
@Composable
internal fun MessagesHeader(
    archived: Boolean,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    onCamera: () -> Unit = {},
    menu: @Composable () -> Unit
) {
    val d = IqTheme.dimens
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = d.gutter, end = d.gapXs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(d.iconButton)
                .background(CanvasGrey, CircleShape)
                .border(1.dp, Hairline.copy(alpha = 0.7f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(IqIcons.PersonOutline, "Your profile", Modifier.size(d.icon), tint = Ink)
        }
        Spacer(Modifier.width(d.gapMd))
        Text(
            if (archived) "Archived" else "Messages",
            style = if (d.isCompact) MaterialTheme.typography.headlineMedium
                else MaterialTheme.typography.headlineLarge.let {
                    it.copy(fontSize = it.fontSize * 0.94f, lineHeight = it.lineHeight * 0.94f)
                },
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        MessagesHeaderAction(IqIcons.Search, "Search", onSearch)
        MessagesHeaderAction(IqIcons.CameraOutline, "Camera", onCamera)
        menu()
    }
}

@Composable
internal fun MessagesMenuButton(onClick: () -> Unit) {
    MessagesHeaderAction(IqIcons.MoreVert, "More options", onClick)
}

@Composable
private fun MessagesHeaderAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    val d = IqTheme.dimens
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = tween(120),
        label = "messagesIconPress"
    )
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactions,
                indication = androidx.compose.foundation.LocalIndication.current,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(d.scaled(40))
                .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
                .background(CanvasGrey, CircleShape)
                .border(1.dp, Hairline.copy(alpha = 0.55f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription, Modifier.size(d.icon), tint = Ink)
        }
    }
}

@Composable
internal fun NewMessageButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val d = IqTheme.dimens
    val shape = RoundedCornerShape(d.scaled(18))
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(120),
        label = "newMessagePress"
    )
    Row(
        modifier
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .shadow(4.dp, shape, ambientColor = Crimson.copy(alpha = 0.12f), spotColor = Crimson.copy(alpha = 0.18f))
            .clip(shape)
            .background(Crimson)
            .clickable(
                interactionSource = interactions,
                indication = androidx.compose.foundation.LocalIndication.current,
                role = Role.Button,
                onClick = onClick
            )
            .heightIn(min = d.scaled(54).coerceAtLeast(48.dp))
            .padding(horizontal = d.scaled(16), vertical = d.gapMd),
        horizontalArrangement = Arrangement.spacedBy(d.gapSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(MessageCompose, null, Modifier.size(d.scaled(20)), tint = Color.White)
        Text("New message", style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
}

/** The rounded page-and-pencil icon from the user's selected Messages design. */
private val MessageCompose = ImageVector.Builder(
    name = "MessageCompose", defaultWidth = 24.dp, defaultHeight = 24.dp,
    viewportWidth = 24f, viewportHeight = 24f
).apply {
    path(
        stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(11f, 4f); lineTo(7f, 4f)
        curveTo(4.8f, 4f, 3.5f, 5.3f, 3.5f, 7.5f)
        lineTo(3.5f, 17f)
        curveTo(3.5f, 19.2f, 4.8f, 20.5f, 7f, 20.5f)
        lineTo(16.5f, 20.5f)
        curveTo(18.7f, 20.5f, 20f, 19.2f, 20f, 17f)
        lineTo(20f, 13f)
        moveTo(10f, 14f); lineTo(10.8f, 10.3f); lineTo(17.8f, 3.3f)
        quadTo(18.8f, 2.3f, 19.8f, 3.3f)
        lineTo(20.7f, 4.2f); quadTo(21.7f, 5.2f, 20.7f, 6.2f)
        lineTo(13.7f, 13.2f); close()
        moveTo(16.5f, 4.6f); lineTo(19.4f, 7.5f)
    }
}.build()
