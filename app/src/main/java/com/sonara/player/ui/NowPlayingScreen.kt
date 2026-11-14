package com.sonara.player.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import com.sonara.player.MainViewModel
import com.sonara.player.data.ArtworkLoader
import com.sonara.player.data.Song
import com.sonara.player.data.Store
import com.sonara.player.playback.SleepTimer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    vm: MainViewModel,
    song: Song,
    onClose: () -> Unit,
    onOpenEq: () -> Unit,
    dragModifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val ui by vm.player.ui.collectAsState()
    val favs by Store.favorites.collectAsState()
    val adaptive by Store.adaptive.collectAsState()
    val accent = LocalAccent.current
    val baseBg = LocalBg.current
    val vizStyle by Store.vizStyle.collectAsState()
    val sleepEnd by SleepTimer.endAt.collectAsState()
    val songs by vm.songs.collectAsState()

    val tint by produceState(baseBg, song.id, adaptive, baseBg) {
        value = baseBg
        if (adaptive) {
            val bmp = ArtworkLoader.load(ctx, song.uri, 128)
            if (bmp != null) {
                val p = Palette.from(bmp).generate()
                val c = p.getDarkVibrantColor(p.getDarkMutedColor(p.getDominantColor(baseBg.toArgb())))
                value = Color(c)
            }
        }
    }
    val tintAnim by animateColorAsState(tint, label = "tint")
    val isFav = song.id in favs

    var showQueue by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(tintAnim, baseBg), startY = 0f, endY = 2200f))
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        ) {
            // top bar
            Row(dragModifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Default.KeyboardArrowDown, "Close", Modifier.size(32.dp)) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PLAYING FROM YOUR LIBRARY", fontSize = 10.sp, letterSpacing = 1.sp, color = TextSecondary)
                    Text(song.album, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { showSleep = true }) {
                    Icon(
                        Icons.Default.Bedtime, "Sleep timer",
                        tint = if (sleepEnd > 0) accent else MaterialTheme.colorScheme.onBackground,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            // cover (swipe to skip)
            var dx by remember { mutableStateOf(0f) }
            Cover(
                song,
                Modifier.fillMaxWidth().aspectRatio(1f)
                    .shadow(24.dp, RoundedCornerShape(8.dp))
                    .pointerInput(song.id) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (dx < -120) vm.player.next() else if (dx > 120) vm.player.previous()
                                dx = 0f
                            },
                        ) { _, d -> dx += d }
                    },
                px = 1024, corner = 8.dp,
            )

            Spacer(Modifier.height(24.dp))
            // title + favorite heart (like Spotify)
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.animation.AnimatedContent(
                    targetState = song,
                    modifier = Modifier.weight(1f),
                    transitionSpec = {
                        (androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(280)) { it / 3 } +
                            androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(280))) togetherWith
                            (androidx.compose.animation.slideOutVertically(androidx.compose.animation.core.tween(180)) { -it / 3 } +
                                androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(150)))
                    },
                    label = "title",
                ) { s ->
                    Column {
                        Text(s.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(s.artist, fontSize = 16.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                IconButton(onClick = {
                    Store.toggleFavorite(song.id)
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                }) {
                    HeartIcon(isFav, accent, 30.dp)
                }
            }

            SeekBar(vm, song.durationMs)

            // transport
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.player.toggleShuffle(songs) }) {
                    Icon(Icons.Default.Shuffle, "Shuffle", tint = if (ui.shuffle) accent else MaterialTheme.colorScheme.onBackground)
                }
                IconButton(onClick = { vm.player.previous() }, Modifier.size(56.dp)) {
                    Icon(Icons.Default.SkipPrevious, "Previous", Modifier.size(40.dp))
                }
                Box(
                    Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onBackground)
                        .clickable { vm.player.togglePlay() },
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.animation.Crossfade(ui.isPlaying, animationSpec = androidx.compose.animation.core.tween(150), label = "pp") { p ->
                        Icon(
                            if (p) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause",
                            tint = MaterialTheme.colorScheme.background, modifier = Modifier.size(44.dp),
                        )
                    }
                }
                IconButton(onClick = { vm.player.next() }, Modifier.size(56.dp)) {
                    Icon(Icons.Default.SkipNext, "Next", Modifier.size(40.dp))
                }
                IconButton(onClick = { vm.player.cycleRepeat() }) {
                    Icon(
                        if (ui.repeat == 2) Icons.Default.RepeatOne else Icons.Default.Repeat, "Repeat",
                        tint = if (ui.repeat != 0) accent else MaterialTheme.colorScheme.onBackground,
                    )
                }
            }

            // live visualizer — tap to change style
            AudioVisualizer(
                playing = ui.isPlaying, style = vizStyle, color = accent,
                modifier = Modifier.fillMaxWidth().height(110.dp)
                    .clickable { Store.setVizStyle((vizStyle + 1) % VIZ_STYLES.size) },
            )

            // bottom row: quality • EQ • queue
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onOpenEq) { Icon(Icons.Default.Equalizer, "Equalizer") }
                Spacer(Modifier.weight(1f))
                Text(
                    song.qualityLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    color = if (song.isLossless) accent else TextSecondary,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.1f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showQueue = true }) { Icon(Icons.AutoMirrored.Filled.QueueMusic, "Queue") }
            }
        }
    }

    if (showQueue) {
        ModalBottomSheet(onDismissRequest = { showQueue = false }, containerColor = MaterialTheme.colorScheme.surface) {
            QueueList(vm)
        }
    }
    if (showSleep) {
        AlertDialog(
            onDismissRequest = { showSleep = false },
            title = { Text("Sleep timer") },
            text = {
                Column {
                    listOf(5, 15, 30, 45, 60, 90).forEach { m ->
                        Text(
                            "$m minutes",
                            Modifier.fillMaxWidth().clickable { SleepTimer.start(m); showSleep = false }.padding(vertical = 12.dp),
                        )
                    }
                    if (sleepEnd > 0) Text(
                        "Turn off timer", color = accent, fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth().clickable { SleepTimer.cancel(); showSleep = false }.padding(vertical = 12.dp),
                    )
                }
            },
            confirmButton = {},
            dismissButton = { TextButton({ showSleep = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun QueueList(vm: MainViewModel) {
    val queue by vm.player.queue.collectAsState()
    val idx by vm.player.queueIndex.collectAsState()
    val byId by vm.byId.collectAsState()
    val accent = LocalAccent.current
    val rowPx = with(androidx.compose.ui.platform.LocalDensity.current) { 64.dp.toPx() }
    // stable keys (same song twice gets #0, #1) so rows animate instead of re-creating when reordered
    val keys = remember(queue) {
        val seen = HashMap<Long, Int>()
        queue.map { id -> val n = seen[id] ?: 0; seen[id] = n + 1; "$id#$n" }
    }
    val state = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = (idx - 2).coerceAtLeast(0))
    var dragKey by remember { mutableStateOf<String?>(null) }
    var offset by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var busy by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(queue) { busy = false }

    Text("Queue", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    Text("Drag the handle to reorder", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(horizontal = 16.dp))
    LazyColumn(state = state, modifier = Modifier.fillMaxWidth().height(480.dp)) {
        itemsIndexed(queue, key = { i, _ -> keys[i] }) { i, id ->
            val s = byId[id] ?: return@itemsIndexed
            val key = keys[i]
            val dragging = dragKey == key
            val curIndex by androidx.compose.runtime.rememberUpdatedState(i)
            Row(
                Modifier.fillMaxWidth().height(64.dp)
                    .then(
                        if (dragging) Modifier.zIndex(1f).graphicsLayer { translationY = offset }
                            .background(MaterialTheme.colorScheme.surface)
                        else Modifier.animateItem()
                    )
                    .clickable { vm.player.playQueueIndex(i) }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cover(s, Modifier.size(48.dp), px = 128)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp,
                        color = if (i == idx) accent else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (i == idx) FontWeight.Bold else FontWeight.Normal,
                    )
                    Text(s.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = TextSecondary)
                }
                if (i != idx) IconButton(onClick = { vm.player.removeFromQueue(i) }) {
                    Icon(Icons.Default.Close, "Remove", tint = TextSecondary)
                }
                Icon(
                    Icons.Default.DragHandle, "Reorder", tint = TextSecondary,
                    modifier = Modifier.size(40.dp).padding(8.dp).pointerInput(key) {
                        detectDragGestures(
                            onDragStart = { dragKey = key; offset = 0f },
                            onDragEnd = { dragKey = null; offset = 0f },
                            onDragCancel = { dragKey = null; offset = 0f },
                        ) { change, drag ->
                            change.consume()
                            offset += drag.y
                            if (busy) return@detectDragGestures
                            val cur = curIndex
                            if (offset > rowPx / 2 && cur < queue.size - 1) {
                                busy = true; vm.player.moveItem(cur, cur + 1); offset -= rowPx
                            } else if (offset < -rowPx / 2 && cur > 0) {
                                busy = true; vm.player.moveItem(cur, cur - 1); offset += rowPx
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
fun MiniPlayer(vm: MainViewModel, song: Song, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ui by vm.player.ui.collectAsState()
    val favs by Store.favorites.collectAsState()
    val accent = LocalAccent.current
    val isFav = song.id in favs
    Column(
        modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(song, Modifier.size(44.dp), px = 128)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(song.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, fontSize = 12.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { Store.toggleFavorite(song.id) }) { HeartIcon(isFav, accent, 24.dp) }
            IconButton(onClick = { vm.player.togglePlay() }) {
                Icon(if (ui.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause", Modifier.size(30.dp))
            }
        }
        MiniProgress(vm, song.durationMs, accent)
    }
}

/** Seek bar that moves every frame; only this composable recomposes while the song plays. */
@Composable
private fun SeekBar(vm: MainViewModel, fallbackDurationMs: Long) {
    val live by rememberLivePosition(vm.player)
    val ui by vm.player.ui.collectAsState()
    var drag by remember { mutableStateOf<Float?>(null) }
    val dur = (ui.durationMs.takeIf { it > 0 } ?: fallbackDurationMs).toFloat().coerceAtLeast(1f)
    val pos = (drag ?: live.toFloat()).coerceIn(0f, dur)
    Slider(
        value = pos,
        onValueChange = { drag = it },
        onValueChangeFinished = { drag?.let { vm.player.seekTo(it.toLong()) }; drag = null },
        valueRange = 0f..dur,
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.onBackground,
            activeTrackColor = MaterialTheme.colorScheme.onBackground,
            inactiveTrackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.25f),
        ),
    )
    Row(Modifier.fillMaxWidth()) {
        Text(formatTime(pos.toLong()), fontSize = 12.sp, color = TextSecondary)
        Spacer(Modifier.weight(1f))
        Text(formatTime(dur.toLong()), fontSize = 12.sp, color = TextSecondary)
    }
}

@Composable
private fun MiniProgress(vm: MainViewModel, durationMs: Long, accent: Color) {
    val live by rememberLivePosition(vm.player)
    val ui by vm.player.ui.collectAsState()
    val dur = (ui.durationMs.takeIf { it > 0 } ?: durationMs).coerceAtLeast(1L)
    Box(
        Modifier.fillMaxWidth().height(2.dp).drawBehind {
            drawRect(Color.White.copy(alpha = 0.15f))
            drawRect(accent, size = androidx.compose.ui.geometry.Size(size.width * (live.toFloat() / dur).coerceIn(0f, 1f), size.height))
        },
    )
}
