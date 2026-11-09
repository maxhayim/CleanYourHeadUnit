# Changelog

All notable changes to this project are listed here. Versions follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Changed

- Settings → About → **Updates** is now Off / Notify only / Automatic, replacing the single "Check when the app opens" switch (On becomes Notify only). Off stays the default.
- **Automatic** downloads a newer release when the app opens (at most once a day), checks GitHub's checksum and the signature, and installs it through Android's PackageInstaller. Android 12 and later install it without a prompt once CleanYourHeadUnit installed one of its own updates; older Android asks, and only while the app is on screen, never over the radio or camera. The last result shows under Updates and on the Dashboard card.

## [0.1.2] - 2026-11-09

### Added

- Diagnostics → **Report this unit**: opens GitHub's Compatibility report form in the browser, filled in with the model, chip, real and claimed Android, firmware, MCU, screen and package counts per category. It shows exactly what goes in first, never includes serials, MAC or IP addresses, or network and account names, and sends nothing itself: the owner adds the car and presses Submit. Without a browser it saves the report to a file instead.
- The Compatibility report form asks for the car (make, model, years)

## [0.1.1] - 2026-11-08

### Added

- Settings → About → **Check when the app opens** (off by default): at most once a day, asks GitHub for a newer release and shows an "Update available" card on the Dashboard. It never downloads or installs by itself; Update still asks, and so does Android. Turning it off clears the note.
- `docs/units/`: a page per real head unit, grouped by firmware family (first: the Fortdows Camry unit)

## [0.1.0] - 2026-11-08

First version of CleanYourHeadUnit, derived from CleanYourTV `f47c893d` by @Gathin23. A pre-release: the head-unit app has run read-only on one real unit (a 2012–2014 Camry with FYT software); nothing has been disabled on real hardware yet. The phone companion was released separately as `companion-v0.1.0` on 2026-11-05.

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
- Check for updates (Settings → About): only on request, only from this project's GitHub releases, checksum and same-signature checks, then Android's installer
- Themes: Midnight, Amber Dash, Daylight, plus an accent colour you choose (ten presets or any #RRGGBB) that works with every theme
- Tests: 47 Python tests, 23 Java tests for the head-unit app and 20 for the companion (including shared classification vectors and screen renders at five resolutions)
- Docs: README, INSTALL, COMPATIBILITY, DEBLOAT, PRIVACY, MODULES, THEMES, COMMUNITY, CONTRIBUTING, SAFETY, RESTORE, CREDITS, SECURITY, plus the hardware checklist, test report, release readiness and specs

### Removed (from CleanYourTV)

- the Android TV launcher, add-ons, TV-specific tools and data (see [docs/reuse-audit.md](docs/reuse-audit.md))
