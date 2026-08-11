package com.valepoint.hfo.ui.widgets

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.valepoint.hfo.sim.FuelMode
import com.valepoint.hfo.sim.Plant
import com.valepoint.hfo.sim.Spec
import com.valepoint.hfo.ui.theme.P

/**
 * Drawing helpers for the plant mimics.
 *
 * Text is sized from the canvas *width*, not its height. Legends run
 * horizontally, so width is what decides whether two of them collide, and
 * sizing off height is what makes a diagram fall apart when it is moved from a
 * tablet to a phone.
 */
private class Mim(val ds: DrawScope, val paint: Paint, val textScale: Float) {
    val w get() = ds.size.width
    val h get() = ds.size.height

    /** Nominal legend size in pixels. */
    val ts get() = w * textScale

    fun x(f: Double) = (w * f).toFloat()
    fun y(f: Double) = (h * f).toFloat()

    fun tank(fx: Double, fy: Double, fw: Double, fh: Double, level: Double, label: String, sub: String, hot: Boolean) {
        val l = Offset(x(fx), y(fy))
        val s = Size(x(fw), y(fh))
        ds.drawRect(Color(0xFF1A1E1B), l, s)
        val fillH = s.height * level.coerceIn(0.0, 1.0).toFloat()
        ds.drawRect(
            if (hot) Color(0xFF6E4A22) else Color(0xFF3A3A2A),
            Offset(l.x, l.y + s.height - fillH), Size(s.width, fillH)
        )
        ds.drawRect(P.Legend, l, s, style = Stroke(1.5f))
        with(ds) {
            panelText(label, l.x + s.width / 2, l.y - ts * 0.35f, ts, P.Legend, paint, bold = true)
            panelText(sub, l.x + s.width / 2, l.y + s.height + ts * 1.15f, ts * 0.92f, P.LegendDim, paint)
        }
    }

    fun pipe(x1: Double, y1: Double, x2: Double, y2: Double, flowing: Boolean, hot: Boolean = false) {
        val col = when {
            !flowing -> Color(0xFF4A5148)
            hot -> Color(0xFFD9843C)
            else -> P.Trace
        }
        ds.drawLine(col, Offset(x(x1), y(y1)), Offset(x(x2), y(y2)), strokeWidth = if (flowing) 4f else 2.5f)
    }

    fun pump(fx: Double, fy: Double, running: Boolean, label: String) {
        val c = Offset(x(fx), y(fy))
        val r = ts * 0.85f
        ds.drawCircle(if (running) P.LampGreen else Color(0xFF3A403A), r, c)
        ds.drawCircle(P.Legend, r, c, style = Stroke(1.5f))
        with(ds) {
            panelText(label, c.x, c.y + r + ts * 1.05f, ts * 0.92f, P.LegendDim, paint)
        }
    }

    fun box(fx: Double, fy: Double, fw: Double, fh: Double, on: Boolean, label: String, value: String, onColor: Color) {
        val l = Offset(x(fx), y(fy))
        val s = Size(x(fw), y(fh))
        ds.drawRect(if (on) onColor.copy(alpha = 0.75f) else Color(0xFF2A302B), l, s)
        ds.drawRect(P.Legend, l, s, style = Stroke(1.5f))
        val ink = if (on) Color(0xFF14170F) else P.Legend
        val inkDim = if (on) Color(0xFF14170F) else P.LegendDim
        with(ds) {
            panelText(label, l.x + s.width / 2, l.y + s.height * 0.45f, ts, ink, paint, bold = true)
            panelText(value, l.x + s.width / 2, l.y + s.height * 0.85f, ts * 0.92f, inkDim, paint)
        }
    }

    fun valve(fx: Double, fy: Double, open: Boolean, label: String) {
        val c = Offset(x(fx), y(fy))
        val r = ts * 0.7f
        val col = if (open) P.LampGreen else P.LampRed
        ds.drawPath(androidx.compose.ui.graphics.Path().apply {
            moveTo(c.x - r, c.y - r); lineTo(c.x + r, c.y + r); lineTo(c.x + r, c.y - r)
            lineTo(c.x - r, c.y + r); close()
        }, col)
        with(ds) { panelText(label, c.x + r * 2.2f, c.y + ts * 0.35f, ts * 0.92f, P.LegendDim, paint) }
    }

    fun caption(text: String, fy: Double, color: Color, scale: Float = 1.0f, bold: Boolean = false) {
        with(ds) { panelText(text, w / 2f, y(fy), ts * scale, color, paint, bold = bold) }
    }
}

/** True when there is not enough width to carry the wide, left to right layout. */
private const val NARROW_DP = 430

