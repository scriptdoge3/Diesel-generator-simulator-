package com.valepoint.hfo.sim

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

enum class EngineState { STOPPED, CRANKING, FIRING, RUNNING, STOPPING, TRIPPED, WRECKED }

class LogEntry(val clock: String, val text: String, val severity: Int)

/**
 * The whole generating set and its auxiliaries, integrated on a fixed step.
 *
 * Everything here is written as a lumped physical model rather than a script:
 * heat goes in and out of masses, pressure comes from pumps minus restrictions,
 * torque comes from burnt fuel. Faults are therefore emergent - a blocked filter
 * really does starve the injection pumps, which really does drop the load.
 */
class Plant(seed: Long = 20260811L) {

    val rng = Rng(seed)
    val ctl = Controls()
    val ann = Annunciator()
    val grid = Grid(rng)
    val events = EventDirector(this)

    // ---------------------------------------------------------------- time
    var clockSec: Double = 5 * 3600.0 + 20 * 60.0
    var runHours: Double = 41230.0
    var timeScale: Double = 1.0

    // ------------------------------------------------------------- engine
    var state: EngineState = EngineState.STOPPED
    var wreckReason: String? = null
    var rpm: Double = 0.0
    var fuelRack: Double = 0.0
    var rackDemand: Double = 0.0
    var govIntegral: Double = 0.0
    var indicatedMw: Double = 0.0
    var mechMw: Double = 0.0
    var frictionMw: Double = 0.0
    var lambda: Double = 10.0
    var fuelKgS: Double = 0.0
    var turboFrac: Double = 0.0
    var scavBar: Double = 0.0
    var chargeAirTemp: Double = 25.0
    var exhMeanC: Double = 25.0
    var tcOutC: Double = 25.0
    var tcRpm: Double = 0.0
    val cylExhC = DoubleArray(Spec.CYLINDERS) { 25.0 }
    val injectorCond = DoubleArray(Spec.CYLINDERS) { 1.0 }
    val exhValveCond = DoubleArray(Spec.CYLINDERS) { 1.0 }
    var smokeIndex: Double = 0.0
    private var crankTimer = 0.0
    private var firingTimer = 0.0
    private var startAirOpen = false
    var govHunt = 0.0
    private var huntPhase = 0.0

    // -------------------------------------------------------- starting air
    var airBar: Double = 28.0
    var controlAirBar: Double = 7.0
    var compressorRunning = false
    var startAttempts = 0

    // ------------------------------------------------------------ lube oil
    var loBar: Double = 0.0
    var loTempC: Double = 24.0
    var loFilterDp: Double = 0.35
    var loLitres: Double = Spec.LO_SUMP_LITRES
    var loLeakLpm: Double = 0.0
    var loContamination: Double = 0.05 // 0..1
    var oilMist: Double = 0.0
    private var preLubeSeconds = 0.0

    // ------------------------------------------------------------- cooling
    var htTempC: Double = 24.0
    var htReturnC: Double = 24.0
    var htBar: Double = 0.0
    var htExpansionPct: Double = 78.0
    var htLeakLpm: Double = 0.0
    var ltTempC: Double = 24.0
    var ambientC: Double = 26.0
    var radiatorDemand: Double = 0.0
    var htValve: Double = 0.0 // 0 = full bypass, 1 = full through cooler
    var preheaterOn = false

    // ---------------------------------------------------------------- fuel
    var settlingM3: Double = 70.0
    var settlingTempC: Double = 40.0
    var serviceM3: Double = 44.0
    var serviceTempC: Double = 45.0
    var mdoM3: Double = 32.0
    var bunkerM3: Double = 640.0
    var steamBar: Double = 7.0
    var boilerOnline = true
    var fuelTempC: Double = 30.0
    var fuelViscCst: Double = 380.0
    var fuelBar: Double = 0.0
    var fuelFilterDp: Double = 0.15
    var heaterOutput: Double = 0.0
    var viscoIntegral: Double = 0.0
    var purifierRunning = false
    var purifierTripped = false
    var purifierFeedTempC: Double = 30.0
    var sludgeLitres: Double = 0.0

    /** Quality of what is in the service tank. */
    var waterPct: Double = 0.15
    var catFinesPpm: Double = 18.0
    var bunkerGradeCst: Double = Spec.HFO_GRADE_CST50
    var bunkerWaterPct: Double = 0.5
    var bunkerCatFines: Double = 32.0
    var bunkerSulphurPct: Double = 2.9

    var fuelConsumedT: Double = 0.0
    var congealedFuel: Double = 0.0 // residual left in the injection system after a hot stop

    // ---------------------------------------------------------- electrical
    var fieldA: Double = 0.0
    var emfKv: Double = 0.0
    var terminalKv: Double = 0.0
    var genMw: Double = 0.0
    var genMvar: Double = 0.0
    var statorA: Double = 0.0
    var loadAngleDeg: Double = 0.0
    var syncAngleDeg: Double = 0.0
    var slipHz: Double = 0.0
    var breakerClosed = false
    var statorTempC: Double = 30.0
    var avrIntegral: Double = 0.0
    var poleSlipping = false
    var pmsBlocked: String? = null

    // -------------------------------------------------------------- damage
    var bearingWear: Double = 0.02
    var linerWear: Double = 0.06
    var turboFouling: Double = 0.05
    var crankshaftDamage: Double = 0.0
    var scavFireLevel: Double = 0.0
    var scavDeposits: Double = 0.08

    // ------------------------------------------------------------- logging
    val logbook = ArrayDeque<LogEntry>()

    fun log(text: String, severity: Int = 0) {
        logbook.addFirst(LogEntry(formatClock(clockSec), text, severity))
        while (logbook.size > 300) logbook.removeLast()
    }

    init {
        // Start the session with a cold, shut down unit and the board reset.
        for (i in 0 until Spec.CYLINDERS) {
            injectorCond[i] = 1.0 - rng.range(0.0, 0.06)
            exhValveCond[i] = 1.0 - rng.range(0.0, 0.08)
        }
        log("Watch handover. Unit 3 shut down, cold, on turning gear.", 0)
        log("Fuel: 380 cSt residual in settling and service tanks.", 0)
    }

    // =====================================================================
    //  Top level integration
    // =====================================================================

    fun step(dtReal: Double) {
        val dt = dtReal * timeScale
        if (dt <= 0.0) return
        // Sub-step so that fast electrical and speed dynamics stay stable when
        // the operator runs the plant at 60x.
        val steps = max(1, min(40, (dt / 0.05).toInt()))
        val h = dt / steps
        repeat(steps) { integrate(h) }
    }

    private fun integrate(dt: Double) {
        clockSec += dt
        if (state == EngineState.RUNNING || state == EngineState.FIRING) runHours += dt / 3600.0

        updateAmbient(dt)
        updateAir(dt)
        updateSteamAndTanks(dt)
        updateFuelLine(dt)
        updateLubeOil(dt)
        updateCooling(dt)
        updateStartStop(dt)
        updateGovernor(dt)
        updateCombustion(dt)
        updateMechanical(dt)
        updateGenerator(dt)
        grid.step(dt, clockSec)
        updateSystemFrequency(dt)
        updateWear(dt)
        events.step(dt)
        updateAlarms(dt)
    }

