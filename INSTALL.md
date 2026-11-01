# Install

## The computer tool (`cyhu`)

1. Install Python 3.8 or newer and [Android platform-tools](https://developer.android.com/tools/releases/platform-tools) (for `adb`).
2. Get CleanYourHeadUnit: `git clone https://github.com/maxhayim/CleanYourHeadUnit` (or download the ZIP).
3. No `pip install` is needed: `cyhu` only uses the Python standard library.

```
cd CleanYourHeadUnit
python3 tools/cyhu.py --demo tests/fixtures/fyt-android10-1280x720.json status   # try it on a simulated unit
```

## Connect the head unit

You need **Developer options → USB debugging** turned on. On head units it's often hidden:

- **Most units:** Settings → About → tap *Build number* seven times, then Settings → System → Developer options.
- **FYT (and similar):** factory or developer settings sit behind a code entered in the car settings. The code varies by firmware version: ask your seller or check your unit's community thread. CleanYourHeadUnit doesn't publish codes.
- **MTCD/MTCE:** Developer options is usually in Settings after the *Build number* taps. Some firmware hides it in *Factory settings*.

Then either:

- **USB:** most units have a USB port that works as a device port only in a specific mode, or only on one of their ports. Check your unit's manual.
- **Wi-Fi:** on some units, Developer options has *ADB over network* or *Wireless debugging*. Then:
  ```
  adb connect <unit-ip>:5555
  ```
  On Android 11+ with *Wireless debugging*, pair first with `adb pair <ip>:<port>`.

Check the connection:

```
adb devices                         # the unit should say "device"
python3 tools/cyhu.py status        # read-only
python3 tools/cyhu.py backup        # record the unit as it is now
```

If more than one device is connected, add `--serial <serial-or-ip:port>` to every command.

## The app

There is no published release yet. To build one:

```
./gradlew :app:assembleDebug        # needs JDK 17 and the Android SDK
adb install app/build/outputs/apk/debug/app-debug.apk
```

The app runs on Android 4.4 and newer. On first open it records a snapshot of the unit. That's the "before" that Backup & Restore compares against.

### Optional: let the app set Private DNS itself

Android only lets apps change Private DNS with a permission you grant from a computer:

```
adb shell pm grant io.github.maxhayim.cleanyourheadunit android.permission.WRITE_SECURE_SETTINGS
```

Without it, the app shows the hostname for you to type into Android's Private DNS setting.

### Optional: CleanYourHeadUnit Home

The app includes a simple launcher. It's off by default. Turn it on in **Settings → CleanYourHeadUnit Home**, then choose it under **Launchers → Choose Home app**. Your factory launcher stays installed and enabled.

## Uninstall

1. Run `python3 tools/cyhu.py restore --all`, or use **Backup & Restore** in the app, if you want your changes undone.
2. Uninstall the app like any other: `adb uninstall io.github.maxhayim.cleanyourheadunit`.
3. Delete `~/.cleanyourheadunit` on the computer if you don't need the records any more.
