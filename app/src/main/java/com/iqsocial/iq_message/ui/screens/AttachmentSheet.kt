package com.iqsocial.iq_message.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iqsocial.iq_message.ui.components.HairlineDivider
import com.iqsocial.iq_message.ui.components.SheetHandle
import com.iqsocial.iq_message.ui.icons.IqIcons
import com.iqsocial.iq_message.ui.theme.Crimson
import com.iqsocial.iq_message.ui.theme.FieldGrey
import com.iqsocial.iq_message.ui.theme.Hairline
import com.iqsocial.iq_message.ui.theme.Ink
import com.iqsocial.iq_message.ui.theme.IqTheme

/**
 * Gradient stand-ins for the gallery thumbnails in the mockup — the palettes
 * echo the original photo set (city dusk, cafe, beach, jungle, food, monument…).
 */
private val galleryTiles = listOf(
    Color(0xFFB08155) to Color(0xFF5A4230),
    Color(0xFFD8A87C) to Color(0xFF6B4B34),
    Color(0xFFF2B45C) to Color(0xFF9E4E2C),
    Color(0xFF7FBF6A) to Color(0xFF23502A),
    Color(0xFFE8A33D) to Color(0xFFA33B22),
    Color(0xFF9FC7E8) to Color(0xFF7A6248),
    Color(0xFFBFC7CC) to Color(0xFF5C6367),
    Color(0xFF5FC3D6) to Color(0xFF1F6E7E),
    Color(0xFFF07A3C) to Color(0xFFD4356B),
    Color(0xFFB9C4C9) to Color(0xFF5D686D),
    Color(0xFF6FB8E8) to Color(0xFF255C8A),
    Color(0xFFE85D9B) to Color(0xFF8E2560)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentSheet(
    onDismiss: () -> Unit,
    onSend: (Int) -> Unit
) {
    val d = IqTheme.dimens
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selected = remember { mutableStateListOf(1, 5, 10) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        dragHandle = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = d.gapMd),
                contentAlignment = Alignment.Center
            ) { SheetHandle() }
        }
    ) {
        BoxWithConstraints(Modifier.fillMaxHeight(0.72f)) {
            // Wider screens get more columns instead of giant thumbnails.
            val columns = when {
                maxWidth >= 720.dp -> 5
                maxWidth >= 520.dp -> 4
                else -> 3
            }

            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = d.gutter, vertical = d.gapMd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Recent",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Ink,
                        maxLines = 1
                    )
                    Spacer(Modifier.width(d.gapSm))
                    Icon(IqIcons.ChevronDown, null, Modifier.size(d.icon), tint = Ink)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Gallery",
                        style = MaterialTheme.typography.titleLarge,
                        color = Ink,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1
                    )
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = d.gapSm),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    QuickAction(IqIcons.DocumentOutline, "Document", Modifier.weight(1f))
                    QuickAction(IqIcons.CameraOutline, "Camera", Modifier.weight(1f))
                    QuickAction(IqIcons.LocationPin, "Location", Modifier.weight(1f))
                    QuickAction(IqIcons.Waveform, "Audio", Modifier.weight(1f))
                }

                Spacer(Modifier.height(d.gapMd))
                HairlineDivider()

                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    contentPadding = PaddingValues(d.gapSm),
                    horizontalArrangement = Arrangement.spacedBy(d.gapXs + 2.dp),
                    verticalArrangement = Arrangement.spacedBy(d.gapXs + 2.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(galleryTiles) { index, (top, bottom) ->
                        val isSelected = selected.contains(index)
                        Box(
                            Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(d.radiusSm))
                                .background(Brush.verticalGradient(listOf(top, bottom)))
                                .then(
                                    if (isSelected)
                                        Modifier.border(2.5.dp, Crimson, RoundedCornerShape(d.radiusSm))
                                    else Modifier
                                )
                                .clickable {
                                    if (isSelected) selected.remove(index) else selected.add(index)
                                }
                        ) {
                            if (isSelected) {
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(d.gapXs + 2.dp)
                                        .size(d.scaled(26))
                                        .clip(CircleShape)
                                        .background(Crimson),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        IqIcons.Check, "Selected",
                                        Modifier.size(d.iconXs), tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (selected.isNotEmpty()) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = d.gutter, bottom = d.gapXl)
                ) {
                    Box(
                        Modifier
                            .size(d.fab)
                            .clip(CircleShape)
                            .background(Crimson)
                            .clickable { onSend(selected.size) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            IqIcons.PaperPlane, "Send",
                            Modifier.size(d.iconLg), tint = Color.White
                        )
                    }
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(d.scaled(26))
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(1.dp, Hairline, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            selected.size.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Ink
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, modifier: Modifier = Modifier) {
    val d = IqTheme.dimens
    Column(
        modifier
            .clip(RoundedCornerShape(d.radiusMd))
            .clickable { }
            .padding(vertical = d.gapSm),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(d.scaled(56))
                .clip(CircleShape)
                .background(FieldGrey),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(d.iconLg), tint = Ink)
        }
        Spacer(Modifier.height(d.gapSm))
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
