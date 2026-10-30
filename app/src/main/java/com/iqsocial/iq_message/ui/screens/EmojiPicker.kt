package com.iqsocial.iq_message.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iqsocial.iq_message.data.EmojiRecents
import com.iqsocial.iq_message.ui.components.HairlineDivider
import com.iqsocial.iq_message.ui.theme.CrimsonTint
import com.iqsocial.iq_message.ui.theme.FieldGrey
import com.iqsocial.iq_message.ui.theme.Ink
import com.iqsocial.iq_message.ui.theme.InkMuted
import com.iqsocial.iq_message.ui.theme.IqTheme
import kotlinx.coroutines.launch

/**
 * The emoji sheet, laid out the way Messenger lays it out.
 *
 * One scrolling grid with a heading per section rather than a tab per category:
 * scrolling past "Smileys and people" into "Gestures" is how people browse, and
 * tabs would break that into separate pages. The bar along the bottom jumps to a
 * section instead of replacing the contents, so the position is never lost.
 *
 * It sits in the keyboard's place rather than in a dialog: a dialog would take
 * focus off the field and close the keyboard, so picking two emoji in a row would
 * mean opening it twice. Picking a reaction is the one case where it is a sheet,
 * because there is no field to keep focus on.
 */
@Composable
fun EmojiPanel(onPick: (String) -> Unit, onBackspace: (() -> Unit)? = null) {
    val d = IqTheme.dimens
    val context = LocalContext.current
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { EmojiRecents.load(context) }
    val recent = EmojiRecents.recent

    // One flat list of cells, so a heading can be found by index and jumped to.
    val cells = remember(recent) {
        buildList {
            if (recent.isNotEmpty()) {
                add(Cell.Heading("Recent"))
                recent.forEach { add(Cell.Emoji(it)) }
            }
            EmojiSection.entries.forEach { section ->
                add(Cell.Heading(section.title))
                section.emoji.forEach { add(Cell.Emoji(it)) }
            }
        }
    }
    val headingAt = remember(cells) {
        cells.withIndex()
            .filter { it.value is Cell.Heading }
            .associate { (i, cell) -> (cell as Cell.Heading).title to i }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White)
            .heightIn(min = d.screenHeight * 0.34f, max = d.screenHeight * 0.42f)
    ) {
        HairlineDivider()

        // The handle says this panel is a sheet, even though it does not drag yet.
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .padding(vertical = d.gapSm)
                    .width(d.scaled(36))
                    .height(d.scaled(4))
                    .clip(CircleShape)
                    .background(FieldGrey)
            )
        }

        LazyVerticalGrid(
            state = grid,
            columns = GridCells.Adaptive(minSize = d.scaled(44)),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = d.gapSm, vertical = d.gapXs)
        ) {
            items(
                count = cells.size,
                span = { index ->
                    if (cells[index] is Cell.Heading) GridItemSpan(maxLineSpan)
                    else GridItemSpan(1)
                },
                key = { index ->
                    when (val cell = cells[index]) {
                        is Cell.Heading -> "h_" + cell.title
                        is Cell.Emoji -> "e_" + index
                    }
                }
            ) { index ->
                when (val cell = cells[index]) {
                    is Cell.Heading -> Text(
                        cell.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = InkMuted,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(
                            start = d.gapSm,
                            top = d.gapMd,
                            bottom = d.gapXs
                        )
                    )

                    is Cell.Emoji -> Box(
                        Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(d.radiusSm))
                            .clickable {
                                EmojiRecents.remember(context, cell.value)
                                onPick(cell.value)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            cell.value,
                            style = MaterialTheme.typography.headlineSmall,
                            color = Ink
                        )
                    }
                }
            }
        }

        HairlineDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = d.gapSm, vertical = d.gapXs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (recent.isNotEmpty()) {
                JumpTo("🕘") {
                    scope.launch { grid.animateScrollToItem(0) }
                }
            }
            EmojiSection.entries.forEach { section ->
                JumpTo(section.icon) {
                    headingAt[section.title]?.let { target ->
                        scope.launch { grid.animateScrollToItem(target) }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            // There is nothing to delete when the sheet is picking a reaction
            // rather than typing, so the key is left out instead of sitting dead.
            onBackspace?.let { backspace ->
                Box(
                    Modifier
                        .size(d.iconButtonSm)
                        .clip(CircleShape)
                        .background(CrimsonTint)
                        .clickable(onClick = backspace),
                    contentAlignment = Alignment.Center
                ) {
                    Text("⌫", style = MaterialTheme.typography.titleMedium, color = Ink)
                }
            }
        }
    }
}

@Composable
private fun JumpTo(glyph: String, onClick: () -> Unit) {
    val d = IqTheme.dimens
    Box(
        Modifier
            .size(d.iconButtonSm)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(glyph, style = MaterialTheme.typography.titleMedium)
    }
}

private sealed interface Cell {
    data class Heading(val title: String) : Cell
    data class Emoji(val value: String) : Cell
}

/**
 * Hand-picked rather than enumerated from a font.
 *
 * A complete set needs names and search to be usable at all; this is the range
 * people actually send, grouped the way the sheet reads.
 */
private enum class EmojiSection(val title: String, val icon: String, val emoji: List<String>) {
    SMILEYS(
        "Smileys and people", "😀",
        listOf(
            "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "🙂", "🙃",
            "😉", "😊", "😇", "🥰", "😍", "🤩", "😘", "😗", "😚", "😙",
            "😋", "😛", "😜", "🤪", "😝", "🤑", "🤗", "🤭", "🤫", "🤔",
            "🤐", "🤨", "😐", "😑", "😶", "😏", "😒", "🙄", "😬", "😌",
            "😔", "😪", "🤤", "😴", "😷", "🤒", "🤕", "🥵", "🥶", "🥴",
            "😵", "🤯", "🤠", "🥳", "😎", "🤓", "🧐", "😕", "😟", "🙁",
            "😮", "😯", "😲", "😳", "🥺", "😦", "😧", "😨", "😰", "😥",
            "😢", "😭", "😱", "😖", "😣", "😞", "😓", "😩", "😫", "🥱",
            "😤", "😡", "😠", "🤬", "😈", "💀", "💩", "🤡", "👻", "👽"
        )
    ),
    GESTURES(
        "Gestures", "👍",
        listOf(
            "👍", "👎", "👌", "🤌", "✌️", "🤞", "🤟", "🤘", "🤙", "👈",
            "👉", "👆", "👇", "☝️", "✋", "🤚", "🖐️", "🖖", "👋", "🤝",
            "🙏", "👏", "🙌", "👐", "🤲", "💪", "✍️", "🤳", "💅", "👀"
        )
    ),
    HEARTS(
        "Hearts", "❤️",
        listOf(
            "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔",
            "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "💟", "♥️",
            "😻", "🥰", "😍", "😘", "💋", "💌", "🫶", "💐", "🌹", "🌷"
        )
    ),
    THINGS(
        "Objects and symbols", "🔥",
        listOf(
            "🔥", "✨", "🎉", "🎊", "🎁", "🏆", "⭐", "🌟", "💯", "✅",
            "❌", "⚡", "☀️", "🌙", "🌈", "❄️", "💧", "🍀", "🌸", "🎵",
            "☕", "🍵", "🍕", "🍔", "🍟", "🍰", "🎂", "🍎", "🍌", "🍇",
            "⚽", "🏀", "🏏", "🎮", "🎧", "📱", "💻", "📷", "💰", "🎯",
            "✈️", "🚗", "🏠", "🏥", "🎓", "📚", "⏰", "🔔", "🔑", "🚀"
        )
    )
}
