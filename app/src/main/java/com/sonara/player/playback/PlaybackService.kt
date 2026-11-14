package com.sonara.player.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Tracks
import androidx.media3.common.Player
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.sonara.player.MainActivity
import com.sonara.player.R
import com.sonara.player.audio.DspAudioProcessor
import com.sonara.player.audio.DspState
import com.sonara.player.data.ArtworkLoader
import com.sonara.player.data.Store
import com.sonara.player.widget.PlayerWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.util.concurrent.Callable
import java.util.concurrent.Executors

@UnstableApi
class PlaybackService : MediaSessionService() {

    companion object {
        const val CMD_FAV = "com.sonara.player.TOGGLE_FAV"
        @Volatile var instance: PlaybackService? = null
    }

    lateinit var player: ExoPlayer
        private set
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        instance = this
        Store.init(this)
        com.sonara.player.data.ArtworkLoader.init(this)
        com.sonara.player.data.LyricsRepo.init(this)
        DspState.load()
        DspState.rgOn = Store.replayGain.value

        // High-quality pipeline: float output for hi-res, our DSP chain in front of the sink.
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(true)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessorChain(DefaultAudioSink.DefaultAudioProcessorChain(DspAudioProcessor()))
                .build()
        }

        player = ExoPlayer.Builder(this, renderers)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        player.repeatMode = when (Store.repeatMode) {
            2 -> Player.REPEAT_MODE_ONE
            1 -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }

        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(Callback())
            .setBitmapLoader(ArtBitmapLoader(this))
            .build()

        SleepTimer.onFire = { player.pause() }

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.mediaId?.toLongOrNull()?.let { Store.addRecent(it) }
                recycleShuffleCycleIfNeeded()
                refreshExtras()
                prefetchLyrics()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) consecutiveErrors = 0
                if (!isPlaying) saveResumePos()
                refreshExtras()
            }

            override fun onTracksChanged(tracks: Tracks) = readReplayGain(tracks)
            override fun onMetadata(metadata: Metadata) = scanForGain(metadata)

            // a deleted / corrupt file must not stop the music: drop it and carry on
            override fun onPlayerError(error: PlaybackException) {
                consecutiveErrors++
                if (consecutiveErrors > 5 || player.mediaItemCount <= 1) return
                player.removeMediaItem(player.currentMediaItemIndex)
                player.prepare()
                player.play()
            }
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                    refreshExtras()
                    Store.saveResumeQueue((0 until player.mediaItemCount).mapNotNull { player.getMediaItemAt(it).mediaId.toLongOrNull() })
                }
            }
        })

        // playback options
        player.setPlaybackSpeed(Store.speed.value)
        player.skipSilenceEnabled = Store.skipSilence.value
        scope.launch { Store.speed.drop(1).collect { player.setPlaybackSpeed(it) } }
        scope.launch { Store.skipSilence.drop(1).collect { player.skipSilenceEnabled = it } }
        scope.launch { fadeLoop() }
        registerNetworkWatcher()
        DspState.rgOn = Store.replayGain.value
        scope.launch { Store.replayGain.drop(1).collect { DspState.setReplayGainEnabled(it) } }
        // remember where we were so the app can resume after a restart
        scope.launch {
            while (true) {
                kotlinx.coroutines.delay(5000)
                if (player.mediaItemCount > 0) saveResumePos()
            }
        }

        scope.launch { Store.favorites.drop(1).collect { refreshExtras() } }
        scope.launch { Store.heartCompact.drop(1).collect { refreshExtras() } }
    }

    /**
     * No-repeat shuffle. The queue is a permutation of every song in the list, so nothing plays
     * twice until the whole list is done. When the LAST song of the permutation starts playing,
     * drop everything before it and append a brand-new permutation of all the other songs.
     */
    private fun recycleShuffleCycleIfNeeded() {
        if (!Store.shuffle || player.repeatMode != Player.REPEAT_MODE_ALL) return
        val count = player.mediaItemCount
        val idx = player.currentMediaItemIndex
        if (count < 2 || idx != count - 1) return
        val others = (0 until count - 1).map { player.getMediaItemAt(it) }
            .filter { m -> m.mediaId.toLongOrNull()?.let { !Store.isIgnored(it) } ?: true } // honour "ignore in shuffle"
            .shuffled()
        player.removeMediaItems(0, idx)
        player.addMediaItems(others)
    }

    /**
     * Fade through track changes: volume ramps down over the last N seconds of a song and back up over
     * the first N seconds of the next one (smoothstep curve).
     */
    private suspend fun fadeLoop() {
        while (true) {
            kotlinx.coroutines.delay(50)
            val fade = Store.fadeSec.value * 1000L
            if (fade <= 0L) { if (player.volume != 1f) player.volume = 1f; continue }
            if (!player.isPlaying) continue
            val dur = player.duration
            if (dur <= 0L || dur < fade * 2) { if (player.volume != 1f) player.volume = 1f; continue }
            val pos = player.currentPosition
            val v = minOf(pos.toFloat() / fade, (dur - pos).toFloat() / fade).coerceIn(0f, 1f)
            player.volume = v * v * (3f - 2f * v)
        }
    }

    private var consecutiveErrors = 0

    /** Download lyrics for the playing song and the next two in the queue (once, then cached). */
    private fun prefetchLyrics() {
        if (!Store.lyricsAuto.value) return
        val idx = player.currentMediaItemIndex
        val queries = (idx until minOf(idx + 3, player.mediaItemCount)).mapNotNull { i ->
            val m = player.getMediaItemAt(i)
            val id = m.mediaId.toLongOrNull() ?: return@mapNotNull null
            val md = m.mediaMetadata
            val dur = ((md.durationMs ?: 0L) / 1000L).toInt()
            if (md.title == null || dur <= 0) null
            else com.sonara.player.data.LyricQuery(id, md.title.toString(), md.artist?.toString() ?: "", md.albumTitle?.toString() ?: "", dur)
        }
        if (queries.isEmpty()) return
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            for (q in queries) com.sonara.player.data.LyricsRepo.load(this@PlaybackService, q)
        }
    }

    private var netCallback: android.net.ConnectivityManager.NetworkCallback? = null

    /** When the phone (re)connects to the internet, fetch lyrics that were missing. */
    private fun registerNetworkWatcher() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return
        val cb = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                scope.launch { prefetchLyrics() }
            }
        }
        runCatching { cm.registerDefaultNetworkCallback(cb); netCallback = cb }
    }
    private val gainRegex = Regex("""REPLAYGAIN_(TRACK|ALBUM)_GAIN\D*?(-?\d+(?:[.,]\d+)?)""", RegexOption.IGNORE_CASE)
    private var haveTrackGain = false

    private fun saveResumePos() {
        Store.saveResumePos(player.currentMediaItemIndex, player.currentPosition.coerceAtLeast(0))
    }

    /** Best effort: reads REPLAYGAIN_* tags (ID3 TXXX / Vorbis comments) and feeds the DSP chain. */
    private fun scanForGain(md: Metadata) {
        for (i in 0 until md.length()) {
            val m = gainRegex.find(md.get(i).toString()) ?: continue
            val v = m.groupValues[2].replace(',', '.').toFloatOrNull() ?: continue
            val isTrack = m.groupValues[1].equals("TRACK", ignoreCase = true)
            if (isTrack || !haveTrackGain) {
                haveTrackGain = haveTrackGain || isTrack
                DspState.setTrackGain(v)
            }
        }
    }

    private fun readReplayGain(tracks: Tracks) {
        haveTrackGain = false
        DspState.setTrackGain(0f)
        for (g in tracks.groups) {
            if (g.type != C.TRACK_TYPE_AUDIO) continue
            for (i in 0 until g.length) g.getTrackFormat(i).metadata?.let { scanForGain(it) }
        }
    }

    private fun favButton(): CommandButton {
        val id = player.currentMediaItem?.mediaId?.toLongOrNull()
        val fav = id != null && Store.isFavorite(id)
        return CommandButton.Builder(if (fav) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED)
            .setDisplayName(if (fav) "Remove from favorites" else "Add to favorites")
            .setSessionCommand(SessionCommand(CMD_FAV, Bundle.EMPTY))
            // Android 13+ has 5 slots: overflow = extra slots 4/5 (expanded view), BACK = replaces Previous (compact view too)
            .setSlots(if (Store.heartCompact.value) CommandButton.SLOT_BACK else CommandButton.SLOT_OVERFLOW)
            .build()
    }

    private fun refreshExtras() {
        session?.setMediaButtonPreferences(ImmutableList.of(favButton()))
        scope.launch { PlayerWidget.pushFromPlayer(this@PlaybackService, player) }
    }

    private inner class Callback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val cmds = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                .buildUpon().add(SessionCommand(CMD_FAV, Bundle.EMPTY)).build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(cmds)
                .setMediaButtonPreferences(ImmutableList.of(favButton()))
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == CMD_FAV) {
                player.currentMediaItem?.mediaId?.toLongOrNull()?.let { Store.toggleFavorite(it) }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        instance = null
        SleepTimer.onFire = null
        netCallback?.let { cb ->
            runCatching { (getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager).unregisterNetworkCallback(cb) }
        }
        scope.cancel()
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}

/** Pulls cover art (embedded tag picture) for notification / lock screen / Bluetooth. */
@UnstableApi
class ArtBitmapLoader(private val ctx: Context) : BitmapLoader {
    private val ex = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(2))
    override fun supportsMimeType(mimeType: String) = true
    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        ex.submit(Callable { BitmapFactory.decodeByteArray(data, 0, data.size) ?: ArtworkLoader.placeholder() })

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        ex.submit(Callable { ArtworkLoader.loadBlocking(ctx, uri, 512) ?: ArtworkLoader.placeholder() })
}
