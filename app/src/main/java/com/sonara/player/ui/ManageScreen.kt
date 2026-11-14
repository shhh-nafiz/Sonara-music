package com.sonara.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonara.player.MainViewModel
import com.sonara.player.data.Song
import com.sonara.player.data.Store

private val MANAGE_TABS = listOf("Ignored", "Blacklist", "Sources")

private fun prettyFolder(f: String) = f.replace(Regex("^/storage/[^/]+(/0)?/?"), "").ifEmpty { "(root)" }

@Composable
fun ManageScreen(vm: MainViewModel, onBack: () -> Unit) {
    val accent = LocalAccent.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .statusBarsPadding().navigationBarsPadding(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Manage library", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        TabRow(tab, containerColor = Color.Transparent, contentColor = accent) {
            MANAGE_TABS.forEachIndexed { i, t ->
                Tab(tab == i, { tab = i }, text = { Text(t, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal) })
            }
        }
        when (tab) {
            0 -> SongStateList(vm, blacklist = false)
            1 -> SongStateList(vm, blacklist = true)
            else -> SourcesTab(vm)
        }
    }
}

/** Lists songs that are ignored (or blacklisted); search finds any song so it can be toggled. */
@Composable
private fun SongStateList(vm: MainViewModel, blacklist: Boolean) {
    val accent = LocalAccent.current
    val raw by vm.rawSongs.collectAsState()
    val visible by vm.songs.collectAsState()
    val ignored by Store.ignored.collectAsState()
    val black by Store.blacklist.collectAsState()
    var q by rememberSaveable { mutableStateOf("") }
    val marked = if (blacklist) black else ignored

    val list: List<Song> = remember(q, raw, visible, marked, blacklist) {
        if (q.isBlank()) {
            (if (blacklist) raw else visible).filter { it.id in marked }
        } else {
            val t = q.trim().lowercase()
            (if (blacklist) raw else visible)
                .filter { it.title.lowercase().contains(t) || it.artist.lowercase().contains(t) || it.album.lowercase().contains(t) }
                .take(150)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            if (blacklist) "Blacklisted songs are hidden everywhere and never play. Search any song to blacklist it, or switch it off here to restore it."
            else "Ignored songs stay in your library but are skipped by shuffle. Search any song to ignore it, or switch it off here to bring it back.",
            fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(16.dp),
        )
        TextField(
            q, { q = it }, singleLine = true, placeholder = { Text("Search songs to ${if (blacklist) "blacklist" else "ignore"}") },
            leadingIcon = { Icon(Icons.Default.Search, null) }, shape = RoundedCornerShape(8.dp),
            colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        LazyColumn(Modifier.fillMaxSize()) {
            if (list.isEmpty()) item {
                EmptyHint(if (q.isBlank()) "Nothing here yet" else "No matches")
            }
            items(list, key = { it.id }) { s ->
                val on = s.id in marked
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Cover(s, Modifier.size(48.dp), px = 128)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
                        Text("${s.artist} • ${prettyFolder(s.folder)}", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = TextSecondary)
                    }
                    Switch(
                        on,
                        { if (blacklist) vm.toggleBlacklist(s) else Store.toggleIgnored(s.id) },
                        colors = SwitchDefaults.colors(checkedTrackColor = accent, checkedThumbColor = Color.White),
                    )
                }
            }
        }
    }
}

/** Choose which storage (internal / SD card) and which folders the library is built from. */
@Composable
private fun SourcesTab(vm: MainViewModel) {
    val accent = LocalAccent.current
    val raw by vm.rawSongs.collectAsState()
    val excluded by Store.excludedFolders.collectAsState()
    val offVolumes by Store.offVolumes.collectAsState()
    val minDur by Store.minDurationSec.collectAsState()
    val musicOnly by Store.musicOnly.collectAsState()

    val volumes = remember(raw) { raw.groupBy { it.volume }.mapValues { it.value.size }.toList().sortedBy { it.first } }
    val folders = remember(raw) {
        raw.groupBy { it.volume to it.folder }.map { (k, v) -> Triple(k.first, k.second, v.size) }
            .sortedWith(compareBy({ it.first }, { it.second.lowercase() }))
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Music files only", fontWeight = FontWeight.Medium)
                    Text("Skips files Android doesn't flag as music (voice notes, recordings)", fontSize = 12.sp, color = TextSecondary)
                }
                Switch(musicOnly, { Store.setMusicOnly(it) }, colors = SwitchDefaults.colors(checkedTrackColor = accent, checkedThumbColor = Color.White))
            }
            Column(Modifier.padding(horizontal = 16.dp)) {
                Row {
                    Text("Ignore clips shorter than", fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text(if (minDur == 0) "off" else "$minDur s", color = TextSecondary)
                }
                Slider(
                    minDur.toFloat(), { Store.setMinDuration(Math.round(it / 5f) * 5) }, valueRange = 0f..180f, steps = 35,
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
                )
            }
            Text("Storage", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
        }
        items(volumes, key = { it.first }) { (v, count) ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(v, fontWeight = FontWeight.Medium)
                    Text("$count audio files", fontSize = 12.sp, color = TextSecondary)
                }
                Switch(
                    v !in offVolumes, { Store.toggleVolume(v) },
                    colors = SwitchDefaults.colors(checkedTrackColor = accent, checkedThumbColor = Color.White),
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Folders", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton({ Store.setExcludedFolders(emptySet()) }) { Text("All on", color = accent) }
                TextButton({ Store.setExcludedFolders(folders.map { it.second }.toSet()) }) { Text("All off", color = accent) }
            }
            Text(
                "Untick folders you don't want in the player (e.g. WhatsApp Audio). WhatsApp, recorder and ringtone folders were switched off automatically.",
                fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        items(folders, key = { it.first + it.second }) { (vol, folder, count) ->
            val on = folder !in excluded
            Row(
                Modifier.fillMaxWidth().clickable { Store.toggleFolder(folder) }.padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(on, { Store.toggleFolder(folder) }, colors = CheckboxDefaults.colors(checkedColor = accent))
                Column(Modifier.weight(1f)) {
                    Text(prettyFolder(folder), maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                    Text("$vol • $count songs", fontSize = 11.sp, color = TextSecondary)
                }
            }
        }
    }
}
