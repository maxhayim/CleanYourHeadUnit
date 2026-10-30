# Xiaomi Mi Box S 2nd gen (MiTV-AFKR0) — validated variants

Stock list: [`debloat.txt`](debloat.txt) (disable `launcherx` and `setupwraith` only after CleanYourTV owns Home).

## RTT0.211222.001.683 (EU remote map)

Validated on **MiTV-AFKR0** / **jaws**, **Google TV 11**, build **`RTT0.211222.001.683`**, 2 GB RAM, Xiaomi RC
(`Vendor_2717_Product_32b9.kl`). The **737/772** entries in the header of `debloat.txt` document keycode **190**
for YouTube; on **683** the same remote uses **scancodes** instead — do not copy the 737 `app_keys` lines blindly.

### Extra packages (typical for SmartTube + custom remote map)

| Package | Why |
| --- | --- |
| `com.xiaomi.android.tvsetup.partnercustomizer` | Stock shortcut handler; disable when using CleanYourTV `app_keys` with **InputKeyService** / **HomeKeyService** |
| `com.google.android.youtube.tv` | Optional: disable when using SmartTube (`org.smarttube.stable`) |
| `com.netflix.ninja` | Optional: disable if unused |

### Launcher install

```powershell
$env:TV_HOST = '<box-ip>'
cd launcher
powershell -ExecutionPolicy Bypass -File .\build-and-install.ps1 -TvHost $env:TV_HOST -InputKeyService -HomeKeyService
```

Look and wallpaper are personal: use any folder under [`themes/`](../../themes/README.md) with `tools/theme.ps1`, or
change **Wallpaper** in **Launcher settings** (hold OK on **Settings** → **Launcher settings**) after a theme with
pictures is on the TV.

Remote mapping for this build is in [`launcher-config.example.json`](launcher-config.example.json). Push it in a
**your own** theme folder (copy a theme from `themes/` first — `manage.py` refuses writing into `examples/`), or bake
the same keys into `launcher/.../config.xml` before `build-and-install`.

### Remote (`app_keys` — build 683, `Vendor_2717_Product_32b9.kl`)

| Button | Scancode(s) | Typical target |
| --- | --- | --- |
| Netflix | 240, 416 | Your streaming app (example: Crunchyroll) |
| YouTube | 425, 426 | SmartTube stable (`org.smarttube.stable`) |
| Prime | 207 | Max (`com.wbd.stream`) |

Confirm scancodes on your unit: `tools/tv.py 'getevent -lt'` (press OK on the remote first if it was sleeping).

Install order: **launcher first**, then disable `com.google.android.apps.tv.launcherx` and
`com.google.android.tungsten.setupwraith` (see `debloat.txt`). If `disable.sh` skips setupwraith because Wi‑Fi was saved
during setup, re-join the network in Settings or use `FORCE_SETUPWRAITH=1` knowing the password may be asked again.

### Private DNS (optional, set once)

In this project the most documented **set-and-forget** choice is **[AdGuard public DNS](https://adguard-dns.io/en/public-dns.html)** filter (`dns.adguard-dns.com`). It appears throughout `docs/faq.md`, device notes, and `PROMPT.md`. It **persists across reboots**; you should not need to toggle it unless something stops playing.

```powershell
adb -s <box-ip>:5555 shell settings put global private_dns_specifier dns.adguard-dns.com
adb -s <box-ip>:5555 shell settings put global private_dns_mode hostname
```

Check: `settings get global private_dns_mode` → `hostname`. Undo only if an app or provider breaks:
`settings put global private_dns_mode opportunistic`.

**Limits:** does not remove YouTube ads (use SmartTube) or all in-app promos on Disney+/Max. **[NextDNS](https://nextdns.io)** is the usual alternative if you want your own blocklists (needs an account and a personal hostname); cleanyourtv documents it less often than AdGuard.

Many Google TV boxes have **no Settings UI** for Private DNS; ADB (or the prompt’s `tools/tv.py` lines) is normal.

### Animation scale (optional)

```powershell
adb shell settings put global window_animation_scale 0.5
adb shell settings put global transition_animation_scale 0.5
adb shell settings put global animator_duration_scale 0.5
```

### Apply debloat

[`debloat-phase1.txt`](debloat-phase1.txt) then launcher install, then [`debloat-phase2-setupwraith.txt`](debloat-phase2-setupwraith.txt), or the full [`debloat.txt`](debloat.txt) with the same ordering rules.

### Still working after soak (683)

Wi‑Fi, Chromecast, Google Assistant, SmartTube, scancode shortcuts with partnercustomizer disabled.
