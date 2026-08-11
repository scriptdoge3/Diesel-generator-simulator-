package com.valepoint.hfo.sim

import kotlin.math.abs

class PlantEvent(
    val key: String,
    val title: String,
    val detail: String,
    val offsite: Boolean,
    val weight: Double,
    val allowed: (Plant) -> Boolean = { true },
    val fire: (Plant) -> Unit,
)

/**
 * Decides what goes wrong and when. Off-site events come from the island
 * system and the fuel supply chain; on-site events are the machine and its
 * auxiliaries misbehaving. Everything it does is a change to a physical
 * quantity, so the operator sees symptoms rather than a message telling them
 * what has happened.
 */
class EventDirector(private val p: Plant) {

    var randomEventsEnabled = true

    /** 0 = quiet watch, 1 = normal, 2 = a bad night. */
    var intensity: Double = 1.0

    private var timer: Double = 0.0
    private var nextIn: Double = 900.0
    private var despatchTimer: Double = 600.0

    val history = ArrayDeque<String>()

    val catalogue: List<PlantEvent> = listOf(
        // ------------------------------------------------------ off site
        PlantEvent(
            "unit_trip", "UNIT TRIP ON THE SYSTEM",
            "Another set has tripped. Frequency will fall and Unit 3 must pick up.",
            offsite = true, weight = 1.0,
            allowed = { it.grid.machines.count { m -> m.online && !m.isPeaker } > 1 },
        ) { pl ->
            val victim = pl.grid.machines.filter { it.online && !it.isPeaker }.maxByOrNull { it.outputMw }
            victim?.let {
                pl.grid.tripMachine(it.name)
                pl.log("${it.name} has tripped, ${"%.1f".format(it.outputMw)} MW lost from the system.", 2)
            }
        },
        PlantEvent(
            "load_surge", "SYSTEM LOAD SURGE",
            "A large industrial load has come on. Demand steps up.",
            offsite = true, weight = 1.4,
        ) { pl ->
            val step = pl.rng.range(2.0, 5.5)
            pl.grid.demandStepMw += step
            pl.log("Island demand risen by ${"%.1f".format(step)} MW.", 1)
        },
        PlantEvent(
            "load_drop", "SYSTEM LOAD REJECTION",
            "A feeder has been switched out and demand falls away sharply.",
            offsite = true, weight = 0.8,
        ) { pl ->
            val step = pl.rng.range(2.0, 6.0)
            pl.grid.demandStepMw -= step
            pl.log("Feeder tripped, island demand down ${"%.1f".format(step)} MW.", 1)
        },
        PlantEvent(
            "system_fault", "SYSTEM FAULT",
            "An earth fault on the 33 kV network. Voltage collapses briefly.",
            offsite = true, weight = 0.7,
        ) { pl ->
            val depth = pl.rng.range(0.35, 0.75)
            pl.grid.applyFault(depth, pl.rng.range(0.18, 0.6))
            pl.log("Voltage dip on the system, fault on the 33 kV network.", 2)
        },
        PlantEvent(
            "bad_bunker", "BUNKER DELIVERY OFF SPEC",
            "The barge has delivered fuel outside specification.",
            offsite = true, weight = 0.9,
        ) { pl ->
            pl.bunkerM3 = 900.0
            pl.bunkerGradeCst = pl.rng.range(420.0, 700.0)
            pl.bunkerCatFines = pl.rng.range(45.0, 110.0)
            pl.bunkerWaterPct = pl.rng.range(0.8, 2.4)
            pl.bunkerSulphurPct = pl.rng.range(3.0, 4.3)
            pl.log(
                "Bunker delivery received: ${pl.bunkerGradeCst.toInt()} cSt, " +
                    "${pl.bunkerCatFines.toInt()} ppm Al+Si, ${"%.1f".format(pl.bunkerWaterPct)} pct water.", 2
            )
        },
        PlantEvent(
            "good_bunker", "BUNKER DELIVERY",
            "A clean delivery of 380 cSt residual fuel.",
            offsite = true, weight = 0.5,
        ) { pl ->
            pl.bunkerM3 = 900.0
            pl.bunkerGradeCst = 380.0
            pl.bunkerCatFines = pl.rng.range(10.0, 25.0)
            pl.bunkerWaterPct = pl.rng.range(0.1, 0.5)
            pl.bunkerSulphurPct = pl.rng.range(2.2, 3.0)
            pl.log("Bunker delivery received, on specification.", 0)
        },
        PlantEvent(
            "heat_wave", "AMBIENT TEMPERATURE RISE",
            "Still, hot air. Radiator duty falls away.",
            offsite = true, weight = 0.6,
        ) { pl ->
            pl.ambientC += pl.rng.range(6.0, 11.0)
            pl.log("Ambient temperature climbing, cooling towers will struggle.", 1)
        },
        PlantEvent(
            "peaker_start", "GT PEAKER CALLED",
            "The despatcher starts the gas turbine to cover the peak.",
            offsite = true, weight = 0.5,
            allowed = { pl -> pl.grid.machines.none { it.isPeaker && it.online } },
        ) { pl ->
            pl.grid.startPeaker()
            pl.log("Load despatch have started GT 1.", 0)
        },

        // ------------------------------------------------------- on site
        PlantEvent(
            "filter_block", "FUEL FILTER BLOCKING",
            "The duty fuel filter is loading up rapidly.",
            offsite = false, weight = 1.2,
            allowed = { it.rpm > 100 },
        ) { pl ->
            pl.fuelFilterDp += pl.rng.range(0.6, 1.1)
            pl.catFinesPpm += 15.0
        },
        PlantEvent(
            "lo_leak", "LUBE OIL LEAK",
            "A cooler joint has started to weep.",
            offsite = false, weight = 0.8,
            allowed = { it.loLeakLpm < 0.1 },
        ) { pl ->
            pl.loLeakLpm = pl.rng.range(1.5, 6.0)
            pl.log("Oil on the floor plates under the cooler.", 1)
        },
        PlantEvent(
            "ht_leak", "JACKET WATER LEAK",
            "The expansion tank level is falling.",
            offsite = false, weight = 0.7,
            allowed = { it.htLeakLpm < 0.1 },
        ) { pl ->
            pl.htLeakLpm = pl.rng.range(2.0, 8.0)
        },
        PlantEvent(
            "injector_fail", "INJECTOR FAILURE",
            "One unit's injector has given up.",
            offsite = false, weight = 1.0,
            allowed = { it.rpm > 100 },
        ) { pl ->
            val i = pl.rng.int(0, Spec.CYLINDERS)
            pl.injectorCond[i] = pl.rng.range(0.05, 0.3)
            pl.log("Combustion irregular. Watch the individual exhaust temperatures.", 1)
        },
        PlantEvent(
            "exh_valve", "EXHAUST VALVE BLOWBY",
            "An exhaust valve seat is burning.",
            offsite = false, weight = 0.7,
            allowed = { it.rpm > 100 },
        ) { pl ->
            val i = pl.rng.int(0, Spec.CYLINDERS)
            pl.exhValveCond[i] = pl.rng.range(0.2, 0.5)
        },
        PlantEvent(
            "purifier_trip", "PURIFIER TRIP",
            "The separator has lost its water seal and tripped.",
            offsite = false, weight = 0.8,
            allowed = { it.purifierRunning },
        ) { pl ->
            pl.purifierTripped = true
        },
        PlantEvent(
            "boiler_trip", "AUXILIARY BOILER TRIP",
            "Steam pressure will fall away and the fuel heaters with it.",
            offsite = false, weight = 0.8,
            allowed = { it.boilerOnline },
        ) { pl ->
            pl.boilerOnline = false
            pl.log("Auxiliary boiler has tripped on flame failure.", 2)
        },
        PlantEvent(
            "aux_motor", "AUXILIARY MOTOR OVERLOAD",
            "A pump motor has tripped on overload.",
            offsite = false, weight = 0.6,
            allowed = { !it.auxMotorFailed },
        ) { pl ->
            pl.auxMotorFailed = true
            pl.log("Motor control centre: auxiliary pump tripped on overload.", 2)
        },
        PlantEvent(
            "compressor_fail", "AIR COMPRESSOR FAILURE",
            "The starting air compressor has failed.",
            offsite = false, weight = 0.5,
            allowed = { !it.compressorFailed },
        ) { pl ->
            pl.compressorFailed = true
            pl.log("Starting air compressor has failed. Air receivers will not be replenished.", 1)
        },
        PlantEvent(
            "gov_hunt", "GOVERNOR HUNTING",
            "The governor is unstable and the load is swinging.",
            offsite = false, weight = 0.7,
            allowed = { it.rpm > 300 && it.govHunt < 0.1 },
        ) { pl ->
            pl.govHunt = pl.rng.range(0.5, 1.0)
        },
        PlantEvent(
            "control_air", "CONTROL AIR LEAK",
            "Control air pressure is falling. The governor will run back.",
            offsite = false, weight = 0.5,
        ) { pl ->
            pl.controlAirBar = 2.5
            pl.log("Control air line fractured in the engine room.", 2)
        },
        PlantEvent(
            "turbo_foul", "TURBOCHARGER FOULING",
            "Deposits on the turbine side, boost pressure falling.",
            offsite = false, weight = 0.6,
            allowed = { it.rpm > 100 },
        ) { pl ->
            pl.turboFouling = (pl.turboFouling + pl.rng.range(0.2, 0.4)).coerceAtMost(1.0)
        },
    )

