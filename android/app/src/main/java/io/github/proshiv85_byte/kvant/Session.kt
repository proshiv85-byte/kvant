// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.proshiv85_byte.kvant

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** The queue, current track and position, saved so the next start can pick up from there. */
class Session(val items: List<MediaItem>, val current: Int, val positionMs: Long) {

    /** Snapshot of the player. Must be taken on the main thread; [write] can run anywhere. */
    constructor(player: Player) : this(
        (0 until player.mediaItemCount).map(player::getMediaItemAt),
        player.currentMediaItemIndex,
        player.currentPosition,
    )

    fun write(context: Context) {
        val queue = JSONArray()
        for (item in items) {
            val md = item.mediaMetadata
            queue.put(
                JSONObject()
                    .put("id", item.mediaId)
                    .putOpt("title", md.title?.toString())
                    .putOpt("artist", md.artist?.toString())
                    .putOpt("album", md.albumTitle?.toString())
                    .putOpt("duration", md.durationMs)
                    .putOpt("cover", md.artworkUri?.toString())
            )
        }
        val json = JSONObject().put("queue", queue).put("current", current).put("position", positionMs)
        // Write to a temporary file first, so a crash mid-write can't leave a broken session.
        val tmp = File(context.filesDir, "$FILE.tmp")
        tmp.writeText(json.toString())
        tmp.renameTo(File(context.filesDir, FILE))
    }

    companion object {
        private const val FILE = "session.json"

        /**
         * Reads the saved session, leaving out tracks Kvant can no longer open. It also
         * hands back folder and file access that no saved track needs any more, since
         * Android limits how many an app can keep.
         */
        fun read(context: Context): Session? {
            val resolver = context.contentResolver
            val perms = resolver.persistedUriPermissions.filter { it.isReadPermission }
            val granted = perms.map { it.uri.toString() }
            fun accessible(uri: String) = granted.any { uri == it || uri.startsWith("$it/") }

            val json = runCatching { JSONObject(File(context.filesDir, FILE).readText()) }.getOrNull()
            val savedCurrent = json?.optInt("current", 0) ?: 0
            val items = mutableListOf<MediaItem>()
            var current = -1
            val queue = json?.optJSONArray("queue") ?: JSONArray()
            for (i in 0 until queue.length()) {
                val o = queue.getJSONObject(i)
                val id = o.getString("id")
                val uri = id.substringAfter('|')
                if (!accessible(uri)) continue
                if (i == savedCurrent) current = items.size
                items += MediaItem.Builder()
                    .setMediaId(id)
                    .setUri(uri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(o.optString("title").ifEmpty { null })
                            .setArtist(o.optString("artist").ifEmpty { null })
                            .setAlbumTitle(o.optString("album").ifEmpty { null })
                            .setArtworkUri(o.optString("cover").ifEmpty { null }?.toUri())
                            .apply { if (o.has("duration")) setDurationMs(o.getLong("duration")) }
                            .build()
                    )
                    .build()
            }

            val used = items.map { it.mediaId.substringAfter('|') }
            // Access taken in the last minute may be for a queue that's still being opened.
            val recent = System.currentTimeMillis() - 60_000
            for (perm in perms.filter { it.persistedTime < recent }.map { it.uri.toString() }) {
                if (used.none { it == perm || it.startsWith("$perm/") }) {
                    runCatching { resolver.releasePersistableUriPermission(perm.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                }
            }

            if (items.isEmpty()) return null
            // If the current track is gone, start from the top.
            return if (current >= 0) Session(items, current, json?.optLong("position") ?: 0)
            else Session(items, 0, 0)
        }
    }
}
