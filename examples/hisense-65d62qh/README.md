# Hisense 65D62QH (China, JuUI 9.0.10.845)

Android 11 on MediaTek `MT9653`, build `RP1A.200720.011`, no GMS. Same platform and build as
`../vidda-mt9653/` and `../hisense-e5q-pro/`; this list was derived from theirs and re-checked against
this set's own `pm list packages` (131 packages, nothing disabled at the factory).

- `debloat.txt` — five batches, each applied and reboot-checked on the real set. 26 packages stick.
- `config.json` — the vendor source panel (there is no `TvInputManager` input to list on this
  firmware), Live TV, and the tiles that should stay off Home.

Full notes, including the launcher handover and the blank screen this firmware gives you once
`com.jamdeo.tv.vod` is disabled, are in
[`docs/devices/hisense-65d62qh.md`](../../docs/devices/hisense-65d62qh.md).

```bash
TV_HOST=<tv ip> tools/disable.sh examples/hisense-65d62qh/debloat.txt
TV_HOST=<tv ip> tools/restore.sh examples/hisense-65d62qh/debloat.txt
TV_HOST=<tv ip> tools/theme.sh examples/hisense-65d62qh
```

The stock launcher is `com.jamdeo.tv.vod`; it is not auto-detected, so pass it explicitly to the
install script: `STOCK_LAUNCHER=com.jamdeo.tv.vod` (`-StockLauncher com.jamdeo.tv.vod` on Windows).
