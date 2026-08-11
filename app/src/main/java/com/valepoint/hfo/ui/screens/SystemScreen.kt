package com.valepoint.hfo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.valepoint.hfo.sim.Spec
import com.valepoint.hfo.sim.formatClock
import com.valepoint.hfo.ui.SimViewModel
import com.valepoint.hfo.ui.theme.P
import com.valepoint.hfo.ui.widgets.EdgewiseMeter
import com.valepoint.hfo.ui.widgets.Readout
import com.valepoint.hfo.ui.widgets.RoundGauge
import com.valepoint.hfo.ui.widgets.SectionPanel
import com.valepoint.hfo.ui.widgets.StripChart

@Composable
fun SystemScreen(vm: SimViewModel, modifier: Modifier = Modifier) {
    @Suppress("UNUSED_VARIABLE") val t = vm.tick
    val p = vm.plant
    val ourGen = if (p.breakerClosed) p.genMw else 0.0
    val totalGen = p.grid.onlineGenerationMw() + ourGen

    Column(modifier.padding(8.dp)) {
        SectionPanel("ISLAND SYSTEM", trailing = formatClock(p.clockSec)) {
            Row {
                RoundGauge(
                    p.grid.frequency, 45.0, 55.0, "Hz", "SYSTEM FREQ",
                    diameter = 120.dp, majorTicks = 5, greenBand = 49.5..50.5, decimals = 3
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Readout("DEMAND", "%.2f MW".format(p.grid.demandMw))
                    Readout("GENERATION", "%.2f MW".format(totalGen))
                    Readout(
                        "IMBALANCE", "%+.2f MW".format(totalGen - p.grid.demandMw),
                        color = if (kotlin.math.abs(totalGen - p.grid.demandMw) > 1.5) P.LampAmber else P.Legend
                    )
                    Readout("SPINNING RESERVE", "%.2f MW".format(p.grid.spinningReserveMw()))
                    Readout("SYSTEM INERTIA", "%.0f MJ".format(p.grid.onlineKineticMj() + if (p.breakerClosed) Spec.KINETIC_MJ else 0.0))
                }
            }
            Spacer(Modifier.height(6.dp))
            StripChart(
                vm.freqHistory.toList(), 46.0, 53.0, "SYSTEM FREQUENCY",
                Modifier
                    .fillMaxWidth()
                    .height(90.dp), nominal = 50.0, decimals = 3
            )
        }

        Spacer(Modifier.height(8.dp))

        SectionPanel("GENERATION ON THE SYSTEM") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("MACHINE", style = MaterialTheme.typography.bodySmall, color = P.Brass)
                Text("OUTPUT / RATING", style = MaterialTheme.typography.bodySmall, color = P.Brass)
            }
            Spacer(Modifier.height(4.dp))
            EdgewiseMeter(
                ourGen, 0.0, 13.0, "UNIT 3 (YOURS)", "MW",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp), decimals = 2
            )
            p.grid.machines.forEach { m ->
                Spacer(Modifier.height(3.dp))
                EdgewiseMeter(
                    m.outputMw, 0.0, m.ratedMw, if (m.online) m.name else "${m.name}  (OFF)",
                    "MW",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp), decimals = 2
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        SectionPanel("LOAD DESPATCH") {
            val target = p.grid.despatchTargetMw
            if (target != null) {
                Readout("INSTRUCTION", "%.1f MW".format(target), color = P.LampAmber)
                Readout("BY", formatClock(p.grid.despatchDeadlineSec))
                Readout(
                    "ERROR", "%+.2f MW".format(ourGen - target),
                    color = if (kotlin.math.abs(ourGen - target) < 0.6) P.LampGreen else P.LampAmber
                )
            } else {
                Text(
                    "No outstanding instruction. Hold the set where the despatcher left it.",
                    style = MaterialTheme.typography.bodySmall, color = P.LegendDim
                )
            }
            Spacer(Modifier.height(6.dp))
            StripChart(
                vm.loadHistory.toList(), 0.0, 13.0, "UNIT 3 OUTPUT",
                Modifier
                    .fillMaxWidth()
                    .height(80.dp), decimals = 2
            )
        }

        Spacer(Modifier.height(8.dp))

        SectionPanel("SYSTEM NOTES") {
            Text(
                "The island has no interconnector. Losing a 12.5 MW set at the evening " +
                    "peak drops the frequency fast because there is very little inertia. " +
                    "On droop your set will pick up automatically; on isochronous control " +
                    "it will try to hold 50 Hz on its own and can be pushed straight into " +
                    "overload.",
                style = MaterialTheme.typography.bodySmall, color = P.LegendDim
            )
        }
    }
}
