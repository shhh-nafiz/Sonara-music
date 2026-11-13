package com.sonara.player.playback

import android.content.ComponentName
import android.content.Context
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

data class PlayerUi(
    val currentId: Long? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: Int = 1, // 0 off, 1 all, 2 one
    val buffering: Boolean = false,
)

/** UI-side handle on the playback service (MediaController) + queue building / shuffle logic. */
class PlayerController(private val app: Context) {

    private var controller: MediaController? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var ticker: Job? = null
    private var pending: (() -> Unit)? = null

    /** Songs of the list the user started playing from, in their natural order. */
    private var contextOrdered: List<Song> = emptyList()

    private val _ui = MutableStateFlow(PlayerUi(shuffle = Store.shuffle, repeat = Store.repeatMode))
    val ui: StateFlow<PlayerUi> = _ui
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

        ticker = scope.launch {
            while (true) {
                delay(250)
                val c = controller ?: continue
                if (c.isPlaying) publish()
            }
        }
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
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = c.duration.takeIf { it > 0 } ?: 0,
            shuffle = Store.shuffle,
            repeat = Store.repeatMode,
            buffering = c.playbackState == Player.STATE_BUFFERING,
        )
        _queueIndex.value = c.currentMediaItemIndex
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
     * Play [songs]. With shuffle on, the queue is [start] followed by a random permutation of
     * every other song, so nothing repeats until the whole list has been played.
     */
    fun play(songs: List<Song>, startId: Long? = null, shuffle: Boolean = Store.shuffle) {
        if (songs.isEmpty()) return
        Store.shuffle = shuffle
        contextOrdered = songs
        val start = songs.firstOrNull { it.id == startId } ?: if (shuffle) songs.random() else songs.first()
        withController { c ->
            if (shuffle) {
                val q = listOf(start) + songs.filter { it.id != start.id }.shuffled()
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
                    base.filter { it.id != curId }.shuffled()
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

    fun release() {
        ticker?.cancel()
        scope.cancel()
        controller?.release()
        controller = null
    }
}
