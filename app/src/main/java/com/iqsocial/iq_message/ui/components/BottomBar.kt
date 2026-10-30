package com.iqsocial.iq_message.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iqsocial.iq_message.ui.icons.IqIcons
import com.iqsocial.iq_message.ui.theme.Crimson
import com.iqsocial.iq_message.ui.theme.InkSoft
import com.iqsocial.iq_message.ui.theme.IqTheme

enum class HomeTab(
    val label: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector
) {
    Chats("Chats", IqIcons.ChatFilled, IqIcons.ChatOutline),
    Calls("Calls", IqIcons.Phone, IqIcons.PhoneOutline),
    People("People", IqIcons.PeopleFilled, IqIcons.People),
    Rewards("Rewards", IqIcons.Star, IqIcons.StarOutline),
    Settings("Settings", IqIcons.Settings, IqIcons.SettingsOutline)
}

@Composable
fun IqBottomBar(
    selected: HomeTab,
    onSelect: (HomeTab) -> Unit,
    modifier: Modifier = Modifier,
    /** Unread conversations, badged on the Chats tab. */
    unread: Int = 0
) {
    val d = IqTheme.dimens
    Column(
        modifier
            .fillMaxWidth()
            .background(Color.White)
    ) {
        HairlineDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = d.gapSm, vertical = d.gapSm),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HomeTab.entries.forEach { tab ->
                TabItem(
                    tab = tab,
                    active = tab == selected,
                    onClick = { onSelect(tab) },
                    badge = if (tab == HomeTab.Chats) unread else 0,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TabItem(
    tab: HomeTab,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: Int = 0
) {
    val d = IqTheme.dimens
    val tint by animateColorAsState(
        targetValue = if (active) Crimson else InkSoft,
        label = "tabTint"
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(d.radiusMd))
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(vertical = d.gapXs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .padding(horizontal = d.gapMd, vertical = d.scaled(5)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (active) tab.selectedIcon else tab.icon,
                contentDescription = tab.label,
                modifier = Modifier.size(d.navIcon),
                tint = tint
            )
            if (badge > 0) {
                // Sits on the icon's top-right corner, outside the glyph's box so
                // it never covers the shape it belongs to.
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = d.gapSm, y = -d.gapXs)
                        .heightIn(min = d.scaled(18))
                        .widthIn(min = d.scaled(18))
                        .clip(RoundedCornerShape(percent = 50))
                        .background(Crimson)
                        .padding(horizontal = d.gapXs),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (badge > 99) "99+" else badge.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1
                    )
                }
            }
        }
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = d.scaled(3))
        )
    }
}
