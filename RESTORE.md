# Restore

Every change CleanYourHeadUnit makes can be undone. Here is how, from easiest to last resort.

## 1. With `cyhu` (computer)

```
python3 tools/cyhu.py restore --all              # everything, newest change first
python3 tools/cyhu.py restore --package com.android.email
python3 tools/cyhu.py restore --launcher         # back to the Home app you started with
python3 tools/cyhu.py restore --dns              # Private DNS back to what it was
python3 tools/cyhu.py restore --all --dry-run    # show what would happen
```

- Records live in `~/.cleanyourheadunit/<unit>/`. `baseline.json` is the unit as first seen and is never overwritten. `changes.json` holds each change with its before and after state. `log.jsonl` is the full log.
- Restore is safe to run twice. Anything already back is skipped.
- If you changed something yourself after CleanYourHeadUnit did (for example, re-enabled an app), restore reports it as *changed since* and leaves it alone. Add `--force` to restore it anyway.

## 2. In the app (head unit)

**Backup & Restore** lists everything that differs from the snapshot the app took when it first opened: package states, Home app, Private DNS and theme. Each item has a button that opens the right Android setting. **Export restore instructions** saves the `adb` commands for all of them.

To turn off the local firewall: press **Stop** in its notification, use **Backup & Restore → Turn off firewall**, or go to Android Settings → VPN.

To leave CleanYourHeadUnit Home: press **Change Home** in its dock, or go to Settings → Apps → Default apps → Home. On Android 4.4, use Settings → Apps → CleanYourHeadUnit → Clear defaults, then press Home.

## 3. Emergency: plain `adb`, no CleanYourHeadUnit needed

Use this if the unit boots to a black screen, has no launcher, or the app won't open. You need a computer with [platform-tools](https://developer.android.com/tools/releases/platform-tools) and USB debugging (or ADB over Wi-Fi) still on.

```
adb devices                                          # the unit must be listed
adb shell pm list packages -d                        # everything that is disabled
adb shell pm enable --user 0 <package>               # bring one back; repeat for each
adb shell cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME
adb shell settings put global private_dns_mode opportunistic   # Private DNS back to automatic
adb shell settings delete global private_dns_specifier
adb reboot
```

To re-enable everything that's disabled (on macOS, Linux, or Git Bash on Windows):

```
for p in $(adb shell pm list packages -d | sed 's/package://' | tr -d '\r'); do adb shell pm enable --user 0 "$p"; done
```

This also re-enables packages that were disabled before CleanYourHeadUnit, which is harmless. `python3 tools/cyhu.py restore --script` prints the exact list for your unit instead.

If the unit has no working launcher at all, re-enabling the factory launcher fixes it. On FYT that's usually `com.android.launcher6`, otherwise often `com.android.launcher3`. The Home app was protected, so CleanYourHeadUnit can't have disabled it, but something else might have.

## 4. If ADB is off and the screen is unusable

CleanYourHeadUnit doesn't change ADB settings, and it doesn't touch firmware. If you can't reach the unit at all, your remaining option is the firmware's own recovery or factory reset, which erases your settings and apps. Ask your unit's seller or firmware community for the procedure. Never flash firmware that wasn't made for your exact unit and MCU.
