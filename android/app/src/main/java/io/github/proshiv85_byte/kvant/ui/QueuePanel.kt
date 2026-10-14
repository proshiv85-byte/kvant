// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.proshiv85_byte.kvant.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.proshiv85_byte.kvant.PlayerUi
import io.github.proshiv85_byte.kvant.QueueEntry
import io.github.proshiv85_byte.kvant.R
import io.github.proshiv85_byte.kvant.Tracks

class QueueActions(
    val play: (Int) -> Unit,
    val remove: (Int) -> Unit,
    val addFiles: () -> Unit,
    val addFolder: () -> Unit,
    val clear: () -> Unit,
)

@Composable
fun QueuePanel(ui: PlayerUi, actions: QueueActions, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val list = rememberLazyListState()

    // Keep the current track in view.
    LaunchedEffect(ui.current) {
        if (ui.current < 0) return@LaunchedEffect
        val visible = list.layoutInfo.visibleItemsInfo
        if (visible.none { it.index == ui.current && it.offset >= 0 }) list.animateScrollToItem(ui.current)
    }

    Column(modifier.clip(RoundedCornerShape(24.dp)).background(colors.surfaceContainerLow).padding(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.queue), color = colors.onSurface, fontSize = 22.sp)
                Text(
                    pluralStringResource(R.plurals.tracks, ui.queue.size, ui.queue.size),
                    color = colors.onSurfaceVariant, fontSize = 13.sp,
                )
            }
            KIconButton(KvantIcons.Add, stringResource(R.string.add_files), actions.addFiles, size = 44.dp, litRotation = 90f)
            KIconButton(KvantIcons.Folder, stringResource(R.string.add_folder), actions.addFolder, size = 44.dp, litRotation = 10f)
            KIconButton(
                KvantIcons.Clear, stringResource(R.string.clear_queue), actions.clear,
                size = 44.dp, enabled = ui.queue.isNotEmpty(), litNudge = 3.dp,
            )
        }

        if (ui.queue.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.queue_empty), color = colors.onSurfaceVariant, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                state = list,
                modifier = Modifier.fillMaxSize().padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(ui.queue, key = { _, e -> e.id }) { index, entry ->
                    QueueRow(
                        index = index,
                        entry = entry,
                        current = index == ui.current,
                        playing = ui.playing,
                        onClick = { actions.play(index) },
                        onRemove = { actions.remove(index) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueRow(
    index: Int,
    entry: QueueEntry,
    current: Boolean,
    playing: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val remove by rememberUpdatedState(onRemove)
    // Swipe a track away (either direction) to remove it from the queue.
    val swipe = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) remove()
            value != SwipeToDismissBoxValue.Settled
        },
    )
    val highlight by animateColorAsState(
        if (current) colors.secondaryContainer else colors.surfaceContainerLow, tween(250), label = "rowBg",
    )
    val onRow = if (current) colors.onSecondaryContainer else colors.onSurface

    SwipeToDismissBox(
        state = swipe,
        modifier = modifier,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(colors.errorContainer).padding(horizontal = 20.dp),
                contentAlignment = if (swipe.dismissDirection == SwipeToDismissBoxValue.EndToStart) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Icon(KvantIcons.Delete, stringResource(R.string.remove), tint = colors.onErrorContainer)
            }
        },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(highlight)
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                if (current) Equalizer(playing, colors.onSecondaryContainer)
                else Text("${index + 1}", color = colors.onSurfaceVariant, fontSize = 13.sp)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    entry.title, color = onRow, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                )
                if (entry.artist.isNotEmpty()) {
                    Text(
                        entry.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp,
                        color = if (current) colors.onSecondaryContainer.copy(alpha = 0.8f) else colors.onSurfaceVariant,
                    )
                }
            }
            if (entry.durationMs > 0) {
                Text(
                    Tracks.formatTime(entry.durationMs), fontSize = 13.sp,
                    color = if (current) colors.onSecondaryContainer else colors.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
        }
    }
}

/** Little bouncing bars for the current track; they settle when paused. */
@Composable
private fun Equalizer(playing: Boolean, color: androidx.compose.ui.graphics.Color) {
    val transition = rememberInfiniteTransition(label = "eq")
    Row(Modifier.height(16.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        repeat(3) { i ->
            val bounce by transition.animateFloat(
                initialValue = 5f + i, targetValue = 16f - i * 3,
                animationSpec = infiniteRepeatable(tween(280 + i * 90), RepeatMode.Reverse), label = "bar$i",
            )
            val h by animateFloatAsState(if (playing) bounce else 6f, label = "barHeight$i")
            Spacer(
                Modifier
                    .width(4.dp)
                    .height(h.dp)
                    .graphicsLayer { clip = true; shape = RoundedCornerShape(2.dp) }
                    .background(color)
            )
        }
    }
}
