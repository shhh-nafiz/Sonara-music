package com.sonara.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.sonara.player.audio.DspState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

val VIZ_STYLES = listOf("Bars", "Mirror", "Wave", "Circle")

/** Real-time spectrum analyzer fed by the samples tapped from the DSP chain. */
class VizAnalyzer(private val bands: Int = 48) {
    private val n = 1024
    private val re = FloatArray(n)
    private val im = FloatArray(n)
    private val window = FloatArray(n) { (0.5 - 0.5 * cos(2 * PI * it / (n - 1))).toFloat() }
    val levels = FloatArray(bands)
    private val k0 = IntArray(bands)
    private val k1 = IntArray(bands)
    private var edgesForRate = 0
    private var lastNanos = 0L

    fun maxLevel() = levels.maxOrNull() ?: 0f

    private fun buildEdges(sr: Int) {
        edgesForRate = sr
        val fMin = 40.0
        val fMax = min(16000.0, sr / 2.0 * 0.95)
        for (b in 0 until bands) {
            val f0 = fMin * Math.pow(fMax / fMin, b.toDouble() / bands)
            val f1 = fMin * Math.pow(fMax / fMin, (b + 1).toDouble() / bands)
            k0[b] = max(1, (f0 * n / sr).toInt())
            k1[b] = min(n / 2, max(k0[b] + 1, (f1 * n / sr).toInt() + 1))
        }
    }

    /** Called every display frame; the heavy FFT runs at most ~60 times a second even on 144 Hz screens. */
    fun update(playing: Boolean, nowNanos: Long) {
        if (nowNanos - lastNanos < 15_000_000L) return
        lastNanos = nowNanos
        if (!playing) {
            for (i in levels.indices) levels[i] *= 0.9f
            return
        }
        val ring = DspState.ring
        val end = DspState.ringPos
        for (i in 0 until n) {
            val idx = (end - n + i + DspState.RING * 2) and (DspState.RING - 1)
            re[i] = ring[idx] * window[i]
            im[i] = 0f
        }
        fft()
        val sr = DspState.sampleRate
        if (sr != edgesForRate) buildEdges(sr)
        for (b in 0 until bands) {
            var m = 0f
            for (k in k0[b] until k1[b]) {
                val mag = sqrt(re[k] * re[k] + im[k] * im[k])
                if (mag > m) m = mag
            }
            val db = 20f * log10(max(m / (n / 4f), 1e-7f)) + 18f * b / bands // slight high-freq tilt
            val v = ((db + 70f) / 60f).coerceIn(0f, 1f)
            levels[b] = if (v > levels[b]) levels[b] + (v - levels[b]) * 0.65f else levels[b] * 0.88f
        }
    }

    private fun fft() {
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = cos(ang).toFloat(); val wi = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var cr = 1f; var ci = 0f
                for (k in 0 until len / 2) {
                    val ur = re[i + k]; val ui = im[i + k]
                    val vr = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                    val vi = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                    re[i + k] = ur + vr; im[i + k] = ui + vi
                    re[i + k + len / 2] = ur - vr; im[i + k + len / 2] = ui - vi
                    val nr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = nr
                }
                i += len
            }
            len = len shl 1
        }
    }
}

@Composable
fun AudioVisualizer(playing: Boolean, style: Int, color: Color, modifier: Modifier = Modifier) {
    val analyzer = remember { VizAnalyzer(48) }
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(playing) {
        while (playing || analyzer.maxLevel() > 0.01f) {
            val t = androidx.compose.runtime.withFrameNanos { it }
            analyzer.update(playing, t)
            frame++
        }
        frame++
    }
    Canvas(modifier) {
        if (frame < 0) return@Canvas // reads state so every frame redraws
        val lv = analyzer.levels
        val n = lv.size
        val w = size.width; val h = size.height
        val brush = Brush.verticalGradient(listOf(color, color.copy(alpha = 0.35f)))
        when (style) {
            0 -> { // bars
                val gap = w / n * 0.3f
                val bw = (w - gap * (n - 1)) / n
                for (i in 0 until n) {
                    val bh = max(bw, lv[i] * h)
                    drawRoundRect(brush, Offset(i * (bw + gap), h - bh), Size(bw, bh), CornerRadius(bw / 2))
                }
            }
            1 -> { // mirrored from the centre line
                val gap = w / n * 0.35f
                val bw = (w - gap * (n - 1)) / n
                for (i in 0 until n) {
                    val bh = max(bw, lv[i] * h)
                    drawRoundRect(color.copy(alpha = 0.5f + 0.5f * lv[i]), Offset(i * (bw + gap), (h - bh) / 2), Size(bw, bh), CornerRadius(bw / 2))
                }
            }
            2 -> { // smooth wave
                val path = Path()
                val step = w / (n - 1)
                path.moveTo(0f, h)
                for (i in 0 until n) {
                    val x = i * step; val y = h - lv[i] * h
                    if (i == 0) path.lineTo(x, y) else {
                        val px = (i - 1) * step; val py = h - lv[i - 1] * h
                        path.cubicTo((px + x) / 2, py, (px + x) / 2, y, x, y)
                    }
                }
                path.lineTo(w, h); path.close()
                drawPath(path, Brush.verticalGradient(listOf(color.copy(alpha = 0.9f), color.copy(alpha = 0.05f))))
                val line = Path()
                for (i in 0 until n) {
                    val x = i * step; val y = h - lv[i] * h
                    if (i == 0) line.moveTo(x, y) else {
                        val px = (i - 1) * step; val py = h - lv[i - 1] * h
                        line.cubicTo((px + x) / 2, py, (px + x) / 2, y, x, y)
                    }
                }
                drawPath(line, color, style = Stroke(width = 3f, cap = StrokeCap.Round))
            }
            else -> { // circle
                val cx = w / 2; val cy = h / 2
                val r0 = min(w, h) * 0.22f
                val maxLen = min(w, h) * 0.28f
                for (i in 0 until n) {
                    val a = (2 * PI * i / n - PI / 2).toFloat()
                    val len = 4f + lv[i] * maxLen
                    drawLine(
                        color.copy(alpha = 0.45f + 0.55f * lv[i]),
                        Offset(cx + cos(a) * r0, cy + sin(a) * r0),
                        Offset(cx + cos(a) * (r0 + len), cy + sin(a) * (r0 + len)),
                        strokeWidth = 5f, cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}