    fun step(dt: Double) {
        // Despatch instructions arrive whether or not faults are enabled.
        despatchTimer -= dt
        if (despatchTimer <= 0.0) {
            issueDespatch()
            despatchTimer = p.rng.range(900.0, 2400.0)
        }
        p.grid.despatchTargetMw?.let { tgt ->
            if (p.clockSec > p.grid.despatchDeadlineSec) {
                if (p.breakerClosed && abs(p.genMw - tgt) < 0.6) {
                    p.log("Load despatch: instruction met, ${"%.1f".format(p.genMw)} MW.", 0)
                } else if (p.breakerClosed) {
                    p.log("Load despatch: Unit 3 has not met its instruction of ${"%.1f".format(tgt)} MW.", 1)
                }
                p.grid.despatchTargetMw = null
            }
        }

        // Slow decay of transient conditions.
        if (p.govHunt > 0.0) p.govHunt = (p.govHunt - dt / 900.0).coerceAtLeast(0.0)
        if (p.grid.demandStepMw != 0.0) {
            p.grid.demandStepMw = lag(p.grid.demandStepMw, 0.0, 2400.0, dt)
        }

        if (!randomEventsEnabled || intensity <= 0.0) return
        timer += dt
        if (timer < nextIn) return
        timer = 0.0
        nextIn = p.rng.range(420.0, 1500.0) / intensity.coerceAtLeast(0.15)
        fireRandom()
    }

