package com.sonara.player.audio

import com.sonara.player.data.Store
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class EqPreset(val name: String, val gains: List<Float>)

/**
 * Shared, process-wide state for the DSP chain (equalizer, bass boost) and the
 * sample tap used by the visualizer. The audio thread reads it, the UI writes it.
 */
object DspState {
    val BAND_FREQS = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
    const val BANDS = 10
    const val MAX_DB = 12f

    val PRESETS: List<EqPreset> = listOf(
        EqPreset("Flat", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
        EqPreset("Bass Booster", listOf(6f, 5f, 4f, 2.5f, 1f, 0f, 0f, 0f, 0f, 0f)),
        EqPreset("Bass Reducer", listOf(-6f, -5f, -4f, -2f, -1f, 0f, 0f, 0f, 0f, 0f)),
        EqPreset("Treble Booster", listOf(0f, 0f, 0f, 0f, 0f, 1f, 2.5f, 4f, 5f, 6f)),
        EqPreset("Vocal Boost", listOf(-2f, -2f, -1f, 1f, 3f, 4f, 4f, 3f, 1f, 0f)),
        EqPreset("Rock", listOf(5f, 4f, 3f, 1f, -1f, -1f, 1f, 3f, 4f, 5f)),
        EqPreset("Pop", listOf(-1f, 1f, 3f, 4f, 3f, 0f, -1f, -1f, 1f, 2f)),
        EqPreset("Hip-Hop", listOf(6f, 5f, 3f, 3f, -1f, -1f, 1f, -1f, 2f, 3f)),
        EqPreset("Electronic", listOf(5f, 4f, 1f, 0f, -2f, 2f, 1f, 2f, 4f, 5f)),
        EqPreset("Dance", listOf(6f, 5f, 3f, 0f, 1f, 3f, 4f, 3f, 1f, 0f)),
        EqPreset("Jazz", listOf(3f, 2f, 1f, 2f, -2f, -2f, 0f, 1f, 2f, 3f)),
        EqPreset("Classical", listOf(4f, 3f, 2f, 1f, -1f, -1f, 0f, 2f, 3f, 4f)),
        EqPreset("Acoustic", listOf(4f, 4f, 3f, 1f, 2f, 1f, 2f, 3f, 3f, 2f)),
        EqPreset("Piano", listOf(2f, 1f, 0f, 2f, 3f, 1f, 3f, 4f, 3f, 2f)),
        EqPreset("Lounge", listOf(-3f, -1f, 1f, 2f, 3f, 2f, 0f, -1f, 2f, 1f)),
        EqPreset("Loudness", listOf(5f, 3f, 0f, 0f, -1f, 0f, 0f, 0f, 3f, 5f)),
        EqPreset("V-Shape (Hi-Fi)", listOf(4f, 3f, 1f, -1f, -2f, -1f, 1f, 3f, 4f, 5f)),
        EqPreset("Late Night", listOf(3f, 2f, 1f, 0f, 0f, 1f, 2f, 3f, 3f, 2f)),
        EqPreset("Small Speakers", listOf(5f, 5f, 4f, 3f, 2f, 0f, -1f, -1f, 0f, 0f)),
        EqPreset("Spoken Word", listOf(-4f, -3f, 0f, 2f, 4f, 4f, 3f, 1f, -1f, -3f)),
    )

    // ---- parameters (written by UI, read by audio thread) ----
    @Volatile var enabled: Boolean = false
    @Volatile var gains: FloatArray = FloatArray(BANDS)
    @Volatile var preampDb: Float = 0f
    @Volatile var bassBoostDb: Float = 0f
    @Volatile var version: Int = 0

    private val _presetName = MutableStateFlow("Flat")
    val presetName: StateFlow<String> = _presetName
    private val _tick = MutableStateFlow(0)
    /** Bumps whenever any parameter changes so Compose can re-read them. */
    val tick: StateFlow<Int> = _tick

    private fun changed() {
        version++
        _tick.value = _tick.value + 1
    }

    fun load() {
        enabled = Store.getBool("eq_on", false)
        val g = Store.getString("eq_gains", "").split(',').mapNotNull { it.toFloatOrNull() }
        if (g.size == BANDS) gains = g.toFloatArray()
        preampDb = Store.getFloat("eq_pre", 0f)
        bassBoostDb = Store.getFloat("eq_bass", 0f)
        _presetName.value = Store.getString("eq_preset", "Flat")
        changed()
    }

    private fun save() {
        Store.putBool("eq_on", enabled)
        Store.putString("eq_gains", gains.joinToString(","))
        Store.putFloat("eq_pre", preampDb)
        Store.putFloat("eq_bass", bassBoostDb)
        Store.putString("eq_preset", _presetName.value)
    }

    fun setOn(on: Boolean) { enabled = on; save(); changed() }

    fun applyPreset(p: EqPreset) {
        gains = p.gains.toFloatArray()
        _presetName.value = p.name
        if (!enabled) enabled = true
        save(); changed()
    }

    fun setBand(i: Int, db: Float) {
        val g = gains.copyOf()
        g[i] = db.coerceIn(-MAX_DB, MAX_DB)
        gains = g
        _presetName.value = "Custom"
        save(); changed()
    }

    fun setPreamp(db: Float) { preampDb = db.coerceIn(-12f, 12f); save(); changed() }
    fun setBass(db: Float) { bassBoostDb = db.coerceIn(0f, 12f); save(); changed() }

    fun reset() {
        gains = FloatArray(BANDS); preampDb = 0f; bassBoostDb = 0f
        _presetName.value = "Flat"
        save(); changed()
    }

    // ---- visualizer tap: ring buffer of the latest mono samples (post-EQ) ----
    const val RING = 4096
    val ring = FloatArray(RING)
    @Volatile var ringPos = 0
    @Volatile var sampleRate = 44100
}
