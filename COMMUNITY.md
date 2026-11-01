# Community Hub

The Community Hub lists projects that work with head units, credits the people who make them, and shows honestly what has been tested. It's built from `data/catalog.json` and appears in the app's **Community** page and in `cyhu catalog list`.

## Developers

People are credited by **GitHub username** wherever one is verified. A real name is used only where no GitHub account could be verified, and the entry says so.

| Developer | Projects |
|---|---|
| [@Gathin23](https://github.com/Gathin23) | CleanYourTV (the project this one is derived from) |
| [@maxhayim](https://github.com/maxhayim) | CleanYourHeadUnit, RetroDeck, Webmote |
| [@tdater](https://github.com/tdater) | KITT Launcher, KITT Launcher.2 |
| [Sergey Kozlov](https://boosty.to/teslafw) (Boosty; no GitHub username verified) | custom firmware for Tesla-style head units |

## Labels

| Label | Meaning |
|---|---|
| Verified | Tested on a named head unit, with a linked report |
| Community | Reported working by users; not checked by the project |
| Untested | No head-unit report yet |
| Planned | Head-unit support isn't released yet |

Every listing today is **Untested** or **Planned**.

## Submitting

Use the issue templates. Each one asks for exactly what a listing needs:

- **Launcher submission**: GitHub username, official repository and releases, package name, signing-certificate SHA-256, licence, orientations, minimum Android, screen sizes, real screenshots
- **Module submission**: the same, plus what it does in a car
- **Theme submission**: `theme.json`, a preview, and the launcher and version it targets
- **Device profile**: see [CONTRIBUTING.md](CONTRIBUTING.md#device-profiles)
- **Compatibility report**: a unit, the checklist items run, and diagnostics

Submissions are checked with `python3 tools/cyhu.py catalog validate --submission entry.json` (catalog entries) or `python3 tools/cyhu.py catalog validate` (themes in the repository).

## What a listing is not

- not an endorsement, certification or partnership
- not a mirror: downloads come from the developer's own releases
- not permanent: entries whose official source disappears are marked or removed

Developers who'd rather not be listed can open an issue or email the maintainer, and the entry will be removed.