    private fun issueDespatch() {
        if (!p.breakerClosed) return
        val need = (p.grid.demandMw - p.grid.onlineGenerationMw()).coerceIn(0.0, Spec.RATED_MW)
        val target = (need + p.rng.range(-1.5, 1.5)).clamp(Spec.MIN_LOAD_MW, Spec.RATED_MW * 0.98)
        p.grid.despatchTargetMw = target
        p.grid.despatchDeadlineSec = p.clockSec + 900.0
        p.ann.set(AlarmId.DISPATCH, true, 99.0, p.clockSec, 0.0)
        p.log("Load despatch: take Unit 3 to ${"%.1f".format(target)} MW within 15 minutes.", 1)
        // The legend is momentary; clear the condition next scan.
        p.ann.windows.getValue(AlarmId.DISPATCH).pending = 0.0
    }

    private fun fireRandom() {
        val pool = catalogue.filter { it.allowed(p) }
        if (pool.isEmpty()) return
        val total = pool.sumOf { it.weight }
        var r = p.rng.nextDouble() * total
        for (e in pool) {
            r -= e.weight
            if (r <= 0.0) {
                fireNow(e)
                return
            }
        }
    }

    fun fireNow(e: PlantEvent) {
        e.fire(p)
        history.addFirst("${formatClock(p.clockSec)}  ${e.title}")
        while (history.size > 40) history.removeLast()
    }

    fun fireByKey(key: String) {
        catalogue.firstOrNull { it.key == key }?.let { fireNow(it) }
    }

    // ---- repairs the operator can call for ----

    fun repairBoiler() {
        p.boilerOnline = true
        p.log("Auxiliary boiler relit.", 0)
    }

    fun repairAuxMotor() {
        p.auxMotorFailed = false
        p.log("Auxiliary motor overload reset.", 0)
    }

    fun repairCompressor() {
        p.compressorFailed = false
        p.log("Starting air compressor repaired.", 0)
    }

    fun stopLoLeak() {
        p.loLeakLpm = 0.0
        p.log("Lube oil leak clamped.", 0)
    }

    fun stopHtLeak() {
        p.htLeakLpm = 0.0
        p.log("Jacket water leak made good.", 0)
    }

    fun restoreControlAir() {
        p.controlAirBar = 7.0
        p.log("Control air line repaired.", 0)
    }
}