/**
 * Mimic of the heavy fuel treatment and supply system.
 *
 * Two layouts: a wide one that reads left to right across a tablet, and a tall
 * one for a phone in portrait where the process runs down the screen instead.
 */
@Composable
fun FuelMimic(p: Plant, modifier: Modifier = Modifier) {
    val paint = remember { Paint() }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val narrow = maxWidth.value < NARROW_DP
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(if (narrow) 0.92f else 2.30f)
        ) {
            drawRect(P.DialDark, Offset(0f, 0f), Size(size.width, size.height))
            val m = Mim(this, paint, if (narrow) 0.0265f else 0.0175f)
            if (narrow) fuelNarrow(m, p) else fuelWide(m, p)
        }
    }
}

private fun fuelNarrow(m: Mim, p: Plant) {
    val onHfo = p.ctl.fuelMode == FuelMode.HFO
    val boosting = p.ctl.boosterA || p.ctl.boosterB || p.ctl.circPump
    val hot = p.fuelTempC > 90

    m.caption(
        "WATER ${"%.2f".format(p.waterPct)}%   CAT FINES ${p.catFinesPpm.toInt()} ppm   STEAM ${"%.1f".format(p.steamBar)} bar",
        0.035, P.LegendDim, 0.88f
    )

    // bunker across to the settling tank
    m.tank(0.04, 0.09, 0.22, 0.11, p.bunkerM3 / 900.0, "BUNKER", "${p.bunkerM3.toInt()} m3", false)
    m.pipe(0.26, 0.145, 0.40, 0.145, p.ctl.transferPump)
    m.pump(0.33, 0.145, p.ctl.transferPump, "XFER")
    m.tank(0.40, 0.09, 0.22, 0.11, p.settlingM3 / Spec.SETTLING_TANK_M3, "SETTLING", "${p.settlingTempC.toInt()} C", p.settlingTempC > 55)

    // down through the purifier into the service tank
    m.pipe(0.51, 0.20, 0.51, 0.28, p.purifierRunning)
    m.box(0.40, 0.28, 0.22, 0.085, p.purifierRunning, "PURIFIER", "${p.purifierFeedTempC.toInt()} C", P.LampGreen)
    m.pipe(0.51, 0.365, 0.51, 0.44, p.purifierRunning)
    m.tank(0.40, 0.44, 0.22, 0.11, p.serviceM3 / Spec.SERVICE_TANK_M3, "SERVICE", "${p.serviceTempC.toInt()} C", p.serviceTempC > 60)
    m.tank(0.04, 0.44, 0.22, 0.11, p.mdoM3 / Spec.MDO_TANK_M3, "MDO", "${p.mdoM3.toInt()} m3", false)

    // changeover and the booster module
    m.pipe(0.51, 0.55, 0.51, 0.635, onHfo && boosting, hot = hot)
    m.pipe(0.15, 0.55, 0.15, 0.665, !onHfo && boosting)
    m.pipe(0.15, 0.665, 0.51, 0.665, !onHfo && boosting)
    m.valve(0.51, 0.655, boosting, if (onHfo) "HFO" else "MDO")
    m.pipe(0.51, 0.675, 0.51, 0.735, boosting, hot = hot)
    m.pipe(0.51, 0.735, 0.22, 0.735, boosting, hot = hot)
    m.pump(0.17, 0.735, boosting, "BOOSTER")
    m.pipe(0.13, 0.735, 0.08, 0.735, boosting, hot = hot)
    m.pipe(0.08, 0.735, 0.08, 0.835, boosting, hot = hot)

    m.box(0.04, 0.835, 0.26, 0.085, p.heaterOutput > 0.05, "HEATER", "${(p.heaterOutput * 100).toInt()} %", Color(0xFFD9843C))
    m.pipe(0.30, 0.877, 0.36, 0.877, boosting, hot = hot)
    m.box(0.36, 0.835, 0.26, 0.085, p.fuelFilterDp > 1.2, "FILTER", "%.2f bar".format(p.fuelFilterDp), P.LampRed)
    m.pipe(0.62, 0.877, 0.68, 0.877, boosting, hot = hot)
    m.box(0.68, 0.835, 0.28, 0.085, p.rpm > 100, "ENGINE", "${p.rpm.toInt()} rpm", P.LampGreen)

    m.caption(
        "VISCOSITY ${"%.1f".format(p.fuelViscCst)} cSt    ${p.fuelTempC.toInt()} C    ${"%.1f".format(p.fuelBar)} bar",
        0.975, if (p.fuelViscCst in 8.0..18.0) P.Trace else P.LampAmber, 1.0f, bold = true
    )
}

