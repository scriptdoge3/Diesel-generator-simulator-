package com.valepoint.hfo.ui.widgets

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.valepoint.hfo.sim.FuelMode
import com.valepoint.hfo.sim.Plant
import com.valepoint.hfo.sim.Spec
import com.valepoint.hfo.ui.theme.P

private class Mim(val ds: DrawScope, val paint: Paint) {
    val w get() = ds.size.width
    val h get() = ds.size.height
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
            panelText(label, l.x + s.width / 2, l.y - h * 0.012f, h * 0.045f, P.Legend, paint, bold = true)
            panelText(sub, l.x + s.width / 2, l.y + s.height + h * 0.055f, h * 0.042f, P.LegendDim, paint)
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
        val r = h * 0.045f
        ds.drawCircle(if (running) P.LampGreen else Color(0xFF3A403A), r, c)
        ds.drawCircle(P.Legend, r, c, style = Stroke(1.5f))
        with(ds) {
            panelText(label, c.x, c.y + r + h * 0.055f, h * 0.040f, P.LegendDim, paint)
        }
    }

    fun box(fx: Double, fy: Double, fw: Double, fh: Double, on: Boolean, label: String, value: String, onColor: Color) {
        val l = Offset(x(fx), y(fy))
        val s = Size(x(fw), y(fh))
        ds.drawRect(if (on) onColor.copy(alpha = 0.75f) else Color(0xFF2A302B), l, s)
        ds.drawRect(P.Legend, l, s, style = Stroke(1.5f))
        with(ds) {
            panelText(label, l.x + s.width / 2, l.y + s.height * 0.45f, h * 0.042f, if (on) Color(0xFF14170F) else P.Legend, paint, bold = true)
            panelText(value, l.x + s.width / 2, l.y + s.height * 0.85f, h * 0.040f, if (on) Color(0xFF14170F) else P.LegendDim, paint)
        }
    }

    fun valve(fx: Double, fy: Double, open: Boolean, label: String) {
        val c = Offset(x(fx), y(fy))
        val r = h * 0.030f
        val col = if (open) P.LampGreen else P.LampRed
        ds.drawPath(androidx.compose.ui.graphics.Path().apply {
            moveTo(c.x - r, c.y - r); lineTo(c.x + r, c.y + r); lineTo(c.x + r, c.y - r)
            lineTo(c.x - r, c.y + r); close()
        }, col)
        with(ds) { panelText(label, c.x, c.y - r - h * 0.018f, h * 0.038f, P.LegendDim, paint) }
    }
}

