"""Pins the Android TV 10 data point for the key-helper/auto-repeat trade (#2446).

docs/devices/tcl-55p71b-g09-key-repeat.md carries one section per set, and the #944 section has to say
the empty-list control was not run there, so "the ramp returns with no service bound" rested on the G09
alone. #2446 is a Xiaomi Mi TV 4A (MiTV-MOOQ1, Android TV 10) where the owner ran that control both ways,
and where the helper is needed for a reason the note's G09 table does not have: the firmware addresses
VIEW_INPUTS to launcherx's own Inputs panel by name, so the launcher answers it with handle_view_inputs
rather than with a vendor keycode. These tests keep the section to what the report measured, hold its
config advice to switches the launcher actually reads, and keep the troubleshooting entry and the note's
own cross-reference in step.
"""
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
G09 = ROOT / "docs" / "devices" / "tcl-55p71b-g09-key-repeat.md"
MOOQ3 = ROOT / "examples" / "redmi-mitv-mooq3" / "debloat.txt"
TROUBLE = ROOT / "docs" / "TROUBLESHOOTING.md"
THEMES = ROOT / "themes" / "README.md"
CONFIG_XML = ROOT / "launcher" / "app" / "src" / "main" / "res" / "values" / "config.xml"
JAVA = ROOT / "launcher" / "app" / "src" / "main" / "java"

HEADING = "## A Xiaomi Mi TV 4A on Android TV 10, with the empty-list control run"
BUILD = "QTG3.200305.006.6148"
INPUTS_PANEL = "com.google.android.tvlauncher/.inputs.InputsPanelActivity"
TROUBLE_HEADING = "## A key helper is bound and a held remote button stopped repeating"


def flat(path):
    return re.sub(r"\s+", " ", path.read_text(encoding="utf-8"))


def section_of(text, heading):
    """The flattened body under `heading`, up to the next `##` heading; None when it is not there."""
    flat_text = re.sub(r"\s+", " ", text)
    if heading not in flat_text:
        return None
    rest = flat_text.split(heading, 1)[1]
    following = re.search(r"\s##\s", rest)
    return rest if following is None else rest[:following.start()]


def links(path):
    text = path.read_text(encoding="utf-8")
    return [str((path.parent / target).resolve()) for target in re.findall(r"\]\(([^)\s]+)\)", text)
            if not target.startswith(("http", "#"))]


class SectionTest(unittest.TestCase):
    def setUp(self):
        body = section_of(G09.read_text(encoding="utf-8"), HEADING)
        self.assertIsNotNone(body, f"{HEADING} is gone from the G09 note")
        self.section = body or ""

    def test_says_which_set_and_which_build(self):
        for needle in ("#2446", "MiTV-MOOQ1", "`tarzan`", BUILD, "Android TV 10", "SDK 29", "MediaTek"):
            with self.subTest(needle=needle):
                self.assertIn(needle, self.section)

    def test_keeps_all_three_rows_of_the_control(self):
        for needle in ("`com.gathin.tvhome/.InputKeyService` | one step per press",
                       "`''`, with `accessibility_enabled 0` | ramps continuously",
                       "`InputKeyService` written back | one step per press again"):
            with self.subTest(needle=needle):
                self.assertIn(needle, self.section)

    def test_reports_the_control_as_run_and_says_what_it_costs(self):
        self.assertIn("Clearing the list kills both buttons here too", self.section)
        self.assertIn("was", self.section)
        self.assertNotIn("empty-list control was not run", self.section,
                         "this report ran the control; only the sections that did not may say otherwise")

    def test_names_what_was_not_tried(self):
        self.assertIn("`consume_volume_keys` was not tried", self.section)
        self.assertIn("`HomeKeyService` alone was not tried", self.section)

    def test_the_input_button_route_matches_the_launcher(self):
        for needle in ("`KEYCODE_TV_INPUT`", INPUTS_PANEL, "ActivityNotFound", "handle_view_inputs",
                       "INPUT_KEY_SERVICE=1", "VIEW_INPUTS"):
            with self.subTest(needle=needle):
                self.assertIn(needle, self.section)
        self.assertIn("handle_view_inputs", flat(THEMES),
                      "the Keys table no longer documents the switch this section tells the owner to set")
        self.assertIn("handle_view_inputs", CONFIG_XML.read_text(encoding="utf-8"),
                      "the bool the section names is not in config.xml")
        if JAVA.is_dir():
            java = "\n".join(p.read_text(encoding="utf-8") for p in JAVA.rglob("*.java"))
            self.assertIn("handle_view_inputs", java, "the launcher no longer reads that switch")
            self.assertIn("KEYCODE_TV_INPUT", java,
                          "the keycode the section names is not one the helper still answers")

    def test_the_patchwall_route_is_the_one_the_mooq3_list_left_open(self):
        for needle in ("`KEY_WWW`", "`MSC_SCAN 000c0196`", "`com.mitv.tvhome.atv`",
                       "`scan:150:com.spotify.tv.android`"):
            with self.subTest(needle=needle):
                self.assertIn(needle, self.section)
        self.assertIn("has not been tried on the real remote yet", flat(MOOQ3),
                      "the mooq3 list is what this section answers; it no longer leaves the remap open")
        self.assertIn("records as not yet tried", self.section)

    def test_no_fix_is_promised(self):
        self.assertNotIn("solves", self.section.lower())
        if JAVA.is_dir():
            java = "\n".join(p.read_text(encoding="utf-8") for p in JAVA.rglob("*.java"))
            self.assertNotIn("adjustSuggestedStreamVolume", java,
                             "the launcher now synthesises the repeat: this section's wording has to change")

    def test_the_note_no_longer_credits_the_control_to_the_g09_alone(self):
        text = flat(G09)
        self.assertNotIn("still measured on the G09 alone", text)
        self.assertIn("was measured on the G09 alone until the Mi TV 4A below ran that", text,
                      "the #944 section has to point forward to this one")


class EntryPointsTest(unittest.TestCase):
    def test_troubleshooting_carries_the_set(self):
        text = flat(TROUBLE)
        self.assertEqual(text.count(TROUBLE_HEADING), 1)
        body = text[text.index(TROUBLE_HEADING):]
        body = body[:body.find(" ## ")] if " ## " in body else body
        for needle in ("MiTV-MOOQ1", BUILD, "#2446", INPUTS_PANEL, "empty list was run"):
            with self.subTest(needle=needle):
                self.assertIn(needle, body)

    def test_links_resolve(self):
        for target in links(G09):
            with self.subTest(target=target):
                self.assertTrue(Path(target).exists(), "relative link target does not exist")


if __name__ == "__main__":
    unittest.main()
