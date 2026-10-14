// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.proshiv85_byte.kvant.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/**
 * Material 3 color scheme generated from a single seed color.
 * Tonal palettes are built in OKLCH; "tone" is matched to CIE L* like HCT does.
 * This is a direct port of the desktop app's src/theme.cpp, so both apps produce the
 * same colors for the same accent.
 */
object Palette {

    private class Rgb(val r: Double, val g: Double, val b: Double) // linear sRGB

    private fun toLinear(c: Double) = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    private fun fromLinear(c: Double) = if (c <= 0.0031308) c * 12.92 else 1.055 * c.pow(1.0 / 2.4) - 0.055

    private fun oklabToLinear(l: Double, a: Double, b: Double): Rgb {
        val l_ = l + 0.3963377774 * a + 0.2158037573 * b
        val m_ = l - 0.1055613458 * a - 0.0638541728 * b
        val s_ = l - 0.0894841775 * a - 1.2914855480 * b
        val ll = l_ * l_ * l_
        val mm = m_ * m_ * m_
        val ss = s_ * s_ * s_
        return Rgb(
            +4.0767416621 * ll - 3.3077115913 * mm + 0.2309699292 * ss,
            -1.2684380046 * ll + 2.6097574011 * mm - 0.3413193965 * ss,
            -0.0041960863 * ll - 0.7034186147 * mm + 1.7076147010 * ss,
        )
    }

    /** Returns (L, C, h) in OKLCH, h in radians. */
    private fun linearToOklch(c: Rgb): Triple<Double, Double, Double> {
        val l = cbrt(0.4122214708 * c.r + 0.5363325363 * c.g + 0.0514459929 * c.b)
        val m = cbrt(0.2119034982 * c.r + 0.6806995451 * c.g + 0.1073969566 * c.b)
        val s = cbrt(0.0883024619 * c.r + 0.2817188376 * c.g + 0.6299787005 * c.b)
        val bigL = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s
        val a = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s
        val b = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
        return Triple(bigL, hypot(a, b), atan2(b, a))
    }

    private fun inGamut(c: Rgb): Boolean {
        val e = 1e-4
        return c.r >= -e && c.r <= 1 + e && c.g >= -e && c.g <= 1 + e && c.b >= -e && c.b <= 1 + e
    }

    /** CIE L* from relative luminance. */
    private fun lstar(c: Rgb): Double {
        val y = 0.2126 * c.r + 0.7152 * c.g + 0.0722 * c.b
        return if (y > 216.0 / 24389.0) 116.0 * cbrt(y) - 16.0 else y * 24389.0 / 27.0
    }

    /** Largest in-gamut chroma <= maxC at the given OKLab lightness and hue. */
    private fun clampedColor(l: Double, maxC: Double, h: Double): Rgb {
        fun at(c: Double) = oklabToLinear(l, c * cos(h), c * sin(h))
        val full = at(maxC)
        if (inGamut(full)) return full
        var lo = 0.0
        var hi = maxC
        repeat(18) {
            val mid = (lo + hi) / 2
            if (inGamut(at(mid))) lo = mid else hi = mid
        }
        return at(lo)
    }

    /** Color at the given hue/chroma whose CIE L* equals [tone]. */
    private fun toneColor(h: Double, chroma: Double, tone: Double): Color {
        if (tone <= 0) return Color.Black
        if (tone >= 100) return Color.White
        var lo = 0.0
        var hi = 1.0
        var c = Rgb(0.0, 0.0, 0.0)
        repeat(22) {
            val mid = (lo + hi) / 2
            c = clampedColor(mid, chroma, h)
            if (lstar(c) < tone) lo = mid else hi = mid
        }
        fun ch(v: Double) = fromLinear(v.coerceIn(0.0, 1.0)).coerceIn(0.0, 1.0).toFloat()
        return Color(ch(c.r), ch(c.g), ch(c.b))
    }

    fun scheme(seed: Color, dark: Boolean): ColorScheme {
        val (_, chroma, h) = linearToOklch(
            Rgb(toLinear(seed.red.toDouble()), toLinear(seed.green.toDouble()), toLinear(seed.blue.toDouble()))
        )

        // "Tonal spot" scheme: chroma per palette, roughly the HCT values of M3 / 300.
        val cPrimary = chroma.coerceIn(0.10, 0.15)
        val greyish = if (chroma < 0.02) 0.0 else 1.0 // keep neutral seeds neutral
        val hTertiary = h + PI / 3.0
        val hError = 25.0 * PI / 180.0

        val p = { t: Double -> toneColor(h, cPrimary * greyish, t) }
        val s = { t: Double -> toneColor(h, 0.053 * greyish, t) }
        val t3 = { t: Double -> toneColor(hTertiary, 0.08 * greyish, t) }
        val n = { t: Double -> toneColor(h, 0.012 * greyish, t) }
        val nv = { t: Double -> toneColor(h, 0.025 * greyish, t) }
        val e = { t: Double -> toneColor(hError, 0.2, t) }

        return if (dark) darkColorScheme(
            primary = p(80.0), onPrimary = p(20.0), primaryContainer = p(30.0), onPrimaryContainer = p(90.0),
            inversePrimary = p(40.0),
            secondary = s(80.0), onSecondary = s(20.0), secondaryContainer = s(30.0), onSecondaryContainer = s(90.0),
            tertiary = t3(80.0), onTertiary = t3(20.0), tertiaryContainer = t3(30.0), onTertiaryContainer = t3(90.0),
            error = e(80.0), onError = e(20.0), errorContainer = e(30.0), onErrorContainer = e(90.0),
            background = n(6.0), onBackground = n(90.0),
            surface = n(6.0), onSurface = n(90.0), surfaceVariant = nv(30.0), onSurfaceVariant = nv(80.0),
            surfaceTint = p(80.0), inverseSurface = n(90.0), inverseOnSurface = n(20.0),
            outline = nv(60.0), outlineVariant = nv(30.0), scrim = Color.Black,
            surfaceDim = n(6.0), surfaceBright = n(24.0),
            surfaceContainerLowest = n(4.0), surfaceContainerLow = n(10.0), surfaceContainer = n(12.0),
            surfaceContainerHigh = n(17.0), surfaceContainerHighest = n(22.0),
        ) else lightColorScheme(
            primary = p(40.0), onPrimary = p(100.0), primaryContainer = p(90.0), onPrimaryContainer = p(10.0),
            inversePrimary = p(80.0),
            secondary = s(40.0), onSecondary = s(100.0), secondaryContainer = s(90.0), onSecondaryContainer = s(10.0),
            tertiary = t3(40.0), onTertiary = t3(100.0), tertiaryContainer = t3(90.0), onTertiaryContainer = t3(10.0),
            error = e(40.0), onError = e(100.0), errorContainer = e(90.0), onErrorContainer = e(10.0),
            background = n(98.0), onBackground = n(10.0),
            surface = n(98.0), onSurface = n(10.0), surfaceVariant = nv(90.0), onSurfaceVariant = nv(30.0),
            surfaceTint = p(40.0), inverseSurface = n(20.0), inverseOnSurface = n(95.0),
            outline = nv(50.0), outlineVariant = nv(80.0), scrim = Color.Black,
            surfaceDim = n(87.0), surfaceBright = n(98.0),
            surfaceContainerLowest = n(100.0), surfaceContainerLow = n(96.0), surfaceContainer = n(94.0),
            surfaceContainerHigh = n(92.0), surfaceContainerHighest = n(90.0),
        )
    }
}
