"""A power press the launcher cannot take is described where its owner looks.

A remote's power button that does nothing while a long press opens Android's shutdown dialog was reported
on a TCL 43BP615 (BeyondTV, Android 11, SDK 30, #1608) with the repository's stock install, and again with
`HomeKeyService` bound through `settings put secure enabled_accessibility_services`, so the key helper is
not what changes it. The maintainer's question in that issue was exactly whether the helper was catching
the key; the owner's answer was that it never sees it, and the launcher itself maps no power key. Nothing
on the page said so: the only power-key sections were the standby opt-in (a *different* symptom: the key
does nothing while the TV is off) and one XGIMI bullet about that set's F11, so an owner with this symptom
read a page that sent them through three `settings` writes which could not help.

The note is short because the answer is short - there is nothing to run. What it must not do is go stale,
so the facts are read out of the sources rather than copied in: the launcher's own `KEYCODE_POWER`
mentions (the note rests on there being none that consume the key), and `tools/restore.sh`'s argument
order (the note hands the owner to the undo as its escape). A launcher that starts handling the power key,
or a restore.sh that swaps its two arguments, fails this file instead of leaving the page quietly wrong.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DOC = ROOT / "docs" / "TROUBLESHOOTING.md"
JAVA = ROOT / "launcher" / "app" / "src" / "main" / "java" / "com" / "gathin" / "tvhome"
RESTORE = ROOT / "tools" / "restore.sh"

ISSUE = "#1608"
MODEL = "43BP615"
# The helper the report bound, and the write that binds it: the note turns on this being recorded.
HELPER = "HomeKeyService"
BIND = "enabled_accessibility_services"

# A level-2 heading, the shape this page's troubleshooting entries take.
HEADING = re.compile(r"^##\s+(.+?)\s*$")
# A fenced block, so a heading written inside a command example is not read as a section.
FENCE = re.compile(r"^```([a-zA-Z0-9]*)[ \t]*$")


def slug(heading):
    """GitHub's anchor for a heading: lower case, no punctuation, spaces as hyphens."""
    text = re.sub(r"[^\w\s-]", "", heading.strip().lstrip("#").strip().lower())
    return re.sub(r"\s+", "-", text)


def sections(page):
    """{anchor: [lines]} for every level-2 section, headings inside a fence not counted."""
    found, current, fenced = {}, None, False
    for line in page.splitlines():
        if FENCE.match(line):
            fenced = not fenced
        elif not fenced:
            match = HEADING.match(line)
            if match:
                current = slug(match.group(1))
                found[current] = []
                continue
        if current is not None:
            found[current].append(line)
    return found


def heading_of(page, anchor):
    """The heading text whose slug is `anchor`, or '' when no heading answers to it."""
    for line in page.splitlines():
        match = HEADING.match(line)
        if match and slug(match.group(1)) == anchor:
            return match.group(1)
    return ""


def section_about(page, needle):
    """(anchor, body) of the level-2 section whose text carries `needle`, or ("", "") for none.

    Located by the issue number rather than by its heading, so the wording stays free.
    """
    for anchor, lines in sections(page).items():
        if any(needle in line for line in lines):
            return anchor, "\n".join(lines)
    return "", ""


def link_targets(text):
    """Every in-page anchor a piece of markdown links to, in file order."""
    return re.findall(r"\]\(#([^)\s]+)\)", text)


def flat(text):
    """The text on one line, so a phrase a page wraps across two lines can still be found."""
    return " ".join(text.split())


def power_key_lines():
    """[(file, line number, text)] for every launcher line that names KEYCODE_POWER."""
    out = []
    for path in sorted(JAVA.glob("*.java")):
        for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            if "KEYCODE_POWER" in line:
                out.append((path.name, number, line.strip()))
    return out


class Source(unittest.TestCase):
    """What the launcher and the undo actually say, read rather than copied in."""

    @classmethod
    def setUpClass(cls):
        cls.doc = DOC.read_text(encoding="utf-8")
        cls.restore = RESTORE.read_text(encoding="utf-8")
        cls.power = power_key_lines()
        list_arg = re.search(r"LIST=\"\$\{1:", cls.restore)
        stock_arg = re.search(r"STOCK=\"\$\{2:", cls.restore)
        cls.list_first = bool(list_arg)
        cls.stock_second = bool(stock_arg)

    def test_the_launcher_still_names_the_power_key_at_least_once(self):
        # The note says the launcher maps no power key; if the identifier disappears entirely this
        # file has nothing left to compare against and should be rewritten with the new code.
        self.assertTrue(self.power, "no launcher source mentions KEYCODE_POWER any more")

    def test_the_undo_still_takes_the_list_first_and_the_stock_launcher_second(self):
        self.assertTrue(self.list_first, "tools/restore.sh no longer reads its list from $1")
        self.assertTrue(self.stock_second, "tools/restore.sh no longer reads the stock launcher from $2")


