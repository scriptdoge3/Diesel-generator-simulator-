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
import com.valepoint.hfo.sim.AlarmGroup
import com.valepoint.hfo.sim.AlarmId
import com.valepoint.hfo.sim.LampState
import com.valepoint.hfo.sim.formatClock
import com.valepoint.hfo.ui.SimViewModel
import com.valepoint.hfo.ui.theme.P
import com.valepoint.hfo.ui.widgets.AnnunciatorWindow
import com.valepoint.hfo.ui.widgets.PushButton
import com.valepoint.hfo.ui.widgets.SectionPanel

private val groupTitles = mapOf(
    AlarmGroup.ENGINE to "ENGINE",
    AlarmGroup.FUEL to "FUEL OIL",
    AlarmGroup.LUBE to "LUBRICATING OIL",
    AlarmGroup.COOL to "COOLING WATER",
    AlarmGroup.ELEC to "ELECTRICAL",
    AlarmGroup.AUX to "AUXILIARIES",
)

@Composable
fun AlarmScreen(vm: SimViewModel, modifier: Modifier = Modifier) {
    @Suppress("UNUSED_VARIABLE") val t = vm.tick
    val p = vm.plant
    val now = System.currentTimeMillis()
    val fast = (now / 300) % 2 == 0L
    val slow = (now / 800) % 2 == 0L

    Column(modifier.padding(8.dp)) {
        SectionPanel(
            "ANNUNCIATOR",
            trailing = if (p.ann.horn) "HORN SOUNDING" else "${p.ann.active.size} ACTIVE"
        ) {
            Row {
                PushButton("ACCEPT", if (p.ann.horn) P.LampAmber else P.PanelHigh) { p.ann.accept() }
                Spacer(Modifier.width(6.dp))
                PushButton("RESET", P.PanelHigh) { p.ann.reset() }
                Spacer(Modifier.width(6.dp))
                PushButton("LAMP TEST", P.PanelHigh) { p.ann.lampTest = !p.ann.lampTest }
                Spacer(Modifier.width(6.dp))
                PushButton("SILENCE", P.PanelHigh) { p.ann.silence() }
            }
            val firstOut = p.ann.windows.values.firstOrNull { it.firstOut }
            if (firstOut != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "FIRST OUT  ${firstOut.id.legend.replace('\n', ' ')}  at ${formatClock(firstOut.raisedAtSec)}",
                    style = MaterialTheme.typography.labelLarge, color = P.LampRed
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        AlarmGroup.entries.forEach { g ->
            val ids = AlarmId.ofGroup(g)
            SectionPanel(groupTitles[g] ?: g.name) {
                ids.chunked(3).forEach { rowIds ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        rowIds.forEach { id ->
                            val w = p.ann.windows.getValue(id)
                            AnnunciatorWindow(
                                legend = id.legend,
                                lit = lampLit(w.state, fast, slow, p.ann.lampTest),
                                color = lampColour(id),
                                firstOut = w.firstOut,
                                modifier = Modifier.weight(1f),
                                onClick = { p.ann.accept() }
                            )
                        }
                        repeat(3 - rowIds.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        SectionPanel("ALARM LIST") {
            val act = p.ann.active.sortedByDescending { it.raisedAtSec }
            if (act.isEmpty()) {
                Text("No alarms standing.", style = MaterialTheme.typography.bodySmall, color = P.LegendDim)
            }
            act.take(20).forEach { w ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "${formatClock(w.raisedAtSec)}  ${w.id.legend.replace('\n', ' ')}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (w.id.trip) P.LampRed else P.LampAmber
                    )
                    Text(
                        when (w.state) {
                            LampState.UNACK -> "UNACC"
                            LampState.ACCEPTED -> "ACC"
                            LampState.RINGBACK -> "CLEARED"
                            LampState.CLEAR -> ""
                        },
                        style = MaterialTheme.typography.bodySmall, color = P.LegendDim
                    )
                }
            }
        }
    }
}
