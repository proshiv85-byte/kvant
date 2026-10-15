// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.proshiv85_byte.kvant.ui

import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.proshiv85_byte.kvant.KvantViewModel
import io.github.proshiv85_byte.kvant.QueueSide
import io.github.proshiv85_byte.kvant.ThemeMode
import io.github.proshiv85_byte.kvant.ui.theme.KvantTheme

// Material 3 emphasized easings.
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EaseInOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

@Composable
fun KvantApp(vm: KvantViewModel) {
    val settings = vm.settings.collectAsStateWithLifecycle().value ?: return
    val ui by vm.ui.collectAsStateWithLifecycle()
    val position by vm.position.collectAsStateWithLifecycle()
    val cover by vm.cover.collectAsStateWithLifecycle()

    val dark = when (settings.themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    // Status/navigation bar icons follow the app's theme, not just the system's.
    val activity = LocalActivity.current as? ComponentActivity
    DisposableEffect(dark) {
        val style = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT) { dark }
        activity?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose {}
    }

    KvantTheme(Color(settings.accent), dark) {
        // Opening from Now Playing replaces the queue; the queue's own buttons append.
        var replace by remember { mutableStateOf(true) }
        val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            vm.openFiles(uris, replace)
        }
        val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) vm.openFolder(uri, replace)
        }
        fun openFiles(r: Boolean) {
            replace = r
            pickFiles.launch(arrayOf("audio/*", "application/ogg"))
        }
        fun openFolder(r: Boolean) {
            replace = r
            pickFolder.launch(null)
        }

        var showSettings by rememberSaveable { mutableStateOf(false) }
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(Unit) { vm.errors.collect { snackbar.showSnackbar(it) } }

        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                // Inline queue only on tablet-sized screens; landscape phones are wide but short.
                val wide = maxWidth >= 720.dp && maxHeight >= 480.dp
                val right = settings.queueSide == QueueSide.Right
                var overlayOpen by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(wide) { if (wide) overlayOpen = false }

                val queueActions = QueueActions(
                    play = vm::playIndex,
                    remove = vm::remove,
                    addFiles = { openFiles(false) },
                    addFolder = { openFolder(false) },
                    clear = vm::clear,
                )
                val nowPlayingActions = NowPlayingActions(
                    toggleQueue = {
                        if (wide) vm.updateSettings { it.copy(queueOpen = !it.queueOpen) } else overlayOpen = !overlayOpen
                    },
                    openFiles = { openFiles(true) },
                    openFolder = { openFolder(true) },
                    openSettings = { showSettings = true },
                    toggle = vm::toggle,
                    next = vm::next,
                    previous = vm::previous,
                    toggleShuffle = vm::toggleShuffle,
                    cycleRepeat = vm::cycleRepeat,
                    seek = vm::seek,
                )

                // Wide screens: an inline panel whose slot grows while it slides in from its edge.
                val progress by animateFloatAsState(
                    if (wide && settings.queueOpen) 1f else 0f,
                    if (wide && settings.queueOpen) tween(500, easing = EmphasizedDecelerate) else tween(380, easing = EaseInOut),
                    label = "queueSlot",
                )
                val panelWidth = min(360.dp, maxWidth * 0.36f)
                val gap = 12.dp
                val inlinePanel = @Composable {
                    if (progress > 0f) {
                        Box(
                            Modifier.fillMaxHeight().width((panelWidth + gap) * progress).clipToBounds(),
                            contentAlignment = if (right) Alignment.CenterStart else Alignment.CenterEnd,
                        ) {
                            QueuePanel(
                                ui, queueActions,
                                Modifier
                                    .requiredWidth(panelWidth)
                                    .fillMaxHeight()
                                    .padding(start = if (right) gap else 0.dp, end = if (right) 0.dp else gap)
                                    .padding(vertical = 12.dp)
                                    .graphicsLayer {
                                        alpha = (progress * 1.6f).coerceAtMost(1f)
                                        val s = 0.94f + 0.06f * progress
                                        scaleX = s
                                        scaleY = s
                                    },
                            )
                        }
                    }
                }

                Row(Modifier.fillMaxSize().padding(horizontal = if (wide) 12.dp else 0.dp)) {
                    if (!right) inlinePanel()
                    NowPlaying(
                        ui, position, cover, settings,
                        queueShown = if (wide) settings.queueOpen else overlayOpen,
                        actions = nowPlayingActions,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    if (right) inlinePanel()
                }

                // Phones: the queue slides over the player from its edge.
                if (!wide) {
                    BackHandler(overlayOpen) { overlayOpen = false }
                    AnimatedVisibility(overlayOpen, enter = fadeIn(tween(300)), exit = fadeOut(tween(250))) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.4f))
                                .clickable(remember { MutableInteractionSource() }, indication = null) { overlayOpen = false }
                        )
                    }
                    AnimatedVisibility(
                        overlayOpen,
                        modifier = Modifier.align(if (right) Alignment.CenterEnd else Alignment.CenterStart),
                        enter = slideInHorizontally(tween(450, easing = EmphasizedDecelerate)) { if (right) it else -it } +
                            fadeIn(tween(200)),
                        exit = slideOutHorizontally(tween(320, easing = EaseInOut)) { if (right) it else -it } +
                            fadeOut(tween(320)),
                    ) {
                        QueuePanel(
                            ui, queueActions,
                            Modifier.fillMaxHeight().width(min(380.dp, maxWidth * 0.9f)).padding(8.dp),
                        )
                    }
                }

                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
            }

            if (showSettings) {
                SettingsSheet(settings, onChange = vm::updateSettings, onDismiss = { showSettings = false })
            }
        }
    }
}
