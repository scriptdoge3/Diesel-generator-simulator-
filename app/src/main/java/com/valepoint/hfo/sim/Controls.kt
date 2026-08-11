package com.valepoint.hfo.sim

enum class FuelMode(val label: String) { MDO("DISTILLATE"), HFO("RESIDUAL") }

enum class GovMode(val label: String) { DROOP("DROOP"), ISOCH("ISOCH") }

enum class AvrMode(val label: String) { AUTO("AUTO"), MANUAL("MANUAL") }

enum class SyncMode(val label: String) { OFF("OFF"), CHECK("CHECK SYNC"), MANUAL("MANUAL") }

enum class FilterSel(val label: String) { A("A"), B("B"), BOTH("A+B") }

/** Everything the operator can physically move. Held mutable and read by the models. */
class Controls {
    // ---- fuel oil ----
    var transferPump = false
    var purifier = false
    var purifierFeed = 0.6 // fraction of design throughput
    var settlingHeater = false
    var serviceHeater = false
    var fuelMode = FuelMode.MDO
    var boosterA = false
    var boosterB = false
    var circPump = false
    var viscoAuto = true
    var viscoSetpoint = Spec.VISC_SETPOINT_CST
    var heaterManual = 0.0 // 0..1 when visco control is in manual
    var fuelFilter = FilterSel.A

    // ---- lube oil ----
    var preLube = false
    var loStandbyPump = false
    var loStandbyAuto = true
    var loPurifier = false
    var loCoolerManual = 0.5
    var loCoolerAuto = true
    var loFilter = FilterSel.A

    // ---- cooling ----
    var htPump = false
    var ltPump = false
    var preheater = false
    var htThermostatAuto = true
    var htSetpoint = Spec.HT_NORMAL_TEMP
    var radiatorAuto = true
    var radiatorManual = 0.0 // 0..1 fan demand

    // ---- starting air ----
    var compressorAuto = true
    var compressorRun = false
    var receiverAOpen = true
    var receiverBOpen = true
    var turningGear = true
    var indicatorCocks = true

    // ---- engine / governor ----
    var govMode = GovMode.DROOP
    var droopPct = 4.0
    var speedRefRpm = Spec.RATED_RPM
    var loadRefMw = 0.0
    var loadLimitPct = 110.0
    var fuelRackManual = false
    var fuelRackHandwheel = 0.0

    // ---- generator ----
    var fieldSwitch = false
    var avrMode = AvrMode.AUTO
    var voltageSetpointKv = Spec.RATED_KV
    var fieldRheostat = 0.5
    var syncMode = SyncMode.OFF
    var breakerClosed = false

    // ---- misc ----
    var vent = false
}
