# Safety

A head unit is part of a car. These rules come before every feature.

## Rules the code enforces

- **Use it while parked.** The app shows this on every page that can change something, and `cyhu` prints it before every change.
- **Nothing is uninstalled.** Packages are disabled for user 0 (`pm disable-user --user 0`). That can always be undone with `pm enable --user 0`, even from a computer if the unit won't boot to the launcher (see [RESTORE.md](RESTORE.md)).
- **Protected packages are never offered**, whatever a profile, a category flag or a typed package name says. `cyhu debloat apply --packages` refuses them and tells you why. Protected includes:
  - Android core: system UI, settings, package installer, telephony, Bluetooth, input methods, WebView, providers, and others
  - MCU communication and CAN bus services
  - HVAC, air conditioning and climate controls
  - reversing cameras (AVM/360) and parking sensors
  - steering-wheel controls
  - factory radio, amplifiers, DSP/EQ and audio routing
  - Bluetooth hands-free calling and the microphone
  - vehicle settings and car information displays
  - manufacturer configuration utilities ("factory settings", "car settings")
  - startup and recovery services, emergency-related services
  - essential firmware update infrastructure, unless the telemetry catalogue documents a specific updater, and then it's **Advanced**, never automatic
  - every app that can be the Home app on this unit
- **Unknown packages are never offered.** A vendor app without a documented purpose may be the one that talks to the car. The only way to touch one is `cyhu debloat apply --allow-unknown <package>`, one package at a time, typed by you, with a warning.
- **Recommended needs a tested profile.** No profile is tested yet, so nothing is Recommended.
- **The factory launcher is never disabled** to force another launcher. Switching Home uses Android's own Home setting, and the original is recorded for "back to original".
- **Nothing talks to the MCU, CAN bus, ECUs or firmware.** No flashing, no `/dev` access, no MCU commands, no CAN messages.
- **No Android security bypasses.** No root, no exploits, no hidden APIs used to change anything. The app reads one non-public property API (`SystemProperties`) only to show build information, and works without it. Setting Private DNS from the app needs `WRITE_SECURE_SETTINGS`, which only you can grant, from a computer.
- **The local firewall** needs Android's VPN permission. It shows a notification with Stop for as long as it runs, never starts at boot, never intercepts HTTPS, installs no certificates and never reads browsing content. It warns you before replacing another VPN.

## What you should still do

1. Run `cyhu backup` (or open the app once) before changing anything. That's your "before".
2. Change a few packages at a time. Then test what you use: reversing camera, radio, Bluetooth calls, steering-wheel buttons, climate (if the unit controls it), and a cold start after the ignition has been off.
3. If anything misbehaves, run `cyhu restore --all`, or follow [RESTORE.md](RESTORE.md).
4. Don't change anything you can't recover from without the touchscreen. Keep a computer with `adb` available the first time.

## Reporting a safety problem

If a package that controls a vehicle function wasn't protected, please open a [compatibility report](https://github.com/maxhayim/CleanYourHeadUnit/issues/new/choose) with the package name and what stopped working. That's the most valuable bug report this project can get.
