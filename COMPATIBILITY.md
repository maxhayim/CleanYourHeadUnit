# Compatibility

**No real head unit has been tested yet.** This page says what the code supports, what it has been tested against (simulated units only), and what's still unknown. It will only claim a unit works once someone has run the [hardware checklist](docs/hardware-checklist.md) on it and filed a [compatibility report](https://github.com/maxhayim/CleanYourHeadUnit/issues/new/choose).

## Android versions

| Android | App | `cyhu` | Notes |
|---|---|---|---|
| 4.4 (API 19) | Yes | Yes | No Private DNS. Firewall: DNS filter only. `set-home-activity` isn't available, so Home is changed through Android's chooser. |
| 5.0–6.0 | Yes | Yes | Firewall App block mode available from 5.0. |
| 7.0–8.1 | Yes | Yes | `cmd package set-home-activity` available. |
| 9–10 | Yes | Yes | Private DNS. |
| 11–15 | Yes | Yes | The app declares `QUERY_ALL_PACKAGES` to list packages. |

*Yes* means the code path exists and passes tests on simulated units. It hasn't been run on a head unit with that Android version.

## Firmware families (device profiles)

| Profile | Recognised by | Status | Simulated test unit |
|---|---|---|---|
| FYT | `com.syu.ms` installed | unverified (recognised correctly on one real unit, read-only) | Android 10, 1280×720 |
| MTCD/MTCE | a `microntek` package installed | unverified | none |
| DoFun / TW | `com.tw.core` installed | unverified | Android 12, 1080×1920 portrait |
| ALPS / YT9216 | `yt9216` in build properties | unverified | Android 8, 1024×600 |
| Sergey Kozlov firmware | declared by you | experimental | none |
| Generic Android | always | generic | Android 4.4, 800×480 |

*Unverified* means the profile is built from public sources (cited in each `data/profiles/*.json`), not from a unit in hand. *Experimental* (Sergey Kozlov) also means launcher changes come with a warning and nothing becomes Recommended, because the firmware is customised and its internals aren't documented publicly.

## Screens

The layout adapts between a side rail (landscape), an icon-only rail (under 900dp wide, e.g. 800×480) and a bottom bar (portrait). The test suite renders every screen at 800×480, 1024×600, 1280×720, 1920×720 and 1080×1920 portrait. The renders are in [docs/screenshots](docs/screenshots).

## Catalog entries

| Entry | Developer | Head-unit status |
|---|---|---|
| CleanYourHeadUnit Home | @maxhayim | untested on head units |
| KITT Launcher (portrait) | @tdater | untested on head units |
| KITT Launcher.2 (landscape) | @tdater | untested on head units |
| RetroDeck | @maxhayim | untested on head units (tested by its developer on an Android TV) |
| Webmote | @maxhayim | automotive support planned |
| AGAMA Car Launcher | altercars.ru | verified on one unit: the Camry's daily launcher (FYT, Android 8.1) |
| LocalSend | @localsend | verified on one unit: the 2012–2014 Camry (FYT, Android 8.1) received and installed an APK sent from a phone |

## Real units

One page per unit, grouped by firmware family: [docs/units](docs/units/README.md).

| Unit | Software | Real Android | What was checked | Result |
|---|---|---|---|---|
| Fortdows, Toyota Camry 2012–2014, 10.1" 1280×800 | FYT (`com.syu.ms`), SC7731E / UIS8141E, 2 GB | 8.1 (API 27); Settings say 13 | Head-unit app, read-only: Dashboard, Debloater, Launchers | Runs and fits the screen; FYT recognised; 166 packages: 65 Protected, 90 Unknown, 6 Optional, 1 Advanced. Its USB port is host-only (Android Auto), so ADB over USB from a computer or the phone companion isn't possible. No changes made. Second run (commit 11ac7311): real version "8.1, Settings say 13", "FYT firmware" and launcher packages confirmed on the unit. |

## Reporting a unit

Please use the **Compatibility report** issue template and attach `cyhu diagnostics` output, which is sanitised: serial numbers, MAC addresses and account and network names are removed. Read it before you post it. Say which checklist items you ran and what happened, including anything that *didn't* work.
