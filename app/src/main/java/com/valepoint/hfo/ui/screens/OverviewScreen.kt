package com.valepoint.hfo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.valepoint.hfo.sim.EngineState
import com.valepoint.hfo.sim.Spec
import com.valepoint.hfo.ui.SimViewModel
import com.valepoint.hfo.ui.theme.P
import com.valepoint.hfo.ui.widgets.EdgewiseMeter
import com.valepoint.hfo.ui.widgets.IndicatorLamp
import com.valepoint.hfo.ui.widgets.PushButton
import com.valepoint.hfo.ui.widgets.Readout
import com.valepoint.hfo.ui.widgets.RoundGauge
import com.valepoint.hfo.ui.widgets.SectionPanel

@Composable
fun OverviewScreen(vm: SimViewModel, modifier: Modifier = Modifier) {
    @Suppress("UNUSED_VARIABLE") val t = vm.tick
    val p = vm.plant
    val flash = (System.currentTimeMillis() / 350) % 2 == 0L

    Column(modifier.padding(8.dp)) {
        if (p.wreckReason != null) {
            WreckBanner(p)
            Spacer(Modifier.height(8.dp))
        }

        val unack = p.ann.unacknowledgedCount()
        if (unack > 0) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(if (flash) P.LampRed else P.Danger)
                    .padding(6.dp)
            ) {
                val firstOut = p.ann.windows.values.firstOrNull { it.firstOut }
                Text(
                    if (firstOut != null) "FIRST OUT: ${firstOut.id.legend.replace('\n', ' ')}"
                    else "$unack ALARM(S) UNACCEPTED",
                    style = MaterialTheme.typography.labelLarge, color = P.LampWhite
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        Row(Modifier.horizontalScroll(rememberScrollState())) {
            RoundGauge(
                value = if (p.breakerClosed) p.genMw else 0.0,
                min = -2.0, max = 14.0, label = "MW", unit = "MEGAWATTS",
                diameter = 118.dp, majorTicks = 8, redFrom = 12.6, decimals = 2
            )
            Spacer(Modifier.width(6.dp))
            RoundGauge(
                value = p.grid.frequency,
                min = 45.0, max = 55.0, label = "Hz", unit = "SYSTEM FREQ",
                diameter = 118.dp, majorTicks = 5, greenBand = 49.5..50.5, decimals = 2
            )
            Spacer(Modifier.width(6.dp))
            RoundGauge(
                value = p.terminalKv, min = 0.0, max = 13.2, label = "kV", unit = "STATOR VOLTS",
                diameter = 118.dp, majorTicks = 6, redFrom = 12.1, decimals = 2
            )
            Spacer(Modifier.width(6.dp))
            RoundGauge(
                value = p.rpm, min = 0.0, max = 600.0, label = "RPM", unit = "ENGINE SPEED",
                diameter = 118.dp, majorTicks = 6, redFrom = 550.0, decimals = 0
            )
        }

        Spacer(Modifier.height(8.dp))

        SectionPanel("UNIT CONDITION", trailing = "%.0f HRS".format(p.runHours)) {
            Row {
                Column(Modifier.weight(1f)) {
                    Readout("FUEL RACK", "${(p.fuelRack * 100).toInt()} %")
                    Readout(
                        "JACKET WATER", "${p.htTempC.toInt()} C",
                        color = if (p.htTempC > Spec.HT_HIGH_TEMP) P.LampRed else P.Legend
                    )
                    Readout(
                        "LUB OIL", "%.1f bar".format(p.loBar),
                        color = if (p.loBar < Spec.LO_LOW_BAR && p.rpm > 100) P.LampRed else P.Legend
                    )
                    Readout("EXH MEAN", "${p.exhMeanC.toInt()} C")
                    Readout("SCAV AIR", "%.2f bar".format(p.scavBar))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Readout(
                        "FUEL VISC", "%.1f cSt".format(p.fuelViscCst),
                        color = if (p.fuelViscCst in 8.0..18.0) P.Trace else P.LampAmber
                    )
                    Readout("FUEL TEMP", "${p.fuelTempC.toInt()} C")
                    Readout("START AIR", "%.1f bar".format(p.airBar))
                    Readout("MVAR", "%.2f".format(p.genMvar))
                    Readout("STATOR", "${p.statorA.toInt()} A")
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                IndicatorLamp(p.state == EngineState.RUNNING, P.LampGreen, "RUN")
                IndicatorLamp(p.breakerClosed, P.LampRed, "BKR")
                IndicatorLamp(p.ctl.fieldSwitch, P.LampAmber, "FIELD")
                IndicatorLamp(p.ctl.fuelMode == com.valepoint.hfo.sim.FuelMode.HFO, P.LampAmber, "HFO")
                IndicatorLamp(p.ctl.turningGear, P.LampBlue, "T/GEAR")
                IndicatorLamp(p.smokeIndex > 0.8, P.LampRed, "SMOKE")
            }
        }

        Spacer(Modifier.height(8.dp))

        SectionPanel("ISLAND SYSTEM", trailing = "%.2f Hz".format(p.grid.frequency)) {
            EdgewiseMeter(
                value = p.grid.demandMw, min = 0.0, max = 50.0, label = "DEMAND", unit = "MW",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
            )
            Spacer(Modifier.height(4.dp))
            EdgewiseMeter(
                value = p.grid.onlineGenerationMw() + if (p.breakerClosed) p.genMw else 0.0,
                min = 0.0, max = 50.0, label = "GENERATION", unit = "MW",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
            )
            Spacer(Modifier.height(4.dp))
            p.grid.despatchTargetMw?.let {
                Readout(
                    "DESPATCH INSTRUCTION", "%.1f MW".format(it), color = P.LampAmber
                )
            }
            Readout("SPINNING RESERVE", "%.1f MW".format(p.grid.spinningReserveMw()))
        }

        Spacer(Modifier.height(8.dp))

        SectionPanel("UNIT CONTROL") {
            val blocks = p.startInterlocks()
            Row {
                PushButton(
                    "START", P.LampGreen,
                    enabled = p.state == EngineState.STOPPED && blocks.isEmpty(),
                    subLabel = "AIR"
                ) { p.requestStart() }
                Spacer(Modifier.width(8.dp))
                PushButton(
                    "STOP", P.LampAmber,
                    enabled = p.state == EngineState.RUNNING || p.state == EngineState.FIRING
                ) { p.requestStop() }
                Spacer(Modifier.width(8.dp))
                PushButton("EMERG\nSTOP", P.LampRed, enabled = p.state != EngineState.STOPPED) {
                    p.emergencyStop()
                }
            }
            if (blocks.isNotEmpty() && p.state == EngineState.STOPPED) {
                Spacer(Modifier.height(6.dp))
                Text("START INTERLOCKS", style = MaterialTheme.typography.bodySmall, color = P.LampAmber)
                blocks.take(6).forEach {
                    Text("  $it", style = MaterialTheme.typography.bodySmall, color = P.LampAmber)
                }
            }
        }
    }
}
