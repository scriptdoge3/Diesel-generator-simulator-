package com.valepoint.hfo.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.valepoint.hfo.ui.theme.P
import com.valepoint.hfo.ui.widgets.SectionPanel

private class Note(val title: String, val body: String)

private val notes = listOf(
    Note(
        "STANDING ORDERS",
        "Unit 3 is a nine cylinder medium speed set of 1974, burning 380 cSt residual " +
            "fuel and feeding the island 11 kV system. There is no interconnector. " +
            "Nothing on this panel is scripted: pressures come from pumps, temperatures " +
            "from heat balances and torque from burnt fuel, so symptoms follow causes."
    ),
    Note(
        "PREPARING A COLD UNIT",
        "1  Start the jacket water pump and the LT pump.\n" +
            "2  Switch on the jacket water preheater and wait for 60 C or more. This " +
            "takes an hour of plant time, so use the time multiplier on the LOG page.\n" +
            "3  Start the pre-lube pump and let it run for at least half a minute.\n" +
            "4  Put the settling and service tank heaters on, and start the purifier " +
            "once the feed is near 98 C.\n" +
            "5  Start a booster pump so the fuel circulates through the final heater.\n" +
            "6  Select distillate fuel for a cold start unless the engine is already hot.\n" +
            "7  Take the turning gear out and shut the indicator cocks.\n" +
            "8  Check the starting air receivers are above 15 bar."
    ),
    Note(
        "STARTING",
        "Press START. Air is admitted to the cylinders, the engine turns, and fuel is " +
            "admitted around 90 rpm. If it does not fire within about fourteen seconds " +
            "the attempt is abandoned and the air is wasted. Cold jacket water, thick " +
            "fuel or a low supply pressure are the usual reasons for a failed start."
    ),
    Note(
        "CHANGING TO RESIDUAL FUEL",
        "Only once the engine is warm and the jacket water is above 60 C. Watch the " +
            "viscometer: the controller heats the oil to hold roughly 13 cSt at the " +
            "injectors. Too thick and the fuel will not atomise, the exhaust temperatures " +
            "climb and the filter blocks. Too thin and the injection pumps lose their " +
            "lubrication."
    ),
    Note(
        "SYNCHRONISING",
        "Run up to 500 rpm, close the field switch and set 11 kV. Put the " +
            "synchronising switch to CHECK SYNC, then trim the speed setting until the " +
            "synchroscope creeps slowly clockwise, in the FAST direction. Close the " +
            "breaker a little before the pointer reaches twelve o'clock. In MANUAL the " +
            "check synchroniser is bypassed and a bad closure will shock the crankshaft " +
            "hard enough to write the machine off."
    ),
    Note(
        "TAKING LOAD",
        "On droop, raising the speed setting takes load and lowering it sheds load. " +
            "Load must be taken slowly: the turbocharger needs time to spin up, and the " +
            "fuel limiter will hold the rack back until the scavenge air catches up. " +
            "Ramming the setting up produces black smoke, high exhaust temperatures and " +
            "nothing much extra on the megawatt meter."
    ),
    Note(
        "WATCHKEEPING",
        "Keep the mean exhaust temperature below about 480 C and the deviation between " +
            "cylinders below 50 C. A single cold cylinder is a failed injector. Watch the " +
            "fuel filter differential pressure, especially after a poor bunker delivery, " +
            "and backflush before it starves the injection pumps. Keep the lubricating " +
            "oil above 3.5 bar and 65 C, and the jacket water near 88 C."
    ),
    Note(
        "WHEN THINGS GO WRONG",
        "Crankcase oil mist means a bearing is running hot. Slow the engine down, do not " +
            "open the crankcase doors, and stop it. A scavenge fire shows as a high " +
            "exhaust temperature with deposits, and will destroy the pistons if it is " +
            "allowed to run. Reverse power means the engine has stopped driving and the " +
            "system is now motoring the alternator: open the breaker."
    ),
    Note(
        "BLACK START",
        "If everything on the island trips the busbar goes dead. With a dead bar there " +
            "is nothing to synchronise to, so the check synchroniser is bypassed. Run " +
            "the set up, excite it, close the breaker onto the dead bar and the governor " +
            "goes to isochronous control. Your set then holds the frequency of the whole " +
            "island on its own, so pick load up gently."
    ),
    Note(
        "STOPPING",
        "Shed load with the speed setting, open the breaker below about half a megawatt, " +
            "change over to distillate fuel and run for a few minutes to flush the " +
            "injection system, then stop. Residual fuel left standing in a hot line sets " +
            "hard and makes the next start very difficult. Keep the pre-lube running and " +
            "put the turning gear in."
    ),
)

@Composable
fun NotesScreen(modifier: Modifier = Modifier) {
    Column(modifier.padding(8.dp)) {
        notes.forEach { n ->
            SectionPanel(n.title) {
                Text(n.body, style = MaterialTheme.typography.bodyMedium, color = P.Legend)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