private fun fuelWide(m: Mim, p: Plant) {
    val onHfo = p.ctl.fuelMode == FuelMode.HFO
    val boosting = p.ctl.boosterA || p.ctl.boosterB || p.ctl.circPump
    val hot = p.fuelTempC > 90

    m.caption(
        "WATER ${"%.2f".format(p.waterPct)}%  CAT FINES ${p.catFinesPpm.toInt()} ppm  STEAM ${"%.1f".format(p.steamBar)} bar",
        0.055, P.LegendDim, 0.9f
    )

    m.tank(0.02, 0.15, 0.13, 0.27, p.bunkerM3 / 900.0, "BUNKER", "${p.bunkerM3.toInt()} m3", false)
    m.tank(0.22, 0.11, 0.13, 0.31, p.settlingM3 / Spec.SETTLING_TANK_M3, "SETTLING", "${p.settlingTempC.toInt()} C", p.settlingTempC > 55)
    m.tank(0.52, 0.11, 0.13, 0.31, p.serviceM3 / Spec.SERVICE_TANK_M3, "SERVICE", "${p.serviceTempC.toInt()} C", p.serviceTempC > 60)
    m.tank(0.22, 0.62, 0.13, 0.16, p.mdoM3 / Spec.MDO_TANK_M3, "MDO", "${p.mdoM3.toInt()} m3", false)

    m.pipe(0.15, 0.34, 0.22, 0.34, p.ctl.transferPump)
    m.pump(0.185, 0.42, p.ctl.transferPump, "XFER")

    m.pipe(0.35, 0.24, 0.41, 0.24, p.purifierRunning)
    m.box(0.38, 0.15, 0.11, 0.18, p.purifierRunning, "PURIF", "${p.purifierFeedTempC.toInt()} C", P.LampGreen)
    m.pipe(0.49, 0.24, 0.52, 0.24, p.purifierRunning)

    m.pipe(0.585, 0.42, 0.585, 0.56, onHfo && boosting, hot = hot)
    m.pipe(0.285, 0.62, 0.285, 0.58, !onHfo && boosting)
    m.pipe(0.285, 0.58, 0.585, 0.58, !onHfo && boosting)
    m.valve(0.585, 0.60, boosting, if (onHfo) "HFO" else "MDO")

    m.pipe(0.585, 0.62, 0.585, 0.68, boosting, hot = hot)
    m.pipe(0.585, 0.68, 0.62, 0.68, boosting, hot = hot)
    m.pump(0.645, 0.68, boosting, "BOOST")
    m.pipe(0.67, 0.68, 0.70, 0.68, boosting, hot = hot)

    m.box(0.70, 0.60, 0.10, 0.16, p.heaterOutput > 0.05, "HEATER", "${(p.heaterOutput * 100).toInt()}%", Color(0xFFD9843C))
    m.pipe(0.80, 0.68, 0.83, 0.68, boosting, hot = hot)
    m.box(0.83, 0.60, 0.09, 0.16, p.fuelFilterDp > 1.2, "FILTER", "%.2f".format(p.fuelFilterDp), P.LampRed)
    m.pipe(0.92, 0.68, 0.96, 0.68, boosting, hot = hot)
    m.pipe(0.96, 0.68, 0.96, 0.33, boosting, hot = hot)

    m.box(0.78, 0.15, 0.19, 0.18, p.rpm > 100, "ENGINE", "${p.rpm.toInt()} rpm", P.LampGreen)

    m.caption(
        "VISCOSITY ${"%.1f".format(p.fuelViscCst)} cSt   TEMP ${p.fuelTempC.toInt()} C   PRESS ${"%.1f".format(p.fuelBar)} bar",
        0.97, if (p.fuelViscCst in 8.0..18.0) P.Trace else P.LampAmber, 1.0f, bold = true
    )
}

/** Mimic of the jacket water, low temperature and lubricating oil circuits. */
@Composable
fun CoolingMimic(p: Plant, modifier: Modifier = Modifier) {
    val paint = remember { Paint() }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val narrow = maxWidth.value < NARROW_DP
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(if (narrow) 0.98f else 2.30f)
        ) {
            drawRect(P.DialDark, Offset(0f, 0f), Size(size.width, size.height))
            val m = Mim(this, paint, if (narrow) 0.0265f else 0.0175f)
            if (narrow) coolNarrow(m, p) else coolWide(m, p)
        }
    }
}

