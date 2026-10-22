# Themes

A theme is a folder of files on the TV, pushed with `tools/theme.sh` (`tools/theme.bat` on Windows,
`-Theme` in PowerShell). It changes the look and behaviour of the launcher without rebuilding it. The
TV loads a theme from the launcher's external files directory and applies it on the next Home launch:

```sh
./tools/theme.sh themes/venice
```

A theme is a folder with any of these files (everything is optional):

- `config.json` — the settings below
- `wallpaper.jpg` — one full-screen picture behind the home screen, over the solid background
- `wallpapers/` — a collection: pictures shown in rotation
- `captions.txt` — a name for each rotating picture, shown in the top bar

To undo a theme: `./tools/theme.sh themes/none` removes the folder.

## Keys (config.json)

The launcher reads the settings below from `config.json`. Every key is optional: an unset key keeps the
built-in value from `res/values/config.xml`, and an unknown or badly typed key is ignored. A theme can
also set every key that config.xml sets, and a few more (the look keys below and `wallpaper`).

### Dock and grid

| key | type | default | meaning |
|-----|------|---------|---------|
| `dock_apps` | array of package names | Netflix, Prime Video, YouTube, Hotstar | Apps pinned to the dock row at the bottom, in this order. Missing apps are skipped. |
| `fill_dock` | bool | true | Grid apps fill the dock slots `dock_apps` leaves free; false leaves them empty. |
| `columns` | int 4-7 | 4 | Tiles per row in the grid; the dock always has this many slots. |
| `rows_below_dock` | int 0-3 | 0 | Grid rows left visible under the dock on the home screen. |
| `dock_title` | bool | true | The focused dock app's name in large type above the dock. |
| `dock_row` | bool | true | The dock as its own row: a lighter band, no names under the tiles. false: one plain grid. |
| `dock_glass` | bool | false | The dock band as light frosted glass, like the pills, so a sky or wallpaper shows through. |
| `dock_opacity` | int 30-100 | 100 | The dock band's opacity in percent; below 100 the wallpaper shows through. |
| `always_show_names` | bool | false | Names under every tile, not only the focused one. |
| `show_home_settings_shortcut` | bool | false | An optional Default-home tile. |
| `library_button` | bool | false | The app library as a round button in the top bar (needs `app_library`). |
| `app_library` | bool | false | The App library: an "Added" collection of apps you choose yourself. |
| `library_apps` | array of package names | empty | Packages pre-seeded into the App library's "added" set at startup (off by default). They show on Home as if added in the App library UI, and stay removable there. |

### Home screen