    // =====================================================================
    //  Ambient and station services
    // =====================================================================

    private fun updateAmbient(dt: Double) {
        val h = (clockSec / 3600.0) % 24.0
        val diurnal = 26.0 + 5.0 * sin((h - 9.0) / 24.0 * 2.0 * Math.PI)
        ambientC = lag(ambientC, diurnal, 900.0, dt)
        controlAirBar = lag(controlAirBar, if (airBar > 12.0) 7.0 else airBar * 0.5, 8.0, dt)
    }

    private fun updateAir(dt: Double) {
        val wantRun = if (ctl.compressorAuto) {
            (airBar < 25.0) || (compressorRunning && airBar < Spec.AIR_MAX_BAR)
        } else ctl.compressorRun
        compressorRunning = wantRun && airBar < Spec.AIR_MAX_BAR + 0.2 && !compressorFailed
        if (compressorRunning) {
            airBar += 0.030 * dt
        }
        if (startAirOpen) {
            airBar -= 0.42 * dt
        }
        airBar -= 0.00015 * dt // receiver leakage
        airBar = airBar.clamp(0.0, Spec.AIR_MAX_BAR + 0.5)
    }

    var compressorFailed = false

    private fun updateSteamAndTanks(dt: Double) {
        // Auxiliary boiler. Exhaust gas economiser helps when the unit is loaded.
        val demand = (if (ctl.settlingHeater) 1.0 else 0.0) +
            (if (ctl.serviceHeater) 1.0 else 0.0) +
            heaterOutput * 1.6 +
            (if (purifierRunning) 0.7 else 0.0)
        val supply = if (boilerOnline) 5.6 else if (mechMw > 4.0) 1.9 else 0.0
        val net = supply - demand
        steamBar = (steamBar + net * 0.06 * dt).clamp(0.0, 7.5)

        val steamOk = steamBar > 2.5
        val heatAuth = mapRange(steamBar, 1.0, 6.0, 0.0, 1.0)

        // Settling tank: heated for water and sediment to drop out.
        val settlingTarget = if (ctl.settlingHeater && steamOk) 70.0 else ambientC + 4.0
        settlingTempC = lag(settlingTempC, lerp(ambientC + 4.0, settlingTarget, heatAuth), 1400.0, dt)

        // Service tank held hot so the booster pumps see a pumpable oil.
        val serviceTarget = if (ctl.serviceHeater && steamOk) 88.0 else ambientC + 4.0
        serviceTempC = lag(serviceTempC, lerp(ambientC + 4.0, serviceTarget, heatAuth), 1100.0, dt)

        // Bunker transfer into the settling tank.
        if (ctl.transferPump && bunkerM3 > 0.5 && settlingM3 < Spec.SETTLING_TANK_M3) {
            val rate = 12.0 / 3600.0 * dt // 12 m3/h
            val q = min(rate, min(bunkerM3, Spec.SETTLING_TANK_M3 - settlingM3))
            bunkerM3 -= q
            settlingM3 += q
        }

        updatePurifier(dt)
    }

    /**
     * The centrifuge. Feed temperature is everything: below about 90 degC a
     * residual fuel will not release its water, and pushing the feed rate up
     * shortens the residence time and lets cat fines through.
     */
    private fun updatePurifier(dt: Double) {
        val heaterOk = steamBar > 2.0
        val feedTarget = if (ctl.purifier && heaterOk) 98.0 else settlingTempC
        purifierFeedTempC = lag(purifierFeedTempC, feedTarget, 120.0, dt)

        if (purifierTripped) ctl.purifier = false
        purifierRunning = ctl.purifier && !purifierTripped && settlingM3 > 1.0 &&
            serviceM3 < Spec.SERVICE_TANK_M3 - 0.2

        if (!purifierRunning) return

        val throughput = 5.0 * ctl.purifierFeed / 3600.0 * dt // m3
        val q = min(throughput, min(settlingM3, Spec.SERVICE_TANK_M3 - serviceM3))
        if (q <= 0.0) return

        // Separation efficiency from feed temperature and residence time.
        val tempEff = mapRange(purifierFeedTempC, 75.0, 96.0, 0.15, 1.0)
        val rateEff = mapRange(ctl.purifierFeed, 0.4, 1.0, 1.0, 0.55)
        val eff = (tempEff * rateEff).clamp(0.05, 0.985)

        // Settling tank contents partly cleaned already by gravity when hot.
        val gravity = mapRange(settlingTempC, 40.0, 70.0, 0.0, 0.35)
        val inWater = bunkerWaterPct * (1.0 - gravity)
        val inFines = bunkerCatFines * (1.0 - gravity * 0.5)

        val outWater = inWater * (1.0 - eff)
        val outFines = inFines * (1.0 - eff * 0.92)

        // Blend into the service tank.
        val total = serviceM3 + q
        waterPct = (waterPct * serviceM3 + outWater * q) / total
        catFinesPpm = (catFinesPpm * serviceM3 + outFines * q) / total
        settlingM3 -= q
        serviceM3 += q
        sludgeLitres += q * 1000.0 * (inWater - outWater + (inFines - outFines) / 1000.0) * 0.01

        if (ctl.purifierFeed > 0.9 && purifierFeedTempC < 85.0 && rng.chance(0.0006 * dt * 60)) {
            purifierTripped = true
            log("Purifier bowl tripped - water seal broken.", 1)
        }
    }

    // =====================================================================
    //  Fuel to the injection pumps
    // =====================================================================

