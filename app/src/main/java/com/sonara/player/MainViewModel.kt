package com.sonara.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sonara.player.data.MediaRepo
import com.sonara.player.data.Song
import com.sonara.player.data.Store
import com.sonara.player.playback.PlayerController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val player = PlayerController(app)

    /** Everything MediaStore returned (before blacklist / folder / storage filters). */
    private val _raw = MutableStateFlow<List<Song>>(emptyList())
    val rawSongs: StateFlow<List<Song>> = _raw
    val allById: StateFlow<Map<Long, Song>> =
        _raw.map { l -> l.associateBy { it.id } }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading
    private var loaded = false

    /** The visible library: raw minus blacklisted songs, excluded folders, disabled storages, short clips. */
    val songs: StateFlow<List<Song>> = combine(
        _raw, Store.blacklist, Store.excludedFolders, Store.offVolumes, Store.minDurationSec,
    ) { raw, black, folders, off, minSec ->
        raw.filter { it.id !in black && it.folder !in folders && it.volume !in off && it.durationMs >= minSec * 1000L }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val byId: StateFlow<Map<Long, Song>> =
        songs.map { l -> l.associateBy { it.id } }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    init {
        // reload when "music files only" changes; drop queue entries that just got hidden
        viewModelScope.launch { Store.musicOnly.drop(1).collect { load() } }
        viewModelScope.launch {
            songs.collect { visible -> if (loaded && visible.isNotEmpty()) player.prune(visible.map { it.id }.toSet()) }
        }
    }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            val list = MediaRepo.loadSongs(getApplication(), Store.musicOnly.value)
            if (!Store.foldersInitialised && list.isNotEmpty()) {
                // first run: keep chat apps / recorders / ringtones out of the music library
                val junk = listOf(
                    "whatsapp", "telegram", "voice", "recording", "recorder", "ringtone", "notification",
                    "alarm", "call record", "callrecord", "messenger", "signal", "snapchat", "instagram", "tiktok",
                )
                val hide = list.map { it.folder }.distinct().filter { f -> junk.any { f.lowercase().contains(it) } }
                Store.setExcludedFolders(Store.excludedFolders.value + hide)
                Store.foldersInitialised = true
            }
            _raw.value = list
            loaded = true
            _loading.value = false
        }
    }

    /** Hide a song everywhere (and pull it out of the current queue). */
    fun toggleBlacklist(song: Song) {
        Store.toggleBlacklist(song.id)
        if (song.id in Store.blacklist.value) player.removeById(song.id)
    }

    override fun onCleared() {
        player.release()
    }
}
