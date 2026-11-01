# Security Policy – CleanYourHeadUnit

CleanYourHeadUnit changes package states and network settings on a device in a car, and it can download and hand APKs to Android's installer. Security problems here can affect a vehicle's head unit, so please report them responsibly.

---

## Reporting a vulnerability

- Do **not** open a public issue.
- Use GitHub's **Private vulnerability reporting** on this repository, or email the maintainer.
- Include what's affected (app, `cyhu`, catalog data), the version or commit, steps to reproduce, and any logs. Remove serial numbers, addresses and account names from logs.

Please allow reasonable time for a fix before public disclosure.

## In scope

- anything that could disable a **protected** package, or an **Unknown** one without `--allow-unknown`
- install verification bypasses: wrong repository owner, checksum, package name or signing certificate accepted
- the local firewall leaking or recording more than documented, starting without consent, or failing open silently
- theme or catalog data that could cause code execution or file writes outside the app's storage
- diagnostics exports that leak identifiers the sanitiser should remove

## Out of scope

- vulnerabilities in head-unit firmware, other apps or catalog projects. Report those to their vendors or developers. A head-unit malware finding with a public source is welcome as a **telemetry catalogue** contribution.
- the fact that Android lets a user with ADB disable packages. That's the intended mechanism.

## Design notes

- No root, no exploits, no hidden APIs used to change anything.
- `WRITE_SECURE_SETTINGS` is only ever granted by you, over ADB, and only lets the app set Private DNS.
- The local firewall never intercepts TLS, installs no certificate, stores only counters and the last 30 blocked domain names, and never starts at boot.
- APK installs are pinned to the developer's GitHub release, GitHub's asset digest, the listed package name and the signing certificate, and Android's installer always asks you.
