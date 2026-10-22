# TCL 65Q7D Pro (2026, Greece): the Q7C list and launcher on a Wi-Fi-only unit

One owner's session on 2026-10-22, following [`PROMPT.md`](../../PROMPT.md) on Windows 11 with the repo's
tools (upstream `4c55533`, launcher 2.6). The firmware is the same build as
[`examples/tcl-q7c-google-tv/`](../../examples/tcl-q7c-google-tv/README.md) (`G08_4K_GB`, `AU02`), so that
folder is the list to run; this note records the deltas and what differed on this unit. The packages were
applied in five batches with `tools/disable.sh` and checked after one reboot, but they have **not** been lived
with for several days, so this is not a list of its own yet.

![Reference launcher on the TCL 65Q7D Pro, five-tile dock, rotating wallpaper (greeting name and Wi-Fi name blurred)](tcl-65q7d-pro-home.png)

## Identify the firmware

| Field | Observed value |
|---|---|
| Retail model | `65Q7D Pro` (Q7D Pro SQD-Mini LED, 2026), supplied by the owner; not exposed by `ro.product.model` |
| `ro.product.manufacturer` / `ro.product.brand` | `TCL` / `TCL` |
| `ro.product.model` | `Smart TV Pro` |
| `ro.product.name` / `ro.product.device` | `G08_4K_GB` / `G08` |
| Android / SDK | `14` / `34`, Google TV |
| `ro.build.display.id` | `tcl9618-user 14 UTT2.250416.001 AU02 release-keys` |
| `ro.build.fingerprint` | `TCL/G08_4K_GB/G08:14/UTT2.250416.001/AU02:user/release-keys` |
| `ro.software.version_id` | `V8-T653T01-LF1V641` |
| Security patch | `2025-12-05` |
| `ro.hardware` / `ro.board.platform` | `mt5896` / `merak` |
| `ro.adb.secure` | `1` |

`G08_4K_GB` + `AU02` is the pair the Q7C, 85RM7L and 55C745 lists were applied on. The stock launcher is
`com.google.android.apps.tv.launcherx` and the Live TV player is `com.tcl.tv`
(`com.tcl.player.TVActivity`), as on the Q7C.

## Connecting: Wireless debugging only, and its port moves

The Q7C note says USB debugging opens port 5555 by itself. **Not on this unit.** With USB debugging on,
5555 kept answering `Connection refused` (the TV was reachable at the same IP). What worked was Developer
options > Wireless debugging, with Google's platform-tools `adb`:

```sh
adb mdns services                       # lists _adb-tls-pairing._tcp while the pairing dialog is open
adb pair <tv ip>:<pairing port> <six-digit code>
adb connect <tv ip>:<connect port>
adb -s <tv ip>:<connect port> tcpip 5555   # now tools/tv.py and the scripts work on 5555
```

- The first time, `adb mdns services` listed both `_adb-tls-pairing._tcp` and `_adb-tls-connect._tcp`. After a
  reboot only the pairing service was advertised, and the connect port had to be read from the TV's
  Wireless debugging screen.
- After the reboot that screen said "IP address and port: unavailable" while pairing still succeeded. Switching
  Wireless debugging off and on again brought the address back.
- `adb tcpip 5555` restarts adbd, so the connect port shown on the TV changes afterwards. Both the new port
  and 5555 worked.
- Forgetting and re-joining the Wi-Fi network (needed for setupwraith, below) switched Wireless debugging off,
  and a reboot closed 5555. Each time a new pairing code was needed. Plan for three or four pairings in one
  session.
- On Windows, clone the repo to a short path. In a deeply nested folder, `git clone` left files unchecked-out
  and `pip install -r tools/requirements.txt` failed on a `cryptography` file path longer than 260 characters.

## setupwraith owned the Wi-Fi network in use

`tools/tv.py "dumpsys wifi" | grep -o 'cname=[^ ]*'` showed the saved network the TV was connected to with
`cname=com.google.android.tungsten.setupwraith` (a second, older saved network belonged to
`android.uid.system:1000`). The owner forgot the network in Settings and joined it again. After that its
creator was `com.android.tv.settings`, not `android.uid.system` as on the
[QM751G](tcl-75qm751g-qm7.md). setupwraith then owned no network, and `tools/disable.sh` disabled it without
`FORCE_SETUPWRAITH`. Wi-Fi stayed up, and the TV kept the same IP.

Order used, as in the Q7C README: setupwraith first, while launcherx still owned Home, then
`STOCK_LAUNCHER=com.google.android.apps.tv.launcherx INPUT_KEY_SERVICE=1 HOME_KEY_SERVICE=1
launcher/build-and-install.sh`, which started the new launcher, disabled launcherx and reported
`Home now resolves to com.gathin.tvhome/.MainActivity`. `mFocusedApp` confirmed it.

## The list as applied

29 packages in five batches, with a screenshot after each. Home, Live TV (`topResumedActivity` =
`com.tcl.tv/com.tcl.player.TVActivity`) and YouTube were checked between batches. launcherx was disabled by
`build-and-install.sh`, not from the list.