/** Mimic of the heavy fuel treatment and supply system. */
@Composable
fun FuelMimic(p: Plant, modifier: Modifier = Modifier) {
    val paint = remember { Paint() }
    Canvas(modifier) {
        drawRect(P.DialDark, Offset(0f, 0f), Size(size.width, size.height))
        val m = Mim(this, paint)
        val onHfo = p.ctl.fuelMode == FuelMode.HFO
        val boosting = p.ctl.boosterA || p.ctl.boosterB || p.ctl.circPump

        m.tank(0.02, 0.15, 0.13, 0.27, p.bunkerM3 / 900.0, "BUNKER", "${p.bunkerM3.toInt()} m3", false)
        m.tank(0.22, 0.11, 0.13, 0.31, p.settlingM3 / Spec.SETTLING_TANK_M3, "SETTLING", "${p.settlingTempC.toInt()} C", p.settlingTempC > 55)
        m.tank(0.52, 0.11, 0.13, 0.31, p.serviceM3 / Spec.SERVICE_TANK_M3, "SERVICE", "${p.serviceTempC.toInt()} C", p.serviceTempC > 60)
        m.tank(0.22, 0.62, 0.13, 0.16, p.mdoM3 / Spec.MDO_TANK_M3, "MDO", "${p.mdoM3.toInt()} m3", false)

        m.pipe(0.15, 0.34, 0.22, 0.34, p.ctl.transferPump)
        m.pump(0.185, 0.42, p.ctl.transferPump, "XFER")

        m.pipe(0.35, 0.24, 0.41, 0.24, p.purifierRunning)
        m.box(0.38, 0.15, 0.11, 0.18, p.purifierRunning, "PURIF", "${p.purifierFeedTempC.toInt()} C", P.LampGreen)
        m.pipe(0.49, 0.24, 0.52, 0.24, p.purifierRunning)

        // service tank / MDO to the booster module
        m.pipe(0.585, 0.42, 0.585, 0.56, onHfo && boosting, hot = p.fuelTempC > 90)
        m.pipe(0.285, 0.62, 0.285, 0.58, !onHfo && boosting)
        m.pipe(0.285, 0.58, 0.585, 0.58, !onHfo && boosting)
        m.valve(0.585, 0.60, boosting, if (onHfo) "HFO" else "MDO")

        m.pipe(0.585, 0.62, 0.585, 0.68, boosting, hot = p.fuelTempC > 90)
        m.pipe(0.585, 0.68, 0.62, 0.68, boosting, hot = p.fuelTempC > 90)
        m.pump(0.645, 0.68, boosting, "BOOST")
        m.pipe(0.67, 0.68, 0.70, 0.68, boosting, hot = p.fuelTempC > 90)

        m.box(0.70, 0.60, 0.10, 0.16, p.heaterOutput > 0.05, "HEATER", "${(p.heaterOutput * 100).toInt()}%", Color(0xFFD9843C))
        m.pipe(0.80, 0.68, 0.83, 0.68, boosting, hot = p.fuelTempC > 90)
        m.box(0.83, 0.60, 0.09, 0.16, p.fuelFilterDp > 1.2, "FILTER", "%.2f".format(p.fuelFilterDp), P.LampRed)
        m.pipe(0.92, 0.68, 0.96, 0.68, boosting, hot = p.fuelTempC > 90)
        m.pipe(0.96, 0.68, 0.96, 0.33, boosting, hot = p.fuelTempC > 90)

        m.box(0.78, 0.15, 0.19, 0.18, p.rpm > 100, "ENGINE", "${p.rpm.toInt()} rpm", P.LampGreen)

        with(this) {
            panelText(
                "VISCOSITY ${"%.1f".format(p.fuelViscCst)} cSt   TEMP ${p.fuelTempC.toInt()} C   PRESS ${"%.1f".format(p.fuelBar)} bar",
                size.width / 2, size.height * 0.97f, size.height * 0.048f,
                if (p.fuelViscCst in 8.0..18.0) P.Trace else P.LampAmber, paint, bold = true
            )
            panelText(
                "WATER ${"%.2f".format(p.waterPct)}%  CAT FINES ${p.catFinesPpm.toInt()} ppm  STEAM ${"%.1f".format(p.steamBar)} bar",
                size.width / 2, size.height * 0.055f, size.height * 0.042f, P.LegendDim, paint
            )
        }
    }
}

/** Mimic of the jacket water, low temperature and lubricating oil circuits. */
@Composable
fun CoolingMimic(p: Plant, modifier: Modifier = Modifier) {
    val paint = remember { Paint() }
    Canvas(modifier) {
        drawRect(P.DialDark, Offset(0f, 0f), Size(size.width, size.height))
        val m = Mim(this, paint)
        val ht = p.ctl.htPump && !p.auxMotorFailed
        val lt = p.ctl.ltPump && !p.auxMotorFailed

        m.box(0.36, 0.36, 0.26, 0.28, p.rpm > 100, "ENGINE", "${p.htTempC.toInt()} C JKT", P.LampGreen)

        // HT loop
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

        // LT loop and radiators
        m.box(0.46, 0.78, 0.20, 0.16, lt, "RADIATORS", "${(p.radiatorDemand * 100).toInt()}%", Color(0xFF3E7FA8))
        m.pipe(0.66, 0.86, 0.80, 0.86, lt)
        m.pipe(0.80, 0.86, 0.80, 0.64, lt)
        m.box(0.04, 0.42, 0.14, 0.16, lt, "LO COOLER", "${p.loTempC.toInt()} C", Color(0xFF3E7FA8))
        m.pipe(0.18, 0.50, 0.36, 0.50, lt)

        with(this) {
            panelText(
                "LO ${"%.1f".format(p.loBar)} bar / ${p.loTempC.toInt()} C    LT ${p.ltTempC.toInt()} C    CHARGE AIR ${p.chargeAirTemp.toInt()} C    AMB ${p.ambientC.toInt()} C",
                size.width / 2, size.height * 0.98f, size.height * 0.046f, P.Legend, paint, bold = true
            )
        }
    }
}
