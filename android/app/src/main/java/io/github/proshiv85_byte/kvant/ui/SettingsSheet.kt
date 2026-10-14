// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.proshiv85_byte.kvant.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.proshiv85_byte.kvant.QueueSide
import io.github.proshiv85_byte.kvant.R
import io.github.proshiv85_byte.kvant.Settings
import io.github.proshiv85_byte.kvant.ThemeMode

private val swatches = listOf(
    0xFF6750A4, 0xFF0061A4, 0xFF006A6A, 0xFF386A20,
    0xFF7C5800, 0xFF9C4400, 0xFFB3261E, 0xFF984061,
).map { it.toInt() }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsSheet(settings: Settings, onChange: ((Settings) -> Settings) -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    // Sliders edit a local draft and save when released, so dragging stays smooth.
    var draft by remember { mutableStateOf(settings) }
    LaunchedEffect(settings) { draft = settings }
    fun commit(s: Settings) {
        draft = s
        onChange { s }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surfaceContainerHigh,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Text(stringResource(R.string.settings), color = colors.onSurface, fontSize = 24.sp)

            Section(stringResource(R.string.theme))
            val modes = listOf(R.string.theme_system to ThemeMode.System, R.string.theme_light to ThemeMode.Light, R.string.theme_dark to ThemeMode.Dark)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                modes.forEachIndexed { i, (label, mode) ->
                    SegmentedButton(
                        selected = draft.themeMode == mode,
                        onClick = { commit(draft.copy(themeMode = mode)) },
                        shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                    ) { Text(stringResource(label)) }
                }
            }

            Section(stringResource(R.string.accent_color))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                swatches.forEach { argb ->
                    Swatch(Color(argb), selected = draft.accent == argb) { commit(draft.copy(accent = argb)) }
                }
            }
            HexField(draft.accent) { commit(draft.copy(accent = it)) }

            Section(stringResource(R.string.queue_position))
            val sides = listOf(R.string.side_left to QueueSide.Left, R.string.side_right to QueueSide.Right)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                sides.forEachIndexed { i, (label, side) ->
                    SegmentedButton(
                        selected = draft.queueSide == side,
                        onClick = { commit(draft.copy(queueSide = side)) },
                        shape = SegmentedButtonDefaults.itemShape(i, sides.size),
                    ) { Text(stringResource(label)) }
                }
            }

            Section(stringResource(R.string.progress_bar))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.squiggly_line), Modifier.weight(1f), color = colors.onSurface, fontSize = 16.sp)
                Switch(
                    checked = draft.squiggle,
                    onCheckedChange = { commit(draft.copy(squiggle = it)) },
                    thumbContent = if (draft.squiggle) {
                        { Icon(KvantIcons.Check, null, Modifier.size(16.dp)) }
                    } else null,
                )
            }
            // Live preview
            WavySeekBar(
                value = 0.6f, playing = true, squiggle = draft.squiggle,
                amplitude = draft.amplitude, wavelength = draft.wavelength, speed = draft.speed,
                onSeek = {}, interactive = false, modifier = Modifier.padding(vertical = 8.dp),
            )
            Column(Modifier.graphicsLayer { alpha = if (draft.squiggle) 1f else 0.38f }) {
                ValueSlider(stringResource(R.string.height), draft.amplitude, 1f..8f, "%.1f dp", draft.squiggle,
                    onChange = { draft = draft.copy(amplitude = it) }, onDone = { commit(draft) })
                ValueSlider(stringResource(R.string.wavelength), draft.wavelength, 16f..80f, "%.0f dp", draft.squiggle,
                    onChange = { draft = draft.copy(wavelength = it) }, onDone = { commit(draft) })
                ValueSlider(stringResource(R.string.speed), draft.speed, 0f..3f, "%.1f×", draft.squiggle,
                    onChange = { draft = draft.copy(speed = it) }, onDone = { commit(draft) })
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 24.dp, bottom = 10.dp),
    )
}

@Composable
private fun Swatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateDpAsState(if (selected) 14.dp else 22.dp, spring(dampingRatio = 0.5f), label = "swatchCorner")
    val scale by animateFloatAsState(
        if (pressed) 0.88f else 1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium), label = "swatchScale",
    )
    val checkScale by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.45f), label = "check")
    Box(
        Modifier
            .size(44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(corner))
            .background(color)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(KvantIcons.Check, null, tint = Color.White, modifier = Modifier.size(22.dp).graphicsLayer {
            scaleX = checkScale
            scaleY = checkScale
            alpha = checkScale.coerceIn(0f, 1f)
        })
    }
}

@Composable
private fun HexField(accent: Int, onAccent: (Int) -> Unit) {
    fun hexOf(argb: Int) = "#%06X".format(argb and 0xFFFFFF)
    var text by remember(accent) { mutableStateOf(hexOf(accent)) }
    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color(accent)))
        Spacer(Modifier.size(12.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { input ->
                val clean = "#" + input.uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                text = clean
                if (clean.length == 7) onAccent(("FF" + clean.drop(1)).toLong(16).toInt())
            },
            singleLine = true,
            placeholder = { Text("#RRGGBB") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ValueSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: String,
    enabled: Boolean,
    onChange: (Float) -> Unit,
    onDone: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.padding(top = 4.dp)) {
        Row {
            Text(label, Modifier.weight(1f), color = colors.onSurface, fontSize = 15.sp)
            Text(format.format(value), color = colors.onSurfaceVariant, fontSize = 13.sp)
        }
        // No steps: the handle follows the finger continuously.
        Slider(value = value, onValueChange = onChange, onValueChangeFinished = onDone, valueRange = range, enabled = enabled,
            modifier = Modifier.height(40.dp))
    }
}
