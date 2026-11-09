# Modules and the launcher catalog

Modules are independent apps that work well on head units. Each is its own project, maintained by its own developer, with its own licence and releases. CleanYourHeadUnit lists them, credits them, and can download them from the developer's own release page. It never bundles, mirrors or modifies them.

## Listed today

| Entry | Kind | Developer | Source | Status |
|---|---|---|---|---|
| [RetroDeck](https://github.com/maxhayim/RetroDeck) | module (featured) | [@maxhayim](https://github.com/maxhayim) | GitHub releases, MIT | untested on head units |
| [Webmote](https://github.com/maxhayim/webmote) | module | [@maxhayim](https://github.com/maxhayim) | GitHub releases, MIT | **Automotive support planned**: no install offered yet |
| [KITT Launcher](https://github.com/tdater/KITT-Launcher-releases) | launcher, portrait | [@tdater](https://github.com/tdater) | GitHub releases, closed source (all rights reserved) | untested on head units |
| [KITT Launcher.2](https://github.com/tdater/KITT-Launcher.2-releases) | launcher, landscape | [@tdater](https://github.com/tdater) | GitHub releases, closed source (all rights reserved) | untested on head units |
| [LocalSend](https://localsend.org) | module (recommended) | [@localsend](https://github.com/localsend) | [Google Play](https://play.google.com/store/apps/details?id=org.localsend.localsend_app), [F-Droid](https://f-droid.org/packages/org.localsend.localsend_app), [GitHub releases](https://github.com/localsend/localsend/releases/latest), Apache-2.0 | **works on one real unit** (2012–2014 Camry, FYT, Android 8.1: received and installed an APK). Links only: the release has one APK per processor type, so no install is offered |
| CleanYourHeadUnit Home | launcher | [@maxhayim](https://github.com/maxhayim) | built into the app | untested on head units |

**Recommended apps:** RetroDeck, Webmote and LocalSend, in that order. The list is `recommended` in `data/catalog.json`, each with a one-line reason; `catalog validate` checks that every entry exists and has a reason.

KITT Launcher and KITT Launcher.2 are proprietary. CleanYourHeadUnit links to and downloads from @tdater's official release repositories only, and is not affiliated with or endorsed by @tdater.

## How install works

`cyhu install <id>`, or **Download and install** in the app:

1. Asks GitHub for the latest release of the listed repository. The repository owner must be the listed developer.
2. Picks the asset that matches the listing's file pattern.
3. Downloads it and checks the SHA-256 against the `digest` GitHub publishes for that asset.
4. Reads the APK's package name and signing certificate and compares them with the listing. A different package name or certificate stops the install. (`cyhu install --allow-unpinned` exists for entries without a pinned certificate. None are listed today.)
5. Hands it to Android's installer, which asks you. On Android 8+, Android first asks you to allow CleanYourHeadUnit to install apps.

The app also shows, for anything installed, whether its signature matches the developer's certificate.

## Module cards

The **Modules** page also lists installed apps that declare a module card:

- `io.github.maxhayim.cleanyourheadunit.category.MODULE_CARD`, CleanYourHeadUnit's own category, or
- `com.gathin.tvhome.category.DOCK_CARD`, CleanYourTV's existing card category, so apps that already support CleanYourTV show up too.

An app declares one with an activity like:

```xml
<activity android:name=".CardActivity" android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="io.github.maxhayim.cleanyourheadunit.category.MODULE_CARD" />
    </intent-filter>
</activity>
```

Today the card opens the activity. Embedding live cards on CleanYourHeadUnit Home is planned (see [docs/specs/module-card.md](docs/specs/module-card.md)).

## Adding a module or launcher

Open a **Module submission** or **Launcher submission** issue, or a pull request adding an entry to `data/catalog.json`. Then run:

```
python3 tools/cyhu.py catalog validate
python3 tools/cyhu.py catalog validate --submission my-entry.json
```

Requirements:

- the developer's GitHub username, which must be real and verifiable
- the official source (a GitHub repository with releases)
- the package name and signing-certificate SHA-256
- the licence
- orientations and minimum Android
- honest compatibility: *untested* until someone reports a unit

Closed-source apps are fine. Mirrored or repackaged APKs are not.

Listing is not endorsement or certification, and never implies a partnership with the developer.
