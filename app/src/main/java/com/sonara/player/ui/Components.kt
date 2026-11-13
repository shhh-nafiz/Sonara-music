package com.sonara.player.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonara.player.data.ArtworkLoader
import com.sonara.player.data.Playlist
import com.sonara.player.data.Song
import com.sonara.player.data.Store
import com.sonara.player.playback.PlayerController

fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/** Cover art for a song (embedded picture → MediaStore thumbnail → placeholder). */
@Composable
fun Cover(song: Song?, modifier: Modifier = Modifier, px: Int = 256, corner: Dp = 4.dp) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, song?.id, px) {
        value = song?.let { ArtworkLoader.load(ctx, it.uri, px) }
    }
    val shape = RoundedCornerShape(corner)
    Box(modifier.clip(shape).background(Color(0xFF282828)), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) {
            Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Default.MusicNote, null, tint = Color(0xFF7F7F7F), modifier = Modifier.fillMaxSize(0.45f))
        }
    }
}

@Composable
fun SongRow(
    song: Song,
    playing: Boolean,
    player: PlayerController,
    playlists: List<Playlist>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    extraMenu: (@Composable (dismiss: () -> Unit) -> Unit)? = null,
) {
    val accent = LocalAccent.current
    val fav = Store.favorites.collectAsState().value
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(song, Modifier.size(52.dp), px = 128)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp,
                color = if (playing) accent else MaterialTheme.colorScheme.onBackground,
                fontWeight = if (playing) FontWeight.Bold else FontWeight.Normal,
            )
            Text(
                "${if (song.isLossless) "Lossless • " else ""}${song.artist}",
                maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = TextSecondary,
            )
        }
        if (song.id in fav) {
            Icon(Icons.Default.Favorite, null, tint = accent, modifier = Modifier.size(18.dp))
        }
        SongMenu(song, player, playlists, extraMenu)
    }
}

@Composable
fun SongMenu(
    song: Song,
    player: PlayerController,
    playlists: List<Playlist>,
    extra: (@Composable (dismiss: () -> Unit) -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    var pickPlaylist by remember { mutableStateOf(false) }
    val isFav = song.id in Store.favorites.collectAsState().value
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, "More", tint = TextSecondary)
        }
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem(
                text = { Text(if (isFav) "Remove from favorites" else "Add to favorites") },
                leadingIcon = { Icon(if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null) },
                onClick = { Store.toggleFavorite(song.id); open = false },
            )
            DropdownMenuItem(text = { Text("Play next") }, onClick = { player.playNext(song); open = false })
            DropdownMenuItem(text = { Text("Add to queue") }, onClick = { player.addToQueue(song); open = false })
            DropdownMenuItem(text = { Text("Add to playlist") }, onClick = { open = false; pickPlaylist = true })
            extra?.invoke { open = false }
        }
    }
    if (pickPlaylist) PlaylistPicker(song, playlists) { pickPlaylist = false }
}

@Composable
fun PlaylistPicker(song: Song, playlists: List<Playlist>, onDismiss: () -> Unit) {
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (creating) "New playlist" else "Add to playlist") },
        text = {
            if (creating) {
                TextField(name, { name = it }, singleLine = true, placeholder = { Text("Playlist name") })
            } else {
                Column {
                    Text(
                        "+ New playlist", color = LocalAccent.current, fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth().clickable { creating = true }.padding(vertical = 12.dp),
                    )
                    playlists.forEach { p ->
                        Text(
                            p.name,
                            modifier = Modifier.fillMaxWidth().clickable {
                                Store.addToPlaylist(p.id, song.id); onDismiss()
                            }.padding(vertical = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (creating) TextButton(onClick = {
                if (name.isNotBlank()) Store.createPlaylist(name.trim(), song.id)
                onDismiss()
            }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, fontSize = 22.sp, fontWeight = FontWeight.Bold,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
fun EmptyHint(text: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = TextSecondary)
    }
}
