package com.valepoint.hfo.ui.widgets

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.valepoint.hfo.ui.theme.P
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

internal fun DrawScope.panelText(
    text: String,
    x: Float,
    y: Float,
    sizePx: Float,
    color: Color,
    paint: Paint,
    align: Paint.Align = Paint.Align.CENTER,
    bold: Boolean = false,
) {
    paint.isAntiAlias = true
    paint.textSize = sizePx
    paint.color = color.toArgb()
    paint.textAlign = align
    paint.typeface = if (bold) Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) else Typeface.MONOSPACE
    drawIntoCanvas { it.nativeCanvas.drawText(text, x, y, paint) }
}

/**
 * A round instrument with an engraved cream dial, a red pointer and a bezel.
 * [redFrom] marks the start of a red band as a fraction of full scale.
 */
@Composable
fun RoundGauge(
    value: Double,
    min: Double,
    max: Double,
    label: String,
    unit: String,
    modifier: Modifier = Modifier,
    diameter: Dp = 112.dp,
    majorTicks: Int = 6,
    redFrom: Double? = null,
    greenBand: ClosedRange<Double>? = null,
    decimals: Int = 1,
    darkDial: Boolean = false,
) {
    val paint = remember { Paint() }
    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val w = size.minDimension
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = w / 2f

            // bezel
            drawCircle(P.Bezel, r, c)
            drawCircle(
                Brush.linearGradient(
                    listOf(Color(0xFF5A6259), Color(0xFF23292A)),
                    start = Offset(0f, 0f), end = Offset(size.width, size.height)
                ), r * 0.97f, c
            )
            val face = if (darkDial) P.DialDark else P.Dial
            drawCircle(face, r * 0.88f, c)
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0x00000000), Color(0x33000000)),
                    center = c, radius = r * 0.88f
                ), r * 0.88f, c
            )

            val ink = if (darkDial) P.Legend else P.Ink
            val start = 135.0
            val sweep = 270.0
            val rr = r * 0.88f

            greenBand?.let { band ->
                val f0 = ((band.start - min) / (max - min)).coerceIn(0.0, 1.0)
                val f1 = ((band.endInclusive - min) / (max - min)).coerceIn(0.0, 1.0)
                drawArc(
                    color = Color(0xFF2E7D32),
                    startAngle = (start + sweep * f0).toFloat(),
                    sweepAngle = (sweep * (f1 - f0)).toFloat(),
                    useCenter = false,
                    topLeft = Offset(c.x - rr * 0.80f, c.y - rr * 0.80f),
                    size = Size(rr * 1.60f, rr * 1.60f),
                    style = Stroke(width = r * 0.07f)
                )
            }
            redFrom?.let { rf ->
                val f0 = ((rf - min) / (max - min)).coerceIn(0.0, 1.0)
                drawArc(
                    color = Color(0xFFB0281C),
                    startAngle = (start + sweep * f0).toFloat(),
                    sweepAngle = (sweep * (1.0 - f0)).toFloat(),
                    useCenter = false,
                    topLeft = Offset(c.x - rr * 0.80f, c.y - rr * 0.80f),
                    size = Size(rr * 1.60f, rr * 1.60f),
                    style = Stroke(width = r * 0.07f)
                )
            }

            // ticks
            val minor = majorTicks * 5
            for (i in 0..minor) {
                val f = i.toDouble() / minor
                val a = Math.toRadians(start + sweep * f)
                val isMajor = i % 5 == 0
                val r1 = rr * (if (isMajor) 0.66f else 0.74f)
                val r2 = rr * 0.84f
                drawLine(
                    ink,
                    Offset(c.x + (cos(a) * r1).toFloat(), c.y + (sin(a) * r1).toFloat()),
                    Offset(c.x + (cos(a) * r2).toFloat(), c.y + (sin(a) * r2).toFloat()),
                    strokeWidth = if (isMajor) r * 0.035f else r * 0.015f
                )
                if (isMajor) {
                    val v = min + (max - min) * f
                    val txt = if (kotlin.math.abs(v) >= 100 || (max - min) >= 40) v.roundToInt().toString()
                    else "%.${if ((max - min) < 4) 1 else 0}f".format(v)
                    val tr = rr * 0.62f
                    panelText(
                        txt,
                        c.x + (cos(a) * tr).toFloat(),
                        c.y + (sin(a) * tr).toFloat() + r * 0.05f,
                        r * 0.135f, ink, paint
                    )
                }
            }

            // Description and digital repeat share one plate below the hub, which
            // keeps them clear of the engraved figures around the scale.
            drawRoundRect(
                P.DialDark,
                Offset(c.x - r * 0.44f, c.y + r * 0.455f),
                Size(r * 0.88f, r * 0.28f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r * 0.03f, r * 0.03f)
            )
            panelText(unit, c.x, c.y + r * 0.565f, r * 0.092f, P.LegendDim, paint)
            panelText(
                "%.${decimals}f".format(value) + " " + label,
                c.x, c.y + r * 0.700f, r * 0.135f, P.Trace, paint, bold = true
            )

            // pointer
            val f = ((value - min) / (max - min)).coerceIn(0.0, 1.0)
            val ang = (start + sweep * f).toFloat()
            rotate(ang, c) {
                val p = Path().apply {
                    moveTo(c.x - r * 0.12f, c.y - r * 0.035f)
                    lineTo(c.x + r * 0.80f, c.y - r * 0.008f)
                    lineTo(c.x + r * 0.80f, c.y + r * 0.008f)
                    lineTo(c.x - r * 0.12f, c.y + r * 0.035f)
                    close()
                }
                drawPath(p, P.Needle)
            }
            drawCircle(Color(0xFF3A3F3B), r * 0.085f, c)
            drawCircle(P.Brass, r * 0.045f, c)

            // glass
            drawArc(
                Brush.linearGradient(
                    listOf(Color(0x22FFFFFF), Color(0x00FFFFFF)),
                    start = Offset(c.x - r, c.y - r), end = Offset(c.x + r, c.y + r)
                ),
                180f, 140f, true,
                topLeft = Offset(c.x - rr, c.y - rr), size = Size(rr * 2, rr * 2)
            )
        }
    }
}

