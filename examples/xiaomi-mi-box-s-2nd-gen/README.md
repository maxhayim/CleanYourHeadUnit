# Xiaomi Mi Box S 2nd gen (MiTV-AFKR0) — validated variants

Stock list: [`debloat.txt`](debloat.txt) (launcherx + setupwraith after CleanYourTV is Home).

## RTT0.211222.001.683 (EU, Carmonacu setup)

Validated on **MiTV-AFKR0** / **jaws**, **Google TV 11**, build **`RTT0.211222.001.683`**, 2 GB RAM, Xiaomi RC
(`Vendor_2717_Product_32b9.kl`). Differs from the **737/772** notes in the header of `debloat.txt`: shortcut scancodes
use **425/426** for YouTube (not keycode 190), **240/416** for Netflix, **207** for Prime Video.

### Extra packages disabled (beyond `debloat.txt`)

| Package | Why |
| --- | --- |
| `com.xiaomi.android.tvsetup.partnercustomizer` | Stock shortcut handler; must be off for `app_keys` / CleanYourTV key services |
| `com.google.android.youtube.tv` | Replaced by SmartTube (`org.smarttube.stable`) |
| `com.netflix.ninja` | Not used |

### Launcher

- CleanYourTV with **`InputKeyService`** and **`HomeKeyService`** (`launcher/build-and-install.ps1 -InputKeyService -HomeKeyService`).
- Theme: [`carmonacu-theme/`](carmonacu-theme/) (nature photos from `themes/nature-calm`, green-tinted background, dock + remote map in `config.json`).

```powershell
$env:TV_HOST = '<box-ip>'
powershell -ExecutionPolicy Bypass -File tools\theme.ps1 -TvHost $env:TV_HOST examples\xiaomi-mi-box-s-2nd-gen\carmonacu-theme
```

### Remote (`app_keys` in theme / `config.xml`)

| Button | Scancode(s) | App |
| --- | --- | --- |
| Netflix | 240, 416 | Crunchyroll |
| YouTube | 425, 426 | SmartTube stable |
| Prime | 207 | Max (`com.wbd.stream`) |

Wake the remote with **OK** before testing; first press after sleep is often dropped.

### Private DNS (AdGuard)

Blocks many tracker/ad domains system-wide; **does not** remove in-app video ads inside YouTube (use SmartTube) or all
streaming ads (Disney+/Max may still show promos). Tested:

```powershell
adb -s <box-ip>:5555 shell settings put global private_dns_specifier dns.adguard-dns.com
adb -s <box-ip>:5555 shell settings put global private_dns_mode hostname
```

Undo: `settings put global private_dns_mode opportunistic` (and delete specifier if needed).

### Animation scale

Snappier UI on 2 GB devices:

```powershell
adb shell settings put global window_animation_scale 0.5
adb shell settings put global transition_animation_scale 0.5
adb shell settings put global animator_duration_scale 0.5
```

### Apply debloat (this variant)

Use the stock list, then the extras above (or `debloat-phase1.txt` + `setupwraith` after launcher install). Keep
`com.xiaomi.mitv.updateservice` disabled only if you accept no OTA from Xiaomi.

### Still working after soak

Wi‑Fi (re-enter password once if `setupwraith` was removed while the network was tied to it), Chromecast, Google
Assistant, SmartTube + Disney+ + Crunchyroll + Max, Bluetooth remote shortcuts via scancodes.
