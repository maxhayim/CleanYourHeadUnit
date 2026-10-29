# Hisense VIDAA_TV / NOVA72691 (70V1F-S, Android 9): what actually holds HOME and boot

Owner-observed note, measured on 2026-10-28/29. CleanYourTV's no-root flow **cannot** take HOME on this set.
This note is deliberately long: the failure modes on this firmware are non-obvious, and most of them look like
"the tool is broken" when they are actually the firmware refusing to let a third-party window become the home.

Everything below was reproduced with the TV on the bench, ADB over TCP, and a physical remote for the key
checks. Commands are copy-pasteable.

---

## 1. Device

| Field | Observed value |
|---|---|
| Android model / device | `VIDAA_TV` / `NOVA72691` |
| Retail model | 70V1F-S (BOM 0008) |
| Android / API | 9 / 28 |
| Build fingerprint | `Hisense/VIDAA_TV/NOVA72691:9/PPR2.180905.006.A1/04.02.60.03:user/release-keys` |
| TV software version | 04.02.60.03 |
| Security patch | 2021-01-05 |
| SoC / ABI | Novatek n62020 (NT72691) / `armeabi-v7a` only (32-bit) |
| Kernel | `4.9.118+` #1, built 2024-02-20 |
| SELinux | `ro.boot.selinux=permissive` (permissive at runtime) |
| ADB | `ro.adb.secure=1`, `persist.adb.tcp.port=5555`, owner accepted the prompt |
| Stock launcher | `com.jamdeo.tv.vod/com.ju.main.launcher.LauncherActivity` (聚好看), HOME priority 50 |

---

## 2. TL;DR for the next person on this firmware

1. **No-root: HOME cannot be taken.** Not with `set-home-activity`, not with intent-filter priority, not with
   a vendor settings pointer, not with an accessibility guard. Keep the stock launcher and give the user a
   secondary entry (an app icon, a remote-key shortcut, or the Hisense "signal source" style mapping).
2. **Do not disable `com.jamdeo.tv.vod/com.ju.main.launcher.LauncherActivity`.** It does not free HOME; it
   removes the only window the boot sequence is willing to draw, and the TV never finishes booting.
3. **With root there is a clean answer**: install the launcher as a system app with `android:priority="100"`.
   The HOME key then opens it directly. Boot still draws the stock launcher first for ~8–10 s; a small
   boot-time retry loop is enough to land on the custom launcher.
4. Everything that failed is documented below with the exact command and the exact symptom, so nobody has to
   repeat the sequence.

---

## 3. No-root: what was tried, and what actually happened

| # | Attempt | Command | Result |
|---|---|---|---|
| 1 | Read HOME candidates | `cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME` | Stock launcher `priority=50`; third-party HOME `priority=0` |
| 2 | Select a replacement home | `cmd package set-home-activity --user 0 <launcher>/.MainActivity` | Prints `Success`; **no effect**. `package-restrictions.xml` only gains `always="false"`; HOME still opens the stock launcher |
| 3 | Vendor "custom startup package" pointer | `settings put system settings_startup_source_custom_pkg_key <launcher>` (and `startup_source_is_custom` was already `1`) | **No effect** after two pre-seeded reboots; boot still resolved to the stock launcher |
| 4 | Remove the stock launcher from the candidate set | `pm disable --user 0 com.jamdeo.tv.vod/com.ju.main.launcher.LauncherActivity` | HOME key stops working **and** boot never completes (see §4) |
| 5 | Let the system resolve another built-in home | `pm enable com.android.settings` (enables `.FallbackHome`) + disable stock component | Boot resolves to `FallbackHome`, but it force-finishes itself in a loop and boot still never completes |
| 6 | Accessibility guard | third-party accessibility service watching `TYPE_WINDOW_STATE_CHANGED` | Service binds, but **no window events arrive** for the stock launcher |
| 7 | Inject HOME to test behaviour | `input keyevent 3` | **Swallowed by the firmware.** No `START` appears in logcat whether the stock component is enabled or disabled — key behaviour can only be checked with the physical remote |

### Why the no-root paths fail (mechanisms)

- **Priority clamp.** The firmware rewrites third-party HOME filters to priority 0; the stock launcher stays at
  50, so plain resolution never reaches a replacement.
