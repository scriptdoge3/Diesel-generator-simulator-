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
import com.valepoint.hfo.sim.FilterSel
import com.valepoint.hfo.sim.Spec
import com.valepoint.hfo.ui.SimViewModel
import com.valepoint.hfo.ui.theme.P
import com.valepoint.hfo.ui.widgets.CoolingMimic
import com.valepoint.hfo.ui.widgets.EdgewiseMeter
import com.valepoint.hfo.ui.widgets.PanelSlider
import com.valepoint.hfo.ui.widgets.PanelSwitch
import com.valepoint.hfo.ui.widgets.PushButton
import com.valepoint.hfo.ui.widgets.Readout
import com.valepoint.hfo.ui.widgets.RoundGauge
import com.valepoint.hfo.ui.widgets.SectionPanel
import com.valepoint.hfo.ui.widgets.GaugeGrid
import com.valepoint.hfo.ui.widgets.GaugeSpec
import com.valepoint.hfo.ui.widgets.WrapRow
import com.valepoint.hfo.ui.widgets.Selector
import com.valepoint.hfo.ui.widgets.StripChart

@Composable
fun CoolingScreen(vm: SimViewModel, modifier: Modifier = Modifier) {
    @Suppress("UNUSED_VARIABLE") val t = vm.tick
    val p = vm.plant

    Column(modifier.padding(8.dp)) {
        SectionPanel("COOLING AND LUBRICATION") {
            CoolingMimic(p, Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(8.dp))

        GaugeGrid(
            listOf(
                GaugeSpec(p.loBar, 0.0, 8.0, "BAR", "LUB OIL PRESS", majorTicks = 8, greenBand = 3.5..6.0, decimals = 2),
                GaugeSpec(p.loTempC, 0.0, 100.0, "DEG C", "LUB OIL TEMP", majorTicks = 5, redFrom = 78.0, decimals = 0),
                GaugeSpec(
                    p.htTempC, 0.0, 120.0, "DEG C", "JACKET WATER", majorTicks = 6,
                    redFrom = Spec.HT_HIGH_TEMP, greenBand = 80.0..92.0, decimals = 0
                ),
                GaugeSpec(p.ltTempC, 0.0, 60.0, "DEG C", "LT WATER", majorTicks = 6, redFrom = 45.0, decimals = 0),
            )
        )

        Spacer(Modifier.height(8.dp))

        SectionPanel("LUBRICATING OIL") {
            WrapRow {
                PanelSwitch("PRE-LUBE\nPUMP", p.ctl.preLube) { p.ctl.preLube = it }
                PanelSwitch("STANDBY\nPUMP", p.ctl.loStandbyPump) { p.ctl.loStandbyPump = it }
                PanelSwitch(
                    "STANDBY\nAUTO", p.ctl.loStandbyAuto, onLabel = "AUTO", offLabel = "OFF"
                ) { p.ctl.loStandbyAuto = it }
                PanelSwitch("LO\nPURIFIER", p.ctl.loPurifier) { p.ctl.loPurifier = it }
            }
            Spacer(Modifier.height(6.dp))
            EdgewiseMeter(
                p.loFilterDp, 0.0, 3.0, "LO FILTER DIFF PRESS", "bar",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp), redFrom = 1.4, decimals = 2
            )
            Spacer(Modifier.height(4.dp))
            Selector(
                "DUTY FILTER", listOf("A", "B", "A+B"),
                when (p.ctl.loFilter) {
                    FilterSel.A -> 0; FilterSel.B -> 1; FilterSel.BOTH -> 2
                }
            ) {
                p.ctl.loFilter = when (it) {
                    0 -> FilterSel.A; 1 -> FilterSel.B; else -> FilterSel.BOTH
                }
            }
            Readout(
                "SUMP LEVEL", "${p.loLitres.toInt()} l",
                color = if (p.loLitres < Spec.LO_SUMP_LITRES * 0.6) P.LampAmber else P.Legend
            )
            Readout(
                "LEAK RATE", "%.1f l/min".format(p.loLeakLpm),
                color = if (p.loLeakLpm > 0.1) P.LampRed else P.Legend
            )
            Readout("CONTAMINATION", "${(p.loContamination * 100).toInt()} %")
            WrapRow {
                PushButton("CHANGE FILTER", P.PanelHigh) { p.changeLoFilter() }
                PushButton("TOP UP SUMP", P.PanelHigh) { p.topUpSump(600.0) }
                if (p.loLeakLpm > 0.1) {
                    PushButton("CLAMP LEAK", P.LampAmber) { p.events.stopLoLeak() }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("LO COOLER VALVE", style = MaterialTheme.typography.bodySmall, color = P.LegendDim)
            Selector("", listOf("AUTO", "MANUAL"), if (p.ctl.loCoolerAuto) 0 else 1) {
                p.ctl.loCoolerAuto = it == 0
            }
            if (!p.ctl.loCoolerAuto) {
                PanelSlider("COOLER VALVE", p.ctl.loCoolerManual.toFloat()) {
                    p.ctl.loCoolerManual = it.toDouble()
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        SectionPanel("JACKET WATER") {
            WrapRow {
                PanelSwitch("HT PUMP", p.ctl.htPump) { p.ctl.htPump = it }
                PanelSwitch("LT PUMP", p.ctl.ltPump) { p.ctl.ltPump = it }
                PanelSwitch(
                    "PREHEATER", p.ctl.preheater, onLabel = "ON", offLabel = "OFF"
                ) { p.ctl.preheater = it }
                PanelSwitch(
                    "THERMOSTAT", p.ctl.htThermostatAuto, onLabel = "AUTO", offLabel = "MAN"
                ) { p.ctl.htThermostatAuto = it }
            }
            Spacer(Modifier.height(4.dp))
            Readout("JACKET OUTLET", "${p.htTempC.toInt()} C")
            Readout("JACKET RETURN", "${p.htReturnC.toInt()} C")
            Readout(
                "CIRCULATING PRESS", "%.1f bar".format(p.htBar),
                color = if (p.htBar < 1.0 && p.rpm > 100) P.LampRed else P.Legend
            )
            Readout(
                "EXPANSION TANK", "${p.htExpansionPct.toInt()} %",
                color = if (p.htExpansionPct < 25) P.LampAmber else P.Legend
            )
            Readout(
                "LEAK RATE", "%.1f l/min".format(p.htLeakLpm),
                color = if (p.htLeakLpm > 0.1) P.LampRed else P.Legend
            )
            Readout("THERMOSTAT VALVE", "${(p.htValve * 100).toInt()} % TO COOLER")
            WrapRow {
                PushButton("TOP UP EXP TANK", P.PanelHigh) { p.topUpExpansionTank() }
                if (p.htLeakLpm > 0.1) {
                    PushButton("MAKE GOOD LEAK", P.LampAmber) { p.events.stopHtLeak() }
                }
            }
            Spacer(Modifier.height(6.dp))
            StripChart(
                vm.jacketHistory.toList(), 0.0, 120.0, "JACKET WATER TEMPERATURE",
                Modifier
                    .fillMaxWidth()
                    .height(80.dp), nominal = Spec.HT_NORMAL_TEMP, decimals = 1
            )
        }

        Spacer(Modifier.height(8.dp))

        SectionPanel("LOW TEMPERATURE CIRCUIT AND RADIATORS") {
            Selector("RADIATOR FANS", listOf("AUTO", "MANUAL"), if (p.ctl.radiatorAuto) 0 else 1) {
                p.ctl.radiatorAuto = it == 0
            }
            if (!p.ctl.radiatorAuto) {
                PanelSlider("FAN DEMAND", p.ctl.radiatorManual.toFloat()) {
                    p.ctl.radiatorManual = it.toDouble()
                }
            }
            Readout("FAN DEMAND", "${(p.radiatorDemand * 100).toInt()} %")
            Readout("LT WATER", "${p.ltTempC.toInt()} C")
            Readout(
                "CHARGE AIR", "${p.chargeAirTemp.toInt()} C",
                color = if (p.chargeAirTemp > 60) P.LampAmber else P.Legend
            )
            Readout("AMBIENT", "${p.ambientC.toInt()} C")
        }
    }
}