    private fun updateFuelLine(dt: Double) {
        val onHfo = ctl.fuelMode == FuelMode.HFO
        val grade = if (onHfo) bunkerGradeCst else Spec.MDO_GRADE_CST50
        val supplyTemp = if (onHfo) serviceTempC else ambientC + 3.0
        val boosters = (if (ctl.boosterA) 1 else 0) + (if (ctl.boosterB) 1 else 0)

        // Final heater under viscosity control.
        val target = ctl.viscoSetpoint
        if (ctl.viscoAuto) {
            val err = fuelViscCst - target // too thick -> positive -> more heat
            viscoIntegral = (viscoIntegral + err * 0.0055 * dt).clamp(-1.0, 1.6)
            heaterOutput = (err * 0.012 + viscoIntegral).clamp(0.0, 1.0)
        } else {
            heaterOutput = ctl.heaterManual.clamp(0.0, 1.0)
            viscoIntegral = heaterOutput
        }
        val steamAuth = mapRange(steamBar, 1.5, 6.0, 0.0, 1.0)
        val heatRise = 75.0 * heaterOutput * steamAuth
        val circulating = boosters > 0 || ctl.circPump
        val fuelTarget = if (circulating) supplyTemp + heatRise else ambientC + 6.0
        fuelTempC = lag(fuelTempC, fuelTarget, if (circulating) 45.0 else 600.0, dt)
        fuelViscCst = viscosityAt(grade, fuelTempC)

        // Filter blockage. Cat fines and cold thick oil are what actually block it.
        val flowFactor = 0.4 + fuelKgS * 1.2
        val fouling = (catFinesPpm / 30.0) * 0.6 + (fuelViscCst / 60.0).coerceAtMost(2.0) * 0.5 +
            waterPct * 0.8
        val filterArea = when (ctl.fuelFilter) {
            FilterSel.BOTH -> 2.0
            else -> 1.0
        }
        fuelFilterDp += (fouling * flowFactor * 0.000045 / filterArea) * dt
        fuelFilterDp = fuelFilterDp.clamp(0.05, 4.0)

        val pumpHead = when (boosters) {
            0 -> 0.0
            1 -> 8.0
            else -> 9.2
        }
        val draw = fuelKgS * 1.1
        val tankEmpty = if (onHfo) serviceM3 < 0.3 else mdoM3 < 0.3
        val target2 = if (tankEmpty) 0.0 else (pumpHead - fuelFilterDp - draw * 1.6).coerceAtLeast(0.0)
        fuelBar = lag(fuelBar, target2, 1.2, dt)

        // Consumption.
        if (fuelKgS > 0.0) {
            val m3 = fuelKgS * dt / 960.0 // roughly 0.96 t/m3
            if (onHfo) serviceM3 = (serviceM3 - m3).coerceAtLeast(0.0)
            else mdoM3 = (mdoM3 - m3).coerceAtLeast(0.0)
            fuelConsumedT += fuelKgS * dt / 1000.0
        }

        // Residual left standing in a hot line slowly sets.
        if (onHfo && state == EngineState.STOPPED && !circulating) {
            congealedFuel = (congealedFuel + dt / 1800.0).coerceAtMost(1.0)
        } else if (circulating && fuelTempC > 90.0) {
            congealedFuel = (congealedFuel - dt / 300.0).coerceAtLeast(0.0)
        }
    }

    /** How well the fuel atomises, 0..1. The heart of running on residual oil. */
    fun atomisationQuality(): Double {
        val v = fuelViscCst
        val q = when {
            v < 8.0 -> mapRange(v, 2.0, 8.0, 0.55, 1.0) // too thin, injector leaks and pump wear
            v <= 17.0 -> 1.0
            else -> mapRange(v, 17.0, 60.0, 1.0, 0.25)
        }
        return (q * (1.0 - congealedFuel * 0.5)).clamp(0.1, 1.0)
    }

    // =====================================================================
    //  Lube oil
    // =====================================================================

    private fun updateLubeOil(dt: Double) {
        val running = rpm > 30.0
        val standbyWanted = ctl.loStandbyPump || ctl.preLube ||
            (ctl.loStandbyAuto && loBar < Spec.LO_LOW_BAR && state != EngineState.STOPPED)
        val standby = standbyWanted && !auxMotorFailed

        // Engine driven pump delivers with speed; electric pump gives a fixed head.
        val viscFactor = mapRange(loTempC, 20.0, 60.0, 0.75, 1.0)
        val enginePump = if (running) 5.6 * (rpm / Spec.RATED_RPM).pow(0.85) * viscFactor else 0.0
        val elecPump = if (standby) 2.6 else 0.0
        val head = max(enginePump, elecPump)

        loFilterDp += (0.0000012 + loContamination * 0.000012) * (if (running) 1.0 else 0.15) * dt
        loFilterDp = loFilterDp.clamp(0.15, 3.0)
        val filterArea = if (ctl.loFilter == FilterSel.BOTH) 1.9 else 1.0
        val effDp = loFilterDp / filterArea

        val levelFactor = mapRange(loLitres, Spec.LO_SUMP_LITRES * 0.45, Spec.LO_SUMP_LITRES * 0.7, 0.2, 1.0)
        val wearLeak = bearingWear * 1.6
        val targetBar = ((head - effDp - wearLeak) * levelFactor).coerceAtLeast(0.0)
        loBar = lag(loBar, targetBar, 0.8, dt)

        if (ctl.preLube && loBar > 1.0) preLubeSeconds += dt else if (!running) preLubeSeconds = max(0.0, preLubeSeconds - dt * 0.2)

        // Sump temperature: the engine puts heat in, the cooler takes it out.
        val heatIn = 0.55 + mechMw * 0.10
        val coolerFlow = if (ctl.ltPump && !auxMotorFailed) {
            if (ctl.loCoolerAuto) mapRange(loTempC, 58.0, 72.0, 0.0, 1.0) else ctl.loCoolerManual
        } else 0.0
        val coolTo = ltTempC + 6.0
        val eqTemp = if (running) {
            lerp(ambientC + 20.0 + heatIn * 22.0, coolTo, coolerFlow * 0.85)
        } else lerp(ambientC + 2.0, coolTo, coolerFlow * 0.3)
        loTempC = lag(loTempC, eqTemp, if (running) 320.0 else 1500.0, dt)

        // Contamination and its purifier.
        val makingSoot = (1.0 - atomisationQuality()) * (fuelKgS * 0.6)
        loContamination += (makingSoot * 0.00012 + catFinesPpm * 1e-7 * fuelKgS) * dt
        if (ctl.loPurifier && !auxMotorFailed) loContamination -= 0.000012 * dt
        loContamination = loContamination.clamp(0.0, 1.0)

        loLitres = (loLitres - loLeakLpm / 60.0 * dt).coerceAtLeast(0.0)

        // Oil mist from hot bearings. This is the warning before a crankcase explosion.
        val hotBearing = if (running) {
            (bearingWear - 0.35).coerceAtLeast(0.0) * 2.0 +
                (if (loBar < Spec.LO_LOW_BAR) 0.4 else 0.0) +
                (loTempC - 80.0).coerceAtLeast(0.0) * 0.02
        } else 0.0
        oilMist = (oilMist + (hotBearing * 0.03 - 0.02) * dt).clamp(0.0, 3.0)

        if (oilMist > 2.4 && rpm > 100 && rng.chance(0.0012 * dt * 60)) {
            wreck("CRANKCASE EXPLOSION. Oil mist ignited on a hot bearing.")
        }
    }

    var auxMotorFailed = false

    // =====================================================================
    //  Cooling water
    // =====================================================================

