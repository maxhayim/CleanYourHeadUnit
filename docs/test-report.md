# Test report

Date: 2026-11-01. Machine: macOS (Apple silicon), JDK 17, Android Gradle Plugin 8.7.3, Robolectric 4.14.1 (Android 14 runtime).

**No real head unit, phone, or emulator was used.** Every result below is from automated tests against simulated units. The simulated units (`tests/fixtures/*.json`) are labelled SIMULATED in each file. Only package names taken from public sources (FYT, Adups, Redstone, DoFun) are real, and every `com.example.*` name is invented.

## Results

| Suite | Command | Result |
|---|---|---|
| `cyhu` (Python 3.9.6) | `python3 -m unittest tests.test_cyhu` | 44 passed |
| `cyhu` (Python 3.12.14) | same | 44 passed |
| Python 3.8 syntax | `ast.parse(..., feature_version=(3, 8))` over `tools/` and `tests/` | no problems |
| Catalog and themes | `python3 tools/cyhu.py catalog validate` | OK |
| App build | `./gradlew :app:assembleDebug` | OK (debug APK, unsigned release not built) |
| App unit and UI tests | `./gradlew :app:testDebugUnitTest` | 16 passed (DnsTest 4, RulesTest 5, ScreensTest 7) |
| Android lint | `./gradlew :app:lintDebug` | 0 errors, 3 warnings (below). Calls above minSdk 19 are errors. |
| GitHub Actions | `.github/workflows/*.yml` | **not run yet**: the repository isn't on GitHub |

## What the tests cover

**Python (`tests/test_cyhu.py`)**

- the shared classification vectors (30)
- vehicle keyword matching
- profile matching on hardware signals only (names never match), including declared Sergey Kozlov firmware and its policy
- plan refusals (Protected, Unknown, disabled, missing)
- apply with verification, including a simulated firmware that silently refuses a disable
- idempotent restore, the "changed since" rule and `--force`
- launcher set and undo (factory launcher never disabled)
- Private DNS set and undo, Android <9 refusal
- telemetry findings and the permission audit
- diagnostics sanitising (serials, MACs, e-mails, LAN IPs)
- catalog and theme validation, including rejection of commands, wrong owners and bad colours
- release asset choice, refusal of non-GitHub download origins, and refusal of a wrong digest or signing certificate
- the APK inspector against a real APK (`CYHU_TEST_APK`; run with this app's own debug build. RetroDeck 0.15.0 was also inspected earlier and matched `apksigner`)

**Java**

- `DnsTest`: DNS query parsing (including malformed packets), NXDOMAIN answers, IPv4/UDP packet building with checksums verified, hosts and adblock list parsing, parent-domain matching, allowlist precedence, and refusal of bare top-level domains.
- `RulesTest`: the same 30 shared vectors as Python, profile matching, Protected and Unknown never offered, theme validation, report scrubbing.
- `ScreensTest`: opens the real `MainActivity` on simulated FYT (1280×720), ALPS (1024×600), Android 4.4 (800×480), DoFun portrait (1080×1920) and 1920×720 units, visits every section, and checks:
  - the model and Android version are shown
  - no serial or MAC address is shown or exported
  - Protected packages have no "Add to plan"
  - the firewall shows Off
  - telemetry entries show their sources
  - @tdater, @Gathin23 and Sergey Kozlov are credited correctly, with no invented username
  - Webmote shows "Automotive support planned"
  - all three themes apply
  - CleanYourHeadUnit Home renders and is off by default

  With `-Pscreenshots` it writes the PNGs in `docs/screenshots`.

## Lint warnings left

| Warning | Why it stays |
|---|---|
| `PrivateApi` (`Unit.sysProp`) | Reads `android.os.SystemProperties` to *display* build and MCU properties. It's read-only, wrapped in a catch, and the app works without it. |
| `NotificationIconCompatibility` (`ic_shield`) | The firewall notification uses a vector icon. On Android 4.4 the system may draw it as a blank square. Fine to fix later with a PNG. |
| `ViewConstructor` (`Ui.Grid`) | Grid is only created in code, never inflated from XML. |

## Not tested

- anything on a real head unit (see [hardware-checklist.md](hardware-checklist.md))
- the local firewall moving real traffic: only its packet code is tested, not a live `VpnService`
- real ADB: tested against `FakeDevice`, which simulates `pm`, `cmd package`, `settings`, `getprop`, `dumpsys` and `wm`
- the GitHub API call and a real download: asset choice and the digest and certificate checks are tested on local data, but no download from GitHub was run or installed
- Android versions other than 14 for the app UI (the Robolectric runtime used). Lint checks API use down to 19.
- Windows (CI is configured for it but hasn't run)
