# Philips 50PUS7303/12: Philips Sources and launcher notes

These are notes from one set, not a tested debloat list. The setup changed on 29 October 2026 and has not
had a several-day soak. Do not copy a sibling model's package list onto this sales model.

## Identify the exact firmware

| Field | Observed value |
|---|---|
| Retail model | Philips `50PUS7303/12` |
| Board | `TPM17XE_96` |
| Product | `PH7M_EU_5596` |
| SoC / memory | MediaTek MT5891 / approximately 2 GB |
| Android / SDK | Android TV 8.0.0 / 26 |
| Build display | `OC release-keys`, incremental `4718` |
| Android security patch | 2021-01-05 |
| Fingerprint | `Philips/PH7M_EU_5596/PH7M_EU_5596:8.0.0/OC/4718:user/release-keys` |

This is the TPM171E generation. The 55PUS7503 and 65PUS8102 lists use the same product code and firmware
family, but are different sales models; their package choices have not been validated on this 50PUS7303.

## Home and Philips Sources

The original Home was `com.google.android.tvlauncher`; CleanYourTV is now Home. Live TV is
`org.droidtv.playtv`.

The physical remote's Input key (scancode 610) has two actions: a tap opens CleanYourTV's Inputs, while
holding it for about 500 ms opens
`org.droidtv.channels/.sources.SourcesDrawerActivity`. Both actions were checked with the real remote.

`org.droidtv.eum` (Philips Help) must remain enabled for the Philips Sources menu on this set. With it
disabled, pressing the physical Sources button opened the drawer and it immediately closed with a
`NullPointerException` in `bindToEumService`. Re-enabling `org.droidtv.eum` restored the menu.

After some launcher reinstalls, `HomeKeyService` was configured but unbound; one reboot restored it.
Later reinstalls connected successfully, so this does not establish that a reboot is always required.

## Package and testing limits

- The original backup already showed `com.google.android.music`, `com.google.android.videos` and
  `com.google.android.play.games` disabled. They are baseline state, not packages tested in this setup.
- YouTube was re-enabled after its temporary disable. Apps manually uninstalled outside the disable
  batches are not list entries.
- Keep the tuner/HDMI stack, Philips Sources, remote and Bluetooth services, keyboard, Play Store and
  Play Services, Chromecast, Assistant, firmware updates and Ambilight enabled.
- The final disabled-package set has not had a several-day soak, so this note does not provide a
  debloat list. HDMI playback, tuner reception, USB media and other functions are not claimed as tested.
