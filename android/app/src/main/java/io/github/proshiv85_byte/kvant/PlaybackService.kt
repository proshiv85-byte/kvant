// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.proshiv85_byte.kvant

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * Owns the player. Media3 turns the session into the media notification, lock-screen
 * controls and Bluetooth/headset button handling (Android's equivalent of MPRIS).
 * The system media controls also feed the "island" style widgets some phones have
 * (Samsung's Now Bar, Xiaomi's Super Island, OnePlus' Live Alerts).
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var store: SettingsStore
    private var errorsInARow = 0
    private var lastItemId: String? = null
    private var restored = false // never save over the last session before it's been read
    private var saveJob: Job? = null
    private var positionJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        store = SettingsStore(this)
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true) // pause when headphones are unplugged
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                // Like the desktop app: at the end of the queue, stop at the start of the last track.
                if (state == Player.STATE_ENDED) {
                    player.pause()
                    player.seekTo(player.currentMediaItemIndex, 0)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) errorsInARow = 0
                // Save the position now and then while playing, and where it was paused.
                positionJob?.cancel()
                if (isPlaying) {
                    positionJob = scope.launch {
                        while (isActive) {
                            delay(5_000)
                            saveSession()
                        }
                    }
                } else {
                    saveSoon()
                }
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) saveSoon()
            }

            override fun onPlayerError(error: PlaybackException) {
                // Skip files that can't be played, but stop once every track has failed,
                // or "repeat all" would cycle through a queue of broken files forever.
                if (++errorsInARow < player.mediaItemCount && player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                }
            }

            override fun onShuffleModeEnabledChanged(enabled: Boolean) {
                if (enabled) shuffleAfterCurrent(player)
                updateButtons()
                scope.launch { store.update { it.copy(shuffle = enabled) } }
            }

            override fun onRepeatModeChanged(mode: Int) {
                updateButtons()
                scope.launch { store.update { it.copy(repeat = mode) } }
            }

            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                // The previous track left the queue, so this is a new queue (or the playing
                // track was removed): shuffle what's left from the current track on.
                saveSoon()
                val previous = lastItemId
                lastItemId = item?.mediaId
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED && player.shuffleModeEnabled &&
                    (0 until player.mediaItemCount).none { player.getMediaItemAt(it).mediaId == previous }
                ) shuffleAfterCurrent(player)
            }
        })

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(Callback())
            .setMediaButtonPreferences(buttons(player))
            .build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply {
                setSmallIcon(R.drawable.ic_notification)
            }
        )

        // Restore shuffle, repeat and the queue from the last run, paused.
        scope.launch {
            val s = store.settings.first()
            player.shuffleModeEnabled = s.shuffle
            player.repeatMode = s.repeat
            val last = withContext(Dispatchers.IO) { Session.read(this@PlaybackService) }
            if (last != null && player.mediaItemCount == 0) {
                player.setMediaItems(last.items, last.current, last.positionMs)
                player.prepare()
            }
            restored = true
        }
    }

    private fun saveSoon() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(1_000)
            saveSession()
        }
    }

    private suspend fun saveSession() {
        val player = session?.player ?: return
        if (!restored) return
        val snapshot = Session(player)
        withContext(Dispatchers.IO) { runCatching { snapshot.write(this@PlaybackService) } }
    }

    /** Last chance to save, when the service goes away. Small enough to write right here. */
    private fun saveSessionNow() {
        val player = session?.player ?: return
        if (restored) runCatching { Session(player).write(this) }
    }

    /** New random order with the current track first, so all the others are still ahead. */
    private fun shuffleAfterCurrent(player: ExoPlayer) {
        val count = player.mediaItemCount
        if (count < 2) return
        val current = player.currentMediaItemIndex
        val rest = (0 until count).filter { it != current }.shuffled()
        player.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder((listOf(current) + rest).toIntArray(), Random.nextLong()))
    }

    /** Shuffle and repeat buttons for the media notification and the system media controls. */
    private fun buttons(player: Player): List<CommandButton> {
        val shuffle = CommandButton.Builder(
            if (player.shuffleModeEnabled) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF
        )
            .setDisplayName(getString(if (player.shuffleModeEnabled) R.string.shuffle_on else R.string.shuffle_off))
            .setSessionCommand(SHUFFLE)
            .setSlots(CommandButton.SLOT_BACK_SECONDARY, CommandButton.SLOT_OVERFLOW)
            .build()
        val repeat = CommandButton.Builder(
            when (player.repeatMode) {
                Player.REPEAT_MODE_ALL -> CommandButton.ICON_REPEAT_ALL
                Player.REPEAT_MODE_ONE -> CommandButton.ICON_REPEAT_ONE
                else -> CommandButton.ICON_REPEAT_OFF
            }
        )
            .setDisplayName(
                getString(
                    when (player.repeatMode) {
                        Player.REPEAT_MODE_ALL -> R.string.repeat_all
                        Player.REPEAT_MODE_ONE -> R.string.repeat_one
                        else -> R.string.repeat_off
                    }
                )
            )
            .setSessionCommand(REPEAT)
            .setSlots(CommandButton.SLOT_FORWARD_SECONDARY, CommandButton.SLOT_OVERFLOW)
            .build()
        return listOf(shuffle, repeat)
    }

    private fun updateButtons() {
        session?.let { it.setMediaButtonPreferences(buttons(it.player)) }
    }

    private inner class Callback : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
            MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().add(SHUFFLE).add(REPEAT).build()
                )
                .build()

        // Lets the system's media controls and headset/Bluetooth play buttons pick up where
        // Kvant left off, even after the app was closed.
        override fun onPlaybackResumption(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
            val player = session.player
            if (player.mediaItemCount > 0) {
                MediaSession.MediaItemsWithStartPosition(
                    (0 until player.mediaItemCount).map(player::getMediaItemAt),
                    player.currentMediaItemIndex, player.currentPosition,
                )
            } else {
                val last = withContext(Dispatchers.IO) { Session.read(this@PlaybackService) }
                    ?: throw UnsupportedOperationException("Nothing to resume")
                restored = true
                MediaSession.MediaItemsWithStartPosition(last.items, last.current, last.positionMs)
            }
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            val player = session.player
            when (customCommand.customAction) {
                SHUFFLE.customAction -> player.shuffleModeEnabled = !player.shuffleModeEnabled
                REPEAT.customAction -> player.repeatMode = nextRepeatMode(player.repeatMode)
                else -> return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    // Swiping the app away from recents stops playback unless music is playing.
    override fun onTaskRemoved(rootIntent: Intent?) {
        saveSessionNow()
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        saveSessionNow()
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    private companion object {
        val SHUFFLE = SessionCommand("io.github.proshiv85_byte.kvant.SHUFFLE", Bundle.EMPTY)
        val REPEAT = SessionCommand("io.github.proshiv85_byte.kvant.REPEAT", Bundle.EMPTY)
    }
}
