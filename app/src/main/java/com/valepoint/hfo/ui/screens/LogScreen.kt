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
import com.valepoint.hfo.ui.SimViewModel
import com.valepoint.hfo.ui.theme.P
import com.valepoint.hfo.ui.widgets.PanelSlider
import com.valepoint.hfo.ui.widgets.PushButton
import com.valepoint.hfo.ui.widgets.Readout
import com.valepoint.hfo.ui.widgets.SectionPanel
import com.valepoint.hfo.ui.widgets.WrapRow
import com.valepoint.hfo.ui.widgets.Selector

@Composable
fun LogScreen(vm: SimViewModel, modifier: Modifier = Modifier) {
    @Suppress("UNUSED_VARIABLE") val t = vm.tick
    val p = vm.plant

    Column(modifier.padding(8.dp)) {
        SectionPanel("WATCH SETTINGS") {
            val scales = listOf(1.0, 5.0, 15.0, 60.0)
            Selector(
                "TIME", scales.map { "${it.toInt()}x" },
                scales.indexOfFirst { it == p.timeScale }.coerceAtLeast(0)
            ) { p.timeScale = scales[it] }
            WrapRow {
                PushButton(if (vm.paused) "RESUME" else "PAUSE", P.PanelHigh) { vm.paused = !vm.paused }
                PushButton("NEW WATCH\nCOLD", P.PanelHigh) { vm.restart(warm = false) }
                PushButton("NEW WATCH\nPREPARED", P.PanelHigh) { vm.restart(warm = true) }
            }
            Spacer(Modifier.height(6.dp))
            PanelSlider(
                "FAULT INTENSITY  ${"%.1f".format(p.events.intensity)}",
                p.events.intensity.toFloat(), range = 0f..2.5f
            ) { p.events.intensity = it.toDouble() }
            Selector(
                "RANDOM EVENTS", listOf("ON", "OFF"),
                if (p.events.randomEventsEnabled) 0 else 1
            ) { p.events.randomEventsEnabled = it == 0 }
        }

        Spacer(Modifier.height(8.dp))

        SectionPanel("ENGINE ROOM REPAIRS") {
            Text(
                "Send a hand to deal with what has gone wrong.",
                style = MaterialTheme.typography.bodySmall, color = P.LegendDim
            )
            Spacer(Modifier.height(4.dp))
            WrapRow {
                if (!p.boilerOnline) PushButton("RELIGHT\nBOILER", P.LampAmber) { p.events.repairBoiler() }
                if (p.auxMotorFailed) PushButton("RESET\nMOTOR", P.LampAmber) { p.events.repairAuxMotor() }
                if (p.compressorFailed) PushButton("REPAIR\nCOMPRESSOR", P.LampAmber) { p.events.repairCompressor() }
            }
            Spacer(Modifier.height(4.dp))
            WrapRow {
                if (p.controlAirBar < 5.0) PushButton("REPAIR\nCONTROL AIR", P.LampAmber) { p.events.restoreControlAir() }
                if (p.loLeakLpm > 0.1) PushButton("CLAMP\nLO LEAK", P.LampAmber) { p.events.stopLoLeak() }
                if (p.htLeakLpm > 0.1) PushButton("MAKE GOOD\nHT LEAK", P.LampAmber) { p.events.stopHtLeak() }
            }
            Spacer(Modifier.height(4.dp))
            Readout("BOILER", if (p.boilerOnline) "ON LINE" else "TRIPPED", color = if (p.boilerOnline) P.Legend else P.LampRed)
            Readout("CONTROL AIR", "%.1f bar".format(p.controlAirBar))
            Readout("PURIFIER", if (p.purifierTripped) "TRIPPED" else if (p.purifierRunning) "RUNNING" else "STOPPED")
        }

        Spacer(Modifier.height(8.dp))

        SectionPanel("PROVOKE AN EVENT", trailing = "SANDBOX") {
            Text(
                "Off-site events come from the system and the fuel supply. On-site " +
                    "events are the machine and its auxiliaries.",
                style = MaterialTheme.typography.bodySmall, color = P.LegendDim
            )
            Spacer(Modifier.height(6.dp))
            listOf(true, false).forEach { offsite ->
                Text(
                    if (offsite) "OFF SITE" else "ON SITE",
                    style = MaterialTheme.typography.labelLarge, color = P.Brass
                )
                p.events.catalogue.filter { it.offsite == offsite }.chunked(2).forEach { pair ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        pair.forEach { e ->
                            Column(Modifier.weight(1f)) {
                                PushButton(
                                    e.title, if (offsite) P.PanelHigh else P.PanelFace,
                                    enabled = e.allowed(p)
                                ) { p.events.fireNow(e) }
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }

        Spacer(Modifier.height(8.dp))

        SectionPanel("STATION LOG", trailing = "${p.logbook.size} ENTRIES") {
            p.logbook.take(60).forEach { e ->
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        e.clock,
                        style = MaterialTheme.typography.bodySmall,
                        color = P.LegendDim,
                        modifier = Modifier.width(70.dp)
                    )
                    Text(
                        e.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = when (e.severity) {
                            3 -> P.LampRed
                            2 -> P.LampRed
                            1 -> P.LampAmber
                            else -> P.Legend
                        }
                    )
                }
            }
        }
    }
}
