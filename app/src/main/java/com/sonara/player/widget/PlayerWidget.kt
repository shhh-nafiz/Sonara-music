package com.sonara.player.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.media3.common.Player
import com.sonara.player.MainActivity
import com.sonara.player.R
import com.sonara.player.data.ArtworkLoader
import com.sonara.player.data.Store
import com.sonara.player.playback.PlaybackService

/** Home-screen widget: cover, title, and a favorite (heart) button next to the transport controls. */
class PlayerWidget : AppWidgetProvider() {

    companion object {
        const val A_REFRESH = "com.sonara.player.WIDGET_REFRESH"
        const val A_FAV = "com.sonara.player.WIDGET_FAV"
        const val A_PLAY = "com.sonara.player.WIDGET_PLAYPAUSE"
        const val A_NEXT = "com.sonara.player.WIDGET_NEXT"
        const val A_PREV = "com.sonara.player.WIDGET_PREV"

        private fun ids(ctx: Context) =
            AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, PlayerWidget::class.java))

        private fun pi(ctx: Context, action: String): PendingIntent =
            PendingIntent.getBroadcast(
                ctx, action.hashCode(),
                Intent(ctx, PlayerWidget::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        suspend fun pushFromPlayer(ctx: Context, player: Player) {
            if (ids(ctx).isEmpty()) return
            val item = player.currentMediaItem
            val title = item?.mediaMetadata?.title?.toString()
            val artist = item?.mediaMetadata?.artist?.toString()
            val art = item?.mediaMetadata?.artworkUri?.let { ArtworkLoader.load(ctx, it, 256) }
            val fav = item?.mediaId?.toLongOrNull()?.let { Store.isFavorite(it) } ?: false
            push(ctx, title, artist, art, player.isPlaying, fav)
        }

        fun push(
            ctx: Context, title: String?, artist: String?,
            art: android.graphics.Bitmap?, playing: Boolean, fav: Boolean,
        ) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val v = RemoteViews(ctx.packageName, R.layout.widget_player)
            v.setTextViewText(R.id.w_title, title ?: "Nothing playing")
            v.setTextViewText(R.id.w_artist, artist ?: "Tap to open Sonara")
            if (art != null) v.setImageViewBitmap(R.id.w_cover, art)
            else v.setImageViewBitmap(R.id.w_cover, ArtworkLoader.placeholder(160))
            v.setImageViewResource(R.id.w_play, if (playing) R.drawable.ic_pause else R.drawable.ic_play)
            v.setImageViewResource(R.id.w_fav, if (fav) R.drawable.ic_heart else R.drawable.ic_heart_outline)
            v.setInt(R.id.w_fav, "setColorFilter", if (fav) 0xFF1ED760.toInt() else 0xFFFFFFFF.toInt())
            v.setOnClickPendingIntent(R.id.w_fav, pi(ctx, A_FAV))
            v.setOnClickPendingIntent(R.id.w_play, pi(ctx, A_PLAY))
            v.setOnClickPendingIntent(R.id.w_next, pi(ctx, A_NEXT))
            v.setOnClickPendingIntent(R.id.w_prev, pi(ctx, A_PREV))
            val open = PendingIntent.getActivity(
                ctx, 1, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            v.setOnClickPendingIntent(R.id.w_cover, open)
            v.setOnClickPendingIntent(R.id.w_title, open)
            mgr.updateAppWidget(ids(ctx), v)
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val p = PlaybackService.instance?.player
        if (p != null) {
            val item = p.currentMediaItem
            push(
                context, item?.mediaMetadata?.title?.toString(), item?.mediaMetadata?.artist?.toString(),
                null, p.isPlaying, item?.mediaId?.toLongOrNull()?.let { Store.isFavorite(it) } ?: false,
            )
        } else {
            push(context, null, null, null, false, false)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Store.init(context)
        val p = PlaybackService.instance?.player
        when (intent.action) {
            A_FAV -> {
                val id = p?.currentMediaItem?.mediaId?.toLongOrNull()
                if (id != null) Store.toggleFavorite(id)
            }
            A_PLAY -> if (p != null) { if (p.isPlaying) p.pause() else p.play() } else openApp(context)
            A_NEXT -> p?.seekToNext() ?: openApp(context)
            A_PREV -> p?.seekToPrevious() ?: openApp(context)
        }
    }

    private fun openApp(ctx: Context) {
        ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
