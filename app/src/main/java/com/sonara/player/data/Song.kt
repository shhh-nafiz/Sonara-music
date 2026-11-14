package com.sonara.player.data

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val uri: Uri,
    val mime: String,
    val sizeBytes: Long,
    val dateAdded: Long,
    val track: Int,
    val displayName: String,
    val path: String = "",
) {
    val folder: String get() = path.substringBeforeLast('/', "")

    val volume: String
        get() = when {
            path.startsWith("/storage/emulated/") -> "Internal storage"
            path.startsWith("/storage/") -> "SD card / USB (${path.split('/').getOrElse(2) { "?" }})"
            else -> "Other storage"
        }

    val format: String
        get() = displayName.substringAfterLast('.', "").uppercase().ifBlank {
            mime.substringAfterLast('/').uppercase()
        }

    /** Average bitrate in kbps computed from file size and duration. */
    val bitrateKbps: Int
        get() = if (durationMs > 0) ((sizeBytes * 8) / durationMs).toInt() else 0

    val isLossless: Boolean
        get() = format in setOf("FLAC", "WAV", "ALAC", "AIFF", "AIF", "APE", "WV")

    val qualityLabel: String
        get() = if (bitrateKbps > 0) "$format • $bitrateKbps kbps" else format
}

data class Album(val id: Long, val name: String, val artist: String, val songs: List<Song>)
data class Artist(val name: String, val songs: List<Song>)
data class Playlist(val id: String, val name: String, val songIds: List<Long>)
