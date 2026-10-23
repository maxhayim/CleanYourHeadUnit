"""The three scripts that draw the README card need Pillow, and nothing a contributor reads said so.

`tools/requirements.txt` is what a reader installs for the tools and it has no Pillow; the package lives
in `tools/requirements-perf.txt`, a file whose header used to call itself "only for the performance
tools". `docs/workflows/press.md` is the page that asks a contributor to redraw `docs/featured.png` and
`docs/card.jpg`, so following it ended in `ModuleNotFoundError: No module named 'PIL'` with no hint of
what to install. The three card scripts now name that file the way tools/flamechart.py and
tools/screendiff.py already did, and this test keeps the three of them, the requirements file and the
workflow page in step.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / "tools"
PRESS = ROOT / "docs" / "workflows" / "press.md"
PERF = TOOLS / "requirements-perf.txt"

# The scripts press.md sends a reader to, with the two arguments each takes: the font, then the file.
CARD_SCRIPTS = {
    "readme_stats.py": ["Fredoka.ttf", "stats.png"],
    "readme_featured.py": ["Fredoka.ttf", "featured.png"],
    "readme_card.py": ["Fredoka.ttf", "card.jpg"],
}


def shadow_dir(tmp):
    """A PIL package that cannot be imported, so a script behaves as on a venv without Pillow."""
    pkg = Path(tmp) / "PIL"
    pkg.mkdir(exist_ok=True)
    (pkg / "__init__.py").write_text('raise ImportError("No module named \'PIL\'")\n', encoding="utf-8")
    return str(tmp)


def run_without_pillow(argv, tmp):
    env = dict(os.environ)
    env["PYTHONPATH"] = shadow_dir(tmp)
    return subprocess.run([sys.executable, *argv], cwd=ROOT, env=env, capture_output=True, text=True)


class CardScriptsPillowTest(unittest.TestCase):
    def test_the_shadow_really_hides_pillow(self):
        with tempfile.TemporaryDirectory() as tmp:
            done = run_without_pillow(["-c", "import PIL"], tmp)
        self.assertNotEqual(done.returncode, 0, "the PIL shadow did not take effect: the rest of this file would prove nothing")

    def test_each_card_script_is_importable_without_pillow_and_names_the_file(self):
        with tempfile.TemporaryDirectory() as tmp:
            for name, args in CARD_SCRIPTS.items():
                with self.subTest(script=name):
                    self.assertTrue((TOOLS / name).is_file(), f"press.md names {name}, which is not in tools/")
                    done = run_without_pillow([str(TOOLS / name), *args], tmp)
                    self.assertNotEqual(done.returncode, 0, f"{name} ran without Pillow, so the shadow missed it")
                    self.assertIn("requirements-perf.txt", done.stderr, f"{name} does not say where Pillow comes from")
                    self.assertNotIn("Traceback", done.stderr, f"{name} still dies on a bare traceback")

    def test_the_requirements_file_installs_pillow(self):
        lines = [l.strip().lower() for l in PERF.read_text(encoding="utf-8").split("\n")]
        self.assertTrue(any(l.startswith("pillow") for l in lines), "tools/requirements-perf.txt no longer installs Pillow")
        header = "\n".join(l for l in PERF.read_text(encoding="utf-8").split("\n") if l.startswith("#"))
        for name in CARD_SCRIPTS:
            with self.subTest(script=name):
                self.assertIn(name, header, f"the header of requirements-perf.txt does not say it is needed by {name}")

    def test_press_md_sends_the_reader_to_that_file(self):
        text = PRESS.read_text(encoding="utf-8")
        self.assertIn("tools/requirements-perf.txt", text, "press.md asks for a redraw without saying what to install")
        self.assertIn("docs/card.jpg", text, "press.md no longer names the card the README links")
        self.assertIn("docs/featured.png", text)
        # readme_stats.py draws the stat strip, which a press entry does not change, so press.md is not
        # required to name it; the two scripts it does send a reader to are.
        for name in ("readme_featured.py", "readme_card.py"):
            with self.subTest(script=name):
                self.assertIn(name, text, f"press.md does not name {name}")


if __name__ == "__main__":
    unittest.main()
