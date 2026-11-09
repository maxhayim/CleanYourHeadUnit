# Changelog

All notable changes to this project are listed here. Versions follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

First version of CleanYourHeadUnit, derived from CleanYourTV `f47c893d` by @Gathin23. Not yet released or tested on a real head unit.

### Added

- `cyhu`, a computer tool over ADB:
  - status, inventory, debloat plan/apply, restore, backup
  - Privacy Shield audit/profile/DNS, launcher list/set/recommend
  - catalog list/show/validate, verified install, sanitised diagnostics, profiles
  - `--demo` with simulated units
- The Android app (Android 4.4+):
  - dashboard, debloater with exportable plans, Privacy Shield (findings, permission audit, Private DNS, local firewall with DNS filter and App block modes, profiles)
  - launchers, themes, modules, Community Hub, diagnostics, backup & restore, settings
  - optional CleanYourHeadUnit Home launcher
- Shared rules in `data/`: protected packages (core, vehicle namespaces, vehicle keywords), telemetry catalogue with sources, device profiles (FYT, MTCD/MTCE, DoFun/TW, ALPS/YT9216, Sergey Kozlov, generic), DNS providers, the module/launcher catalog
- Door, trunk, seatbelt and car-status apps protected by name on any unit (`door`, `canbox`, `cluster`, `instrument`, `bcm`, and `car`/`can` as word starts); vehicle rules now come before device-profile entries, and the firewall skips Protected apps when it starts
- CleanYourHeadUnit Companion, a phone app (Android 7.0+): connects over USB (OTG) or Wi-Fi with its own ADB client (written from AOSP's protocol description), reads the unit (including the real Android version when the settings claim another), plans, applies and restores with the shared rules and the same record files as `cyhu`, and works as a bridge (`tcpip 5555`) for units without Wireless debugging. It never offers Unknown apps. 20 tests against a simulated adbd and a simulated unit. Not yet tested on real hardware.
- The classifier (`Rules.java`) and data loader moved to `shared/java`, used by both apps
- Recommended apps (RetroDeck, Webmote, LocalSend) at the top of the Community Hub and in the README; LocalSend added to the catalog (links only, since it ships one APK per processor)
- The real Android version (from the API level) everywhere: the app's dashboard, `cyhu status` and the companion show "8.1 (Settings say 13)" on units that edit the version string. Found on the first real unit.
- Launchers show each Home app's package, since several often share one name, and label Android's start-up screen as not a launcher
- The FYT profile is just "FYT firmware"; its chip list gains SC7731E / UIS8141E, seen on a real unit
- LocalSend marked as working on a real unit: the Camry received an APK sent from a phone and installed it
- AGAMA Car Launcher in the launcher catalog and Recommended apps (Google Play link only; closed source; works on the Camry)
- Themes: Midnight, Amber Dash, Daylight, plus an accent colour you choose (ten presets or any #RRGGBB) that works with every theme
- Tests: 47 Python tests, 21 Java tests for the head-unit app and 20 for the companion (including shared classification vectors and screen renders at five resolutions)
- Docs: README, INSTALL, COMPATIBILITY, DEBLOAT, PRIVACY, MODULES, THEMES, COMMUNITY, CONTRIBUTING, SAFETY, RESTORE, CREDITS, SECURITY, plus the hardware checklist, test report, release readiness and specs

### Removed (from CleanYourTV)

- the Android TV launcher, add-ons, TV-specific tools and data (see [docs/reuse-audit.md](docs/reuse-audit.md))
