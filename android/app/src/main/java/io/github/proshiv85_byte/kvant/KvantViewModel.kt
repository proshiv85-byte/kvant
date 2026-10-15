// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.proshiv85_byte.kvant

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.random.Random

data class QueueEntry(val id: String, val title: String, val artist: String, val durationMs: Long)

data class PlayerUi(
    val queue: List<QueueEntry> = emptyList(),
    val current: Int = -1,
    val playing: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0,
    val seekable: Boolean = false,
    val canNext: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
) {
    val hasTrack get() = current >= 0
}

/**
 * Talks to [PlaybackService] through a MediaController. The queue lives in the player;
 * each item's media ID carries its file URI, so the UI can rebuild everything from the
 * player even after the activity was closed and reopened.
 */
class KvantViewModel(private val app: Application) : AndroidViewModel(app) {

    private val store = SettingsStore(app)

    /** Null until loaded, so the UI never flashes the wrong theme. */
    val settings: StateFlow<Settings?> = store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _ui = MutableStateFlow(PlayerUi())
    val ui: StateFlow<PlayerUi> = _ui.asStateFlow()

    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    private val _cover = MutableStateFlow<ImageBitmap?>(null)
    val cover: StateFlow<ImageBitmap?> = _cover.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val errors: SharedFlow<String> = _errors

