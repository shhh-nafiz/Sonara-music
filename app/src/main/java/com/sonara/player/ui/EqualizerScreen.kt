package com.sonara.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonara.player.audio.DspState

private val BAND_LABELS = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EqualizerScreen(onBack: () -> Unit) {
    val accent = LocalAccent.current
    DspState.tick.collectAsState().value // re-read parameters when they change
    val preset by DspState.presetName.collectAsState()
    val on = DspState.enabled
    val gains = DspState.gains

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Equalizer", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { DspState.reset() }) { Text("Reset", color = accent) }
            Switch(
                checked = on, onCheckedChange = { DspState.setOn(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = accent, checkedThumbColor = Color.White),
            )
            Spacer(Modifier.width(12.dp))
        }

        Text(
            "Presets", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextSecondary,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        )
        FlowRow(
            Modifier.padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DspState.PRESETS.forEach { p ->
                val sel = on && preset == p.name
                Text(
                    p.name, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    color = if (sel) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.clip(RoundedCornerShape(50))
                        .background(if (sel) accent else MaterialTheme.colorScheme.surface)
                        .clickable { DspState.applyPreset(p) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        if (preset == "Custom") {
            Text("Custom curve", fontSize = 12.sp, color = accent, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
        }

        // 10-band sliders
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 20.dp).height(250.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Column(Modifier.height(250.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text("+12", fontSize = 10.sp, color = TextSecondary)
                Text("0", fontSize = 10.sp, color = TextSecondary)
                Text("-12", fontSize = 10.sp, color = TextSecondary)
            }
            for (i in 0 until DspState.BANDS) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    VerticalGain(gains[i], on, accent, Modifier.weight(1f).width(26.dp)) { DspState.setBand(i, it) }
                    Text(BAND_LABELS[i], fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }

        LabeledSlider("Preamp", DspState.preampDb, -12f..12f, "dB", accent) { DspState.setPreamp(it) }
        LabeledSlider("Bass boost", DspState.bassBoostDb, 0f..12f, "dB", accent) { DspState.setBass(it) }

        Text(
            "Processing is done in 32-bit floating point with automatic headroom and a soft limiter, " +
                "so boosts never clip. With the equalizer off, 16-bit audio passes through bit-perfect.",
            fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun LabeledSlider(
    label: String, value: Float, range: ClosedFloatingPointRange<Float>, unit: String,
    accent: Color, onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row {
            Text(label, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text("%+.1f %s".format(value, unit), color = TextSecondary, fontSize = 13.sp)
        }
        Slider(
            value, onChange, valueRange = range,
            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
        )
    }
}

/** Vertical -12..+12 dB slider that responds to touch and drag anywhere on the track. */
@Composable
private fun VerticalGain(
    value: Float, enabled: Boolean, accent: Color, modifier: Modifier, onChange: (Float) -> Unit,
) {
    val track = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.18f)
    val col = if (enabled) accent else accent.copy(alpha = 0.4f)
    Canvas(
        modifier.pointerInput(Unit) {
            awaitEachGesture {
                val h = size.height.toFloat()
                fun upd(y: Float) {
                    val f = (1f - (y / h).coerceIn(0f, 1f))
                    val db = (f * 2f - 1f) * DspState.MAX_DB
                    onChange((Math.round(db * 2f) / 2f)) // 0.5 dB steps
                }
                val down = awaitFirstDown()
                upd(down.position.y)
                do {
                    val ev = awaitPointerEvent()
                    ev.changes.forEach { if (it.pressed) { upd(it.position.y); it.consume() } }
                } while (ev.changes.any { it.pressed })
            }
        },
    ) {
        val cx = size.width / 2
        val tw = 4.dp.toPx()
        drawRoundRect(track, Offset(cx - tw / 2, 0f), Size(tw, size.height), CornerRadius(tw / 2))
        val mid = size.height / 2
        val y = size.height * (1f - (value / DspState.MAX_DB + 1f) / 2f)
        drawRoundRect(col, Offset(cx - tw / 2, minOf(y, mid)), Size(tw, kotlin.math.abs(y - mid)), CornerRadius(tw / 2))
        drawLine(track, Offset(0f, mid), Offset(size.width, mid), 2f)
        drawCircle(col, 9.dp.toPx(), Offset(cx, y))
    }
}
