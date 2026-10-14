// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.proshiv85_byte.kvant

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { System, Light, Dark }
enum class QueueSide { Left, Right }

/** Same options as the desktop app. */
data class Settings(
    val accent: Int = 0xFF6750A4.toInt(),
    val themeMode: ThemeMode = ThemeMode.System,
    val queueSide: QueueSide = QueueSide.Left,
    val queueOpen: Boolean = true, // inline panel on wide screens
    val squiggle: Boolean = true,
    val amplitude: Float = 3f,
    val wavelength: Float = 36f,
    val speed: Float = 1f,
)

private val Context.dataStore by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val accent = intPreferencesKey("accent")
        val themeMode = stringPreferencesKey("themeMode")
        val queueSide = stringPreferencesKey("queueSide")
        val queueOpen = booleanPreferencesKey("queueOpen")
        val squiggle = booleanPreferencesKey("squiggle")
        val amplitude = floatPreferencesKey("amplitude")
        val wavelength = floatPreferencesKey("wavelength")
        val speed = floatPreferencesKey("speed")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    suspend fun update(transform: (Settings) -> Settings) {
        context.dataStore.edit { prefs ->
            val s = transform(prefs.toSettings())
            prefs[Keys.accent] = s.accent
            prefs[Keys.themeMode] = s.themeMode.name
            prefs[Keys.queueSide] = s.queueSide.name
            prefs[Keys.queueOpen] = s.queueOpen
            prefs[Keys.squiggle] = s.squiggle
            prefs[Keys.amplitude] = s.amplitude
            prefs[Keys.wavelength] = s.wavelength
            prefs[Keys.speed] = s.speed
        }
    }

    private fun Preferences.toSettings(): Settings {
        val d = Settings()
        return Settings(
            accent = this[Keys.accent] ?: d.accent,
            themeMode = this[Keys.themeMode]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.themeMode,
            queueSide = this[Keys.queueSide]?.let { runCatching { QueueSide.valueOf(it) }.getOrNull() } ?: d.queueSide,
            queueOpen = this[Keys.queueOpen] ?: d.queueOpen,
            squiggle = this[Keys.squiggle] ?: d.squiggle,
            amplitude = this[Keys.amplitude] ?: d.amplitude,
            wavelength = this[Keys.wavelength] ?: d.wavelength,
            speed = this[Keys.speed] ?: d.speed,
        )
    }
}
