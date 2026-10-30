package com.iqsocial.iq_message.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iqsocial.iq_message.data.Person
import com.iqsocial.iq_message.data.call.CallEngine
import com.iqsocial.iq_message.ui.components.Avatar
import com.iqsocial.iq_message.ui.components.CallVideo
import com.iqsocial.iq_message.ui.theme.Crimson
import com.iqsocial.iq_message.ui.theme.IqTheme

/**
 * Everyone else on the call, laid out.
 *
 * Rows of at most two, which is what the shape of a phone decides rather than a
 * preference: a portrait screen split into columns gives each person a sliver, and
 * split into rows of two it gives them a rectangle roughly the shape of a face. Two
 * people get one each, three and four get a two-by-two, and beyond that the rows
 * keep going and the tiles get shorter - which is the point at which a call is a
 * meeting and nobody is looking closely at any one tile.
 *
 * A camera that is off is still a tile. Dropping it would make somebody vanish from
 * the call by covering their lens, and the person who muted their camera is still
 * talking.
 */
@Composable
fun CallGrid(
    feeds: List<CallEngine.RemoteFeed>,
    modifier: Modifier = Modifier
) {
    val d = IqTheme.dimens
    val rows = feeds.chunked(2)

    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(d.gapXs)
    ) {
        rows.forEach { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    // Every row takes an equal share of the height, so a lone person
                    // on the last row is as tall as the pair above rather than
                    // stretched down the rest of the screen.
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(d.gapXs)
            ) {
                row.forEach { feed ->
                    CallTile(feed, Modifier.weight(1f).fillMaxSize())
                }
                // An odd count leaves a gap rather than a double-width tile: a face
                // stretched across two columns is the only one on screen that is a
                // different shape from the rest.
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

/** One person: their camera, or their face when it is off. */
@Composable
private fun CallTile(feed: CallEngine.RemoteFeed, modifier: Modifier = Modifier) {
    val d = IqTheme.dimens
    val speaking = feed.uid in CallEngine.speaking

    Box(
        modifier
            .clip(RoundedCornerShape(d.radiusLg))
            .background(Color.Black)
            // Who is talking, which in a grid is the one thing a still picture
            // cannot say. A ring rather than a highlight, so it reads at a glance
            // without changing what the tile shows.
            .then(
                if (speaking) {
                    Modifier.border(2.dp, Crimson, RoundedCornerShape(d.radiusLg))
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        val video = feed.video
        if (video != null) {
            CallVideo(track = video, modifier = Modifier.fillMaxSize())
        } else {
            Avatar(
                person = Person(
                    id = feed.uid,
                    name = feed.name.ifBlank { "?" },
                    handle = ""
                ),
                size = d.avatarLg
            )
        }

        if (feed.name.isNotBlank()) {
            Text(
                feed.name,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(d.gapXs)
                    .clip(RoundedCornerShape(d.radiusSm))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = d.gapXs)
            )
        }
    }
}