    private fun updateCooling(dt: Double) {
        val htRunning = ctl.htPump && !auxMotorFailed
        val ltRunning = ctl.ltPump && !auxMotorFailed
        htBar = lag(htBar, if (htRunning) 2.6 * mapRange(htExpansionPct, 10.0, 35.0, 0.3, 1.0) else 0.0, 2.0, dt)

        htExpansionPct = (htExpansionPct - htLeakLpm / 60.0 * dt * 0.06).coerceAtLeast(0.0)

        // Radiator / cooling tower duty.
        radiatorDemand = if (ctl.radiatorAuto) {
            mapRange(ltTempC, 30.0, 42.0, 0.0, 1.0)
        } else ctl.radiatorManual
        val ltDuty = if (ltRunning) (0.25 + 0.75 * radiatorDemand) else 0.0
        val ltHeat = 0.4 + mechMw * 0.28 // charge air cooler plus lube oil cooler
        val ltEq = ambientC + 5.0 + ltHeat * (if (ltDuty > 0.02) 1.9 / ltDuty else 60.0)
        ltTempC = lag(ltTempC, ltEq.coerceAtMost(120.0), 180.0, dt)

        // Jacket water.
        preheaterOn = ctl.preheater && rpm < 50.0
        val preheatKw = if (preheaterOn) 72.0 else 0.0
        val jacketHeatMw = if (rpm > 30) 0.18 * indicatedMw + 0.08 else 0.0
        htValve = if (ctl.htThermostatAuto) {
            lag(htValve, mapRange(htTempC, ctl.htSetpoint - 4.0, ctl.htSetpoint + 4.0, 0.0, 1.0), 12.0, dt)
        } else lag(htValve, 1.0, 12.0, dt)

        val massC = 9.0e6 // effective thermal mass of engine plus jacket water, J/K
        val coolOut = if (htRunning) htValve * 2.60e6 * (htTempC - (ltTempC + 4.0)).coerceAtLeast(0.0) / 40.0 else 0.0
        val ambientLoss = 1800.0 * (htTempC - ambientC)
        val flowStall = if (htRunning) 1.0 else 0.12 // no circulation means local boiling, temperature runs away
        val netW = jacketHeatMw * 1e6 / flowStall.coerceAtLeast(0.12) * (if (htRunning) 1.0 else 1.0) +
            preheatKw * 1000.0 - coolOut - ambientLoss
        htTempC = (htTempC + netW / massC * dt).clamp(-5.0, 160.0)
        htReturnC = lag(htReturnC, htTempC - (if (rpm > 100) 8.0 else 1.0), 30.0, dt)

        if (htExpansionPct < 5.0 && rpm > 100) {
            htTempC += 0.05 * dt // air locked, no heat transfer
        }
    }

    // =====================================================================
    //  Start and stop sequencing
    // =====================================================================

    fun startInterlocks(): List<String> {
        val out = mutableListOf<String>()
        if (ctl.turningGear) out += "TURNING GEAR ENGAGED"
        if (ctl.indicatorCocks) out += "INDICATOR COCKS OPEN"
        if (airBar < Spec.AIR_MIN_START_BAR) out += "STARTING AIR BELOW ${Spec.AIR_MIN_START_BAR.toInt()} BAR"
        if (loBar < 1.0) out += "NO LUB OIL PRESSURE (PRE-LUBE)"
        if (preLubeSeconds < 25.0) out += "PRE-LUBE NOT COMPLETE"
        if (loLitres < Spec.LO_SUMP_LITRES * 0.5) out += "SUMP LEVEL LOW"
        if (!ctl.htPump) out += "JACKET WATER PUMP STOPPED"
        if (htTempC < 45.0) out += "JACKET WATER BELOW 45 C"
        if (ctl.fuelMode == FuelMode.HFO && htTempC < Spec.HT_PREHEAT_MIN)
            out += "RESIDUAL FUEL SELECTED, JACKET BELOW ${Spec.HT_PREHEAT_MIN.toInt()} C"
        if (fuelBar < 2.0) out += "FUEL OIL PRESSURE LOW"
        if (ctl.fuelMode == FuelMode.HFO && fuelViscCst > 25.0) out += "FUEL VISCOSITY ABOVE 25 cSt"
        if (ann.windows.values.any { it.id.trip && it.active }) out += "TRIP NOT RESET"
        if (state == EngineState.WRECKED) out += "UNIT OUT OF SERVICE"
        return out
    }

    fun requestStart(): Boolean {
        if (state != EngineState.STOPPED) return false
        val blocks = startInterlocks()
        if (blocks.isNotEmpty()) {
            log("Start blocked: ${blocks.first()}", 1)
            return false
        }
        state = EngineState.CRANKING
        crankTimer = 0.0
        firingTimer = 0.0
        startAirOpen = true
        startAttempts++
        ann.clearFirstOut()
        log("Air start valve opened. Attempt $startAttempts.", 0)
        return true
    }

    fun requestStop() {
        if (state == EngineState.RUNNING || state == EngineState.FIRING || state == EngineState.CRANKING) {
            if (breakerClosed) {
                log("Stop refused: generator breaker still closed.", 1)
                return
            }
            state = EngineState.STOPPING
            startAirOpen = false
            log("Normal stop. Fuel racks to zero.", 0)
            if (ctl.fuelMode == FuelMode.HFO) {
                log("Note: stopped on residual fuel, injection system not flushed.", 1)
            }
        }
    }

    fun emergencyStop() {
        if (state == EngineState.STOPPED || state == EngineState.WRECKED) return
        trip(AlarmId.EMERGENCY_STOP, "Emergency stop operated.")
    }

    fun trip(id: AlarmId, msg: String) {
        ann.set(id, true, 99.0, clockSec, 0.0)
        if (state == EngineState.RUNNING || state == EngineState.FIRING || state == EngineState.CRANKING) {
            state = EngineState.STOPPING
        }
        startAirOpen = false
        if (breakerClosed) openBreaker("trip")
        log(msg, 2)
    }

    private fun updateStartStop(dt: Double) {
        when (state) {
            EngineState.CRANKING -> {
                crankTimer += dt
                if (airBar < 8.0 || crankTimer > 14.0) {
                    startAirOpen = false
                    state = EngineState.STOPPED
                    ann.set(AlarmId.START_FAIL, true, 99.0, clockSec, 0.0)
                    log("Start failure - engine did not fire.", 2)
                } else if (rpm > 150.0 && mechMw > 0.05) {
                    state = EngineState.FIRING
                    log("Engine firing.", 0)
                }
            }
            EngineState.FIRING -> {
                firingTimer += dt
                if (rpm > 220.0) startAirOpen = false
                if (rpm > Spec.RATED_RPM * 0.85) {
                    state = EngineState.RUNNING
                    ctl.turningGear = false
                    log("Engine up to speed, ${rpm.toInt()} rpm.", 0)
                } else if (firingTimer > 20.0 && rpm < 150.0) {
                    state = EngineState.STOPPING
                    startAirOpen = false
                    ann.set(AlarmId.START_FAIL, true, 99.0, clockSec, 0.0)
                    log("Start failure - engine stalled during run up.", 2)
                }
            }
            EngineState.STOPPING -> {
                if (rpm < 15.0) {
                    state = EngineState.STOPPED
                    rpm = 0.0
                    log("Engine stopped.", 0)
                }
            }
            else -> {}
        }
        if (startAirOpen && airBar < 6.0) startAirOpen = false
    }

    // =====================================================================
    //  Governor
    // =====================================================================

