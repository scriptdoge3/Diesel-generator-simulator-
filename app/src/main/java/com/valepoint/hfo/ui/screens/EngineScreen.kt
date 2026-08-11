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
import com.valepoint.hfo.sim.EngineState
import com.valepoint.hfo.sim.GovMode
import com.valepoint.hfo.sim.Spec
import com.valepoint.hfo.ui.SimViewModel
import com.valepoint.hfo.ui.theme.P
import com.valepoint.hfo.ui.widgets.CylinderBars
import com.valepoint.hfo.ui.widgets.EdgewiseMeter
import com.valepoint.hfo.ui.widgets.PanelSwitch
import com.valepoint.hfo.ui.widgets.PushButton
import com.valepoint.hfo.ui.widgets.RaiseLower
import com.valepoint.hfo.ui.widgets.Readout
import com.valepoint.hfo.ui.widgets.RoundGauge
import com.valepoint.hfo.ui.widgets.SectionPanel
import com.valepoint.hfo.ui.widgets.GaugeGrid
import com.valepoint.hfo.ui.widgets.GaugeSpec
import com.valepoint.hfo.ui.widgets.WrapRow
import com.valepoint.hfo.ui.widgets.Selector

@Composable
fun EngineScreen(vm: SimViewModel, modifier: Modifier = Modifier) {
    @Suppress("UNUSED_VARIABLE") val t = vm.tick
    val p = vm.plant

    Column(modifier.padding(8.dp)) {
        GaugeGrid(
            listOf(
                GaugeSpec(p.rpm, 0.0, 600.0, "RPM", "ENGINE SPEED", majorTicks = 6, redFrom = 550.0, decimals = 0),
                GaugeSpec(p.scavBar, 0.0, 3.0, "BAR", "SCAVENGE AIR", majorTicks = 6, decimals = 2),
                GaugeSpec(p.exhMeanC, 0.0, 700.0, "DEG C", "EXH MEAN", majorTicks = 7, redFrom = 480.0, decimals = 0),
                GaugeSpec(p.airBar, 0.0, 32.0, "BAR", "STARTING AIR", majorTicks = 8, greenBand = 18.0..30.0, decimals = 1),
            )
        )

        Spacer(Modifier.height(8.dp))

        SectionPanel("CYLINDER EXHAUST TEMPERATURES", trailing = "DEV ${(p.cylExhC.max() - p.cylExhC.min()).toInt()} C") {
            CylinderBars(
                values = p.cylExhC, labels = Spec.CYL_NAMES,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            )
        }

        Spacer(Modifier.height(8.dp))

        SectionPanel("COMBUSTION") {
            Row {
                Column(Modifier.weight(1f)) {
                    Readout("FUEL RACK", "${(p.fuelRack * 100).toInt()} %")
                    Readout("FUEL FLOW", "%.3f kg/s".format(p.fuelKgS))
                    Readout(
                        "AIR RATIO", "%.2f".format(p.lambda),
                        color = if (p.lambda < 1.5 && p.rpm > 100) P.LampRed else P.Legend
                    )
                    Readout("INDICATED", "%.2f MW".format(p.indicatedMw))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Readout("T/C SPEED", "${p.tcRpm.toInt()} rpm")
                    Readout("T/C OUTLET", "${p.tcOutC.toInt()} C")
                    Readout("CHARGE AIR", "${p.chargeAirTemp.toInt()} C")
                    Readout(
                        "SMOKE", "%.1f".format(p.smokeIndex),
                        color = if (p.smokeIndex > 0.8) P.LampAmber else P.Legend
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            EdgewiseMeter(
                p.fuelRack * 100, 0.0, 110.0, "RACK INDEX", "%",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp), redFrom = 100.0, decimals = 0
            )
        }

        Spacer(Modifier.height(8.dp))

        SectionPanel("GOVERNOR") {
            Selector(
                "MODE", listOf("DROOP", "ISOCH"),
                if (p.ctl.govMode == GovMode.DROOP) 0 else 1
            ) {
                p.ctl.govMode = if (it == 0) GovMode.DROOP else GovMode.ISOCH
                p.log("Governor set to ${p.ctl.govMode.label}.", 0)
            }
            RaiseLower(
                "SPEED / LOAD SETTING", "${"%.1f".format(p.ctl.speedRefRpm)} rpm",
                onRaise = { p.raiseSpeed(1.0) }, onLower = { p.raiseSpeed(-1.0) }
            )
            Readout(
                "RACK DEMAND",
                "${"%.0f".format((p.ctl.speedRefRpm / Spec.RATED_RPM - 1.0) * 100.0 / (p.ctl.droopPct / 100.0))} %"
            )
            Readout("DROOP", "${"%.1f".format(p.ctl.droopPct)} %")
            Readout("LOAD LIMIT", "${p.ctl.loadLimitPct.toInt()} %")
            WrapRow {
                PushButton("LIMIT -", P.PanelHigh) {
                    p.ctl.loadLimitPct = (p.ctl.loadLimitPct - 5).coerceAtLeast(20.0)
                }
                PushButton("LIMIT +", P.PanelHigh) {
                    p.ctl.loadLimitPct = (p.ctl.loadLimitPct + 5).coerceAtMost(110.0)
                }
                PushButton("DROOP -", P.PanelHigh) {
                    p.ctl.droopPct = (p.ctl.droopPct - 0.5).coerceAtLeast(2.0)
                }
                PushButton("DROOP +", P.PanelHigh) {
                    p.ctl.droopPct = (p.ctl.droopPct + 0.5).coerceAtMost(8.0)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        SectionPanel("STARTING") {
            WrapRow {
                PanelSwitch(
                    "TURNING\nGEAR", p.ctl.turningGear, onLabel = "IN", offLabel = "OUT",
                    enabled = p.rpm < 5.0
                ) { p.ctl.turningGear = it }
                PanelSwitch(
                    "INDICATOR\nCOCKS", p.ctl.indicatorCocks, onLabel = "OPEN", offLabel = "SHUT",
                    enabled = p.rpm < 5.0
                ) { p.ctl.indicatorCocks = it }
                PanelSwitch(
                    "AIR\nCOMPRESSOR", p.ctl.compressorAuto, onLabel = "AUTO", offLabel = "MAN"
                ) { p.ctl.compressorAuto = it }
                PanelSwitch(
                    "COMP\nMANUAL", p.ctl.compressorRun, onLabel = "RUN", offLabel = "OFF",
                    enabled = !p.ctl.compressorAuto
                ) { p.ctl.compressorRun = it }
            }
            Spacer(Modifier.height(6.dp))
            WrapRow {
                PushButton(
                    "START", P.LampGreen,
                    enabled = p.state == EngineState.STOPPED && p.startInterlocks().isEmpty()
                ) { p.requestStart() }
                PushButton(
                    "STOP", P.LampAmber,
                    enabled = p.state == EngineState.RUNNING || p.state == EngineState.FIRING
                ) { p.requestStop() }
                PushButton("EMERG STOP", P.LampRed, enabled = p.state != EngineState.STOPPED) {
                    p.emergencyStop()
                }
            }
            if (p.compressorFailed) {
                Text("AIR COMPRESSOR FAILED", style = MaterialTheme.typography.bodySmall, color = P.LampRed)
            }
        }

        Spacer(Modifier.height(8.dp))

        SectionPanel("MECHANICAL CONDITION") {
            Readout("BEARING WEAR", "${(p.bearingWear * 100).toInt()} %", color = wearColour(p.bearingWear))
            Readout("LINER WEAR", "${(p.linerWear * 100).toInt()} %", color = wearColour(p.linerWear))
            Readout("T/C FOULING", "${(p.turboFouling * 100).toInt()} %", color = wearColour(p.turboFouling))
            Readout("SCAVENGE DEPOSITS", "${(p.scavDeposits * 100).toInt()} %", color = wearColour(p.scavDeposits))
            Readout("CRANKSHAFT SHOCK", "${(p.crankshaftDamage * 100).toInt()} %", color = wearColour(p.crankshaftDamage))
            Readout(
                "OIL MIST", "%.2f".format(p.oilMist),
                color = if (p.oilMist > 1.2) P.LampRed else P.Legend
            )
            Readout(
                "WORST INJECTOR",
                "A${p.injectorCond.indices.minByOrNull { p.injectorCond[it] }!! + 1} at ${(p.injectorCond.min() * 100).toInt()} %",
                color = wearColour(1.0 - p.injectorCond.min())
            )
        }
    }
}

private fun wearColour(v: Double) = when {
    v > 0.6 -> P.LampRed
    v > 0.3 -> P.LampAmber
    else -> P.Legend
}
