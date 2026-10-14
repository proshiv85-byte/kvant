// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.proshiv85_byte.kvant.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Material 3 "wavy" seek bar: a sine wave up to the thumb, a flat track after it and a
 * stop indicator at the end. Same drawing as the desktop app's WavyProgress.
 *
 * @param amplitude wave height in dp, @param wavelength in dp, @param speed waves per second.
 */
@Composable
fun WavySeekBar(
    value: Float,
    playing: Boolean,
    squiggle: Boolean,
    amplitude: Float,
    wavelength: Float,
    speed: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    onDrag: (Float?) -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    var dragValue by remember { mutableStateOf<Float?>(null) }
    val shown = dragValue ?: value

    val amp by animateFloatAsState(
        if (squiggle && playing) amplitude else 0f, tween(450, easing = FastOutSlowInEasing), label = "amplitude",
    )
    val thumbWidth by animateFloatAsState(if (dragValue != null) 2f else 4f, tween(120), label = "thumbWidth")
    val thumbHeight by animateFloatAsState(if (dragValue != null) 28f else 24f, tween(120), label = "thumbHeight")

    // Advance the phase every frame so speed changes apply instantly (no restart/jump).
    var phase by remember { mutableFloatStateOf(0f) }
    val currentSpeed by rememberUpdatedState(speed)
    val moving = amp > 0f && speed > 0f
    LaunchedEffect(moving) {
        if (!moving) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                phase = (phase + (now - last) / 1e9f * currentSpeed) % 1f
                last = now
            }
        }
    }

    val seek by rememberUpdatedState(onSeek)
    val drag by rememberUpdatedState(onDrag)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
                if (interactive) setProgress { seek(it.coerceIn(0f, 1f)); true }
            }
            .then(
                if (!interactive) Modifier else Modifier.pointerInput(Unit) {
                    fun at(x: Float) = ((x - 2.dp.toPx()) / (size.width - 4.dp.toPx())).coerceIn(0f, 1f)
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        dragValue = at(down.position.x)
                        drag(dragValue)
                        horizontalDrag(down.id) { change ->
                            dragValue = at(change.position.x)
                            drag(dragValue)
                            change.consume()
                        }
                        dragValue?.let { seek(it) }
                        dragValue = null
                        drag(null)
                    }
                }
            )
    ) {
        val stroke = 4.dp.toPx()
        val half = stroke / 2
        val gap = 6.dp.toPx()
        val cy = size.height / 2
        val start = half
        val end = size.width - half
        val thumbX = start + (end - start) * shown
        val tw = thumbWidth.dp.toPx()
        val th = thumbHeight.dp.toPx()

        // Active (wavy) part; the wave eases in over the first half wavelength.
        val activeEnd = thumbX - tw / 2 - gap - half
        if (activeEnd > start) {
            val wl = wavelength.dp.toPx()
            val a = amp.dp.toPx()
            val k = 2f * PI.toFloat() / wl
            val ramp = wl / 2
            fun y(x: Float) = cy + a * min(1f, (x - start) / ramp) * sin(k * x - 2f * PI.toFloat() * phase)
            val path = Path().apply {
                moveTo(start, y(start))
                var x = start + 1f
                while (x < activeEnd) {
                    lineTo(x, y(x))
                    x += 1.5f
                }
                lineTo(activeEnd, y(activeEnd))
            }
            drawPath(path, colors.primary, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        // Inactive (flat) part and stop indicator.
        val inactiveStart = thumbX + tw / 2 + gap + half
        if (inactiveStart < end) {
            drawLine(colors.secondaryContainer, Offset(inactiveStart, cy), Offset(end, cy), stroke, StrokeCap.Round)
            if (end - inactiveStart > stroke * 2) {
                drawCircle(colors.primary, radius = stroke / 4 + 0.5f, center = Offset(end - half, cy))
            }
        }

        // Thumb: a vertical pill.
        drawRoundRect(
            colors.primary,
            topLeft = Offset(thumbX - tw / 2, cy - th / 2),
            size = Size(tw, th),
            cornerRadius = CornerRadius(tw / 2),
        )
    }
}