    private val controllerReady = CompletableDeferred<MediaController>()
    private var controller: MediaController? = null
    private val tagJobs = mutableListOf<Job>()
    private var coverFor: String? = null
    private var coverJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            refresh(queueChanged = events.contains(Player.EVENT_TIMELINE_CHANGED))
        }

        override fun onPlayerError(error: PlaybackException) {
            _errors.tryEmit(app.getString(R.string.error_playback, error.errorCodeName))
        }
    }

    init {
        viewModelScope.launch {
            val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
            val c = MediaController.Builder(app, token).buildAsync().await()
            controller = c
            c.addListener(listener)
            refresh(queueChanged = true)
            controllerReady.complete(c)
        }
        // Smooth progress while playing.
        viewModelScope.launch {
            while (isActive) {
                controller?.let { if (it.isPlaying) _position.value = it.currentPosition }
                delay(50)
            }
        }
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        controller?.release()
    }

    // ---- Opening ----------------------------------------------------------------

    fun openFiles(uris: List<Uri>, replace: Boolean) {
        if (uris.isEmpty()) return
        uris.forEach(::keepAccess)
        viewModelScope.launch {
            add(withContext(Dispatchers.IO) { Tracks.files(app, uris) }, replace)
        }
    }

    fun openFolder(tree: Uri, replace: Boolean) {
        keepAccess(tree)
        viewModelScope.launch {
            val files = withContext(Dispatchers.IO) { runCatching { Tracks.scanTree(app, tree) }.getOrDefault(emptyList()) }
            if (files.isEmpty()) _errors.tryEmit(app.getString(R.string.error_no_audio))
            else add(files, replace)
        }
    }

    /**
     * Keeps read access after the app restarts, so the queue can be restored. Files
     * shared from other apps ("Open with") can't be kept, so they just won't come back.
     */
    private fun keepAccess(uri: Uri) {
        runCatching { app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    private suspend fun add(files: List<TrackFile>, replace: Boolean) {
        val c = controllerReady.await()
        // With shuffle on, a new queue starts at a random track (the service shuffles the
        // rest after it). Its tags are read up front, so playback starts with the right info.
        val start = if (replace && c.shuffleModeEnabled) Random.nextInt(files.size) else 0
        val first = withContext(Dispatchers.IO) { Tracks.read(app, files[start]) }
        val items = files.mapIndexed { i, f -> mediaItem(f, if (i == start) first else null) }

        if (replace) {
            tagJobs.forEach { it.cancel() }
            tagJobs.clear()
            c.setMediaItems(items, start, 0)
            c.prepare()
            c.play()
        } else {
            val idle = c.mediaItemCount == 0
            val start = c.mediaItemCount
            c.addMediaItems(items)
            if (idle) {
                c.seekToDefaultPosition(start)
                c.prepare()
                c.play()
            }
        }
        loadTags(items.zip(files).filterIndexed { i, _ -> i != start })
    }

    private fun mediaItem(f: TrackFile, info: TrackInfo?): MediaItem =
        MediaItem.Builder()
            .setMediaId("${UUID.randomUUID()}|${f.uri}")
            .setUri(f.uri)
            .setMediaMetadata(metadata(f, info))
            .build()

    private fun metadata(f: TrackFile, info: TrackInfo?): MediaMetadata =
        MediaMetadata.Builder()
            .setTitle(info?.title ?: f.name.substringBeforeLast('.'))
            .setArtist(info?.artist?.ifEmpty { null })
            .setAlbumTitle(info?.album?.ifEmpty { null })
            .setArtworkUri(f.coverUri)
            .apply { info?.durationMs?.takeIf { it > 0 }?.let { setDurationMs(it) } }
            .build()

    /** Reads tags in the background and updates the queue entries as they come in. */
    private fun loadTags(pending: List<Pair<MediaItem, TrackFile>>) {
        if (pending.isEmpty()) return
        tagJobs += viewModelScope.launch {
            var hint = 0
            for ((item, file) in pending) {
                val info = withContext(Dispatchers.IO) { Tracks.read(app, file) }
                val c = controller ?: return@launch
                val index = indexOf(c, item.mediaId, hint)
                if (index < 0) continue // removed meanwhile
                hint = index + 1
                c.replaceMediaItem(index, item.buildUpon().setMediaMetadata(metadata(file, info)).build())
            }
        }
    }

    private fun indexOf(c: MediaController, id: String, hint: Int): Int {
        if (hint < c.mediaItemCount && c.getMediaItemAt(hint).mediaId == id) return hint
        return (0 until c.mediaItemCount).firstOrNull { c.getMediaItemAt(it).mediaId == id } ?: -1
    }

    // ---- Controls -----------------------------------------------------------------

    fun toggle() {
        val c = controller ?: return
        if (c.playWhenReady && c.playbackState != Player.STATE_ENDED) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun playIndex(index: Int) {
        val c = controller ?: return
        c.seekToDefaultPosition(index)
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        c.play()
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    /** Restarts the track if more than 3 s in, otherwise goes to the previous one. */
    fun previous() {
        controller?.seekToPrevious()
    }

    fun toggleShuffle() {
        controller?.run { shuffleModeEnabled = !shuffleModeEnabled }
    }

    /** Off → all → one → off, like the desktop app. */
    fun cycleRepeat() {
        controller?.run { repeatMode = nextRepeatMode(repeatMode) }
    }

    fun seek(fraction: Float) {
        val c = controller ?: return
        val target = (fraction * _ui.value.durationMs).toLong()
        c.seekTo(target)
        _position.value = target
    }

    fun remove(index: Int) {
        controller?.removeMediaItem(index)
    }

    fun clear() {
        tagJobs.forEach { it.cancel() }
        tagJobs.clear()
        controller?.run {
            stop()
            clearMediaItems()
        }
    }

    fun updateSettings(transform: (Settings) -> Settings) {
        viewModelScope.launch { store.update(transform) }
    }

    // ---- State --------------------------------------------------------------------

    private fun refresh(queueChanged: Boolean) {
        val c = controller ?: return
        val count = c.mediaItemCount
        val queue = if (queueChanged || _ui.value.queue.size != count) {
            (0 until count).map { i ->
                val item = c.getMediaItemAt(i)
                val md = item.mediaMetadata
                QueueEntry(
                    id = item.mediaId,
                    title = md.title?.toString().orEmpty(),
                    artist = md.artist?.toString().orEmpty(),
                    durationMs = md.durationMs ?: 0,
                )
            }
        } else _ui.value.queue

        val current = if (count == 0) -1 else c.currentMediaItemIndex
        val item = if (current >= 0) c.getMediaItemAt(current) else null
        val md = item?.mediaMetadata
        val duration = c.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: md?.durationMs ?: 0

        _ui.value = PlayerUi(
            queue = queue,
            current = current,
            playing = c.playWhenReady && c.playbackState != Player.STATE_ENDED && c.playbackState != Player.STATE_IDLE,
            title = md?.title?.toString().orEmpty(),
            artist = md?.artist?.toString().orEmpty(),
            album = md?.albumTitle?.toString().orEmpty(),
            durationMs = duration,
            seekable = c.isCurrentMediaItemSeekable,
            canNext = c.hasNextMediaItem(),
            shuffle = c.shuffleModeEnabled,
            repeat = c.repeatMode,
        )
        _position.value = c.currentPosition
        if (item?.mediaId != coverFor) loadCover(item)
    }

    private fun loadCover(item: MediaItem?) {
        coverJob?.cancel()
        coverFor = item?.mediaId
        if (item == null) {
            _cover.value = null
            return
        }
        val uri = item.mediaId.substringAfter('|').toUri()
        val folderCover = item.mediaMetadata.artworkUri
        coverJob = viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) { Tracks.cover(app, uri, folderCover) }
            _cover.value = bitmap?.asImageBitmap()
        }
    }
}

fun nextRepeatMode(mode: Int): Int = when (mode) {
    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
    else -> Player.REPEAT_MODE_OFF
}
