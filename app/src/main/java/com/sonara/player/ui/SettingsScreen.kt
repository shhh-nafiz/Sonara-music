package com.sonara.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonara.player.MainViewModel
import com.sonara.player.data.Store

private val ACCENTS = listOf(
    0xFF1ED760, 0xFF1DB9FF, 0xFF7C5CFF, 0xFFFF4D8D, 0xFFFF5A36, 0xFFFFB400,
    0xFFE8E8E8, 0xFF00E5C3, 0xFFB6F500, 0xFFFF2D2D, 0xFFC77DFF, 0xFF4D7CFF,
).map { it.toInt() }

private val BACKGROUNDS = listOf(
    0xFF121212, 0xFF000000, 0xFF0B1220, 0xFF1A1033, 0xFF0F2318, 0xFF2A0E14,
    0xFF1F1F2E, 0xFF2B2118, 0xFF263238, 0xFFF4F4F4,
).map { it.toInt() }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: MainViewModel, onManage: () -> Unit, onBack: () -> Unit) {
    val accent by Store.accent.collectAsState()
    val bg by Store.bg.collectAsState()
    val adaptive by Store.adaptive.collectAsState()
    val heartCompact by Store.heartCompact.collectAsState()
    val fadeSec by Store.fadeSec.collectAsState()
    val speed by Store.speed.collectAsState()
    val skipSilence by Store.skipSilence.collectAsState()
    val replayGain by Store.replayGain.collectAsState()
    val dynAccent by Store.dynamicAccent.collectAsState()
    val viz by Store.vizStyle.collectAsState()
    val songs by vm.songs.collectAsState()
    val acc = Color(accent)

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Appearance & settings", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        Button(onClick = onManage, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("Manage library: sources, blacklist, ignored songs")
        }

        Header("Accent color")
        Swatches(ACCENTS, accent) { Store.setAccent(it) }
        RgbPicker(accent) { Store.setAccent(it) }

        Header("Background color")
        Swatches(BACKGROUNDS, bg) { Store.setBg(it) }
        RgbPicker(bg) { Store.setBg(it) }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Adaptive player background", fontWeight = FontWeight.Medium)
                Text("Tint the player with colors from the album cover", fontSize = 12.sp, color = TextSecondary)
            }
            Switch(
                adaptive, { Store.setAdaptive(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = acc, checkedThumbColor = Color.White),
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Heart in compact lock-screen controls", fontWeight = FontWeight.Medium)
                Text("Puts the favorite heart where Previous normally is so it shows on the lock screen and notch. Off = heart appears in the expanded controls.", fontSize = 12.sp, color = TextSecondary)
            }
            Switch(
                heartCompact, { Store.setHeartCompact(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = acc, checkedThumbColor = Color.White),
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Accent follows album cover", fontWeight = FontWeight.Medium)
                Text("The accent color changes with the song that is playing", fontSize = 12.sp, color = TextSecondary)
            }
            Switch(
                dynAccent, { Store.setDynamicAccent(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = acc, checkedThumbColor = Color.White),
            )
        }

        Header("Playback")
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row {
                Text("Fade between songs", fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text(if (fadeSec == 0) "off" else "$fadeSec s", color = TextSecondary)
            }
            Slider(
                fadeSec.toFloat(), { Store.setFadeSec(it.toInt()) }, valueRange = 0f..12f, steps = 11,
                colors = SliderDefaults.colors(thumbColor = acc, activeTrackColor = acc),
            )
            Row {
                Text("Playback speed", fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text("%.2fx".format(speed), color = TextSecondary)
            }
            Slider(
                speed, { Store.setSpeed(Math.round(it * 20f) / 20f) }, valueRange = 0.5f..2f,
                colors = SliderDefaults.colors(thumbColor = acc, activeTrackColor = acc),
            )
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Loudness normalization (ReplayGain)", fontWeight = FontWeight.Medium)
                Text("Evens out volume between songs using ReplayGain tags in your files (songs without tags are unchanged)", fontSize = 12.sp, color = TextSecondary)
            }
            Switch(replayGain, { Store.setReplayGain(it) }, colors = SwitchDefaults.colors(checkedTrackColor = acc, checkedThumbColor = Color.White))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Skip silence", fontWeight = FontWeight.Medium)
                Text("Jumps over silent gaps inside tracks", fontSize = 12.sp, color = TextSecondary)
            }
            Switch(skipSilence, { Store.setSkipSilence(it) }, colors = SwitchDefaults.colors(checkedTrackColor = acc, checkedThumbColor = Color.White))
        }

        Header("Visualizer style")
        FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VIZ_STYLES.forEachIndexed { i, n ->
                val sel = viz == i
                Text(
                    n, fontWeight = FontWeight.Medium,
                    color = if (sel) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.clip(RoundedCornerShape(50))
                        .background(if (sel) acc else MaterialTheme.colorScheme.surface)
                        .clickable { Store.setVizStyle(i) }.padding(horizontal = 16.dp, vertical = 9.dp),
                )
            }
        }

        Header("Library")
        Text("${songs.size} songs found on this phone", color = TextSecondary, modifier = Modifier.padding(horizontal = 16.dp))
        Button(onClick = { vm.load() }, modifier = Modifier.padding(16.dp)) { Text("Rescan library") }
    }
}

@Composable
private fun Header(t: String) =
    Text(t, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 10.dp))

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Swatches(colors: List<Int>, selected: Int, onPick: (Int) -> Unit) {
    FlowRow(
        Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        colors.forEach { c ->
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Color(c))
                    .border(if (c == selected) 3.dp else 1.dp, if (c == selected) Color.White else Color.Gray, CircleShape)
                    .clickable { onPick(c) },
            )
        }
    }
}

@Composable
private fun RgbPicker(argb: Int, onChange: (Int) -> Unit) {
    val c = Color(argb)
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        listOf(
            Triple("R", c.red, Color.Red), Triple("G", c.green, Color.Green), Triple("B", c.blue, Color(0xFF4D7CFF)),
        ).forEachIndexed { i, (label, v, tint) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.size(20.dp), color = TextSecondary, fontSize = 12.sp)
                Slider(
                    v, { nv ->
                        val r = if (i == 0) nv else c.red
                        val g = if (i == 1) nv else c.green
                        val b = if (i == 2) nv else c.blue
                        onChange(Color(r, g, b).toArgb())
                    },
                    colors = SliderDefaults.colors(thumbColor = tint, activeTrackColor = tint),
                )
            }
        }
    }
}
