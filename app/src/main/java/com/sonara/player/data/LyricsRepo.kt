package com.sonara.player.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

data class LyricLine(val timeMs: Long, val text: String)

fun Song.lyricQuery() = LyricQuery(id, title, artist, album, (durationMs / 1000).toInt())

data class LyricQuery(val id: Long, val title: String, val artist: String, val album: String, val durationSec: Int)

sealed interface LyricsResult {
    object Loading : LyricsResult
    /** [synced] is empty when only plain (un-timed) lyrics exist. */
    data class Found(val synced: List<LyricLine>, val plain: String?) : LyricsResult
    object NotFound : LyricsResult
    object Offline : LyricsResult
}

/**
 * Lyrics from LRCLIB (https://lrclib.net) – a free, community-maintained database with time-synced
 * lyrics. A song only matches when title + artist (+ album) agree and the duration is within a few
 * seconds, so you don't get lyrics of a different version. Results are cached in the app's storage,
 * so each song is downloaded once and then works offline.
 */
object LyricsRepo {
    private const val BASE = "https://lrclib.net/api"
    private const val UA = "Sonara/1.0 (local music player; https://lrclib.net)"
    private const val NOT_FOUND_RETRY_MS = 7L * 24 * 60 * 60 * 1000

    private var dir: File? = null

    fun init(ctx: Context) {
        if (dir == null) dir = File(ctx.filesDir, "lyrics").apply { mkdirs() }
    }

    private fun file(id: Long) = File(dir ?: File("/nonexistent"), "$id.json")

    // ------------------------------------------------------------ cache

    fun cached(id: Long): LyricsResult? {
        val f = file(id)
        if (!f.exists()) return null
        return runCatching {
            val o = JSONObject(f.readText())
            if (o.optString("status") == "found") {
                val synced = parseLrc(o.optString("synced"))
                val plain = o.optString("plain").takeIf { it.isNotBlank() }
                if (synced.isEmpty() && plain == null) null else LyricsResult.Found(synced, plain)
            } else {
                if (System.currentTimeMillis() - o.optLong("t") > NOT_FOUND_RETRY_MS) null else LyricsResult.NotFound
            }
        }.getOrNull()
    }

    fun hasCached(id: Long) = cached(id) != null

    fun clear(id: Long) { file(id).delete() }

    private fun save(id: Long, status: String, synced: String? = null, plain: String? = null) {
        runCatching {
            file(id).writeText(
                JSONObject().put("status", status).put("synced", synced ?: "").put("plain", plain ?: "")
                    .put("t", System.currentTimeMillis()).toString()
            )
        }
    }

    // ------------------------------------------------------------ network

    /** Is a usable connection available (and Wi-Fi if the user asked for Wi-Fi only)? */
    fun networkOk(ctx: Context): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        if (Store.lyricsWifiOnly.value) {
            return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        }
        return true
    }

    /** Cached lyrics if present, otherwise (when allowed and online) download, cache and return them. */
    suspend fun load(ctx: Context, q: LyricQuery, allowNetwork: Boolean = true, force: Boolean = false): LyricsResult =
        withContext(Dispatchers.IO) {
            init(ctx)
            if (!force) cached(q.id)?.let { return@withContext it }
            if (!allowNetwork || !networkOk(ctx)) return@withContext LyricsResult.Offline
            try {
                val hit = fetch(q)
                if (hit == null) {
                    save(q.id, "none")
                    LyricsResult.NotFound
                } else {
                    val synced = hit.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
                    val plain = hit.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }
                    if (synced == null && plain == null) {
                        save(q.id, "none"); LyricsResult.NotFound
                    } else {
                        save(q.id, "found", synced, plain)
                        LyricsResult.Found(parseLrc(synced ?: ""), plain)
                    }
                }
            } catch (e: IOException) {
                LyricsResult.Offline // network problem: don't cache anything, try again later
            } catch (e: Exception) {
                LyricsResult.Offline
            }
        }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun fetch(q: LyricQuery): JSONObject? {
        val artist = q.artist.takeIf { it != "Unknown artist" } ?: ""
        // 1) exact match on title + artist + album + duration
        get("$BASE/get?track_name=${enc(q.title)}&artist_name=${enc(artist)}&album_name=${enc(q.album)}&duration=${q.durationSec}")
            ?.let { if (it is JSONObject && !it.optBoolean("instrumental")) return it }

        // 2) search, then accept only a result whose title matches and whose length is within 3 s
        val arr = get("$BASE/search?track_name=${enc(q.title)}&artist_name=${enc(artist)}") as? JSONArray
            ?: get("$BASE/search?q=${enc("${q.title} $artist")}") as? JSONArray
            ?: return null
        val wantTitle = norm(q.title)
        val wantArtist = norm(artist)
        var best: JSONObject? = null
        var bestScore = -1
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optBoolean("instrumental")) continue
            val dur = o.optDouble("duration", -100.0)
            if (abs(dur - q.durationSec) > 3.0) continue
            if (norm(o.optString("trackName")) != wantTitle) continue
            if (wantArtist.isNotEmpty() && !norm(o.optString("artistName")).contains(wantArtist.take(6))) continue
            var score = 0
            if (o.optString("syncedLyrics").let { it.isNotBlank() && it != "null" }) score += 10
            if (norm(o.optString("albumName")) == norm(q.album)) score += 3
            score -= (abs(dur - q.durationSec)).toInt()
            if (score > bestScore) { bestScore = score; best = o }
        }
        return best
    }

    /** Lower-case, drops "(feat. …)", "[Remastered]" and punctuation so tags from different sources compare equal. */
    private fun norm(s: String): String =
        s.lowercase().replace(Regex("""\(.*?\)|\[.*?]"""), "").replace(Regex("""\s-\s.*$"""), "")
            .replace(Regex("""[^\p{L}\p{N}]"""), "")

    /** HTTPS GET; returns JSONObject / JSONArray, null on 404, throws IOException on network trouble. */
    private fun get(url: String): Any? {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8000
            c.readTimeout = 10000
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("Accept", "application/json")
            return when (val code = c.responseCode) {
                200 -> {
                    val body = c.inputStream.bufferedReader().use { it.readText() }.trim()
                    if (body.startsWith("[")) JSONArray(body) else JSONObject(body)
                }
                404 -> null
                else -> throw IOException("HTTP $code")
            }
        } finally {
            c.disconnect()
        }
    }

    // ------------------------------------------------------------ LRC parsing

    private val tagRe = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val offsetRe = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)

    fun parseLrc(lrc: String): List<LyricLine> {
        if (lrc.isBlank()) return emptyList()
        val offset = offsetRe.find(lrc)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val out = ArrayList<LyricLine>()
        for (line in lrc.lineSequence()) {
            val tags = tagRe.findAll(line).toList()
            if (tags.isEmpty()) continue
            val text = line.replace(tagRe, "").trim()
            for (m in tags) {
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val frac = m.groupValues[3]
                val ms = when (frac.length) { 0 -> 0L; 1 -> frac.toLong() * 100; 2 -> frac.toLong() * 10; else -> frac.take(3).toLong() }
                out += LyricLine((min * 60 + sec) * 1000 + ms - offset, text)
            }
        }
        return out.sortedBy { it.timeMs }
    }
}
