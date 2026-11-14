package com.sonara.player.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
    val bmp by produceState<Bitmap?>(song?.let { ArtworkLoader.peek(it.uri, px) }, song?.id, px) {
        value = song?.let { ArtworkLoader.peek(it.uri, px) ?: ArtworkLoader.load(ctx, it.uri, px) }
    }
    val shape = RoundedCornerShape(corner)
    Box(modifier.clip(shape).background(Color(0xFF282828)), contentAlignment = Alignment.Center) {
        if (px >= 512) {
            androidx.compose.animation.Crossfade(bmp, animationSpec = androidx.compose.animation.core.tween(200), label = "cover") { b ->
                if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(Icons.Default.MusicNote, null, tint = Color(0xFF7F7F7F), modifier = Modifier.fillMaxSize(0.45f))
            }
        } else {
            // list thumbnails: no animation layer per row (keeps scrolling smooth)
            val b = bmp
            if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Default.MusicNote, null, tint = Color(0xFF7F7F7F), modifier = Modifier.fillMaxSize(0.45f))
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
        if (song.id in Store.ignored.collectAsState().value) {
            Icon(Icons.Default.VisibilityOff, "Ignored in shuffle", tint = TextSecondary, modifier = Modifier.size(18.dp).padding(end = 2.dp))
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
    val isIgnored = song.id in Store.ignored.collectAsState().value
    val vm = LocalMainVm.current
    var confirmFolder by remember { mutableStateOf(false) }
    var confirmBlacklist by remember { mutableStateOf(false) }
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
            DropdownMenuItem(
                text = { Text(if (isIgnored) "Include in shuffle again" else "Ignore in shuffle") },
                leadingIcon = { Icon(Icons.Default.VisibilityOff, null) },
                onClick = { Store.toggleIgnored(song.id); open = false },
            )
            androidx.compose.material3.HorizontalDivider()
            if (song.folder.isNotEmpty()) DropdownMenuItem(
                text = { Text("Exclude this folder…") },
                onClick = { open = false; confirmFolder = true },
            )
            DropdownMenuItem(
                text = { Text("Blacklist song…") },
                onClick = { open = false; confirmBlacklist = true },
            )
            extra?.invoke { open = false }
        }
    }
    if (pickPlaylist) PlaylistPicker(song, playlists) { pickPlaylist = false }
    if (confirmFolder) {
        val n = vm?.songs?.value?.count { it.folder == song.folder } ?: 0
        AlertDialog(
            onDismissRequest = { confirmFolder = false },
            title = { Text("Exclude this folder?") },
            text = { Text("This hides all $n songs in\n${song.folder.substringAfterLast('/')}\n\nYou can bring them back in Settings → Manage library → Sources.") },
            confirmButton = {
                TextButton(onClick = {
                    if (song.folder !in Store.excludedFolders.value) Store.toggleFolder(song.folder)
                    confirmFolder = false
                }) { Text("Exclude") }
            },
            dismissButton = { TextButton(onClick = { confirmFolder = false }) { Text("Cancel") } },
        )
    }
    if (confirmBlacklist) AlertDialog(
        onDismissRequest = { confirmBlacklist = false },
        title = { Text("Blacklist this song?") },
        text = { Text("\"${song.title}\" will be hidden everywhere and never play. Restore it any time in Settings → Manage library → Blacklist.") },
        confirmButton = {
            TextButton(onClick = {
                if (vm != null) vm.toggleBlacklist(song) else Store.toggleBlacklist(song.id)
                confirmBlacklist = false
            }) { Text("Blacklist") }
        },
        dismissButton = { TextButton(onClick = { confirmBlacklist = false }) { Text("Cancel") } },
    )
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

/** Gives deep composables (song menus) access to the view model for blacklisting. */
val LocalMainVm = staticCompositionLocalOf<com.sonara.player.MainViewModel?> { null }

/**
 * Playback position that advances every display frame (144 Hz friendly). Only the composable that
 * reads the returned state recomposes, so the rest of the screen stays still.
 */
@Composable
fun rememberLivePosition(player: PlayerController): State<Long> {
    val anchor by player.anchor.collectAsState()
    val pos = remember { mutableStateOf(anchor.positionMs) }
    LaunchedEffect(anchor) {
        pos.value = anchor.positionMs
        if (anchor.playing) {
            while (true) {
                withFrameNanos { }
                pos.value = anchor.positionMs + (android.os.SystemClock.elapsedRealtime() - anchor.atElapsed)
            }
        }
    }
    return pos
}

/** Heart that pops with a spring when it becomes a favorite. */
@Composable
fun HeartIcon(fav: Boolean, accent: Color, size: Dp) {
    val scale = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(fav) {
        if (fav) {
            scale.animateTo(1.35f, androidx.compose.animation.core.spring(0.35f, androidx.compose.animation.core.Spring.StiffnessHigh))
            scale.animateTo(1f, androidx.compose.animation.core.spring(0.5f))
        }
    }
    Icon(
        if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorite",
        tint = if (fav) accent else MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.size(size).graphicsLayer { scaleX = scale.value; scaleY = scale.value },
    )
}

/** A–Z strip on the right edge; touch or drag on it to jump through a long list. */
@Composable
fun AlphabetScroller(letters: List<Char>, modifier: Modifier = Modifier, onSelect: (Char) -> Unit) {
    val accent = LocalAccent.current
    var active by remember { mutableStateOf<Char?>(null) }
    Box(modifier) {
        Column(
            Modifier.fillMaxSize().padding(end = 2.dp)
                .pointerInput(letters) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        fun pick(y: Float) {
                            val i = ((y / size.height) * letters.size).toInt().coerceIn(0, letters.lastIndex)
                            val c = letters[i]
                            if (c != active) { active = c; onSelect(c) }
                        }
                        pick(down.position.y); down.consume()
                        do {
                            val ev = awaitPointerEvent()
                            ev.changes.forEach { if (it.pressed) { pick(it.position.y); it.consume() } }
                        } while (ev.changes.any { it.pressed })
                        active = null
                    }
                },
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            letters.forEach { Text(it.toString(), fontSize = 10.sp, color = accent, fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp)) }
        }
        active?.let {
            Box(
                Modifier.align(Alignment.CenterStart).padding(end = 40.dp).size(64.dp)
                    .clip(RoundedCornerShape(16.dp)).background(accent),
                contentAlignment = Alignment.Center,
            ) { Text(it.toString(), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.background) }
        }
    }
}
