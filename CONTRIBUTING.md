# Contributing

Thanks for helping. Head units vary enormously, and the project gets better mostly through what people learn on real ones.

Please read [SAFETY.md](SAFETY.md) first, and follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## Most wanted: reports from real units

Run the [hardware checklist](docs/hardware-checklist.md) and open a **Compatibility report** issue with `python3 tools/cyhu.py diagnostics --out report.json`. The report is sanitised, but read it before you post it. Reports of what *didn't* work are just as useful as what did.

## Device profiles

A profile (`data/profiles/<id>.json`) teaches CleanYourHeadUnit about a firmware family. Rules:

- **Match on what the unit runs**: a package that only that firmware has (`packages_all`, `packages_regex_any`) or a build property (`props_any`). Never match on a brand, seller or model name alone.
- **Protect generously.** If you're not sure a package is safe to disable, leave it Unknown or make it protected.
- **Every claim needs a source**: your own unit (say which, with the diagnostics), or a public page. Put sources in `sources`.
- `status` starts as `unverified`. A profile becomes `tested` only after the hardware checklist has passed on at least one named unit, with the report linked. Only `tested` profiles can make anything Recommended.
- Never include firmware files, download links to firmware, or factory codes.

Then run:

```
python3 -m unittest tests.test_cyhu
./gradlew :app:testDebugUnitTest      # Java: same vectors, plus screen renders
```

If you change the classifier or `data/protected.json`, add a vector to `tests/vectors/classify.json`. Both the Python and the Java tests run it.

## Telemetry entries

`data/telemetry.json` only takes packages with **published, citable evidence** (a security vendor's write-up, a paper, a CVE). Give `finding`, `purpose`, `evidence`, `risk` and `sources`. Don't call something spyware because it contacts its maker. That's a *connected service* unless there's evidence of more.

## Launchers, modules and themes

See [MODULES.md](MODULES.md), [THEMES.md](THEMES.md) and [COMMUNITY.md](COMMUNITY.md). In short:

- use the developer's real GitHub username; never guess one
- link the official source only
- closed source is fine, mirrored or repackaged APKs aren't
- compatibility is `untested` until there's a report
- screenshots must be real (or clearly labelled as mockups/renders) and show no personal information

## Code

- `tools/cyhu/`: Python 3.9+, standard library only. Run `python3 -m unittest tests.test_cyhu`.
- `app/`: plain Java, Android 4.4+ (minSdk 19), no AndroidX. Lint treats a call above minSdk as an error. Build with JDK 17: `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`.
- `./gradlew :app:testDebugUnitTest -Pscreenshots` re-renders `docs/screenshots`, `assets/logo.png` and the theme previews.
- No analytics, ads, tracking, accounts or network calls the user didn't ask for.
- Anything that changes the unit must be reversible, recorded, confirmed and verified.

## Pull requests

Small and focused, please. Describe what you tested and on what: a simulated fixture or a real unit (which one).
