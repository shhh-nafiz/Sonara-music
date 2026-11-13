package com.sonara.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sonara.player.data.MediaRepo
import com.sonara.player.data.Song
import com.sonara.player.playback.PlayerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val player = PlayerController(app)

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading
    private val _byId = MutableStateFlow<Map<Long, Song>>(emptyMap())
    val byId: StateFlow<Map<Long, Song>> = _byId

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            val list = MediaRepo.loadSongs(getApplication())
            _songs.value = list
            _byId.value = list.associateBy { it.id }
            _loading.value = false
        }
    }

    override fun onCleared() {
        player.release()
    }
}
