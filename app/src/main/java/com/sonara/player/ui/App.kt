package com.sonara.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.sonara.player.MainViewModel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

private data class Page(val tab: Int, val detail: Detail?)

@Composable
fun SonaraApp(vm: MainViewModel, granted: Boolean, requestPermission: () -> Unit) {
    val accent = LocalAccent.current
    val bg = MaterialTheme.colorScheme.background

    if (!granted) {
        Column(
            Modifier.fillMaxSize().background(bg).padding(32.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Sonara needs access to your music", style = MaterialTheme.typography.titleLarge)
            Text("Allow access so it can play the songs stored on your phone.", modifier = Modifier.padding(vertical = 12.dp), color = TextSecondary)
            Button(onClick = requestPermission) { Text("Allow access") }
        }
        return
    }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    val stack = remember { mutableStateListOf<Detail>() }
    var showEq by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showManage by rememberSaveable { mutableStateOf(false) }

    val ui by vm.player.ui.collectAsState()
    val byId by vm.byId.collectAsState()
    val current = ui.currentId?.let { byId[it] }

    val holder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()

    // ---- draggable player sheet: 0 = collapsed (mini player), 1 = full screen ----
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var heightPx by remember { mutableFloatStateOf(1f) }
    val sheetVisible by remember { derivedStateOf { progress.value > 0.001f } }
    val sheetOpen by remember { derivedStateOf { progress.value > 0.5f } }
    fun animateSheet(target: Float) {
        scope.launch { progress.animateTo(target, spring(dampingRatio = 0.86f, stiffness = 380f)) }
    }
    val dragState = rememberDraggableState { d ->
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            progress.snapTo((progress.value - d / heightPx).coerceIn(0f, 1f))
        }
    }
    val dragMod = Modifier.draggable(
        dragState, Orientation.Vertical,
        onDragStopped = { v ->
            animateSheet(
                when {
                    v < -900f -> 1f
                    v > 900f -> 0f
                    progress.value > 0.5f -> 1f
                    else -> 0f
                }
            )
        },
    )

    BackHandler(enabled = showEq) { showEq = false }
    BackHandler(enabled = showManage && !showEq) { showManage = false }
    BackHandler(enabled = showSettings && !showEq && !showManage) { showSettings = false }
    BackHandler(enabled = sheetOpen && !showEq) { animateSheet(0f) }
    BackHandler(enabled = stack.isNotEmpty() && !sheetOpen && !showEq && !showSettings) { stack.removeAt(stack.lastIndex) }

    CompositionLocalProvider(LocalMainVm provides vm) {
        Box(Modifier.fillMaxSize().background(bg).onSizeChanged { heightPx = it.height.toFloat().coerceAtLeast(1f) }) {
            Scaffold(
                containerColor = bg,
                bottomBar = {
                    Column {
                        if (current != null) MiniPlayer(
                            vm, current, onClick = { animateSheet(1f) },
                            modifier = dragMod.graphicsLayer { alpha = (1f - progress.value * 2f).coerceIn(0f, 1f) },
                        )
                        NavigationBar(containerColor = bg) {
                            listOf(
                                Triple("Home", Icons.Default.Home, 0),
                                Triple("Search", Icons.Default.Search, 1),
                                Triple("Library", Icons.Default.LibraryMusic, 2),
                            ).forEach { (label, icon, i) ->
                                NavigationBarItem(
                                    selected = tab == i && stack.isEmpty(),
                                    onClick = { tab = i; stack.clear() },
                                    icon = { Icon(icon, label) },
                                    label = { Text(label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = accent, selectedTextColor = accent,
                                        indicatorColor = accent.copy(alpha = 0.15f),
                                    ),
                                )
                            }
                        }
                    }
                },
            ) { pad ->
                Box(Modifier.padding(pad).fillMaxSize()) {
                    AnimatedContent(
                        targetState = Page(tab, stack.lastOrNull()),
                        transitionSpec = {
                            val pushing = targetState.detail != null && initialState.detail == null
                            val popping = targetState.detail == null && initialState.detail != null
                            when {
                                pushing -> (slideInHorizontally(tween(300)) { it / 5 } + fadeIn(tween(300))) togetherWith fadeOut(tween(160))
                                popping -> fadeIn(tween(240)) togetherWith (slideOutHorizontally(tween(260)) { it / 5 } + fadeOut(tween(200)))
                                else -> fadeIn(tween(240)) togetherWith fadeOut(tween(120))
                            }
                        },
                        label = "page",
                    ) { page ->
                        val detail = page.detail
                        if (detail != null) {
                            DetailScreen(vm, detail) { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
                        } else holder.SaveableStateProvider(page.tab) {
                            when (page.tab) {
                                0 -> HomeScreen(vm, { stack.add(it) }) { showSettings = true }
                                1 -> SearchScreen(vm)
                                else -> LibraryScreen(vm, { stack.add(it) }) { showSettings = true }
                            }
                        }
                    }
                }
            }

            // full player slides with the finger, then springs open/closed
            if (sheetVisible && current != null) {
                Box(Modifier.fillMaxSize().graphicsLayer { translationY = (1f - progress.value) * size.height }) {
                    NowPlayingScreen(
                        vm, current, onClose = { animateSheet(0f) },
                        onOpenEq = { showEq = true }, dragModifier = dragMod,
                    )
                }
            }
            AnimatedVisibility(showSettings, enter = slideInVertically(tween(320)) { it }, exit = slideOutVertically(tween(260)) { it }) {
                SettingsScreen(vm, onManage = { showManage = true }) { showSettings = false }
            }
            AnimatedVisibility(showManage, enter = slideInVertically(tween(320)) { it }, exit = slideOutVertically(tween(260)) { it }) {
                ManageScreen(vm) { showManage = false }
            }
            AnimatedVisibility(showEq, enter = slideInVertically(tween(320)) { it }, exit = slideOutVertically(tween(260)) { it }) {
                EqualizerScreen { showEq = false }
            }
        }
    }
}
