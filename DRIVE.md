# Drive: features for older units

Drive (in the app's menu) adds things cheap and older head units usually lack. None needs ADB or root, nothing leaves the unit, and each one is off until you turn it on.

**Not yet tried on a real head unit.** These are tested against simulated units only (see the CHANGELOG). Try each one parked first.

## Speed-based volume

The music gets one step louder for each speed step above a starting speed, up to a limit, and quieter again as you slow down (it waits 5 km/h below each step, so it doesn't flick at a steady speed). It's added on top of your own volume: if you change the volume while driving, your new volume becomes the base.

- Needs the location permission (GPS speed). A notification shows while it runs.
- Choose: start speed, step, the most steps added, and km/h or mph.
- It changes Android's media volume. On units where the amplifier volume is kept by the car-interface chip (the MCU), it may not change what you hear. Check by watching the volume on screen while it changes.

## Startup actions

After power-up, after the delay you choose: set the media volume, open one app (music or navigation), and send Play.

- **Also after waking from sleep:** many units don't restart when the ignition goes off; they sleep. With this on, the screen coming back after at least two minutes off counts as a power-up. This keeps a small background service running, with its notification.
- **Try it now** runs the actions straight away.
- Android 10 and newer don't let apps open other apps from the background, so there only volume and Play may work.

## Day and night brightness

Sets Android's screen brightness to your day level at sunrise and your night level at sunset. Sunrise and sunset are worked out on the unit from your position, with no internet needed.

- Needs **Modify system settings** (granted once, in Android Settings; the app opens the page) and a position (the location permission, or any earlier fix).
- Some units dim the backlight themselves when the headlights are on. This sets Android's own brightness, and the two can combine.

## Phone hotspot helper

Choose your phone's hotspot (connect to it once in Wi-Fi settings first). After power-up, and whenever the connection drops, the app switches Wi-Fi on and picks that network. A notification says **NO INTERNET** when there's none, so you know to turn on the phone's hotspot.

- Up to Android 9 (most head units, including "Android 13" ones that are really 8.1). Android 10 and newer don't let apps pick a Wi-Fi network, so there it only watches and tells you.
- It never forgets, edits or switches off your other networks.

## Slowness check

Shows free memory and storage, and every app that starts itself at power-up, sorted by the rules: Protected, Unknown, your own, and the ones the rules offer to disable. Each of those opens its Debloater page, with its risk and the ways to disable it on this unit. With **Direct changes** connected, it also lists the apps using the most memory (`dumpsys meminfo`); without ADB, Android doesn't let one app see another's memory.

## Permissions

| Permission | For | When it's asked |
|---|---|---|
| Location | Speed-based volume (GPS speed); sunrise and sunset | When you turn either on |
| Modify system settings | Day/night brightness | Once, in Android Settings |
| Start at power-up | Startup actions, brightness, the background service | Granted at install; does nothing until a Drive feature is on |
| Change Wi-Fi | Hotspot helper (Android 9 and older) | Granted at install |
| Background service | Speed volume, hotspot helper, wake detection | Runs only while one of those is on, with a notification |
