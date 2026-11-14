package com.sonara.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.sonara.player.audio.DspState
import com.sonara.player.data.Store
import com.sonara.player.ui.SonaraApp
import com.sonara.player.ui.SonaraTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.init(this)
        com.sonara.player.data.ArtworkLoader.init(this)
        DspState.load()
        requestHighRefreshRate()
        setContent {
            val accent by Store.accent.collectAsState()
            val bg by Store.bg.collectAsState()
            val dyn by Store.dynamicAccent.collectAsState()
            val ui by vm.player.ui.collectAsState()
            val byId by vm.byId.collectAsState()
            val song = ui.currentId?.let { byId[it] }
            // accent that follows the album cover (optional)
            val coverAccent by produceState<Int?>(null, song?.id, dyn, bg) {
                value = if (dyn && song != null) {
                    com.sonara.player.data.ArtworkLoader.load(this@MainActivity, song.uri, 128)?.let { bmp ->
                        val p = androidx.palette.graphics.Palette.from(bmp).generate()
                        val c = p.getVibrantColor(p.getLightVibrantColor(p.getDominantColor(0)))
                        if (c == 0) null else readableAccent(c, bg)
                    }
                } else null
            }
            val shownAccent by animateColorAsState(Color(coverAccent ?: accent), tween(600), label = "accent")
            SonaraTheme(shownAccent, Color(bg)) {
                // Surface sets the default text color; without it text falls back to black on dark backgrounds
                androidx.compose.material3.Surface(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background,
                    contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                ) {
                    PermissionGate(vm)
                }
            }
        }
    }
}

/** Nudges a cover color until it is readable on the chosen background. */
private fun readableAccent(argb: Int, bg: Int): Int {
    var c = Color(argb)
    val dark = Color(bg).luminance() < 0.5f
    repeat(6) {
        if (dark && c.luminance() < 0.22f) c = lerp(c, Color.White, 0.18f)
        else if (!dark && c.luminance() > 0.55f) c = lerp(c, Color.Black, 0.18f)
    }
    return c.toArgb()
}

private fun MainActivity.requestHighRefreshRate() {
    // Without this many phones keep apps at 60 Hz even on a 120/144 Hz panel.
    runCatching {
        val d = if (Build.VERSION.SDK_INT >= 30) display else @Suppress("DEPRECATION") windowManager.defaultDisplay
        d ?: return
        val cur = d.mode
        val best = d.supportedModes
            .filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return
        window.attributes = window.attributes.apply { preferredDisplayModeId = best.modeId }
    }
}

private fun audioPermission() =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

@Composable
private fun PermissionGate(vm: MainViewModel) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, audioPermission()) == PackageManager.PERMISSION_GRANTED)
    }
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }
    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(audioPermission())
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notif.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    LaunchedEffect(granted) { if (granted) vm.load() }
    SonaraApp(vm, granted) { launcher.launch(audioPermission()) }
}
