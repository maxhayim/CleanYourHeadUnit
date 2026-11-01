# Release readiness

**Verdict: not ready for a public release.** The code is complete enough for a first pre-release, and every automated test passes, but no part of it has run on a head unit. A head unit is part of a car, so the [hardware checklist](hardware-checklist.md) has to pass on at least one real unit first.

## Feature status

Labels:

- **Implemented, tested**: automated tests pass (simulated units, JVM). It has *not* been run on hardware.
- **Implemented, untested**: code exists but has no automated test.
- **Partial**: some of the feature is missing.
- **Planned**: not built.
- **Blocked**: can't be done within the project's rules.

Nothing here is tested on hardware.

| Area | Feature | Status |
|---|---|---|
| Debloat | Categories, classifier, profiles, refusals | Implemented, tested |
| | `cyhu debloat plan/apply` with confirm, record, verify | Implemented, tested |
| | Dry run, per-package explanation | Implemented, tested |
| | App debloater page, plan export | Implemented, tested (UI render) |
| | Disable from the app without ADB | **Blocked**: Android doesn't allow it. The app opens App info instead |
| Backup/restore | Baseline, change log, per-item and full restore, idempotent, "changed since" | Implemented, tested |
| | Emergency `adb` procedure, `restore --script` | Implemented, tested (script); procedure documented |
| | App snapshot and comparison | Implemented, tested (UI render). Restore in the app opens Android settings |
| Privacy Shield | Telemetry catalogue with sources | Implemented, tested |
| | Permission audit | Implemented, tested |
| | Private DNS (CLI, and app with ADB-granted permission) | Implemented, tested (CLI); app path untested |
| | Standard/Strict/Custom profiles | Implemented, tested |
| | Local firewall: DNS filter | Packet code tested; **the running VpnService is untested** |
| | Local firewall: App block | Implemented, untested |
| | Block list download/import, allowlist, export/import | Implemented, untested (list parsing tested) |
| | Per-app network activity | **Blocked**: not observable over ADB without root |
| Launchers | List, set, undo, "back to original" (CLI) | Implemented, tested |
| | App launcher page, orientation-aware catalog | Implemented, tested (UI render) |
| | CleanYourHeadUnit Home | Implemented, tested (render); use as Home untested |
| Catalog | Validation, developer credit | Implemented, tested |
| | GitHub-only verified install (CLI) | Partial test: asset choice, origin, digest and certificate checks tested; GitHub API call and download not run |
| | App install path (download, digest, cert pin, installer) | Implemented, untested |
| | Module card discovery | Implemented, untested |
| | Live module cards on Home | Planned ([spec](specs/module-card.md)) |
| Themes | Schema, validation, three themes, import/export | Implemented, tested |
| | Wallpapers, third-party launcher themes | Planned |
| Diagnostics | Sanitised report (CLI and app) | Implemented, tested |
| UI | Landscape rail, compact rail, portrait bottom bar, 800×480 → 1920×720 | Implemented, tested (renders) |
| Docs | All required documents, specs, templates, CI | Done; CI passing |

## Before a first pre-release

1. Run the hardware checklist on at least one unit, ideally one FYT and one other family. Fix what it finds.
3. Create a release signing key for the app (own key, no paid certificate) and build a signed release APK.
4. Decide the version (suggested `0.1.0`) and update CHANGELOG.
5. Re-render screenshots, and add real photos of a unit if the owner agrees. Check them for personal information.

## Needs the owner's decision or authorization

- **Creating `github.com/maxhayim/CleanYourHeadUnit` and pushing.** Not done: it needs explicit authorization. The local repository has upstream's history plus this work. A GitHub *fork* isn't possible because the account already has a fork of cleanyourtv, so this would be a new repository, with `upstream` kept as a remote.
- **Telling @Gathin23** (optional, courteous), and **asking @tdater** whether they're happy with the KITT listings and the in-app download from their releases.
- **Testing on a real unit**: needs a unit and the owner's go-ahead.
- **Prismor egress**: several research sites (securelist.com, mullvad.net, adguard-dns.io, privacyguides.org, xdaforums.com) were blocked from this machine. Facts from them come from search summaries and are cited, but weren't read in full here. In particular, cardoor.cn is flagged in `data/telemetry.json` as not confirmed against Kaspersky's IOC list.
