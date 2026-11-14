package com.sonara.player.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads every audio file the phone's MediaStore knows about (internal storage + SD card). */
object MediaRepo {

    suspend fun loadSongs(context: Context, musicOnly: Boolean = true, minDurationMs: Long = 3_000): List<Song> =
        withContext(Dispatchers.IO) {
            val out = ArrayList<Song>()
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.TRACK,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DATA,
            )
            val selection =
                (if (musicOnly) "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND " else "") +
                    "${MediaStore.Audio.Media.DURATION} >= ?"
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                arrayOf(minDurationMs.toString()),
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val iAlbumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val iMime = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val iSize = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val iDate = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val iTrack = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val iName = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val iData = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    val artist = c.getString(iArtist)?.takeIf { it != "<unknown>" } ?: "Unknown artist"
                    out += Song(
                        id = id,
                        title = c.getString(iTitle) ?: "Unknown",
                        artist = artist,
                        album = c.getString(iAlbum) ?: "Unknown album",
                        albumId = c.getLong(iAlbumId),
                        durationMs = c.getLong(iDur),
                        uri = ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                        ),
                        mime = c.getString(iMime) ?: "audio/*",
                        sizeBytes = c.getLong(iSize),
                        dateAdded = c.getLong(iDate),
                        track = c.getInt(iTrack),
                        displayName = c.getString(iName) ?: "",
                        path = c.getString(iData) ?: "",
                    )
                }
            }
            out
        }

    fun albums(songs: List<Song>): List<Album> =
        songs.groupBy { it.albumId }.map { (id, list) ->
            val sorted = list.sortedBy { it.track }
            Album(id, sorted.first().album, sorted.first().artist, sorted)
        }.sortedBy { it.name.lowercase() }

    fun artists(songs: List<Song>): List<Artist> =
        songs.groupBy { it.artist }.map { (n, l) -> Artist(n, l) }
            .sortedBy { it.name.lowercase() }
}
