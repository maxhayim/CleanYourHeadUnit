# Xiaomi L43M7-EA: stock-launcher startup advertisement

On this TV, setting `Settings.System.boot_ad_switch` to `0` removed the approximately
15-second startup advertisement after a full restart. The stock launcher was retained.
This is a device-specific settings note, not a tested package-disable list.

## Tested configuration

Checked on a real TV on 2026-10-20:

| Field | Value |
| --- | --- |
| Retail model | Xiaomi L43M7-EA, 43-inch, 1920 x 1080 |
| Internal model / device | MiTV-ANTL0 / starwars |
| Android | 5.1.1, SDK 22 |
| Build (`ro.build.display.id`) | 23.2.4.2225 |
| Settings UI version | MiTV OS 2.12.2225 |
| Active stock launcher | `com.mitv.tvhome`, version 3.38.1017, versionCode 1017 |

The launcher was an updated system app. Record its version as well as the firmware:
the setting is read by the launcher, so the internal model alone is not a compatibility
guarantee. No root commands, package disabling, APK replacement or launcher installation
were needed.

## Difference from the older ANTL0 example

The [existing ANTL0 list](../../examples/redmi-mitv-antl0/debloat.txt) reports firmware
22.8.17.1977 and a `persist.bootvideo.bootanim` property change. On the configuration
above, changing that property from `true` to `false` survived a restart but did **not**
remove the 15-second advertisement. It was restored to its original value, `true`,
before the successful test.

That result limits the old method's applicability; it does not invalidate the report
from the older firmware.

## Read, change and verify

Commands below run in the TV's ADB shell. With the repository client, wrap each command
as `TV_HOST=YOUR_TV_IP tools/venv/bin/python -I tools/tv.py 'COMMAND'`, replacing
`YOUR_TV_IP` and `COMMAND`.

1. Read and save the original setting before changing it:

   ```sh
   settings get system boot_ad_switch
   ```

   On the tested TV this key was absent (`null`). Keep the original value or absence
   so it can be restored.

2. Change this one setting, then read it back:

   ```sh
   settings put system boot_ad_switch 0
   settings get system boot_ad_switch
   ```

   The readback should be `0`. Do not infer success from an empty command response.

3. Perform a full restart using the TV's normal controls and observe the startup.
   After reconnecting ADB, read the setting again. On this TV, network ADB became
   unavailable after a restart; toggling ADB debugging off and on in the TV's account
   and security settings restored the connection.

The owner confirmed that the 15-second advertisement disappeared. A subsequent
post-restart settings snapshot still contained `boot_ad_switch=0`. An approximately
10-second Xiaomi brand startup screen remained; this test does not establish a
reduction in the time required to initialize Android or the desktop.

## Restore the saved state

If the key was originally absent, restore that absence:

```sh
settings delete system boot_ad_switch
settings get system boot_ad_switch
```

The readback should be `null`. If the key had a value before the change, use
`settings put system boot_ad_switch ORIGINAL_VALUE` with that saved value instead,
then read it back. Setting it to `1` is not an exact restoration of an originally
absent key.

This is the restoration procedure, not a claim that the successful setting was
subsequently undone and the advertisement re-tested.

## Timed closure and persistence limits

Inspection of the installed launcher found that `BootUpADManager.canPlayBootAd()`
checks the Global `mitv.settings.advertise.state` setting and separately checks
`AdPreference.getBootAdSwitchValue()`. The latter reads the **System** key
`boot_ad_switch`, with a default of `1`; a value of `0` prevents boot-ad playback.
The settings namespace matters.

A temporary native closure was also investigated. Its Global keys were:

- `mitv.settings.advertise.closetime`
- `mitv.settings.advertise.state`
- `mitv.settings.advertise.tv.incremental`

Those keys were originally absent on this TV. After that experiment, all three were
restored to their original absence before the successful restart, and were still
absent in the later snapshot. They are not required steps in the procedure above.
Do not delete another TV's pre-existing values merely to copy this case.

The inspected seven-day reset path in `mitv.service.advertise.AdvertiseUtil` updates
those Global keys, not `System.boot_ad_switch`. This is code-inspection evidence
that the tested switch is separate from the timed closure, **not** a seven-day
observation or a promise of permanent removal.

The stock launcher's `AuthManageActivity` can write `boot_ad_switch=1` when handling
its `persist.sys.adswitch` ad-permission control. Changing that UI control may undo
the setting. Launcher/firmware updates and factory resets were not tested.

Validation covers this configuration and the observed restart only. It does not
establish long-term persistence, applicability to every Xiaomi TV, compatibility
with the project's replacement launcher, or the safety of the older example's
package-disable list on this firmware.