    private fun updateGovernor(dt: Double) {
        val fuelCut = state == EngineState.STOPPING || state == EngineState.STOPPED ||
            state == EngineState.WRECKED
        if (fuelCut) {
            rackDemand = 0.0
            govIntegral = 0.0
        } else if (state == EngineState.CRANKING) {
            rackDemand = if (rpm > 70.0) 0.42 else 0.0
        } else if (ctl.fuelRackManual) {
            rackDemand = ctl.fuelRackHandwheel.clamp(0.0, 1.0)
        } else {
            when (ctl.govMode) {
                GovMode.DROOP -> {
                    val speedErr = (ctl.speedRefRpm - rpm) / Spec.RATED_RPM
                    rackDemand = speedErr * (100.0 / ctl.droopPct)
                }
                GovMode.ISOCH -> {
                    val f = if (breakerClosed) grid.frequency else rpm / 10.0
                    val err = (Spec.GRID_HZ - f) / Spec.GRID_HZ
                    govIntegral = (govIntegral + err * 2.2 * dt).clamp(-0.3, 1.25)
                    rackDemand = err * 14.0 + govIntegral
                }
            }
            // Start-up idle governing when off load and off the bus.
            if (!breakerClosed && state == EngineState.FIRING) {
                rackDemand = ((Spec.RATED_RPM - rpm) / Spec.RATED_RPM * 6.0).clamp(0.0, 0.5)
            }
        }

        // Control air failure drives the actuator to minimum on this design.
        if (controlAirBar < 3.0 && !ctl.fuelRackManual) rackDemand = min(rackDemand, 0.12)

        // Load limiter and the scavenge air fuel limiter.
        val limiter = ctl.loadLimitPct / 100.0
        val scavLimit = 0.16 + 0.9 * (scavBar / 2.4).coerceIn(0.0, 1.0)
        var target = rackDemand.clamp(0.0, min(limiter, scavLimit))

        if (govHunt > 0.0) {
            huntPhase += dt * 6.0
            target += sin(huntPhase) * 0.09 * govHunt
        }
        val actuatorRate = if (controlAirBar > 4.0) 0.55 else 0.2
        fuelRack = slew(fuelRack, target.clamp(0.0, 1.05), actuatorRate, dt)
        fuelRack = lag(fuelRack, target.clamp(0.0, 1.05), 0.30, dt)
    }

    // =====================================================================
    //  Combustion, turbocharging, exhaust
    // =====================================================================

    private fun updateCombustion(dt: Double) {
        val rpmFrac = (rpm / Spec.RATED_RPM).coerceIn(0.0, 1.3)
        val onHfo = ctl.fuelMode == FuelMode.HFO
        val lhv = if (onHfo) 40.2 else 42.7

        // Fuel actually delivered depends on rack, speed and supply pressure.
        val supplyOk = mapRange(fuelBar, 0.4, 2.5, 0.0, 1.0)
        val maxFlow = 0.78
        fuelKgS = if (state == EngineState.STOPPED || state == EngineState.WRECKED) 0.0
        else fuelRack * rpmFrac * maxFlow * supplyOk

        // Air side. Turbocharger spins up on exhaust energy and lags badly.
        val energy = (indicatedMw / 12.5).coerceIn(0.0, 1.4)
        val tcTarget = (0.10 + 0.92 * energy.pow(0.62)) * rpmFrac.pow(0.4)
        val tcTau = if (rpmFrac < 0.5) 6.5 else 3.4
        turboFrac = lag(turboFrac, tcTarget.coerceIn(0.0, 1.15), tcTau, dt)
        tcRpm = turboFrac * 21000.0
        scavBar = 2.45 * turboFrac.pow(2.0) * (1.0 - 0.42 * turboFouling)

        val scavAbs = scavBar + 1.0
        val compressorRise = 95.0 * (scavAbs.pow(0.283) - 1.0) * 3.4
        val cacDuty = if (ctl.ltPump && !auxMotorFailed) 0.97 else 0.0
        val airOut = lerp(ambientC + compressorRise, ltTempC + 8.0, cacDuty)
        chargeAirTemp = lag(chargeAirTemp, airOut, 25.0, dt)

        val airKgS = 7.0 * scavAbs * rpmFrac * (293.0 / (chargeAirTemp + 273.15))
        lambda = if (fuelKgS > 1e-4) (airKgS / (14.5 * fuelKgS)).coerceIn(0.4, 30.0) else 30.0

        // Combustion quality.
        val atom = atomisationQuality()
        val airFactor = mapRange(lambda, 1.05, 1.75, 0.42, 1.0)
        val compression = mapRange(linerWear, 0.0, 0.9, 1.0, 0.72)
        val coldStart = if (htTempC < 40.0) mapRange(htTempC, 10.0, 45.0, 0.55, 1.0) else 1.0
        val injAvg = injectorCond.average()
        val waterPenalty = mapRange(waterPct, 0.2, 1.5, 1.0, 0.82)
        val eff = 0.472 * airFactor * (0.55 + 0.45 * atom) * compression * coldStart *
            (0.6 + 0.4 * injAvg) * waterPenalty

        indicatedMw = fuelKgS * lhv * eff
        smokeIndex = ((1.6 - lambda).coerceAtLeast(0.0) * 1.4 + (1.0 - atom) * 0.8).clamp(0.0, 3.0)

        // Exhaust temperatures.
        val meanTarget = if (fuelKgS > 1e-4) chargeAirTemp + 800.0 / lambda else ambientC + 10.0
        exhMeanC = lag(exhMeanC, meanTarget.coerceIn(ambientC, 900.0), 22.0, dt)
        val condSum = injectorCond.sum()
        for (i in 0 until Spec.CYLINDERS) {
            // A weak injector burns less in its own pot and pushes fuel to the others.
            val share = injectorCond[i] / (condSum / Spec.CYLINDERS)
            val valvePenalty = (1.0 - exhValveCond[i]) * 90.0
            val t = exhMeanC * (0.55 + 0.45 * share) + valvePenalty * (if (fuelKgS > 0.01) 1.0 else 0.0)
            cylExhC[i] = lag(cylExhC[i], t, 18.0, dt)
        }
        tcOutC = lag(tcOutC, exhMeanC - 130.0 * turboFrac, 25.0, dt)

        // Surge: high back pressure with a fouled machine and a fast load throw off.
        if (turboFouling > 0.35 && lambda < 1.35 && turboFrac > 0.5 && rng.chance(0.02 * dt * 60)) {
            ann.set(AlarmId.TC_SURGE, true, 99.0, clockSec, 0.0)
            turboFrac *= 0.72
        }
    }

    // =====================================================================
    //  Speed / torque
    // =====================================================================

