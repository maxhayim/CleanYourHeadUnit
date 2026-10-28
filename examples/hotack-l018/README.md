# HOTACK L018 Smart Projector (Android 11, Allwinner H713)

Chengdu Hotack's L018, sold as "Smart Projector". The firmware calls itself `ADT-3`, build
`Projector.20260530.1707`, with the LuminaOS launcher (`com.htc.luminaos`). Both halves work: the
debloat list and the Clean Your TV launcher as Home. Applied on 24 October 2026.

| File | What it is |
|---|---|
| `debloat.txt` | The 18 packages that were disabled, grouped and commented, with what to keep and why |
| `config.json` | Hidden tiles and calmer motion, the two settings this projector needs; push it with `tools/theme.sh` |

All commands run from the repo root, with the projector's IP in `TV_HOST`.

## Install

```bash
TV_HOST=<projector ip> tools/disable.sh examples/hotack-l018/debloat.txt
TV_HOST=<projector ip> STOCK_LAUNCHER=none launcher/build-and-install.sh
TV_HOST=<projector ip> tools/theme.sh examples/hotack-l018
```

`STOCK_LAUNCHER=none` keeps LuminaOS enabled: the projector's own settings (network, Bluetooth,
projection, keystone) live only inside it. Before you build, add its settings screen to
`settings_fallback_components` as the header of `debloat.txt` says, or the Settings pill has nowhere to go.

## Notes

- **What `config.json` sets, and what it leaves to you.** It sets only what this projector needs.
  `hidden_apps` hides two tiles: LuminaOS, which stays enabled for its settings but is not an app to
  open from Home, and `com.softwinner.tv.factorymenu`, the factory menu, which must never be opened.
  The motion keys are the next note. The dock and the number of columns are a matter of taste, so they
  stay at the launcher's defaults; add `dock_apps` and `columns` to the file for your own apps.
- **Motion.** With the default spring motion, the focus move took about 0.8 s and most of its frames
  missed their deadline: the GPU does not keep up. `config.json` sets `reduced_motion` and turns
  `focus_overshoot` off.
- **`tools/bench.py`.** This firmware does not log `injectKeyEvent`; its window manager logs
  `TvWindowManager: key event key = <KEYCODE>` instead. So `bench.py` does not measure the return to
  Home here, and times key response from just before the key is sent.
- **Projector settings over ADB.** `am start -n com.htc.luminaos/com.htc.luminaos.activity.MainSettingActivity`
  opens them without switching Home; HOME still returns to the Clean Your TV launcher.
