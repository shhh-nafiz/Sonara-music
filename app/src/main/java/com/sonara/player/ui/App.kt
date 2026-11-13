package com.sonara.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sonara.player.MainViewModel

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
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    var showEq by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    val ui by vm.player.ui.collectAsState()
    val byId by vm.byId.collectAsState()
    val current = ui.currentId?.let { byId[it] }

    BackHandler(enabled = showEq) { showEq = false }
    BackHandler(enabled = showSettings && !showEq) { showSettings = false }
    BackHandler(enabled = showPlayer && !showEq) { showPlayer = false }
    BackHandler(enabled = stack.isNotEmpty() && !showPlayer && !showEq && !showSettings) { stack.removeAt(stack.lastIndex) }

    Box(Modifier.fillMaxSize().background(bg)) {
        Scaffold(
            containerColor = bg,
            bottomBar = {
                Column {
                    if (current != null) MiniPlayer(vm, current) { showPlayer = true }
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
                val detail = stack.lastOrNull()
                if (detail != null) {
                    DetailScreen(vm, detail) { stack.removeAt(stack.lastIndex) }
                } else when (tab) {
                    0 -> HomeScreen(vm, { stack.add(it) }) { showSettings = true }
                    1 -> SearchScreen(vm)
                    else -> LibraryScreen(vm, { stack.add(it) }) { showSettings = true }
                }
            }
        }

        AnimatedVisibility(
            visible = showPlayer && current != null,
            enter = slideInVertically { it }, exit = slideOutVertically { it },
        ) {
            if (current != null) NowPlayingScreen(vm, current, onClose = { showPlayer = false }, onOpenEq = { showEq = true })
        }
        AnimatedVisibility(showSettings, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
            SettingsScreen(vm) { showSettings = false }
        }
        AnimatedVisibility(showEq, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
            EqualizerScreen { showEq = false }
        }
    }
}
