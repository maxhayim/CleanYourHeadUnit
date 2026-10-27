# OPPO K9 55 (OPPOCNM632): ColorOS TV package-name Home allowlist

Tested on an OPPO K9 55 (`OPPOCNM632`), Android 9 / ColorOS TV. The display build id was not
captured. Stock Home is `com.heytap.tv.launcher/com.oneplus.tv.launcher2.HomeLauncher`.
The package lists for this unit live in
[`examples/oppo-k9-55-oppocnm632/debloat.txt`](../../examples/oppo-k9-55-oppocnm632/debloat.txt)
and
[`examples/oppo-k9-55-oppocnm632/debloat-extra.txt`](../../examples/oppo-k9-55-oppocnm632/debloat-extra.txt).

The allowlist and the boot category were first written up for a K9 65 (`OPPOCNM652`, ColorOS TV
2.4) in [aerial-tv's device note](https://github.com/pengchujin/aerial-tv/blob/master/docs/devices/oppo-coloros-tv.md).
The checks below are this 55" set, with this launcher.

## The three allowlisted package ids

ColorOS accepts a HOME install only for three package names:

| Package | Role on this set |
|---|---|
| `com.heytap.tv.launcher` | Stock home. Do not build over it, and do not disable it |
| `com.oneplus.tv.launcher2` | The identity that installed and became Home here |
| `com.oppo.tv.launcher2` | Also allowlisted. Not the identity used on this set |

`pm path com.heytap.tv.launcher` already answers: that name is the system launcher. A build with
`-PlauncherApplicationId=com.heytap.tv.launcher` would be an attempt to replace it. Stop if
`pm path com.oneplus.tv.launcher2` (or `com.oppo.tv.launcher2`, if you have to use that one)
already names some other application.

The default `com.gathin.tvhome` build was blocked as a HOME install on this set. The same refusal
is quoted on the K9 65 note as
`INSTALL_FAILED_DUPLICATE_PERMISSION: App installation is banned, because of launcher categories`.

## Build with the opt-in identity

`MainActivity`'s HOME filter declares `com.heytap.tv.launcher.category.MODERN_HOME`. ColorOS boot
only promotes a HOME that carries that category; the K9 65 note sat on the brand animation until
it was added. The Java namespace stays `com.gathin.tvhome`.

Do not run `launcher/build-and-install.sh` here. It always builds and installs `com.gathin.tvhome`,
then points Home at `com.gathin.tvhome/.MainActivity`. `com.heytap.tv.launcher` is deliberately
absent from that script's `KNOWN_STOCK_LAUNCHERS`: naming it as `STOCK_LAUNCHER` would disable the
stock home. Install by hand, and leave the stock package enabled.

From the repository root:

```bash
export TV_HOST=192.168.1.50
PY=tools/venv/bin/python
"$PY" -I tools/tv.py 'pm path com.oneplus.tv.launcher2'
"$PY" -I tools/tv.py 'pm path com.heytap.tv.launcher'
"$PY" -I tools/tv.py 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
(cd launcher && ./gradlew assembleRelease -PlauncherApplicationId=com.oneplus.tv.launcher2)
"$PY" -I tools/b64push.py launcher/app/build/outputs/apk/release/app-release.apk /data/local/tmp/tvhome-allowlist.apk
"$PY" -I tools/tv.py 'pm install -r /data/local/tmp/tvhome-allowlist.apk; rm -f /data/local/tmp/tvhome-allowlist.apk'
"$PY" -I tools/tv.py 'am start -W -n com.oneplus.tv.launcher2/com.gathin.tvhome.MainActivity'
```

After the new screen is up, select it as Home and read the resolution back:

```bash
"$PY" -I tools/tv.py 'cmd package set-home-activity --user 0 com.oneplus.tv.launcher2/com.gathin.tvhome.MainActivity'
"$PY" -I tools/tv.py 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
```

The component is `com.oneplus.tv.launcher2/com.gathin.tvhome.MainActivity`.
`com.oneplus.tv.launcher2/.MainActivity` is the wrong class. `tools/theme.sh` still writes to
`com.gathin.tvhome`; a theme file for this build belongs in
`/sdcard/Android/data/com.oneplus.tv.launcher2/files/config.json`.

## What this set confirmed

Install reported Success for versionName 3.0 / versionCode 9, signed with the debug keystore.
`cmd package set-home-activity` was set to
`com.oneplus.tv.launcher2/com.gathin.tvhome.MainActivity`. `com.heytap.tv.launcher` stayed
enabled. The owner confirmed the home screen. Android 9 is API 28, above the launcher's minSdk 26.

The K9 65 note disabled its stock desktop after declaring the same category and still booted.
That was a different size, a different product code and a different launcher. This set did not
disable `com.heytap.tv.launcher`, and this list does not either. Several-day use, a cold boot
with the stock home disabled, and OTA persistence were not part of the check.

## Undo

```bash
"$PY" -I tools/tv.py 'cmd package set-home-activity --user 0 com.heytap.tv.launcher/com.oneplus.tv.launcher2.HomeLauncher'
"$PY" -I tools/tv.py 'pm uninstall com.oneplus.tv.launcher2'
```

`pm uninstall` here removes the sideloaded launcher only. The debloat lists disable, and
`tools/restore.sh` puts those packages back. The stock home activity on this firmware is
`com.heytap.tv.launcher/com.oneplus.tv.launcher2.HomeLauncher`.