```text
# TCL promo, push messages, content rows and store demo
com.tcl.messagebox
com.tcl.esticker
com.tcl.exhibit
com.tcl.tv.tclhome_passive
com.tcl.waterfall.overseas
com.tcl.channelplus
com.tcl.usercenter
com.tcl.guard

# Telemetry, diagnostics and Android 14 ad / privacy services
com.tcl.logkit
com.google.android.feedback
com.android.tv.feedbackconsent
com.android.adservices.api
com.android.ondevicepersonalization.services
com.android.federatedcompute.services

# Extras nobody asked for
com.tcl.ocean.instructions
com.tcl.repairguide
com.android.printspooler
com.google.android.syncadapters.calendar
com.google.android.onetimeinitializer
com.google.android.play.games

# Off by the owner's choice
com.google.android.katniss              # Assistant; the mic button then opens the Play Store search
com.google.android.apps.mediashell      # Chromecast receiver
com.tcl.miracast
com.tcl.magiconnectfree
com.tcl.smartalexa
com.google.android.youtube.tvmusic      # was already disabled before the session
com.tcl.ui_mediaCenter
com.tcl.hearaid
com.tcl.t_solo

# Second Home claimant, after re-joining the Wi-Fi network in Settings
com.google.android.tungsten.setupwraith
```

Compared with the Q7C list:

| Q7C entry | Here |
|---|---|
| `com.tcl.airplay2`, `com.mediatek.AirplayAPK`, `com.mediatek.airplaydaemon` | Kept: the owner uses AirPlay |
| `com.tcl.UpdatePeripheral` | Kept: a TCL soundbar and subwoofer are connected, and this updates their firmware |
| `com.netflix.ninja`, `com.amazon.amazonvideo.livingroom` | Already disabled by the owner before the session; left as they were |
| `com.netflix.tokenmanager` | Left enabled (installed) |
| `com.google.android.videos` | Not installed on this preload |
| (not in the Q7C list) `com.google.android.apps.mediashell` | Off by choice. The Q7C keeps Chromecast |

Everything the Q7C header leaves alone was left alone here too, including `com.tcl.ttvs`, `com.tcl.eva`,
`com.tcl.tv`, `com.tcl.tvinput`, `com.tcl.suspension` and the firmware updater. `com.tcl.factory.view` is
installed and was not opened.

## Remote buttons, and the volume repeat they cost

The helpers were installed with `INPUT_KEY_SERVICE=1 HOME_KEY_SERVICE=1`, with the Q7C keycodes
(`settings_key_keycodes` `[83]`, `app_keys` `190:com.google.android.youtube.tv`). The owner pressed the real
buttons: **Home, Input, the gear and the YouTube button all worked.**

**Holding Volume + on the TCL remote moved the volume one step per press**, with both helpers bound. This is the
symptom in [the G09 key-repeat note](tcl-55p71b-g09-key-repeat.md), now seen on a G08 Google TV 14 build. Only
the "both helpers on" state was tried. The owner kept the helpers, because the soundbar's own remote still
ramps the volume continuously. Someone without a soundbar may prefer `"key_filter": false` or no helpers, and
give up the gear and YouTube buttons instead.

## Launcher config used

Pushed with `tools/theme.sh` (`greeting_name` left out here):

```json
{
  "columns": 5,
  "rows_below_dock": 0,
  "dock_apps": ["com.tcl.tv", "com.apple.atve.androidtv.appletv", "org.xbmc.kodi", "com.wbd.stream",
                "com.google.android.youtube.tv", "tunein.player", "com.windscribe.vpn"],
  "hidden_apps": ["com.tcl.dashboard", "com.tcl.partnercustomizer", "com.tcl.suspension", "com.tcl.gallery"],
  "live_tv_package": "com.tcl.tv",
  "app_labels": ["com.tcl.tv=Live TV", "com.wbd.stream=HBO Max"],
  "settings_key_keycodes": [83],
  "app_keys": ["190:com.google.android.youtube.tv"],
  "cinematic_home": false,
  "wallpaper": true,
  "wallpaper_rotation_minutes": 60
}
```

- With seven dock apps and `columns` 7, the tiles were small. `columns` 5 made them larger. The two extra
  dock apps then lead the grid row under the dock, which `rows_below_dock` 0 keeps mostly off screen until
  Down is pressed.
- `cinematic_home` was tried first. With a wallpaper it covers almost all of the picture with the focused
  app's colour. With it off, the wallpaper shows and the ambient glow still tints it in the focused app's
  colour.
- The `wallpapers/` collection was six 1920×1080 JPEGs drawn locally, 55 to 250 KB each. The
  collection loaded and showed one picture. An hourly change was not watched.

## Reboot and closing ADB

After one reboot, `pm list packages -d` returned the same 33 packages as before it: the 29 above, launcherx, and
the three disabled before the session. Nothing came back. HOME still resolved to `com.gathin.tvhome/.MainActivity`,
and `enabled_accessibility_services` still listed `InputKeyService` and `HomeKeyService`.

To close: `tools/tv.py 'settings put global adb_wifi_enabled 0'`, then `'settings put global adb_enabled 0'`.
The connection dropped during these two writes, and `tools/adb-closed.py <tv ip>` then reported 5555 closed. The owner revoked the USB debugging authorisations,
removed the paired computer, switched Wireless debugging and USB debugging off, and rebooted. Afterwards:

- `tools/adb-closed.py` reported `Connection refused`, and `adb mdns services` listed no ADB service.
- A full TCP scan of the TV found 6465, 6466, 6467, 6553, 7000 (AirPlay, kept), 16794 and 45011 open. 16794 and
  45011 accept a TCP connection, but `adb connect` to them stayed `offline`, so they are not ADB. Both were
  already open while debugging was on.

## Test limits

- One set, one firmware, one session and one reboot. Nothing here has been lived with for several days.
- HDMI inputs, the tuner, channel scanning, AirPlay, the Netflix/Prime/app buttons and the microphone button were
  not tested after the list. Live TV and YouTube were opened, not watched for long.
- Standby/wake, a cold boot from mains, and a firmware update are untested.
- The volume repeat was measured with both helpers bound only. No bisect, and no `key_filter` false run.
