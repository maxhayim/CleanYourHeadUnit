# CleanYourHeadUnit Companion (phone app)

The Companion is an Android phone app that does the computer tool's job from your pocket. It also works as a **bridge** for head units that have no Wireless debugging.

> **Status: not yet tested on a real head unit or a real phone.** The ADB client is tested against a simulated adbd, and the debloat logic against a simulated unit, in CI. See [docs/test-report.md](docs/test-report.md).

## Why

Many budget head units say "Android 13" but really run Android 8.1 or 9 (the build number gives it away; the Companion shows the real version). Wireless debugging only exists from Android 11, so those units can't be reached over Wi-Fi until ADB is switched to network mode once, and that needs a USB connection. The phone can make that connection.

## What it does

- **Connect over USB.** The phone is the USB host: use an OTG cable or adapter to the head unit. The head unit's USB port has to be one that can act as a device, the way a phone's port does for a computer. Not every head unit has such a port.
- **Connect over Wi-Fi** to a unit whose ADB already listens on the network (port 5555 by default).
- **Make wireless (the bridge).** Over USB, it asks the unit's ADB to listen on Wi-Fi (`adb tcpip 5555`), then reconnects over Wi-Fi so you can unplug the cable. A computer can then use `adb connect <unit-ip>:5555` too. This lasts until the unit restarts; most head units only sleep when the ignition is off. **Turn wireless ADB off** switches it back to USB-only.
- **Read the unit:** model, the real Android version (and the version its settings claim), chipset, screen, memory, firmware family, Home app.
- **Review packages** sorted by the same rules as the computer tool and the head-unit app (the shared `data/` and `shared/java/.../Rules.java`), **plan, apply and restore.**
- **Declare custom firmware** that can't be recognised automatically, such as Sergey Kozlov's.

## What it never does

- **It doesn't get round Android's protection.** The unit must have USB debugging on, and the first time it asks *"Allow USB debugging?"* with this phone's key fingerprint. You tap Allow on the unit.
- **It never disables Protected apps:** Android core, everything vehicle-related (MCU, CAN bus, doors and car status, cameras, radio, amplifier, Bluetooth calling, climate, steering-wheel keys, updaters) and every app that can be Home.
- **It never disables Unknown apps.** It's stricter than the computer tool here: it has no "allow Unknown" option. An app with no documented purpose can only be disabled from a computer, by name, with `cyhu`.
- **Advanced apps need a tick.** You have to tick *Include Advanced* and read each risk first.
- **Nothing is uninstalled.** Only `pm disable-user --user 0` and `pm enable --user 0` are used.
- **No account, no analytics, no ads.** The only network connection is to the head unit.

## Backups

Before the first change, the unit's state is saved on the phone, one folder per unit. Every change is recorded with its before and after state. Restore works newest first, and leaves alone anything you changed by hand since. The files are the same as the computer tool's (`baseline.json`, `changes.json`, `log.jsonl`). The unit's serial number is never stored; the folder name is a hash.

## Wireless debugging (Android 11+ units)

On an Android 11+ unit, the Companion pairs and connects without a cable:

1. On the unit: Developer options → **Wireless debugging** → On, then **Pair device with pairing code**. Keep that window open.
2. On the phone: **Find on this Wi-Fi**, then the **Pair** entry, and type the six-digit code. (Or **Pair with code** and type the address and port from that window.)
3. The Companion then finds the unit's Wireless debugging and connects over TLS. Next time, **Find on this Wi-Fi** → **Connect** is enough.

Pairing needs the **phone** to run Android 12 or newer. This is also the way to reach **Android 11** units, which can't pair with themselves. Some networks and phone hotspots block the search: then type the address and port shown under Wireless debugging.

## ADB console

Once connected (USB, port 5555 or Wireless debugging), **Open console** runs shell commands on the unit, as `adb shell` does from a computer: quick buttons for device details, disabled apps, the Wi-Fi address, memory, storage and the recent log. Commands stop after 30 seconds. Changes made in the console skip the Debloater's rules and aren't recorded for Restore; a command that would disable, remove, hide or clear a **Protected** or **Unknown** app (or the unit's Home app) asks first. Commands are logged on the phone only.

## Security note

With wireless ADB on, the unit accepts ADB connections from the network. A normal unit still asks *"Allow USB debugging?"* for every new device. Some budget units have that check switched off (`ro.adb.secure=0`); the Companion warns you when it sees this, because then anyone on the same Wi-Fi could control the unit. Use the bridge on a network you trust, and turn wireless ADB off when you're done.

## Not supported yet

- Launcher switching, Private DNS and the privacy audit (they're in the head-unit app and the computer tool).

## Download

Get `CleanYourHeadUnit-Companion-<version>.apk` from the [Companion releases](https://github.com/maxhayim/CleanYourHeadUnit/releases) (tags `companion-v*`) and install it on an Android 7.0+ phone. Android asks you to allow installs from your browser or file manager first.

It's a pre-release: not yet tested on a real phone or head unit. Releases are signed with the Companion's own key, so later versions install over earlier ones. The signing certificate's SHA-256 is:

```
a8e531626c7e7b1f26115026de856a0541ceda02b7bdc1bade1f4b979b47a8c7
```

A different certificate means the APK didn't come from this project. If you installed a debug build you made yourself, uninstall it first.

## Building

```
./gradlew :companion:assembleDebug
adb install companion/build/outputs/apk/debug/companion-debug.apk
```

Maintainers build the signed release with `tools/release-companion.sh` (the key is kept outside the repo). It needs Android 7.0 or newer on the phone. The ADB client is written for this project from AOSP's published protocol description (`packages/modules/adb/protocol.txt`), without third-party code.
