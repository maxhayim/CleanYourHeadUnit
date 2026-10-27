# TCL C06 "Smart TV", Android TV 11 — debloat + launcher, restore and build notes

Unit: device `C06`, product `C06_2K_GB`, build `AR2101` / `AR14`, MediaTek mt5867 (MT9221), 1 GB RAM,
1280x720 @ 213 dpi, Vietnam. Stock home: Google TV (`com.google.android.tvlauncher`), which this
setup disables in favour of `launcher/` (package `com.gathin.tvhome`).

Everything here is reversible. Nothing was uninstalled.

## Re-enable any app

One package at a time (each `pm` call on its own):

```sh
TV_HOST=<tv ip> tools/tv.py 'pm enable --user 0 com.tcl.guard'
```

The full undo for the debloat list:

```sh
TV_HOST=<tv ip> tools/restore.sh examples/tcl-c06-us-vietnam/debloat.txt
```

`restore.sh` only re-enables what this list disabled; apps that were already disabled when the TV
arrived (`au.com.stan.and`, `tv.wuaki.apptv`, `com.tcl.smartalexa`) stay as they were. The record it
reads is in `~/.cleanyourtv/recovery-<tv ip>-<hash>.json`.

To go back to the stock Google TV home:

```sh
TV_HOST=<tv ip> tools/tv.py 'pm enable --user 0 com.google.android.tvlauncher'
TV_HOST=<tv ip> tools/tv.py 'cmd package set-home-activity --user 0 com.google.android.tvlauncher/.MainActivity'
```

## Screensaver

Switched from Google's photo backdrop to the plain AOSP colours before the backdrop was disabled:

```sh
TV_HOST=<tv ip> tools/tv.py "settings put secure screensaver_components com.android.dreams.basic/.Colors"
# undo:
TV_HOST=<tv ip> tools/tv.py "settings put secure screensaver_components com.google.android.backdrop/.Backdrop"
```

## Change the dock order

Edit `config.json` in this folder:

```json
"dock_apps": [
  "com.google.android.youtube.tv",
  "com.viettel.tv360.tv",
  "vn.vtv.vtvgotv",
  "org.smarttube.stable"
]
```

Order is left to right, four slots, four columns. Add a fifth entry and the dock still shows four;
change `columns` to match. Missing or disabled packages are skipped and the free slots fill from the
grid (`fill_dock`).

Apply it without a rebuild — the launcher overlays this `config.json` on its built-in `config.xml`:

```sh
TV_HOST=<tv ip> tools/theme.sh examples/tcl-c06-us-vietnam   # apply
TV_HOST=<tv ip> tools/theme.sh --reset                        # back to the defaults
```

Editing `launcher/app/src/main/res/values/config.xml` instead would change those defaults for every
TV, which is why this unit keeps its own `config.json`.

`hidden_apps` drops apps from the grid; `app_labels` renames a tile (`package=Name`).

## Build and install

`launcher/build-and-install.ps1 -TvHost <tv ip>` (Windows) or `launcher/build-and-install.sh`
(POSIX). It builds, pushes, installs, starts the launcher, checks that Home resolves to it, and only
then disables the stock launcher — rolling back if Home does not follow.

On this machine Gradle's daemon inherits `GRADLE_USER_HOME` from the scoop Gradle install, so the
script's build can look idle while it is compiling; if the install half never prints, build alone
with `--no-daemon` and re-run the script (it is idempotent and skips the disable when Home already
belongs to the launcher).

Needs JDK 17+ (`JAVA_HOME`) and Android SDK platform 34 + build-tools 34.0.0
(`sdkmanager "platforms;android-34" "build-tools;34.0.0"`). APK size gate: under 589,824 B; this
build is 544,829 B.

The APK is signed with the debug key, which is enough for a sideloaded launcher and is what the
repo asks for ("debug build is fine").

## Wallpaper

Pictures go in the launcher's `wallpapers/` folder on the TV, pushed by
`tools/theme.sh themes/<name>`; `wallpaper` in `config.xml` (or the theme) turns it on,
`wallpaper_dim` controls its darkness, and `wallpaper_true_color` is for 2 GB sets only.

## Known quirks on this unit

- `com.tcl.usercenter` (TCL account) is switched back on by the firmware at every boot. Left idle;
  not re-disabled after each boot.
- `com.google.android.tungsten.setupwraith` owns the saved Wi-Fi network, so it stays enabled:
  disabling it deletes the network and takes the TV off Wi-Fi and ADB.
- Netflix, Prime Video and Play Games are preload **stubs** on this unit (Netflix: 6.6 MB stub in
  `/data/app`, real 83 MB APK in `/product/app/Netflix`). `pm enable` on a stub brings its tile
  back, so the grid can still offer an app whose stub is enabled; hide those tiles in launcher
  Settings (tile menu, needs `app_library`).
- Live TV and HDMI inputs photograph as empty screenshots (`screencap` returns 0 bytes); check
  `dumpsys activity activities | grep mResumedActivity` or `dumpsys tv_input` instead.
- Inputs on this TV: 4x HDMI + AV + the tuner, from `com.tcl.tvinput`.
- Voice is disabled by choice: `com.google.android.katniss` and `com.google.android.tv.remote.service`
  are off, so the mic button does nothing. Re-enable both for voice.