/** A slim edgewise meter of the sort mounted in rows on a switchboard. */
@Composable
fun EdgewiseMeter(
    value: Double,
    min: Double,
    max: Double,
    label: String,
    unit: String,
    modifier: Modifier = Modifier,
    redFrom: Double? = null,
    decimals: Int = 1,
) {
    val paint = remember { Paint() }
    Canvas(modifier) {
        val h = size.height
        val w = size.width
        val barTop = h * 0.42f
        val barH = h * 0.26f
        drawRect(P.Bezel, Offset(0f, 0f), Size(w, h))
        drawRect(P.Dial, Offset(2f, 2f), Size(w - 4f, h - 4f))

        val f = ((value - min) / (max - min)).coerceIn(0.0, 1.0)
        drawRect(Color(0xFFCBC4AE), Offset(6f, barTop), Size(w - 12f, barH))
        redFrom?.let {
            val rf = ((it - min) / (max - min)).coerceIn(0.0, 1.0).toFloat()
            drawRect(Color(0x55B0281C), Offset(6f + (w - 12f) * rf, barTop), Size((w - 12f) * (1f - rf), barH))
        }
        drawRect(
            if (redFrom != null && value >= redFrom) P.Needle else Color(0xFF2F4F3E),
            Offset(6f, barTop), Size((w - 12f) * f.toFloat(), barH)
        )
        for (i in 0..10) {
            val x = 6f + (w - 12f) * i / 10f
            drawLine(P.Ink, Offset(x, barTop + barH), Offset(x, barTop + barH + (if (i % 5 == 0) h * 0.10f else h * 0.05f)), 1.5f)
        }
        panelText(label, 8f, h * 0.30f, h * 0.24f, P.Ink, paint, Paint.Align.LEFT, bold = true)
        // Format the number first: the unit is arbitrary text and may itself
        // contain a per cent sign.
        val shown = "%.${decimals}f".format(value) + " " + unit
        panelText(shown, w - 8f, h * 0.30f, h * 0.26f, P.Ink, paint, Paint.Align.RIGHT, bold = true)
    }
}

