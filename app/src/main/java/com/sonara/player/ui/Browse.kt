package com.sonara.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonara.player.MainViewModel
import com.sonara.player.data.MediaRepo
import com.sonara.player.data.Song
import com.sonara.player.data.Store
import java.util.Calendar
import kotlinx.coroutines.launch

/** What a detail page shows. */
data class Detail(val kind: String, val key: String) // kind: album | artist | playlist | favorites | recents

private fun greeting(): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when { h < 5 -> "Good night"; h < 12 -> "Good morning"; h < 18 -> "Good afternoon"; else -> "Good evening" }
}

// ------------------------------------------------------------------ HOME

@Composable
fun HomeScreen(vm: MainViewModel, onOpen: (Detail) -> Unit, onSettings: () -> Unit) {
    val songs by vm.songs.collectAsState()
    val byId by vm.byId.collectAsState()
    val favIds by Store.favorites.collectAsState()
    val recentIds by Store.recents.collectAsState()
    val playlists by Store.playlists.collectAsState()
    val ui by vm.player.ui.collectAsState()
    val accent = LocalAccent.current

    val favs = favIds.mapNotNull { byId[it] }
    val recents = recentIds.mapNotNull { byId[it] }
    val added = remember(songs) { songs.sortedByDescending { it.dateAdded }.take(20) }
    val loading by vm.loading.collectAsState()

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)) {
        item {
            Row(Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(greeting(), fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") }
            }
        }
        // quick tiles (like Spotify's home grid)
        item {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickTile("Liked Songs", Modifier.weight(1f), icon = Icons.Default.Favorite, iconTint = accent) {
                        onOpen(Detail("favorites", ""))
                    }
                    QuickTile("Shuffle all", Modifier.weight(1f), icon = Icons.Default.Shuffle, iconTint = accent) {
                        vm.player.shuffleAll(songs)
                    }
                }
                playlists.take(4).chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { p ->
                            QuickTile(p.name, Modifier.weight(1f), song = byId[p.songIds.firstOrNull()]) { onOpen(Detail("playlist", p.id)) }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        if (favs.isNotEmpty()) item {
            SectionTitle("Your favorites")
            SongCarousel(favs, ui.currentId) { vm.player.play(favs, it.id) }
        }
        if (recents.isNotEmpty()) item {
            SectionTitle("Recently played")
            SongCarousel(recents, ui.currentId) { vm.player.play(recents, it.id) }
        }
        if (added.isNotEmpty()) item {
            SectionTitle("Recently added")
            SongCarousel(added, ui.currentId) { vm.player.play(added, it.id) }
        }
        if (songs.isEmpty()) item {
            if (loading) Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator(color = accent)
            } else EmptyHint("No music found yet. Add songs to your phone, then rescan in Settings → Manage library.")
        }
    }
}

