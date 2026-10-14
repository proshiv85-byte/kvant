// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.proshiv85_byte.kvant.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import io.github.proshiv85_byte.kvant.PlayerUi
import io.github.proshiv85_byte.kvant.QueueSide
import io.github.proshiv85_byte.kvant.R
import io.github.proshiv85_byte.kvant.Settings
import io.github.proshiv85_byte.kvant.Tracks

class NowPlayingActions(
    val toggleQueue: () -> Unit,
    val openFiles: () -> Unit,
    val openFolder: () -> Unit,
    val openSettings: () -> Unit,
    val toggle: () -> Unit,
    val next: () -> Unit,
    val previous: () -> Unit,
    val seek: (Float) -> Unit,
)

@Composable
fun NowPlaying(
    ui: PlayerUi,
    position: Long,
    cover: ImageBitmap?,
    settings: Settings,
    queueShown: Boolean,
    actions: NowPlayingActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        TopBar(settings, queueShown, actions)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val landscape = maxWidth > maxHeight * 1.2f && maxHeight < 520.dp
            if (landscape) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AnimatedCover(ui, cover, Modifier.fillMaxHeight().aspectRatio(1f))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                        Details(ui, position, settings, actions)
                    }
                }
            } else {
                Column(
                    Modifier
                        .align(Alignment.TopCenter)
                        .widthIn(max = 520.dp)
                        .fillMaxSize()
                        .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        val side = min(maxWidth, maxHeight)
                        AnimatedCover(ui, cover, Modifier.size(side - 8.dp))
                    }
                    Spacer(Modifier.height(24.dp))
                    Details(ui, position, settings, actions)
                }
            }
        }
    }
}

@Composable
private fun TopBar(settings: Settings, queueShown: Boolean, actions: NowPlayingActions) {
    val right = settings.queueSide == QueueSide.Right
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // The queue button sits on the same side as the queue (nudging towards it).
    val queueButton = @Composable {
        KIconButton(
            KvantIcons.Queue, stringResource(R.string.queue), actions.toggleQueue,
            variant = if (queueShown) ButtonVariant.Tonal else ButtonVariant.Standard,
            litNudge = if (right != rtl) 3.dp else (-3).dp,
        )
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!right) queueButton()
        Spacer(Modifier.weight(1f))
        KIconButton(KvantIcons.File, stringResource(R.string.open_files), actions.openFiles, litRotation = -10f)
        KIconButton(KvantIcons.Folder, stringResource(R.string.open_folder), actions.openFolder, litRotation = 10f)
        KIconButton(KvantIcons.Settings, stringResource(R.string.settings), actions.openSettings, litRotation = 90f)
        if (right) queueButton()
    }
}

@Composable
private fun AnimatedCover(ui: PlayerUi, cover: ImageBitmap?, modifier: Modifier) {
    val scale by animateFloatAsState(
        if (ui.playing || !ui.hasTrack) 1f else 0.92f,
        spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow), label = "coverScale",
    )
    Box(modifier.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }) {
        Cover(cover, Modifier.fillMaxSize(), corner = 28.dp)
    }
}

@Composable
private fun ColumnScope.Details(ui: PlayerUi, position: Long, settings: Settings, actions: NowPlayingActions) {
    val colors = MaterialTheme.colorScheme
    Text(
        if (ui.hasTrack) ui.title else stringResource(R.string.nothing_playing),
        modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2000),
        textAlign = TextAlign.Center, maxLines = 1,
        color = colors.onSurface, fontSize = 24.sp, fontWeight = FontWeight.Medium,
    )
    Text(
        if (ui.hasTrack) listOf(ui.artist, ui.album).filter { it.isNotEmpty() }.joinToString("  ·  ")
        else stringResource(R.string.empty_hint),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
        color = colors.onSurfaceVariant, fontSize = 16.sp,
    )

    if (!ui.hasTrack) {
        Row(
            Modifier.align(Alignment.CenterHorizontally).padding(vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KTextButton(stringResource(R.string.open_files), actions.openFiles, icon = KvantIcons.File, variant = ButtonVariant.Filled)
            KTextButton(stringResource(R.string.open_folder), actions.openFolder, icon = KvantIcons.Folder)
        }
        return
    }

    var dragging by remember { mutableStateOf<Float?>(null) }
    val fraction = if (ui.durationMs > 0) (position.toFloat() / ui.durationMs).coerceIn(0f, 1f) else 0f
    WavySeekBar(
        value = fraction,
        playing = ui.playing,
        squiggle = settings.squiggle,
        amplitude = settings.amplitude,
        wavelength = settings.wavelength,
        speed = settings.speed,
        onSeek = actions.seek,
        onDrag = { dragging = it },
        interactive = ui.seekable,
        modifier = Modifier.padding(top = 16.dp),
    )
    Row(Modifier.fillMaxWidth()) {
        val tnum = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum", fontSize = 13.sp, color = colors.onSurfaceVariant)
        Text(Tracks.formatTime(dragging?.let { (it * ui.durationMs).toLong() } ?: position), style = tnum)
        Spacer(Modifier.weight(1f))
        Text(Tracks.formatTime(ui.durationMs), style = tnum)
    }
    Row(
        Modifier.align(Alignment.CenterHorizontally).padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KIconButton(
            KvantIcons.Previous, stringResource(R.string.previous), actions.previous,
            variant = ButtonVariant.Tonal, size = 64.dp, iconSize = 30.dp, litNudge = (-4).dp,
        )
        PlayButton(ui.playing, actions.toggle)
        KIconButton(
            KvantIcons.Next, stringResource(R.string.next), actions.next,
            variant = ButtonVariant.Tonal, size = 64.dp, iconSize = 30.dp, litNudge = 4.dp, enabled = ui.canNext,
        )
    }
}
