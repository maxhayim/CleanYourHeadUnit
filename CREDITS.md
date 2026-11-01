# Credits

## Upstream

**CleanYourHeadUnit is derived from [CleanYourTV](https://github.com/Gathin23/cleanyourtv) by [@Gathin23](https://github.com/Gathin23)** (MIT). This repository keeps CleanYourTV's full git history up to commit `f47c893d`, and CleanYourTV's copyright notice in [LICENSE](LICENSE). CleanYourTV is not affiliated with CleanYourHeadUnit and has not endorsed it.

What came from CleanYourTV, and what was changed, is listed file by file in [docs/reuse-audit.md](docs/reuse-audit.md). In short:

- **the approach**: non-root, reversible `pm disable-user`, explain every package, back up first, restore idempotently
- **the core protected-package rules**: CleanYourTV's core list, minus its TV middleware, plus radios, telephony, input methods and WebView
- **the privacy catalogue approach**: findings with evidence and sources, and the `com.google.android.feedback` entry
- **the module card category**: `com.gathin.tvhome.category.DOCK_CARD` is recognised so existing CleanYourTV cards show up

## Catalog projects

| Project | Developer | Licence |
|---|---|---|
| [KITT Launcher](https://github.com/tdater/KITT-Launcher-releases) | [@tdater](https://github.com/tdater) | Proprietary (all rights reserved). Linked, never redistributed |
| [KITT Launcher.2](https://github.com/tdater/KITT-Launcher.2-releases) | [@tdater](https://github.com/tdater) | Proprietary (all rights reserved). Linked, never redistributed |
| [RetroDeck](https://github.com/maxhayim/RetroDeck) | [@maxhayim](https://github.com/maxhayim) | MIT |
| [Webmote](https://github.com/maxhayim/webmote) | [@maxhayim](https://github.com/maxhayim) | MIT |
| Custom firmware for Tesla-style head units | [Sergey Kozlov](https://boosty.to/teslafw) (Boosty; no GitHub username verified) | Paid firmware from the developer. Never redistributed; only his official page is linked |

## Research cited

- Kryptowire, *Mobile Phone Firmware that Transmitted Personally Identifiable Information* (Nov 2016): Adups FOTA
- Malwarebytes Labs: Adups (2016) and the Redstone auto-installer (2021)
- Kaspersky Securelist (2026): DoFun head units, TWCore and JarService
- [@PimpinPumpkin/fyt7862-mods](https://github.com/PimpinPumpkin/fyt7862-mods) and the XDA head-unit forums: FYT and MTC package roles

## Assets

- **Outfit** typeface: The Outfit Project Authors, SIL Open Font License 1.1 ([app/FONT-LICENSE-OFL.txt](app/FONT-LICENSE-OFL.txt))
- Icons and logo: drawn for this project (MIT)
- Block lists, downloaded only when you choose one: [StevenBlack/hosts](https://github.com/StevenBlack/hosts) (MIT), [AdGuard DNS filter](https://github.com/AdguardTeam/AdGuardSDNSFilter) (GPL-3.0)

## CleanYourHeadUnit

[@maxhayim](https://github.com/maxhayim)
