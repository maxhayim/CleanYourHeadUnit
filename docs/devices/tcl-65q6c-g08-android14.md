# TCL 65Q6C: Google TV 14, G08_4K_GB

One unit, set up in one session on 2026-10-28, with several software reboots. These are owner
observations, not a several-day debloat list. Android identifies the set as `Smart TV Pro`, device `G08`,
product `G08_4K_GB`, platform `merak` (MediaTek), Android 14 / SDK 34, build
`tcl9618-user 14 UTT2.250416.001 AU02`, security patch 2025-12-05, about 2.4 GB RAM, connected over
Ethernet. On the day of the test, Google's system updater (`SystemUpdatePanoActivity`) reported the
firmware as up to date.

[`examples/tcl-55q6cx1`](../../examples/tcl-55q6cx1/) is the same Q6C series and board on the Android 12
build (`STT2.221228.001`). The [US G08 list](../../examples/tcl-g08-us-google-tv/) is the closest Android 14
firmware (`UTT2.250416.001 AU16`). Of those two lists, the packages that exist on this build disabled without
trouble.

The session used source revision `6ffa7fa5df7c38714cd89fd77fe636c53e759083`, with local launcher resource
changes only (dock, greeting, Ukrainian strings), and the `nature-calm` theme.

## What was disabled

The 18 packages that were off at the end of the session:

```
com.amazon.amazonvideo.livingroom
com.android.printspooler
com.google.android.apps.tv.launcherx
com.google.android.play.games
com.google.android.tts
com.google.android.tungsten.setupwraith
com.iqiyi.i18n.tv
com.tcl.dashboard
com.tcl.eva
com.tcl.hearaid
com.tcl.logkit
com.tcl.magiconnectfree
com.tcl.messagebox
com.tcl.ocean.instructions
com.tcl.repairguide
com.tcl.smartalexa
com.tcl.t_solo
com.wbd.stream
```

The owner re-enabled `com.tcl.browser` (BrowseHere), `com.internet.tvbrowser` and
`com.google.android.apps.tv.dreamx` (Ambient mode) after the first pass. The reference launcher was
installed with `HOME_KEY_SERVICE=1 INPUT_KEY_SERVICE=1`. Home resolved to `com.gathin.tvhome/.MainActivity`
after a reboot, and the Home and Settings keys worked.

Disabling `setupwraith` (`FORCE_SETUPWRAITH=1`) dropped the saved Wi-Fi networks, as the guard warns. The
set is on Ethernet, so nothing was lost here.

## Ambient mode with Google Photos does not survive the debloat

Ambient mode (`dreamx`, `.service.Backdrop`) can be set to Google Photos as the screensaver source, but with
launcherx and setupwraith disabled the screensaver only showed Google's stock photos:

1. The album picker (`.v2.photossetup.jumpstart.PhotosSetupActivityV2`) opened and closed at once. Logcat:
   `Failed to find provider info for com.google.android.apps.tv.launcherx...AccountModeContentProvider`.
   Enabling launcherx made the picker ask for account confirmation on the phone.
2. After confirmation, the picker reported that content could not be loaded. Logcat:
   `Failed to find provider info for ...castsetup.castsetupinfo.CastInfoProvider`, which setupwraith hosts.
   Enabling setupwraith made the albums load, and an album could be selected and confirmed
   ("Screensaver updated").
3. The screensaver still showed stock photos, before and after a reboot with both packages enabled, while
   `BackdropClient` logged `JWT token not available` on every start.

With setupwraith enabled again, its `RecoveryActivity` (priority 1) took Home over the reference launcher
even after `set-home-activity`, as described in [PROMPT.md](../../PROMPT.md).
`pm disable-user` on that single component hung the shell. The owner chose the reference launcher, so
launcherx and setupwraith were disabled again and the Google Photos screensaver was given up.

If you want personal photos on this set without launcherx, put them in a theme's `wallpapers/` folder
([`themes/`](../../themes/README.md)) instead of using Ambient mode.
