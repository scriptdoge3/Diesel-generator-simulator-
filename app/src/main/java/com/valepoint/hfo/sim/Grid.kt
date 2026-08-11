package com.valepoint.hfo.sim

import kotlin.math.abs

/** One of the other machines on the island system. */
class GridMachine(
    val name: String,
    val ratedMw: Double,
    val minMw: Double,
    val inertiaMj: Double,
    val responseTau: Double,
    var online: Boolean,
    var setpointMw: Double,
    val droopPct: Double = 4.0,
    val isPeaker: Boolean = false,
) {
    var outputMw: Double = if (online) setpointMw else 0.0
    var tripped: Boolean = false

    fun governorTarget(freq: Double): Double {
        if (!online) return 0.0
        val err = (Spec.GRID_HZ - freq) / Spec.GRID_HZ
        val pickup = err * (100.0 / droopPct) * ratedMw
        return (setpointMw + pickup).clamp(minMw, ratedMw * 1.05)
    }

    fun step(freq: Double, dt: Double) {
        outputMw = if (!online) {
            lag(outputMw, 0.0, 1.5, dt)
        } else {
            lag(outputMw, governorTarget(freq), responseTau, dt)
        }
    }
}

/**
 * The island power system Unit 3 sits on. Small, weak and with no
 * interconnector, which is exactly what makes a single 12.5 MW set matter.
 */
class Grid(private val rng: Rng) {
    val machines = listOf(
        GridMachine("UNIT 1", 12.5, 3.0, Spec.KINETIC_MJ, 7.0, online = true, setpointMw = 8.0),
        GridMachine("UNIT 2", 12.5, 3.0, Spec.KINETIC_MJ, 7.0, online = true, setpointMw = 8.0),
        GridMachine("GT 1 PEAKER", 15.0, 2.0, 22.0, 4.0, online = false, setpointMw = 0.0, droopPct = 5.0, isPeaker = true),
        GridMachine("HYDRO", 4.0, 0.0, 9.0, 3.0, online = true, setpointMw = 2.5, droopPct = 3.0),
    )

    /** Island demand, MW. */
    var demandMw: Double = 22.0
    var demandStepMw: Double = 0.0
    var frequency: Double = Spec.GRID_HZ
    var busKv: Double = Spec.RATED_KV

    /** Set during a system fault; scales bus voltage. */
    var faultDepth: Double = 0.0
    var faultTimer: Double = 0.0

    var blackout: Boolean = false

    /** Load despatch instruction for Unit 3, MW, or null when none outstanding. */
    var despatchTargetMw: Double? = null
    var despatchDeadlineSec: Double = 0.0

    private val hourly = doubleArrayOf(
        17.0, 16.0, 15.5, 15.0, 15.5, 17.0, 20.0, 25.0, // 00-07
        29.0, 31.0, 32.0, 33.0, 33.5, 32.5, 31.5, 31.0, // 08-15
        32.0, 35.0, 39.0, 41.5, 39.0, 34.0, 27.0, 21.0, // 16-23
    )

    private var noise = 0.0

    fun baseDemand(clockSec: Double): Double {
        val h = (clockSec / 3600.0) % 24.0
        val i = h.toInt()
        val f = h - i
        val a = hourly[i % 24]
        val b = hourly[(i + 1) % 24]
        return lerpRaw(a, b, f)
    }

    fun onlineGenerationMw(): Double = machines.filter { it.online }.sumOf { it.outputMw }

    fun onlineKineticMj(): Double = machines.filter { it.online }.sumOf { it.inertiaMj }

    fun spinningReserveMw(): Double =
        machines.filter { it.online }.sumOf { (it.ratedMw - it.outputMw).coerceAtLeast(0.0) }

    fun step(dt: Double, clockSec: Double) {
        noise = lag(noise, rng.gauss() * 0.45, 20.0, dt)
        demandMw = (baseDemand(clockSec) + demandStepMw + noise).coerceAtLeast(2.0)

        if (faultTimer > 0.0) {
            faultTimer -= dt
            if (faultTimer <= 0.0) faultDepth = 0.0
        }
        for (m in machines) m.step(frequency, dt)
    }

    fun tripMachine(name: String) {
        machines.firstOrNull { it.name == name && it.online }?.let {
            it.online = false
            it.tripped = true
        }
    }

    fun startPeaker() {
        machines.firstOrNull { it.isPeaker }?.let {
            if (!it.online) {
                it.online = true
                it.setpointMw = 4.0
                it.outputMw = 0.5
            }
        }
    }

    fun applyFault(depth: Double, seconds: Double) {
        faultDepth = depth
        faultTimer = seconds
    }

    /** True when some other machine is holding the system up. */
    fun othersOnline(): Boolean = machines.any { it.online }

    fun frequencyError(): Double = abs(frequency - Spec.GRID_HZ)
}
