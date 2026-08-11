package com.valepoint.hfo.ui.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One instrument in a [GaugeGrid]. */
class GaugeSpec(
    val value: Double,
    val min: Double,
    val max: Double,
    val label: String,
    val unit: String,
    val majorTicks: Int = 6,
    val redFrom: Double? = null,
    val greenBand: ClosedRange<Double>? = null,
    val decimals: Int = 1,
)

/**
 * Lays instruments out in as many columns as the screen will carry: two on a
 * phone held in portrait, three or four on anything wider. Nothing is ever
 * pushed off the side, so there is no hidden horizontal scrolling.
 */
@Composable
fun GaugeGrid(gauges: List<GaugeSpec>, modifier: Modifier = Modifier, maxDiameter: Dp = 152.dp) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        var cols = when {
            maxWidth < 400.dp -> 2
            maxWidth < 620.dp -> 3
            else -> 4
        }
        // Avoid leaving a single instrument stranded on the last row.
        while (cols > 2 && gauges.size % cols == 1) cols--
        val gap = 6.dp
        val d = ((maxWidth - gap * (cols - 1)) / cols).coerceAtMost(maxDiameter)
        Column {
            gauges.chunked(cols).forEachIndexed { rowIndex, row ->
                if (rowIndex > 0) Spacer(Modifier.height(gap))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap)
                ) {
                    row.forEach { g ->
                        RoundGauge(
                            value = g.value, min = g.min, max = g.max,
                            label = g.label, unit = g.unit,
                            diameter = d, majorTicks = g.majorTicks,
                            redFrom = g.redFrom, greenBand = g.greenBand,
                            decimals = g.decimals
                        )
                    }
                }
            }
        }
    }
}

/** A row of controls that wraps onto a second line rather than clipping. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WrapRow(
    modifier: Modifier = Modifier,
    spacing: Dp = 6.dp,
    content: @Composable () -> Unit,
) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) { content() }
}
