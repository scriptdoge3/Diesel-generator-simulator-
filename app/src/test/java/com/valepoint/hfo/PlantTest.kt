package com.valepoint.hfo

import com.valepoint.hfo.sim.AlarmId
import com.valepoint.hfo.sim.EngineState
import com.valepoint.hfo.sim.FuelMode
import com.valepoint.hfo.sim.GovMode
import com.valepoint.hfo.sim.Plant
import com.valepoint.hfo.sim.Spec
import com.valepoint.hfo.sim.SyncMode
import com.valepoint.hfo.sim.viscosityAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Headless exercise of the plant model. These are the checks that the physics
 * actually hangs together: a prepared unit starts, synchronises, takes load,
 * and the protections act when they should.
 */
class PlantTest {

    private fun run(p: Plant, seconds: Double, dt: Double = 0.05) {
        var t = 0.0
        while (t < seconds) {
            p.step(dt)
            t += dt
        }
    }

    /** Auxiliaries running, engine warm, fuel hot: the state a previous watch leaves. */
    private fun prepared(events: Boolean = false): Plant {
        val p = Plant(12345L)
        p.events.randomEventsEnabled = events
        p.ctl.htPump = true
        p.ctl.ltPump = true
        p.ctl.preheater = true
        p.ctl.settlingHeater = true
        p.ctl.serviceHeater = true
        p.ctl.boosterA = true
        p.ctl.circPump = true
        p.ctl.preLube = true
        p.ctl.purifier = true
        p.htTempC = 70.0
        p.loTempC = 45.0
        p.serviceTempC = 88.0
        p.fuelTempC = 125.0
        p.steamBar = 6.5
        p.airBar = 29.0
        p.ctl.fuelMode = FuelMode.HFO
        p.ctl.turningGear = false
        p.ctl.indicatorCocks = false
        run(p, 40.0) // pre-lube and let the fuel line settle
        return p
    }

    @Test
    fun viscosityChartIsSane() {
        // A 380 cSt residual fuel needs roughly 125-135 degC to reach 13 cSt.
        assertEquals(380.0, viscosityAt(380.0, 50.0), 5.0)
        val at130 = viscosityAt(380.0, 130.0)
        assertTrue("380 cSt fuel at 130 C was $at130", at130 in 8.0..20.0)
        assertTrue(viscosityAt(380.0, 40.0) > 380.0)
    }

    @Test
    fun coldUnitRefusesToStart() {
        val p = Plant(1L)
        assertTrue(p.startInterlocks().isNotEmpty())
        assertFalse(p.requestStart())
        assertEquals(EngineState.STOPPED, p.state)
    }

    @Test
    fun preparedUnitStartsAndRunsUp() {
        val p = prepared()
        assertEquals("interlocks: ${p.startInterlocks()}", 0, p.startInterlocks().size)
        assertTrue(p.requestStart())
        run(p, 45.0)
        assertEquals(EngineState.RUNNING, p.state)
        assertTrue("speed ${p.rpm}", abs(p.rpm - Spec.RATED_RPM) < 25.0)
        assertTrue("lub oil ${p.loBar}", p.loBar > 3.0)
        assertTrue("start air used: ${p.airBar}", p.airBar < 29.0)
    }

    @Test
    fun synchronisesAndTakesLoad() {
        val p = prepared()
        p.requestStart()
        run(p, 60.0)
        p.ctl.fieldSwitch = true
        run(p, 20.0)
        assertTrue("terminal volts ${p.terminalKv}", p.terminalKv > Spec.RATED_KV * 0.95)

        p.ctl.syncMode = SyncMode.CHECK
        // Trim the speed setting until the machine creeps slowly fast, then close
        // on the next pass through zero.
        var closed = false
        var guard = 0
        while (!closed && guard++ < 40000) {
            p.step(0.02)
            if (p.slipHz < 0.05) p.ctl.speedRefRpm += 0.02
            if (p.slipHz > 0.25) p.ctl.speedRefRpm -= 0.02
            val (ok, _) = p.canCloseBreaker()
            if (ok) {
                p.closeBreaker()
                closed = p.breakerClosed
            }
        }
        assertTrue("never got a permissive to close", closed)
        assertFalse("closure damaged the machine", p.state == EngineState.WRECKED)
        assertTrue("crankshaft shock ${p.crankshaftDamage}", p.crankshaftDamage < 0.05)

        // Take load on droop by raising the speed setting.
        p.ctl.govMode = GovMode.DROOP
        repeat(60) {
            p.raiseSpeed(0.2)
            run(p, 6.0)
        }
        // Droop is a shared characteristic: as this set takes load the other
        // machines back off and the frequency rises, so the set settles part way.
        assertTrue("output ${p.genMw} MW", p.genMw > 4.5)
        assertTrue("output ${p.genMw} MW", p.genMw < Spec.RATED_MW * 1.15)
        assertTrue("exhaust ${p.exhMeanC} C", p.exhMeanC in 200.0..560.0)
        assertTrue("scavenge ${p.scavBar} bar", p.scavBar > 0.8)
        assertTrue("air ratio ${p.lambda}", p.lambda > 1.4)
        assertTrue("frequency ${p.grid.frequency}", abs(p.grid.frequency - 50.0) < 1.0)
        assertFalse(p.genMw.isNaN())
        assertFalse(p.htTempC.isNaN())
    }

