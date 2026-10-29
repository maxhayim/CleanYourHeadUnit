"""Every front page that shows docs/card.jpg states the launcher's size the card is drawn from.

The size is one number in the tree - `STATS` in `tools/readme_stats.py`, which `tools/gen_tables.py --font`
draws `docs/stats.png` and `docs/card.jpg` from - and each page that shows the card writes it by hand in
three places: the card's alt text (the sentence describing the picture), the `launcher/build-and-install.sh`
row of the Tools table, and the first sentence of the Launcher section. Only the alt text's three counts
are generated; the size is not, so it drifts with nothing to notice: README.nl.md was added on 2026-10-26
while the release was 523 KB, the next day's release moved to 532 KB (`bbf8a56a`, which updated README.md
and `STATS`), the German page arrived at 532 KB on 2026-10-28, and the Dutch page went on telling a reader
"ongeveer 523 KB" in all three places, directly under a card that said 532.

The number is not copied in here: it is read out of `STATS`, which is what the picture the alt text
describes is built from, so a release that moves the size fails this until every page saying it has moved,
and a `STATS` that stops stating one fails instead of reading as "no page states a size".

Run from the repository root: python3 -m unittest discover -s tests -v
"""
from pathlib import Path
import re
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
STATS = ROOT / "tools" / "readme_stats.py"
CARD = "docs/card.jpg"

# The launcher's entry in STATS, in the form the file writes it: ("532 KB", "LAUNCHER").
LAUNCHER = re.compile(r'\(\s*"(\d+) KB"\s*,\s*"LAUNCHER"\s*\)')
# A size written into a page. The space is part of it: README.nl.md's "TCL-LA-RT41KB-S1" is a model code.
SIZE = re.compile(r"(\d+) KB")


def launcher_kb(stats_text=None):
    """The launcher's size in KB, as STATS states it; raises when STATS no longer states one."""
    text = STATS.read_text(encoding="utf-8") if stats_text is None else stats_text
    match = LAUNCHER.search(text)
    if not match:
        raise AssertionError(
            'tools/readme_stats.py no longer states the launcher\'s size as ("N KB", "LAUNCHER"): the '
            "front pages' size has nothing left to be held to, and docs/card.jpg is drawn from that entry")
    return int(match.group(1))


def pages_showing_card(root=ROOT):
    """The front pages that show the card, README.md included."""
    return sorted(page for page in Path(root).glob("README*.md")
                  if CARD in page.read_text(encoding="utf-8"))


def sizes_in(text):
    """The distinct sizes a page states, in KB, in the order they appear."""
    found = []
    for number in SIZE.findall(text):
        if int(number) not in found:
            found.append(int(number))
    return found


def stale(root, kb):
    """[(page name, sizes it states)] for the pages that show the card and do not state `kb` alone."""
    out = []
    for page in pages_showing_card(root):
        written = sizes_in(page.read_text(encoding="utf-8"))
        if written != [kb]:
            out.append((page.name, written))
    return out


def report(root, kb):
    """What a reader is told about the pages that are not in step, or None when they all are."""
    wrong = stale(root, kb)
    if not wrong:
        return None
    lines = []
    for name, written in wrong:
        if not written:
            lines.append(f"{name} shows {CARD} but never states the launcher's size")
        elif len(written) > 1:
            lines.append(f"{name} states more than one size, {written}, so the one it means cannot be read")
        else:
            lines.append(f"{name} says the launcher is {written[0]} KB and {CARD} says {kb} KB")
    return "; ".join(lines)


