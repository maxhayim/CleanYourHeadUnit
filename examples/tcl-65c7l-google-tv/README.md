# TCL 65" C7L Google TV

Cleaned up on 22 October 2026. Board G08 (MediaTek tcl9618), Android 14, firmware UTT2.250416.001 AU02,
2.4 GB RAM. Replace 192.168.1.50 below with your TV's IP address.

What changed:

- **27 apps disabled**, none uninstalled. They are listed with reasons in [debloat.txt](debloat.txt):
  TCL tracking (Samba TV, TCL analytics, logs), TCL promos and FAST channels, the retail demo, Alexa,
  TCL's "cleaner", the partner-app auto-installer, Stan, T-Cast and the TCL soundbar updater.
- **Google TV home replaced** by the clean launcher from `launcher/`: a dock of Plex, Netflix,
  YouTube and Prime Video, the other apps in a grid below, and pills for Inputs, Wi-Fi and Settings.
  Its clock screensaver replaces Google's promo screensaver.
- **Ad-blocking DNS**: Private DNS is set to `dns.adguard-dns.com`. It blocks ads and trackers in many
  apps, but **not YouTube ads**.

Checked on the TV afterwards: all four HDMI inputs are still registered, Live TV starts, the Input button's
source panel opens, every remote button works (gear, app buttons, Input, mic), YouTube loads over the
ad-blocking DNS, Play Store updates install, and Chromecast is still advertised on the network. AirPlay,
Miracast and the game bar were kept enabled but not tested. Nothing came back on after a reboot.

Take a full backup before any change (package lists, settings, getprop) into the gitignored
`backup/` folder; on this unit nothing was disabled beforehand.

## Connecting to the TV

Every script below needs network debugging on the TV:
**Settings > System > Developer options > Wireless debugging** on. If the TV shows
"Allow USB debugging?", tick "Always allow" and accept it.

Turn it off again when you're done, as in [When you're done](#when-youre-done).

The commands below run from the repo folder in PowerShell. In each one, `tools\venv\Scripts\python.exe -I tools\tv.py`
sends one command to the TV:

```powershell
$env:TV_HOST = '192.168.1.50'
```

## Re-enable one app

On the TV: **Settings > Apps > See all apps > Show system apps**, pick the app and choose **Enable**.

Or from the PC, for example to bring back Stan:

```powershell
tools\venv\Scripts\python.exe -I tools\tv.py 'pm enable au.com.stan.and'
```

Then put a `#` in front of its line in `debloat.txt`, so it stays on if the list is applied again.

Leave `com.google.android.tungsten.setupwraith` and `com.google.android.apps.tv.launcherx` alone while the new
launcher is Home. Either one can take Home back. Use the full restore below instead.

## Change the dock order

Quickest: on the TV, **hold OK on the Settings pill** and choose **reorder**.

To keep the change through a reinstall, edit `dock_apps` in [config.json](config.json). The apps go into the dock
left to right, and `columns` (4 to 7) sets how many fit:

```json
"dock_apps": ["com.plexapp.android", "com.netflix.ninja", "com.google.android.youtube.tv", "com.amazon.amazonvideo.livingroom"],
```

Then push it to the TV. No rebuild is needed:

```powershell
tools\theme.ps1 -TvHost 192.168.1.50 examples\tcl-65c7l-google-tv
```

Package names of the installed apps:

| App | Package |
|---|---|
| Plex | `com.plexapp.android` |
| Netflix | `com.netflix.ninja` |
| YouTube | `com.google.android.youtube.tv` |
| Prime Video | `com.amazon.amazonvideo.livingroom` |
| Disney+ | `com.disney.disneyplus` |
| ABC iview | `au.net.abc.iview` |
| SBS On Demand | `com.sbs.ondemand.tv` |
| 7plus | `com.swm.live` |
| 9Now | `au.com.ninenow.ctv` |
| 10 | `au.com.tenplay` |
| Kodi | `org.xbmc.kodi` |
| YouTube Music | `com.google.android.youtube.tvmusic` |
| Live TV | `com.tcl.tv` |

## Change the wallpaper

1. Put a picture named `wallpaper.jpg` in this folder. Use 1920×1080, because the TV draws its interface at 1080p.
2. Add `"wallpaper": true,` to [config.json](config.json). Optionally add `"wallpaper_dim": 30` to darken it so tile names stay readable.
3. Push it with the same `tools\theme.ps1` command as above.

For a rotating set, look at `themes/scenes`, or try a ready-made look with `tools\theme.ps1 -TvHost 192.168.1.50 themes\aurora`.
Run `tools\theme.ps1 -TvHost 192.168.1.50 -Reset` to go back to plain black. Every key is described in `themes/README.md`.

## Rebuild or reinstall the launcher

```powershell
examples\tcl-65c7l-google-tv\install.ps1 -TvHost 192.168.1.50
```

This builds the launcher, installs it, makes it Home, pushes `config.json` and sets the clock screensaver.
It's safe to run again at any time. The build needs JDK 17 (`JAVA_HOME`, or a `jdk-17` folder in
`%USERPROFILE%\.jdks`) and Android SDK 34 in `%LOCALAPPDATA%\Android\Sdk`.

After a factory reset, run the steps in this order:

1. Run `tools\disable.ps1 -TvHost 192.168.1.50 examples\tcl-65c7l-google-tv\debloat.txt`. It holds back the
   stock home and the setup wizard until it's safe to disable them.
2. If it says the Wi-Fi network belongs to setupwraith, forget the network and join it again in Settings.
3. Run `install.ps1`.
4. Run `disable.ps1` again.

## After a firmware update

TCL updates can turn apps back on or give Home back to Google TV. To see what changed:

```powershell
tools\venv\Scripts\python.exe -I tools\check.py examples\tcl-65c7l-google-tv\debloat.txt com.gathin.tvhome
```

Add `--fix` after `check.py` to re-disable whatever came back and put the launcher back on Home.

## Undo everything

```powershell
examples\tcl-65c7l-google-tv\restore.ps1 -TvHost 192.168.1.50
```

This turns every disabled app back on, makes the Google TV home Home again, and restores Google's
screensaver and automatic DNS. Add `-UninstallLauncher` to also remove the new launcher.

To undo only the ad-blocking DNS, either switch **Settings > Network & Internet > Private DNS** to
Automatic, or run:

```powershell
tools\venv\Scripts\python.exe -I tools\tv.py 'settings put global private_dns_mode opportunistic'
```

## When you're done

Network debugging lets anyone on your network who is accepted at the prompt control the TV. Port 5555
is unencrypted. Close it when you're done:

1. **Developer options**: switch off Wireless debugging and USB debugging, and choose
   **Revoke USB debugging authorisations**.
2. Restart the TV.
3. Check that the port is closed. "Connection refused" means it worked:

```powershell
tools\venv\Scripts\python.exe -I tools\adb-closed.py 192.168.1.50
```
