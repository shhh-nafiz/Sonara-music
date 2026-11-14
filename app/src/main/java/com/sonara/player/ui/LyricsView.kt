package com.sonara.player.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonara.player.MainViewModel
import com.sonara.player.data.LyricLine
import com.sonara.player.data.LyricsRepo
import com.sonara.player.data.LyricsResult
import com.sonara.player.data.Song
import com.sonara.player.data.lyricQuery

/** Spotify-style lyrics: the current line is big and bright, lines scroll with the music, tap a line to jump to it. */
@Composable
fun LyricsPanel(vm: MainViewModel, song: Song, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var retry by remember { mutableIntStateOf(0) }
    val result by produceState<LyricsResult>(LyricsResult.Loading, song.id, retry) {
        value = LyricsResult.Loading
        value = LyricsRepo.load(ctx, song.lyricQuery(), force = retry > 0)
    }

    Box(modifier.clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.30f))) {
        when (val r = result) {
            LyricsResult.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = LocalAccent.current)
            LyricsResult.Offline -> Message("Lyrics will download when you're online", "Try again") { retry++ }
            LyricsResult.NotFound -> Message("No lyrics found for this song", "Search again") { retry++ }
            is LyricsResult.Found -> {
                if (r.synced.isNotEmpty()) SyncedLyrics(vm, r.synced)
                else PlainLyrics(r.plain ?: "")
                Text(
                    "Lyrics from LRCLIB", fontSize = 10.sp, color = TextSecondary,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun Message(text: String, action: String, onAction: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, color = TextSecondary, textAlign = TextAlign.Center)
        TextButton(onClick = onAction) { Text(action, color = LocalAccent.current) }
    }
}

@Composable
private fun PlainLyrics(text: String) {
    Text(
        text, fontSize = 18.sp, fontWeight = FontWeight.Medium, lineHeight = 28.sp,
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
    )
}

private fun activeIndex(lines: List<LyricLine>, pos: Long): Int {
    var lo = 0
    var hi = lines.lastIndex
    var ans = -1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (lines[mid].timeMs <= pos) { ans = mid; lo = mid + 1 } else hi = mid - 1
    }
    return ans
}

@Composable
private fun SyncedLyrics(vm: MainViewModel, lines: List<LyricLine>) {
    // the position State is only read inside derivedStateOf, so just a line change recomposes this
    val live = rememberLivePosition(vm.player)
    val active by remember(lines) { derivedStateOf { activeIndex(lines, live.value + 250) } }
    val listState = rememberLazyListState()

    LaunchedEffect(active, lines) {
        if (active >= 0) listState.animateScrollToItem(active)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 90.dp, bottom = 220.dp),
    ) {
        itemsIndexed(lines) { i, line ->
            val isActive = i == active
            val scale by animateFloatAsState(if (isActive) 1f else 0.84f, tween(300), label = "lyricScale")
            val alpha by animateFloatAsState(
                if (isActive) 1f else if (i < active) 0.40f else 0.62f, tween(300), label = "lyricAlpha",
            )
            Text(
                line.text.ifBlank { "♪" },
                fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth()
                    .clickable { vm.player.seekTo(line.timeMs) }
                    .padding(vertical = 8.dp)
                    .graphicsLayer {
                        scaleX = scale; scaleY = scale
                        this.alpha = alpha
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    },
            )
        }
    }
}