    private fun updateMechanical(dt: Double) {
        val rpmFrac = rpm / Spec.RATED_RPM
        val loViscDrag = mapRange(loTempC, 15.0, 55.0, 1.9, 1.0)
        frictionMw = (1.05 * rpmFrac.pow(2.0) + 0.18 * rpmFrac) * loViscDrag *
            (1.0 + bearingWear * 0.8)
        if (rpm < 5.0) frictionMw = 0.0

        val airStartMw = if (startAirOpen && airBar > 6.0) {
            mapRange(rpm, 0.0, 190.0, 1.35, 0.05) * mapRange(airBar, 6.0, 25.0, 0.3, 1.0)
        } else 0.0

        mechMw = indicatedMw - frictionMw + airStartMw

        if (breakerClosed) {
            // Locked to the system: the grid sets the speed.
            rpm = grid.frequency * 120.0 / Spec.POLES
        } else {
            val omega = (rpm * 2.0 * Math.PI / 60.0).coerceAtLeast(0.4)
            val j = 2.0 * Spec.KINETIC_MJ * 1e6 /
                (Spec.RATED_RPM * 2.0 * Math.PI / 60.0).pow(2.0)
            val netW = (mechMw - genMw.coerceAtLeast(0.0)) * 1e6
            val domega = netW / (j * omega)
            rpm = (rpm + domega * 60.0 / (2.0 * Math.PI) * dt).coerceAtLeast(0.0)
            if (state == EngineState.STOPPED && !startAirOpen) rpm = 0.0
        }

        if (rpm > Spec.OVERSPEED_TRIP_RPM && state != EngineState.WRECKED) {
            trip(AlarmId.OVERSPEED, "Overspeed trip at ${rpm.toInt()} rpm.")
            if (rpm > Spec.RATED_RPM * 1.35) {
                wreck("Engine destroyed by overspeed. The overspeed trip did not act in time.")
            }
        }
    }

    // =====================================================================
    //  Generator, AVR, synchronising
    // =====================================================================

    private fun updateGenerator(dt: Double) {
        val speedPu = rpm / Spec.RATED_RPM

        // Excitation.
        val fieldTarget = if (!ctl.fieldSwitch) 0.0 else when (ctl.avrMode) {
            AvrMode.AUTO -> {
                val measured = terminalKv
                val ref = ctl.voltageSetpointKv - if (breakerClosed) genMvar * 0.045 else 0.0
                val err = (ref - measured) / Spec.RATED_KV
                avrIntegral = (avrIntegral + err * 1.4 * dt).clamp(0.0, 3.2)
                (err * 3.0 + avrIntegral).clamp(0.0, 3.2)
            }
            AvrMode.MANUAL -> ctl.fieldRheostat * 3.2
        }
        fieldA = lag(fieldA, fieldTarget * 380.0, 0.9, dt)
        val fieldPu = fieldA / 380.0
        emfKv = Spec.RATED_KV * fieldPu * speedPu

        val busLive = grid.othersOnline() && grid.frequency > 20.0
        grid.busKv = if (busLive) {
            val nominal = Spec.RATED_KV * (1.0 - grid.faultDepth)
            lag(grid.busKv, nominal, 0.25, dt)
        } else if (breakerClosed) {
            terminalKv
        } else {
            lag(grid.busKv, 0.0, 0.4, dt)
        }

        val xd = 1.85
        if (breakerClosed) {
            terminalKv = grid.busKv
            val vPu = (terminalKv / Spec.RATED_KV).coerceAtLeast(0.05)
            val ePu = (emfKv / Spec.RATED_KV).coerceAtLeast(0.001)
            val pPu = (mechMw - 0.06 - 0.22 * (mechMw / Spec.RATED_MW).pow(2.0)) / Spec.RATED_MVA
            val sinDelta = (pPu * xd / (ePu * vPu))
            if (abs(sinDelta) > 1.0) {
                poleSlipping = true
                loadAngleDeg = 90.0 * sign(sinDelta)
            } else {
                poleSlipping = false
                loadAngleDeg = Math.toDegrees(asin(sinDelta))
            }
            val qPu = (ePu * vPu * cos(Math.toRadians(loadAngleDeg)) - vPu * vPu) / xd
            genMw = pPu * Spec.RATED_MVA
            genMvar = lag(genMvar, qPu * Spec.RATED_MVA, 0.4, dt)
            syncAngleDeg = 0.0
            slipHz = 0.0
        } else {
            poleSlipping = false
            terminalKv = lag(terminalKv, emfKv, 0.35, dt)
            genMw = 0.0
            genMvar = 0.0
            loadAngleDeg = 0.0
            val fGen = rpm / 10.0
            val fBus = if (busLive) grid.frequency else 0.0
            slipHz = fGen - fBus
            if (busLive) syncAngleDeg = wrap360(syncAngleDeg + slipHz * 360.0 * dt)
        }

        val mva = sqrt(genMw * genMw + genMvar * genMvar)
        statorA = if (terminalKv > 0.5) mva * 1e6 / (1.7320508 * terminalKv * 1e3) else 0.0
        val statorEq = ambientC + 25.0 + 70.0 * (statorA / Spec.RATED_AMPS).pow(2.0)
        statorTempC = lag(statorTempC, statorEq, 300.0, dt)

        if (poleSlipping && breakerClosed) {
            crankshaftDamage += 0.004 * dt
            if (rng.chance(0.02 * dt * 60)) {
                trip(AlarmId.FIELD_FAIL, "Machine pole slipped - loss of synchronism.")
            }
        }
    }

    /** Frequency of the whole island, including our own contribution. */
    private fun updateSystemFrequency(dt: Double) {
        val others = grid.onlineGenerationMw()
        val ourGen = if (breakerClosed) genMw else 0.0
        val kinetic = grid.onlineKineticMj() + if (breakerClosed) Spec.KINETIC_MJ else 0.0

        if (kinetic < 1.0) {
            grid.frequency = 0.0
            grid.blackout = true
            if (!grid.othersOnline() && !breakerClosed) return
            return
        }
        grid.blackout = false

        // Load relief with falling frequency, about 1.5 percent per Hz.
        val damping = 1.0 + (grid.frequency - Spec.GRID_HZ) * 0.015
        val load = grid.demandMw * damping.coerceIn(0.6, 1.4)
        val imbalance = others + ourGen - load
        val df = grid.frequency * imbalance / (2.0 * kinetic)
        grid.frequency = (grid.frequency + df * dt).clamp(0.0, 62.0)

        shedTimer = (shedTimer - dt).coerceAtLeast(0.0)
        if (grid.frequency < 47.5 && grid.othersOnline() && shedTimer <= 0.0) {
            // Under frequency load shedding trips distribution feeders in stages.
            val shed = (grid.demandMw * 0.12).coerceAtMost(4.0)
            grid.demandStepMw -= shed
            shedTimer = 8.0
            log("Distribution under-frequency relays shed ${"%.1f".format(shed)} MW.", 1)
        }
    }

    // =====================================================================
    //  Breaker and synchronising
    // =====================================================================