- **Preferred activity is written non-sticky.** `set-home-activity` produces an `always="false"` entry, which
  does not override the priority comparison.
- **The vendor pointer is bookkeeping.** `settings_startup_source_custom_pkg_key` lives in the `system`
  namespace and did not move the boot launcher in two controlled reboots.
- **No window events to third-party services.** Same behaviour as the Vidda C1S report in aerial-tv (#545):
  the guard binds but never sees the stock launcher's `TYPE_WINDOW_STATE_CHANGED`, so it cannot bounce back.

---

## 4. Why the TV hangs when the stock launcher component is disabled

This is the single most important safety note in this file.

Normal boot emits an **implicit** HOME start:

```console
I ActivityManager: START u0 {act=android.intent.action.MAIN cat=[android.intent.category.HOME]
  flg=0x10000100 cmp=com.jamdeo.tv.vod/com.ju.main.launcher.LauncherActivity} from uid 0
```

With the stock `LauncherActivity` disabled, **no HOME start is emitted at all**. No application window is
drawn, the boot animation never gets its first frame, and `sys.boot_completed` stays empty. Measured twice, on
two boots, sampling every 15 s for four minutes: every sample empty.

It is not "no home activity exists" — it is "nothing drew a frame". Proof: enabling the system
`com.android.settings/.FallbackHome` makes the boot emit

```console
START u0 {act=android.intent.action.MAIN cat=[android.intent.category.HOME]
  flg=0x10000100 cmp=com.android.settings/.FallbackHome} from uid 0
```

so the boot path does resolve the HOME intent; `FallbackHome` simply has no real home behind it, force-finishes,
and is restarted forever.

Recovery used while testing: because `adbd` lives on `/system` and the TV keeps `adb` reachable even with a
black screen, a small root-side script can re-enable the component and start a home intent; the TV then
finishes boot. Keep such a script available before touching anything (see §6.4).

---

## 5. Root path (outside CleanYourTV's no-root model, documented for completeness)

This is what actually takes HOME here. It needs root and a `/system` write; **CleanYourTV should not do this**.
It is recorded because people arriving from the no-root dead end will otherwise re-derive it from scratch.

### 5.1 Getting root

On this firmware the stock `autorun` service runs `/system/bin/autorun.sh` as **root on every boot**
(`/init.hisense.rc`: `service autorun ... user root`, `class main`, `oneshot`). That script ends by unpacking
`/data/local/misc/Arun.zip` into `/tmp`, `chmod 777 /tmp/run.sh`, and executing it. `/data/local/misc` is
`0777 shell:shell`, so ADB can write it. Trigger without a reboot:

```console
adb push hook.tar /data/local/misc/Arun.zip     # a tar (not zip) containing run.sh
adb shell setprop ctl.start autorun             # ~15 s later run.sh runs as uid 0
```

This is device-specific; treat it as "how this box was rooted", not as a portable recipe.

### 5.2 Install the launcher as a system app

```console
adb push TvHome.apk /data/local/tmp/TvHome.apk
# from a root shell:
mount -o remount,rw /
mkdir -p /system/priv-app/TvHome
cp /data/local/tmp/TvHome.apk /system/priv-app/TvHome/com.local.tvhome.apk   # filename MUST be <packageName>.apk
chown -R 0:0 /system/priv-app/TvHome
chmod 755 /system/priv-app/TvHome
chmod 644 /system/priv-app/TvHome/com.local.tvhome.apk
restorecon -R /system/priv-app/TvHome
sync
mount -o remount,ro /
```

Get the filename wrong and PackageManager rejects the whole directory:

```console
W PackageManager: Failed to parse /system/priv-app/TvHome: No packages found in split
```

Also make sure the directory is `0755` — a root umask of 077 produces `0700` and shell-side inspection fails.

### 5.3 Give the HOME filter priority 100

```xml
<intent-filter android:priority="100">
    <action android:name="android.intent.action.MAIN" />
    <category android:name="android.intent.category.HOME" />
    <category android:name="android.intent.category.DEFAULT" />
</intent-filter>
```

As a system app the priority is **no longer clamped**: `query-activities` reports `priority=100`, and
`resolve-activity` returns it. Owner-verified afterwards: the physical HOME key opens the custom launcher
directly, with no stock-launcher flash.

Confirm the install took:

```console
adb shell pm path com.local.tvhome
# package:/system/priv-app/TvHome/com.local.tvhome.apk
adb shell dumpsys package com.local.tvhome | grep -m1 flags=
# flags=[ SYSTEM HAS_CODE ALLOW_CLEAR_USER_DATA ]
```

### 5.4 Boot still draws the stock launcher first

The firmware starts the stock launcher before any third-party component is startable. Measured window: the
custom package cannot be started until roughly **8–10 s after `boot_completed`**; before that:

```console
Error: Activity class {com.local.tvhome/com.local.tvhome.MainActivity} does not exist.
```

A boot-time helper that polls `top activity`, then retries `am start` until it sticks, then force-stops the
stock package, is enough (90 s lifetime, no resident process):

```sh
# inside a boot hook that already has root
VODPKG=com.jamdeo.tv.vod
TVH=com.local.tvhome/.MainActivity
pm enable --user 0 com.jamdeo.tv.vod/com.ju.main.launcher.LauncherActivity   # keep boot drawable
k=0
while [ $k -lt 90 ]; do
  k=$((k+1))
  case "$(dumpsys activity activities 2>/dev/null | grep -m1 mResumedActivity)" in
    *com.jamdeo.tv.vod*)
      i=0
      while [ $i -lt 6 ]; do
        i=$((i+1)); am start -n "$TVH"; sleep 2
        case "$(dumpsys activity activities 2>/dev/null | grep -m1 mResumedActivity)" in
          *tvhome*) break ;;
        esac
      done
      sleep 1; am force-stop "$VODPKG"
      break ;;
  esac
  sleep 1
done
```

### 5.5 Reclaiming the stock launcher's memory (optional)

`am kill` is not enough (its keep-alive service pins the process). `am force-stop com.jamdeo.tv.vod` dropped
PSS from ~180–217 MB to zero and raised `MemAvailable` from ~678 MB to ~834 MB. It does **not** stop the TV
from starting it again on the next boot — which is exactly what the boot hook expects.

---

## 6. Field notes / gotchas worth keeping

1. **Never disable the stock launcher component** (§4). This is the one change that bricks the boot sequence
   without corrupting anything.
2. **`input keyevent 3` is swallowed** on this firmware; verify HOME with the remote, not with adb.
3. **`adbd` lives on `/system`** (`/system/bin/adbd`; `/sbin` only holds `charger`, `ueventd`, `watchdogd`).
   If a `/system` mistake stops it from mounting, ADB is gone too — there is no remote recovery at that point.
4. **Keep a watchdog.** A 180 s dead-man that re-enables the launcher component and starts a HOME intent turned
   three failed experiments into a recoverable state instead of a service call.
5. **Back up before writing `/system`**: `tar -cf /data/local/tmp/priv-app-backup.tar -C /system priv-app`
   (581 MB here). Restoring means deleting the added directory, but the tarball makes that provable.
6. **The home package can still be flipped later by a vendor property.** aerial-tv documents
   `persist.vt.use_home_pkg` working on Android 11 / MT9653 Hisense sets. It was **not tested** on this
   Android 9 build; if it exists here it would remove the boot flash entirely.

---

## 7. Diagnostic cookbook

```console
# who is home, and at what priority
adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME

# what the boot actually started
adb shell logcat -b system -d | grep 'START u0' | grep -i home

# did boot finish?
adb shell getprop sys.boot_completed

# preferred-activity state (needs root to read)
tr '>' '\n' < /data/system/users/0/package-restrictions.xml | grep -iE 'preferred|always'

# is the stock launcher's process still around?
adb shell ps -A | grep com.jamdeo.tv.vod
adb shell dumpsys meminfo com.jamdeo.tv.vod | grep -m1 TOTAL
```

---

## 8. Not evaluated

No vendor package was evaluated for a debloat list in this session, and none is proposed: there is no evidence
here to disable `com.jamdeo.tv.vod` or to copy package lists from another Hisense board. This note omits the
unit's serial number, network address and About-page screenshots.