    @Test
    fun jacketWaterFailureTripsTheSet() {
        val p = prepared()
        p.requestStart()
        run(p, 60.0)
        p.ctl.speedRefRpm = Spec.RATED_RPM * 1.02
        run(p, 60.0)
        p.ctl.htPump = false
        run(p, 2400.0, dt = 0.1)
        assertTrue(
            "jacket ${p.htTempC} state ${p.state}",
            p.ann.windows.getValue(AlarmId.HT_TEMP_TRIP).active ||
                p.state == EngineState.STOPPING || p.state == EngineState.STOPPED
        )
    }

    @Test
    fun lossOfLubeOilTripsTheSet() {
        val p = prepared()
        p.requestStart()
        run(p, 60.0)
        p.loLitres = 1000.0 // sump all but empty, pump loses suction
        run(p, 60.0)
        assertTrue(
            "oil ${p.loBar} bar, state ${p.state}",
            p.state == EngineState.STOPPING || p.state == EngineState.STOPPED
        )
    }

    @Test
    fun outOfStepClosureShocksTheMachine() {
        val p = prepared()
        p.requestStart()
        run(p, 60.0)
        p.ctl.fieldSwitch = true
        run(p, 20.0)
        p.ctl.syncMode = SyncMode.MANUAL
        // Deliberately close a long way out of phase.
        p.ctl.speedRefRpm = Spec.RATED_RPM * 1.03
        var guard = 0
        while (guard++ < 20000) {
            p.step(0.02)
            val a = abs(((p.syncAngleDeg + 180.0) % 360.0) - 180.0)
            if (a in 100.0..160.0) break
        }
        p.closeBreaker()
        assertTrue("no shock recorded", p.crankshaftDamage > 0.05 || p.state == EngineState.WRECKED)
    }

    @Test
    fun coldResidualFuelBlocksTheStart() {
        val p = prepared()
        p.ctl.viscoAuto = false
        p.ctl.heaterManual = 0.0
        run(p, 900.0, dt = 0.2) // let the fuel cool down in the line
        assertTrue("viscosity ${p.fuelViscCst}", p.fuelViscCst > 25.0)
        assertTrue(p.startInterlocks().any { it.contains("VISCOSITY") })
    }

    @Test
    fun losingAnotherSetPullsTheFrequencyDown() {
        val p = prepared()
        p.requestStart()
        run(p, 60.0)
        p.ctl.fieldSwitch = true
        run(p, 20.0)
        // Put it on the bars by force so the test stays deterministic.
        p.ctl.syncMode = SyncMode.MANUAL
        p.grid.frequency = 50.0
        p.ctl.speedRefRpm = Spec.RATED_RPM
        var guard = 0
        while (guard++ < 40000) {
            p.step(0.02)
            val a = abs(((p.syncAngleDeg + 180.0) % 360.0) - 180.0)
            if (a < 3.0 && abs(p.slipHz) < 0.2) break
        }
        p.closeBreaker()
        p.ctl.govMode = GovMode.DROOP
        repeat(40) { p.raiseSpeed(0.2); run(p, 5.0) }
        val before = p.grid.frequency
        val ourLoadBefore = p.genMw
        p.events.fireByKey("unit_trip")
        run(p, 12.0)
        assertTrue("frequency did not fall: $before -> ${p.grid.frequency}", p.grid.frequency < before - 0.05)
        run(p, 40.0)
        // Droop action should have picked up load.
        assertTrue(
            "no pickup: $ourLoadBefore -> ${p.genMw}",
            p.genMw > ourLoadBefore || p.state != EngineState.RUNNING
        )
    }

    @Test
    fun badBunkerBlocksTheFuelFilter() {
        val p = prepared()
        p.requestStart()
        run(p, 60.0)
        p.ctl.speedRefRpm = Spec.RATED_RPM * 1.03
        run(p, 120.0)
        val dpBefore = p.fuelFilterDp
        p.catFinesPpm = 90.0
        p.waterPct = 1.8
        run(p, 7200.0, dt = 0.25)
        assertTrue("filter dp ${p.fuelFilterDp} was $dpBefore", p.fuelFilterDp > dpBefore)
    }

    @Test
    fun nothingGoesToNaNOverALongWatch() {
        val p = prepared(events = true)
        p.events.intensity = 2.5
        p.requestStart()
        run(p, 60.0)
        p.timeScale = 1.0
        run(p, 6.0 * 3600.0, dt = 0.25)
        listOf(
            p.rpm, p.genMw, p.genMvar, p.htTempC, p.loBar, p.fuelViscCst, p.exhMeanC,
            p.grid.frequency, p.terminalKv, p.airBar, p.scavBar, p.lambda
        ).forEach { assertFalse("NaN in the model", it.isNaN()) }
        assertTrue(p.htTempC < 200.0)
        assertTrue(p.rpm < 700.0)
    }
}
