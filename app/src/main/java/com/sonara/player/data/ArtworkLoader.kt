package com.sonara.player.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loads cover art for a song: embedded tag picture first, then MediaStore thumbnail. */
object ArtworkLoader {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val misses = HashSet<String>()

    fun loadBlocking(ctx: Context, uri: Uri, size: Int): Bitmap? {
        val key = "$uri@$size"
        cache.get(key)?.let { return it }
        if (synchronized(misses) { key in misses }) return null

        var bmp: Bitmap? = null
        // 1) embedded cover (full quality, any format)
        runCatching {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(ctx, uri)
                val bytes = r.embeddedPicture
                if (bytes != null) bmp = decodeSampled(bytes, size)
            } finally {
                r.release()
            }
        }
        // 2) MediaStore thumbnail (folder.jpg / album art)
        if (bmp == null && Build.VERSION.SDK_INT >= 29) {
            runCatching { bmp = ctx.contentResolver.loadThumbnail(uri, Size(size, size), null) }
        }
        val out = bmp
        if (out != null) cache.put(key, out) else synchronized(misses) { misses += key }
        return out
    }

    suspend fun load(ctx: Context, uri: Uri, size: Int): Bitmap? =
        withContext(Dispatchers.IO) { loadBlocking(ctx, uri, size) }

    private fun decodeSampled(bytes: ByteArray, target: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        var sample = 1
        while (o.outWidth / (sample * 2) >= target && o.outHeight / (sample * 2) >= target) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    fun placeholder(size: Int = 256): Bitmap {
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(Color.parseColor("#282828"))
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#7F7F7F") }
        val s = size / 100f
        c.drawCircle(38 * s, 68 * s, 10 * s, p)
        c.drawRect(44 * s, 28 * s, 48 * s, 68 * s, p)
        c.drawRect(44 * s, 28 * s, 68 * s, 36 * s, p)
        return b
    }
}
