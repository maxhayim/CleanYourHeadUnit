"""The Settings button on a Sony BRAVIA VU3 opens Android TV Settings instead of Sony's ribbon (#2131).

The report (Google TV 14, `BRAVIA_VU3`, launcher 3.0 as Home) is a key-routing one: with
`HomeKeyService` bound the press opens `com.android.tv.settings/.MainSettings`, and with the helper off
the press arrives and nothing opens - `WindowManager` logs
`mShortPressOnSettingsBehavior=SHORT_PRESS_SETTINGS_NOTHING` - while Sony's own ribbon still opens when
its activity is started by hand. An owner who reads the keycode bullet above this one goes looking for
a scancode to add, so the page says why that cannot work here: everything the launcher does with a
settings key ends in `Settings.ACTION_SETTINGS`, and `settings_fallback_components` is only consulted
when that action resolves to nothing.

Documentation only. These tests pin the words a reader acts on, and the three things in the launcher
that make the answer true, so the page cannot claim it after the code changes:
`HomeKeyService` taking `KEYCODE_SETTINGS`, the two targets its settings branch opens, and
`SystemSettings` preferring the standard action over the vendor list.
"""
import re
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
TROUBLE = ROOT / "docs" / "TROUBLESHOOTING.md"
HOME_KEYS = ROOT / "launcher" / "app" / "src" / "main" / "java" / "com" / "gathin" / "tvhome" / "HomeKeyService.java"
SYSTEM_SETTINGS = ROOT / "launcher" / "app" / "src" / "main" / "java" / "com" / "gathin" / "tvhome" / "SystemSettings.java"
KEYS_PAGE = ROOT / "themes" / "README.md"
CONFIG_XML = ROOT / "launcher" / "app" / "src" / "main" / "res" / "values" / "config.xml"

SECTION = "## Accessibility helper enabled but the remote button does nothing"
START = "- **The Settings button opens Android TV Settings, not the TV's own menu**"
LINK = re.compile(r"\]\(([^)\s]+)\)")


def bullet(text, start):
    """The markdown bullet that begins with `start`, plus the continuation lines under it."""
    lines = text.split("\n")
    first = next((i for i, line in enumerate(lines) if line.startswith(start)), None)
    if first is None:   # a reworded bullet is easier to find from this than from a StopIteration
        raise AssertionError("no bullet in the page starts with %r" % start)
    out = [lines[first]]
    for line in lines[first + 1:]:
        if line.startswith("- **") or line.startswith("## "):
            break
        out.append(line)
    return "\n".join(out)


class SonySettingsKeyNoteTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.trouble_text = TROUBLE.read_text(encoding="utf-8")
        cls.note = bullet(cls.trouble_text, START)
        cls.home_keys = HOME_KEYS.read_text(encoding="utf-8")
        cls.system_settings = SYSTEM_SETTINGS.read_text(encoding="utf-8")

    def test_bullet_exists_once(self):
        self.assertEqual(self.trouble_text.count(START), 1,
                         "the note must live in one bullet, so the two helper cases cannot drift apart")

    def test_bullet_sits_in_the_remote_button_section(self):
        # The reader arrives here from the helper table, not from a device note: a bullet above the
        # section would be read as advice about a bound helper.
        self.assertIn(SECTION, self.trouble_text)
        self.assertGreater(self.trouble_text.index(START), self.trouble_text.index(SECTION))

    def test_bullet_quotes_what_the_tv_logs_with_the_helper_off(self):
        # Without the log line a reader cannot tell this apart from "the helper is not bound", which
        # the two bullets around it answer.
        self.assertIn("mShortPressOnSettingsBehavior=SHORT_PRESS_SETTINGS_NOTHING", self.note)
        self.assertIn("WindowManager", self.note)

    def test_bullet_names_the_ribbon_activity_and_how_to_open_it(self):
        self.assertIn("com.sony.dtv.smartui/.settings.SmartSettingsActivity", self.note)
        self.assertIn("am start -n com.sony.dtv.smartui/.settings.SmartSettingsActivity", self.note)
        self.assertIn("tools/tv.py", self.note)

    def test_bullet_names_the_settings_the_button_opens_instead(self):
        self.assertIn("com.android.tv.settings/.MainSettings", self.note)
        self.assertIn("settings_key_panel", self.note)
        for key in ("settings_key_keycodes", "settings_key_scancodes"):
            self.assertIn(key, self.note)

    def test_bullet_says_a_setting_cannot_put_the_ribbon_back(self):
        # The answer to "which scancode do I add": none, and the page has to say so or the next owner
        # spends the session on getevent.
        self.assertIn("cannot be put back on the", self.note)
        self.assertIn("Settings.ACTION_SETTINGS", self.note)
        self.assertIn("settings_fallback_components", self.note)

    def test_bullet_names_the_model_and_links_the_report(self):
        self.assertIn("BRAVIA_VU3", self.note)
        self.assertIn("[#2131](https://github.com/Gathin23/cleanyourtv/issues/2131)", self.note)

    def test_bullet_links_resolve(self):
        self.assertEqual([target for target in LINK.findall(self.note)
                          if not target.startswith(("http://", "https://", "#"))
                          and not (TROUBLE.parent / target.split("#", 1)[0]).exists()], [],
                         "the bullet must not link a page that does not exist")

    def test_helper_still_takes_keycode_settings_before_any_app(self):
        # The note's first claim, in the launcher: isSettingsKey() answers KEYCODE_SETTINGS itself,
        # whatever settings_key_keycodes adds.
        self.assertIn("if (event.getKeyCode() == KeyEvent.KEYCODE_SETTINGS) return true;", self.home_keys)
        is_settings = self.home_keys.index("private boolean isSettingsKey(KeyEvent event)")
        on_key = self.home_keys.index("protected boolean onKeyEvent(KeyEvent event)")
        self.assertLess(is_settings, self.home_keys.index("if (isSettingsKey(event)) {", on_key))

    def test_the_settings_branch_opens_the_settings_action_or_the_panel(self):
        # What the press can become: Settings.ACTION_SETTINGS through SystemSettings, or the launcher's
        # own panel. An arbitrary vendor activity is not one of the choices here.
        self.assertIn("settingsPanel ? new Intent(this, QuickSettingsActivity.class)", self.home_keys)
        self.assertIn("SystemSettings.intent(this, Settings.ACTION_SETTINGS)", self.home_keys)

    def test_the_vendor_list_is_only_read_when_the_action_resolves_to_nothing(self):
        # The note's second claim: putting com.sony.dtv.smartui in settings_fallback_components would
        # not reach it while Settings.ACTION_SETTINGS resolves on the set.
        resolved = self.system_settings.index("if (resolved != null && !resolved.activityInfo.name.contains(\"CTSDummy\")) return standard;")
        vendor = self.system_settings.index("Intent vendor = vendorIntent(context);")
        self.assertLess(resolved, vendor)

    def test_the_named_switch_still_exists_and_is_config_only(self):
        # settings_key_panel is the one thing the note tells an owner to try; it is a bool in
        # config.xml and a row in the Keys table a theme reader looks at.
        self.assertIn('<bool name="settings_key_panel">', CONFIG_XML.read_text(encoding="utf-8"))
        self.assertIn("| `settings_key_panel` |", KEYS_PAGE.read_text(encoding="utf-8"))

    def test_bullet_extraction_stops_at_the_next_bullet(self):
        sample = "\n".join([START + " one", "  two", "- **Next** three"])
        self.assertEqual(bullet(sample, START), START + " one\n  two")

    def test_a_reworded_bullet_is_reported_by_its_text(self):
        # The helper the other tests rely on: when the bullet is gone, say which text went missing.
        with self.assertRaises(AssertionError) as raised:
            bullet("## A page\n- **Something else** one\n", START)
        self.assertIn("no bullet in the page starts with", str(raised.exception))

    def test_read_guard(self):
        # A walk that read the wrong page, or a truncated file, must not pass by finding nothing.
        self.assertGreater(len(self.trouble_text.splitlines()), 1000)
        self.assertGreater(len(self.home_keys.splitlines()), 100)


if __name__ == "__main__":
    unittest.main()
