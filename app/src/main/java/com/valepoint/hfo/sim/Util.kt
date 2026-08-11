package com.valepoint.hfo.sim

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow

/** Small numeric helpers shared by the plant models. */

fun Double.clamp(lo: Double, hi: Double): Double = if (this < lo) lo else if (this > hi) hi else this

fun Float.clamp(lo: Float, hi: Float): Float = if (this < lo) lo else if (this > hi) hi else this

fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t.clamp(0.0, 1.0)

/** Linear interpolation over an unclamped parameter. */
fun lerpRaw(a: Double, b: Double, t: Double): Double = a + (b - a) * t

/** Maps v from [inLo,inHi] onto [outLo,outHi], clamped. */
fun mapRange(v: Double, inLo: Double, inHi: Double, outLo: Double, outHi: Double): Double {
    if (inHi == inLo) return outLo
    return lerp(outLo, outHi, (v - inLo) / (inHi - inLo))
}

/**
 * First order lag. Moves [current] toward [target] with time constant [tau] seconds.
 * Exact exponential form, so it stays stable at any timestep.
 */
fun lag(current: Double, target: Double, tau: Double, dt: Double): Double {
    if (tau <= 1e-6) return target
    val a = exp(-dt / tau)
    return target + (current - target) * a
}

/** Slew rate limiter, [rate] in units per second. */
fun slew(current: Double, target: Double, rate: Double, dt: Double): Double {
    val d = target - current
    val max = rate * dt
    return if (abs(d) <= max) target else current + max * (if (d > 0) 1.0 else -1.0)
}

/** Wraps an angle into [-180, 180) degrees. */
fun wrapDeg(deg: Double): Double {
    var d = deg % 360.0
    if (d >= 180.0) d -= 360.0
    if (d < -180.0) d += 360.0
    return d
}

/** Wraps an angle into [0, 360) degrees. */
fun wrap360(deg: Double): Double {
    var d = deg % 360.0
    if (d < 0) d += 360.0
    return d
}

/** Deterministic-ish pseudo random source so a session can be replayed from a seed. */
class Rng(seed: Long = 0x5DEECE66DL) {
    private var s: Long = if (seed == 0L) 1L else seed

    fun nextDouble(): Double {
        // xorshift64*
        s = s xor (s shl 13)
        s = s xor (s ushr 7)
        s = s xor (s shl 17)
        val v = (s * -0x61c8864680b583ebL) ushr 11
        return v.toDouble() / (1L shl 53).toDouble()
    }

    fun range(lo: Double, hi: Double): Double = lo + (hi - lo) * nextDouble()

    fun chance(p: Double): Boolean = nextDouble() < p

    fun int(loInclusive: Int, hiExclusive: Int): Int {
        if (hiExclusive <= loInclusive) return loInclusive
        return loInclusive + (nextDouble() * (hiExclusive - loInclusive)).toInt()
    }

    /** Roughly normal, mean 0 sigma 1, via sum of uniforms. */
    fun gauss(): Double = (nextDouble() + nextDouble() + nextDouble() + nextDouble() +
        nextDouble() + nextDouble() + nextDouble() + nextDouble() + nextDouble() +
        nextDouble() + nextDouble() + nextDouble()) - 6.0
}

/**
 * Kinematic viscosity of a residual fuel at temperature, from its 50 degC grade.
 * Uses the Walther / ASTM D341 relation:  log10(log10(v + 0.7)) = A - B * log10(T_kelvin)
 * with B fitted from the typical slope of residual fuels. Good enough that the
 * viscosity/temperature behaviour on the panel matches a real viscosity chart.
 */
fun viscosityAt(gradeCst50: Double, tempC: Double): Double {
    val b = 3.55 // slope typical of residual fuel oils
    val t50 = 273.15 + 50.0
    val z50 = gradeCst50 + 0.7
    val a = log10(log10(z50)) + b * log10(t50)
    val t = (273.15 + tempC).coerceAtLeast(250.0)
    val ll = a - b * log10(t)
    val z = 10.0.pow(10.0.pow(ll))
    return (z - 0.7).coerceIn(0.5, 50000.0)
}

/** Temperature that yields a target viscosity for a given fuel grade. Bisection, cheap and robust. */
fun tempForViscosity(gradeCst50: Double, targetCst: Double): Double {
    var lo = 20.0
    var hi = 200.0
    repeat(40) {
        val mid = (lo + hi) * 0.5
        if (viscosityAt(gradeCst50, mid) > targetCst) lo = mid else hi = mid
    }
    return (lo + hi) * 0.5
}

private fun log10(x: Double): Double = ln(x) / ln(10.0)

/** Saturation pressure-ish helper for the steam side, bar absolute from degC. */
fun steamSatPressure(tempC: Double): Double = exp(13.7 - 5120.0 / (tempC + 273.15))

fun formatClock(secondsOfDay: Double): String {
    val total = ((secondsOfDay % 86400.0) + 86400.0) % 86400.0
    val h = (total / 3600.0).toInt()
    val m = ((total % 3600.0) / 60.0).toInt()
    val s = (total % 60.0).toInt()
    return "%02d:%02d:%02d".format(h, m, s)
}
