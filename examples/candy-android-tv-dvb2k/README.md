# CANDY Android TV DVB2K (CANDYATVT56, device broadway)

Haier-built, CVTE/MediaTek chassis sold under the Candy brand, 720p, armeabi-v7a, 934 MB RAM.
Android TV 11 (SDK 30). Stock launcher: `com.google.android.tvlauncher`. Live TV / HDMI app:
`com.mediatek.wwtv.tvcenter`, inputs via `com.mediatek.tvinput`. Default IME is Gboard
(`com.google.android.inputmethod.latin`), not the bundled `com.haier.tv.hmagicboard`.

## Factory-menu packages: never launch, never disable

This board ships Haier/CVTE factory-test packages: `com.at.factory.androidtvaidlservice`,
`com.at.factory.factorymanager`, `com.cvte.tv.at`, `com.cvte.tv.hkcfac`, `com.cvte.tv.fac.menu`,
`com.cvte.tv.fac.aging`, `mediatek.factorymenu.ui`. Opening any one of them, even once, can flip a
factory-mode flag and disable Play Services, Netflix and Chromecast on the next boot with no way
back from the shell. They are left out of `debloat.txt` on purpose. See the launcher fix below:
enabling `app_library` on this board surfaced these as "Add to Home" entries, which is the reason
the second code change exists.

## Firmware re-enables these at every boot

`com.haier.launcher.haiermatrix` (Haier TV+, an alternate launcher, not a HOME claimant),
`com.google.android.tvrecommendations` (drives the stock "Play Next" row),
`com.google.android.apps.mediashell` (Chromecast receiver) and `com.google.android.katniss` (voice
search) come back on at every boot regardless of `pm disable-user`. Left out of `debloat.txt` for
that reason; hidden in the launcher's `hidden_apps` instead.

## Launcher fix 1: Live TV dock tile opened Composite, not HDMI1

The Live TV tile launched `com.mediatek.wwtv.tvcenter` normally, which just reopened whatever input
it last remembered (Composite, on this unit) instead of going straight to HDMI1.
`live_tv_input_id` does not help here: in `MainActivity.java` it only matches
`TvInputInfo.TYPE_TUNER` inputs (broadcast tuners), never `TYPE_HDMI`, so pointing it at the HDMI1
component ID was a no-op.

Fixed by adding an `input:<id>` prefix to `launch_actions`, the same syntax `app_keys` already uses
for remote buttons, recognized in `AppAdapter.launch()` via a new `TileListener.onOpenInput()`
callback that reuses the existing `openInput()` path the Inputs menu's own HDMI rows already use.
With the switch off (no tile's `launch_actions` value starts with `input:`), behaviour is unchanged.
This unit's (local, not upstreamed) `config.xml` entry:
`com.mediatek.wwtv.tvcenter=input:com.mediatek.tvinput/.hdmi.HDMIInputService/HW5` (`HW5` =
`hdmi_port=1`, from `dumpsys tv_input`). Verified over two reboots that the tile opens `HW5`, not
`HW2`/Composite, and Home returns cleanly afterward.

## Launcher fix 2: App Library listed the factory-menu packages

With `app_library=true` on, the stock library catalogue lists every `LEANBACK_LAUNCHER` package
regardless of `hidden_apps`, which on this board meant "Factory Menu" and "HKCFactoryAT" showed up
as addable "Add to Home" entries despite being hidden from the dock and grid — see the danger note
above.

Fixed in `AppLibraryDialog`: it now takes the same `hidden_apps` set the dock/grid already use and
excludes those packages from the catalogue entirely, not just from Home. Verified the catalogue
dropped from 10 apps to 7, with Factory Menu / HKCFactoryAT / Haier TV+ no longer listed, and that
this survives a reboot.

## Performance

SoC is `mt5867` (MStar), same family as [tcl-32es570f](../tcl-32es570f/debloat.txt). Dexopt had
settled at `speed-profile`/`reason=install` after a couple of config-only reinstalls; recompiling
with `cmd package compile -m speed -f` (the same step `build-and-install.sh` runs after a fresh
install) fixed it. `dumpsys gfxinfo` in steady state: 0% janky frames, GPU frames 6-17ms. APK 545 KB
(R8 minify+shrink). No config changes were needed for performance; `cinematic_home`, `fade_rows` and
blur were already off for this RAM budget.

## Dock and config

This unit's dock order, `hidden_apps` and the `input:` entry above live in this builder's own
`config.xml`, not in this PR — see [CONTRIBUTING.md](../../CONTRIBUTING.md#2-improve-the-launcher).
