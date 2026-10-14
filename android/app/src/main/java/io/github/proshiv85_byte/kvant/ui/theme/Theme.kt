// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.proshiv85_byte.kvant.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

@Composable
fun KvantTheme(seed: Color, dark: Boolean, content: @Composable () -> Unit) {
    val target = remember(seed, dark) { Palette.scheme(seed, dark) }
    MaterialTheme(colorScheme = animated(target), content = content)
}

// Cross-fade the whole scheme when the accent or light/dark mode changes.
@Composable
private fun animated(s: ColorScheme): ColorScheme {
    @Composable
    fun a(c: Color) = animateColorAsState(c, tween(400), label = "scheme").value
    return s.copy(
        primary = a(s.primary), onPrimary = a(s.onPrimary),
        primaryContainer = a(s.primaryContainer), onPrimaryContainer = a(s.onPrimaryContainer),
        secondary = a(s.secondary), onSecondary = a(s.onSecondary),
        secondaryContainer = a(s.secondaryContainer), onSecondaryContainer = a(s.onSecondaryContainer),
        tertiary = a(s.tertiary), onTertiary = a(s.onTertiary),
        surface = a(s.surface), onSurface = a(s.onSurface), onSurfaceVariant = a(s.onSurfaceVariant),
        background = a(s.background), onBackground = a(s.onBackground),
        surfaceContainerLow = a(s.surfaceContainerLow), surfaceContainer = a(s.surfaceContainer),
        surfaceContainerHigh = a(s.surfaceContainerHigh), surfaceContainerHighest = a(s.surfaceContainerHighest),
        outline = a(s.outline), outlineVariant = a(s.outlineVariant),
        inverseSurface = a(s.inverseSurface), inverseOnSurface = a(s.inverseOnSurface),
    )
}