    fun canCloseBreaker(): Pair<Boolean, String> {
        if (breakerClosed) return false to "BREAKER ALREADY CLOSED"
        if (state != EngineState.RUNNING) return false to "SET NOT RUNNING"
        if (!ctl.fieldSwitch || terminalKv < Spec.RATED_KV * 0.85) return false to "MACHINE NOT EXCITED"
        val busLive = grid.othersOnline() && grid.frequency > 20.0
        if (!busLive) return true to "DEAD BUS - BLACK START"
        if (ctl.syncMode == SyncMode.OFF) return false to "SYNCHRONISING SWITCH OFF"
        val angle = abs(wrapDeg(syncAngleDeg))
        val vErr = abs(terminalKv - grid.busKv) / Spec.RATED_KV
        if (ctl.syncMode == SyncMode.CHECK) {
            if (abs(slipHz) > 0.30) return false to "CHECK SYNC: SLIP ${"%.2f".format(slipHz)} Hz"
            if (angle > 12.0) return false to "CHECK SYNC: ${angle.toInt()} DEG OUT"
            if (vErr > 0.05) return false to "CHECK SYNC: VOLTAGE MISMATCH"
        }
        return true to "READY"
    }

    fun closeBreaker() {
        val (ok, why) = canCloseBreaker()
        if (!ok) {
            pmsBlocked = why
            log("Breaker close refused: $why", 1)
            return
        }
        val busLive = grid.othersOnline() && grid.frequency > 20.0
        if (!busLive) {
            breakerClosed = true
            ctl.breakerClosed = true
            grid.frequency = rpm / 10.0
            ctl.govMode = GovMode.ISOCH
            log("Breaker closed onto dead busbar. Unit 3 is now the island.", 1)
            return
        }

        val angle = abs(wrapDeg(syncAngleDeg))
        val slip = abs(slipHz)
        breakerClosed = true
        ctl.breakerClosed = true

        // Shock torque on closing out of step.
        val shock = (angle / 30.0).pow(2.0) + (slip / 0.5).pow(2.0) * 0.5
        when {
            angle < 10.0 && slip < 0.35 -> log("Breaker closed. Clean synchronisation.", 0)
            shock < 1.0 -> {
                log("Breaker closed ${angle.toInt()} deg out, ${"%.2f".format(slipHz)} Hz slip. Rough.", 1)
                crankshaftDamage += shock * 0.05
            }
            else -> {
                crankshaftDamage += shock * 0.16
                trip(AlarmId.SYNC_FAULT, "Closed ${angle.toInt()} deg out of step. Severe shock to the coupling.")
                if (crankshaftDamage > 0.85 || angle > 90.0) {
                    wreck("Crankshaft and flexible coupling wrecked by an out of step closure.")
                }
            }
        }
        // The machine is dragged into step; whatever load it had becomes real load.
        govIntegral = (fuelRack).clamp(0.0, 1.0)
    }

    fun openBreaker(reason: String) {
        if (!breakerClosed) return
        breakerClosed = false
        ctl.breakerClosed = false
        syncAngleDeg = 0.0
        genMw = 0.0
        genMvar = 0.0
        log("Generator breaker opened ($reason).", if (reason == "trip") 2 else 0)
    }

    fun wreck(reason: String) {
        if (state == EngineState.WRECKED) return
        state = EngineState.WRECKED
        wreckReason = reason
        fuelRack = 0.0
        rpm = 0.0
        if (breakerClosed) openBreaker("machine destroyed")
        log(reason, 3)
    }

    // =====================================================================
    //  Wear and slow damage
    // =====================================================================

    private fun updateWear(dt: Double) {
        val running = rpm > 100.0
        if (!running) {
            scavFireLevel = (scavFireLevel - 0.02 * dt).coerceAtLeast(0.0)
            return
        }
        val loadFrac = (genMw / Spec.RATED_MW).coerceIn(0.0, 1.2)
        val hoursDt = dt / 3600.0

        // Bearings: oil pressure, oil condition and load.
        val pressDeficit = (Spec.LO_NORMAL_BAR - loBar).coerceAtLeast(0.0) / Spec.LO_NORMAL_BAR
        bearingWear += (0.0008 + pressDeficit.pow(2.0) * 0.9 + loContamination * 0.02) *
            (0.4 + loadFrac) * hoursDt
        if (loBar < Spec.LO_TRIP_BAR) bearingWear += 0.02 * hoursDt * 60.0

        // Liners: cat fines are abrasive, and cold running with residual fuel
        // makes sulphuric acid on the liner wall.
        val coldCorrosion = if (htTempC < 70.0 && ctl.fuelMode == FuelMode.HFO)
            mapRange(htTempC, 45.0, 72.0, 1.0, 0.0) * bunkerSulphurPct * 0.4 else 0.0
        linerWear += (0.0006 + catFinesPpm * 0.00004 + coldCorrosion * 0.01) * (0.3 + loadFrac) * hoursDt

        // Turbo and scavenge space fouling: bad combustion and long low load running.
        val lowLoad = (0.35 - loadFrac).coerceAtLeast(0.0) * 3.0
        val badBurn = (1.0 - atomisationQuality())
        turboFouling += (0.0015 + lowLoad * 0.01 + badBurn * 0.03) * hoursDt
        scavDeposits += (0.001 + lowLoad * 0.02 + badBurn * 0.05 + smokeIndex * 0.01) * hoursDt
        if (ctl.fuelMode == FuelMode.MDO) scavDeposits -= 0.004 * hoursDt

        turboFouling = turboFouling.clamp(0.0, 1.0)
        scavDeposits = scavDeposits.clamp(0.0, 1.5)
        bearingWear = bearingWear.clamp(0.0, 1.2)
        linerWear = linerWear.clamp(0.0, 1.2)

        // Injectors suffer from thick, dirty or watery fuel.
        for (i in 0 until Spec.CYLINDERS) {
            val stress = (1.0 - atomisationQuality()) * 0.02 + catFinesPpm * 0.00002 + waterPct * 0.004
            injectorCond[i] = (injectorCond[i] - stress * hoursDt * rng.range(0.5, 1.5)).clamp(0.0, 1.0)
            if (cylExhC[i] > 520.0) {
                exhValveCond[i] = (exhValveCond[i] - (cylExhC[i] - 520.0) * 0.00006 * hoursDt).clamp(0.0, 1.0)
            }
        }

        // Scavenge fire: deposits plus blowby plus a hot cylinder.
        val hotCyl = cylExhC.max()
        if (scavDeposits > 0.6 && hotCyl > 480.0) {
            scavFireLevel += (scavDeposits - 0.6) * (hotCyl - 480.0) * 0.00004 * dt
        } else {
            scavFireLevel = (scavFireLevel - 0.01 * dt).coerceAtLeast(0.0)
        }
        if (scavFireLevel > 1.0) {
            ann.set(AlarmId.SCAV_FIRE, true, 99.0, clockSec, 0.0)
            if (scavFireLevel > 2.5) {
                wreck("Scavenge fire ran out of control and destroyed the scavenge space and pistons.")
            }
        }
        if (crankshaftDamage > 1.0) wreck("Crankshaft failed under accumulated shock damage.")
    }

    // =====================================================================
    //  Alarm scanning
    // =====================================================================

