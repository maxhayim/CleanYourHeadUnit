# Fortdows for Toyota Camry 2012–2014 (JBL): FYT software on SC7731E

One owner's unit, checked in November 2026. The head-unit app runs on it, and an app was disabled through it with the car's functions checked afterwards (see Checks).

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

- 166 packages, 6 already disabled by the owner before CleanYourHeadUnit: `com.google.android.webview`, `com.sprd.powersavemodelauncher`, `com.syu.av`, `com.syu.canbusdvr`, `com.syu.carradio`, `com.syu.carui`. The radio and the reversing camera still work with these off, so this unit uses other apps for them. CleanYourHeadUnit classes the `com.syu` ones as Protected and would never offer them.
- Debloater: Recommended 0, Optional 6, Advanced 1, Unknown 90, Protected 65, the owner's apps 4.
- Optional included Default Print Service, Calculator, Basic Daydreams, Android Easter Egg, Print Spooler and Market Feedback Agent.
- Several Home apps share a name: two "AGAMA Car Launcher", three "Launcher3", plus `launcher20`, `launcher21` and `launcher22`. The Launchers page shows each one's package so they can be told apart.
- 1 Privacy Shield finding: Market Feedback Agent (`com.google.android.feedback`), now disabled.
- Private DNS isn't available: the unit really runs 8.1, and Private DNS needs Android 9.

The first run found three bugs, all fixed and confirmed on this unit in v0.1.0: the dashboard showed the claimed Android 13, the FYT profile's name listed other chips, and same-named launchers couldn't be told apart.

## Connecting

- **USB debugging** is on in Developer options. There's no **Wireless debugging** (it needs Android 11).
- The USB port is **host-only**: a phone plugged in is offered Android Auto. So ADB over USB isn't possible from a computer or from the phone companion.
- ADB over the network (port 5555) is closed.
- So the computer tool and the companion can't reach this unit. Changes go through Android Settings instead (see Disabling apps).

## Disabling apps

- **Some apps, from Android Settings.** CleanYourHeadUnit's **Disable in Android Settings** opens the app's page, and Android's **Disable** works for some apps: Market Feedback Agent (`com.google.android.feedback`, the unit's one privacy finding) was disabled this way (2026-11-09). For others the button is greyed out, for example Android Easter Egg (`com.android.egg`). Those can't be changed here, since there's no ADB (see Connecting).
- Optional on this unit (6): Default Print Service, Calculator, Basic Daydreams, Android Easter Egg, Print Spooler, Market Feedback Agent (`com.google.android.feedback`, the one privacy finding: Google's feedback reporter).
- Counts in the Debloater: Recommended 0, Optional 6, Advanced 1, Unknown 90, Protected 65, user apps 4.

## Checks

After Market Feedback Agent was disabled through the app (2026-11-09):

| Check | Result |
|---|---|
| Disable through **Disable in Android Settings** | Passed (Market Feedback Agent) |
| Open-door display | Works |
| Steering-wheel buttons | Work |
| Climate display | Works |
| Radio | Works |
| Reversing camera | Works |
| Bluetooth call | Not checked yet |
| Still disabled after an ignition off/on | Not checked yet |
| Restore (Backup → App info → Enable) | Not checked yet |

## Works on this unit

- **CleanYourHeadUnit** (the head-unit app): installs from a flash drive or LocalSend, runs, fits the screen.
- **Check for updates**: updated an earlier release to 0.1.3 in place over the unit's Wi-Fi (2026-11-09). Later updated to 0.1.4. Automatic updates arrived in 0.1.3; whether one has run here isn't confirmed.
- **LocalSend**: receives APKs sent from a phone, which then install.
- **AGAMA Car Launcher**: the daily Home app.

## Protected on this unit

The firmware's vehicle apps (`com.syu.*`: MCU, CAN, car settings, door display, reversing camera, radio, Bluetooth) are Protected by the FYT profile and the vehicle rules. The factory amplifier is JBL, driven through the CAN decoder, so audio routing must never be touched.
