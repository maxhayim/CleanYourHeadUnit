# Brightness

Dims the whole picture below what the TV's own settings allow, and warms it at night, over every app, Live
TV and the home screen. For the [Clean Your TV](../../README.md) launcher it declares a
[module card](../../docs/modules.md): once you answer **Show card** its tile reads "Brightness" with an amber
glow. On any other Android TV home it is an ordinary app.

Opening it shows one list, worked with the remote alone:

| Row | What it does |
|---|---|
| **Dimming** | The master switch, focused first. **Off puts the screen back to normal at once**; the other settings stay for next time. |
| **Brightness** | A slider from 100 % down to 20 % in 5 % steps (◀ ▶), used outside night mode. |
| **Night mode** | Off, On (now, until switched off) or Scheduled (every day between two times). |
| **Night brightness** | Its own slider, 20 % to 100 %. |
| **Warmth** | Night mode only: an amber tint that takes blue out of the picture, 0 to 100 %. |
| **Night starts**, **Night ends** | The schedule, in 15 minute steps; a night may run past midnight (21:00 to 07:00 is the default). |

Every change shows at once, over the settings screen too, so you see the result as you move the slider. The
settings are kept, and the layer and the schedule come back after a reboot.

The screen never goes below 20 %, so the picture and this app stay readable whatever is set. If it is ever too
dark to find the way, any of these gives the normal screen back:

- open Brightness and press OK on **Dimming**;
- from a computer, `adb shell am broadcast -a io.github.maxhayim.brightness.OFF -n io.github.maxhayim.brightness/.OffReceiver`;
- `adb shell am force-stop io.github.maxhayim.brightness`, or uninstalling it.

## How it works

This is software dimming. A full-screen window of one colour sits over everything (`TYPE_APPLICATION_OVERLAY`),
black at the missing brightness with the amber folded in for warmth. It never takes focus, keys or touches, so
the remote keeps controlling whatever is on screen. A foreground service holds the window and switches night
mode at the scheduled times, and it stops itself when there is nothing to draw and no schedule to follow.

## Permissions

| Permission | Why |
|---|---|
| Display over other apps (`SYSTEM_ALERT_WINDOW`) | To draw the dimming layer over other apps. Granted by `install.sh`, since many TVs hide this setting. Without it the app says so and draws nothing. |
| Foreground service (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`) | One service holds the layer and follows the schedule. It runs only while dimming is on and there is a layer to draw or a schedule to follow. |
| Start at boot (`RECEIVE_BOOT_COMPLETED`) | To put the layer and the schedule back after a reboot or an update, if dimming was on. |

No internet permission: nothing leaves the TV. No accounts, telemetry, ads or libraries. Settings stay in the
app's own storage (backups are off). The `OffReceiver` is open to any sender on purpose: it can only switch
dimming off.

## Build and install

JDK 17 and Android SDK platform 34 with build-tools 34.0.0, as for the launcher. From the repo root, with ADB on:

```bash
TV_HOST=<tv ip> addons/brightness/install.sh
```

It builds the add-on, installs it, allows display over other apps (and TCL's auto-start), and opens it. Press
Home: the launcher asks once whether to show the card. Change the answer later by holding OK on Settings, then
Module cards. To build only: `cd addons/brightness && ./gradlew assembleRelease`; the JVM tests run with
`./gradlew testReleaseUnitTest`.

The release build is signed with the debug key so it installs over ADB like the launcher. Sign your own builds
with your own key if you hand them out; the launcher pins its Show answer to the signing key.

## Limits

- **It darkens the picture, not the backlight.** Whites get dimmer but blacks don't get blacker, and the TV
  uses as much power as before. For a darker panel, lower the backlight in the TV's own picture settings first,
  then use this for the rest.
- **Some things are drawn above it.** Android keeps the on-screen keyboard, the volume bar, toasts and the
  system's own panels above app overlays, so they show at full brightness.
- **Some screens hide it.** Since Android 12, permission prompts and the app installer can hide overlays while
  they are open.
- **Menus drawn by the TV itself** are not Android windows on some sets (picture menus on several TCL and Sony
  models), so they show at full brightness.
- **Video planes vary.** On most TVs video, HDMI inputs and the tuner sit under Android's graphics and are
  dimmed with the rest. Some chips put protected, HDR or tunnelled video on their own plane, which may show
  undimmed or lose the tint. Check on your TV with each source you use.
- **The setting may be hidden.** Some firmware (TCL, some Fire TV builds) has no screen to allow display over
  other apps; `install.sh` allows it over ADB, or the app shows the ADB command.
- **Aggressive memory managers** can stop the service on 1 GB TVs. If they do, the layer goes and the screen
  is brighter, never darker; opening the app starts it again.
- Screenshots and screen recordings include the layer.

## Tested on

Not yet on a TV. The JVM tests cover the levels, the colour arithmetic, the schedule (past midnight, edges,
empty windows), the 20 % floor and the clamping of stored values. These checks need a TV:

1. The card prompt and **Show card**; the card opens the app.
2. Each slider moves the layer at once; the 20 % floor holds.
3. **Dimming** Off removes the layer at once, and so do the ADB broadcast and a force stop.
4. Night mode On, then Scheduled, with a window starting a minute or two ahead: it switches at the start and
   at the end without opening the app.
5. The layer over Home, a streaming app playing video, Live TV and an HDMI input, with every remote key still
   reaching the app below.
6. A reboot with dimming on brings the layer and the schedule back.
7. On a TV that hides the overlay setting, the app's message and `install.sh` grant.
8. Memory with the layer up (`dumpsys meminfo io.github.maxhayim.brightness`).

## Maintainer

[@maxhayim](https://github.com/maxhayim)
