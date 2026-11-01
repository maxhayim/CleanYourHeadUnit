# Catalog spec (`data/catalog.json`)

```json
{
  "schema": 1,
  "developers": [
    {"id": "tdater", "github": "tdater", "name": null, "projects": ["KITT Launcher"]}
  ],
  "items": [ { ...item... } ]
}
```

## Developer

| Key | Rules |
|---|---|
| `id` | lowercase id used by items |
| `github` | the verified GitHub username, or `null` if none could be verified. **Never guessed.** |
| `name` | only when `github` is `null` (or the developer asks to be named) |
| `website` | optional official page (https), for developers without a verified GitHub username or with a main page elsewhere |
| `projects`, `note` | display only |

The credit shown is `@github` when there is one, otherwise `name`.

## Item

| Key | Rules |
|---|---|
| `id` | unique, lowercase |
| `kind` | `launcher`, `module` or `theme` |
| `name` | 1–40 characters |
| `description` | required |
| `developer` | a developer `id` |
| `featured` | optional boolean |
| `license` | required; proprietary is allowed, and is said plainly |
| `open_source` | boolean |
| `package` | Android package name (not needed for themes) |
| `signing_cert_sha256` | 64 lowercase hex characters. Required for `github_release` installs; install refuses a different certificate |
| `android` | `{"min_sdk": 24, "min_version": "7.0"}` |
| `orientations` | `["landscape"]`, `["portrait"]` or both |
| `screens` | the sizes the developer names, e.g. `["1024x600", "1280x720"]` |
| `source` | `{"type": "github_release", "repo": "owner/name", "asset_pattern": "^App-[0-9.]+\\.apk$"}`, `{"type": "builtin"}`, or `null`. For `github_release`, `owner` must equal the developer's `github`. |
| `install` | `github_release`, `store_listing`, `builtin` or `none`. A `planned` entry can't offer one |
| `links` | official pages only (`repository`, `releases`, `website`), `https://` with no credentials |
| `compatibility` | `{"status": "untested" \| "community" \| "verified" \| "planned", "verified": [], "reports": []}`. `verified` needs at least one named unit |
| `status` | optional: `planned` |
| `automotive_status` | optional text, e.g. "Automotive support planned" |
| `warnings` | optional list of plain sentences |
| `target` | themes only: `{"launcher": "<launcher id in the catalog>"}` |
| `screenshots` | required for new submissions: real screenshots or clearly labelled mockups |

No item may carry commands or scripts (`command`, `script`, `shell`, `adb`… keys are rejected), and no string may look like a shell command: listings describe, they never instruct.

`python3 tools/cyhu.py catalog validate` checks all of this, and `--submission entry.json` checks one new entry (with a `developers` list if the developer is new).
