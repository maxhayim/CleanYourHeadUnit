# Philips 50PUS7303/12: Philips Sources and launcher notes

The Philips 50PUS7303/12 in Europe is recorded here as a second tested model on the same product,
board and firmware build as the 55PUS7503/12. The owner reports several days of use without issues.

## Identify the exact firmware

| Field | Observed value |
|---|---|
| Retail model | Philips `50PUS7303/12`, Europe |
| Board | `TPM17XE_96` |
| Product | `PH7M_EU_5596` |
| SoC / memory | MediaTek MT5891 / approximately 2 GB |
| Android / SDK | Android TV 8.0.0 / 26 |
| Build display | `OC release-keys`, incremental `4718` |
| Android security patch | 2021-01-05 |
| Fingerprint | `Philips/PH7M_EU_5596/PH7M_EU_5596:8.0.0/OC/4718:user/release-keys` |

This is the TPM171E generation. The build values above were read from the original backup; no
marketing firmware version was recorded. The 65PUS8102 list also uses this product code and build,
but its additional package choices have not been validated on this 50PUS7303.

## Tested package choices

The 50PUS7303 setup disabled the seven feedback, tutorial, promotional, logging and recommendation
packages active in [debloat.txt](debloat.txt). The stock launcher was replaced by CleanYourTV.
It also disabled the eight packages shown as commented options at the end of that file:
Inscape ACR, the Philips DAC pair, store-demo stickers, Twitter sharing, the NetTV app gallery,
Prime Video and Alexa. Those options remain commented to preserve the 55PUS7503 list's defaults
and to leave streaming apps and voice assistants available unless an owner chooses otherwise.

Use the Apply and Undo instructions in the list header. The 55PUS7503's HDMI-port, YPbPr and
Netflix checks are not test claims for the 50PUS7303.

## Home and Philips Sources

The original Home was `com.google.android.tvlauncher`; CleanYourTV is now Home. Live TV is
`org.droidtv.playtv`.

`org.droidtv.eum` (Philips Help) must remain enabled for the Philips Sources menu on this set. With it
disabled, pressing the physical Input button opened
`org.droidtv.channels/.sources.SourcesDrawerActivity`, its default target, but the drawer immediately
closed with a `NullPointerException` in `bindToEumService`. Re-enabling `org.droidtv.eum` restored the
menu, confirmed with the physical remote.

After some launcher reinstalls, `HomeKeyService` was configured but unbound; one reboot restored it.
Later reinstalls connected successfully, so this does not establish that a reboot is always required.

## Package and testing limits

- The original backup already showed `com.google.android.music`, `com.google.android.videos` and
  `com.google.android.play.games` disabled. They are baseline state, not packages tested in this setup.
- Keep the tuner/HDMI stack, Philips Sources, remote and Bluetooth services, keyboard, Play Store and
  Play Services, Chromecast, Assistant, firmware updates and Ambilight enabled.
- The owner confirmed HDMI playback, tuner reception and USB media playback through the built-in app.
- Other functions are not claimed as tested.
