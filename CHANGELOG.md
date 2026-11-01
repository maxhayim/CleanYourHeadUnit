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
- Themes: Midnight, Amber Dash, Daylight, plus an accent colour you choose (ten presets or any #RRGGBB) that works with every theme
- Tests: 44 Python tests and 18 Java tests (including shared classification vectors and screen renders at five resolutions)
- Docs: README, INSTALL, COMPATIBILITY, DEBLOAT, PRIVACY, MODULES, THEMES, COMMUNITY, CONTRIBUTING, SAFETY, RESTORE, CREDITS, SECURITY, plus the hardware checklist, test report, release readiness and specs

### Removed (from CleanYourTV)

- the Android TV launcher, add-ons, TV-specific tools and data (see [docs/reuse-audit.md](docs/reuse-audit.md))