/** Bar graph of the nine cylinder exhaust temperatures with a mean line. */
@Composable
fun CylinderBars(
    values: DoubleArray,
    labels: List<String>,
    modifier: Modifier = Modifier,
    max: Double = 600.0,
    warn: Double = 480.0,
) {
    val paint = remember { Paint() }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(P.DialDark, Offset(0f, 0f), Size(w, h))
        val plotTop = h * 0.10f
        val plotH = h * 0.72f
        val n = values.size
        // Scale figures live in a gutter down the left so they never sit under a bar.
        val gutter = h * 0.30f
        val slot = (w - gutter - 8f) / n
        val mean = if (values.isNotEmpty()) values.average() else 0.0

        for (g in 0..4) {
            val y = plotTop + plotH * g / 4f
            drawLine(Color(0xFF2C332E), Offset(gutter, y), Offset(w - 8f, y), 1f)
            val v = max - (max / 4.0) * g
            panelText("${v.toInt()}", gutter - 5f, y + h * 0.028f, h * 0.075f, P.LegendDim, paint, Paint.Align.RIGHT)
        }
        val warnY = plotTop + plotH * (1f - (warn / max).toFloat())
        drawLine(Color(0xFF8A3A2A), Offset(gutter, warnY), Offset(w - 8f, warnY), 1.5f)

        for (i in 0 until n) {
            val f = (values[i] / max).coerceIn(0.0, 1.0).toFloat()
            val bh = plotH * f
            val x = gutter + slot * i + slot * 0.18f
            val bw = slot * 0.64f
            val col = when {
                values[i] > warn + 60 -> P.LampRed
                values[i] > warn -> P.LampAmber
                kotlin.math.abs(values[i] - mean) > 45 -> P.LampBlue
                else -> P.Trace
            }
            drawRect(col, Offset(x, plotTop + plotH - bh), Size(bw, bh))
            panelText(labels.getOrElse(i) { "" }, x + bw / 2f, h - 4f, h * 0.085f, P.LegendDim, paint)
            panelText(
                values[i].toInt().toString(), x + bw / 2f,
                (plotTop + plotH - bh - 3f).coerceAtLeast(h * 0.09f), h * 0.075f, P.Legend, paint
            )
        }
        val meanY = plotTop + plotH * (1f - (mean / max).toFloat()).coerceIn(0f, 1f)
        drawLine(P.Brass, Offset(gutter, meanY), Offset(w - 8f, meanY), 1.5f)
    }
}

/** Rolling strip chart, the paper recorder of the panel. */
@Composable
fun StripChart(
    history: List<Double>,
    min: Double,
    max: Double,
    label: String,
    modifier: Modifier = Modifier,
    nominal: Double? = null,
    decimals: Int = 2,
) {
    val paint = remember { Paint() }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(P.DialDark, Offset(0f, 0f), Size(w, h))
        drawRect(P.Bezel, Offset(0f, 0f), Size(w, h), style = Stroke(2f))
        for (g in 0..4) {
            val y = h * g / 4f
            drawLine(Color(0xFF262C27), Offset(0f, y), Offset(w, y), 1f)
        }
        nominal?.let {
            val y = h * (1f - ((it - min) / (max - min)).toFloat()).coerceIn(0f, 1f)
            drawLine(Color(0xFF4A5A4A), Offset(0f, y), Offset(w, y), 1f)
        }
        if (history.size > 1) {
            val path = Path()
            history.forEachIndexed { i, v ->
                val x = w * i / (history.size - 1).toFloat()
                val y = h * (1f - ((v - min) / (max - min)).toFloat()).coerceIn(0f, 1f)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, P.Trace, style = Stroke(width = 2f))
        }
        panelText(label, 6f, h * 0.20f, h * 0.15f, P.LegendDim, paint, Paint.Align.LEFT, bold = true)
        history.lastOrNull()?.let {
            panelText("%.${decimals}f".format(it), w - 6f, h * 0.20f, h * 0.17f, P.Trace, paint, Paint.Align.RIGHT, bold = true)
        }
    }
}

/**
 * The synchroscope: pointer turns at the slip frequency, twelve o'clock is in
 * phase. Slow and clockwise, close a little before the top.
 */
