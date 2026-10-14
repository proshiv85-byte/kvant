// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.proshiv85_byte.kvant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.OpenableColumns

data class TrackInfo(
    val title: String,
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0,
)

/** A file picked by the user, before tags are read. */
data class TrackFile(val uri: Uri, val name: String, val coverUri: Uri? = null)

object Tracks {

    private val coverNames = listOf("cover", "folder", "front", "album", "albumart")
    private val imageExtensions = listOf("jpg", "jpeg", "png", "webp")

    private fun isAudio(mime: String?, name: String): Boolean =
        mime != null && (mime.startsWith("audio/") || mime == "application/ogg") ||
            name.substringAfterLast('.', "").lowercase() in listOf("mp3", "flac", "ogg", "opus", "m4a", "aac", "wav", "wma", "mka")

    /** Display names for individually picked files. */
    fun files(context: Context, uris: List<Uri>): List<TrackFile> = uris.map { uri ->
        TrackFile(uri, displayName(context, uri) ?: uri.lastPathSegment ?: "Unknown")
    }

    /** All audio files below a picked folder (recursively), naturally sorted by path. */
    fun scanTree(context: Context, tree: Uri): List<TrackFile> {
        val found = mutableListOf<Pair<String, TrackFile>>()
        val projection = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE)

        fun walk(docId: String, path: String) {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
            val dirs = mutableListOf<Pair<String, String>>()
            val audio = mutableListOf<Pair<String, String>>()
            var cover: Uri? = null
            context.contentResolver.query(children, projection, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0)
                    val name = c.getString(1) ?: continue
                    val mime = c.getString(2)
                    when {
                        mime == Document.MIME_TYPE_DIR -> dirs += id to name
                        isAudio(mime, name) -> audio += id to name
                        cover == null && name.substringBeforeLast('.').lowercase() in coverNames &&
                            name.substringAfterLast('.', "").lowercase() in imageExtensions ->
                            cover = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                    }
                }
            }
            for ((id, name) in audio) {
                found += "$path/$name" to TrackFile(DocumentsContract.buildDocumentUriUsingTree(tree, id), name, cover)
            }
            for ((id, name) in dirs) walk(id, "$path/$name")
        }

        walk(DocumentsContract.getTreeDocumentId(tree), "")
        return found.sortedWith { a, b -> NaturalOrder.compare(a.first, b.first) }.map { it.second }
    }

    fun read(context: Context, file: TrackFile): TrackInfo {
        val fallback = file.name.substringBeforeLast('.')
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, file.uri)
            TrackInfo(
                title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim().orEmpty().ifEmpty { fallback },
                artist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.trim().orEmpty(),
                album = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.trim().orEmpty(),
                durationMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0,
            )
        } catch (e: Exception) {
            TrackInfo(fallback)
        } finally {
            r.release()
        }
    }

    /** Embedded cover art, else the folder image. Downscaled to about [maxSize] px. */
    fun cover(context: Context, uri: Uri, coverUri: Uri?, maxSize: Int = 1024): Bitmap? {
        val embedded: ByteArray? = MediaMetadataRetriever().let { r ->
            try {
                r.setDataSource(context, uri)
                r.embeddedPicture
            } catch (e: Exception) {
                null
            } finally {
                r.release()
            }
        }
        if (embedded != null) decode(maxSize) { opts -> BitmapFactory.decodeByteArray(embedded, 0, embedded.size, opts) }?.let { return it }
        if (coverUri != null) {
            return try {
                decode(maxSize) { opts ->
                    context.contentResolver.openInputStream(coverUri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                }
            } catch (e: Exception) {
                null
            }
        }
        return null
    }

    private fun decode(maxSize: Int, load: (BitmapFactory.Options) -> Bitmap?): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        load(bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSize && bounds.outHeight / (sample * 2) >= maxSize) sample *= 2
        return load(BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun displayName(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (e: Exception) {
        null
    }

    fun formatTime(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60)
        else "%d:%02d".format(s / 60, s % 60)
    }
}

/** Compares strings with numbers by value ("track 2" < "track 10"), case-insensitively. */
object NaturalOrder : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                val si = i
                val sj = j
                while (i < a.length && a[i].isDigit()) i++
                while (j < b.length && b[j].isDigit()) j++
                val na = a.substring(si, i).trimStart('0')
                val nb = b.substring(sj, j).trimStart('0')
                if (na.length != nb.length) return na.length - nb.length
                val cmp = na.compareTo(nb)
                if (cmp != 0) return cmp
            } else {
                val cmp = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (cmp != 0) return cmp
                i++
                j++
            }
        }
        return (a.length - i) - (b.length - j)
    }
}
