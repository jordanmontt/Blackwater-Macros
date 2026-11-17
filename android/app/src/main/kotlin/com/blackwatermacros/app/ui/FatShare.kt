package com.blackwatermacros.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blackwatermacros.app.R
import com.blackwatermacros.app.core.FAT_SHARE_MAX
import com.blackwatermacros.app.core.FAT_SHARE_MIN
import com.blackwatermacros.app.core.FatShareCheck
import com.blackwatermacros.app.core.FatVerdict

/**
 * Progreso's macro card: one line when the period's fat is outside 20–35 % of
 * calories (`checkFatShare`), with «Why?» opening the user's numbers and the
 * reasons, then the methodology's [FAT_SECTION]. Mirrors web `fat-share.tsx`.
 */
@Composable
internal fun FatShareNotice(check: FatShareCheck, onOpenMethodology: (String?) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val low = check.verdict == FatVerdict.LOW
    // Text-level contrast: darker amber on the light theme, lighter on the dark one.
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val iconTint = if (!low) MaterialTheme.colorScheme.onSurfaceVariant else if (dark) Color(0xFFE8B84A) else Color(0xFF7A5200)
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.padding(top = 10.dp)) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = iconTint, modifier = Modifier.padding(top = 1.dp).size(16.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(noticeText(check), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                TextButton(
                    onClick = { open = true },
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.offset(x = (-8).dp),
                ) {
                    Text(stringResource(R.string.check_why), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
    if (open) {
        FatShareSheet(
            check = check,
            onDismiss = { open = false },
            onOpenMethodology = { open = false; onOpenMethodology(FAT_SECTION) },
        )
    }
}

@Composable
private fun noticeText(check: FatShareCheck): String = stringResource(
    if (check.verdict == FatVerdict.LOW) R.string.progress_fat_low else R.string.progress_fat_high,
    formatNumber(check.share),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FatShareSheet(check: FatShareCheck, onDismiss: () -> Unit, onOpenMethodology: () -> Unit) {
    val low = check.verdict == FatVerdict.LOW
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.progress_fat_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(noticeText(check), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                stringResource(
                    R.string.progress_fat_numbers,
                    formatNumber(check.fat),
                    formatNumber(check.fatKcal),
                    formatNumber(check.macroKcal),
                    formatNumber(check.share),
                ),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
            FatShareBar(check.share)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(if (low) R.string.progress_fat_low_why else R.string.progress_fat_high_why),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(if (low) R.string.progress_fat_low_what else R.string.progress_fat_high_what),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onOpenMethodology) { Text(stringResource(R.string.progress_fat_how_it_works)) }
        }
    }
}

/** 0–100 % of calories: the shaded band is 20–35 %, the dot is the period's fat. */
@Composable
private fun FatShareBar(share: Double) {
    val range = "${formatNumber(FAT_SHARE_MIN)}–${formatNumber(FAT_SHARE_MAX)} %"
    val rangeLabel = stringResource(R.string.label_value, stringResource(R.string.progress_fat_range), range)
    val description = "$rangeLabel. ${stringResource(R.string.macro_fat)}: ${formatNumber(share)} %."
    val primary = MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .semantics { contentDescription = description },
        ) {
            val width = maxWidth
            Box(Modifier.fillMaxWidth().height(1.dp).offset(y = 14.dp).background(MaterialTheme.colorScheme.outlineVariant))
            Box(
                Modifier
                    .offset(x = width * (FAT_SHARE_MIN / 100).toFloat())
                    .width(width * ((FAT_SHARE_MAX - FAT_SHARE_MIN) / 100).toFloat())
                    .fillMaxHeight()
                    .background(primary.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                    .border(1.dp, primary.copy(alpha = 0.6f), RoundedCornerShape(6.dp)),
            )
            val dot = 12.dp
            Box(
                Modifier
                    .offset(x = width * (share.coerceIn(2.0, 98.0) / 100).toFloat() - dot / 2, y = 14.dp - dot / 2)
                    .size(dot)
                    .background(MaterialTheme.colorScheme.onSurface, CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
            )
        }
        Row(Modifier.fillMaxWidth()) {
            Text("0 %", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("100 %", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row {
            Box(
                Modifier
                    .padding(top = 3.dp)
                    .size(width = 16.dp, height = 10.dp)
                    .background(primary.copy(alpha = 0.25f), RoundedCornerShape(2.dp))
                    .border(1.dp, primary.copy(alpha = 0.6f), RoundedCornerShape(2.dp)),
            )
            Spacer(Modifier.width(8.dp))
            Text(rangeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