class FrontPageLauncherSizeTest(unittest.TestCase):
    def test_the_size_is_read_from_the_entry_the_card_is_drawn_from(self):
        kb = launcher_kb()
        # The launcher ships under a 576 KB ceiling (AGENTS.md, ci.yml's launcher job), so a figure outside
        # anything a TV launcher could weigh means the entry was misread rather than moved.
        self.assertTrue(60 <= kb <= 576, f"read {kb} KB out of STATS, which is not the launcher's size")
        with self.assertRaises(AssertionError):
            launcher_kb('STATS = [("359", "TV MODELS"), ("97", "BRANDS")]\n')

    def test_the_walk_reads_every_front_page_showing_the_card(self):
        pages = [page.name for page in pages_showing_card()]
        self.assertGreaterEqual(len(pages), 3, f"read too few front pages to prove anything: {pages}")
        self.assertIn("README.md", pages, "the English front page shows docs/card.jpg but was not read")

    def test_every_front_page_states_the_size_the_card_shows(self):
        kb = launcher_kb()
        self.assertIsNone(report(ROOT, kb),
                          f"tools/readme_stats.py's STATS says the launcher is {kb} KB, which is what "
                          f"docs/card.jpg shows: {report(ROOT, kb)}")

    def test_the_english_page_states_it_in_the_three_places(self):
        # A guard on the walk: the alt text, the Tools row and the Launcher section are three statements,
        # so a reader that lost two of them would still pass the test above.
        written = SIZE.findall((ROOT / "README.md").read_text(encoding="utf-8"))
        self.assertGreaterEqual(len(written), 3,
                                f"README.md states a size {len(written)} time(s), so the walk saw only part "
                                "of the page")


class TheWalkAgainstAPageWrittenHereTest(unittest.TestCase):
    """The readers above, against a tree written here, so none of them can pass by reading nothing."""

    def tree(self, pages, stats='STATS = [("359", "TV MODELS"), ("532 KB", "LAUNCHER")]\n'):
        root = Path(tempfile.mkdtemp())
        self.addCleanup(lambda: __import__("shutil").rmtree(root, ignore_errors=True))
        (root / "tools").mkdir()
        (root / "tools" / "readme_stats.py").write_text(stats, encoding="utf-8")
        for name, body in pages.items():
            (root / name).write_text(body, encoding="utf-8")
        return root

    def card_page(self, *sizes):
        return " ".join(f'<img src="{CARD}" alt="359 TV models · {size} KB launcher">' for size in sizes)

    def test_a_page_stating_the_size_is_quiet(self):
        root = self.tree({"README.md": self.card_page(532) + "\nabout 532 KB, plain Java.\n"})
        self.assertIsNone(report(root, launcher_kb()))

    def test_a_page_with_the_old_size_is_named_with_both_numbers(self):
        root = self.tree({"README.md": self.card_page(532),
                          "README.nl.md": self.card_page(523) + "\nongeveer 523 KB, gewone Java.\n"})
        said = report(root, launcher_kb())
        self.assertIn("README.nl.md says the launcher is 523 KB and docs/card.jpg says 532 KB", said)
        self.assertNotIn("README.md", said, "the page that is in step was reported as well")

    def test_a_page_showing_the_card_without_a_size_is_reported(self):
        root = self.tree({"README.md": self.card_page(532),
                          "README.de.md": f'<img src="{CARD}" alt="359 TV-Modelle · 532">\n'})
        self.assertIn("README.de.md shows docs/card.jpg but never states the launcher's size",
                      report(root, launcher_kb()))

    def test_a_page_stating_two_sizes_is_reported_rather_than_guessed(self):
        root = self.tree({"README.md": self.card_page(532),
                          "README.it.md": self.card_page(532) + "\nabout 523 KB.\n"})
        self.assertIn("README.it.md states more than one size, [532, 523]", report(root, launcher_kb()))

    def test_a_page_that_does_not_show_the_card_is_not_judged(self):
        root = self.tree({"README.md": self.card_page(532), "README.ja.md": "about 523 KB.\n"})
        self.assertIsNone(report(root, launcher_kb()))

    def test_a_size_written_without_a_space_is_not_a_size(self):
        # README.nl.md's Tools table carries a TCL model code ending in "41KB"; it is not a launcher size.
        root = self.tree({"README.md": self.card_page(532) + "\nTCL-LA-RT41KB-S1\n"})
        self.assertEqual([532], sizes_in((root / "README.md").read_text(encoding="utf-8")))


if __name__ == "__main__":
    unittest.main()
