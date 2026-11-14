package com.sonara.player.playback

import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.sonara.player.data.Song
import com.sonara.player.data.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Changes rarely (track change, play/pause, shuffle...) so lists don't recompose while a song plays. */
data class PlayerUi(
    val currentId: Long? = null,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: Int = 1, // 0 off, 1 all, 2 one
    val buffering: Boolean = false,
)

/** Playback position at a moment in time; the UI extrapolates it every frame for a buttery seek bar. */
data class Anchor(val positionMs: Long, val atElapsed: Long, val playing: Boolean)

class PlayerController(private val app: Context) {

    private var controller: MediaController? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var ticker: Job? = null
    private var pending: (() -> Unit)? = null
    private var contextOrdered: List<Song> = emptyList()

    private val _ui = MutableStateFlow(PlayerUi(shuffle = Store.shuffle, repeat = Store.repeatMode))
    val ui: StateFlow<PlayerUi> = _ui
    private val _anchor = MutableStateFlow(Anchor(0, SystemClock.elapsedRealtime(), false))
    val anchor: StateFlow<Anchor> = _anchor
    private val _queue = MutableStateFlow<List<Long>>(emptyList())
    val queue: StateFlow<List<Long>> = _queue
    private val _queueIndex = MutableStateFlow(0)
    val queueIndex: StateFlow<Int> = _queueIndex