@Composable
fun Synchroscope(
    angleDeg: Double,
    slipHz: Double,
    live: Boolean,
    modifier: Modifier = Modifier,
    diameter: Dp = 140.dp,
    idleText: String = "BUS DEAD",
) {
    val paint = remember { Paint() }
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = size.minDimension / 2f
            drawCircle(P.Bezel, r, c)
            drawCircle(Color(0xFF2A312D), r * 0.96f, c)
            drawCircle(P.DialDark, r * 0.86f, c)

            for (i in 0 until 36) {
                val a = Math.toRadians(i * 10.0 - 90.0)
                val major = i % 3 == 0
                val r1 = r * (if (major) 0.70f else 0.78f)
                drawLine(
                    if (major) P.Legend else P.LegendDim,
                    Offset(c.x + (cos(a) * r1).toFloat(), c.y + (sin(a) * r1).toFloat()),
                    Offset(c.x + (cos(a) * r * 0.84f).toFloat(), c.y + (sin(a) * r * 0.84f).toFloat()),
                    if (major) 2f else 1f
                )
            }
            // in-phase mark
            drawLine(P.LampGreen, Offset(c.x, c.y - r * 0.86f), Offset(c.x, c.y - r * 0.62f), 4f)
            panelText("SLOW", c.x - r * 0.48f, c.y - r * 0.40f, r * 0.13f, P.LegendDim, paint)
            panelText("FAST", c.x + r * 0.48f, c.y - r * 0.40f, r * 0.13f, P.LegendDim, paint)

            if (live) {
                val a = Math.toRadians(angleDeg - 90.0)
                val tip = Offset(c.x + (cos(a) * r * 0.72f).toFloat(), c.y + (sin(a) * r * 0.72f).toFloat())
                val tail = Offset(c.x - (cos(a) * r * 0.22f).toFloat(), c.y - (sin(a) * r * 0.22f).toFloat())
                val near = kotlin.math.abs(((angleDeg + 180.0) % 360.0) - 180.0) < 12.0
                drawLine(if (near) P.LampGreen else P.NeedleWhite, tail, tip, 5f)
                drawCircle(P.Brass, r * 0.06f, c)
                panelText(
                    "%+.2f Hz".format(slipHz), c.x, c.y + r * 0.44f, r * 0.14f,
                    if (kotlin.math.abs(slipHz) < 0.3) P.LampGreen else P.LampAmber, paint, bold = true
                )
            } else {
                panelText(idleText, c.x, c.y + r * 0.05f, r * 0.17f, P.LampAmber, paint, bold = true)
            }
        }
    }
}

/** Two lamps across the breaker: dark when in phase on this connection. */
@Composable
fun SyncLamps(angleDeg: Double, live: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val n = 2
        val d = size.height.coerceAtMost(size.width / 3f)
        val phase = Math.toRadians(angleDeg)
        val bright = ((1.0 - cos(phase)) / 2.0).coerceIn(0.0, 1.0)
        // A pair of lamps across the breaker, sat side by side in the middle.
        for (i in 0 until n) {
            val cx = size.width / 2f + (i - 0.5f) * d * 1.25f
            val cy = size.height / 2
            drawCircle(P.Bezel, d / 2, Offset(cx, cy))
            val lit = if (live) bright else 0.0
            drawCircle(
                Color(
                    (0.25f + 0.75f * lit).toFloat().coerceIn(0f, 1f),
                    (0.12f + 0.55f * lit).toFloat().coerceIn(0f, 1f),
                    0.05f, 1f
                ),
                d / 2 * 0.82f, Offset(cx, cy)
            )
        }
    }
}

/** Simple round indicator lamp with an engraved legend underneath. */
@Composable
fun IndicatorLamp(
    on: Boolean,
    color: Color,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Canvas(Modifier.size(size)) {
            val r = this.size.minDimension / 2f
            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            drawCircle(P.Bezel, r, c)
            drawCircle(if (on) color else color.copy(alpha = 0.16f), r * 0.78f, c)
            if (on) drawCircle(color.copy(alpha = 0.28f), r * 1.0f, c)
            drawCircle(Color(0x33FFFFFF), r * 0.30f, Offset(c.x - r * 0.25f, c.y - r * 0.25f))
        }
        androidx.compose.material3.Text(
            label,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = if (on) P.Legend else P.LegendDim,
            modifier = Modifier
        )
    }
}

internal fun rectOf(w: Float, h: Float) = Rect(0f, 0f, w, h)

internal fun polar(c: Offset, r: Float, deg: Double): Offset {
    val a = deg / 180.0 * PI
    return Offset(c.x + (cos(a) * r).toFloat(), c.y + (sin(a) * r).toFloat())
}
