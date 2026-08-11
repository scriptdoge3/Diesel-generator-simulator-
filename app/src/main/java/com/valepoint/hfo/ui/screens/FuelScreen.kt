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
import com.valepoint.hfo.sim.FuelMode
import com.valepoint.hfo.sim.Spec
import com.valepoint.hfo.sim.tempForViscosity
import com.valepoint.hfo.ui.SimViewModel
import com.valepoint.hfo.ui.theme.P
import com.valepoint.hfo.ui.widgets.EdgewiseMeter
import com.valepoint.hfo.ui.widgets.FuelMimic
import com.valepoint.hfo.ui.widgets.PanelSlider
import com.valepoint.hfo.ui.widgets.PanelSwitch
import com.valepoint.hfo.ui.widgets.PushButton
import com.valepoint.hfo.ui.widgets.Readout
import com.valepoint.hfo.ui.widgets.RoundGauge
import com.valepoint.hfo.ui.widgets.SectionPanel
import com.valepoint.hfo.ui.widgets.WrapRow
import com.valepoint.hfo.ui.widgets.Selector

@Composable
fun FuelScreen(vm: SimViewModel, modifier: Modifier = Modifier) {
    @Suppress("UNUSED_VARIABLE") val t = vm.tick
    val p = vm.plant

    Column(modifier.padding(8.dp)) {
        SectionPanel("HEAVY FUEL OIL SYSTEM", trailing = p.ctl.fuelMode.label) {
            FuelMimic(p, Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(8.dp))

        SectionPanel("VISCOSITY CONTROL", trailing = "TARGET ${"%.0f".format(p.ctl.viscoSetpoint)} cSt") {
            Row {
                RoundGauge(
                    p.fuelViscCst.coerceAtMost(60.0), 0.0, 60.0, "cSt", "VISCOSITY",
                    diameter = 112.dp, majorTicks = 6, greenBand = 10.0..16.0, decimals = 1
                )
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Readout("FUEL TEMP", "${p.fuelTempC.toInt()} C")
                    Readout(
                        "REQUIRED TEMP",
                        "${
                            tempForViscosity(
                                if (p.ctl.fuelMode == FuelMode.HFO) p.bunkerGradeCst else Spec.MDO_GRADE_CST50,
                                p.ctl.viscoSetpoint
                            ).toInt()
                        } C"
                    )
                    Readout("HEATER OUTPUT", "${(p.heaterOutput * 100).toInt()} %")
                    Readout(
                        "STEAM", "%.1f bar".format(p.steamBar),
                        color = if (p.steamBar < 3.0) P.LampAmber else P.Legend
                    )
                    Readout("GRADE", "${p.bunkerGradeCst.toInt()} cSt @ 50 C")
                    Spacer(Modifier.height(4.dp))
                    Selector("CONTROL", listOf("AUTO", "MANUAL"), if (p.ctl.viscoAuto) 0 else 1) {
                        p.ctl.viscoAuto = it == 0
                    }
                }
            }
            if (!p.ctl.viscoAuto) {
                PanelSlider("HEATER VALVE", p.ctl.heaterManual.toFloat()) {
                    p.ctl.heaterManual = it.toDouble()
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        SectionPanel("FUEL CHANGEOVER") {
            Selector(
                "SELECTED FUEL", listOf("DISTILLATE (MDO)", "RESIDUAL (HFO)"),
                if (p.ctl.fuelMode == FuelMode.MDO) 0 else 1
            ) {
                val newMode = if (it == 0) FuelMode.MDO else FuelMode.HFO
                if (newMode != p.ctl.fuelMode) {
                    p.ctl.fuelMode = newMode
                    p.log("Changed over to ${newMode.label} fuel.", 0)
                    if (newMode == FuelMode.HFO && p.htTempC < Spec.HT_PREHEAT_MIN) {
                        p.log("Engine is cold for residual fuel. Expect poor combustion.", 1)
                    }
                }
            }
            Text(
                "Change over slowly: a sudden temperature change will seize an injection pump. " +
                    "Run on distillate for starting and before a planned stop.",
                style = MaterialTheme.typography.bodySmall, color = P.LegendDim
            )
        }

        Spacer(Modifier.height(8.dp))

        SectionPanel("PUMPS AND TREATMENT") {
            WrapRow {
                PanelSwitch("TRANSFER\nPUMP", p.ctl.transferPump) { p.ctl.transferPump = it }
                PanelSwitch("BOOSTER\nA", p.ctl.boosterA) { p.ctl.boosterA = it }
                PanelSwitch("BOOSTER\nB", p.ctl.boosterB) { p.ctl.boosterB = it }
                PanelSwitch("CIRC\nPUMP", p.ctl.circPump) { p.ctl.circPump = it }
            }
            Spacer(Modifier.height(6.dp))
            WrapRow {
                PanelSwitch("SETTLING\nHEATER", p.ctl.settlingHeater, onLabel = "ON", offLabel = "OFF") {
                    p.ctl.settlingHeater = it
                }
                PanelSwitch("SERVICE\nHEATER", p.ctl.serviceHeater, onLabel = "ON", offLabel = "OFF") {
                    p.ctl.serviceHeater = it
                }
                PanelSwitch(
                    "PURIFIER", p.ctl.purifier, onLabel = "ON", offLabel = "OFF",
                    enabled = !p.purifierTripped
                ) { p.ctl.purifier = it }
                Column {
                    if (p.purifierTripped) {
                        PushButton("RESET\nPURIF", P.LampAmber) { p.resetPurifier() }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            PanelSlider(
                "PURIFIER FEED RATE  ${(p.ctl.purifierFeed * 100).toInt()} %",
                p.ctl.purifierFeed.toFloat(), range = 0.2f..1f
            ) { p.ctl.purifierFeed = it.toDouble().coerceIn(0.2, 1.0) }
            Readout(
                "FEED TEMPERATURE", "${p.purifierFeedTempC.toInt()} C",
                color = if (p.purifierFeedTempC < 90 && p.purifierRunning) P.LampAmber else P.Legend
            )
            Readout("SEPARATED SLUDGE", "${p.sludgeLitres.toInt()} l")
        }

        Spacer(Modifier.height(8.dp))

        SectionPanel("FILTRATION AND SUPPLY") {
            EdgewiseMeter(
                p.fuelFilterDp, 0.0, 3.0, "FILTER DIFF PRESS", "bar",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp), redFrom = 1.2, decimals = 2
            )
            Spacer(Modifier.height(4.dp))
            EdgewiseMeter(
                p.fuelBar, 0.0, 12.0, "SUPPLY PRESSURE", "bar",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp), decimals = 1
            )
            Spacer(Modifier.height(6.dp))
            Selector(
                "DUTY FILTER", listOf("A", "B", "A+B"),
                when (p.ctl.fuelFilter) {
                    FilterSel.A -> 0; FilterSel.B -> 1; FilterSel.BOTH -> 2
                }
            ) {
                p.ctl.fuelFilter = when (it) {
                    0 -> FilterSel.A; 1 -> FilterSel.B; else -> FilterSel.BOTH
                }
            }
            WrapRow {
                PushButton("BACKFLUSH FILTER", P.PanelHigh) { p.backflushFuelFilter() }
            }
            Spacer(Modifier.height(4.dp))
            Readout("SERVICE TANK", "%.1f m3".format(p.serviceM3))
            Readout("SETTLING TANK", "%.1f m3".format(p.settlingM3))
            Readout("MDO TANK", "%.1f m3".format(p.mdoM3))
            Readout("CONSUMED THIS WATCH", "%.2f t".format(p.fuelConsumedT))
            Readout(
                "WATER IN SERVICE TANK", "%.2f %%".format(p.waterPct),
                color = if (p.waterPct > 0.5) P.LampAmber else P.Legend
            )
            Readout(
                "CAT FINES", "${p.catFinesPpm.toInt()} ppm",
                color = if (p.catFinesPpm > 40) P.LampRed else P.Legend
            )
        }
    }
}
