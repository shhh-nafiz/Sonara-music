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
        DspState.load()

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
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) = refreshExtras()
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) refreshExtras()
            }
        })

        scope.launch { Store.favorites.drop(1).collect { refreshExtras() } }
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
        val others = (0 until count - 1).map { player.getMediaItemAt(it) }.shuffled()
        player.removeMediaItems(0, idx)
        player.addMediaItems(others)
    }

    private fun favButton(): CommandButton {
        val id = player.currentMediaItem?.mediaId?.toLongOrNull()
        val fav = id != null && Store.isFavorite(id)
        return CommandButton.Builder()
            .setDisplayName(if (fav) "Remove from favorites" else "Add to favorites")
            .setIconResId(if (fav) R.drawable.ic_heart else R.drawable.ic_heart_outline)
            .setSessionCommand(SessionCommand(CMD_FAV, Bundle.EMPTY))
            .build()
    }

    private fun refreshExtras() {
        session?.setCustomLayout(ImmutableList.of(favButton()))
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
                .setCustomLayout(ImmutableList.of(favButton()))
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