class TheNote(Source):
    """The section a reader with this symptom lands on."""

    @classmethod
    def setUpClass(cls):
        super().setUpClass()
        cls.anchor, cls.body = section_about(cls.doc, ISSUE)

    def test_the_page_describes_this_symptom(self):
        self.assertTrue(self.body, f"no section of docs/TROUBLESHOOTING.md names {ISSUE}")

    def test_the_heading_names_the_symptom_rather_than_a_model(self):
        title = heading_of(self.doc, self.anchor)
        self.assertTrue(title, f"#{self.anchor} has no heading")
        self.assertNotIn(MODEL, title, "the heading names the model instead of the symptom")

    def test_it_names_the_tv_it_was_seen_on(self):
        self.assertIn(MODEL, self.body, "the section does not name the TV")
        self.assertIn("Android 11", self.body, "the section does not give the Android version")
        self.assertIn("BeyondTV", self.body, "the section does not name the firmware family")

    def test_it_records_the_helper_was_bound_when_the_key_was_tried(self):
        # Without this the reader cannot tell the note from a guess: the maintainer's question in the
        # issue was whether the key helper was catching the key.
        self.assertIn(HELPER, self.body, "the section does not name the key helper it ruled out")
        self.assertIn(BIND, self.body, "the section does not say how the helper was bound")

    def test_it_says_no_switch_on_the_page_takes_the_press_back(self):
        body = flat(self.body)
        self.assertIn("app_keys", body, "the section does not rule out app_keys")
        self.assertIn("config.xml", body, "the section does not rule out config.xml")
        self.assertIn("maps no power key", body,
                      "the section does not say the launcher maps no power key")

    def test_it_says_the_install_is_not_broken(self):
        self.assertIn("Nothing is broken", flat(self.body),
                      "the section does not tell an owner their install is intact")

    def test_it_hands_the_owner_to_the_undo(self):
        self.assertIn("tools/restore.sh", self.body,
                      "the section gives no way back to the stock launcher")

    def test_it_is_a_paragraph_a_reader_can_finish(self):
        self.assertGreaterEqual(len(self.body.split()), 80, "the section is a stub")
        self.assertLessEqual(len(self.body.split()), 400, "the section is an essay")


class TheStandbySectionLinksHere(Source):
    """The standby section is the page a power-key reader arrives on, so it has to point across."""

    STANDBY = "The power key does not wake the TV while the key helper is bound"

    def test_the_standby_section_links_this_one(self):
        body = sections(self.doc).get(slug(self.STANDBY), [])
        self.assertTrue(body, "the standby power-key section is gone")
        self.assertIn(TheNote.anchor, link_targets("\n".join(body)),
                      "the standby section does not link the other power-key fault")

    def test_the_link_resolves_to_a_section(self):
        body = sections(self.doc).get(slug(self.STANDBY), [])
        found = sections(self.doc)
        for target in link_targets("\n".join(body)):
            self.assertIn(target, found, f"#{target} is linked and no heading answers to it")


class TheLauncherStillMapsNoPowerKey(Source):
    """The note's central claim, checked against the code rather than the page."""

    def test_every_mention_is_an_exclusion_not_a_mapping(self):
        # `HomeKeyService` names KEYCODE_POWER only to keep it out of the power_on_home handover. A line
        # that compares with == instead would be the launcher taking the key, and the note would be wrong.
        handlers = [entry for entry in self.power if "!=" not in entry[2] and "==" in entry[2]]
        self.assertEqual(handlers, [],
                         "the launcher now handles the power key; read the #1608 note and update it: "
                         + str(handlers))

    def test_the_exclusion_is_where_the_note_says_it_is(self):
        self.assertEqual({entry[0] for entry in self.power}, {HELPER + ".java"},
                         "the KEYCODE_POWER exclusion moved out of " + HELPER + ".java")


class Readers(unittest.TestCase):
    """The readers above, on text written here, so a broken one cannot pass quietly."""

    def test_section_about_finds_a_section_by_its_issue_number(self):
        page = "intro\n## First thing\nbody one (#1)\n## Second thing\nnames #2 here\n"
        anchor, body = section_about(page, "#2")
        self.assertEqual(anchor, "second-thing")
        self.assertIn("names #2 here", body)
        self.assertEqual(section_about(page, "#3"), ("", ""))

    def test_a_heading_inside_a_fence_is_not_a_section(self):
        page = "## Real\n```sh\n## Not a heading\n```\nmore\n"
        self.assertEqual(sorted(sections(page)), ["real"])

    def test_slug_follows_the_github_rule(self):
        self.assertEqual(slug("A power press does nothing, and a long press opens the dialog"),
                         "a-power-press-does-nothing-and-a-long-press-opens-the-dialog")

    def test_link_targets_reads_anchors_only(self):
        self.assertEqual(link_targets("see [it](#a-b) and [there](../x.md) and [one](#c)"), ["a-b", "c"])

    def test_power_key_lines_reads_the_launcher(self):
        self.assertTrue(power_key_lines(), "no launcher line names KEYCODE_POWER")
        for name, number, text in power_key_lines():
            self.assertTrue(name.endswith(".java"))
            self.assertGreater(number, 0)
            self.assertIn("KEYCODE_POWER", text)


if __name__ == "__main__":
    unittest.main()
