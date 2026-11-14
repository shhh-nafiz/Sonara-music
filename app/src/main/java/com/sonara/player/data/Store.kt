package com.sonara.player.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Tiny persistent store (SharedPreferences) shared by the UI, the service and the widget. */
object Store {
    private lateinit var sp: SharedPreferences
    private var inited = false

    private val _favorites = MutableStateFlow<Set<Long>>(emptySet())
    val favorites: StateFlow<Set<Long>> = _favorites

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists

    private val _recents = MutableStateFlow<List<Long>>(emptyList())
    val recents: StateFlow<List<Long>> = _recents

    // Library filters
    private val _blacklist = MutableStateFlow<Set<Long>>(emptySet())
    val blacklist: StateFlow<Set<Long>> = _blacklist
    private val _ignored = MutableStateFlow<Set<Long>>(emptySet())
    /** Songs that stay in the library but are skipped by shuffle. */
    val ignored: StateFlow<Set<Long>> = _ignored
    private val _excludedFolders = MutableStateFlow<Set<String>>(emptySet())
    val excludedFolders: StateFlow<Set<String>> = _excludedFolders
    private val _offVolumes = MutableStateFlow<Set<String>>(emptySet())
    val offVolumes: StateFlow<Set<String>> = _offVolumes
    private val _minDur = MutableStateFlow(20)
    val minDurationSec: StateFlow<Int> = _minDur
    private val _musicOnly = MutableStateFlow(true)
    val musicOnly: StateFlow<Boolean> = _musicOnly

    // Theme
    private val _accent = MutableStateFlow(0xFF1ED760.toInt())
    val accent: StateFlow<Int> = _accent
    private val _bg = MutableStateFlow(0xFF121212.toInt())
    val bg: StateFlow<Int> = _bg
    private val _adaptive = MutableStateFlow(true)
    val adaptive: StateFlow<Boolean> = _adaptive
    private val _vizStyle = MutableStateFlow(0)
    val vizStyle: StateFlow<Int> = _vizStyle

    fun init(context: Context) {
        if (inited) return
        sp = context.applicationContext.getSharedPreferences("sonara", Context.MODE_PRIVATE)
        inited = true
        _favorites.value = sp.getString("fav", "")!!.split(',').mapNotNull { it.toLongOrNull() }.toSet()
        _recents.value = sp.getString("recents", "")!!.split(',').mapNotNull { it.toLongOrNull() }
        _accent.value = sp.getInt("accent", _accent.value)
        _bg.value = sp.getInt("bg", _bg.value)
        _adaptive.value = sp.getBoolean("adaptive", true)
        _vizStyle.value = sp.getInt("viz", 0)
        _blacklist.value = sp.getString("black", "")!!.split(',').mapNotNull { it.toLongOrNull() }.toSet()
        _ignored.value = sp.getString("ignored", "")!!.split(',').mapNotNull { it.toLongOrNull() }.toSet()
        _excludedFolders.value = sp.getStringSet("exFolders", emptySet())!!.toSet()
        _offVolumes.value = sp.getStringSet("offVolumes", emptySet())!!.toSet()
        _minDur.value = sp.getInt("minDur", 20)
        _musicOnly.value = sp.getBoolean("musicOnly", true)
        runCatching {
            val arr = JSONArray(sp.getString("playlists", "[]"))
            _playlists.value = (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                val ids = o.getJSONArray("songs")
                Playlist(o.getString("id"), o.getString("name"), (0 until ids.length()).map { i -> ids.getLong(i) })
            }
        }
    }

    // ---- favorites ----
    fun isFavorite(id: Long) = id in _favorites.value

    fun toggleFavorite(id: Long) {
        val s = _favorites.value.toMutableSet()
        if (!s.add(id)) s.remove(id)
        _favorites.value = s
        sp.edit().putString("fav", s.joinToString(",")).apply()
    }

    // ---- recents ----
    fun addRecent(id: Long) {
        val l = (listOf(id) + _recents.value.filter { it != id }).take(30)
        _recents.value = l
        sp.edit().putString("recents", l.joinToString(",")).apply()
    }

