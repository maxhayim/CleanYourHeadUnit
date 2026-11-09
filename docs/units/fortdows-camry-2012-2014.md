# Fortdows for Toyota Camry 2012–2014 (JBL): FYT software on SC7731E

One owner's unit, checked in November 2026. The head-unit app ran read-only. Nothing has been disabled on this unit.

| Identifier | Observed value |
|---|---|
| Retail | Fortdows 10.1" for Toyota Camry 2012–2014 with JBL (Amazon B0DZTCD6TR) |
| ADB product manufacturer / model | `sprd` / `sp7731e_1h10_native` |
| Chip | UIS8141E (baseband `sc7731e`), quad-core 1.3 GHz, `armeabi-v7a` (32-bit) |
| Memory / storage | 2 GB / 64 GB |
| Screen | 1280×800, 160 dpi, landscape |
| Android | **8.1 (API 27)**, build `OPM2.171019.012`. Settings → About says **Android 13**. |
| MCU (About screen) | `NOR_815_825_F15_GD15P32F32` Ver 3.0; CAN protocol `V3.28.139BYPS` |
| Firmware family | FYT (`com.syu.ms` installed) |
| Home | AGAMA Car Launcher (`altergames.carlauncher`) |

## What CleanYourHeadUnit saw

- 166 packages, 6 already disabled by the seller's firmware.
- Debloater: Recommended 0, Optional 6, Advanced 1, Unknown 90, Protected 65, the owner's apps 4.
- Optional included Default Print Service, Calculator, Basic Daydreams, Android Easter Egg, Print Spooler and Market Feedback Agent.
- Several Home apps share a name: two "AGAMA Car Launcher", three "Launcher3", plus `launcher20`, `launcher21` and `launcher22`. The Launchers page shows each one's package so they can be told apart.
- 1 Privacy Shield finding (not yet reviewed).
- Private DNS isn't available: the unit really runs 8.1, and Private DNS needs Android 9.

The first run found three bugs, all fixed and confirmed on this unit in v0.1.0: the dashboard showed the claimed Android 13, the FYT profile's name listed other chips, and same-named launchers couldn't be told apart.

## Connecting

- **USB debugging** is on in Developer options. There's no **Wireless debugging** (it needs Android 11).
- The USB port is **host-only**: a phone plugged in is offered Android Auto. So ADB over USB isn't possible from a computer or from the phone companion.
- ADB over the network (port 5555) is closed.
- So far, the computer tool and the companion can't reach this unit, and nothing can be disabled on it.

## Disabling apps

- **Not possible without root on this unit.** In Android Settings → Apps, **Disable** is greyed out for built-in apps (tried with Android Easter Egg, `com.android.egg`, 2026-11-09). With no ADB either (see Connecting), CleanYourHeadUnit can only show and plan here, not change anything.
- Optional on this unit (6): Default Print Service, Calculator, Basic Daydreams, Android Easter Egg, Print Spooler, Market Feedback Agent (`com.google.android.feedback`, the one privacy finding: Google's feedback reporter).
- Counts in the Debloater: Recommended 0, Optional 6, Advanced 1, Unknown 90, Protected 65, user apps 4.

## Works on this unit

- **CleanYourHeadUnit** (the head-unit app): installs from a flash drive or LocalSend, runs, fits the screen.
- **Check for updates**: updated an earlier release to 0.1.3 in place over the unit's Wi-Fi (2026-11-09). Automatic updates arrived in 0.1.3 and haven't run here yet.
- **LocalSend**: receives APKs sent from a phone, which then install.
- **AGAMA Car Launcher**: the daily Home app.

## Protected on this unit

The firmware's vehicle apps (`com.syu.*`: MCU, CAN, car settings, door display, reversing camera, radio, Bluetooth) are Protected by the FYT profile and the vehicle rules. The factory amplifier is JBL, driven through the CAN decoder, so audio routing must never be touched.
