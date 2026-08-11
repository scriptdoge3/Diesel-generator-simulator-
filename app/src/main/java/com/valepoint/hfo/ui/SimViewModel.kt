package com.valepoint.hfo.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.valepoint.hfo.sim.Plant
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns the plant and drives it in real time. [autoRun] is only turned off by
 * the screenshot and render tests, which need a still frame.
 */
class SimViewModel(private val autoRun: Boolean = true) : ViewModel() {

    var plant = Plant(System.currentTimeMillis())
        private set

    /** Bumped on every UI refresh so composables that read it recompose. */
    var tick by mutableIntStateOf(0)
        private set

    var paused by mutableStateOf(false)

    val freqHistory = ArrayDeque<Double>()
    val loadHistory = ArrayDeque<Double>()
    val exhHistory = ArrayDeque<Double>()
    val jacketHistory = ArrayDeque<Double>()

    private var lastSampleSec = 0.0
    private var frame = 0

    init {
        if (autoRun) viewModelScope.launch {
            var last = System.nanoTime()
            while (isActive) {
                delay(20)
                val now = System.nanoTime()
                val dt = ((now - last) / 1e9).coerceAtMost(0.25)
                last = now
                if (!paused) plant.step(dt)
                sample()
                frame++
                if (frame % 2 == 0) tick++
            }
        }
    }

    private fun sample() {
        val p = plant
        if (p.clockSec - lastSampleSec < 4.0) return
        lastSampleSec = p.clockSec
        push(freqHistory, p.grid.frequency)
        push(loadHistory, if (p.breakerClosed) p.genMw else 0.0)
        push(exhHistory, p.exhMeanC)
        push(jacketHistory, p.htTempC)
    }

    private fun push(q: ArrayDeque<Double>, v: Double) {
        q.addLast(v)
        while (q.size > 180) q.removeFirst()
    }

    fun setTimeScale(x: Double) {
        plant.timeScale = x
    }

    fun restart(warm: Boolean) {
        val p = Plant(System.currentTimeMillis())
        if (warm) warmStart(p)
        plant = p
        freqHistory.clear(); loadHistory.clear(); exhHistory.clear(); jacketHistory.clear()
        lastSampleSec = 0.0
    }

    /**
     * Hands the operator a unit that the previous watch has already prepared:
     * auxiliaries running, jacket preheated, fuel hot and on specification.
     * Useful when you want to practise synchronising rather than a cold start.
     */
    private fun warmStart(p: Plant) {
        p.ctl.htPump = true
        p.ctl.ltPump = true
        p.ctl.preheater = true
        p.ctl.serviceHeater = true
        p.ctl.settlingHeater = true
        p.ctl.boosterA = true
        p.ctl.circPump = true
        p.ctl.purifier = true
        p.ctl.preLube = true
        p.ctl.loStandbyPump = true
        p.htTempC = 68.0
        p.loTempC = 42.0
        p.serviceTempC = 88.0
        p.settlingTempC = 70.0
        p.fuelTempC = 128.0
        p.airBar = 29.0
        p.steamBar = 6.5
        p.loBar = 2.6
        p.ctl.fuelMode = com.valepoint.hfo.sim.FuelMode.HFO
        p.log("Previous watch has prepared the unit: auxiliaries running, jacket at 68 C.", 0)
    }
}