@Composable
private fun QuickTile(
    title: String, modifier: Modifier, song: Song? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null, iconTint: Color = Color.White,
    onClick: () -> Unit,
) {
    Row(
        modifier.height(56.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surface).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(Modifier.size(56.dp).background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = iconTint)
            }
        } else Cover(song, Modifier.size(56.dp), px = 128, corner = 0.dp)
        Text(title, Modifier.padding(horizontal = 10.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SongCarousel(list: List<Song>, currentId: Long?, onClick: (Song) -> Unit) {
    LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(list, key = { it.id }) { s ->
            Column(Modifier.width(140.dp).clickable { onClick(s) }) {
                Cover(s, Modifier.size(140.dp), px = 320, corner = 6.dp)
                Text(
                    s.title, Modifier.padding(top = 6.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, color = if (s.id == currentId) LocalAccent.current else MaterialTheme.colorScheme.onBackground,
                )
                Text(s.artist, fontSize = 12.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ------------------------------------------------------------------ SEARCH

@Composable
fun SearchScreen(vm: MainViewModel) {
    val songs by vm.songs.collectAsState()
    val ui by vm.player.ui.collectAsState()
    val playlists by Store.playlists.collectAsState()
    var q by rememberSaveable { mutableStateOf("") }
    val res = remember(q, songs) {
        if (q.isBlank()) emptyList() else {
            val t = q.trim().lowercase()
            songs.filter { it.title.lowercase().contains(t) || it.artist.lowercase().contains(t) || it.album.lowercase().contains(t) }
        }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text("Search", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
        TextField(
            q, { q = it }, singleLine = true,
            placeholder = { Text("Songs, artists, albums") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            shape = RoundedCornerShape(8.dp),
            colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
            items(res, key = { it.id }) { s ->
                SongRow(s, s.id == ui.currentId, vm.player, playlists, { vm.player.play(res, s.id) })
            }
            if (q.isNotBlank() && res.isEmpty()) item { EmptyHint("No results for \"$q\"") }
        }
    }
}

// ------------------------------------------------------------------ LIBRARY

private val TABS = listOf("Songs", "Albums", "Artists", "Folders", "Playlists", "Favorites")
private val SORTS = listOf("Title", "Artist", "Recent", "Length", "Album")
private fun folderName(f: String) = f.substringAfterLast('/').ifEmpty { f }

@Composable
fun LibraryScreen(vm: MainViewModel, onOpen: (Detail) -> Unit, onSettings: () -> Unit) {
    val songs by vm.songs.collectAsState()
    val byId by vm.byId.collectAsState()
    val ui by vm.player.ui.collectAsState()
    val playlists by Store.playlists.collectAsState()
    val favIds by Store.favorites.collectAsState()
    val accent = LocalAccent.current
    val loading by vm.loading.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val albums = remember(songs) { MediaRepo.albums(songs) }
    val artists = remember(songs) { MediaRepo.artists(songs) }
    val sort by Store.sortMode.collectAsState()
    val sorted = remember(songs, sort) {
        when (sort) {
            0 -> songs.sortedBy { it.title.lowercase() }
            1 -> songs.sortedBy { it.artist.lowercase() }
            2 -> songs.sortedByDescending { it.dateAdded }
            3 -> songs.sortedByDescending { it.durationMs }
            else -> songs.sortedBy { it.album.lowercase() }
        }
    }
    val folders = remember(songs) { songs.groupBy { it.folder }.toList().sortedBy { it.first.lowercase() } }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Your Library", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") }
        }
        ScrollableTabRow(
            tab, containerColor = Color.Transparent, edgePadding = 8.dp, contentColor = accent,
            divider = {},
        ) {
            TABS.forEachIndexed { i, t ->
                Tab(tab == i, { tab = i }, text = {
                    Text(t, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal,
                        color = if (tab == i) accent else MaterialTheme.colorScheme.onBackground)
                })
            }
        }
        when (tab) {
            0 -> Box(Modifier.fillMaxSize()) {
              val listState = androidx.compose.foundation.lazy.rememberLazyListState()
              val scope = androidx.compose.runtime.rememberCoroutineScope()
              val letterIndex = remember(sorted, sort) {
                  val m = LinkedHashMap<Char, Int>()
                  if (sort == 0 || sort == 1 || sort == 4) {
                      sorted.forEachIndexed { i, s ->
                          val text = when (sort) { 0 -> s.title; 1 -> s.artist; else -> s.album }
                          val c = text.trim().firstOrNull()?.uppercaseChar() ?: '#'
                          m.putIfAbsent(if (c in 'A'..'Z') c else '#', i)
                      }
                  }
                  m
              }
              LazyColumn(state = listState, contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)) {
                item {
                    LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(SORTS.size) { i ->
                            val sel = sort == i
                            Text(
                                SORTS[i], fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                color = if (sel) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.clip(RoundedCornerShape(50))
                                    .background(if (sel) accent else MaterialTheme.colorScheme.surface)
                                    .clickable { Store.setSort(i) }.padding(horizontal = 14.dp, vertical = 7.dp),
                            )
                        }
                    }
                }
                item { PlayHeader(sorted, vm, "${songs.size} songs") }
                items(sorted, key = { it.id }) { s ->
                    SongRow(s, s.id == ui.currentId, vm.player, playlists, { vm.player.play(sorted, s.id) })
                }
                if (songs.isEmpty()) item {
                    if (loading) Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.CircularProgressIndicator(color = accent)
                    } else EmptyHint("No songs found")
                }
              }
              if (letterIndex.size > 1) AlphabetScroller(
                  letterIndex.keys.toList(),
                  Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(26.dp).padding(vertical = 12.dp),
              ) { c -> scope.launch { listState.scrollToItem(2 + (letterIndex[c] ?: 0)) } }
            }
            1 -> LazyColumn {
                items(albums, key = { it.id }) { a ->
                    ListRow(a.name, "${a.artist} • ${a.songs.size} songs", a.songs.first()) { onOpen(Detail("album", a.id.toString())) }
                }
            }
            2 -> LazyColumn {
                items(artists, key = { it.name }) { a ->
                    ListRow(a.name, "${a.songs.size} songs", a.songs.first(), round = true) { onOpen(Detail("artist", a.name)) }
                }
            }
            3 -> LazyColumn {
                items(folders, key = { it.first }) { (f, list) ->
                    ListRow(folderName(f), "${list.size} songs • $f", list.first()) { onOpen(Detail("folder", f)) }
                }
            }
            4 -> LazyColumn {
                if (playlists.isEmpty()) item { EmptyHint("No playlists yet. Use ⋮ on any song → Add to playlist.") }
                items(playlists, key = { it.id }) { p ->
                    ListRow(p.name, "${p.songIds.size} songs", byId[p.songIds.firstOrNull()]) { onOpen(Detail("playlist", p.id)) }
                }
            }
            else -> {
                val favs = favIds.mapNotNull { byId[it] }
                LazyColumn {
                    item { PlayHeader(favs, vm, "${favs.size} liked songs") }
                    if (favs.isEmpty()) item { EmptyHint("Tap the heart on any song to add it here.") }
                    items(favs, key = { it.id }) { s ->
                        SongRow(s, s.id == ui.currentId, vm.player, playlists, { vm.player.play(favs, s.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ListRow(title: String, sub: String, song: Song?, round: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Cover(song, Modifier.size(56.dp), px = 128, corner = if (round) 28.dp else 4.dp)
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, fontSize = 13.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PlayHeader(list: List<Song>, vm: MainViewModel, label: String) {
    val accent = LocalAccent.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        IconButton(onClick = { vm.player.shuffleAll(list) }) { Icon(Icons.Default.Shuffle, "Shuffle play", tint = accent) }
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(accent).clickable { vm.player.play(list, null, Store.shuffle) },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.PlayArrow, "Play", tint = MaterialTheme.colorScheme.background, modifier = Modifier.size(30.dp)) }
    }
}

// ------------------------------------------------------------------ DETAIL (album / artist / playlist / liked)

@Composable
fun DetailScreen(vm: MainViewModel, d: Detail, onBack: () -> Unit) {
    val songs by vm.songs.collectAsState()
    val byId by vm.byId.collectAsState()
    val ui by vm.player.ui.collectAsState()
    val playlists by Store.playlists.collectAsState()
    val favIds by Store.favorites.collectAsState()

    val (title, list) = when (d.kind) {
        "album" -> songs.filter { it.albumId.toString() == d.key }.sortedBy { it.track }.let { (it.firstOrNull()?.album ?: "Album") to it }
        "artist" -> d.key to songs.filter { it.artist == d.key }
        "folder" -> folderName(d.key) to songs.filter { it.folder == d.key }
        "playlist" -> {
            val p = playlists.firstOrNull { it.id == d.key }
            (p?.name ?: "Playlist") to (p?.songIds?.mapNotNull { byId[it] } ?: emptyList())
        }
        else -> "Liked Songs" to favIds.mapNotNull { byId[it] }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (d.kind == "playlist") {
                androidx.compose.material3.TextButton(onClick = { Store.deletePlaylist(d.key); onBack() }) { Text("Delete") }
            }
        }
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 120.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Cover(list.firstOrNull(), Modifier.size(200.dp), px = 512, corner = 8.dp)
                }
                PlayHeader(list, vm, "${list.size} songs")
            }
            items(list, key = { it.id }) { s ->
                SongRow(
                    s, s.id == ui.currentId, vm.player, playlists, { vm.player.play(list, s.id) },
                    extraMenu = { dismiss ->
                        if (d.kind == "playlist") androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Remove from this playlist") },
                            onClick = { Store.removeFromPlaylist(d.key, s.id); dismiss() },
                        )
                    },
                )
            }
        }
    }
}
