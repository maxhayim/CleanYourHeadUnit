"""The Google Home panel needs launcherx, not only dreamx (#1653).

docs/TROUBLESHOOTING.md answers "the Google Home panel is gone from the quick settings" with "re-enable
dreamx", and the G03 folder's header says the same, but #1653 is a G03_4K_US unit where the panel opens
and closes at once with dreamx still enabled: dreamx loads the panel's account and the device trust
through launcherx, the package every Google TV list disables when the replacement launcher takes Home.
Re-enabling launcherx brought the panel back with no reboot.

Nothing here copies a package name or a key in. Both packages come out of the G03 list itself - dreamx
from the group whose heading says it hosts the panel, the stock launcher from the `Undo:` line, which is
the argument tools/restore.sh turns back on - and the way out is the Keys-table row of themes/README.md
that describes the stock launcher redirect. A rename in any of the three files fails a test here instead
of leaving the page stale.
"""
import re
import tempfile
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
TROUBLESHOOTING = ROOT / "docs" / "TROUBLESHOOTING.md"
THEMES_README = ROOT / "themes" / "README.md"
LIST_PATH = ROOT / "examples" / "tcl-g03-google-tv" / "debloat.txt"

SECTION = "## Working on the TV"
PANEL_HEADING = re.compile(r"^# -{4,} .*Google Home panel")
PACKAGE = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$")
ISSUE = "1653"


def section_lines(path, heading):
    """The lines under `heading`, up to the next `## ` (None when the heading is gone)."""
    lines = path.read_text(encoding="utf-8").split("\n")
    try:
        start = lines.index(heading)
    except ValueError:
        return None
    body = []
    for line in lines[start + 1:]:
        if line.startswith("## "):
            break
        body.append(line)
    return body


def bullets(lines):
    """Each `- ` bullet of a section, its wrapped lines joined with one space."""
    out = []
    for line in lines:
        if line.startswith("- "):
            out.append(line[2:].strip())
        elif line.startswith("  ") and line.strip() and out:
            out[-1] = out[-1] + " " + line.strip()
    return [bullet for bullet in out if bullet]


def undo_packages(path=LIST_PATH):
    """Package names on the list's `Undo:` line: what tools/restore.sh turns back on."""
    for line in path.read_text(encoding="utf-8").split("\n"):
        if line.startswith("# Undo:"):
            return [token for token in re.split(r"\s+", line) if PACKAGE.match(token)]
    return []


def panel_group_packages(path=LIST_PATH):
    """Active package lines of the group whose heading says it hosts the Google Home panel."""
    packages = []
    inside = False
    for line in path.read_text(encoding="utf-8").split("\n"):
        if PANEL_HEADING.match(line):
            inside = True
            continue
        if inside and line.startswith("# ----"):
            inside = False
            continue
        if inside:
            body = line.split("#", 1)[0].strip()
            if body:
                packages.append(body)
    return packages


def redirect_keys(path=THEMES_README):
    """Keys-table rows whose description says the stock launcher package is sent back to this launcher."""
    keys = []
    for line in path.read_text(encoding="utf-8").split("\n"):
        if not line.startswith("|"):
            continue
        cells = [cell.strip() for cell in line.strip().strip("|").split("|")]
        if len(cells) >= 3 and "Stock launcher package" in cells[2]:
            keys.append(cells[0].strip("`"))
    return keys


class DerivationTest(unittest.TestCase):
    """There is exactly one of each, so the tests below cannot go stale on their own."""

    def test_the_list_names_one_stock_launcher_to_restore(self):
        packages = undo_packages()
        self.assertEqual(len(packages), 1, f"the Undo line should name one stock launcher: {packages}")

    def test_the_panel_group_names_one_package(self):
        packages = panel_group_packages()
        self.assertEqual(len(packages), 1,
                         f"the group about the Google Home panel should list one package: {packages}")

    def test_the_keys_table_has_one_redirect_row(self):
        keys = redirect_keys()
        self.assertEqual(len(keys), 1, f"one Keys-table row describes the redirect: {keys}")


