package com.valepoint.hfo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import com.valepoint.hfo.ui.SimViewModel
import com.valepoint.hfo.ui.screens.AlarmScreen
import com.valepoint.hfo.ui.screens.CoolingScreen
import com.valepoint.hfo.ui.screens.ElectricalScreen
import com.valepoint.hfo.ui.screens.EngineScreen
import com.valepoint.hfo.ui.screens.FuelScreen
import com.valepoint.hfo.ui.screens.OverviewScreen
import com.valepoint.hfo.ui.screens.SystemScreen
import com.valepoint.hfo.ui.theme.P
import com.valepoint.hfo.ui.theme.StationTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Draws each page into a bitmap so the panel layout can be inspected off device. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {

    private fun shoot(name: String, content: @Composable () -> Unit) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        val view = ComposeView(activity)
        view.setContent {
            StationTheme {
                Column(Modifier.background(P.Room)) { content() }
            }
        }
        activity.setContentView(view)
        shadowOf(Looper.getMainLooper()).idle()

        val wSpec = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
        val hSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(wSpec, hSpec)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        shadowOf(Looper.getMainLooper()).idle()
        view.measure(wSpec, hSpec)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)

        val w = view.measuredWidth.coerceAtLeast(1)
        val h = view.measuredHeight.coerceIn(1, 12000)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val dir = File(System.getProperty("shotDir") ?: "/tmp/shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("SHOT $name ${w}x$h")
        controller.close()
    }

    /** Drives the set up, onto the bars and out to load, the way an operator would. */
    private fun loadedVm(): SimViewModel {
        val vm = SimViewModel(autoRun = false)
        val p = vm.plant
        p.events.randomEventsEnabled = false
        p.ctl.htPump = true; p.ctl.ltPump = true; p.ctl.preheater = true
        p.ctl.settlingHeater = true; p.ctl.serviceHeater = true; p.ctl.purifier = true
        p.ctl.boosterA = true; p.ctl.circPump = true; p.ctl.preLube = true
        p.ctl.turningGear = false; p.ctl.indicatorCocks = false
        p.htTempC = 70.0; p.loTempC = 45.0; p.serviceTempC = 88.0
        p.fuelTempC = 130.0; p.steamBar = 6.5; p.airBar = 29.0
        p.ctl.fuelMode = com.valepoint.hfo.sim.FuelMode.HFO
        fun run(sec: Double) { var t = 0.0; while (t < sec) { p.step(0.05); t += 0.05 } }
        run(40.0)
        p.requestStart(); run(60.0)
        p.ctl.fieldSwitch = true; run(20.0)
        p.ctl.syncMode = com.valepoint.hfo.sim.SyncMode.CHECK
        var g = 0
        while (g++ < 40000 && !p.breakerClosed) {
            p.step(0.02)
            if (p.slipHz < 0.05) p.ctl.speedRefRpm += 0.02
            if (p.slipHz > 0.25) p.ctl.speedRefRpm -= 0.02
            if (p.canCloseBreaker().first) p.closeBreaker()
        }
        p.ctl.speedRefRpm = 508.0
        repeat(60) { p.raiseSpeed(0.2); p.grid.demandStepMw += 0.09; run(5.0) }
        // A couple of faults so the board is not blank.
        p.events.fireByKey("injector_fail")
        p.events.fireByKey("filter_block")
        run(240.0)
        return vm
    }

    @Test fun allPages() {
        val hot = loadedVm()
        shoot("8-overview-onload") { OverviewScreen(hot) }
        shoot("9-engine-onload") { EngineScreen(hot) }
        shoot("10-electrical-onload") { ElectricalScreen(hot) }
        shoot("1-overview") { OverviewScreen(SimViewModel(autoRun = false)) }
        shoot("2-engine") { EngineScreen(SimViewModel(autoRun = false)) }
        shoot("3-fuel") { FuelScreen(SimViewModel(autoRun = false)) }
        shoot("4-cooling") { CoolingScreen(SimViewModel(autoRun = false)) }
        shoot("5-electrical") { ElectricalScreen(SimViewModel(autoRun = false)) }
        shoot("6-system") { SystemScreen(SimViewModel(autoRun = false)) }
        shoot("7-alarms") { AlarmScreen(SimViewModel(autoRun = false)) }
    }
}