    init {
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            val c = future.get()
            controller = c
            c.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    if (events.contains(Player.EVENT_TIMELINE_CHANGED) ||
                        events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)
                    ) publishQueue()
                    publish()
                }
            })
            publishQueue(); publish()
            pending?.invoke(); pending = null
        }, MoreExecutors.directExecutor())

        // re-sync the anchor now and then so extrapolation never drifts
        ticker = scope.launch {
            while (true) {
                delay(2000)
                val c = controller ?: continue
                if (c.isPlaying) publishAnchor(c)
            }
        }
    }

    private fun publishAnchor(c: MediaController) {
        _anchor.value = Anchor(c.currentPosition.coerceAtLeast(0), SystemClock.elapsedRealtime(), c.isPlaying)
    }

    private fun publishQueue() {
        val c = controller ?: return
        _queue.value = (0 until c.mediaItemCount).mapNotNull { c.getMediaItemAt(it).mediaId.toLongOrNull() }
        _queueIndex.value = c.currentMediaItemIndex
    }

    private fun publish() {
        val c = controller ?: return
        _ui.value = PlayerUi(
            currentId = c.currentMediaItem?.mediaId?.toLongOrNull(),
            isPlaying = c.isPlaying,
            durationMs = c.duration.takeIf { it > 0 } ?: 0,
            shuffle = Store.shuffle,
            repeat = Store.repeatMode,
            buffering = c.playbackState == Player.STATE_BUFFERING,
        )
        _queueIndex.value = c.currentMediaItemIndex
        publishAnchor(c)
    }

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) block(c) else pending = { controller?.let(block) }
    }

    private fun item(s: Song): MediaItem = MediaItem.Builder()
        .setMediaId(s.id.toString())
        .setUri(s.uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(s.title).setArtist(s.artist).setAlbumTitle(s.album)
                .setArtworkUri(s.uri) // our BitmapLoader pulls the embedded cover from the audio file
                .setIsPlayable(true).setIsBrowsable(false)
                .build()
        ).build()

    // ------------------------------------------------------------------ playback

    /**
     * Play [songs]. With shuffle on, the queue is [startId]'s song followed by a random permutation of
     * every other song except those marked "ignore in shuffle", so nothing repeats until the list is done.
     */
    fun play(songs: List<Song>, startId: Long? = null, shuffle: Boolean = Store.shuffle) {
        if (songs.isEmpty()) return
        Store.shuffle = shuffle
        contextOrdered = songs
        val eligible = songs.filter { !Store.isIgnored(it.id) }
        val start = songs.firstOrNull { it.id == startId }
            ?: if (shuffle) (eligible.ifEmpty { songs }).random() else songs.first()
        withController { c ->
            if (shuffle) {
                val q = listOf(start) + eligible.filter { it.id != start.id }.shuffled()
                c.setMediaItems(q.map(::item), 0, 0L)
            } else {
                c.setMediaItems(songs.map(::item), songs.indexOf(start), 0L)
            }
            c.prepare(); c.play(); publish()
        }
    }

    fun shuffleAll(songs: List<Song>) = play(songs, null, true)

    fun playQueueIndex(i: Int) = withController { it.seekTo(i, 0L); it.play() }
    fun togglePlay() = withController { if (it.isPlaying) it.pause() else it.play() }
    fun next() = withController { it.seekToNext() }
    fun previous() = withController { it.seekToPrevious() }
    fun seekTo(ms: Long) = withController { it.seekTo(ms); publish() }

    fun toggleShuffle(allSongs: List<Song>) {
        val on = !Store.shuffle
        Store.shuffle = on
        withController { c ->
            if (c.mediaItemCount > 0) {
                val idx = c.currentMediaItemIndex
                val curId = c.currentMediaItem?.mediaId?.toLongOrNull()
                if (idx < c.mediaItemCount - 1) c.removeMediaItems(idx + 1, c.mediaItemCount)
                if (idx > 0) c.removeMediaItems(0, idx)
                val base = contextOrdered.ifEmpty { allSongs }
                val upcoming: List<Song> = if (on) {
                    base.filter { it.id != curId && !Store.isIgnored(it.id) }.shuffled()
                } else {
                    val pos = base.indexOfFirst { it.id == curId }
                    val after = if (pos >= 0) base.drop(pos + 1) else base.filter { it.id != curId }
                    val before = if (pos > 0 && Store.repeatMode != 0) base.take(pos) else emptyList()
                    after + before
                }
                c.addMediaItems(upcoming.map(::item))
            }
            publish()
        }
    }

    fun cycleRepeat() {
        val m = (Store.repeatMode + 1) % 3
        Store.repeatMode = m
        withController {
            it.repeatMode = when (m) {
                2 -> Player.REPEAT_MODE_ONE
                1 -> Player.REPEAT_MODE_ALL
                else -> Player.REPEAT_MODE_OFF
            }
            publish()
        }
    }

    fun playNext(s: Song) = withController {
        it.addMediaItem(if (it.mediaItemCount == 0) 0 else it.currentMediaItemIndex + 1, item(s))
        if (it.mediaItemCount == 1) { it.prepare(); it.play() }
    }

    fun addToQueue(s: Song) = withController {
        it.addMediaItem(item(s))
        if (it.mediaItemCount == 1) { it.prepare(); it.play() }
    }

    fun removeFromQueue(index: Int) = withController {
        if (index != it.currentMediaItemIndex) it.removeMediaItem(index)
    }

    /** Remove every queue entry of this song (used when blacklisting). Skips to next if it is playing. */
    fun removeById(id: Long) = withController { c ->
        val cur = c.currentMediaItemIndex
        for (i in c.mediaItemCount - 1 downTo 0) {
            if (c.getMediaItemAt(i).mediaId == id.toString()) c.removeMediaItem(i)
        }
        if (c.mediaItemCount == 0) c.stop()
        if (cur >= 0) publish()
    }

    /** Drop queue entries that are no longer visible in the library (folder excluded etc.). */
    fun prune(visible: Set<Long>) = withController { c ->
        for (i in c.mediaItemCount - 1 downTo 0) {
            val id = c.getMediaItemAt(i).mediaId.toLongOrNull() ?: continue
            if (id !in visible) c.removeMediaItem(i)
        }
    }

    fun release() {
        ticker?.cancel()
        scope.cancel()
        controller?.release()
        controller = null
    }
}
