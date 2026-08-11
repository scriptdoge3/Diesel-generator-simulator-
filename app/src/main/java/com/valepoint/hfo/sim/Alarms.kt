package com.valepoint.hfo.sim

/** Which panel window group a legend belongs to. */
enum class AlarmGroup { ENGINE, FUEL, LUBE, COOL, ELEC, AUX }

/**
 * The annunciator legends. Text is deliberately in the clipped, all caps style
 * of an engraved 1970s lamp box.
 */
enum class AlarmId(
    val legend: String,
    val group: AlarmGroup,
    val trip: Boolean = false,
) {
    LO_PRESS_LOW("LUB OIL\nPRESS LOW", AlarmGroup.LUBE),
    LO_PRESS_TRIP("LUB OIL\nPRESS TRIP", AlarmGroup.LUBE, trip = true),
    LO_TEMP_HIGH("LUB OIL\nTEMP HIGH", AlarmGroup.LUBE),
    LO_FILTER_DP("LUB OIL FILT\nDIFF PRESS", AlarmGroup.LUBE),
    LO_LEVEL_LOW("SUMP LEVEL\nLOW", AlarmGroup.LUBE),
    OIL_MIST("CRANKCASE\nOIL MIST", AlarmGroup.LUBE, trip = true),

    HT_TEMP_HIGH("JACKET WTR\nTEMP HIGH", AlarmGroup.COOL),
    HT_TEMP_TRIP("JACKET WTR\nTEMP TRIP", AlarmGroup.COOL, trip = true),
    HT_PRESS_LOW("JACKET WTR\nPRESS LOW", AlarmGroup.COOL),
    HT_EXP_LOW("EXPANSION TK\nLEVEL LOW", AlarmGroup.COOL),
    LT_TEMP_HIGH("CHARGE AIR\nTEMP HIGH", AlarmGroup.COOL),

    FUEL_PRESS_LOW("FUEL OIL\nPRESS LOW", AlarmGroup.FUEL),
    FUEL_VISC_HIGH("FUEL VISC\nHIGH", AlarmGroup.FUEL),
    FUEL_VISC_LOW("FUEL VISC\nLOW", AlarmGroup.FUEL),
    FUEL_FILTER_DP("FUEL FILT\nDIFF PRESS", AlarmGroup.FUEL),
    SERVICE_TANK_LOW("SERVICE TK\nLEVEL LOW", AlarmGroup.FUEL),
    PURIFIER_TRIP("PURIFIER\nTRIPPED", AlarmGroup.FUEL),
    STEAM_LOW("STEAM PRESS\nLOW", AlarmGroup.FUEL),

    EXH_TEMP_HIGH("EXH GAS\nTEMP HIGH", AlarmGroup.ENGINE),
    EXH_DEV_HIGH("EXH GAS\nDEVIATION", AlarmGroup.ENGINE),
    TC_SURGE("TURBOCHARGER\nSURGE", AlarmGroup.ENGINE),
    SCAV_FIRE("SCAVENGE\nFIRE", AlarmGroup.ENGINE),
    OVERSPEED("OVERSPEED\nTRIP", AlarmGroup.ENGINE, trip = true),
    START_FAIL("START\nFAILURE", AlarmGroup.ENGINE),
    EMERGENCY_STOP("EMERGENCY\nSTOP", AlarmGroup.ENGINE, trip = true),
    AIR_PRESS_LOW("START AIR\nPRESS LOW", AlarmGroup.ENGINE),
    GOV_HUNTING("GOVERNOR\nHUNTING", AlarmGroup.ENGINE),

    REVERSE_POWER("REVERSE\nPOWER", AlarmGroup.ELEC, trip = true),
    OVERCURRENT("STATOR\nOVERCURRENT", AlarmGroup.ELEC, trip = true),
    GEN_DIFF("GENERATOR\nDIFFERENTIAL", AlarmGroup.ELEC, trip = true),
    FIELD_FAIL("LOSS OF\nEXCITATION", AlarmGroup.ELEC, trip = true),
    OVER_VOLTS("OVER\nVOLTAGE", AlarmGroup.ELEC),
    UNDER_FREQ("UNDER\nFREQUENCY", AlarmGroup.ELEC),
    OVER_FREQ("OVER\nFREQUENCY", AlarmGroup.ELEC),
    STATOR_TEMP("STATOR WDG\nTEMP HIGH", AlarmGroup.ELEC),
    BUS_DEAD("BUSBAR\nDEAD", AlarmGroup.ELEC),
    SYNC_FAULT("OUT OF STEP\nCLOSURE", AlarmGroup.ELEC, trip = true),

    CONTROL_AIR("CONTROL AIR\nPRESS LOW", AlarmGroup.AUX),
    AUX_MOTOR_FAIL("AUX MOTOR\nOVERLOAD", AlarmGroup.AUX),
    DC_SUPPLY("110V DC\nSUPPLY FAIL", AlarmGroup.AUX),
    DISPATCH("LOAD DESPATCH\nCALLING", AlarmGroup.AUX),
    ;

    companion object {
        fun ofGroup(g: AlarmGroup): List<AlarmId> = entries.filter { it.group == g }
    }
}

