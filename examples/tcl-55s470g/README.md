# TCL 55S470G

Google TV 12 (device G10, product G10_4K_US_NF, firmware STT2.230203.001 AS50), 1.8 GB RAM.
Same platform and build as [`tcl-g10-google-tv`](../tcl-g10-google-tv/), with your own choices on top.

| File | What it is |
|---|---|
| `debloat.txt` | The 29 packages disabled on this TV, in the batches they were applied, with comments (the stock launcher, the 30th, is handled by the install and restore scripts) |
| `install.ps1`, `install.sh` | Build the launcher, install it as Home and push the dock theme |
| `restore.ps1`, `restore.sh` | Undo everything: re-enable every package, give Home back to Google TV, DNS and screensaver back |
| `theme/config.json` | The dock order and look, pushed to the TV without a rebuild |

All commands run from the repository root. Replace `192.168.1.50` if the TV gets a different
address. Network debugging must be on (Settings > System > Developer options > USB debugging), and the
TV asks "Allow USB debugging?" the first time a computer connects.

## What was done

- **Disabled, not uninstalled:** Samba TV front end, TCL log upload, TCL account nags, TCL home-screen
  content and promos, the silent partner-app installers, retail demo and hotel mode, the e-manual,
  game bar, USB media player, soundbar updater, TalkBack, Play Games, the TGuard cleaner, ambient mode,
  Google Assistant voice, Chromecast, YouTube Music, the Google TV app, Alexa, the phone-remote
  service, and finally the stock launcher and the setup wizard (they both claim Home).
- **Kept on purpose:** Live TV, HDMI and tuner (`com.tcl.tv`, `com.tcl.tvinput`), the Input/source
  panel (`com.tcl.suspension`), remote pairing, Bluetooth, keyboard, Play Store and Play Services,
  AirPlay, TCL Live and every streaming app you use. `com.tcl.ttvs` also stays: on this firmware it
  carries the firmware updater as well as Samba TV's frame capture and TCL analytics. To stop the
  tracking, open Settings > Privacy > Samba Interactive TV and untick it, then open **Manage providers**
  on the same page and untick every provider there by hand: unticking Samba Interactive TV clears the
  main list but leaves the providers ticked.
- **Screensaver** moved from ambient mode to Android's plain Colors screensaver. For the launcher's
  drifting clock instead:
  `tools\venv\Scripts\python.exe -I tools\tv.py "settings put secure screensaver_components com.gathin.tvhome/.ClockDream"`
- **Ad-blocking DNS:** Private DNS points at `dns.adguard-dns.com`. It blocks ad and tracker domains
  for every app, but **not YouTube ads** (they come from the same servers as the videos). Undo:
  `tools\venv\Scripts\python.exe -I tools\tv.py "settings put global private_dns_mode opportunistic"`
  (or Settings > Network & Internet > Private DNS > Automatic).

## Re-enable one app

```powershell
$env:TV_HOST = '192.168.1.50'
tools\venv\Scripts\python.exe -I tools\tv.py "pm enable com.google.android.apps.mediashell"
```

Use the package name from `debloat.txt` (Chromecast in the example). Without a computer: Settings >
Apps > See all apps > Show system apps, pick the app, Enable. To keep it enabled next time you run a
list, put a `#` in front of its line in `debloat.txt`.

To undo everything at once:

```powershell
powershell -ExecutionPolicy Bypass -File examples\tcl-55s470g\restore.ps1 -TvHost 192.168.1.50
```

The launcher stays installed after a restore; remove it with
`tools\venv\Scripts\python.exe -I tools\tv.py "pm uninstall com.gathin.tvhome"`.

## The remote's Settings (gear) button

It sends keycode 83, which only the stock Google TV home understood. The launcher's `HomeKeyService`
(an accessibility service, enabled by `install.ps1`) catches it and opens a small glass panel, on Home
or over any app: TV system settings, Picture, Network, Apps (open an app and choose Enable to bring
back something from `debloat.txt`) and Launcher settings. Back or the gear again closes it.

- Gear opens the TV's Settings instead: set `"settings_key_panel": false` in `theme/config.json` and
  push the theme.
- Gear stops working after a reinstall: the service was switched off. Run `install.ps1` again, or check
  it with `tools\venv\Scripts\python.exe -I tools\tv.py "settings get secure enabled_accessibility_services"`
  (it should list `com.gathin.tvhome/.HomeKeyService`).

## Change the dock order

Edit `theme/config.json`. `dock_apps` lists the dock left to right:

```json
"dock_apps": [
  "com.netflix.ninja",
  "com.google.android.youtube.tv",
  "com.amazon.amazonvideo.livingroom",
  "com.disney.disneyplus"
]
```

Other packages on this TV: `com.apple.atve.androidtv.appletv` (Apple TV), `com.cbs.ott` (Paramount+),
`com.tubitv` (Tubi), `com.tcl.tv.plus` (TCLtv+). List everything with
`tools\venv\Scripts\python.exe -I tools\tv.py "pm list packages -3"`. Then push it, no rebuild:

```powershell
powershell -ExecutionPolicy Bypass -File tools\theme.ps1 -TvHost 192.168.1.50 examples\tcl-55s470g\theme
```

## Change the wallpaper

Put a dark 1920×1080 JPEG under about 300 KB at `theme/wallpaper.jpg`, set `"wallpaper": true` in
`theme/config.json` and push the theme with the command above. Set it back to `false` (or delete the
file) for the plain black background. `themes/aurora` in the repo is a ready-made example. Other keys
(columns, background colour, the time-of-day sky) are in [`themes/README.md`](../../themes/README.md).

## Rebuild or reinstall the launcher

Needs JDK 17 (`winget install Microsoft.OpenJDK.17`) and the Android SDK at
`%LOCALAPPDATA%\Android\Sdk` with `platforms;android-34` and `build-tools;34.0.0`.

```powershell
powershell -ExecutionPolicy Bypass -File examples\tcl-55s470g\install.ps1 -TvHost 192.168.1.50
```

## Quirks on this TV

- Nothing came disabled from the factory (on the G10 example's units Samba TV, logkit and ttvs did).
- `tv.samba.ssm.ui` comes back enabled after every reboot (`enabled=1`, an explicit enable at boot,
  most likely from `com.tcl.ttvs`, which stays for firmware updates). Everything else in the list
  stayed disabled across a reboot. The Samba opt-out in the TV's Settings is what actually stops the
  viewing data, so keep that off.
- Screenshots of Live TV or an HDMI input come back empty (0 bytes). That is the TV, not a fault.
- The Wi-Fi network was saved by Android (`android.uid.system`), not the setup wizard, so disabling
  setupwraith did not drop Wi-Fi. If you reset the TV and set Wi-Fi up again in the wizard, forget and
  re-join the network in Settings before running `install.ps1` (the script checks this).
- The remote's Input button opens TCL's own source panel even with the stock launcher disabled, so
  `InputKeyService` is not needed (checked with the physical remote).
- The gear button is keycode 83 (`KEYCODE_NOTIFICATION`), scancode 233, from the Bluetooth remote
  `BT_RC813_MG`. Injected `input keyevent 83` does not reach the accessibility service here; only the
  physical button proves it works.
