# Debloating

## What "debloat" means here

A package is **disabled for user 0**: `adb shell pm disable-user --user 0 <package>`. Its code and data stay on the unit, it stops running and its icon disappears. `pm enable --user 0 <package>` brings it back exactly as it was. Nothing is ever uninstalled, and no data is cleared.

## Categories

| Category | Meaning | Offered? |
|---|---|---|
| **Recommended** | Verified unnecessary on a *tested* device profile. | Yes. Today nothing qualifies, because no profile is tested. |
| **Optional** | Known purpose. Disable it if you don't use what it does (Email, Calculator, Print spooler…). | Yes |
| **Advanced** | Needs care: usually an updater or a documented telemetry client. Read the risk first. | Only with `--advanced`, or one at a time in the app |
| **Protected** | Android core and vehicle functions (see [SAFETY.md](SAFETY.md)). | **Never** |
| **Unknown** | Purpose not verified. | **Never** (except one at a time with `--allow-unknown`) |
| Your app | Something you installed. | Not part of debloating |

## How a package gets its category

In this order. The first rule that matches wins:

1. **Android core** (`android`, `com.android.systemui`, settings, providers, telephony, Bluetooth, input methods, WebView, and others). This can't be overridden by anything. The rules come from CleanYourTV's core list, minus its TV-specific middleware, plus radios, telephony, input and WebView.
2. **Home apps found on this unit**: every app that can be Home, and the current Home.
3. **Profile-protected**: for example, everything under `com.syu.*` on FYT.
4. **The telemetry catalogue**: packages with published evidence get the category the catalogue gives them (usually Advanced) and a privacy finding. This is the only thing matched before the vehicle rules, and only with published sources (for example DoFun's `com.tw.core`, documented as malware).
5. **Vehicle namespaces**: `com.syu.*`, `com.microntek.*` / `android.microntek.*`, `com.tw.*`.
6. **Vehicle words in the package name**, outside the `com.android.*` / `com.google.android.*` / `android.*` namespaces: `mcu`, `canbus`, `can`, `canbox`, `car`, `carinfo`, `door`, `trunk`, `seatbelt`, `cluster`, `instrument`, `bcm`, `chime`, `avm`, `dvr`, `swc`, `obd`, `radio`, `tuner`, `bt`, `bluetooth`, `phone`, `hvac`, `climate`, `aircon`, `camera`, `reverse`, `parking`, `amp`, `dsp`, `eq`, `audio`, `factory`, `carsetting`, `update`, `ota` and others. `car`, `can` and `bcm` also match as the start of a word (`carstatus`, `canbox`, `bcmservice`). The full list and how it matches are in `data/protected.json`.
7. **Profile entries**: packages a profile documents. They come after the vehicle rules, so a profile can never make a vehicle app removable.
8. A package you installed is **Your app**.
9. Anything else is **Unknown**.

A profile can protect more and document more, but it can't make an Unknown or Protected package removable.

**Door, trunk and car-status warnings.** The on-screen "door open" warnings, parking overlays and car information pages come from the unit's CAN bus app (`com.syu.canbus` on FYT, the `microntek` CAN apps on MTCD/MTCE, `com.tw.*` on DoFun) or a vendor app with a name like those above. They are all Protected: never offered, never selectable with `--allow-unknown` or `--advanced`, and never blockable by the firewall's App block mode. The shared tests check this for both the computer tool and the app, including a "tested" profile that tries to list them as removable. Only a profile with `"status": "tested"` can make something Recommended.

## Commands

```
python3 tools/cyhu.py inventory [--category unknown]
python3 tools/cyhu.py debloat plan --category optional
python3 tools/cyhu.py debloat plan --category advanced --advanced
python3 tools/cyhu.py debloat apply --packages com.android.egg,com.android.email
python3 tools/cyhu.py debloat apply --category optional --dry-run
python3 tools/cyhu.py --firmware sergey-kozlov debloat plan --category optional
```

`apply`:

1. Re-reads the unit and re-classifies every package. A plan made earlier can't sneak in a package that's Protected now.
2. Refuses Protected, Unknown (unless `--allow-unknown`), already-disabled and missing packages, and says why for each.
3. Shows the plan and asks you to type `yes` (or takes `--yes`).
4. Records the before state, disables, and reads the state back. A package the firmware refused, or re-enabled straight away, is reported as failed and not counted.

## In the app

The **Debloater** page lists packages by category. Tap one for its purpose, action, confidence, risk and restore command. Because Android doesn't let an app disable system packages without ADB, the app:

- opens the package's page in Android Settings (some firmware offers a Disable button there), and
- builds a **plan** you export as a `cyhu` command, plus plain `adb` lines and the matching `pm enable` lines.

## Adding knowledge

The best way to make debloating better is a device profile from a real unit. See [CONTRIBUTING.md](CONTRIBUTING.md#device-profiles).
