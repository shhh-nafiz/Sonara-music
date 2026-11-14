package com.sonara.player.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Software DSP in the ExoPlayer audio pipeline:
 *  - 10-band parametric/graphic EQ (RBJ peaking biquads, double precision)
 *  - low-shelf bass boost
 *  - automatic headroom + soft limiter (no clipping)
 *  - taps the final samples for the visualizer
 * With EQ off and 16-bit input the audio passes through bit-perfect.
 */
@UnstableApi
class DspAudioProcessor : BaseAudioProcessor() {

    private val nF = DspState.BANDS + 1 // + bass shelf
    private var sampleRate = 0
    private var channels = 0
    private var inEnc = C.ENCODING_INVALID
    private var coefVersion = -1
    private val coefs = DoubleArray(nF * 5)
    private val active = BooleanArray(nF)
    private var z = DoubleArray(0)
    private var preGain = 1.0
    private var processing = false

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        val enc = inputAudioFormat.encoding
        val ok = enc == C.ENCODING_PCM_16BIT || enc == C.ENCODING_PCM_24BIT ||
            enc == C.ENCODING_PCM_32BIT || enc == C.ENCODING_PCM_FLOAT
        if (!ok || inputAudioFormat.channelCount > 8) return AudioFormat.NOT_SET
        sampleRate = inputAudioFormat.sampleRate
        channels = inputAudioFormat.channelCount
        inEnc = enc
        DspState.sampleRate = sampleRate
        z = DoubleArray(channels * nF * 2)
        coefVersion = -1
        val outEnc = if (enc == C.ENCODING_PCM_16BIT) C.ENCODING_PCM_16BIT else C.ENCODING_PCM_FLOAT
        return AudioFormat(sampleRate, channels, outEnc)
    }

    override fun onFlush() {
        z.fill(0.0)
    }

    private fun refresh() {
        val v = DspState.version
        if (v == coefVersion) return
        coefVersion = v
        val gains = DspState.gains
        val on = DspState.enabled
        var maxBoost = 0f
        for (i in 0 until DspState.BANDS) {
            val g = if (on) gains[i] else 0f
            val f = DspState.BAND_FREQS[i].toDouble()
            active[i] = abs(g) > 0.05f && f < sampleRate * 0.45
            if (active[i]) peaking(i, f, g.toDouble(), 1.41)
            if (g > maxBoost) maxBoost = g
        }
        val bass = if (on) DspState.bassBoostDb else 0f
        active[nF - 1] = bass > 0.05f
        if (active[nF - 1]) lowShelf(nF - 1, 90.0, bass.toDouble())
        if (bass > maxBoost) maxBoost = bass
        // automatic headroom: pull the level down by the biggest boost so nothing clips
        val pre = if (on) DspState.preampDb - maxBoost else 0f
        val rg = if (DspState.rgOn) DspState.rgDb else 0f
        preGain = 10.0.pow((pre + rg) / 20.0)
        processing = (on && (active.any { it } || abs(DspState.preampDb) > 0.05f)) || abs(rg) > 0.05f
    }

    private fun set(i: Int, b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) {
        val o = i * 5
        coefs[o] = b0 / a0; coefs[o + 1] = b1 / a0; coefs[o + 2] = b2 / a0
        coefs[o + 3] = a1 / a0; coefs[o + 4] = a2 / a0
    }

    private fun peaking(i: Int, f: Double, gDb: Double, q: Double) {
        val a = 10.0.pow(gDb / 40.0)
        val w = 2 * PI * f / sampleRate
        val al = sin(w) / (2 * q)
        val c = cos(w)
        set(i, 1 + al * a, -2 * c, 1 - al * a, 1 + al / a, -2 * c, 1 - al / a)
    }

    private fun lowShelf(i: Int, f: Double, gDb: Double) {
        val a = 10.0.pow(gDb / 40.0)
        val w = 2 * PI * f / sampleRate
        val c = cos(w)
        val al = sin(w) / 2 * sqrt(2.0)
        val t = 2 * sqrt(a) * al
        set(
            i,
            a * ((a + 1) - (a - 1) * c + t),
            2 * a * ((a - 1) - (a + 1) * c),
            a * ((a + 1) - (a - 1) * c - t),
            (a + 1) + (a - 1) * c + t,
            -2 * ((a - 1) + (a + 1) * c),
            (a + 1) + (a - 1) * c - t,
        )
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        refresh()
        inputBuffer.order(ByteOrder.nativeOrder())
        val bytesPer = when (inEnc) {
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_24BIT -> 3
            else -> 4
        }
        val frames = remaining / (bytesPer * channels)
        val is16 = inEnc == C.ENCODING_PCM_16BIT
        val out = replaceOutputBuffer(frames * channels * (if (is16) 2 else 4)).order(ByteOrder.nativeOrder())
        var pos = inputBuffer.position()
        val ring = DspState.ring
        var rp = DspState.ringPos
        val mask = DspState.RING - 1

        for (fr in 0 until frames) {
            var mono = 0f
            for (ch in 0 until channels) {
                var raw: Short = 0
                var x: Double = when (inEnc) {
                    C.ENCODING_PCM_16BIT -> { val s = inputBuffer.getShort(pos); raw = s; pos += 2; s / 32768.0 }
                    C.ENCODING_PCM_24BIT -> {
                        val v = (inputBuffer.get(pos).toInt() and 0xFF) or
                            ((inputBuffer.get(pos + 1).toInt() and 0xFF) shl 8) or
                            (inputBuffer.get(pos + 2).toInt() shl 16)
                        pos += 3; v / 8388608.0
                    }
                    C.ENCODING_PCM_32BIT -> { val s = inputBuffer.getInt(pos); pos += 4; s / 2147483648.0 }
                    else -> { val s = inputBuffer.getFloat(pos); pos += 4; s.toDouble() }
                }
                if (processing) {
                    x *= preGain
                    val zb = ch * nF * 2
                    for (f in 0 until nF) {
                        if (!active[f]) continue
                        val o = f * 5
                        val y = coefs[o] * x + z[zb + f * 2]
                        z[zb + f * 2] = coefs[o + 1] * x - coefs[o + 3] * y + z[zb + f * 2 + 1]
                        z[zb + f * 2 + 1] = coefs[o + 2] * x - coefs[o + 4] * y
                        x = y
                    }
                    val ax = abs(x)
                    if (ax > 0.95) x = sign(x) * (0.95 + 0.05 * tanh((ax - 0.95) / 0.05))
                }
                mono += x.toFloat()
                if (is16 && !processing) {
                    out.putShort(raw) // bit-perfect passthrough
                } else if (is16) {
                    out.putShort((x * 32767.0).roundToInt().coerceIn(-32768, 32767).toShort())
                } else {
                    out.putFloat(x.toFloat())
                }
            }
            ring[rp and mask] = mono / channels
            rp++
        }
        DspState.ringPos = rp and mask
        inputBuffer.position(inputBuffer.limit())
        out.flip()
    }
}
