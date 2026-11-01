# Theme spec (schema 1)

A theme is `themes/<id>/theme.json`, plus an optional `preview.png` and wallpaper image. Themes are data only: a theme folder may contain only `.json`, `.png`, `.jpg`/`.jpeg`, `.webp`, `.md` and `.txt` files, and no string may look like a shell command.

| Key | Type | Required | Rules |
|---|---|---|---|
| `schema` | int | yes | `1` |
| `id` | string | yes | `^[a-z0-9][a-z0-9-]{1,40}$` |
| `name` | string | yes | display name |
| `author` | object | yes | `{"github": "<username>"}` |
| `license` | string | yes | e.g. `MIT`, `CC-BY-4.0` |
| `target` | object | yes | `{"launcher": "cyhu-home", "min_version": "0.1.0"}`. The launcher the theme is for |
| `dark` | bool | no | default `true` |
| `colors` | object | yes | exactly `background`, `surface`, `text`, `text_dim`, `accent`, `warn`, `ok`, each `#RRGGBB` |
| `layout.dock` | string | no | `bottom` (default) or `left` |
| `layout.columns_landscape` | int | no | 2–10 (default 6) |
| `layout.columns_portrait` | int | no | 2–10 (default 4) |
| `layout.icon_size` | string | no | `small`, `medium`, `large` |
| `layout.clock` | bool | no | default `true` |
| `wallpaper` | string | no | a file in the theme folder, at most 2 MB. Reserved: the app doesn't display wallpapers yet |
| `description` | string | no | one sentence |

No other top-level keys are accepted. The app rejects imports over 64 KB, and only imports themes whose `target.launcher` is `cyhu-home`.

Validated identically by `tools/cyhu/catalog.py` (`check_theme`, `check_theme_folder`) and `Theme.validate` in the app.