    // ---- playlists ----
    private fun savePlaylists() {
        val arr = JSONArray()
        _playlists.value.forEach { p ->
            arr.put(JSONObject().put("id", p.id).put("name", p.name).put("songs", JSONArray(p.songIds)))
        }
        sp.edit().putString("playlists", arr.toString()).apply()
    }

    fun createPlaylist(name: String, firstSong: Long? = null): String {
        val p = Playlist(UUID.randomUUID().toString(), name, listOfNotNull(firstSong))
        _playlists.value = _playlists.value + p
        savePlaylists()
        return p.id
    }

    fun addToPlaylist(playlistId: String, songId: Long) {
        _playlists.value = _playlists.value.map {
            if (it.id == playlistId && songId !in it.songIds) it.copy(songIds = it.songIds + songId) else it
        }
        savePlaylists()
    }

    fun removeFromPlaylist(playlistId: String, songId: Long) {
        _playlists.value = _playlists.value.map {
            if (it.id == playlistId) it.copy(songIds = it.songIds - songId) else it
        }
        savePlaylists()
    }

    fun deletePlaylist(playlistId: String) {
        _playlists.value = _playlists.value.filter { it.id != playlistId }
        savePlaylists()
    }

    // ---- library filters ----
    fun isIgnored(id: Long) = id in _ignored.value
    fun toggleBlacklist(id: Long) {
        val s = _blacklist.value.toMutableSet()
        if (!s.add(id)) s.remove(id)
        _blacklist.value = s
        sp.edit().putString("black", s.joinToString(",")).apply()
    }
    fun toggleIgnored(id: Long) {
        val s = _ignored.value.toMutableSet()
        if (!s.add(id)) s.remove(id)
        _ignored.value = s
        sp.edit().putString("ignored", s.joinToString(",")).apply()
    }
    fun setExcludedFolders(s: Set<String>) {
        _excludedFolders.value = s
        sp.edit().putStringSet("exFolders", s).apply()
    }
    fun toggleFolder(folder: String) {
        val s = _excludedFolders.value.toMutableSet()
        if (!s.add(folder)) s.remove(folder)
        setExcludedFolders(s)
    }
    fun toggleVolume(v: String) {
        val s = _offVolumes.value.toMutableSet()
        if (!s.add(v)) s.remove(v)
        _offVolumes.value = s
        sp.edit().putStringSet("offVolumes", s).apply()
    }
    fun setMinDuration(sec: Int) { _minDur.value = sec; sp.edit().putInt("minDur", sec).apply() }
    fun setMusicOnly(b: Boolean) { _musicOnly.value = b; sp.edit().putBoolean("musicOnly", b).apply() }
    var foldersInitialised: Boolean
        get() = sp.getBoolean("foldersInit", false)
        set(v) = sp.edit().putBoolean("foldersInit", v).apply()

    // ---- theme ----
    fun setAccent(c: Int) { _accent.value = c; sp.edit().putInt("accent", c).apply() }
    fun setBg(c: Int) { _bg.value = c; sp.edit().putInt("bg", c).apply() }
    fun setAdaptive(b: Boolean) { _adaptive.value = b; sp.edit().putBoolean("adaptive", b).apply() }
    fun setVizStyle(i: Int) { _vizStyle.value = i; sp.edit().putInt("viz", i).apply() }

    // ---- playback prefs (read by service) ----
    var shuffle: Boolean
        get() = sp.getBoolean("shuffle", false)
        set(v) = sp.edit().putBoolean("shuffle", v).apply()

    var repeatMode: Int // 0 off, 1 all, 2 one
        get() = sp.getInt("repeat", 1)
        set(v) = sp.edit().putInt("repeat", v).apply()

    // ---- equalizer prefs ----
    fun getString(key: String, def: String): String = sp.getString(key, def) ?: def
    fun putString(key: String, v: String) = sp.edit().putString(key, v).apply()
    fun getBool(key: String, def: Boolean) = sp.getBoolean(key, def)
    fun putBool(key: String, v: Boolean) = sp.edit().putBoolean(key, v).apply()
    fun getFloat(key: String, def: Float) = sp.getFloat(key, def)
    fun putFloat(key: String, v: Float) = sp.edit().putFloat(key, v).apply()
}
