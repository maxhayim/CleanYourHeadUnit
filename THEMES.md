# Themes

A theme is a JSON file of colours and layout choices. It never contains code. The app and `cyhu` validate every theme with the same rules, and reject unknown keys, bad colours, bad layout values and files over 64 KB.

There's no universal theme format across Android launchers, so every theme names the launcher it's for (`target`). Today, themes target **CleanYourHeadUnit**: the app itself and CleanYourHeadUnit Home. A theme for a third-party launcher would need that launcher's own format and should be listed as that launcher's theme.

## Built in

| Theme | Look | Preview |
|---|---|---|
| Midnight | dark, soft blue accent (default) | [themes/midnight/preview.png](themes/midnight/preview.png) |
| Amber Dash | dark, amber instrument-cluster colours | [themes/amber-dash/preview.png](themes/amber-dash/preview.png) |
| Daylight | light, for bright cabins | [themes/daylight/preview.png](themes/daylight/preview.png) |

Previews are renders by the test suite (simulated unit), not photos.

**Accent colour.** Under Themes (and in Settings) you can pick an accent colour that replaces the theme's own: one of ten presets or any `#RRGGBB`. It applies to every theme, and "Theme's own colour" goes back. Button text switches between black and white to stay readable on the colour you pick. The choice stays on the unit; nothing about it is sent anywhere.

## Format (schema 1)

```json
{
  "schema": 1,
  "id": "my-theme",
  "name": "My Theme",
  "author": {"github": "your-github-username"},
  "license": "MIT",
  "target": {"launcher": "cyhu-home", "min_version": "0.1.0"},
  "dark": true,
  "colors": {"background": "#0B0E11", "surface": "#161B21", "text": "#E8EDF2", "text_dim": "#8A96A3",
             "accent": "#8FA8FF", "warn": "#F2A93B", "ok": "#4CC38A"},
  "layout": {"dock": "bottom", "columns_landscape": 6, "columns_portrait": 4, "icon_size": "large", "clock": true},
  "description": "One sentence."
}
```

| Key | Rules |
|---|---|
| allowed keys | `schema`, `id`, `name`, `author`, `license`, `target`, `dark`, `colors`, `layout`, `wallpaper`, `description`. Anything else is rejected |
| `id` | lowercase letters, digits and `-` |
| `target` | `launcher` and `min_version` are required. The app only imports `cyhu-home` themes |
| `colors` | exactly the seven keys above, each `#RRGGBB` |
| `layout.dock` | `bottom` or `left` (left becomes bottom in portrait) |
| `layout.columns_landscape`, `layout.columns_portrait` | whole numbers from 2 to 10 |
| `layout.icon_size` | `small`, `medium` or `large` |
| `layout.clock` | `true` or `false` |
| `wallpaper` | an image file next to `theme.json` in the repository (at most 2 MB). The app doesn't display wallpapers yet and drops the key on import |
| file size | at most 64 KB when imported in the app |
| theme folders | only `json`, `png`, `jpg`, `webp`, `md` and `txt` files, so no code |

The full spec is in [docs/specs/theme.md](docs/specs/theme.md).

## Use one

In the app: **Themes → Import theme file**, or pick a built-in one. **Export current** saves the active theme as JSON to share.

Check the themes in the repository (yours included) on a computer:

```
python3 tools/cyhu.py catalog validate
```

## Submit one

Use the **Theme submission** issue template, or open a pull request adding `themes/<id>/theme.json` and a `preview.png`. If the preview is a render or mockup rather than a photo of a unit, say so.
