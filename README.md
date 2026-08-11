# Vale Point B Station, Unit 3

An Android simulator of a 1970s utility-scale heavy fuel oil generating set.

Unit 3 is a 1974 nine-cylinder in-line medium-speed four-stroke turbocharged
diesel burning 380 cSt residual fuel, direct coupled to a 12-pole 12.5 MW
alternator on an island 11 kV system. There is no interconnector, so what this
one set does matters to the whole island.

It is a free-play sandbox: no scripted lessons, no scoring. You keep the watch,
the plant behaves, and events arrive from off site and on site.

## What is actually simulated

Nothing here is a state machine dressed up as a plant. Pressures come from pumps
minus restrictions, temperatures from heat balances over real thermal masses, and
torque from fuel that is burnt with the air that is actually available. Faults
are emergent: a blocking filter genuinely starves the injection pumps, which
genuinely drops the load.

**Heavy fuel treatment.** Bunker, settling and service tanks with steam coils fed
from an auxiliary boiler. A centrifugal purifier whose separation efficiency
depends on feed temperature and residence time — run it cold or too fast and
water and catalytic fines carry over into the service tank. Viscosity control on
the final heater using a Walther/ASTM D341 viscosity-temperature relation, so
380 cSt fuel really does need about 135 °C to reach 13 cSt at the injectors.
Filter differential pressure that climbs with cat fines, water and cold thick oil.
MDO/HFO changeover.

**Engine.** Fuel rack to indicated power through combustion efficiency that
depends on air/fuel ratio, atomisation quality, compression (liner wear), jacket
temperature and individual injector condition. Turbocharger with real spool-up
lag and a scavenge-air fuel limiter, so load has to be taken slowly or you get
black smoke and high exhaust temperatures instead of megawatts. Nine individual
cylinder exhaust temperatures — a failed injector cools its own pot and pushes
fuel into the other eight.

**Auxiliaries.** Starting air receivers and compressor; a cranking model that can
fail to fire on cold jacket water or thick fuel. Lubricating oil pressure from
pump speed, filter dP, sump level and bearing clearance, with contamination and
oil-mist build-up. HT jacket circuit with preheater and thermostatic valve, LT
circuit, charge air cooler and radiators, all ambient-dependent.

**Electrical.** Round-rotor machine model with load angle and pole slipping. AVR
in auto or manual with reactive droop. Synchroscope, check-sync relay and a
manual position that will let you close badly out of step and wreck the coupling.
Droop and isochronous governing. Island frequency from a swing equation over the
real inertia of whatever is online, so losing a 12.5 MW set at the evening peak
drops the frequency fast.

**Protection.** A first-out annunciator with accept, reset and lamp test, in the
ISA-18.1 M-A-1 sequence a station of this vintage would have had. Overspeed, LO
pressure, jacket temperature, reverse power, overcurrent, loss of excitation,
under/over frequency, generator differential, crankcase oil mist.

**Damage.** Bearing and liner wear, turbocharger and scavenge-space fouling,
injector and exhaust valve condition, crankshaft shock. The unit can be destroyed:
crankcase explosion, scavenge fire, overspeed, or an out-of-step breaker closure.

## Events

Off site, from the system and the fuel supply chain: another set tripping, load
surges and rejections, 33 kV faults, load despatch instructions, gas turbine
peaker starts, ambient temperature swings, and bunker deliveries that may be well
off specification.

On site: fuel filter blockages, purifier trips, lube oil and jacket water leaks,
injector and exhaust valve failures, auxiliary boiler trips, motor overloads, air
compressor failures, governor hunting, control air leaks and turbocharger fouling.

Events fire on their own at an adjustable rate, and every one can also be
provoked by hand from the LOG page.

## Pages

`UNIT` `ENGINE` `FUEL OIL` `COOLING` `ELECTRICAL` `SYSTEM` `ALARMS` `LOG` `NOTES`

The NOTES page carries the standing orders: how to prepare a cold unit, start,
change over to residual fuel, synchronise, take load, keep the watch, and black
start the island after a total loss of supply.

Plant time can be run at 1x, 5x, 15x or 60x, which you will want, because
preheating a cold jacket to 60 °C takes an hour of plant time — as it does in
real life.

## Building

Requires JDK 17 or newer and an Android SDK with platform 35.

```
./gradlew :app:assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`. It is signed with
the debug key, so allow installation from unknown sources on the phone.

Minimum Android 8.0 (API 26). No network permission, no data collection; the
whole thing runs offline.

## Tests

```
./gradlew :app:testDebugUnitTest
```

`PlantTest` exercises the physics headlessly: a prepared unit starts and runs up,
synchronises cleanly and takes load, the jacket water and lube oil protections
operate, closing out of step shocks the machine, cold residual fuel blocks the
start, losing another set pulls the frequency down and droop picks it up, a bad
bunker blocks the filter, and nothing drifts to NaN over a six hour watch.

`ScreenRenderTest` composes every page under Robolectric. `ScreenshotTest` draws
each page into a PNG (set `-DshotDir=...`) so the panel layout can be checked off
device.
