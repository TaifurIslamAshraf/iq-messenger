package com.iqsocial.iq_message.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iqsocial.iq_message.data.Country
import com.iqsocial.iq_message.ui.icons.IqIcons
import com.iqsocial.iq_message.ui.theme.Crimson
import com.iqsocial.iq_message.ui.theme.CrimsonTint
import com.iqsocial.iq_message.ui.theme.FieldGrey
import com.iqsocial.iq_message.ui.theme.Ink
import com.iqsocial.iq_message.ui.theme.InkFaint
import com.iqsocial.iq_message.ui.theme.InkMuted
import com.iqsocial.iq_message.ui.theme.IqTheme

/**
 * The country flag, drawn from the ISO code's regional-indicator emoji.
 *
 * No fill behind it: a backstop chip reads as a grey box around every flag, which
 * is worse than the rare case of a build whose font lacks the glyph.
 */
@Composable
fun CountryFlag(country: Country, modifier: Modifier = Modifier) {
    val d = IqTheme.dimens
    Box(
        modifier
            .size(width = d.scaled(28), height = d.scaled(20))
            .clip(RoundedCornerShape(d.scaled(3))),
        contentAlignment = Alignment.Center
    ) {
        Text(text = country.flag, fontSize = d.scaled(18).value.sp, maxLines = 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountryPickerSheet(
    selected: Country,
    onDismiss: () -> Unit,
    onSelect: (Country) -> Unit
) {
    val d = IqTheme.dimens
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val results = remember(query) { Country.search(query) }

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
        Column(Modifier.fillMaxHeight(0.9f)) {
            Text(
                "Select country",
                style = MaterialTheme.typography.headlineMedium,
                color = Ink,
                modifier = Modifier.padding(horizontal = d.gutter, vertical = d.gapMd)
            )

            Row(
                Modifier
                    .padding(horizontal = d.gutter)
                    .fillMaxWidth()
                    .height(d.fieldHeight)
                    .clip(RoundedCornerShape(d.radiusMd))
                    .background(FieldGrey)
                    .padding(horizontal = d.gapLg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(IqIcons.Search, null, Modifier.size(d.icon), tint = InkMuted)
                Spacer(Modifier.width(d.gapMd))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            "Country or code",
                            style = MaterialTheme.typography.bodyLarge,
                            color = InkFaint,
                            maxLines = 1
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
                        cursorBrush = SolidColor(Crimson),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(d.gapSm))
            HairlineDivider()

            LazyColumn(
                Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = d.gapXxl)
            ) {
                items(results, key = { it.iso + it.dialCode }) { country ->
                    val isSelected = country.iso == selected.iso
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(country) }
                            .background(if (isSelected) CrimsonTint.copy(alpha = 0.45f) else Color.Transparent)
                            .heightIn(min = 56.dp)
                            .padding(horizontal = d.gutter, vertical = d.gapSm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CountryFlag(country)
                        Spacer(Modifier.width(d.gapMd))
                        Text(
                            country.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(d.gapSm))
                        Text(
                            country.dialCode,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isSelected) Crimson else InkMuted,
                            maxLines = 1
                        )
                        if (isSelected) {
                            Spacer(Modifier.width(d.gapSm))
                            Icon(IqIcons.Check, null, Modifier.size(d.iconSm), tint = Crimson)
                        }
                    }
                    HairlineDivider(Modifier.padding(start = d.gutter + d.scaled(30) + d.gapMd))
                }

                if (results.isEmpty()) {
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(d.gapXxl),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No country matches \"$query\"",
                                style = MaterialTheme.typography.bodyLarge,
                                color = InkMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Row order used by the field: flag, dial code, chevron. */
@Composable
fun CountryChip(
    country: Country,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val d = IqTheme.dimens
    Row(
        modifier
            .clip(RoundedCornerShape(d.radiusSm))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = d.gapXs, vertical = d.gapXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(d.gapXs)
    ) {
        CountryFlag(country)
        Text(
            country.dialCode,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = Ink,
            maxLines = 1
        )
        Icon(IqIcons.ChevronDown, null, Modifier.size(d.iconXs), tint = InkMuted)
    }
}
