# TCL 75C845X2 (UK/Europe), Google TV 14

- **TV:** TCL 75C845X2, UK/Europe build. Model string "Smart TV Pro", product `G08_4K_GB`, MediaTek `tcl9618`, Android TV OS 14 (Google TV).
- **Firmware:** `V8-0012T01-LF1V667`, fingerprint `TCL/G08_4K_GB/G08:14/UTT2.250416.001/AU04:user/release-keys`.
- **Based on:** [`tcl-c755-google-tv`](../tcl-c755-google-tv/debloat.txt), the same G08 board and package set.
- **Tested:** applied on a real 75C845X2 on 2026-10-20. Home, the launcher and its dock apps worked afterwards.

## Differences from the C755 list

- YouTube (`com.google.android.youtube.tv`) and Google Play Movies & TV (`com.google.android.videos`) are disabled, because SmartTube is used instead.
- Chromecast, AirPlay (three packages) and the game bar are kept (commented out).
- `com.tcl.hotelmenu` and `au.com.stan.and` are not on this UK build. The "Unknown package" errors for them are harmless.

## Known issue: Settings > Accounts & profiles is empty

`install` disables the stock launcher, `com.google.android.apps.tv.launcherx`. On this firmware that package serves the Accounts & profiles page, so the page comes up empty. The accounts are not deleted: `dumpsys account` still lists them.

Enabling `launcherx` brings the page back, but it takes Home back too. While it is enabled it resolves as HOME with priority 2, `set-home-activity` prints `Success` without overriding it, and `pm disable` on its home activity alone is refused with a SecurityException from the shell.

So keep `launcherx` disabled and enable it only to switch profile. Disable first, set Home last:

```
adb shell pm enable com.google.android.apps.tv.launcherx
# switch profile on the stock home screen, then:
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
adb shell cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity
adb shell am start -n com.gathin.tvhome/.MainActivity
```

Most account switching can also be done inside each app (YouTube, Plex, SmartTube).

Not confirmed: `pm list users` also shows `configured_user` (10) and `new_user` (11). Whether other Google profiles run as separate Android users was not tested (`am get-current-user` while on each profile would show it).
