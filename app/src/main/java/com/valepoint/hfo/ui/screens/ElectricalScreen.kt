package com.valepoint.hfo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.valepoint.hfo.sim.AvrMode
import com.valepoint.hfo.sim.GovMode
import com.valepoint.hfo.sim.Spec
import com.valepoint.hfo.sim.SyncMode
import com.valepoint.hfo.ui.SimViewModel
import com.valepoint.hfo.ui.theme.P
import com.valepoint.hfo.ui.widgets.EdgewiseMeter
import com.valepoint.hfo.ui.widgets.IndicatorLamp
import com.valepoint.hfo.ui.widgets.PanelSlider
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
import com.valepoint.hfo.ui.widgets.SyncLamps
import com.valepoint.hfo.ui.widgets.Synchroscope
import kotlin.math.abs

@Composable
fun ElectricalScreen(vm: SimViewModel, modifier: Modifier = Modifier) {
    @Suppress("UNUSED_VARIABLE") val t = vm.tick
    val p = vm.plant
    val busLive = p.grid.othersOnline() && p.grid.frequency > 20.0
    val (canClose, why) = p.canCloseBreaker()

    Column(modifier.padding(8.dp)) {
        SectionPanel("SYNCHRONISING", trailing = if (p.breakerClosed) "ON BARS" else "OFF BARS") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Synchroscope(
                    angleDeg = p.syncAngleDeg,
                    slipHz = p.slipHz,
                    live = busLive && !p.breakerClosed,
                    diameter = 138.dp,
                    // Once the machine is on the bars the instrument is out of
                    // circuit; saying "bus dead" there would be a lie.
                    idleText = if (p.breakerClosed) "ON BARS" else "BUS DEAD"
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Readout("INCOMING", "%.2f kV".format(p.terminalKv))
                    Readout("RUNNING (BUS)", "%.2f kV".format(p.grid.busKv))
                    Readout(
                        "INCOMING FREQ", "%.2f Hz".format(if (p.breakerClosed) p.grid.frequency else p.rpm / 10.0)
                    )
                    Readout("BUS FREQ", "%.2f Hz".format(p.grid.frequency))
                    Readout(
                        "PHASE ANGLE",
                        if (busLive && !p.breakerClosed) "${p.syncAngleDeg.toInt()} deg" else "--",
                        color = if (abs(((p.syncAngleDeg + 180) % 360) - 180) < 12) P.LampGreen else P.Legend
                    )
                    Spacer(Modifier.height(4.dp))
                    SyncLamps(
                        p.syncAngleDeg, busLive && !p.breakerClosed,
                        Modifier
                            .fillMaxWidth()
                            .height(30.dp)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Selector(
                "SYNCHRONISING SWITCH", listOf("OFF", "CHECK SYNC", "MANUAL"),
                when (p.ctl.syncMode) {
                    SyncMode.OFF -> 0; SyncMode.CHECK -> 1; SyncMode.MANUAL -> 2
                }
            ) {
                p.ctl.syncMode = when (it) {
                    0 -> SyncMode.OFF; 1 -> SyncMode.CHECK; else -> SyncMode.MANUAL
                }
            }
            Text(
                if (p.breakerClosed) "BREAKER CLOSED" else why,
                style = MaterialTheme.typography.bodySmall,
                color = if (canClose || p.breakerClosed) P.LampGreen else P.LampAmber
            )
            if (p.ctl.syncMode == SyncMode.MANUAL && !p.breakerClosed) {
                Text(
                    "MANUAL: the check synchroniser is bypassed. Closing out of step " +
                        "will shock the crankshaft and coupling.",
                    style = MaterialTheme.typography.bodySmall, color = P.LampRed
                )
            }
            Spacer(Modifier.height(6.dp))
            WrapRow {
                PushButton(
                    "CLOSE BREAKER", P.LampRed,
                    enabled = !p.breakerClosed && p.state == com.valepoint.hfo.sim.EngineState.RUNNING
                ) { p.closeBreaker() }
                PushButton("OPEN BREAKER", P.LampGreen, enabled = p.breakerClosed) {
                    p.openBreaker("operator")
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        GaugeGrid(
            listOf(
                GaugeSpec(if (p.breakerClosed) p.genMw else 0.0, -2.0, 14.0, "MW", "REAL POWER", majorTicks = 8, redFrom = 12.6, decimals = 2),
                GaugeSpec(if (p.breakerClosed) p.genMvar else 0.0, -6.0, 10.0, "MVAR", "REACTIVE", majorTicks = 8, decimals = 2),
                GaugeSpec(p.statorA, 0.0, 1200.0, "AMPS", "STATOR CURRENT", majorTicks = 6, redFrom = Spec.RATED_AMPS, decimals = 0),
                GaugeSpec(p.fieldA, 0.0, 1200.0, "AMPS", "FIELD CURRENT", majorTicks = 6, decimals = 0),
            )
        )

        Spacer(Modifier.height(8.dp))

        SectionPanel("EXCITATION") {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                PanelSwitch(
                    "FIELD\nSWITCH", p.ctl.fieldSwitch, onLabel = "ON", offLabel = "OFF"
                ) {
                    p.ctl.fieldSwitch = it
                    p.log(if (it) "Field switch closed." else "Field switch opened.", if (p.breakerClosed && !it) 2 else 0)
                }
                Column(Modifier.weight(1f)) {
                    Selector(
                        "AVR", listOf("AUTO", "MANUAL"),
                        if (p.ctl.avrMode == AvrMode.AUTO) 0 else 1
                    ) {
                        p.ctl.avrMode = if (it == 0) AvrMode.AUTO else AvrMode.MANUAL
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IndicatorLamp(p.poleSlipping, P.LampRed, "POLE SLIP")
                        Spacer(Modifier.width(10.dp))
                        IndicatorLamp(p.terminalKv > Spec.RATED_KV * 1.05, P.LampAmber, "OVER V")
                    }
                }
            }
            if (p.ctl.avrMode == AvrMode.AUTO) {
                RaiseLower(
                    "VOLTAGE SETTING", "%.2f kV".format(p.ctl.voltageSetpointKv),
                    onRaise = { p.raiseVolts(0.05) }, onLower = { p.raiseVolts(-0.05) }
                )
            } else {
                PanelSlider("FIELD RHEOSTAT", p.ctl.fieldRheostat.toFloat()) {
                    p.ctl.fieldRheostat = it.toDouble()
                }
            }
            Readout("LOAD ANGLE", "${p.loadAngleDeg.toInt()} deg", color = if (abs(p.loadAngleDeg) > 60) P.LampAmber else P.Legend)
            Readout(
                "POWER FACTOR",
                if (p.breakerClosed && (abs(p.genMw) + abs(p.genMvar)) > 0.05)
                    "%.3f".format(p.genMw / kotlin.math.sqrt(p.genMw * p.genMw + p.genMvar * p.genMvar))
                else "--"
            )
            Readout(
                "STATOR TEMP", "${p.statorTempC.toInt()} C",
                color = if (p.statorTempC > 110) P.LampAmber else P.Legend
            )
        }

        Spacer(Modifier.height(8.dp))

        SectionPanel("LOAD CONTROL", trailing = p.ctl.govMode.label) {
            Selector("GOVERNOR", listOf("DROOP", "ISOCH"), if (p.ctl.govMode == GovMode.DROOP) 0 else 1) {
                p.ctl.govMode = if (it == 0) GovMode.DROOP else GovMode.ISOCH
            }
            RaiseLower(
                "SPEED / LOAD SETTING",
                "%.1f rpm".format(p.ctl.speedRefRpm),
                onRaise = { p.raiseSpeed(1.0) }, onLower = { p.raiseSpeed(-1.0) }
            )
            WrapRow {
                PushButton("- 5", P.PanelHigh) { p.raiseSpeed(-5.0) }
                PushButton("- 1", P.PanelHigh) { p.raiseSpeed(-1.0) }
                PushButton("+ 1", P.PanelHigh) { p.raiseSpeed(1.0) }
                PushButton("+ 5", P.PanelHigh) { p.raiseSpeed(5.0) }
            }
            Spacer(Modifier.height(6.dp))
            EdgewiseMeter(
                if (p.breakerClosed) p.genMw else 0.0, 0.0, 13.0, "UNIT 3 OUTPUT", "MW",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp), redFrom = 12.6, decimals = 2
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "On droop the speed setting decides how much load the set takes. " +
                    "On isochronous control the set holds system frequency on its own.",
                style = MaterialTheme.typography.bodySmall, color = P.LegendDim
            )
        }

        Spacer(Modifier.height(8.dp))

        SectionPanel("SWITCHBOARD") {
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                IndicatorLamp(p.breakerClosed, P.LampRed, "CLOSED")
                IndicatorLamp(!p.breakerClosed, P.LampGreen, "OPEN")
                IndicatorLamp(busLive, P.LampWhite, "BUS LIVE")
                IndicatorLamp(p.grid.blackout, P.LampAmber, "BLACKOUT")
            }
            if (p.grid.blackout) {
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                ) {
                    Text(
                        "ISLAND BLACKOUT. With the busbar dead the check synchroniser is " +
                            "bypassed: run the set up to speed, excite it and close onto the " +
                            "dead bar to black start the island.",
                        style = MaterialTheme.typography.bodySmall, color = P.LampAmber
                    )
                }
            }
        }
    }
}
