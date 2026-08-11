package com.valepoint.hfo

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
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
import com.valepoint.hfo.ui.theme.StationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Composes every page so that a layout or draw fault fails the build, not the device. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ScreenRenderTest {

    @get:Rule
    val rule = createComposeRule()

    private fun render(content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent {
            StationTheme {
                Column(Modifier.width(400.dp)) { content() }
            }
        }
        rule.waitForIdle()
    }

    @Test fun overview() { val vm = SimViewModel(autoRun = false); render { OverviewScreen(vm) } }
    @Test fun engine() { val vm = SimViewModel(autoRun = false); render { EngineScreen(vm) } }
    @Test fun fuel() { val vm = SimViewModel(autoRun = false); render { FuelScreen(vm) } }
    @Test fun cooling() { val vm = SimViewModel(autoRun = false); render { CoolingScreen(vm) } }
    @Test fun electrical() { val vm = SimViewModel(autoRun = false); render { ElectricalScreen(vm) } }
    @Test fun system() { val vm = SimViewModel(autoRun = false); render { SystemScreen(vm) } }
    @Test fun alarms() { val vm = SimViewModel(autoRun = false); render { AlarmScreen(vm) } }
    @Test fun log() { val vm = SimViewModel(autoRun = false); render { LogScreen(vm) } }
    @Test fun notes() { render { NotesScreen() } }
}
