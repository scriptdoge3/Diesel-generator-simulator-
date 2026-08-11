package com.valepoint.hfo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.valepoint.hfo.sim.EngineState
import com.valepoint.hfo.sim.LampState
import com.valepoint.hfo.sim.Plant
import com.valepoint.hfo.sim.Spec
import com.valepoint.hfo.sim.formatClock
import com.valepoint.hfo.ui.theme.P

fun stateColour(s: EngineState): Color = when (s) {
    EngineState.RUNNING -> P.LampGreen
    EngineState.FIRING, EngineState.CRANKING -> P.LampAmber
    EngineState.STOPPING -> P.LampAmber
    EngineState.TRIPPED, EngineState.WRECKED -> P.LampRed
    EngineState.STOPPED -> P.LegendDim
}

fun lampColour(id: com.valepoint.hfo.sim.AlarmId): Color =
    if (id.trip) P.LampRed else P.LampAmber

fun lampLit(state: LampState, fastFlash: Boolean, slowFlash: Boolean, lampTest: Boolean): Boolean =
    when {
        lampTest -> true
        state == LampState.UNACK -> fastFlash
        state == LampState.ACCEPTED -> true
        state == LampState.RINGBACK -> slowFlash
        else -> false
    }

/** The strip that runs across the top of every page: identity, clock, state. */
@Composable
fun TopStrip(p: Plant, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Color(0xFF0B0D0C))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text("${Spec.STATION}  ${Spec.UNIT}", style = MaterialTheme.typography.labelLarge, color = P.Brass)
            Text(
                "${Spec.RATED_MW} MW  ${Spec.RATED_KV} kV  ${Spec.RATED_RPM.toInt()} rpm",
                style = MaterialTheme.typography.bodySmall, color = P.LegendDim
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatClock(p.clockSec), style = MaterialTheme.typography.titleMedium, color = P.Legend)
            Text(
                p.state.name,
                style = MaterialTheme.typography.bodySmall,
                color = stateColour(p.state)
            )
        }
    }
}

/** Red banner shown when the machine has been destroyed. */
@Composable
fun WreckBanner(p: Plant, modifier: Modifier = Modifier) {
    val reason = p.wreckReason ?: return
    Box(
        modifier
            .fillMaxWidth()
            .background(P.Danger)
            .padding(10.dp)
    ) {
        Column {
            Text("UNIT DESTROYED", style = MaterialTheme.typography.titleMedium, color = P.LampWhite)
            Text(reason, style = MaterialTheme.typography.bodyMedium, color = P.LampWhite)
            Text(
                "Take a new watch from the LOG page.",
                style = MaterialTheme.typography.bodySmall, color = P.LampWhite, textAlign = TextAlign.Start
            )
        }
    }
}
