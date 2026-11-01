# Reuse audit: CleanYourTV → CleanYourHeadUnit

Upstream: [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv) at `f47c893d` (MIT, by @Gathin23). This repository contains upstream's full history up to that commit. CleanYourHeadUnit's work is the commits on top of it. Nothing was pushed to or changed in upstream.

## Kept, as is or adapted

| Upstream | Here | How |
|---|---|---|
| `LICENSE` (MIT) | `LICENSE` | Upstream notice kept verbatim. CleanYourHeadUnit copyright added below it, with a derivation and no-endorsement note. |
| `tools/disable.sh` `CORE_RE` (the core protected list; upstream issues #884, #1711, #2557, #2803) | `data/protected.json` → `core` | Adapted: TV middleware entries removed; radios, telephony, input methods and WebView added. Credited in the file. |
| `tools/disable.sh` "disable-user, never uninstall; `pm enable` to undo" (#165) | `tools/cyhu/adb.py`, `debloat.py`, `restore.py` | Same mechanism, rewritten in Python with recording and verification. Credited in `adb.py`. |
| `tools/privacy.py` catalogue approach (finding + evidence + sources), and the `com.google.android.feedback` entry | `data/telemetry.json` | Approach and that one entry reused. All head-unit entries are new, with their own sources. |
| Launcher module cards (`com.gathin.tvhome.category.DOCK_CARD`) | `Screens.Modules` | Category recognised, so existing CleanYourTV cards appear. Nothing from the TV launcher's code is used. |
| `addons/photo-prints/.../FONT-LICENSE-OFL.txt` | `app/FONT-LICENSE-OFL.txt` | Same file (OFL 1.1). The variable `outfit.ttf` was used at first and replaced by the static Regular and SemiBold files from the Outfit project's repository, because Android before 8.0 renders a variable font at its default weight (Thin). |
| `addons/*` Gradle wrapper, `gradle.properties`, `settings.gradle` | repository root | Build scaffolding, edited for this project. |
| `addons/photo-prints/.../values/themes.xml` | `app/src/main/res/values/styles.xml` | Reduced to a no-action-bar theme. |
| `.gitignore` | `.gitignore` | Extended (signing files). |

## Removed

Everything TV-specific:

- `launcher/`: the Android TV launcher
- `addons/`: about 50 TV add-on apps
- `examples/`, `themes/` (TV themes), `docs/`, `tests/`, `tools/` (the TV scripts: disable/restore/theme/trace/screendiff…)
- `.github/`, the translated READMEs, `SHOWCASE.md`, `AGENTS.md`, `CLAUDE.md`, `GEMINI.md`, `PROMPT.md`

They remain in this repository's git history. Git's rename detection pairs a few empty or near-identical files (for example an empty `proguard-rules.pro` with `tools/cyhu/__init__.py`). Those aren't real reuse.

## New

Everything else: `tools/cyhu/` (the computer tool), `app/` (the Android app), `data/` (except the core list and the feedback entry noted above), `themes/`, `tests/`, and all documentation.

## Licences of things referenced but not included

| Item | Licence | How it's used |
|---|---|---|
| KITT Launcher, KITT Launcher.2 (@tdater) | Proprietary, all rights reserved | Linked; downloaded from @tdater's official GitHub releases at the user's request; never mirrored |
| RetroDeck, Webmote (@maxhayim) | MIT | Linked; downloaded from official releases |
| Sergey Kozlov firmware | Not published under an open licence | Described; his official Boosty page is linked. Never downloaded or redistributed |
| StevenBlack hosts | MIT | Downloaded on the user's request |
| AdGuard DNS filter | GPL-3.0 | Downloaded on the user's request; not bundled |
