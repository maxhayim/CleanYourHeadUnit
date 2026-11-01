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
| FYT | `com.syu.ms` installed | unverified | Android 10, 1280×720 |
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

## Reporting a unit

Please use the **Compatibility report** issue template and attach `cyhu diagnostics` output, which is sanitised: serial numbers, MAC addresses and account and network names are removed. Read it before you post it. Say which checklist items you ran and what happened, including anything that *didn't* work.