enum class LampState {
    /** Off, condition healthy and acknowledged. */
    CLEAR,

    /** Condition present, not yet accepted. Fast flash plus horn. */
    UNACK,

    /** Condition present and accepted. Steady. */
    ACCEPTED,

    /** Condition cleared but never reset. Slow flash, "ringback". */
    RINGBACK,
}

class AlarmWindow(val id: AlarmId) {
    var state: LampState = LampState.CLEAR
    var active: Boolean = false
    var firstOut: Boolean = false
    var raisedAtSec: Double = -1.0
    var pending: Double = 0.0 // on-delay accumulator
}

/**
 * Annunciator panel logic: sequence ISA-18.1 M-A-1 with first-out, which is
 * what a station of this vintage would have had.
 */
class Annunciator {
    val windows: Map<AlarmId, AlarmWindow> = AlarmId.entries.associateWith { AlarmWindow(it) }
    var horn: Boolean = false
        private set
    var lampTest: Boolean = false
    private var anyTripLatched = false

    val active: List<AlarmWindow> get() = windows.values.filter { it.state != LampState.CLEAR }

    fun unacknowledgedCount(): Int = windows.values.count { it.state == LampState.UNACK }

    /**
     * Drives one legend. [cond] is the raw process condition, [delay] an on-delay
     * in seconds so that transients do not fill the board with noise.
     */
    fun set(id: AlarmId, cond: Boolean, dt: Double, clockSec: Double, delay: Double = 1.0) {
        val w = windows.getValue(id)
        if (cond) {
            w.pending += dt
        } else {
            w.pending = 0.0
        }
        val nowActive = w.pending >= delay
        if (nowActive && !w.active) {
            w.active = true
            w.raisedAtSec = clockSec
            w.state = LampState.UNACK
            horn = true
            if (id.trip && !anyTripLatched) {
                anyTripLatched = true
                w.firstOut = true
            }
        } else if (!nowActive && w.active) {
            w.active = false
            w.state = if (w.state == LampState.CLEAR) LampState.CLEAR else LampState.RINGBACK
        }
    }

    fun accept() {
        horn = false
        for (w in windows.values) {
            if (w.state == LampState.UNACK) w.state = LampState.ACCEPTED
        }
    }

    fun reset() {
        for (w in windows.values) {
            if (w.state == LampState.RINGBACK && !w.active) {
                w.state = LampState.CLEAR
                w.firstOut = false
            }
        }
        if (windows.values.none { it.firstOut && it.active }) {
            anyTripLatched = windows.values.any { it.active && it.id.trip }
        }
    }

    /** Clears the first-out latch, used when the unit is reset for a fresh start. */
    fun clearFirstOut() {
        windows.values.forEach { it.firstOut = false }
        anyTripLatched = false
    }

    fun silence() {
        horn = false
    }
}