private fun coolNarrow(m: Mim, p: Plant) {
    val ht = p.ctl.htPump && !p.auxMotorFailed
    val lt = p.ctl.ltPump && !p.auxMotorFailed
    val hotJacket = p.htTempC > 70

    m.tank(0.06, 0.07, 0.20, 0.09, p.htExpansionPct / 100.0, "EXP TANK", "${p.htExpansionPct.toInt()} %", false)
    m.box(0.62, 0.07, 0.32, 0.09, p.preheaterOn, "PREHEATER", if (p.preheaterOn) "ON" else "OFF", Color(0xFFD9843C))
    m.pipe(0.78, 0.16, 0.78, 0.27, p.preheaterOn, hot = true)
    m.pipe(0.16, 0.20, 0.16, 0.27, ht)

    m.box(0.10, 0.27, 0.80, 0.13, p.rpm > 100, "ENGINE", "JACKET ${p.htTempC.toInt()} C", P.LampGreen)

    // HT circuit down the right hand side and back through the pump
    m.pipe(0.80, 0.40, 0.80, 0.48, ht, hot = hotJacket)
    m.box(0.62, 0.48, 0.32, 0.10, ht, "HT COOLER", "${(p.htValve * 100).toInt()} %", Color(0xFF3E7FA8))
    m.pipe(0.78, 0.58, 0.78, 0.655, ht)
    m.pipe(0.78, 0.655, 0.30, 0.655, ht)
    m.pump(0.24, 0.655, ht, "HT PUMP")
    m.pipe(0.19, 0.655, 0.10, 0.655, ht)
    m.pipe(0.10, 0.655, 0.10, 0.40, ht)

    // LT circuit: lube oil cooler, charge air cooler and the radiators
    m.box(0.06, 0.735, 0.40, 0.10, lt, "LO COOLER", "${p.loTempC.toInt()} C", Color(0xFF3E7FA8))
    m.box(0.54, 0.735, 0.40, 0.10, lt, "CHARGE AIR", "${p.chargeAirTemp.toInt()} C", Color(0xFF3E7FA8))
    m.pipe(0.26, 0.835, 0.26, 0.875, lt)
    m.pipe(0.74, 0.835, 0.74, 0.875, lt)
    m.pipe(0.26, 0.875, 0.74, 0.875, lt)
    m.box(0.30, 0.875, 0.40, 0.085, lt, "RADIATORS", "${(p.radiatorDemand * 100).toInt()} %", Color(0xFF3E7FA8))

    m.caption(
        "LO ${"%.1f".format(p.loBar)} bar / ${p.loTempC.toInt()} C     LT ${p.ltTempC.toInt()} C     AMB ${p.ambientC.toInt()} C",
        0.99, P.Legend, 1.0f, bold = true
    )
}

private fun coolWide(m: Mim, p: Plant) {
    val ht = p.ctl.htPump && !p.auxMotorFailed
    val lt = p.ctl.ltPump && !p.auxMotorFailed

    m.box(0.36, 0.36, 0.26, 0.28, p.rpm > 100, "ENGINE", "${p.htTempC.toInt()} C JKT", P.LampGreen)

    m.pipe(0.62, 0.42, 0.80, 0.42, ht, hot = p.htTempC > 70)
    m.box(0.80, 0.30, 0.16, 0.22, ht, "HT COOLER", "${p.htValve.times(100).toInt()}%", Color(0xFF3E7FA8))
    m.pipe(0.88, 0.52, 0.88, 0.72, ht)
    m.pipe(0.88, 0.72, 0.30, 0.72, ht)
    m.pump(0.26, 0.72, ht, "HT PUMP")
    m.pipe(0.22, 0.72, 0.12, 0.72, ht)
    m.pipe(0.12, 0.72, 0.12, 0.50, ht)
    m.pipe(0.12, 0.50, 0.36, 0.50, ht)
    m.box(0.04, 0.10, 0.14, 0.16, p.preheaterOn, "PREHEAT", "${p.htTempC.toInt()} C", Color(0xFFD9843C))
    m.pipe(0.11, 0.26, 0.11, 0.50, p.preheaterOn, hot = true)
    m.tank(0.24, 0.06, 0.09, 0.16, p.htExpansionPct / 100.0, "EXP TK", "${p.htExpansionPct.toInt()}%", false)

    m.box(0.46, 0.78, 0.20, 0.16, lt, "RADIATORS", "${(p.radiatorDemand * 100).toInt()}%", Color(0xFF3E7FA8))
    m.pipe(0.66, 0.86, 0.80, 0.86, lt)
    m.pipe(0.80, 0.86, 0.80, 0.64, lt)
    m.box(0.04, 0.42, 0.14, 0.16, lt, "LO COOLER", "${p.loTempC.toInt()} C", Color(0xFF3E7FA8))
    m.pipe(0.18, 0.50, 0.36, 0.50, lt)

    m.caption(
        "LO ${"%.1f".format(p.loBar)} bar / ${p.loTempC.toInt()} C    LT ${p.ltTempC.toInt()} C    CHARGE AIR ${p.chargeAirTemp.toInt()} C    AMB ${p.ambientC.toInt()} C",
        0.98, P.Legend, 1.0f, bold = true
    )
}
