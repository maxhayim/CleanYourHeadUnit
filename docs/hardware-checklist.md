# Hardware safety checklist

Run this on a real head unit before calling anything "tested" on it. **Parked, engine off or idling, handbrake on.** Have a computer with `adb` connected, and [RESTORE.md](../RESTORE.md) open.

Record the result of each step (pass, fail or not applicable) in a Compatibility report.

## A. Before anything changes

- [ ] A1. `adb devices` lists the unit; `cyhu status` identifies it. Note the Android version, chipset, MCU version, matched profiles and screen size.
- [ ] A2. `cyhu backup` succeeds; `~/.cleanyourheadunit/<unit>/baseline.json` exists.
- [ ] A3. `cyhu inventory --category protected` includes every vehicle app you can see on the unit (radio, Bluetooth/phone, camera/AVM, car settings, CAN/MCU, steering-wheel learning, EQ/DSP, climate). **Any vehicle app that is not Protected is a bug: stop and report it.**
- [ ] A4. `cyhu inventory --category unknown`: note the list in the report.
- [ ] A5. Baseline function check, before any change: radio, Bluetooth pairing and a hands-free call (mic heard by the other side), reversing camera on reverse gear, parking sensors, steering-wheel buttons, climate display/control (if on the unit), volume and audio sources, GPS fix, and Home after a cold start.

## B. Debloating

- [ ] B1. `cyhu debloat plan --category optional` lists only packages whose purpose is shown, with risk and restore.
- [ ] B2. `cyhu debloat apply --packages <one optional package>`: confirm prompt, applied, verified.
- [ ] B3. Repeat A5. Everything still works.
- [ ] B4. **Cold start**: ignition off long enough for the unit to fully sleep (or cut ACC), then on. Repeat A5. Check the package is still disabled (some firmware re-enables on boot).
- [ ] B5. `cyhu restore --package <it>` re-enables it; running it again says nothing to do.
- [ ] B6. `cyhu debloat apply --packages <a protected package>` is refused.
- [ ] B7. Repeat B2–B5 for a small batch of Optional packages.

## C. Launcher

- [ ] C1. `cyhu launcher list` shows the factory launcher as protected.
- [ ] C2. Install a catalog launcher (`cyhu install kitt-launcher-2` on landscape units) and confirm the checksum and signature checks pass.
- [ ] C3. Switch Home to it (Android's chooser or `cyhu launcher set`). Cold start (B4). The unit comes up on the new launcher, and the vehicle functions in A5 still work.
- [ ] C4. `cyhu restore --launcher` brings back the factory launcher. Cold start again.

## D. Privacy Shield

- [ ] D1. `cyhu privacy audit` lists only catalogued packages, each with a source. Note any `com.adups*`, `com.redstone*`, `com.tw.*` findings.
- [ ] D2. (Android 9+) `cyhu privacy dns adguard`; a website still loads; `cyhu restore --dns` restores the old value.
- [ ] D3. App firewall, DNS filter: Android asks for VPN permission; key icon and notification shown. Browse a little; the counters move. Navigation and online music still work. Stop from the notification; the icon disappears.
- [ ] D4. Firewall App block (Android 5+): a blocked app has no network; others do. Stop.
- [ ] D5. Reboot with the firewall running: it does **not** start again by itself.

## E. App

- [ ] E1. The app installs and opens; the layout fits the screen (no clipped buttons) in the unit's orientation.
- [ ] E2. Every page opens. Diagnostics export contains no serial or MAC address.
- [ ] E3. Backup & Restore lists the changes made in B–D, and Export restore instructions matches.
- [ ] E4. CleanYourHeadUnit Home: turn on, choose as Home, cold start, use **Change Home** to go back, turn off.

## F. Report

- [ ] F1. `cyhu restore --all`; A5 once more.
- [ ] F2. File a Compatibility report with `cyhu diagnostics --out report.json` (read it first), the results above, and the unit's make and model as sold.

A profile can become `tested` after a passing run of A–C and F on at least one named unit.
