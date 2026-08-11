package com.valepoint.hfo.sim

/**
 * Nameplate data for the simulated set.
 *
 * Vale Point "B" Station, Unit 3: a 1974 nine cylinder in-line medium speed
 * four stroke trunk piston engine burning 380 cSt residual fuel, direct
 * coupled to a 12 pole salient pole alternator on an island 11 kV system.
 */
object Spec {
    const val STATION = "VALE POINT B STATION"
    const val UNIT = "UNIT 3"
    const val ENGINE_TYPE = "9 CYL IN-LINE 4-STROKE TURBOCHARGED"
    const val BUILT = "COMMISSIONED 1974"

    const val CYLINDERS = 9
    const val BORE_MM = 640.0
    const val STROKE_MM = 900.0

    /** Electrical. */
    const val POLES = 12
    const val GRID_HZ = 50.0
    const val RATED_RPM = 500.0 // 120 * 50 / 12
    const val RATED_KV = 11.0
    const val RATED_MW = 12.5
    const val POWER_FACTOR = 0.8
    const val RATED_MVA = RATED_MW / POWER_FACTOR
    const val RATED_AMPS = RATED_MVA * 1e6 / (1.7320508 * RATED_KV * 1e3)

    /** Minimum continuous rating; running below this on residual fuel fouls the engine. */
    const val MIN_LOAD_MW = 3.0

    /** Inertia constant of the set, seconds on its own MVA base. */
    const val INERTIA_H = 1.9
    val KINETIC_MJ = INERTIA_H * RATED_MVA

    /** Starting air. */
    const val AIR_RECEIVER_M3 = 6.0
    const val AIR_MAX_BAR = 30.0
    const val AIR_MIN_START_BAR = 15.0

    /** Lube oil. */
    const val LO_SUMP_LITRES = 9000.0
    const val LO_NORMAL_BAR = 4.5
    const val LO_LOW_BAR = 2.8
    const val LO_TRIP_BAR = 2.0
    const val LO_NORMAL_TEMP = 65.0

    /** Jacket / HT cooling. */
    const val HT_NORMAL_TEMP = 88.0
    const val HT_PREHEAT_MIN = 60.0 // minimum jacket temperature to start on residual fuel
    const val HT_HIGH_TEMP = 95.0
    const val HT_TRIP_TEMP = 105.0

    /** Fuel oil. */
    const val HFO_GRADE_CST50 = 380.0
    const val MDO_GRADE_CST50 = 6.0
    const val VISC_SETPOINT_CST = 13.0
    const val SERVICE_TANK_M3 = 60.0
    const val SETTLING_TANK_M3 = 90.0
    const val MDO_TANK_M3 = 40.0

    /** Consumption at full load, tonnes/hour, from about 195 g/kWh. */
    const val SFOC_G_PER_KWH = 195.0

    /** Speed protection. */
    const val OVERSPEED_TRIP_RPM = RATED_RPM * 1.15

    val CYL_NAMES: List<String> = (1..CYLINDERS).map { "A$it" }
}
