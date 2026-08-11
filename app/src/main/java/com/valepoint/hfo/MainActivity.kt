package com.valepoint.hfo

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.valepoint.hfo.ui.SimViewModel
import com.valepoint.hfo.ui.screens.AlarmScreen
import com.valepoint.hfo.ui.screens.CoolingScreen
import com.valepoint.hfo.ui.screens.ElectricalScreen
import com.valepoint.hfo.ui.screens.EngineScreen
import com.valepoint.hfo.ui.screens.FuelScreen
import com.valepoint.hfo.ui.screens.LogScreen
import com.valepoint.hfo.ui.screens.NotesScreen
import com.valepoint.hfo.ui.screens.OverviewScreen
import com.valepoint.hfo.ui.screens.SystemScreen
import com.valepoint.hfo.ui.screens.TopStrip
import com.valepoint.hfo.ui.theme.P
import com.valepoint.hfo.ui.theme.StationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            StationTheme {
                StationApp()
            }
        }
    }
}

private val TABS = listOf(
    "UNIT", "ENGINE", "FUEL OIL", "COOLING", "ELECTRICAL", "SYSTEM", "ALARMS", "LOG", "NOTES"
)

@Composable
fun StationApp(vm: SimViewModel = viewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val scrollStates = List(TABS.size) { rememberScrollState() }

    // Reading the tick here keeps the header strip live.
    @Suppress("UNUSED_VARIABLE") val t = vm.tick

    Scaffold(
        containerColor = P.Room,
        topBar = {
            Column {
                TopStrip(vm.plant)
                ScrollableTabRow(
                    selectedTabIndex = tab,
                    containerColor = Color(0xFF161A17),
                    contentColor = P.Brass,
                    edgePadding = 4.dp,
                ) {
                    TABS.forEachIndexed { i, name ->
                        Tab(
                            selected = tab == i,
                            onClick = { tab = i },
                            text = {
                                Text(
                                    name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (tab == i) P.Brass else P.LegendDim
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .background(P.Room)
                .verticalScroll(scrollStates[tab])
        ) {
            when (tab) {
                0 -> OverviewScreen(vm)
                1 -> EngineScreen(vm)
                2 -> FuelScreen(vm)
                3 -> CoolingScreen(vm)
                4 -> ElectricalScreen(vm)
                5 -> SystemScreen(vm)
                6 -> AlarmScreen(vm)
                7 -> LogScreen(vm)
                else -> NotesScreen()
            }
        }
    }
}
