# Privacy Shield

Privacy Shield helps you find software on the head unit that's documented to collect or send data, and choose what to do about it. It works with any launcher.

**What it can't do:** no tool can stop all vehicle tracking. Many cars have a telematics module outside Android, which the head unit can't see. Per-app network activity can't be observed over ADB without root, so CleanYourHeadUnit doesn't claim to know what an app sends. Every blocked-connection count comes from the local firewall's own counters, and is zero when it isn't running.

## Telemetry detection

`data/telemetry.json` lists packages with **published evidence**, each with its sources. Nothing is listed on suspicion alone, and contacting a manufacturer's server is not called spyware.

| Package | Finding | Evidence |
|---|---|---|
| `com.adups.fota`, `com.adups.fota.sysoper` | Confirmed telemetry | Kryptowire (Nov 2016) found versions sending SMS content, call logs, contacts and identifiers to `bigdata.adups.com`. Malwarebytes reports later versions dropped that code. Which version a unit has is unknown until checked. |
| `com.redstone.ota.ui` | Suspected tracking | Malwarebytes (2021) detects it as PUP.Riskware.Autoins.Redstone: an updater that also installs apps on its own. |
| `com.tw.core` | Confirmed telemetry | Kaspersky Securelist (2026): DoFun's TWCore collects device telemetry and downloads and installs APKs. |
| `com.tw.jar1` ("JarService") | Malware indicator | Kaspersky Securelist (2026): the multi-stage downloader delivered through TWCore. A unit with it should be considered compromised. |
| `com.google.android.feedback` | Connected service | User-initiated feedback reports. Listed the same way CleanYourTV lists it. |

Finding labels: *Confirmed telemetry*, *Suspected tracking*, *Advertising*, *Malware indicator*, *Connected service*, *Unknown traffic*.

Disabling an updater also stops firmware updates, and each entry says so. Where a unit carries a malware indicator, disabling is not a clean-up, so ask the seller for fixed firmware.

To add a package: open an issue with a **public, citable source**. Your own capture is welcome as supporting evidence, but it isn't enough on its own.

## Permission audit

Lists apps granted location, the microphone, contacts and messages (contacts, call log, SMS), storage, network access, and starting by themselves at boot. This shows what apps are *allowed* to do, from Android's records, not what they did. On Android 6+ you change a permission on the app's page in Android Settings. On older versions permissions are granted at install time, so the choice is to disable the app or keep it.

## Network protection

### Private DNS (Android 9+)

Sends DNS lookups encrypted to a filtering resolver. Built-in choices (hostnames resolved and checked on 2026-11-01):

| Provider | Hostname | Blocks |
|---|---|---|
| AdGuard DNS | `dns.adguard-dns.com` | ads and trackers |
| Mullvad (adblock) | `adblock.dns.mullvad.net` | ads and trackers |
| Mullvad (base) | `base.dns.mullvad.net` | ads, trackers and malware |
| Quad9 | `dns.quad9.net` | malware only |

`cyhu privacy dns <provider>` sets it, records the old value, and `cyhu restore --dns` puts it back. In the app it's one tap if you've granted `WRITE_SECURE_SETTINGS` (see [INSTALL.md](INSTALL.md)). Otherwise the app shows the hostname to type into Android's setting.

### Local firewall (app, optional)

Uses Android's `VpnService`, entirely on the unit. Nothing goes to any server of ours.

- **DNS filter** (Android 4.4+): only DNS is routed into the firewall. Listed domains get "no such domain". Everything else is forwarded to your network's own DNS server, outside the VPN. The block list is the telemetry catalogue's domains plus one list you choose (StevenBlack hosts, MIT; or AdGuard DNS filter, GPL-3.0; downloaded only when you press the button) or a file you import. The allowlist always wins.
- **App block** (Android 5.0+): the apps you choose get no network at all while it runs. Protected apps can't be chosen.

It needs Android's VPN permission (Android asks you). Android shows a key icon and the app shows a notification with **Stop** while it runs. It doesn't restart after a reboot, doesn't open or decrypt HTTPS, installs no certificate and never stores what you browse. It records only counters and the last 30 blocked domain names, on the unit. Android allows one VPN at a time, so the app warns you if another is active. Settings can be exported and imported as JSON.

Limits: DNS filtering doesn't affect apps with their own DNS-over-HTTPS or fixed IP addresses. DNS lookups can't be attributed to individual apps, because Android resolves them for all apps.

## Profiles

| Profile | What it proposes |
|---|---|
| **Standard** (default) | Shows verified findings. Proposes only tested removals. Keeps navigation, maps and connected apps. Doesn't filter the network. |
| **Strict** | Also proposes the Advanced telemetry packages (each one still needs your OK), suggests a filtering Private DNS, and proposes the firewall's App block for packages with a confirmed-telemetry or malware finding. Vehicle functions stay protected. |
| **Custom** | You choose every package and rule. Protected packages still can't be chosen. A JSON file works with `cyhu privacy profile custom --file my.json`. |

A profile only *proposes*. Nothing is applied until you confirm.

## Location

Receiving GPS sends nothing anywhere, so CleanYourHeadUnit never turns GPS off: navigation needs it. An app needs the location permission to read where the car is, and network access to send it. The permission audit shows both.

## Privacy of CleanYourHeadUnit itself

No account, analytics, ads or tracking. The app connects to the internet only when you press something that needs it: downloading a catalog app from its developer's GitHub release, checking for new releases, or downloading a block list. The local firewall also forwards DNS lookups for you. Diagnostics are written to a file on the unit and are never uploaded.