class TroubleshootingNoteTest(unittest.TestCase):
    def setUp(self):
        lines = section_lines(TROUBLESHOOTING, SECTION)
        self.assertIsNotNone(lines, f"{TROUBLESHOOTING.name} no longer has a {SECTION!r} section")
        self.found = bullets(lines)
        self.assertTrue(len(self.found) >= 20,
                        f"only {len(self.found)} bullets read; the reader is not reading the page")
        self.stock = undo_packages()[0]
        self.dreamx = panel_group_packages()[0]

    def panel_bullets(self):
        """Bullets that name the panel's package: the ones this page answers this question with."""
        return [bullet for bullet in self.found if self.dreamx in bullet]

    def both_ways_bullet(self):
        """The bullet that says the panel needs the stock launcher too."""
        return [bullet for bullet in self.panel_bullets() if self.stock in bullet]

    def test_a_bullet_says_the_panel_needs_the_stock_launcher(self):
        self.assertTrue(self.both_ways_bullet(),
                        f"no bullet names {self.dreamx} and {self.stock} together: re-enabling dreamx is "
                        "not enough once the stock launcher is off (#1653)")

    def test_it_carries_the_logcat_line_for_the_account(self):
        bullet = " ".join(self.both_ways_bullet())
        provider = re.search(re.escape(self.stock) + r"\.coreservices\.\w+(\.\w+)*", bullet)
        self.assertIsNotNone(provider,
                             f"the bullet should quote the logcat line naming {self.stock}.coreservices.* "
                             "as the provider dreamx fails to find")

    def test_the_way_out_is_the_redirect_key(self):
        key = redirect_keys()[0]
        bullet = " ".join(self.both_ways_bullet())
        self.assertIn(key, bullet,
                      f"the bullet should name {key} as the way to keep the panel and still have Home on "
                      "the new launcher")
        self.assertIn("themes/README.md", bullet, "and say where that key is documented")

    def test_it_names_the_issue(self):
        self.assertIn(ISSUE, " ".join(self.both_ways_bullet()),
                      "the entries on this page carry the issue they came from")

    def test_the_older_dreamx_answer_stays(self):
        # The panel lives in dreamx and Apps only mode hides it: that half is still true.
        joined = " ".join(self.panel_bullets())
        self.assertIn("quick settings", joined)
        self.assertIn("Apps only mode", joined)


class G03HeaderTest(unittest.TestCase):
    """The folder the issue's unit is in says the same thing the page did."""

    def header_bullets(self):
        """The list's opening comment block as bullets: `# - ` starts one, `#   ` continues it."""
        out = []
        for line in LIST_PATH.read_text(encoding="utf-8").split("\n"):
            if line.startswith("# - "):
                out.append(line[4:].strip())
            elif line.startswith("#   ") and line.strip() and out:
                out[-1] = out[-1] + " " + line.lstrip("# ").strip()
            elif line.startswith("#") or not line.strip():
                continue
            else:
                break  # the first package line ends the header
        return [bullet for bullet in out if bullet]

    def test_the_header_has_the_bullets_it_did(self):
        found = self.header_bullets()
        self.assertTrue(len(found) >= 5, f"only {len(found)} header bullets read; the reader is not reading the header")
        self.assertTrue(any(panel_group_packages()[0] in bullet for bullet in found),
                        "the dreamx bullet should be one of them")

    def test_the_dreamx_bullet_names_the_stock_launcher(self):
        dreamx = panel_group_packages()[0]
        stock = undo_packages()[0]
        named = [bullet for bullet in self.header_bullets() if dreamx in bullet]
        self.assertTrue(named, f"the header should still record what {dreamx} is")
        self.assertTrue(any(stock in bullet for bullet in named),
                        f"the header's dreamx bullet should name {stock}: keeping dreamx is not enough for the "
                        "panel once the stock launcher is off (#1653)")


class ReaderTest(unittest.TestCase):
    """The readers themselves, against pages written here."""

    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.addCleanup(self.folder.cleanup)
        self.base = Path(self.folder.name)

    def page(self, name, body):
        path = self.base / name
        path.write_text(body, encoding="utf-8")
        return path

    def test_a_wrapped_bullet_is_one_bullet(self):
        path = self.page("a.md", f"# T\n\n{SECTION}\n\n- **First** line one\n  line two\n- **Second**\n\n## Next\n")
        found = bullets(section_lines(path, SECTION))
        self.assertEqual(len(found), 2, found)
        self.assertIn("line two", found[0])

    def test_the_reader_stops_at_the_next_section(self):
        path = self.page("b.md", f"# T\n\n{SECTION}\n\n- **Kept** here\n\n## Next\n\n- **Other** section\n")
        self.assertEqual(bullets(section_lines(path, SECTION)), ["**Kept** here"])

    def test_a_missing_section_reads_as_none(self):
        self.assertIsNone(section_lines(self.page("c.md", "# T\n\n## Other\n\n- x\n"), SECTION))

    def test_the_undo_reader_takes_only_package_names(self):
        path = self.page("d.txt", "# Undo: TV_HOST=<tv ip> tools/restore.sh examples/some-tv/debloat.txt com.example.home\n")
        self.assertEqual(undo_packages(path), ["com.example.home"])

    def test_a_list_without_an_undo_line_reads_as_no_packages(self):
        self.assertEqual(undo_packages(self.page("e.txt", "com.example.one\n")), [])

    def test_the_group_reader_stops_at_the_next_heading(self):
        path = self.page("f.txt",
                         "# ---- 1. telemetry ----\ncom.example.telemetry\n"
                         "# ---- 2. screensaver; also hosts the Google Home panel (see header) ----\n"
                         "com.example.panel   # a comment is not the package name\n"
                         "# ---- 3. kept ----\ncom.example.kept\n")
        self.assertEqual(panel_group_packages(path), ["com.example.panel"])

    def test_the_key_reader_takes_the_first_cell(self):
        path = self.page("g.md", "| `some_key` | string | Stock launcher package; sent back here |\n")
        self.assertEqual(redirect_keys(path), ["some_key"])

    def test_the_key_reader_ignores_rows_about_something_else(self):
        self.assertEqual(redirect_keys(self.page("h.md", "| `other_key` | bool | Show the Wi-Fi name |\n")), [])


if __name__ == "__main__":
    unittest.main()
