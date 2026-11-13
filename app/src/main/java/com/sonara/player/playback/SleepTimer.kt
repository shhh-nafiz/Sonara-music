package com.sonara.player.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

object SleepTimer {
    private val scope = CoroutineScope(Dispatchers.Main)
    private var job: Job? = null
    private val _endAt = MutableStateFlow(0L)
    /** Epoch millis when playback will pause, or 0 when no timer is set. */
    val endAt: StateFlow<Long> = _endAt
    var onFire: (() -> Unit)? = null

    fun start(minutes: Int) {
        job?.cancel()
        val end = System.currentTimeMillis() + minutes * 60_000L
        _endAt.value = end
        job = scope.launch {
            delay(minutes * 60_000L)
            onFire?.invoke()
            _endAt.value = 0L
        }
    }

    fun cancel() {
        job?.cancel()
        _endAt.value = 0L
    }
}