| key | type | default | meaning |
|-----|------|---------|---------|
| `background_color` | string (#RRGGBB) | #000000 | The colour behind everything, when no wallpaper is shown. |
| `wallpaper` | bool | true | Whether the theme's `wallpaper.jpg` (and a `wallpapers/` collection) is shown. |
| `wallpaper_rotation_minutes` | int | 0 (off) | Rotate through the theme's `wallpapers/` folder this often. |
| `wallpaper_order` | string | shuffle | Playback order: `sequential` or `shuffle`. |
| `wallpaper_dim` | int 0-90 | 0 | Darken the wallpaper by this percent, for readable names and buttons. |
| `wallpaper_captions` | bool | false | Show each rotating picture's name (from `captions.txt`) in the top bar. |
| `wallpaper_true_color` | bool | false | Decode wallpapers in 32-bit colour instead of RGB_565 (for TVs with 2 GB or more). |
| `sky_intensity` | int 0-100 | 0 | Time-of-day backdrop behind everything: a dark gradient that follows the clock. |
| `latitude` / `longitude` | string | — | Where the TV is, in decimal degrees, so the sky's glows follow the real sun. |
| `cinematic_home` | bool | false | Cinematic top shelf: the focused dock app's artwork, softened, fills the top of the screen. |
| `show_stats` | bool | false | One line under the clock: CPU, free memory and free storage. |
| `home_idle_clock_minutes` | int | 0 (off) | Minutes Home may sit untouched before it shows the clock screensaver. |
| `greeting_name` | string | — | A name after the greeting: "GOOD MORNING, MR. TRỊNH". |
| `festival` | string | — | A festive skin: `lunar-new-year`, `eid`, `diwali` or `holi`. |
| `festival_until` | string | — | End date of the skin, `YYYY-MM-DD`. |
| `greeting` | string | — | A custom greeting line for the skin ("FESTIVE GREETINGS"). |
| `accent_color` | string (#RRGGBB) | — | Accent colour for the skin. |
| `race_calendar` | array of races | — | F1 race weekends: `round`, `name`, `place`, and `sessions` with `name` and `start` (ISO 8601, UTC). |

### Top bar

| key | type | default | meaning |
|-----|------|---------|---------|
| `show_network_pill` | bool | true | The Wi-Fi/Ethernet pill in the top bar. |
| `show_inputs_pill` | bool | true | The Inputs pill in the top bar. |
| `icon_only_buttons` | bool | false | Wi-Fi and Settings as round icon-only buttons; Inputs keeps its label. |
| `wifi_label_width` | int | 0 | With icon-only buttons, keep the network name on the Wi-Fi pill up to this width in dp. |
| `top_menu` | bool | false | Inputs, Wi-Fi and Settings folded into one round gear with a dropdown, like Google TV. |
| `launcher_settings_button` | bool | false | A direct Launcher settings action instead of TV Settings in the header. |
| `reorder_button` | bool | false | A reorder button in the top bar. |

### Inputs

| key | type | default | meaning |
|-----|------|---------|---------|
| `hidden_apps` | array of package names | Media Center, Gallery | Apps never shown as tiles. |
| `live_tv_package` | string | com.mediatek.wwtv.tvcenter | The TV's Live TV / tuner app, offered at the end of the Inputs menu. |
| `live_tv_action` | string | — | An action for a Live TV app with no leanback entry point. |
| `live_tv_input_id` | string | — | An exact system tuner ID for the Inputs menu's Live TV row. |
| `input_action` | string | — | An action sent with an input's passthrough URI where ACTION_VIEW is not answered. |
| `input_uri_scheme` | string | — | A scheme to send the input's passthrough URI with instead of `content://`. |
| `input_sources` | array of strings | — | Vendor source numbers, "HW<n>=<number>", by the framework input id's last part. |
| `coocaa_sources` | bool | false | List Coocaa OS sources from com.skyworth.tv in the Inputs menu. |
| `source_panel_service` | string | — | A service to start when no inputs could be listed (component, then action). |
| `source_panel_action` | string | — | The action for the source panel above. |
| `source_panel_extra` | string | — | One int extra sent with the source panel intent, "name=value". |
| `handle_view_inputs` | bool | false | Answer the system input switcher action with the Inputs menu. |
| `boot_to_live_tv` | bool | false | Open the Live TV app the first time Home comes up after the TV is switched on. |

### Remote

| key | type | default | meaning |
|-----|------|---------|---------|
| `settings_key_keycodes` | array of ints | — | Remote buttons that open Settings, by Android keycode. |
| `settings_key_scancodes` | array of ints | — | Remote buttons that open Settings, by Linux scancode. |
| `settings_key_panel` | bool | false | What those Settings buttons open: a small glass panel over whatever is on screen. |
| `input_key_keycodes` | array of ints | — | More remote buttons that open the input switcher, by Android keycode. |
| `app_keys` | array of strings | — | Remote buttons that open an app, "keycode:package" or "KEYCODE_NAME:package". |
| `key_filter` | bool | true | Whether the key services request key filtering. false restores key auto-repeat on some firmware. |
| `stock_home_redirect` | string | — | Package of a stock launcher whose home screen is sent back here if the firmware revives it. |
| `restore_key_services` | bool | false | Home puts the remote's accessibility helpers back after a force stop. |

### Look and motion

| key | type | default | meaning |
|-----|------|---------|---------|
| `animations` | bool | true | Basic motion is on. |
| `advanced_effects` | bool | false | Advanced effects are opt-in in the launcher's own Settings screen. |
| `reduced_motion` | bool | false | Calmer motion for weak GPUs: shorter fades, no overshoot. |
| `focus_overshoot` | bool | true | The focus highlight and the focused tile swing past their target before settling. |
| `ambient_glow` | bool | true | The glow behind the dock in the focused app's colour. |
| `app_glow` | bool | false | The focus highlight glows in the focused app's colour on every tile. |
| `glow_style` | string | radial | Shape of the glow: `radial` or `horizon`. |
| `tile_menu` | bool | false | Hold OK on a tile opens a menu (Move, Remove from Home, App info) instead of App info. |
| `module_cards` | bool | true | An app that declares a card can stand in for its tile once you answer Show. |
| `shamsi_date` | bool | false | Show the Solar Hijri date used in Iran and Afghanistan above the Gregorian date. |
| `launch_actions` | array of strings | — | Tiles that open a screen of the app instead of its start screen: "package=action". |
| `app_labels` | array of strings | — | A tile's name instead of the app's own: "package=Name". |
| `app_banners` | array of strings | — | A tile's artwork instead of the app's banner: "package=drawable". |