    private fun updateAlarms(dt: Double) {
        val running = rpm > 100.0
        val t = clockSec

        ann.set(AlarmId.LO_PRESS_LOW, running && loBar < Spec.LO_LOW_BAR, dt, t, 2.0)
        ann.set(AlarmId.LO_TEMP_HIGH, loTempC > 78.0, dt, t, 5.0)
        ann.set(AlarmId.LO_FILTER_DP, loFilterDp > 1.4, dt, t, 3.0)
        ann.set(AlarmId.LO_LEVEL_LOW, loLitres < Spec.LO_SUMP_LITRES * 0.6, dt, t, 3.0)
        ann.set(AlarmId.OIL_MIST, oilMist > 1.6, dt, t, 1.0)

        ann.set(AlarmId.HT_TEMP_HIGH, htTempC > Spec.HT_HIGH_TEMP, dt, t, 4.0)
        ann.set(AlarmId.HT_PRESS_LOW, running && htBar < 1.0, dt, t, 3.0)
        ann.set(AlarmId.HT_EXP_LOW, htExpansionPct < 25.0, dt, t, 3.0)
        ann.set(AlarmId.LT_TEMP_HIGH, chargeAirTemp > 65.0 && running, dt, t, 6.0)

        ann.set(AlarmId.FUEL_PRESS_LOW, running && fuelBar < 3.0, dt, t, 3.0)
        ann.set(AlarmId.FUEL_VISC_HIGH, fuelViscCst > 20.0 && ctl.fuelMode == FuelMode.HFO, dt, t, 8.0)
        ann.set(AlarmId.FUEL_VISC_LOW, fuelViscCst < 7.0 && running, dt, t, 8.0)
        ann.set(AlarmId.FUEL_FILTER_DP, fuelFilterDp > 1.2, dt, t, 4.0)
        ann.set(AlarmId.SERVICE_TANK_LOW, serviceM3 < 8.0, dt, t, 5.0)
        ann.set(AlarmId.PURIFIER_TRIP, purifierTripped, dt, t, 1.0)
        ann.set(AlarmId.STEAM_LOW, steamBar < 3.0, dt, t, 10.0)

        ann.set(AlarmId.EXH_TEMP_HIGH, exhMeanC > 480.0, dt, t, 5.0)
        val dev = if (running) cylExhC.max() - cylExhC.min() else 0.0
        ann.set(AlarmId.EXH_DEV_HIGH, dev > 55.0, dt, t, 8.0)
        ann.set(AlarmId.AIR_PRESS_LOW, airBar < 18.0, dt, t, 5.0)
        ann.set(AlarmId.GOV_HUNTING, govHunt > 0.3, dt, t, 4.0)

        ann.set(AlarmId.OVER_VOLTS, terminalKv > Spec.RATED_KV * 1.10, dt, t, 2.0)
        ann.set(
            AlarmId.UNDER_FREQ,
            breakerClosed && grid.frequency < 48.5 && grid.frequency > 5.0, dt, t, 2.0
        )
        ann.set(AlarmId.OVER_FREQ, breakerClosed && grid.frequency > 51.5, dt, t, 2.0)
        ann.set(AlarmId.STATOR_TEMP, statorTempC > 115.0, dt, t, 10.0)
        ann.set(AlarmId.BUS_DEAD, grid.blackout, dt, t, 1.0)
        ann.set(AlarmId.CONTROL_AIR, controlAirBar < 4.5, dt, t, 3.0)
        ann.set(AlarmId.AUX_MOTOR_FAIL, auxMotorFailed, dt, t, 1.0)

        // ---- protective trips ----
        if (running && loBar < Spec.LO_TRIP_BAR && state != EngineState.WRECKED) {
            trip(AlarmId.LO_PRESS_TRIP, "Lubricating oil pressure trip at ${"%.1f".format(loBar)} bar.")
        }
        if (htTempC > Spec.HT_TRIP_TEMP && running) {
            trip(AlarmId.HT_TEMP_TRIP, "Jacket water high temperature trip at ${htTempC.toInt()} C.")
        }
        if (breakerClosed && genMw < -Spec.RATED_MW * 0.08) {
            reversePowerTimer += dt
            if (reversePowerTimer > 8.0) {
                trip(AlarmId.REVERSE_POWER, "Reverse power relay operated, motoring at ${"%.1f".format(-genMw)} MW.")
                reversePowerTimer = 0.0
            }
        } else reversePowerTimer = 0.0

        if (breakerClosed && statorA > Spec.RATED_AMPS * 1.25) {
            overcurrentTimer += dt
            if (overcurrentTimer > 6.0) {
                trip(AlarmId.OVERCURRENT, "Stator overcurrent, ${statorA.toInt()} A.")
                overcurrentTimer = 0.0
            }
        } else overcurrentTimer = 0.0

        if (breakerClosed && ctl.fieldSwitch.not()) {
            trip(AlarmId.FIELD_FAIL, "Field switch opened while on load.")
        }
        if (breakerClosed && (grid.frequency < 47.0 || grid.frequency > 52.5)) {
            trip(AlarmId.UNDER_FREQ, "System frequency ${"%.2f".format(grid.frequency)} Hz - unit tripped by frequency protection.")
        }
        if (grid.faultDepth > 0.55 && breakerClosed && rng.chance(0.05 * dt * 60)) {
            trip(AlarmId.GEN_DIFF, "Generator protection operated during system fault.")
        }
    }

    private var shedTimer = 0.0
    private var reversePowerTimer = 0.0
    private var overcurrentTimer = 0.0

    // =====================================================================
    //  Operator actions used by the UI
    // =====================================================================

    fun raiseSpeed(amount: Double) {
        ctl.speedRefRpm = (ctl.speedRefRpm + amount).clamp(Spec.RATED_RPM * 0.94, Spec.RATED_RPM * 1.10)
        if (ctl.govMode == GovMode.ISOCH) ctl.loadRefMw = (ctl.loadRefMw + amount).clamp(0.0, Spec.RATED_MW)
    }

    fun raiseVolts(amount: Double) {
        ctl.voltageSetpointKv = (ctl.voltageSetpointKv + amount).clamp(Spec.RATED_KV * 0.9, Spec.RATED_KV * 1.08)
        ctl.fieldRheostat = (ctl.fieldRheostat + amount * 0.08).clamp(0.0, 1.0)
    }

    fun backflushFuelFilter() {
        fuelFilterDp = 0.15
        log("Fuel oil filter backflushed.", 0)
    }

    fun changeLoFilter() {
        loFilterDp = 0.2
        log("Lube oil filter changed over to the standby element.", 0)
    }

    fun resetPurifier() {
        purifierTripped = false
        log("Purifier reset.", 0)
    }

    fun topUpSump(litres: Double) {
        loLitres = (loLitres + litres).coerceAtMost(Spec.LO_SUMP_LITRES)
        log("Sump replenished with ${litres.toInt()} litres.", 0)
    }

    fun topUpExpansionTank() {
        htExpansionPct = 85.0
        log("Jacket water expansion tank topped up.", 0)
    }

    /** Total load on the island that Unit 3 is being asked to help carry. */
    fun systemSummary(): String =
        "DEMAND ${"%.1f".format(grid.demandMw)} MW   GEN ${"%.1f".format(grid.onlineGenerationMw() + if (breakerClosed) genMw else 0.0)} MW"
}
